package org.koitharu.kotatsu.jsext.repo

import org.koitharu.kotatsu.jsext.JsItemType
import org.koitharu.kotatsu.jsext.JsSourceInfo

enum class SourceCodeLanguage { DART, JAVASCRIPT, UNKNOWN }

/**
 * One source advertised by a Mangayomi-format repo index (`index.json`, `novel_index.json`,
 * `anime_index.json`, ...). Only [SourceCodeLanguage.JAVASCRIPT] entries can run here; Dart ones
 * need Mangayomi's own interpreter.
 */
data class JsSourceEntry(
	val id: Long,
	val name: String,
	val baseUrl: String,
	val lang: String,
	val version: String,
	val itemType: JsItemType,
	val language: SourceCodeLanguage,
	val sourceCodeUrl: String,
	val iconUrl: String,
	val apiUrl: String,
	val dateFormat: String,
	val dateFormatLocale: String,
	val isNsfw: Boolean,
	val hasCloudflare: Boolean,
	val additionalParams: String,
	val notes: String,
	/** The index it came from. */
	val repoUrl: String,
) {

	val isRunnable: Boolean
		get() = language == SourceCodeLanguage.JAVASCRIPT

	fun toSourceInfo() = JsSourceInfo(
		id = id,
		name = name,
		baseUrl = baseUrl,
		lang = lang,
		apiUrl = apiUrl,
		iconUrl = iconUrl,
		dateFormat = dateFormat,
		dateFormatLocale = dateFormatLocale,
		hasCloudflare = hasCloudflare,
		isNsfw = isNsfw,
		additionalParams = additionalParams,
		notes = notes,
		version = version,
		itemType = itemType,
	)
}
