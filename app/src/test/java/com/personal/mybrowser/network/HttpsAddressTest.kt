package com.personal.mybrowser.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HttpsAddressTest {
    @Test fun omittedSchemeAndRootPathAreAdded() {
        assertEquals("https://example.com/", parse("  example.com  ").value)
        assertEquals("https://www.example.com/", parse("www.example.com").value)
        assertEquals("https://example.com/", parse("//example.com").value)
    }

    @Test fun schemeHostAndDefaultPortNormalizeWithoutChangingPathCase() {
        assertEquals("https://example.com/Path?Case=Yes", parse("HTTPS://EXAMPLE.COM:443/Path?Case=Yes").value)
    }

    @Test fun hostPortInputsAndIpv6AreSupported() {
        assertEquals("https://example.com:8443/page", parse("example.com:8443/page").value)
        assertEquals("https://localhost:8443/", parse("localhost:8443").value)
        assertEquals("https://[::1]:8443/", parse("[::1]:8443").value)
        assertEquals("https://192.0.2.1:8443/", parse("192.0.2.1:8443").value)
    }

    @Test fun escapedPathAndQueryArePreservedWithoutDoubleEncoding() {
        val value = "https://example.com/A%2Fb%20c?q=a%2Bb&x=one+two"
        assertEquals(value, parse(value).value)
        assertEquals("https://example.com/%E2%82%AC", parse("https://example.com/€").value)
    }

    @Test fun fragmentRemainsInTheAddressButIsNotSentInHttp() {
        val address = parse("example.com/page?q=%23#section")
        assertEquals("https://example.com/page?q=%23#section", address.value)
        assertEquals("https://example.com/page?q=%23", address.requestUrl)
    }

    @Test fun unsupportedSchemesAndOpaqueHttpsAreRejected() {
        for (input in listOf("http://example.com", "ftp://example.com", "file:///tmp/a", "data:text/html,hi", "javascript:alert(1)", "javascript:123", "mailto:me@example.com", "https:example.com")) reject(input)
    }

    @Test fun missingOrMalformedHostIsRejected() {
        for (input in listOf("", "  ", "https://", "https:///example.com", "/page", "https://bad_host.test", "https://example.com%2Fevil.test", "https://example.com/%zz")) reject(input)
    }

    @Test fun embeddedCredentialsAreRejected() {
        for (input in listOf("https://user@example.com", "https://user:password@example.com", "user@example.com")) reject(input)
    }

    @Test fun invalidPortsAreRejected() {
        for (input in listOf("https://example.com:0", "https://example.com:65536", "https://example.com:-1", "https://example.com:abc", "https://example.com:")) reject(input)
    }

    @Test fun embeddedWhitespaceControlsAndBackslashesAreRejected() {
        for (input in listOf("example.com/a b", "https://example.com/a\nb", "https://example.com/a\u0000b", "https://example.com\\evil", "https://example.com/a\u00a0b")) reject(input)
    }

    @Test fun asciiPunycodeHostsAreAcceptedAndUnicodeHostsAreExplicitlyUnsupported() {
        assertEquals("https://xn--bcher-kva.de/", parse("xn--bcher-kva.de").value)
        reject("https://bücher.de/")
    }

    @Test fun normalizedAddressesCompareByValue() {
        val first = parse("EXAMPLE.COM:443")
        val second = parse("https://example.com/")
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test fun syntacticValidationDoesNotRequireAnExistingDomain() {
        assertEquals("https://does-not-exist.invalid/", parse("does-not-exist.invalid").value)
    }

    private fun parse(input: String) = HttpsAddress.parseInput(input)
    private fun reject(input: String) {
        assertThrows(input, AddressValidationException::class.java) { parse(input) }
    }
}
