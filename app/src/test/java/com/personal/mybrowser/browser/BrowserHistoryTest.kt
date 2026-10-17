package com.personal.mybrowser.browser

import androidx.lifecycle.ViewModelStore
import com.personal.mybrowser.network.LoadedHtml
import com.personal.mybrowser.network.PageLoader
import java.io.IOException
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
class BrowserHistoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun prepareMain() { Dispatchers.setMain(dispatcher) }
    @After fun cleanUp() { store.clear(); Dispatchers.resetMain() }

    @Test fun backRestoresHtmlAndAddressOfflineWithoutFetching() = runTest {
        var online = true
        val requested = mutableListOf<String>()
        val browser = browser(PageLoader { address ->
            requested += address.value
            if (!online) throw IOException("offline")
            LoadedHtml(address.value, "<p>${address.value}</p>")
        })
        for (path in listOf("a", "b", "c")) {
            submit(browser, path)
            advanceUntilIdle()
        }
        online = false
        browser.editAddress("unsubmitted.example")
        for (path in listOf("b", "a")) {
            browser.back()
            advanceUntilIdle()
            val url = "https://example.com/$path"
            assertEquals(WebPageState.Ready(LoadedHtml(url, "<p>$url</p>")), browser.webPage)
            assertEquals(url, browser.addressInput)
        }
        assertEquals(3, requested.size)
        browser.back()
        assertEquals(WebPageState.Local, browser.webPage)
    }

    @Test fun visitsToTheSameUrlRestoreDifferentHtmlVersions() = runTest {
        var calls = 0
        val browser = browser(PageLoader { address -> LoadedHtml(address.value, "version ${++calls}") })
        repeat(3) { submit(browser, "same"); advanceUntilIdle() }
        browser.back()
        assertEquals("version 2", (browser.webPage as WebPageState.Ready).page.html)
        browser.back()
        assertEquals("version 1", (browser.webPage as WebPageState.Ready).page.html)
        assertEquals(3, calls)
        browser.back()
        assertEquals(WebPageState.Local, browser.webPage)
    }

    @Test fun backDuringLoadingRestoresCurrentVisitAndIgnoresLateSuccess() = runTest {
        val pending = CompletableDeferred<LoadedHtml>()
        var calls = 0
        val browser = browser(PageLoader { address ->
            calls++
            if (address.value.endsWith("/c")) withContext(NonCancellable) { pending.await() }
            else LoadedHtml(address.value, address.value)
        })
        for (path in listOf("a", "b")) { submit(browser, path); advanceUntilIdle() }
        submit(browser, "c")
        runCurrent()
        browser.back()
        assertCurrent(browser, "b") // Cancelling C must not skip B.
        pending.complete(LoadedHtml("https://example.com/c", "late C"))
        advanceUntilIdle()
        assertCurrent(browser, "b")
        submit(browser, "d")
        advanceUntilIdle()
        browser.back()
        assertCurrent(browser, "b") // C must not have been appended behind the restored screen.
        browser.back()
        assertCurrent(browser, "a")
        assertEquals(4, calls)
    }

    @Test fun lateFailureCannotReplaceANewVisitAfterBack() = runTest {
        val releaseFailure = CompletableDeferred<Unit>()
        val browser = browser(PageLoader { address ->
            if (address.value.endsWith("/slow")) {
                withContext(NonCancellable) { releaseFailure.await() }
                throw IOException("late failure")
            }
            LoadedHtml(address.value, "page")
        })
        submit(browser, "a")
        advanceUntilIdle()
        submit(browser, "slow")
        runCurrent()
        browser.back()
        assertCurrent(browser, "a")
        submit(browser, "b")
        runCurrent()
        releaseFailure.complete(Unit)
        advanceUntilIdle()
        assertCurrent(browser, "b")
        browser.back()
        assertCurrent(browser, "a")
    }

    @Test fun failedVisitDoesNotSkipOrDuplicateTheLastSuccessfulPage() = runTest {
        val browser = browser(PageLoader { address ->
            if (address.value.endsWith("/failure")) throw IOException("offline")
            LoadedHtml(address.value, "page")
        })
        for (path in listOf("a", "b")) { submit(browser, path); advanceUntilIdle() }
        submit(browser, "failure")
        advanceUntilIdle()
        assertTrue(browser.webPage is WebPageState.Failed)
        browser.back()
        assertCurrent(browser, "b")
        browser.back()
        assertCurrent(browser, "a")
        browser.back()
        assertEquals(WebPageState.Local, browser.webPage)
    }

    @Test fun retryAddsExactlyOneVisitWhenItSucceeds() = runTest {
        var attempts = 0
        val browser = browser(PageLoader { address ->
            if (address.value.endsWith("/b") && ++attempts < 3) throw IOException("offline")
            LoadedHtml(address.value, "page")
        })
        submit(browser, "a")
        advanceUntilIdle()
        submit(browser, "b")
        advanceUntilIdle()
        repeat(2) { browser.retry(); advanceUntilIdle() }
        assertCurrent(browser, "b")
        assertEquals(3, attempts)
        browser.back()
        assertCurrent(browser, "a")
        browser.back()
        assertEquals(WebPageState.Local, browser.webPage)
    }

    @Test fun newNavigationAfterBackBranchesFromTheRestoredPage() = runTest {
        val requested = mutableListOf<String>()
        val browser = browser(PageLoader { address ->
            requested += address.value
            LoadedHtml(address.value, "page")
        })
        for (path in listOf("a", "docs/b", "elsewhere/c")) { submit(browser, path); advanceUntilIdle() }
        browser.back()
        assertCurrent(browser, "docs/b")
        browser.editAddress("other.example/unsubmitted")
        assertTrue(browser.open("d"))
        advanceUntilIdle()
        assertEquals("https://example.com/docs/d", requested.last())
        browser.back()
        assertCurrent(browser, "docs/b")
        browser.back()
        assertCurrent(browser, "a")
        browser.back()
        assertEquals(WebPageState.Local, browser.webPage)
        assertEquals(4, requested.size)
    }

    @Test fun rejectedInputDoesNotAddVisitsAndBackClearsItsErrors() = runTest {
        var calls = 0
        val browser = browser(PageLoader { address -> calls++; LoadedHtml(address.value, "page") })
        for (path in listOf("a", "b")) { submit(browser, path); advanceUntilIdle() }
        browser.editAddress("http://example.com/invalid")
        assertFalse(browser.submitAddress())
        assertFalse(browser.open("javascript:123"))
        browser.back()
        assertCurrent(browser, "a")
        assertEquals(null, browser.addressError)
        assertEquals(null, browser.linkError)
        assertEquals(2, calls)
    }

    @Test fun leavingNetworkModeClearsSnapshotsButPreservesLocalVisits() = runTest {
        val browser = browser(PageLoader { address -> LoadedHtml(address.value, "page") })
        browser.open(DemoPages.ABOUT)
        for (path in listOf("a", "b")) { submit(browser, path); advanceUntilIdle() }
        browser.showLocalDemo()
        assertEquals(DemoPages.ABOUT, browser.navigator.currentPath)
        submit(browser, "c")
        advanceUntilIdle()
        browser.back()
        assertEquals(WebPageState.Local, browser.webPage) // A/B cannot leak into the new session.
        assertEquals(DemoPages.ABOUT, browser.navigator.currentPath)
        browser.back()
        assertEquals(DemoPages.HOME, browser.navigator.currentPath)
    }

    @Test fun backAfterTheFirstVisitEndsTheOnlineSession() = runTest {
        val browser = browser(PageLoader { address -> LoadedHtml(address.value, "page") })
        submit(browser, "a")
        advanceUntilIdle()
        browser.back()
        assertEquals(WebPageState.Local, browser.webPage)
        submit(browser, "b")
        advanceUntilIdle()
        browser.back()
        assertEquals(WebPageState.Local, browser.webPage)
    }

    @Test fun oldestVisitIsUnavailableAfterTheTwentyVisitLimit() = runTest {
        var calls = 0
        val browser = browser(PageLoader { address -> calls++; LoadedHtml(address.value, "page") })
        for (number in 0..20) { submit(browser, "$number"); advanceUntilIdle() }
        for (number in 19 downTo 1) {
            browser.back()
            assertCurrent(browser, "$number")
        }
        browser.back()
        assertEquals(WebPageState.Local, browser.webPage)
        assertEquals(21, calls)
    }

    @Test fun failedInitialRequestReturnsToTheLocalVisit() = runTest {
        val browser = browser(PageLoader { throw IOException("offline") })
        browser.open(DemoPages.ABOUT)
        submit(browser, "a")
        advanceUntilIdle()
        browser.back()
        assertEquals(WebPageState.Local, browser.webPage)
        assertEquals(DemoPages.ABOUT, browser.navigator.currentPath)
    }

    @Test fun redirectedResultUsesFinalUrlForLinksAndOneHistoryEntry() = runTest {
        val requested = mutableListOf<String>()
        val browser = browser(PageLoader { address ->
            requested += address.value
            if (address.value.endsWith("/redirect")) LoadedHtml("https://other.example/docs/final", "final page")
            else LoadedHtml(address.value, "page")
        })
        submit(browser, "before")
        advanceUntilIdle()
        submit(browser, "redirect")
        advanceUntilIdle()
        assertEquals("https://other.example/docs/final", browser.addressInput)
        assertEquals("https://other.example/docs/final", (browser.webPage as WebPageState.Ready).page.url)
        assertTrue(browser.open("next"))
        advanceUntilIdle()
        assertEquals("https://other.example/docs/next", requested.last())
        browser.back()
        assertEquals("https://other.example/docs/final", browser.addressInput)
        browser.back()
        assertCurrent(browser, "before") // No intermediate redirect entry.
        assertEquals(3, requested.size)
    }

    @Test fun redirectCompletionPreservesAnUnsubmittedAddressEdit() = runTest {
        val pending = CompletableDeferred<LoadedHtml>()
        val browser = browser(PageLoader { pending.await() })
        submit(browser, "redirect")
        runCurrent()
        browser.editAddress("my-unsubmitted-draft.example")
        pending.complete(LoadedHtml("https://other.example/final", "final page"))
        advanceUntilIdle()
        assertEquals("my-unsubmitted-draft.example", browser.addressInput)
        assertEquals("https://other.example/final", (browser.webPage as WebPageState.Ready).page.url)
    }

    @Test fun backFromComplexOnlineContentRestoresSavedHtmlWithoutFetching() = runTest {
        var calls = 0
        val browser = browser(PageLoader { address ->
            calls++
            val html = if (address.value.endsWith("/complex")) DemoPages.pages.getValue(DemoPages.TOO_DEEP) else "<p>Good page</p>"
            LoadedHtml(address.value, html)
        })
        submit(browser, "a")
        advanceUntilIdle()
        submit(browser, "complex")
        advanceUntilIdle()
        assertEquals(PageContent.TooComplex, PageContent.parse((browser.webPage as WebPageState.Ready).page.html))
        browser.back()
        assertCurrent(browser, "a")
        assertTrue(PageContent.parse((browser.webPage as WebPageState.Ready).page.html) is PageContent.Ready)
        assertEquals(2, calls)
    }

    @Test fun backFromAnInitialComplexOnlinePageReturnsToLocalDemo() = runTest {
        val browser = browser(PageLoader { address -> LoadedHtml(address.value, DemoPages.pages.getValue(DemoPages.TOO_MANY_NODES)) })
        submit(browser, "complex")
        advanceUntilIdle()
        assertEquals(PageContent.TooComplex, PageContent.parse((browser.webPage as WebPageState.Ready).page.html))
        browser.back()
        assertEquals(WebPageState.Local, browser.webPage)
        assertTrue(PageContent.parse(browser.navigator.currentHtml) is PageContent.Ready)
    }

    private fun browser(loader: PageLoader) = BrowserViewModel(loader).also { store.put("browser", it) }
    private fun submit(browser: BrowserViewModel, path: String) {
        browser.editAddress("https://example.com/$path")
        assertTrue(browser.submitAddress())
    }
    private fun assertCurrent(browser: BrowserViewModel, path: String) {
        val url = "https://example.com/$path"
        assertEquals(url, (browser.webPage as WebPageState.Ready).page.url)
        assertEquals(url, browser.addressInput)
    }
}
