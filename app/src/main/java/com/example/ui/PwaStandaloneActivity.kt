package com.example.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.MainActivity
import com.example.network.KaspaPrivacyEngine
import com.example.network.NativeIntentRoutingEngine
import com.example.network.WebViewAssetLruCache
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMesh
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

/**
 * Dedicated Standalone Web Application (PWA / WebAPK) Activity.
 *
 * Implements the architecture used by Google Chrome and Brave:
 * 1. Independent Task Space: Runs with `FLAG_ACTIVITY_NEW_DOCUMENT` in its own separate
 *    window in Android's Recents/Multitasking overview screen.
 * 2. Standalone Viewport: 100% full screen web application window with ZERO browser bars,
 *    zero Omnibar, zero tab count, zero bottom navigation bar.
 * 3. Native Integration: Full GPU acceleration, zero-copy mmap asset caching,
 *    safe pull-to-refresh overscroll physics, and hardware back-stack integration.
 */
class PwaStandaloneActivity : FragmentActivity() {

    companion object {
        private const val TAG = "PwaStandaloneActivity"
    }

    private var targetUrl: String = ""
    private var appTitle: String = ""
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Global crash handler to protect background renderers
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stack = Log.getStackTraceString(throwable).lowercase()
            val isNonFatal = thread.name.contains("Render", ignoreCase = true) ||
                    thread.name.contains("Chromium", ignoreCase = true) ||
                    stack.contains("android.webkit") ||
                    stack.contains("org.chromium")
            if (isNonFatal) {
                Log.w(TAG, "Suppressed non-fatal renderer crash in PWA: ${throwable.message}")
                return@setDefaultUncaughtExceptionHandler
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        extractIntentData(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView?.canGoBack() == true) {
                    webView?.goBack()
                } else {
                    finish()
                }
            }
        })

        setContent {
            MyApplicationTheme {
                PwaStandaloneScreen(
                    initialUrl = targetUrl,
                    initialTitle = appTitle,
                    onOpenInBrowser = { url ->
                        val browserIntent = Intent(this@PwaStandaloneActivity, MainActivity::class.java).apply {
                            action = Intent.ACTION_VIEW
                            data = Uri.parse(url)
                            putExtra("PWA_URL", url)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(browserIntent)
                    },
                    onCloseApp = { finish() },
                    onWebViewCreated = { wv -> webView = wv }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIntentData(intent)
        if (targetUrl.isNotBlank()) {
            webView?.loadUrl(targetUrl)
        }
    }

    private fun extractIntentData(intent: Intent?) {
        targetUrl = intent?.getStringExtra("PWA_URL")
            ?: intent?.dataString
            ?: "https://kaspa.org"
        appTitle = intent?.getStringExtra("PWA_TITLE")
            ?: try { Uri.parse(targetUrl).host ?: "Web App" } catch (_: Exception) { "Web App" }
    }

    override fun onDestroy() {
        try {
            webView?.stopLoading()
            (webView?.parent as? ViewGroup)?.removeView(webView)
            webView?.destroy()
            webView = null
        } catch (_: Exception) {}
        super.onDestroy()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PwaStandaloneScreen(
    initialUrl: String,
    initialTitle: String,
    onOpenInBrowser: (String) -> Unit,
    onCloseApp: () -> Unit,
    onWebViewCreated: (WebView) -> Unit
) {
    val context = LocalContext.current
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf(initialTitle) }
    var isLoading by remember { mutableStateOf(true) }
    var webProgress by remember { mutableFloatStateOf(0.1f) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showMinimalControls by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Fullscreen standalone web view
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val density = ctx.resources.displayMetrics.density
                val swipeLayout = SwipeRefreshLayout(ctx).apply {
                    setColorSchemeColors(
                        android.graphics.Color.parseColor("#00E5FF"),
                        android.graphics.Color.parseColor("#10B981")
                    )
                    setProgressBackgroundColorSchemeColor(android.graphics.Color.parseColor("#121824"))
                    setProgressViewOffset(true, -(64 * density).toInt(), (56 * density).toInt())
                    setDistanceToTriggerSync((110 * density).toInt())
                }

                val wv = WebView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    isHapticFeedbackEnabled = true
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, false)
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        userAgentString = KaspaPrivacyEngine.getMobileUserAgent()
                    }

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            url?.let { currentUrl = it }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            isRefreshing = false
                            swipeLayout.isRefreshing = false
                            url?.let { currentUrl = it }
                            view?.title?.let { if (it.isNotBlank()) pageTitle = it }
                        }

                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: return null
                            if (WebViewAssetLruCache.shouldCache(reqUrl, request.method, request.isForMainFrame)) {
                                val cached = WebViewAssetLruCache.get(reqUrl)
                                if (cached != null) return cached
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            val hasGesture = request?.hasGesture() ?: false
                            return NativeIntentRoutingEngine.routeUrl(ctx, url, hasGesture) { fallback ->
                                view?.loadUrl(fallback)
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            webProgress = (newProgress / 100f).coerceIn(0f, 1f)
                            if (newProgress >= 100) {
                                isLoading = false
                                isRefreshing = false
                                swipeLayout.isRefreshing = false
                            }
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            super.onReceivedTitle(view, title)
                            title?.let { if (it.isNotBlank()) pageTitle = it }
                        }
                    }

                    loadUrl(initialUrl)
                }

                swipeLayout.setOnRefreshListener {
                    isRefreshing = true
                    wv.reload()
                }

                swipeLayout.addView(wv)
                onWebViewCreated(wv)
                swipeLayout
            }
        )

        // Loading Progress Bar at the top
        AnimatedVisibility(
            visible = isLoading && webProgress < 1f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            LinearProgressIndicator(
                progress = { webProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = ElectricCyan,
                trackColor = Color.Transparent
            )
        }

        // Minimalist Standalone Overlay Button (Top-Right subtle icon for quick controls)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = SurfaceDark.copy(alpha = 0.75f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier.size(32.dp)
            ) {
                IconButton(
                    onClick = { showMinimalControls = !showMinimalControls },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = if (showMinimalControls) Icons.Default.Close else Icons.Default.OpenInBrowser,
                        contentDescription = "PWA Options",
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = showMinimalControls,
                onDismissRequest = { showMinimalControls = false },
                modifier = Modifier.background(SurfaceDark)
            ) {
                DropdownMenuItem(
                    text = { Text("Open in Browser Tabs", color = TextPrimary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMinimalControls = false
                        onOpenInBrowser(currentUrl)
                        onCloseApp()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Reload Web App", color = TextPrimary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = EmeraldMesh, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMinimalControls = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Exit App", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMinimalControls = false
                        onCloseApp()
                    }
                )
            }
        }
    }
}
