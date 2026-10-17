package com.personal.mybrowser.engine.layout

import com.personal.mybrowser.engine.html.HtmlNode
import com.personal.mybrowser.engine.style.Display
import com.personal.mybrowser.engine.style.StyledNode
import com.personal.mybrowser.engine.style.TextStyle

/**
 * Minimal block/inline text layout for our controlled fixtures, not CSS layout.
 * Each call owns its state, so the same tree can be laid out again at a new width.
 */
class HtmlLayoutEngine(private val measurer: TextMeasurer) {
    fun layout(
        document: StyledNode,
        viewportWidthPx: Float,
        paddingPx: Float = 16f,
    ): PageLayout {
        require(viewportWidthPx.isFinite() && viewportWidthPx >= 0f)
        require(paddingPx.isFinite() && paddingPx >= 0f)
        return LayoutPass(measurer, viewportWidthPx, document.style.text, paddingPx).run(document)
    }
}

private class LayoutPass(
    private val measurer: TextMeasurer,
    viewportWidth: Float,
    private val defaultStyle: TextStyle,
    private val padding: Float,
) {
    private data class Run(
        val text: String,
        val style: TextStyle,
        val metrics: TextMetrics,
        val linkTarget: String? = null,
    )

    private val availableWidth = (viewportWidth - padding * 2).coerceAtLeast(0f)
    private val commands = mutableListOf<DrawText>()
    private val images = mutableListOf<DrawImage>()
    private val line = mutableListOf<Run>()
    // A word can cross inline elements: hel<b>lo</b> must stay one word for wrapping.
    private val word = mutableListOf<Run>()
    private var pendingSpace: Run? = null
    private var lineWidth = 0f
    private var y = padding

    fun run(document: StyledNode): PageLayout {
        visit(document)
        flushWord()
        flushLine()
        return PageLayout(commands.toList(), y + padding, images.toList())
    }

    private fun visit(node: StyledNode, inheritedLink: String? = null) {
        val style = node.style.text
        when (val source = node.source) {
            is HtmlNode.Document -> node.children.forEach { visit(it, inheritedLink) }
            is HtmlNode.Text -> addText(source.data, style, inheritedLink)
            is HtmlNode.Element -> {
                if (node.style.display == Display.NONE) return
                if (source.tagName == "img") {
                    addImage(source, inheritedLink)
                    return
                }
                if (node.style.lineBreak) {
                    flushWord()
                    flushLine(forceEmpty = true, emptyStyle = style)
                    return
                }
                val linkTarget = if (source.tagName == "a") source.attributes["href"] else inheritedLink
                val block = node.style.display == Display.BLOCK
                if (block) {
                    flushWord()
                    flushLine()
                    pendingSpace = null
                }
                val commandsBefore = commands.size
                node.children.forEach { visit(it, linkTarget) }
                if (block) {
                    flushWord()
                    flushLine()
                    pendingSpace = null
                    if (commands.size > commandsBefore) y += node.style.bottomGapPx
                }
            }
        }
    }

    private fun addImage(source: HtmlNode.Element, linkTarget: String?) {
        val src = source.attributes["src"].orEmpty()
        val alt = source.attributes["alt"].orEmpty()
        if (src.isBlank()) {
            if (alt.isNotBlank()) addText(alt, defaultStyle, linkTarget)
            return
        }
        flushWord()
        flushLine()
        pendingSpace = null
        val requestedWidth = source.attributes["width"]?.toIntOrNull()?.takeIf { it > 0 } ?: 240
        val requestedHeight = source.attributes["height"]?.toIntOrNull()?.takeIf { it > 0 } ?: 160
        val width = requestedWidth.toFloat().coerceAtMost(availableWidth)
        val height = (requestedHeight.toFloat() * width / requestedWidth).coerceAtMost(600f)
        images += DrawImage(src, alt, TextBounds(padding, y, padding + width, y + height), linkTarget)
        y += height + defaultStyle.fontSizePx * 0.25f
    }

    private fun addText(text: String, style: TextStyle, linkTarget: String?) {
        var position = 0
        while (position < text.length) {
            if (text[position].isCollapsibleSpace()) {
                flushWord()
                if (line.isNotEmpty() && pendingSpace == null) {
                    pendingSpace = Run(" ", style, measurer.measure(" ", style), linkTarget)
                }
                position++
            } else {
                val start = position
                while (position < text.length && !text[position].isCollapsibleSpace()) position++
                val part = text.substring(start, position)
                val previous = word.lastOrNull()
                if (previous?.style == style && previous.linkTarget == linkTarget) {
                    val combined = previous.text + part
                    word[word.lastIndex] = Run(combined, style, measurer.measure(combined, style), linkTarget)
                } else {
                    word += Run(part, style, measurer.measure(part, style), linkTarget)
                }
            }
        }
    }

    private fun flushWord() {
        if (word.isEmpty()) return
        val width = word.sumOf { it.metrics.width.toDouble() }.toFloat()
        val space = pendingSpace?.takeIf { line.isNotEmpty() }
        if (line.isNotEmpty() && lineWidth + (space?.metrics?.width ?: 0f) + width > availableWidth) {
            flushLine() // Whitespace at the start of the wrapped line is discarded.
        } else if (space != null) {
            line += space
            lineWidth += space.metrics.width
        }
        // An indivisible word wider than the viewport overflows; no characters are dropped.
        line += word
        lineWidth += width
        word.clear()
        pendingSpace = null
    }

    private fun flushLine(forceEmpty: Boolean = false, emptyStyle: TextStyle = defaultStyle) {
        if (line.isEmpty()) {
            if (forceEmpty) {
                val metrics = measurer.measure(" ", emptyStyle)
                y += metrics.ascent + metrics.descent + emptyStyle.fontSizePx * 0.25f
            }
        } else {
            val ascent = line.maxOf { it.metrics.ascent }
            val descent = line.maxOf { it.metrics.descent }
            val leading = line.maxOf { it.style.fontSizePx } * 0.25f
            val baseline = y + ascent
            var x = padding
            for (run in line) {
                commands += DrawText(
                    run.text, x, baseline, run.style,
                    TextBounds(x, baseline - run.metrics.ascent, x + run.metrics.width, baseline + run.metrics.descent),
                    run.linkTarget,
                )
                x += run.metrics.width
            }
            y += ascent + descent + leading
        }
        line.clear()
        lineWidth = 0f
        pendingSpace = null
    }

}

private fun Char.isCollapsibleSpace() = this == ' ' || this == '\t' || this == '\n' || this == '\r' || this == '\u000C'
