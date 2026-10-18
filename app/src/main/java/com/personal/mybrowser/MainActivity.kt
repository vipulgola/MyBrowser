package com.personal.mybrowser

import android.content.Intent
import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.personal.mybrowser.browser.BrowserAppLockSettings
import com.personal.mybrowser.browser.BrowserHistoryDialog
import com.personal.mybrowser.browser.BrowserHistoryRepository
import com.personal.mybrowser.browser.BrowserPopupSettings
import com.personal.mybrowser.browser.BrowserSettingsDialog
import com.personal.mybrowser.browser.BrowserTorSettings
import com.personal.mybrowser.browser.BrowserViewModel
import com.personal.mybrowser.browser.BrowserFullscreenHost
import com.personal.mybrowser.browser.BrowserNavigation
import com.personal.mybrowser.browser.CompatibleBrowser
import com.personal.mybrowser.browser.CompatibleNavigation
import com.personal.mybrowser.browser.PageContent
import com.personal.mybrowser.browser.IncomingWebLink
import com.personal.mybrowser.browser.NormalBrowserTabs
import com.personal.mybrowser.browser.PageForm
import com.personal.mybrowser.browser.WebPageState
import com.personal.mybrowser.browser.YouTubeRouting
import com.personal.mybrowser.engine.html.HtmlNode
import com.personal.mybrowser.engine.render.HtmlPageView
import com.personal.mybrowser.network.AddressValidationException
import com.personal.mybrowser.network.PrivacyRelay
import com.personal.mybrowser.ui.theme.MyBrowserTheme

class MainActivity : FragmentActivity(), BrowserFullscreenHost {
    private data class RouteRequest(val id: Long, val torEnabled: Boolean, val closeSession: Boolean)

