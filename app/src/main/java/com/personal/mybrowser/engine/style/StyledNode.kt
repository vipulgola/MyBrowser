package com.personal.mybrowser.engine.style

import com.personal.mybrowser.engine.html.HtmlNode

/** Theme tokens stay unresolved until painting, so a theme change needs no relayout. */
sealed interface TextColor {
    data object Foreground : TextColor
    data object Link : TextColor
    data class Argb(val value: Int) : TextColor
}

/** Font sizes here are resolved physical pixels, ready for measurement. */
data class TextStyle(
    val fontSizePx: Float,
    val bold: Boolean = false,
    val color: TextColor = TextColor.Foreground,
)

enum class Display { INLINE, BLOCK, NONE }

data class ComputedStyle(
    val text: TextStyle,
    val display: Display = Display.INLINE,
    val bottomGapPx: Float = 0f,
    val lineBreak: Boolean = false,
)

/** A separate snapshot of resolved styles. Resolving styles never mutates the source DOM. */
data class StyledNode(
    val source: HtmlNode,
    val style: ComputedStyle,
    val children: List<StyledNode> = emptyList(),
)
