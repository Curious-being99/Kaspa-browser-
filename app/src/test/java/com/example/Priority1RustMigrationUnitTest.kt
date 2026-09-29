package com.example

import com.example.network.CryptoUtils
import com.example.network.KaspaPrivacyEngine
import com.example.network.UBlockEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Priority1RustMigrationUnitTest {

    @Test
    fun testSha256Hashing() {
        val helloHash = CryptoUtils.sha256("hello")
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", helloHash)

        val rawHash = CryptoUtils.sha256Raw("hello".toByteArray(Charsets.UTF_8))
        assertEquals(32, rawHash.size)

        val bytesHash = CryptoUtils.sha256Bytes("hello".toByteArray(Charsets.UTF_8))
        assertEquals(helloHash, bytesHash)
    }

    @Test
    fun testHexConversion() {
        val hex = "2cf24dba"
        val bytes = CryptoUtils.hexToBytes(hex)
        assertEquals(4, bytes.size)
        assertEquals(0x2c.toByte(), bytes[0])
        assertEquals(0xf2.toByte(), bytes[1])
        assertEquals(0x4d.toByte(), bytes[2])
        assertEquals(0xba.toByte(), bytes[3])
    }

    @Test
    fun testGenerateCid() {
        val cid = CryptoUtils.generateCid("hello world")
        assertTrue(cid.startsWith("bafybei"))
        assertEquals(39, cid.length)
    }

    @Test
    fun testExtractHostFast() {
        val host1 = KaspaPrivacyEngine.extractHostFast("https://doubleclick.net/pagead/ads")
        assertEquals("doubleclick.net", host1)

        val host2 = KaspaPrivacyEngine.extractHostFast("http://localhost:8080/index.html")
        assertEquals("localhost", host2)

        val host3 = KaspaPrivacyEngine.extractHostFast("https://accounts.google.com/signin/v2")
        assertEquals("accounts.google.com", host3)
    }

    @Test
    fun testGoogleAccountExemptions() {
        assertTrue(KaspaPrivacyEngine.isGoogleAccountDomain("accounts.google.com"))
        assertTrue(KaspaPrivacyEngine.isGoogleAccountDomain("myaccount.google.com"))
        assertTrue(KaspaPrivacyEngine.isGoogleAccountDomain("oauth2.googleapis.com"))
        assertFalse(KaspaPrivacyEngine.isGoogleAccountDomain("doubleclick.net"))

        assertTrue(KaspaPrivacyEngine.isGoogleAccountOrAuthUrl("https://accounts.google.com/signin/oauth"))
        assertFalse(KaspaPrivacyEngine.isGoogleAccountOrAuthUrl("https://google-analytics.com/analytics.js"))
    }

    @Test
    fun testTrackerAndAdBlocking() {
        assertTrue(KaspaPrivacyEngine.isTrackerOrAd("https://doubleclick.net/ad.js"))
        assertTrue(KaspaPrivacyEngine.isTrackerOrAd("https://google-analytics.com/collect"))
        assertTrue(KaspaPrivacyEngine.isTracker("https://connect.facebook.net/en_US/fbevents.js"))

        // Google account and non-trackers must never be blocked
        assertFalse(KaspaPrivacyEngine.isTrackerOrAd("https://accounts.google.com/signin"))
        assertFalse(KaspaPrivacyEngine.isTrackerOrAd("https://kaspa.org/index.html"))

        // Media resources must never be blocked
        assertFalse(KaspaPrivacyEngine.isTrackerOrAd("https://example.com/banner.png"))
        assertFalse(KaspaPrivacyEngine.isTrackerOrAd("https://example.com/style.css"))
    }

    @Test
    fun testUBlockShouldBlock() {
        assertTrue(UBlockEngine.shouldBlock("https://googleadservices.com/pagead/conversion.js"))
        assertTrue(UBlockEngine.shouldBlock("https://pixel.facebook.com/tr/"))
        assertFalse(UBlockEngine.shouldBlock("https://accounts.google.com/ServiceLogin"))
        assertFalse(UBlockEngine.shouldBlock("https://cloudflare.com/script.js"))
    }
}
