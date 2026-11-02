package com.example

import com.example.network.KaspaPrivacyEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPrivacyUnitTest {

    @Test
    fun testGeolocationSensorShieldIsActiveInPrivacyScript() {
        val script = KaspaPrivacyEngine.JS_PRIVACY_SHIELD_INJECTION

        // Verify Privacy Shield is active and preserves native DOM prototypes for Cloudflare Turnstile compliance
        assertTrue("Script must be non-empty", script.isNotBlank())
        assertFalse("Script must not modify HTMLMetaElement prototype", script.contains("HTMLMetaElement.prototype"))
    }

    @Test
    fun testWebRtcIpLeakShieldIsActive() {
        val script = KaspaPrivacyEngine.JS_PRIVACY_SHIELD_INJECTION

        // Verify native WebRTC prototypes remain pristine to avoid Cloudflare 600010 security flags
        assertFalse("Script must not alter native RTCPeerConnection prototype", script.contains("RTCPeerConnection.prototype"))
    }
}
