package org.koitharu.kotatsu.jsext.source

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.core.network.MangaHttpClient
import org.koitharu.kotatsu.jsext.JsExtension
import org.koitharu.kotatsu.jsext.JsPreferenceStore
import org.koitharu.kotatsu.jsext.repo.JsSourceEntry
import org.koitharu.kotatsu.jsext.repo.JsSourceStore
import javax.inject.Inject
import javax.inject.Singleton

/** One live [JsExtension] per installed source, recreated when the source is updated or removed. */
@Singleton
class JsExtensionProvider @Inject constructor(
	@ApplicationContext private val context: Context,
	@MangaHttpClient private val client: OkHttpClient,
	private val store: JsSourceStore,
) {

	private class Live(val entry: JsSourceEntry, val codeHash: Int, val extension: JsExtension)

	private val mutex = Mutex()
	private val live = HashMap<Long, Live>()

	private fun prefs(id: Long) = context.getSharedPreferences("js_source_$id", Context.MODE_PRIVATE)

	/** Preferences declared by the installed Mangayomi source. Most sources declare none. */
	suspend fun getSourcePreferences(id: Long): List<JsonObject> = runCatching {
		(get(id).getSourcePreferences() as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
	}.getOrDefault(emptyList())

	fun getPreference(id: Long, key: String): String? = prefs(id).getString(key, null)

	suspend fun setPreference(id: Long, key: String, value: String?) {
		prefs(id).edit {
			if (value == null) remove(key) else putString(key, value)
		}
		// Some extensions derive state from preferences during construction. Recreate the live
		// QuickJS runtime after a settings change so those sources observe the new value reliably.
		mutex.withLock {
			live.remove(id)?.extension?.close()
		}
	}

	suspend fun get(id: Long): JsExtension = mutex.withLock {
		val installed = store.get(id)
		if (installed == null) {
			live.remove(id)?.extension?.close()
			throw IllegalStateException("JS source $id is not installed")
		}
		val codeHash = installed.code.hashCode()
		live[id]?.takeIf { it.entry == installed.entry && it.codeHash == codeHash }?.let { return it.extension }
		live.remove(id)?.extension?.close()
		val prefs = prefs(id)
		val extension = JsExtension(
			info = installed.entry.toSourceInfo(),
			code = installed.code,
			http = JsOkHttpTransport(client, JsMangaSource(id)),
			preferences = object : JsPreferenceStore {
				override fun getString(key: String): String? = prefs.getString(key, null)
				override fun setString(key: String, value: String) = prefs.edit { putString(key, value) }
			},
			logger = { message -> if (BuildConfig.DEBUG) Log.d("JsExtension", message) },
		)
		live[id] = Live(installed.entry, codeHash, extension)
		extension
	}
}
