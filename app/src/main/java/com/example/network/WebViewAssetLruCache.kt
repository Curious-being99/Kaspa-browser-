package com.example.network

import android.content.Context
import android.util.Log
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import okhttp3.OkHttpClient

/**
 * High-Performance Disk-Based LRU Cache for WebView Assets.
 *
 * Caches static Web assets (CSS, JS, Fonts, Images, WASM, IPFS / KNS decentralized node assets)
 * on disk with an LRU (Least-Recently-Used) eviction strategy to reduce network bandwidth,
 * lower node latency, and accelerate loading of decentralized network resources.
 */
object WebViewAssetLruCache {

    private const val TAG = "WebViewAssetLruCache"

    // Default Cache Config: 256 MB max size, 5,000 max entries
    private const val DEFAULT_MAX_SIZE_BYTES = 256L * 1024L * 1024L // 256MB
    private const val DEFAULT_MAX_ENTRIES = 5000

    private var cacheDir: File? = null
    private var maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES
    private var maxEntries: Int = DEFAULT_MAX_ENTRIES

    private val rwLock = ReentrantReadWriteLock()

    // Metrics counters
    private val hitCount = AtomicLong(0)
    private val missCount = AtomicLong(0)
    private val evictionCount = AtomicLong(0)
    private val currentTotalSize = AtomicLong(0)

    private data class CacheMetadata(
        val key: String,
        val url: String,
        val mimeType: String,
        val encoding: String?,
        val contentLength: Long,
        var lastAccessed: Long,
        val responseHeaders: Map<String, String>
    )

    // In-memory index of entries sorted by access time for LRU tracking
    private val memoryIndex = ConcurrentHashMap<String, CacheMetadata>()

    private val httpClient: OkHttpClient by lazy {
        CronetClientFactory.buildClient(
            OkHttpClient.Builder()
                .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
        )
    }

    fun initialize(context: Context, maxSize: Long = DEFAULT_MAX_SIZE_BYTES, maxItems: Int = DEFAULT_MAX_ENTRIES) {
        rwLock.write {
            val dir = File(context.cacheDir, "webview_asset_lru_cache")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            cacheDir = dir
            maxSizeBytes = maxSize
            maxEntries = maxItems

            // Read existing cached files and restore index
            memoryIndex.clear()
            var totalBytes = 0L

            dir.listFiles()?.filter { it.extension == "meta" }?.forEach { metaFile ->
                try {
                    val meta = parseMetaFile(metaFile)
                    val dataFile = File(dir, "${meta.key}.data")
                    if (dataFile.exists()) {
                        memoryIndex[meta.key] = meta
                        totalBytes += dataFile.length()
                    } else {
                        metaFile.delete()
                    }
                } catch (_: Exception) {
                    metaFile.delete()
                }
            }

            currentTotalSize.set(totalBytes)
            trimToSize()
            Log.d(TAG, "WebViewAssetLruCache initialized: $totalBytes bytes across ${memoryIndex.size} assets.")
        }
    }

