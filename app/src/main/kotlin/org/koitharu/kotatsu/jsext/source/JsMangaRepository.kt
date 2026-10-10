package org.koitharu.kotatsu.jsext.source

import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.parser.CachingMangaRepository
import org.koitharu.kotatsu.jsext.JsChapter
import org.koitharu.kotatsu.jsext.JsItemType
import org.koitharu.kotatsu.jsext.JsManga
import org.koitharu.kotatsu.jsext.repo.JsSourceRegistry
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap

/**
 * Presents a Mangayomi JS source as an ordinary Kotatsu source, so Explore, search, favourites and
 * history work with it unchanged.
 */
class JsMangaRepository(
	override val source: JsMangaSource,
	cache: MemoryContentCache,
	private val extensions: JsExtensionProvider,
) : CachingMangaRepository(cache) {

	private class PageState(val lastPage: Int, val hasNext: Boolean)

	/** Mangayomi pages by 1-based number, Kotatsu by item offset: remember where each listing got to. */
	private val pageStates = ConcurrentHashMap<String, PageState>()

	private val entry
		get() = JsSourceRegistry.get(source.id)

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.POPULARITY, SortOrder.UPDATED)

	override var defaultSortOrder: SortOrder = SortOrder.POPULARITY

	override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val query = filter?.query?.takeIf { it.isNotBlank() }
		val key = if (query != null) "q:$query" else "o:${order ?: defaultSortOrder}"
		val state = pageStates[key]
		val page = when {
			offset == 0 || state == null -> 1
			!state.hasNext -> return emptyList()
			else -> state.lastPage + 1
		}
		val ext = extensions.get(source.id)
		val result = when {
			query != null -> ext.search(query, page)
			order == SortOrder.UPDATED && ext.supportsLatest() -> ext.getLatestUpdates(page)
			else -> ext.getPopular(page)
		}
		pageStates[key] = PageState(page, result.hasNextPage && result.list.isNotEmpty())
		return result.list.mapNotNull { it.toManga(chapters = null) }
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga {
		val details = extensions.get(source.id).getDetail(manga.url)
		val mapped = details.copy(link = manga.url).toManga(chapters = details.chapters) ?: return manga
		return mapped.copy(
			title = mapped.title.ifBlank { manga.title },
			coverUrl = mapped.coverUrl ?: manga.coverUrl,
			publicUrl = manga.publicUrl,
		)
	}

	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = when (entry?.itemType) {
		JsItemType.MANGA, null -> extensions.get(source.id).getPageList(chapter.url).map {
			MangaPage(id = uid(it.url), url = it.url, preview = null, source = source)
		}

		// Text/video readers use one synthetic page so Kotatsu's existing chapter/history loader can
		// keep doing chapter selection and persistence. The media reader never treats this URL as an image.
		JsItemType.NOVEL, JsItemType.ANIME -> listOf(
			MangaPage(
				id = uid("media:${chapter.url}"),
				url = chapter.url,
				preview = null,
				source = source,
			),
		)
	}

	override suspend fun getPageUrl(page: MangaPage): String = page.url

	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()

	// --- mapping ---

	private fun JsManga.toManga(chapters: List<JsChapter>?): Manga? {
		val link = link?.takeIf { it.isNotBlank() } ?: return null
		val title = name?.takeIf { it.isNotBlank() } ?: return null
		val meta = entry
		return Manga(
			id = uid(link),
			title = title,
			altTitles = emptySet(),
			url = link,
			publicUrl = absolute(link, meta?.baseUrl.orEmpty()),
			rating = RATING_UNKNOWN,
			contentRating = if (meta?.isNsfw == true) ContentRating.ADULT else null,
			coverUrl = imageUrl?.takeIf { it.isNotBlank() },
			tags = genre.mapTo(LinkedHashSet()) { MangaTag(title = it, key = it, source = source) },
			state = status.toMangaState(),
			authors = listOfNotNull(author, artist).filter { it.isNotBlank() }.toSet(),
			largeCoverUrl = null,
			description = description?.takeIf { it.isNotBlank() },
			chapters = chapters?.toMangaChapters(),
			source = source,
		)
	}

	/**
	 * Mangayomi lists chapters newest-first by convention; Kotatsu wants reading order. A source that
	 * already returns them oldest-first is detected from the upload dates.
	 */
	private fun List<JsChapter>.toMangaChapters(): List<MangaChapter> {
		val firstDate = firstOrNull()?.dateUpload ?: 0L
		val lastDate = lastOrNull()?.dateUpload ?: 0L
		val ordered = if (firstDate != 0L && lastDate != 0L && firstDate < lastDate) this else asReversed()
		return ordered.mapIndexed { index, c ->
			MangaChapter(
				id = uid(c.url),
				title = c.name.ifBlank { null },
				number = (index + 1).toFloat(),
				volume = 0,
				url = c.url,
				scanlator = c.scanlator?.takeIf { it.isNotBlank() },
				uploadDate = c.dateUpload ?: 0L,
				branch = null,
				source = source,
			)
		}
	}

	private fun Int.toMangaState(): MangaState? = when (this) {
		0 -> MangaState.ONGOING
		1, 4 -> MangaState.FINISHED
		2 -> MangaState.PAUSED
		3 -> MangaState.ABANDONED
		else -> null
	}

	private fun absolute(link: String, baseUrl: String): String = when {
		link.startsWith("http://") || link.startsWith("https://") -> link
		baseUrl.isEmpty() -> link
		else -> baseUrl.trimEnd('/') + "/" + link.trimStart('/')
	}

	/** Same scheme as the parsers' `generateUid`: stable per source + url. */
	private fun uid(url: String): Long {
		var h = UID_SEED
		source.name.forEach { c -> h = 31 * h + c.code }
		url.forEach { c -> h = 31 * h + c.code }
		return h
	}

	private companion object {

		const val UID_SEED = 1125899906842597L
	}
}
