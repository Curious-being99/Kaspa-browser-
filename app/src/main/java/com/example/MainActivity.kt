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
    handleIncomingIntent(intent)

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
        com.example.util.KaspaWebViewConfigurator.initServiceWorkerSupport()
      } catch (e: Exception) {
        Log.w("MainActivity", "Failed to initialize network services: ${e.message}")
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

  private fun handleIncomingIntent(intent: Intent?) {
    if (checkAndRoutePwaIntent(intent)) {
      return
    }
    val targetUrl = intent?.getStringExtra("PWA_URL") ?: intent?.dataString
    if (!targetUrl.isNullOrBlank()) {
      Log.d("PWA", "[PWA] MainActivity handling external VIEW intent: $targetUrl")
      viewModel.setPendingExplicitUrl(targetUrl)
      val now = System.currentTimeMillis()
      if (targetUrl != lastHandledIntentUrl || (now - lastHandledIntentTimestamp > 1500L)) {
        lastHandledIntentUrl = targetUrl
        lastHandledIntentTimestamp = now
        viewModel.openUrlInBrowser(targetUrl, isExternal = true, isStandalonePwa = false)
      }
      // Consume the intent data so leaving and returning to the task does not replay the intent
      try {
        intent?.data = null
        intent?.removeExtra("PWA_URL")
        intent?.removeExtra("IS_PWA_MODE")
        intent?.removeExtra("PWA_STANDALONE")
      } catch (_: Exception) {}
    }
  }
}

