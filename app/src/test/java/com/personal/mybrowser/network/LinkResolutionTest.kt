package com.personal.mybrowser.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LinkResolutionTest {
    private val base = HttpsAddress.parseInput("https://example.com/docs/start?old=1#old")

    @Test fun relativeAndRootPathsResolveAgainstTheDocument() {
        val cases = mapOf(
            "guide" to "/docs/guide", "./guide" to "/docs/guide",
            "../guide" to "/guide", "/about" to "/about",
            "../../../../guide" to "/guide", "." to "/docs/", ".." to "/",
            "/a/./b/../c/" to "/a/c/", "/a//b" to "/a//b",
            "/a//../b" to "/a/b", "other.com" to "/docs/other.com",
        )
        cases.forEach { (link, path) -> assertEquals(link, "https://example.com$path", base.resolveLink(link).value) }
    }

    @Test fun trailingSlashOnBaseChangesTheRelativeDirectory() {
        assertEquals("https://example.com/docs/start/guide", HttpsAddress.parseInput("example.com/docs/start/").resolveLink("guide").value)
    }

    @Test fun absoluteAndProtocolRelativeLinksCanChangeHosts() {
        assertEquals("https://other.com/guide", base.resolveLink(" HTTPS://OTHER.COM:443/a/../guide ").value)
        assertEquals("https://other.com:8443/guide", base.resolveLink("//OTHER.COM:8443/guide").value)
        assertEquals("https://[::1]/", base.resolveLink("//[::1]").value)
    }

    @Test fun queryOnlyLinksKeepTheDocumentPathAndReplaceTheQuery() {
        assertEquals("https://example.com/docs/start?page=2", base.resolveLink("?page=2").value)
        assertEquals("https://example.com/docs/start?", base.resolveLink("?").value)
        assertEquals("https://example.com/docs/guide", base.resolveLink("guide").value)
    }

    @Test fun emptyAndFragmentReferencesKeepThePathAndQuery() {
        assertEquals("https://example.com/docs/start?old=1", base.resolveLink("").value)
        val address = base.resolveLink("#new")
        assertEquals("https://example.com/docs/start?old=1#new", address.value)
        assertEquals("https://example.com/docs/start?old=1", address.requestUrl)
        assertEquals("https://example.com/docs/start?old=1#", base.resolveLink("#").value)
    }

    @Test fun escapesAndUnicodeArePreservedWithoutInterpretingEncodedDotsOrSlashes() {
        assertEquals("https://example.com/docs/%2E%2E/A%2Fb?q=%23#part", base.resolveLink("%2E%2E/A%2Fb?q=%23#part").value)
        assertEquals("https://example.com/docs/%E2%82%AC", base.resolveLink("€").value)
        assertEquals("https://example.com/docs/guide?q=/../x#/./y", base.resolveLink("guide?q=/../x#/./y").value)
    }

    @Test fun unsupportedSchemesAreNeverReinterpretedAsAddressBarHosts() {
        listOf("http://other.com", "javascript:alert(1)", "javascript:123", "mailto:a@b.com", "data:text/html,hello", "file:///tmp/a", "other.com:8443/page").forEach(::reject)
    }

    @Test fun malformedAndCredentialBearingLinksAreRejected() {
        listOf("https:other.com", "https:/other.com", "///other.com", "//", "//user:pass@other.com/", "//bad_host.test", "//other.com:0/", "//other.com:65536/", "//other.com:/", "%zz", "a b", "a\nb", "a\u0000b", "a\\b").forEach(::reject)
    }

    private fun reject(link: String) {
        assertThrows(link, AddressValidationException::class.java) { base.resolveLink(link) }
    }
}
