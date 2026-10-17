package com.personal.mybrowser.browser

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Build
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.personal.mybrowser.network.AddressValidationException

/** A separate compatibility mode. Its content is rendered by Android WebView, not our engine. */
@Composable
fun CompatibleBrowser(startAddress: String, onOwnEngine: (String?) -> Unit, modifier: Modifier = Modifier) {
    var address by remember(startAddress) { mutableStateOf(startAddress) }
    var currentUrl by remember(startAddress) { mutableStateOf<String?>(null) }
    var error by remember(startAddress) { mutableStateOf<String?>(null) }
    var loading by remember(startAddress) { mutableStateOf(false) }
    var canBack by remember(startAddress) { mutableStateOf(false) }
    var canForward by remember(startAddress) { mutableStateOf(false) }
    var scriptsEnabled by remember(startAddress) { mutableStateOf(false) }
    var webView by remember(startAddress) { mutableStateOf<WebView?>(null) }
    val focusManager = LocalFocusManager.current

    val submit: () -> Unit = {
        try {
            val target = CompatibleNavigation.address(address)
            error = null
            address = target
            webView?.loadUrl(target)
            focusManager.clearFocus()
        } catch (invalid: AddressValidationException) {
            error = invalid.message ?: "Enter a valid HTTPS address."
        }
    }
    val goBack: () -> Unit = {
        if (webView?.canGoBack() == true) webView?.goBack() else onOwnEngine(null)
    }
    BackHandler { goBack() }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it; error = null },
                    modifier = Modifier.weight(1f),
                    label = { Text("Search or enter address") },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                )
                Button(onClick = submit, enabled = webView != null) { Text("Go") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = goBack) { Text(if (canBack) "Back" else "Close") }
                TextButton(onClick = { webView?.goForward() }, enabled = canForward) { Text("Forward") }
                if (loading) TextButton(onClick = { webView?.stopLoading(); loading = false }) { Text("Stop") }
                else TextButton(onClick = { webView?.reload() }, enabled = webView != null) { Text("Reload") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onOwnEngine(currentUrl) }) { Text("Own engine") }
                TextButton(onClick = {
                    scriptsEnabled = !scriptsEnabled
                    webView?.settings?.javaScriptEnabled = scriptsEnabled
                    webView?.reload()
                }) { Text(if (scriptsEnabled) "Disable JavaScript" else "Enable JavaScript") }
            }
            Text(
                "Compatible view · Android WebView · cookies and site storage disabled · JavaScript ${if (scriptsEnabled) "on" else "off"}",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                "Third-party resources can still track you. WebView may retain internal data on this device.",
                style = MaterialTheme.typography.labelSmall,
            )
            if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                // CookieManager is shared by this app's WebViews. Disable cookies before loading.
                CookieManager.getInstance().setAcceptCookie(false)
                WebView(context).apply {
                    configurePrivateSettings()
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (!request.isForMainFrame) return false
                            if (CompatibleNavigation.isAllowedNavigation(request.url.toString())) return false
                            error = "Only HTTPS pages can open in compatible view."
                            return true
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            loading = true
                            error = null
                            currentUrl = url
                            address = url
                            canBack = view.canGoBack()
                            canForward = view.canGoForward()
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                            currentUrl = url
                            canBack = view.canGoBack()
                            canForward = view.canGoForward()
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, webError: WebResourceError) {
                            if (request.isForMainFrame) {
                                loading = false
                                error = "This page could not be loaded. Check your connection or try again."
                            }
                        }

                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, sslError: SslError) {
                            handler.cancel()
                            loading = false
                            error = "A secure connection could not be verified."
                        }
                    }
                    webView = this
                    try {
                        loadUrl(CompatibleNavigation.address(startAddress))
                    } catch (invalid: AddressValidationException) {
                        error = invalid.message ?: "Enter a valid HTTPS address."
                    }
                }
            },
            onRelease = { view ->
                view.stopLoading()
                view.loadUrl("about:blank")
                view.clearHistory()
                view.clearCache(true)
                WebStorage.getInstance().deleteAllData()
                WebViewDatabase.getInstance(view.context).clearFormData()
                view.destroy()
                if (webView === view) webView = null
            },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configurePrivateSettings() {
    settings.javaScriptEnabled = false // The user can enable it for this session.
    settings.domStorageEnabled = false
    settings.databaseEnabled = false
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.allowFileAccessFromFileURLs = false
    settings.allowUniversalAccessFromFileURLs = false
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    settings.cacheMode = WebSettings.LOAD_NO_CACHE
    settings.setSupportMultipleWindows(false)
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.mediaPlaybackRequiresUserGesture = true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true
}
