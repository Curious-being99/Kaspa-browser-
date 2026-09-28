package com.example

import com.example.network.PwaShortcutHelper
import com.example.util.BrowserTabWebViewManager
import com.example.util.KaspaWebViewConfigurator
import org.junit.Assert.*
import org.junit.Test

class PwaLaunchUnitTest {

    @Test
    fun testPwaUrlNormalizationAndSameUrl() {
        assertTrue(BrowserTabWebViewManager.isSameUrl("https://kaspa.stream", "https://kaspa.stream/"))
        assertTrue(BrowserTabWebViewManager.isSameUrl("https://kaspa.stream/explorer", "https://kaspa.stream/explorer/"))
        assertTrue(BrowserTabWebViewManager.isSameUrl("http://kaspa.stream", "https://kaspa.stream"))
        assertFalse(BrowserTabWebViewManager.isSameUrl("https://kaspa.stream/explorer", "https://kaspa.stream/settings"))
    }

    @Test
    fun testResolveAbsoluteUrl() {
        val base = "https://kaspa.stream/app/index.html"
        val resolvedRel = PwaShortcutHelper.resolveAbsoluteUrl(base, "manifest.json")
        assertEquals("https://kaspa.stream/app/manifest.json", resolvedRel)

        val resolvedRootRel = PwaShortcutHelper.resolveAbsoluteUrl(base, "/manifest.json")
        assertEquals("https://kaspa.stream/manifest.json", resolvedRootRel)

        val resolvedAbs = PwaShortcutHelper.resolveAbsoluteUrl(base, "https://cdn.kaspa.stream/icon.png")
        assertEquals("https://cdn.kaspa.stream/icon.png", resolvedAbs)
    }

    @Test
    fun testErrorHtmlGeneration() {
        val html = KaspaWebViewConfigurator.generateErrorHtml("https://kaspa.stream", "ERR_INTERNET_DISCONNECTED")
        assertTrue(html.contains("No internet connection"))
        assertTrue(html.contains("https://kaspa.stream"))
        assertTrue(html.contains("Retry Connection"))
    }

    @Test
    fun testSslWarningHtmlGeneration() {
        val html = KaspaWebViewConfigurator.generateSslErrorHtml("https://untrusted.kaspa.org", "The certificate authority is untrusted.")
        assertTrue(html.contains("Security Warning: Untrusted Certificate"))
        assertTrue(html.contains("The certificate authority is untrusted."))
        assertTrue(html.contains("Return to Safety"))
    }
}
