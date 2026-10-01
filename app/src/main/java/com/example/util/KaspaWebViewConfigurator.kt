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
import com.example.network.KaspaPrivacyEngine

object KaspaWebViewConfigurator {

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
            swSettings.allowContentAccess = true
            swSettings.allowFileAccess = true
            swSettings.blockNetworkLoads = false
            swSettings.cacheMode = WebSettings.LOAD_DEFAULT
            swController.setServiceWorkerClient(object : ServiceWorkerClient() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                    Log.d("PWA", "[PWA] service worker: intercepting ${request.url}")
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
    fun applyWebSettings(webView: WebView, context: Context, isDesktop: Boolean = false, acceptThirdPartyCookies: Boolean = false) {
        val defaultDeviceUa = try {
            WebSettings.getDefaultUserAgent(context)
        } catch (_: Throwable) {
            null
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = isDesktop
            loadWithOverviewMode = isDesktop
            textZoom = 100
            cacheMode = WebSettings.LOAD_DEFAULT
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            loadsImagesAutomatically = true
            blockNetworkImage = false
            blockNetworkLoads = false
            offscreenPreRaster = true

            // Security Hardening: Never allow unencrypted mixed content on HTTPS connections
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }

            userAgentString = if (isDesktop) {
                KaspaPrivacyEngine.getDesktopUserAgent(defaultDeviceUa)
            } else {
                KaspaPrivacyEngine.getMobileUserAgent(defaultDeviceUa)
            }
        }

        // Cookie Configuration
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, acceptThirdPartyCookies)
        } catch (e: Exception) {
            Log.w(TAG, "CookieManager setup notice: ${e.message}")
        }
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
        val escapedUrl = failingUrl.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "&quot;")

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
                        var targetUrl = '$escapedUrl';
                        window.location.href = targetUrl;
                    }
                </script>
            </head>
            <body>
                <div class="card">
                    <div class="icon">📡</div>
                    <h2>Connection Problem</h2>
                    <p>$errorMsg</p>
                    <div class="url">$failingUrl</div>
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
                    <p>$sslReason</p>
                    <p>Kaspa Privacy Shield halted this connection to prevent data interception.</p>
                    <div class="url">$failingUrl</div>
                    <button class="btn" onclick="history.back()">Return to Safety</button>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
