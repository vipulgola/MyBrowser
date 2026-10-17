package com.personal.mybrowser.browser

import com.personal.mybrowser.network.AddressValidationException
import com.personal.mybrowser.network.HttpsAddress
import java.net.URLEncoder

/** Classifies submitted input only; no network activity or suggestions while typing. */
object AddressInputResolver {
    private const val SEARCH_URL = "https://lite.duckduckgo.com/lite/?q="
    private val schemePrefix = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

    fun resolve(input: String): HttpsAddress {
        if (input.any { Character.isISOControl(it) || it == '\\' }) {
            throw AddressValidationException("The input contains invalid characters.")
        }
        val text = input.trim()
        if (text.isEmpty()) throw AddressValidationException("Enter a website address or search terms.")
        val hasSpace = text.any { it.isWhitespace() || Character.isSpaceChar(it) }
        val host = text.substringBefore('/').substringBefore('?').substringBefore('#')
        val looksLikeAddress = schemePrefix.containsMatchIn(text) || text.startsWith('/') ||
            text.startsWith('[') || (!hasSpace && (
                '.' in host || ':' in host || '@' in host || host.equals("localhost", ignoreCase = true)
            ))
        // Malformed URL-like input is an error, never a fallback search sent to a third party.
        if (looksLikeAddress) return HttpsAddress.parseInput(text)
        val query = URLEncoder.encode(text, "UTF-8")
        return HttpsAddress.parseInput(SEARCH_URL + query)
    }
}
