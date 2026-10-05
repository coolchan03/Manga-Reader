package org.koitharu.kotatsu.sourcerepo

import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.sourcerepo.domain.SourceRepoIndexParser
import java.io.File

/** Keeps docs/sample-repo/index.json valid, since docs/SOURCE_REPOS.md points people at it. */
class SampleRepoTest {

	@Test
	fun sampleIndexParses() {
		val file = listOf("../docs/sample-repo/index.json", "docs/sample-repo/index.json")
			.map(::File).first { it.exists() }
		val plugins = SourceRepoIndexParser.parse(file.readText(), "https://example.com/index.json")
		assertEquals(2, plugins.size)
		assertEquals(MediaType.MANGA, plugins[0].mediaType)
		assertEquals(MediaType.BOOK, plugins[1].mediaType)
	}
}
