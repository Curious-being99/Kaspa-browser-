package com.example.util

import android.app.ActivityManager
import android.content.Context
import android.content.MutableContextWrapper
import android.os.Bundle
import android.os.Debug
import android.os.Process
import android.os.Parcel
import android.view.ViewGroup
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class BrowserTabWebViewManager {
    private val webViewMap = ConcurrentHashMap<String, WebView>()
    private val tabScrollMap = ConcurrentHashMap<String, Pair<Int, Int>>() // tabId -> (scrollX, scrollY)
    private val tabLastActiveAt = ConcurrentHashMap<String, Long>()
    private val frozenTabIds = ConcurrentHashMap.newKeySet<String>()
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

    /**
     * Captures a point-in-time memory snapshot for device profiling.
     *
     * WebView renderers commonly run in isolated processes, so app PSS and renderer
     * PSS are reported separately. A zero renderer count means Android did not expose
     * a matching renderer process at the time of sampling; it is not proof of zero use.
     */
    @Suppress("DEPRECATION")
    fun memorySnapshot(context: Context): BrowserMemorySnapshot {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appPssKb = activityManager.getProcessMemoryInfo(intArrayOf(Process.myPid()))
            .firstOrNull()?.totalPss ?: 0

        val rendererProcesses = activityManager.runningAppProcesses.orEmpty().filter { process ->
            process.processName.startsWith("${context.packageName}:") &&
                (process.processName.contains("sandboxed_process", ignoreCase = true) ||
                    process.processName.contains("privileged_process", ignoreCase = true))
        }
        val rendererPssKb = if (rendererProcesses.isEmpty()) {
            0
        } else {
            activityManager.getProcessMemoryInfo(rendererProcesses.map { it.pid }.toIntArray())
                .sumOf { it.totalPss }
        }

        val views = webViewMap.values.toList()
        return BrowserMemorySnapshot(
            timestampMs = System.currentTimeMillis(),
            liveWebViewCount = views.size,
            attachedWebViewCount = views.count { it.parent != null },
            appPssKb = appPssKb,
            rendererProcessCount = rendererProcesses.size,
            rendererPssKb = rendererPssKb,
            javaHeapUsedBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
            nativeHeapAllocatedBytes = Debug.getNativeHeapAllocatedSize()
        )
    }

    fun logMemorySnapshot(context: Context, reason: String): BrowserMemorySnapshot {
        val snapshot = memorySnapshot(context)
        android.util.Log.i(
            "BrowserMemory",
            "reason=$reason liveWebViews=${snapshot.liveWebViewCount} " +
                "attachedWebViews=${snapshot.attachedWebViewCount} " +
                "appPssKb=${snapshot.appPssKb} rendererProcesses=${snapshot.rendererProcessCount} " +
                "rendererPssKb=${snapshot.rendererPssKb} javaHeapBytes=${snapshot.javaHeapUsedBytes} " +
                "nativeHeapBytes=${snapshot.nativeHeapAllocatedBytes}"
        )
        return snapshot
    }

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
        tabLastActiveAt[tabId] = System.currentTimeMillis()
        frozenTabIds.remove(tabId)
        com.example.engine.BrowserEngineManager.recordTabLifecycleStats(frozenTabIds.size)
        return Pair(newWebView, true)
    }


    fun pauseTab(tabId: String) {
        val wv = webViewMap[tabId] ?: return
        wv.onPause()
        frozenTabIds.add(tabId)
        com.example.engine.BrowserEngineManager.recordTabLifecycleStats(frozenTabIds.size)
    }

    fun resumeTab(tabId: String) {
        val wv = webViewMap[tabId]
        wv?.onResume()
        tabLastActiveAt[tabId] = System.currentTimeMillis()
        frozenTabIds.remove(tabId)
        com.example.engine.BrowserEngineManager.recordTabLifecycleStats(frozenTabIds.size)
    }

    fun activateTab(tabId: String) {
        resumeTab(tabId)
    }

    fun getFrozenTabCount(): Int = frozenTabIds.size

    /**
     * Kazview Cold-Tab Memory Guard: when live WebViews exceed maxLiveWebViews, cleanly
     * captures state + scroll + history for the oldest inactive background tabs and destroys
     * only those cold WebViews so memory stays lean without losing user session state.
     */
    fun hibernateColdTabsIfNeeded(
        activeTabId: String?,
        maxLiveWebViews: Int = 6,
        onCaptureState: (tabId: String, bundle: Bundle?, scroll: Pair<Int, Int>?, historyJson: String) -> Unit
    ): Int {
        if (maxLiveWebViews < 1 || webViewMap.size <= maxLiveWebViews) return 0
        val candidates = webViewMap.keys
            .filter { it != activeTabId }
            .sortedBy { tabLastActiveAt[it] ?: 0L }
        val excessCount = webViewMap.size - maxLiveWebViews
        val toHibernate = candidates.take(excessCount)
        var hibernated = 0
        for (coldId in toHibernate) {
            val bundle = saveTabBundle(coldId)
            val scroll = getTabScroll(coldId)
            val historyJson = extractHistoryJson(coldId)
            try {
                onCaptureState(coldId, bundle, scroll, historyJson)
            } catch (_: Throwable) {}
            destroyTab(coldId, "Kazview cold-tab hibernation")
            hibernated++
        }
        if (hibernated > 0) {
            com.example.engine.BrowserEngineManager.recordTabLifecycleStats(frozenTabIds.size, hibernated)
        }
        return hibernated
    }

    fun saveTabScroll(tabId: String, scrollX: Int, scrollY: Int) {
        tabScrollMap[tabId] = Pair(scrollX, scrollY)
    }

    fun getTabScroll(tabId: String): Pair<Int, Int>? = tabScrollMap[tabId]

    fun saveTabBundle(tabId: String): Bundle? {
        val wv = webViewMap[tabId] ?: return null
        return if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            try {
                val bundle = Bundle()
                com.example.engine.BrowserEngineManager.saveWebViewStateBounded(wv, bundle)
                BrowserStateLog.save("Captured bounded saveState bundle for tab $tabId")
                bundle
            } catch (e: Exception) {
                android.util.Log.w("BrowserTabWebViewManager", "Error saving bundle for tab $tabId: ${e.message}")
                null
            }
        } else {
            var bundle: Bundle? = null
            val latch = java.util.concurrent.CountDownLatch(1)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    val b = Bundle()
                    com.example.engine.BrowserEngineManager.saveWebViewStateBounded(wv, b)
                    bundle = b
                } catch (e: Exception) {
                    android.util.Log.w("BrowserTabWebViewManager", "Error saving bundle on main thread: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
            try {
                latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: Exception) {}
            bundle
        }
    }

    fun saveAllTabsBundle(): Map<String, Bundle> {
        val result = mutableMapOf<String, Bundle>()
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            webViewMap.forEach { (tabId, wv) ->
                try {
                    val b = Bundle()
                    com.example.engine.BrowserEngineManager.saveWebViewStateBounded(wv, b)
                    result[tabId] = b
                } catch (e: Exception) {
                    android.util.Log.w("BrowserTabWebViewManager", "Error saving state for tab $tabId: ${e.message}")
                }
            }
        } else {
            val latch = java.util.concurrent.CountDownLatch(1)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    webViewMap.forEach { (tabId, wv) ->
                        try {
                            val b = Bundle()
                            com.example.engine.BrowserEngineManager.saveWebViewStateBounded(wv, b)
                            result[tabId] = b
                        } catch (_: Exception) {}
                    }
                } finally {
                    latch.countDown()
                }
            }
            try {
                latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: Exception) {}
        }
        BrowserStateLog.save("Saved bounded bundles for all ${result.size} active WebViews")
        return result
    }

    private fun extractHistoryInternal(wv: WebView): String {
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

    fun extractHistoryJson(tabId: String): String {
        val wv = webViewMap[tabId] ?: return "[]"
        return if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            extractHistoryInternal(wv)
        } else {
            var history = "[]"
            val latch = java.util.concurrent.CountDownLatch(1)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    history = extractHistoryInternal(wv)
                } catch (_: Exception) {}
                finally {
                    latch.countDown()
                }
            }
            try {
                latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: Exception) {}
            history
        }
    }

    fun destroyTab(tabId: String, reason: String = "tab closed") {
        val wv = webViewMap.remove(tabId) ?: return
        tabScrollMap.remove(tabId)
        tabLastActiveAt.remove(tabId)
        frozenTabIds.remove(tabId)
        com.example.engine.BrowserEngineManager.recordTabLifecycleStats(frozenTabIds.size)
        BrowserStateLog.webViewDestroyed("$reason: tab $tabId")
        val task = Runnable {
            try {
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.stopLoading()
                wv.clearHistory()
                wv.destroy()
            } catch (e: Exception) {
                android.util.Log.w("BrowserTabWebViewManager", "Error destroying webView for $tabId: ${e.message}")
            }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            task.run()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post(task)
        }
    }

    fun destroyAll(reason: String = "app shutdown") {
        val allTabs = webViewMap.keys.toList()
        allTabs.forEach { tabId ->
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
            if (noProto1 == noProto2) return true
            val noWww1 = noProto1.removePrefix("www.")
            val noWww2 = noProto2.removePrefix("www.")
            if (noWww1 == noWww2) return true
            val base1 = noWww1.substringBefore("?")
            val base2 = noWww2.substringBefore("?")
            return base1 == base2
        }
    }
}
