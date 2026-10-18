package com.personal.mybrowser.engine.style

import com.personal.mybrowser.engine.html.HtmlNode

/** Minimal inherited text properties + element defaults + embedded/inline overrides. */
class StyleResolver(
    private val baseFontSizePx: Float = 16f,
    private val cssPixelScale: Float = 1f,
) {
    init {
        require(baseFontSizePx.isFinite() && baseFontSizePx > 0f)
        require(cssPixelScale.isFinite() && cssPixelScale > 0f)
    }

    fun resolve(document: HtmlNode.Document): StyledNode {
        val rules = EmbeddedStyleSheet.parse(document)
        return resolveNode(document, TextStyle(baseFontSizePx), rules)
    }

    private fun resolveNode(
        node: HtmlNode,
        inheritedText: TextStyle,
        rules: List<EmbeddedStyleSheet.Rule>,
    ): StyledNode {
        if (node !is HtmlNode.Element) {
            val style = ComputedStyle(inheritedText)
            val children = if (node is HtmlNode.Document) node.children.map { resolveNode(it, inheritedText, rules) } else emptyList()
            return StyledNode(node, style, children)
        }

        val tag = node.tagName
        val headingScale = headingScales[tag]
        var text = inheritedText.copy(
            fontSizePx = headingScale?.let { inheritedText.fontSizePx * it } ?: inheritedText.fontSizePx,
            bold = inheritedText.bold || headingScale != null || tag == "b" || tag == "strong",
            color = if (tag == "a" && "href" in node.attributes) TextColor.Link else inheritedText.color,
        )
        if (rules.isNotEmpty()) {
            val classes = node.attributes["class"].orEmpty().split(classWhitespace)
                .filter { it.isNotEmpty() }.toSet()
            for (rule in rules) {
                if (rule.matches(node, classes)) text = applyDeclarations(text, rule.declarations)
            }
        }
        text = applyDeclarations(text, InlineStyleParser.parse(node.attributes["style"].orEmpty()))

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
        val children = if (style.display == Display.NONE) emptyList() else node.children.map { resolveNode(it, text, rules) }
        return StyledNode(node, style, children)
    }

    private fun applyDeclarations(text: TextStyle, declarations: InlineStyle): TextStyle {
        var result = text
        declarations.fontSizeCssPx?.times(cssPixelScale)?.takeIf { it.isFinite() }?.let {
            result = result.copy(fontSizePx = it)
        }
        declarations.colorArgb?.let { result = result.copy(color = TextColor.Argb(it)) }
        return result
    }

    private companion object {
        val classWhitespace = Regex("[\\t\\n\\r\\f ]+")
        val headingScales = mapOf("h1" to 2f, "h2" to 1.5f, "h3" to 1.25f, "h4" to 1.125f, "h5" to 1f, "h6" to 0.875f)
        val blockTags = setOf("html", "body", "main", "article", "section", "div", "p") + headingScales.keys
        val hiddenTags = setOf("head", "title", "script", "style", "meta", "link")
    }
}
