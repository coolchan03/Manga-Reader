package org.koitharu.kotatsu.sourcerepo.domain

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.koitharu.kotatsu.parsers.util.await
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.jsext.repo.JsSourceEntry
import org.koitharu.kotatsu.jsext.repo.JsSourceStore
import org.koitharu.kotatsu.jsext.repo.MangayomiIndexParser
import org.koitharu.kotatsu.jsext.repo.compareVersions
import org.koitharu.kotatsu.sourcerepo.data.SourceRepoStore
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class PluginEntry(
	val plugin: RepoPlugin,
	val state: PluginState,
)

/** A Mangayomi JS source and whether it is installed here. */
data class JsSourceItem(
	val entry: JsSourceEntry,
	val state: PluginState,
)

data class RepoFetchResult(
	val plugins: List<PluginEntry>,
	val jsSources: List<JsSourceItem>,
	/** Dart sources found in Mangayomi-format repos; they need Mangayomi's own runtime. */
	val skippedDartCount: Int,
	/** Repo URL -> failure, so one dead repo does not hide the others. */
	val errors: Map<String, Throwable>,
)

private const val MAX_SOURCE_BYTES = 2L * 1024 * 1024

@Singleton
class SourceRepoRepository @Inject constructor(
	@ApplicationContext private val context: Context,
	@BaseHttpClient private val client: OkHttpClient,
	private val store: SourceRepoStore,
	private val jsStore: JsSourceStore,
) {

	fun getRepos(): List<String> = store.getAll()

	fun addRepo(url: String): Boolean = store.add(url)

	fun removeRepo(url: String) = store.remove(url)

	suspend fun fetchAll(): RepoFetchResult = withContext(Dispatchers.IO) {
		val plugins = ArrayList<RepoPlugin>()
		val jsEntries = ArrayList<JsSourceEntry>()
		val errors = LinkedHashMap<String, Throwable>()
		for (url in store.getAll()) {
			runCatchingCancellable { fetchIndex(url) }
				.onSuccess {
					plugins += it.plugins
					jsEntries += it.jsSources
				}
				.onFailure { errors[url] = it }
		}
		val newestJs = jsEntries.groupBy { it.id }
			.map { (_, group) -> group.reduce { a, b -> if (compareVersions(b.version, a.version) > 0) b else a } }
		val runnable = newestJs.filter { it.isRunnable }.sortedBy { it.name.lowercase() }
		RepoFetchResult(
			plugins = plugins.mergeNewest().map { PluginEntry(it, it.stateFor(installedVersionCode(it.packageName))) },
			jsSources = runnable.map { JsSourceItem(it, jsStateOf(it)) },
			skippedDartCount = newestJs.size - runnable.size,
			errors = errors,
		)
	}

	/** Number of installed sources (APK plugins and JS) with a newer version in some repo. For badges. */
	suspend fun countUpdates(): Int = fetchAll().let { result ->
		result.plugins.count { it.state == PluginState.UPDATE_AVAILABLE } +
			result.jsSources.count { it.state == PluginState.UPDATE_AVAILABLE }
	}

	/**
	 * Queues the APK download. Verification and the install prompt happen in [verifiedApk] once the
	 * download finishes, so a tampered file never reaches the system installer.
	 */
	fun enqueueDownload(plugin: RepoPlugin): Long {
		val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
		val uri = plugin.apkUrl.toUri()
		val request = DownloadManager.Request(uri)
			.setTitle(plugin.name)
			.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${plugin.packageName}-${plugin.versionCode}.apk")
			.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
			.setMimeType("application/vnd.android.package-archive")
		return dm.enqueue(request)
	}

	/**
	 * @return the downloaded file's content [android.net.Uri] if it matches [plugin]'s sha256 (or the
	 * index gave none), otherwise `null`.
	 */
	suspend fun verifiedApk(downloadId: Long, plugin: RepoPlugin): android.net.Uri? = withContext(Dispatchers.IO) {
		val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
		val uri = dm.getUriForDownloadedFile(downloadId) ?: return@withContext null
		val expected = plugin.sha256 ?: return@withContext uri
		val digest = MessageDigest.getInstance("SHA-256")
		context.contentResolver.openInputStream(uri)?.use { input ->
			val buffer = ByteArray(64 * 1024)
			while (true) {
				val n = input.read(buffer)
				if (n < 0) break
				digest.update(buffer, 0, n)
			}
		} ?: return@withContext null
		val actual = digest.digest().joinToString("") { "%02x".format(it) }
		if (actual == expected) uri else null
	}

	private class FetchedIndex(val plugins: List<RepoPlugin>, val jsSources: List<JsSourceEntry>)

	private fun jsStateOf(entry: JsSourceEntry): PluginState {
		val installed = jsStore.installedVersion(entry.id) ?: return PluginState.NOT_INSTALLED
		return if (compareVersions(entry.version, installed) > 0) PluginState.UPDATE_AVAILABLE else PluginState.INSTALLED
	}

	/** Downloads a JS source and stores it. The code only ever runs sandboxed inside QuickJS. */
	suspend fun installJsSource(entry: JsSourceEntry) = withContext(Dispatchers.IO) {
		val request = Request.Builder().get().url(entry.sourceCodeUrl).build()
		val code = client.newCall(request).await().use { response ->
			if (!response.isSuccessful) {
				throw java.io.IOException("HTTP ${response.code} from ${entry.sourceCodeUrl}")
			}
			val body = response.body
			if (body.contentLength() > MAX_SOURCE_BYTES) {
				throw java.io.IOException("Source is unexpectedly large")
			}
			body.string()
		}
		if (code.length > MAX_SOURCE_BYTES || !code.contains("DefaultExtension")) {
			throw java.io.IOException("Not a Mangayomi JavaScript source: ${entry.name}")
		}
		jsStore.install(entry, code)
	}

	fun uninstallJsSource(entry: JsSourceEntry) = jsStore.uninstall(entry.id)

	private suspend fun fetchIndex(url: String): FetchedIndex {
		val request = Request.Builder().get().url(url).build()
		val text = client.newCall(request).await().use { response ->
			if (!response.isSuccessful) {
				throw java.io.IOException("HTTP ${response.code} from $url")
			}
			response.body.string()
		}
		return if (MangayomiIndexParser.looksLikeMangayomiIndex(text)) {
			FetchedIndex(emptyList(), MangayomiIndexParser.parse(text, url))
		} else {
			FetchedIndex(SourceRepoIndexParser.parse(text, url), emptyList())
		}
	}

	@Suppress("DEPRECATION")
	private fun installedVersionCode(packageName: String): Long? = try {
		context.packageManager.getPackageInfo(packageName, 0).longVersionCode
	} catch (e: PackageManager.NameNotFoundException) {
		null
	}
}
