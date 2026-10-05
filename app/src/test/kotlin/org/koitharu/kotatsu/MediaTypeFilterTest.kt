package org.koitharu.kotatsu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.media.accepts
import org.koitharu.kotatsu.core.media.toMediaType
import org.koitharu.kotatsu.parsers.model.ContentType

class MediaTypeFilterTest {

	@Test
	fun nullAcceptsEverything() {
		MediaType.entries.forEach { assertTrue(null.accepts(it)) }
	}

	@Test
	fun specificTypeAcceptsOnlyItself() {
		assertTrue(MediaType.BOOK.accepts(MediaType.BOOK))
		assertFalse(MediaType.BOOK.accepts(MediaType.MANGA))
	}

	@Test
	fun contentTypesMap() {
		assertEquals(MediaType.BOOK, ContentType.NOVEL.toMediaType())
		ContentType.entries.filter { it != ContentType.NOVEL }.forEach {
			assertEquals(MediaType.MANGA, it.toMediaType())
		}
	}
}
