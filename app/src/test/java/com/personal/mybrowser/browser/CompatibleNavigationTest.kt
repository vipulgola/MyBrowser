package com.personal.mybrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibleNavigationTest {
    @Test fun userInputResolvesToHttpsOrSearch() {
        assertEquals("https://example.com/", CompatibleNavigation.address("example.com"))
        assertEquals(
            "https://lite.duckduckgo.com/lite/?q=private+browser",
            CompatibleNavigation.address("private browser"),
        )
    }

    @Test fun topLevelNavigationRejectsInsecureAndLocalSchemes() {
        assertTrue(CompatibleNavigation.isAllowedNavigation("https://example.com/path"))
        listOf("http://example.com/", "file:///secret", "content://example/item", "javascript:alert(1)",
            "https://user:pass@example.com/", "https://example.com\\evil").forEach {
            assertFalse(it, CompatibleNavigation.isAllowedNavigation(it))
        }
    }
}
