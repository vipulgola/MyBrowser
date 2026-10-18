package com.personal.mybrowser.browser

import android.os.Build
import android.os.Message
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.GeolocationPermissions
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import java.io.ByteArrayInputStream

/** Captures tapped new-window destinations without loading them in the source tab. */
class BrowserPopupHandler(
    private val container: FrameLayout,
    private val canOpenTab: () -> Boolean,
    private val openTab: (String) -> Unit,
    private val onBlocked: (() -> Unit)? = null,
    private val showFullscreen: ((View, CustomViewCallback) -> Unit)? = null,
    private val hideFullscreen: (() -> Unit)? = null,
) : WebChromeClient() {
    private val pending = mutableSetOf<WebView>()
    private val handled = mutableSetOf<WebView>()
    private var disposed = false

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback,
    ) {
        callback.invoke(origin, false, false)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (disposed) {
            callback.onCustomViewHidden()
            return
        }
        val presenter = showFullscreen
        if (presenter == null) callback.onCustomViewHidden()
        else presenter(view, callback)
    }

    override fun onHideCustomView() {
        hideFullscreen?.invoke()
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean {
        if (disposed || !isUserGesture || !canOpenTab() || pending.size >= 3) return block()
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return block()
        if (resultMsg.target == null) return block()

        val probe = WebView(container.context).apply {
            visibility = View.INVISIBLE
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.setGeolocationEnabled(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.blockNetworkLoads = true
            settings.setSupportMultipleWindows(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true
        }
        probe.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.isForMainFrame) capture(probe, request.url.toString())
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                capture(probe, url)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
            }
        }
        pending.add(probe)
        container.addView(probe, FrameLayout.LayoutParams(1, 1))
        transport.webView = probe
        resultMsg.sendToTarget()
        container.postDelayed({ close(probe) }, 10_000L)
        return true
    }

    private fun block(): Boolean {
        onBlocked?.invoke()
        return false
    }

    private fun capture(probe: WebView, rawUrl: String) {
        if (rawUrl == "about:blank" || probe !in pending || !handled.add(probe)) return
        val target = IncomingWebLink.resolve(rawUrl)
        try {
            if (!disposed && canOpenTab() && target != null) openTab(target.value)
            else onBlocked?.invoke()
        } finally {
            container.post { close(probe) }
        }
    }

    private fun close(probe: WebView) {
        if (!pending.remove(probe)) return
        handled.remove(probe)
        probe.stopLoading()
        probe.webViewClient = WebViewClient()
        container.removeView(probe)
        probe.destroy()
    }

    fun dispose() {
        disposed = true
        hideFullscreen?.invoke()
        pending.toList().forEach(::close)
    }
}
