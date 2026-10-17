package com.personal.mybrowser.engine.layout

import com.personal.mybrowser.engine.style.TextStyle

/** Positive distances above and below the baseline, plus the horizontal advance. */
data class TextMetrics(val width: Float, val ascent: Float, val descent: Float)

fun interface TextMeasurer {
    fun measure(text: String, style: TextStyle): TextMetrics
}

/** A display-list instruction. Painting does not need the original HTML tree. */
data class DrawText(
    val text: String,
    val x: Float,
    val baselineY: Float,
    val style: TextStyle,
    val bounds: TextBounds,
    val linkTarget: String? = null,
)

data class DrawImage(
    val source: String,
    val alt: String,
    val bounds: TextBounds,
    val linkTarget: String? = null,
)

data class TextBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    // Half-open edges avoid two adjacent fragments claiming the same boundary point.
    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
}

data class PageLayout(
    val commands: List<DrawText>,
    val contentHeight: Float,
    val images: List<DrawImage> = emptyList(),
) {
    /** Coordinates are local to the full document view, not the visible viewport. */
    fun linkAt(x: Float, y: Float): String? = images.asReversed().firstOrNull {
        it.linkTarget != null && it.bounds.contains(x, y)
    }?.linkTarget ?: commands.asReversed().firstOrNull {
        it.linkTarget != null && it.bounds.contains(x, y)
    }?.linkTarget
}
