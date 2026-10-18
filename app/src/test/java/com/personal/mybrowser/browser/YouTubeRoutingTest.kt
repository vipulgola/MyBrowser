package com.personal.mybrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeRoutingTest {
    @Test fun addressBarRoutesYouTubeHostsButNotLookalikesOrSearches() {
        assertEquals("https://youtube.com/", YouTubeRouting.fromAddressInput("youtube.com"))
        assertEquals("https://m.youtube.com/watch?v=123", YouTubeRouting.fromAddressInput("m.youtube.com/watch?v=123"))
        assertEquals("https://youtu.be/abc", YouTubeRouting.fromAddressInput("https://youtu.be/abc"))
        assertNull(YouTubeRouting.fromAddressInput("youtube.com.evil.example/watch"))
        assertNull(YouTubeRouting.fromAddressInput("notyoutube.com"))
        assertNull(YouTubeRouting.fromAddressInput("search for youtube.com"))
        assertNull(YouTubeRouting.fromAddressInput("http://youtube.com/watch"))
    }

    @Test fun pageLinksAreResolvedBeforeHostMatching() {
        val page = "https://example.com/article"
        assertEquals("https://www.youtube.com/watch?v=abc",
            YouTubeRouting.fromPageLink(page, "https://www.youtube.com/watch?v=abc"))
        assertEquals("https://youtu.be/abc", YouTubeRouting.fromPageLink(page, "//youtu.be/abc"))
        assertNull(YouTubeRouting.fromPageLink(page, "https://example.com/redirect?to=youtube.com"))
        assertNull(YouTubeRouting.fromPageLink(page, "https://youtube.com.evil.example/"))
        assertNull(YouTubeRouting.fromPageLink(page, "javascript:alert(1)"))
    }

    @Test fun onlyYouTubePageHostsGetTheJavaScriptPrompt() {
        assertTrue(YouTubeRouting.isYouTubePageUrl("https://www.youtube.com/watch?v=abc"))
        assertTrue(YouTubeRouting.isYouTubePageUrl("https://music.youtube.com/"))
        assertFalse(YouTubeRouting.isYouTubePageUrl("https://youtu.be/abc"))
        assertFalse(YouTubeRouting.isYouTubePageUrl("https://youtube.com.evil.example/"))
    }
}