    private lateinit var browser: BrowserViewModel
    private var compatibleNavigation by mutableStateOf<BrowserNavigation?>(null)
    private var nextNavigationId = 0L
    private var relayStatus by mutableStateOf<PrivacyRelay.Status?>(null)
    private var requestedRoute by mutableStateOf<RouteRequest?>(null)
    private var nextRouteId = 0L
    private lateinit var popupSettings: BrowserPopupSettings
    private lateinit var appLockSettings: BrowserAppLockSettings
    private lateinit var browserHistory: BrowserHistoryRepository
    private lateinit var torSettings: BrowserTorSettings
    private var popupsAllowed by mutableStateOf(false)
    private var appLockEnabled by mutableStateOf(false)
    private var appLocked by mutableStateOf(false)
    private var appLockMessage by mutableStateOf<String?>(null)
    private var appLockPromptVisible = false
    private var torEnabled by mutableStateOf(true)
    private var settingsVisible by mutableStateOf(false)
    private var historyVisible by mutableStateOf(false)
    private var settingsError by mutableStateOf<String?>(null)
    private var fullscreenView: View? = null
    private var fullscreenCallback: android.webkit.WebChromeClient.CustomViewCallback? = null
    private val fullscreenBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = hideWebFullscreen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, fullscreenBackCallback)
        browser = ViewModelProvider(this)[BrowserViewModel::class.java]
        popupSettings = BrowserPopupSettings(applicationContext)
        appLockSettings = BrowserAppLockSettings(applicationContext)
        browserHistory = BrowserHistoryRepository(applicationContext)
        torSettings = BrowserTorSettings(applicationContext)
        popupsAllowed = popupSettings.isEnabled()
        appLockEnabled = appLockSettings.isEnabled()
        appLocked = appLockEnabled
        torEnabled = torSettings.isEnabled()
        if (!browser.ownEngineSelected) {
            browser.compatibleCurrentUrl?.let(::navigateCompatible)
        }
        openIncomingLink(intent, isNewIntent = false)
        checkPrivacyRelay()
        setContent {
            val useDarkPages = isSystemInDarkTheme()
            MyBrowserTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val pendingRoute = requestedRoute
                    if (pendingRoute != null) {
                        // This effect runs after composition removes the old WebViews.
                        LaunchedEffect(pendingRoute.id) {
                            if (pendingRoute.closeSession) {
                                browser.stopAndAwait()
                                browser.showHome()
                            }
                            relayStatus = PrivacyRelay.prepare(applicationContext, pendingRoute.torEnabled)
                            requestedRoute = null
                        }
                    }
                    if (pendingRoute != null || relayStatus !in setOf(PrivacyRelay.Status.READY, PrivacyRelay.Status.DIRECT)) {
                        PrivacyRelayGate(
                            status = relayStatus,
                            torEnabled = torEnabled,
                            onRetry = ::checkPrivacyRelay,
                            onOpenOrbot = ::openOrbot,
                            onOpenSettings = ::openSettings,
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else if (!browser.ownEngineSelected) {
                        NormalBrowserTabs(
                            navigationRequest = compatibleNavigation,
                            onCurrentUrlChange = { browser.compatibleCurrentUrl = it },
                            popupsAllowed = popupsAllowed,
                            torEnabled = torEnabled,
                            darkPagesEnabled = useDarkPages,
                            onOpenSettings = ::openSettings,
                            onOpenHistory = { historyVisible = true },
                            onPageVisit = { url, title -> browserHistory.record(url, title) },
                            onOwnEngine = { currentUrl ->
                                if (YouTubeRouting.isYouTubeUrl(currentUrl)) {
                                    browser.openEmbeddedYouTube(checkNotNull(currentUrl))
                                    browser.ownEngineSelected = true
                                } else {
                                    browser.closeEmbeddedYouTube()
                                    browser.ownEngineSelected = true
                                    if (currentUrl != null && CompatibleNavigation.isAllowedNavigation(currentUrl)) {
                                        browser.editAddress(currentUrl)
                                        browser.submitAddress()
                                    } else {
                                        browser.showHome()
                                    }
                                }
                            },
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else if (browser.embeddedCurrentUrl != null) {
                        EmbeddedYouTubePanel(
                            currentUrl = checkNotNull(browser.embeddedCurrentUrl),
                            onCurrentUrlChange = browser::updateEmbeddedUrl,
                            onBackToEngine = browser::closeEmbeddedYouTube,
                            torEnabled = torEnabled,
                            darkPagesEnabled = useDarkPages,
                            onOpenSettings = ::openSettings,
                            onOpenHistory = { historyVisible = true },
                            onPageVisit = { url, title -> browserHistory.record(url, title) },
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        OwnEngineBrowser(
                            browser = browser,
                            onCompatible = {
                                val currentUrl = when (val page = browser.webPage) {
                                    is WebPageState.Ready -> page.page.url
                                    is WebPageState.Loading -> page.address.value
                                    is WebPageState.Failed -> page.address.value
                                    WebPageState.Home -> null
                                }
                                switchToCompatible(currentUrl)
                            },
                            onYouTube = browser::openEmbeddedYouTube,
                            onOpenSettings = ::openSettings,
                            onOpenHistory = { historyVisible = true },
                            onPageVisit = { url, title -> browserHistory.record(url, title) },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                    }
                    if (appLocked) AppLockOverlay(
                        message = appLockMessage,
                        onUnlock = ::requestAppUnlock,
                        onExit = { moveTaskToBack(true) },
                    )
                }
                if (settingsVisible && !appLocked) BrowserSettingsDialog(
                    torEnabled = torEnabled,
                    onTorEnabledChange = ::changeTorMode,
                    biometricLockEnabled = appLockEnabled,
                    onBiometricLockEnabledChange = ::changeAppLock,
                    popupsAllowed = popupsAllowed,
                    onPopupsAllowedChange = { allowed ->
                        if (popupSettings.setEnabled(allowed)) {
                            popupsAllowed = allowed
                            settingsError = null
                        } else settingsError = "Could not save the pop-up setting. Try again."
                    },
                    error = settingsError,
                    onDismiss = { settingsVisible = false },
                )
                if (historyVisible && !appLocked) BrowserHistoryDialog(
                    entries = browserHistory.entries,
                    onOpen = { entry ->
                        historyVisible = false
                        switchToCompatible(entry.url)
                    },
                    onRemove = { entry -> browserHistory.remove(entry.id) },
                    onClear = browserHistory::clear,
                    onDismiss = { historyVisible = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openIncomingLink(intent, isNewIntent = true)
    }

    override fun onPostResume() {
        super.onPostResume()
        if (appLockEnabled && appLocked) requestAppUnlock()
    }

    override fun onPause() {
        if (appLockEnabled && !appLockPromptVisible) appLocked = true
        super.onPause()
    }

    private fun checkPrivacyRelay() {
        relayStatus = null
        requestedRoute = RouteRequest(++nextRouteId, torEnabled, closeSession = false)
    }

    private fun openSettings() {
        settingsError = null
        settingsVisible = true
    }

    private fun changeAppLock(enabled: Boolean) {
        if (enabled == appLockEnabled) return
        if (enabled && !canUseAppLock()) {
            settingsError = "Set up a biometric or a device screen lock before enabling app lock."
            return
        }
        if (!appLockSettings.setEnabled(enabled)) {
            settingsError = "Could not save the app-lock setting. Try again."
            return
        }
        appLockEnabled = enabled
        appLockMessage = null
        settingsError = null
        if (enabled) {
            appLocked = true
            settingsVisible = false
            requestAppUnlock()
        } else appLocked = false
    }

    private fun canUseAppLock(): Boolean {
        val biometrics = BiometricManager.from(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return biometrics.canAuthenticate(appLockAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS
        }
        val biometricReady = biometrics.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
        val keyguard = getSystemService(KeyguardManager::class.java)
        return biometricReady || keyguard?.isDeviceSecure == true
    }

    private fun appLockAuthenticators(): Int =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    @Suppress("DEPRECATION")
    private fun requestAppUnlock() {
        if (!appLockEnabled || !appLocked || appLockPromptVisible || isFinishing || isDestroyed) return
        if (!canUseAppLock()) {
            appLockMessage = "Set up a biometric or a device screen lock to unlock MyBrowser."
            return
        }
        appLockPromptVisible = true
        val prompt = BiometricPrompt(this, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                appLockPromptVisible = false
                appLockMessage = null
                appLocked = false
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                appLockPromptVisible = false
                appLockMessage = "Unlock MyBrowser to continue."
            }
        })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock MyBrowser")
            .setSubtitle("Use biometrics or your device screen lock")
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(appLockAuthenticators())
                } else setDeviceCredentialAllowed(true)
            }
            .build()
        prompt.authenticate(promptInfo)
    }

    private fun changeTorMode(enabled: Boolean) {
        if (enabled == torEnabled) return
        if (!torSettings.setEnabled(enabled)) {
            settingsError = "Could not save the Tor setting. Try again."
            return
        }
        torEnabled = enabled
        settingsVisible = false
        browser.closeEmbeddedYouTube()
        browser.ownEngineSelected = false
        browser.compatibleCurrentUrl = null
        compatibleNavigation = null
        relayStatus = null
        requestedRoute = RouteRequest(++nextRouteId, torEnabled, closeSession = true)
    }

    private fun openOrbot() {
        val launch = packageManager.getLaunchIntentForPackage("org.torproject.android")
        if (launch != null) startActivity(launch)
        else Toast.makeText(this, "Install Orbot from orbot.app first.", Toast.LENGTH_LONG).show()
    }

    override fun showWebFullscreen(view: View, callback: android.webkit.WebChromeClient.CustomViewCallback) {
        hideWebFullscreen()
        val content = findViewById<ViewGroup>(android.R.id.content)
        (view.parent as? ViewGroup)?.removeView(view)
        view.setBackgroundColor(Color.BLACK)
        content.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        fullscreenView = view
        fullscreenCallback = callback
        fullscreenBackCallback.isEnabled = true
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun hideWebFullscreen() {
        val view = fullscreenView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        fullscreenView = null
        val callback = fullscreenCallback
        fullscreenCallback = null
        fullscreenBackCallback.isEnabled = false
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        callback?.onCustomViewHidden()
    }

    override fun onDestroy() {
        hideWebFullscreen()
        super.onDestroy()
    }

    private fun openIncomingLink(intent: Intent?, isNewIntent: Boolean) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val address = IncomingWebLink.resolve(intent.dataString) ?: return
        if (!browser.acceptIncomingUrl(address.value, isNewIntent)) return
        switchToCompatible(address.value)
    }

    private fun switchToCompatible(url: String?) {
        browser.stop()
        browser.closeEmbeddedYouTube()
        browser.ownEngineSelected = false
        if (url != null) navigateCompatible(url)
        else {
            compatibleNavigation = null
            browser.compatibleCurrentUrl = null
        }
    }

    private fun navigateCompatible(url: String) {
        browser.compatibleCurrentUrl = url
        compatibleNavigation = BrowserNavigation(++nextNavigationId, url)
    }
}

@Composable
private fun PrivacyRelayGate(
    status: PrivacyRelay.Status?,
    torEnabled: Boolean,
    onRetry: () -> Unit,
    onOpenOrbot: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (torEnabled) "Private connection required" else "Changing connection mode",
            style = MaterialTheme.typography.headlineMedium)
        when (status) {
            null -> {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
                Text(if (torEnabled) "Checking the Tor connection…" else "Preparing direct browsing…",
                    modifier = Modifier.padding(top = 16.dp))
            }
            PrivacyRelay.Status.ORBOT_UNAVAILABLE -> {
                Text(
                    "MyBrowser will not open websites using your direct IP. " +
                        "Connect Orbot to Tor, enable Power User mode and its HTTP proxy on port 8118, then retry. " +
                        "Orbot does not need VPN mode for this.",
                    modifier = Modifier.padding(top = 16.dp),
                )
                TextButton(onClick = onOpenOrbot, modifier = Modifier.padding(top = 12.dp)) { Text("Open Orbot") }
                Button(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) { Text("Retry connection") }
            }
            PrivacyRelay.Status.WEBVIEW_UNSUPPORTED -> {
                Text(
                    "This device's WebView lacks a required privacy feature. Browsing is blocked to protect your IP.",
                    modifier = Modifier.padding(top = 16.dp),
                )
                Button(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) { Text("Retry") }
            }
            PrivacyRelay.Status.PROXY_CHANGE_FAILED -> {
                Text("Could not change the WebView connection route. Browsing is paused.",
                    modifier = Modifier.padding(top = 16.dp))
                Button(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) { Text("Retry") }
            }
            PrivacyRelay.Status.READY, PrivacyRelay.Status.DIRECT -> Unit
        }
        TextButton(onClick = onOpenSettings, modifier = Modifier.padding(top = 12.dp)) { Text("Settings") }
    }
}

@Composable
private fun EmbeddedYouTubePanel(
    currentUrl: String,
    onCurrentUrlChange: (String?) -> Unit,
    onBackToEngine: () -> Unit,
    torEnabled: Boolean,
    darkPagesEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onPageVisit: (url: String, title: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (YouTubeRouting.isYouTubeUrl(currentUrl)) "Experimental mode · YouTube uses WebView"
                else "Experimental mode · WebView page",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onBackToEngine) { Text("Back to engine") }
        }
        CompatibleBrowser(
            navigationRequest = BrowserNavigation(0L, currentUrl),
            onCurrentUrlChange = onCurrentUrlChange,
            onOwnEngine = { onBackToEngine() },
            engineButtonLabel = "Back to engine",
            torEnabled = torEnabled,
            darkPagesEnabled = darkPagesEnabled,
            onOpenSettings = onOpenSettings,
            onOpenHistory = onOpenHistory,
            onPageVisit = onPageVisit,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun OwnEngineBrowser(
    browser: BrowserViewModel,
    modifier: Modifier = Modifier,
    onCompatible: () -> Unit,
    onYouTube: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onPageVisit: (url: String, title: String?) -> Unit,
) {
    val webPage = browser.webPage
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(webPage) {
        (webPage as? WebPageState.Ready)?.let { onPageVisit(it.page.url, null) }
    }
    val submit = {
        val youtubeUrl = YouTubeRouting.fromAddressInput(browser.addressInput)
        if (youtubeUrl != null) {
            focusManager.clearFocus()
            onYouTube(youtubeUrl)
        } else if (browser.submitAddress()) focusManager.clearFocus()
    }
    BackHandler(enabled = browser.canGoBack) { browser.back() }
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
                    value = browser.addressInput,
                    onValueChange = browser::editAddress,
                    modifier = Modifier.weight(1f),
                    label = { Text("Search or enter address") },
                    placeholder = { Text("Search DuckDuckGo") },
                    singleLine = true,
                    isError = browser.addressError != null,
                    supportingText = browser.addressError?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                )
                Button(onClick = submit) { Text("Go") }
            }
            if (webPage != WebPageState.Home) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = browser::back, enabled = browser.canGoBack) { Text("Back") }
                    TextButton(onClick = browser::forward, enabled = browser.canGoForward) { Text("Forward") }
                    if (webPage is WebPageState.Loading) {
                        TextButton(onClick = browser::stop) { Text("Stop") }
                    } else {
                        TextButton(onClick = browser::reload) { Text("Reload") }
                    }
                }
            }
            Text(
                text = when (webPage) {
                    WebPageState.Home -> "Experimental engine"
                    is WebPageState.Loading -> webPage.address.value
                    is WebPageState.Failed -> webPage.address.value
                    is WebPageState.Ready -> webPage.page.url
                },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (webPage != WebPageState.Home) {
                TextButton(onClick = browser::showHome) { Text("Home") }
            }
            TextButton(onClick = onCompatible) { Text("Normal browser") }
            TextButton(onClick = onOpenHistory) { Text("History") }
            TextButton(onClick = onOpenSettings) { Text("Settings") }
            Text(
                "Ad blocking and known-threat checking are unavailable in this experimental mode.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        when (webPage) {
            WebPageState.Home -> Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Own engine", style = MaterialTheme.typography.headlineMedium)
                Text("Enter an HTTPS address above. This experimental renderer supports only simple pages.")
                Button(onClick = onCompatible, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Open normal browser")
                }
            }
            is WebPageState.Loading -> Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text("Loading page…", modifier = Modifier.padding(top = 16.dp))
            }
            is WebPageState.Failed -> Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(webPage.message)
                Button(onClick = browser::retry, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
            }
            is WebPageState.Ready -> {
                if (YouTubeRouting.isYouTubeUrl(webPage.page.url)) {
                    LaunchedEffect(webPage.page.url) { onYouTube(webPage.page.url) }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Text("Opening YouTube in normal browser…", modifier = Modifier.padding(top = 16.dp))
                    }
                } else {
                    val content = remember(webPage) { PageContent.parse(webPage.page.html) }
                    when (content) {
                        PageContent.TooComplex -> Column(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("This page is too complex for this browser yet.")
                            Button(onClick = browser::back, modifier = Modifier.padding(top = 12.dp)) {
                                Text("Go back")
                            }
                            Button(onClick = onCompatible, modifier = Modifier.padding(top = 8.dp)) {
                                Text("Open in normal browser")
                            }
                        }
                        is PageContent.Ready -> {
                            val form = remember(content.document, webPage) {
                                PageForm.find(content.document, webPage.page.url)
                            }
                            val navigate: (String) -> Unit = { target ->
                                val youtubeUrl = (browser.webPage as? WebPageState.Ready)?.page?.url
                                    ?.let { YouTubeRouting.fromPageLink(it, target) }
                                if (youtubeUrl != null) onYouTube(youtubeUrl)
                                else if (!browser.open(target)) {
                                    val message = browser.linkError ?: "Wait for the page to finish loading."
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }
                            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                if (form != null) PageFormControls(form, navigate)
                                BrowserPage(
                                    document = content.document,
                                    pageUrl = webPage.page.url,
                                    onLinkClick = navigate,
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLockOverlay(
    message: String?,
    onUnlock: () -> Unit,
    onExit: () -> Unit,
) {
    BackHandler(onBack = onExit)
    Box(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("MyBrowser is locked", style = MaterialTheme.typography.headlineMedium)
            Text(
                message ?: "Verify your identity to return to private browsing.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onUnlock) { Text("Unlock") }
            TextButton(onClick = onExit) { Text("Exit") }
        }
    }
}

@Composable
private fun PageFormControls(form: PageForm, navigate: (String) -> Unit) {
    val context = LocalContext.current
    var value by remember(form) { mutableStateOf(form.initialValue) }
    val submit = {
        try {
            navigate(form.submissionUrl(value).value)
        } catch (error: AddressValidationException) {
            Toast.makeText(context, error.message ?: "This form cannot be submitted.", Toast.LENGTH_SHORT).show()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.weight(1f),
            label = { Text(form.fieldLabel) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )
        Button(onClick = submit) { Text(form.submitLabel) }
    }
}

@Composable
private fun BrowserPage(
    document: HtmlNode.Document,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    pageUrl: String? = null,
) {
    val foreground = MaterialTheme.colorScheme.onBackground.toArgb()
    val background = MaterialTheme.colorScheme.background.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            ScrollView(context).apply {
                isFillViewport = true
                addView(
                    HtmlPageView(context),
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
                )
            }
        },
        update = { scrollView ->
            val changed = (scrollView.getChildAt(0) as HtmlPageView)
                .show(document, pageUrl, foreground, background, linkColor, onLinkClick)
            if (changed) scrollView.post { scrollView.scrollTo(0, 0) }
        },
    )
}
