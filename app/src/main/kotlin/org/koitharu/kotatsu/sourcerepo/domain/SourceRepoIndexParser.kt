package org.koitharu.kotatsu.sourcerepo.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import org.koitharu.kotatsu.core.media.MediaType

/**
 * Parses a repo index. Accepted shapes:
 *
 * ```
 * [ { "name": "...", "pkg": "...", "version": 12, "versionName": "1.2", "apk": "https://...",
 *     "type": "manga|book|video", "sha256": "..." } ]
 * { "name": "My repo", "plugins": [ ...same entries... ] }
 * ```
 *
 * Parsing is deliberately forgiving about one bad entry (it is skipped, not fatal) but strict about
 * what it lets through: the APK URL must be https and the hash, when present, must be 64 hex chars.
 */
object SourceRepoIndexParser {

	private val json = Json { isLenient = true }
	private val sha256Regex = Regex("^[0-9a-fA-F]{64}$")

	/** @throws IllegalArgumentException if [text] is not a recognisable index at all. */
	fun parse(text: String, repoUrl: String): List<RepoPlugin> {
		val root = try {
			json.parseToJsonElement(text)
		} catch (e: Exception) {
			throw IllegalArgumentException("Repo index is not valid JSON", e)
		}
		val entries: JsonArray = when (root) {
			is JsonArray -> root
			is JsonObject -> root["plugins"] as? JsonArray
				?: throw IllegalArgumentException("Repo index has no \"plugins\" list")

			else -> throw IllegalArgumentException("Repo index must be a list or an object")
		}
		return entries.mapNotNull { parseEntry(it, repoUrl) }
	}

	private fun parseEntry(element: JsonElement, repoUrl: String): RepoPlugin? {
		val obj = element as? JsonObject ?: return null
		val pkg = obj.string("pkg", "package")?.takeIf { it.isNotBlank() } ?: return null
		val apk = obj.string("apk", "url")?.takeIf { it.startsWith("https://", ignoreCase = true) } ?: return null
		val version = obj.long("version", "versionCode") ?: return null
		val sha = obj.string("sha256")?.takeIf { it.isNotEmpty() }
		if (sha != null && !sha256Regex.matches(sha)) {
			return null
		}
		return RepoPlugin(
			name = obj.string("name")?.takeIf { it.isNotBlank() } ?: pkg,
			packageName = pkg,
			versionCode = version,
			versionName = obj.string("versionName") ?: version.toString(),
			apkUrl = apk,
			mediaType = parseMediaType(obj.string("type", "mediaType")),
			sha256 = sha?.lowercase(),
			repoUrl = repoUrl,
		)
	}

	private fun parseMediaType(value: String?): MediaType = when (value?.trim()?.lowercase()) {
		"book", "books", "novel" -> MediaType.BOOK
		"video", "anime" -> MediaType.VIDEO
		else -> MediaType.MANGA
	}

	private fun JsonObject.string(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
		(this[key] as? JsonPrimitive)?.contentOrNull
	}

	private fun JsonObject.long(vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
		(this[key] as? JsonPrimitive)?.longOrNull
	}
}

/** When several repos advertise the same package, keep the newest version. */
fun List<RepoPlugin>.mergeNewest(): List<RepoPlugin> = groupBy { it.packageName }
	.map { (_, group) -> group.maxBy { it.versionCode } }
	.sortedBy { it.name.lowercase() }
