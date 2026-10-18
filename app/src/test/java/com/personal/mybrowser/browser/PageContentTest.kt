package com.personal.mybrowser.browser

import com.personal.mybrowser.engine.html.HtmlNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageContentTest {
    @Test fun normalHtmlSuppliesADocumentToTheRenderer() {
        val content = PageContent.parse("<p>Hi &amp; welcome</p>") as PageContent.Ready
        val paragraph = content.document.children.single() as HtmlNode.Element
        assertEquals(listOf(HtmlNode.Text("Hi & welcome")), paragraph.children)
    }

    @Test fun bothComplexityFixturesBecomeAnErrorInsteadOfAPartialDocument() {
        for (html in listOf(TestHtmlFixtures.tooDeep, TestHtmlFixtures.tooManyNodes)) {
            assertEquals(PageContent.TooComplex, PageContent.parse(html))
        }
    }

    @Test fun validPageCanBeParsedAfterRejection() {
        PageContent.parse(TestHtmlFixtures.tooDeep)
        assertTrue(PageContent.parse("<p>Okay</p>") is PageContent.Ready)
    }
}
