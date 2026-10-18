package com.personal.mybrowser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.SafeBrowsingResponse
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.personal.mybrowser.network.AddressValidationException
import com.personal.mybrowser.network.PrivacyRelay
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.launch

/** Activity-level host for the fullscreen view requested by a WebView video player. */
interface BrowserFullscreenHost {
    fun showWebFullscreen(view: View, callback: WebChromeClient.CustomViewCallback)
    fun hideWebFullscreen()
}

/** Normal browsing uses the Android WebView provider with privacy-conscious defaults. */
@Composable
fun CompatibleBrowser(
    navigationRequest: BrowserNavigation?,
    onCurrentUrlChange: (String?) -> Unit,
    onOwnEngine: (String?) -> Unit,
    popupsAllowed: Boolean = false,
    torEnabled: Boolean = true,
    darkPagesEnabled: Boolean = false,
    onOpenNewTab: ((String) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    addressBarRevealToken: Long = 0L,
    homeRequestToken: Long = 0L,
    overflowMenuRequestToken: Long = 0L,
    externalChrome: Boolean = false,
    engineButtonLabel: String = "Experimental engine",
    isActive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var address by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var activeUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showSiteDetails by remember { mutableStateOf(false) }
    var blockedThreat by remember { mutableStateOf<String?>(null) }
    var blockedPage by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showAddressBar by rememberSaveable { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val fullscreenHost = context as? BrowserFullscreenHost
    val javaScriptPermissions = remember(context) { JavaScriptSitePermissions(context.applicationContext) }
    val currentIsActive = rememberUpdatedState(isActive)
    val currentPopupsAllowed = rememberUpdatedState(popupsAllowed)
    val currentOnOpenNewTab = rememberUpdatedState(onOpenNewTab)
    var adBlockingEnabled by rememberSaveable { mutableStateOf(true) }
    val adBlocker = remember(context) {
        AdBlockRepository(context.applicationContext).apply { enabled = adBlockingEnabled }
    }
    val scope = rememberCoroutineScope()
    var updatingAdList by remember { mutableStateOf(false) }
    var adListNotice by remember { mutableStateOf<String?>(null) }
    var lastHomeRequestToken by remember { mutableStateOf(0L) }
    var lastOverflowMenuRequestToken by remember { mutableStateOf(0L) }

    LaunchedEffect(isActive, webView) {
        if (isActive) webView?.onResume()
        else {
            menuExpanded = false
            showSiteDetails = false
            webView?.onPause()
        }
    }

    LaunchedEffect(addressBarRevealToken) {
        if (isActive && addressBarRevealToken != 0L) showAddressBar = true
    }

    LaunchedEffect(navigationRequest?.id) {
        val request = navigationRequest ?: return@LaunchedEffect
        val wasBlocked = blockedPage != null
        address = request.url
        error = null
        blockedThreat = null
        blockedPage = null
        activeUrl = request.url
        currentUrl = request.url
        onCurrentUrlChange(request.url)
        if (!wasBlocked) webView?.let { view ->
            view.applyJavaScriptFor(request.url, javaScriptPermissions)
            view.loadUrl(request.url)
        }
    }

    val submit: () -> Unit = {
        try {
            val target = CompatibleNavigation.address(address)
            val wasBlocked = blockedPage != null
            error = null
            blockedThreat = null
            blockedPage = null
            address = target
            activeUrl = target
            currentUrl = target
            onCurrentUrlChange(target)
            if (!wasBlocked) webView?.let { view ->
                view.applyJavaScriptFor(target, javaScriptPermissions)
                view.loadUrl(target)
            }
            showAddressBar = false
            focusManager.clearFocus()
        } catch (invalid: AddressValidationException) {
            error = invalid.message ?: "Enter a valid HTTPS address."
        }
    }
    val home: () -> Unit = {
        webView?.stopLoading()
        activeUrl = null
        currentUrl = null
        address = ""
        error = null
        blockedThreat = null
        blockedPage = null
        loading = false
        canBack = false
        canForward = false
        showAddressBar = true
        onCurrentUrlChange(null)
    }
    LaunchedEffect(homeRequestToken) {
        if (isActive && homeRequestToken != 0L && homeRequestToken != lastHomeRequestToken) {
            lastHomeRequestToken = homeRequestToken
            home()
        }
    }
    LaunchedEffect(overflowMenuRequestToken) {
        if (
            isActive && activeUrl != null && overflowMenuRequestToken != 0L &&
            overflowMenuRequestToken != lastOverflowMenuRequestToken
        ) {
            lastOverflowMenuRequestToken = overflowMenuRequestToken
            menuExpanded = true
        }
    }
    val changeJavaScriptPermission: (String, Boolean) -> Unit = { siteUrl, allowed ->
        if (javaScriptPermissions.setAllowed(siteUrl, allowed)) {
            webView?.let { view ->
                val host = SiteIdentity.hostname(siteUrl)
                val loadedUrl = view.url
                when {
                    SiteIdentity.hostname(loadedUrl) == host -> {
                        view.applyJavaScriptFor(loadedUrl, javaScriptPermissions)
                        view.reload()
                    }
                    SiteIdentity.hostname(activeUrl) == host -> {
                        view.applyJavaScriptFor(siteUrl, javaScriptPermissions)
                        view.loadUrl(siteUrl)
                    }
                }
            }
        } else error = "Could not save the JavaScript setting for this site."
    }
    val goBack: () -> Unit = {
        if (blockedPage != null) home()
        else if (webView?.canGoBack() == true) webView?.let { view ->
            view.applyJavaScriptFor(view.historyUrl(-1), javaScriptPermissions)
            view.goBack()
        }
        else home()
    }
    BackHandler(enabled = isActive && activeUrl != null) { goBack() }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .shadow(3.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!externalChrome && (activeUrl == null || showAddressBar)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it; error = null },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search or enter address") },
                        singleLine = true,
                        isError = error != null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        shape = RoundedCornerShape(18.dp),
                    )
                    Button(
                        onClick = submit,
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) { Text("Go") }
                    if (activeUrl != null) TextButton(onClick = { menuExpanded = true }) { Text("⋮") }
                }
            } else if (!externalChrome) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        SiteIdentity.hostname(currentUrl ?: activeUrl) ?: "Secure page",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(onClick = { menuExpanded = true }) { Text("⋮") }
                }
            }
            if (activeUrl != null) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Forward") },
                            onClick = {
                                menuExpanded = false
                                webView?.let { view ->
                                    view.applyJavaScriptFor(view.historyUrl(1), javaScriptPermissions)
                                    view.goForward()
                                }
                            },
                            enabled = canForward,
                        )
                        DropdownMenuItem(
                            text = { Text(if (loading) "Stop loading" else "Reload") },
                            onClick = {
                                menuExpanded = false
                                if (loading) {
                                    webView?.stopLoading()
                                    loading = false
                                } else webView?.let { view ->
                                    view.applyJavaScriptFor(view.url, javaScriptPermissions)
                                    view.reload()
                                }
                            },
                            enabled = webView != null,
                        )
                        DropdownMenuItem(text = { Text("Home") }, onClick = {
                            menuExpanded = false
                            home()
                        })
                        DropdownMenuItem(text = { Text("Site details") }, onClick = {
                            menuExpanded = false
                            showSiteDetails = true
                        })
                        if (onOpenSettings != null) DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                menuExpanded = false
                                onOpenSettings()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (adBlockingEnabled) "Turn off ad blocking" else "Turn on ad blocking") },
                            onClick = {
                                menuExpanded = false
                                adBlockingEnabled = !adBlockingEnabled
                                adBlocker.enabled = adBlockingEnabled
                                webView?.reload()
                            },
                            enabled = blockedPage == null,
                        )
                        DropdownMenuItem(
                            text = { Text(if (updatingAdList) "Updating ad list…" else "Update ad list") },
                            onClick = {
                                menuExpanded = false
                                updatingAdList = true
                                scope.launch {
                                    adListNotice = try {
                                        val count = adBlocker.update()
                                        webView?.reload()
                                        "Ad list updated: $count hosts."
                                    } catch (_: IOException) {
                                        "Could not update the ad list. The existing rules are still active."
                                    } finally {
                                        updatingAdList = false
                                    }
                                }
                            },
                            enabled = !updatingAdList,
                        )
                        DropdownMenuItem(
                            text = { Text("Blocked requests this session: ${adBlocker.blockedCount}") },
                            onClick = {},
                            enabled = false,
                        )
                        DropdownMenuItem(text = { Text(engineButtonLabel) }, onClick = {
                            menuExpanded = false
                            onOwnEngine(currentUrl ?: activeUrl)
                        }, enabled = blockedPage == null)
                        val siteUrl = currentUrl ?: activeUrl
                        val siteHost = SiteIdentity.hostname(siteUrl)
                        val siteAllowsJavaScript = javaScriptPermissions.isAllowed(siteUrl)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (siteAllowsJavaScript) "Block JavaScript for $siteHost"
                                    else "Allow JavaScript for $siteHost"
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                if (siteUrl != null) changeJavaScriptPermission(siteUrl, !siteAllowsJavaScript)
                            },
                            enabled = siteHost != null && blockedPage == null,
                        )
                    }
                }
            }
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            if (error != null && blockedPage == null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            val youtubeUrl = (currentUrl ?: activeUrl)?.takeIf(YouTubeRouting::isYouTubePageUrl)
            if (youtubeUrl != null && blockedPage == null && !javaScriptPermissions.isAllowed(youtubeUrl)) {
                val host = SiteIdentity.hostname(youtubeUrl).orEmpty()
                Text("YouTube videos need JavaScript. It is off for $host.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { changeJavaScriptPermission(youtubeUrl, true) }) {
                    Text("Allow JavaScript for $host")
                }
            }
            adListNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }

        val initialUrl = activeUrl
        if (initialUrl == null) {
            BrowserHome(
                onExperimentalEngine = { onOwnEngine(null) },
                engineButtonLabel = engineButtonLabel,
                adBlockingEnabled = adBlockingEnabled,
                popupsAllowed = popupsAllowed,
                torEnabled = torEnabled,
                onOpenSettings = onOpenSettings,
            )
        } else if (blockedPage != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Known threat blocked", style = MaterialTheme.typography.headlineMedium)
                Text(blockedPage.orEmpty(), modifier = Modifier.padding(top = 12.dp))
                Button(onClick = home, modifier = Modifier.padding(top = 16.dp)) { Text("Go Home") }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val container = BrowserWebViewContainer(context)
                    container.darkPagesEnabled = darkPagesEnabled
                    container.popupHandler = BrowserPopupHandler(
                        container = container,
                        canOpenTab = {
                            currentIsActive.value && currentPopupsAllowed.value && currentOnOpenNewTab.value != null
                        },
                        openTab = { url -> currentOnOpenNewTab.value?.invoke(url) },
                        showFullscreen = fullscreenHost?.let { host -> { video, callback ->
                            host.showWebFullscreen(video, callback)
                        } },
                        hideFullscreen = fullscreenHost?.let { host -> { host.hideWebFullscreen() } },
                    )
                    // First-party cookies support sign-in and site state; third-party cookies stay blocked.
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(context).apply {
                        configurePrivateSettings(darkPagesEnabled)
                        applyJavaScriptFor(initialUrl, javaScriptPermissions)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webChromeClient = container.popupHandler
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                if (!adBlocker.blocks(request.url.toString(), request.isForMainFrame)) return null
                                return WebResourceResponse(
                                    "text/plain", "UTF-8", 204, "No Content", emptyMap(),
                                    ByteArrayInputStream(ByteArray(0)),
                                )
                            }

                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (!request.isForMainFrame) return false
                                val target = IncomingWebLink.resolve(request.url.toString())
                                if (target == null) {
                                    error = "Only HTTPS pages can open in MyBrowser."
                                    return true
                                }
                                view.applyJavaScriptFor(target.value, javaScriptPermissions)
                                if (request.url.scheme.equals("https", ignoreCase = true)) return false
                                view.loadUrl(target.value) // Upgrade HTTP links without an insecure request.
                                return true
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                if (blockedPage != null) return
                                view.applyJavaScriptFor(url, javaScriptPermissions)
                                loading = true
                                error = null
                                activeUrl = url
                                currentUrl = url
                                address = url
                                onCurrentUrlChange(url)
                                canBack = view.canGoBack()
                                canForward = view.canGoForward()
                                showAddressBar = false
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                if (blockedPage != null) return
                                loading = false
                                activeUrl = url
                                currentUrl = url
                                onCurrentUrlChange(url)
                                canBack = view.canGoBack()
                                canForward = view.canGoForward()
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, webError: WebResourceError) {
                                if (request.isForMainFrame && blockedPage == null) {
                                    loading = false
                                    error = "This page could not be loaded. Check your connection or try again."
                                    showAddressBar = true
                                }
                            }

                            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, sslError: SslError) {
                                handler.cancel()
                                if (blockedPage != null) return
                                loading = false
                                error = "A secure connection could not be verified."
                                showAddressBar = true
                            }

                            override fun onSafeBrowsingHit(
                                view: WebView,
                                request: WebResourceRequest,
                                threatType: Int,
                                callback: SafeBrowsingResponse,
                            ) {
                                callback.backToSafety(false)
                                val host = SiteIdentity.hostname(request.url.toString()) ?: "this address"
                                if (request.isForMainFrame) {
                                    blockedPage = "WebView identified $host as a known threat and blocked the site."
                                    address = request.url.toString()
                                    currentUrl = request.url.toString()
                                    loading = false
                                    error = null
                                    canBack = false
                                    canForward = false
                                } else {
                                    blockedThreat = "WebView blocked known harmful content from $host on this page."
                                }
                            }
                        }
                        container.page = this
                        container.addView(
                            this,
                            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                        )
                        webView = this
                        loadUrl(initialUrl)
                    }
                    container
                },
                update = { container ->
                    if (container.darkPagesEnabled != darkPagesEnabled) {
                        container.darkPagesEnabled = darkPagesEnabled
                        container.page.setDarkPagesEnabled(darkPagesEnabled)
                        container.page.reload()
                    }
                },
                onRelease = { container ->
                    container.popupHandler.dispose()
                    val view = container.page
                    view.stopLoading()
                    view.settings.javaScriptEnabled = false
                    view.webViewClient = WebViewClient()
                    view.webChromeClient = WebChromeClient()
                    view.clearHistory()
                    // WebStorage, cookies, and cache are shared by other open tabs.
                    container.removeView(view)
                    view.destroy()
                    if (webView === view) webView = null
                },
            )
        }
    }

    if (isActive && showSiteDetails) {
        val url = currentUrl ?: activeUrl
        val host = SiteIdentity.hostname(url)
        AlertDialog(
            onDismissRequest = { showSiteDetails = false },
            title = { Text("Site details") },
            text = {
                Text(
                    "Hostname: ${host ?: "Unknown"}\n\n" +
                        "Address: ${url ?: "Unknown"}\n\n" +
                        "A valid HTTPS connection is encrypted, but does not prove a site is trustworthy. " +
                        "WebView checks for known threats when Safe Browsing is supported. " +
                        "Check the hostname before entering personal information."
                )
            },
            confirmButton = { TextButton(onClick = { showSiteDetails = false }) { Text("Close") } },
        )
    }
    if (isActive) blockedThreat?.let { warning ->
        AlertDialog(
            onDismissRequest = { blockedThreat = null },
            title = { Text("Unsafe content blocked") },
            text = { Text(warning) },
            confirmButton = { TextButton(onClick = { blockedThreat = null }) { Text("OK") } },
        )
    }
}

