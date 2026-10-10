package org.koitharu.kotatsu.jsext.repo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.koitharu.kotatsu.jsext.JsItemType

/**
 * Parses Mangayomi repo indexes (the JSON array in `index.json` & friends).
 *
 * Entries without a numeric `id` and a `sourceCodeUrl` are skipped: those are Mihon/Aniyomi-style
 * APK extension entries (`pkgName`), a different system. Entries are kept even when their code is
 * Dart so the UI can say how many it had to leave out; check [JsSourceEntry.isRunnable].
 */
object MangayomiIndexParser {

	private val json = Json { isLenient = true }

	/** @throws IllegalArgumentException if [text] is not a JSON array. */
	fun parse(text: String, repoUrl: String): List<JsSourceEntry> {
		val root = try {
			json.parseToJsonElement(text)
		} catch (e: Exception) {
			throw IllegalArgumentException("Repo index is not valid JSON", e)
		}
		val array = root as? JsonArray ?: throw IllegalArgumentException("Mangayomi index must be a JSON array")
		return array.mapNotNull { parseEntry(it, repoUrl) }
	}

	/** True for the shape [parse] understands, so callers can pick a parser per index. */
	fun looksLikeMangayomiIndex(text: String): Boolean = try {
		val root = json.parseToJsonElement(text) as? JsonArray
		root != null && root.any { (it as? JsonObject)?.containsKey("sourceCodeUrl") == true }
	} catch (e: Exception) {
		false
	}

	private fun parseEntry(element: JsonElement, repoUrl: String): JsSourceEntry? {
		val o = element as? JsonObject ?: return null
		val id = o.long("id") ?: return null
		val codeUrl = o.string("sourceCodeUrl")?.takeIf { it.startsWith("https://", ignoreCase = true) } ?: return null
		val language = when (o.int("sourceCodeLanguage")) {
			0 -> SourceCodeLanguage.DART
			1 -> SourceCodeLanguage.JAVASCRIPT
			else -> if (codeUrl.endsWith(".js", ignoreCase = true)) SourceCodeLanguage.JAVASCRIPT else SourceCodeLanguage.UNKNOWN
		}
		return JsSourceEntry(
			id = id,
			name = o.string("name")?.takeIf { it.isNotBlank() } ?: return null,
			baseUrl = o.string("baseUrl").orEmpty(),
			lang = o.string("lang").orEmpty(),
			version = o.string("version").orEmpty(),
			itemType = JsItemType.fromCode(o.int("itemType")),
			language = language,
			sourceCodeUrl = codeUrl,
			iconUrl = o.string("iconUrl").orEmpty(),
			apiUrl = o.string("apiUrl").orEmpty(),
			dateFormat = o.string("dateFormat").orEmpty(),
			dateFormatLocale = o.string("dateFormatLocale").orEmpty(),
			isNsfw = o.bool("isNsfw") ?: false,
			hasCloudflare = o.bool("hasCloudflare") ?: false,
			additionalParams = o.string("additionalParams").orEmpty(),
			notes = o.string("notes").orEmpty(),
			repoUrl = repoUrl,
		)
	}

	private fun JsonObject.prim(key: String) = this[key] as? JsonPrimitive

	private fun JsonObject.string(key: String): String? = prim(key)?.contentOrNull

	private fun JsonObject.long(key: String): Long? = prim(key)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

	private fun JsonObject.int(key: String): Int? = prim(key)?.intOrNull

	private fun JsonObject.bool(key: String): Boolean? = prim(key)?.contentOrNull?.toBooleanStrictOrNull()
}

/** Dotted-number comparison: "0.1.10" > "0.1.9". Non-numeric parts count as 0. */
fun compareVersions(a: String, b: String): Int {
	val pa = a.trim().split('.', '-').map { it.toIntOrNull() ?: 0 }
	val pb = b.trim().split('.', '-').map { it.toIntOrNull() ?: 0 }
	for (i in 0 until maxOf(pa.size, pb.size)) {
		val c = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
		if (c != 0) return c
	}
	return 0
}
