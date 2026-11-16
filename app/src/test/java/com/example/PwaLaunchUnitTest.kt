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
        assertFalse(BrowserTabWebViewManager.isSameUrl("https://duckduckgo.com/?q=kaspa", "https://duckduckgo.com/?q=blockdag"))
        assertFalse(BrowserTabWebViewManager.isSameUrl("https://kaspa.org/#overview", "https://kaspa.org/#roadmap"))
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
        assertTrue(html.contains("Play Offline Arcade"))
        assertTrue(html.contains("BlockDAG Runner"))
        assertTrue(html.contains("Kaspa 2048"))
    }

    @Test
    fun testOfflineArcadeHtmlGeneration() {
        val html = KaspaWebViewConfigurator.generateOfflineArcadeHtml()
        assertTrue(html.contains("Kaspa Offline Arcade"))
        assertTrue(html.contains("BlockDAG Runner"))
        assertTrue(html.contains("Kaspa 2048"))
        assertTrue(html.contains("runnerCanvas"))
        assertTrue(html.contains("grid2048"))
    }

    @Test
    fun testLocalHostExceptionDetection() {
        assertTrue(com.example.network.KaspaPrivacyEngine.isLocalOrPrivateHost("localhost"))
        assertTrue(com.example.network.KaspaPrivacyEngine.isLocalOrPrivateHost("127.0.0.1"))
        assertTrue(com.example.network.KaspaPrivacyEngine.isLocalOrPrivateHost("192.168.1.50"))
        assertTrue(com.example.network.KaspaPrivacyEngine.isLocalOrPrivateHost("10.0.0.2"))
        assertTrue(com.example.network.KaspaPrivacyEngine.isLocalOrPrivateHost("[::1]"))
        assertFalse(com.example.network.KaspaPrivacyEngine.isLocalOrPrivateHost("localhosting.com"))
        assertFalse(com.example.network.KaspaPrivacyEngine.isLocalOrPrivateHost("kaspa.org"))
    }

    @Test
    fun testSslWarningHtmlGeneration() {
        val html = KaspaWebViewConfigurator.generateSslErrorHtml("https://untrusted.kaspa.org", "The certificate authority is untrusted.")
        assertTrue(html.contains("Security Warning: Untrusted Certificate"))
        assertTrue(html.contains("The certificate authority is untrusted."))
        assertTrue(html.contains("Return to Safety"))
    }
}
