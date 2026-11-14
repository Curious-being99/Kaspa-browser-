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
        "x.com",
        "twitter.com",
        "twimg.com",
        "api.x.com",
        "api.twitter.com",
        "arkoselabs.com",
        "client-api.arkoselabs.com",
        "github.com",
        "githubassets.com",
        "octocaptcha.com",
        "hcaptcha.com",
        "recaptcha.net",
        "challenges.cloudflare.com",
        "cloudflare.com",
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
        "clerk.dev",
        "newassets.hcaptcha.com",
        "imgs.hcaptcha.com",
        "geetest.com",
        "friendlycaptcha.com",
        "datadome.co",
        "captcha-delivery.com",
        "px-cdn.net",
        "funcaptcha.com",
        "js.stripe.com",
        "m.stripe.network"
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
            path.contains("/auth") || path.contains("/captcha") || path.contains("/recaptcha") ||
            path.contains("/hcaptcha") || path.contains("/geetest") || path.contains("/friendly-captcha") ||
            path.contains("/challenge") || path.contains("/turnstile") || path.contains("/arkose") ||
            path.contains("/octocaptcha") || path.contains("/cdn-cgi/") || path.contains("/webauthn") ||
            path.contains("/passkey") || path.contains("/two-factor") || path.contains("/password_reset") ||
            path.contains("/account") || lower.contains("__cf_chl_")
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
     * Checks if a URL is a Google authentication, sign-in, or 2FA URL.
     */
    fun isGoogleAuthUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        return lower.contains("accounts.google.com") ||
               lower.contains("google.com/accounts") ||
               lower.contains("signin.google.com") ||
               lower.contains("oauth2") ||
               lower.contains("ServiceLogin") ||
               lower.contains("IdentifierVerification") ||
               lower.contains("v2/identifier")
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

        // Always exempt essential auth, OAuth, and CAPTCHA/challenge endpoints BEFORE consulting native rules
        if (isEssentialAuthOrCaptchaUrl(url) || isGoogleAccountOrAuthUrl(url)) {
            return false
        }
        val fastHost = extractHostFast(url)
        if (fastHost != null && (isEssentialAuthOrCaptchaDomain(fastHost) || isGoogleAccountDomain(fastHost))) {
            return false
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

        val host = fastHost ?: extractHostFast(url)
        if (host != null && (isEssentialAuthOrCaptchaDomain(host) || isGoogleAccountDomain(host))) {
            return false
        }

        if (isNativeLoaded) {
            try {
                return nativeIsTrackerOrAd(url)
            } catch (_: Throwable) {}
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
     * Strictly preserves native Navigator/DOM property descriptors, WebGL, WebRTC, touch,
     * hardware, and geolocation APIs on direct connections so Cloudflare Turnstile,
     * Managed Challenges, and anti-bot checks never flag synthetic DOM/prototype tampering.
     */
    const val JS_PRIVACY_SHIELD_INJECTION = """
        (function() {
            try {
                var h = (window.location && window.location.hostname) ? window.location.hostname.toLowerCase() : '';
                var p = (window.location && window.location.pathname) ? window.location.pathname.toLowerCase() : '';
                var q = (window.location && window.location.search) ? window.location.search.toLowerCase() : '';
                if (h.indexOf('google.com') !== -1 || h.indexOf('gstatic.com') !== -1 ||
                    h.indexOf('googleapis.com') !== -1 || h.indexOf('recaptcha.net') !== -1 ||
                    h.indexOf('hcaptcha.com') !== -1 || h.indexOf('cloudflare.com') !== -1 ||
                    h.indexOf('turnstile') !== -1 || h.indexOf('x.com') !== -1 ||
                    h.indexOf('twitter.com') !== -1 || h.indexOf('twimg.com') !== -1 ||
                    h.indexOf('arkoselabs.com') !== -1 || h.indexOf('octocaptcha.com') !== -1 ||
                    h.indexOf('geetest.com') !== -1 || h.indexOf('friendlycaptcha.com') !== -1 ||
                    h.indexOf('funcaptcha.com') !== -1 || h.indexOf('captcha-delivery.com') !== -1 ||
                    h.indexOf('stripe.com') !== -1 || h.indexOf('paypal.com') !== -1 ||
                    h.indexOf('datadome') !== -1 || h.indexOf('perimeterx') !== -1 ||
                    h.indexOf('auth0.com') !== -1 || h.indexOf('okta.com') !== -1 ||
                    h.indexOf('appleid.apple.com') !== -1 || h.indexOf('login.microsoftonline.com') !== -1 ||
                    h.indexOf('github.com') !== -1 ||
                    p.indexOf('/cdn-cgi/') !== -1 || p.indexOf('/recaptcha') !== -1 ||
                    p.indexOf('/hcaptcha') !== -1 || p.indexOf('/turnstile') !== -1 ||
                    q.indexOf('__cf_chl_') !== -1) {
                    return;
                }
                if ((document.title && document.title.indexOf('Just a moment') !== -1) ||
                    document.getElementById('challenge-running') ||
                    document.getElementById('challenge-form') ||
                    document.querySelector('script[src*="/cdn-cgi/challenge-platform"], div.cf-turnstile')) {
                    return;
                }
            } catch(e) {}

            // Strictly gate WebRTC & Geolocation isolation to active Privacy Relay / Proxy sessions
            // so direct connections never exhibit synthetic navigator/window tampering to anti-bot checks.
            if (window.__kaspa_relay_shield === true) {
                if (window.__kaspa_shield_injected) return;
                window.__kaspa_shield_injected = true;
                try {
                    if (window.RTCPeerConnection) {
                        var OrigRTC = window.RTCPeerConnection;
                        window.RTCPeerConnection = function(config, constraints) {
                            if (config && config.iceServers) {
                                config.iceServers = config.iceServers.filter(function(s) {
                                    var urls = Array.isArray(s.urls) ? s.urls : [s.urls];
                                    return !urls.some(function(u) { return typeof u === 'string' && u.indexOf('stun:') !== -1; });
                                });
                            }
                            return new OrigRTC(config, constraints);
                        };
                        window.RTCPeerConnection.prototype = OrigRTC.prototype;
                    }
                    if (navigator.geolocation) {
                        var denyGeo = function(successCb, errorCb) {
                            if (typeof errorCb === 'function') {
                                errorCb({ code: 1, PERMISSION_DENIED: 1, message: 'Geolocation permission denied by Privacy Shield' });
                            }
                        };
                        navigator.geolocation.getCurrentPosition = denyGeo;
                        navigator.geolocation.watchPosition = function(s, e) { denyGeo(s, e); return 0; };
                    }
                } catch (e) {}
            }
        })();
    """

    const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    fun getDesktopUserAgent(baseUa: String? = null): String {
        val chromeVersion = baseUa
            ?.let { Regex("Chrome/([0-9.]+)").find(it)?.groupValues?.get(1) }
            ?: Regex("Chrome/([0-9.]+)").find(DESKTOP_USER_AGENT)?.groupValues?.get(1)
            ?: "130.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Safari/537.36"
    }

    /**
     * Cleans the mobile User-Agent to match standard standalone Chrome on Android (removing WebView markers).
     */
    fun getMobileUserAgent(baseUa: String? = null): String {
        if (baseUa.isNullOrBlank() || !baseUa.contains("Android", ignoreCase = true)) {
            val chromeVersion = baseUa
                ?.let { Regex("Chrome/([0-9.]+)").find(it)?.groupValues?.get(1) }
                ?: return MOBILE_USER_AGENT
            return "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Mobile Safari/537.36"
        }
        return baseUa
            .replace(Regex(";\\s*wv\\b"), "")
            .replace(Regex("\\bVersion/\\d+\\.\\d+\\s*"), "")
    }

    @Volatile
    private var defaultMobileMetadata: androidx.webkit.UserAgentMetadata? = null

    private val appliedUaSettingsMode = java.util.Collections.synchronizedMap(
        java.util.WeakHashMap<android.webkit.WebSettings, Pair<Boolean, String>>()
    )

    /**
     * Synchronizes WebSettings.userAgentString, viewport settings, and Chromium's native C++
     * Client-Hint UserAgentMetadata (Sec-CH-UA-Platform, Sec-CH-UA-Mobile, Sec-CH-UA-Model,
     * Sec-CH-UA-Arch, Sec-CH-UA-Bitness, and Sec-CH-UA brands) so HTTP headers and
     * navigator.userAgentData match navigator.userAgent seamlessly across all requests & iframes.
     */
    fun applyUserAgentAndMetadata(
        settings: android.webkit.WebSettings,
        isDesktop: Boolean,
        baseUa: String? = null
    ) {
        val resolvedBaseUa = baseUa?.takeIf { it.isNotBlank() }
            ?: settings.userAgentString?.takeIf { it.contains("Chrome/") }
        val targetUa = if (isDesktop) {
            getDesktopUserAgent(resolvedBaseUa)
        } else {
            getMobileUserAgent(resolvedBaseUa)
        }
        if (settings.userAgentString != targetUa) {
            settings.userAgentString = targetUa
        }
        if (settings.useWideViewPort != isDesktop) {
            settings.useWideViewPort = isDesktop
        }
        if (settings.loadWithOverviewMode != isDesktop) {
            settings.loadWithOverviewMode = isDesktop
        }

        val desiredState = Pair(isDesktop, targetUa)
        if (appliedUaSettingsMode[settings] == desiredState) {
            return
        }

        try {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.USER_AGENT_METADATA)) {
                val current = androidx.webkit.WebSettingsCompat.getUserAgentMetadata(settings)
                if (defaultMobileMetadata == null && current.platform.equals("Android", ignoreCase = true)) {
                    defaultMobileMetadata = current
                }
                val baseMeta = defaultMobileMetadata ?: current
                val cleanedBrands = baseMeta.brandVersionList.map { bv ->
                    if (bv.brand.contains("Android WebView", ignoreCase = true)) {
                        androidx.webkit.UserAgentMetadata.BrandVersion.Builder()
                            .setBrand("Google Chrome")
                            .setMajorVersion(bv.majorVersion)
                            .setFullVersion(bv.fullVersion)
                            .build()
                    } else {
                        bv
                    }
                }
                val builder = androidx.webkit.UserAgentMetadata.Builder(baseMeta)
                if (cleanedBrands.isNotEmpty()) {
                    builder.setBrandVersionList(cleanedBrands)
                }
                if (isDesktop) {
                    builder
                        .setPlatform("Linux")
                        .setPlatformVersion("")
                        .setModel("")
                        .setMobile(false)
                        .setArchitecture("x86")
                        .setBitness(64)
                        .setWow64(false)
                } else {
                    val baseIsAndroid = baseMeta.platform.equals("Android", ignoreCase = true)
                    val mobilePlatformVersion = (if (baseIsAndroid) baseMeta.platformVersion else null)
                        ?.takeIf { it.isNotBlank() }
                        ?: android.os.Build.VERSION.RELEASE.orEmpty()
                    val mobileModel = (if (baseIsAndroid) baseMeta.model else null)
                        ?.takeIf { it.isNotBlank() }
                        ?: android.os.Build.MODEL.orEmpty()
                    val mobileArch = if (baseIsAndroid) baseMeta.architecture else ""
                    val mobileBitness = if (baseIsAndroid) baseMeta.bitness else 0
                    builder
                        .setPlatform("Android")
                        .setPlatformVersion(mobilePlatformVersion)
                        .setModel(mobileModel)
                        .setMobile(true)
                        .setArchitecture(mobileArch)
                        .setBitness(mobileBitness)
                        .setWow64(false)
                }
                androidx.webkit.WebSettingsCompat.setUserAgentMetadata(settings, builder.build())
                appliedUaSettingsMode[settings] = desiredState
            }
        } catch (_: Throwable) {}
    }

    /**
     * Selects the real page URL when toggling desktop mode. WebView.url can be
     * "about:blank" during an in-flight navigation; because it is non-null, a
     * simple Elvis fallback would reload the blank document and lose the page.
     */
    fun resolveUrlForDesktopToggle(
        webViewUrl: String?,
        urlInput: String?,
        resourceUrl: String?
    ): String? {
        return sequenceOf(webViewUrl, urlInput, resourceUrl)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull { candidate ->
                !candidate.equals("about:blank", ignoreCase = true) &&
                    !candidate.startsWith("data:", ignoreCase = true)
            }
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
     * Returns no additional one-shot Client-Hint headers for loadUrl().
     * Passing static Sec-CH-UA* via WebView.loadUrl(url, headers) only applies to the initial
     * top-level GET while Chromium omits or sends native Android headers on redirects, XHR/fetch,
     * subresources, and CAPTCHA iframes — causing header-split bot/VPN detection.
     * Consistent desktop identification is handled uniformly via WebSettings.userAgentString.
     */
    fun getDesktopHeaders(@Suppress("UNUSED_PARAMETER") isDesktop: Boolean, @Suppress("UNUSED_PARAMETER") baseUa: String? = null): Map<String, String> {
        return emptyMap()
    }

    /**
     * Dynamic top-frame viewport layout helper for Desktop mode presentation.
     * Evaluates desktop mode from runtime navigator.userAgent so WebView reloads after toggling
     * Desktop Mode ON/OFF take effect cleanly, skips subframes and CAPTCHA/challenge pages
     * (by URL at document-start AND dynamically if a widget mounts later), and never tampers
     * with native HTMLMetaElement / navigator prototypes.
     */
    fun getDesktopViewportScript(isDesktop: Boolean = false, @Suppress("UNUSED_PARAMETER") baseUa: String? = null): String {
        return """
        (function() {
            try {
                // Never modify viewport or inject desktop layout CSS inside subframes/CAPTCHAs/verification iframes
                if (window.top !== window.self) return;

                var host = (window.location && window.location.hostname) ? window.location.hostname.toLowerCase() : '';
                var path = (window.location && window.location.pathname) ? window.location.pathname.toLowerCase() : '';
                var search = (window.location && window.location.search) ? window.location.search.toLowerCase() : '';
                if (host.indexOf('challenges.cloudflare.com') !== -1 ||
                    host.indexOf('hcaptcha.com') !== -1 ||
                    host.indexOf('recaptcha.net') !== -1 ||
                    host.indexOf('arkoselabs.com') !== -1 ||
                    host.indexOf('octocaptcha.com') !== -1 ||
                    host.indexOf('geetest.com') !== -1 ||
                    host.indexOf('friendlycaptcha.com') !== -1 ||
                    host.indexOf('captcha-delivery.com') !== -1 ||
                    host.indexOf('datadome.co') !== -1 ||
                    host.indexOf('accounts.google.com') !== -1 ||
                    path.indexOf('/cdn-cgi/') !== -1 ||
                    path.indexOf('/recaptcha') !== -1 ||
                    path.indexOf('/hcaptcha') !== -1 ||
                    path.indexOf('/turnstile') !== -1 ||
                    search.indexOf('__cf_chl_') !== -1) {
                    return;
                }

                function cleanupDesktopOverrides() {
                    try {
                        if (window.__kaspa_desktop_observer) {
                            window.__kaspa_desktop_observer.disconnect();
                            window.__kaspa_desktop_observer = null;
                        }
                        var metas = document.querySelectorAll('meta[name="viewport"]');
                        for (var i = 0; i < metas.length; i++) {
                            var content = metas[i].getAttribute('content') || '';
                            if (content.indexOf('width=980') !== -1) {
                                metas[i].setAttribute('content', 'width=device-width, initial-scale=1.0, user-scalable=yes');
                            }
                        }
                        var styleEl = document.getElementById('__kaspa_desktop_css');
                        if (styleEl && styleEl.parentNode) {
                            styleEl.parentNode.removeChild(styleEl);
                        }
                    } catch (_) {}
                }

                function hasChallengeContent() {
                    try {
                        if ((document.title && document.title.indexOf('Just a moment') !== -1) ||
                            document.getElementById('challenge-running') ||
                            document.getElementById('challenge-form') ||
                            document.querySelector('script[src*="/cdn-cgi/challenge-platform"], script[src*="challenges.cloudflare.com"], iframe[src*="challenges.cloudflare.com"], iframe[src*="recaptcha"], iframe[src*="hcaptcha.com"], div.cf-turnstile, div.g-recaptcha, div.h-captcha')) {
                            return true;
                        }
                    } catch (_) {}
                    return false;
                }

                if (hasChallengeContent()) {
                    cleanupDesktopOverrides();
                    return;
                }

                var ua = navigator.userAgent || '';
                var isDesktopMode = ua.length > 0 ? (ua.indexOf('Android') === -1 && ua.indexOf('Mobile') === -1) : $isDesktop;

                if (!isDesktopMode) {
                    cleanupDesktopOverrides();
                    return;
                }

                var DESKTOP_VIEWPORT = 'width=980, user-scalable=yes';
                var isSanitizingMeta = false;

                function sanitizeMeta(el) {
                    try {
                        if (isSanitizingMeta || !el || el.nodeType !== 1 || el.nodeName !== 'META') return;
                        var n = (el.getAttribute('name') || el.name || '').toLowerCase();
                        if (n === 'viewport' && el.getAttribute('content') !== DESKTOP_VIEWPORT) {
                            isSanitizingMeta = true;
                            try {
                                el.setAttribute('content', DESKTOP_VIEWPORT);
                            } finally {
                                isSanitizingMeta = false;
                            }
                        }
                    } catch (_) {
                        isSanitizingMeta = false;
                    }
                }

                function enforceDesktopLayout() {
                    try {
                        if (hasChallengeContent()) {
                            cleanupDesktopOverrides();
                            return;
                        }
                        var target = document.head;
                        if (!target) return;

                        var metas = document.querySelectorAll('meta[name="viewport"]');
                        if (metas.length === 0) {
                            var meta = document.createElement('meta');
                            meta.name = 'viewport';
                            meta.content = DESKTOP_VIEWPORT;
                            target.insertBefore(meta, target.firstChild);
                        } else {
                            for (var j = 0; j < metas.length; j++) {
                                sanitizeMeta(metas[j]);
                            }
                        }

                        if (!document.getElementById('__kaspa_desktop_css')) {
                            var style = document.createElement('style');
                            style.id = '__kaspa_desktop_css';
                            style.textContent = 'html, body { min-width: 980px !important; }';
                            target.insertBefore(style, target.firstChild);
                        }
                    } catch (_) {}
                }

                enforceDesktopLayout();

                try {
                    if (!window.__kaspa_desktop_observer) {
                        var rootTarget = document.documentElement || document;
                        var observer = new MutationObserver(function(mutations) {
                            if (isSanitizingMeta) return;
                            if (hasChallengeContent()) {
                                cleanupDesktopOverrides();
                                return;
                            }
                            for (var m = 0; m < mutations.length; m++) {
                                var mut = mutations[m];
                                if (mut.type === 'attributes' && mut.target && mut.target.nodeName === 'META') {
                                    sanitizeMeta(mut.target);
                                    continue;
                                }
                                var added = mut.addedNodes;
                                if (!added) continue;
                                for (var k = 0; k < added.length; k++) {
                                    var node = added[k];
                                    if (node.nodeType === 1) {
                                        if (node.nodeName === 'HEAD') {
                                            enforceDesktopLayout();
                                        } else if (node.nodeName === 'META') {
                                            sanitizeMeta(node);
                                        } else if (node.getElementsByTagName) {
                                            var childMetas = node.getElementsByTagName('meta');
                                            for (var c = 0; c < childMetas.length; c++) {
                                                sanitizeMeta(childMetas[c]);
                                            }
                                        }
                                    }
                                }
                            }
                        });
                        observer.observe(rootTarget, {
                            childList: true,
                            subtree: true,
                            attributes: true,
                            attributeFilter: ['content', 'name']
                        });
                        window.__kaspa_desktop_observer = observer;
                    }
                } catch (_) {}

                document.addEventListener('DOMContentLoaded', enforceDesktopLayout, { once: true });

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

    fun getPrivacyShieldScript(@Suppress("UNUSED_PARAMETER") safeGpuMode: Boolean = !isDrmRendernodeAvailable, isDesktop: Boolean = false, baseUa: String? = null): String {
        val viewportScript = getDesktopViewportScript(isDesktop, baseUa)
        return "$JS_PRIVACY_SHIELD_INJECTION\n$viewportScript"
    }
}
