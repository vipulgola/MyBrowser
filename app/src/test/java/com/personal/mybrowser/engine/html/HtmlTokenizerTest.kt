package com.personal.mybrowser.engine.html

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlTokenizerTest {
    @Test fun separatesMarkupFromText() {
        assertEquals(
            listOf(
                HtmlToken.StartTag("p", emptyMap(), false),
                HtmlToken.Text("Hello"),
                HtmlToken.EndTag("p"),
            ),
            HtmlTokenizer("<p>Hello</p>").tokenize(),
        )
    }

    @Test fun readsQuotedUnquotedAndBooleanAttributes() {
        val token = HtmlTokenizer("<A HREF='next.html' title=\"1 > 0\" data-id=AbC disabled>")
            .tokenize().single() as HtmlToken.StartTag
        assertEquals("a", token.name)
        assertEquals(mapOf("href" to "next.html", "title" to "1 > 0", "data-id" to "AbC", "disabled" to ""), token.attributes)
    }

    @Test fun firstDuplicateAttributeWins() {
        val token = HtmlTokenizer("<p ID=first id=second>").tokenize().single() as HtmlToken.StartTag
        assertEquals(mapOf("id" to "first"), token.attributes)
    }

    @Test fun preservesWhitespaceUnicodeAndLiteralLessThan() {
        val text = "  Hello\nनमस्ते 🌱\t2 < 3 &amp; "
        assertEquals(listOf(HtmlToken.Text(text.replace("&amp;", "&"))), HtmlTokenizer(text).tokenize())
    }

    @Test fun skipsCommentsAndDoctype() {
        assertEquals(
            listOf(HtmlToken.Text("before"), HtmlToken.Text("after")),
            HtmlTokenizer("<!DOCTYPE html>before<!-- <p>hidden</p> -->after").tokenize(),
        )
    }

    @Test fun unfinishedCommentConsumesRemainder() {
        assertEquals(listOf(HtmlToken.Text("before")), HtmlTokenizer("before<!-- unfinished").tokenize())
    }

    @Test fun unfinishedTagIsPreservedAsText() {
        val text = "before <a title='unfinished>"
        assertEquals(listOf(HtmlToken.Text(text)), HtmlTokenizer(text).tokenize())
    }

    @Test fun distinguishesSelfClosingSyntaxFromSlashInUnquotedValue() {
        assertEquals(HtmlToken.StartTag("img", mapOf("src" to "photo.png"), true), HtmlTokenizer("<img src=photo.png />").tokenize().single())
        assertEquals(HtmlToken.StartTag("img", mapOf("src" to "photo.png/"), false), HtmlTokenizer("<img src=photo.png/>").tokenize().single())
    }

    @Test(timeout = 1000) fun malformedAttributeStillMakesProgress() {
        assertEquals(
            HtmlToken.StartTag("p", mapOf("broken" to ""), false),
            HtmlTokenizer("<p =broken>").tokenize().single(),
        )
    }

    @Test fun emptyInputProducesNoTokens() {
        assertEquals(emptyList<HtmlToken>(), HtmlTokenizer("").tokenize())
    }
}
