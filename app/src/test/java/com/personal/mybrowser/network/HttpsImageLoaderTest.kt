package com.personal.mybrowser.network

import java.io.ByteArrayInputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpsImageLoaderTest {
    private val page = HttpsAddress.parseInput("https://example.com/docs/page")

    @Test fun sameOriginImageUsesHttpsAndReturnsBoundedBytes() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val connection = FakeImageConnection(bytes)
        val urls = mutableListOf<String>()
        val loader = HttpsImageLoader({ url -> urls += url.toString(); connection }, StandardTestDispatcher(testScheduler))
        assertArrayEquals(bytes, loader.load(page, "../images/photo.png"))
        assertEquals(listOf("https://example.com/images/photo.png"), urls)
        assertEquals("GET", connection.requestMethod)
        assertTrue(connection.disconnected)
    }

    @Test fun thirdPartyHttpAndRedirectedThirdPartyImagesNeverConnect() = runTest {
        val redirect = FakeImageConnection().apply { status = 302; location = "https://tracker.test/collect" }
        val urls = mutableListOf<String>()
        val loader = HttpsImageLoader({ url -> urls += url.toString(); redirect }, StandardTestDispatcher(testScheduler))
        assertNull(loader.load(page, "https://tracker.test/pixel"))
        assertNull(loader.load(page, "http://example.com/photo"))
        assertEquals(emptyList<String>(), urls)
        assertNull(loader.load(page, "/redirect"))
        assertEquals(listOf("https://example.com/redirect"), urls)
        assertTrue(redirect.disconnected)
    }

    @Test fun rejectsNonImageAndOversizedResponses() = runTest {
        val nonImage = FakeImageConnection().apply { mime = "text/html" }
        val tooLarge = FakeImageConnection(ByteArray(HttpsImageLoader.MAX_BYTES + 1))
        val connections = listOf(nonImage, tooLarge)
        var index = 0
        val loader = HttpsImageLoader({ connections[index++] }, StandardTestDispatcher(testScheduler))
        assertNull(loader.load(page, "/wrong"))
        assertNull(loader.load(page, "/huge"))
        assertEquals(2, index)
        assertTrue(connections.all { it.disconnected })
    }

    private class FakeImageConnection(private val bytes: ByteArray = byteArrayOf(1)) :
        HttpsURLConnection(URL("https://example.com/")) {
        var status = 200
        var location: String? = null
        var mime = "image/png"
        var disconnected = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String?) = if (name.equals("Location", true)) location else null
        override fun getContentType() = mime
        override fun getContentLengthLong() = -1L
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun connect() = Unit
        override fun getCipherSuite() = "TLS_TEST"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
}
