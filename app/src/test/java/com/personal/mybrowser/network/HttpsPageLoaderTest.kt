package com.personal.mybrowser.network

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HttpsPageLoaderTest {
    private val address = HttpsAddress.parseInput(HttpsPageLoader.EXAMPLE_URL)
    @Test fun fetchesTheValidatedUrlAndDecodesUtf8() = runTest {
        val address = HttpsAddress.parseInput("https://example.org/Path?q=%2F#section")
        val connection = FakeConnection("<h1>नमस्ते 🌱</h1>".toByteArray())
        var requestedUrl: URL? = null
        val page = HttpsPageLoader({ requestedUrl = it; connection }, StandardTestDispatcher(testScheduler)).load(address)
        assertEquals("https://example.org/Path?q=%2F", requestedUrl.toString())
        assertEquals(LoadedHtml("https://example.org/Path?q=%2F#section", "<h1>नमस्ते 🌱</h1>"), page)
        assertEquals("GET", connection.requestMethod)
        assertEquals(8000, connection.connectTimeout)
        assertEquals(8000, connection.readTimeout)
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        assertTrue(connection.closed)
        assertTrue(connection.disconnected)
    }

    @Test fun honoursTheDeclaredHttpCharset() = runTest {
        val connection = FakeConnection("<p>café</p>".toByteArray(Charsets.ISO_8859_1)).apply {
            mime = "text/html; CHARSET=\"ISO-8859-1\""
        }
        assertEquals("<p>café</p>", HttpsPageLoader({ connection }, StandardTestDispatcher(testScheduler)).load(address).html)
    }

    @Test fun rejectsHttpErrorsAndDisconnects() = runTest {
        val connection = FakeConnection().apply { status = 404 }
        var error: PageLoadException? = null
        try { HttpsPageLoader({ connection }, StandardTestDispatcher(testScheduler)).load(address) }
        catch (failure: PageLoadException) { error = failure }
        assertEquals("The server returned HTTP 404.", error?.message)
        assertTrue(connection.disconnected)
        assertFalse(connection.openedBody)
    }

    @Test fun rejectsRedirectsWithoutALocation() = runTest {
        val connection = FakeConnection().apply { status = 302 }
        var calls = 0
        try {
            HttpsPageLoader({ calls++; connection }, StandardTestDispatcher(testScheduler)).load(address)
            throw AssertionError("Expected redirect rejection")
        } catch (error: PageLoadException) {
            assertTrue(error.message!!.contains("redirect"))
        }
        assertEquals(1, calls)
        assertTrue(connection.disconnected)
    }

    @Test fun acceptsOnlyHtmlResponses() = runTest {
        for (mime in listOf("image/png", "text/plain", "")) {
            val connection = FakeConnection().apply { this.mime = mime }
            try {
                HttpsPageLoader({ connection }, StandardTestDispatcher(testScheduler)).load(address)
                throw AssertionError("Expected MIME rejection")
            } catch (error: PageLoadException) {
                assertTrue(error.message!!.contains("HTML"))
            }
            assertFalse(connection.openedBody)
            assertTrue(connection.disconnected)
        }
    }

    @Test fun rejectsAnOversizedDeclaredBodyBeforeOpeningIt() = runTest {
        val connection = FakeConnection().apply { declaredLength = HttpsPageLoader.MAX_BODY_BYTES + 1L }
        try {
            HttpsPageLoader({ connection }, StandardTestDispatcher(testScheduler)).load(address)
            throw AssertionError("Expected size rejection")
        } catch (error: PageLoadException) {
            assertTrue(error.message!!.contains("128 KiB"))
        }
        assertFalse(connection.openedBody)
        assertTrue(connection.disconnected)
    }

    @Test fun enforcesTheLimitWhenContentLengthIsUnknownOrMisreported() = runTest {
        for (declared in listOf(-1L, 1L)) {
            val connection = FakeConnection(ByteArray(HttpsPageLoader.MAX_BODY_BYTES + 1) { 'x'.code.toByte() }).apply {
                declaredLength = declared
            }
            try {
                HttpsPageLoader({ connection }, StandardTestDispatcher(testScheduler)).load(address)
                throw AssertionError("Expected streamed size rejection")
            } catch (error: PageLoadException) {
                assertTrue(error.message!!.contains("128 KiB"))
            }
            assertTrue(connection.closed)
            assertTrue(connection.disconnected)
        }
    }

    @Test fun acceptsABodyExactlyAtTheLimit() = runTest {
        val connection = FakeConnection(ByteArray(HttpsPageLoader.MAX_BODY_BYTES) { 'x'.code.toByte() })
        val result = HttpsPageLoader({ connection }, StandardTestDispatcher(testScheduler)).load(address)
        assertEquals(HttpsPageLoader.MAX_BODY_BYTES, result.html.length)
    }

    @Test fun unsupportedCharsetsFailRatherThanSilentlyMisdecode() = runTest {
        val connection = FakeConnection().apply { mime = "text/html; charset=not-a-real-charset" }
        try {
            HttpsPageLoader({ connection }, StandardTestDispatcher(testScheduler)).load(address)
            throw AssertionError("Expected charset rejection")
        } catch (error: PageLoadException) {
            assertTrue(error.message!!.contains("encoding"))
        }
        assertTrue(connection.disconnected)
    }

    @Test fun unsupportedCompressionIsRejected() = runTest {
        val connection = FakeConnection().apply { encoding = "gzip" }
        try {
            HttpsPageLoader({ connection }, StandardTestDispatcher(testScheduler)).load(address)
            throw AssertionError("Expected encoding rejection")
        } catch (error: PageLoadException) {
            assertTrue(error.message!!.contains("compression"))
        }
        assertTrue(connection.disconnected)
    }

    @Test fun handshakeErrorsPropagateAndDisconnect() = runTest {
        val failure = SSLHandshakeException("test certificate rejection")
        val connection = FakeConnection().apply { responseFailure = failure }
        try {
            HttpsPageLoader({ connection }, StandardTestDispatcher(testScheduler)).load(address)
            throw AssertionError("Expected handshake failure")
        } catch (error: SSLHandshakeException) {
            assertEquals(failure.message, error.message)
        }
        assertTrue(connection.disconnected)
    }

    @Test fun readTimeoutClosesTheStreamAndDisconnects() = runTest {
        val failure = SocketTimeoutException("test read timeout")
        val connection = FakeConnection().apply { readFailure = failure }
        try {
            HttpsPageLoader({ connection }, StandardTestDispatcher(testScheduler)).load(address)
            throw AssertionError("Expected read timeout")
        } catch (error: SocketTimeoutException) {
            assertEquals(failure.message, error.message)
        }
        assertTrue(connection.closed)
        assertTrue(connection.disconnected)
    }

    @Test fun cancellationBetweenReadsClosesAndDisconnects() = runTest {
        val connection = FakeConnection("<p>text</p>".toByteArray())
        val loader = HttpsPageLoader({ connection }, StandardTestDispatcher(testScheduler))
        val job = launch { loader.load(address); throw AssertionError("Cancelled result must not be returned") }
        connection.onRead = { job.cancel() }
        runCurrent()
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(connection.closed)
        assertTrue(connection.disconnected)
    }

    @Test fun followsAllSupportedStatusesAndClosesEachHopBeforeOpeningTheNext() = runTest {
        val statuses = listOf(301, 302, 303, 307, 308)
        val connections = statuses.mapIndexed { index, status ->
            FakeConnection().apply { this.status = status; location = "/hop${index + 1}" }
        } + FakeConnection("<p>Final</p>".toByteArray())
        val urls = mutableListOf<String>()
        val loader = HttpsPageLoader({ url ->
            if (urls.isNotEmpty()) assertTrue(connections[urls.lastIndex].disconnected)
            urls += url.toString()
            connections[urls.lastIndex]
        }, StandardTestDispatcher(testScheduler))
        assertEquals(LoadedHtml("https://example.com/hop5", "<p>Final</p>"), loader.load(address))
        assertEquals(listOf("https://example.com/") + (1..5).map { "https://example.com/hop$it" }, urls)
        connections.forEachIndexed { index, connection ->
            assertEquals("GET", connection.requestMethod)
            assertFalse(connection.instanceFollowRedirects)
            assertFalse(connection.useCaches)
            assertEquals(HttpsPageLoader.TIMEOUT_MS, connection.connectTimeout)
            assertEquals(HttpsPageLoader.TIMEOUT_MS, connection.readTimeout)
            assertEquals("text/html", connection.getRequestProperty("Accept"))
            assertTrue(connection.disconnected)
            assertEquals(index == connections.lastIndex, connection.openedBody)
        }
        assertTrue(connections.last().closed)
    }

    @Test fun resolvesEveryRelativeLocationAgainstItsOwnResponseUrl() = runTest {
        val locations = listOf("../guide", "?page=2", "//OTHER.EXAMPLE:443/docs/start", "next")
        val connections = locations.map { target -> FakeConnection().apply { status = 302; location = target } } + FakeConnection()
        val urls = mutableListOf<String>()
        val loader = HttpsPageLoader({ url -> urls += url.toString(); connections[urls.lastIndex] }, StandardTestDispatcher(testScheduler))
        val result = loader.load(HttpsAddress.parseInput("https://example.com/docs/start"))
        assertEquals(listOf("https://example.com/docs/start", "https://example.com/guide", "https://example.com/guide?page=2", "https://other.example/docs/start", "https://other.example/docs/next"), urls)
        assertEquals(urls.last(), result.url)
    }

    @Test fun rejectsTheSixthRedirectWithoutOpeningASeventhConnection() = runTest {
        val connections = mutableListOf<FakeConnection>()
        val loader = HttpsPageLoader({
            FakeConnection().apply { status = 302; location = "/hop${connections.size + 1}" }.also { connections += it }
        }, StandardTestDispatcher(testScheduler))
        try { loader.load(address); throw AssertionError("Expected redirect limit") }
        catch (error: PageLoadException) { assertTrue(error.message!!.contains("5 redirects")) }
        assertEquals(6, connections.size)
        assertTrue(connections.all { it.disconnected && !it.openedBody })
    }

    @Test fun detectsLoopsBeforeReconnectingAndIgnoresFragmentDifferences() = runTest {
        for (destination in listOf("https://EXAMPLE.COM:443/", "https://example.com/#different")) {
            val connections = listOf(
                FakeConnection().apply { status = 301; location = "/b" },
                FakeConnection().apply { status = 302; location = destination },
            )
            var calls = 0
            val loader = HttpsPageLoader({ connections[calls++] }, StandardTestDispatcher(testScheduler))
            try { loader.load(address); throw AssertionError("Expected loop rejection") }
            catch (error: PageLoadException) { assertTrue(error.message!!.contains("loop")) }
            assertEquals(2, calls)
            assertTrue(connections.all { it.disconnected && !it.openedBody })
        }
    }

    @Test fun rejectsInvalidOrNonHttpsDestinationsBeforeConnecting() = runTest {
        for (target in listOf("http://example.org/", "javascript:123", "file:///tmp/page", "https:/bad", "//user:pass@example.org/", "//example.org:0/", "%zz", "a b", "a\\b")) {
            val connection = FakeConnection().apply { status = 302; location = target }
            var calls = 0
            val loader = HttpsPageLoader({ calls++; connection }, StandardTestDispatcher(testScheduler))
            try { loader.load(address); throw AssertionError("Expected rejection: $target") }
            catch (error: PageLoadException) { assertTrue(error.message!!.startsWith("Redirect rejected:")) }
            assertEquals(1, calls)
            assertTrue(connection.disconnected)
            assertFalse(connection.openedBody)
        }
    }

    @Test fun rejectsBlankLocationsAndDoesNotReadTheirBodies() = runTest {
        for (target in listOf("", "   ")) {
            val connection = FakeConnection().apply { status = 302; location = target }
            try {
                HttpsPageLoader({ connection }, StandardTestDispatcher(testScheduler)).load(address)
                throw AssertionError("Expected missing destination")
            } catch (error: PageLoadException) { assertTrue(error.message!!.contains("without a destination")) }
            assertTrue(connection.disconnected)
            assertFalse(connection.openedBody)
        }
    }

    @Test fun doesNotFollowOtherThreeHundredStatuses() = runTest {
        for (status in listOf(300, 304, 305, 306, 309)) {
            val connection = FakeConnection().apply { this.status = status; location = "/target" }
            var calls = 0
            try {
                HttpsPageLoader({ calls++; connection }, StandardTestDispatcher(testScheduler)).load(address)
                throw AssertionError("Expected HTTP error")
            } catch (error: PageLoadException) { assertEquals("The server returned HTTP $status.", error.message) }
            assertEquals(1, calls)
            assertTrue(connection.disconnected)
        }
    }

    @Test fun inheritsFragmentsUnlessLocationExplicitlyReplacesThem() = runTest {
        for ((location, suffix) in listOf("/final" to "#original", "/final#new" to "#new", "/final#" to "#")) {
            val connections = listOf(FakeConnection().apply { status = 302; this.location = location }, FakeConnection())
            val urls = mutableListOf<String>()
            val loader = HttpsPageLoader({ url -> urls += url.toString(); connections[urls.lastIndex] }, StandardTestDispatcher(testScheduler))
            val result = loader.load(HttpsAddress.parseInput("https://example.com/#original"))
            assertEquals("https://example.com/final$suffix", result.url)
            assertEquals(listOf("https://example.com/", "https://example.com/final"), urls)
        }
    }

    @Test fun finalPageStillEnforcesTheBodyLimitAfterRedirecting() = runTest {
        val connections = listOf(
            FakeConnection().apply { status = 302; location = "/large" },
            FakeConnection().apply { declaredLength = HttpsPageLoader.MAX_BODY_BYTES + 1L },
        )
        var calls = 0
        try {
            HttpsPageLoader({ connections[calls++] }, StandardTestDispatcher(testScheduler)).load(address)
            throw AssertionError("Expected size rejection")
        } catch (error: PageLoadException) { assertTrue(error.message!!.contains("128 KiB")) }
        assertEquals(2, calls)
        assertTrue(connections.all { it.disconnected && !it.openedBody })
    }

    @Test fun certificateFailureOnALaterHopPropagatesAndDisconnects() = runTest {
        val connections = listOf(
            FakeConnection().apply { status = 302; location = "https://other.example/" },
            FakeConnection().apply { responseFailure = SSLHandshakeException("untrusted destination") },
        )
        var calls = 0
        try {
            HttpsPageLoader({ connections[calls++] }, StandardTestDispatcher(testScheduler)).load(address)
            throw AssertionError("Expected certificate rejection")
        } catch (error: SSLHandshakeException) { assertEquals("untrusted destination", error.message) }
        assertTrue(connections.all { it.disconnected })
    }

    @Test fun cancellationBetweenHopsPreventsTheNextConnection() = runTest {
        val connection = FakeConnection().apply { status = 302; location = "/next" }
        var calls = 0
        val loader = HttpsPageLoader({ calls++; connection }, StandardTestDispatcher(testScheduler))
        val job = launch { loader.load(address); throw AssertionError("Cancelled result must not be returned") }
        connection.onDisconnect = { job.cancel() }
        runCurrent()
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(1, calls)
        assertTrue(connection.disconnected)
        assertFalse(connection.openedBody)
    }

    private class FakeConnection(body: ByteArray = "<h1>Example</h1>".toByteArray()) :
        HttpsURLConnection(URL(HttpsPageLoader.EXAMPLE_URL)) {
        var status = 200
        var location: String? = null
        var onDisconnect: (() -> Unit)? = null
        var mime = "text/html"
        var encoding: String? = null
        var declaredLength = -1L
        var responseFailure: java.io.IOException? = null
        var readFailure: java.io.IOException? = null
        var onRead: (() -> Unit)? = null
        var openedBody = false
        var closed = false
        var disconnected = false
        private val stream = object : ByteArrayInputStream(body) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                onRead?.invoke()
                readFailure?.let { throw it }
                return super.read(buffer, offset, length)
            }
            override fun close() { closed = true; super.close() }
        }
        override fun getResponseCode(): Int { responseFailure?.let { throw it }; return status }
        override fun getHeaderField(name: String): String? = if (name.equals("Location", ignoreCase = true)) location else null
        override fun getContentType(): String = mime
        override fun getContentEncoding(): String? = encoding
        override fun getContentLengthLong(): Long = declaredLength
        override fun getInputStream(): InputStream { openedBody = true; return stream }
        override fun disconnect() { disconnected = true; onDisconnect?.invoke() }
        override fun usingProxy() = false
        override fun connect() = Unit
        override fun getCipherSuite() = "TEST"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
}
