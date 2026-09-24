package com.example.ui

import com.example.data.*
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlin.coroutines.resume
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.data.AccountEntity
import com.example.network.CryptoUtils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.filled.Article
import com.example.data.BookmarkEntity
import com.example.data.ContentEntity
import com.example.model.NetworkProtocol
import com.example.model.ResolvedResource
import com.example.model.VerificationStatus
import com.example.R
import androidx.compose.ui.res.painterResource
import com.example.ui.theme.AmberCentral
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMesh
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RedTamper
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.network.KaspaPrivacyEngine
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.KaspaTea
import com.example.ui.theme.VioletBridge
import com.example.viewmodel.DecentralViewModel
import com.example.viewmodel.KaspaAddressValidationResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

data class BrowserTab(
    val id: String = java.util.UUID.randomUUID().toString(),
    val url: String = "",
    val title: String = "New Tab",
    val resource: ResolvedResource? = null
)

internal fun executeImageDownload(
    context: android.content.Context,
    targetImgUrl: String,
    viewModel: DecentralViewModel,
    webViewInstance: WebView?
) {
    try {
        val downloadId = System.currentTimeMillis()
        val cleanFileName: String

        if (targetImgUrl.startsWith("data:", ignoreCase = true)) {
            val ext = when {
                targetImgUrl.startsWith("data:image/png", ignoreCase = true) -> "png"
                targetImgUrl.startsWith("data:image/jpeg", ignoreCase = true) || targetImgUrl.startsWith("data:image/jpg", ignoreCase = true) -> "jpg"
                targetImgUrl.startsWith("data:image/webp", ignoreCase = true) -> "webp"
                targetImgUrl.startsWith("data:image/svg", ignoreCase = true) -> "svg"
                targetImgUrl.startsWith("data:image/gif", ignoreCase = true) -> "gif"
                else -> "png"
            }
            cleanFileName = "image_${System.currentTimeMillis()}.$ext"
            viewModel.addDownload(downloadId, cleanFileName, targetImgUrl)
            viewModel.saveBase64ImageDownload(context, downloadId, targetImgUrl, cleanFileName)
            viewModel.setStatusMessage("Downloading image: $cleanFileName")
            android.widget.Toast.makeText(context, "Downloading $cleanFileName", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // Standard HTTP / HTTPS URL
        val rawFilename = android.webkit.URLUtil.guessFileName(targetImgUrl, null, "image/*")
        if (rawFilename.isBlank() || rawFilename == "downloadfile" || !rawFilename.contains(".")) {
            val urlClean = targetImgUrl.substringBefore("?").substringBefore("#")
            val lastSegment = urlClean.substringAfterLast("/").trim()
            val guessedExt = when {
                lastSegment.endsWith(".png", true) -> "png"
                lastSegment.endsWith(".jpg", true) || lastSegment.endsWith(".jpeg", true) -> "jpg"
                lastSegment.endsWith(".webp", true) -> "webp"
                lastSegment.endsWith(".svg", true) -> "svg"
                lastSegment.endsWith(".gif", true) -> "gif"
                targetImgUrl.contains("format=webp", true) -> "webp"
                targetImgUrl.contains("format=png", true) -> "png"
                targetImgUrl.contains("format=jpg", true) || targetImgUrl.contains("format=jpeg", true) -> "jpg"
                else -> "jpg"
            }
            cleanFileName = if (lastSegment.isNotBlank() && lastSegment.length < 40 && !lastSegment.contains(".")) {
                "${lastSegment}_${System.currentTimeMillis()}.$guessedExt"
            } else {
                "image_${System.currentTimeMillis()}.$guessedExt"
            }
        } else {
            cleanFileName = rawFilename
        }

        val sanitizedFileName = cleanFileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        val cookies = try {
            android.webkit.CookieManager.getInstance().getCookie(targetImgUrl)
        } catch (_: Exception) {
            null
        }
        val userAgent = webViewInstance?.settings?.userAgentString
        val referer = webViewInstance?.url ?: targetImgUrl

        val dmId = try {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(targetImgUrl)).apply {
                setTitle(sanitizedFileName)
                setDescription("Downloading image")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI or android.app.DownloadManager.Request.NETWORK_MOBILE)
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, sanitizedFileName)
                if (!userAgent.isNullOrBlank()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }
                if (!referer.isNullOrBlank()) {
                    addRequestHeader("Referer", referer)
                }
            }
            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.enqueue(request)
        } catch (_: Exception) {
            downloadId
        }

        viewModel.addDownload(dmId, sanitizedFileName, targetImgUrl)
        viewModel.startDownload(context, dmId, targetImgUrl, sanitizedFileName, cookies, userAgent, referer)
        viewModel.setStatusMessage("Downloading image: $sanitizedFileName")
        android.widget.Toast.makeText(context, "Downloading $sanitizedFileName", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        viewModel.setStatusMessage("Image download failed: ${e.message}")
        android.widget.Toast.makeText(context, "Image download failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun HubActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = TextPrimary,
    titleColor: Color = TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = titleColor
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@SuppressLint("SetJavaScriptEnabled", "WrongConstant", "NewApi")
@Composable
fun BrowserGatewayScreen(viewModel: DecentralViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val defaultDeviceUa = remember {
        try {
            android.webkit.WebSettings.getDefaultUserAgent(context)
        } catch (_: Throwable) {
            null
        }
    }
    val tabs by viewModel.browserTabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showBrowserMenu by remember { mutableStateOf(false) }

    val themePrefs = remember { context.getSharedPreferences("kaspa_browser_theme", android.content.Context.MODE_PRIVATE) }
    var browserTheme by remember { mutableStateOf(themePrefs.getString("selected_theme", "classic_dark") ?: "classic_dark") }
    var customBgColorHex by remember { mutableStateOf(themePrefs.getString("custom_bg_hex", "#12141C") ?: "#12141C") }
    var showThemeDialog by remember { mutableStateOf(false) }

    var isReaderMode by remember { mutableStateOf(false) }
    var originalPageUrl by remember { mutableStateOf("") }
    var isExtractingOrLoadingReaderMode by remember { mutableStateOf(false) }
    var readerTitle by remember { mutableStateOf("") }
    var readerContent by remember { mutableStateOf("") }
    var readerLeadImage by remember { mutableStateOf("") }
    var readerFontSize by remember { mutableIntStateOf(themePrefs.getInt("reader_font_size", 18)) }

    fun saveBrowserTheme(theme: String, hex: String = customBgColorHex) {
        browserTheme = theme
        customBgColorHex = hex
        themePrefs.edit().putString("selected_theme", theme).putString("custom_bg_hex", hex).apply()
    }

    var longPressedLinkUrl by remember { mutableStateOf<String?>(null) }
    var longPressedImageUrl by remember { mutableStateOf<String?>(null) }
    var longPressedImageTitle by remember { mutableStateOf<String?>(null) }
    var isLongPressedImage by remember { mutableStateOf(false) }
    var showLinkContextMenu by remember { mutableStateOf(false) }
    val linkContextMenuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val bookmarks by viewModel.bookmarks.collectAsState()
    val searchEngine by viewModel.searchEngine.collectAsState()

    val hasWalletPassword by viewModel.hasWalletPassword.collectAsState()
    val biometricsEnabled by viewModel.biometricsEnabled.collectAsState()

    var showFindInPage by remember { mutableStateOf(false) }
    var findInPageQuery by remember { mutableStateOf("") }

    var jsAlertMessage by remember { mutableStateOf<String?>(null) }
    var jsAlertResult by remember { mutableStateOf<android.webkit.JsResult?>(null) }

    var jsConfirmMessage by remember { mutableStateOf<String?>(null) }
    var jsConfirmResult by remember { mutableStateOf<android.webkit.JsResult?>(null) }

    var jsPromptMessage by remember { mutableStateOf<String?>(null) }
    var jsPromptDefaultValue by remember { mutableStateOf("") }
    var jsPromptResult by remember { mutableStateOf<android.webkit.JsPromptResult?>(null) }
    var jsPromptInputText by remember { mutableStateOf("") }

    var pendingGeoOrigin by remember { mutableStateOf<String?>(null) }
    var pendingGeoCallback by remember { mutableStateOf<android.webkit.GeolocationPermissions.Callback?>(null) }
    var uploadCallback by remember { mutableStateOf<android.webkit.ValueCallback<Array<android.net.Uri>>?>(null) }
    var customVideoView by remember { mutableStateOf<android.view.View?>(null) }
    var customVideoCallback by remember { mutableStateOf<android.webkit.WebChromeClient.CustomViewCallback?>(null) }
    var isInputFocused by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var lastDownloadedUrl by remember { mutableStateOf<String?>(null) }
    var lastDownloadTimestamp by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            try {
                jsAlertResult?.cancel()
                jsConfirmResult?.cancel()
                jsPromptResult?.cancel()
                pendingGeoCallback?.invoke(pendingGeoOrigin, false, false)
                uploadCallback?.onReceiveValue(null)
                try {
                    customVideoCallback?.onCustomViewHidden()
                } catch (_: Throwable) {}
                customVideoView = null
                customVideoCallback = null
            } catch (_: Exception) {}
            try {
                webViewInstance?.onPause()
            } catch (_: Exception) {}
        }
    }
    var webViewRecreateKey by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var rendererCrashCount by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var lastCrashTimestamp by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var lastProgressChangeTime by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var lastProgressValue by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var isRendererUnresponsive by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webProgress by remember { mutableFloatStateOf(0f) }
    var isWebLoading by remember { mutableStateOf(false) }
    var viewSourceMode by remember { mutableStateOf(false) }
    val urlFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    LaunchedEffect(isWebLoading) {
        if (!isWebLoading) {
            (webViewInstance?.parent as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout)?.isRefreshing = false
        }
    }

    val urlInput by viewModel.urlInput.collectAsState()
    val kaspaAddressValidation by viewModel.kaspaAddressValidation.collectAsState()
    val isKaspaAddress = kaspaAddressValidation?.isValid == true
    val selectedProtocol by viewModel.selectedProtocol.collectAsState()
    val currentResource by viewModel.currentResource.collectAsState()
    val navigationSessionId by viewModel.navigationSessionId.collectAsState()

    var textFieldValue by remember {
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                text = urlInput,
                selection = androidx.compose.ui.text.TextRange(urlInput.length)
            )
        )
    }

    LaunchedEffect(isInputFocused) {
        if (isInputFocused) {
            try {
                urlFocusRequester.requestFocus()
            } catch (_: Exception) {}
            if (textFieldValue.text.isNotEmpty()) {
                textFieldValue = textFieldValue.copy(
                    selection = androidx.compose.ui.text.TextRange(0, textFieldValue.text.length)
                )
            }
        } else {
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(urlInput) {
        if (!isInputFocused && textFieldValue.text != urlInput) {
            textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                text = urlInput,
                selection = androidx.compose.ui.text.TextRange(urlInput.length)
            )
        }
    }

    LaunchedEffect(urlInput, currentResource?.title) {
        val title = currentResource?.title ?: if (urlInput.isEmpty()) "Home" else urlInput
        viewModel.updateActiveTabMetadata(urlInput, title)
    }

    fun toggleReaderMode() {
        if (isReaderMode) {
            isReaderMode = false
            isExtractingOrLoadingReaderMode = false
            val restoreUrl = if (originalPageUrl.isNotBlank() && !originalPageUrl.startsWith("data:")) originalPageUrl else urlInput
            if (restoreUrl.isNotBlank() && !restoreUrl.startsWith("data:")) {
                webViewInstance?.loadUrl(restoreUrl)
            } else {
                webViewInstance?.reload()
            }
            viewModel.setStatusMessage("Exited Reader Mode")
        } else {
            val currentUrl = webViewInstance?.url ?: urlInput
            if (currentUrl.isNotBlank() && !currentUrl.startsWith("data:")) {
                originalPageUrl = currentUrl
            }
            viewModel.setStatusMessage("Extracting reader content...")
            isExtractingOrLoadingReaderMode = true

            webViewInstance?.evaluateJavascript(com.example.network.KaspaReaderMode.JS_EXTRACT_CONTENT) { rawResult ->
                if (rawResult.isNullOrBlank() || rawResult == "null") {
                    isExtractingOrLoadingReaderMode = false
                    viewModel.setStatusMessage("Could not extract article content")
                    return@evaluateJavascript
                }
                try {
                    var clean = rawResult.trim()
                    if (clean.startsWith("\"") && clean.endsWith("\"")) {
                        clean = clean.substring(1, clean.length - 1)
                    }
                    val decodedJson = java.net.URLDecoder.decode(clean, "UTF-8")
                    val json = org.json.JSONObject(decodedJson)
                    val title = json.optString("title", "Reader View")
                    val leadImage = json.optString("leadImage", "")
                    val content = json.optString("content", "")

                    if (content.isBlank() || content.length < 30) {
                        isExtractingOrLoadingReaderMode = false
                        viewModel.setStatusMessage("No article content found on page")
                        return@evaluateJavascript
                    }

                    readerTitle = title
                    readerContent = content
                    readerLeadImage = leadImage
                    isReaderMode = true

                    val readerHtml = com.example.network.KaspaReaderMode.getReaderHtml(
                        title = title,
                        content = content,
                        leadImage = leadImage,
                        theme = "dark",
                        fontSizeSp = readerFontSize
                    )
                    webViewInstance?.loadDataWithBaseURL(originalPageUrl.ifBlank { urlInput }, readerHtml, "text/html", "UTF-8", null)
                    viewModel.setStatusMessage("Reader Mode Enabled")
                } catch (e: Exception) {
                    isExtractingOrLoadingReaderMode = false
                    viewModel.setStatusMessage("Unable to format page into Reader Mode")
                }
            }
        }
    }

    LaunchedEffect(isRendererUnresponsive) {
        if (isRendererUnresponsive) {
            val webView = webViewInstance
            if (webView != null) {
                try {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView.destroy()
                } catch (_: Exception) {}
                webViewInstance = null
                isRendererUnresponsive = false
                webViewRecreateKey++
            }
        }
    }

    LaunchedEffect(currentResource) {
        if (currentResource == null) {
            try {
                webViewInstance?.apply {
                    tag = null
                    stopLoading()
                    loadUrl("about:blank")
                }
            } catch (_: Exception) {}
            webViewInstance = null
            webProgress = 0f
            isWebLoading = false
        }
    }

    LaunchedEffect(navigationSessionId) {
        // Clear old modal prompts on fresh navigation to prevent background dialog deadlock
        jsAlertResult?.cancel()
        jsAlertResult = null
        jsAlertMessage = null
        jsConfirmResult?.cancel()
        jsConfirmResult = null
        jsConfirmMessage = null
        jsPromptResult?.cancel()
        jsPromptResult = null
        jsPromptMessage = null
    }

    val isLoading by viewModel.isLoading.collectAsState()
    val pinnedContents by viewModel.pinnedContents.collectAsState()

    val blockTrackers by viewModel.blockTrackers.collectAsState()
    val enableDownloads by viewModel.enableDownloads.collectAsState()
    val enableUploads by viewModel.enableUploads.collectAsState()
    val encryptedLocalStorage by viewModel.encryptedLocalStorage.collectAsState()
    val thirdPartyCookies by viewModel.thirdPartyCookies.collectAsState()
    val blockThirdPartyCookies by viewModel.blockThirdPartyCookies.collectAsState()
    val strictDecentralizedMode by viewModel.strictDecentralizedMode.collectAsState()
    val sendDntHeaders by viewModel.sendDntHeaders.collectAsState()
    val httpsOnlyMode by viewModel.httpsOnlyMode.collectAsState()
    val desktopModeEnabled by viewModel.desktopModeEnabled.collectAsState()
    val activeAccount by viewModel.activeAccount.collectAsState()
    val allAccounts by viewModel.allAccounts.collectAsState()
    val webAuthEnabled by viewModel.webAuthEnabled.collectAsState()
    val showWebAuthnRpIdDialog by viewModel.showWebAuthnRpIdDialog.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var showSecuritySheet by remember { mutableStateOf(false) }
    var showInstallSheet by remember { mutableStateOf(false) }
    var showPwaDialog by remember { mutableStateOf(false) }
    var showProtocolMenu by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var accountDialogInitialTab by remember { mutableStateOf(0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val installSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var pendingPermissionRequest by remember { mutableStateOf<android.webkit.PermissionRequest?>(null) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            pendingPermissionRequest?.let { req ->
                req.grant(req.resources)
            }
        } else {
            pendingPermissionRequest?.deny()
        }
        pendingPermissionRequest = null
    }

    val fileChooserLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val uris: Array<android.net.Uri>? = if (data != null) {
                val parsed = try {
                    android.webkit.WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
                } catch (_: Throwable) {
                    null
                }
                if (!parsed.isNullOrEmpty()) {
                    parsed
                } else if (data.data != null) {
                    arrayOf(data.data!!)
                } else if (data.clipData != null && data.clipData!!.itemCount > 0) {
                    val list = mutableListOf<android.net.Uri>()
                    for (i in 0 until data.clipData!!.itemCount) {
                        data.clipData!!.getItemAt(i).uri?.let { list.add(it) }
                    }
                    if (list.isNotEmpty()) list.toTypedArray() else null
                } else {
                    null
                }
            } else {
                null
            }
            uploadCallback?.onReceiveValue(uris)
        } else {
            uploadCallback?.onReceiveValue(null)
        }
        uploadCallback = null
    }

    LaunchedEffect(webAuthEnabled) {
        webViewInstance?.let { wv ->
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.WEB_AUTHENTICATION)) {
                val supportLevel = if (webAuthEnabled) {
                    androidx.webkit.WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
                } else {
                    androidx.webkit.WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_NONE
                }
                try {
                    androidx.webkit.WebSettingsCompat.setWebAuthenticationSupport(wv.settings, supportLevel)
                } catch (_: Throwable) {}
            }
            if (webAuthEnabled) {
                try {
                    wv.evaluateJavascript(
                        com.example.network.KaspaWebAuthnBridge.getInjectionScript(),
                        null
                    )
                } catch (_: Throwable) {}
            }
        }
    }

    val status = currentResource?.verificationStatus ?: VerificationStatus.UNVERIFIED
    val shieldColor = when (status) {
        VerificationStatus.VERIFIED_TAMPER_PROOF, VerificationStatus.MIRROR_MATCHED -> EmeraldMesh
        VerificationStatus.TAMPERED_HASH_MISMATCH -> RedTamper
        VerificationStatus.UNVERIFIED -> AmberCentral
    }

    val isHtml = remember(currentResource) {
        val res = currentResource ?: return@remember false
        res.contentType.contains("html", ignoreCase = true) ||
                res.content.trim().startsWith("<!DOCTYPE", ignoreCase = true) ||
                res.content.trim().startsWith("<html", ignoreCase = true) ||
                res.content.contains("</html>", ignoreCase = true) ||
                res.url.startsWith("http://", ignoreCase = true) ||
                res.url.startsWith("https://", ignoreCase = true) ||
                res.url.startsWith("kaspa://", ignoreCase = true) ||
                res.url.startsWith("ipfs://", ignoreCase = true) ||
                res.url.startsWith("dnet://", ignoreCase = true) ||
                res.url.startsWith("mesh://", ignoreCase = true) ||
                res.url.startsWith("kns://", ignoreCase = true)
    }

    androidx.activity.compose.BackHandler(enabled = isInputFocused || showTabSwitcher || canGoBack || currentResource != null) {
        if (isInputFocused) {
            isInputFocused = false
            focusManager.clearFocus()
            textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                text = urlInput,
                selection = androidx.compose.ui.text.TextRange(urlInput.length)
            )
        } else if (showTabSwitcher) {
            showTabSwitcher = false
        } else if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else if (currentResource != null) {
            viewModel.resetToHome()
        }
    }

    val currentBgColor: Color = remember(browserTheme, customBgColorHex) {
        when (browserTheme) {
            "oled_obsidian" -> Color(0xFF000000)
            "aurora_gradient" -> Color(0xFF0F172A)
            "cyber_gradient" -> Color(0xFF042F2E)
            "warm_sepia" -> Color(0xFF292524)
            "midnight_purple" -> Color(0xFF1E1035)
            "soft_slate" -> Color(0xFF1E293B)
            "pure_white" -> Color(0xFFFFFFFF)
            "custom_hex" -> try {
                val hex = customBgColorHex.trim().removePrefix("#")
                val parseHex = if (hex.length == 6) "FF$hex" else hex
                Color(android.graphics.Color.parseColor("#$parseHex"))
            } catch (_: Exception) {
                Color(0xFF12141C)
            }
            else -> Color(0xFF12141C) // "classic_dark"
        }
    }

    val backgroundModifier = remember(browserTheme, currentBgColor) {
        when (browserTheme) {
            "aurora_gradient" -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF090A0F))))
            "cyber_gradient" -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFF0B0F17), Color(0xFF042F2E), Color(0xFF090A0F))))
            else -> Modifier.background(currentBgColor)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(backgroundModifier)
    ) {
        // TOP BROWSER BAR: Directly starting with the search/URL bar
        val scope = rememberCoroutineScope()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("browser_address_bar"),
            color = SurfaceDark,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                if (isInputFocused) {
                    // FOCUSED SEARCH HEADER (Industry Standard Chrome/Safari/Brave UX)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isInputFocused = false
                                focusManager.clearFocus()
                                textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                    text = urlInput,
                                    selection = androidx.compose.ui.text.TextRange(urlInput.length)
                                )
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Cancel Search",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = SurfaceCard,
                            border = if (isKaspaAddress) {
                                androidx.compose.foundation.BorderStroke(2.dp, KaspaTea)
                            } else {
                                androidx.compose.foundation.BorderStroke(1.5.dp, ElectricCyan)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isKaspaAddress) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Kaspa Network Address Detected",
                                        tint = KaspaTea,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .testTag("kaspa_address_indicator_icon")
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (textFieldValue.text.isEmpty()) {
                                        Text(
                                            text = "Search or type URL",
                                            color = TextMuted,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = textFieldValue,
                                        onValueChange = { newValue ->
                                            textFieldValue = newValue
                                            if (urlInput != newValue.text) {
                                                viewModel.setUrlInput(newValue.text)
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(urlFocusRequester)
                                            .testTag("url_input_field"),
                                        singleLine = true,
                                        cursorBrush = SolidColor(if (isKaspaAddress) KaspaTea else ElectricCyan),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                        keyboardActions = KeyboardActions(onGo = {
                                            isInputFocused = false
                                            focusManager.clearFocus()
                                            viewSourceMode = false
                                            val input = textFieldValue.text.trim()
                                            if (input.isNotBlank()) {
                                                val normalized = viewModel.normalizeUrlOrQuery(input)
                                                viewModel.onUserSubmitUrl(normalized)
                                            }
                                        }),
                                        textStyle = TextStyle(
                                            fontFamily = FontFamily.SansSerif,
                                            fontSize = 14.sp,
                                            color = TextPrimary
                                        )
                                    )
                                }

                                if (textFieldValue.text.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            viewModel.setUrlInput("")
                                            textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                                "",
                                                selection = androidx.compose.ui.text.TextRange.Zero
                                            )
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear input",
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Compact visual feedback banner immediately under focused input
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isKaspaAddress && kaspaAddressValidation != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        kaspaAddressValidation?.let { valResult ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, start = 40.dp, end = 4.dp, bottom = 2.dp)
                                    .testTag("kaspa_address_detected_banner"),
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, KaspaTea.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Kaspa Valid Address",
                                            tint = KaspaTea,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Valid ${valResult.networkName} • ${valResult.addressType}",
                                            color = KaspaTea,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = if (valResult.checksumValid) "Checksum Verified ✓" else "Format Valid ✓",
                                        color = EmeraldMesh,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // NORMAL UNFOCUSED BROWSING HEADER
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.resetToHome() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))

                        // Main URL Pill Container
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = SurfaceCard,
                            border = if (isKaspaAddress) {
                                androidx.compose.foundation.BorderStroke(1.5.dp, KaspaTea)
                            } else {
                                androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clickable {
                                    isInputFocused = true
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Protocol Mode Indicator Icon
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = SurfaceDark,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { showProtocolMenu = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isKaspaAddress) {
                                                    Icons.Default.CheckCircle
                                                } else {
                                                    when (selectedProtocol) {
                                                        NetworkProtocol.HYBRID_COEXISTENCE -> Icons.Default.Hub
                                                        NetworkProtocol.DECENTRALIZED_P2P -> Icons.Default.Language
                                                        NetworkProtocol.CENTRALIZED_HTTP -> Icons.Default.Cloud
                                                    }
                                                },
                                                contentDescription = "Protocol Mode Logo",
                                                tint = if (isKaspaAddress) {
                                                    KaspaTea
                                                } else {
                                                    when (selectedProtocol) {
                                                        NetworkProtocol.HYBRID_COEXISTENCE -> ElectricCyan
                                                        NetworkProtocol.DECENTRALIZED_P2P -> EmeraldMesh
                                                        NetworkProtocol.CENTRALIZED_HTTP -> AmberCentral
                                                    }
                                                },
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showProtocolMenu,
                                        onDismissRequest = { showProtocolMenu = false },
                                        modifier = Modifier.background(SurfaceDark)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Hybrid Verified (Auto)", color = ElectricCyan, fontSize = 12.sp) },
                                            onClick = {
                                                viewModel.setProtocol(NetworkProtocol.HYBRID_COEXISTENCE)
                                                showProtocolMenu = false
                                                viewModel.resolveUrl()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("P2P Mesh (Zero-Trust)", color = EmeraldMesh, fontSize = 12.sp) },
                                            onClick = {
                                                viewModel.setProtocol(NetworkProtocol.DECENTRALIZED_P2P)
                                                showProtocolMenu = false
                                                viewModel.resolveUrl()
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (urlInput.isBlank()) "Search or type URL" else urlInput,
                                            color = if (urlInput.isBlank()) TextMuted else TextPrimary,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isKaspaAddress) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = KaspaTea.copy(alpha = 0.2f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, KaspaTea.copy(alpha = 0.6f)),
                                                modifier = Modifier.testTag("kaspa_unfocused_address_badge")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = KaspaTea,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "Kaspa Address",
                                                        color = KaspaTea,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (urlInput.isNotEmpty()) {
                                        val isBookmarked = remember(urlInput, bookmarks) {
                                            bookmarks.any { it.url == urlInput }
                                        }

                                        IconButton(
                                            onClick = {
                                                viewModel.toggleBookmark(urlInput, currentResource?.title ?: urlInput)
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                contentDescription = "Bookmark",
                                                tint = if (isBookmarked) ElectricCyan else TextMuted,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(2.dp))

                                        IconButton(
                                            onClick = {
                                                if (isWebLoading) {
                                                    webViewInstance?.stopLoading()
                                                    isWebLoading = false
                                                    viewModel.setIsLoading(false)
                                                } else {
                                                    val normalized = viewModel.normalizeUrlOrQuery(urlInput)
                                                    if ((normalized.startsWith("http://") || normalized.startsWith("https://")) && webViewInstance != null) {
                                                        webViewInstance?.reload()
                                                    } else {
                                                        viewModel.onUserSubmitUrl(normalized)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isWebLoading) Icons.Default.Close else Icons.Default.Refresh,
                                                contentDescription = if (isWebLoading) "Stop Loading" else "Reload",
                                                tint = if (isWebLoading) ElectricCyan else TextMuted,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(2.dp))

                                        IconButton(
                                            onClick = { toggleReaderMode() },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Article,
                                                contentDescription = if (isReaderMode) "Exit Reader Mode" else "Reader Mode",
                                                tint = if (isReaderMode) ElectricCyan else TextMuted,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = { viewModel.createNewTab() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Tab",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .border(1.2.dp, TextPrimary, RoundedCornerShape(5.dp))
                                .clickable { showTabSwitcher = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabs.size.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                        
                        Box {
                            IconButton(
                                onClick = { showBrowserMenu = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Menu",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showBrowserMenu,
                                onDismissRequest = { showBrowserMenu = false },
                                modifier = Modifier.background(SurfaceDark)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New Tab", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = ElectricCyan) },
                                    onClick = {
                                        showBrowserMenu = false
                                        viewModel.createNewTab()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Find in Page", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                                    onClick = {
                                        showFindInPage = true
                                        showBrowserMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Desktop site", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = if (desktopModeEnabled) ElectricCyan else TextMuted) },
                                    trailingIcon = {
                                        Checkbox(
                                            checked = desktopModeEnabled,
                                            onCheckedChange = null,
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = ElectricCyan,
                                                checkmarkColor = Color.Black,
                                                uncheckedColor = TextMuted
                                            )
                                        )
                                    },
                                    onClick = {
                                        showBrowserMenu = false
                                        val newMode = !desktopModeEnabled
                                        viewModel.toggleDesktopMode(newMode)
                                        webViewInstance?.let { wv ->
                                            val defaultDeviceUa = try {
                                                WebSettings.getDefaultUserAgent(context)
                                            } catch (_: Throwable) {
                                                null
                                            }
                                            val currentUa = if (newMode) {
                                                KaspaPrivacyEngine.getDesktopUserAgent(defaultDeviceUa)
                                            } else {
                                                KaspaPrivacyEngine.getMobileUserAgent(defaultDeviceUa)
                                            }
                                            wv.settings.userAgentString = currentUa
                                            wv.settings.useWideViewPort = true
                                            wv.settings.loadWithOverviewMode = newMode
                                            wv.setInitialScale(0)
                                            val rawUrl = wv.url ?: (if (urlInput.isNotBlank()) urlInput else currentResource?.url)
                                            val curUrl = if (newMode && !rawUrl.isNullOrBlank()) {
                                                KaspaPrivacyEngine.convertMobileUrlToDesktop(rawUrl)
                                            } else if (!newMode && !rawUrl.isNullOrBlank()) {
                                                KaspaPrivacyEngine.convertDesktopUrlToMobile(rawUrl)
                                            } else {
                                                rawUrl
                                            }
                                            if (!curUrl.isNullOrBlank() && curUrl != rawUrl) {
                                                viewModel.setUrlInput(curUrl)
                                            }
                                            if (!curUrl.isNullOrBlank() && (curUrl.startsWith("http://", ignoreCase = true) || curUrl.startsWith("https://", ignoreCase = true))) {
                                                wv.loadUrl(curUrl, KaspaPrivacyEngine.getDesktopHeaders(newMode, defaultDeviceUa))
                                            } else {
                                                wv.reload()
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share Page", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = TextMuted) },
                                    onClick = {
                                        showBrowserMenu = false
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, urlInput)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share URL"))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isReaderMode) "Exit Reader Mode" else "Reader Mode", color = TextPrimary) },
                                    leadingIcon = { Icon(if (isReaderMode) Icons.Default.Close else Icons.AutoMirrored.Filled.Article, contentDescription = null, tint = ElectricCyan) },
                                    onClick = {
                                        showBrowserMenu = false
                                        toggleReaderMode()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Customize Background", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = ElectricCyan) },
                                    onClick = {
                                        showBrowserMenu = false
                                        showThemeDialog = true
                                    }
                                )
                                 HorizontalDivider(color = SurfaceCardBorder, modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("Network (Mesh Radar)", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Hub, contentDescription = null, tint = ElectricCyan) },
                                    onClick = {
                                        showBrowserMenu = false
                                        viewModel.setTab(com.example.viewmodel.AppTab.MESH_RADAR)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings & Traffic Audit", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = ElectricCyan) },
                                    onClick = {
                                        showBrowserMenu = false
                                        viewModel.setTab(com.example.viewmodel.AppTab.TRAFFIC_AUDIT)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Library (History & Bookmarks)", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, tint = ElectricCyan) },
                                    onClick = {
                                        showBrowserMenu = false
                                        viewModel.setTab(com.example.viewmodel.AppTab.LIBRARY)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = CircleShape,
                            color = SurfaceCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable {
                                    accountDialogInitialTab = 0
                                    showAccountDialog = true
                                }
                                .testTag("account_identity_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (activeAccount?.accountType == "GOOGLE_ZK_BRIDGE") {
                                    GoogleLogoIcon(iconSize = 20.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = "Decentralized Account",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Find in Page Bar
                if (showFindInPage) {
                    FindInPageBar(
                        query = findInPageQuery,
                        onQueryChange = { 
                            findInPageQuery = it
                            webViewInstance?.findAllAsync(it)
                        },
                        onNext = { webViewInstance?.findNext(true) },
                        onPrevious = { webViewInstance?.findNext(false) },
                        onClose = { 
                            showFindInPage = false
                            findInPageQuery = ""
                            webViewInstance?.clearMatches()
                        }
                    )
                }

                // Web Page Loading Progress Bar (Fixed 2dp container - zero sticky header jitter or height shift)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                ) {
                    if (isWebLoading && webProgress in 0.01f..0.99f) {
                        LinearProgressIndicator(
                            progress = { webProgress },
                            modifier = Modifier.fillMaxSize(),
                            color = ElectricCyan,
                            trackColor = Color.Transparent
                        )
                    }
                }

                // Reader Mode Active Header Controls
                AnimatedVisibility(
                    visible = isReaderMode,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Article,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reader View", color = ElectricCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (readerFontSize > 12) {
                                            readerFontSize -= 2
                                            themePrefs.edit().putInt("reader_font_size", readerFontSize).apply()
                                            val html = com.example.network.KaspaReaderMode.getReaderHtml(
                                                readerTitle, readerContent, readerLeadImage, "dark", readerFontSize
                                            )
                                            webViewInstance?.loadDataWithBaseURL(urlInput, html, "text/html", "UTF-8", null)
                                        }
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Text("A-", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = {
                                        if (readerFontSize < 34) {
                                            readerFontSize += 2
                                            themePrefs.edit().putInt("reader_font_size", readerFontSize).apply()
                                            val html = com.example.network.KaspaReaderMode.getReaderHtml(
                                                readerTitle, readerContent, readerLeadImage, "dark", readerFontSize
                                            )
                                            webViewInstance?.loadDataWithBaseURL(urlInput, html, "text/html", "UTF-8", null)
                                        }
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Text("A+", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SurfaceDark,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            toggleReaderMode()
                                        }
                                ) {
                                    Text(
                                        text = "Exit",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    // BROWSER VIEWPORT: Full-Screen in-app rendering
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .then(backgroundModifier)
        ) {
            val resource = currentResource

            if (resource == null) {
                // Speed Dial / Empty State
                BrowserSpeedDial(
                    pinnedContents = pinnedContents,
                    viewModel = viewModel,
                    onNavigate = { url -> viewModel.resolveUrl(url) }
                )
            } else if (isHtml && !viewSourceMode) {
                // IN-APP WEB VIEW: Renders full web pages inside the browser itself!
                androidx.compose.runtime.key(webViewRecreateKey) {
                    AndroidView(
                        factory = { ctx ->
                        val browserBgColor = android.graphics.Color.WHITE

                        androidx.swiperefreshlayout.widget.SwipeRefreshLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(browserBgColor)
                            setColorSchemeColors(
                                android.graphics.Color.parseColor("#00E5FF"),
                                android.graphics.Color.parseColor("#10B981")
                            )
                            setProgressBackgroundColorSchemeColor(android.graphics.Color.parseColor("#131B2E"))

                            val webView = WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                if (rendererCrashCount > 0) {
                                    setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                                } else {
                                    setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                                }
                            setInitialScale(0)
                            overScrollMode = android.view.View.OVER_SCROLL_NEVER
                            isHapticFeedbackEnabled = false
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            scrollBarStyle = android.view.View.SCROLLBARS_INSIDE_OVERLAY
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                webViewRenderProcessClient = object : android.webkit.WebViewRenderProcessClient() {
                                    override fun onRenderProcessUnresponsive(view: WebView, renderer: android.webkit.WebViewRenderProcess?) {
                                        android.util.Log.w("BrowserGatewayScreen", "onRenderProcessUnresponsive triggered!")
                                        isRendererUnresponsive = true
                                    }

                                    override fun onRenderProcessResponsive(view: WebView, renderer: android.webkit.WebViewRenderProcess?) {
                                        android.util.Log.i("BrowserGatewayScreen", "onRenderProcessResponsive triggered!")
                                        isRendererUnresponsive = false
                                    }
                                }
                            }
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                @Suppress("DEPRECATION")
                                databaseEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                setGeolocationEnabled(false)
                                allowFileAccess = false
                                allowContentAccess = true
                                @Suppress("DEPRECATION")
                                allowFileAccessFromFileURLs = false
                                @Suppress("DEPRECATION")
                                allowUniversalAccessFromFileURLs = false
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                textZoom = 100
                                javaScriptCanOpenWindowsAutomatically = false
                                setSupportMultipleWindows(false)
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                                // Dynamic theme rendering based on system theme is disabled to allow sites to render their own CSS
                                // FORCE_DARK and ALGORITHMIC_DARKENING removed to prevent "black page" issues.
                                
                                // Native FIDO2 / WebAuthn Passkeys support via AndroidX Webkit
                                if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.WEB_AUTHENTICATION)) {
                                    val supportLevel = if (webAuthEnabled) {
                                        androidx.webkit.WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
                                    } else {
                                        androidx.webkit.WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_NONE
                                    }
                                    try {
                                        androidx.webkit.WebSettingsCompat.setWebAuthenticationSupport(this, supportLevel)
                                    } catch (_: Throwable) {}
                                }

                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    safeBrowsingEnabled = true
                                }
                                layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                                cacheMode = WebSettings.LOAD_DEFAULT
                                loadsImagesAutomatically = true
                                blockNetworkImage = false
                                blockNetworkLoads = false
                                offscreenPreRaster = true
                                val defaultDeviceUa = try {
                                    WebSettings.getDefaultUserAgent(ctx)
                                } catch (_: Throwable) {
                                    null
                                }
                                userAgentString = if (desktopModeEnabled) {
                                    KaspaPrivacyEngine.getDesktopUserAgent(defaultDeviceUa)
                                } else {
                                    KaspaPrivacyEngine.getMobileUserAgent(defaultDeviceUa)
                                }
                            }

                            // Early document-start JavaScript injection for WebGL stability, privacy shield, and Web3 wallet bridge
                            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
                                try {
                                    androidx.webkit.WebViewCompat.addDocumentStartJavaScript(
                                        this,
                                        KaspaPrivacyEngine.getPrivacyShieldScript(
                                            safeGpuMode = rendererCrashCount > 0,
                                            isDesktop = desktopModeEnabled,
                                            baseUa = defaultDeviceUa
                                        ),
                                        setOf("*")
                                    )
                                } catch (_: Throwable) {}
                            }

                            // Attach WebAuthn FIDO2 / Passkey Javascript Interface & Credential Manager Bridge
                            val webAuthnBridge = com.example.network.KaspaWebAuthnBridge(
                                context = ctx,
                                webViewProvider = { webViewInstance },
                                viewModel = viewModel,
                                scope = scope
                            )
                            addJavascriptInterface(webAuthnBridge, "KaspaWebAuthnBridge")

                            // Long-press context menu for links and images (Chrome-style Hub trigger)
                            setOnLongClickListener { v ->
                                val wv = v as? WebView
                                val result = wv?.hitTestResult
                                if (result != null) {
                                    val type = result.type
                                    val extra = result.extra
                                    val isImg = type == WebView.HitTestResult.IMAGE_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                                    
                                    if (type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                                        // For linked image, fetch both link and image URL asynchronously via hit test message
                                        val message = android.os.Handler(android.os.Looper.getMainLooper()).obtainMessage()
                                        message.target = object : android.os.Handler(android.os.Looper.getMainLooper()) {
                                            override fun handleMessage(msg: android.os.Message) {
                                                val src = msg.data.getString("src")
                                                val url = msg.data.getString("url")
                                                val title = msg.data.getString("title")
                                                longPressedImageUrl = if (!src.isNullOrBlank()) src else extra
                                                longPressedLinkUrl = if (!url.isNullOrBlank()) url else null
                                                longPressedImageTitle = if (!title.isNullOrBlank()) title else null
                                                isLongPressedImage = true
                                                showLinkContextMenu = true
                                            }
                                        }
                                        wv.requestFocusNodeHref(message)
                                        return@setOnLongClickListener true
                                    } else if (type == WebView.HitTestResult.IMAGE_TYPE && !extra.isNullOrBlank()) {
                                        longPressedImageUrl = extra
                                        longPressedLinkUrl = null
                                        longPressedImageTitle = null
                                        isLongPressedImage = true
                                        showLinkContextMenu = true
                                        return@setOnLongClickListener true
                                    } else if (!extra.isNullOrBlank() && (
                                        type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                                        type == WebView.HitTestResult.GEO_TYPE ||
                                        type == WebView.HitTestResult.EMAIL_TYPE ||
                                        type == WebView.HitTestResult.PHONE_TYPE
                                    )) {
                                        longPressedImageUrl = null
                                        longPressedLinkUrl = extra
                                        longPressedImageTitle = null
                                        isLongPressedImage = false
                                        showLinkContextMenu = true
                                        return@setOnLongClickListener true
                                    }
                                }
                                false
                            }

                            // Touch & Gesture navigation (Edge swipe Back/Forward)
                            val gestureDetector = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
                                override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                                    if (e1 == null) return false
                                    val deltaX = e2.x - e1.x
                                    val deltaY = e2.y - e1.y
                                    if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 150 && Math.abs(velocityX) > 150) {
                                        if (deltaX > 0) {
                                            if (canGoBack()) {
                                                goBack()
                                                return true
                                            }
                                        } else {
                                            if (canGoForward()) {
                                                goForward()
                                                return true
                                            }
                                        }
                                    }
                                    return false
                                }
                            })
                            @android.annotation.SuppressLint("ClickableViewAccessibility")
                            setOnTouchListener { _, event ->
                                gestureDetector.onTouchEvent(event)
                                false
                            }

                            val wv = this
                            android.webkit.CookieManager.getInstance().apply {
                                setAcceptCookie(true)
                                setAcceptThirdPartyCookies(wv, thirdPartyCookies)
                            }
                            setBackgroundColor(android.graphics.Color.WHITE)
                            isHapticFeedbackEnabled = false

                            fun handleDeepLinkOrNavigate(targetWv: WebView?, rawUrl: String, hasGesture: Boolean): Boolean {
                                val targetView = targetWv ?: wv
                                val targetCtx = targetView?.context ?: context
                                val cleanUrl = rawUrl.trim()
                                val currentPageUrl = targetView?.url ?: viewModel.currentResource.value?.url ?: viewModel.urlInput.value
                                val currentHost = runCatching { Uri.parse(currentPageUrl).host?.lowercase() }.getOrNull() ?: ""
                                val targetUri = runCatching { Uri.parse(cleanUrl) }.getOrNull()
                                val targetHost = targetUri?.host?.lowercase() ?: ""
                                val targetPath = targetUri?.path?.lowercase() ?: ""
                                val targetScheme = targetUri?.scheme?.lowercase() ?: ""
                                
                                return if (cleanUrl.startsWith("ipfs://", ignoreCase = true) ||
                                    cleanUrl.startsWith("mesh://", ignoreCase = true) ||
                                    cleanUrl.startsWith("dweb://", ignoreCase = true) ||
                                    cleanUrl.startsWith("p2p://", ignoreCase = true) ||
                                    cleanUrl.startsWith("kas://", ignoreCase = true) ||
                                    cleanUrl.startsWith("kaspa://", ignoreCase = true) ||
                                    cleanUrl.startsWith("dnet://", ignoreCase = true) ||
                                    cleanUrl.startsWith("kns://", ignoreCase = true) ||
                                    cleanUrl.startsWith("hyper://", ignoreCase = true)
                                ) {
                                    viewModel.resolveUrl(cleanUrl)
                                    true
                                } else if (!hasGesture && (
                                    cleanUrl.startsWith("market://", ignoreCase = true) ||
                                    targetHost.contains("play.google.com") ||
                                    targetHost.contains("apps.apple.com") ||
                                    targetHost.contains("itunes.apple.com") ||
                                    targetHost.contains("onelink.me") ||
                                    targetHost.contains("adjust.com") ||
                                    targetHost.contains("smart.link") ||
                                    targetHost.contains("branch.io") ||
                                    targetHost.contains("app.link")
                                )) {
                                    // Suppress non-gesture background redirects attempting to force store downloads or install trackers
                                    true
                                } else if (cleanUrl.startsWith("intent://", ignoreCase = true)) {
                                    try {
                                        val parsedIntent = Intent.parseUri(cleanUrl, Intent.URI_INTENT_SCHEME).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            component = null
                                        }
                                        parsedIntent.selector?.let {
                                            it.component = null
                                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        try {
                                            targetCtx.startActivity(parsedIntent)
                                            true
                                        } catch (_: Exception) {
                                            val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
                                            if (!fallbackUrl.isNullOrEmpty() &&
                                                !fallbackUrl.startsWith("market://", ignoreCase = true) &&
                                                !fallbackUrl.contains("play.google.com/store", ignoreCase = true)
                                            ) {
                                                viewModel.setUrlInput(fallbackUrl)
                                                targetView?.loadUrl(fallbackUrl)
                                            }
                                            true
                                        }
                                    } catch (_: Exception) {
                                        true
                                    }
                                } else if (!cleanUrl.startsWith("http://", ignoreCase = true) &&
                                    !cleanUrl.startsWith("https://", ignoreCase = true) &&
                                    !cleanUrl.startsWith("about:", ignoreCase = true) &&
                                    !cleanUrl.startsWith("data:", ignoreCase = true) &&
                                    !cleanUrl.startsWith("javascript:", ignoreCase = true) &&
                                    !cleanUrl.startsWith("blob:", ignoreCase = true)
                                ) {
                                    if (targetScheme != "market" && !cleanUrl.startsWith("market://", ignoreCase = true)) {
                                        try {
                                            val customIntent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            targetCtx.startActivity(customIntent)
                                        } catch (_: Exception) {}
                                    }
                                    true
                                } else {
                                    // HTTP / HTTPS URL: Check if an installed native application handles this link when clicked by user
                                    var redirectedToNativeApp = false
                                    if (hasGesture) {
                                        try {
                                            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            val pm = targetCtx.packageManager
                                            val resolveList = pm.queryIntentActivities(appIntent, 0)
                                            val ourPkg = targetCtx.packageName
                                            val nativeApp = resolveList.firstOrNull { ri ->
                                                val pkg = ri.activityInfo?.packageName?.lowercase() ?: ""
                                                pkg.isNotEmpty() && pkg != ourPkg &&
                                                    !pkg.contains("chrome") &&
                                                    !pkg.contains("browser") &&
                                                    !pkg.contains("webview") &&
                                                    !pkg.contains("firefox") &&
                                                    !pkg.contains("opera") &&
                                                    !pkg.contains("duckduckgo")
                                            }
                                            if (nativeApp != null) {
                                                appIntent.setPackage(nativeApp.activityInfo.packageName)
                                                targetCtx.startActivity(appIntent)
                                                redirectedToNativeApp = true
                                            }
                                        } catch (_: Exception) {}
                                    }
                                    redirectedToNativeApp
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onJsAlert(
                                    view: WebView?,
                                    url: String?,
                                    message: String?,
                                    result: android.webkit.JsResult?
                                ): Boolean {
                                    jsAlertMessage = message
                                    jsAlertResult = result
                                    return true
                                }

                                override fun onJsConfirm(
                                    view: WebView?,
                                    url: String?,
                                    message: String?,
                                    result: android.webkit.JsResult?
                                ): Boolean {
                                    jsConfirmMessage = message
                                    jsConfirmResult = result
                                    return true
                                }

                                override fun onJsPrompt(
                                    view: WebView?,
                                    url: String?,
                                    message: String?,
                                    defaultValue: String?,
                                    result: android.webkit.JsPromptResult?
                                ): Boolean {
                                    jsPromptMessage = message
                                    jsPromptDefaultValue = defaultValue ?: ""
                                    jsPromptInputText = defaultValue ?: ""
                                    jsPromptResult = result
                                    return true
                                }

                                override fun onJsBeforeUnload(
                                    view: WebView?,
                                    url: String?,
                                    message: String?,
                                    result: android.webkit.JsResult?
                                ): Boolean {
                                    result?.confirm()
                                    return true
                                }

                                override fun onGeolocationPermissionsShowPrompt(
                                    origin: String?,
                                    callback: android.webkit.GeolocationPermissions.Callback?
                                ) {
                                    pendingGeoOrigin = origin
                                    pendingGeoCallback = callback
                                }
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    val now = System.currentTimeMillis()
                                    if (newProgress != lastProgressValue) {
                                        lastProgressValue = newProgress
                                        lastProgressChangeTime = now
                                    }
                                    webProgress = newProgress / 100f
                                    if (newProgress >= 95) {
                                        isWebLoading = false
                                        viewModel.setIsLoading(false)
                                    } else {
                                        isWebLoading = true
                                    }
                                    if (newProgress == 100) {
                                        webProgress = 1.0f
                                        isWebLoading = false
                                        viewModel.setIsLoading(false)
                                        val cur = view?.url ?: ""
                                        if (cur.isNotBlank() && !cur.startsWith("data:") && !cur.startsWith("about:")) {
                                            viewModel.recordBrowserTraffic(cur, 220 * 1024L)
                                        }
                                    }
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    title?.let {
                                        if (it.isNotBlank() && !it.startsWith("http://") && !it.startsWith("https://")) {
                                            viewModel.updateResourceTitle(it)
                                        }
                                    }
                                }

                                override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                                    if (request != null) {
                                        pendingPermissionRequest = request
                                        val androidPermissions = mutableListOf<String>()
                                        if (request.resources.contains(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                                            androidPermissions.add(android.Manifest.permission.RECORD_AUDIO)
                                        }
                                        if (request.resources.contains(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                                            androidPermissions.add(android.Manifest.permission.CAMERA)
                                        }
                                        if (androidPermissions.isNotEmpty()) {
                                            permissionLauncher.launch(androidPermissions.toTypedArray())
                                        } else {
                                            request.grant(request.resources)
                                            pendingPermissionRequest = null
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
                                    // Strictly reject automatic window opening without explicit user gesture (e.g. scroll popups)
                                    if (!isUserGesture) return false

                                    val tempWebView = WebView(view.context).apply {
                                        settings.javaScriptEnabled = true
                                        settings.setSupportMultipleWindows(false)
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(wv: WebView?, request: WebResourceRequest?): Boolean {
                                                val target = request?.url?.toString() ?: return false
                                                val gesture = request?.hasGesture() ?: isUserGesture
                                                val intercepted = handleDeepLinkOrNavigate(view, target, gesture)
                                                if (!intercepted) {
                                                    viewModel.openUrlInBrowser(target, isExternal = true)
                                                }
                                                return true
                                            }

                                            @Deprecated("Deprecated in Java")
                                            override fun shouldOverrideUrlLoading(wv: WebView?, url: String?): Boolean {
                                                val target = url ?: return false
                                                val intercepted = handleDeepLinkOrNavigate(view, target, isUserGesture)
                                                if (!intercepted) {
                                                    viewModel.openUrlInBrowser(target, isExternal = true)
                                                }
                                                return true
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

                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    uploadCallback?.onReceiveValue(null)
                                    if (!enableUploads) {
                                        viewModel.setStatusMessage("File uploads are disabled in Settings")
                                        filePathCallback?.onReceiveValue(null)
                                        return false
                                    }
                                    uploadCallback = filePathCallback
                                    return try {
                                        val baseIntent = try {
                                            fileChooserParams?.createIntent()
                                        } catch (_: Throwable) {
                                            null
                                        }
                                        val intent = baseIntent ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                            val rawAccept = fileChooserParams?.acceptTypes?.filter { !it.isNullOrBlank() }
                                            if (!rawAccept.isNullOrEmpty()) {
                                                if (rawAccept.size == 1) {
                                                    type = rawAccept[0]
                                                } else {
                                                    type = "*/*"
                                                    putExtra(Intent.EXTRA_MIME_TYPES, rawAccept.toTypedArray())
                                                }
                                            } else {
                                                type = "*/*"
                                            }
                                        }
                                        if (fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                                            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                        }
                                        val title = fileChooserParams?.title?.takeIf { it.isNotBlank() } ?: "Choose file to upload"
                                        val chooser = Intent.createChooser(intent, title)
                                        fileChooserLauncher.launch(chooser)
                                        true
                                    } catch (_: Exception) {
                                        uploadCallback?.onReceiveValue(null)
                                        uploadCallback = null
                                        false
                                    }
                                }

                                override fun onShowCustomView(
                                    view: android.view.View?,
                                    callback: WebChromeClient.CustomViewCallback?
                                ) {
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

                                override fun getDefaultVideoPoster(): android.graphics.Bitmap? {
                                    return try {
                                        android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                                    } catch (_: Throwable) {
                                        null
                                    }
                                }
                            }

                            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                                if (!enableDownloads) {
                                    viewModel.setStatusMessage("Downloads are disabled in Settings")
                                    return@setDownloadListener
                                }
                                val now = System.currentTimeMillis()
                                if (url == lastDownloadedUrl && (now - lastDownloadTimestamp < 15000L)) {
                                    return@setDownloadListener
                                }
                                lastDownloadedUrl = url
                                lastDownloadTimestamp = now

                                val currentWvUrl = wv.url
                                if (!currentWvUrl.isNullOrBlank() && currentWvUrl != url) {
                                    viewModel.updateCurrentUrl(currentWvUrl)
                                }
                                try {
                                    val filename = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
                                    val fallbackId = System.currentTimeMillis()
                                    val downloadId = try {
                                        val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
                                            setMimeType(mimetype)
                                            addRequestHeader("cookie", android.webkit.CookieManager.getInstance().getCookie(url))
                                            addRequestHeader("User-Agent", userAgent)
                                            setDescription("Downloading file...")
                                            setTitle(filename)
                                            setAllowedOverMetered(true)
                                            setAllowedOverRoaming(true)
                                            setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI or android.app.DownloadManager.Request.NETWORK_MOBILE)
                                            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)
                                        }
                                        val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                        dm.enqueue(request)
                                    } catch (_: Exception) {
                                        fallbackId
                                    }
                                    viewModel.addDownload(downloadId, filename, url)
                                    viewModel.startDownload(context, downloadId, url, filename)
                                    viewModel.setStatusMessage("Download started: $filename")
                                } catch (e: Exception) {
                                    viewModel.setStatusMessage("Download failed: ${e.message}")
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onSafeBrowsingHit(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    threatType: Int,
                                    callback: android.webkit.SafeBrowsingResponse?
                                ) {
                                    val host = request?.url?.host ?: "unknown"
                                    viewModel.logBlockedTracker("[MALICIOUS PHISHING DETECTED] $host")
                                    viewModel.setStatusMessage("Blocked access to potentially harmful phishing/malware site: $host")
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                                    callback?.backToSafety(true)
                                } else {
                                    webViewInstance?.loadUrl("about:blank")
                                }
                                }
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    if (!isReaderMode && !isExtractingOrLoadingReaderMode) {
                                        // normal page load
                                    } else if (url != null && !url.startsWith("data:") && url != originalPageUrl) {
                                        isReaderMode = false
                                        isExtractingOrLoadingReaderMode = false
                                    }
                                    isWebLoading = true
                                    webProgress = 0.05f
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                    url?.let {
                                        val isNavigableUrl = it.startsWith("http://", ignoreCase = true) ||
                                            it.startsWith("https://", ignoreCase = true) ||
                                            it.startsWith("kas://", ignoreCase = true) ||
                                            it.startsWith("kaspa://", ignoreCase = true) ||
                                            it.startsWith("ipfs://", ignoreCase = true) ||
                                            it.startsWith("dnet://", ignoreCase = true) ||
                                            it.startsWith("mesh://", ignoreCase = true) ||
                                            it.startsWith("dweb://", ignoreCase = true) ||
                                            it.startsWith("p2p://", ignoreCase = true) ||
                                            it.startsWith("kns://", ignoreCase = true) ||
                                            it.startsWith("hyper://", ignoreCase = true)

                                        if (isNavigableUrl && it != urlInput) {
                                            viewModel.updateCurrentUrl(it)
                                            viewModel.recordBrowserTraffic(it, 160 * 1024L)
                                        }
                                    }

                                    view?.evaluateJavascript(
                                        KaspaPrivacyEngine.getPrivacyShieldScript(
                                            safeGpuMode = rendererCrashCount > 0,
                                            isDesktop = desktopModeEnabled,
                                            baseUa = defaultDeviceUa
                                        ),
                                        null
                                    )

                                    if (webAuthEnabled) {
                                        view?.evaluateJavascript(
                                            com.example.network.KaspaWebAuthnBridge.getInjectionScript(),
                                            null
                                        )
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isWebLoading = false
                                    (view?.parent as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout)?.isRefreshing = false
                                    webProgress = 1.0f
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                    url?.let {
                                        val isNavigableUrl = it.startsWith("http://", ignoreCase = true) ||
                                            it.startsWith("https://", ignoreCase = true) ||
                                            it.startsWith("kas://", ignoreCase = true) ||
                                            it.startsWith("kaspa://", ignoreCase = true) ||
                                            it.startsWith("ipfs://", ignoreCase = true) ||
                                            it.startsWith("dnet://", ignoreCase = true) ||
                                            it.startsWith("mesh://", ignoreCase = true) ||
                                            it.startsWith("dweb://", ignoreCase = true) ||
                                            it.startsWith("p2p://", ignoreCase = true) ||
                                            it.startsWith("kns://", ignoreCase = true) ||
                                            it.startsWith("hyper://", ignoreCase = true)

                                        if (isNavigableUrl) {
                                            viewModel.updateCurrentUrl(it)
                                            viewModel.addToHistory(it, view?.title ?: it)
                                        }
                                    }

                                    view?.evaluateJavascript(
                                        KaspaPrivacyEngine.getPrivacyShieldScript(
                                            safeGpuMode = rendererCrashCount > 0,
                                            isDesktop = desktopModeEnabled,
                                            baseUa = defaultDeviceUa
                                        ),
                                        null
                                    )

                                    if (webAuthEnabled) {
                                        view?.evaluateJavascript(
                                            com.example.network.KaspaWebAuthnBridge.getInjectionScript(),
                                            null
                                        )
                                    }

                                    if (blockTrackers) {
                                        view?.evaluateJavascript(
                                            com.example.network.UBlockEngine.getCosmeticHidingCss(),
                                            null
                                        )
                                    }

                                    // Automatic PWA Manifest and Metadata extraction for Device Installation
                                    val currentLoadedUrl = url ?: ""
                                    if (currentLoadedUrl.startsWith("http://") || currentLoadedUrl.startsWith("https://") || currentLoadedUrl.startsWith("kas://")) {
                                        view?.evaluateJavascript("""
                                            (function() {
                                                try {
                                                    var t = document.title || '';
                                                    var m = document.querySelector('link[rel="manifest"]');
                                                    var manifestUrl = m ? m.href : '';
                                                    var iconEl = document.querySelector('link[rel="apple-touch-icon"]') || 
                                                                 document.querySelector('link[rel="icon"][sizes="512x512"]') || 
                                                                 document.querySelector('link[rel="icon"][sizes="192x192"]') || 
                                                                 document.querySelector('link[rel="icon"]');
                                                    var iconUrl = iconEl ? iconEl.href : '';
                                                    return JSON.stringify({ 
                                                        title: t, 
                                                        icon: iconUrl, 
                                                        manifestUrl: manifestUrl, 
                                                        hasManifest: m !== null 
                                                    });
                                                } catch(e) { return '{}'; }
                                            })();
                                        """.trimIndent()) { result ->
                                            try {
                                                if (!result.isNullOrBlank() && result != "null") {
                                                    val raw = if (result.startsWith("\"") && result.endsWith("\"")) {
                                                        org.json.JSONTokener(result).nextValue().toString()
                                                    } else result
                                                    val json = org.json.JSONObject(raw)
                                                    val t = json.optString("title", "")
                                                    val ic = json.optString("icon", "")
                                                    val mf = json.optString("manifestUrl", "")
                                                    val hasM = json.optBoolean("hasManifest", false)
                                                    viewModel.setDetectedPwa(
                                                        title = if (t.isNotBlank()) t else (view?.title ?: ""),
                                                        url = currentLoadedUrl,
                                                        iconUrl = if (ic.isNotBlank()) ic else null,
                                                        manifestUrl = if (mf.isNotBlank()) mf else null,
                                                        hasManifest = hasM
                                                    )
                                                }
                                            } catch (_: Exception) {
                                                viewModel.setDetectedPwa(view?.title ?: "", currentLoadedUrl, null, null, false)
                                            }
                                        }
                                    }
                                }

                                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                    url?.let {
                                        val isNavigableUrl = it.startsWith("http://", ignoreCase = true) ||
                                            it.startsWith("https://", ignoreCase = true) ||
                                            it.startsWith("kas://", ignoreCase = true) ||
                                            it.startsWith("kaspa://", ignoreCase = true) ||
                                            it.startsWith("ipfs://", ignoreCase = true) ||
                                            it.startsWith("dnet://", ignoreCase = true) ||
                                            it.startsWith("mesh://", ignoreCase = true) ||
                                            it.startsWith("dweb://", ignoreCase = true) ||
                                            it.startsWith("p2p://", ignoreCase = true) ||
                                            it.startsWith("kns://", ignoreCase = true) ||
                                            it.startsWith("hyper://", ignoreCase = true)

                                        if (isNavigableUrl) {
                                            viewModel.updateCurrentUrl(it)
                                        }
                                    }
                                }

                                override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                                    val didCrash = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        detail?.didCrash() == true
                                    } else {
                                        true
                                    }
                                    android.util.Log.w("BrowserGatewayScreen", "WebView renderer process gone (didCrash=$didCrash)")
                                    try {
                                        (view?.parent as? ViewGroup)?.removeView(view)
                                        view?.destroy()
                                    } catch (_: Exception) {}
                                    webViewInstance = null

                                    val now = System.currentTimeMillis()
                                    if (now - lastCrashTimestamp < 30_000L) {
                                        rendererCrashCount++
                                    } else {
                                        rendererCrashCount = 1
                                    }
                                    lastCrashTimestamp = now

                                    if (rendererCrashCount >= 3) {
                                        viewModel.setStatusMessage("Heavy graphics halted to prevent crash loop")
                                        viewModel.resolveUrl("about:blank")
                                    } else {
                                        viewModel.setStatusMessage("Graphics rendering process restored in Safe Mode")
                                    }
                                    webViewRecreateKey++
                                    return true
                                }

                                 override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    if (request == null) return null

                                    val reqUrl = request.url?.toString() ?: return null
                                    val reqUrlLower = reqUrl.lowercase()
                                    val path = request.url?.path?.lowercase() ?: ""

                                    // CRITICAL: NEVER block the main frame navigation (the website itself).
                                    if (request.isForMainFrame) {
                                        return null
                                    }

                                    // CRITICAL: Never block styles, fonts, images, image videos (thumbnails/posters), or video/audio media streams
                                    val isStyleOrFont = path.endsWith(".css") || path.endsWith(".woff") || path.endsWith(".woff2") ||
                                        path.endsWith(".ttf") || path.endsWith(".otf") || path.endsWith(".eot")
                                    val isImageOrGraphic = path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                                        path.endsWith(".webp") || path.endsWith(".gif") || path.endsWith(".svg") ||
                                        path.endsWith(".ico") || path.endsWith(".bmp") || path.endsWith(".avif") ||
                                        path.endsWith(".heic") || path.endsWith(".heif") || path.endsWith(".tiff")
                                    val isVideoOrAudioStream = path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m4v") ||
                                        path.endsWith(".m4a") || path.endsWith(".m4s") || path.endsWith(".mp3") ||
                                        path.endsWith(".ogg") || path.endsWith(".ogv") || path.endsWith(".ts") ||
                                        path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".flv") ||
                                        path.endsWith(".avi") || path.endsWith(".mov") || path.endsWith(".wav") ||
                                        path.endsWith(".aac")

                                    val acceptHeader = request.requestHeaders?.get("Accept")?.lowercase()
                                        ?: request.requestHeaders?.get("accept")?.lowercase()
                                    val isMediaAccept = acceptHeader?.contains("image/") == true ||
                                        acceptHeader?.contains("video/") == true ||
                                        acceptHeader?.contains("audio/") == true ||
                                        acceptHeader?.contains("media") == true ||
                                        acceptHeader?.contains("text/css") == true

                                    val isMediaStreamEndpoint = reqUrlLower.contains("videoplayback") ||
                                        reqUrlLower.contains("/thumb") ||
                                        reqUrlLower.contains("/poster") ||
                                        reqUrlLower.contains("/video/") ||
                                        reqUrlLower.contains("/videos/") ||
                                        reqUrlLower.contains("/images/") ||
                                        reqUrlLower.contains("/img/") ||
                                        reqUrlLower.contains("stream") ||
                                        reqUrlLower.contains("blob:")

                                    if (isVideoOrAudioStream || isMediaStreamEndpoint) {
                                        return null
                                    }



                                    if (com.example.network.WebViewAssetLruCache.shouldCache(reqUrl, request.method, request.isForMainFrame)) {
                                        val cachedResponse = com.example.network.WebViewAssetLruCache.get(reqUrl)
                                        if (cachedResponse != null) {
                                            return cachedResponse
                                        }
                                    }

                                    if (isStyleOrFont || isImageOrGraphic || isMediaAccept) {
                                        return null
                                    }

                                    if (reqUrlLower.contains("undefined") || reqUrlLower.contains("null")) {
                                        return null
                                    }

                                    // Thread-safe first-party detection without calling view.url on the background thread
                                    val referer = request.requestHeaders?.get("Referer") ?: request.requestHeaders?.get("referer")
                                    val pageHost = referer?.let { runCatching { android.net.Uri.parse(it).host }.getOrNull() }?.lowercase()
                                        ?: viewModel.urlInput.value.let { runCatching { android.net.Uri.parse(it).host }.getOrNull() }?.lowercase()
                                    val resourceHost = request.url?.host?.lowercase()

                                    val pageRoot = pageHost?.let { h ->
                                        val parts = h.split('.')
                                        if (parts.size >= 2) "${parts[parts.size - 2]}.${parts[parts.size - 1]}" else h
                                    }
                                    val resourceRoot = resourceHost?.let { h ->
                                        val parts = h.split('.')
                                        if (parts.size >= 2) "${parts[parts.size - 2]}.${parts[parts.size - 1]}" else h
                                    }

                                    val isFirstParty = pageHost != null && resourceHost != null &&
                                        (resourceHost == pageHost || resourceHost.endsWith(".$pageHost") || (pageRoot != null && pageRoot == resourceRoot))

                                    // Never block Google Account login, OAuth tokens, user profiles, or authentication requests
                                    val isGoogleAccountRequest = KaspaPrivacyEngine.isGoogleAccountOrAuthUrl(reqUrl) ||
                                        (resourceHost != null && KaspaPrivacyEngine.isGoogleAccountDomain(resourceHost)) ||
                                        (pageHost != null && KaspaPrivacyEngine.isGoogleAccountDomain(pageHost))

                                    if (isGoogleAccountRequest) {
                                        return null
                                    }

                                    if (blockTrackers && !isFirstParty && KaspaPrivacyEngine.isTrackerOrAd(reqUrl)) {
                                        val host = resourceHost ?: reqUrl
                                        viewModel.logBlockedTracker(host)
                                        return WebResourceResponse(
                                            "text/plain",
                                            "UTF-8",
                                            403,
                                            "Blocked by uBlock Engine",
                                            mapOf("Access-Control-Allow-Origin" to "*"),
                                            java.io.ByteArrayInputStream(ByteArray(0))
                                        )
                                    }
                                    return null
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val targetUrl = request?.url?.toString() ?: return false
                                    val hasGesture = request?.hasGesture() ?: false

                                    if (strictDecentralizedMode && targetUrl.startsWith("http://", ignoreCase = true)) {
                                        viewModel.setStatusMessage("Blocked unencrypted http:// URL under Strict Pure Decentralized Mode")
                                        return true
                                    }

                                    if (httpsOnlyMode && targetUrl.startsWith("http://", ignoreCase = true)) {
                                        val httpsUrl = targetUrl.replaceFirst("http://", "https://", ignoreCase = true)
                                        view?.loadUrl(httpsUrl)
                                        return true
                                    }

                                    val handled = handleDeepLinkOrNavigate(view, targetUrl, hasGesture)
                                    if (handled) {
                                        return true
                                    }

                                    return false
                                }

                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    val targetUrl = url ?: return false

                                    if (strictDecentralizedMode && targetUrl.startsWith("http://", ignoreCase = true)) {
                                        viewModel.setStatusMessage("Blocked unencrypted http:// URL under Strict Pure Decentralized Mode")
                                        return true
                                    }

                                    if (httpsOnlyMode && targetUrl.startsWith("http://", ignoreCase = true)) {
                                        val httpsUrl = targetUrl.replaceFirst("http://", "https://", ignoreCase = true)
                                        view?.loadUrl(httpsUrl)
                                        return true
                                    }

                                    val handled = handleDeepLinkOrNavigate(view, targetUrl, false)
                                    if (handled) {
                                        return true
                                    }

                                    return false
                                }

                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                    super.onReceivedError(view, request, error)
                                    val failingUrl = request?.url?.toString() ?: ""
                                    if (failingUrl.startsWith("market://", ignoreCase = true) ||
                                        failingUrl.startsWith("intent://", ignoreCase = true) ||
                                        failingUrl.startsWith("snssdk", ignoreCase = true) ||
                                        failingUrl.startsWith("tiktok:", ignoreCase = true) ||
                                        failingUrl.startsWith("aweme:", ignoreCase = true) ||
                                        failingUrl.startsWith("bytedance:", ignoreCase = true) ||
                                        failingUrl.contains("play.google.com") ||
                                        failingUrl.contains("apps.apple.com") ||
                                        failingUrl.contains("onelink.me") ||
                                        failingUrl.contains("adjust.com")
                                    ) {
                                        return
                                    }
                                    if (request?.isForMainFrame == true) {
                                        isWebLoading = false
                                        (view?.parent as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout)?.isRefreshing = false
                                        if (failingUrl.startsWith("http://", ignoreCase = true) || failingUrl.startsWith("https://", ignoreCase = true)) {
                                            val errorMsg = error?.description?.toString() ?: "Network error or connection timed out"
                                            val errorPage = """
                                                <!DOCTYPE html>
                                                <html>
                                                <head>
                                                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                                    <style>
                                                        body { background-color: #0B0F17; color: #E2E8F0; font-family: -apple-system, sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 80vh; margin: 0; padding: 24px; text-align: center; }
                                                        .card { background: #151A26; border: 1px solid #1E293B; border-radius: 16px; padding: 28px 20px; max-width: 360px; }
                                                        h2 { color: #FFFFFF; font-size: 18px; margin: 0 0 8px; font-weight: 600; }
                                                        p { color: #94A3B8; font-size: 13px; line-height: 1.5; margin: 0 0 16px; }
                                                        .url { color: #00E5FF; font-family: monospace; font-size: 11px; word-break: break-all; background: #0B0F17; padding: 6px 10px; border-radius: 6px; margin-bottom: 20px; border: 1px solid #1E293B; }
                                                        .btn { background: #00E5FF; color: #0B0F17; border: none; padding: 10px 24px; border-radius: 8px; font-weight: 600; font-size: 14px; cursor: pointer; }
                                                    </style>
                                                </head>
                                                <body>
                                                    <div class="card">
                                                        <h2>Unable to Reach Webpage</h2>
                                                        <p>$errorMsg</p>
                                                        <div class="url">$failingUrl</div>
                                                        <button class="btn" onclick="location.reload()">Retry</button>
                                                    </div>
                                                </body>
                                                </html>
                                            """.trimIndent()
                                            view?.loadDataWithBaseURL(null, errorPage, "text/html", "UTF-8", null)
                                        }
                                    }
                                }

                                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                    handler?.cancel()
                                    isWebLoading = false
                                    (view?.parent as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout)?.isRefreshing = false
                                    val failingUrl = error?.url ?: ""
                                    val sslReason = when (error?.primaryError) {
                                        android.net.http.SslError.SSL_EXPIRED -> "The SSL certificate for this site has expired."
                                        android.net.http.SslError.SSL_IDMISMATCH -> "The SSL certificate host does not match the requested domain."
                                        android.net.http.SslError.SSL_UNTRUSTED -> "The certificate authority is untrusted or self-signed."
                                        android.net.http.SslError.SSL_NOTYETVALID -> "The SSL certificate is not yet valid."
                                        android.net.http.SslError.SSL_DATE_INVALID -> "The device or certificate clock/date is invalid."
                                        else -> "SSL Certificate handshake verification failed."
                                    }
                                    val sslWarningPage = """
                                        <!DOCTYPE html>
                                        <html>
                                        <head>
                                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                            <style>
                                                body { font-family: -apple-system, sans-serif; background: #0A0E17; color: #F0F4F8; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }
                                                .card { background: #131B2E; border: 1px solid #EF4444; border-radius: 12px; padding: 24px; max-width: 400px; text-align: center; }
                                                h2 { color: #EF4444; margin-top: 0; font-size: 18px; }
                                                p { color: #94A3B8; font-size: 13px; line-height: 1.5; }
                                                .url { word-break: break-all; font-family: monospace; background: #0D121D; padding: 8px; border-radius: 6px; font-size: 11px; margin: 12px 0; color: #F59E0B; }
                                                .btn { background: #EF4444; color: white; border: none; padding: 10px 20px; border-radius: 8px; font-weight: bold; cursor: pointer; }
                                            </style>
                                        </head>
                                        <body>
                                            <div class="card">
                                                <h2>Security Warning: Untrusted Certificate</h2>
                                                <p>$sslReason</p>
                                                <p>Kaspa Privacy Shield blocked this connection to prevent eavesdropping and data interception.</p>
                                                <div class="url">$failingUrl</div>
                                                <button class="btn" onclick="history.back()">Return to Safety</button>
                                            </div>
                                        </body>
                                        </html>
                                    """.trimIndent()
                                    view?.loadDataWithBaseURL(null, sslWarningPage, "text/html", "UTF-8", null)
                                }
                            }

                            webViewInstance = this
                        }

                        addView(webView)

                        setOnChildScrollUpCallback { _, _ ->
                            webView.canScrollVertically(-1) || webView.scrollY > 0
                        }

                        setOnRefreshListener {
                            val currentUrl = webView.url ?: urlInput
                            val normalized = viewModel.normalizeUrlOrQuery(currentUrl)
                            if (normalized.startsWith("http://", ignoreCase = true) || normalized.startsWith("https://", ignoreCase = true)) {
                                webView.reload()
                            } else {
                                viewModel.resolveUrl()
                            }
                        }
                    }
                },
                update = { containerLayout ->
                    try {
                        val webView = (0 until containerLayout.childCount)
                            .mapNotNull { containerLayout.getChildAt(it) as? WebView }
                            .firstOrNull() ?: return@AndroidView

                        val swipeRefresh = containerLayout as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout
                        if (swipeRefresh != null) {
                            swipeRefresh.isEnabled = !showFindInPage && !isReaderMode
                            if (!isWebLoading && swipeRefresh.isRefreshing) {
                                swipeRefresh.isRefreshing = false
                            }
                        }

                        webViewInstance = webView
                        canGoBack = webView.canGoBack()
                        canGoForward = webView.canGoForward()

                        if (isReaderMode) {
                            val readerBgInt = android.graphics.Color.parseColor("#0F172A")
                            containerLayout.setBackgroundColor(readerBgInt)
                            webView.setBackgroundColor(readerBgInt)
                            swipeRefresh?.isEnabled = false
                            return@AndroidView
                        }

                        // Ensure browser background remains white for consistent website rendering
                        containerLayout.setBackgroundColor(android.graphics.Color.WHITE)
                        webView.setBackgroundColor(android.graphics.Color.WHITE)
                        // Algorithmic darkening removed to prevent "black page" issues.

                        val currentUrl = resource.url
                        val isGoogleAccountSession = KaspaPrivacyEngine.isGoogleAccountOrAuthUrl(currentUrl)
                        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, thirdPartyCookies || isGoogleAccountSession)

                        // Sync WebAuthn support state with settings safely
                        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.WEB_AUTHENTICATION)) {
                            val targetSupport = if (webAuthEnabled) {
                                androidx.webkit.WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
                            } else {
                                androidx.webkit.WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_NONE
                            }
                            try {
                                val current = androidx.webkit.WebSettingsCompat.getWebAuthenticationSupport(webView.settings)
                                if (current != targetSupport) {
                                    androidx.webkit.WebSettingsCompat.setWebAuthenticationSupport(webView.settings, targetSupport)
                                }
                            } catch (_: Throwable) {}
                        }

                        val isDirectHttp = resource.url.startsWith("http://", ignoreCase = true) || resource.url.startsWith("https://", ignoreCase = true)
                        
                        if (isDirectHttp) {
                            val isWebStore = resource.url.contains("chromewebstore.google.com") || resource.url.contains("chrome.google.com/webstore")
                            val defaultDeviceUa = try {
                                WebSettings.getDefaultUserAgent(context)
                            } catch (_: Throwable) {
                                null
                            }
                            val desiredUa = if (desktopModeEnabled || isWebStore) {
                                KaspaPrivacyEngine.getDesktopUserAgent(defaultDeviceUa)
                            } else {
                                KaspaPrivacyEngine.getMobileUserAgent(defaultDeviceUa)
                            }
                            val uaChanged = webView.settings.userAgentString != desiredUa
                            if (uaChanged) {
                                webView.settings.userAgentString = desiredUa
                                webView.settings.useWideViewPort = true
                                webView.settings.loadWithOverviewMode = desktopModeEnabled || isWebStore
                                webView.setInitialScale(0)
                            }
                            
                            val currentTag = webView.tag as? Pair<*, *>
                            val tagId = currentTag?.second as? Int ?: (webView.tag as? Int)
                            val isRecentDownload = (resource.url == lastDownloadedUrl && (System.currentTimeMillis() - lastDownloadTimestamp < 30000L))
                            val isNewSession = (tagId != navigationSessionId) && !isRecentDownload

                            if (isNewSession) {
                                webView.tag = Pair(resource.url, navigationSessionId)
                                val finalUrl = if (httpsOnlyMode && resource.url.startsWith("http://", ignoreCase = true)) {
                                    resource.url.replaceFirst("http://", "https://", ignoreCase = true)
                                } else {
                                    resource.url
                                }
                                val desktopHeaders = KaspaPrivacyEngine.getDesktopHeaders(desktopModeEnabled || isWebStore, defaultDeviceUa)
                                webView.loadUrl(finalUrl, desktopHeaders)
                            } else if (uaChanged) {
                                val rawUrl = webView.url ?: resource.url
                                val targetUrl = if (desktopModeEnabled && !rawUrl.isNullOrBlank()) {
                                    KaspaPrivacyEngine.convertMobileUrlToDesktop(rawUrl)
                                } else if (!desktopModeEnabled && !rawUrl.isNullOrBlank()) {
                                    KaspaPrivacyEngine.convertDesktopUrlToMobile(rawUrl)
                                } else {
                                    rawUrl
                                }
                                if (!targetUrl.isNullOrBlank() && targetUrl != rawUrl) {
                                    viewModel.setUrlInput(targetUrl)
                                }
                                if (!targetUrl.isNullOrBlank()) {
                                    val desktopHeaders = KaspaPrivacyEngine.getDesktopHeaders(desktopModeEnabled || isWebStore, defaultDeviceUa)
                                    webView.loadUrl(targetUrl, desktopHeaders)
                                }
                            }
                        } else {
                            val cidKey = if (resource.cid.isNotBlank()) resource.cid else "kaspa"
                            val loadKey = "${resource.url}_$cidKey"
                            val currentTag = webView.tag as? Pair<*, *>
                            val tagUrl = currentTag?.first as? String
                            val tagId = currentTag?.second as? Int ?: (webView.tag as? Int)

                            if (tagUrl != loadKey || tagId != navigationSessionId) {
                                webView.tag = Pair(loadKey, navigationSessionId)
                                val baseUrl = if (resource.cid.isNotBlank()) {
                                    "https://${resource.cid}.ipfs.dweb.link/"
                                } else {
                                    "https://kaspa.org/"
                                }
                                webView.loadDataWithBaseURL(
                                    baseUrl,
                                    resource.content,
                                    "text/html",
                                    "UTF-8",
                                    baseUrl
                                )
                            }
                        }
                    } catch (e: Throwable) {
                        android.util.Log.w("BrowserGatewayScreen", "Error during webView update: ${e.message}")
                    }
                },
                onRelease = { containerLayout ->
                    val swipeRefresh = containerLayout as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout
                    swipeRefresh?.setOnRefreshListener(null)
                    swipeRefresh?.setOnChildScrollUpCallback(null)
                    val webView = (0 until containerLayout.childCount)
                        .mapNotNull { containerLayout.getChildAt(it) as? WebView }
                        .firstOrNull()
                    webView?.stopLoading()
                    webView?.loadUrl("about:blank")
                    webView?.destroy()
                },
                    modifier = Modifier.fillMaxSize()
                )
            }
            } else {
                // NATIVE DOCUMENT READER: Markdown / Text / Source Code
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = resource.title,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = resource.url,
                                        color = ElectricCyan,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = SurfaceCard,
                                    modifier = Modifier.clickable { viewSourceMode = !viewSourceMode }
                                ) {
                                    Text(
                                        text = if (viewSourceMode) "Raw Payload" else "Markdown Document",
                                        fontSize = 10.sp,
                                        color = EmeraldMesh,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = SurfaceCardBorder)
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = resource.content,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = TextPrimary,
                                fontFamily = if (viewSourceMode) FontFamily.Monospace else FontFamily.Default
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${(resource.sizeBytes / 1024.0).let { "%.1f KB".format(it) }} • ${resource.contentType}",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IconButton(
                                        onClick = { clipboardManager.setText(AnnotatedString(resource.cid)) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy CID", tint = TextSecondary, modifier = Modifier.size(15.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, resource.title)
                                                putExtra(Intent.EXTRA_TEXT, "${resource.title}\n${resource.url}\nCID: ${resource.cid}\n\n${resource.content}")
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share"))
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Share", tint = TextSecondary, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Floating Audit Card
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setTab(com.example.viewmodel.AppTab.TRAFFIC_AUDIT) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldMesh, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Cryptographic Verification & Route",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                            Text(
                                text = "${resource.latencyMs}ms",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // FULL-SCREEN PROFESSIONAL SEARCH OVERLAY PANEL
            if (isInputFocused) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            isInputFocused = false
                            focusManager.clearFocus()
                        },
                    color = ObsidianBg
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Quick Action Buttons Row (Paste / Copy)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val clipText = try { clipboardManager.getText()?.text } catch (_: Exception) { null }
                            if (!clipText.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = SurfaceCard,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                                    modifier = Modifier.clickable {
                                        val pasted = clipText.trim()
                                        viewModel.setUrlInput(pasted)
                                        textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                            text = pasted,
                                            selection = androidx.compose.ui.text.TextRange(pasted.length)
                                        )
                                        val normalized = viewModel.normalizeUrlOrQuery(pasted)
                                        viewModel.onUserSubmitUrl(normalized)
                                        isInputFocused = false
                                        focusManager.clearFocus()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = null,
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Paste from clipboard",
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            if (urlInput.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = SurfaceCard,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                                    modifier = Modifier.clickable {
                                        try {
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(urlInput))
                                            viewModel.setStatusMessage("URL copied to clipboard")
                                        } catch (_: Exception) {}
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            tint = EmeraldMesh,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Copy link",
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val query = urlInput.trim().lowercase()
                        val predefined = listOf(
                            Triple("kaspa.stream", "Kaspa BlockDAG Explorer", "https://kaspa.stream"),
                            Triple("kaspa.org", "Kaspa Proof-of-Work BlockDAG", "https://kaspa.org"),
                            Triple("kaspa.com", "Kaspa Ecosystem & Markets", "https://kaspa.com"),
                            Triple("kasrace.com", "Kasrace 4D Realtime Explorer", "https://kasrace.com"),
                            Triple("kaskad.live", "Kaskad Decentralized Network", "https://kaskad.live"),
                            Triple("mykai.dev", "Kai Sovereign Cloud & Apps", "https://mykai.dev"),
                            Triple("duckduckgo.com", "DuckDuckGo Privacy Search Engine", "https://duckduckgo.com"),
                            Triple("github.com", "GitHub Developer Platform", "https://github.com"),
                            Triple("reddit.com/r/kaspa", "Kaspa Reddit Community", "https://www.reddit.com/r/kaspa"),
                            Triple("discord.gg/kaspa", "Kaspa Discord Server", "https://discord.gg/kaspa")
                        )

                        val suggestionsList = remember(query) {
                            val list = mutableListOf<Triple<String, String, String>>()
                            if (query.isNotEmpty()) {
                                val encodedQuery = try {
                                    java.net.URLEncoder.encode(query, "UTF-8")
                                } catch (_: Exception) { query }
                                list.add(Triple(query, "Search DuckDuckGo for \"$query\"", "https://duckduckgo.com/?q=$encodedQuery"))

                                if (query.contains(".") || query.startsWith("http")) {
                                    val directUrl = if (query.startsWith("http")) query else "https://$query"
                                    list.add(Triple(query, "Go directly to $directUrl", directUrl))
                                }

                                val matches = predefined.filter {
                                    it.first.contains(query) || it.second.lowercase().contains(query)
                                }
                                list.addAll(matches)
                            } else {
                                list.addAll(predefined)
                            }
                            list
                        }

                        Text(
                            text = if (query.isEmpty()) "Quick Navigation" else "Search Suggestions",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        )

                        suggestionsList.take(8).forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceCard,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        val target = item.third
                                        viewModel.setUrlInput(target)
                                        textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                            text = target,
                                            selection = androidx.compose.ui.text.TextRange(target.length)
                                        )
                                        viewSourceMode = false
                                        val normalized = viewModel.normalizeUrlOrQuery(target)
                                        viewModel.onUserSubmitUrl(normalized)
                                        isInputFocused = false
                                        focusManager.clearFocus()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(SurfaceDark),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (item.third.contains("google.com/search")) Icons.Default.Search else Icons.Default.Language,
                                            contentDescription = null,
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.second,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.third,
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    // Install Sheet (matching download UL pattern, with Install, Shortcut, and Button)
    if (showInstallSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInstallSheet = false },
            sheetState = installSheetState,
            containerColor = SurfaceDark,
            tonalElevation = 8.dp,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(SurfaceCardBorder)
                )
            }
        ) {
            InstallSheetContent(
                viewModel = viewModel,
                currentResource = currentResource,
                urlInput = urlInput,
                onClose = {
                    scope.launch { installSheetState.hide() }.invokeOnCompletion { showInstallSheet = false }
                }
            )
        }
    }



    // PWA & Web App Device Installation Dialog
    if (showPwaDialog) {
        val detectedPwa by viewModel.currentPagePwa.collectAsState()
        val pwaTitle = detectedPwa?.name ?: currentResource?.title ?: if (urlInput.isNotBlank()) urlInput else "Web App"
        val pwaUrl = detectedPwa?.url ?: if (urlInput.isNotBlank()) urlInput else currentResource?.url ?: "https://kaspa.org"

        PwaInstallDialog(
            initialTitle = pwaTitle,
            url = pwaUrl,
            onInstall = { title, url ->
                showPwaDialog = false
                viewModel.installPwa(context, title, url)
            },
            onAddShortcut = { title, url ->
                showPwaDialog = false
                viewModel.installPwa(context, title, url)
            },
            onDismiss = { showPwaDialog = false }
        )
    }

    // Decentralized Identity & Google zk-Bridge Dialog
    if (showAccountDialog) {
        DecentralizedAccountDialog(
            activeAccount = activeAccount,
            allAccounts = allAccounts,
            initialTab = accountDialogInitialTab,
            onDismiss = { showAccountDialog = false },
            hasAccountPassword = hasWalletPassword,
            biometricEnabled = biometricsEnabled,
            onVerifyPassword = { password -> viewModel.unlockWalletWithPassword(password) },
            onLinkGoogle = { email, displayName ->
                viewModel.linkGoogleDecentralizedAccount(email, displayName)
            },
            onOpenGoogleLogin = {
                showAccountDialog = false
                viewModel.openUrlInBrowser("https://accounts.google.com")
            },
            onSwitchAccount = { did ->
                viewModel.switchAccount(did)
            },
            onDeleteAccount = { acc ->
                viewModel.deleteAccount(acc)
            },
            onOpenUrl = { url ->
                showAccountDialog = false
                viewModel.openUrlInBrowser(url)
            },
            onSignOut = {
                val acc = activeAccount
                if (acc != null) {
                    viewModel.deleteAccount(acc)
                }
                showAccountDialog = false
            }
        )
    }

    // Browser Background Theme Customization Dialog & Color Selector
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = SurfaceDark,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = ElectricCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Customize Browser Background", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Select a background color or curated theme for your browser:", color = TextSecondary, fontSize = 12.sp)

                    Text("Quick Color Palette", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val colorSwatches = listOf(
                            "#000000", "#12141C", "#0F172A", "#042F2E",
                            "#1E1035", "#292524", "#0F2027", "#1E293B", "#FFFFFF"
                        )
                        colorSwatches.forEach { hex ->
                            val parsedColor = try {
                                Color(android.graphics.Color.parseColor(hex))
                            } catch (_: Exception) { Color.DarkGray }

                            val isSelected = browserTheme == "custom_hex" && customBgColorHex.equals(hex, ignoreCase = true)

                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(parsedColor)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) ElectricCyan else SurfaceCardBorder,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        saveBrowserTheme("custom_hex", hex)
                                    }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customBgColorHex,
                            onValueChange = { newHex ->
                                customBgColorHex = newHex
                                if (newHex.length >= 6) {
                                    saveBrowserTheme("custom_hex", newHex)
                                }
                            },
                            label = { Text("Custom Color Hex", color = TextMuted, fontSize = 11.sp) },
                            placeholder = { Text("#12141C", color = TextMuted) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = SurfaceCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        val previewColor = try {
                            val h = customBgColorHex.trim().removePrefix("#")
                            val p = if (h.length == 6) "FF$h" else h
                            Color(android.graphics.Color.parseColor("#$p"))
                        } catch (_: Exception) { Color.Transparent }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(previewColor)
                                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(8.dp))
                        )
                    }

                    HorizontalDivider(color = SurfaceCardBorder, modifier = Modifier.padding(vertical = 2.dp))

                    Text("Curated Preset Themes", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    ThemeOptionCard(
                        title = "Classic Dark Slate",
                        subtitle = "Standard clean dark background (#12141C)",
                        isSelected = browserTheme == "classic_dark",
                        onClick = { saveBrowserTheme("classic_dark", "#12141C") }
                    )
                    ThemeOptionCard(
                        title = "OLED Obsidian Black",
                        subtitle = "Deep pure black (#000000) for high contrast",
                        isSelected = browserTheme == "oled_obsidian",
                        onClick = { saveBrowserTheme("oled_obsidian", "#000000") }
                    )
                    ThemeOptionCard(
                        title = "Aurora Indigo Gradient",
                        subtitle = "Subtle deep indigo and blue gradient",
                        isSelected = browserTheme == "aurora_gradient",
                        onClick = { saveBrowserTheme("aurora_gradient", "#0F172A") }
                    )
                    ThemeOptionCard(
                        title = "Cyber Tech Gradient",
                        subtitle = "Modern tech teal and slate gradient",
                        isSelected = browserTheme == "cyber_gradient",
                        onClick = { saveBrowserTheme("cyber_gradient", "#042F2E") }
                    )
                    ThemeOptionCard(
                        title = "Warm Sepia Coffee",
                        subtitle = "Relaxing warm dark sepia tone (#292524)",
                        isSelected = browserTheme == "warm_sepia",
                        onClick = { saveBrowserTheme("warm_sepia", "#292524") }
                    )
                    ThemeOptionCard(
                        title = "Midnight Violet",
                        subtitle = "Deep purple dark canvas (#1E1035)",
                        isSelected = browserTheme == "midnight_purple",
                        onClick = { saveBrowserTheme("midnight_purple", "#1E1035") }
                    )
                    ThemeOptionCard(
                        title = "Soft Light Slate",
                        subtitle = "Soft slate dark blue (#1E293B)",
                        isSelected = browserTheme == "soft_slate",
                        onClick = { saveBrowserTheme("soft_slate", "#1E293B") }
                    )
                    ThemeOptionCard(
                        title = "Crisp Light Canvas",
                        subtitle = "Clean light background (#FFFFFF)",
                        isSelected = browserTheme == "pure_white",
                        onClick = { saveBrowserTheme("pure_white", "#FFFFFF") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Done", color = ElectricCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // HTML5 FULLSCREEN VIDEO CONTAINER OVERLAY
    if (customVideoView != null) {
        androidx.activity.compose.BackHandler {
            try {
                customVideoCallback?.onCustomViewHidden()
            } catch (_: Throwable) {}
            customVideoView = null
            customVideoCallback = null
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .zIndex(9999f)
        ) {
            AndroidView(
                factory = { _ ->
                    customVideoView ?: android.view.View(context)
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = {
                    try {
                        customVideoCallback?.onCustomViewHidden()
                    } catch (_: Throwable) {}
                    customVideoView = null
                    customVideoCallback = null
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Fullscreen Video",
                    tint = Color.White
                )
            }
        }
    }

    // CUSTOM IN-BROWSER JS ALERTS, CONFIRMS, PROMPTS & LOCATION OVERLAYS
    if (jsAlertMessage != null) {
        AlertDialog(
            onDismissRequest = {
                jsAlertResult?.cancel()
                jsAlertMessage = null
                jsAlertResult = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Site Notification", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            text = {
                Text(jsAlertMessage ?: "", fontSize = 14.sp, color = TextSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        jsAlertResult?.confirm()
                        jsAlertMessage = null
                        jsAlertResult = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (jsConfirmMessage != null) {
        AlertDialog(
            onDismissRequest = {
                jsConfirmResult?.cancel()
                jsConfirmMessage = null
                jsConfirmResult = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm Action", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            text = {
                Text(jsConfirmMessage ?: "", fontSize = 14.sp, color = TextSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        jsConfirmResult?.confirm()
                        jsConfirmMessage = null
                        jsConfirmResult = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirm", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        jsConfirmResult?.cancel()
                        jsConfirmMessage = null
                        jsConfirmResult = null
                    }
                ) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (jsPromptMessage != null) {
        AlertDialog(
            onDismissRequest = {
                jsPromptResult?.cancel()
                jsPromptMessage = null
                jsPromptResult = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Site Request", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            text = {
                Column {
                    Text(jsPromptMessage ?: "", fontSize = 14.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = jsPromptInputText,
                        onValueChange = { jsPromptInputText = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = SurfaceCardBorder,
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        jsPromptResult?.confirm(jsPromptInputText)
                        jsPromptMessage = null
                        jsPromptResult = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Submit", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        jsPromptResult?.cancel()
                        jsPromptMessage = null
                        jsPromptResult = null
                    }
                ) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (pendingGeoOrigin != null) {
        AlertDialog(
            onDismissRequest = {
                pendingGeoCallback?.invoke(pendingGeoOrigin, false, false)
                pendingGeoOrigin = null
                pendingGeoCallback = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Location Permission Request", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            text = {
                Text(
                    text = "The website at \"$pendingGeoOrigin\" wants to access your device's physical location. DecentralNet isolates and protects your geolocation info.",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingGeoCallback?.invoke(pendingGeoOrigin, true, true)
                        pendingGeoOrigin = null
                        pendingGeoCallback = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
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
                    Text("Deny", color = TextMuted)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showLinkContextMenu && (longPressedLinkUrl != null || longPressedImageUrl != null)) {
        val linkUrl = longPressedLinkUrl ?: (longPressedImageUrl ?: "")
        val imageUrl = longPressedImageUrl
        val isImage = isLongPressedImage || imageUrl != null
        val hasSeparateLink = !longPressedLinkUrl.isNullOrBlank() && longPressedLinkUrl != imageUrl

        ModalBottomSheet(
            onDismissRequest = {
                showLinkContextMenu = false
                longPressedLinkUrl = null
                longPressedImageUrl = null
                longPressedImageTitle = null
                isLongPressedImage = false
            },
            sheetState = linkContextMenuSheetState,
            containerColor = SurfaceDark,
            tonalElevation = 8.dp,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(SurfaceCardBorder)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                // Header preview (100% like Chrome)
                if (isImage && !imageUrl.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceCard)
                                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Image preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val displayTitle = if (!longPressedImageTitle.isNullOrBlank()) {
                                longPressedImageTitle
                            } else {
                                val clean = imageUrl.substringBefore("?").substringBefore("#")
                                val seg = clean.substringAfterLast("/")
                                if (seg.isNotBlank() && seg.length < 50 && !seg.contains("=")) seg else "Image"
                            }
                            Text(
                                text = displayTitle ?: "Image",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val hostDisplay = try {
                                val parsedUri = android.net.Uri.parse(imageUrl)
                                if (imageUrl.startsWith("data:", true)) "Embedded image"
                                else if (imageUrl.startsWith("blob:", true)) "Blob image"
                                else parsedUri.host ?: imageUrl
                            } catch (_: Exception) {
                                imageUrl
                            }
                            Text(
                                text = hostDisplay,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(SurfaceCard)
                                .border(1.dp, SurfaceCardBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val hostDisplay = try {
                                android.net.Uri.parse(linkUrl).host ?: linkUrl
                            } catch (_: Exception) {
                                linkUrl
                            }
                            Text(
                                text = hostDisplay,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = linkUrl,
                                fontSize = 12.sp,
                                color = ElectricCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = SurfaceCardBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(6.dp))

                // Actions
                if (isImage && !imageUrl.isNullOrBlank()) {
                    // 1. Download image
                    HubActionRow(
                        icon = Icons.Default.Download,
                        title = "Download image",
                        onClick = {
                            scope.launch { linkContextMenuSheetState.hide() }.invokeOnCompletion {
                                showLinkContextMenu = false
                                val target = imageUrl
                                longPressedLinkUrl = null
                                longPressedImageUrl = null
                                longPressedImageTitle = null
                                isLongPressedImage = false
                                executeImageDownload(context, target, viewModel, webViewInstance)
                            }
                        }
                    )

                    // 2. Share image
                    HubActionRow(
                        icon = Icons.Default.Share,
                        title = "Share image",
                        onClick = {
                            scope.launch { linkContextMenuSheetState.hide() }.invokeOnCompletion {
                                showLinkContextMenu = false
                                val target = imageUrl
                                longPressedLinkUrl = null
                                longPressedImageUrl = null
                                longPressedImageTitle = null
                                isLongPressedImage = false
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, target)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                            }
                        }
                    )

                    // 3. Copy image address
                    HubActionRow(
                        icon = Icons.Default.ContentCopy,
                        title = "Copy image address",
                        onClick = {
                            scope.launch { linkContextMenuSheetState.hide() }.invokeOnCompletion {
                                showLinkContextMenu = false
                                val target = imageUrl
                                longPressedLinkUrl = null
                                longPressedImageUrl = null
                                longPressedImageTitle = null
                                isLongPressedImage = false
                                clipboardManager.setText(AnnotatedString(target))
                                viewModel.setStatusMessage("Image address copied to clipboard")
                                android.widget.Toast.makeText(context, "Image address copied", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    // If image is inside an anchor link:
                    if (hasSeparateLink && !longPressedLinkUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = SurfaceCardBorder, thickness = 1.dp)
                        Text(
                            text = "LINK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                        )

                        HubActionRow(
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                            title = "Open link in new tab",
                            onClick = {
                                scope.launch { linkContextMenuSheetState.hide() }.invokeOnCompletion {
                                    showLinkContextMenu = false
                                    val target = longPressedLinkUrl ?: ""
                                    longPressedLinkUrl = null
                                    longPressedImageUrl = null
                                    longPressedImageTitle = null
                                    isLongPressedImage = false
                                    viewModel.createNewTab(target, "New Tab")
                                }
                            }
                        )

                        HubActionRow(
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                            title = "Open link in background tab",
                            onClick = {
                                scope.launch { linkContextMenuSheetState.hide() }.invokeOnCompletion {
                                    showLinkContextMenu = false
                                    val target = longPressedLinkUrl ?: ""
                                    longPressedLinkUrl = null
                                    longPressedImageUrl = null
                                    longPressedImageTitle = null
                                    isLongPressedImage = false
                                    viewModel.openBackgroundTab(target, "New Tab")
                                }
                            }
                        )

                        HubActionRow(
                            icon = Icons.Default.ContentCopy,
                            title = "Copy link address",
                            onClick = {
                                scope.launch { linkContextMenuSheetState.hide() }.invokeOnCompletion {
                                    showLinkContextMenu = false
                                    val target = longPressedLinkUrl ?: ""
                                    longPressedLinkUrl = null
                                    longPressedImageUrl = null
                                    longPressedImageTitle = null
                                    isLongPressedImage = false
                                    clipboardManager.setText(AnnotatedString(target))
                                    viewModel.setStatusMessage("Link address copied to clipboard")
                                    android.widget.Toast.makeText(context, "Link address copied", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        HubActionRow(
                            icon = Icons.Default.Share,
                            title = "Share link",
                            onClick = {
                                scope.launch { linkContextMenuSheetState.hide() }.invokeOnCompletion {
                                    showLinkContextMenu = false
                                    val target = longPressedLinkUrl ?: ""
                                    longPressedLinkUrl = null
                                    longPressedImageUrl = null
                                    longPressedImageTitle = null
                                    isLongPressedImage = false
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, target)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Link"))
                                }
                            }
                        )
                    }
                } else {
                    // Pure Link actions
                    HubActionRow(
                        icon = Icons.Default.Add,
                        title = "Open link in new tab",
                        onClick = {
                            scope.launch { linkContextMenuSheetState.hide() }.invokeOnCompletion {
                                showLinkContextMenu = false
                                val target = linkUrl
                                longPressedLinkUrl = null
                                longPressedImageUrl = null
                                longPressedImageTitle = null
                                isLongPressedImage = false
                                viewModel.createNewTab(target, "New Tab")
                            }
                        }
                    )

                    HubActionRow(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        title = "Open link in background tab",
                        onClick = {
                            scope.launch { linkContextMenuSheetState.hide() }.invokeOnCompletion {
                                showLinkContextMenu = false
                                val target = linkUrl
                                longPressedLinkUrl = null
                                longPressedImageUrl = null
                                longPressedImageTitle = null
                                isLongPressedImage = false
                                viewModel.openBackgroundTab(target, "New Tab")
                            }
                        }
                    )

                    HubActionRow(
                        icon = Icons.Default.ContentCopy,
                        title = "Copy link address",
                        onClick = {
                            scope.launch { linkContextMenuSheetState.hide() }.invokeOnCompletion {
                                showLinkContextMenu = false
                                val target = linkUrl
                                longPressedLinkUrl = null
                                longPressedImageUrl = null
                                longPressedImageTitle = null
                                isLongPressedImage = false
                                clipboardManager.setText(AnnotatedString(target))
                                viewModel.setStatusMessage("Link address copied to clipboard")
                                android.widget.Toast.makeText(context, "Link address copied", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    HubActionRow(
                        icon = Icons.Default.Share,
                        title = "Share link",
                        onClick = {
                            scope.launch { linkContextMenuSheetState.hide() }.invokeOnCompletion {
                                showLinkContextMenu = false
                                val target = linkUrl
                                longPressedLinkUrl = null
                                longPressedImageUrl = null
                                longPressedImageTitle = null
                                isLongPressedImage = false
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, target)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Link"))
                            }
                        }
                    )
                }
            }
        }
    }

    // MULTI-TAB SWITCHER FULL-SCREEN OVERLAY
    if (showTabSwitcher) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ObsidianBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showTabSwitcher = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = "${tabs.size} open tabs",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    IconButton(onClick = {
                        viewModel.createNewTab()
                        showTabSwitcher = false
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Tab",
                            tint = ElectricCyan
                        )
                    }
                }

                HorizontalDivider(color = SurfaceCardBorder, thickness = 1.dp)

                // Grid of open tabs
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(tabs) { tab ->
                        val isActive = tab.id == activeTabId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isActive) 2.dp else 1.dp,
                                    color = if (isActive) ElectricCyan else SurfaceCardBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    viewModel.setActiveTab(tab.id)
                                    showTabSwitcher = false
                                },
                            color = if (isActive) SurfaceCard else SurfaceDark
                        ) {
                            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // Tab Page Title
                                        Text(
                                            text = tab.title,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        // Tab URL
                                        Text(
                                            text = if (tab.url.isEmpty()) "Home screen" else tab.url,
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    
                                    // Close Tab Button (Bottom-Right aligned inside the card)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.closeTab(tab.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close Tab",
                                                tint = TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom New Tab CTA bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                viewModel.createNewTab()
                                showTabSwitcher = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("New Tab", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}
}


@Composable
fun GoogleLogoIcon(modifier: Modifier = Modifier, iconSize: Dp = 24.dp) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.22f
        val radius = (w - strokeWidth) / 2f
        val center = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f)

        // Draw Google G with 4 segmented arcs and a blue horizontal bar
        val red = Color(0xFFEA4335)
        val yellow = Color(0xFFFBBC05)
        val green = Color(0xFF70C7BA)
        val blue = Color(0xFF4285F4)

        val arcStyle = androidx.compose.ui.graphics.drawscope.Stroke(
            width = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Butt
        )

        // Yellow: Left arc
        drawArc(
            color = yellow,
            startAngle = 135f,
            sweepAngle = 90f,
            useCenter = false,
            style = arcStyle,
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
            topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f)
        )

        // Green: Bottom arc
        drawArc(
            color = green,
            startAngle = 40f,
            sweepAngle = 100f,
            useCenter = false,
            style = arcStyle,
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
            topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f)
        )

        // Red: Top arc
        drawArc(
            color = red,
            startAngle = 220f,
            sweepAngle = 100f,
            useCenter = false,
            style = arcStyle,
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
            topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f)
        )

        // Blue: Right arc
        drawArc(
            color = blue,
            startAngle = -40f,
            sweepAngle = 85f,
            useCenter = false,
            style = arcStyle,
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
            topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f)
        )

        // Draw horizontal blue bar extending to the center of G
        drawLine(
            color = blue,
            start = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f),
            end = androidx.compose.ui.geometry.Offset(w - strokeWidth / 2f, h / 2f),
            strokeWidth = strokeWidth
        )
    }
}

@Composable
fun KaspaLogoIcon(modifier: Modifier = Modifier, iconSize: Dp = 24.dp) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val kaspaColor = Color(0xFF70C7BA) // Kaspa Teal

        // Solid round coin background
        drawCircle(
            color = kaspaColor,
            radius = w / 2f,
            center = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f)
        )

        // Real Kaspa logo: Stylized white "reverse K" inside the coin
        val strokeWidth = w * 0.12f

        // Vertical bar on the right side
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.22f),
            end = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.78f),
            strokeWidth = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Upper diagonal going up-left
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.5f),
            end = androidx.compose.ui.geometry.Offset(w * 0.32f, h * 0.25f),
            strokeWidth = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Lower diagonal going down-left
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.5f),
            end = androidx.compose.ui.geometry.Offset(w * 0.32f, h * 0.75f),
            strokeWidth = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
fun KaspaComLogoIcon(modifier: Modifier = Modifier, iconSize: Dp = 24.dp) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val tealColor = Color(0xFF70C7BA)

        // 1. Draw slanted orbital ring in the background (light grey) using DrawScope.rotate
        rotate(degrees = -22f, pivot = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f)) {
            drawOval(
                color = Color(0xFFE2E8F0),
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.05f, h * 0.32f),
                size = androidx.compose.ui.geometry.Size(w * 0.9f, h * 0.36f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = w * 0.07f,
                    cap = StrokeCap.Round
                )
            )
        }

        // 2. Draw central merged K + C symbol (Teal)
        val strokeWidth = w * 0.12f

        // Central vertical stem
        drawLine(
            color = tealColor,
            start = androidx.compose.ui.geometry.Offset(w * 0.48f, h * 0.23f),
            end = androidx.compose.ui.geometry.Offset(w * 0.48f, h * 0.77f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Left upper diagonal leg
        drawLine(
            color = tealColor,
            start = androidx.compose.ui.geometry.Offset(w * 0.48f, h * 0.5f),
            end = androidx.compose.ui.geometry.Offset(w * 0.22f, h * 0.32f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Left lower diagonal leg
        drawLine(
            color = tealColor,
            start = androidx.compose.ui.geometry.Offset(w * 0.48f, h * 0.5f),
            end = androidx.compose.ui.geometry.Offset(w * 0.22f, h * 0.68f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Right curved C-shaped arm (A clean capital letter 'C' opening to the right, with its leftmost back curve touching near the central stem)
        val cPath = Path().apply {
            moveTo(w * 0.74f, h * 0.26f)
            cubicTo(
                w * 0.42f, h * 0.22f,  // Control point 1 (pulls curve up-left)
                w * 0.42f, h * 0.78f,  // Control point 2 (pulls curve down-left)
                w * 0.74f, h * 0.74f   // End point at bottom-right
            )
        }
        drawPath(
            path = cPath,
            color = tealColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 3. Draw five-pointed teal star on bottom right of the ring
        val starCx = w * 0.71f
        val starCy = h * 0.71f
        val starOuterRad = w * 0.08f
        val starInnerRad = w * 0.035f
        
        val starPath = Path().apply {
            var rot = Math.PI / 2 * 3
            val step = Math.PI / 5
            moveTo(starCx, starCy - starOuterRad)
            for (i in 0 until 5) {
                lineTo(
                    (starCx + Math.cos(rot) * starOuterRad).toFloat(),
                    (starCy + Math.sin(rot) * starOuterRad).toFloat()
                )
                rot += step
                lineTo(
                    (starCx + Math.cos(rot) * starInnerRad).toFloat(),
                    (starCy + Math.sin(rot) * starInnerRad).toFloat()
                )
                rot += step
            }
            close()
        }
        drawPath(
            path = starPath,
            color = tealColor
        )
    }
}

@Composable
fun KaspaStreamLogoIcon(modifier: Modifier = Modifier, iconSize: Dp = 24.dp) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height

        // 1. Draw soft circular coin background with vibrant cyan-to-purple gradient
        val gradient = androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(Color(0xFF22D3EE), Color(0xFF8B5CF6)),
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(w, h)
        )
        drawCircle(
            brush = gradient,
            radius = w / 2f,
            center = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f)
        )

        // 2. Draw clean white stylized "Reverse K" in the center
        val strokeWidth = w * 0.12f

        // Vertical bar on the right side
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.22f),
            end = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.78f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Upper diagonal going up-left
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.5f),
            end = androidx.compose.ui.geometry.Offset(w * 0.32f, h * 0.25f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Lower diagonal going down-left
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.5f),
            end = androidx.compose.ui.geometry.Offset(w * 0.32f, h * 0.75f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun IgraLabsLogoIcon(modifier: Modifier = Modifier, iconSize: Dp = 24.dp) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height

        // Define Left Shape Path (Lime Green Face)
        val leftPath = Path().apply {
            moveTo(w * 0.40f, h * 0.50f)
            // Top-right prong
            lineTo(w * 0.52f, h * 0.38f)
            lineTo(w * 0.34f, h * 0.20f)
            lineTo(w * 0.30f, h * 0.23f)
            lineTo(w * 0.44f, h * 0.39f)
            
            // Horizontal left prong
            lineTo(w * 0.16f, h * 0.40f)
            lineTo(w * 0.17f, h * 0.46f)
            lineTo(w * 0.35f, h * 0.46f)
            
            // Bottom-left diagonal prong
            lineTo(w * 0.17f, h * 0.65f)
            lineTo(w * 0.21f, h * 0.68f)
            lineTo(w * 0.34f, h * 0.55f)
            
            // Bottom vertical-ish prong
            lineTo(w * 0.31f, h * 0.80f)
            lineTo(w * 0.38f, h * 0.82f)
            lineTo(w * 0.40f, h * 0.57f)
            close()
        }

        // Define Right Shape Path (Lime Green Face)
        val rightPath = Path().apply {
            moveTo(w * 0.60f, h * 0.50f)
            
            // Top vertical prong
            lineTo(w * 0.58f, h * 0.14f)
            lineTo(w * 0.65f, h * 0.17f)
            lineTo(w * 0.61f, h * 0.43f)
            
            // Top-right diagonal prong
            lineTo(w * 0.80f, h * 0.28f)
            lineTo(w * 0.83f, h * 0.35f)
            lineTo(w * 0.65f, h * 0.48f)
            
            // Horizontal right prong
            lineTo(w * 0.84f, h * 0.54f)
            lineTo(w * 0.81f, h * 0.61f)
            lineTo(w * 0.60f, h * 0.54f)
            
            // Bottom-right diagonal prong
            lineTo(w * 0.65f, h * 0.77f)
            lineTo(w * 0.58f, h * 0.79f)
            lineTo(w * 0.50f, h * 0.58f)
            close()
        }

        // 1. Draw 3D extrusion/shadows in cyan/blue
        val shadowColor = Color(0xFF0EA5E9)
        drawPath(
            path = leftPath,
            color = shadowColor
        )
        drawPath(
            path = rightPath,
            color = shadowColor
        )

        // Draw slightly shifted main face for 3D extrusion illusion
        translate(left = -w * 0.02f, top = -h * 0.02f) {
            drawPath(
                path = leftPath,
                color = Color(0xFF70C7BA) // Kaspa Tea Color
            )
            drawPath(
                path = rightPath,
                color = Color(0xFF70C7BA) // Kaspa Tea Color
            )
        }
    }
}

@Composable
fun KaChatLogoIcon(modifier: Modifier = Modifier, iconSize: Dp = 24.dp) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val chatTeal = Color(0xFF70C7BA) // Match the exact soft teal/cyan chat bubble color

        // 1. Draw elegant chat bubble path with bottom-left pointer tail
        val bubblePath = Path().apply {
            moveTo(w * 0.5f, h * 0.15f)
            // Top and right curves
            cubicTo(w * 0.88f, h * 0.15f, w * 0.95f, h * 0.32f, w * 0.95f, h * 0.48f)
            cubicTo(w * 0.95f, h * 0.64f, w * 0.85f, h * 0.78f, w * 0.5f, h * 0.78f)
            
            // Pointer tail on bottom-left
            lineTo(w * 0.20f, h * 0.85f)
            lineTo(w * 0.24f, h * 0.68f)
            
            // Left curves back to top
            cubicTo(w * 0.10f, h * 0.62f, w * 0.10f, h * 0.45f, w * 0.10f, h * 0.32f)
            cubicTo(w * 0.10f, h * 0.18f, w * 0.25f, h * 0.15f, w * 0.5f, h * 0.15f)
            close()
        }
        drawPath(
            path = bubblePath,
            color = chatTeal
        )

        // 2. Draw clean white stylized "Reverse K" inside the chat bubble
        val strokeWidth = w * 0.09f

        // Vertical bar on the right side of the inner area
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.64f, h * 0.28f),
            end = androidx.compose.ui.geometry.Offset(w * 0.64f, h * 0.68f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Upper diagonal going up-left
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.64f, h * 0.48f),
            end = androidx.compose.ui.geometry.Offset(w * 0.36f, h * 0.32f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Lower diagonal going down-left
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.64f, h * 0.48f),
            end = androidx.compose.ui.geometry.Offset(w * 0.36f, h * 0.64f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun KasplayLogoIcon(modifier: Modifier = Modifier, iconSize: Dp = 24.dp) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val cyanColor = Color(0xFF35B1B1)  // Clean playful cyan from the logo
        val pinkColor = Color(0xFFD81B8A)  // Playful hot pink from the logo
        val strokeWidth = w * 0.16f

        // 1. Bottom-Left Diagonal Leg (Pink)
        drawLine(
            color = pinkColor,
            start = androidx.compose.ui.geometry.Offset(w * 0.42f, h * 0.52f),
            end = androidx.compose.ui.geometry.Offset(w * 0.16f, h * 0.78f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // 2. Main Cyan "K" Structure:
        // Top-Left to Center-Right Diagonal
        drawLine(
            color = cyanColor,
            start = androidx.compose.ui.geometry.Offset(w * 0.16f, h * 0.22f),
            end = androidx.compose.ui.geometry.Offset(w * 0.72f, h * 0.78f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Right Vertical Stem
        drawLine(
            color = cyanColor,
            start = androidx.compose.ui.geometry.Offset(w * 0.72f, h * 0.16f),
            end = androidx.compose.ui.geometry.Offset(w * 0.72f, h * 0.84f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // 3. Playful features inside:
        // A. Hot Pink Play Button Triangle (Pointing Left/Up-Left) inside the vertical stem
        val triPath = Path().apply {
            moveTo(w * 0.72f, h * 0.32f)
            lineTo(w * 0.58f, h * 0.34f)
            lineTo(w * 0.72f, h * 0.44f)
            close()
        }
        drawPath(
            path = triPath,
            color = pinkColor
        )

        // B. Happy Smile curve beneath the play button
        val smilePath = Path().apply {
            moveTo(w * 0.44f, h * 0.52f)
            quadraticTo(
                w * 0.62f, h * 0.66f, // Control point below
                w * 0.76f, h * 0.52f  // Right anchor point
            )
        }
        drawPath(
            path = smilePath,
            color = pinkColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w * 0.06f,
                cap = StrokeCap.Round
            )
        )
    }
}

@Composable
fun MyKaiLogoIcon(modifier: Modifier = Modifier, iconSize: Dp = 36.dp) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val tealColor = Color(0xFF00C896) // Vivid teal/turquoise matching logo exactly
        val whiteColor = Color.White

        // 1. Top Antenna
        drawLine(
            color = tealColor,
            start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.02f),
            end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.12f),
            strokeWidth = w * 0.07f,
            cap = StrokeCap.Round
        )

        // 2. Head (Horizontal rounded pill)
        drawRoundRect(
            color = tealColor,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.16f, h * 0.12f),
            size = androidx.compose.ui.geometry.Size(w * 0.68f, h * 0.36f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.18f, h * 0.18f)
        )

        // Eyes (Two solid white circles)
        val eyeRadius = w * 0.06f
        val eyeY = h * 0.30f
        drawCircle(
            color = whiteColor,
            radius = eyeRadius,
            center = androidx.compose.ui.geometry.Offset(w * 0.37f, eyeY)
        )
        drawCircle(
            color = whiteColor,
            radius = eyeRadius,
            center = androidx.compose.ui.geometry.Offset(w * 0.63f, eyeY)
        )

        // 3. Body (Solid teal circle below head)
        val bodyCenter = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.72f)
        val bodyRadius = w * 0.26f
        drawCircle(
            color = tealColor,
            radius = bodyRadius,
            center = bodyCenter
        )

        // 4. Network Graph inside Body
        val n1 = androidx.compose.ui.geometry.Offset(bodyCenter.x, bodyCenter.y - bodyRadius * 0.45f)
        val n2 = androidx.compose.ui.geometry.Offset(bodyCenter.x - bodyRadius * 0.45f, bodyCenter.y + bodyRadius * 0.35f)
        val n3 = androidx.compose.ui.geometry.Offset(bodyCenter.x + bodyRadius * 0.45f, bodyCenter.y + bodyRadius * 0.35f)
        val linkStroke = w * 0.045f

        // Connecting lines
        drawLine(color = whiteColor, start = n1, end = n2, strokeWidth = linkStroke, cap = StrokeCap.Round)
        drawLine(color = whiteColor, start = n2, end = n3, strokeWidth = linkStroke, cap = StrokeCap.Round)
        drawLine(color = whiteColor, start = n3, end = n1, strokeWidth = linkStroke, cap = StrokeCap.Round)

        // Node circles
        val nodeRadius = w * 0.048f
        drawCircle(color = whiteColor, radius = nodeRadius, center = n1)
        drawCircle(color = whiteColor, radius = nodeRadius, center = n2)
        drawCircle(color = whiteColor, radius = nodeRadius, center = n3)
    }
}

@Composable
fun MyKaiAppLogoIcon(modifier: Modifier = Modifier, iconSize: Dp = 36.dp) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val kaiGreen = Color(0xFF70C7BA)
        val sw = w * 0.10f
        val yTop = h * 0.28f
        val yBot = h * 0.72f
        val yMid = (yTop + yBot) / 2f

        // --- Letter K ---
        val kLeft = w * 0.08f
        val kRight = w * 0.32f
        drawLine(kaiGreen, androidx.compose.ui.geometry.Offset(kLeft, yTop), androidx.compose.ui.geometry.Offset(kLeft, yBot), sw, StrokeCap.Butt)
        drawLine(kaiGreen, androidx.compose.ui.geometry.Offset(kLeft, yMid), androidx.compose.ui.geometry.Offset(kRight, yTop), sw, StrokeCap.Butt)
        drawLine(kaiGreen, androidx.compose.ui.geometry.Offset(kLeft, yMid), androidx.compose.ui.geometry.Offset(kRight, yBot), sw, StrokeCap.Butt)

        // --- Letter A ---
        val aTopX = w * 0.51f
        val aLeftX = w * 0.38f
        val aRightX = w * 0.64f
        drawLine(kaiGreen, androidx.compose.ui.geometry.Offset(aTopX, yTop), androidx.compose.ui.geometry.Offset(aLeftX, yBot), sw, StrokeCap.Butt)
        drawLine(kaiGreen, androidx.compose.ui.geometry.Offset(aTopX, yTop), androidx.compose.ui.geometry.Offset(aRightX, yBot), sw, StrokeCap.Butt)
        val crossY = yTop + (yBot - yTop) * 0.58f
        val crossL = aTopX + (aLeftX - aTopX) * 0.58f
        val crossR = aTopX + (aRightX - aTopX) * 0.58f
        drawLine(kaiGreen, androidx.compose.ui.geometry.Offset(crossL, crossY), androidx.compose.ui.geometry.Offset(crossR, crossY), sw * 0.85f, StrokeCap.Butt)

        // --- Letter I ---
        val iX = w * 0.82f
        val iSerifW = w * 0.09f
        drawLine(kaiGreen, androidx.compose.ui.geometry.Offset(iX, yTop), androidx.compose.ui.geometry.Offset(iX, yBot), sw, StrokeCap.Butt)
        drawLine(kaiGreen, androidx.compose.ui.geometry.Offset(iX - iSerifW, yTop), androidx.compose.ui.geometry.Offset(iX + iSerifW, yTop), sw, StrokeCap.Butt)
        drawLine(kaiGreen, androidx.compose.ui.geometry.Offset(iX - iSerifW, yBot), androidx.compose.ui.geometry.Offset(iX + iSerifW, yBot), sw, StrokeCap.Butt)
    }
}

@Composable
fun KaskadLogoIcon(modifier: Modifier = Modifier, iconSize: Dp = 36.dp) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val kaskadGreen = Color(0xFF70C7BA) // Kaspa Tea color matching Kaskad logo

        // Draw 3 horizontal cascading wave layers
        val waveStarts = listOf(h * 0.16f, h * 0.42f, h * 0.68f)
        for (yStart in waveStarts) {
            val path = Path().apply {
                // Start left rounded corner
                moveTo(w * 0.08f, yStart + h * 0.12f)
                // Top curve flowing right
                cubicTo(
                    w * 0.10f, yStart,
                    w * 0.50f, yStart,
                    w * 0.90f, yStart + h * 0.02f
                )
                // Downward curving right tail tip
                cubicTo(
                    w * 0.95f, yStart + h * 0.12f,
                    w * 0.92f, yStart + h * 0.22f,
                    w * 0.88f, yStart + h * 0.26f
                )
                // Bottom curve returning left
                cubicTo(
                    w * 0.50f, yStart + h * 0.19f,
                    w * 0.15f, yStart + h * 0.19f,
                    w * 0.08f, yStart + h * 0.20f
                )
                // Left end join
                cubicTo(
                    w * 0.04f, yStart + h * 0.18f,
                    w * 0.04f, yStart + h * 0.14f,
                    w * 0.08f, yStart + h * 0.12f
                )
                close()
            }
            drawPath(path = path, color = kaskadGreen)
        }
    }
}

@Composable
fun DotkLogoIcon(modifier: Modifier = Modifier, iconSize: Dp = 36.dp) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val bgDark = Color(0xFF0B121C)
        val mintTeal = Color(0xFF4EE0B5)
        val strokeW = w * 0.085f

        // Dark circular base
        drawCircle(
            color = bgDark,
            radius = w * 0.48f
        )

        // Outer segmented circular ring (3 arcs with gaps)
        val ringRect = androidx.compose.ui.geometry.Rect(
            w * 0.10f,
            h * 0.10f,
            w * 0.90f,
            h * 0.90f
        )
        val ringStroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = strokeW,
            cap = StrokeCap.Round
        )

        // Arc 1: Top-Right
        drawArc(
            color = mintTeal,
            startAngle = -40f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = ringRect.topLeft,
            size = ringRect.size,
            style = ringStroke
        )

        // Arc 2: Bottom
        drawArc(
            color = mintTeal,
            startAngle = 80f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = ringRect.topLeft,
            size = ringRect.size,
            style = ringStroke
        )

        // Arc 3: Top-Left
        drawArc(
            color = mintTeal,
            startAngle = 205f,
            sweepAngle = 85f,
            useCenter = false,
            topLeft = ringRect.topLeft,
            size = ringRect.size,
            style = ringStroke
        )

        // Inner solid dot '.'
        drawCircle(
            color = mintTeal,
            radius = w * 0.075f,
            center = androidx.compose.ui.geometry.Offset(w * 0.31f, h * 0.60f)
        )

        // Lowercase 'k'
        val kStemX = w * 0.47f
        val kStemTop = h * 0.33f
        val kStemBot = h * 0.67f
        val kJointY = h * 0.52f

        // Stem
        drawLine(
            color = mintTeal,
            start = androidx.compose.ui.geometry.Offset(kStemX, kStemTop),
            end = androidx.compose.ui.geometry.Offset(kStemX, kStemBot),
            strokeWidth = strokeW,
            cap = StrokeCap.Square
        )

        // Upper arm
        drawLine(
            color = mintTeal,
            start = androidx.compose.ui.geometry.Offset(kStemX, kJointY),
            end = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.42f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )

        // Lower arm
        drawLine(
            color = mintTeal,
            start = androidx.compose.ui.geometry.Offset(kStemX, kJointY),
            end = androidx.compose.ui.geometry.Offset(w * 0.70f, h * 0.67f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

data class CustomShortcut(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val url: String
)

object CustomShortcutManager {
    private const val PREFS_NAME = "browser_custom_shortcuts"
    private const val KEY_SHORTCUTS = "shortcuts_list"

    fun loadShortcuts(context: android.content.Context): List<CustomShortcut> {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_SHORTCUTS, null) ?: return emptyList()
        val list = mutableListOf<CustomShortcut>()
        try {
            val array = org.json.JSONArray(rawJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CustomShortcut(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", "Shortcut"),
                        url = obj.optString("url", "https://")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveShortcuts(context: android.content.Context, shortcuts: List<CustomShortcut>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val array = org.json.JSONArray()
        for (item in shortcuts) {
            val obj = org.json.JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("url", item.url)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_SHORTCUTS, array.toString()).apply()
    }
}

@Composable
fun CustomShortcutLogo(
    url: String,
    name: String,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 30.dp
) {
    val cleanUrl = remember(url) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }
    val domain = remember(cleanUrl) {
        try {
            val host = android.net.Uri.parse(cleanUrl).host ?: cleanUrl
            host.removePrefix("www.")
        } catch (e: Exception) {
            cleanUrl
        }
    }
    val faviconUrl = remember(domain) {
        if (domain.isNotBlank()) "https://www.google.com/s2/favicons?domain=$domain&sz=128" else ""
    }

    var loadFailed by remember(url) { mutableStateOf(false) }

    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!loadFailed && faviconUrl.isNotEmpty()) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(faviconUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Fit,
                onError = { loadFailed = true },
                onSuccess = { loadFailed = false },
                modifier = Modifier.size(iconSize)
            )
        } else {
            val initial = (name.firstOrNull() ?: domain.firstOrNull() ?: '?')
                .uppercaseChar().toString()
            Text(
                text = initial,
                color = Color(0xFF70C7BA),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SpeedDialCircleItem(
    label: String,
    iconColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    sublabel: String? = null,
    iconContent: @Composable () -> Unit
) {
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(clickModifier)
            .width(68.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            iconContent()
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
        if (!sublabel.isNullOrBlank()) {
            Text(
                text = sublabel,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = TextMuted
            )
        }
    }
}

@Composable
fun DiscoverFeedCard(
    title: String,
    desc: String,
    category: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = null,
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    letterSpacing = 1.sp
                )
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = desc,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun BrowserSpeedDial(
    pinnedContents: List<ContentEntity>,
    viewModel: DecentralViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    var customShortcuts by remember {
        mutableStateOf(CustomShortcutManager.loadShortcuts(context))
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var shortcutToDelete by remember { mutableStateOf<CustomShortcut?>(null) }
    var addShortcutName by remember { mutableStateOf("") }
    var addShortcutUrl by remember { mutableStateOf("https://") }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.curated_browser_wallpaper_1789516978789),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Dark translucent overlay for card readability (Chrome/Brave style)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
        // 1. HORIZONTAL SPEED DIAL SHORTCUTS CONTAINER (Edge to edge, zero top gap, no write up)
        Surface(
            shape = androidx.compose.ui.graphics.RectangleShape,
            color = SurfaceDark,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Item 1: Kaspa.org
                SpeedDialCircleItem(
                    label = "Kaspa.org",
                    iconColor = Color(0xFF70C7BA),
                    onClick = { onNavigate("https://kaspa.org") }
                ) {
                    KaspaLogoIcon(iconSize = 36.dp)
                }

                // Item 2: mykai.dev
                SpeedDialCircleItem(
                    label = "mykai.dev",
                    iconColor = Color(0xFF70C7BA),
                    onClick = { onNavigate("https://mykai.dev") }
                ) {
                    MyKaiLogoIcon(iconSize = 36.dp)
                }

                // Item 3: Kaspa.com
                SpeedDialCircleItem(
                    label = "Kaspa.com",
                    iconColor = Color(0xFF70C7BA),
                    onClick = { onNavigate("https://kaspa.com") }
                ) {
                    KaspaComLogoIcon(iconSize = 36.dp)
                }

                // Item 4: Kaspa.stream
                SpeedDialCircleItem(
                    label = "Kaspa.stream",
                    iconColor = Color(0xFF8B5CF6),
                    onClick = { onNavigate("https://kaspa.stream") }
                ) {
                    KaspaStreamLogoIcon(iconSize = 36.dp)
                }

                // Item 5: igralabs.com
                SpeedDialCircleItem(
                    label = "igralabs.com",
                    iconColor = Color(0xFF70C7BA),
                    onClick = { onNavigate("https://igralabs.com") }
                ) {
                    IgraLabsLogoIcon(iconSize = 36.dp)
                }

                // Item 6: Kaskad.live
                SpeedDialCircleItem(
                    label = "Kaskad.live",
                    iconColor = Color(0xFF70C7BA),
                    onClick = { onNavigate("https://kaskad.live") }
                ) {
                    KaskadLogoIcon(iconSize = 36.dp)
                }

                // Item 7: KaChat
                SpeedDialCircleItem(
                    label = "KaChat",
                    iconColor = Color(0xFF70C7BA),
                    onClick = { onNavigate("https://kachat.app/home/") }
                ) {
                    KaChatLogoIcon(iconSize = 36.dp)
                }

                // Item 7: mykai
                SpeedDialCircleItem(
                    label = "mykai",
                    iconColor = Color(0xFF70C7BA),
                    onClick = { onNavigate("https://mykai.app") }
                ) {
                    MyKaiAppLogoIcon(iconSize = 36.dp)
                }

                // Item 8: Kasplay
                SpeedDialCircleItem(
                    label = "Kasplay",
                    iconColor = Color(0xFF35B1B1),
                    onClick = { onNavigate("https://kasplay.fun/") }
                ) {
                    KasplayLogoIcon(iconSize = 36.dp)
                }

                // Item 9: Dotk
                SpeedDialCircleItem(
                    label = "Dotk",
                    iconColor = Color(0xFF70C7BA),
                    onClick = { onNavigate("https://dotk.name/") }
                ) {
                    DotkLogoIcon(iconSize = 36.dp)
                }

                // Custom User Shortcuts
                customShortcuts.forEach { shortcut ->
                    SpeedDialCircleItem(
                        label = shortcut.name,
                        iconColor = Color(0xFF70C7BA),
                        onClick = { onNavigate(shortcut.url) },
                        onLongClick = { shortcutToDelete = shortcut }
                    ) {
                        CustomShortcutLogo(
                            url = shortcut.url,
                            name = shortcut.name,
                            iconSize = 30.dp
                        )
                    }
                }

                // Item: Add New
                SpeedDialCircleItem(
                    label = "Add new",
                    iconColor = Color(0xFF70C7BA),
                    onClick = {
                        addShortcutName = ""
                        addShortcutUrl = "https://"
                        showAddDialog = true
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF161D2B), CircleShape)
                            .border(
                                width = 1.5.dp,
                                color = Color(0xFF70C7BA).copy(alpha = 0.6f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add new shortcut",
                            tint = Color(0xFF70C7BA),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // News Feed Section with side padding
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            KaspaNewsSection(viewModel = viewModel, onNavigate = onNavigate)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Photo Attribution badge (Brave / Chrome style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "Photo by Curated Art Collection",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Add Shortcut Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                addShortcutName = ""
                addShortcutUrl = "https://"
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF70C7BA).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color(0xFF70C7BA),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Add Shortcut",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Enter a name and URL for your custom website shortcut.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = addShortcutName,
                        onValueChange = { addShortcutName = it },
                        label = { Text("Name") },
                        placeholder = { Text("e.g., Kaspa BlockDAG") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_shortcut_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF70C7BA),
                            unfocusedBorderColor = SurfaceCardBorder,
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = Color(0xFF70C7BA),
                            unfocusedLabelColor = TextSecondary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = addShortcutUrl,
                        onValueChange = { addShortcutUrl = it },
                        label = { Text("URL") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_shortcut_url_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF70C7BA),
                            unfocusedBorderColor = SurfaceCardBorder,
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = Color(0xFF70C7BA),
                            unfocusedLabelColor = TextSecondary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Real-time Logo Preview (30dp)
                    val trimmedPreviewUrl = addShortcutUrl.trim()
                    if (trimmedPreviewUrl.length > 8 && trimmedPreviewUrl != "https://") {
                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CustomShortcutLogo(
                                    url = trimmedPreviewUrl,
                                    name = addShortcutName.ifBlank { "Site" },
                                    iconSize = 30.dp
                                )
                                Text(
                                    text = if (addShortcutName.isNotBlank()) addShortcutName else trimmedPreviewUrl,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val trimmedUrl = addShortcutUrl.trim()
                val isValid = trimmedUrl.length > 8 && trimmedUrl != "https://"
                Button(
                    onClick = {
                        val formattedUrl = if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                            "https://$trimmedUrl"
                        } else {
                            trimmedUrl
                        }
                        val formattedName = addShortcutName.trim().ifEmpty {
                            try {
                                val host = android.net.Uri.parse(formattedUrl).host ?: ""
                                host.removePrefix("www.").substringBefore(".").replaceFirstChar { it.uppercase() }
                            } catch (e: Exception) {
                                "Shortcut"
                            }.ifEmpty { "Shortcut" }
                        }
                        val newShortcut = CustomShortcut(
                            name = formattedName,
                            url = formattedUrl
                        )
                        val updated = customShortcuts + newShortcut
                        customShortcuts = updated
                        CustomShortcutManager.saveShortcuts(context, updated)
                        showAddDialog = false
                        addShortcutName = ""
                        addShortcutUrl = "https://"
                    },
                    enabled = isValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF70C7BA),
                        contentColor = Color(0xFF0F111A),
                        disabledContainerColor = Color(0xFF70C7BA).copy(alpha = 0.3f),
                        disabledContentColor = Color.White.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_add_shortcut_button")
                ) {
                    Text("Add Shortcut", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        addShortcutName = ""
                        addShortcutUrl = "https://"
                    }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Delete Shortcut Confirmation Dialog
    shortcutToDelete?.let { shortcut ->
        AlertDialog(
            onDismissRequest = { shortcutToDelete = null },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Remove Shortcut",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Do you want to remove '${shortcut.name}' from your shortcuts?",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = customShortcuts.filterNot { it.id == shortcut.id }
                        customShortcuts = updated
                        CustomShortcutManager.saveShortcuts(context, updated)
                        shortcutToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RedTamper,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Remove", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { shortcutToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
    }
}

@Composable
fun BookmarkPill(label: String, tag: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
        modifier = Modifier
            .testTag(tag)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

fun openDownloadedFile(context: android.content.Context, downloadId: Long, fileName: String, originUrl: String) {
    try {
        val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        
        // 1. Try dm.getUriForDownloadedFile
        var uri = try { dm.getUriForDownloadedFile(downloadId) } catch (e: Exception) { null }
        var mime = if (uri != null) dm.getMimeTypeForDownloadedFile(downloadId) else null
        
        // 2. If null, try accessing physical file in app Downloads or public Downloads folder using FileProvider
        if (uri == null && fileName.isNotEmpty()) {
            val appDownloadFolder = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            val publicDownloadFolder = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val physicalFile = listOfNotNull(
                appDownloadFolder?.let { java.io.File(it, fileName) },
                java.io.File(publicDownloadFolder, fileName)
            ).firstOrNull { it.exists() }

            if (physicalFile != null && physicalFile.exists()) {
                val authority = "${context.packageName}.fileprovider"
                uri = androidx.core.content.FileProvider.getUriForFile(context, authority, physicalFile)
                val ext = physicalFile.extension.lowercase()
                mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            }
        }
        
        // 3. Launch ACTION_VIEW Intent if we found a valid Uri
        if (uri != null) {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime ?: "*/*")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }
        
        // Fallback: Open system downloads manager app
        val downloadsIntent = android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(downloadsIntent)
    } catch (e: Exception) {
        try {
            val downloadsIntent = android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(downloadsIntent)
        } catch (ex: Exception) {
            // ignore
        }
    }
}


@Composable
fun InstallSheetContent(
    viewModel: DecentralViewModel,
    currentResource: ResolvedResource?,
    urlInput: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val detectedPwa by viewModel.currentPagePwa.collectAsState()
    val installedPwas by viewModel.installedPwas.collectAsState()
    val activeUrl = detectedPwa?.url?.ifBlank { null } 
        ?: if (urlInput.isNotBlank()) urlInput else currentResource?.url ?: ""
    val activeTitle = detectedPwa?.name?.ifBlank { null } 
        ?: currentResource?.title?.ifBlank { null } 
        ?: if (urlInput.isNotBlank()) urlInput else "Web App"
    val isPwaSupported = detectedPwa?.hasManifest == true || !detectedPwa?.manifestUrl.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Title Row (same pattern as download UL)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.InstallMobile,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PWA",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            IconButton(onClick = onClose) {
                Icon(Icons.Default.Clear, contentDescription = "Close", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Same UL / Card container pattern as download UL
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (activeUrl.isBlank()) {
                    Text(
                        text = "No active webpage loaded to install.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Current Webpage / PWA Item Card in UL
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceDark,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                SurfaceCardBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = activeTitle,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = activeUrl,
                                            fontSize = 10.sp,
                                            color = TextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Surface(
                                        color = if (isPwaSupported) EmeraldMesh.copy(alpha = 0.15f) else ElectricCyan.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        Text(
                                            text = if (isPwaSupported) "PWA Supported" else "Web App",
                                            color = if (isPwaSupported) EmeraldMesh else ElectricCyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // In the UL you have: install, shortcut and button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. INSTALL BUTTON
                                    Button(
                                        onClick = {
                                            viewModel.installCurrentPwa(context)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricCyan,
                                            contentColor = Color.Black
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.InstallMobile,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Install",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // 2. SHORTCUT BUTTON
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.createDownloadShortcut(
                                                context = context,
                                                fileName = activeTitle,
                                                url = activeUrl,
                                                downloadId = 0L
                                            )
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = EmeraldMesh
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldMesh.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Shortcut,
                                            contentDescription = null,
                                            tint = EmeraldMesh,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Shortcut",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = EmeraldMesh
                                        )
                                    }

                                    // 3. BUTTON (Open)
                                    OutlinedButton(
                                        onClick = {
                                            onClose()
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = TextSecondary
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInBrowser,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Open",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // Installed PWAs in the UL
                        if (installedPwas.isNotEmpty()) {
                            Text(
                                text = "Installed PWAs",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            installedPwas.forEach { pwa ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SurfaceDark,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                Text(
                                                    text = pwa.name,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = pwa.url,
                                                    fontSize = 10.sp,
                                                    color = TextMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Surface(
                                                color = EmeraldMesh.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "Installed",
                                                    color = EmeraldMesh,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.removeInstalledPwa(pwa.id)
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f)),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                modifier = Modifier.height(30.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Remove", fontSize = 11.sp, fontWeight = FontWeight.Normal, color = Color.Red)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun PrivacyToggleItem(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top
        ) {
            if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = if (checked) ElectricCyan else TextMuted,
                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) ElectricCyan else TextMuted,
                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = description, fontSize = 10.sp, color = TextSecondary, lineHeight = 13.sp)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = ElectricCyan,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = SurfaceCard
            )
        )
    }
}

@Composable
fun KaspaNewsSection(
    viewModel: DecentralViewModel? = null,
    onNavigate: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var shuffleTrigger by remember { mutableIntStateOf(0) }

    val defaultItems = remember { getDefaultCuratedNews() }
    val newsItemsState by (viewModel?.newsFeedItems ?: remember {
        kotlinx.coroutines.flow.MutableStateFlow(defaultItems)
    }).collectAsState()

    val newsItems = if (newsItemsState.isNotEmpty()) newsItemsState else defaultItems

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val categories = listOf("All", "X", "YouTube", "News", "Reddit", "GitHub")
                categories.forEach { cat ->
                    val isSelected = selectedFilter == cat
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) ElectricCyan else SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.clickable {
                            if (cat == "All") {
                                shuffleTrigger++
                            }
                            selectedFilter = cat
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            when (cat) {
                                "Reddit" -> {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_reddit_logo),
                                        contentDescription = null,
                                        tint = if (isSelected) Color.Black else TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                }
                                "GitHub" -> {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_github_logo),
                                        contentDescription = null,
                                        tint = if (isSelected) Color.Black else TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                }
                                "X" -> {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_x_logo),
                                        contentDescription = null,
                                        tint = if (isSelected) Color.Black else TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                }
                                "YouTube" -> {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_youtube_logo),
                                        contentDescription = null,
                                        tint = if (isSelected) Color.Black else TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                }
                            }
                            Text(
                                text = cat,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.Black else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val filteredItems = remember(newsItems, selectedFilter, shuffleTrigger) {
            val deduplicated = newsItems.distinctBy { getKaspaNewsDeduplicationKey(it) }
            when (selectedFilter) {
                "All" -> deduplicated.sortedByDescending { it.epochMillis }
                "X" -> deduplicated.filter {
                    it.category.contains("X", ignoreCase = true) ||
                    it.url.contains("x.com") ||
                    it.url.contains("twitter") ||
                    it.url.contains("nitter") ||
                    it.author.startsWith("@")
                }.sortedByDescending { it.epochMillis }
                "YouTube" -> deduplicated.filter {
                    it.category.equals("YouTube", ignoreCase = true) ||
                    it.videoId != null ||
                    it.url.contains("youtube") ||
                    it.url.contains("youtu.be")
                }.sortedByDescending { it.epochMillis }
                "News", "Kaspa News" -> deduplicated.filter {
                    it.category.contains("News", ignoreCase = true) ||
                    it.url.contains("kaspanews") ||
                    it.url.contains("kaspa.org") ||
                    it.url.contains("medium.com")
                }.sortedByDescending { it.epochMillis }
                "Reddit" -> deduplicated.filter {
                    it.category.equals("Reddit", ignoreCase = true) ||
                    it.url.contains("reddit.com")
                }.sortedByDescending { it.epochMillis }
                "GitHub" -> deduplicated.filter {
                    it.category.equals("GitHub", ignoreCase = true) ||
                    it.url.contains("github.com")
                }.sortedByDescending { it.epochMillis }
                else -> deduplicated.filter {
                    it.category.contains(selectedFilter, ignoreCase = true)
                }.sortedByDescending { it.epochMillis }
            }
        }

        if (filteredItems.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = null,
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No news found in this category.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            filteredItems.forEachIndexed { index, item ->
                if (item.category == "YouTube" || item.videoId != null) {
                    YouTubeVideoCard(item = item, onNavigate = onNavigate)
                } else {
                    NewsFeedCard(item = item, onNavigate = onNavigate)
                }
                if (index < filteredItems.lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

fun formatEpochToTime(epochMillis: Long, originalFallback: String): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMillis
    return when {
        diff < 0 -> "1s ago"
        diff < 60_000 -> "${maxOf(1, diff / 1000)}s ago"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> {
            try {
                val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                sdf.format(java.util.Date(epochMillis))
            } catch (_: Exception) {
                originalFallback
            }
        }
    }
}

@Composable
fun NewsFeedCard(item: KaspaNewsItem, onNavigate: (String) -> Unit) {
    val brandColor = when (item.category) {
        "GitHub" -> Color(0xFFA855F7)
        "Reddit" -> Color(0xFFF97316)
        "X", "X Feeds" -> Color.White
        "YouTube" -> Color(0xFFEF4444)
        else -> Color(0xFF14B8A6)
    }

    val brandDrawableId = when (item.category) {
        "GitHub" -> R.drawable.ic_github_logo
        "Reddit" -> R.drawable.ic_reddit_logo
        "X", "X Feeds" -> R.drawable.ic_x_logo
        "YouTube" -> R.drawable.ic_youtube_logo
        else -> null
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = null,
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigate(item.url) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SurfaceCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (brandDrawableId != null) {
                        Icon(
                            painter = painterResource(brandDrawableId),
                            contentDescription = item.category,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = item.category,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                val displayTime = remember(item.epochMillis) { formatEpochToTime(item.epochMillis, item.timestamp) }
                val isRecent = remember(item.epochMillis) { (System.currentTimeMillis() - item.epochMillis) < 60_000 }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // RECENT UPDATE badge removed per user request
                    }
                    Text(
                        text = displayTime,
                        fontSize = 9.sp,
                        color = if (isRecent) EmeraldMesh else TextMuted,
                        fontWeight = if (isRecent) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.desc,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.author.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "by ${item.author}",
                        fontSize = 9.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeVideoCard(
    item: KaspaNewsItem,
    onNavigate: (String) -> Unit
) {
    val rawId = item.videoId?.trim()?.takeIf {
        it.isNotBlank() && !it.equals("undefined", ignoreCase = true) && !it.equals("null", ignoreCase = true)
    } ?: extractYouTubeVideoId(item.url)
    val effectiveVideoId = rawId?.takeIf {
        it.isNotBlank() && !it.equals("undefined", ignoreCase = true) && !it.equals("null", ignoreCase = true)
    } ?: "By_Zw58PN6o"
    var isPlaying by remember { mutableStateOf(false) }
    
    var pendingPermissionRequest by remember { mutableStateOf<android.webkit.PermissionRequest?>(null) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            pendingPermissionRequest?.let { req ->
                req.grant(req.resources)
            }
        } else {
            pendingPermissionRequest?.deny()
        }
        pendingPermissionRequest = null
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = null,
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // VIDEO PLAYER / THUMBNAIL CONTAINER (16:9)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                if (isPlaying) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            android.webkit.WebView(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                                overScrollMode = android.view.View.OVER_SCROLL_NEVER
                                isVerticalScrollBarEnabled = false
                                isHorizontalScrollBarEnabled = false

                                // Enable cookies & third-party cookies required by YouTube embed player
                                val cookieManager = android.webkit.CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    mediaPlaybackRequiresUserGesture = false
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    textZoom = 100
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    @Suppress("DEPRECATION")
                                    allowFileAccessFromFileURLs = false
                                    @Suppress("DEPRECATION")
                                    allowUniversalAccessFromFileURLs = false
                                    setGeolocationEnabled(false)
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        safeBrowsingEnabled = true
                                    }
                                    offscreenPreRaster = false
                                    val defaultUa = userAgentString
                                    userAgentString = defaultUa.replace("; wv", "")
                                    @Suppress("DEPRECATION")
                                    databaseEnabled = true
                                    domStorageEnabled = true
                                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    // Dark mode forcing removed to prevent rendering issues
                                    cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                                }
                                webChromeClient = object : android.webkit.WebChromeClient() {
                                    override fun getDefaultVideoPoster(): android.graphics.Bitmap? {
                                        return android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)
                                    }
                                    override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                                        if (request != null) {
                                            pendingPermissionRequest = request
                                            val androidPermissions = mutableListOf<String>()
                                            if (request.resources.contains(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                                                androidPermissions.add(android.Manifest.permission.RECORD_AUDIO)
                                            }
                                            if (request.resources.contains(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                                                androidPermissions.add(android.Manifest.permission.CAMERA)
                                            }
                                            if (androidPermissions.isNotEmpty()) {
                                                permissionLauncher.launch(androidPermissions.toTypedArray())
                                            } else {
                                                request.grant(request.resources)
                                                pendingPermissionRequest = null
                                            }
                                        }
                                    }
                                }
                                webViewClient = object : android.webkit.WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                        val targetUrl = request?.url?.toString() ?: return false
                                        if (!targetUrl.startsWith("data:") && !targetUrl.startsWith("about:") && !targetUrl.contains("youtube-nocookie.com/embed/")) {
                                            onNavigate(targetUrl)
                                            return true
                                        }
                                        return false
                                    }
                                    override fun onRenderProcessGone(view: android.webkit.WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                                        (view?.parent as? android.view.ViewGroup)?.removeView(view)
                                        try {
                                            view?.destroy()
                                        } catch (_: Exception) {}
                                        return true
                                    }
                                }

                                val embedHtml = """
                                    <!DOCTYPE html>
                                    <html lang="en">
                                    <head>
                                    <meta charset="utf-8">
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                    <meta name="referrer" content="strict-origin-when-cross-origin">
                                    <style>
                                        * { margin:0; padding:0; box-sizing:border-box; background:#000; }
                                        body, html { width:100%; height:100%; overflow:hidden; }
                                        iframe { width:100%; height:100%; border:none; display:block; }
                                    </style>
                                    </head>
                                    <body>
                                    <iframe 
                                        src="https://www.youtube-nocookie.com/embed/$effectiveVideoId?autoplay=1&playsinline=1&rel=0&enablejsapi=1&fs=1&widget_referrer=https%3A%2F%2Fwww.youtube-nocookie.com" 
                                        frameborder="0"
                                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                                        referrerpolicy="strict-origin-when-cross-origin"
                                        allowfullscreen>
                                    </iframe>
                                    </body>
                                    </html>
                                """.trimIndent()
                                loadDataWithBaseURL("https://www.youtube-nocookie.com", embedHtml, "text/html", "UTF-8", "https://www.youtube-nocookie.com")
                            }
                        },
                        onRelease = { webView ->
                            webView.stopLoading()
                            webView.loadUrl("about:blank")
                            webView.destroy()
                        }
                    )

                    // Overlay top controls while playing
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            modifier = Modifier.clickable { onNavigate(item.url) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = "Open in Browser",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Full Web Mode",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ElectricCyan
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.75f),
                            modifier = Modifier
                                .size(30.dp)
                                .clickable { isPlaying = false }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Video Poster & Thumbnail with Play Button
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { isPlaying = true },
                        contentAlignment = Alignment.Center
                    ) {
                        // High-res YouTube thumbnail via Coil
                        AsyncImage(
                            model = "https://img.youtube.com/vi/$effectiveVideoId/hqdefault.jpg",
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Dark gradient overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.35f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.8f)
                                        )
                                    )
                                )
                        )

                        // Top Badges
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopStart)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFEF4444)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_youtube_logo),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "YOUTUBE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            if (!item.duration.isNullOrEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.8f)
                                ) {
                                    Text(
                                        text = item.duration,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Centered YouTube Play Button
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFEF4444),
                            shadowElevation = 8.dp,
                            modifier = Modifier.size(width = 60.dp, height = 40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        // Bottom Tap to play hint
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "▶ Tap to play video",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // DETAILS SECTION
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = item.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onNavigate(item.url) }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = item.author.ifEmpty { "Kaspa Official" },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFEF4444)
                    )
                    val displayTime = remember(item.epochMillis) { formatEpochToTime(item.epochMillis, item.timestamp) }
                    Text(
                        text = " • $displayTime",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = item.desc,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SurfaceCardBorder.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { onNavigate(item.url) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Open Video",
                                tint = TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Open in Browser",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FindInPageBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceDark,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Find", tint = TextMuted)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceCard, RoundedCornerShape(4.dp))
                    .padding(8.dp),
                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(ElectricCyan),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous", tint = TextMuted)
            }
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", tint = TextMuted)
            }
        }
    }
}

@Composable
fun ThemeOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) SurfaceCard else SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, if (isSelected) ElectricCyan else SurfaceCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = TextMuted, fontSize = 12.sp)
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

