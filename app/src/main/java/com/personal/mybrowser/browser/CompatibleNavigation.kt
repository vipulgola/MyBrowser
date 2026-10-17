package com.personal.mybrowser.browser

import com.personal.mybrowser.network.AddressValidationException
import com.personal.mybrowser.network.HttpsAddress

/** The WebView mode never starts a non-HTTPS top-level navigation. */
object CompatibleNavigation {
    fun address(input: String): String = AddressInputResolver.resolve(input).value

    fun isAllowedNavigation(url: String): Boolean = try {
        HttpsAddress.parseInput(url)
        url.startsWith("https://", ignoreCase = true)
    } catch (_: AddressValidationException) {
        false
    }
}
