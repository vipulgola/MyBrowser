package com.personal.mybrowser.browser

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import com.personal.mybrowser.network.PrivacyRelay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bundled offline rules with an optional user-requested HTTPS refresh. */
class AdBlockRepository(context: Context) {
    private val appContext = context.applicationContext
    private val savedRules = AtomicFile(File(appContext.filesDir, "ad_hosts.txt"))

    @Volatile private var rules: AdBlockRules = loadRules()
    @Volatile var enabled: Boolean = true
    private val blocked = AtomicInteger()

    val hostCount: Int get() = rules.size
    val blockedCount: Int get() = blocked.get()

    fun blocks(url: String, isMainFrame: Boolean): Boolean {
        if (!enabled || !rules.blocks(url, isMainFrame)) return false
        blocked.incrementAndGet()
        return true
    }

    suspend fun update(): Int = withContext(Dispatchers.IO) {
        val connection = PrivacyRelay.openHttps(URL(FILTER_URL)).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = false
        }
        try {
            if (connection.responseCode != 200) throw IOException("Ad list server returned HTTP ${connection.responseCode}.")
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_BYTES) throw IOException("Ad list is too large.")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val updated = AdBlockRules.parse(bytes.toString(Charsets.UTF_8).lineSequence())
            if (updated.size < MIN_HOSTS) throw IOException("The downloaded ad list is incomplete.")
            val output = savedRules.startWrite()
            try {
                output.write(bytes)
                savedRules.finishWrite(output)
            } catch (error: IOException) {
                savedRules.failWrite(output)
                throw error
            }
            rules = updated
            updated.size
        } finally {
            connection.disconnect()
        }
    }

    private fun loadRules(): AdBlockRules {
        try {
            val updated = savedRules.openRead().bufferedReader().use { reader ->
                AdBlockRules.parse(reader.lineSequence())
            }
            if (updated.size >= MIN_HOSTS) return updated
        } catch (_: IOException) {
            // A missing or interrupted update falls back to the bundled list.
        }
        return appContext.assets.open("ad_hosts.txt").bufferedReader().use { reader ->
            AdBlockRules.parse(reader.lineSequence())
        }
    }

    companion object {
        private const val FILTER_URL =
            "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext"
        private const val MAX_BYTES = 512 * 1024
        private const val MIN_HOSTS = 1_000
    }
}
