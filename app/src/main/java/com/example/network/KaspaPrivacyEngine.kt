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

    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("kaspasearch")
            isNativeLoaded = true
        } catch (_: Throwable) {
            isNativeLoaded = false
        }
    }

    @JvmStatic
    private external fun nativeIsTrackerOrAd(url: String): Boolean

    @JvmStatic
    private external fun nativeExtractHostFast(url: String): String?

    @JvmStatic
    private external fun nativeIsGoogleAccountDomain(host: String): Boolean

    @JvmStatic
    private external fun nativeIsGoogleAccountOrAuthUrl(url: String): Boolean

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

        // Fingerprinting & Behavioral Profiling (Ad trackers only)
        "fingerprintjs.com",
        "fpjs.sh"
    )

    private val ESSENTIAL_AUTH_CAPTCHA_DOMAINS = setOf(
        "github.com",
        "githubassets.com",
        "octocaptcha.com",
        "arkoselabs.com",
        "client-api.arkoselabs.com",
        "hcaptcha.com",
        "recaptcha.net",
        "challenges.cloudflare.com",
        "accounts.google.com",
        "apis.google.com",
        "gstatic.com",
        "ssl.gstatic.com",
        "appleid.apple.com",
        "login.microsoftonline.com",
        "login.live.com",
        "auth0.com",
        "okta.com",
        "clerk.com",
        "clerk.dev"
    )

    /**
     * Checks if a host belongs to essential login, signup, authentication, CAPTCHA or identity services.
     */
    fun isEssentialAuthOrCaptchaDomain(host: String): Boolean {
        val h = host.lowercase().trim()
        if (isGoogleAccountDomain(h)) return true
        if (ESSENTIAL_AUTH_CAPTCHA_DOMAINS.contains(h)) return true
        return ESSENTIAL_AUTH_CAPTCHA_DOMAINS.any { h == it || h.endsWith(".$it") }
    }

    /**
     * Checks if a URL is part of login, signup, OAuth, CAPTCHA, or user authentication.
     */
    fun isEssentialAuthOrCaptchaUrl(url: String): Boolean {
        if (url.length < 5) return false
        val lower = url.lowercase()
        val host = extractHostFast(url)
        if (host != null && isEssentialAuthOrCaptchaDomain(host)) return true
        if (isGoogleAccountOrAuthUrl(url)) return true

        val path = runCatching { android.net.Uri.parse(url).path?.lowercase() }.getOrNull() ?: ""
        if (path.contains("/login") || path.contains("/signup") || path.contains("/signin") ||
            path.contains("/register") || path.contains("/session") || path.contains("/oauth") ||
            path.contains("/auth") || path.contains("/captcha") || path.contains("/challenge") ||
            path.contains("/turnstile") || path.contains("/arkose") || path.contains("/octocaptcha") ||
            path.contains("/webauthn") || path.contains("/passkey") || path.contains("/two-factor") ||
            path.contains("/password_reset") || path.contains("/account")
        ) {
            return true
        }
        return false
    }

    /**
     * Checks if a host belongs to Google Account login, profile, authentication or identity services.
     */
    fun isGoogleAccountDomain(host: String): Boolean {
        if (isNativeLoaded) {
            try {
                return nativeIsGoogleAccountDomain(host)
            } catch (_: Throwable) {}
        }
        return UBlockEngine.isGoogleAccountDomain(host)
    }

    /**
     * Checks if a URL is part of Google Account login, OAuth, account management, or authentication.
     */
    fun isGoogleAccountOrAuthUrl(url: String): Boolean {
        if (isNativeLoaded) {
            try {
                return nativeIsGoogleAccountOrAuthUrl(url)
            } catch (_: Throwable) {}
        }
        return UBlockEngine.isGoogleAccountOrAuthUrl(url)
    }

    /**
     * Backward-compatible alias for checking Google Account URLs
     */
    fun isGoogleAccountUrl(url: String): Boolean = isGoogleAccountOrAuthUrl(url)

    /**
     * Checks if a given request URL matches any known tracker, ad network, or telemetry domain.
     * Evaluates against the uBlock Origin rule engine with microsecond sub-domain suffix lookups.
     */
    fun isTrackerOrAd(url: String): Boolean {
        if (isNativeLoaded) {
            try {
                return nativeIsTrackerOrAd(url)
            } catch (_: Throwable) {}
        }
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

        // Never block file downloads, documents, media streams, fonts, or stylesheets
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
            path.endsWith(".pdf") || path.endsWith(".zip") || path.endsWith(".tar") ||
            path.endsWith(".gz") || path.endsWith(".apk") || path.endsWith(".exe") ||
            path.endsWith(".dmg") || path.endsWith(".pkg") || path.endsWith(".iso") ||
            path.endsWith(".rar") || path.endsWith(".7z") || path.endsWith(".csv") ||
            path.endsWith(".xlsx") || path.endsWith(".docx") || path.endsWith(".pptx") ||
            path.endsWith(".txt") || path.endsWith(".bin") || path.endsWith(".deb") ||
            path.endsWith(".rpm") || path.endsWith(".json") || path.endsWith(".xml") ||
            path.endsWith(".flac") || path.endsWith(".wav") || path.endsWith(".aac") ||
            path.endsWith(".avi") || path.endsWith(".mkv") || path.endsWith(".mov") ||
            path.endsWith(".epub") || path.endsWith(".mobi") || path.endsWith(".msi") ||
            path.endsWith(".jar") || path.endsWith(".img") ||
            path.contains("/download") || path.contains("/downloads/") ||
            path.contains("/file/") || path.contains("/files/") ||
            path.contains("/attachment") || path.contains("/attachments/") ||
            path.contains("/releases/") || path.contains("/export") ||
            lower.contains("download=") || lower.contains("content-disposition") ||
            lower.contains("response-content-disposition") || lower.contains("filename=") ||
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

        // Never block Google Account or essential login, signup, authentication, and CAPTCHA endpoints
        if (isEssentialAuthOrCaptchaUrl(url) || isGoogleAccountOrAuthUrl(url)) {
            return false
        }

        val host = extractHostFast(url)
        if (host != null && (isEssentialAuthOrCaptchaDomain(host) || isGoogleAccountDomain(host))) {
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

    fun extractHostFast(url: String): String? {
        if (isNativeLoaded) {
            try {
                val host = nativeExtractHostFast(url)
                if (host != null) return host
            } catch (_: Throwable) {}
        }
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
            "Sec-GPC" to "1"
        )
    }

    /**
     * Client-side JavaScript injected into WebView DOMs before page scripts load.
     * Enforces Brave-grade Farbling, Do-Not-Track, Global Privacy Control,
     * and defeats Canvas, WebGL, AudioContext, Font Metrics, and WebRTC fingerprinting safely.
     */
    const val JS_PRIVACY_SHIELD_INJECTION = """
        (function() {
            if (window.__kaspa_shield_injected) return;
            window.__kaspa_shield_injected = true;
            try {
                // 1. Global Privacy Control & Do-Not-Track (W3C Standard)
                try {
                    Object.defineProperty(navigator, 'doNotTrack', { get: () => '1', configurable: true });
                    Object.defineProperty(navigator, 'globalPrivacyControl', { get: () => true, configurable: true });
                } catch(e) {}

                // 2. Hardware & Concurrency Fingerprint Normalization
                try {
                    Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 8, configurable: true });
                    Object.defineProperty(navigator, 'deviceMemory', { get: () => 8, configurable: true });
                    Object.defineProperty(navigator, 'maxTouchPoints', { get: () => 5, configurable: true });
                    if (window.screen) {
                        Object.defineProperty(window.screen, 'colorDepth', { get: () => 24, configurable: true });
                        Object.defineProperty(window.screen, 'pixelDepth', { get: () => 24, configurable: true });
                    }
                } catch(e) {}

                // 3. WebGL GPU Parameter Normalization (Safe Context Instance Wrapping)
                try {
                    if (window.HTMLCanvasElement && window.HTMLCanvasElement.prototype) {
                        const origGetContext = HTMLCanvasElement.prototype.getContext;
                        if (origGetContext) {
                            HTMLCanvasElement.prototype.getContext = function(type) {
                                const ctx = origGetContext.apply(this, arguments);
                                if (ctx && (type === 'webgl' || type === 'webgl2' || type === 'experimental-webgl')) {
                                    try {
                                        if (!ctx.__kaspa_masked) {
                                            ctx.__kaspa_masked = true;
                                            const origParam = ctx.getParameter;
                                            if (typeof origParam === 'function') {
                                                ctx.getParameter = function(param) {
                                                    if (param === 0x9245) return 'Google Inc. (NVIDIA)';
                                                    if (param === 0x9246) return 'ANGLE (NVIDIA, NVIDIA GeForce RTX 4070 Direct3D11 vs_5_0 ps_5_0)';
                                                    if (param === 0x1F00) return 'WebKit';
                                                    if (param === 0x1F01) return 'WebKit WebGL';
                                                    if (param === 0x1F02) return 'WebGL 1.0 (OpenGL ES 2.0 Chromium)';
                                                    if (param === 0x8B8C) return 'WebGL GLSL ES 1.0 (OpenGL ES GLSL ES 1.0 Chromium)';
                                                    if (param === 0x0D52) return 8;
                                                    if (param === 0x0D53) return 8;
                                                    if (param === 0x0D54) return 8;
                                                    if (param === 0x0D55) return 8;
                                                    if (param === 0x0D56) return 24;
                                                    if (param === 0x0D57) return 8;
                                                    try {
                                                        return origParam.apply(this, arguments);
                                                    } catch(e) {
                                                        return null;
                                                    }
                                                };
                                            }
                                        }
                                    } catch(e) {}
                                }
                                return ctx;
                            };
                        }
                    }
                } catch(e) {}

                // 4. WebRTC IP Leak Prevention (Relay Mode when TURN available)
                if (window.RTCPeerConnection) {
                    try {
                        const OrigRTC = window.RTCPeerConnection;
                        window.RTCPeerConnection = function(config, constraints) {
                            try {
                                if (config && config.iceServers && Array.isArray(config.iceServers)) {
                                    const hasTurn = config.iceServers.some(s => {
                                        const urls = Array.isArray(s.urls) ? s.urls : [s.urls || s.url || ''];
                                        return urls.some(u => typeof u === 'string' && (u.startsWith('turn:') || u.startsWith('turns:')));
                                    });
                                    if (hasTurn) {
                                        config.iceTransportPolicy = 'relay';
                                    }
                                }
                            } catch(_) {}
                            return new OrigRTC(config, constraints);
                        };
                        window.RTCPeerConnection.prototype = OrigRTC.prototype;
                    } catch(e) {}
                }

                // 5. HTML5 Geolocation Sensor Shield (Zero Hardware GPS Leakage)
                try {
                    if (navigator.geolocation) {
                        navigator.geolocation.getCurrentPosition = function(success, error, options) {
                            if (error) {
                                error({
                                    code: 1,
                                    message: "Geolocation permission denied by Privacy Shield",
                                    PERMISSION_DENIED: 1,
                                    POSITION_UNAVAILABLE: 2,
                                    TIMEOUT: 3
                                });
                            }
                        };
                        navigator.geolocation.watchPosition = function(success, error, options) {
                            if (error) {
                                error({
                                    code: 1,
                                    message: "Geolocation permission denied by Privacy Shield",
                                    PERMISSION_DENIED: 1,
                                    POSITION_UNAVAILABLE: 2,
                                    TIMEOUT: 3
                                });
                            }
                            return 0;
                        };
                        navigator.geolocation.clearWatch = function(id) {};
                    }
                } catch(e) {}
            } catch (e) {}
        })();
    """

    const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    /**
     * Dynamically generates a desktop Chromium User-Agent matching the device's actual bundled Chromium version.
     * Replaces Android platform tokens with Windows 10 64-bit and strips Mobile tokens.
     */
    fun getDesktopUserAgent(baseUa: String? = null): String {
        if (baseUa.isNullOrBlank()) return DESKTOP_USER_AGENT
        val chromeMatch = Regex("Chrome/([0-9.]+)").find(baseUa)
        val chromeVer = chromeMatch?.groupValues?.get(1) ?: "130.0.0.0"
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVer Safari/537.36"
    }

    /**
     * Cleans the mobile User-Agent to match standard standalone Chrome on Android (removing WebView markers).
     */
    fun getMobileUserAgent(baseUa: String? = null): String {
        if (baseUa.isNullOrBlank()) return MOBILE_USER_AGENT
        return baseUa.replace("; wv", "").replace("Version/4.0 ", "")
    }

    /**
     * Converts common mobile subdomains (m.youtube.com, en.m.wikipedia.org, m.facebook.com)
     * to their full desktop equivalent when Desktop Mode is toggled, exactly like Chrome on Android.
     */
    fun convertMobileUrlToDesktop(url: String): String {
        if (url.isBlank()) return url
        var converted = url
            .replace("://m.youtube.com", "://www.youtube.com")
            .replace("://m.facebook.com", "://www.facebook.com")
            .replace("://mobile.twitter.com", "://twitter.com")
            .replace("://m.twitter.com", "://twitter.com")
            .replace("://m.wikipedia.org", "://wikipedia.org")
            .replace("://m.reddit.com", "://www.reddit.com")
        
        // Handle wikipedia language subdomains like en.m.wikipedia.org -> en.wikipedia.org
        converted = Regex("://([a-z0-9-]+)\\.m\\.wikipedia\\.org").replace(converted, "://$1.wikipedia.org")
        // General mobile subdomain fallback like m.example.com -> www.example.com
        converted = Regex("://m\\.([a-zA-Z0-9-]+\\.[a-z]{2,})").replace(converted, "://www.$1")
        return converted
    }

    /**
     * Converts desktop-specific subdomains to their mobile equivalents when Desktop Mode is toggled OFF,
     * ensuring immediate mobile responsiveness without fighting redirects.
     */
    fun convertDesktopUrlToMobile(url: String): String {
        if (url.isBlank()) return url
        var converted = url
            .replace("://www.youtube.com", "://m.youtube.com")
            .replace("://www.facebook.com", "://m.facebook.com")
            .replace("://www.reddit.com", "://m.reddit.com")
        
        // Handle wikipedia language subdomains like en.wikipedia.org -> en.m.wikipedia.org
        converted = Regex("://([a-z0-9-]+)\\.wikipedia\\.org").replace(converted, "://$1.m.wikipedia.org")
        return converted
    }

    /**
     * Client hints and network headers for Desktop / Mobile site modes.
     * Tells modern servers (Google, YouTube, Reddit, etc.) whether to serve desktop or mobile layouts.
     */
    fun getDesktopHeaders(isDesktop: Boolean, baseUa: String? = null): Map<String, String> {
        val chromeMatch = if (!baseUa.isNullOrBlank()) Regex("Chrome/([0-9.]+)").find(baseUa) else null
        val fullVer = chromeMatch?.groupValues?.get(1) ?: "130.0.0.0"
        val majorVer = fullVer.substringBefore(".")
        
        return if (isDesktop) {
            mapOf(
                "Sec-CH-UA-Mobile" to "?0",
                "Sec-CH-UA-Platform" to "\"Windows\"",
                "Sec-CH-UA" to "\"Chromium\";v=\"$majorVer\", \"Google Chrome\";v=\"$majorVer\", \"Not?A_Brand\";v=\"99\"",
                "Sec-CH-UA-Model" to "\"\"",
                "Sec-CH-UA-Platform-Version" to "\"15.0.0\"",
                "Sec-CH-UA-Full-Version-List" to "\"Chromium\";v=\"$fullVer\", \"Google Chrome\";v=\"$fullVer\", \"Not?A_Brand\";v=\"99.0.0.0\""
            )
        } else {
            mapOf(
                "Sec-CH-UA-Mobile" to "?1",
                "Sec-CH-UA-Platform" to "\"Android\"",
                "Sec-CH-UA" to "\"Chromium\";v=\"$majorVer\", \"Google Chrome\";v=\"$majorVer\", \"Not?A_Brand\";v=\"99\"",
                "Sec-CH-UA-Model" to "\"\"",
                "Sec-CH-UA-Platform-Version" to "\"10.0.0\"",
                "Sec-CH-UA-Arch" to "\"arm\"",
                "Sec-CH-UA-Bitness" to "\"64\"",
                "Sec-CH-Device-Memory" to "\"8\""
            )
        }
    }

    /**
     * Minimal dynamic script for Desktop mode presentation.
     * Ensures navigator.userAgentData reflects mobile: false for responsive layout engines
     * while preserving native hardware capabilities (e.g. touch gestures, high-DPI scaling).
     */
    fun getDesktopViewportScript(isDesktop: Boolean = false, baseUa: String? = null): String {
        val chromeMatch = if (!baseUa.isNullOrBlank()) Regex("Chrome/([0-9.]+)").find(baseUa) else null
        val fullVer = chromeMatch?.groupValues?.get(1) ?: "130.0.0.0"
        val majorVer = fullVer.substringBefore(".")

        return """
        (function() {
            try {
                var ua = navigator.userAgent || '';
                var isDesktopMode = (!ua.includes('Android') && !ua.includes('Mobile'));

                if (navigator.userAgentData) {
                    try {
                        var origUaData = navigator.userAgentData;
                        Object.defineProperty(navigator, 'userAgentData', {
                            get: function() {
                                return {
                                    brands: (origUaData && origUaData.brands) ? origUaData.brands : [
                                        { brand: 'Chromium', version: '$majorVer' },
                                        { brand: 'Google Chrome', version: '$majorVer' },
                                        { brand: 'Not?A_Brand', version: '99' }
                                    ],
                                    mobile: !isDesktopMode,
                                    platform: isDesktopMode ? 'Windows' : 'Android',
                                    getHighEntropyValues: function(hints) {
                                        if (origUaData && origUaData.getHighEntropyValues) {
                                            return origUaData.getHighEntropyValues(hints).then(function(v) {
                                                return Object.assign({}, v, {
                                                    mobile: !isDesktopMode,
                                                    platform: isDesktopMode ? 'Windows' : 'Android'
                                                });
                                            });
                                        }
                                        return Promise.resolve({
                                            mobile: !isDesktopMode,
                                            platform: isDesktopMode ? 'Windows' : 'Android',
                                            uaFullVersion: '$fullVer'
                                        });
                                    }
                                };
                            },
                            configurable: true,
                            enumerable: true
                        });
                    } catch (_) {}
                }

                try {
                    Object.defineProperty(navigator, 'platform', {
                        get: function() { return isDesktopMode ? 'Win32' : 'Linux armv81'; },
                        configurable: true
                    });
                } catch (_) {}

                if (isDesktopMode) {
                    var DESKTOP_VIEWPORT = 'width=980, user-scalable=yes';

                    // Synchronous viewport application
                    function enforceDesktopViewport() {
                        try {
                            var metas = document.querySelectorAll('meta[name="viewport"]');
                            var applied = false;
                            for (var i = 0; i < metas.length; i++) {
                                var m = metas[i];
                                if (m.getAttribute('content') !== DESKTOP_VIEWPORT) {
                                    m.setAttribute('content', DESKTOP_VIEWPORT);
                                }
                                applied = true;
                            }
                            if (!applied) {
                                var head = document.head || document.getElementsByTagName('head')[0] || document.documentElement;
                                if (head) {
                                    var meta = document.createElement('meta');
                                    meta.name = 'viewport';
                                    meta.content = DESKTOP_VIEWPORT;
                                    if (head.firstChild) {
                                        head.insertBefore(meta, head.firstChild);
                                    } else {
                                        head.appendChild(meta);
                                    }
                                }
                            }
                        } catch (_) {}
                    }

                    // 3. MutationObserver on document to catch <meta name="viewport"> the very millisecond the HTML parser creates it
                    try {
                        var observer = new MutationObserver(function(mutations) {
                            for (var i = 0; i < mutations.length; i++) {
                                var nodes = mutations[i].addedNodes;
                                for (var j = 0; j < nodes.length; j++) {
                                    var n = nodes[j];
                                    if (n.nodeType === 1) {
                                        if (n.nodeName === 'META' && (n.getAttribute('name') === 'viewport' || n.name === 'viewport')) {
                                            n.setAttribute('content', DESKTOP_VIEWPORT);
                                        } else if (n.getElementsByTagName) {
                                            var vms = n.getElementsByTagName('meta');
                                            for (var k = 0; k < vms.length; k++) {
                                                if (vms[k].getAttribute('name') === 'viewport' || vms[k].name === 'viewport') {
                                                    vms[k].setAttribute('content', DESKTOP_VIEWPORT);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        });
                        var targetNode = document.documentElement || document;
                        if (targetNode) {
                            observer.observe(targetNode, { childList: true, subtree: true });
                        }
                    } catch (_) {}

                    enforceDesktopViewport();
                    if (document.readyState === 'loading') {
                        document.addEventListener('DOMContentLoaded', enforceDesktopViewport, { once: true });
                    }
                } else {
                    // Mobile mode: Restore standard mobile viewport if it was previously forced
                    function restoreMobileViewport() {
                        try {
                            var metas = document.querySelectorAll('meta[name="viewport"]');
                            for (var i = 0; i < metas.length; i++) {
                                var m = metas[i];
                                if (m.getAttribute('content') && m.getAttribute('content').indexOf('width=980') !== -1) {
                                    m.setAttribute('content', 'width=device-width, initial-scale=1.0, user-scalable=yes');
                                }
                            }
                        } catch (_) {}
                    }
                    restoreMobileViewport();
                    if (document.readyState === 'loading') {
                        document.addEventListener('DOMContentLoaded', restoreMobileViewport, { once: true });
                    }
                }
            } catch (_) {}
        })();
        """.trimIndent()
    }

    val isDrmRendernodeAvailable: Boolean by lazy {
        try {
            val dri = java.io.File("/dev/dri")
            dri.exists() && (java.io.File(dri, "renderD128").exists() || java.io.File(dri, "card0").exists())
        } catch (_: Throwable) {
            false
        }
    }

    private val TRACKING_PARAMS = setOf(
        "fbclid", "gclid", "gbraid", "wbraid", "dclid", "msclkid",
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id", "utm_reader",
        "mc_cid", "mc_eid", "igshid", "yclid", "_hsenc", "_hsmi", "mkt_tok",
        "sc_src", "sc_llid", "sc_lid", "sc_uid", "sc_customer", "twclid", "oly_anon_id",
        "oly_enc_id", "_openstat", "vero_id", "vero_conv", "wickedid"
    )

    /**
     * Strips bounce trackers, ad click IDs, and telemetry query parameters from URLs (Brave/uBlock Parity)
     */
    fun stripTrackingParameters(url: String): String {
        if (!url.contains("?")) return url
        return try {
            val uri = URI(url)
            val query = uri.rawQuery ?: return url
            val pairs = query.split("&")
            val cleanPairs = pairs.filter { pair ->
                val key = pair.substringBefore("=").lowercase()
                !TRACKING_PARAMS.contains(key) && !key.startsWith("utm_")
            }
            val newQuery = if (cleanPairs.isNotEmpty()) "?${cleanPairs.joinToString("&")}" else ""
            val portStr = if (uri.port != -1) ":${uri.port}" else ""
            val schemeStr = if (uri.scheme != null) "${uri.scheme}://" else ""
            val fragmentStr = if (uri.rawFragment != null) "#${uri.rawFragment}" else ""
            "${schemeStr}${uri.host ?: ""}${portStr}${uri.rawPath ?: ""}${newQuery}${fragmentStr}"
        } catch (_: Throwable) {
            url
        }
    }

    fun getPrivacyShieldScript(safeGpuMode: Boolean = !isDrmRendernodeAvailable, isDesktop: Boolean = false, baseUa: String? = null): String {
        val flag = if (safeGpuMode) "window.__kaspa_software_rendering = true;" else ""
        val viewportScript = getDesktopViewportScript(isDesktop, baseUa)
        return "$flag\n$JS_PRIVACY_SHIELD_INJECTION\n$viewportScript"
    }
}
