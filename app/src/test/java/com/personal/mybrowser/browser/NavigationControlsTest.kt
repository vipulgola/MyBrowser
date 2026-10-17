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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationControlsTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun prepare() { Dispatchers.setMain(dispatcher) }
    @After fun cleanUp() { store.clear(); Dispatchers.resetMain() }

    @Test fun forwardRestoresSavedContentWithoutAnotherRequest() = runTest {
        var calls = 0
        val browser = browser(PageLoader { LoadedHtml(it.value, "version ${++calls}") })
        for (path in listOf("a", "b", "c")) { go(browser, path); advanceUntilIdle() }
        assertFalse(browser.canGoForward)
        browser.back()
        assertTrue(browser.canGoForward)
        browser.back()
        browser.forward()
        assertEquals("version 2", ready(browser).html)
        browser.forward()
        assertEquals("https://example.com/c", browser.addressInput)
        assertEquals("version 3", ready(browser).html)
        assertFalse(browser.canGoForward)
        browser.forward()
        assertEquals(3, calls)
    }

    @Test fun reloadReplacesCurrentSnapshotAndPreservesForwardVisits() = runTest {
        var calls = 0
        val browser = browser(PageLoader { LoadedHtml(it.value, "version ${++calls}") })
        for (path in listOf("a", "b", "c")) { go(browser, path); advanceUntilIdle() }
        browser.back()
        browser.editAddress("unsubmitted.example")
        browser.reload()
        assertFalse(browser.canGoForward)
        advanceUntilIdle()
        assertEquals("https://example.com/b", browser.addressInput)
        assertEquals("version 4", ready(browser).html)
        assertTrue(browser.canGoForward)
        browser.forward()
        assertEquals("version 3", ready(browser).html)
        browser.back()
        assertEquals("version 4", ready(browser).html)
        browser.back()
        assertEquals("version 1", ready(browser).html)
        assertEquals(4, calls)
    }

    @Test fun failedReloadRetryStillReplacesRatherThanAppending() = runTest {
        var calls = 0
        val browser = browser(PageLoader {
            calls++
            if (calls == 2) throw IOException("offline")
            LoadedHtml(it.value, "version $calls")
        })
        go(browser, "a"); advanceUntilIdle()
        browser.reload(); advanceUntilIdle()
        assertTrue((browser.webPage as WebPageState.Failed).isReload)
        browser.retry(); advanceUntilIdle()
        assertEquals("version 3", ready(browser).html)
        browser.back()
        assertEquals(WebPageState.Local, browser.webPage)
    }

    @Test fun redirectDuringReloadUpdatesOnlyTheCurrentEntry() = runTest {
        var calls = 0
        val browser = browser(PageLoader {
            if (++calls == 3) LoadedHtml("https://other.example/final", "reloaded") else LoadedHtml(it.value, "page")
        })
        for (path in listOf("a", "b")) { go(browser, path); advanceUntilIdle() }
        browser.reload(); advanceUntilIdle()
        assertEquals("https://other.example/final", browser.addressInput)
        browser.back()
        assertEquals("https://example.com/a", browser.addressInput)
        browser.forward()
        assertEquals("reloaded", ready(browser).html)
        assertEquals(3, calls)
    }

    @Test fun stopRestoresCurrentVisitAndLateCompletionCannotOverwriteIt() = runTest {
        val pending = CompletableDeferred<LoadedHtml>()
        var calls = 0
        val browser = browser(PageLoader {
            if (++calls == 3) withContext(NonCancellable) { pending.await() } else LoadedHtml(it.value, "version $calls")
        })
        for (path in listOf("a", "b")) { go(browser, path); advanceUntilIdle() }
        browser.back() // A has B in its forward branch.
        browser.reload(); runCurrent()
        browser.stop()
        assertEquals("version 1", ready(browser).html)
        assertTrue(browser.canGoForward)
        pending.complete(LoadedHtml("https://example.com/a", "late"))
        advanceUntilIdle()
        assertEquals("version 1", ready(browser).html)
        browser.forward()
        assertEquals("version 2", ready(browser).html)
        assertEquals(3, calls)
    }

    @Test fun stopOnInitialRequestReturnsToLocalAndCancelsWork() = runTest {
        val pending = CompletableDeferred<LoadedHtml>()
        var cancelled = false
        val browser = browser(PageLoader { try { pending.await() } finally { cancelled = true } })
        browser.open(DemoPages.ABOUT)
        go(browser, "a"); runCurrent()
        browser.stop(); advanceUntilIdle()
        assertTrue(cancelled)
        assertEquals(WebPageState.Local, browser.webPage)
        assertEquals(DemoPages.ABOUT, browser.navigator.currentPath)
    }

    @Test fun localControlsPreserveVisitsAndDiscardForwardBranchOnNewNavigation() {
        val browser = browser(PageLoader { throw AssertionError("Local controls must not fetch") })
        assertFalse(browser.canGoBack)
        assertFalse(browser.canGoForward)
        browser.open(DemoPages.ABOUT)
        browser.back()
        assertTrue(browser.canGoForward)
        browser.reload()
        assertTrue(browser.canGoForward)
        browser.forward()
        assertEquals(DemoPages.ABOUT, browser.navigator.currentPath)
        browser.reload()
        browser.back()
        assertEquals(DemoPages.HOME, browser.navigator.currentPath)
        browser.open(DemoPages.TOO_DEEP)
        assertFalse(browser.canGoForward)
        browser.stop() // Idle Stop is a no-op.
        assertEquals(DemoPages.TOO_DEEP, browser.navigator.currentPath)
    }

    @Test fun newGoAfterAFailedReloadCreatesANewVisit() = runTest {
        var calls = 0
        val browser = browser(PageLoader {
            if (++calls == 2) throw IOException("offline")
            LoadedHtml(it.value, "page")
        })
        go(browser, "a"); advanceUntilIdle()
        browser.reload(); advanceUntilIdle()
        go(browser, "b"); advanceUntilIdle()
        browser.back()
        assertEquals("https://example.com/a", browser.addressInput)
    }

    @Test fun repeatedIdenticalVisitsStillHaveCorrectForwardAvailability() = runTest {
        val browser = browser(PageLoader { LoadedHtml(it.value, "same") })
        repeat(2) { go(browser, "a"); advanceUntilIdle() }
        browser.back()
        assertTrue(browser.canGoForward)
        browser.forward()
        assertFalse(browser.canGoForward)
    }

    @Test fun reloadAndForwardDuringLoadingDoNotStartExtraRequests() = runTest {
        var calls = 0
        val pending = CompletableDeferred<LoadedHtml>()
        val browser = browser(PageLoader { calls++; pending.await() })
        go(browser, "a"); runCurrent()
        browser.reload(); browser.forward(); runCurrent()
        assertEquals(1, calls)
        browser.stop(); advanceUntilIdle()
    }

    private fun browser(loader: PageLoader) = BrowserViewModel(loader).also { store.put("browser", it) }
    private fun go(browser: BrowserViewModel, path: String) {
        browser.editAddress("https://example.com/$path")
        assertTrue(browser.submitAddress())
    }
    private fun ready(browser: BrowserViewModel) = (browser.webPage as WebPageState.Ready).page
}
