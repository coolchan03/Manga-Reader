package org.koitharu.kotatsu.sourcerepo.domain

import org.koitharu.kotatsu.core.media.MediaType

/** One installable source plugin as advertised by a repo index. */
data class RepoPlugin(
	val name: String,
	val packageName: String,
	val versionCode: Long,
	val versionName: String,
	val apkUrl: String,
	val mediaType: MediaType,
	val sha256: String?,
	val repoUrl: String,
)

enum class PluginState {

	NOT_INSTALLED,
	INSTALLED,
	UPDATE_AVAILABLE,
}

/** [installedVersionCode] is `null` when the package is not installed. */
fun RepoPlugin.stateFor(installedVersionCode: Long?): PluginState = when {
	installedVersionCode == null -> PluginState.NOT_INSTALLED
	versionCode > installedVersionCode -> PluginState.UPDATE_AVAILABLE
	else -> PluginState.INSTALLED
}
