package com.personal.mybrowser.engine.style

import com.personal.mybrowser.engine.html.HtmlNode

/** Minimal inherited text properties + element defaults + inline overrides, without selectors. */
class StyleResolver(
    private val baseFontSizePx: Float = 16f,
    private val cssPixelScale: Float = 1f,
) {
    init {
        require(baseFontSizePx.isFinite() && baseFontSizePx > 0f)
        require(cssPixelScale.isFinite() && cssPixelScale > 0f)
    }

    fun resolve(document: HtmlNode.Document): StyledNode = resolveNode(document, TextStyle(baseFontSizePx))

    private fun resolveNode(node: HtmlNode, inheritedText: TextStyle): StyledNode {
        if (node !is HtmlNode.Element) {
            val style = ComputedStyle(inheritedText)
            val children = if (node is HtmlNode.Document) node.children.map { resolveNode(it, inheritedText) } else emptyList()
            return StyledNode(node, style, children)
        }

        val tag = node.tagName
        val headingScale = headingScales[tag]
        var text = inheritedText.copy(
            fontSizePx = headingScale?.let { inheritedText.fontSizePx * it } ?: inheritedText.fontSizePx,
            bold = inheritedText.bold || headingScale != null || tag == "b" || tag == "strong",
            color = if (tag == "a" && "href" in node.attributes) TextColor.Link else inheritedText.color,
        )
        val inline = InlineStyleParser.parse(node.attributes["style"].orEmpty())
        inline.fontSizeCssPx?.times(cssPixelScale)?.takeIf { it.isFinite() }?.let {
            text = text.copy(fontSizePx = it)
        }
        inline.colorArgb?.let { text = text.copy(color = TextColor.Argb(it)) }

        // Display/spacing are resolved afresh for each element; they are not inherited.
        val style = ComputedStyle(
            text = text,
            display = when (tag) {
                in hiddenTags -> Display.NONE
                in blockTags -> Display.BLOCK
                else -> Display.INLINE
            },
            bottomGapPx = if (tag == "p" || headingScale != null) baseFontSizePx * 0.75f else 0f,
            lineBreak = tag == "br",
        )
        val children = if (style.display == Display.NONE) emptyList() else node.children.map { resolveNode(it, text) }
        return StyledNode(node, style, children)
    }

    private companion object {
        val headingScales = mapOf("h1" to 2f, "h2" to 1.5f, "h3" to 1.25f, "h4" to 1.125f, "h5" to 1f, "h6" to 0.875f)
        val blockTags = setOf("html", "body", "main", "article", "section", "div", "p") + headingScales.keys
        val hiddenTags = setOf("head", "title", "script", "style", "meta", "link")
    }
}
