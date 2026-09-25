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
                // 1. Global Privacy Control & Do-Not-Track
                try {
                    Object.defineProperty(navigator, 'doNotTrack', { get: () => '1', configurable: true });
                    Object.defineProperty(navigator, 'globalPrivacyControl', { get: () => true, configurable: true });
                } catch(e) {}

                // 2. Hardware & Concurrency Fingerprint Spoofing
                try {
                    Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 8, configurable: true });
                    Object.defineProperty(navigator, 'deviceMemory', { get: () => 8, configurable: true });
                    Object.defineProperty(navigator, 'maxTouchPoints', { get: () => 5, configurable: true });
                } catch(e) {}

                // 3. Canvas Fingerprint Noise Injection (Anti-Canvas-Tracking)
                try {
                    const originalToDataURL = HTMLCanvasElement.prototype.toDataURL;
                    const originalGetImageData = CanvasRenderingContext2D.prototype.getImageData;

                    HTMLCanvasElement.prototype.toDataURL = function(type, encoderOptions) {
                        try {
                            const ctx = this.getContext('2d');
                            if (ctx && this.width > 0 && this.height > 0) {
                                const imgData = ctx.getImageData(0, 0, Math.min(this.width, 10), Math.min(this.height, 10));
                                if (imgData && imgData.data && imgData.data.length > 0) {
                                    imgData.data[0] = imgData.data[0] ^ 1;
                                    ctx.putImageData(imgData, 0, 0);
                                }
                            }
                        } catch(_) {}
                        return originalToDataURL.apply(this, arguments);
                    };

                    if (HTMLCanvasElement.prototype.toBlob) {
                        const originalToBlob = HTMLCanvasElement.prototype.toBlob;
                        HTMLCanvasElement.prototype.toBlob = function(callback, type, quality) {
                            try {
                                const ctx = this.getContext('2d');
                                if (ctx && this.width > 0 && this.height > 0) {
                                    const imgData = ctx.getImageData(0, 0, Math.min(this.width, 10), Math.min(this.height, 10));
                                    if (imgData && imgData.data && imgData.data.length > 0) {
                                        imgData.data[0] = imgData.data[0] ^ 1;
                                        ctx.putImageData(imgData, 0, 0);
                                    }
                                }
                            } catch(_) {}
                            return originalToBlob.apply(this, arguments);
                        };
                    }

                    CanvasRenderingContext2D.prototype.getImageData = function(sx, sy, sw, sh) {
                        const res = originalGetImageData.apply(this, arguments);
                        try {
                            if (res && res.data && res.data.length > 4) {
                                res.data[0] = (res.data[0] + 1) % 256;
                            }
                        } catch(_) {}
                        return res;
                    };
                } catch(e) {}

                // 4. WebGL GPU Vendor & Renderer Masking & Rendernode Guard
                try {
                    const origGetContext = HTMLCanvasElement.prototype.getContext;
                    HTMLCanvasElement.prototype.getContext = function(type, attributes) {
                        if (type === 'webgl' || type === 'webgl2' || type === 'experimental-webgl') {
                            try {
                                const ctx = origGetContext.apply(this, arguments);
                                if (ctx) return ctx;
                            } catch (_) {
                                return null;
                            }
                        }
                        return origGetContext.apply(this, arguments);
                    };

                    const getParamOrig = WebGLRenderingContext.prototype.getParameter;
                    WebGLRenderingContext.prototype.getParameter = function(param) {
                        if (param === 0x9245) return 'ANGLE (Google, Vulkan 1.3, Direct3D11)';
                        if (param === 0x9246) return 'ANGLE (NVIDIA, NVIDIA GeForce RTX 3060 Direct3D11 vs_5_0 ps_5_0)';
                        return getParamOrig.apply(this, arguments);
                    };

                    if (window.WebGL2RenderingContext) {
                        const getParam2Orig = WebGL2RenderingContext.prototype.getParameter;
                        WebGL2RenderingContext.prototype.getParameter = function(param) {
                            if (param === 0x9245) return 'ANGLE (Google, Vulkan 1.3, Direct3D11)';
                            if (param === 0x9246) return 'ANGLE (NVIDIA, NVIDIA GeForce RTX 3060 Direct3D11 vs_5_0 ps_5_0)';
                            return getParam2Orig.apply(this, arguments);
                        };
                    }
                } catch(e) {}

                // 5. AudioContext WebAudio Anti-Fingerprinting Noise
                try {
                    if (window.AudioContext || window.webkitAudioContext) {
                        const origGetChannelData = AudioBuffer.prototype.getChannelData;
                        AudioBuffer.prototype.getChannelData = function() {
                            const channel = origGetChannelData.apply(this, arguments);
                            try {
                                for (let i = 0; i < Math.min(channel.length, 100); i += 10) {
                                    channel[i] += 0.0000001 * (Math.random() - 0.5);
                                }
                            } catch(_) {}
                            return channel;
                        };
                    }
                } catch(e) {}

                // 6. Protect Battery API Fingerprinting (W3C Battery Status API Masking)
                try {
                    const fakeBatteryManager = {
                        charging: true,
                        chargingTime: 0,
                        dischargingTime: Infinity,
                        level: 1.0,
                        onchargingchange: null,
                        onchargingtimechange: null,
                        ondischargingtimechange: null,
                        onlevelchange: null,
                        addEventListener: function() {},
                        removeEventListener: function() {},
                        dispatchEvent: function() { return false; }
                    };
                    const getBatteryFn = function() {
                        return Promise.resolve(fakeBatteryManager);
                    };
                    try {
                        Object.defineProperty(navigator, 'getBattery', {
                            get: () => getBatteryFn,
                            configurable: true,
                            enumerable: true
                        });
                    } catch (_) {
                        navigator.getBattery = getBatteryFn;
                    }
                    if (window.Navigator && Navigator.prototype) {
                        try {
                            Object.defineProperty(Navigator.prototype, 'getBattery', {
                                get: () => getBatteryFn,
                                configurable: true,
                                enumerable: true
                            });
                        } catch (_) {}
                    }
                } catch(e) {}

                // 7. WebRTC IP Leak Prevention (Relay Mode)
                if (window.RTCPeerConnection) {
                    try {
                        const OrigRTC = window.RTCPeerConnection;
                        window.RTCPeerConnection = function(config, constraints) {
                            try {
                                if (config && config.iceServers) {
                                    config.iceTransportPolicy = 'relay';
                                }
                            } catch(_) {}
                            return new OrigRTC(config, constraints);
                        };
                        window.RTCPeerConnection.prototype = OrigRTC.prototype;
                    } catch(e) {}
                }

                // 8. Remove Tap Highlight / Click Effects
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
                "Sec-CH-UA" to "\"Chromium\";v=\"$majorVer\", \"Google Chrome\";v=\"$majorVer\", \"Not?A_Brand\";v=\"99\""
            )
        }
    }

    /**
     * Minimal dynamic script for Desktop mode presentation.
     * Ensures navigator.userAgentData reflects mobile: false for responsive layout engines
     * while preserving native hardware capabilities (e.g. touch gestures, high-DPI scaling).
     */
    fun getDesktopViewportScript(isDesktop: Boolean, baseUa: String? = null): String {
        val chromeMatch = if (!baseUa.isNullOrBlank()) Regex("Chrome/([0-9.]+)").find(baseUa) else null
        val fullVer = chromeMatch?.groupValues?.get(1) ?: "130.0.0.0"
        val majorVer = fullVer.substringBefore(".")

        return """
        (function() {
            try {
                var ua = navigator.userAgent || '';
                var isDesktopMode = $isDesktop || (!ua.includes('Android') && !ua.includes('Mobile'));

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

                    // 1. Immediately patch HTMLMetaElement.prototype.setAttribute so that
                    // client-side frameworks (e.g. Next.js Head, React Helmet) cannot
                    // overwrite the viewport back to mobile width=device-width during hydration.
                    try {
                        var proto = HTMLMetaElement.prototype;
                        var origSetAttr = proto.setAttribute;
                        proto.setAttribute = function(name, value) {
                            if (name && name.toLowerCase() === 'content') {
                                var mName = this.getAttribute('name') || this.name;
                                if (mName && mName.toLowerCase() === 'viewport') {
                                    return origSetAttr.call(this, name, DESKTOP_VIEWPORT);
                                }
                            }
                            return origSetAttr.apply(this, arguments);
                        };
                    } catch (_) {}

                    // 2. Synchronous viewport application
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
                    try {
                        var meta = document.querySelector('meta[name="viewport"]');
                        if (meta && meta.getAttribute('content') && meta.getAttribute('content').indexOf('width=980') !== -1) {
                            meta.setAttribute('content', 'width=device-width, initial-scale=1.0, user-scalable=yes');
                        }
                    } catch (_) {}
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

    fun getPrivacyShieldScript(safeGpuMode: Boolean = !isDrmRendernodeAvailable, isDesktop: Boolean = false, baseUa: String? = null): String {
        val flag = if (safeGpuMode) "window.__kaspa_software_rendering = true;" else ""
        val viewportScript = getDesktopViewportScript(isDesktop, baseUa)
        return "$flag\n$JS_PRIVACY_SHIELD_INJECTION\n$viewportScript"
    }
}
