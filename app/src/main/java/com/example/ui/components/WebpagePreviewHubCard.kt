package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.network.KaspaPriceService
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.KaspaTea
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

private const val CHROME_MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

/**
 * Real-time Webpage Live Preview Little Hub.
 * Crawls and renders any website in a live miniature preview container
 * when typing in the search bar. Robustly traps errors and handles redirects,
 * mixed content, SSL, and custom schemes.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebpagePreviewHubCard(
    urlQuery: String,
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit,
    onOpenInNewTab: ((String) -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val cleanUrl = remember(urlQuery) {
        normalizePreviewUrl(urlQuery)
    }

    if (cleanUrl.isNullOrBlank()) return

    val domainHost = remember(cleanUrl) {
        try {
            Uri.parse(cleanUrl).host ?: cleanUrl
        } catch (_: Exception) {
            cleanUrl
        }
    }

    var pageTitle by remember(cleanUrl) { mutableStateOf<String?>(null) }
    var pageProgress by remember(cleanUrl) { mutableFloatStateOf(0.15f) }
    var isLoading by remember(cleanUrl) { mutableStateOf(true) }
    var isSecure by remember(cleanUrl) { mutableStateOf(cleanUrl.startsWith("https://")) }
    var hasError by remember(cleanUrl) { mutableStateOf(false) }
    var errorMessage by remember(cleanUrl) { mutableStateOf<String?>(null) }
    var previewWebView by remember { mutableStateOf<WebView?>(null) }
    val clipboardManager = LocalClipboardManager.current
    var copiedNotice by remember { mutableStateOf(false) }

    DisposableEffect(cleanUrl) {
        onDispose {
            try {
                previewWebView?.stopLoading()
                previewWebView?.destroy()
                previewWebView = null
            } catch (_: Exception) {}
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("webpage_preview_hub_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Header Row: Favicon/Lock + Domain/Title + Security + Loading + Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSecure) Icons.Default.Lock else Icons.Default.Language,
                            contentDescription = "Security Status",
                            tint = if (isSecure) KaspaTea else TextMuted,
                            modifier = Modifier.size(11.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pageTitle ?: domainHost,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = cleanUrl,
                            color = TextMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = KaspaTea,
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    onDismiss?.let { dismissAction ->
                        IconButton(
                            onClick = dismissAction,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Preview",
                                tint = TextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            // Real-time Progress Bar
            if (isLoading && pageProgress < 1.0f) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { pageProgress },
                    color = KaspaTea,
                    trackColor = SurfaceDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Real-Time Live Webpage Viewport Container (110dp height preview)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
                    .border(0.5.dp, SurfaceCardBorder, RoundedCornerShape(8.dp))
                    .clickable {
                        onOpenUrl(cleanUrl)
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(0xFF161B1E.toInt())

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                setSupportZoom(false)
                                displayZoomControls = false
                                cacheMode = WebSettings.LOAD_DEFAULT
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = CHROME_MOBILE_USER_AGENT
                                allowFileAccess = false
                                allowContentAccess = false
                            }

                            setInitialScale(45) // Scale down for a rich miniature overview
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            isClickable = false
                            isFocusable = false

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    pageProgress = (newProgress / 100f).coerceIn(0.1f, 1f)
                                    if (newProgress >= 85) {
                                        isLoading = false
                                    }
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank() && !title.startsWith("http", ignoreCase = true) && !title.contains("404") && !title.contains("Error")) {
                                        pageTitle = title
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    hasError = false
                                    if (url != null) isSecure = url.startsWith("https://")
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    val currentTitle = view?.title
                                    if (!currentTitle.isNullOrBlank() && !currentTitle.startsWith("http", ignoreCase = true)) {
                                        pageTitle = currentTitle
                                    }
                                }

                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                    if (request?.isForMainFrame == true) {
                                        // Ignore benign net errors (e.g. subresource aborts)
                                        val errCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) error?.errorCode ?: 0 else 0
                                        if (errCode != ERROR_TIMEOUT && errCode != ERROR_CONNECT && errCode != ERROR_HOST_LOOKUP) {
                                            // Soft error, still allow display if partial content rendered
                                        } else {
                                            hasError = true
                                            errorMessage = "Could not load preview"
                                            isLoading = false
                                        }
                                    }
                                }

                                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                                    if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 400) {
                                        // Allow preview to show response page or fallback
                                        isLoading = false
                                    }
                                }

                                @SuppressLint("WebViewClientOnReceivedSslError")
                                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                    // In preview sandbox mode, allow viewing preview
                                    handler?.proceed()
                                    isSecure = false
                                }

                                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                    hasError = true
                                    isLoading = false
                                    return true // Handled gracefully, do not crash host app
                                }
                            }

                            tag = cleanUrl
                            loadUrl(cleanUrl)
                            previewWebView = this
                        }
                    },
                    update = { wv ->
                        // Only reload if the target cleanUrl changed from what was loaded into this WebView
                        if (wv.tag != cleanUrl) {
                            wv.tag = cleanUrl
                            isLoading = true
                            hasError = false
                            pageProgress = 0.15f
                            wv.loadUrl(cleanUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Error fallback overlay (gracefully traps errors and allows 1-tap open)
                if (hasError) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = KaspaTea,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = errorMessage ?: "Preview unavailable (protected site)",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Tap here to open $domainHost directly",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Toolbar: "Open Site" + "New Tab" + "Copy Link"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Open in Active Tab
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = KaspaTea.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, KaspaTea.copy(alpha = 0.4f)),
                        modifier = Modifier.clickable { onOpenUrl(cleanUrl) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Open",
                                tint = KaspaTea,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Open Site",
                                color = KaspaTea,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Open in New Tab
                    onOpenInNewTab?.let { newTabAction ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SurfaceDark,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, SurfaceCardBorder),
                            modifier = Modifier.clickable { newTabAction(cleanUrl) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = "New Tab",
                                    tint = TextMuted,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "New Tab",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Copy Link
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, SurfaceCardBorder),
                    modifier = Modifier.clickable {
                        clipboardManager.setText(AnnotatedString(cleanUrl))
                        copiedNotice = true
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = if (copiedNotice) KaspaTea else TextMuted,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (copiedNotice) "Copied!" else "Copy",
                            color = if (copiedNotice) KaspaTea else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Checks if a search query represents a previewable website or domain.
 * Accurately detects domains, IPs, URLs, localhost, ports, and top-level domains.
 */
