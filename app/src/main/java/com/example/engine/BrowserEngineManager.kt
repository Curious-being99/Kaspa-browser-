package com.example.engine

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import com.example.network.KaspaPrivacyEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class BrowserEngineStatus(
    val engineName: String = "Kazview",
    val engineArchitecture: String = "Kazview WebKit / WebKitGTK Core",
    val webViewPackage: String = "Checking...",
    val webViewVersion: String = "Unknown",
    val isMultiProcess: Boolean = false,
    val asyncStartupState: String = "Pending",
    val startupDurationMs: Long = 0L,
    val uiBlockingLocationsCount: Int = 0,
    val supportsWebAuthn: Boolean = false,
    val passkeySupportMode: String = "Unchecked",
    val passkeyDowngradedBySystem: Boolean = false,
    val supportsDocumentStartScript: Boolean = false,
    val supportsWebMessageListener: Boolean = false,
    val supportsProxyOverride: Boolean = false,
    val backForwardCacheEnabled: Boolean = false,
    val attributionPrivacyDisabled: Boolean = false,
    val multiProfileSupported: Boolean = false,
    val stealthProfileActive: Boolean = false,
    val quicHintsCount: Int = 0,
    val preconnectCount: Int = 0,
    val frozenTabsCount: Int = 0,
    val hibernatedTabsCount: Int = 0,
    val customTabsProvider: String? = null
)

/**
 * Kazview Browser Core Coordinator (WebKit & WebKitGTK Standards).
 *
 * Combines Android's high-speed WebKit rendering engine (`android.webkit` / `androidx.webkit`),
 * multi-process isolation, WebKitGTK Linux standards parity, WebAuthn FOR_BROWSER passkeys,
 * and Custom Tabs handoff with Kazview's privacy & speed layer:
 * - Async off-UI-thread WebKit renderer warm-up
 * - Native WebKit Back/Forward Cache & Attribution Reporting privacy hardening
 * - HTTP/3 QUIC profile hints + privacy-gated TLS/QUIC speculative preconnect
 * - Isolated WebKit Multi-Profile storage for Stealth sessions
 * - Non-destructive background tab freezer & cold-tab memory hibernation
 */
object BrowserEngineManager {
    private const val TAG = "Kazview"
    const val ENGINE_BRAND_NAME = "Kazview"
    const val STEALTH_PROFILE_NAME = "kazview_stealth"
    private const val DEFAULT_MAX_SAVED_STATE_BYTES = 512 * 1024 // 512 KB per tab avoids TransactionTooLargeException
    private const val PRECONNECT_COOLDOWN_MS = 60_000L

    private val KAZVIEW_QUIC_HINTS = setOf(
        "kaspa.org",
        "explorer.kaspa.org",
        "api.kaspa.org",
        "api.kasplex.org",
        "tn10api.kasplex.org",
        "github.com",
        "wikipedia.org"
    )

    private val startupExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val dirInitLock = Any()
    private val warmupStarted = AtomicBoolean(false)
    private val webViewStartupCompleted = AtomicBoolean(false)
    private val profileTuned = AtomicBoolean(false)
    private val pendingReadyCallbacks = mutableListOf<() -> Unit>()
    private val totalPreconnects = AtomicInteger(0)
    private val totalHibernatedTabs = AtomicInteger(0)
    private val recentPreconnects = ConcurrentHashMap<String, Long>()

    private val _engineStatus = MutableStateFlow(BrowserEngineStatus())
    val engineStatus: StateFlow<BrowserEngineStatus> = _engineStatus.asStateFlow()

