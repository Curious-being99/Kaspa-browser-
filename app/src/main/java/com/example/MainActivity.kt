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
    // Configure system properties to inform Mesa DRI compositor to use software rendering when hardware rendernodes are missing in virtual container
    try {
      System.setProperty("libgl_always_software", "true")
      System.setProperty("GALLIUM_DRIVER", "softpipe")
      System.setProperty("MESA_LOADER_DRIVER_OVERRIDE", "swrast")
    } catch (_: Exception) {}

    super.onCreate(savedInstanceState)

    // Global crash handler to protect the app from background renderer / thread crashes
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      Log.e("CrashHandler", "Uncaught exception in thread ${thread.name}", throwable)
      if (thread.name.contains("Render", ignoreCase = true) ||
          thread.name.contains("Chromium", ignoreCase = true) ||
          thread.name.contains("Chrome", ignoreCase = true) ||
          thread.name.contains("Binder", ignoreCase = true) ||
          thread.name.contains("GLThread", ignoreCase = true) ||
          throwable.message?.contains("rendernode", ignoreCase = true) == true ||
          throwable.message?.contains("gles2", ignoreCase = true) == true ||
          throwable.message?.contains("texture", ignoreCase = true) == true) {
        // Prevent background graphics/Mesa/Chromium/WebGL container warnings from interrupting the app
        return@setDefaultUncaughtExceptionHandler
      }
      defaultHandler?.uncaughtException(thread, throwable)
    }

    enableEdgeToEdge()

    // Initialize Cronet Engine with true QUIC transport capabilities
    com.example.network.CronetClientFactory.initialize(applicationContext)

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

