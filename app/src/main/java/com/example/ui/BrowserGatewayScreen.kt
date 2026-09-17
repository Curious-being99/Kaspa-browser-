package com.example.ui

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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import com.example.ui.theme.VioletBridge
import com.example.viewmodel.DecentralViewModel
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@SuppressLint("SetJavaScriptEnabled", "WrongConstant", "NewApi")
@Composable
fun BrowserGatewayScreen(viewModel: DecentralViewModel, modifier: Modifier = Modifier) {
    val tabs by viewModel.browserTabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showBrowserMenu by remember { mutableStateOf(false) }
    var browserTheme by remember { mutableStateOf("classic_dark") }
    var showThemeDialog by remember { mutableStateOf(false) }
    var isReaderMode by remember { mutableStateOf(false) }
    var longPressedLinkUrl by remember { mutableStateOf<String?>(null) }
    var showLinkContextMenu by remember { mutableStateOf(false) }

    val bookmarks by viewModel.bookmarks.collectAsState()
    val searchEngine by viewModel.searchEngine.collectAsState()

    val isWalletLocked by viewModel.isWalletLocked.collectAsState()
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
                webViewInstance?.apply {
                    stopLoading()
                    clearHistory()
                    loadUrl("about:blank")
                    onPause()
                    destroy()
                }
            } catch (_: Exception) {}
            webViewInstance = null
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
    var isPullRefreshing by remember { mutableStateOf(false) }
    var pullOffsetY by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var viewSourceMode by remember { mutableStateOf(false) }
    val urlFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val urlInput by viewModel.urlInput.collectAsState()
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

    LaunchedEffect(urlInput, currentResource) {
        viewModel.updateActiveTabMetadata(urlInput, currentResource?.title ?: if (urlInput.isEmpty()) "Home" else urlInput)
    }

    LaunchedEffect(webViewInstance, webViewRecreateKey) {
        val webView = webViewInstance ?: return@LaunchedEffect
        lastProgressChangeTime = System.currentTimeMillis()

        while (true) {
            kotlinx.coroutines.delay(15000L)

            // Graceful loading indicator timeout (hides spinner without interrupting page load)
            val isCurrentlyLoading = isWebLoading
            val progressTime = lastProgressChangeTime
            val now = System.currentTimeMillis()
            if (isCurrentlyLoading && (now - progressTime > 30_000L)) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isWebLoading = false
                    viewModel.setIsLoading(false)
                }
            }

            // Check if system reported renderer process as unresponsive
            if (isRendererUnresponsive) {
                android.util.Log.w("WebViewWatchdog", "System reported renderer unresponsive. Recovering...")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    try {
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        webView.destroy()
                    } catch (_: Exception) {}
                    webViewInstance = null
                    isRendererUnresponsive = false
                    webViewRecreateKey++
                }
                break
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

    LaunchedEffect(isWebLoading, navigationSessionId) {
        if (isWebLoading) {
            kotlinx.coroutines.delay(18_000L)
            if (isWebLoading) {
                isWebLoading = false
                webProgress = 1.0f
                viewModel.setIsLoading(false)
            }
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
    val blockedTrackersCount by viewModel.blockedTrackersCount.collectAsState()
    val blockedTrackerLogs by viewModel.blockedTrackerLogs.collectAsState()
    val activeAccount by viewModel.activeAccount.collectAsState()
    val allAccounts by viewModel.allAccounts.collectAsState()
    val kaspaWalletState by viewModel.kaspaWalletState.collectAsState()
    val allDomains by viewModel.allDomains.collectAsState()
    val domainAvailability by viewModel.domainAvailability.collectAsState()
    val isRegisteringDomain by viewModel.isRegisteringDomain.collectAsState()
    val webAuthEnabled by viewModel.webAuthEnabled.collectAsState()
    val showWebAuthnRpIdDialog by viewModel.showWebAuthnRpIdDialog.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
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
            if (data != null) {
                val uris = android.webkit.WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
                uploadCallback?.onReceiveValue(uris)
            } else {
                uploadCallback?.onReceiveValue(null)
            }
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
                res.url.startsWith("http://") ||
                res.url.startsWith("https://")
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

    val backgroundModifier = when (browserTheme) {
        "oled_obsidian" -> Modifier.background(Color(0xFF090A0F))
        "aurora_gradient" -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF090A0F))))
        "cyber_gradient" -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFF0B0F17), Color(0xFF042F2E), Color(0xFF090A0F))))
        else -> Modifier.background(Color(0xFF12141C)) // Classic Dark Slate (Chrome/Brave style)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(backgroundModifier)
    ) {
        // TOP BROWSER BAR: Directly starting with the search/URL bar with Sticky Head Pull-To-Refresh
        val scope = rememberCoroutineScope()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("browser_address_bar")
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0f || pullOffsetY > 0f) {
                                change.consume()
                                pullOffsetY = (pullOffsetY + dragAmount * 0.45f).coerceIn(0f, 90f)
                            }
                        },
                        onDragEnd = {
                            if (pullOffsetY >= 40f) {
                                isPullRefreshing = true
                                scope.launch {
                                    val normalized = viewModel.normalizeUrlOrQuery(urlInput)
                                    if ((normalized.startsWith("http://") || normalized.startsWith("https://")) && webViewInstance != null) {
                                        webViewInstance?.reload()
                                    } else {
                                        viewModel.resolveUrl()
                                    }
                                    viewModel.refreshKaspaWallet()
                                    kotlinx.coroutines.delay(1200L)
                                    isPullRefreshing = false
                                }
                            }
                            pullOffsetY = 0f
                        },
                        onDragCancel = {
                            pullOffsetY = 0f
                        }
                    )
                },
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
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, ElectricCyan),
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
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )

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
                                        cursorBrush = SolidColor(ElectricCyan),
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
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
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
                                                imageVector = when (selectedProtocol) {
                                                    NetworkProtocol.HYBRID_COEXISTENCE -> Icons.Default.Hub
                                                    NetworkProtocol.DECENTRALIZED_P2P -> Icons.Default.Language
                                                    NetworkProtocol.CENTRALIZED_HTTP -> Icons.Default.Cloud
                                                },
                                                contentDescription = "Protocol Mode Logo",
                                                tint = when (selectedProtocol) {
                                                    NetworkProtocol.HYBRID_COEXISTENCE -> ElectricCyan
                                                    NetworkProtocol.DECENTRALIZED_P2P -> EmeraldMesh
                                                    NetworkProtocol.CENTRALIZED_HTTP -> AmberCentral
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
                                    Text(
                                        text = if (urlInput.isBlank()) "Search or type URL" else urlInput,
                                        color = if (urlInput.isBlank()) TextMuted else TextPrimary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Box(
                                    modifier = Modifier.size(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoading || isWebLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = ElectricCyan
                                        )
                                    } else if (urlInput.isNotEmpty()) {
                                        val isBookmarked = remember(urlInput, bookmarks) {
                                            bookmarks.any { it.url == urlInput }
                                        }

                                        IconButton(
                                            onClick = {
                                                viewModel.toggleBookmark(urlInput, currentResource?.title ?: urlInput)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                contentDescription = "Bookmark",
                                                tint = if (isBookmarked) ElectricCyan else TextMuted,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(2.dp))

                                        IconButton(
                                            onClick = {
                                                val normalized = viewModel.normalizeUrlOrQuery(urlInput)
                                                if ((normalized.startsWith("http://") || normalized.startsWith("https://")) && webViewInstance != null) {
                                                    webViewInstance?.reload()
                                                } else {
                                                    viewModel.onUserSubmitUrl(normalized)
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Reload",
                                                tint = TextMuted,
                                                modifier = Modifier.size(14.dp)
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
                                    text = { Text("Reader Mode", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, tint = TextMuted) },
                                    onClick = {
                                        showBrowserMenu = false
                                        webViewInstance?.evaluateJavascript(com.example.network.KaspaReaderMode.JS_EXTRACT_CONTENT) { result ->
                                            try {
                                                val json = org.json.JSONObject(result.removePrefix("\"").removeSuffix("\"").replace("\\\"", "\""))
                                                val title = json.getString("title")
                                                val content = json.getString("content")
                                                val readerHtml = com.example.network.KaspaReaderMode.getReaderHtml(title, content)
                                                webViewInstance?.loadDataWithBaseURL(urlInput, readerHtml, "text/html", "UTF-8", null)
                                                isReaderMode = true
                                            } catch (_: Exception) {}
                                        }
                                    }
                                )
                                                                 DropdownMenuItem(
                                     text = { Text("Customize Background", color = TextPrimary) },
                                     leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = ElectricCyan) },
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
                                    accountDialogInitialTab = 1
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

                // PULL TO REFRESH (Frameless inline banner directly after search bar)
                AnimatedVisibility(
                    visible = pullOffsetY > 0f || isPullRefreshing,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isPullRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = ElectricCyan
                            )
                        } else {
                            val rotation = (pullOffsetY * 4f) % 360f
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Pull to refresh",
                                tint = ElectricCyan,
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer(rotationZ = rotation)
                            )
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

                // Web Page Loading Progress Bar
                if ((isWebLoading || isLoading) && webProgress < 1.0f) {
                    LinearProgressIndicator(
                        progress = { if (webProgress > 0f) webProgress else 0.5f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = ElectricCyan,
                        trackColor = Color.Transparent
                    )
                }
            }
        }

    // BROWSER VIEWPORT: Full-Screen in-app rendering
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .background(Color(0xFF0B0F17))
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

                        android.widget.FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(browserBgColor)

                            val webView = WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                if (rendererCrashCount > 0) {
                                    setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                                } else {
                                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
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
                                databaseEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                safeBrowsingEnabled = false
                                setGeolocationEnabled(true)
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
                                @Suppress("DEPRECATION")
                                databaseEnabled = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

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
                                mediaPlaybackRequiresUserGesture = false
                                loadsImagesAutomatically = true
                                blockNetworkImage = false
                                blockNetworkLoads = false
                                offscreenPreRaster = true
                                setGeolocationEnabled(false)
                                userAgentString = if (desktopModeEnabled) {
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                                } else {
                                    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                                }
                            }

                            // Early document-start JavaScript injection for WebGL stability, privacy shield, and Web3 wallet bridge
                            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
                                try {
                                    androidx.webkit.WebViewCompat.addDocumentStartJavaScript(
                                        this,
                                        KaspaPrivacyEngine.getPrivacyShieldScript(safeGpuMode = rendererCrashCount > 0),
                                        setOf("*")
                                    )
                                    androidx.webkit.WebViewCompat.addDocumentStartJavaScript(
                                        this,
                                        com.example.network.KaspaWalletBridge.getInjectionScript(),
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

                            // Attach Universal Kaspa Web3 Wallet Bridge (window.kasware / window.kaspa)
                            val kaspaWalletBridge = com.example.network.KaspaWalletBridge(
                                context = ctx,
                                webViewProvider = { webViewInstance },
                                viewModel = viewModel,
                                scope = scope
                            )
                            addJavascriptInterface(kaspaWalletBridge, "KaspaWalletBridge")

                            // Long-press context menu for links and images
                            setOnLongClickListener { v ->
                                val wv = v as? WebView
                                val result = wv?.hitTestResult
                                if (result != null) {
                                    val type = result.type
                                    val extra = result.extra
                                    if (!extra.isNullOrBlank() && (
                                        type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                                        type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ||
                                        type == WebView.HitTestResult.IMAGE_TYPE ||
                                        type == WebView.HitTestResult.GEO_TYPE ||
                                        type == WebView.HitTestResult.EMAIL_TYPE ||
                                        type == WebView.HitTestResult.PHONE_TYPE
                                    )) {
                                        longPressedLinkUrl = extra
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
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)

                            val handleDeepLinkOrNavigate: (WebView?, String, Boolean) -> Boolean = { targetWv, rawUrl, hasGesture ->
                                val targetView = targetWv ?: wv
                                val targetCtx = targetView?.context ?: context
                                val cleanUrl = rawUrl.trim()
                                val currentPageUrl = targetView?.url ?: viewModel.currentResource.value?.url ?: viewModel.urlInput.value
                                val currentHost = runCatching { Uri.parse(currentPageUrl).host?.lowercase() }.getOrNull() ?: ""
                                val targetUri = runCatching { Uri.parse(cleanUrl) }.getOrNull()
                                val targetHost = targetUri?.host?.lowercase() ?: ""
                                val targetPath = targetUri?.path?.lowercase() ?: ""
                                val targetScheme = targetUri?.scheme?.lowercase() ?: ""
                                val isTikTokSite = currentHost.contains("tiktok.com") || currentHost.contains("tiktokv.com")
                                
                                if (cleanUrl.startsWith("ipfs://", ignoreCase = true) ||
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
                                } else if ((cleanUrl.startsWith("market://", ignoreCase = true) ||
                                    targetHost.contains("play.google.com") ||
                                    cleanUrl.contains("play.google.com") ||
                                    targetHost.contains("apps.apple.com") ||
                                    targetHost.contains("itunes.apple.com")) &&
                                    !KaspaPrivacyEngine.isGoogleAccountOrAuthUrl(cleanUrl) &&
                                    !KaspaPrivacyEngine.isGoogleAccountDomain(targetHost)
                                ) {
                                    // A web browser must NOT override navigation to open Google Play Store or App Store.
                                    // TikTok.com and websites try to push app installs; suppress and stay on the web.
                                    true
                                } else if (targetScheme == "tiktok" || targetScheme.startsWith("snssdk") || targetScheme == "aweme" || targetScheme == "bytedance") {
                                    // Suppress proprietary app deep links so the browser remains on TikTok.com website
                                    true
                                } else if (targetHost.contains("onelink.me") ||
                                    targetHost.contains("link.tiktok.com") ||
                                    targetHost.contains("adjust.com") ||
                                    targetHost.contains("smart.link") ||
                                    targetHost.contains("branch.io") ||
                                    targetHost.contains("app.link")
                                ) {
                                    // Suppress app store/install tracking redirects
                                    true
                                } else if (isTikTokSite && (
                                    targetPath == "/download" || targetPath.startsWith("/download/") || cleanUrl.contains("/download?") ||
                                    targetPath == "/app" || targetPath.startsWith("/app/") || cleanUrl.contains("/app?") ||
                                    targetPath == "/redirect" || targetPath.startsWith("/redirect/") || cleanUrl.contains("/redirect?") ||
                                    (!hasGesture && (targetPath == "/login" || targetPath.startsWith("/login/") || targetPath == "/signup" || targetPath.startsWith("/signup/")))
                                )) {
                                    // Suppress TikTok scroll-triggered redirects so user stays on video stream
                                    true
                                } else if (cleanUrl.startsWith("intent://", ignoreCase = true)) {
                                    try {
                                        val parsedIntent = Intent.parseUri(cleanUrl, Intent.URI_INTENT_SCHEME)
                                        if (parsedIntent != null) {
                                            val appPkg = parsedIntent.`package`?.lowercase() ?: ""
                                            val isTikTokOrPlayStore = isTikTokSite ||
                                                appPkg.contains("musically") ||
                                                appPkg.contains("tiktok") ||
                                                appPkg.contains("android.vending") ||
                                                cleanUrl.contains("snssdk", ignoreCase = true) ||
                                                cleanUrl.contains("aweme", ignoreCase = true) ||
                                                cleanUrl.contains("bytedance", ignoreCase = true)

                                            if (isTikTokOrPlayStore) {
                                                // TikTok.com does not override to open Play Store. It is a website like x.com.
                                                true
                                            } else {
                                                val pm = targetCtx.packageManager
                                                val info = pm.resolveActivity(parsedIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                                                if (info != null && appPkg != "com.android.vending") {
                                                    parsedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    targetCtx.startActivity(parsedIntent)
                                                } else {
                                                    val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
                                                    if (!fallbackUrl.isNullOrEmpty() &&
                                                        !fallbackUrl.startsWith("market://", ignoreCase = true) &&
                                                        !fallbackUrl.contains("play.google.com/store", ignoreCase = true)
                                                    ) {
                                                        viewModel.setUrlInput(fallbackUrl)
                                                        targetView?.loadUrl(fallbackUrl)
                                                    }
                                                }
                                                true
                                            }
                                        } else {
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
                                    if (targetScheme != "market" && !targetScheme.startsWith("snssdk") && targetScheme != "tiktok" && targetScheme != "aweme" && targetScheme != "bytedance") {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            val pm = targetCtx.packageManager
                                            if (pm.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY) != null) {
                                                targetCtx.startActivity(intent)
                                            }
                                        } catch (_: Exception) {}
                                    }
                                    true
                                } else {
                                    false
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
                                                    viewModel.setUrlInput(target)
                                                    view.loadUrl(target)
                                                }
                                                return true
                                            }

                                            @Deprecated("Deprecated in Java")
                                            override fun shouldOverrideUrlLoading(wv: WebView?, url: String?): Boolean {
                                                val target = url ?: return false
                                                val intercepted = handleDeepLinkOrNavigate(view, target, isUserGesture)
                                                if (!intercepted) {
                                                    viewModel.setUrlInput(target)
                                                    view.loadUrl(target)
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
                                        val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                            type = "*/*"
                                        }
                                        fileChooserLauncher.launch(intent)
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
                                try {
                                    val filename = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
                                    val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
                                        setMimeType(mimetype)
                                        addRequestHeader("cookie", android.webkit.CookieManager.getInstance().getCookie(url))
                                        addRequestHeader("User-Agent", userAgent)
                                        setDescription("Downloading file...")
                                        setTitle(filename)
                                        setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)
                                    }
                                    val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                    val downloadId = dm.enqueue(request)
                                    viewModel.addDownload(downloadId, filename, url)
                                    viewModel.startMonitoringDownload(context, downloadId)
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
                                    isReaderMode = false
                                    isWebLoading = true
                                    webProgress = 0.15f
                                    viewModel.setIsLoading(true)
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
                                            view?.tag = Pair(it, viewModel.navigationSessionId.value)
                                            viewModel.updateCurrentUrl(it)
                                            viewModel.recordBrowserTraffic(it, 160 * 1024L)
                                        }
                                    }

                                    view?.evaluateJavascript(
                                        KaspaPrivacyEngine.getPrivacyShieldScript(safeGpuMode = rendererCrashCount > 0),
                                        null
                                    )

                                    view?.evaluateJavascript(
                                        com.example.network.KaspaWalletBridge.getInjectionScript(),
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
                                    webProgress = 1.0f
                                    viewModel.setIsLoading(false)
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
                                            view?.tag = Pair(it, viewModel.navigationSessionId.value)
                                            viewModel.updateCurrentUrl(it)
                                            viewModel.addToHistory(it, view?.title ?: it)
                                        }
                                    }

                                    view?.evaluateJavascript(
                                        KaspaPrivacyEngine.getPrivacyShieldScript(safeGpuMode = rendererCrashCount > 0),
                                        null
                                    )

                                    view?.evaluateJavascript(
                                        com.example.network.KaspaWalletBridge.getInjectionScript(),
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
                                            view?.tag = Pair(it, viewModel.navigationSessionId.value)
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

                                    viewModel.setUrlInput(targetUrl)
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

                                    viewModel.setUrlInput(targetUrl)
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
                    }
                },
                update = { containerLayout ->
                    try {
                        val webView = (0 until containerLayout.childCount)
                            .mapNotNull { containerLayout.getChildAt(it) as? WebView }
                            .firstOrNull() ?: return@AndroidView

                        webViewInstance = webView
                        canGoBack = webView.canGoBack()
                        canGoForward = webView.canGoForward()

                        // Ensure browser background remains white for consistent website rendering
                        containerLayout.setBackgroundColor(android.graphics.Color.WHITE)
                        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
                            val desiredUa = if (desktopModeEnabled || isWebStore) {
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                            } else {
                                "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                            }
                            val uaChanged = webView.settings.userAgentString != desiredUa
                            if (uaChanged) {
                                webView.settings.userAgentString = desiredUa
                            }
                            
                            val loadKey = resource.url
                            val currentWvUrl = webView.url ?: ""
                            val normWv = currentWvUrl.removeSuffix("/").trim().lowercase()
                            val normRes = resource.url.removeSuffix("/").trim().lowercase()

                            val currentTag = webView.tag as? Pair<*, *>
                            val tagUrl = currentTag?.first as? String
                            val tagId = currentTag?.second as? Int
                            val tagPair = Pair(loadKey, navigationSessionId)

                            val needsNavigation = (tagUrl != loadKey || tagId != navigationSessionId)
                            if (needsNavigation) {
                                webView.tag = tagPair
                                if (normWv != normRes || tagId != navigationSessionId) {
                                    val finalUrl = if (httpsOnlyMode && resource.url.startsWith("http://", ignoreCase = true)) {
                                        resource.url.replaceFirst("http://", "https://", ignoreCase = true)
                                    } else {
                                        resource.url
                                    }
                                    webView.loadUrl(finalUrl)
                                }
                            } else if (uaChanged && normWv.isNotEmpty()) {
                                webView.reload()
                            } else {
                                webView.tag = Pair(currentWvUrl.ifEmpty { loadKey }, navigationSessionId)
                            }
                        } else {
                            val cidKey = if (resource.cid.isNotBlank()) resource.cid else "kaspa"
                            val loadKey = "${resource.url}_$cidKey"
                            val currentTag = webView.tag as? Pair<*, *>
                            val tagUrl = currentTag?.first as? String
                            val tagId = currentTag?.second as? Int
                            val tagPair = Pair(loadKey, navigationSessionId)

                            if (tagUrl != loadKey || tagId != navigationSessionId) {
                                webView.tag = tagPair
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

    // WebAuthn Passkey / RP ID In-App Authentication Notice Dialog
    if (showWebAuthnRpIdDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowWebAuthnRpIdDialog(false) },
            containerColor = SurfaceDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            icon = {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Passkey Verification Notice",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Android OS restricts hardware Passkey RP ID validation to domains linked by the site owner.",
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Continue inside this browser:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ElectricCyan
                            )
                            Text(
                                text = "1. Tap 'More options' on the screen.",
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "2. Select 'Authenticator app', 'GitHub Mobile', or 'Recovery code' to authenticate seamlessly.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.setShowWebAuthnRpIdDialog(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg)
                ) {
                    Text("Got It (Continue Here)", fontWeight = FontWeight.SemiBold)
                }
            }
        )
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

    // Decentralized Identity & Google zk-Bridge Dialog with Built-in Kaspa Wallet
    if (showAccountDialog) {
        DecentralizedAccountDialog(
            activeAccount = activeAccount,
            allAccounts = allAccounts,
            walletState = kaspaWalletState,
            initialTab = accountDialogInitialTab,
            registeredDomains = allDomains,
            domainAvailability = domainAvailability,
            isRegisteringDomain = isRegisteringDomain,
            onDismiss = { showAccountDialog = false },
            onCheckDomainAvailability = { viewModel.checkDomainAvailability(it) },
            onRegisterDomain = { domain, cid, onComplete ->
                viewModel.registerKabDomain(domain, cid, onComplete)
            },
            onTransferDomain = { domain, newOwner, onComplete ->
                viewModel.transferKabDomain(domain, newOwner, onComplete)
            },
            onDeleteDomain = { domain ->
                viewModel.deleteDomain(domain)
            },
            onCreateAccount = { handle, mnemonic, password, enableBiometric ->
                viewModel.createDecentralizedAccount(handle, mnemonic, password, enableBiometric)
            },
            isWalletLocked = isWalletLocked,
            hasWalletPassword = hasWalletPassword,
            biometricEnabled = biometricsEnabled,
            onUnlockWalletWithPassword = { password -> viewModel.unlockWalletWithPassword(password) },
            onUnlockWalletWithBiometric = { viewModel.unlockWalletWithBiometric() },
            onLockWallet = { viewModel.lockWallet() },
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
            onRefreshWallet = {
                viewModel.refreshKaspaWallet()
            },
            onSendKaspa = { to, amt ->
                viewModel.sendKaspaTransaction(to, amt)
            },
            onOpenUrl = { url ->
                showAccountDialog = false
                viewModel.openUrlInBrowser(url)
            },
            onSignOut = {
                viewModel.signOutActiveAccount()
            }
        )
    }

    // Browser Background Theme Customization Dialog (Chrome & Brave Style)
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = SurfaceDark,
            title = { Text("Customize Browser Background", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose a curated browser theme, just like Chrome and Brave:", color = TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(2.dp))

                    ThemeOptionCard(
                        title = "Classic Dark Slate",
                        subtitle = "Standard clean browser dark theme",
                        isSelected = browserTheme == "classic_dark",
                        onClick = {
                            browserTheme = "classic_dark"
                            showThemeDialog = false
                        }
                    )
                    ThemeOptionCard(
                        title = "OLED Obsidian Black",
                        subtitle = "Deep pure black for high contrast",
                        isSelected = browserTheme == "oled_obsidian",
                        onClick = {
                            browserTheme = "oled_obsidian"
                            showThemeDialog = false
                        }
                    )
                    ThemeOptionCard(
                        title = "Aurora Gradient",
                        subtitle = "Subtle deep blue and indigo gradient",
                        isSelected = browserTheme == "aurora_gradient",
                        onClick = {
                            browserTheme = "aurora_gradient"
                            showThemeDialog = false
                        }
                    )
                    ThemeOptionCard(
                        title = "Cyber Tech Gradient",
                        subtitle = "Modern tech teal and slate gradient",
                        isSelected = browserTheme == "cyber_gradient",
                        onClick = {
                            browserTheme = "cyber_gradient"
                            showThemeDialog = false
                        }
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

    if (showLinkContextMenu && longPressedLinkUrl != null) {
        val linkUrl = longPressedLinkUrl ?: ""
        AlertDialog(
            onDismissRequest = {
                showLinkContextMenu = false
                longPressedLinkUrl = null
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Link Options",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = linkUrl,
                        fontSize = 12.sp,
                        color = ElectricCyan,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = SurfaceCardBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(4.dp))

                    // Option 1: Open in New Tab
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showLinkContextMenu = false
                                longPressedLinkUrl = null
                                viewModel.createNewTab(linkUrl, "New Tab")
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Open in New Tab", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }

                    // Option 2: Open in Background Tab
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showLinkContextMenu = false
                                longPressedLinkUrl = null
                                viewModel.openBackgroundTab(linkUrl, "New Tab")
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Open in Background Tab", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }

                    // Option 3: Copy Link Address
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showLinkContextMenu = false
                                longPressedLinkUrl = null
                                clipboardManager.setText(AnnotatedString(linkUrl))
                                viewModel.setStatusMessage("Link address copied to clipboard")
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Copy Link Address", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }

                    // Option 4: Share Link
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showLinkContextMenu = false
                                longPressedLinkUrl = null
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, linkUrl)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Link"))
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Share Link", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLinkContextMenu = false
                        longPressedLinkUrl = null
                    }
                ) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
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
            KaspaNewsSection(onNavigate = onNavigate)
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
        
        // 2. If null, try accessing physical file in public Downloads folder using FileProvider
        if (uri == null && fileName.isNotEmpty()) {
            val downloadFolder = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val physicalFile = java.io.File(downloadFolder, fileName)
            if (physicalFile.exists()) {
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

data class KaspaNewsItem(
    val title: String,
    val desc: String,
    val url: String,
    val category: String, // "Reddit", "GitHub", "X Feeds", "YouTube"
    val timestamp: String,
    val author: String = "",
    val videoId: String? = null,
    val duration: String? = null,
    val epochMillis: Long = System.currentTimeMillis()
)

fun extractTagContent(xml: String, tagName: String): String {
    val regex = Regex("<$tagName(?:\\s+[^>]*)?>(.*?)</$tagName>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val match = regex.find(xml)
    if (match != null) {
        return cleanXmlText(match.groupValues[1])
    }
    return ""
}

fun extractLinkUrl(xml: String): String {
    val hrefMatch = Regex("<link[^>]+href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(xml)
    if (hrefMatch != null) {
        return cleanXmlText(hrefMatch.groupValues[1]).trim()
    }
    val content = extractTagContent(xml, "link")
    if (content.isNotEmpty()) return content.trim()
    return ""
}

fun formatEpochToDisplay(epochMillis: Long): String {
    val diff = System.currentTimeMillis() - epochMillis
    if (diff < 0) return "Just now"
    if (diff < 60_000) return "Just now"
    if (diff < 3600_000) return "${diff / 60_000}m ago"
    if (diff < 86400_000) return "${diff / 3600_000}h ago"
    val days = diff / 86400_000
    if (days < 7) return "${days}d ago"
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US)
    return sdf.format(java.util.Date(epochMillis))
}

fun parseDateToEpoch(dateStr: String): Long {
    if (dateStr.isBlank() || dateStr == "Recently" || dateStr == "Just now") return System.currentTimeMillis()
    val formats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss z",
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ss",
        "MMM dd, yyyy"
    )
    for (fmt in formats) {
        try {
            val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val parsed = sdf.parse(dateStr)
            if (parsed != null) return parsed.time
        } catch (_: Exception) {}
    }
    return System.currentTimeMillis()
}

fun extractYouTubeVideoId(url: String): String? {
    val clean = url.trim()
    if (clean.isBlank() || clean.equals("undefined", ignoreCase = true) || clean.equals("null", ignoreCase = true)) return null
    if (clean.matches(Regex("^[a-zA-Z0-9_-]{11}$")) && !clean.equals("undefined", ignoreCase = true)) return clean
    val vMatch = Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(clean)
    if (vMatch != null && !vMatch.groupValues[1].equals("undefined", ignoreCase = true)) return vMatch.groupValues[1]
    val beMatch = Regex("youtu\\.be/([a-zA-Z0-9_-]{11})").find(clean)
    if (beMatch != null && !beMatch.groupValues[1].equals("undefined", ignoreCase = true)) return beMatch.groupValues[1]
    val embedMatch = Regex("embed/([a-zA-Z0-9_-]{11})").find(clean)
    if (embedMatch != null && !embedMatch.groupValues[1].equals("undefined", ignoreCase = true)) return embedMatch.groupValues[1]
    return null
}

fun parseRssXml(xml: String, defaultCategory: String): List<KaspaNewsItem> {
    val items = mutableListOf<KaspaNewsItem>()
    try {
        var index = 0
        while (index < xml.length) {
            var itemStart = xml.indexOf("<item>", index)
            var isAtom = false
            if (itemStart == -1) {
                itemStart = xml.indexOf("<entry>", index)
                isAtom = true
            }
            if (itemStart == -1) break

            val itemEnd = if (isAtom) {
                xml.indexOf("</entry>", itemStart)
            } else {
                xml.indexOf("</item>", itemStart)
            }
            if (itemEnd == -1) break

            val itemXml = xml.substring(itemStart, itemEnd)
            index = itemEnd

            var title = extractTagContent(itemXml, "title")
            if (title.isEmpty()) title = extractTagContent(itemXml, "media:title")

            val link = extractLinkUrl(itemXml)

            var desc = extractTagContent(itemXml, "description")
            if (desc.isEmpty()) desc = extractTagContent(itemXml, "summary")
            if (desc.isEmpty()) desc = extractTagContent(itemXml, "content")
            if (desc.isEmpty()) desc = extractTagContent(itemXml, "media:description")
            if (desc.length > 200) {
                desc = desc.take(197) + "..."
            }

            var rawDate = extractTagContent(itemXml, "pubDate")
            if (rawDate.isEmpty()) rawDate = extractTagContent(itemXml, "updated")
            if (rawDate.isEmpty()) rawDate = extractTagContent(itemXml, "published")
            if (rawDate.isEmpty()) rawDate = extractTagContent(itemXml, "dc:date")

            val epochMillis = parseDateToEpoch(rawDate)
            val displayDate = if (rawDate.isNotBlank()) formatEpochToDisplay(epochMillis) else "Recently"

            var author = extractTagContent(itemXml, "name")
            if (author.isEmpty()) author = extractTagContent(itemXml, "author")
            if (author.isEmpty()) author = extractTagContent(itemXml, "dc:creator")
            if (author.isEmpty()) author = extractTagContent(itemXml, "source")
            if (author.isEmpty() && defaultCategory == "News") author = "Kaspa News"

            var videoId: String? = extractTagContent(itemXml, "yt:videoId").trim().takeIf {
                it.isNotBlank() && !it.equals("undefined", ignoreCase = true) && !it.equals("null", ignoreCase = true)
            }
            if (videoId == null && link.isNotEmpty() && !link.equals("undefined", ignoreCase = true)) {
                videoId = extractYouTubeVideoId(link)
            }

            val finalCategory = if (!videoId.isNullOrBlank() || defaultCategory == "YouTube" || link.contains("youtube.com") || link.contains("youtu.be")) {
                "YouTube"
            } else if (link.contains("x.com") || link.contains("twitter.com") || link.contains("nitter") || defaultCategory == "X") {
                "X"
            } else if (defaultCategory == "Reddit" || link.contains("reddit.com")) {
                "Reddit"
            } else if (defaultCategory == "GitHub" || link.contains("github.com")) {
                "GitHub"
            } else {
                if (defaultCategory.isBlank()) "Kaspa News" else defaultCategory
            }

            if (title.isNotBlank()) {
                items.add(
                    KaspaNewsItem(
                        title = title,
                        desc = desc.ifBlank { "Click to view full blockDAG update." },
                        url = link.ifBlank { "https://kaspa.org" },
                        category = finalCategory,
                        timestamp = displayDate,
                        author = author,
                        videoId = videoId,
                        epochMillis = epochMillis
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return items
}

fun unescapeHtmlEntities(input: String): String {
    if (input.isBlank()) return ""
    return try {
        @Suppress("DEPRECATION")
        android.text.Html.fromHtml(input).toString()
    } catch (_: Throwable) {
        input.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#32;", " ")
            .replace("&nbsp;", " ")
    }
}

fun cleanXmlText(text: String): String {
    if (text.isBlank()) return ""
    var cleaned = text

    // 1. Extract content inside CDATA blocks if present
    while (cleaned.contains("<![CDATA[")) {
        cleaned = cleaned.replace(Regex("<!\\[CDATA\\[(.*?)\\]\\]>", RegexOption.DOT_MATCHES_ALL)) { match ->
            match.groupValues[1]
        }
    }

    // 2. Unescape HTML entities first so encoded tags (e.g. &lt;!-- SC_OFF --&gt;) become literal tags/comments
    cleaned = unescapeHtmlEntities(cleaned)

    // 3. Remove HTML comments <!-- ... -->
    cleaned = cleaned.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    // 4. Remove all HTML tags <...>
    cleaned = cleaned.replace(Regex("<[^>]+>"), " ")

    // 5. Unescape any secondary entities that were double-encoded
    cleaned = unescapeHtmlEntities(cleaned)

    // 6. Remove Reddit & RSS boilerplate clutter
    cleaned = cleaned.replace(Regex("submitted by\\s+/u/\\S+", RegexOption.IGNORE_CASE), "")
    cleaned = cleaned.replace(Regex("\\[link\\]", RegexOption.IGNORE_CASE), "")
    cleaned = cleaned.replace(Regex("\\[comments\\]", RegexOption.IGNORE_CASE), "")

    // 7. Collapse all spaces, tabs, and line breaks into single spaces
    cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

    return cleaned
}

fun getKaspaNewsDeduplicationKey(item: KaspaNewsItem): String {
    val cleanTitle = item.title.lowercase().replace(Regex("[^a-z0-9]"), "")
    return if (cleanTitle.length > 8) cleanTitle else item.url.lowercase().trim()
}

@Composable
fun KaspaNewsSection(
    onNavigate: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var isRefreshing by remember { mutableStateOf(false) }
    var shuffleTrigger by remember { mutableIntStateOf(0) }
    
    val initialItems = remember {
        listOf(
            KaspaNewsItem(
                title = "@KaspaCurrency: Kaspad v0.15.2 released with DagKnight sync optimizations and mainnet BPS enhancements",
                desc = "Latest node update delivers major performance improvements for peer sync, UTXO set validation, and block propagation latency.",
                url = "https://x.com/KaspaCurrency",
                category = "X",
                timestamp = "Sep 10, 2026",
                author = "@KaspaCurrency",
                epochMillis = 1788998400000L
            ),
            KaspaNewsItem(
                title = "kaspanet/kaspad: Release v0.15.2 mainnet binaries & DagKnight DAG engine",
                desc = "Official release binaries compiled with Rust 1.80. High-performance peer-to-peer block ordering with zero latency assumptions.",
                url = "https://github.com/kaspanet/kaspad",
                category = "GitHub",
                timestamp = "Sep 10, 2026",
                author = "shaiwy",
                epochMillis = 1788998000000L
            ),
            KaspaNewsItem(
                title = "r/kaspa: Kaspad v0.15.2 is live! DagKnight performance tests inside",
                desc = "Community node operators reporting 30% reduction in sync times and ultra-low RAM usage across desktop and server nodes.",
                url = "https://reddit.com/r/kaspa",
                category = "Reddit",
                timestamp = "Sep 10, 2026",
                author = "u/BlockDAGLover",
                epochMillis = 1788997000000L
            ),
            KaspaNewsItem(
                title = "Kaspa BPS Upgrade & DagKnight Consensus Live Demo",
                desc = "Dr. Yonatan Sompolinsky and core developers demonstrate parameterless proof-of-work DAG consensus achieving unprecedented throughput.",
                url = "https://www.youtube.com/watch?v=By_Zw58PN6o",
                category = "YouTube",
                timestamp = "Sep 10, 2026",
                author = "Kaspa Official",
                videoId = "By_Zw58PN6o",
                duration = "16:45",
                epochMillis = 1788996000000L
            ),
            KaspaNewsItem(
                title = "@Kaspa_Ecosystem: New decentralised bridge & KCC-20 indexer live on testnet",
                desc = "Developers can now build cross-chain dApps on Kaspa BlockDAG with sub-second finality and zero latency overhead.",
                url = "https://x.com/Kaspa_Ecosystem",
                category = "X",
                timestamp = "Sep 09, 2026",
                author = "@Kaspa_Ecosystem",
                epochMillis = 1788912000000L
            ),
            KaspaNewsItem(
                title = "Yonatan Sompolinsky at AusCryptoCon: BlockDAG & Scalability",
                desc = "Dr. Yonatan Sompolinsky discusses the fundamentals of BlockDAG architecture, parameterless consensus, and high throughput decentralization.",
                url = "https://www.youtube.com/watch?v=By_Zw58PN6o",
                category = "YouTube",
                timestamp = "Sep 07, 2026",
                author = "Kaspa Official",
                videoId = "By_Zw58PN6o",
                duration = "14:20",
                epochMillis = 1788739200000L
            ),
            KaspaNewsItem(
                title = "Kaspa Commons X Space Featuring Kaskad",
                desc = "Community discussion covering the latest network upgrades, ecosystem development, and decentralized applications.",
                url = "https://www.youtube.com/watch?v=BbUSm6inXhg",
                category = "YouTube",
                timestamp = "Sep 06, 2026",
                author = "Kaspa Official",
                videoId = "BbUSm6inXhg",
                duration = "18:45",
                epochMillis = 1788652800000L
            ),
            KaspaNewsItem(
                title = "@KaspaCurrency: DagKnight consensus protocol adapts dynamically to live internet latency",
                desc = "Parameterless proof-of-work is the ultimate solution to the blockchain trilemma. Sub-second confirmations without hardcoded assumptions.",
                url = "https://x.com/KaspaCurrency",
                category = "X",
                timestamp = "Sep 07, 2026",
                author = "@KaspaCurrency",
                epochMillis = 1788739200000L
            ),
            KaspaNewsItem(
                title = "@Kaspa_Ecosystem: KCC-20 token indexer performance hits record highs",
                desc = "Community node operators have processed millions of KCC-20 requests seamlessly. High-speed DAG token minting and smart contracts at scale.",
                url = "https://x.com/KaspaCurrency",
                category = "X",
                timestamp = "Sep 06, 2026",
                author = "@Kaspa_Ecosystem",
                epochMillis = 1788652800000L
            ),
            KaspaNewsItem(
                title = "kaspanet/rusty-kaspa: DagKnight consensus dynamic ordering engine (PR #2491)",
                desc = "Parameterless DAG reachability tree and adaptive confirmation times. Mainnet benchmark tests achieving 32 blocks per second.",
                url = "https://github.com/kaspanet/kaspad",
                category = "GitHub",
                timestamp = "Sep 06, 2026",
                author = "shaiwy",
                epochMillis = 1788652800000L
            ),
            KaspaNewsItem(
                title = "kaspa-core/kcc20-protocol: Release v1.2.0-alpha for smart contracts",
                desc = "High-throughput token inscription standard, automated UTXO batching and validation engine for KCC-20 composable contracts.",
                url = "https://github.com/kaspanet/kaspad",
                category = "GitHub",
                timestamp = "Sep 05, 2026",
                author = "michaels",
                epochMillis = 1788566400000L
            ),
            KaspaNewsItem(
                title = "@YonatanSompo: DagKnight achieves near-optimal 49% BFT security",
                desc = "Unlike protocols with fixed latency bounds, DagKnight dynamically tightens confirmation times as network conditions improve.",
                url = "https://x.com/YonatanSompo",
                category = "X",
                timestamp = "Sep 05, 2026",
                author = "@YonatanSompo",
                epochMillis = 1788566400000L
            ),
            KaspaNewsItem(
                title = "r/kaspa: DagKnight is the true endgame for Proof-of-Work scalability",
                desc = "Why parameterless consensus changes everything: zero latency assumptions, dynamic confirmation times, and 100 BPS capability.",
                url = "https://reddit.com/r/kaspa",
                category = "Reddit",
                timestamp = "Sep 07, 2026",
                author = "u/DagMaster",
                epochMillis = 1788739200000L
            ),
            KaspaNewsItem(
                title = "r/kaspa: KCC-20 tokens are taking off! What are your favorite projects?",
                desc = "Community discussion about newly launched KCC-20 projects, volume milestones, and decentralized indexer incentives.",
                url = "https://reddit.com/r/kaspa",
                category = "Reddit",
                timestamp = "Sep 06, 2026",
                author = "u/BlockDAGLover",
                epochMillis = 1788652800000L
            ),
            KaspaNewsItem(
                title = "Dev Workshop: Performance Aspects with Michael Sutton & Hans Moog",
                desc = "In-depth engineering workshop exploring performance optimization, memory layout, and node scaling for BlockDAG.",
                url = "https://www.youtube.com/watch?v=cMFeijKSv1g",
                category = "YouTube",
                timestamp = "Sep 04, 2026",
                author = "Kaspa Official",
                videoId = "cMFeijKSv1g",
                duration = "22:10",
                epochMillis = 1788480000000L
            ),
            KaspaNewsItem(
                title = "@Kaspa_Miners: Node runners and ASIC operators testing DagKnight parameters",
                desc = "Please update your node daemon connection configurations to optimize block propagation and prepare for DagKnight testnet validation.",
                url = "https://x.com/Kaspa_Support",
                category = "X",
                timestamp = "Sep 04, 2026",
                author = "@Kaspa_Miners",
                epochMillis = 1788480000000L
            ),
            KaspaNewsItem(
                title = "Kaspa BlockDAG: Revolutionary 10 BPS Mainnet Upgrade Landmark",
                desc = "Historical milestone as Kaspa mainnet transitions smoothly to 10 blocks per second, validating sub-second transactions.",
                url = "https://medium.com/@kaspanet",
                category = "News",
                timestamp = "Aug 28, 2026",
                author = "Kaspa Research",
                epochMillis = 1787875200000L
            ),
            KaspaNewsItem(
                title = "kaspanet/kaspad: Rust Node Engine Complete Transition Milestone",
                desc = "Full deprecation of legacy Go node code base in favor of high-speed multi-threaded Rust p2p engine.",
                url = "https://github.com/kaspanet/kaspad",
                category = "GitHub",
                timestamp = "Aug 15, 2026",
                author = "shaiwy",
                epochMillis = 1786752000000L
            )
        ).distinctBy { getKaspaNewsDeduplicationKey(it) }
    }

    var newsItems by remember { mutableStateOf(initialItems) }
    val scope = rememberCoroutineScope()

    val refreshFeeds = {
        isRefreshing = true
        scope.launch {
            try {
                val fetched = withContext(Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient.Builder()
                        .protocols(listOf(okhttp3.Protocol.QUIC, okhttp3.Protocol.HTTP_1_1))
                        .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val feeds = listOf(
                        Pair("https://kaspanews.com/feed/", "News"),
                        Pair("https://kaspanews.com/feed/", "News"),
                        Pair("https://kaspa.org/feed/", "News"),
                        Pair("https://medium.com/feed/@kaspanet", "News"),
                        Pair("https://www.reddit.com/r/kaspa/.rss", "Reddit"),
                        Pair("https://github.com/kaspanet/kaspad/commits/master.atom", "GitHub"),
                        Pair("https://github.com/kaspanet/rusty-kaspa/commits/master.atom", "GitHub"),
                        Pair("https://github.com/kaspanet/kaspad/releases.atom", "GitHub"),
                        Pair("https://www.youtube.com/feeds/videos.xml?channel_id=UCsnbLKm_lpCUj63_HPW17og", "YouTube"),
                        Pair("https://www.youtube.com/feeds/videos.xml?channel_id=UCZ-FjVIxrICs_FmJUGL3R-Q", "YouTube"),
                        Pair("https://nitter.privacydev.net/KaspaCurrency/rss", "X"),
                        Pair("https://nitter.poast.org/KaspaCurrency/rss", "X")
                    )

                    val list: MutableList<KaspaNewsItem> = coroutineScope {
                        feeds.map { (url, cat) ->
                            async(Dispatchers.IO) {
                                val feedItems = mutableListOf<KaspaNewsItem>()
                                try {
                                    val request = okhttp3.Request.Builder()
                                        .url(url)
                                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) KaspaBrowser/1.0")
                                        .build()
                                    client.newCall(request).execute().use { response ->
                                        if (response.isSuccessful) {
                                            val bodyStr = response.body?.string() ?: ""
                                            feedItems.addAll(parseRssXml(bodyStr, cat))
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("KaspaNews", "Feed fetch failed for $url: ${e.message}")
                                }
                                feedItems
                            }
                        }.awaitAll().flatten().toMutableList()
                    }
                    list
                }

                val combined = (fetched + newsItems)
                    .distinctBy { getKaspaNewsDeduplicationKey(it) }
                    .sortedByDescending { it.epochMillis }
                    .take(250)

                newsItems = combined
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRefreshing = false
            }
        }
    }

    // Auto-refresh feeds periodically every 15 minutes
    LaunchedEffect(Unit) {
        while (isActive) {
            refreshFeeds()
            kotlinx.coroutines.delay(15 * 60_000L)
        }
    }

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
