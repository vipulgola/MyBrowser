package com.personal.mybrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteIdentityTest {
    @Test fun showsTheActualHostInsteadOfANameHiddenInTheUrl() {
        assertEquals("login.example.com", SiteIdentity.hostname("https://login.example.com/sign-in"))
        assertEquals("attacker.example", SiteIdentity.hostname("https://attacker.example/bank.example/sign-in"))
        assertEquals("bank.example.attacker.example", SiteIdentity.hostname("https://bank.example.attacker.example/"))
    }

    @Test fun doesNotPresentAnInvalidAddressAsASiteIdentity() {
        assertNull(SiteIdentity.hostname(null))
        assertNull(SiteIdentity.hostname("http://example.com/"))
        assertNull(SiteIdentity.hostname("https://user@example.com/"))
    }
}