private class BrowserWebViewContainer(context: Context) : FrameLayout(context) {
    lateinit var page: WebView
    lateinit var popupHandler: BrowserPopupHandler
    var darkPagesEnabled: Boolean = false
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.applyJavaScriptFor(url: String?, permissions: JavaScriptSitePermissions) {
    settings.javaScriptEnabled = permissions.isAllowed(url)
}

private fun WebView.historyUrl(offset: Int): String? {
    val history = copyBackForwardList()
    val index = history.currentIndex + offset
    return if (index in 0 until history.size) history.getItemAtIndex(index)?.url else null
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configurePrivateSettings(darkPagesEnabled: Boolean) {
    PrivacyRelay.protectWebView(this)
    settings.javaScriptEnabled = false // Set for the validated HTTPS host before loading.
    settings.domStorageEnabled = true
    settings.setGeolocationEnabled(false)
    settings.databaseEnabled = false
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.allowFileAccessFromFileURLs = false
    settings.allowUniversalAccessFromFileURLs = false
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    settings.cacheMode = WebSettings.LOAD_NO_CACHE
    settings.setSupportMultipleWindows(true)
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.mediaPlaybackRequiresUserGesture = true
    setDarkPagesEnabled(darkPagesEnabled)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true
}

private fun WebView.setDarkPagesEnabled(enabled: Boolean) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, enabled)
    }
}
