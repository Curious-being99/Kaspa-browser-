package com.example.network

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Native Deep Link & External App Intent Routing Engine.
 *
 * Implements the architecture used by Chrome & Brave for:
 * 1. Opening installed native Android apps (e.g. YouTube, Spotify, Telegram, X/Twitter, WhatsApp, Maps)
 *    directly from web links instead of trapping the user in the web view.
 * 2. Parsing Android `intent://` links with package target resolution and fallback URLs.
 * 3. Handling custom schemes (e.g. `mailto:`, `tel:`, `geo:`, crypto wallets `kaspa:`, `ethereum:`, `solana:`).
 */
object NativeIntentRoutingEngine {

    private const val TAG = "NativeIntentRouting"

    private val KNOWN_APP_DOMAINS = mapOf(
        "youtube.com" to "com.google.android.youtube",
        "youtu.be" to "com.google.android.youtube",
        "open.spotify.com" to "com.spotify.music",
        "t.me" to "org.telegram.messenger",
        "telegram.me" to "org.telegram.messenger",
        "twitter.com" to "com.twitter.android",
        "x.com" to "com.twitter.android",
        "instagram.com" to "com.instagram.android",
        "maps.google.com" to "com.google.android.apps.maps",
        "whatsapp.com" to "com.whatsapp"
    )

    /**
     * Attempts to route the URL to an installed native Android app.
     * Returns true if handled externally; false if the URL should be loaded in the browser.
     */
    fun routeUrl(
        context: Context,
        rawUrl: String,
        hasUserGesture: Boolean,
        onFallbackWebUrl: (String) -> Unit
    ): Boolean {
        val cleanUrl = rawUrl.trim()
        if (cleanUrl.isBlank()) return false

        // 1. Android Intent URI (intent://...)
        if (cleanUrl.startsWith("intent://", ignoreCase = true)) {
            return try {
                val parsedIntent = Intent.parseUri(cleanUrl, Intent.URI_INTENT_SCHEME).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    component = null
                }
                parsedIntent.selector?.let {
                    it.component = null
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                try {
                    context.startActivity(parsedIntent)
                    true
                } catch (_: ActivityNotFoundException) {
                    val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
                    if (!fallbackUrl.isNullOrBlank() &&
                        !fallbackUrl.startsWith("market://", ignoreCase = true) &&
                        !fallbackUrl.contains("play.google.com/store", ignoreCase = true)
                    ) {
                        onFallbackWebUrl(fallbackUrl)
                    }
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to launch parsed intent: ${e.message}")
                    true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Malformed intent URI: ${e.message}")
                true
            }
        }

        // 2. Custom system protocols (e.g. mailto:, tel:, sms:, geo:, crypto wallets)
        val uri = try { Uri.parse(cleanUrl) } catch (_: Exception) { null }
        val scheme = uri?.scheme?.lowercase() ?: ""

        if (scheme.isNotBlank() &&
            scheme != "http" && scheme != "https" &&
            scheme != "about" && scheme != "data" &&
            scheme != "javascript" && scheme != "blob" &&
            scheme != "file" && scheme != "content"
        ) {
            return try {
                val customIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(customIntent)
                true
            } catch (_: ActivityNotFoundException) {
                Log.d(TAG, "No app found to handle custom scheme: $scheme")
                true
            } catch (e: Exception) {
                Log.w(TAG, "Error handling scheme $scheme: ${e.message}")
                true
            }
        }

        // 3. HTTP / HTTPS with native app domain matching (only on user gesture)
        if (hasUserGesture && (cleanUrl.startsWith("http://", ignoreCase = true) || cleanUrl.startsWith("https://", ignoreCase = true))) {
            val host = uri?.host?.lowercase() ?: ""
            
            // Check known app domains
            for ((domain, packageName) in KNOWN_APP_DOMAINS) {
                if (host.contains(domain) || cleanUrl.lowercase().contains(domain)) {
                    try {
                        val appIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage(packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(appIntent)
                        return true
                    } catch (_: ActivityNotFoundException) {
                        // Native app not installed; fall through to browser rendering
                    } catch (e: Exception) {
                        Log.d(TAG, "App package launch error: ${e.message}")
                    }
                }
            }

            // Check if user has an associated native app handler for this link
            try {
                val genericIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val pm = context.packageManager
                val resolveList = pm.queryIntentActivities(genericIntent, 0)
                val ourPkg = context.packageName

                val nativeApp = resolveList.firstOrNull { ri ->
                    val pkg = ri.activityInfo?.packageName?.lowercase() ?: ""
                    pkg.isNotEmpty() && pkg != ourPkg &&
                        !pkg.contains("chrome") &&
                        !pkg.contains("browser") &&
                        !pkg.contains("webview") &&
                        !pkg.contains("firefox") &&
                        !pkg.contains("opera") &&
                        !pkg.contains("duckduckgo")
                }

                if (nativeApp != null) {
                    genericIntent.setPackage(nativeApp.activityInfo.packageName)
                    context.startActivity(genericIntent)
                    return true
                }
            } catch (_: Exception) {}
        }

        return false
    }
}
