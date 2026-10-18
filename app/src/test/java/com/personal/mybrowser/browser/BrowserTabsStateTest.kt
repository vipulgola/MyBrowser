package com.personal.mybrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTabsStateTest {
    @Test fun tabsKeepSeparateAddressesAndExternalLinksUseSelectedTab() {
        val tabs = BrowserTabsState()
        val first = tabs.selectedTab.id
        tabs.updateUrl(first, "https://example.com/")
        val second = tabs.openTab().id
        tabs.updateUrl(second, "https://www.wikipedia.org/")

        tabs.selectTab(first)
        assertEquals("https://example.com/", tabs.selectedTab.url)
        tabs.route(BrowserNavigation(7L, "https://developer.android.com/"))

        assertEquals("https://developer.android.com/", tabs.selectedTab.url)
        assertEquals(7L, tabs.selectedTab.navigationRequest?.id)
        tabs.selectTab(second)
        assertEquals("https://www.wikipedia.org/", tabs.selectedTab.url)
        assertEquals(null, tabs.selectedTab.navigationRequest)
    }

    @Test fun closingSelectedTabChoosesNeighborAndLastCloseCreatesEmptyTab() {
        val tabs = BrowserTabsState()
        val first = tabs.selectedTab.id
        tabs.updateUrl(first, "https://example.com/")
        val second = tabs.openTab().id
        tabs.updateUrl(second, "https://www.wikipedia.org/")
        val third = tabs.openTab().id

        assertEquals(second, tabs.closeTab(third).id)
        assertEquals(first, tabs.closeTab(second).id)
        assertEquals(null, tabs.closeTab(first).url)
        assertEquals(1, tabs.tabs.size)
        assertTrue(tabs.selectedTab.id > third)
    }

    @Test fun popupAddressStartsOnlyInNewTab() {
        val tabs = BrowserTabsState()
        val original = tabs.selectedTab.id
        tabs.updateUrl(original, "https://example.com/")

        val popup = tabs.openTab("https://www.wikipedia.org/")

        assertEquals("https://www.wikipedia.org/", popup.url)
        assertEquals("https://www.wikipedia.org/", popup.navigationRequest?.url)
        assertTrue(popup.navigationRequest!!.id < 0)
        tabs.selectTab(original)
        assertEquals("https://example.com/", tabs.selectedTab.url)
    }
}
