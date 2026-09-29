package com.example

import com.example.network.KaspaSecurityGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KaspaSecurityGuardUnitTest {

    @Test
    fun testExemptionsNeverBlocked() {
        val googleAuth = KaspaSecurityGuard.inspectUrl("https://accounts.google.com/signin/v2/identifier")
        assertTrue("Google accounts must be safe", googleAuth.isSafe)

        val youtubeVideo = KaspaSecurityGuard.inspectUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertTrue("YouTube video streams must be safe", youtubeVideo.isSafe)

        val kaspaOrg = KaspaSecurityGuard.inspectUrl("https://kaspa.org/docs")
        assertTrue("Kaspa ecosystem must be safe", kaspaOrg.isSafe)

        val cloudflare = KaspaSecurityGuard.inspectUrl("https://cloudflare.com/dns")
        assertTrue("Cloudflare must be safe", cloudflare.isSafe)
    }

    @Test
    fun testCryptojackingDetection() {
        val coinHive = KaspaSecurityGuard.inspectUrl("https://coinhive.com/lib/coinhive.min.js")
        assertFalse("Coinhive must be flagged as threat", coinHive.isSafe)
        assertEquals("MALICIOUS_CRYPTOJACKING", coinHive.threatType)
        assertTrue(coinHive.riskScore >= 90)

        val cryptoLoot = KaspaSecurityGuard.inspectUrl("https://crypto-loot.com/loader.js")
        assertFalse("Crypto-loot must be flagged as threat", cryptoLoot.isSafe)
        assertEquals("MALICIOUS_CRYPTOJACKING", cryptoLoot.threatType)
    }

    @Test
    fun testHomographPhishingDetection() {
        // Cyrillic 'о' (\u043E) visual lookalike spoofing google.com
        val spoofedGoogle = "https://g\u043E\u043Egle.com/account/login"
        val verdict = KaspaSecurityGuard.inspectUrl(spoofedGoogle)
        assertFalse("Homograph spoof of google.com must be caught", verdict.isSafe)
        assertEquals("PHISHING_HOMOGRAPH", verdict.threatType)
        assertEquals("google.com", verdict.matchedTarget)
        assertTrue(verdict.riskScore >= 95)
    }

    @Test
    fun testTyposquattingDetection() {
        val typoGoogle = KaspaSecurityGuard.inspectUrl("https://goolge.com/search?q=test")
        assertFalse("Typosquat of google.com must be caught", typoGoogle.isSafe)
        assertEquals("TYPOSQUATTING", typoGoogle.threatType)
        assertEquals("google.com", typoGoogle.matchedTarget)

        val typoYoutube = KaspaSecurityGuard.inspectUrl("https://youutube.com/video")
        assertFalse("Typosquat of youtube.com must be caught", typoYoutube.isSafe)
        assertEquals("TYPOSQUATTING", typoYoutube.threatType)
        assertEquals("youtube.com", typoYoutube.matchedTarget)

        val typoKaspa = KaspaSecurityGuard.inspectUrl("https://kasspa.org/wallet")
        assertFalse("Typosquat of kaspa.org must be caught", typoKaspa.isSafe)
        assertEquals("TYPOSQUATTING", typoKaspa.threatType)
        assertEquals("kaspa.org", typoKaspa.matchedTarget)
    }
}
