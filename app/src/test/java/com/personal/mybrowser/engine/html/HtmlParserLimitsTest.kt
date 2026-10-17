package com.personal.mybrowser.engine.html

import com.personal.mybrowser.engine.layout.HtmlLayoutEngine
import com.personal.mybrowser.engine.layout.TextMeasurer
import com.personal.mybrowser.engine.layout.TextMetrics
import com.personal.mybrowser.engine.style.StyleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HtmlParserLimitsTest {
    @Test fun maximumDepthCanBeParsedStyledAndLaidOut() {
        val depth = HtmlParser.MAX_ELEMENT_DEPTH
        val doc = HtmlParser().parse("<div>".repeat(depth) + "visible" + "</div>".repeat(depth))
        val engine = HtmlLayoutEngine(TextMeasurer { text, _ -> TextMetrics(text.length * 8f, 8f, 2f) })
        val page = engine.layout(StyleResolver().resolve(doc), 360f, 0f)
        assertEquals("visible", page.commands.single().text)
    }

    @Test fun oneElementBeyondTheDepthLimitIsRejected() {
        val error = assertThrows(HtmlComplexityException::class.java) {
            HtmlParser().parse("<div>".repeat(HtmlParser.MAX_ELEMENT_DEPTH + 1))
        }
        assertEquals(HtmlComplexityException.Limit.ELEMENT_DEPTH, error.limit)
    }

    @Test fun maximumNodeCountIsAcceptedAndTheNextNodeIsRejected() {
        val parser = HtmlParser()
        assertEquals(HtmlParser.MAX_NODES, parser.parse("<br>".repeat(HtmlParser.MAX_NODES)).children.size)
        val error = assertThrows(HtmlComplexityException::class.java) {
            parser.parse("<br>".repeat(HtmlParser.MAX_NODES + 1))
        }
        assertEquals(HtmlComplexityException.Limit.NODE_COUNT, error.limit)
    }

    @Test fun nodeCountIncludesTextAndElementsButNotDocumentOrClosingTags() {
        val parser = HtmlParser(maxNodes = 2)
        assertEquals(1, parser.parse("<p>x</p></unmatched>").children.size)
        assertEquals(HtmlNode.Document(), parser.parse("<!--comment--><!DOCTYPE html>"))
        assertEquals(2, parser.parse("a<!-- split -->b").children.size)
        val error = assertThrows(HtmlComplexityException::class.java) { parser.parse("<p>x</p>y") }
        assertEquals(HtmlComplexityException.Limit.NODE_COUNT, error.limit)
    }

    @Test fun voidElementsConsumeDepthButDoNotAccumulateIt() {
        val parser = HtmlParser(maxElementDepth = 2)
        parser.parse("<div><br><img><br></div>")
        val error = assertThrows(HtmlComplexityException::class.java) { parser.parse("<div><p><br>") }
        assertEquals(HtmlComplexityException.Limit.ELEMENT_DEPTH, error.limit)
    }

    @Test fun siblingsAndAncestorRecoveryReleaseDepth() {
        val parser = HtmlParser(maxElementDepth = 2)
        parser.parse("<div><p>x</div><div><p>y</p></div>")
        parser.parse("<p>x</p>".repeat(200))
        assertThrows(HtmlComplexityException::class.java) { parser.parse("<div/><p/><b/>") }
    }

    @Test fun hiddenElementsStillConsumeBudgetButRawTextIsOneNode() {
        val parser = HtmlParser(maxNodes = 2)
        val script = parser.parse("<script>" + "<div>".repeat(1000) + "</script>")
        assertEquals(1, script.children.size)
        val error = assertThrows(HtmlComplexityException::class.java) { parser.parse("<head><title>x</title></head>") }
        assertEquals(HtmlComplexityException.Limit.NODE_COUNT, error.limit)
    }

    @Test fun reuseAfterRejectionStartsWithFreshCounters() {
        val parser = HtmlParser(maxElementDepth = 2, maxNodes = 3)
        assertThrows(HtmlComplexityException::class.java) { parser.parse("<div><div><div>") }
        assertEquals(1, parser.parse("<p>x</p>").children.size)
        assertThrows(HtmlComplexityException::class.java) { parser.parse("<br><br><br><br>") }
        assertEquals(1, parser.parse("<p>x</p>").children.size)
    }

    @Test fun nonPositiveLimitsAreRejected() {
        for (limit in listOf(0, -1)) {
            assertThrows(IllegalArgumentException::class.java) { HtmlParser(maxElementDepth = limit) }
            assertThrows(IllegalArgumentException::class.java) { HtmlParser(maxNodes = limit) }
        }
    }
}
