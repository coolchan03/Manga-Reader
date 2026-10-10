package org.koitharu.kotatsu.sourcerepo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.sourcerepo.domain.PluginState
import org.koitharu.kotatsu.sourcerepo.domain.SourceRepoIndexParser
import org.koitharu.kotatsu.sourcerepo.domain.mergeNewest
import org.koitharu.kotatsu.sourcerepo.domain.stateFor

class SourceRepoIndexParserTest {

	private val hash = "a".repeat(64)

	@Test
	fun parsesBareListAndObjectForms() {
		val entry = """{"name":"A","pkg":"x.a","version":3,"apk":"https://h/a.apk","type":"video"}"""
		val list = SourceRepoIndexParser.parse("[$entry]", "https://r")
		val obj = SourceRepoIndexParser.parse("""{"name":"R","plugins":[$entry]}""", "https://r")
		assertEquals(list, obj)
		assertEquals(MediaType.VIDEO, list.single().mediaType)
		assertEquals("3", list.single().versionName)
	}

	@Test
	fun skipsBadEntriesButKeepsGoodOnes() {
		val text = """[
			{"pkg":"ok","version":1,"apk":"https://h/ok.apk"},
			{"pkg":"http","version":1,"apk":"http://h/insecure.apk"},
			{"pkg":"nohash","version":1,"apk":"https://h/x.apk","sha256":"zzz"},
			{"pkg":"nover","apk":"https://h/x.apk"},
			{"version":1,"apk":"https://h/x.apk"},
			"junk"
		]"""
		assertEquals(listOf("ok"), SourceRepoIndexParser.parse(text, "r").map { it.packageName })
	}

	@Test
	fun normalisesHashAndDefaultsMediaType() {
		val p = SourceRepoIndexParser.parse(
			"""[{"pkg":"p","version":1,"apk":"https://h/p.apk","sha256":"${hash.uppercase()}"}]""", "r",
		).single()
		assertEquals(hash, p.sha256)
		assertEquals(MediaType.MANGA, p.mediaType)
		assertEquals("p", p.name)
	}

	@Test
	fun rejectsGarbage() {
		assertThrows(IllegalArgumentException::class.java) { SourceRepoIndexParser.parse("<html>", "r") }
		assertThrows(IllegalArgumentException::class.java) { SourceRepoIndexParser.parse("""{"x":1}""", "r") }
		assertThrows(IllegalArgumentException::class.java) { SourceRepoIndexParser.parse("42", "r") }
	}

	@Test
	fun stateAndMerge() {
		val a1 = SourceRepoIndexParser.parse("""[{"pkg":"p","version":1,"apk":"https://h/1.apk"}]""", "r1")
		val a2 = SourceRepoIndexParser.parse("""[{"pkg":"p","version":2,"apk":"https://h/2.apk"}]""", "r2")
		val merged = (a1 + a2).mergeNewest().single()
		assertEquals(2L, merged.versionCode)
		assertEquals(PluginState.NOT_INSTALLED, merged.stateFor(null))
		assertEquals(PluginState.UPDATE_AVAILABLE, merged.stateFor(1))
		assertEquals(PluginState.INSTALLED, merged.stateFor(2))
		assertEquals(PluginState.INSTALLED, merged.stateFor(3))
		assertNull(a1.single().sha256)
	}
}

class RepoUrlTest {

	@Test
	fun acceptsOnlyAbsoluteHttps() {
		assertEquals("https://h/repo/index.json", org.koitharu.kotatsu.sourcerepo.domain.normalizeRepoUrl("  https://h/repo/index.json "))
		assertEquals("https://h/b", org.koitharu.kotatsu.sourcerepo.domain.normalizeRepoUrl("https://h/a/../b"))
		for (bad in listOf("http://h/i.json", "ftp://h/i", "h/i.json", "https://", "https://user:pw@h/i", "", "https://h/ bad")) {
			assertNull(bad, org.koitharu.kotatsu.sourcerepo.domain.normalizeRepoUrl(bad))
		}
	}
}
