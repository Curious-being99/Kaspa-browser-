package com.example.network

import android.util.Log

/**
 * Kotlin JNI Wrapper for the Rust 'kaspasearch' native library.
 * 
 * Exposes compiled Rust search engine functions to the Kotlin application layer,
 * enabling fast, zero-tracking, on-device query execution and privacy URL sanitization.
 */
object SearchEngine {

    private const val TAG = "SearchEngineJNI"
    private var isNativeLibraryLoaded = false

    init {
        try {
            System.loadLibrary("kaspasearch")
            isNativeLibraryLoaded = true
            Log.i(TAG, "Successfully loaded native 'kaspasearch' library.")
        } catch (e: UnsatisfiedLinkError) {
            isNativeLibraryLoaded = false
            Log.w(TAG, "Native 'kaspasearch' library not loaded. Falling back to EmbeddedRustSearchEngine engine.")
        } catch (e: Exception) {
            isNativeLibraryLoaded = false
            Log.e(TAG, "Error loading 'kaspasearch' native library", e)
        }
    }

    /**
     * Native JNI method to execute a privacy-first web search query using the Rust federated engine.
     */
    private external fun nativeSearch(query: String): String

    /**
     * Native JNI method to sanitize tracking parameters (UTM, fbclid, gclid) from URLs in Rust.
     */
    private external fun nativeSanitizeUrl(rawUrl: String): String

    /**
     * Native JNI method to pre-filter tracking scripts, ads, and telemetry from HTML AST in Rust v9.
     */
    private external fun nativeAstFilter(rawHtml: String): String

    /**
     * Checks if the native Rust library is successfully loaded.
     */
    fun isNativeAvailable(): Boolean = isNativeLibraryLoaded

    /**
     * Invokes the Rust search engine or delegates to the on-device Kotlin fallback handler.
     */
    suspend fun executeSearch(query: String): List<EmbeddedRustSearchEngine.SearchResult> {
        if (query.isBlank()) return emptyList()

        if (isNativeLibraryLoaded) {
            return try {
                val jsonResult = nativeSearch(query)
                parseResults(jsonResult)
            } catch (e: Exception) {
                Log.e(TAG, "Native search execution failed, falling back to embedded handler", e)
                EmbeddedRustSearchEngine.searchOnDevice(query)
            }
        }

        return EmbeddedRustSearchEngine.searchOnDevice(query)
    }

    /**
     * Sanitizes tracking tokens from a URL using Rust native code if available.
     */
    fun sanitizeUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return rawUrl

        if (isNativeLibraryLoaded) {
            try {
                return nativeSanitizeUrl(rawUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Native URL sanitization failed", e)
            }
        }

        return EmbeddedRustSearchEngine.sanitizeUrl(rawUrl)
    }

    /**
     * Filters HTML AST in native Rust v9 to strip telemetry and ads before layout computation.
     */
    fun filterHtmlAst(rawHtml: String): String {
        if (rawHtml.isBlank()) return rawHtml

        if (isNativeLibraryLoaded) {
            try {
                return nativeAstFilter(rawHtml)
            } catch (e: Exception) {
                Log.e(TAG, "Native AST filter execution failed", e)
            }
        }

        return EmbeddedRustSearchEngine.filterHtmlAst(rawHtml)
    }

    private fun parseResults(jsonStr: String): List<EmbeddedRustSearchEngine.SearchResult> {
        val list = mutableListOf<EmbeddedRustSearchEngine.SearchResult>()
        try {
            val jsonArray = org.json.JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    EmbeddedRustSearchEngine.SearchResult(
                        title = obj.optString("title", "Search Result"),
                        url = obj.optString("url"),
                        snippet = obj.optString("snippet"),
                        engineSource = obj.optString("engine_source", "Kaspa Engine"),
                        isSecure = obj.optBoolean("is_verified_secure", true)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing search results JSON", e)
        }
        return list
    }
}
