package com.example.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import com.example.MainActivity
import com.example.network.CronetClientFactory
import com.example.network.KaspaPrivacyEngine
import com.example.network.KaspaPrivacyRelayEngine
import com.example.network.NativeIntentRoutingEngine
import com.example.network.WebViewAssetLruCache
import com.example.ui.theme.*
import com.example.util.BrowserTabWebViewManager
import com.example.util.KaspaWebViewConfigurator
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Dedicated Standalone Web Application (PWA / WebAPK) Activity.
 *
 * Implements complete Web Platform standards for installed PWAs:
 * 1. Full Browser Engine Parity (JavaScript, DOM Storage, IndexedDB, Cookies, Service Worker).
 * 2. Independent Window / Document Space (FLAG_ACTIVITY_NEW_DOCUMENT).
 * 3. Lifecycle-safe state restoration with zero unnecessary reloads.
 * 4. Error recovery protection against blank screens.
 */
class PwaStandaloneActivity : FragmentActivity() {

    companion object {
        private const val TAG = "PWA"
    }

    private var targetUrl: String = ""
    private var appTitle: String = ""
    private var webView: WebView? = null
    private var savedWebViewBundle: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Global crash handler to protect background renderers
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val msg = throwable.message.orEmpty().lowercase()
            val stack = Log.getStackTraceString(throwable).lowercase()
            val isNonFatal = thread.name.contains("Render", ignoreCase = true) ||
                    thread.name.contains("Chromium", ignoreCase = true) ||
                    msg.contains("supervised") ||
                    msg.contains("timeout") ||
                    stack.contains("android.webkit") ||
                    stack.contains("org.chromium") ||
                    stack.contains("superviseduser")
            if (isNonFatal) {
                Log.w(TAG, "Suppressed non-fatal renderer crash in PWA: ${throwable.message}")
                return@setDefaultUncaughtExceptionHandler
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Initialize WebView cache directories synchronously before UI / WebView initialization
        try {
            val crashpadDir = File(cacheDir, "WebView/Crashpad/attachments")
            if (!crashpadDir.exists()) crashpadDir.mkdirs()
            val wasmCacheDir = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
            if (!wasmCacheDir.exists()) wasmCacheDir.mkdirs()
            val jsCacheDir = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            if (!jsCacheDir.exists()) jsCacheDir.mkdirs()
        } catch (_: Exception) {}

        // Initialize background networking and ServiceWorker subsystem
        try {
            CronetClientFactory.initialize(applicationContext)
            WebViewAssetLruCache.initialize(applicationContext)
            KaspaWebViewConfigurator.initServiceWorkerSupport()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize network services in PWA: ${e.message}")
        }

        extractIntentData(intent)
        savedWebViewBundle = savedInstanceState?.getBundle("PWA_WEBVIEW_STATE")
        if (savedWebViewBundle != null) {
            Log.d(TAG, "[PWA] state restored from savedInstanceState")
        }

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
                    savedStateBundle = savedWebViewBundle,
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
                    onWebViewCreated = { wv ->
                        webView = wv
                        Log.d(TAG, "[PWA] WebView created for $targetUrl")
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Idempotent: Never call webView.reload() or loadUrl() from onStart
    }

    override fun onResume() {
        super.onResume()
        // Idempotent: Never call webView.reload() or loadUrl() from onResume
    }

    override fun onPause() {
        super.onPause()
        try {
            CookieManager.getInstance().flush()
        } catch (_: Exception) {}
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        try {
            val bundle = Bundle()
            webView?.saveState(bundle)
            outState.putBundle("PWA_WEBVIEW_STATE", bundle)
            outState.putString("PWA_SAVED_URL", webView?.url ?: targetUrl)
            outState.putString("PWA_SAVED_TITLE", appTitle)
            Log.d(TAG, "[PWA] state saved")
        } catch (e: Exception) {
            Log.w(TAG, "Notice saving PWA instance state: ${e.message}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIntentData(intent)
        val currentWvUrl = webView?.url
        if (targetUrl.isNotBlank() && !BrowserTabWebViewManager.isSameUrl(currentWvUrl, targetUrl)) {
            Log.d(TAG, "[PWA] onNewIntent: loading new URL $targetUrl (was: $currentWvUrl)")
            webView?.loadUrl(targetUrl)
        } else {
            Log.d(TAG, "[PWA] onNewIntent: prevented reload because URL matches ($currentWvUrl)")
        }
    }

    private fun extractIntentData(intent: Intent?) {
        val action = intent?.action ?: Intent.ACTION_VIEW
        val dataUri = intent?.data
        val dataUrl = dataUri?.toString()
        val extraUrl = intent?.getStringExtra("PWA_URL")
            ?: intent?.getStringExtra("PWA_START_URL")

        // Priority: Explicit PWA Intent extra > Intent data URI > default fallback
        targetUrl = when {
            !extraUrl.isNullOrBlank() -> extraUrl
            !dataUrl.isNullOrBlank() -> dataUrl
            else -> "https://kaspa.org"
        }

        appTitle = intent?.getStringExtra("PWA_TITLE")
            ?: intent?.getStringExtra("title")
            ?: runCatching { Uri.parse(targetUrl).host }.getOrNull()
            ?: "Web App"

        // Diagnostics logging (Requirements 1 & 16)
        Log.d(TAG, "[PWA] intent=$intent")
        Log.d(TAG, "[PWA] action=$action")
        Log.d(TAG, "[PWA] data=$dataUri")
        Log.d(TAG, "[PWA] launchUrl=$targetUrl")
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
    savedStateBundle: Bundle? = null,
    onOpenInBrowser: (String) -> Unit,
    onCloseApp: () -> Unit,
    onWebViewCreated: (WebView) -> Unit
) {
    val context = LocalContext.current

    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf(initialTitle) }
    var isLoading by remember { mutableStateOf(false) }
    var webProgress by remember { mutableFloatStateOf(0f) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isDesktopMode by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }

    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customVideoCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var uploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    var pendingPermissionOrigin by remember { mutableStateOf<String?>(null) }
    var pendingGeoOrigin by remember { mutableStateOf<String?>(null) }
    var pendingGeoCallback by remember { mutableStateOf<GeolocationPermissions.Callback?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val req = pendingPermissionRequest
        val expectedUri = pendingPermissionOrigin?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val expectedHost = expectedUri?.host?.lowercase()
        val activeUrl = webViewRef?.url ?: currentUrl
        val currentOrigin = runCatching { Uri.parse(activeUrl) }.getOrNull()
        val currentHost = currentOrigin?.host?.lowercase()
        val isOriginStillValid = expectedUri?.scheme?.equals("https", ignoreCase = true) == true &&
            expectedHost != null && currentHost != null &&
            (expectedHost == currentHost || expectedHost.endsWith(".$currentHost"))

        if (req != null) {
            val resourcesToGrant = mutableListOf<String>()
            if (req.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) &&
                grants[android.Manifest.permission.RECORD_AUDIO] == true
            ) {
                resourcesToGrant.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
            }
            if (req.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) &&
                grants[android.Manifest.permission.CAMERA] == true
            ) {
                resourcesToGrant.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
            }

            if (isOriginStillValid && resourcesToGrant.isNotEmpty()) {
                req.grant(resourcesToGrant.toTypedArray())
            } else {
                req.deny()
            }
            pendingPermissionRequest = null
            pendingPermissionOrigin = null
        }
    }

    fun applyDesktopModeToggle(wv: WebView, desktop: Boolean) {
        val defaultDeviceUa = try {
            WebSettings.getDefaultUserAgent(context)
        } catch (_: Throwable) {
            null
        }
        wv.settings.apply {
            userAgentString = if (desktop) {
                KaspaPrivacyEngine.getDesktopUserAgent(defaultDeviceUa)
            } else {
                KaspaPrivacyEngine.getMobileUserAgent(defaultDeviceUa)
            }
            useWideViewPort = desktop
            loadWithOverviewMode = desktop
        }
        val target = wv.url ?: currentUrl
        if (target.isNotBlank() && !target.startsWith("data:")) {
            wv.loadUrl(target)
        }
    }

    androidx.activity.compose.BackHandler(enabled = canGoBack || webViewRef?.canGoBack() == true) {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onCloseApp()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Standalone PWA Native Layout - 2dp Chromium progress indicator during page navigation
        AnimatedVisibility(
            visible = isLoading && webProgress < 1f,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 150)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 150))
        ) {
            LinearProgressIndicator(
                progress = { webProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = ElectricCyan.copy(alpha = 0.85f),
                trackColor = Color.Transparent
            )
        }

        // Standalone Web Viewport
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val swipeLayout = BrowserSwipeRefreshLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setColorSchemeColors(
                            android.graphics.Color.parseColor("#00E5FF"),
                            android.graphics.Color.parseColor("#10B981")
                        )
                        setProgressBackgroundColorSchemeColor(android.graphics.Color.parseColor("#131B2E"))
                    }

                    val wv = WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.parseColor("#0B0F17"))
                        setLayerType(View.LAYER_TYPE_NONE, null)
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
                            } else if (swipeLayout.isGestureAllowed) {
                                swipeLayout.isEnabled = true
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, false)
                        }

                        // Apply full web settings parity (Requirement 3)
                        KaspaWebViewConfigurator.applyWebSettings(this, ctx, isDesktopMode, acceptThirdPartyCookies = false)

                        // Cookie configuration
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, false)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                webProgress = 0.1f
                                canGoBack = view?.canGoBack() == true
                                url?.let {
                                    currentUrl = it
                                    Log.d("PWA", "[PWA] navigation started: $it")
                                }
                                val shieldScript = KaspaPrivacyEngine.getPrivacyShieldScript(
                                    safeGpuMode = false,
                                    isDesktop = isDesktopMode,
                                    baseUa = WebSettings.getDefaultUserAgent(ctx)
                                )
                                view?.evaluateJavascript(shieldScript, null)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                isRefreshing = false
                                swipeLayout.isRefreshing = false
                                canGoBack = view?.canGoBack() == true
                                url?.let {
                                    currentUrl = it
                                    Log.d("PWA", "[PWA] navigation finished: $it")
                                }
                                view?.title?.let { if (it.isNotBlank()) pageTitle = it }
                                try {
                                    CookieManager.getInstance().flush()
                                } catch (_: Exception) {}

                                // Soft Keyboard Focus Auto-Scroll Helper
                                view?.evaluateJavascript("""
                                    (function() {
                                        if (window.__kaspaAutoKeyboardScrollInit) return;
                                        window.__kaspaAutoKeyboardScrollInit = true;
                                        document.addEventListener('focusin', function(e) {
                                            if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.isContentEditable)) {
                                                setTimeout(function() {
                                                    try {
                                                        e.target.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' });
                                                    } catch(_) {}
                                                }, 300);
                                            }
                                        }, true);
                                    })();
                                """.trimIndent(), null)
                            }

                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                if (request == null) return null
                                val reqUrl = request.url?.toString() ?: return null

                                val mainHost = runCatching { Uri.parse(view?.url ?: currentUrl).host?.lowercase() }.getOrNull()
                                val reqHost = request.url?.host?.lowercase()
                                val isFirstParty = mainHost != null && reqHost != null && (reqHost == mainHost || reqHost.endsWith(".$mainHost"))

                                if (!isFirstParty && KaspaPrivacyEngine.isTrackerOrAd(reqUrl)) {
                                    return WebResourceResponse(
                                        "text/plain",
                                        "UTF-8",
                                        403,
                                        "Blocked by Shield",
                                        mapOf("Access-Control-Allow-Origin" to "*"),
                                        ByteArrayInputStream(ByteArray(0))
                                    )
                                }

                                if (KaspaPrivacyRelayEngine.isRelayApplicable(reqUrl)) {
                                    try {
                                        val headers = request.requestHeaders ?: emptyMap()
                                        val resp: WebResourceResponse? = kotlinx.coroutines.runBlocking {
                                            KaspaPrivacyRelayEngine.interceptForWebView(reqUrl, request.method ?: "GET", headers, request.isForMainFrame)
                                        }
                                        if (resp != null) return resp
                                    } catch (_: Exception) {}
                                }

                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return false
                                val hasGesture = request?.hasGesture() ?: false
                                val uri = request?.url

                                // Support internal PWA routing: same origin / domain stays within the PWA viewport
                                val currentHost = runCatching { Uri.parse(view?.url ?: currentUrl).host?.lowercase() }.getOrNull() ?: ""
                                val targetHost = uri?.host?.lowercase() ?: ""

                                val isSameOrigin = targetHost.isNotEmpty() && (targetHost == currentHost || targetHost.endsWith(".$currentHost"))
                                if (isSameOrigin && (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))) {
                                    // Let WebView handle client-side routing & standard same-domain pages internally
                                    return false
                                }

                                return NativeIntentRoutingEngine.routeUrl(ctx, url, hasGesture) { fallback ->
                                    view?.loadUrl(fallback)
                                }
                            }

                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                super.onReceivedError(view, request, error)
                                Log.e("PWA", "[PWA] Web resource error: ${error?.description} for ${request?.url}")
                                if (request == null || request.isForMainFrame) {
                                    isLoading = false
                                    isRefreshing = false
                                    swipeLayout.isRefreshing = false
                                    Log.e("PWA", "[PWA] resource error: ${error?.description}")
                                    val failingUrl = request?.url?.toString() ?: view?.url ?: currentUrl
                                    val errorHtml = KaspaWebViewConfigurator.generateErrorHtml(failingUrl, error?.description?.toString())
                                    view?.loadDataWithBaseURL("kaspa-error://offline/", errorHtml, "text/html", "UTF-8", failingUrl)
                                }
                            }

                            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                Log.w("PWA", "[PWA] HTTP error: ${errorResponse?.statusCode} for ${request?.url}")
                            }

                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                                handler?.cancel()
                                isLoading = false
                                isRefreshing = false
                                swipeLayout.isRefreshing = false
                                val failingUrl = error?.url ?: currentUrl
                                val sslReason = when (error?.primaryError) {
                                    android.net.http.SslError.SSL_EXPIRED -> "The SSL certificate for this site has expired."
                                    android.net.http.SslError.SSL_IDMISMATCH -> "The SSL certificate host does not match the requested domain."
                                    android.net.http.SslError.SSL_UNTRUSTED -> "The certificate authority is untrusted or self-signed."
                                    android.net.http.SslError.SSL_NOTYETVALID -> "The SSL certificate is not yet valid."
                                    android.net.http.SslError.SSL_DATE_INVALID -> "The device clock or SSL certificate date is invalid."
                                    else -> "SSL Certificate handshake verification failed."
                                }
                                val warningPage = KaspaWebViewConfigurator.generateSslErrorHtml(failingUrl, sslReason)
                                view?.loadDataWithBaseURL(null, warningPage, "text/html", "UTF-8", null)
                            }

                            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                val didCrash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) detail?.didCrash() == true else true
                                Log.e("PWA", "[PWA] renderer process gone (didCrash=$didCrash)")
                                try {
                                    (view?.parent as? ViewGroup)?.removeView(view)
                                    view?.destroy()
                                } catch (_: Exception) {}
                                return true
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                if (newProgress >= 100) {
                                    webProgress = 1.0f
                                    isLoading = false
                                    isRefreshing = false
                                    swipeLayout.isRefreshing = false
                                } else if (isLoading) {
                                    val progressFraction = (newProgress / 100f).coerceIn(0.15f, 0.98f)
                                    if (progressFraction >= webProgress) {
                                        webProgress = progressFraction
                                    }
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
                                pendingGeoOrigin = origin
                                pendingGeoCallback = callback
                            }

                            override fun onPermissionRequest(request: PermissionRequest?) {
                                if (request != null) {
                                    val reqOrigin = request.origin
                                    val isSecure = reqOrigin?.scheme?.equals("https", ignoreCase = true) == true
                                    val reqHost = reqOrigin?.host?.lowercase()
                                    val activeUrl = webViewRef?.url ?: currentUrl
                                    val currentOrigin = runCatching { Uri.parse(activeUrl) }.getOrNull()
                                    val currentHost = currentOrigin?.host?.lowercase()
                                    val matchesCurrentHost = reqHost != null && currentHost != null &&
                                        (reqHost == currentHost || reqHost.endsWith(".$currentHost"))

                                    if (!isSecure || !matchesCurrentHost) {
                                        request.deny()
                                        return
                                    }

                                    // Explicitly reject unknown future resources: only support audio and video
                                    val requestedKnownResources = request.resources.filter {
                                        it == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                                        it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                    }
                                    if (requestedKnownResources.isEmpty()) {
                                        request.deny()
                                        return
                                    }

                                    pendingPermissionRequest = request
                                    pendingPermissionOrigin = reqOrigin?.toString()
                                    val permissions = mutableListOf<String>()
                                    if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                                        permissions.add(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                    if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                                        permissions.add(android.Manifest.permission.CAMERA)
                                    }
                                    if (permissions.isNotEmpty()) {
                                        permissionLauncher.launch(permissions.toTypedArray())
                                    } else {
                                        request.deny()
                                        pendingPermissionRequest = null
                                        pendingPermissionOrigin = null
                                    }
                                }
                            }

                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: android.os.Message?
                            ): Boolean {
                                if (resultMsg == null || view == null) return false
                                val tempWebView = WebView(view.context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.setSupportMultipleWindows(false)
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = false
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(wv: WebView?, url: String?, favicon: Bitmap?) {
                                            super.onPageStarted(wv, url, favicon)
                                            val target = url ?: return
                                            wv?.stopLoading()
                                            view.loadUrl(target)
                                        }
                                    }
                                }
                                val transport = resultMsg.obj as? WebView.WebViewTransport
                                if (transport != null) {
                                    transport.webView = tempWebView
                                    resultMsg.sendToTarget()
                                    return true
                                }
                                return false
                            }

                            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                Toast.makeText(ctx, message ?: "", Toast.LENGTH_SHORT).show()
                                result?.confirm()
                                return true
                            }

                            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                result?.confirm()
                                return true
                            }
                        }

                        // Download Handling
                        setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                            try {
                                val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
                                val cookies = try { CookieManager.getInstance().getCookie(url) } catch (_: Exception) { null }
                                val request = DownloadManager.Request(Uri.parse(url)).apply {
                                    setMimeType(mimetype)
                                    cookies?.let { addRequestHeader("Cookie", it) }
                                    addRequestHeader("User-Agent", userAgent)
                                    setDescription("Downloading $filename")
                                    setTitle(filename)
                                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                                }
                                val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                                dm?.enqueue(request)
                                Toast.makeText(ctx, "Downloading $filename", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        // In-process state restoration check (Requirement 11)
                        var stateRestored = false
                        if (savedStateBundle != null) {
                            try {
                                val restored = restoreState(savedStateBundle)
                                if (restored != null && restored.size > 0 && !url.isNullOrBlank()) {
                                    stateRestored = true
                                    Log.d("PWA", "[PWA] WebView restored: true (url: $url)")
                                }
                            } catch (e: Exception) {
                                Log.w("PWA", "Notice restoring WebView state in PWA: ${e.message}")
                            }
                        }

                        if (!stateRestored) {
                            Log.d("PWA", "[PWA] loadUrl: $initialUrl")
                            loadUrl(initialUrl)
                        }
                    }

                    swipeLayout.setOnRefreshListener {
                        isRefreshing = true
                        wv.reload()
                    }

                    swipeLayout.addView(wv)
                    swipeLayout.targetWebView = wv
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

            // Location Access Consent Dialog
            if (pendingGeoOrigin != null) {
                AlertDialog(
                    onDismissRequest = {
                        pendingGeoCallback?.invoke(pendingGeoOrigin, false, false)
                        pendingGeoOrigin = null
                        pendingGeoCallback = null
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Location Permission Request", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                        }
                    },
                    text = {
                        Text(
                            text = "The standalone web application at \"$pendingGeoOrigin\" wants to access your device location.",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                pendingGeoCallback?.invoke(pendingGeoOrigin, true, false)
                                pendingGeoOrigin = null
                                pendingGeoCallback = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF0B0F17)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Allow Access", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                pendingGeoCallback?.invoke(pendingGeoOrigin, false, false)
                                pendingGeoOrigin = null
                                pendingGeoCallback = null
                            }
                        ) {
                            Text("Deny", color = Color(0xFF94A3B8))
                        }
                    },
                    containerColor = Color(0xFF151A26),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}
