package com.personal.mybrowser

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personal.mybrowser.network.PrivacyRelay
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyRelayInstrumentedTest {
    @Test fun websiteCannotCreateWebRtcPeerConnection() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        lateinit var page: WebView
        try {
            scenario.onActivity { activity ->
                page = WebView(activity)
                PrivacyRelay.protectWebView(page)
                page.settings.javaScriptEnabled = true
                page.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(
                            "typeof RTCPeerConnection + ':' + typeof webkitRTCPeerConnection",
                        ) { value ->
                            result.set(value)
                            completed.countDown()
                        }
                    }
                }
                activity.addContentView(page, android.view.ViewGroup.LayoutParams(1, 1))
                page.loadDataWithBaseURL("https://example.com/", "<html><body>Test</body></html>", "text/html", "UTF-8", null)
            }
            assertTrue("Page script did not run", completed.await(10, TimeUnit.SECONDS))
            assertEquals("\"undefined:undefined\"", result.get())
        } finally {
            scenario.onActivity {
                (page.parent as? android.view.ViewGroup)?.removeView(page)
                page.destroy()
            }
            scenario.close()
        }
    }
}
