package com.personal.mybrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingWebLinkTest {
    @Test fun preservesHttpsLinks() {
        assertEquals("https://example.com/path?q=1", IncomingWebLink.resolve("https://example.com/path?q=1")?.value)
    }

    @Test fun upgradesHttpLinksBeforeNavigation() {
        assertEquals("https://example.com/", IncomingWebLink.resolve("http://example.com")?.value)
    }

    @Test fun rejectsOtherSchemesAndInvalidAddresses() {
        assertNull(IncomingWebLink.resolve("javascript:alert(1)"))
        assertNull(IncomingWebLink.resolve("content://example.com/item"))
        assertNull(IncomingWebLink.resolve("https://user:pass@example.com"))
        assertNull(IncomingWebLink.resolve("http://"))
        assertNull(IncomingWebLink.resolve(null))
    }
}
