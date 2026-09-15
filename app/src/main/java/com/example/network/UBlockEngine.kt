package com.example.network

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Real uBlock Filter & Ad Blocking Engine.
 * Implements uBlock Origin rule parsing, domain suffix matching, exception handling,
 * cosmetic element hiding, and real-time filter list updates.
 */
object UBlockEngine {
    private const val TAG = "UBlockEngine"
    private const val PREFS_NAME = "ublock_engine_prefs"
    private const val KEY_LAST_UPDATE = "last_rules_update"
    private const val KEY_CUSTOM_RULES = "custom_rules_cache"
    private const val FILTERS_FILENAME = "ublock_rules.txt"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    // Essential CDN, font, and web infrastructure domains that must NEVER be blocked
    private val ESSENTIAL_WEB_DOMAINS = setOf(
        "fonts.googleapis.com",
        "fonts.gstatic.com",
        "cdnjs.cloudflare.com",
        "cdn.jsdelivr.net",
        "unpkg.com",
        "ajax.googleapis.com",
        "stackpath.bootstrapcdn.com",
        "maxcdn.bootstrapcdn.com",
        "code.jquery.com",
        "cdn.tailwindcss.com",
        "cloudflare.com",
        "fastly.net",
        "akamaihd.net",
        "githubassets.com",
        "raw.githubusercontent.com",
        "wikimedia.org",
        "wikipedia.org",
        "wp.com",
        "s.w.org",
        "w3.org",
        "kaspa.org",
        "kas.pa",
        "youtube.com",
        "m.youtube.com",
        "www.youtube.com",
        "youtu.be",
        "googlevideo.com",
        "ytimg.com",
        "i.ytimg.com",
        "jnn-pa.googleapis.com",
        "play.google.com",
        "ggpht.com",
        "yt3.ggpht.com",
        "yt4.ggpht.com",
        "youtube-nocookie.com",
        "vimeo.com",
        "vimeocdn.com",
        "dailymotion.com"
    )

    // High performance O(1) exact domain blocklist
    private val blockedDomains = ConcurrentHashMap.newKeySet<String>()

    // Whitelisted domains / exceptions (rules starting with @@)
    private val whitelistedDomains = ConcurrentHashMap.newKeySet<String>()

    // Cosmetic element hiding selectors extracted from uBlock rules (##selector)
    private val cosmeticSelectors = ConcurrentHashMap.newKeySet<String>()

    // Fixed constant set of universal ad/telemetry paths
    private val COMMON_AD_PATHS = arrayOf(
        "/pagead/",
        "/gtm.js?id=",
        "/fbevents.js",
        "/analytics.js",
        "/pixel.gif",
        "/adsbygoogle.js"
    )

    private val _rulesCount = MutableStateFlow(0)
    val rulesCount: StateFlow<Int> = _rulesCount.asStateFlow()

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private val _lastUpdateStatus = MutableStateFlow("uBlock filter database active")
    val lastUpdateStatus: StateFlow<String> = _lastUpdateStatus.asStateFlow()

    @Volatile
    private var isInitialized = false

    /**
     * Production uBlock Origin & EasyList official mirror sources
     */
    private val OFFICIAL_FILTER_URLS = listOf(
        "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt",
        "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt",
        "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/badware.txt",
        "https://easylist.to/easylist/easyprivacy.txt"
    )

