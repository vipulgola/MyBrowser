package com.personal.mybrowser.engine.html

import com.personal.mybrowser.engine.layout.HtmlLayoutEngine
import com.personal.mybrowser.engine.layout.TextMeasurer
import com.personal.mybrowser.engine.layout.TextMetrics
import com.personal.mybrowser.engine.style.StyleResolver
import com.personal.mybrowser.network.HttpsAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HtmlEntityDecoderTest {
    @Test fun commonNamedReferencesDecodeWithCaseSensitiveNames() {
        assertEquals("& < > \" ' \u00a0 © ® ™ … – — • « » € £ ¥ ¢", decode("&amp; &lt; &gt; &quot; &apos; &nbsp; &copy; &reg; &trade; &hellip; &ndash; &mdash; &bull; &laquo; &raquo; &euro; &pound; &yen; &cent;"))
        assertEquals("& < > \" &Amp; &NBSP;", decode("&AMP; &LT; &GT; &QUOT; &Amp; &NBSP;"))
    }

    @Test fun decimalHexAndSupplementaryCharactersDecode() {
        assertEquals("AAA 😀 😀", decode("&#65;&#x41;&#X0041; &#128512; &#x1F600;"))
        assertEquals("A", decode("&#" + "0".repeat(100) + "65;"))
    }

    @Test fun invalidScalarsAndIntegerOverflowBecomeReplacementCharacters() {
        for (value in listOf("&#0;", "&#xD800;", "&#xDFFF;", "&#1114112;", "&#x110000;", "&#999999999999999999999;")) {
            assertEquals(value, "\ufffd", decode(value))
        }
        assertEquals(String(Character.toChars(0x10ffff)), decode("&#x10FFFF;"))
    }

    @Test fun legacyNumericReferencesUseHtmlReplacements() {
        assertEquals("€ ‘ ’ “ ” — ™", decode("&#128; &#x91; &#146; &#147; &#x94; &#151; &#153;"))
        assertEquals("\t\n\r\u0081", decode("&#9;&#10;&#13;&#129;"))
    }

    @Test fun unknownMalformedAndMissingSemicolonReferencesRemainLiteral() {
        val value = "&unknown; &amp &lt &#65 &#x41 &; &#; &#x; &#-1; &#+65; &#xGG; &#１２;"
        assertEquals(value, decode(value))
    }

    @Test fun decodingIsSinglePassAndPreservesUnrelatedText() {
        assertEquals("&lt;b&gt; &amp; 😀 नमस्ते", decode("&amp;lt;b&amp;gt; &amp;amp; 😀 नमस्ते"))
        assertEquals("", decode(""))
        assertEquals("a & b && ©", decode("a & b && &copy;"))
    }

    @Test fun decodedMarkupDoesNotBecomeElements() {
        val document = HtmlParser().parse("<p>&lt;b&gt;Hello&lt;/b&gt;</p>")
        val paragraph = document.children.single() as HtmlNode.Element
        assertEquals(listOf(HtmlNode.Text("<b>Hello</b>")), paragraph.children)
    }

    @Test fun decodedQuotesAndSpacesCannotCreateExtraAttributes() {
        val token = HtmlTokenizer("<a title='&quot; &apos; &gt;' href=/?a=1&amp;b=2 data-x=one&#32;two>").tokenize().single() as HtmlToken.StartTag
        assertEquals(mapOf("title" to "\" ' >", "href" to "/?a=1&b=2", "data-x" to "one two"), token.attributes)
        val quoted = HtmlTokenizer("<p title=\"a&quot; id=&quot;fake\" id=real>").tokenize().single() as HtmlToken.StartTag
        assertEquals(mapOf("title" to "a\" id=\"fake", "id" to "real"), quoted.attributes)
    }

    @Test fun namesAreNotDecodedAndFirstDuplicateAttributeStillWins() {
        val token = HtmlTokenizer("<p data&amp;x='&amp;' href='a&amp;b' href='ignored'>").tokenize().single() as HtmlToken.StartTag
        assertEquals(mapOf("data&amp;x" to "&", "href" to "a&b"), token.attributes)
        val tag = HtmlTokenizer("<p&amp;>").tokenize().single() as HtmlToken.StartTag
        assertEquals("p&amp;", tag.name)
    }

    @Test fun referencesCannotJoinAcrossMarkupBoundaries() {
        assertEquals(listOf(HtmlToken.Text("&am"), HtmlToken.Text("p;")), HtmlTokenizer("&am<!--x-->p;").tokenize())
    }

    @Test fun decodedTextAndLinkTargetsReachLayoutAndUrlResolution() {
        val document = HtmlParser().parse("<a href='/search?q=android&amp;page=2'>&lt;b&gt;Next&lt;/b&gt;</a>")
        val engine = HtmlLayoutEngine(TextMeasurer { text, _ -> TextMetrics(text.length * 8f, 8f, 2f) })
        val page = engine.layout(StyleResolver().resolve(document), 500f, 0f)
        assertEquals("<b>Next</b>", page.commands.joinToString("") { it.text })
        assertFalse(page.commands.single().style.bold)
        val command = page.commands.single()
        val href = page.linkAt(1f, command.baselineY)!!
        assertEquals("https://example.com/search?q=android&page=2", HttpsAddress.parseInput("example.com").resolveLink(href).value)
    }

    @Test(timeout = 2000) fun longUnterminatedReferencesAndLongNumbersMakeProgress() {
        val value = "&".repeat(10000) + "a".repeat(10000)
        assertEquals(value, decode(value))
        assertEquals("\ufffd", decode("&#" + "9".repeat(10000) + ";"))
    }

    private fun decode(value: String) = HtmlEntityDecoder.decode(value)
}
