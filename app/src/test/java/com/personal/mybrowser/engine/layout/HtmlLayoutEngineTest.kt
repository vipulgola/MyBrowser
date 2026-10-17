package com.personal.mybrowser.engine.layout

import com.personal.mybrowser.engine.html.HtmlParser
import com.personal.mybrowser.engine.style.StyleResolver
import com.personal.mybrowser.engine.style.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlLayoutEngineTest {
    // Predictable metrics isolate layout behavior from installed fonts and Android.
    private val measurer = TextMeasurer { text, style ->
        TextMetrics(
            width = text.length * style.fontSizePx * if (style.bold) 1.1f else 1f,
            ascent = style.fontSizePx * if (style.bold) 0.9f else 0.8f,
            descent = style.fontSizePx * if (style.bold) 0.3f else 0.2f,
        )
    }
    private val engine = HtmlLayoutEngine(measurer)

    @Test fun headingUsesLargerBoldFontAndParagraphStartsBelowIt() {
        val page = layout("<h1>Hi</h1><p>body</p>")
        val heading = page.commands.first()
        val body = page.commands.last()
        assertEquals(TextStyle(20f, bold = true), heading.style)
        assertEquals(TextStyle(10f), body.style)
        assertTrue(body.baselineY > heading.baselineY + measurer.measure("Hi", heading.style).descent)
    }

    @Test fun wrapsAtSpacesAndDropsWhitespaceAtLineEdges() {
        val commands = layout("  one   two  ", width = 50f).commands
        assertEquals(listOf("one", "two"), commands.map { it.text })
        assertEquals(0f, commands[0].x, 0.001f)
        assertEquals(0f, commands[1].x, 0.001f)
        assertTrue(commands[1].baselineY > commands[0].baselineY)
    }

    @Test fun anExactFitStaysOnOneLine() {
        val commands = layout("one two", width = 70f).commands
        assertEquals("one two", commands.joinToString("") { it.text })
        assertEquals(1, commands.map { it.baselineY }.distinct().size)
        assertEquals(40f, commands.last().x, 0.001f)
    }

    @Test fun aWordAcrossInlineStylesWrapsAsOneUnit() {
        val commands = layout("x hel<b>lo</b>", width = 60f).commands
        assertEquals(listOf("x", "hel", "lo"), commands.map { it.text })
        assertTrue(commands[1].baselineY > commands[0].baselineY)
        assertEquals(commands[1].baselineY, commands[2].baselineY, 0.001f)
        assertEquals(30f, commands[2].x, 0.001f)
    }

    @Test fun whitespaceCollapsesAcrossElementBoundaries() {
        val commands = layout("  A \n<b> \tB</b>  C ").commands
        assertEquals("A B C", commands.joinToString("") { it.text })
    }

    @Test fun inlineStylesShareABaselineAndDoNotLeakToSiblings() {
        val commands = layout("a<b>b</b>c").commands
        assertEquals(listOf(false, true, false), commands.map { it.style.bold })
        assertEquals(1, commands.map { it.baselineY }.distinct().size)
        assertEquals(9f, commands.first().baselineY, 0.001f)
    }

    @Test fun consecutiveBreaksReserveAnEmptyLine() {
        val single = layout("a<br>b").commands
        val double = layout("a<br><br>b").commands
        assertEquals(single.first().baselineY, double.first().baselineY, 0.001f)
        assertEquals(12.5f, double.last().baselineY - single.last().baselineY, 0.001f)
    }

    @Test fun sourceIndentationDoesNotAddBlankLinesBetweenBlocks() {
        val compact = layout("<p>first</p><p>second</p>")
        val indented = layout("\n  <p>first</p>\n  <p>second</p>\n")
        assertEquals(compact, indented)
        assertTrue(compact.commands[1].baselineY - compact.commands[0].baselineY > 12.5f)
    }

    @Test fun reflowAtNarrowerWidthIncreasesHeightWithoutChangingContent() {
        val document = styled("one two three four")
        val wide = engine.layout(document, 300f, 0f)
        val narrow = engine.layout(document, 60f, 0f)
        assertTrue(narrow.contentHeight > wide.contentHeight)
        assertEquals(listOf("one", "two", "three", "four"), narrow.commands.map { it.text })
        assertEquals(wide, engine.layout(document, 300f, 0f))
    }

    @Test fun contentHeightIncludesGlyphExtentsAndPadding() {
        val page = engine.layout(styled("Hi"), 100f, 7f)
        assertEquals(7f, page.commands.single().x, 0.001f)
        assertEquals(15f, page.commands.single().baselineY, 0.001f)
        assertEquals(26.5f, page.contentHeight, 0.001f)
    }

    @Test fun paddingReducesTheWidthAvailableForWrapping() {
        val page = engine.layout(styled("one two"), 80f, 10f)
        assertEquals(2, page.commands.map { it.baselineY }.distinct().size)
        assertTrue(page.commands.all { it.x == 10f })
    }

    @Test fun anOversizedWordOverflowsWithoutDroppingTextOrLooping() {
        val page = layout("abcdefgh next", width = 30f)
        assertEquals(listOf("abcdefgh", "next"), page.commands.map { it.text })
        assertTrue(page.commands.last().baselineY > page.commands.first().baselineY)
    }

    @Test fun emptyAndWhitespaceOnlyDocumentsHaveOnlyPadding() {
        for (html in listOf("", " \n\t", "<p> </p>")) {
            val page = engine.layout(styled(html), 100f, 8f)
            assertTrue(page.commands.isEmpty())
            assertEquals(16f, page.contentHeight, 0.001f)
        }
    }

    @Test fun metadataDoesNotAppearInTheDisplayList() {
        val page = layout("<head><title>hidden</title></head><p>visible</p>")
        assertEquals("visible", page.commands.joinToString("") { it.text })
        assertFalse(page.commands.any { it.text.contains("hidden") })
    }

    @Test fun imageReservesSpaceAndItsLinkRemainsTappable() {
        val page = layout("<p>before</p><a href='/photo'><img src='images/pic.png' alt='Portrait' width='100' height='50'></a><p>after</p>", width = 80f)
        val image = page.images.single()
        assertEquals("images/pic.png", image.source)
        assertEquals("Portrait", image.alt)
        assertEquals(80f, image.bounds.right - image.bounds.left, 0.001f)
        assertEquals(40f, image.bounds.bottom - image.bounds.top, 0.001f)
        assertEquals("/photo", page.linkAt(20f, image.bounds.top + 10f))
        assertTrue(page.commands.last().bounds.top >= image.bounds.bottom)
    }

    private fun layout(html: String, width: Float = 300f) =
        engine.layout(styled(html), width, paddingPx = 0f)

    private fun styled(html: String) = StyleResolver(baseFontSizePx = 10f).resolve(HtmlParser().parse(html))
}
