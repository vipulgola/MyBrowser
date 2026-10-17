package com.personal.mybrowser.browser

import com.personal.mybrowser.engine.html.HtmlNode
import com.personal.mybrowser.network.AddressValidationException
import com.personal.mybrowser.network.HttpsAddress
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/** One editable text/search input in a simple GET form, shown as native Android controls. */
class PageForm private constructor(
    val sourceUrl: String,
    val fieldLabel: String,
    val initialValue: String,
    val submitLabel: String,
    private val action: String,
    private val parameters: List<Parameter>,
    private val liteSearch: Boolean,
) {
    fun submissionUrl(value: String): HttpsAddress {
        if (value.length > MAX_FIELD_LENGTH) {
            throw AddressValidationException("This form input is too long.")
        }
        val base = HttpsAddress.parseInput(sourceUrl)
        val destination = if (liteSearch) {
            HttpsAddress.parseInput(LITE_SEARCH_URL)
        } else {
            base.resolveLink(action)
        }
        val uri = URI(destination.value)
        val query = parameters.joinToString("&") { parameter ->
            val submittedValue = if (parameter.editable) value else parameter.value
            "${encode(parameter.name)}=${encode(submittedValue)}"
        }
        val url = buildString {
            append("https://")
            append(uri.rawAuthority)
            append(uri.rawPath)
            append('?')
            append(query)
            uri.rawFragment?.let { append('#'); append(it) }
        }
        if (url.length > MAX_URL_LENGTH) throw AddressValidationException("This form address is too long.")
        return HttpsAddress.parseInput(url)
    }

    private data class Parameter(val name: String, val value: String, val editable: Boolean = false)

    companion object {
        private const val LITE_SEARCH_URL = "https://lite.duckduckgo.com/lite/"
        private const val MAX_FIELD_LENGTH = 2048
        private const val MAX_URL_LENGTH = 8192

        fun find(document: HtmlNode.Document, sourceUrl: String): PageForm? {
            fun visit(node: HtmlNode): PageForm? = when (node) {
                is HtmlNode.Text -> null
                is HtmlNode.Document -> node.children.firstNotNullOfOrNull(::visit)
                is HtmlNode.Element -> {
                    if (node.tagName == "form") fromElement(node, sourceUrl)
                    else node.children.firstNotNullOfOrNull(::visit)
                }
            }
            return visit(document)
        }

        private fun fromElement(form: HtmlNode.Element, sourceUrl: String): PageForm? {
            val method = form.attributes["method"]?.trim()?.lowercase(Locale.ROOT).orEmpty().ifEmpty { "get" }
            val action = form.attributes["action"].orEmpty()
            val liteSearch = method == "post" && action == "/lite/" && isLitePage(sourceUrl)
            if (method != "get" && !liteSearch) return null
            if (form.attributes["target"]?.let { it.isNotBlank() && !it.equals("_self", true) } == true) return null

            val parameters = mutableListOf<Parameter>()
            var editableName: String? = null
            var initialValue = ""
            var label = "Page text field"
            var submitLabel = if (liteSearch) "Search" else "Submit"
            var hasSubmit = false
            var unsupported = false

            fun inspect(node: HtmlNode) {
                if (unsupported || node !is HtmlNode.Element) return
                if (node.tagName == "form") return // Nested forms are not controls of this form.
                val attrs = node.attributes
                if ("disabled" in attrs) return
                when (node.tagName) {
                    "input" -> {
                        val type = attrs["type"]?.lowercase(Locale.ROOT).orEmpty().ifEmpty { "text" }
                        val name = attrs["name"].orEmpty()
                        when (type) {
                            "text", "search" -> {
                                if (editableName != null || name.isEmpty()) { unsupported = true; return }
                                editableName = name
                                initialValue = attrs["value"].orEmpty()
                                label = if (liteSearch || type == "search") "Search this page" else name
                                parameters += Parameter(name, initialValue, editable = true)
                            }
                            "hidden" -> if (!liteSearch && name.isNotEmpty()) parameters += Parameter(name, attrs["value"].orEmpty())
                            "submit" -> if (!hasSubmit) {
                                if ("formmethod" in attrs || "formaction" in attrs) { unsupported = true; return }
                                hasSubmit = true
                                submitLabel = attrs["value"]?.takeIf { it.isNotBlank() } ?: submitLabel
                                if (!liteSearch && name.isNotEmpty()) parameters += Parameter(name, attrs["value"].orEmpty())
                            }
                            else -> if (!liteSearch) unsupported = true
                        }
                    }
                    "button" -> if (!hasSubmit && attrs["type"]?.lowercase(Locale.ROOT) in listOf(null, "", "submit")) {
                        if ("formmethod" in attrs || "formaction" in attrs) { unsupported = true; return }
                        hasSubmit = true
                        submitLabel = textContent(node).trim().ifEmpty { submitLabel }
                        val name = attrs["name"].orEmpty()
                        if (!liteSearch && name.isNotEmpty()) parameters += Parameter(name, attrs["value"].orEmpty())
                    }
                    "select", "textarea" -> if (!liteSearch) unsupported = true
                }
                if (!unsupported) node.children.forEach(::inspect)
            }
            form.children.forEach(::inspect)
            if (unsupported || editableName == null || parameters.size > 20) return null
            if (liteSearch) {
                // Lite declares POST, but its q= GET endpoint was verified separately.
                // Submit only q; the page's other POST controls are deliberately ignored.
                val q = editableName.takeIf { it == "q" } ?: return null
                return PageForm(sourceUrl, "Search again", initialValue, "Search", action, listOf(Parameter(q, initialValue, true)), true)
            }
            return PageForm(sourceUrl, label, initialValue, submitLabel, action, parameters.toList(), false)
        }

        private fun isLitePage(sourceUrl: String): Boolean = try {
            val uri = URI(sourceUrl)
            uri.scheme?.equals("https", true) == true &&
                uri.host?.equals("lite.duckduckgo.com", true) == true && uri.path == "/lite/"
        } catch (_: Exception) {
            false
        }

        private fun textContent(node: HtmlNode): String = when (node) {
            is HtmlNode.Text -> node.data
            is HtmlNode.Document -> node.children.joinToString("") { textContent(it) }
            is HtmlNode.Element -> node.children.joinToString("") { textContent(it) }
        }

        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    }
}
