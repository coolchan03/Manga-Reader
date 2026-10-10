package org.koitharu.kotatsu.jsext.source

import org.koitharu.kotatsu.parsers.model.MangaSource

/** An installed Mangayomi JS source. Persisted by name as `js:<id>`. */
data class JsMangaSource(val id: Long) : MangaSource {

	override val name: String
		get() = "$PREFIX$id"

	companion object {

		const val PREFIX = "js:"

		/** @return `null` if [name] is not a JS source name. */
		fun fromName(name: String): JsMangaSource? =
			name.takeIf { it.startsWith(PREFIX) }?.substring(PREFIX.length)?.toLongOrNull()?.let(::JsMangaSource)
	}
}
