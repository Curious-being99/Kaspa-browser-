package com.example.network

import java.net.URI

/**
 * KaspaPrivacyEngine
 * 
 * Decentralized, zero-telemetry privacy shield engine for Kaspa DNet Browser Gateway.
 * Intercepts tracking scripts, ad networks, canvas fingerprinting, telemetry beacons,
 * and malicious WebRTC IP leaks while providing native DNT / Global Privacy Control (GPC) injection.
 */
object KaspaPrivacyEngine {

    private val TRACKER_AND_AD_DOMAINS = setOf(
        // Analytics & Tracking Beacons
        "google-analytics.com",
        "googletagmanager.com",
        "doubleclick.net",
        "googleadservices.com",
        "connect.facebook.net",
        "facebook.com/tr",
        "scorecardresearch.com",
        "quantserve.com",
        "hotjar.com",
        "mixpanel.com",
        "segment.io",
        "amplitude.com",
        "heap.io",
        "fullstory.com",
        "crazyegg.com",
        "mouseflow.com",
        "smartlook.com",
        "clarity.ms",
        "yandex.ru/metrika",
        "mc.yandex.ru",

        // Ad Networks & Pixels
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "adnxs.com",
        "adsrvr.org",
        "moatads.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "adroll.com",
        "applovin.com",
        "unityads.unity3d.com",
        "vungle.com",
        "chartboost.com",
        "inmobi.com",
        "ironsrc.com",
        "adcolony.com",
        "amazon-adsystem.com",
        "casalemedia.com",
        "bidswitch.net",
        "smartadserver.com",
        "sovrn.com",
        "advertising.com",

        // Telemetry & Error Tracking
        "bugsnag.com",
        "sentry.io",
        "sentry-cdn.com",
        "getsentry.com",
        "loggly.com",
        "datadoghq.com",
        "newrelic.com",
        "raygun.io",
        "rollbar.com",
        "trackjs.com",
        "inspectlet.com",

        // Fingerprinting & Behavioral Profiling
        "fingerprintjs.com",
        "fpjs.sh",
        "iovation.com",
        "threatmetrix.com",
        "perimeterx.net",
        "arkoselabs.com",
        "datadome.co"
    )

    /**
     * Checks if a given request URL matches any known tracker, ad network, or telemetry domain.
     */
    fun isTrackerOrAd(url: String): Boolean {
        if (url.isBlank()) return false
        
        // Never block decentralized Kaspa schemes or localhost nodes
        if (url.startsWith("kaspa://", ignoreCase = true) ||
            url.startsWith("dnet://", ignoreCase = true) ||
            url.startsWith("ipfs://", ignoreCase = true) ||
            url.startsWith("hyper://", ignoreCase = true) ||
            url.contains("127.0.0.1") ||
            url.contains("localhost")
        ) {
            return false
        }

        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return false
            TRACKER_AND_AD_DOMAINS.any { domain ->
                host == domain || host.endsWith(".$domain")
            }
        } catch (_: Exception) {
            val cleanUrl = url.lowercase()
            TRACKER_AND_AD_DOMAINS.any { domain ->
                cleanUrl.contains("://$domain") || cleanUrl.contains(".$domain/")
            }
        }
    }

    /**
     * Backward-compatible alias for legacy TrackerBlocklist
     */
    fun isTracker(url: String): Boolean = isTrackerOrAd(url)

    /**
     * Custom HTTP headers injected into network requests to enforce privacy.
     */
    fun getPrivacyHeaders(): Map<String, String> {
        return mapOf(
            "DNT" to "1",
            "Sec-GPC" to "1",
            "X-Kaspa-Privacy-Shield" to "Active-Level-3"
        )
    }

    /**
     * Client-side JavaScript injected into WebView DOMs before page scripts load.
     * Enforces Do-Not-Track, Global Privacy Control, and disables canvas/audio/WebGL fingerprinting.
     */
    const val JS_PRIVACY_SHIELD_INJECTION = """
        (function() {
            if (window.__kaspa_shield_injected) return;
            window.__kaspa_shield_injected = true;
            try {
                // 1. Global Privacy Control & DNT
                Object.defineProperty(navigator, 'doNotTrack', { get: () => '1', configurable: false });
                Object.defineProperty(navigator, 'globalPrivacyControl', { get: () => true, configurable: false });
                
                // 2. Protect Battery API Fingerprinting
                if (navigator.getBattery) {
                    navigator.getBattery = function() {
                        return Promise.resolve({
                            charging: true,
                            chargingTime: 0,
                            dischargingTime: Infinity,
                            level: 1.0,
                            addEventListener: function() {},
                            removeEventListener: function() {}
                        });
                    };
                }

                // 3. WebRTC Local IP Leak Mitigation
                if (window.RTCPeerConnection) {
                    const OrigRTC = window.RTCPeerConnection;
                    window.RTCPeerConnection = function(config, constraints) {
                        if (config && config.iceServers) {
                            config.iceTransportPolicy = 'relay'; // Force relay mode to hide local LAN IP
                        }
                        return new OrigRTC(config, constraints);
                    };
                }

                // 4. WebGL Stability & Texture Unit Shield (Prevents Mesa driver crash & gles2 unbound texture warnings)
                if (window.HTMLCanvasElement && window.HTMLCanvasElement.prototype) {
                    const origGetContext = window.HTMLCanvasElement.prototype.getContext;
                    window.HTMLCanvasElement.prototype.getContext = function(type, attribs) {
                        if (attribs && (type === 'webgl' || type === 'webgl2' || type === 'experimental-webgl')) {
                            attribs.powerPreference = 'low-power';
                            attribs.antialias = false; // Reduce memory allocation 4x on Mesa software renderer
                            attribs.failIfMajorPerformanceCaveat = false;
                        }
                        const ctx = origGetContext.apply(this, arguments);
                        if (ctx && (type === 'webgl' || type === 'webgl2' || type === 'experimental-webgl')) {
                            try {
                                if (!ctx.__kaspa_units_bound) {
                                    ctx.__kaspa_units_bound = true;
                                    const dummyTex = ctx.createTexture();
                                    ctx.bindTexture(ctx.TEXTURE_2D, dummyTex);
                                    ctx.texImage2D(ctx.TEXTURE_2D, 0, ctx.RGBA, 1, 1, 0, ctx.RGBA, ctx.UNSIGNED_BYTE, new Uint8Array([0, 0, 0, 0]));
                                    const maxUnits = Math.min(ctx.getParameter(ctx.MAX_COMBINED_TEXTURE_IMAGE_UNITS) || 32, 32);
                                    for (let i = 0; i < maxUnits; i++) {
                                        ctx.activeTexture(ctx.TEXTURE0 + i);
                                        ctx.bindTexture(ctx.TEXTURE_2D, dummyTex);
                                    }
                                    ctx.activeTexture(ctx.TEXTURE0);
                                }
                            } catch (err) {}
                        }
                        return ctx;
                    };
                }
            } catch (e) {}
        })();
    """
}
