package org.koitharu.kotatsu.jsext

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Mangayomi's `itemType`: what a JS source provides. */
enum class JsItemType(val code: Int) {

	MANGA(0),
	ANIME(1),
	NOVEL(2),
	;

	companion object {

		fun fromCode(code: Int?): JsItemType = entries.firstOrNull { it.code == code } ?: MANGA
	}
}

/** The `source` object a Mangayomi extension sees as `this.source`. */
data class JsSourceInfo(
	val id: Long,
	val name: String,
	val baseUrl: String,
	val lang: String,
	val apiUrl: String = "",
	val iconUrl: String = "",
	val dateFormat: String = "",
	val dateFormatLocale: String = "",
	val hasCloudflare: Boolean = false,
	val isFullData: Boolean = false,
	val isNsfw: Boolean = false,
	val additionalParams: String = "",
	val notes: String = "",
	val version: String = "",
	val itemType: JsItemType = JsItemType.MANGA,
) {

	fun toJson(): JsonObject = buildJsonObject {
		put("id", id)
		put("name", name)
		put("baseUrl", baseUrl)
		put("lang", lang)
		put("apiUrl", apiUrl)
		put("iconUrl", iconUrl)
		put("dateFormat", dateFormat)
		put("dateFormatLocale", dateFormatLocale)
		put("hasCloudflare", hasCloudflare)
		put("isFullData", isFullData)
		put("isNsfw", isNsfw)
		put("additionalParams", additionalParams)
		put("notes", notes)
		put("version", version)
		put("itemType", itemType.code)
	}
}

data class JsChapter(
	val name: String,
	val url: String,
	val dateUpload: Long?,
	val scanlator: String?,
)

/** Mangayomi's `MManga`; used both for list entries and for details. */
data class JsManga(
	val name: String?,
	val link: String?,
	val imageUrl: String?,
	val description: String?,
	val author: String?,
	val artist: String?,
	/** 0 ongoing, 1 completed, 2 hiatus, 3 canceled, 4 publishing finished, else unknown. */
	val status: Int,
	val genre: List<String>,
	val chapters: List<JsChapter>,
)

data class JsPages(val list: List<JsManga>, val hasNextPage: Boolean)

data class JsPage(val url: String, val headers: Map<String, String>?)

data class JsTrack(val file: String?, val label: String?)

data class JsVideo(
	val url: String,
	val quality: String,
	val originalUrl: String,
	val headers: Map<String, String>?,
	val subtitles: List<JsTrack>,
	val audios: List<JsTrack>,
)

// --- JSON -> model, tolerant in the same places Mangayomi's fromJson is ---

internal fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

internal fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

internal fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

internal fun JsonElement?.stringMap(): Map<String, String>? =
	(this as? JsonObject)?.mapValues { (_, v) -> v.str().orEmpty() }

/** Extensions sometimes `push(array)` instead of spreading it; keep those tags instead of dropping them. */
internal fun JsonArray.flattenStrings(): List<String> = flatMap { e ->
	if (e is JsonArray) e.flattenStrings() else listOfNotNull(e.str())
}

internal fun JsonObject.toJsChapter(): JsChapter = JsChapter(
	name = this["name"].str().orEmpty(),
	url = this["url"].str().orEmpty(),
	dateUpload = this["dateUpload"].long(),
	scanlator = this["scanlator"].str(),
)

internal fun JsonObject.toJsManga(): JsManga {
	val chapterArray = (this["chapters"] as? JsonArray) ?: (this["episodes"] as? JsonArray)
	return JsManga(
		name = this["name"].str(),
		link = this["link"].str(),
		imageUrl = this["imageUrl"].str(),
		description = this["description"].str(),
		author = this["author"].str(),
		artist = this["artist"].str(),
		status = this["status"].long()?.toInt() ?: 5,
		genre = (this["genre"] as? JsonArray)?.flattenStrings().orEmpty(),
		chapters = chapterArray?.mapNotNull { (it as? JsonObject)?.toJsChapter() }.orEmpty(),
	)
}

internal fun JsonObject.toJsPages(): JsPages = JsPages(
	list = (this["list"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.toJsManga() }.orEmpty(),
	hasNextPage = this["hasNextPage"].bool() ?: false,
)

internal fun JsonObject.toJsVideo(): JsVideo? {
	val url = this["url"].str()?.trim() ?: return null
	val originalUrl = this["originalUrl"].str()?.trim() ?: return null
	fun tracks(key: String) = (this[key] as? JsonArray)?.mapNotNull { e ->
		(e as? JsonObject)?.let { JsTrack(it["file"].str()?.trim(), it["label"].str()?.trim()) }
	}.orEmpty()
	return JsVideo(
		url = url,
		quality = this["quality"].str()?.trim().orEmpty(),
		originalUrl = originalUrl,
		headers = this["headers"].stringMap(),
		subtitles = tracks("subtitles"),
		audios = tracks("audios"),
	)
}