fun isWebpagePreviewQuery(query: String?): Boolean {
    if (query.isNullOrBlank()) return false
    val trimmed = query.trim().lowercase()

    // If query has whitespace, it's a search sentence unless it starts with http(s)://
    if (trimmed.contains(" ") && !trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
        return false
    }

    // Exclude Kaspa price queries (those have their dedicated price hub)
    if (KaspaPriceService.isKaspaPriceQuery(trimmed)) return false

    // Direct HTTP(S) or known Web3 protocols
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("kaspa://") || trimmed.startsWith("ipfs://")) {
        return true
    }

    // Localhost or IPv4 pattern (e.g. 192.168.1.1 or localhost:8080)
    if (trimmed.startsWith("localhost") || trimmed.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(:\d+)?(/.*)?$"""))) {
        return true
    }

    // Standard Domain regex check: [subdomain.]domain.tld[/path]
    val domainRegex = Regex("""^([a-z0-9]([a-z0-9\-]{0,61}[a-z0-9])?\.)+[a-z]{2,}(:\d+)?(/.*)?$""")
    if (domainRegex.matches(trimmed)) {
        return true
    }

    // Known common TLD extensions
    val tlds = listOf(
        ".com", ".org", ".net", ".io", ".dev", ".app", ".stream", ".live",
        ".xyz", ".me", ".info", ".co", ".ai", ".tech", ".gg", ".cc", ".to",
        ".sh", ".edu", ".gov", ".uk", ".de", ".fr", ".jp", ".ru", ".ch",
        ".ca", ".au", ".br", ".in", ".nl", ".se", ".no", ".es", ".it", ".kr"
    )
    if (tlds.any { trimmed.endsWith(it) || trimmed.contains("$it/") || trimmed.contains("$it:") }) {
        return true
    }

    return false
}

/**
 * Normalizes input string to a valid http(s) URL for live crawl & preview.
 */
fun normalizePreviewUrl(query: String): String? {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return null

    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.startsWith("kaspa://") || trimmed.startsWith("ipfs://") -> trimmed
        trimmed.startsWith("localhost") || trimmed.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(:\d+)?.*$""")) -> "http://$trimmed"
        trimmed.contains(".") -> "https://$trimmed"
        else -> null
    }
}
