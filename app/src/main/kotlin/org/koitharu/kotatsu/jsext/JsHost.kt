package org.koitharu.kotatsu.jsext

import kotlinx.serialization.json.JsonObject

/** One outgoing request from a JS extension, already normalised (see [JsHttpBridge]). */
class JsHttpRequest(
	val method: String,
	val url: String,
	val headers: Map<String, String>,
	val body: ByteArray?,
	/** The `Client(options)` object the extension created, e.g. redirect behaviour. */
	val options: JsonObject?,
)

class JsHttpResponse(
	val statusCode: Int,
	val reasonPhrase: String?,
	/** Lower-cased names, like Dart's `http` package. */
	val headers: Map<String, String>,
	val body: String,
	val finalUrl: String,
	val isRedirect: Boolean = false,
)

/** The network. The app backs this with its OkHttp client; tests use a fake. */
fun interface JsHttpTransport {

	suspend fun execute(request: JsHttpRequest): JsHttpResponse
}

/** Per-source key/value storage behind `SharedPreferences` in extension code. */
interface JsPreferenceStore {

	fun getString(key: String): String?

	fun setString(key: String, value: String)
}

fun interface JsLogger {

	fun log(message: String)
}

class JsExtensionException(message: String, cause: Throwable? = null) : Exception(message, cause)
