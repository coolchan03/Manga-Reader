package org.koitharu.kotatsu.jsext

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class JsEpubBridgeTest {

	@Test
	fun parsesEpubAndCachesTheDownloadedBook() = runBlocking {
		val epub = makeEpub()
		var requests = 0
		val transport = JsHttpTransport { request ->
			requests++
			assertEquals("https://books.test/demo.epub", request.url)
			assertEquals("Bearer test", request.headers["Authorization"])
			JsHttpResponse(
				statusCode = 200,
				reasonPhrase = "OK",
				headers = mapOf("content-type" to "application/epub+zip"),
				body = "",
				finalUrl = request.url,
				bodyBytes = epub,
			)
		}
		val bridge = JsEpubBridge(transport)
		val baseArgs = JsonArray(
			listOf(
				JsonPrimitive("Demo Book"),
				JsonPrimitive("https://books.test/demo.epub"),
				JsonObject(mapOf("Authorization" to JsonPrimitive("Bearer test"))),
			),
		)

		val metadata = Json.parseToJsonElement(bridge.handle("parseEpub", baseArgs)).jsonObject
		assertEquals("Fixture Book", metadata["title"].str())
		assertEquals("Alex Author", metadata["author"].str())
		assertEquals(
			listOf("Opening", "The End"),
			(metadata["chapters"] as JsonArray).map { it.str() },
		)

		val chapterArgs = JsonArray(baseArgs + JsonPrimitive("The End"))
		val chapter = bridge.handle("parseEpubChapter", chapterArgs)
		assertTrue(chapter.contains("Goodbye"))
		assertEquals(1, requests)
	}

	@Test
	fun rejectsMalformedArchivesClearly() {
		val bridge = JsEpubBridge(
			JsHttpTransport { error("network is not used by parse()") },
		)
		try {
			bridge.parse("not a zip".toByteArray())
			throw AssertionError("Expected a JsExtensionException")
		} catch (e: JsExtensionException) {
			assertTrue(e.message.orEmpty().contains("container.xml"))
		}
	}

	private fun makeEpub(): ByteArray {
		val out = ByteArrayOutputStream()
		ZipOutputStream(out).use { zip ->
			fun entry(path: String, text: String) {
				zip.putNextEntry(ZipEntry(path))
				zip.write(text.toByteArray())
				zip.closeEntry()
			}

			entry(
				"META-INF/container.xml",
				"""
				<?xml version="1.0"?>
				<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
				  <rootfiles>
				    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
				  </rootfiles>
				</container>
				""".trimIndent(),
			)
			entry(
				"OEBPS/content.opf",
				"""
				<?xml version="1.0" encoding="UTF-8"?>
				<package xmlns="http://www.idpf.org/2007/opf" version="3.0">
				  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
				    <dc:title>Fixture Book</dc:title>
				    <dc:creator>Alex Author</dc:creator>
				  </metadata>
				  <manifest>
				    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
				    <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
				    <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
				  </manifest>
				  <spine>
				    <itemref idref="c1"/>
				    <itemref idref="c2"/>
				  </spine>
				</package>
				""".trimIndent(),
			)
			entry(
				"OEBPS/nav.xhtml",
				"""
				<html xmlns="http://www.w3.org/1999/xhtml">
				  <body><nav><ol>
				    <li><a href="text/ch1.xhtml#start">Opening</a></li>
				    <li><a href="text/ch2.xhtml">The End</a></li>
				  </ol></nav></body>
				</html>
				""".trimIndent(),
			)
			entry("OEBPS/text/ch1.xhtml", "<html><body><p>Hello</p></body></html>")
			entry("OEBPS/text/ch2.xhtml", "<html><body><p>Goodbye</p></body></html>")
		}
		return out.toByteArray()
	}
}
