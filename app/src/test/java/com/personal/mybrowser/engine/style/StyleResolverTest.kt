package com.personal.mybrowser.engine.style

import com.personal.mybrowser.engine.html.HtmlNode
import com.personal.mybrowser.engine.html.HtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleResolverTest {
    @Test fun boldChildInheritsColourAndSizeFromItsParagraph() {
        val paragraph = resolve("<p style='color:#1565c0;font-size:20px'>a<b>b</b>c</p>").children.single()
        assertEquals(TextStyle(20f, color = TextColor.Argb(0xFF1565C0.toInt())), paragraph.style.text)
        val bold = paragraph.children[1]
        assertTrue(bold.style.text.bold)
        assertEquals(paragraph.style.text.color, bold.children.single().style.text.color)
        assertEquals(20f, bold.children.single().style.text.fontSizePx, 0.001f)
        assertFalse(paragraph.children.last().style.text.bold)
    }

    @Test fun aChildOverrideDoesNotChangeItsParentOrFollowingSibling() {
        val paragraph = resolve("<p style='color:#123;font-size:20px'><span style='color:#456;font-size:14px'>small</span>large</p>").children.single()
        assertEquals(14f, paragraph.children.first().style.text.fontSizePx, 0.001f)
        assertEquals(TextColor.Argb(0xFF445566.toInt()), paragraph.children.first().style.text.color)
        assertEquals(paragraph.style.text, paragraph.children.last().style.text)
    }

    @Test fun inlineFontSizeOverridesHeadingDefaultWhileKeepingBold() {
        val heading = resolve("<h1 style='font-size:18px'>title</h1>").children.single()
        assertEquals(18f, heading.style.text.fontSizePx, 0.001f)
        assertTrue(heading.style.text.bold)
    }

    @Test fun headingDefaultScalesTheInheritedFont() {
        val heading = resolve("<div style='font-size:24px'><h1>title</h1></div>").children.single().children.single()
        assertEquals(48f, heading.style.text.fontSizePx, 0.001f)
    }

    @Test fun defaultLinkColourOverridesInheritedColourButInlineColourCanOverrideIt() {
        val paragraph = resolve("<p style='color:#123'><a href='about.html'>link</a><a href='about.html' style='color:#456'><b>custom</b></a></p>").children.single()
        assertEquals(TextColor.Link, paragraph.children[0].style.text.color)
        assertEquals(TextColor.Argb(0xFF445566.toInt()), paragraph.children[1].children.single().children.single().style.text.color)
    }

    @Test fun displayAndSpacingDoNotInheritLikeTextProperties() {
        val paragraph = resolve("<p>text<span>inline</span><br></p>").children.single()
        assertEquals(Display.BLOCK, paragraph.style.display)
        assertTrue(paragraph.style.bottomGapPx > 0f)
        assertEquals(Display.INLINE, paragraph.children[1].style.display)
        assertEquals(0f, paragraph.children[1].style.bottomGapPx, 0.001f)
        assertTrue(paragraph.children.last().style.lineBreak)
    }

    @Test fun metadataIsHidden() {
        val head = resolve("<head><title>hidden</title></head>").children.single()
        assertEquals(Display.NONE, head.style.display)
        assertTrue(head.children.isEmpty())
    }

    @Test fun cssFontPixelsAreConvertedExactlyOnce() {
        val document = HtmlParser().parse("<p style='font-size:20px'><b>child</b></p>")
        val paragraph = StyleResolver(baseFontSizePx = 32f, cssPixelScale = 2f).resolve(document).children.single()
        assertEquals(40f, paragraph.style.text.fontSizePx, 0.001f)
        assertEquals(40f, paragraph.children.single().children.single().style.text.fontSizePx, 0.001f)
    }

    @Test fun stylingDoesNotMutateTheSourceDocument() {
        val document = HtmlParser().parse("<p style='font-size:20px'>unchanged</p>")
        val expected = HtmlParser().parse("<p style='font-size:20px'>unchanged</p>")
        val first = StyleResolver(16f).resolve(document)
        val second = StyleResolver(10f).resolve(document)
        assertSame(document, first.source)
        assertEquals(expected, document)
        assertEquals(TextStyle(16f), first.style.text)
        assertEquals(TextStyle(10f), second.style.text)
        assertTrue(first.children.single().source is HtmlNode.Element)
    }

    @Test fun invalidDeclarationsFallBackToInheritedValues() {
        val paragraph = resolve("<div style='font-size:20px;color:#abc'><p style='font-size:-1px;color:bad'>text</p></div>").children.single().children.single()
        assertEquals(TextStyle(20f, color = TextColor.Argb(0xFFAABBCC.toInt())), paragraph.style.text)
    }

    private fun resolve(html: String) = StyleResolver().resolve(HtmlParser().parse(html))
}
