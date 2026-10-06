package org.koitharu.kotatsu.jsext

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.util.ArrayDeque
import java.util.zip.ZipInputStream

/**
 * Host implementation of Mangayomi's parseEpub()/parseEpubChapter() helpers.
 *
 * Upstream downloads the whole EPUB, exposes its title/author/chapter names, then returns the raw
 * XHTML for a selected chapter. Keep one parsed book cached per extension so moving between
 * chapters does not download and unzip the same file repeatedly.
 */
internal class JsEpubBridge(
	private val transport: JsHttpTransport,
) {

	private var cachedUrl: String? = null
	private var cachedBook: ParsedEpub? = null

	suspend fun handle(name: String, args: JsonArray): String = when (name) {
		"parseEpub" -> {
			val book = load(args)
			buildJsonObject {
				put("title", book.title)
				book.author?.let { put("author", it) }
				put("chapters", JsonArray(book.chapters.map { JsonPrimitive(it.title) }))
			}.toString()
		}

		"parseEpubChapter" -> {
			val chapterTitle = args.getOrNull(3).str().orEmpty()
			load(args).chapters.firstOrNull { it.title == chapterTitle }?.content.orEmpty()
		}

		else -> throw JsExtensionException("Unknown EPUB host call: $name")
	}

	private suspend fun load(args: JsonArray): ParsedEpub {
		val url = args.getOrNull(1).str()?.takeIf { it.isNotBlank() }
			?: throw JsExtensionException("EPUB: missing URL")
		if (cachedUrl == url) {
			cachedBook?.let { return it }
		}
		val headers = args.getOrNull(2).stringMap().orEmpty()
		val response = transport.execute(
			JsHttpRequest(
				method = "GET",
				url = url,
				headers = headers,
				body = null,
				options = null,
				binary = true,
			),
		)
		if (response.statusCode !in 200..299) {
			throw JsExtensionException("EPUB download failed: HTTP ${response.statusCode} ${response.reasonPhrase.orEmpty()}".trim())
		}
		val bytes = response.bodyBytes ?: response.body.toByteArray(Charsets.ISO_8859_1)
		if (bytes.isEmpty()) {
			throw JsExtensionException("EPUB download returned an empty file")
		}
		if (bytes.size > MAX_EPUB_BYTES) {
			throw JsExtensionException("EPUB is too large to open safely")
		}
		val book = parse(bytes)
		cachedUrl = url
		cachedBook = book
		return book
	}

	internal fun parse(bytes: ByteArray): ParsedEpub {
		val entries = unzip(bytes)
		val container = entries["META-INF/container.xml"]
			?: throw JsExtensionException("EPUB is missing META-INF/container.xml")
		val containerDoc = xml(container)
		val opfPath = containerDoc.allElements
			.firstOrNull { it.localTag() == "rootfile" }
			?.attr("full-path")
			?.takeIf { it.isNotBlank() }
			?.let(::normalizePath)
			?: throw JsExtensionException("EPUB does not declare its package document")
		val opfBytes = entries[opfPath]
			?: throw JsExtensionException("EPUB package document '$opfPath' is missing")
		val opf = xml(opfBytes)

		val title = opf.firstByLocalTag("title")?.text()?.takeIf { it.isNotBlank() } ?: "Untitled"
		val author = opf.firstByLocalTag("creator")?.text()?.takeIf { it.isNotBlank() }

		val manifest = LinkedHashMap<String, ManifestItem>()
		opf.allElements.filter { it.localTag() == "item" }.forEach { item ->
			val id = item.attr("id")
			val href = item.attr("href")
			if (id.isNotBlank() && href.isNotBlank()) {
				manifest[id] = ManifestItem(
					href = href,
					mediaType = item.attr("media-type"),
					properties = item.attr("properties"),
				)
			}
		}

		val toc = readToc(entries, opfPath, manifest)
		val chapters = ArrayList<EpubChapter>()
		opf.allElements.filter { it.localTag() == "itemref" }.forEachIndexed { index, itemRef ->
			val idref = itemRef.attr("idref")
			val manifestItem = manifest[idref] ?: return@forEachIndexed
			val path = resolveRelative(opfPath, manifestItem.href)
			val contentBytes = entries[path] ?: return@forEachIndexed
			val chapterTitle = toc[path]?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}"
			chapters += EpubChapter(
				title = chapterTitle,
				path = path,
				content = decodeText(contentBytes),
			)
		}
		if (chapters.isEmpty()) {
			throw JsExtensionException("EPUB has no readable spine chapters")
		}
		return ParsedEpub(title = title, author = author, chapters = chapters)
	}

	private fun readToc(
		entries: Map<String, ByteArray>,
		opfPath: String,
		manifest: Map<String, ManifestItem>,
	): Map<String, String> {
		val result = LinkedHashMap<String, String>()

		// EPUB 3 navigation document.
		val navItem = manifest.values.firstOrNull { item ->
			item.properties.split(Regex("\\s+")).any { it == "nav" }
		}
		if (navItem != null) {
			val navPath = resolveRelative(opfPath, navItem.href)
			entries[navPath]?.let { bytes ->
				val nav = xml(bytes)
				nav.allElements.filter { it.localTag() == "a" }.forEach { link ->
					val href = link.attr("href").substringBefore('#')
					val label = link.text().trim()
					if (href.isNotBlank() && label.isNotBlank()) {
						result.putIfAbsent(resolveRelative(navPath, href), label)
					}
				}
			}
		}

		// EPUB 2 NCX fallback.
		val ncxItem = manifest.values.firstOrNull {
			it.mediaType.equals("application/x-dtbncx+xml", ignoreCase = true)
		}
		if (ncxItem != null) {
			val ncxPath = resolveRelative(opfPath, ncxItem.href)
			entries[ncxPath]?.let { bytes ->
				val ncx = xml(bytes)
				ncx.allElements.filter { it.localTag() == "navPoint" }.forEach { point ->
					val label = point.allElements.firstOrNull { it.localTag() == "text" }?.text()?.trim()
					val src = point.allElements.firstOrNull { it.localTag() == "content" }
						?.attr("src")?.substringBefore('#')
					if (!label.isNullOrBlank() && !src.isNullOrBlank()) {
						result.putIfAbsent(resolveRelative(ncxPath, src), label)
					}
				}
			}
		}
		return result
	}

	private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
		val result = LinkedHashMap<String, ByteArray>()
		var total = 0L
		ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
			while (true) {
				val entry = zip.nextEntry ?: break
				if (entry.isDirectory) {
					zip.closeEntry()
					continue
				}
				val path = normalizePath(entry.name)
				if (path.isBlank()) {
					zip.closeEntry()
					continue
				}
				val out = java.io.ByteArrayOutputStream()
				val buffer = ByteArray(16 * 1024)
				var entryTotal = 0L
				while (true) {
					val n = zip.read(buffer)
					if (n < 0) break
					entryTotal += n
					total += n
					if (entryTotal > MAX_ENTRY_BYTES || total > MAX_UNCOMPRESSED_BYTES) {
						throw JsExtensionException("EPUB expands beyond the safe size limit")
					}
					out.write(buffer, 0, n)
				}
				result[path] = out.toByteArray()
				zip.closeEntry()
			}
		}
		return result
	}

	private fun xml(bytes: ByteArray): Document =
		Jsoup.parse(decodeText(bytes), "", Parser.xmlParser())

	private fun decodeText(bytes: ByteArray): String {
		val prefix = bytes.take(256).toByteArray().toString(Charsets.ISO_8859_1)
		val declared = Regex("""encoding\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
			.find(prefix)?.groupValues?.getOrNull(1)
		val charset = declared?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
		return bytes.toString(charset)
	}

	private fun Document.firstByLocalTag(name: String): Element? =
		allElements.firstOrNull { it.localTag() == name }

	private fun Element.localTag(): String = tagName().substringAfterLast(':').lowercase()

	private fun resolveRelative(baseFile: String, href: String): String {
		val cleanHref = runCatching { URLDecoder.decode(href.substringBefore('#'), Charsets.UTF_8.name()) }
			.getOrDefault(href.substringBefore('#'))
			.replace('\\', '/')
		if (cleanHref.startsWith('/')) return normalizePath(cleanHref)
		val baseDir = baseFile.substringBeforeLast('/', "")
		return normalizePath(if (baseDir.isEmpty()) cleanHref else "$baseDir/$cleanHref")
	}

	private fun normalizePath(path: String): String {
		val stack = ArrayDeque<String>()
		path.replace('\\', '/').trimStart('/').split('/').forEach { segment ->
			when (segment) {
				"", "." -> Unit
				".." -> if (stack.isNotEmpty()) stack.removeLast()
				else -> stack.addLast(segment)
			}
		}
		return stack.joinToString("/")
	}

	internal data class ParsedEpub(
		val title: String,
		val author: String?,
		val chapters: List<EpubChapter>,
	)

	internal data class EpubChapter(
		val title: String,
		val path: String,
		val content: String,
	)

	private data class ManifestItem(
		val href: String,
		val mediaType: String,
		val properties: String,
	)

	private companion object {
		const val MAX_EPUB_BYTES = 128 * 1024 * 1024
		const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
		const val MAX_UNCOMPRESSED_BYTES = 256L * 1024 * 1024
	}
}
