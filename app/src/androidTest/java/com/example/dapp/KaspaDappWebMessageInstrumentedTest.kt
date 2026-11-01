package com.example.dapp

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class KaspaDappWebMessageInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun createView(): WebView {
        lateinit var view: WebView
        instrumentation.runOnMainSync {
            view = WebView(context).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val html = when (request.url.toString()) {
                            "https://allowed.example/" -> "<html><body><iframe id='child' src='/frame'></iframe></body></html>"
                            "https://allowed.example/frame" -> "<html><body>frame</body></html>"
                            "https://blocked.example/" -> "<html><body>blocked</body></html>"
                            else -> return null
                        }
                        return WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(html.toByteArray()))
                    }
                }
            }
        }
        return view
    }

    private fun register(view: WebView, callback: (String, Boolean, String) -> Unit) {
        instrumentation.runOnMainSync {
            WebViewCompat.addWebMessageListener(view, "kaspaDappBridge", setOf("https://allowed.example")) {
                    _, message: WebMessageCompat, origin, mainFrame, reply ->
                callback(message.data.orEmpty(), mainFrame, origin.toString())
                reply.postMessage("ack")
            }
        }
    }

    private fun load(view: WebView, url: String) {
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val html = when (request.url.toString()) {
                        "https://allowed.example/" -> "<html><body><iframe id='child' src='/frame'></iframe></body></html>"
                        "https://allowed.example/frame" -> "<html><body>frame</body></html>"
                        "https://blocked.example/" -> "<html><body>blocked</body></html>"
                        else -> return null
                    }
                    return WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(html.toByteArray()))
                }
                override fun onPageFinished(view: WebView, finished: String) {
                    if (finished == url) done.countDown()
                }
            }
            view.loadUrl(url)
        }
        assertTrue("Page load timed out: $url", done.await(15, TimeUnit.SECONDS))
    }

    private fun js(view: WebView, source: String): String {
        val value = AtomicReference<String>()
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync { view.evaluateJavascript(source) { value.set(it); done.countDown() } }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return value.get().orEmpty()
    }

    private fun requireFeatures() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
    }

    @Test fun allowedHttpsOriginHasNativePostMessageRoundTrip() {
        requireFeatures()
        val view = createView()
        val done = CountDownLatch(1)
        val received = AtomicReference<String>()
        val sourceOrigin = AtomicReference<String>()
        register(view) { data, _, origin -> received.set(data); sourceOrigin.set(origin); done.countDown() }
        load(view, "https://allowed.example/")
        js(view, "window.kaspaDappBridge.postMessage(JSON.stringify({id:'test',method:'requestAccounts'}))")
        assertTrue("Native message callback not reached", done.await(10, TimeUnit.SECONDS))
        assertTrue(received.get().contains("requestAccounts"))
        assertEquals("https://allowed.example", sourceOrigin.get())
        instrumentation.runOnMainSync { view.destroy() }
    }

    @Test fun otherHttpsOriginCannotAccessBridge() {
        requireFeatures()
        val view = createView()
        register(view) { _, _, _ -> }
        load(view, "https://blocked.example/")
        assertEquals("\"undefined\"", js(view, "typeof window.kaspaDappBridge"))
        instrumentation.runOnMainSync { view.destroy() }
    }

    @Test fun sameOriginIframeIsMarkedAsNonMainFrame() {
        requireFeatures()
        val view = createView()
        val done = CountDownLatch(1)
        val mainFrame = AtomicReference<Boolean>()
        register(view) { _, isMain, _ -> mainFrame.set(isMain); done.countDown() }
        load(view, "https://allowed.example/")
        js(view, "document.getElementById('child').contentWindow.kaspaDappBridge.postMessage('iframe')")
        assertTrue("Iframe message callback not reached", done.await(10, TimeUnit.SECONDS))
        assertEquals(false, mainFrame.get())
        instrumentation.runOnMainSync { view.destroy() }
    }
}
