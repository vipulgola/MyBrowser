package com.personal.mybrowser.engine.render

import android.graphics.Paint
import android.graphics.Typeface
import com.personal.mybrowser.engine.layout.TextMeasurer
import com.personal.mybrowser.engine.layout.TextMetrics
import com.personal.mybrowser.engine.style.TextStyle

/** Font measurement and painting share the same Paint settings. Used on the UI thread. */
internal class AndroidTextMeasurer : TextMeasurer {
    private val paints = mutableMapOf<TextStyle, Paint>()
    private val boldTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    fun paintFor(style: TextStyle): Paint = paints.getOrPut(style) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = style.fontSizePx
            typeface = if (style.bold) boldTypeface else Typeface.DEFAULT
        }
    }

    override fun measure(text: String, style: TextStyle): TextMetrics {
        val paint = paintFor(style)
        val font = paint.fontMetrics
        // top/bottom reserve room for glyph extents around the baseline.
        return TextMetrics(paint.measureText(text), -font.top, font.bottom)
    }
}
