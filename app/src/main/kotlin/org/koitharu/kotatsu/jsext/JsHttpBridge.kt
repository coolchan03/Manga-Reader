package org.koitharu.kotatsu.jsext

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder

/**
 * Implements the `http_*` calls of Mangayomi's JS runtime (eval/javascript/http.dart).
 *
 * Args from the shim are `[null, clientOptions, url, headers, body?]`.
 */
internal class JsHttpBridge(private val transport: JsHttpTransport) {

	suspend fun handle(method: String, args: JsonArray): String {
		val options = args.getOrNull(1) as? JsonObject
		val url = args[2].str() ?: throw JsExtensionException("http: missing url")
		val headers = args.getOrNull(3).stringMap().orEmpty().toMutableMap()
		val body = args.getOrNull(4)
		val hasBody = method != "GET" && method != "HEAD"

		var bytes: ByteArray? = null
		if (hasBody && body != null && body !is JsonNull) {
			val contentTypeKey = headers.keys.firstOrNull { it.equals("content-type", ignoreCase = true) }
			val isJson = contentTypeKey != null && headers[contentTypeKey]!!.contains("application/json", true)
			bytes = when {
				body is JsonPrimitive && body.isString -> body.content.toByteArray()
				isJson -> body.toString().toByteArray()
				body is JsonObject -> {
					if (contentTypeKey == null) {
						headers["content-type"] = "application/x-www-form-urlencoded; charset=utf-8"
					}
					body.entries.joinToString("&") { (k, v) ->
						URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v.str().orEmpty(), "UTF-8")
					}.toByteArray()
				}

				body is JsonArray -> ByteArray(body.size) { i -> (body[i].str()?.toIntOrNull() ?: 0).toByte() }
				else -> body.toString().toByteArray()
			}
		}
		val response = transport.execute(JsHttpRequest(method, url, headers, bytes, options))
		return responseToJson(response, method, url, headers).toString()
	}

	private fun responseToJson(
		response: JsHttpResponse,
		method: String,
		url: String,
		requestHeaders: Map<String, String>,
	): JsonElement = buildJsonObject {
		put("body", response.body)
		put("headers", JsonObject(response.headers.mapValues { JsonPrimitive(it.value) }))
		put("isRedirect", response.isRedirect)
		put("persistentConnection", true)
		put("reasonPhrase", response.reasonPhrase)
		put("statusCode", response.statusCode)
		put(
			"request",
			buildJsonObject {
				put("url", response.finalUrl.ifEmpty { url })
				put("method", method)
				put("headers", JsonObject(requestHeaders.mapValues { JsonPrimitive(it.value) }))
				put("followRedirects", true)
				put("maxRedirects", 5)
				put("persistentConnection", true)
			},
		)
	}

	companion object {

		val METHODS = mapOf(
			"http_head" to "HEAD",
			"http_get" to "GET",
			"http_post" to "POST",
			"http_put" to "PUT",
			"http_delete" to "DELETE",
			"http_patch" to "PATCH",
		)

		val json = Json { ignoreUnknownKeys = true }
	}
}
