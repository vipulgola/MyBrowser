package com.personal.mybrowser.browser

import androidx.lifecycle.ViewModelStore
import com.personal.mybrowser.engine.html.HtmlParser
import com.personal.mybrowser.network.LoadedHtml
import com.personal.mybrowser.network.PageLoader
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val example = LoadedHtml("https://example.com/", "<h1>Example Domain</h1>")

    @Before fun prepareMain() { Dispatchers.setMain(dispatcher) }
    @After fun cleanUp() { store.clear(); Dispatchers.resetMain() }

    @Test fun newBrowserStartsAtAnEmptyHome() {
        val browser = BrowserViewModel()
        assertEquals(WebPageState.Home, browser.webPage)
        assertEquals("", browser.addressInput)
        assertFalse(browser.canGoBack)
    }

    @Test fun loadingTransitionsToReady() = runTest {
        val browser = browser(PageLoader { example })
        assertEquals(WebPageState.Home, browser.webPage)
        browser.submitAddress()
        assertTrue(browser.webPage is WebPageState.Loading)
        advanceUntilIdle()
        assertEquals(WebPageState.Ready(example), browser.webPage)
    }

    @Test fun failureOffersARetryThatCanSucceed() = runTest {
        var attempts = 0
        val browser = browser(PageLoader { if (++attempts == 1) throw IOException("offline") else example })
        browser.submitAddress()
        advanceUntilIdle()
        assertTrue(browser.webPage is WebPageState.Failed)
        browser.submitAddress()
        assertTrue(browser.webPage is WebPageState.Loading)
        advanceUntilIdle()
        assertEquals(WebPageState.Ready(example), browser.webPage)
        assertEquals(2, attempts)
    }

    @Test fun backCancelsInitialLoadingAndReturnsHome() = runTest {
        val response = CompletableDeferred<LoadedHtml>()
        var cancelled = false
        val browser = browser(PageLoader { try { response.await() } finally { cancelled = true } })
        browser.submitAddress()
        runCurrent()
        browser.back()
        advanceUntilIdle()
        assertTrue(cancelled)
        assertEquals(WebPageState.Home, browser.webPage)
        assertEquals("", browser.addressInput)
        assertFalse(browser.canGoBack)
    }

    @Test fun aLateOldRequestCannotReplaceANewerResult() = runTest {
        val oldResponse = CompletableDeferred<LoadedHtml>()
        var calls = 0
        val browser = browser(PageLoader {
            if (++calls == 1) withContext(NonCancellable) { oldResponse.await() }
            else example.copy(html = "new result")
        })
        browser.submitAddress()
        runCurrent()
        browser.showHome()
        browser.editAddress("example.com")
        browser.submitAddress()
        runCurrent()
        oldResponse.complete(example.copy(html = "old result"))
        advanceUntilIdle()
        assertEquals(WebPageState.Ready(example.copy(html = "new result")), browser.webPage)
    }

    @Test fun repeatedLoadClicksDoNotStartParallelRequests() = runTest {
        var calls = 0
        val response = CompletableDeferred<LoadedHtml>()
        val browser = browser(PageLoader { calls++; response.await() })
        browser.submitAddress()
        runCurrent()
        browser.submitAddress()
        runCurrent()
        assertEquals(1, calls)
        response.complete(example)
        advanceUntilIdle()
    }

    @Test fun onlineLinksResolveAgainstTheDisplayedPage() = runTest {
        val requested = mutableListOf<String>()
        val browser = browser(PageLoader { address ->
            requested += address.value
            LoadedHtml(address.value, "<p>page</p>")
        })
        browser.editAddress("example.com/docs/start")
        browser.submitAddress()
        advanceUntilIdle()
        browser.editAddress("other.com/unsubmitted")
        assertTrue(browser.open("about.html"))
        assertEquals("https://example.com/docs/about.html", browser.addressInput)
        advanceUntilIdle()
        assertEquals(listOf("https://example.com/docs/start", "https://example.com/docs/about.html"), requested)
        browser.back()
        assertEquals("https://example.com/docs/start", (browser.webPage as WebPageState.Ready).page.url)
        assertEquals(2, requested.size)
        browser.back()
        assertEquals(WebPageState.Home, browser.webPage)
    }

    @Test fun invalidLinksLeavePageAndDraftUntouchedAndNeverFetch() = runTest {
        var calls = 0
        val browser = browser(PageLoader { calls++; example })
        browser.submitAddress()
        advanceUntilIdle()
        browser.editAddress("draft.example/")
        for (link in listOf("http://example.org", "javascript:123", "//user:pass@example.org", "%zz")) {
            assertFalse(browser.open(link))
            assertEquals(WebPageState.Ready(example), browser.webPage)
            assertEquals("draft.example/", browser.addressInput)
            assertTrue(browser.linkError != null)
        }
        assertEquals(1, calls)
        assertTrue(browser.open("/valid"))
        assertEquals(null, browser.linkError)
        advanceUntilIdle()
    }

    @Test fun failedLinkCanRetryItsResolvedAddress() = runTest {
        val requested = mutableListOf<String>()
        val browser = browser(PageLoader { address ->
            requested += address.value
            if (requested.size == 2) throw IOException("offline")
            LoadedHtml(address.value, "<p>page</p>")
        })
        browser.submitAddress()
        advanceUntilIdle()
        assertTrue(browser.open("//other.com/path"))
        advanceUntilIdle()
        assertEquals("https://other.com/path", (browser.webPage as WebPageState.Failed).address.value)
        browser.editAddress("unsubmitted.example")
        browser.retry()
        advanceUntilIdle()
        assertEquals(listOf("https://example.com/", "https://other.com/path", "https://other.com/path"), requested)
        assertEquals("https://other.com/path", (browser.webPage as WebPageState.Ready).page.url)
    }

    @Test fun linkCallbacksDuringLoadingOrFailureCannotStartAnotherRequest() = runTest {
        var calls = 0
        val browser = browser(PageLoader { calls++; throw IOException("offline") })
        browser.submitAddress()
        assertFalse(browser.open("/stale"))
        advanceUntilIdle()
        assertFalse(browser.open("/stale"))
        assertEquals(1, calls)
    }

    @Test fun certificateErrorsAreReportedAsConnectionFailures() = runTest {
        val browser = browser(PageLoader { throw SSLHandshakeException("bad certificate") })
        browser.submitAddress()
        advanceUntilIdle()
        assertTrue((browser.webPage as WebPageState.Failed).message.contains("secure connection"))
    }

    @Test fun timeoutErrorsHaveARetryableMessage() = runTest {
        val browser = browser(PageLoader { throw SocketTimeoutException("read timeout") })
        browser.submitAddress()
        advanceUntilIdle()
        assertTrue((browser.webPage as WebPageState.Failed).message.contains("timed out"))
    }

    @Test fun clearingTheViewModelCancelsItsRequest() = runTest {
        val response = CompletableDeferred<LoadedHtml>()
        var finished = false
        val browser = browser(PageLoader { try { response.await() } finally { finished = true } })
        browser.submitAddress()
        runCurrent()
        store.clear()
        advanceUntilIdle()
        assertTrue(finished)
    }

    @Test fun submissionStoresTheNormalizedAddressWithTheRequest() = runTest {
        val response = CompletableDeferred<LoadedHtml>()
        var received = ""
        val browser = browser(PageLoader { address -> received = address.value; response.await() })
        browser.editAddress("EXAMPLE.ORG:443/Path?q=%2F")
        assertTrue(browser.submitAddress())
        assertEquals("https://example.org/Path?q=%2F", browser.addressInput)
        assertEquals(browser.addressInput, (browser.webPage as WebPageState.Loading).address.value)
        runCurrent()
        assertEquals(browser.addressInput, received)
        response.complete(LoadedHtml(received, "<p>result</p>"))
        advanceUntilIdle()
    }

    @Test fun invalidInputNeverStartsARequestOrDiscardsTheCurrentPage() = runTest {
        var calls = 0
        val browser = browser(PageLoader { calls++; example })
        browser.submitAddress()
        advanceUntilIdle()
        val previous = browser.webPage
        browser.editAddress("http://example.org")
        assertFalse(browser.submitAddress())
        assertEquals(1, calls)
        assertEquals(previous, browser.webPage)
        assertTrue(browser.addressError!!.contains("HTTPS"))
        browser.editAddress("example.org")
        assertEquals(null, browser.addressError)
    }

    @Test fun retryUsesTheFailedRequestUrlRatherThanAnEditedDraft() = runTest {
        val requested = mutableListOf<String>()
        val browser = browser(PageLoader { address ->
            requested += address.value
            if (requested.size == 1) throw IOException("offline")
            LoadedHtml(address.value, "<p>success</p>")
        })
        browser.editAddress("example.org/first")
        browser.submitAddress()
        advanceUntilIdle()
        assertEquals("https://example.org/first", (browser.webPage as WebPageState.Failed).address.value)
        browser.editAddress("example.com/other")
        browser.retry()
        advanceUntilIdle()
        assertEquals(listOf("https://example.org/first", "https://example.org/first"), requested)
        assertEquals("https://example.org/first", (browser.webPage as WebPageState.Ready).page.url)
        assertEquals("https://example.org/first", browser.addressInput)
    }

    @Test fun editingTheDraftDoesNotChangeAnInflightRequest() = runTest {
        val response = CompletableDeferred<LoadedHtml>()
        val browser = browser(PageLoader { response.await() })
        browser.submitAddress()
        runCurrent()
        browser.editAddress("example.org")
        assertEquals("https://example.com/", (browser.webPage as WebPageState.Loading).address.value)
        response.complete(example)
        advanceUntilIdle()
        assertEquals(WebPageState.Ready(example), browser.webPage)
        assertEquals("example.org", browser.addressInput)
    }

    @Test fun submittingADifferentUrlReplacesTheRequestAndIgnoresTheOldResponse() = runTest {
        val oldResponse = CompletableDeferred<LoadedHtml>()
        val browser = browser(PageLoader { address ->
            if (address.value == "https://example.com/") withContext(NonCancellable) { oldResponse.await() }
            else LoadedHtml(address.value, "new result")
        })
        browser.submitAddress()
        runCurrent()
        browser.editAddress("example.org")
        browser.submitAddress()
        runCurrent()
        oldResponse.complete(example)
        advanceUntilIdle()
        assertEquals("https://example.org/", (browser.webPage as WebPageState.Ready).page.url)
    }

    @Test fun invalidDraftDoesNotCancelAnInflightRequest() = runTest {
        val response = CompletableDeferred<LoadedHtml>()
        val browser = browser(PageLoader { response.await() })
        browser.submitAddress()
        runCurrent()
        browser.editAddress("https://example.com/invalid space")
        assertFalse(browser.submitAddress())
        response.complete(example)
        advanceUntilIdle()
        assertEquals(WebPageState.Ready(example), browser.webPage)
    }

    @Test fun typingSearchTermsDoesNotFetchUntilSubmitted() = runTest {
        val requested = mutableListOf<String>()
        val browser = browser(PageLoader { address -> requested += address.value; LoadedHtml(address.value, "<p>results</p>") })
        browser.editAddress("android development")
        advanceUntilIdle()
        assertEquals(emptyList<String>(), requested)
        assertTrue(browser.submitAddress())
        advanceUntilIdle()
        val searchUrl = "https://lite.duckduckgo.com/lite/?q=android+development"
        assertEquals(listOf(searchUrl), requested)
        assertEquals(searchUrl, browser.addressInput)
        browser.open("https://example.com/")
        advanceUntilIdle()
        browser.back()
        assertEquals(searchUrl, (browser.webPage as WebPageState.Ready).page.url)
        browser.forward()
        assertEquals("https://example.com/", (browser.webPage as WebPageState.Ready).page.url)
        assertEquals(2, requested.size)
    }

    @Test fun searchRetryUsesOriginalEncodedQueryDespiteDraftEdits() = runTest {
        val requested = mutableListOf<String>()
        val browser = browser(PageLoader { address ->
            requested += address.value
            if (requested.size == 1) throw IOException("offline")
            LoadedHtml(address.value, "results")
        })
        browser.editAddress("C++ & Kotlin")
        browser.submitAddress()
        advanceUntilIdle()
        browser.editAddress("different search")
        browser.retry()
        advanceUntilIdle()
        assertEquals(2, requested.size)
        assertEquals(requested.first(), requested.last())
        assertEquals("https://lite.duckduckgo.com/lite/?q=C%2B%2B+%26+Kotlin", requested.last())
    }

    @Test fun searchAgainSubmissionCreatesOneVisitAndBackRestoresResults() = runTest {
        val requested = mutableListOf<String>()
        val html = "<form action='/lite/' method='post'><input name='q' value='first topic'><input type='submit' value='Search'></form>"
        val browser = browser(PageLoader { address ->
            requested += address.value
            LoadedHtml(address.value, html)
        })
        browser.editAddress("first topic")
        browser.submitAddress()
        advanceUntilIdle()
        val firstUrl = (browser.webPage as WebPageState.Ready).page.url
        val form = PageForm.find(HtmlParser().parse(html), firstUrl)!!
        assertEquals(1, requested.size)
        assertTrue(browser.open(form.submissionUrl("second topic").value))
        advanceUntilIdle()
        assertEquals("https://lite.duckduckgo.com/lite/?q=second+topic", (browser.webPage as WebPageState.Ready).page.url)
        browser.back()
        assertEquals(firstUrl, (browser.webPage as WebPageState.Ready).page.url)
        assertEquals(2, requested.size)
    }

    @Test fun embeddedYouTubeKeepsTheEnginePageForReturn() = runTest {
        val browser = browser(PageLoader { example })
        browser.submitAddress()
        advanceUntilIdle()
        browser.ownEngineSelected = true

        browser.openEmbeddedYouTube("https://youtube.com/watch?v=abc")
        assertTrue(browser.ownEngineSelected)
        assertEquals("https://youtube.com/watch?v=abc", browser.embeddedCurrentUrl)
        assertEquals(WebPageState.Ready(example), browser.webPage)

        browser.updateEmbeddedUrl("https://m.youtube.com/watch?v=abc")
        assertEquals("https://m.youtube.com/watch?v=abc", browser.embeddedCurrentUrl)
        browser.closeEmbeddedYouTube()
        assertEquals(null, browser.embeddedCurrentUrl)
        assertEquals(WebPageState.Ready(example), browser.webPage)
    }

    @Test fun aCompletedYouTubeRedirectDoesNotReopenThePanelAfterReturn() = runTest {
        val youtube = LoadedHtml("https://www.youtube.com/watch?v=abc", "<p>redirected</p>")
        val browser = browser(PageLoader { youtube })
        browser.submitAddress()
        advanceUntilIdle()
        assertEquals(WebPageState.Ready(youtube), browser.webPage)

        browser.openEmbeddedYouTube(youtube.url)
        assertEquals(youtube.url, browser.embeddedCurrentUrl)
        assertEquals(WebPageState.Home, browser.webPage)
        browser.closeEmbeddedYouTube()
        assertEquals(WebPageState.Home, browser.webPage)
    }

    private fun browser(loader: PageLoader) = BrowserViewModel(loader).also {
        store.put("browser", it)
        it.editAddress("example.com")
    }
}
