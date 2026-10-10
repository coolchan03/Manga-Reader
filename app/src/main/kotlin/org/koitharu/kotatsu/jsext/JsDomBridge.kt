package org.koitharu.kotatsu.jsext

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Host side of Mangayomi's `Document`/`Element` shim (eval/javascript/dom_selector.dart), backed
 * by Jsoup. Mangayomi's own selector dialect is modelled on Jsoup's (`:containsOwn`, `:matches`,
 * `:eq`, ...), so this is the faithful implementation, not an approximation.
 *
 * JS holds elements as integer keys into [elements]. A missing element is a key mapped to `null`,
 * and every accessor on it answers "" / false instead of throwing, as upstream does.
 */
internal class JsDomBridge {

	private val elements = HashMap<Int, Element?>()
	private var nextKey = 0

	private val documents = object : LinkedHashMap<String, Document>(8, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Document>?) = size > 8
	}

	/** Keys never outlive one top-level extension call, so drop them between calls. */
	fun reset() {
		elements.clear()
		documents.clear()
	}

	private fun doc(html: String): Document = documents.getOrPut(html) {
		Jsoup.parse(html).also { it.outputSettings().prettyPrint(false) }
	}

	private fun put(element: Element?): Int {
		nextKey++
		elements[nextKey] = element
		return nextKey
	}

	private fun putAll(list: Collection<Element>?): String =
		JsonArray(list.orEmpty().map { JsonPrimitive(put(it)) }).toString()

	private fun element(key: Int?): Element? = key?.let { elements[it] }

	private inline fun <T> safe(block: () -> T): T? = try {
		block()
	} catch (e: Exception) {
		null // an invalid selector yields "nothing found", as upstream's try/catch does
	}

	/** @return `Unit` when [name] is not a DOM call. */
	fun handle(name: String, a: JsonArray): Any? = when (name) {
		"get_doc_element" -> {
			val d = doc(a[0].str().orEmpty())
			put(
				when (a[1].str()) {
					"body" -> d.body()
					"documentElement" -> d.children().firstOrNull()
					"head" -> d.head()
					else -> null
				},
			)
		}

		"get_doc_string" -> when (a[1].str()) {
			"text" -> "" // package:html answers null for Document.text
			else -> doc(a[0].str().orEmpty()).outerHtml()
		}

		"get_element_string" -> elementString(a[0].str().orEmpty(), element(a[1].long()?.toInt()))
		"doc_select_first" -> put(safe { doc(a[0].str().orEmpty()).selectFirst(a[1].str().orEmpty()) })
		"ele_selectFirst" -> put(safe { element(a[1].long()?.toInt())?.selectFirst(a[0].str().orEmpty()) })
		"ele_element_sibling" -> {
			val e = element(a[1].long()?.toInt())
			put(if (a[0].str() == "nextElementSibling") e?.nextElementSibling() else e?.previousElementSibling())
		}

		"ele_attr" -> element(a[1].long()?.toInt())?.attr(a[0].str().orEmpty()).orEmpty()
		"doc_attr" -> ""
		"ele_has_attr" -> element(a[1].long()?.toInt())?.hasAttr(a[0].str().orEmpty()) ?: false
		"doc_has_attr" -> false
		"doc_xpath_first" -> xpathFirst(doc(a[0].str().orEmpty()), a[1].str().orEmpty())
		"ele_xpathFirst", "xpathFirst" -> element(a[1].long()?.toInt())?.let { xpathFirst(it, a[0].str().orEmpty()) }.orEmpty()
		"doc_xpath" -> xpathList(doc(a[0].str().orEmpty()), a[1].str().orEmpty())
		"ele_xpath", "xpath" -> element(a[1].long()?.toInt())?.let { xpathList(it, a[0].str().orEmpty()) } ?: "[]"
		"doc_get_elements_by" -> {
			val d = doc(a[0].str().orEmpty())
			putAll(
				when (a[1].str()) {
					"children" -> d.children()
					"getElementsByTagName" -> d.getElementsByTag(a[2].str().orEmpty())
					else -> d.getElementsByClass(a[2].str().orEmpty())
				},
			)
		}

		"ele_get_elements_by" -> {
			val e = element(a[2].long()?.toInt())
			putAll(
				when (a[0].str()) {
					"children" -> e?.children()
					"getElementsByTagName" -> e?.getElementsByTag(a[1].str().orEmpty())
					else -> e?.getElementsByClass(a[1].str().orEmpty())
				},
			)
		}

		"doc_get_element_by_id" -> put(doc(a[0].str().orEmpty()).getElementById(a[1].str().orEmpty()))
		"doc_select" -> putAll(safe { doc(a[0].str().orEmpty()).select(a[1].str().orEmpty()) })
		"ele_select" -> putAll(safe { element(a[1].long()?.toInt())?.select(a[0].str().orEmpty()) })
		else -> Unit
	}

	private fun elementString(type: String, e: Element?): String {
		if (e == null) return ""
		return when (type) {
			"text" -> textContent(e)
			"innerHtml" -> e.html()
			"outerHtml" -> e.outerHtml()
			"className" -> e.className()
			"localName" -> e.tagName().lowercase()
			"namespaceUri" -> "http://www.w3.org/1999/xhtml"
			"getSrc" -> firstGroup(SRC, e.outerHtml())
			"getImg" -> firstGroup(IMG, e.outerHtml())
			"getHref" -> firstGroup(HREF, e.outerHtml())
			else -> firstGroup(DATA_SRC, e.outerHtml())
		}
	}

	private fun firstGroup(regex: Regex, input: String): String = regex.find(input)?.groupValues?.get(1).orEmpty()

	/** DOM `textContent`: unnormalised, includes script/style data, unlike Jsoup's `text()`. */
	private fun textContent(node: Node): String = buildString {
		fun walk(n: Node) {
			when (n) {
				is TextNode -> append(n.wholeText)
				is DataNode -> append(n.wholeData)
				else -> n.childNodes().forEach(::walk)
			}
		}
		walk(node)
	}

	// XPath: Jsoup only returns elements, so the value is the element's text. No bundled JS source
	// uses xpath yet; revisit if one needs attribute results.
	private fun xpathFirst(root: Element, xpath: String): String =
		safe { root.selectXpath(xpath).firstOrNull()?.text() }.orEmpty()

	private fun xpathList(root: Element, xpath: String): String {
		val found = safe { root.selectXpath(xpath) }.orEmpty()
		// upstream returns [] unless more than one node matched
		val values = if (found.size > 1) found.map { JsonPrimitive(it.text().trim()) } else emptyList()
		return JsonArray(values).toString()
	}

	private companion object {

		val HREF = Regex("""href="([^"]+)"""")
		val DATA_SRC = Regex("""data-src="([^"]+)"""")
		val SRC = Regex("""src="([^"]+)"""")
		val IMG = Regex("""img="([^"]+)"""")
	}
}
