package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omnibar.KaspaBuyOption
import com.example.omnibar.KaspaMarketData
import com.example.omnibar.MathCalculationResult
import com.example.omnibar.SmartOmnibarEngine
import com.example.omnibar.WebsitePreviewData
import com.example.ui.theme.*

/**
 * 1. Instant In-Search Math Calculator Card
 */
@Composable
fun SmartMathCard(
    result: MathCalculationResult,
    onCopy: (String) -> Unit,
    onApply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("smart_math_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = "Calculator",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (result.isCryptoConversion) "Crypto Conversion" else "Instant Calculator",
                        color = ElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                if (result.conversionDetails != null) {
                    Text(
                        text = result.conversionDetails,
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${result.expression} =",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = result.formattedResult,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { onCopy(result.formattedResult) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = SurfaceCard,
                            contentColor = TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy result",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 11.sp)
                    }

                    Button(
                        onClick = { onApply(result.formattedResult) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                    ) {
                        Text("Use", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 2. Real-Time Kaspa Price & Market Ticker Card
 */
@Composable
fun KaspaPriceMarketCard(
    marketData: KaspaMarketData,
    onBuyKaspa: () -> Unit,
    onOpenChart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPositive = marketData.change24h >= 0.0
    val changeColor = if (isPositive) EmeraldMesh else RedTamper
    val changeSign = if (isPositive) "+" else ""

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, KaspaTea.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .testTag("kaspa_price_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(KaspaTea.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Kaspa Ticker",
                            tint = KaspaTea,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Kaspa (KAS) Market",
                        color = KaspaTea,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = changeColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, changeColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "$changeSign%.2f%% 24h".format(marketData.change24h),
                        color = changeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "$%.4f".format(marketData.priceUsd),
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "Cap: ${marketData.marketCapUsd}",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onOpenChart,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 34.dp)
                    ) {
                        Text("Chart", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onBuyKaspa,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KaspaTea,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "Buy",
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Buy KAS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 3. Verified "Where to Buy Kaspa" Gateway Card
 */
@Composable
fun BuyKaspaGatewayCard(
    options: List<KaspaBuyOption>,
    onSelectOption: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, KaspaTea.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .testTag("buy_kaspa_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(KaspaTea.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalMall,
                        contentDescription = "Buy Kaspa",
                        tint = KaspaTea,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Where to Buy Kaspa (KAS)",
                        color = KaspaTea,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Verified exchanges and non-custodial swaps",
                        color = TextMuted,
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            options.take(3).forEach { opt ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clickable { onSelectOption(opt.url) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = opt.name,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = ElectricCyan.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = opt.badge,
                                        color = ElectricCyan,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = opt.description,
                                color = TextMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4. Smart Typo Correction ("Did You Mean?") Banner
 */
@Composable
fun DidYouMeanBanner(
    suggestion: String,
    onApplySuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, AmberCentral.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onApplySuggestion(suggestion) }
            .testTag("did_you_mean_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = "Auto-correct",
                    tint = AmberCentral,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Did you mean: ",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                Text(
                    text = suggestion,
                    color = AmberCentral,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AmberCentral.copy(alpha = 0.15f),
                modifier = Modifier.clickable { onApplySuggestion(suggestion) }
            ) {
                Text(
                    text = "Auto-Correct",
                    color = AmberCentral,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * 5. Instant Real-Time Website Preview Card with Live Web Rendering Viewport
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebsitePreviewCard(
    preview: WebsitePreviewData,
    onOpen: (String) -> Unit,
    onCopyUrl: (String) -> Unit,
    onExpandPreview: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isLoading by remember(preview.fullUrl) { mutableStateOf(true) }
    var pageProgress by remember(preview.fullUrl) { mutableFloatStateOf(0.15f) }
    var pageTitle by remember(preview.fullUrl) { mutableStateOf(preview.title) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .testTag("website_preview_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Header Row: Favicon/Icon + Title + Domain + Security + Real-time Live Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (preview.isDecentralized) KaspaTea.copy(alpha = 0.2f) else ElectricCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (preview.isDecentralized) Icons.Default.Hub else Icons.Default.Language,
                            contentDescription = null,
                            tint = if (preview.isDecentralized) KaspaTea else ElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pageTitle.ifBlank { preview.title },
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = preview.domain,
                            color = ElectricCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // LIVE badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = EmeraldMesh.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldMesh.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldMesh)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "LIVE",
                                color = EmeraldMesh,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = (if (preview.isDecentralized) KaspaTea else EmeraldMesh).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            (if (preview.isDecentralized) KaspaTea else EmeraldMesh).copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (preview.isSecure) Icons.Default.Lock else Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (preview.isDecentralized) KaspaTea else EmeraldMesh,
                                modifier = Modifier.size(9.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = preview.protocolBadge,
                                color = if (preview.isDecentralized) KaspaTea else EmeraldMesh,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Real-Time Progress Bar while rendering page
            if (isLoading && pageProgress < 1.0f) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { pageProgress },
                    color = ElectricCyan,
                    trackColor = SurfaceDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Real-Time Embedded Live Web Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(65.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceDark)
                    .border(0.5.dp, SurfaceCardBorder, RoundedCornerShape(6.dp))
                    .clickable { onOpen(preview.fullUrl) }
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(0xFF14191C.toInt())

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                setSupportZoom(false)
                                displayZoomControls = false
                                cacheMode = WebSettings.LOAD_DEFAULT
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                allowFileAccess = false
                                allowContentAccess = false
                            }

                            setInitialScale(48)
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            isClickable = false
                            isFocusable = false

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    pageProgress = (newProgress / 100f).coerceIn(0.1f, 1f)
                                    if (newProgress >= 80) {
                                        isLoading = false
                                    }
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank() && !title.startsWith("http", ignoreCase = true)) {
                                        pageTitle = title
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    val t = view?.title
                                    if (!t.isNullOrBlank() && !t.startsWith("http", ignoreCase = true)) {
                                        pageTitle = t
                                    }
                                }

                                @SuppressLint("WebViewClientOnReceivedSslError")
                                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                    handler?.proceed()
                                }

                                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                    isLoading = false
                                    return true
                                }
                            }

                            tag = preview.fullUrl
                            loadUrl(preview.fullUrl)
                        }
                    },
                    update = { wv ->
                        if (wv.tag != preview.fullUrl) {
                            wv.tag = preview.fullUrl
                            isLoading = true
                            pageProgress = 0.15f
                            wv.loadUrl(preview.fullUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Subtitle/Tap hint overlay on bottom of viewport
                Surface(
                    color = ObsidianBg.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = preview.description,
                            color = TextMuted,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Tap to open",
                            color = ElectricCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Row: Security shield + Copy + Expand + Open Site
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = EmeraldMesh,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Zero Trackers",
                        color = EmeraldMesh,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = { onCopyUrl(preview.fullUrl) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = SurfaceCard,
                            contentColor = TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Copy", fontSize = 9.sp)
                    }

                    if (onExpandPreview != null) {
                        OutlinedButton(
                            onClick = { onExpandPreview(preview.fullUrl) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, SurfaceCardBorder),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Full View",
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Full", fontSize = 9.sp)
                        }
                    }

                    Button(
                        onClick = { onOpen(preview.fullUrl) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 40.dp, minHeight = 28.dp)
                    ) {
                        Text("Open", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open",
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }
    }
}
