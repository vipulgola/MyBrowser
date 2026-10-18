package com.personal.mybrowser.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.mybrowser.network.HttpsPageLoader
import com.personal.mybrowser.network.HttpsAddress
import com.personal.mybrowser.network.AddressValidationException
import com.personal.mybrowser.network.PageLoadException
import com.personal.mybrowser.network.PageLoader
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** Retains the current visit/history across rotation, with no disk or saved-state storage. */
class BrowserViewModel(private val loader: PageLoader = HttpsPageLoader()) : ViewModel() {
    var webPage by mutableStateOf<WebPageState>(WebPageState.Home)
        private set

    var addressInput by mutableStateOf("")
        private set
    var addressError by mutableStateOf<String?>(null)
        private set

    var linkError by mutableStateOf<String?>(null)
        private set

    // Request state is separate: loading/failing never adds a visit or moves the cursor.
    private var onlineHistory by mutableStateOf(OnlineHistory())
    private var requestId = 0L
    private var request: Job? = null
    private val stoppingRequests = mutableListOf<Job>()
    private var lastIncomingUrl: String? = null
    var ownEngineSelected by mutableStateOf(false)
    var compatibleCurrentUrl by mutableStateOf<String?>(null)
    var embeddedCurrentUrl by mutableStateOf<String?>(null)
        private set

    /** Keep the engine session while YouTube plays in an embedded WebView panel. */
    fun openEmbeddedYouTube(url: String) {
        require(YouTubeRouting.isYouTubeUrl(url)) { "Expected a validated YouTube HTTPS URL." }
        stop()
        // A completed redirect may already have committed YouTube to engine history.
        // Restore the previous engine page so returning from the panel cannot loop.
        if ((webPage as? WebPageState.Ready)?.page?.url == url) back()
        embeddedCurrentUrl = url
    }

    fun updateEmbeddedUrl(url: String?) {
        if (url == null) closeEmbeddedYouTube()
        else if (CompatibleNavigation.isAllowedNavigation(url)) embeddedCurrentUrl = url
    }

    fun closeEmbeddedYouTube() {
        embeddedCurrentUrl = null
    }

    /** A fresh intent is a new visit; recreation of the same Activity is not. */
    fun acceptIncomingUrl(url: String, isNewIntent: Boolean): Boolean {
        if (!isNewIntent && lastIncomingUrl == url) return false
        lastIncomingUrl = url
        return true
    }

    val canGoBack: Boolean get() = webPage != WebPageState.Home
    val canGoForward: Boolean get() = when (webPage) {
        WebPageState.Home -> false
        is WebPageState.Ready -> onlineHistory.canGoForward
        else -> false
    }

    fun forward() {
        if (!canGoForward) return
        onlineHistory = onlineHistory.forward()
        restoreCurrentVisit()
    }

    fun reload() {
        when (val state = webPage) {
            WebPageState.Home -> Unit
            is WebPageState.Ready -> {
                val address = HttpsAddress.parseInput(state.page.url)
                addressInput = address.value
                addressError = null
                linkError = null
                load(address, replaceCurrent = true)
            }
            is WebPageState.Failed -> retry()
            is WebPageState.Loading -> Unit
        }
    }

    fun stop() {
        if (webPage is WebPageState.Loading) restoreCurrentVisit()
    }

    fun open(target: String): Boolean {
        linkError = null
        val ready = webPage as? WebPageState.Ready
        if (ready != null) {
            val address = try {
                HttpsAddress.parseInput(ready.page.url).resolveLink(target)
            } catch (error: AddressValidationException) {
                linkError = error.message
                return false
            }
            addressInput = address.value
            addressError = null
            load(address)
            return true
        }
        return false
    }

    fun back() {
        if (webPage == WebPageState.Home) return
        if (webPage is WebPageState.Ready) {
            if (!onlineHistory.canGoBack) {
                showHome()
                return
            }
            onlineHistory = onlineHistory.back()
        }
        // From Loading/Failed, return to the current committed visit, without skipping it.
        restoreCurrentVisit()
    }

    private fun restoreCurrentVisit() {
        val page = onlineHistory.currentPage
        if (page == null) {
            showHome()
            return
        }
        cancelRequest()
        addressInput = page.url
        addressError = null
        linkError = null
        webPage = WebPageState.Ready(page) // Restore HTML directly; never fetch on Back.
    }

    fun editAddress(value: String) {
        addressInput = value
        addressError = null
    }

    fun submitAddress(): Boolean {
        val address = try {
            AddressInputResolver.resolve(addressInput)
        } catch (error: AddressValidationException) {
            addressError = error.message
            return false // Invalid drafts do not interrupt the current page or request.
        }
        addressError = null
        addressInput = address.value
        load(address)
        return true
    }

    fun retry() {
        val failed = webPage as? WebPageState.Failed ?: return
        addressInput = failed.address.value
        addressError = null
        load(failed.address, replaceCurrent = failed.isReload)
    }

    private fun load(address: HttpsAddress, replaceCurrent: Boolean = false) {
        if ((webPage as? WebPageState.Loading)?.address == address) return
        cancelActiveRequest()
        val id = ++requestId
        webPage = WebPageState.Loading(address)
        request = viewModelScope.launch {
            try {
                val result = loader.load(address) // The loader dispatches blocking work to IO.
                ensureActive()
                if (id == requestId) {
                    onlineHistory = if (replaceCurrent) onlineHistory.replaceCurrent(result) else onlineHistory.visit(result)
                    // Show the final redirect URL without overwriting an unsubmitted user edit.
                    if (addressInput == address.value) addressInput = result.url
                    webPage = WebPageState.Ready(result)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SSLException) {
                fail(id, address, replaceCurrent, "A secure connection could not be verified.")
            } catch (_: SocketTimeoutException) {
                fail(id, address, replaceCurrent, "The connection timed out. Please try again.")
            } catch (error: PageLoadException) {
                fail(id, address, replaceCurrent, error.message ?: "The page could not be loaded.")
            } catch (_: IOException) {
                fail(id, address, replaceCurrent, "Could not load the page. Check your internet connection and try again.")
            } finally {
                if (id == requestId) request = null
            }
        }
    }

    fun showHome() {
        cancelRequest()
        onlineHistory = OnlineHistory() // Release this session's HTML snapshots.
        webPage = WebPageState.Home
        addressInput = ""
        addressError = null
        linkError = null
    }

    /** Wait for any blocking engine fetch to finish canceling before changing network routes. */
    suspend fun stopAndAwait() {
        cancelRequest()
        while (true) {
            val pending = stoppingRequests.filterNot(Job::isCompleted)
            if (pending.isEmpty()) {
                stoppingRequests.clear()
                return
            }
            pending.joinAll()
            stoppingRequests.removeAll(Job::isCompleted)
        }
    }

    private fun cancelRequest() {
        requestId++
        cancelActiveRequest()
    }

    private fun cancelActiveRequest() {
        request?.let { active ->
            active.cancel()
            stoppingRequests.removeAll(Job::isCompleted)
            stoppingRequests.add(active)
        }
        request = null
    }

    private fun fail(id: Long, address: HttpsAddress, replaceCurrent: Boolean, message: String) {
        if (id == requestId) webPage = WebPageState.Failed(address, message, isReload = replaceCurrent)
    }
}
