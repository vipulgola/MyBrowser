package com.personal.mybrowser.browser

import com.personal.mybrowser.network.AddressValidationException
import com.personal.mybrowser.network.HttpsAddress

/** Hand YouTube navigation to the WebView mode; the experimental engine cannot play video. */
object YouTubeRouting {
    fun fromAddressInput(input: String): String? = try {
        AddressInputResolver.resolve(input).value.takeIf(::isYouTubeUrl)
    } catch (_: AddressValidationException) {
        null
    }

    fun fromPageLink(pageUrl: String, target: String): String? = try {
        HttpsAddress.parseInput(pageUrl).resolveLink(target).value.takeIf(::isYouTubeUrl)
    } catch (_: AddressValidationException) {
        null
    }

    fun isYouTubeUrl(url: String?): Boolean {
        val host = SiteIdentity.hostname(url) ?: return false
        return isYouTubePageHost(host) || host == "youtu.be"
    }

    fun isYouTubePageUrl(url: String?): Boolean = SiteIdentity.hostname(url)?.let(::isYouTubePageHost) == true

    private fun isYouTubePageHost(host: String): Boolean = host == "youtube.com" || host.endsWith(".youtube.com")
}
