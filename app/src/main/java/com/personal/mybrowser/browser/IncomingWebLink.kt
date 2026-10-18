package com.personal.mybrowser.browser

import com.personal.mybrowser.network.AddressValidationException
import com.personal.mybrowser.network.HttpsAddress

/** Converts an incoming web link to a validated HTTPS address without making a request. */
object IncomingWebLink {
    fun resolve(rawUrl: String?): HttpsAddress? {
        val url = rawUrl ?: return null
        val secureUrl = when {
            url.startsWith("https://", ignoreCase = true) -> url
            url.startsWith("http://", ignoreCase = true) -> "https://" + url.substring(7)
            else -> return null
        }
        return try {
            HttpsAddress.parseInput(secureUrl)
        } catch (_: AddressValidationException) {
            null
        }
    }
}
