package com.personal.mybrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockRulesTest {
    private val rules = AdBlockRules.parse(
        sequenceOf(
            "# source comment",
            "127.0.0.1 doubleclick.net",
            "0.0.0.0 ad.example.com",
            "127.0.0.1 malformed host",
            "not-an-address tracker.example",
        )
    )

    @Test fun blocksOnlyExactHostsAndTheirSubdomains() {
        assertEquals(2, rules.size)
        assertTrue(rules.blocks("https://doubleclick.net/ad.js", isMainFrame = false))
        assertTrue(rules.blocks("https://a.doubleclick.net/banner", isMainFrame = false))
        assertTrue(rules.blocks("https://ad.example.com/image", isMainFrame = false))
        assertFalse(rules.blocks("https://notdoubleclick.net/ad.js", isMainFrame = false))
        assertFalse(rules.blocks("https://example.com/doubleclick.net/ad.js", isMainFrame = false))
        assertFalse(rules.blocks("https://example.com/?next=doubleclick.net", isMainFrame = false))
    }

    @Test fun doesNotBlockIntentionalPageNavigationOrNonWebSchemes() {
        assertFalse(rules.blocks("https://doubleclick.net/", isMainFrame = true))
        assertFalse(rules.blocks("data:text/plain,doubleclick.net", isMainFrame = false))
        assertFalse(rules.blocks("not a url", isMainFrame = false))
    }
}
