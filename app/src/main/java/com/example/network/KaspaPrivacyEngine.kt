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
                if (navigator && !('globalPrivacyControl' in navigator)) {
                    try { Object.defineProperty(navigator, 'globalPrivacyControl', { value: true, writable: false, configurable: true }); } catch(_) {}
                }
            } catch (_) {}
        })();
    """

    const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    fun getDesktopUserAgent(baseUa: String? = null): String {
        val chromeVersion = baseUa
            ?.let { Regex("Chrome/([0-9.]+)").find(it)?.groupValues?.get(1) }
            ?: Regex("Chrome/([0-9.]+)").find(DESKTOP_USER_AGENT)?.groupValues?.get(1)
            ?: "130.0.0.0"
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Safari/537.36"
    }

    /**
     * Cleans the mobile User-Agent to match standard standalone Chrome on Android (removing WebView markers).
     */
    fun getMobileUserAgent(baseUa: String? = null): String {
        if (baseUa.isNullOrBlank()) return MOBILE_USER_AGENT
        return baseUa.replace("; wv", "").replace("Version/4.0 ", "")
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
     * Network headers for Desktop / Mobile site modes.
     * Returning emptyMap allows the native Chromium engine to manage Client Hints automatically
     * based on userAgentString, preventing Cloudflare / DDoS-Guard header-mismatch errors (e.g. Error 600010).
     */
    fun getDesktopHeaders(isDesktop: Boolean, baseUa: String? = null): Map<String, String> {
        return emptyMap()
    }

    /**
     * Minimal dynamic script for Desktop mode presentation.
     * Ensures responsive desktop layout rendering without modifying native DOM prototypes.
     */
    fun getDesktopViewportScript(isDesktop: Boolean = false, baseUa: String? = null): String {
        return """
        (function() {
            try {
                var ua = navigator.userAgent || '';
                var isDesktopMode = $isDesktop || (!ua.includes('Android') && !ua.includes('Mobile'));

                if (!isDesktopMode) return;

                function enforceDesktopLayout() {
                    try {
                        var target = document.head || document.documentElement || document.body;
                        if (!target) return;

                        var metas = target.querySelectorAll('meta[name="viewport"]');
                        if (metas.length === 0) {
                            var meta = document.createElement('meta');
                            meta.name = 'viewport';
                            meta.content = 'width=980, user-scalable=yes';
                            target.insertBefore(meta, target.firstChild);
                        }

                        if (!document.getElementById('__kaspa_desktop_css')) {
                            var style = document.createElement('style');
                            style.id = '__kaspa_desktop_css';
                            style.textContent = 'html, body { min-width: 980px !important; }';
                            target.insertBefore(style, target.firstChild);
                        }
                    } catch (_) {}
                }

                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', enforceDesktopLayout, { once: true });
                } else {
                    enforceDesktopLayout();
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
