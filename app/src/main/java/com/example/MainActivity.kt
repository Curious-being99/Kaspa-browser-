package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.DecentralViewModel
import java.io.File

class MainActivity : ComponentActivity() {
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

    enableEdgeToEdge()

    // Pre-create and sanitize WebView cache & code-cache directories to prevent Chromium opendir and index errors
    try {
      val webViewCacheDir = File(cacheDir, "WebView/Default")
      val httpCache = File(webViewCacheDir, "HTTP Cache")
      val codeCacheHttp = File(httpCache, "Code Cache")
      val jsHttpDir = File(codeCacheHttp, "js")
      val wasmHttpDir = File(codeCacheHttp, "wasm")
      val defaultCodeCache = File(webViewCacheDir, "Code Cache")
      val jsDefaultDir = File(defaultCodeCache, "js")
      val wasmDefaultDir = File(defaultCodeCache, "wasm")

      jsHttpDir.mkdirs()
      wasmHttpDir.mkdirs()
      jsDefaultDir.mkdirs()
      wasmDefaultDir.mkdirs()
      File(httpCache, "index-dir").mkdirs()
      File(cacheDir, "WebView/Crashpad/attachments").mkdirs()

      // Clean up orphaned or broken zero-length temp cache entries that cause SimpleCache index reconstruction failures
      val cleanupDirs = listOf(jsHttpDir, wasmHttpDir, jsDefaultDir, wasmDefaultDir)
      for (dir in cleanupDirs) {
        if (dir.exists() && dir.isDirectory) {
          dir.listFiles()?.forEach { file ->
            try {
              if (!file.canRead() || file.length() == 0L) {
                file.delete()
              }
            } catch (_: Exception) {}
          }
        }
      }
    } catch (e: Exception) {
      Log.w("MainActivity", "WebView directory init: ${e.message}")
    }

    // Initialize Cronet Engine safely
    try {
      com.example.network.CronetClientFactory.initialize(applicationContext)
    } catch (e: Exception) {
      Log.w("MainActivity", "Failed to initialize Cronet: ${e.message}")
    }

    handleIncomingIntent(intent)

    setContent {
      MyApplicationTheme {
        MainScreen(viewModel = viewModel)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingIntent(intent)
  }

  private fun handleIncomingIntent(intent: Intent?) {
    val pwaUrl = intent?.getStringExtra("PWA_URL") ?: intent?.dataString
    if (!pwaUrl.isNullOrBlank()) {
      viewModel.openUrlInBrowser(pwaUrl)
    }
  }
}

