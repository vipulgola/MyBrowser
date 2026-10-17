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
        for (path in listOf(DemoPages.TOO_DEEP, DemoPages.TOO_MANY_NODES)) {
            assertEquals(PageContent.TooComplex, PageContent.parse(DemoPages.pages.getValue(path)))
        }
    }

    @Test fun backFromARejectedLocalPageRestoresThePreviousPage() {
        val browser = BrowserViewModel()
        for (path in listOf(DemoPages.TOO_DEEP, DemoPages.TOO_MANY_NODES)) {
            assertTrue(browser.open(path))
            assertEquals(PageContent.TooComplex, PageContent.parse(browser.navigator.currentHtml))
            browser.back()
            assertEquals(DemoPages.HOME, browser.navigator.currentPath)
            assertTrue(PageContent.parse(browser.navigator.currentHtml) is PageContent.Ready)
        }
    }

    @Test fun validPageCanBeParsedAfterRejection() {
        PageContent.parse(DemoPages.pages.getValue(DemoPages.TOO_DEEP))
        assertTrue(PageContent.parse(DemoPages.pages.getValue(DemoPages.ABOUT)) is PageContent.Ready)
    }
}
