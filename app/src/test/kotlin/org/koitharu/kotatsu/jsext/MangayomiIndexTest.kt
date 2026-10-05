package org.koitharu.kotatsu.jsext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koitharu.kotatsu.jsext.repo.JsSourceRegistry
import org.koitharu.kotatsu.jsext.repo.JsSourceStore
import org.koitharu.kotatsu.jsext.source.JsMangaSource
import org.koitharu.kotatsu.jsext.repo.MangayomiIndexParser
import org.koitharu.kotatsu.jsext.repo.SourceCodeLanguage
import org.koitharu.kotatsu.jsext.repo.compareVersions

/** Uses a trimmed slice of Mangayomi's real index.json / novel_index.json. */
class MangayomiIndexTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private val sample: String =
		checkNotNull(javaClass.getResourceAsStream("/jsext/mangayomi-index-sample.json")).use { String(it.readBytes()) }

	private val entries = MangayomiIndexParser.parse(sample, "https://repo.test/index.json")

	@Test
	fun parsesRealIndexEntries() {
		assertEquals(6, entries.size)
		assertEquals(2, entries.count { it.language == SourceCodeLanguage.DART })
		assertEquals(4, entries.count { it.isRunnable })
		val novel = entries.single { it.name == "Annas Archive" }
		assertEquals(JsItemType.NOVEL, novel.itemType)
		assertTrue(novel.sourceCodeUrl.endsWith("annasarchive.js"))
		assertEquals("https://repo.test/index.json", novel.repoUrl)
		assertEquals(JsItemType.MANGA, entries.first().itemType)
	}

	@Test
	fun skipsEntriesThatAreNotMangayomiSources() {
		val text = """[
			{"name":"apk style","pkgName":"x.y","version":"1.0"},
			{"id":1,"name":"no code url","version":"1"},
			{"id":2,"name":"http code","sourceCodeUrl":"http://h/x.js","version":"1"},
			{"id":3,"name":"ok","sourceCodeUrl":"https://h/x.js","version":"1","itemType":1}
		]"""
		val parsed = MangayomiIndexParser.parse(text, "r")
		assertEquals(listOf("ok"), parsed.map { it.name })
		assertEquals(SourceCodeLanguage.JAVASCRIPT, parsed.single().language) // inferred from .js
		assertEquals(JsItemType.ANIME, parsed.single().itemType)
	}

	@Test
	fun detectsAndRejects() {
		assertTrue(MangayomiIndexParser.looksLikeMangayomiIndex(sample))
		assertFalse(MangayomiIndexParser.looksLikeMangayomiIndex("""{"plugins":[]}"""))
		assertFalse(MangayomiIndexParser.looksLikeMangayomiIndex("not json"))
		assertThrows(IllegalArgumentException::class.java) { MangayomiIndexParser.parse("""{"a":1}""", "r") }
	}

	@Test
	fun versionComparisonIsNumeric() {
		assertTrue(compareVersions("0.1.10", "0.1.9") > 0)
		assertTrue(compareVersions("1.0", "1.0.0") == 0)
		assertTrue(compareVersions("0.0.35", "0.1.0") < 0)
		assertTrue(compareVersions("abc", "0") == 0)
	}

	@Test
	fun registryAndChangeCounterFollowTheStore() {
		val store = JsSourceStore(tmp.newFolder("js2"))
		val entry = entries.single { it.name == "Annas Archive" }
		val before = store.changes.value
		assertNull(JsSourceRegistry.get(entry.id))
		store.install(entry, "x")
		assertEquals(entry.name, JsSourceRegistry.get(entry.id)?.name)
		assertEquals(JsItemType.NOVEL, JsSourceRegistry.get(entry.id)?.itemType)
		assertEquals(before + 1, store.changes.value)
		store.uninstall(entry.id)
		assertNull(JsSourceRegistry.get(entry.id))
		assertEquals(before + 2, store.changes.value)
		assertEquals(entry.id, JsMangaSource.fromName("js:${entry.id}")?.id)
		assertNull(JsMangaSource.fromName("js:abc"))
		assertNull(JsMangaSource.fromName("MANGADEX"))
	}

	@Test
	fun storeRoundTripsAndOnlyListsCompleteInstalls() {
		val store = JsSourceStore(tmp.newFolder("js"))
		val entry = entries.single { it.name == "Annas Archive" }
		assertNull(store.get(entry.id))
		store.install(entry, "class DefaultExtension extends MProvider {}")
		val got = checkNotNull(store.get(entry.id))
		assertEquals(entry.name, got.entry.name)
		assertEquals(entry.version, store.installedVersion(entry.id))
		assertEquals("class DefaultExtension extends MProvider {}", got.code)
		assertEquals(JsItemType.NOVEL, got.entry.itemType)
		assertEquals(listOf(entry.id), store.list().map { it.id })
		store.uninstall(entry.id)
		assertTrue(store.list().isEmpty())
	}
}