    /**
     * Determines whether a given request URL & method should be intercepted & cached.
     */
    fun shouldCache(url: String, method: String = "GET", isMainFrame: Boolean = false): Boolean {
        if (!method.equals("GET", ignoreCase = true)) return false
        if (isMainFrame) return false // Main frame HTML pages change dynamically; skip main frame

        val lowerUrl = url.lowercase()

        // Never cache sensitive auth / login endpoints
        if (KaspaPrivacyEngine.isGoogleAccountOrAuthUrl(lowerUrl) ||
            lowerUrl.contains("/login") || lowerUrl.contains("/auth") ||
            lowerUrl.contains("/oauth") || lowerUrl.contains("/token")
        ) {
            return false
        }

        // Cache static web assets and decentralized node assets (IPFS, KNS, Kaspa nodes, CDNs)
        val path = try { java.net.URI(url).path?.lowercase() ?: "" } catch (_: Exception) { "" }

        val isStaticExtension = path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".mjs") ||
                path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") ||
                path.endsWith(".otf") || path.endsWith(".eot") ||
                path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".webp") || path.endsWith(".svg") || path.endsWith(".ico") ||
                path.endsWith(".gif") || path.endsWith(".avif") || path.endsWith(".json")

        // Never cache WASM or Kaspa API/dynamic nodes to ensure real-time data flows
        if (path.endsWith(".wasm") || lowerUrl.contains("/api/") || lowerUrl.contains("kaspa") ||
            lowerUrl.contains("linktr.ee") || lowerUrl.contains("mykai") || lowerUrl.contains("igralabs")) return false

        val isDecentralizedStaticAsset = lowerUrl.contains("/ipfs/") || lowerUrl.contains("/ipns/") ||
                lowerUrl.contains("/kns/") || lowerUrl.contains(".kas/") ||
                lowerUrl.contains("/static/") || lowerUrl.contains("/assets/")

        return isStaticExtension || isDecentralizedStaticAsset
    }

    /**
     * Attempts to fetch a cached asset from disk. Returns WebResourceResponse if hit, or null if miss.
     */
    fun get(url: String): WebResourceResponse? {
        val dir = cacheDir ?: return null
        val key = CryptoUtils.sha256(url)

        rwLock.read {
            val meta = memoryIndex[key] ?: run {
                missCount.incrementAndGet()
                return null
            }

            val dataFile = File(dir, "$key.data")
            if (!dataFile.exists() || !dataFile.canRead()) {
                memoryIndex.remove(key)
                missCount.incrementAndGet()
                return null
            }

            // Update LRU access time
            meta.lastAccessed = System.currentTimeMillis()
            hitCount.incrementAndGet()

            return try {
                val inputStream = FileInputStream(dataFile)
                
                // CRITICAL: Overwrite MIME type for .wasm and .mjs on cache hit, 
                // in case it was previously cached with a bad MIME type (e.g. application/octet-stream)
                var resolvedMime = meta.mimeType
                val path = try { java.net.URI(url).path?.lowercase() ?: "" } catch (_: Exception) { url.lowercase() }
                if (path.endsWith(".wasm")) {
                    resolvedMime = "application/wasm"
                } else if (path.endsWith(".mjs")) {
                    resolvedMime = "application/javascript"
                }

                val resolvedEncoding = if (resolvedMime == "application/wasm") null else meta.encoding
                val finalHeaders = meta.responseHeaders.toMutableMap()
                finalHeaders.keys.removeAll { it.equals("content-type", ignoreCase = true) }
                finalHeaders["Content-Type"] = if (resolvedEncoding != null) "$resolvedMime; charset=$resolvedEncoding" else resolvedMime

                WebResourceResponse(
                    resolvedMime,
                    resolvedEncoding,
                    200,
                    "OK",
                    finalHeaders,
                    inputStream
                )
            } catch (e: Exception) {
                missCount.incrementAndGet()
                null
            }
        }
    }

    /**
     * Network fetch & cache method for intercepted WebResourceRequests.
     */
    fun fetchAndCache(url: String, requestHeaders: Map<String, String>? = null): WebResourceResponse? {
        val cached = get(url)
        if (cached != null) return cached

        // Network Fetch
        return try {
            val reqBuilder = okhttp3.Request.Builder().url(url)
            requestHeaders?.forEach { (k, v) ->
                if (!k.equals("Host", ignoreCase = true) && !k.equals("Accept-Encoding", ignoreCase = true) && !k.equals("Cookie", ignoreCase = true)) {
                    reqBuilder.addHeader(k, v)
                }
            }
            
            // Inject Webview cookies to bypass Cloudflare Bot Management and other auth gates
            val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
            if (!cookies.isNullOrEmpty()) {
                reqBuilder.addHeader("Cookie", cookies)
            }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val body = response.body ?: run {
                response.close()
                return null
            }

            val bytes = body.bytes()
            val rawContentType = response.header("Content-Type") ?: deduceMimeType(url)
            val (mimeType, encoding) = parseContentType(rawContentType, url)

            val headers = mutableMapOf<String, String>()
            headers["Access-Control-Allow-Origin"] = "*"
            headers["Cache-Control"] = "public, max-age=31536000"
            headers["X-Kaspa-LRU-Cache"] = "HIT"

            response.headers.names().forEach { name ->
                response.header(name)?.let { valStr ->
                    if (!name.equals("content-type", ignoreCase = true)) {
                        headers[name] = valStr
                    }
                }
            }
            val finalEncoding = if (mimeType == "application/wasm") null else encoding
            // CRITICAL: Ensure the Content-Type header matches our corrected mimeType, 
            // because Chromium WebAssembly compiler checks the header map, not just the WebResourceResponse mimeType argument.
            headers["Content-Type"] = if (finalEncoding != null) "$mimeType; charset=$finalEncoding" else mimeType

            // Store in disk LRU cache
            put(url, mimeType, finalEncoding, headers, bytes)

            WebResourceResponse(
                mimeType,
                finalEncoding,
                200,
                "OK",
                headers,
                ByteArrayInputStream(bytes)
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Stores an asset in the disk LRU cache.
     */
    fun put(
        url: String,
        mimeType: String,
        encoding: String?,
        headers: Map<String, String>,
        data: ByteArray
    ) {
        val dir = cacheDir ?: return
        if (data.isEmpty() || data.size > (maxSizeBytes / 4)) return // Skip huge single files > 64MB (at 256MB max)

        val key = CryptoUtils.sha256(url)

        rwLock.write {
            try {
                val dataFile = File(dir, "$key.data")
                val metaFile = File(dir, "$key.meta")

                FileOutputStream(dataFile).use { it.write(data) }

                val meta = CacheMetadata(
                    key = key,
                    url = url,
                    mimeType = mimeType,
                    encoding = encoding,
                    contentLength = data.size.toLong(),
                    lastAccessed = System.currentTimeMillis(),
                    responseHeaders = headers
                )

                writeMetaFile(metaFile, meta)

                val old = memoryIndex.put(key, meta)
                if (old != null) {
                    currentTotalSize.addAndGet(-old.contentLength)
                }
                currentTotalSize.addAndGet(data.size.toLong())

                trimToSize()
            } catch (_: Exception) {}
        }
    }

    /**
     * Evicts least-recently-used items when cache exceeds maxSizeBytes or maxEntries.
     */
    private fun trimToSize() {
        val dir = cacheDir ?: return

        while (memoryIndex.size > maxEntries || currentTotalSize.get() > maxSizeBytes) {
            // Find oldest accessed entry
            val oldest = memoryIndex.values.minByOrNull { it.lastAccessed } ?: break

            memoryIndex.remove(oldest.key)
            currentTotalSize.addAndGet(-oldest.contentLength)
            evictionCount.incrementAndGet()

            File(dir, "${oldest.key}.data").delete()
            File(dir, "${oldest.key}.meta").delete()
        }
    }

    fun clearCache() {
        rwLock.write {
            cacheDir?.listFiles()?.forEach { it.delete() }
            memoryIndex.clear()
            currentTotalSize.set(0)
            hitCount.set(0)
            missCount.set(0)
            evictionCount.set(0)
        }
    }

    // Diagnostics / Audit getters
    fun getHitCount(): Long = hitCount.get()
    fun getMissCount(): Long = missCount.get()
    fun getEvictionCount(): Long = evictionCount.get()
    fun getCurrentSize(): Long = currentTotalSize.get()
    fun getEntryCount(): Int = memoryIndex.size

    private fun writeMetaFile(file: File, meta: CacheMetadata) {
        file.bufferedWriter().use { writer ->
            writer.write("key=${meta.key}\n")
            writer.write("url=${meta.url}\n")
            writer.write("mimeType=${meta.mimeType}\n")
            writer.write("encoding=${meta.encoding ?: ""}\n")
            writer.write("contentLength=${meta.contentLength}\n")
            writer.write("lastAccessed=${meta.lastAccessed}\n")
            meta.responseHeaders.forEach { (k, v) ->
                writer.write("header:$k=$v\n")
            }
        }
    }

    private fun parseMetaFile(file: File): CacheMetadata {
        var key = ""
        var url = ""
        var mimeType = "application/octet-stream"
        var encoding: String? = null
        var contentLength = 0L
        var lastAccessed = 0L
        val headers = mutableMapOf<String, String>()

        file.forEachLine { line ->
            when {
                line.startsWith("key=") -> key = line.removePrefix("key=")
                line.startsWith("url=") -> url = line.removePrefix("url=")
                line.startsWith("mimeType=") -> mimeType = line.removePrefix("mimeType=")
                line.startsWith("encoding=") -> encoding = line.removePrefix("encoding=").ifEmpty { null }
                line.startsWith("contentLength=") -> contentLength = line.removePrefix("contentLength=").toLongOrNull() ?: 0L
                line.startsWith("lastAccessed=") -> lastAccessed = line.removePrefix("lastAccessed=").toLongOrNull() ?: 0L
                line.startsWith("header:") -> {
                    val kv = line.removePrefix("header:").split("=", limit = 2)
                    if (kv.size == 2) {
                        headers[kv[0]] = kv[1]
                    }
                }
            }
        }

        return CacheMetadata(key, url, mimeType, encoding, contentLength, lastAccessed, headers)
    }

    private fun deduceMimeType(url: String): String {
        val path = try { java.net.URI(url).path?.lowercase() ?: "" } catch (_: Exception) { url.lowercase() }
        return when {
            path.endsWith(".css") -> "text/css"
            path.endsWith(".js") || path.endsWith(".mjs") -> "application/javascript"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".ico") -> "image/x-icon"
            path.endsWith(".gif") -> "image/gif"
            path.endsWith(".avif") -> "image/avif"
            path.endsWith(".woff") -> "font/woff"
            path.endsWith(".woff2") -> "font/woff2"
            path.endsWith(".ttf") -> "font/ttf"
            path.endsWith(".otf") -> "font/otf"
            path.endsWith(".wasm") -> "application/wasm"
            path.endsWith(".html") || path.endsWith(".htm") -> "text/html"
            else -> "application/octet-stream"
        }
    }

    private fun parseContentType(contentType: String, url: String): Pair<String, String?> {
        val parts = contentType.split(";").map { it.trim() }
        var mime = parts.firstOrNull()?.ifEmpty { null } ?: deduceMimeType(url)
        
        // CRITICAL: Force strict mime types for specialized web modules like WebAssembly.
        // Many web servers mistakenly serve .wasm files as application/octet-stream, which breaks WebAssembly.instantiateStreaming.
        val path = try { java.net.URI(url).path?.lowercase() ?: "" } catch (_: Exception) { url.lowercase() }
        if (path.endsWith(".wasm")) {
            mime = "application/wasm"
        } else if (path.endsWith(".mjs")) {
            mime = "application/javascript"
        }

        var encoding: String? = null
        for (part in parts.drop(1)) {
            if (part.startsWith("charset=", ignoreCase = true)) {
                encoding = part.substring("charset=".length).trim()
            }
        }
        return Pair(mime, encoding)
    }
}
