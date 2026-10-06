package org.koitharu.kotatsu.core.media

import org.koitharu.kotatsu.core.model.MangaSourceInfo
import org.koitharu.kotatsu.jsext.JsItemType
import org.koitharu.kotatsu.jsext.repo.JsSourceRegistry
import org.koitharu.kotatsu.jsext.source.JsMangaSource
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource

/** `null` means "all types". */
fun MediaType?.accepts(type: MediaType): Boolean = this == null || this == type

private fun MangaSource.unwrapped(): MangaSource =
	if (this is MangaSourceInfo) mangaSource else this

/** Returns the underlying installed Mangayomi JS source, if this source is one. */
fun MangaSource.asJsSource(): JsMangaSource? = unwrapped() as? JsMangaSource

/** True only for Mangayomi JS books/anime that need the dedicated media reader. */
fun MangaSource.isJsContinuousMedia(): Boolean = asJsSource() != null && mediaType() != MediaType.MANGA

/** Parser sources report a [org.koitharu.kotatsu.parsers.model.ContentType]; everything else is treated as manga. */
fun MangaSource.mediaType(): MediaType {
	val source = unwrapped()
	if (source is JsMangaSource) {
		return when (JsSourceRegistry.get(source.id)?.itemType) {
			JsItemType.NOVEL -> MediaType.BOOK
			JsItemType.ANIME -> MediaType.VIDEO
			else -> MediaType.MANGA
		}
	}
	return (source as? MangaParserSource)?.contentType?.toMediaType() ?: MediaType.MANGA
}

fun <T : MangaSource> List<T>.filterByMediaType(type: MediaType?): List<T> =
	if (type == null) this else filter { type.accepts(it.mediaType()) }
