package com.personal.mybrowser.engine.html

/**
 * Builds a minimal document tree from tokens. This is deliberately not HTML5-compliant:
 * no implicit html/head/body, optional-end-tag rules, table recovery, or script execution.
 * Intended for small, controlled examples until those rules are implemented and tested.
 */
class HtmlParser(
    private val maxElementDepth: Int = MAX_ELEMENT_DEPTH,
    private val maxNodes: Int = MAX_NODES,
) {
    init {
        require(maxElementDepth > 0) { "Element depth limit must be positive" }
        require(maxNodes > 0) { "Node limit must be positive" }
    }

    fun parse(source: String): HtmlNode.Document {
        val document = HtmlNode.Document()
        val openElements = mutableListOf<HtmlNode.Element>()
        var nodeCount = 0

        fun append(node: HtmlNode) {
            if (nodeCount == maxNodes) throw HtmlComplexityException(HtmlComplexityException.Limit.NODE_COUNT)
            nodeCount++ // Count elements and text nodes, excluding the document container.
            val children = openElements.lastOrNull()?.children ?: document.children
            children += node
        }

        for (token in HtmlTokenizer(source).tokenize()) {
            when (token) {
                is HtmlToken.Text -> append(HtmlNode.Text(token.data))
                is HtmlToken.StartTag -> {
                    // Check void elements too: they add a level even though they never push.
                    if (openElements.size >= maxElementDepth) {
                        throw HtmlComplexityException(HtmlComplexityException.Limit.ELEMENT_DEPTH)
                    }
                    val element = HtmlNode.Element(token.name, token.attributes)
                    append(element)
                    // In HTML, <div/> does NOT close a div. Only void elements skip the stack.
                    // The tokenizer retains the slash flag for future foreign-content support.
                    if (token.name !in voidElements) openElements += element
                }
                is HtmlToken.EndTag -> {
                    val matchingIndex = openElements.indexOfLast { it.tagName == token.name }
                    if (matchingIndex != -1) {
                        // Pop the matching element and any unclosed descendants above it.
                        while (openElements.size > matchingIndex) {
                            openElements.removeAt(openElements.lastIndex)
                        }
                    }
                    // Unmatched end tags have no effect on this subset's tree.
                }
            }
        }
        return document
    }

    companion object {
        const val MAX_ELEMENT_DEPTH = 128
        const val MAX_NODES = 10_000

        private val voidElements = setOf(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr",
        )
    }
}

/** Expected rejection of a document exceeding this experimental engine's limits. */
class HtmlComplexityException(val limit: Limit) : Exception("HTML complexity limit exceeded: $limit") {
    enum class Limit { ELEMENT_DEPTH, NODE_COUNT }
}
