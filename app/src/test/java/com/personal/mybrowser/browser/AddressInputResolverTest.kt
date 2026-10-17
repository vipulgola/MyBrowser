package com.personal.mybrowser.browser

import com.personal.mybrowser.network.AddressValidationException
import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AddressInputResolverTest {
    @Test fun addressesKeepExistingHttpsNormalization() {
        val cases = mapOf(
            " EXAMPLE.COM:443 " to "https://example.com/",
            "example.com/path?q=x#part" to "https://example.com/path?q=x#part",
            "//example.org/" to "https://example.org/",
            "localhost:8443" to "https://localhost:8443/",
            "localhost" to "https://localhost/",
            "[::1]:8443" to "https://[::1]:8443/",
            "192.0.2.1" to "https://192.0.2.1/",
        )
        cases.forEach { (input, expected) -> assertEquals(expected, AddressInputResolver.resolve(input).value) }
    }

    @Test fun singleWordsAndPhrasesBecomeSearches() {
        for (query in listOf("kotlin", "android development", "what is example.com", "android/kotlin", "नमस्ते दुनिया", "C++ & Kotlin #1 50% 😀")) {
            val uri = URI(AddressInputResolver.resolve(" $query ").value)
            assertEquals("https", uri.scheme)
            assertEquals("lite.duckduckgo.com", uri.host)
            assertEquals("/lite/", uri.path)
            assertEquals(query, URLDecoder.decode(uri.rawQuery.removePrefix("q="), "UTF-8"))
            assertEquals(null, uri.rawFragment)
            assertEquals(1, uri.rawQuery.split('&').size)
        }
    }

    @Test fun reservedCharactersCannotInjectSearchParameters() {
        assertEquals("https://lite.duckduckgo.com/lite/?q=C%2B%2B+%26+Kotlin+%231", AddressInputResolver.resolve("C++ & Kotlin #1").value)
        assertEquals("https://lite.duckduckgo.com/lite/?q=hello+%26q%3Dother", AddressInputResolver.resolve("hello &q=other").value)
    }

    @Test fun malformedExplicitAddressesAreRejectedInsteadOfSearched() {
        for (input in listOf("http://example.com", "javascript:alert(1)", "file:///tmp/a", "https:bad", "https://", "https://example.com/a b", "example.com:0", "user:secret@example.com", "user@example.com", "bad_host.test", "/relative")) {
            assertThrows(input, AddressValidationException::class.java) { AddressInputResolver.resolve(input) }
        }
    }

    @Test fun emptyInputControlsAndBackslashesAreRejected() {
        for (input in listOf("", "   ", "hello\nworld", "a\u0000b", "a\\b")) {
            assertThrows(input, AddressValidationException::class.java) { AddressInputResolver.resolve(input) }
        }
    }
}
