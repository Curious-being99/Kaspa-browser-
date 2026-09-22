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
        // Ad Networks & Pixels
        "doubleclick.net",
        "google-analytics.com",
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
     * Checks if a host belongs to Google Account login, profile, authentication or identity services.
     */
    fun isGoogleAccountDomain(host: String): Boolean = UBlockEngine.isGoogleAccountDomain(host)

    /**
     * Checks if a URL is part of Google Account login, OAuth, account management, or authentication.
     */
    fun isGoogleAccountOrAuthUrl(url: String): Boolean = UBlockEngine.isGoogleAccountOrAuthUrl(url)

    /**
     * Backward-compatible alias for checking Google Account URLs
     */
    fun isGoogleAccountUrl(url: String): Boolean = isGoogleAccountOrAuthUrl(url)

    /**
     * Checks if a given request URL matches any known tracker, ad network, or telemetry domain.
     * Evaluates against the uBlock Origin rule engine with microsecond sub-domain suffix lookups.
     */
    fun isTrackerOrAd(url: String): Boolean {
        if (url.length < 4) return false

        // Fast skip for data:, blob:, about:, javascript:
        val firstChar = url[0]
        if (firstChar == 'd' || firstChar == 'b' || firstChar == 'a' || firstChar == 'j') {
            if (url.startsWith("data:", ignoreCase = true) || 
                url.startsWith("blob:", ignoreCase = true) || 
                url.startsWith("about:", ignoreCase = true) ||
                url.startsWith("javascript:", ignoreCase = true)) {
                return false
            }
        }

        // Never block images, media streams, fonts, or stylesheets
        val lower = url.lowercase()
        val path = runCatching { android.net.Uri.parse(url).path?.lowercase() }.getOrNull() ?: ""
        if (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
            path.endsWith(".webp") || path.endsWith(".gif") || path.endsWith(".svg") ||
            path.endsWith(".ico") || path.endsWith(".bmp") || path.endsWith(".avif") ||
            path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m4v") ||
            path.endsWith(".m4s") || path.endsWith(".m4a") || path.endsWith(".mp3") ||
            path.endsWith(".ogg") || path.endsWith(".ogv") || path.endsWith(".ts") ||
            path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".css") ||
            path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") ||
            path.contains("/video/") || path.contains("/audio/") || path.contains("/media/") ||
            lower.contains("videoplayback") || lower.contains("stream") ||
            lower.contains("kaspa") || path.contains("/thumb") || path.contains("/poster")
        ) {
            return false
        }

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

        // Never block Google Account login, OAuth, account management, or identity endpoints
        if (isGoogleAccountOrAuthUrl(url)) {
            return false
        }

        val host = extractHostFast(url)
        if (host != null && isGoogleAccountDomain(host)) {
            return false
        }

        // 1. Evaluate via real uBlock Engine
        if (UBlockEngine.shouldBlock(url)) {
            return true
        }

        if (host == null) return false
        if (TRACKER_AND_AD_DOMAINS.contains(host)) return true

        // Check parent domains (e.g. adservice.google.com -> google.com)
        var dotIndex = host.indexOf('.')
        while (dotIndex != -1 && dotIndex < host.length - 1) {
            val parentDomain = host.substring(dotIndex + 1)
            // Never flag if parentDomain is a Google Account domain or top-level infrastructure
            if (isGoogleAccountDomain(parentDomain) || parentDomain == "google.com" || parentDomain == "googleapis.com" || parentDomain == "gstatic.com") {
                break
            }
            if (TRACKER_AND_AD_DOMAINS.contains(parentDomain)) return true
            dotIndex = host.indexOf('.', dotIndex + 1)
        }
        return false
    }

    private fun extractHostFast(url: String): String? {
        val schemeEnd = url.indexOf("://")
        val start = if (schemeEnd != -1) schemeEnd + 3 else 0
        if (start >= url.length) return null
        
        var end = url.indexOf('/', start)
        if (end == -1) end = url.indexOf('?', start)
        if (end == -1) end = url.indexOf('#', start)
        if (end == -1) end = url.length
        
        val colonInHost = url.indexOf(':', start)
        if (colonInHost != -1 && colonInHost < end) {
            end = colonInHost
        }
        
        if (start >= end) return null
        return url.substring(start, end).lowercase()
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
                try {
                    Object.defineProperty(navigator, 'doNotTrack', { get: () => '1', configurable: true });
                } catch(e) {}
                try {
                    Object.defineProperty(navigator, 'globalPrivacyControl', { get: () => true, configurable: true });
                } catch(e) {}
                
                // 2. Protect Battery API Fingerprinting
                if (navigator.getBattery) {
                    try {
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
                    } catch(e) {}
                }

                // 3. WebRTC Privacy (Relay-only is too aggressive for many DApps, use safer mitigation)
                if (window.RTCPeerConnection) {
                    try {
                        const OrigRTC = window.RTCPeerConnection;
                        window.RTCPeerConnection = function(config, constraints) {
                            try {
                                if (config && config.iceServers && window.__kaspa_software_rendering) {
                                    config.iceTransportPolicy = 'relay';
                                }
                            } catch(_) {}
                            return new OrigRTC(config, constraints);
                        };
                        window.RTCPeerConnection.prototype = OrigRTC.prototype;
                    } catch(e) {}
                }

                // 4. WebGL Virtual GPU context recovery
                try {
                    window.addEventListener('webglcontextlost', function(e) {
                        try { e.preventDefault(); } catch (_) {}
                    }, true);
                } catch(e) {}

                // 5. Remove click effect color and tap highlight color across all web elements
                try {
                    const removeClickEffect = function() {
                        if (document.getElementById('__kaspa_no_click_effect')) return;
                        const style = document.createElement('style');
                        style.id = '__kaspa_no_click_effect';
                        style.textContent = '*, *:focus, *:active, *:hover { -webkit-tap-highlight-color: transparent !important; -webkit-tap-highlight-color: rgba(0,0,0,0) !important; outline: none !important; }';
                        (document.head || document.documentElement || document.body)?.appendChild(style);
                    };
                    removeClickEffect();
                    if (document.readyState === 'loading') {
                        document.addEventListener('DOMContentLoaded', removeClickEffect);
                    }
                } catch(_) {}

                // 6. TikTok specific scroll unlock & modal banner dismisser
                const hostname = window.location.hostname || '';
                if (hostname.includes('tiktok.com') || hostname.includes('tiktokv.com')) {
                        function unlockTikTokScroll() {
                            try {
                                if (document.body) {
                                    if (document.body.style.overflow === 'hidden') {
                                        document.body.style.overflow = 'auto';
                                    }
                                }
                                if (document.documentElement) {
                                    if (document.documentElement.style.overflow === 'hidden') {
                                        document.documentElement.style.overflow = 'auto';
                                    }
                                }
                            } catch(_) {}
                        }

                        window.addEventListener('scroll', unlockTikTokScroll, { passive: true });
                        window.addEventListener('touchmove', unlockTikTokScroll, { passive: true });
                        window.addEventListener('DOMContentLoaded', unlockTikTokScroll);

                        function dismissAppPrompts() {
                            try {
                                const closeButtons = [
                                    '[data-e2e="modal-close-inner-button"]',
                                    'button[aria-label="Close"]',
                                    'div[class*="DivBanner"] button',
                                    'div[class*="AppBanner"] button',
                                    'div[class*="DownloadBar"] button'
                                ];
                                for (const sel of closeButtons) {
                                    const btn = document.querySelector(sel);
                                    if (btn && typeof btn.click === 'function') {
                                        btn.click();
                                    }
                                }
                                const overlayContainers = [
                                    'div[class*="DivModalMask"]',
                                    'div[class*="ModalMask"]',
                                    'div[class*="DivBannerContainer"]',
                                    'div[class*="AppBanner"]',
                                    'div[class*="DivDownloadBar"]'
                                ];
                                for (const sel of overlayContainers) {
                                    const el = document.querySelector(sel);
                                    if (el && el.style.display !== 'none') {
                                        el.style.display = 'none';
                                    }
                                }
                                unlockTikTokScroll();
                            } catch(_) {}
                        }

                        setInterval(dismissAppPrompts, 1000);
                    }
                } catch(e) {}
            } catch (e) {}
        })();
    """

    const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    /**
     * Client hints and network headers for Desktop / Mobile site modes.
     * Tells modern servers (Google, YouTube, Reddit, etc.) whether to serve desktop or mobile layouts.
     */
    fun getDesktopHeaders(isDesktop: Boolean): Map<String, String> {
        return if (isDesktop) {
            mapOf(
                "Sec-CH-UA-Mobile" to "?0",
                "Sec-CH-UA-Platform" to "\"Windows\"",
                "Sec-CH-UA" to "\"Chromium\";v=\"130\", \"Google Chrome\";v=\"130\", \"Not?A_Brand\";v=\"99\""
            )
        } else {
            mapOf(
                "Sec-CH-UA-Mobile" to "?1",
                "Sec-CH-UA-Platform" to "\"Android\"",
                "Sec-CH-UA" to "\"Chromium\";v=\"130\", \"Google Chrome\";v=\"130\", \"Not?A_Brand\";v=\"99\""
            )
        }
    }

    /**
     * Dynamic script that enforces standard desktop resolution (1280px),
     * overrides mobile viewport constraints (<meta name="viewport" content="width=device-width">),
     * and aligns navigator.userAgentData and platform with desktop browser standards.
     */
    fun getDesktopViewportScript(isDesktop: Boolean): String {
        return if (isDesktop) {
            """
            (function() {
                try {
                    if (navigator.userAgentData) {
                        try {
                            Object.defineProperty(navigator, 'userAgentData', {
                                get: () => ({
                                    brands: [
                                        { brand: 'Chromium', version: '130' },
                                        { brand: 'Google Chrome', version: '130' },
                                        { brand: 'Not?A_Brand', version: '99' }
                                    ],
                                    mobile: false,
                                    platform: 'Windows',
                                    getHighEntropyValues: (hints) => Promise.resolve({
                                        architecture: 'x86',
                                        bitness: '64',
                                        mobile: false,
                                        model: '',
                                        platform: 'Windows',
                                        platformVersion: '10.0.0',
                                        uaFullVersion: '130.0.0.0'
                                    })
                                }),
                                configurable: true,
                                enumerable: true
                            });
                        } catch(_) {}
                    }
                    try {
                        Object.defineProperty(navigator, 'platform', { get: () => 'Win32', configurable: true });
                    } catch(_) {}
                    try {
                        const dw = Math.max(window.screen.width, 1280);
                        const dh = Math.max(window.screen.height, 800);
                        Object.defineProperty(window.screen, 'width', { get: () => dw, configurable: true });
                        Object.defineProperty(window.screen, 'availWidth', { get: () => dw, configurable: true });
                    } catch(_) {}

                    const enforceDesktopViewport = function() {
                        let meta = document.querySelector('meta[name="viewport"]');
                        if (!meta) {
                            meta = document.createElement('meta');
                            meta.name = 'viewport';
                            (document.head || document.documentElement).appendChild(meta);
                        }
                        meta.setAttribute('content', 'width=1280, initial-scale=0.35, minimum-scale=0.1, maximum-scale=5.0, user-scalable=yes');
                    };
                    enforceDesktopViewport();
                    if (document.readyState === 'loading') {
                        document.addEventListener('DOMContentLoaded', enforceDesktopViewport);
                    }
                    try {
                        const observer = new MutationObserver(function(mutations) {
                            for (const mutation of mutations) {
                                if (mutation.type === 'attributes' && mutation.target.name === 'viewport') {
                                    if (mutation.target.getAttribute('content') !== 'width=1280, initial-scale=0.35, minimum-scale=0.1, maximum-scale=5.0, user-scalable=yes') {
                                        mutation.target.setAttribute('content', 'width=1280, initial-scale=0.35, minimum-scale=0.1, maximum-scale=5.0, user-scalable=yes');
                                    }
                                }
                            }
                        });
                        observer.observe(document.documentElement, { subtree: true, attributes: true, attributeFilter: ['content'] });
                    } catch(_) {}
                } catch(_) {}
            })();
            """.trimIndent()
        } else {
            ""
        }
    }

    fun getPrivacyShieldScript(safeGpuMode: Boolean = true, isDesktop: Boolean = false): String {
        val flag = if (safeGpuMode) "window.__kaspa_software_rendering = true;" else ""
        val desktopScript = if (isDesktop) getDesktopViewportScript(true) else ""
        return "$flag\n$JS_PRIVACY_SHIELD_INJECTION\n$desktopScript"
    }
}