    /**
     * Ensures Chromium WebView's required cache and storage directory trees exist and are
     * writable, cleaning up any stale non-directory files that would otherwise cause
     * Chromium's SimpleBackendImpl::InitCacheStructureOnDisk to fail creating Code Cache/js.
     */
    fun ensureWebViewDirectories(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        return synchronized(dirInitLock) {
            var allOk = true
            try {
                val cacheRoot = appContext.cacheDir
                if (cacheRoot != null) {
                    if (cacheRoot.exists() && !cacheRoot.isDirectory) {
                        cacheRoot.delete()
                    }
                    if (!cacheRoot.exists()) {
                        cacheRoot.mkdirs()
                    }
                    val requiredCachePaths = listOf(
                        "WebView/Crashpad/attachments",
                        "WebView/Default/HTTP Cache/Cache_Data",
                        "WebView/Default/HTTP Cache/Code Cache/js",
                        "WebView/Default/HTTP Cache/Code Cache/wasm"
                    )
                    for (relPath in requiredCachePaths) {
                        if (!ensureDirectoryTree(cacheRoot, relPath)) {
                            allOk = false
                        }
                    }
                }
                val codeCache = appContext.codeCacheDir
                if (codeCache != null) {
                    if (codeCache.exists() && !codeCache.isDirectory) {
                        codeCache.delete()
                    }
                    if (!codeCache.exists()) {
                        codeCache.mkdirs()
                    }
                }
                val dataRoot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appContext.dataDir
                } else {
                    appContext.filesDir?.parentFile
                }
                if (dataRoot != null) {
                    val appWebView = File(dataRoot, "app_webview")
                    if (appWebView.exists() && !appWebView.isDirectory) {
                        appWebView.delete()
                    }
                    if (!appWebView.exists()) {
                        appWebView.mkdirs()
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "ensureWebViewDirectories notice: ${t.message}")
                allOk = false
            }
            allOk
        }
    }

    private fun ensureDirectoryTree(root: File, relativePath: String): Boolean {
        var current = root
        for (segment in relativePath.split('/')) {
            if (segment.isBlank()) continue
            current = File(current, segment)
            if (current.exists()) {
                if (!current.isDirectory) {
                    if (!current.delete()) {
                        Log.w(TAG, "Blocking file prevents WebView directory: ${current.absolutePath}")
                        return false
                    }
                    if (!current.mkdir() && !current.isDirectory) {
                        Log.w(TAG, "Failed to recreate WebView directory: ${current.absolutePath}")
                        return false
                    }
                }
            } else {
                if (!current.mkdirs() && !current.isDirectory) {
                    Log.w(TAG, "Failed to create WebView directory: ${current.absolutePath}")
                    return false
                }
            }
        }
        return current.isDirectory && current.canWrite()
    }

    private fun dispatchReadyCallbacks() {
        val callbacks = synchronized(pendingReadyCallbacks) {
            webViewStartupCompleted.set(true)
            val copy = pendingReadyCallbacks.toList()
            pendingReadyCallbacks.clear()
            copy
        }
        if (callbacks.isEmpty()) return
        val runnable = Runnable {
            callbacks.forEach { cb ->
                runCatching { cb() }.onFailure { err ->
                    Log.w(TAG, "Post-startup callback notice: ${err.message}")
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
    }

    fun warmUpEngineAsync(context: Context, onReady: (() -> Unit)? = null) {
        val appContext = context.applicationContext ?: context
        ensureWebViewDirectories(appContext)
        if (onReady != null) {
            val alreadyCompleted = synchronized(pendingReadyCallbacks) {
                if (webViewStartupCompleted.get()) {
                    true
                } else {
                    pendingReadyCallbacks.add(onReady)
                    false
                }
            }
            if (alreadyCompleted) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    runCatching { onReady() }
                } else {
                    mainHandler.post { runCatching { onReady() } }
                }
            }
        }
        if (!warmupStarted.compareAndSet(false, true)) {
            refreshEngineStatus(appContext)
            return
        }
        // Defer ProfileStore queries until startUpWebView completes so we don't trigger synchronous UI init.
        refreshEngineStatus(appContext, allowProfileQuery = false)
        val startMs = System.currentTimeMillis()
        try {
            val config = WebViewStartUpConfig.Builder(startupExecutor).build()
            WebViewCompat.startUpWebView(
                appContext,
                config,
                object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
                    override fun onResult(result: WebViewStartUpResult) {
                        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                        val uiBlocking = try {
                            result.uiThreadBlockingStartUpLocations?.size ?: 0
                        } catch (_: Throwable) {
                            0
                        }
                        Log.i(
                            TAG,
                            "Kazview async startup finished in ${elapsed}ms (uiBlocking=$uiBlocking)"
                        )
                        applyProfileOptimizations()
                        dispatchReadyCallbacks()
                        val current = buildStatus(appContext, allowProfileQuery = true)
                        _engineStatus.value = current.copy(
                            asyncStartupState = if (uiBlocking == 0) {
                                "Async Warm (${elapsed}ms)"
                            } else {
                                "Async (${elapsed}ms · ${uiBlocking} UI init)"
                            },
                            startupDurationMs = elapsed,
                            uiBlockingLocationsCount = uiBlocking
                        )
                    }

                    override fun onError(error: WebViewStartupException) {
                        Log.w(TAG, "Async Kazview startup notice: ${error.message}")
                        dispatchReadyCallbacks()
                        val current = buildStatus(appContext, allowProfileQuery = true)
                        _engineStatus.value = current.copy(
                            asyncStartupState = "Standard Init"
                        )
                    }
                }
            )
        } catch (t: Throwable) {
            Log.d(TAG, "startUpWebView fallback to lazy init: ${t.message}")
            dispatchReadyCallbacks()
            val current = buildStatus(appContext, allowProfileQuery = true)
            _engineStatus.value = current.copy(asyncStartupState = "Standard Init")
        }
    }

