package com.personal.mybrowser.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.nio.charset.Charset
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class LoadedHtml(val url: String, val html: String)

fun interface PageLoader {
    suspend fun load(address: HttpsAddress): LoadedHtml
}

class PageLoadException(message: String) : IOException(message)

/** Fetches a validated HTTPS address and returns source text, not a rendered page. */
class HttpsPageLoader internal constructor(
    private val connect: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PageLoader {
    override suspend fun load(address: HttpsAddress): LoadedHtml = withContext(ioDispatcher) {
        val context = currentCoroutineContext()
        var current = address
        val visited = mutableSetOf<String>()
        for (redirectCount in 0..MAX_REDIRECTS) {
            context.ensureActive()
            // Fragments do not change an HTTP request and must not bypass loop detection.
            if (!visited.add(current.requestUrl)) throw PageLoadException("The server returned a redirect loop.")
            val connection = connect(URL(current.requestUrl))
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("Accept", "text/html")
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("Cache-Control", "no-store")
                connection.setRequestProperty("User-Agent", "MyBrowser/0.1")
                // Use the platform's trust manager and hostname verifier unchanged on every hop.
                val status = connection.responseCode
                context.ensureActive()
                if (status in REDIRECT_STATUSES) {
                    if (redirectCount == MAX_REDIRECTS) {
                        throw PageLoadException("The page exceeds the limit of $MAX_REDIRECTS redirects.")
                    }
                    val location = connection.getHeaderField("Location")?.trim()
                    if (location.isNullOrEmpty()) {
                        throw PageLoadException("The server returned a redirect without a destination.")
                    }
                    current = redirectAddress(current, location)
                    continue // finally disconnects this hop before the next connection opens.
                }
                if (status != 200) throw PageLoadException("The server returned HTTP $status.")

                val contentType = connection.contentType.orEmpty()
                if (!contentType.substringBefore(';').trim().equals("text/html", ignoreCase = true)) {
                    throw PageLoadException("The server did not return an HTML page.")
                }
                val encoding = connection.contentEncoding
                if (!encoding.isNullOrBlank() && !encoding.equals("identity", ignoreCase = true)) {
                    throw PageLoadException("This demo cannot read the response's compression format.")
                }
                if (connection.contentLengthLong > MAX_BODY_BYTES) {
                    throw PageLoadException("The page exceeds this demo's 128 KiB limit.")
                }
                val charset = responseCharset(contentType)
                val bytes = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        context.ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        if (bytes.size() + count > MAX_BODY_BYTES) {
                            throw PageLoadException("The page exceeds this demo's 128 KiB limit.")
                        }
                        bytes.write(buffer, 0, count)
                    }
                }
                context.ensureActive()
                return@withContext LoadedHtml(current.value, bytes.toString(charset.name()))
            } finally {
                connection.disconnect()
            }
        }
        throw PageLoadException("The page exceeds the limit of $MAX_REDIRECTS redirects.")
    }

    private fun redirectAddress(current: HttpsAddress, location: String): HttpsAddress = try {
        // RFC 9110 section 10.2.2: inherit the original fragment when Location omits one.
        // An explicitly empty # replaces it. Fragments remain display-only for our loader.
        val target = if ('#' !in location && '#' in current.value) {
            location + "#" + current.value.substringAfter('#')
        } else location
        current.resolveLink(target)
    } catch (error: AddressValidationException) {
        throw PageLoadException("Redirect rejected: ${error.message}")
    }

    private fun responseCharset(contentType: String): Charset {
        val parameter = contentType.split(';').drop(1).firstOrNull {
            it.substringBefore('=').trim().equals("charset", ignoreCase = true)
        } ?: return Charsets.UTF_8
        val name = parameter.substringAfter('=', "").trim().removeSurrounding("\"").removeSurrounding("'")
        return try {
            Charset.forName(name)
        } catch (_: IllegalArgumentException) {
            throw PageLoadException("The page declares an unsupported text encoding.")
        }
    }

    companion object {
        const val EXAMPLE_URL = "https://example.com/"
        const val MAX_BODY_BYTES = 128 * 1024
        const val TIMEOUT_MS = 8000
        const val MAX_REDIRECTS = 5
        private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
    }
}
