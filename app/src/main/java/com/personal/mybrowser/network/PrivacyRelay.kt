package com.personal.mybrowser.network

import android.content.Context
import android.webkit.WebView
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.coroutines.resume

/** Applies the selected connection route before browser pages are allowed to open. */
object PrivacyRelay {
    private const val HOST = "127.0.0.1"
    private const val PORT = 8118
    private val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(HOST, PORT))

    enum class Status { READY, DIRECT, ORBOT_UNAVAILABLE, WEBVIEW_UNSUPPORTED, PROXY_CHANGE_FAILED }

    @Volatile private var useTor = true

    fun openHttps(url: URL): HttpsURLConnection =
        (if (useTor) url.openConnection(proxy) else url.openConnection()) as HttpsURLConnection

    /** No WebView is shown until the selected route has been applied. */
    suspend fun prepare(context: Context, torEnabled: Boolean): Status {
        // Keep app-owned requests on Tor while switching; direct traffic starts only after
        // WebView has confirmed that its old proxy override was cleared.
        useTor = true
        if (!torEnabled) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                if (!changeWebViewProxy(context, null)) return Status.PROXY_CHANGE_FAILED
            }
            useTor = false
            return Status.DIRECT
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) return Status.WEBVIEW_UNSUPPORTED
        val config = ProxyConfig.Builder()
            .addProxyRule("http://$HOST:$PORT")
            .removeImplicitRules()
            .build()
        if (!changeWebViewProxy(context, config)) return Status.PROXY_CHANGE_FAILED
        return if (withContext(Dispatchers.IO) { isTorExit() }) Status.READY else Status.ORBOT_UNAVAILABLE
    }

    private suspend fun changeWebViewProxy(context: Context, config: ProxyConfig?): Boolean {
        try {
            withTimeout(10_000) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        val onChanged = Runnable {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                        if (config == null) ProxyController.getInstance()
                            .clearProxyOverride(context.mainExecutor, onChanged)
                        else ProxyController.getInstance()
                            .setProxyOverride(config, context.mainExecutor, onChanged)
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            return false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return false
        }
        return true
    }

    /** Stop site JavaScript from opening direct WebRTC peer connections. */
    fun protectWebView(view: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        WebViewCompat.addDocumentStartJavaScript(
            view,
            """
                (() => {
                  for (const name of ['RTCPeerConnection', 'webkitRTCPeerConnection']) {
                    try { Object.defineProperty(window, name, {
                      value: undefined, writable: false, configurable: false
                    }); } catch (_) {}
                  }
                })();
            """.trimIndent(),
            setOf("*"),
        )
    }

    private fun isTorExit(): Boolean {
        val connection = try {
            openHttps(URL("https://check.torproject.org/api/ip"))
        } catch (_: IOException) {
            return false
        }
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            if (connection.responseCode != 200) return false
            val response = connection.inputStream.bufferedReader().use { it.readText().take(1024) }
            JSONObject(response).optBoolean("IsTor", false)
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }
}
