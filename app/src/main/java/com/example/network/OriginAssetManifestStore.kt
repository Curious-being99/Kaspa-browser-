package com.example.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Persists a bounded per-origin manifest of critical render-path static assets
 * (CSS -> Webfonts -> JS/MJS) observed during normal HTTPS browsing.
 *
 * When a user revisits an origin, clicks a link, or types a host in the address bar,
 * Kazview pre-warms these subresources over Cronet HTTP/3 directly into WebViewAssetLruCache
 * + POSIX mmap ahead of Blink finishing HTML parsing—turning critical stylesheets, fonts,
 * and scripts into sub-millisecond zero-copy cache hits without forking Chromium/Blink.
 *
 * Privacy-strict: never records or warms when Incognito or Privacy Relay / Tor is active,
 * and excludes auth, CAPTCHA, local/private, and tracker endpoints.
 */
object OriginAssetManifestStore {

    private const val PREFS_NAME = "kazview_origin_manifests"
    private const val PREFS_KEY = "manifests_v1"
    const val MAX_ORIGINS = 64
    const val MAX_ASSETS_PER_ORIGIN = 10
    private const val PERSIST_MIN_INTERVAL_MS = 2_000L

    data class ManifestAsset(
        val url: String,
        val priority: Int,
        val lastSeenMs: Long
    )

    private data class OriginEntry(
        val origin: String,
        var lastUpdatedMs: Long,
        val assets: MutableList<ManifestAsset> = mutableListOf()
    )

