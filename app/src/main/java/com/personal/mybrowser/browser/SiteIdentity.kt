package com.personal.mybrowser.browser

import com.personal.mybrowser.network.AddressValidationException
import com.personal.mybrowser.network.HttpsAddress
import java.net.URI

/** The actual validated host, not a domain-like string in the path or query. */
object SiteIdentity {
    fun hostname(url: String?): String? {
        if (url == null) return null
        return try {
            URI(HttpsAddress.parseInput(url).value).host
        } catch (_: AddressValidationException) {
            null
        }
    }
}
