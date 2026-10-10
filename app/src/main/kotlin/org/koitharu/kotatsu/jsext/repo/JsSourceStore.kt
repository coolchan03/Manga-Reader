package org.koitharu.kotatsu.jsext.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koitharu.kotatsu.jsext.JsItemType
import java.io.File

/**
 * Installed JS sources, one `<id>.js` plus `<id>.json` (metadata) per source under [dir].
 * Plain `java.io.File` on purpose, so it is testable on a JVM.
 */
class JsSourceStore(private val dir: File) {

	data class Installed(val entry: JsSourceEntry, val code: String)

	private val _changes = MutableStateFlow(0)

	/** Ticks whenever a source is installed, updated or removed. */
	val changes: StateFlow<Int> = _changes.asStateFlow()

	init {
		JsSourceRegistry.replaceAll(list())
	}

	private fun publish() {
		JsSourceRegistry.replaceAll(list())
		_changes.value++
	}

	/** Writes the metadata last, so a half-finished install is never listed. */
	@Synchronized
	fun install(entry: JsSourceEntry, code: String) {
		dir.mkdirs()
		File(dir, "${entry.id}.js").writeText(code)
		File(dir, "${entry.id}.json").writeText(toJson(entry).toString())
		publish()
	}

	@Synchronized
	fun uninstall(id: Long) {
		File(dir, "$id.json").delete()
		File(dir, "$id.js").delete()
		publish()
	}

	@Synchronized
	fun list(): List<JsSourceEntry> = dir.listFiles { f -> f.extension == "json" }.orEmpty()
		.mapNotNull { runCatching { fromJson(File(it.path).readText()) }.getOrNull() }
		.filter { File(dir, "${it.id}.js").isFile }
		.sortedBy { it.name.lowercase() }

	@Synchronized
	fun get(id: Long): Installed? {
		val meta = File(dir, "$id.json").takeIf { it.isFile } ?: return null
		val code = File(dir, "$id.js").takeIf { it.isFile } ?: return null
		val entry = runCatching { fromJson(meta.readText()) }.getOrNull() ?: return null
		return Installed(entry, code.readText())
	}

	fun installedVersion(id: Long): String? = get(id)?.entry?.version

	private fun toJson(e: JsSourceEntry): JsonObject = buildJsonObject {
		put("id", e.id)
		put("name", e.name)
		put("baseUrl", e.baseUrl)
		put("lang", e.lang)
		put("version", e.version)
		put("itemType", e.itemType.code)
		put("sourceCodeUrl", e.sourceCodeUrl)
		put("iconUrl", e.iconUrl)
		put("apiUrl", e.apiUrl)
		put("dateFormat", e.dateFormat)
		put("dateFormatLocale", e.dateFormatLocale)
		put("isNsfw", e.isNsfw)
		put("hasCloudflare", e.hasCloudflare)
		put("additionalParams", e.additionalParams)
		put("notes", e.notes)
		put("repoUrl", e.repoUrl)
	}

	private fun fromJson(text: String): JsSourceEntry {
		val o = Json.parseToJsonElement(text) as JsonObject
		fun s(k: String) = (o[k] as? JsonPrimitive)?.content.orEmpty()
		fun b(k: String) = (o[k] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
		return JsSourceEntry(
			id = s("id").toLong(),
			name = s("name"),
			baseUrl = s("baseUrl"),
			lang = s("lang"),
			version = s("version"),
			itemType = JsItemType.fromCode(s("itemType").toIntOrNull()),
			language = SourceCodeLanguage.JAVASCRIPT,
			sourceCodeUrl = s("sourceCodeUrl"),
			iconUrl = s("iconUrl"),
			apiUrl = s("apiUrl"),
			dateFormat = s("dateFormat"),
			dateFormatLocale = s("dateFormatLocale"),
			isNsfw = b("isNsfw"),
			hasCloudflare = b("hasCloudflare"),
			additionalParams = s("additionalParams"),
			notes = s("notes"),
			repoUrl = s("repoUrl"),
		)
	}
}
