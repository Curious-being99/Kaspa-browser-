package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.engine.BrowserEngineManager
import com.example.network.KaspaPrivacyEngine

object KaspaWebViewConfigurator {
    private val appliedWebAuthState = java.util.Collections.synchronizedMap(java.util.WeakHashMap<WebSettings, Boolean>())
    private val appliedWebAuthLevel = java.util.Collections.synchronizedMap(java.util.WeakHashMap<WebSettings, Int>())

    /**
     * Configures WebAuthn / FIDO2 Passkey support for a browser WebView using
     * WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER (never FOR_APP, which rejects arbitrary websites).
     * Memoized per WebSettings instance to prevent Compose recompositions from resetting
     * Chromium's WebAuthn handler while an interactive passkey ceremony is in flight.
     * Returns the runtime-applied support level or -1 if unsupported.
     */
    fun applyWebAuthenticationSupport(settings: WebSettings, enabled: Boolean): Int {
        return try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
                -1
            } else {
                val alreadyApplied = synchronized(appliedWebAuthState) {
                    appliedWebAuthState[settings] == enabled
                }
                if (alreadyApplied) {
                    return synchronized(appliedWebAuthLevel) { appliedWebAuthLevel[settings] }
                        ?: WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER
                }
                val targetLevel = if (enabled) {
                    WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER
                } else {
                    WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_NONE
                }
                WebSettingsCompat.setWebAuthenticationSupport(settings, targetLevel)
                val actualLevel = try {
                    WebSettingsCompat.getWebAuthenticationSupport(settings)
                } catch (_: Throwable) {
                    targetLevel
                }
                synchronized(appliedWebAuthState) {
                    appliedWebAuthState[settings] = enabled
                    appliedWebAuthLevel[settings] = actualLevel
                }
                BrowserEngineManager.recordPasskeySupport(enabled, actualLevel)
                actualLevel
            }
        } catch (t: Throwable) {
            Log.w(TAG, "WebAuthn support config notice: ${t.message}")
            -1
        }
    }

    /**
     * Applies Kazview's native Blink enhancements (Back-Forward Cache for instant
     * back/forward navigation and Privacy Sandbox Attribution Reporting suppression) safely
     * behind WebViewFeature checks.
     */
    fun applyKazviewSettings(settings: WebSettings) {
        var bfCache = false
        var attributionDisabled = false
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.BACK_FORWARD_CACHE)) {
                WebSettingsCompat.setBackForwardCacheEnabled(settings, true)
                bfCache = runCatching { WebSettingsCompat.getBackForwardCacheEnabled(settings) }.getOrDefault(true)
            }
        } catch (t: Throwable) {
            Log.d(TAG, "BFCache setup notice: ${t.message}")
        }
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ATTRIBUTION_REGISTRATION_BEHAVIOR)) {
                WebSettingsCompat.setAttributionRegistrationBehavior(
                    settings,
                    WebSettingsCompat.ATTRIBUTION_BEHAVIOR_DISABLED
                )
                attributionDisabled = true
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Attribution privacy setup notice: ${t.message}")
        }
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Requested-With suppression notice: ${t.message}")
        }
        BrowserEngineManager.recordWebSettingsApplied(
            bfCacheEnabled = bfCache,
            attributionDisabled = attributionDisabled
        )
    }

    /**
     * Dynamically ensures Google Sign-In, OAuth, and 2FA authentication URLs have proper cookies and UA settings.
     */
    fun checkAndConfigureForUrl(webView: WebView, url: String?) {
        if (url.isNullOrBlank()) return
        try {
            if (KaspaPrivacyEngine.isGoogleAuthUrl(url) || KaspaPrivacyEngine.isEssentialAuthOrCaptchaUrl(url)) {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(webView, true)
                cookieManager.flush()
            }
        } catch (_: Throwable) {}
    }


    private const val TAG = "KaspaWebViewConfig"

    /**
     * Synchronously enables and configures the Android WebView ServiceWorker subsystem.
     * Ensures ServiceWorker registration, cache API, fetch interception, and offline resources
     * work properly for PWAs and modern web applications.
     */
    fun initServiceWorkerSupport() {
        try {
            val swController = ServiceWorkerController.getInstance()
            val swSettings = swController.serviceWorkerWebSettings
            swSettings.allowContentAccess = false
            swSettings.allowFileAccess = false
            swSettings.blockNetworkLoads = false
            swSettings.cacheMode = WebSettings.LOAD_DEFAULT
            swController.setServiceWorkerClient(object : ServiceWorkerClient() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                    if (request.isForMainFrame) return null
                    val reqUrl = request.url?.toString()
                    if (!reqUrl.isNullOrBlank()) {
                        if (KaspaPrivacyEngine.isEssentialAuthOrCaptchaUrl(reqUrl)) {
                            return null
                        }
                        // Never block PWA offline stylesheets, scripts, or fonts
                        val path = runCatching { request.url?.path?.lowercase() }.getOrNull() ?: ""
                        val isAsset = path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".json") ||
                            path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") ||
                            path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".webp") || path.endsWith(".svg")
                        if (isAsset) {
                            return null
                        }

                        if (KaspaPrivacyEngine.isTrackerOrAd(reqUrl)) {
                            return WebResourceResponse(
                                "text/plain",
                                "UTF-8",
                                403,
                                "Blocked by Shield",
                                mapOf("Access-Control-Allow-Origin" to "*"),
                                java.io.ByteArrayInputStream(ByteArray(0))
                            )
                        }
                    }
                    return super.shouldInterceptRequest(request)
                }
            })
            Log.d("PWA", "[PWA] service worker: active")
        } catch (e: Exception) {
            Log.w(TAG, "ServiceWorkerController initialization notice: ${e.message}")
        }
    }

    /**
     * Configures WebSettings with strict browser/PWA parity and security hardening.
     * Complies with Google Play policy and web standards.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun applyWebSettings(
        webView: WebView,
        context: Context,
        isDesktop: Boolean = false,
        acceptThirdPartyCookies: Boolean = false,
        webAuthEnabled: Boolean = true
    ) {
        val defaultDeviceUa = try {
            WebSettings.getDefaultUserAgent(context)
        } catch (_: Throwable) {
            null
        }

        webView.setLayerType(android.view.View.LAYER_TYPE_NONE, null)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            textZoom = 100
            cacheMode = WebSettings.LOAD_DEFAULT
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            loadsImagesAutomatically = true
            blockNetworkImage = false
            blockNetworkLoads = false
            offscreenPreRaster = false

            // Web Platform Parity: Compatibility mode allows secure webapps and modern PWAs to render seamlessly
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }

            applyWebAuthenticationSupport(this, webAuthEnabled)
            applyKazviewSettings(this)
            KaspaPrivacyEngine.applyUserAgentAndMetadata(this, isDesktop, defaultDeviceUa)
        }

        // Cookie Configuration. Third-party cookies remain disabled unless the
        // caller explicitly opts in for a compatibility-sensitive flow.
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, acceptThirdPartyCookies)
        } catch (e: Exception) {
            Log.w(TAG, "CookieManager setup notice: ${e.message}")
        }

        // Native Document-Start Script Injection (True Chromium Engine-Level Farbling)
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    KaspaPrivacyEngine.JS_PRIVACY_SHIELD_INJECTION,
                    setOf("*")
                )
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    BrowserBackgroundPlayback.getBackgroundPlaybackScript(),
                    setOf("*")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "DocumentStartJavaScript setup notice: ${e.message}")
        }
    }

    private fun escapeHtml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun escapeJsString(input: String): String {
        val sb = StringBuilder(input.length + 2).append('"')
        for (ch in input) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\'' -> sb.append("\\'")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '<' -> sb.append("\\u003C")
                '>' -> sb.append("\\u003E")
                '&' -> sb.append("\\u0026")
                else -> sb.append(ch)
            }
        }
        return sb.append('"').toString()
    }

    /**
     * Generates a modern, clean HTML recovery / error card when web navigation encounters a network error,
     * preventing indefinite blank screens and providing a manual retry trigger.
     */
    fun generateErrorHtml(failingUrl: String, errorDescription: String?): String {
        val errorMsg = when {
            errorDescription?.contains("ERR_INTERNET_DISCONNECTED", ignoreCase = true) == true ->
                "No internet connection. Please check your mobile data or Wi-Fi."
            errorDescription?.contains("ERR_NAME_NOT_RESOLVED", ignoreCase = true) == true ->
                "DNS server resolution failed. The website host could not be reached."
            errorDescription?.contains("ERR_CONNECTION_TIMED_OUT", ignoreCase = true) == true ->
                "Connection timed out. The server took too long to respond."
            errorDescription?.contains("ERR_CONNECTION_REFUSED", ignoreCase = true) == true ->
                "The server refused the connection."
            !errorDescription.isNullOrBlank() -> errorDescription
            else -> "Network connection lost or web server unreachable."
        }
        val safeRetryUrl = failingUrl.trim().takeIf {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        } ?: "https://kaspa.org"
        val quotedJsUrl = escapeJsString(safeRetryUrl)
        val safeHtmlErrorMsg = escapeHtml(errorMsg)
        val safeHtmlUrl = escapeHtml(failingUrl)

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * { box-sizing: border-box; }
                    body { background-color: #0B0F17; color: #E2E8F0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 85vh; margin: 0; padding: 24px; text-align: center; }
                    .card { background: #151A26; border: 1px solid #1E293B; border-radius: 18px; padding: 32px 24px; max-width: 380px; width: 100%; box-shadow: 0 12px 30px rgba(0,0,0,0.55); }
                    .icon { font-size: 40px; margin-bottom: 14px; line-height: 1; }
                    h2 { color: #FFFFFF; font-size: 20px; margin: 0 0 10px; font-weight: 700; }
                    p { color: #94A3B8; font-size: 13.5px; line-height: 1.55; margin: 0 0 16px; }
                    .url { color: #00E5FF; font-family: monospace; font-size: 11.5px; word-break: break-all; background: #0B0F17; padding: 10px 12px; border-radius: 8px; margin-bottom: 24px; border: 1px solid #1E293B; }
                    .btn { background: #00E5FF; color: #0B0F17; border: none; padding: 13px 32px; border-radius: 10px; font-weight: 700; font-size: 14.5px; cursor: pointer; width: 100%; display: inline-block; box-sizing: border-box; transition: all 0.2s ease; text-decoration: none; }
                    .btn:active { transform: scale(0.97); opacity: 0.85; }
                    .btn:disabled { background: #1E293B; color: #64748B; cursor: not-allowed; }
                    .spinner { display: inline-block; width: 14px; height: 14px; border: 2px solid #64748B; border-top-color: #00E5FF; border-radius: 50%; animation: spin 0.8s linear infinite; vertical-align: middle; margin-right: 8px; }
                    @keyframes spin { to { transform: rotate(360deg); } }
                </style>
                <script>
                    var isRetrying = false;
                    function retryLoading() {
                        if (isRetrying) return;
                        isRetrying = true;
                        var btn = document.getElementById('retryBtn');
                        if (btn) {
                            btn.disabled = true;
                            btn.innerHTML = '<span class="spinner"></span>Reconnecting...';
                        }
                        var targetUrl = $quotedJsUrl;
                        try {
                            if (window.KaspaNative && typeof window.KaspaNative.retryConnection === 'function') {
                                window.KaspaNative.retryConnection(targetUrl);
                                return;
                            }
                        } catch(e) {}
                        window.location.href = 'kaspa-action://retry?url=' + encodeURIComponent(targetUrl);
                    }
                </script>
            </head>
            <body>
                <div class="card">
                    <div class="icon">📡</div>
                    <h2>Connection Problem</h2>
                    <p>$safeHtmlErrorMsg</p>
                    <div class="url">$safeHtmlUrl</div>
                    <button id="retryBtn" class="btn" onclick="retryLoading()">Retry Connection</button>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Generates a security warning page when SSL verification fails, protecting users
     * from MITM attacks without silent crashes or blank screens.
     */
    fun generateSslErrorHtml(failingUrl: String, sslReason: String): String {
        val safeReason = escapeHtml(sslReason)
        val safeUrl = escapeHtml(failingUrl)
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { font-family: -apple-system, sans-serif; background: #0A0E17; color: #F0F4F8; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }
                    .card { background: #131B2E; border: 1px solid #EF4444; border-radius: 14px; padding: 28px; max-width: 400px; text-align: center; }
                    h2 { color: #EF4444; margin-top: 0; font-size: 18px; font-weight: 700; }
                    p { color: #94A3B8; font-size: 13px; line-height: 1.5; }
                    .url { word-break: break-all; font-family: monospace; background: #0D121D; padding: 10px; border-radius: 6px; font-size: 11px; margin: 14px 0; color: #F59E0B; }
                    .btn { background: #EF4444; color: white; border: none; padding: 12px 24px; border-radius: 8px; font-weight: bold; cursor: pointer; width: 100%; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>Security Warning: Untrusted Certificate</h2>
                    <p>$safeReason</p>
                    <p>Kaspa Privacy Shield halted this connection to prevent data interception.</p>
                    <div class="url">$safeUrl</div>
                    <button class="btn" onclick="history.back()">Return to Safety</button>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
