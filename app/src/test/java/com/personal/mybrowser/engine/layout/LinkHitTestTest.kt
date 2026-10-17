package com.personal.mybrowser.engine.layout

import com.personal.mybrowser.engine.html.HtmlParser
import com.personal.mybrowser.engine.style.StyleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkHitTestTest {
    private val engine = HtmlLayoutEngine(TextMeasurer { text, style ->
        TextMetrics(text.length * style.fontSizePx, style.fontSizePx * 0.8f, style.fontSizePx * 0.2f)
    })

    @Test fun everyWrappedFragmentKeepsItsDestination() {
        val page = layout("<a href='about.html'>one two three</a>", width = 50f)
        assertEquals(3, page.commands.map { it.baselineY }.distinct().size)
        for (fragment in page.commands) {
            assertEquals("about.html", fragment.linkTarget)
            assertEquals("about.html", page.linkAt(fragment.bounds.left + 1f, fragment.baselineY))
        }
    }

    @Test fun blankLineGapsAndOrdinaryTextAreNotLinks() {
        val page = layout("<a href='about.html'>one two</a><p>plain</p>", width = 50f)
        val first = page.commands.first()
        assertNull(page.linkAt(first.bounds.right + 1f, first.baselineY))
        assertNull(page.linkAt(first.x + 1f, first.bounds.bottom + 1f))
        val plain = page.commands.last()
        assertNull(page.linkAt(plain.x + 1f, plain.baselineY))
        assertNull(page.linkAt(-1f, -1f))
    }

    @Test fun linkSurvivesBoldChildrenAndEndsAtTheAnchorBoundary() {
        val page = layout("<a href='about.html'>a<b>b</b></a>c")
        assertEquals(listOf("about.html", "about.html", null), page.commands.map { it.linkTarget })
        assertEquals(listOf(false, true, false), page.commands.map { it.style.bold })
    }

    @Test fun adjacentLinksWithTheSameFontKeepSeparateTargets() {
        val page = layout("<a href='one.html'>one</a><a href='two.html'>two</a>")
        assertEquals(2, page.commands.size)
        val second = page.commands[1]
        assertEquals("two.html", page.linkAt(second.bounds.left, second.baselineY))
        assertEquals("one.html", page.linkAt(second.bounds.left - 1f, second.baselineY))
    }

    @Test fun aLinkSpaceIsClickableButTrailingOutsideSpaceIsNot() {
        val page = layout("<a href='about.html'>one two</a> plain")
        val linkedSpace = page.commands.first { it.text == " " }
        assertEquals("about.html", page.linkAt(linkedSpace.x + 1f, linkedSpace.baselineY))
        val outsideSpace = page.commands.last { it.text == " " }
        assertNull(page.linkAt(outsideSpace.x + 1f, outsideSpace.baselineY))
    }

    @Test fun anchorWithoutHrefIsNotInteractive() {
        val page = layout("<a>plain anchor</a>")
        assertTrue(page.commands.all { it.linkTarget == null })
        assertNull(page.linkAt(1f, page.commands.first().baselineY))
    }

    @Test fun reflowRebuildsTheHitRegions() {
        val wide = layout("<a href='about.html'>one two</a>", width = 100f)
        val narrow = layout("<a href='about.html'>one two</a>", width = 50f)
        val wrappedWord = narrow.commands.last()
        assertEquals("about.html", narrow.linkAt(1f, wrappedWord.baselineY))
        assertNull(wide.linkAt(1f, wrappedWord.baselineY))
    }

    private fun layout(html: String, width: Float = 300f) =
        engine.layout(StyleResolver(baseFontSizePx = 10f).resolve(HtmlParser().parse(html)), width, paddingPx = 0f)
}
