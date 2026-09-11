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

    // Global crash handler to protect the app from background renderer / thread crashes
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      Log.e("CrashHandler", "Uncaught exception in thread ${thread.name}", throwable)
      if (thread.name.contains("Render") || thread.name.contains("Chromium") || thread.name.contains("Binder")) {
        // Prevent background graphics/web crashes from terminating the app
        return@setDefaultUncaughtExceptionHandler
      }
      defaultHandler?.uncaughtException(thread, throwable)
    }

    enableEdgeToEdge()

    // Clean up stale or corrupted 0-byte code cache files to prevent Chromium SimpleFileEnumerator warnings (Trigger New APK Deploy)
    try {
      val codeCacheDir = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache")
      if (codeCacheDir.exists()) {
        codeCacheDir.walkTopDown().forEach { file ->
          if (file.isFile && (!file.canRead() || file.length() == 0L)) {
            try { file.delete() } catch (_: Exception) {}
          }
        }
      } else {
        codeCacheDir.mkdirs()
      }
      val jsDir = File(codeCacheDir, "js")
      if (!jsDir.exists()) jsDir.mkdirs()
      val wasmDir = File(codeCacheDir, "wasm")
      if (!wasmDir.exists()) wasmDir.mkdirs()
    } catch (_: Exception) {}

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

