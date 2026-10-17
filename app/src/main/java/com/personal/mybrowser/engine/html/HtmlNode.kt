package com.personal.mybrowser.engine.html

/** Document structure only: styles, geometry and Android types belong to later stages. */
sealed interface HtmlNode {
    data class Document(val children: MutableList<HtmlNode> = mutableListOf()) : HtmlNode

    data class Element(
        val tagName: String,
        val attributes: Map<String, String> = emptyMap(),
        val children: MutableList<HtmlNode> = mutableListOf(),
    ) : HtmlNode

    data class Text(val data: String) : HtmlNode
}