    private val lock = Any()
    private val manifests = ConcurrentHashMap<String, OriginEntry>()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)
    private val persistScheduled = AtomicBoolean(false)
    private val lastPersistMs = AtomicLong(0L)

    @Volatile
    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        val appContext = context.applicationContext ?: context
        val sp = runCatching {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull() ?: return
        prefs = sp
        if (!initialized.compareAndSet(false, true)) return

        val rawJson = runCatching { sp.getString(PREFS_KEY, null) }.getOrNull()
        if (rawJson.isNullOrBlank()) return

        runCatching {
            val root = JSONArray(rawJson)
            synchronized(lock) {
                for (i in 0 until root.length()) {
                    val obj = root.optJSONObject(i) ?: continue
                    val origin = obj.optString("origin").takeIf { it.startsWith("https://") } ?: continue
                    val updated = obj.optLong("updated", 0L)
                    val arr = obj.optJSONArray("assets") ?: continue
                    val list = mutableListOf<ManifestAsset>()
                    for (j in 0 until arr.length()) {
                        val aObj = arr.optJSONObject(j) ?: continue
                        val url = aObj.optString("u")
                        val prio = aObj.optInt("p", -1)
                        val seen = aObj.optLong("t", updated)
                        if (url.startsWith("https://") && prio in 0..3) {
                            list.add(ManifestAsset(url = url, priority = prio, lastSeenMs = seen))
                        }
                    }
                    if (list.isNotEmpty()) {
                        list.sortWith(compareBy<ManifestAsset> { it.priority }.thenByDescending { it.lastSeenMs })
                        manifests[origin] = OriginEntry(
                            origin = origin,
                            lastUpdatedMs = updated,
                            assets = list.take(MAX_ASSETS_PER_ORIGIN).toMutableList()
                        )
                    }
                }
                trimOriginsLocked()
            }
        }
    }

    fun normalizeOrigin(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "https") return null
        val host = uri.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
        if (KaspaPrivacyEngine.isLocalOrPrivateHost(host)) return null
        if (KaspaPrivacyEngine.isEssentialAuthOrCaptchaUrl(trimmed)) return null
        val portSuffix = if (uri.port > 0 && uri.port != 443) ":${uri.port}" else ""
        return "https://$host$portSuffix"
    }

    fun priorityForUrl(rawUrl: String?): Int? {
        if (rawUrl.isNullOrBlank()) return null
        val path = runCatching { URI(rawUrl).path?.lowercase(Locale.ROOT) }.getOrNull() ?: return null
        return when {
            path.endsWith(".css") -> 0
            path.endsWith(".woff2") -> 1
            path.endsWith(".woff") || path.endsWith(".ttf") || path.endsWith(".otf") -> 2
            path.endsWith(".js") || path.endsWith(".mjs") -> 3
            else -> null
        }
    }

    fun recordCriticalAsset(
        pageUrlOrOrigin: String?,
        assetUrl: String?,
        isPrivacyBlocked: Boolean
    ): Boolean {
        if (isPrivacyBlocked) return false
        val origin = normalizeOrigin(pageUrlOrOrigin) ?: return false
        val trimmedAsset = assetUrl?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) } ?: return false
        val priority = priorityForUrl(trimmedAsset) ?: return false
        if (!WebViewAssetLruCache.shouldCache(trimmedAsset, method = "GET", isMainFrame = false)) return false
        if (KaspaPrivacyEngine.isTrackerOrAd(trimmedAsset)) return false

        val now = System.currentTimeMillis()
        synchronized(lock) {
            val entry = manifests.getOrPut(origin) { OriginEntry(origin = origin, lastUpdatedMs = now) }
            entry.lastUpdatedMs = now
            entry.assets.removeAll { it.url == trimmedAsset }
            entry.assets.add(ManifestAsset(url = trimmedAsset, priority = priority, lastSeenMs = now))
            entry.assets.sortWith(compareBy<ManifestAsset> { it.priority }.thenByDescending { it.lastSeenMs })
            while (entry.assets.size > MAX_ASSETS_PER_ORIGIN) {
                entry.assets.removeAt(entry.assets.lastIndex)
            }
            trimOriginsLocked()
        }
        schedulePersist()
        return true
    }

    fun getAssetsForOrigin(urlOrOrigin: String?): List<String> {
        val origin = normalizeOrigin(urlOrOrigin) ?: return emptyList()
        synchronized(lock) {
            return manifests[origin]?.assets?.map { it.url } ?: emptyList()
        }
    }

    fun getOriginCount(): Int = manifests.size

    fun warmOriginAssets(
        urlOrOrigin: String?,
        isPrivacyBlocked: Boolean,
        maxAssets: Int = 6,
        onAssetWarmed: (() -> Unit)? = null
    ): Int {
        if (isPrivacyBlocked || maxAssets <= 0) return 0
        val origin = normalizeOrigin(urlOrOrigin) ?: return 0
        val targets = synchronized(lock) {
            val entry = manifests[origin] ?: return 0
            entry.lastUpdatedMs = System.currentTimeMillis()
            entry.assets.take(maxAssets).map { it.url }
        }
        var dispatched = 0
        for (assetUrl in targets) {
            if (WebViewAssetLruCache.warmSubresourceAsync(url = assetUrl, onWarmed = onAssetWarmed)) {
                dispatched++
            }
        }
        return dispatched
    }

    fun clear() {
        synchronized(lock) {
            manifests.clear()
        }
        runCatching {
            prefs?.edit()?.remove(PREFS_KEY)?.apply()
        }
    }

    private fun trimOriginsLocked() {
        while (manifests.size > MAX_ORIGINS) {
            val oldest = manifests.values.minByOrNull { it.lastUpdatedMs } ?: break
            manifests.remove(oldest.origin)
        }
    }

    private fun schedulePersist() {
        if (prefs == null) return
        val now = System.currentTimeMillis()
        if ((now - lastPersistMs.get()) < PERSIST_MIN_INTERVAL_MS) return
        if (!persistScheduled.compareAndSet(false, true)) return

        ioScope.launch {
            try {
                lastPersistMs.set(System.currentTimeMillis())
                val snapshotJson = synchronized(lock) {
                    val root = JSONArray()
                    manifests.values
                        .sortedByDescending { it.lastUpdatedMs }
                        .take(MAX_ORIGINS)
                        .forEach { entry ->
                            val obj = JSONObject()
                            obj.put("origin", entry.origin)
                            obj.put("updated", entry.lastUpdatedMs)
                            val arr = JSONArray()
                            entry.assets.forEach { asset ->
                                arr.put(
                                    JSONObject().apply {
                                        put("u", asset.url)
                                        put("p", asset.priority)
                                        put("t", asset.lastSeenMs)
                                    }
                                )
                            }
                            obj.put("assets", arr)
                            root.put(obj)
                        }
                    root.toString()
                }
                prefs?.edit()?.putString(PREFS_KEY, snapshotJson)?.apply()
            } catch (_: Throwable) {
            } finally {
                persistScheduled.set(false)
            }
        }
    }
}