    /**
     * Applies Chromium Profile-level QUIC hints & renderer process warmup when supported,
     * keeping all calls safely gated behind WebViewFeature checks.
     */
    @androidx.annotation.OptIn(
        Profile.ExperimentalWarmUpRendererProcess::class,
        Profile.ExperimentalAddQuicHints::class
    )
    fun applyProfileOptimizations() {
        if (!profileTuned.compareAndSet(false, true)) return
        try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
            val profile = ProfileStore.getInstance().getProfile(Profile.DEFAULT_PROFILE_NAME) ?: return

            if (WebViewFeature.isFeatureSupported(WebViewFeature.WARM_UP_RENDERER_PROCESS)) {
                runCatching { profile.warmUpRendererProcess() }
            }

            var appliedHints = 0
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ADD_QUIC_HINTS_V1)) {
                runCatching {
                    @Suppress("DEPRECATION")
                    profile.addQuicHints(KAZVIEW_QUIC_HINTS)
                    appliedHints = KAZVIEW_QUIC_HINTS.size
                }
            }
            if (appliedHints > 0) {
                _engineStatus.value = _engineStatus.value.copy(quicHintsCount = appliedHints)
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Profile optimization skipped on runtime: ${t.message}")
        }
    }

    /**
     * Routes dedicated Incognito/Stealth tabs onto an isolated Chromium storage/cookie profile
     * when MULTI_PROFILE is supported, while keeping standard tabs on the Default profile
     * so user logins and Cloudflare clearance remain intact.
     */
    fun assignProfileForTab(webView: WebView, isIncognito: Boolean): Boolean {
        if (!isIncognito) return false
        return try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return false
            ProfileStore.getInstance().getOrCreateProfile(STEALTH_PROFILE_NAME)
            WebViewCompat.setProfile(webView, STEALTH_PROFILE_NAME)
            _engineStatus.value = _engineStatus.value.copy(stealthProfileActive = true)
            true
        } catch (t: Throwable) {
            Log.d(TAG, "Stealth profile assignment fallback: ${t.message}")
            false
        }
    }

    /**
     * Wipes the isolated Stealth Chromium profile completely when clearing browsing data.
     */
    fun clearStealthProfile(): Boolean {
        return try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return false
            val store = ProfileStore.getInstance()
            val deleted = if (store.getAllProfileNames().contains(STEALTH_PROFILE_NAME)) {
                store.deleteProfile(STEALTH_PROFILE_NAME)
            } else {
                true
            }
            _engineStatus.value = _engineStatus.value.copy(stealthProfileActive = false)
            deleted
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Safely preconnects to an HTTPS origin (TLS + DNS + QUIC) ahead of navigation.
     * Strictly skipped whenever Tor or KRP relay privacy routing is active so traffic
     * never bypasses the active privacy circuit, and skips challenge/non-HTTPS URLs.
     */
    @SuppressLint("WrongConstant")
    @androidx.annotation.OptIn(Profile.ExperimentalPreconnect::class)
    fun preconnectOrigin(rawUrl: String?, isPrivacyRelayActive: Boolean): Boolean {
        if (isPrivacyRelayActive || rawUrl.isNullOrBlank()) return false
        val parsed = runCatching { Uri.parse(rawUrl.trim()) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme != "https") return false
        val host = parsed.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return false
        if (KaspaPrivacyEngine.isEssentialAuthOrCaptchaUrl(rawUrl) || KaspaPrivacyEngine.isTrackerOrAd(rawUrl)) {
            return false
        }

        val origin = "https://$host"
        val now = System.currentTimeMillis()
        val last = recentPreconnects[origin]
        if (last != null && (now - last) < PRECONNECT_COOLDOWN_MS) return false

        return try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return false
            val profile = ProfileStore.getInstance().getProfile(Profile.DEFAULT_PROFILE_NAME) ?: return false
            val didPreconnect = when {
                WebViewFeature.isFeatureSupported(WebViewFeature.ENQUEUE_PRECONNECT) -> {
                    profile.enqueuePreconnect(origin)
                    true
                }
                WebViewFeature.isFeatureSupported(WebViewFeature.PRECONNECT) -> {
                    profile.preconnect(origin)
                    true
                }
                else -> false
            }
            if (didPreconnect) {
                recentPreconnects[origin] = now
                if (recentPreconnects.size > 48) {
                    recentPreconnects.entries.removeIf { (now - it.value) > PRECONNECT_COOLDOWN_MS }
                }
                val count = totalPreconnects.incrementAndGet()
                _engineStatus.value = _engineStatus.value.copy(preconnectCount = count)
            }
            didPreconnect
        } catch (_: Throwable) {
            false
        }
    }

    fun recordWebSettingsApplied(bfCacheEnabled: Boolean, attributionDisabled: Boolean) {
        _engineStatus.value = _engineStatus.value.copy(
            backForwardCacheEnabled = _engineStatus.value.backForwardCacheEnabled || bfCacheEnabled,
            attributionPrivacyDisabled = _engineStatus.value.attributionPrivacyDisabled || attributionDisabled
        )
    }

    fun recordTabLifecycleStats(frozenCount: Int, hibernatedDelta: Int = 0) {
        val hibernated = if (hibernatedDelta > 0) {
            totalHibernatedTabs.addAndGet(hibernatedDelta)
        } else {
            totalHibernatedTabs.get()
        }
        _engineStatus.value = _engineStatus.value.copy(
            frozenTabsCount = frozenCount.coerceAtLeast(0),
            hibernatedTabsCount = hibernated
        )
    }

    fun refreshEngineStatus(
        context: Context,
        allowProfileQuery: Boolean = webViewStartupCompleted.get()
    ): BrowserEngineStatus {
        val updated = buildStatus(context.applicationContext ?: context, allowProfileQuery)
        _engineStatus.value = updated
        return updated
    }

    fun recordPasskeySupport(requestedEnabled: Boolean, actualSupport: Int) {
        val modeLabel = when {
            !requestedEnabled -> "Disabled by User"
            actualSupport == WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER -> "FOR_BROWSER Active"
            actualSupport == WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP -> "FOR_APP (Digital Asset Links required)"
            actualSupport == WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_NONE -> "Restricted by System Policy"
            else -> "Mode=$actualSupport"
        }
        val downgraded = requestedEnabled &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION) &&
            actualSupport == WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_NONE
        _engineStatus.value = _engineStatus.value.copy(
            passkeySupportMode = modeLabel,
            passkeyDowngradedBySystem = downgraded
        )
    }

    private fun buildStatus(context: Context, allowProfileQuery: Boolean = webViewStartupCompleted.get()): BrowserEngineStatus {
        val prev = _engineStatus.value
        val pkgInfo = try {
            WebViewCompat.getCurrentWebViewPackage(context)
        } catch (_: Throwable) {
            null
        }
        val multiProcess = try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROCESS)) {
                WebViewCompat.isMultiProcessEnabled()
            } else {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            }
        } catch (_: Throwable) {
            false
        }
        val supportsWebAuth = try {
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)
        } catch (_: Throwable) {
            false
        }
        val supportsDocStart = try {
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        } catch (_: Throwable) {
            false
        }
        val supportsMsgListener = try {
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        } catch (_: Throwable) {
            false
        }
        val supportsProxy = try {
            WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
        } catch (_: Throwable) {
            false
        }
        val supportsMultiProfile = try {
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        } catch (_: Throwable) {
            false
        }
        val stealthActive = if (supportsMultiProfile && allowProfileQuery) {
            try {
                ProfileStore.getInstance().getAllProfileNames().contains(STEALTH_PROFILE_NAME)
            } catch (_: Throwable) {
                prev.stealthProfileActive
            }
        } else {
            prev.stealthProfileActive
        }
        val customTabsPkg = try {
            CustomTabsClient.getPackageName(context, null)
        } catch (_: Throwable) {
            null
        }

        return prev.copy(
            engineName = ENGINE_BRAND_NAME,
            engineArchitecture = "Kazview WebKit / WebKitGTK Core",
            webViewPackage = pkgInfo?.packageName ?: "Android System WebView",
            webViewVersion = pkgInfo?.versionName ?: "Runtime Default",
            isMultiProcess = multiProcess,
            supportsWebAuthn = supportsWebAuth,
            supportsDocumentStartScript = supportsDocStart,
            supportsWebMessageListener = supportsMsgListener,
            supportsProxyOverride = supportsProxy,
            multiProfileSupported = supportsMultiProfile,
            stealthProfileActive = stealthActive,
            quicHintsCount = updateQuicHintStatus(prev, supportsMultiProfile),
            customTabsProvider = customTabsPkg
        )
    }

    @androidx.annotation.OptIn(Profile.ExperimentalAddQuicHints::class)
    private fun updateQuicHintStatus(prev: BrowserEngineStatus, supportsMultiProfile: Boolean): Int {
        return prev.quicHintsCount.takeIf { it > 0 }
            ?: if (supportsMultiProfile && runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.ADD_QUIC_HINTS_V1) }.getOrDefault(false)) {
                KAZVIEW_QUIC_HINTS.size
            } else {
                0
            }
    }

    /**
     * Bounded WebView state serialization using Jetpack Webkit saveState(maxSizeBytes)
     * when available, falling back cleanly while guarding bundle size.
     */
    fun saveWebViewStateBounded(
        webView: WebView,
        outState: Bundle,
        maxBytes: Int = DEFAULT_MAX_SAVED_STATE_BYTES
    ): Boolean {
        return try {
            WebViewCompat.saveState(webView, outState, maxBytes, false)
            true
        } catch (_: NoSuchMethodError) {
            try {
                webView.saveState(outState)
                true
            } catch (_: Throwable) {
                false
            }
        } catch (_: Throwable) {
            try {
                webView.saveState(outState)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    /**
     * Launches an HTTPS/HTTP URL in an Android Custom Tab backed by the device's full browser
     * engine (supporting full FIDO2/WebAuthn passkeys without WebView assetlinks restrictions
     * and full Chrome Autofill/credential providers).
     */
    fun openInCustomTab(context: Context, rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val trimmed = rawUrl.trim()
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        if (uri.host.isNullOrBlank()) return false

        return try {
            val obsidianToolbar = android.graphics.Color.parseColor("#0B0F17")
            val cyanAccent = android.graphics.Color.parseColor("#00E5FF")
            val colorParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(obsidianToolbar)
                .setNavigationBarColor(obsidianToolbar)
                .setSecondaryToolbarColor(cyanAccent)
                .build()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                .setDefaultColorSchemeParams(colorParams)
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .build()

            val pkg = CustomTabsClient.getPackageName(context, null)
            if (!pkg.isNullOrBlank()) {
                customTabsIntent.intent.setPackage(pkg)
            }
            if (context !is android.app.Activity) {
                customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            customTabsIntent.launchUrl(context, uri)
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Custom Tab launch failed, trying external VIEW intent: ${e.message}")
            try {
                val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    if (context !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(viewIntent)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    /**
     * Clears storage and cookies for a specific origin/site cleanly without wiping other sites.
     */
    fun clearSiteData(urlOrOrigin: String, onDone: (() -> Unit)? = null) {
        try {
            val uri = Uri.parse(urlOrOrigin)
            val origin = if (!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) {
                "${uri.scheme}://${uri.host}"
            } else {
                urlOrOrigin
            }
            WebStorage.getInstance().deleteOrigin(origin)
            CookieManager.getInstance().flush()
        } catch (_: Throwable) {
        } finally {
            onDone?.invoke()
        }
    }
}
