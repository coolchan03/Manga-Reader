package org.koitharu.kotatsu.jsext.repo

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory view of the installed JS sources, so code that only has a source id (titles, NSFW and
 * media-type checks deep in the UI) can answer without file access. [JsSourceStore] keeps it current.
 */
object JsSourceRegistry {

	private val entries = ConcurrentHashMap<Long, JsSourceEntry>()

	fun get(id: Long): JsSourceEntry? = entries[id]

	internal fun replaceAll(list: List<JsSourceEntry>) {
		entries.keys.retainAll(list.mapTo(HashSet()) { it.id })
		list.forEach { entries[it.id] = it }
	}
}
