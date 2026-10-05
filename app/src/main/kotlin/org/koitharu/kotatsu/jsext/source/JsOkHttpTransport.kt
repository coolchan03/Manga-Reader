package org.koitharu.kotatsu.jsext.source

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.koitharu.kotatsu.jsext.JsHttpRequest
import org.koitharu.kotatsu.jsext.JsHttpResponse
import org.koitharu.kotatsu.jsext.JsHttpTransport
import org.koitharu.kotatsu.jsext.bool
import org.koitharu.kotatsu.parsers.util.await

/** Sends a JS extension's requests through the app's scraping client (cookies, Cloudflare, rate limits). */
class JsOkHttpTransport(private val client: OkHttpClient) : JsHttpTransport {

	override suspend fun execute(request: JsHttpRequest): JsHttpResponse {
		val effectiveClient = if (request.options?.get("followRedirects").bool() == false) {
			client.newBuilder().followRedirects(false).followSslRedirects(false).build()
		} else {
			client
		}
		val builder = Request.Builder().url(request.url)
		request.headers.forEach { (name, value) -> builder.header(name, value) }
		val contentType = request.headers.entries
			.firstOrNull { it.key.equals("content-type", ignoreCase = true) }?.value?.toMediaTypeOrNull()
		val needsBody = request.method == "POST" || request.method == "PUT" || request.method == "PATCH"
		val body = when {
			request.body != null -> request.body.toRequestBody(contentType)
			needsBody -> ByteArray(0).toRequestBody(contentType)
			else -> null
		}
		builder.method(request.method, body)
		return effectiveClient.newCall(builder.build()).await().use { response ->
			JsHttpResponse(
				statusCode = response.code,
				reasonPhrase = response.message,
				headers = response.headers.names().associate { name ->
					name.lowercase() to response.headers.values(name).joinToString(", ")
				},
				body = if (request.method == "HEAD") "" else response.body.string(),
				finalUrl = response.request.url.toString(),
				isRedirect = response.isRedirect,
			)
		}
	}
}
