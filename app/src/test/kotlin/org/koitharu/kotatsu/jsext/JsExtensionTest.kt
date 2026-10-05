package org.koitharu.kotatsu.jsext

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeNoException
import org.junit.Before
import org.junit.Test

/**
 * Runs an unmodified Mangayomi extension through the engine against canned HTTP responses.
 *
 * QuickJS needs its native library. The desktop build is available when this runs on a plain JVM,
 * but the Android artifact on the app's unit-test classpath ships only Android .so files, so the
 * test skips itself there rather than failing.
 */
class JsExtensionTest {

	private val requests = ArrayList<JsHttpRequest>()
	private val logs = ArrayList<String>()

	private val info = JsSourceInfo(
		id = 1L,
		name = "Wordrain69",
		baseUrl = "https://wordrain69.com",
		lang = "en",
		itemType = JsItemType.NOVEL,
	)

	private val pages = mapOf(
		"https://wordrain69.com/manga-genre/novel/page/1/?m_orderby=trending" to "list.html",
		"https://wordrain69.com/?s=rain" to "list.html",
		"https://wordrain69.com/manga/novel-one/" to "detail.html",
		"https://wordrain69.com/manga/novel-one/ajax/chapters/" to "chapters.html",
		"https://wordrain69.com/manga/novel-one/chapter-1/" to "chapter.html",
	)

	private val transport = JsHttpTransport { request ->
		requests += request
		val fixture = pages[request.url]
		if (fixture == null) {
			JsHttpResponse(404, "Not Found", emptyMap(), "", request.url)
		} else {
			JsHttpResponse(200, "OK", mapOf("content-type" to "text/html"), resource("wordrain69/$fixture"), request.url)
		}
	}

	private val store = object : JsPreferenceStore {
		val data = HashMap<String, String>()
		override fun getString(key: String) = data[key]
		override fun setString(key: String, value: String) {
			data[key] = value
		}
	}

	private fun resource(path: String): String =
		checkNotNull(javaClass.getResourceAsStream("/jsext/$path")) { "missing test resource $path" }
			.use { String(it.readBytes()) }

	private fun extension(code: String = resource("wordrain69.js")) =
		JsExtension(info, code, transport, store) { logs += it }

	@Before
	fun requireNativeQuickJs() {
		try {
			com.dokar.quickjs.QuickJs.create(kotlinx.coroutines.Dispatchers.Default).close()
		} catch (e: Throwable) {
			assumeNoException("QuickJS native library is not loadable on this JVM", e)
		}
	}

	@Test
	fun popularListsEntriesAndPaging() = runBlocking {
		extension().use { ext ->
			val result = ext.getPopular(1)
			assertEquals(listOf("Novel One", "Novel Two"), result.list.map { it.name })
			assertEquals("https://wordrain69.com/manga/novel-one/", result.list[0].link)
			assertEquals("https://img.test/one.jpg", result.list[0].imageUrl)
			assertTrue(result.hasNextPage)
		}
	}

	@Test
	fun searchReusesTheListParser() = runBlocking {
		extension().use { ext ->
			assertEquals(2, ext.search("rain", 1).list.size)
		}
	}

	@Test
	fun detailParsesFieldsAndFollowsTheChapterAjaxCall() = runBlocking {
		extension().use { ext ->
			val manga = ext.getDetail("https://wordrain69.com/manga/novel-one/")
			assertEquals("First line. Second line.", manga.description)
			assertEquals("Jane Doe", manga.author)
			assertEquals("John Roe", manga.artist)
			assertEquals(0, manga.status)
			assertEquals(listOf("Romance", "Drama", "Slow Burn"), manga.genre)
			assertEquals("https://img.test/cover.jpg", manga.imageUrl)
			assertEquals(listOf("Chapter 1", "Chapter 2"), manga.chapters.map { it.name })
			assertEquals("https://wordrain69.com/manga/novel-one/chapter-1/", manga.chapters[0].url)
			assertTrue((manga.chapters[0].dateUpload ?: 0L) > 0L)
			val post = requests.single { it.method == "POST" }
			assertEquals("https://wordrain69.com/manga/novel-one/ajax/chapters/", post.url)
			assertEquals("https://wordrain69.com", post.headers["Origin"])
		}
	}

	@Test
	fun htmlContentForNovelChapters() = runBlocking {
		extension().use { ext ->
			val html = ext.getHtmlContent("Chapter 1", "https://wordrain69.com/manga/novel-one/chapter-1/")
			assertEquals("<h2>Chapter 1</h2><hr><br><p>Hello <b>world</b>.</p>", html)
		}
	}

	@Test
	fun optionalMembersFallBackLikeMangayomi() = runBlocking {
		extension().use { ext ->
			// wordrain69 throws from getHeaders and does not declare supportsLatest
			assertEquals(emptyMap<String, String>(), ext.getHeaders())
			assertTrue(ext.supportsLatest())
		}
	}

	@Test
	fun brokenSourceReportsWhyInsteadOfLookingEmpty() = runBlocking {
		extension("class DefaultExtension extends MProvider { constructor() { throw new Error('boom'); } }").use { ext ->
			try {
				ext.getPopular(1)
				fail("expected an exception")
			} catch (e: JsExtensionException) {
				assertTrue(e.message.orEmpty().contains("boom"))
			}
		}
	}

	@Test
	fun logsReachTheHost() = runBlocking {
		extension("class DefaultExtension extends MProvider { async getPopular(p) { console.log('hi ' + p); return {list: [], hasNextPage: false}; } }")
			.use { ext ->
				ext.getPopular(3)
				assertEquals(listOf("hi 3"), logs)
			}
	}
}
