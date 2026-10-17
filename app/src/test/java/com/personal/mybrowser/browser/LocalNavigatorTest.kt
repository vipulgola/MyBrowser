package com.personal.mybrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNavigatorTest {
    private val pages = mapOf("index.html" to "<h1>Home</h1>", "about.html" to "<h1>About</h1>")
    private fun start() = LocalNavigator(pages, "index.html")

    @Test fun startsOnTheRequestedPageWithNoBackEntry() {
        val navigator = start()
        assertEquals("index.html", navigator.currentPath)
        assertEquals("<h1>Home</h1>", navigator.currentHtml)
        assertFalse(navigator.canGoBack)
        assertSame(navigator, navigator.back())
    }

    @Test fun visitingAPageAddsAVisitWithoutChangingThePreviousState() {
        val home = start()
        val about = home.navigate("about.html")
        assertNotSame(home, about)
        assertEquals("index.html", home.currentPath)
        assertFalse(home.canGoBack)
        assertEquals("about.html", about.currentPath)
        assertEquals("<h1>About</h1>", about.currentHtml)
        assertTrue(about.canGoBack)
        assertEquals("index.html", about.back().currentPath)
        assertFalse(about.back().canGoBack)
    }

    @Test fun unknownAndExternalTargetsCannotChangeThePageOrHistory() {
        val about = start().navigate("about.html")
        for (target in listOf("missing.html", "https://example.com", "javascript:alert(1)", "../index.html", "about.html#section")) {
            assertSame(about, about.navigate(target))
        }
        assertFalse(about.back().canGoBack)
    }

    @Test fun openingHomeViaALinkIsANewVisitRatherThanABackAction() {
        val homeAgain = start().navigate("about.html").navigate("index.html")
        assertEquals("index.html", homeAgain.currentPath)
        assertEquals("about.html", homeAgain.back().currentPath)
        assertEquals("index.html", homeAgain.back().back().currentPath)
        assertFalse(homeAgain.back().back().canGoBack)
    }

    @Test fun repeatedVisitsToTheSamePageRemainSeparateHistoryEntries() {
        val samePage = start().navigate("index.html")
        assertTrue(samePage.canGoBack)
        assertFalse(samePage.back().canGoBack)
    }

    @Test fun sourceMapMutationDoesNotChangeAnExistingSession() {
        val mutablePages = pages.toMutableMap()
        val navigator = LocalNavigator(mutablePages, "index.html")
        mutablePages.clear()
        assertEquals("<h1>About</h1>", navigator.navigate("about.html").currentHtml)
    }

    @Test(expected = IllegalArgumentException::class) fun initialPageMustExist() {
        LocalNavigator(pages, "missing.html")
    }
}
