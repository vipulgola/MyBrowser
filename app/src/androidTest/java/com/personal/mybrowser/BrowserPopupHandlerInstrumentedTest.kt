package com.personal.mybrowser

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.mybrowser.browser.BrowserPopupHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserPopupHandlerInstrumentedTest {
    @Test fun activityFullscreenHostCoversTheBrowserAndBackRestoresIt() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val video = View(activity)
                var hidden = 0
                activity.showWebFullscreen(video, object : WebChromeClient.CustomViewCallback {
                    override fun onCustomViewHidden() { hidden++ }
                })
                assertTrue(video.parent is ViewGroup)
                activity.onBackPressedDispatcher.onBackPressed()
                assertNull(video.parent)
                assertEquals(1, hidden)
            }
        } finally {
            scenario.close()
        }
    }

    @Test fun forwardsFullscreenVideoRequestsToTheActivityHost() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            var shown: View? = null
            var hidden = false
            val handler = BrowserPopupHandler(
                container = FrameLayout(context),
                canOpenTab = { false },
                openTab = {},
                showFullscreen = { view, _ -> shown = view },
                hideFullscreen = { hidden = true },
            )
            val video = View(context)
            val callback = object : WebChromeClient.CustomViewCallback {
                override fun onCustomViewHidden() = Unit
            }
            handler.onShowCustomView(video, callback)
            assertEquals(video, shown)
            handler.onHideCustomView()
            assertTrue(hidden)
        }
    }

    @Test fun deniesWebsiteGeolocationWithoutRememberingPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val handler = BrowserPopupHandler(
                container = FrameLayout(instrumentation.targetContext),
                canOpenTab = { false },
                openTab = {},
            )
            var result: Triple<String, Boolean, Boolean>? = null
            handler.onGeolocationPermissionsShowPrompt("https://example.com") { origin, allowed, retain ->
                result = Triple(origin, allowed, retain)
            }
            assertEquals(Triple("https://example.com", false, false), result)
        }
    }

    @Test fun rejectsNewWindowRequestsWithAndWithoutUserGesture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val container = FrameLayout(instrumentation.targetContext)
            val source = WebView(instrumentation.targetContext)
            try {
                val handler = BrowserPopupHandler(container, canOpenTab = { false }, openTab = {})
                for (gesture in listOf(false, true)) {
                    val message = Message.obtain(Handler(Looper.getMainLooper())).apply {
                        obj = source.WebViewTransport()
                    }
                    assertFalse(handler.onCreateWindow(source, false, gesture, message))
                    assertNull((message.obj as WebView.WebViewTransport).webView)
                }
            } finally {
                source.destroy()
            }
        }
    }

    @Test fun allowedTappedPopupOpensValidatedAddressInNewTab() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val opened = CountDownLatch(1)
        val addresses = mutableListOf<String>()
        lateinit var source: WebView
        lateinit var handler: BrowserPopupHandler
        instrumentation.runOnMainSync {
            val container = FrameLayout(instrumentation.targetContext)
            source = WebView(instrumentation.targetContext)
            handler = BrowserPopupHandler(container, canOpenTab = { true }, openTab = { url ->
                addresses.add(url)
                opened.countDown()
            })
            val message = Message.obtain(Handler(Looper.getMainLooper())).apply {
                obj = source.WebViewTransport()
            }
            assertTrue(handler.onCreateWindow(source, false, true, message))
            val probe = checkNotNull((message.obj as WebView.WebViewTransport).webView)
            assertTrue(probe.settings.blockNetworkLoads)
            probe.loadUrl("http://example.com/")
        }
        try {
            assertTrue("Allowed pop-up was not routed", opened.await(10, TimeUnit.SECONDS))
            assertEquals(listOf("https://example.com/"), addresses)
        } finally {
            instrumentation.runOnMainSync {
                handler.dispose()
                source.destroy()
            }
        }
    }

    @Test fun tappedTargetBlankLinkLeavesCurrentPageAndHistoryUntouched() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pageLoaded = CountDownLatch(1)
        val popupBlocked = CountDownLatch(1)
        val originalUrl = "https://popup-test.invalid/"
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var container: FrameLayout
        lateinit var source: WebView
        try {
            scenario.onActivity { activity ->
                container = FrameLayout(activity)
                source = WebView(activity)
                source.settings.javaScriptEnabled = true
                source.settings.setSupportMultipleWindows(true)
                source.settings.javaScriptCanOpenWindowsAutomatically = false
                source.webChromeClient = BrowserPopupHandler(
                    container = container,
                    canOpenTab = { false },
                    openTab = {},
                    onBlocked = { popupBlocked.countDown() },
                )
                source.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        if (url == originalUrl) pageLoaded.countDown()
                    }
                }
                container.addView(source, FrameLayout.LayoutParams(800, 400))
                activity.addContentView(container, ViewGroup.LayoutParams(800, 400))
                source.loadDataWithBaseURL(
                    originalUrl,
                    "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
                        "<body style='margin:0'><a href='https://example.com/' target='_blank' " +
                        "style='display:block;width:100vw;height:100vh'>Open</a></body></html>",
                    "text/html", "UTF-8", null,
                )
            }
            assertTrue("Test page did not load", pageLoaded.await(10, TimeUnit.SECONDS))
            var historyBefore = -1
            var urlBefore: String? = null
            val location = IntArray(2)
            scenario.onActivity {
                historyBefore = source.copyBackForwardList().size
                urlBefore = source.url
                source.getLocationOnScreen(location)
            }
            val bodyBefore = bodyText(scenario, source)
            assertTrue("Test page content is missing", bodyBefore.contains("Open"))
            instrumentation.waitForIdleSync()
            val downTime = SystemClock.uptimeMillis()
            val x = (location[0] + 400).toFloat()
            val y = (location[1] + 200).toFloat()
            instrumentation.sendPointerSync(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0))
            instrumentation.sendPointerSync(MotionEvent.obtain(downTime, downTime + 80, MotionEvent.ACTION_UP, x, y, 0))

            assertTrue("New-window request did not reach the blocker", popupBlocked.await(10, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(urlBefore, source.url)
                assertEquals(historyBefore, source.copyBackForwardList().size)
            }
            assertEquals(bodyBefore, bodyText(scenario, source))
        } finally {
            scenario.onActivity {
                container.removeView(source)
                source.destroy()
                (container.parent as? ViewGroup)?.removeView(container)
            }
            scenario.close()
        }
    }

    @Test fun tappedTargetBlankLinkOpensNewTabWhenAllowed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pageLoaded = CountDownLatch(1)
        val popupOpened = CountDownLatch(1)
        val openedAddress = AtomicReference<String>()
        val originalUrl = "https://popup-test.invalid/"
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var container: FrameLayout
        lateinit var source: WebView
        lateinit var handler: BrowserPopupHandler
        try {
            scenario.onActivity { activity ->
                container = FrameLayout(activity)
                source = WebView(activity)
                source.settings.setSupportMultipleWindows(true)
                source.settings.javaScriptCanOpenWindowsAutomatically = false
                handler = BrowserPopupHandler(
                    container = container,
                    canOpenTab = { true },
                    openTab = { address ->
                        openedAddress.set(address)
                        popupOpened.countDown()
                    },
                )
                source.webChromeClient = handler
                source.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        if (url == originalUrl) pageLoaded.countDown()
                    }
                }
                container.addView(source, FrameLayout.LayoutParams(800, 400))
                activity.addContentView(container, ViewGroup.LayoutParams(800, 400))
                source.loadDataWithBaseURL(
                    originalUrl,
                    "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
                        "<body style='margin:0'><a href='http://example.com/' target='_blank' " +
                        "style='display:block;width:100vw;height:100vh'>Open</a></body></html>",
                    "text/html", "UTF-8", null,
                )
            }
            assertTrue("Test page did not load", pageLoaded.await(10, TimeUnit.SECONDS))
            val location = IntArray(2)
            var historyBefore = -1
            var urlBefore: String? = null
            scenario.onActivity {
                historyBefore = source.copyBackForwardList().size
                urlBefore = source.url
                source.getLocationOnScreen(location)
            }
            instrumentation.waitForIdleSync()
            val downTime = SystemClock.uptimeMillis()
            val x = (location[0] + 400).toFloat()
            val y = (location[1] + 200).toFloat()
            instrumentation.sendPointerSync(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0))
            instrumentation.sendPointerSync(MotionEvent.obtain(downTime, downTime + 80, MotionEvent.ACTION_UP, x, y, 0))

            assertTrue("Tapped pop-up did not open a new tab", popupOpened.await(10, TimeUnit.SECONDS))
            assertEquals("https://example.com/", openedAddress.get())
            scenario.onActivity {
                assertEquals(urlBefore, source.url)
                assertEquals(historyBefore, source.copyBackForwardList().size)
            }
        } finally {
            scenario.onActivity {
                handler.dispose()
                container.removeView(source)
                source.destroy()
                (container.parent as? ViewGroup)?.removeView(container)
            }
            scenario.close()
        }
    }

    private fun bodyText(scenario: ActivityScenario<MainActivity>, webView: WebView): String {
        val text = AtomicReference<String>()
        val read = CountDownLatch(1)
        scenario.onActivity {
            webView.evaluateJavascript("document.body.innerText") { value ->
                text.set(value)
                read.countDown()
            }
        }
        assertTrue("Could not read test page", read.await(10, TimeUnit.SECONDS))
        return text.get()
    }
}
