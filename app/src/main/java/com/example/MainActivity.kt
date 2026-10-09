package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.DecentralViewModel
import java.io.File

class MainActivity : FragmentActivity() {
  private val viewModel: DecentralViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Global crash handler to protect the app from background renderer, Chromium, and graphic driver crashes
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      Log.e("CrashHandler", "Uncaught exception in thread ${thread.name}", throwable)
      val msg = throwable.message.orEmpty().lowercase()
      val stack = Log.getStackTraceString(throwable).lowercase()
      val isNonFatalInternalError =
          thread.name.contains("Render", ignoreCase = true) ||
          thread.name.contains("Chromium", ignoreCase = true) ||
          thread.name.contains("Chrome", ignoreCase = true) ||
          thread.name.contains("Binder", ignoreCase = true) ||
          thread.name.contains("GLThread", ignoreCase = true) ||
          thread.name.contains("OkHttp", ignoreCase = true) ||
          thread.name.contains("DefaultDispatcher", ignoreCase = true) ||
          msg.contains("rendernode") ||
          msg.contains("render_node") ||
          msg.contains("mesa") ||
          msg.contains("gallium") ||
          msg.contains("dri") ||
          msg.contains("gles2") ||
          msg.contains("texture") ||
          msg.contains("egl") ||
          msg.contains("webview") ||
          msg.contains("deadobjectexception") ||
          msg.contains("supervised") ||
          msg.contains("timeout") ||
          stack.contains("android.webkit") ||
          stack.contains("org.chromium") ||
          stack.contains("superviseduser") ||
          stack.contains("cronet") ||
          stack.contains("cursorwindow") ||
          stack.contains("blobtoobig") ||
          stack.contains("mesa") ||
          stack.contains("rendernode")
      if (isNonFatalInternalError) {
        Log.w("CrashHandler", "Suppressed non-fatal internal WebView/Renderer error on ${thread.name}")
        return@setDefaultUncaughtExceptionHandler
      }
      defaultHandler?.uncaughtException(thread, throwable)
    }

    // Ensure WebView cache directories exist synchronously before UI / WebView initialization
    try {
      val crashpadDir = File(cacheDir, "WebView/Crashpad/attachments")
      if (!crashpadDir.exists()) crashpadDir.mkdirs()
      val wasmCacheDir = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
      if (!wasmCacheDir.exists()) wasmCacheDir.mkdirs()
      val jsCacheDir = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
      if (!jsCacheDir.exists()) jsCacheDir.mkdirs()
    } catch (_: Exception) {}

    enableEdgeToEdge()
    if (checkAndRoutePwaIntent(intent)) {
      finish()
      return
    }
    if (savedInstanceState == null) {
      handleIncomingIntent(intent)
    }

    setContent {
      MyApplicationTheme {
        MainScreen(viewModel = viewModel)
      }
    }

    // Initialize background services asynchronously on IO dispatcher to avoid UI thread startup delay
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val crashpadDir = File(cacheDir, "WebView/Crashpad/attachments")
        if (!crashpadDir.exists()) {
          crashpadDir.mkdirs()
        }
        val wasmCacheDir = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
        if (!wasmCacheDir.exists()) {
          wasmCacheDir.mkdirs()
        }
      } catch (e: Exception) {
        Log.d("MainActivity", "WebView directory notice: ${e.message}")
      }

      try {
        com.example.network.CronetClientFactory.initialize(applicationContext)
        com.example.network.WebViewAssetLruCache.initialize(applicationContext)
        com.example.network.KrpRelayDaemon.start()
        com.example.network.LightweightTorEngine.start()
        com.example.security.PlayIntegrityManager.initialize(applicationContext)
        com.example.util.KaspaWebViewConfigurator.initServiceWorkerSupport()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.example.security.PlayIntegrityManager.requestIntegrityToken(applicationContext)
            } catch (_: Throwable) {}
        }

        val proxyPrefs = getSharedPreferences("kaspa_proxy_prefs", android.content.Context.MODE_PRIVATE)
        if (proxyPrefs.getBoolean("custom_proxy_enabled", false)) {
            val host = proxyPrefs.getString("custom_proxy_host", "")
            val port = proxyPrefs.getString("custom_proxy_port", "")?.toIntOrNull() ?: 0
            val isSocks = proxyPrefs.getBoolean("custom_proxy_is_socks", true)
            if (!host.isNullOrBlank() && port > 0) {
                com.example.network.KaspaPrivacyRelayEngine.setRemoteProxy(host, port, isSocks)
                com.example.network.WebViewProxyManager.applyCustomProxy(host, port, isSocks)
            } else {
                com.example.network.WebViewProxyManager.clearProxy()
            }
        } else {
            // Keep native WebView connection clean and direct without cutting off network access
            com.example.network.WebViewProxyManager.clearProxy()
        }
      } catch (t: Throwable) {
        Log.w("MainActivity", "Network services initialization notice: ${t.message}")
      }

      try {
        android.webkit.WebView.setWebContentsDebuggingEnabled(true)
      } catch (e: Exception) {
        Log.w("MainActivity", "Failed to enable WebView debugging: ${e.message}")
      }
    }
  }

  override fun onStart() {
    super.onStart()
    com.example.util.BrowserStateLog.navigation("MainActivity onStart - preserving state without reload")
  }

  override fun onResume() {
    super.onResume()
    com.example.util.BrowserStateLog.navigation("MainActivity onResume - preserving state without reload")
    viewModel.webViewTabManager.updateActivityContext(this)
  }

  override fun onPause() {
    super.onPause()
    com.example.util.BrowserStateLog.save("MainActivity onPause - saving tabs state")
    viewModel.saveAllTabsState("onPause")
  }

  override fun onStop() {
    super.onStop()
    com.example.util.BrowserStateLog.save("MainActivity onStop - saving tabs state")
    viewModel.saveAllTabsState("onStop")
    // Auto-lock the wallet when app goes to background
    viewModel.lockWallet()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    com.example.util.BrowserStateLog.save("MainActivity onSaveInstanceState - preserving tabs")
    viewModel.saveAllTabsState("onSaveInstanceState")
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingIntent(intent)
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    try {
      if (level >= TRIM_MEMORY_MODERATE) {
        com.example.network.WebViewAssetLruCache.clearCache()
      }
    } catch (_: Exception) {}
  }

  private fun checkAndRoutePwaIntent(intent: Intent?): Boolean {
    if (intent == null) return false
    val isPwa = intent.getBooleanExtra("IS_PWA_MODE", false) ||
        intent.getBooleanExtra("PWA_STANDALONE", false) ||
        intent.action == "com.example.action.LAUNCH_PWA"
    val pwaUrl = intent.getStringExtra("PWA_URL")
        ?: intent.getStringExtra("PWA_START_URL")
        ?: (if (isPwa) intent.dataString else null)
    if (!pwaUrl.isNullOrBlank() && isPwa) {
      Log.d("PWA", "[PWA] MainActivity routing PWA intent: $pwaUrl")
      val pwaIntent = Intent(this, com.example.ui.PwaStandaloneActivity::class.java).apply {
        action = "com.example.action.LAUNCH_PWA"
        data = android.net.Uri.parse(pwaUrl)
        putExtra("PWA_URL", pwaUrl)
        putExtra("PWA_TITLE", intent.getStringExtra("PWA_TITLE") ?: "")
        putExtra("IS_PWA_MODE", true)
        putExtra("PWA_STANDALONE", true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
      }
      startActivity(pwaIntent)
      return true
    }
    return false
  }

  private var lastHandledIntentUrl: String? = null
  private var lastHandledIntentTimestamp: Long = 0L

  private fun sanitizeAndResolveIncomingUrl(intent: Intent?): String? {
    if (intent == null) return null
    val data = intent.data
    val pwaUrl = intent.getStringExtra("PWA_URL")

    // 1. Handle PWA or explicit extras first with validation
    if (!pwaUrl.isNullOrBlank()) {
      val trimmed = pwaUrl.trim()
      if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return trimmed
      }
    }

    if (data != null) {
      val scheme = data.scheme?.lowercase()
      // Block unsafe / local file URI schemes to prevent exposing sensitive local file paths
      if (scheme == "file" || scheme == "content" || scheme == "javascript" || scheme == "data") {
        Log.w("MainActivity", "Blocked unsafe local file or script URI scheme: $scheme")
        return null
      }


      val dataString = intent.dataString
      if (!dataString.isNullOrBlank()) {
        val trimmed = dataString.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
          return trimmed
        }
      }
    }

    // Fallback: check text extras or query
    val textExtra = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra("query")
    if (!textExtra.isNullOrBlank()) {
      val trimmed = textExtra.trim()
      return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
      } else {
        "https://html.duckduckgo.com/html/?q=${android.net.Uri.encode(trimmed)}"
      }
    }

    return null
  }

  private fun handleIncomingIntent(intent: Intent?) {
    if (intent == null) return

    // 1. If launched from recent apps history after being away for a long time,
    // Android re-delivers the original launch intent with FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY.
    // We MUST ignore this replayed intent to prevent reopening the previous external link!
    if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
      Log.d("MainActivity", "Launched from history - ignoring replayed intent")
      setIntent(Intent(this, MainActivity::class.java).apply { action = Intent.ACTION_MAIN })
      return
    }

    // 2. If launched normally from the Android launcher icon (ACTION_MAIN + CATEGORY_LAUNCHER),
    // it is a direct user launch to open the browser home, not an external link navigation.
    if (Intent.ACTION_MAIN == intent.action && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
      Log.d("MainActivity", "Launched from app launcher - regular start")
      setIntent(Intent(this, MainActivity::class.java).apply { action = Intent.ACTION_MAIN })
      return
    }

    if (checkAndRoutePwaIntent(intent)) {
      return
    }

    val targetUrl = sanitizeAndResolveIncomingUrl(intent)

    if (!targetUrl.isNullOrBlank()) {
      Log.d("PWA", "[PWA] MainActivity handling sanitized external intent URL: $targetUrl")
      viewModel.setPendingExplicitUrl(targetUrl)
      val now = System.currentTimeMillis()
      if (targetUrl != lastHandledIntentUrl || (now - lastHandledIntentTimestamp > 1500L)) {
        lastHandledIntentUrl = targetUrl
        lastHandledIntentTimestamp = now
        viewModel.openUrlInBrowser(targetUrl, isExternal = true, isStandalonePwa = false)
      }
      // Replace the Activity's current intent so that future onResume / history events do not replay it
      setIntent(Intent(this, MainActivity::class.java).apply { action = Intent.ACTION_MAIN })
    }
  }
}

