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
import kotlinx.coroutines.launch

/** Retains the current visit/history across rotation, with no disk or saved-state storage. */
class BrowserViewModel(private val loader: PageLoader = HttpsPageLoader()) : ViewModel() {
    var navigator by mutableStateOf(LocalNavigator(DemoPages.pages, DemoPages.HOME))
        private set

    var webPage by mutableStateOf<WebPageState>(WebPageState.Local)
        private set

    var addressInput by mutableStateOf("example.com")
        private set
    var addressError by mutableStateOf<String?>(null)
        private set

    var linkError by mutableStateOf<String?>(null)
        private set

    // Request state is separate: loading/failing never adds a visit or moves the cursor.
    private var onlineHistory by mutableStateOf(OnlineHistory())
    private var requestId = 0L
    private var request: Job? = null

    val canGoBack: Boolean get() = webPage != WebPageState.Local || navigator.canGoBack
    val canGoForward: Boolean get() = when (webPage) {
        WebPageState.Local -> navigator.canGoForward
        is WebPageState.Ready -> onlineHistory.canGoForward
        else -> false
    }

    fun forward() {
        if (!canGoForward) return
        if (webPage == WebPageState.Local) navigator = navigator.forward()
        else {
            onlineHistory = onlineHistory.forward()
            restoreCurrentVisit()
        }
    }

    fun reload() {
        when (val state = webPage) {
            WebPageState.Local -> navigator = navigator.reload()
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
        if (webPage != WebPageState.Local) return false
        val next = navigator.navigate(target)
        if (next === navigator) return false
        navigator = next
        return true
    }

    fun back() {
        if (webPage == WebPageState.Local) {
            navigator = navigator.back()
            return
        }
        if (webPage is WebPageState.Ready) {
            if (!onlineHistory.canGoBack) {
                showLocalDemo()
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
            showLocalDemo()
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
        request?.cancel()
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

    fun showLocalDemo() {
        cancelRequest()
        onlineHistory = OnlineHistory() // Leaving network mode releases this session's snapshots.
        webPage = WebPageState.Local
        addressError = null
        linkError = null
    }

    private fun cancelRequest() {
        requestId++
        request?.cancel()
        request = null
    }

    private fun fail(id: Long, address: HttpsAddress, replaceCurrent: Boolean, message: String) {
        if (id == requestId) webPage = WebPageState.Failed(address, message, isReload = replaceCurrent)
    }
}
