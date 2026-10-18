package com.personal.mybrowser.network

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Only same-origin HTTPS images; no cookies, referrer, disk cache or third-party requests. */
object ImagePolicy {
    fun resolve(page: HttpsAddress, source: String): HttpsAddress? = try {
        val image = page.resolveLink(source)
        if (URI(image.value).rawAuthority == URI(page.value).rawAuthority) image else null
    } catch (_: AddressValidationException) {
        null
    }
}

class HttpsImageLoader internal constructor(
    private val connect: (URL) -> HttpsURLConnection = PrivacyRelay::openHttps,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun load(page: HttpsAddress, source: String): ByteArray? = withContext(ioDispatcher) {
        val context = currentCoroutineContext()
        var current = ImagePolicy.resolve(page, source) ?: return@withContext null
        val visited = mutableSetOf<String>()
        repeat(MAX_REDIRECTS + 1) { hop ->
            context.ensureActive()
            if (!visited.add(current.requestUrl)) return@withContext null
            val connection = connect(URL(current.requestUrl))
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("Accept", "image/png,image/jpeg,image/webp,image/gif")
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("Cache-Control", "no-store")
                connection.setRequestProperty("User-Agent", "MyBrowser/0.1")
                val status = connection.responseCode
                context.ensureActive()
                if (status in REDIRECTS) {
                    if (hop == MAX_REDIRECTS) return@withContext null
                    val location = connection.getHeaderField("Location") ?: return@withContext null
                    current = ImagePolicy.resolve(page, current.resolveLink(location).value) ?: return@withContext null
                    return@repeat
                }
                if (status != 200) return@withContext null
                val type = connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
                if (type !in TYPES) return@withContext null
                if (!connection.contentEncoding.isNullOrBlank() && !connection.contentEncoding.equals("identity", true)) return@withContext null
                if (connection.contentLengthLong > MAX_BYTES) return@withContext null
                val bytes = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        context.ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        if (bytes.size() + count > MAX_BYTES) return@withContext null
                        bytes.write(buffer, 0, count)
                    }
                }
                context.ensureActive()
                return@withContext bytes.toByteArray()
            } catch (_: AddressValidationException) {
                return@withContext null
            } finally {
                connection.disconnect()
            }
        }
        null
    }

    companion object {
        const val MAX_BYTES = 512 * 1024
        const val MAX_REDIRECTS = 3
        const val TIMEOUT_MS = 8000
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
        private val TYPES = setOf("image/png", "image/jpeg", "image/webp", "image/gif")
    }
}
