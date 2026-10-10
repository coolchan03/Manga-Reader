package org.koitharu.kotatsu.core.media

import org.koitharu.kotatsu.parsers.model.ContentType

/**
 * Top-level kind of content a source provides. This is what Explore, search and the library
 * group by; it is deliberately coarser than the parsers' [ContentType].
 */
enum class MediaType {

	MANGA,
	BOOK,
	VIDEO,
}

/**
 * Maps parser content types onto [MediaType]. Everything the parsers library ships today is
 * image-based, except novels which are text. [MediaType.VIDEO] has no parser-level equivalent
 * and is only produced by video sources (see docs/MEDIA_TYPES.md).
 */
fun ContentType.toMediaType(): MediaType = when (this) {
	ContentType.NOVEL -> MediaType.BOOK
	else -> MediaType.MANGA
}
