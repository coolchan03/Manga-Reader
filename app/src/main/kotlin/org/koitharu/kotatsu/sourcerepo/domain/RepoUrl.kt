package org.koitharu.kotatsu.sourcerepo.domain

import java.net.URI

/**
 * Normalises a user-typed repo index URL. Returns `null` unless it is an absolute https URL with a
 * host; plaintext http is refused because the index decides what gets installed.
 */
fun normalizeRepoUrl(input: String): String? {
	val trimmed = input.trim()
	if (!trimmed.startsWith("https://", ignoreCase = true)) {
		return null
	}
	val uri = try {
		URI(trimmed)
	} catch (e: Exception) {
		return null
	}
	if (uri.host.isNullOrEmpty() || uri.userInfo != null) {
		return null
	}
	return uri.normalize().toString()
}
