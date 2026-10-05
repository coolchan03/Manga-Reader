package org.koitharu.kotatsu.core.media

import org.koitharu.kotatsu.core.model.MangaSourceInfo
import org.koitharu.kotatsu.jsext.JsItemType
import org.koitharu.kotatsu.jsext.repo.JsSourceRegistry
import org.koitharu.kotatsu.jsext.source.JsMangaSource
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource

/** `null` means "all types". */
fun MediaType?.accepts(type: MediaType): Boolean = this == null || this == type

/** Parser sources report a [org.koitharu.kotatsu.parsers.model.ContentType]; everything else is treated as manga. */
fun MangaSource.mediaType(): MediaType {
	val source = if (this is MangaSourceInfo) mangaSource else this
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
