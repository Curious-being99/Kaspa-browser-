package com.example.util

import android.content.Context
import android.content.MutableContextWrapper
import android.os.Bundle
import android.os.Parcel
import android.view.ViewGroup
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class BrowserTabWebViewManager {
    private val webViewMap = ConcurrentHashMap<String, WebView>()
    private val tabScrollMap = ConcurrentHashMap<String, Pair<Int, Int>>() // tabId -> (scrollX, scrollY)
    @Volatile
    private var currentActivityContext: Context? = null

    fun updateActivityContext(context: Context) {
        currentActivityContext = context
        webViewMap.values.forEach { webView ->
            try {
                (webView.context as? MutableContextWrapper)?.baseContext = context
            } catch (e: Exception) {
                android.util.Log.w("BrowserTabWebViewManager", "Notice updating context: ${e.message}")
            }
        }
    }

    fun hasWebView(tabId: String): Boolean = webViewMap.containsKey(tabId)

    fun getWebView(tabId: String): WebView? = webViewMap[tabId]

    fun getAllWebViews(): Map<String, WebView> = webViewMap.toMap()

    fun getOrCreateWebView(
        tabId: String,
        context: Context,
        savedStateBundle: Bundle? = null,
        configurator: (WebView) -> Unit
    ): Pair<WebView, Boolean> {
        updateActivityContext(context)
        val existing = webViewMap[tabId]
        if (existing != null) {
            return Pair(existing, false)
        }

        BrowserStateLog.webViewCreated("Creating new preserved WebView for tab $tabId")
        val wrapper = MutableContextWrapper(context)
        val newWebView = com.example.ui.NestedWebView(wrapper)
        configurator(newWebView)

        if (savedStateBundle != null) {
            try {
                val restored = newWebView.restoreState(savedStateBundle)
                if (restored != null && restored.size > 0 && !newWebView.url.isNullOrBlank()) {
                    BrowserStateLog.restore("Restored WebView state from bundle for tab $tabId (url: ${newWebView.url}, items: ${restored.size})")
                } else {
                    BrowserStateLog.restore("restoreState returned empty or null for tab $tabId")
                }
            } catch (e: Exception) {
                android.util.Log.w("BrowserTabWebViewManager", "Failed to restoreState for tab $tabId: ${e.message}")
            }
        }

        webViewMap[tabId] = newWebView
        return Pair(newWebView, true)
    }

    fun saveTabScroll(tabId: String, scrollX: Int, scrollY: Int) {
        tabScrollMap[tabId] = Pair(scrollX, scrollY)
    }

    fun getTabScroll(tabId: String): Pair<Int, Int>? = tabScrollMap[tabId]

    fun saveTabBundle(tabId: String): Bundle? {
        val wv = webViewMap[tabId] ?: return null
        return try {
            val bundle = Bundle()
            wv.saveState(bundle)
            BrowserStateLog.save("Captured saveState bundle for tab $tabId")
            bundle
        } catch (e: Exception) {
            android.util.Log.w("BrowserTabWebViewManager", "Error saving bundle for tab $tabId: ${e.message}")
            null
        }
    }

    fun saveAllTabsBundle(): Map<String, Bundle> {
        val result = mutableMapOf<String, Bundle>()
        webViewMap.forEach { (tabId, wv) ->
            try {
                val b = Bundle()
                wv.saveState(b)
                result[tabId] = b
            } catch (e: Exception) {
                android.util.Log.w("BrowserTabWebViewManager", "Error saving state for tab $tabId: ${e.message}")
            }
        }
        BrowserStateLog.save("Saved bundles for all ${result.size} active WebViews")
        return result
    }

    fun extractHistoryJson(tabId: String): String {
        val wv = webViewMap[tabId] ?: return "[]"
        return try {
            val historyList = wv.copyBackForwardList()
            val size = historyList.size
            val currentIndex = historyList.currentIndex
            val items = JSONArray()
            for (i in 0 until size) {
                val item = historyList.getItemAtIndex(i)
                val obj = JSONObject()
                obj.put("url", item.url)
                obj.put("title", item.title)
                obj.put("originalUrl", item.originalUrl)
                obj.put("isCurrent", i == currentIndex)
                items.put(obj)
            }
            val root = JSONObject()
            root.put("currentIndex", currentIndex)
            root.put("items", items)
            root.toString()
        } catch (e: Exception) {
            "[]"
        }
    }

    fun destroyTab(tabId: String, reason: String = "tab closed") {
        val wv = webViewMap.remove(tabId) ?: return
        tabScrollMap.remove(tabId)
        BrowserStateLog.webViewDestroyed("$reason: tab $tabId")
        try {
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.stopLoading()
            wv.clearHistory()
            wv.destroy()
        } catch (e: Exception) {
            android.util.Log.w("BrowserTabWebViewManager", "Error destroying webView for $tabId: ${e.message}")
        }
    }

    fun destroyAll(reason: String = "app shutdown") {
        webViewMap.keys.toList().forEach { tabId ->
            destroyTab(tabId, reason)
        }
    }

    companion object {
        fun bundleToByteArray(bundle: Bundle?): ByteArray? {
            if (bundle == null) return null
            val parcel = Parcel.obtain()
            return try {
                bundle.writeToParcel(parcel, 0)
                parcel.marshall()
            } catch (e: Exception) {
                android.util.Log.w("BrowserTabWebViewManager", "Failed to marshall bundle: ${e.message}")
                null
            } finally {
                parcel.recycle()
            }
        }

        fun byteArrayToBundle(bytes: ByteArray?): Bundle? {
            if (bytes == null || bytes.isEmpty()) return null
            val parcel = Parcel.obtain()
            return try {
                parcel.unmarshall(bytes, 0, bytes.size)
                parcel.setDataPosition(0)
                val bundle = Bundle()
                bundle.readFromParcel(parcel)
                bundle
            } catch (e: Exception) {
                android.util.Log.w("BrowserTabWebViewManager", "Failed to deserialize bundle: ${e.message}")
                null
            } finally {
                parcel.recycle()
            }
        }

        fun isSameUrl(url1: String?, url2: String?): Boolean {
            if (url1.isNullOrBlank() || url2.isNullOrBlank()) return false
            if (url1 == url2) return true
            val clean1 = url1.trim().trimEnd('/')
            val clean2 = url2.trim().trimEnd('/')
            if (clean1 == clean2) return true
            val noProto1 = clean1.removePrefix("https://").removePrefix("http://")
            val noProto2 = clean2.removePrefix("https://").removePrefix("http://")
            return noProto1 == noProto2
        }
    }
}
