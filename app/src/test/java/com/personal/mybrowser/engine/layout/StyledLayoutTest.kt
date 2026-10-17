package com.personal.mybrowser.engine.layout

import com.personal.mybrowser.engine.html.HtmlParser
import com.personal.mybrowser.engine.style.StyleResolver
import com.personal.mybrowser.engine.style.TextColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StyledLayoutTest {
    private val engine = HtmlLayoutEngine(TextMeasurer { text, style ->
        TextMetrics(text.length * style.fontSizePx, style.fontSizePx * 0.8f, style.fontSizePx * 0.2f)
    })

    @Test fun largerInlineFontChangesWrappingAndContentHeight() {
        val small = layout("<p>one two</p>", 80f)
        val large = layout("<p style='font-size:20px'>one two</p>", 80f)
        assertEquals(1, small.commands.map { it.baselineY }.distinct().size)
        assertEquals(2, large.commands.map { it.baselineY }.distinct().size)
        assertTrue(large.contentHeight > small.contentHeight)
    }

    @Test fun mixedFontSizesShareABaselineAndReserveRoomForTheTallestText() {
        val page = layout("a<span style='font-size:20px'>b</span>c<br>d")
        assertEquals(16f, page.commands[0].baselineY, 0.001f)
        assertEquals(page.commands[0].baselineY, page.commands[1].baselineY, 0.001f)
        assertEquals(page.commands[0].baselineY, page.commands[2].baselineY, 0.001f)
        assertEquals(33f, page.commands.last().baselineY, 0.001f)
        assertEquals(0f, page.commands[1].bounds.top, 0.001f)
    }

    @Test fun colourChangesDoNotChangeTextGeometryOrLoseTheLinkTarget() {
        val plain = layout("<a href='about.html'>link</a>")
        val coloured = layout("<a href='about.html' style='color:#11223344'>link</a>")
        assertEquals(plain.contentHeight, coloured.contentHeight, 0.001f)
        assertEquals(plain.commands.single().bounds, coloured.commands.single().bounds)
        assertEquals(TextColor.Argb(0x44112233), coloured.commands.single().style.color)
        assertEquals("about.html", coloured.linkAt(1f, coloured.commands.single().baselineY))
    }

    @Test fun changingFontSizeAlsoUpdatesTheLinksClickableBounds() {
        val small = layout("<a href='about.html'>Hi</a>")
        val large = layout("<a href='about.html' style='font-size:20px'>Hi</a>")
        assertEquals(20f, small.commands.single().bounds.right, 0.001f)
        assertEquals(40f, large.commands.single().bounds.right, 0.001f)
        assertEquals("about.html", large.linkAt(30f, large.commands.single().baselineY))
        assertEquals(null, small.linkAt(30f, small.commands.single().baselineY))
    }

    @Test fun zeroFontSizeDoesNotCrashOrProduceClickableArea() {
        val page = layout("<a href='about.html' style='font-size:0px'>hidden</a>")
        assertEquals(0f, page.commands.single().bounds.right, 0.001f)
        assertEquals(null, page.linkAt(0f, 0f))
    }

    private fun layout(html: String, width: Float = 300f): PageLayout {
        val styled = StyleResolver(baseFontSizePx = 10f).resolve(HtmlParser().parse(html))
        return engine.layout(styled, width, paddingPx = 0f)
    }
}
