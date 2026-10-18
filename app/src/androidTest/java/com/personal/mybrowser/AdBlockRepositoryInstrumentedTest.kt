package com.personal.mybrowser

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.mybrowser.browser.AdBlockRepository
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdBlockRepositoryInstrumentedTest {
    @Test fun bundledRulesBlockKnownAdResourcesButNotMainPages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val blocker = AdBlockRepository(context)
        assertTrue(blocker.hostCount > 3_000)
        assertTrue(blocker.blocks("https://googleads.g.doubleclick.net/pagead/ads", isMainFrame = false))
        assertFalse(blocker.blocks("https://googleads.g.doubleclick.net/", isMainFrame = true))
        blocker.enabled = false
        assertFalse(blocker.blocks("https://googleads.g.doubleclick.net/pagead/ads", isMainFrame = false))
    }

    @Test fun webViewInterceptsARequestToABlockedHost() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val blocker = AdBlockRepository(instrumentation.targetContext)
        val intercepted = CountDownLatch(1)
        lateinit var webView: WebView
        instrumentation.runOnMainSync {
            webView = WebView(instrumentation.targetContext).apply {
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        if (!blocker.blocks(request.url.toString(), request.isForMainFrame)) return null
                        intercepted.countDown()
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }
                }
                loadDataWithBaseURL(
                    "https://example.com/",
                    "<html><img src=\"https://googleads.g.doubleclick.net/mybrowser-test.png\"></html>",
                    "text/html", "UTF-8", null,
                )
            }
        }
        try {
            assertTrue("WebView did not request the blocked resource", intercepted.await(10, TimeUnit.SECONDS))
            assertTrue(blocker.blockedCount > 0)
        } finally {
            instrumentation.runOnMainSync { webView.destroy() }
        }
    }
}
