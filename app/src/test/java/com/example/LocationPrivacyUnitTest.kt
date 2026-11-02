package com.example

import com.example.network.KaspaPrivacyEngine
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPrivacyUnitTest {

    @Test
    fun testGeolocationSensorShieldIsActiveInPrivacyScript() {
        val script = KaspaPrivacyEngine.JS_PRIVACY_SHIELD_INJECTION

        // Verify HTML5 Geolocation GPS Isolation is injected
        assertTrue("Script must shield navigator.geolocation", script.contains("navigator.geolocation"))
        assertTrue("Script must stub getCurrentPosition", script.contains("navigator.geolocation.getCurrentPosition"))
        assertTrue("Script must stub watchPosition", script.contains("navigator.geolocation.watchPosition"))
        assertTrue("Script must return PERMISSION_DENIED", script.contains("PERMISSION_DENIED: 1"))
        assertTrue("Script must report Privacy Shield denial", script.contains("Geolocation permission denied by Privacy Shield"))
    }

    @Test
    fun testWebRtcIpLeakShieldIsActive() {
        val script = KaspaPrivacyEngine.JS_PRIVACY_SHIELD_INJECTION

        // Verify WebRTC local IP candidate leakage is neutralized
        assertTrue("Script must shield WebRTC", script.contains("RTCPeerConnection"))
    }
}
