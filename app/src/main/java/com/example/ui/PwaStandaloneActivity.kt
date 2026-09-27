package com.example.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.MainActivity
import com.example.network.KaspaPrivacyEngine
import com.example.network.NativeIntentRoutingEngine
import com.example.network.WebViewAssetLruCache
import com.example.ui.theme.*
import java.io.ByteArrayInputStream

/**
 * Dedicated Standalone Web Application (PWA / WebAPK) Activity.
 *
 * Provides:
 * 1. 100% Standalone Web Viewport (No Omnibar, no tab switcher button, no bottom bar).
 * 2. Full Browser Engine Parity:
 *    - Desktop Site Mode Toggle (Desktop User-Agent + Viewport scaling)
 *    - Privacy Shield & Ad/Tracker Blocking via KaspaPrivacyEngine
 *    - Full File Chooser & Camera KYC upload integration
 *    - Geolocation & Media Permissions
 *    - Fullscreen HTML5 Video playback
 *    - External Native App Intent Routing (Spotify, Telegram, YouTube, Wallets)
 *    - Zero-copy GPU caching & pull-to-refresh without accidental triggers
 * 3. Independent Task Space with FLAG_ACTIVITY_NEW_DOCUMENT for separate window in Android Multitasking.
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
                        finish()
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
    val clipboardManager = LocalClipboardManager.current

    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf(initialTitle) }
    var isLoading by remember { mutableStateOf(true) }
    var webProgress by remember { mutableFloatStateOf(0.1f) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isDesktopMode by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var blockedAdsCount by remember { mutableIntStateOf(0) }

    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customVideoCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var uploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (uploadCallback != null) {
            val results: Array<Uri>? = when {
                result.resultCode == android.app.Activity.RESULT_OK -> {
                    val data = result.data
                    val clipData = data?.clipData
                    if (clipData != null && clipData.itemCount > 0) {
                        (0 until clipData.itemCount).mapNotNull { clipData.getItemAt(it).uri }.toTypedArray()
                    } else {
                        val singleUri = data?.data
                        if (singleUri != null) arrayOf(singleUri) else null
                    }
                }
                else -> null
            }
            uploadCallback?.onReceiveValue(results)
            uploadCallback = null
        }
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun applyDesktopMode(wv: WebView, desktop: Boolean) {
        val defaultUa = WebSettings.getDefaultUserAgent(context)
        wv.settings.apply {
            userAgentString = if (desktop) {
                KaspaPrivacyEngine.getDesktopUserAgent(defaultUa)
            } else {
                KaspaPrivacyEngine.getMobileUserAgent(defaultUa)
            }
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        val target = wv.url ?: currentUrl
        if (target.isNotBlank() && !target.startsWith("data:")) {
            val headers = KaspaPrivacyEngine.getDesktopHeaders(desktop, defaultUa)
            wv.loadUrl(target, headers)
        }
    }

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

                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    isHapticFeedbackEnabled = true
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    isNestedScrollingEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER

                    setOnScrollChangeListener { _, _, scrollY, _, _ ->
                        val atTop = (scrollY <= 0 && !canScrollVertically(-1))
                        if (!atTop) {
                            swipeLayout.isEnabled = false
                            if (swipeLayout.isRefreshing) {
                                swipeLayout.isRefreshing = false
                            }
                        } else {
                            swipeLayout.isEnabled = true
                        }
                    }

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, false)
                    }

                    val defaultUa = WebSettings.getDefaultUserAgent(ctx)
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
                        userAgentString = if (isDesktopMode) {
                            KaspaPrivacyEngine.getDesktopUserAgent(defaultUa)
                        } else {
                            KaspaPrivacyEngine.getMobileUserAgent(defaultUa)
                        }
                    }

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            url?.let { currentUrl = it }
                            // Inject privacy shield & viewport scripts
                            val shieldScript = KaspaPrivacyEngine.getPrivacyShieldScript(
                                safeGpuMode = false,
                                isDesktop = isDesktopMode,
                                baseUa = defaultUa
                            )
                            view?.evaluateJavascript(shieldScript, null)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            isRefreshing = false
                            swipeLayout.isRefreshing = false
                            url?.let { currentUrl = it }
                            view?.title?.let { if (it.isNotBlank()) pageTitle = it }
                            CookieManager.getInstance().flush()
                        }

                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: return null
                            // Ad & Tracker blocking
                            if (KaspaPrivacyEngine.isTrackerOrAd(reqUrl)) {
                                blockedAdsCount++
                                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                            }
                            // Zero-copy local asset cache
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

                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            uploadCallback?.onReceiveValue(null)
                            uploadCallback = filePathCallback
                            return try {
                                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                }
                                val chooser = Intent.createChooser(intent, "Select File to Upload")
                                fileChooserLauncher.launch(chooser)
                                true
                            } catch (_: Exception) {
                                uploadCallback?.onReceiveValue(null)
                                uploadCallback = null
                                false
                            }
                        }

                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            if (customVideoView != null) {
                                callback?.onCustomViewHidden()
                                return
                            }
                            customVideoView = view
                            customVideoCallback = callback
                        }

                        override fun onHideCustomView() {
                            try {
                                customVideoCallback?.onCustomViewHidden()
                            } catch (_: Throwable) {}
                            customVideoView = null
                            customVideoCallback = null
                        }

                        override fun onGeolocationPermissionsShowPrompt(
                            origin: String?,
                            callback: GeolocationPermissions.Callback?
                        ) {
                            callback?.invoke(origin, true, false)
                        }

                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.grant(request.resources)
                        }
                    }

                    val headers = KaspaPrivacyEngine.getDesktopHeaders(isDesktopMode, defaultUa)
                    loadUrl(initialUrl, headers)
                }

                swipeLayout.setOnRefreshListener {
                    isRefreshing = true
                    wv.reload()
                }

                swipeLayout.addView(wv)
                webViewRef = wv
                onWebViewCreated(wv)
                swipeLayout
            }
        )

        // Fullscreen Custom Video Player Overlay
        customVideoView?.let { videoView ->
            AndroidView(
                factory = { videoView },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

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
    }
}