    // Preloaded comprehensive uBlock baseline rules covering major ad tech, telemetry, and tracking networks
    private val BASELINE_UBLOCK_RULES = listOf(
        // Google Ad & Telemetry Services
        "||googleadservices.com^",
        "||googlesyndication.com^",
        "||doubleclick.net^",
        "||adservice.google.com^",
        "||pagead2.googlesyndication.com^",
        "||google-analytics.com^",
        "||googletagmanager.com/gtm.js*",
        "||googletagservices.com^",
        "||analytics.google.com^",
        "||ads.google.com^",
        "||adwords.google.com^",
        "||stats.g.doubleclick.net^",

        // Meta / Facebook Tracking Pixels
        "||connect.facebook.net/en_US/fbevents.js*",
        "||connect.facebook.net/signals/config/*",
        "||pixel.facebook.com^",
        "||an.facebook.com^",
        "||ads.facebook.com^",
        "||graph.facebook.com/tr/*",

        // TikTok / ByteDance Adtech
        "||analytics.tiktok.com^",
        "||ads.tiktok.com^",
        "||business-api.tiktok.com^",
        "||pangle-ads.com^",

        // Twitter / X Ads & Analytics
        "||ads-twitter.com^",
        "||static.ads-twitter.com^",
        "||analytics.twitter.com^",
        "||t.co/i/adsct*",

        // Amazon Advertising
        "||amazon-adsystem.com^",
        "||aax.amazon-adsystem.com^",
        "||c.amazon-adsystem.com^",
        "||s.amazon-adsystem.com^",

        // Criteo & Retargeting
        "||criteo.com^",
        "||criteo.net^",
        "||static.criteo.net^",
        "||bidder.criteo.com^",

        // Content Ad Networks (Taboola, Outbrain, Revcontent)
        "||taboola.com^",
        "||cdn.taboola.com^",
        "||outbrain.com^",
        "||widgets.outbrain.com^",
        "||revcontent.com^",
        "||trends.revcontent.com^",
        "||mgid.com^",

        // Programmatic & DSP / SSP Networks
        "||adnxs.com^",
        "||ib.adnxs.com^",
        "||rubiconproject.com^",
        "||fastlane.rubiconproject.com^",
        "||pubmatic.com^",
        "||ads.pubmatic.com^",
        "||openx.net^",
        "||casalemedia.com^",
        "||adroll.com^",
        "||smartadserver.com^",
        "||scorecardresearch.com^",
        "||b.scorecardresearch.com^",
        "||quantserve.com^",
        "||pixel.quantserve.com^",
        "||mathtag.com^",
        "||advertising.com^",
        "||yieldmo.com^",
        "||sharethrough.com^",
        "||inmobi.com^",
        "||smaato.net^",
        "||unityads.unity3d.com^",
        "||appl运.com^",
        "||ironsrc.com^",
        "||vungle.com^",
        "||mintegral.com^",
        "||chartboost.com^",
        "||applovin.com^",

        // Mobile Telemetry, Attribution & Analytics
        "||appsflyer.com^",
        "||api2.appsflyer.com^",
        "||branch.io^",
        "||api.branch.io^",
        "||adjust.com^",
        "||app.adjust.com^",
        "||kochava.com^",
        "||control.kochava.com^",
        "||amplitude.com^",
        "||api.amplitude.com^",
        "||mixpanel.com^",
        "||api.mixpanel.com^",
        "||segment.io^",
        "||api.segment.io^",
        "||hotjar.com^",
        "||static.hotjar.com^",
        "||script.hotjar.com^",
        "||clarity.ms^",
        "||www.clarity.ms^",
        "||c.clarity.ms^",
        "||fullstory.com^",
        "||rs.fullstory.com^",
        "||mouseflow.com^",
        "||crazyegg.com^",
        "||script.crazyegg.com^",
        "||heap.io^",
        "||heapanalytics.com^",
        "||inspectlet.com^",

        // Fingerprinting & Bot Telemetry Networks
        "||fpjs.sh^",
        "||api.fpjs.io^",
        "||fingerprintjs.com^",
        "||threatmetrix.com^",
        "||perimeterx.net^",
        "||arkoselabs.com^",
        "||client-api.arkoselabs.com^",
        "||datadome.co^",
        "||api-js.datadome.co^",
        "||tealiumiq.com^",
        "||tags.tiqcdn.com^",
        "||optimizely.com^",
        "||logx.optimizely.com^",

        // Regional Ad & Analytics Engines (Yandex, Baidu, Yahoo)
        "||mc.yandex.ru^",
        "||an.yandex.ru^",
        "||hm.baidu.com^",
        "||pos.baidu.com^",
        "||adtech.yahooinc.com^",
        "||gemini.yahoo.com^",
        "||flurry.com^",

        // Standard uBlock URL Path Filter Patterns
        "/pagead/js/adsbygoogle.js",
        "/pagead/show_ads.js",
        "/pagead/expansion_embed.js",
        "/ads/ga-audiences",
        "/gtm.js?id=",
        "/analytics.js",
        "/fbevents.js",
        "/beacon/pixel.gif",
        "/telemetry/collect",
        "/tracker/event",
        "/track/adclick",
        "*-ad-banner*",
        "*/advertisement/*",

        // Standard uBlock Cosmetic Ad Hiding Selectors
        "##.adsbygoogle",
        "##ins.adsbygoogle",
        "##[id^=\"google_ads\"]",
        "##[id^=\"div-gpt-ad\"]",
        "##.ad-banner",
        "##.ad_banner",
        "##.ad-container",
        "##.ad_container",
        "##.advertisement",
        "##.ad-placement",
        "##.trc_related_container",
        "###taboola-below-article-thumbnails",
        "##.outbrain_widget",
        "##[data-ad-slot]",
        "##iframe[src*=\"doubleclick.net\"]",
        "##iframe[src*=\"googlesyndication.com\"]",
        "##.sponsored-post",
        "##.native-ad-wrapper"
    )

