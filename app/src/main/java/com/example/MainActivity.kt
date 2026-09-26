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
          stack.contains("android.webkit") ||
          stack.contains("org.chromium") ||
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

  private var lastHandledIntentUrl: String? = null
  private var lastHandledIntentTimestamp: Long = 0L

  private fun handleIncomingIntent(intent: Intent?) {
    val pwaUrl = intent?.getStringExtra("PWA_URL") ?: intent?.dataString
    if (!pwaUrl.isNullOrBlank()) {
      val now = System.currentTimeMillis()
      if (pwaUrl != lastHandledIntentUrl || (now - lastHandledIntentTimestamp > 1500L)) {
        lastHandledIntentUrl = pwaUrl
        lastHandledIntentTimestamp = now
        viewModel.openUrlInBrowser(pwaUrl, isExternal = true)
      }
      // Consume the intent data so leaving and returning to the task does not replay the intent
      try {
        intent?.data = null
        intent?.removeExtra("PWA_URL")
      } catch (_: Exception) {}
    }
  }
}

