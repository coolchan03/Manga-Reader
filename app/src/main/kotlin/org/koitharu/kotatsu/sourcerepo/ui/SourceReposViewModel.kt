package org.koitharu.kotatsu.sourcerepo.ui

import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.call
import org.koitharu.kotatsu.sourcerepo.domain.PluginEntry
import org.koitharu.kotatsu.sourcerepo.domain.RepoPlugin
import org.koitharu.kotatsu.sourcerepo.domain.SourceRepoRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class SourceReposViewModel @Inject constructor(
	private val repository: SourceRepoRepository,
) : BaseViewModel() {

	data class State(
		val repos: List<String>,
		val plugins: List<PluginEntry>,
		val errors: Map<String, Throwable>,
	)

	val state = MutableStateFlow(State(repository.getRepos(), emptyList(), emptyMap()))

	/** A verified APK is ready: the fragment launches the system installer. */
	val onInstall = MutableEventFlow<Intent>()

	/** A string resource to show as a toast. */
	val onMessage = MutableEventFlow<Int>()

	private val pending = ConcurrentHashMap<Long, RepoPlugin>()

	init {
		refresh()
	}

	fun refresh() {
		launchLoadingJob(Dispatchers.Default) {
			val result = repository.fetchAll()
			state.value = State(repository.getRepos(), result.plugins, result.errors)
		}
	}

	/** @return `false` if [url] is not a valid https URL. */
	fun addRepo(url: String): Boolean {
		if (!repository.addRepo(url)) {
			return false
		}
		refresh()
		return true
	}

	fun removeRepo(url: String) {
		repository.removeRepo(url)
		refresh()
	}

	fun install(plugin: RepoPlugin) {
		pending[repository.enqueueDownload(plugin)] = plugin
		onMessage.call(R.string.plugin_download_started)
	}

	fun onDownloadComplete(downloadId: Long) {
		val plugin = pending.remove(downloadId) ?: return
		launchLoadingJob(Dispatchers.Default) {
			val uri = repository.verifiedApk(downloadId, plugin)
			if (uri == null) {
				onMessage.call(R.string.plugin_verify_failed)
				return@launchLoadingJob
			}
			@Suppress("DEPRECATION")
			val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
			intent.setDataAndType(uri, "application/vnd.android.package-archive")
			intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
			onInstall.call(intent)
		}
	}
}
