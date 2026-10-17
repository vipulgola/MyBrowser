package com.personal.mybrowser.engine.html

import com.personal.mybrowser.engine.layout.HtmlLayoutEngine
import com.personal.mybrowser.engine.layout.TextMeasurer
import com.personal.mybrowser.engine.layout.TextMetrics
import com.personal.mybrowser.engine.style.StyleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TextElementTest {
    private val names = listOf("script", "style", "title", "textarea")

    @Test fun scriptAndStylePreserveMarkupCommentsAndReferencesLiterally() {
        val content = "<b>x</b><!-- comment --><!DOCTYPE html> &amp; &#65; a < b"
        for (name in listOf("script", "style")) {
            assertEquals(content, element("<$name>$content</$name>").children.single().let { (it as HtmlNode.Text).data })
        }
    }

    @Test fun titleAndTextareaDecodeReferencesWithoutParsingMarkup() {
        for (name in listOf("title", "textarea")) {
            val node = element("<$name>Tom &amp; Jerry <b>text</b> &lt;i&gt; &#x1F600; &amp;lt;</$name>")
            assertEquals(listOf(HtmlNode.Text("Tom & Jerry <b>text</b> <i> 😀 &lt;")), node.children)
        }
    }

    @Test fun unrelatedEndTagsCannotCloseAncestors() {
        for (name in names) {
            val doc = HtmlParser().parse("<div><$name></div><p>inside</p></$name><p>after</p></div>")
            val outer = doc.children.single() as HtmlNode.Element
            assertEquals(listOf(name, "p"), outer.children.map { (it as HtmlNode.Element).tagName })
            assertEquals(listOf(HtmlNode.Text("</div><p>inside</p>")), (outer.children.first() as HtmlNode.Element).children)
            assertEquals(listOf(HtmlNode.Text("after")), (outer.children.last() as HtmlNode.Element).children)
        }
    }

    @Test fun closingNamesAreAsciiCaseInsensitiveAndRequireADelimiter() {
        for (name in names) {
            val content = "</${name}x></${name}-suffix></$name=bad>"
            for (ending in listOf("></p>", " >", "\t>", "\n>", "\r>", "\u000c>", "/>", " ignored='>'>")) {
                val tokens = HtmlTokenizer("<${name.uppercase()}>$content</${name.uppercase()}$ending<p>after</p>").tokenize()
                assertEquals(HtmlToken.Text(content), tokens[1])
                assertEquals(HtmlToken.EndTag(name), tokens[2])
                assertEquals(HtmlToken.Text("after"), tokens[tokens.lastIndex - 1])
            }
        }
        val script = element("<script></ſcript>still inside</script>")
        assertEquals(listOf(HtmlNode.Text("</ſcript>still inside")), script.children)
    }

    @Test fun encodedClosingTagDoesNotEndTextMode() {
        for (name in names) {
            val raw = "&lt;/$name&gt;<p>still text</p>"
            val expected = if (name == "title" || name == "textarea") "</$name><p>still text</p>" else raw
            assertEquals(listOf(HtmlNode.Text(expected)), element("<$name>$raw</$name>").children)
        }
    }

    @Test fun quotesInsideTextDoNotHideAnActualClosingTag() {
        for (name in names) {
            val doc = HtmlParser().parse("<$name>\"</$name><p>after</p>")
            assertEquals(listOf(HtmlNode.Text("\"")), (doc.children[0] as HtmlNode.Element).children)
            assertEquals("p", (doc.children[1] as HtmlNode.Element).tagName)
        }
    }

    @Test fun selfClosingSyntaxStillEntersTextModeAndAttributesDecodeNormally() {
        for (name in names) {
            val node = element("<$name data-x='&amp;' />literal <b>text</b></$name>")
            assertEquals(mapOf("data-x" to "&"), node.attributes)
            assertEquals(listOf(HtmlNode.Text("literal <b>text</b>")), node.children)
        }
    }

    @Test fun missingOrUnfinishedClosingTagsConsumeRemainingText() {
        for (name in names) {
            for (ending in listOf("", "</$name", "</$name ", "</$name attr='unfinished>")) {
                val content = "text <p>inside</p>$ending"
                assertEquals(listOf(HtmlNode.Text(content)), element("<$name>$content").children)
            }
        }
    }

    @Test fun emptyElementsAndAdjacentModesResumeNormalTokenization() {
        val doc = HtmlParser().parse("<script></script><style>&amp;</style><title>&amp;</title><textarea></textarea><p>&amp;</p>")
        assertEquals(5, doc.children.size)
        assertEquals(emptyList<HtmlNode>(), (doc.children[0] as HtmlNode.Element).children)
        assertEquals(listOf(HtmlNode.Text("&amp;")), (doc.children[1] as HtmlNode.Element).children)
        assertEquals(listOf(HtmlNode.Text("&")), (doc.children[2] as HtmlNode.Element).children)
        assertEquals(emptyList<HtmlNode>(), (doc.children[3] as HtmlNode.Element).children)
        assertEquals(listOf(HtmlNode.Text("&")), (doc.children[4] as HtmlNode.Element).children)
    }

    @Test fun layoutHidesScriptStyleAndTitleButShowsTextareaAsLiteralText() {
        val html = """
            <script>"</p><b>hidden script</b>" &amp;</script>
            <style>p { content: "</div><a href='bad'>hidden style</a>"; }</style>
            <title>hidden <b>title</b> &amp;</title>
            <p>Visible</p><textarea>Tom &amp; Jerry: <b>literal</b></textarea>
        """.trimIndent()
        val engine = HtmlLayoutEngine(TextMeasurer { text, _ -> TextMetrics(text.length * 8f, 8f, 2f) })
        val page = engine.layout(StyleResolver().resolve(HtmlParser().parse(html)), 1000f, 0f)
        val visible = page.commands.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ")
        assertEquals("Visible Tom & Jerry: <b>literal</b>", visible)
        assertFalse(page.commands.any { it.style.bold || it.linkTarget != null })
    }

    @Test(timeout = 2000) fun manyNonMatchingCandidatesMakeProgress() {
        val content = "</scriptx>".repeat(10000)
        assertEquals(listOf(HtmlNode.Text(content)), element("<script>$content</script>").children)
    }

    private fun element(source: String) = HtmlParser().parse(source).children.single() as HtmlNode.Element
}
