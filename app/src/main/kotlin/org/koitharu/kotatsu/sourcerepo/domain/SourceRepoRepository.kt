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
import org.koitharu.kotatsu.sourcerepo.data.SourceRepoStore
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class PluginEntry(
	val plugin: RepoPlugin,
	val state: PluginState,
)

data class RepoFetchResult(
	val plugins: List<PluginEntry>,
	/** Repo URL -> failure, so one dead repo does not hide the others. */
	val errors: Map<String, Throwable>,
)

@Singleton
class SourceRepoRepository @Inject constructor(
	@ApplicationContext private val context: Context,
	@BaseHttpClient private val client: OkHttpClient,
	private val store: SourceRepoStore,
) {

	fun getRepos(): List<String> = store.getAll()

	fun addRepo(url: String): Boolean = store.add(url)

	fun removeRepo(url: String) = store.remove(url)

	suspend fun fetchAll(): RepoFetchResult = withContext(Dispatchers.IO) {
		val plugins = ArrayList<RepoPlugin>()
		val errors = LinkedHashMap<String, Throwable>()
		for (url in store.getAll()) {
			runCatchingCancellable { fetchIndex(url) }
				.onSuccess { plugins += it }
				.onFailure { errors[url] = it }
		}
		RepoFetchResult(
			plugins = plugins.mergeNewest().map { PluginEntry(it, it.stateFor(installedVersionCode(it.packageName))) },
			errors = errors,
		)
	}

	/** Number of installed plugins that have a newer version in some repo. Used for badges. */
	suspend fun countUpdates(): Int = fetchAll().plugins.count { it.state == PluginState.UPDATE_AVAILABLE }

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

	private suspend fun fetchIndex(url: String): List<RepoPlugin> {
		val request = Request.Builder().get().url(url).build()
		val text = client.newCall(request).await().use { response ->
			if (!response.isSuccessful) {
				throw java.io.IOException("HTTP ${response.code} from $url")
			}
			response.body.string()
		}
		return SourceRepoIndexParser.parse(text, url)
	}

	@Suppress("DEPRECATION")
	private fun installedVersionCode(packageName: String): Long? = try {
		context.packageManager.getPackageInfo(packageName, 0).longVersionCode
	} catch (e: PackageManager.NameNotFoundException) {
		null
	}
}