    /**
     * Initializes the uBlock Engine with baseline rules and loads any cached updates.
     */
    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            isInitialized = true
        }

        scope.launch {
            loadRules(context)
        }
    }

    private suspend fun loadRules(context: Context) = withContext(Dispatchers.IO) {
        // 1. Parse Baseline Rules
        for (rule in BASELINE_UBLOCK_RULES) {
            parseRule(rule)
        }

        // 2. Load Cached Downloaded Rules from local storage if available
        try {
            val cachedFile = File(context.filesDir, FILTERS_FILENAME)
            if (cachedFile.exists() && cachedFile.length() > 0) {
                cachedFile.bufferedReader().useLines { lines ->
                    lines.forEach { line -> parseRule(line) }
                }
                _lastUpdateStatus.value = "uBlock rules active (${cachedFile.length() / 1024} KB cached)"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading cached rules: ${e.message}")
        }

        val total = blockedDomains.size + whitelistedDomains.size + cosmeticSelectors.size
        _rulesCount.value = total
        Log.i(TAG, "uBlock Engine initialized with $total active blocking rules")
    }

    private fun isSafeCosmeticSelector(selector: String): Boolean {
        if (selector.length > 80 || selector.contains(":xpath") || selector.contains("body") || selector.contains("html") || selector.contains("'") || selector.contains("\"")) {
            return false
        }
        val lower = selector.lowercase()
        return lower.startsWith(".adsbygoogle") ||
               lower.startsWith("ins.adsbygoogle") ||
               lower.startsWith("[id^=google_ads") ||
               lower.startsWith("[id^=div-gpt-ad") ||
               lower.startsWith(".trc_related_container") ||
               lower.startsWith(".outbrain_widget") ||
               lower.startsWith("#taboola-")
    }

    /**
     * Parses a single rule conforming to standard uBlock / Adblock syntax.
     */
    fun parseRule(rawLine: String) {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[") || line.startsWith("#?#")) {
            // Ignore comments, headers, and extended element queries
            return
        }

        // Whitelist exception: @@||domain.com^
        if (line.startsWith("@@")) {
            val clean = line.removePrefix("@@")
            val domain = clean.removePrefix("||").substringBefore('^').substringBefore('$').trim().lowercase()
            if (domain.isNotEmpty()) {
                whitelistedDomains.add(domain)
            }
            return
        }

        // Cosmetic element hiding rule: ##selector
        if (line.startsWith("##")) {
            val selector = line.removePrefix("##").trim()
            if (isSafeCosmeticSelector(selector)) {
                cosmeticSelectors.add(selector)
            }
            return
        }

        // Domain-specific cosmetic rule: domain.com##selector
        if (line.contains("##")) {
            val selector = line.substringAfter("##").trim()
            if (isSafeCosmeticSelector(selector)) {
                cosmeticSelectors.add(selector)
            }
            return
        }

        // Standard domain block: ||domain.com^
        if (line.startsWith("||")) {
            val withoutPrefix = line.removePrefix("||")
            val domain = withoutPrefix.substringBefore('^')
                .substringBefore('/')
                .substringBefore('$')
                .trim()
                .lowercase()

            // Guard: ensure not an essential CDN or infrastructure domain and has a valid domain structure
            if (domain.length > 3 && domain.contains('.') && !ESSENTIAL_WEB_DOMAINS.contains(domain)) {
                blockedDomains.add(domain)
            }
            return
        }
    }

    /**
     * Evaluates a request URL against the active uBlock rule database in pure O(1) time.
     * Returns true if the request matches a blocking rule and is not whitelisted.
     */
    fun shouldBlock(url: String): Boolean {
        if (url.length < 4) return false

        // Fast skip for internal pseudo-schemes
        val firstChar = url[0]
        if (firstChar == 'd' || firstChar == 'b' || firstChar == 'a' || firstChar == 'j') {
            if (url.startsWith("data:", ignoreCase = true) ||
                url.startsWith("blob:", ignoreCase = true) ||
                url.startsWith("about:", ignoreCase = true) ||
                url.startsWith("javascript:", ignoreCase = true)
            ) {
                return false
            }
        }

        // Never block decentralized Kaspa schemes or localhost nodes
        if (url.startsWith("kaspa:", ignoreCase = true) ||
            url.startsWith("dnet:", ignoreCase = true) ||
            url.startsWith("ipfs:", ignoreCase = true) ||
            url.startsWith("hyper:", ignoreCase = true) ||
            url.contains("127.0.0.1") ||
            url.contains("localhost")
        ) {
            return false
        }

        val host = extractHost(url) ?: return false

        // Check if explicitly whitelisted or an essential web infrastructure domain (O(1))
        if (whitelistedDomains.contains(host) || ESSENTIAL_WEB_DOMAINS.contains(host)) return false

        // 1. Instant O(1) Hierarchical Domain Lookup
        // e.g. for "adserver.doubleclick.net", checks:
        // "adserver.doubleclick.net" in blockedDomains
        // "doubleclick.net" in blockedDomains
        var currentHost = host
        while (true) {
            if (blockedDomains.contains(currentHost)) {
                return true
            }
            val dot = currentHost.indexOf('.')
            if (dot == -1 || dot >= currentHost.length - 2) break
            currentHost = currentHost.substring(dot + 1)
        }

        // 2. Fast check against universal ad telemetry paths (fixed small constant array)
        for (i in 0 until COMMON_AD_PATHS.size) {
            if (url.contains(COMMON_AD_PATHS[i])) {
                return true
            }
        }

        return false
    }

    /**
     * Fast host extraction without allocating unnecessary objects.
     */
    fun extractHost(url: String): String? {
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
     * Triggers a background update from official uBlock Origin & EasyList sources.
     * Parses rules, saves them to local storage, and updates live filtering state.
     */
    fun updateFilters(context: Context, onComplete: ((Boolean, String) -> Unit)? = null) {
        if (_isUpdating.value) return
        _isUpdating.value = true
        _lastUpdateStatus.value = "Updating uBlock filter database..."

        scope.launch {
            var updatedCount = 0
            var success = false
            var errorMessage = ""

            try {
                val tempFile = File(context.filesDir, "${FILTERS_FILENAME}.tmp")
                tempFile.bufferedWriter().use { writer ->
                    for (filterUrl in OFFICIAL_FILTER_URLS) {
                        try {
                            val request = Request.Builder()
                                .url(filterUrl)
                                .header("User-Agent", "uBlock-KaspaBrowser/1.0")
                                .build()

                            httpClient.newCall(request).execute().use { response ->
                                if (response.isSuccessful && response.body != null) {
                                    val stream = response.body!!.byteStream()
                                    stream.bufferedReader().useLines { lines ->
                                        lines.forEach { line ->
                                            writer.write(line)
                                            writer.newLine()
                                            parseRule(line)
                                            updatedCount++
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed fetching $filterUrl: ${e.message}")
                        }
                    }
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    val targetFile = File(context.filesDir, FILTERS_FILENAME)
                    tempFile.renameTo(targetFile)
                    success = true
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Network error"
                Log.e(TAG, "uBlock update error", e)
            } finally {
                val totalRules = blockedDomains.size + whitelistedDomains.size + cosmeticSelectors.size
                _rulesCount.value = totalRules
                _isUpdating.value = false

                val statusText = if (success) {
                    "uBlock filters updated successfully ($totalRules rules active)"
                } else if (totalRules > 0) {
                    "uBlock active ($totalRules rules loaded)"
                } else {
                    "uBlock update failed: $errorMessage"
                }
                _lastUpdateStatus.value = statusText

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(success, statusText)
                }
            }
        }
    }

    /**
     * Injects CSS to cosmetically hide ads and tracking widgets on the active page.
     */
    fun getCosmeticHidingCss(): String {
        val selectors = if (cosmeticSelectors.isNotEmpty()) {
            cosmeticSelectors.take(25).joinToString(", ")
        } else {
            ".adsbygoogle, ins.adsbygoogle, [id^=google_ads], [id^=div-gpt-ad], .outbrain_widget, .trc_related_container"
        }

        return """
            (function() {
                try {
                    var styleId = '__ublock_cosmetic_style';
                    if (document.getElementById(styleId)) return;
                    var style = document.createElement('style');
                    style.id = styleId;
                    style.type = 'text/css';
                    style.textContent = '$selectors { display: none !important; visibility: hidden !important; height: 0 !important; min-height: 0 !important; opacity: 0 !important; pointer-events: none !important; }';
                    (document.head || document.documentElement).appendChild(style);
                } catch(e) {}
            })();
        """.trimIndent()
    }
}
