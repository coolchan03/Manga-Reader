package org.koitharu.kotatsu.jsext

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

/**
 * Runs one unmodified Mangayomi JavaScript extension.
 *
 * The extension is the usual `class DefaultExtension extends MProvider { ... }`. Its environment
 * (`Client`, `Document`, `SharedPreferences`, `MProvider`, ...) comes from the JS shims under
 * resources/jsext, copied from Mangayomi, and the native half of those shims is implemented by the
 * `Js*Bridge` classes. Calls into the extension are serialised: a QuickJS context is single-threaded.
 */
class JsExtension(
	val info: JsSourceInfo,
	private val code: String,
	http: JsHttpTransport,
	private val preferences: JsPreferenceStore,
	logger: JsLogger = JsLogger { },
) : AutoCloseable {

	private val httpBridge = JsHttpBridge(http)
	private val dom = JsDomBridge()
	private val utils = JsUtilsBridge(logger)
	private val mutex = Mutex()
	private var runtime: QuickJs? = null

	/** Preference key -> declared preference object, read once from `getSourcePreferences()`. */
	private var declaredPreferences: Map<String, JsonObject> = emptyMap()

	// --- the extension contract (docs: Mangayomi's CONTRIBUTING-JS.md) ---

	suspend fun getPopular(page: Int): JsPages = callObject("getPopular($page)").toJsPages()

	suspend fun getLatestUpdates(page: Int): JsPages = callObject("getLatestUpdates($page)").toJsPages()

	suspend fun search(query: String, page: Int, filters: JsonArray = JsonArray(emptyList())): JsPages =
		callObject("search(${lit(query)}, $page, $filters)").toJsPages()

	suspend fun getDetail(url: String): JsManga = callObject("getDetail(${lit(url)})").toJsManga()

	suspend fun getPageList(url: String): List<JsPage> = call("getPageList(${lit(url)})").jsonArray.mapNotNull { e ->
		when {
			e is JsonObject -> e["url"].str()?.trim()?.let { JsPage(it, e["headers"].stringMap()) }
			e is JsonNull -> null
			else -> e.str()?.trim()?.let { JsPage(it, null) }
		}
	}.distinctBy { it.url }

	suspend fun getVideoList(url: String): List<JsVideo> = call("getVideoList(${lit(url)})").jsonArray
		.mapNotNull { (it as? JsonObject)?.toJsVideo() }
		.distinctBy { it.url to it.originalUrl }

	/** Novel chapter body as HTML. */
	suspend fun getHtmlContent(name: String, url: String): String =
		call("getHtmlContent(${lit(name)}, ${lit(url)})").str().orEmpty()

	suspend fun cleanHtmlContent(html: String): String = call("cleanHtmlContent(${lit(html)})").str().orEmpty()

	suspend fun getFilterList(): JsonElement = call("getFilterList()")

	suspend fun supportsLatest(): Boolean = runCatching { call("supportsLatest").bool() }.getOrNull() ?: true

	suspend fun getHeaders(): Map<String, String> =
		runCatching { call("getHeaders(${lit(info.baseUrl)})").stringMap() }.getOrNull().orEmpty()

	suspend fun getSourcePreferences(): JsonElement = call("getSourcePreferences()")

	override fun close() {
		runtime?.close()
		runtime = null
	}

	// --- plumbing ---

	private suspend fun callObject(expression: String): JsonObject =
		call(expression) as? JsonObject ?: throw JsExtensionException("${info.name}: $expression did not return an object")

	private suspend fun call(member: String): JsonElement = mutex.withLock {
		val qjs = ensureInitialized()
		dom.reset()
		val text = try {
			withTimeout(CALL_TIMEOUT_MS) {
				qjs.evaluate<String?>("await jsonStringify(() => extention.$member)")
			}
		} catch (e: kotlinx.coroutines.TimeoutCancellationException) {
			throw JsExtensionException("${info.name}: ${member.substringBefore('(')} timed out", e)
		} catch (e: kotlinx.coroutines.CancellationException) {
			throw e
		} catch (e: Exception) {
			throw JsExtensionException("${info.name}: ${member.substringBefore('(')} failed: ${e.message}", e)
		}
		if (text.isNullOrEmpty()) JsonNull else Json.parseToJsonElement(text)
	}

	private suspend fun ensureInitialized(): QuickJs {
		runtime?.let { return it }
		val qjs = QuickJs.create(Dispatchers.Default)
		try {
			qjs.memoryLimit = MEMORY_LIMIT
			qjs.maxStackSize = STACK_LIMIT
			qjs.function("__hostSync") { args -> dispatchSync(args[0] as String, args[1] as String) }
			qjs.asyncFunction("__hostAsync") { args -> dispatchAsync(args[0] as String, args[1] as String) }
			qjs.evaluate<Any?>(PRELUDE.replace("__ASYNC_NAMES__", ASYNC_NAMES.joinToString(",") { "\"$it\"" }), "prelude.js")
			for (shim in SHIMS) {
				var script = readResource(shim)
				if (shim == "mprovider.js") {
					script = script.replace("__SOURCE_JSON__", info.toJson().toString())
				}
				qjs.evaluate<Any?>(script, shim)
			}
			qjs.evaluate<Any?>("$code\nvar extention = new DefaultExtension();", "${info.name}.js")
			runtime = qjs
			declaredPreferences = loadDeclaredPreferences(qjs)
		} catch (e: Exception) {
			qjs.close()
			// A source that fails to evaluate must not look "empty": surface why.
			throw JsExtensionException("${info.name}: failed to load source: ${e.message}", e)
		}
		return qjs
	}

	private suspend fun loadDeclaredPreferences(qjs: QuickJs): Map<String, JsonObject> = try {
		val text = qjs.evaluate<String?>("await jsonStringify(() => extention.getSourcePreferences())")
		(Json.parseToJsonElement(text.orEmpty()) as? JsonArray).orEmpty()
			.mapNotNull { it as? JsonObject }
			.mapNotNull { pref -> pref["key"].str()?.let { it to pref } }
			.toMap()
	} catch (e: Exception) {
		emptyMap() // most sources do not declare preferences, and getSourcePreferences throws by default
	}

	private fun dispatchSync(name: String, argsJson: String): Any? {
		val a = Json.parseToJsonElement(argsJson).jsonArray
		dom.handle(name, a).let { if (it !== Unit) return it }
		utils.handle(name, a).let { if (it !== Unit) return it }
		return when (name) {
			"get" -> preferenceValue(a[0].str().orEmpty())
			"getString" -> preferences.getString(a[0].str().orEmpty()) ?: a.getOrNull(1).str().orEmpty()
			"setString" -> {
				preferences.setString(a[0].str().orEmpty(), a.getOrNull(1).str().orEmpty())
				""
			}

			else -> throw JsExtensionException("Unknown host call: $name")
		}
	}

	private suspend fun dispatchAsync(name: String, argsJson: String): Any? {
		val a = Json.parseToJsonElement(argsJson).jsonArray
		JsHttpBridge.METHODS[name]?.let { return httpBridge.handle(it, a) }
		throw JsExtensionException("Host call '$name' is not supported yet")
	}

	/** `SharedPreferences.get`: the user's value if set, else the default the source declared. */
	private fun preferenceValue(key: String): Any? {
		val declared = declaredPreferences[key] ?: throw JsExtensionException("Source preference '$key' is not declared")
		val stored = preferences.getString(key)
		declared["listPreference"]?.let { list ->
			val values = (list as? JsonObject)?.get("entryValues") as? JsonArray
			val index = (list as? JsonObject)?.get("valueIndex").long()?.toInt() ?: 0
			return stored ?: values?.getOrNull(index).str()
		}
		for (booleanKind in listOf("switchPreferenceCompat", "checkBoxPreference")) {
			(declared[booleanKind] as? JsonObject)?.let { return stored?.toBooleanStrictOrNull() ?: it["value"].bool() ?: false }
		}
		(declared["editTextPreference"] as? JsonObject)?.let { return stored ?: it["value"].str().orEmpty() }
		(declared["multiSelectListPreference"] as? JsonObject)?.let { multi ->
			val values = stored?.let { runCatching { Json.parseToJsonElement(it) as? JsonArray }.getOrNull() }
				?: multi["values"] as? JsonArray
			return values?.mapNotNull { it.str() }.orEmpty()
		}
		return null
	}

	private fun lit(value: String): String = Json.encodeToString(String.serializer(), value)

	private fun readResource(name: String): String {
		val stream = JsExtension::class.java.getResourceAsStream("/jsext/$name")
			?: throw JsExtensionException("Missing bundled script $name")
		return stream.use { String(it.readBytes(), Charsets.UTF_8) }
	}

	private companion object {

		const val CALL_TIMEOUT_MS = 90_000L
		const val MEMORY_LIMIT = 256L * 1024 * 1024
		const val STACK_LIMIT = 2L * 1024 * 1024

		/** Evaluation order matches Mangayomi's service.dart. */
		val SHIMS = listOf("http.js", "dom.js", "utils.js", "extractors.js", "preferences.js", "mprovider.js")

		/** Host calls that return a Promise in the JS runtime. Everything else answers synchronously. */
		val ASYNC_NAMES = JsHttpBridge.METHODS.keys + listOf(
			"evaluateJavascriptViaWebview", "parseEpub", "parseEpubChapter",
			"sibnetExtractor", "myTvExtractor", "okruExtractor", "voeExtractor", "vidBomExtractor",
			"quarkVideosExtractor", "ucVideosExtractor", "quarkFilesExtractor", "ucFilesExtractor",
			"streamlareExtractor", "sendVidExtractor", "yourUploadExtractor", "gogoCdnExtractor",
			"doodExtractor", "streamTapeExtractor", "streamWishExtractor", "filemoonExtractor",
		)

		/** Mangayomi exposes one `sendMessage(name, json)`; QuickJS bindings are sync or async, so route it. */
		const val PRELUDE = """
const __ASYNC_HANDLERS = new Set([__ASYNC_NAMES__]);
function sendMessage(name, args) {
    return __ASYNC_HANDLERS.has(name) ? __hostAsync(name, args) : __hostSync(name, args);
}
// A bare QuickJS context has no console, but Mangayomi's runtime does; utils.js then overrides
// log/warn/error to forward to the host.
if (typeof console === "undefined") { globalThis.console = {}; }
console.info = console.debug = function (message) {
    sendMessage("log", JSON.stringify([String(message)]));
};
"""
	}
}
