package com.example.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ContentEntity
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PhotoBackgroundWrapper
import com.example.ui.theme.PhotoThemePreferences
import com.example.ui.theme.PhotoThemeSelectorDialog
import com.example.ui.theme.RedTamper
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.AppTab
import com.example.viewmodel.DecentralViewModel

/**
 * Pixel-accurate implementation of the Kazium Home/Start Screen
 * matching the user reference design:
 * - Top Header & Hero Banner with Anime character, Kaspa cap/hoodie, and "Kaspa The Fastest Blockchain"
 * - Interactive hotspot overlays for Logo (Account), Shield (Security), and Gear (Settings / 3-dot menu)
 * - Rounded Pill Search Omnibox
 * - Original Speed Dial Shortcuts Dock (Kaspa.org, Braid, Kaspa.com, Kaspa.stream, igralabs, Kaskad, KaChat, Kasplay, Dotk, Kasmap, KaspaRocket, user custom shortcuts & Add shortcut dialog)
 * - Original Kaspa News section connected directly to live BlockDAG & RSS feeds (categories filter, video cards, news cards, live refresh)
 * - Bottom Navigation Bar with Home, Browser, Wallet, SI Agent, More (3 dots -> triggers 3-dot menu)
 */
@Composable
fun BrowserHomeScreen(
    pinnedContents: List<ContentEntity> = emptyList(),
    viewModel: DecentralViewModel,
    onNavigate: (String) -> Unit,
    onOpenAccount: () -> Unit = {},
    onOpenShield: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenQrScanner: () -> Unit = {},
    onSelectTab: (AppTab) -> Unit = {},
    onOpenBrowser: () -> Unit = {},
    onOpenNewTab: () -> Unit = {},
    onOpenDevConsole: () -> Unit = {},
    onToggleDesktopMode: () -> Unit = {},
    onOpenInstallSheet: () -> Unit = {},
    onToggleReaderMode: () -> Unit = {},
    onOpenTranslate: () -> Unit = {},
    onOpenThemeDialog: () -> Unit = {},
    onToggleBookmark: () -> Unit = {},
    isBookmarked: Boolean = false,
    desktopModeEnabled: Boolean = false,
    isReaderMode: Boolean = false,
    isPageTranslated: Boolean = false,
    hasUpdateAvailable: Boolean = false,
    onOpenTabSwitcher: () -> Unit = {},
    tabCount: Int = 1
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val blockedTrackersCount by viewModel.blockedTrackersCount.collectAsState()
    val isPrivacyRelayEnabled by viewModel.isPrivacyRelayEnabled.collectAsState()
    val incognitoMode by viewModel.incognitoMode.collectAsState()
    val selectedNetwork by viewModel.selectedNetwork.collectAsState()

    var customShortcuts by remember {
        mutableStateOf(CustomShortcutManager.loadShortcuts(context))
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var shortcutToDelete by remember { mutableStateOf<CustomShortcut?>(null) }
    var addShortcutName by remember { mutableStateOf("") }
    var addShortcutUrl by remember { mutableStateOf("https://") }
    var showMoreMenu by remember { mutableStateOf(false) }

    val photoThemePrefs = remember { PhotoThemePreferences(context) }
    var currentPhotoTheme by remember { mutableStateOf(photoThemePrefs.getSavedTheme()) }
    var isAutoRotate by remember { mutableStateOf(photoThemePrefs.isAutoRotateEnabled()) }
    var showPhotoThemeDialog by remember { mutableStateOf(false) }

    // Trigger live background fetch of real Kaspa feeds on startup
    LaunchedEffect(Unit) {
        viewModel.refreshNewsFeeds()
    }

    val homeDensity = LocalDensity.current
    var homeViewportHeightDp by remember { mutableStateOf(640.dp) }
    val moreMenuScrollState = rememberScrollState()

    PhotoBackgroundWrapper(currentTheme = currentPhotoTheme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    homeViewportHeightDp = with(homeDensity) { size.height.toDp() }
                }
                .background(Color.Transparent)
        ) {
            // HERO AVATAR HEADER + ELEVATED SEARCH BAR ON THE AVATAR BACKDROP
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(206.dp)
            ) {
                val bannerDrawables = listOf(
                    R.drawable.kaspa_hero_header_clean,
                    R.drawable.kaspa_hero_avatar_privacy,
                    R.drawable.kaspa_hero_avatar_charts,
                    R.drawable.kaspa_hero_avatar_payments,
                    R.drawable.kaspa_hero_avatar_nodes,
                    R.drawable.kaspa_hero_avatar_mining
                )
                var bannerIndex by remember { mutableStateOf(0) }

                LaunchedEffect(Unit) {
                    while (true) {
                        kotlinx.coroutines.delay(15000L)
                        bannerIndex = (bannerIndex + 1) % bannerDrawables.size
                    }
                }

                // Seamlessly blended avatar backdrop layer - feather fades to completely invisible (transparent)
                // at both the top and bottom edges so there is zero hard cardboard edge or dividing line against the theme background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.05f to Color.Black.copy(alpha = 0.35f),
                                    0.12f to Color.Black.copy(alpha = 0.80f),
                                    0.20f to Color.Black,
                                    0.52f to Color.Black,
                                    0.70f to Color.Black.copy(alpha = 0.72f),
                                    0.86f to Color.Black.copy(alpha = 0.28f),
                                    0.96f to Color.Black.copy(alpha = 0.04f),
                                    1.0f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                ) {
                    androidx.compose.animation.Crossfade(
                        targetState = bannerDrawables[bannerIndex],
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000),
                        modifier = Modifier.fillMaxSize(),
                        label = "avatar_banner_carousel"
                    ) { drawableRes ->
                        Image(
                            painter = painterResource(id = drawableRes),
                            contentDescription = "Kaspa Avatar Showcase",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Atmospheric scrim: provides high contrast for top header actions while smoothly feathering out
                    // into transparency at both top and bottom edges so it blends into the wallpaper with no cardboard line
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.06f to ObsidianBg.copy(alpha = 0.32f),
                                    0.16f to ObsidianBg.copy(alpha = 0.56f),
                                    0.32f to ObsidianBg.copy(alpha = 0.22f),
                                    0.58f to ObsidianBg.copy(alpha = 0.32f),
                                    0.82f to ObsidianBg.copy(alpha = 0.12f),
                                    1.0f to Color.Transparent
                                )
                            )
                    )
                }

                // Top Browser Header Controls (Title + Live Network/Shield Status + Quick Actions)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Kaspa",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp,
                            color = Color.White
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpenShield() }
                                .padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (incognitoMode) Color(0xFFA78BFA) else ElectricCyan)
                            )
                            Text(
                                text = "Real time decentralized",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Original Browser Tab Number Button
                        BrowserTabSwitcherButton(
                            tabCount = tabCount,
                            onClick = onOpenTabSwitcher,
                            tint = Color.White,
                            testTag = "home_tab_switcher_square_button"
                        )

                        // Settings trigger
                        Surface(
                            onClick = onOpenSettings,
                            shape = CircleShape,
                            color = ObsidianBg.copy(alpha = 0.44f),
                            border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.16f)),
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("home_header_settings_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings & Traffic Audit",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // ELEVATED SEARCH BAR DIRECTLY ON THE AVATAR BACKDROP
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF101218).copy(alpha = 0.88f),
                    border = BorderStroke(
                        width = 0.85.dp,
                        color = if (searchQuery.isNotBlank()) {
                            ElectricCyan.copy(alpha = 0.65f)
                        } else {
                            Color.White.copy(alpha = 0.18f)
                        }
                    ),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 28.dp)
                        .height(46.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 14.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_kaspa_launch_square),
                            contentDescription = "Kaspa Logo",
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search web, .kas, or enter URL",
                                    color = TextSecondary,
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                cursorBrush = SolidColor(ElectricCyan),
                                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = {
                                    val trimmed = searchQuery.trim()
                                    if (trimmed.isNotBlank()) {
                                        keyboardController?.hide()
                                        onNavigate(trimmed)
                                    }
                                }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("home_search_omnibox_input")
                            )
                        }

                        if (searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Surface(
                                onClick = {
                                    val trimmed = searchQuery.trim()
                                    if (trimmed.isNotBlank()) {
                                        keyboardController?.hide()
                                        onNavigate(trimmed)
                                    }
                                },
                                shape = CircleShape,
                                color = ElectricCyan,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Go",
                                        tint = ObsidianBg,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else {
                            IconButton(
                                onClick = onOpenBrowser,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Open full address bar",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            IconButton(
                                onClick = onOpenNewTab,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Tab",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // NARROW, COMPACT SPEED DIAL DOCK (tucked cleanly under the search bar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val overlap = 14.dp.roundToPx()
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, (placeable.height - overlap).coerceAtLeast(0)) {
                            placeable.place(0, -overlap)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF13151C).copy(alpha = 0.86f),
                    border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.09f)),
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Item 1: Kaspa.org
                        SpeedDialCircleItem(
                            label = "Kaspa.org",
                            iconColor = ElectricCyan,
                            onClick = { onNavigate("https://kaspa.org") }
                        ) {
                            KaspaLogoIcon(iconSize = 26.dp)
                        }

                        // Item 2: Braid
                        SpeedDialCircleItem(
                            label = "Braid",
                            iconColor = Color(0xFFFBBF24),
                            onClick = { onNavigate("https://app.braidswap.com") }
                        ) {
                            BraidSwapLogoIcon(iconSize = 22.dp)
                        }

                        // Item 3: Kaspa.com
                        SpeedDialCircleItem(
                            label = "Kaspa.com",
                            iconColor = ElectricCyan,
                            onClick = { onNavigate("https://kaspa.com") }
                        ) {
                            KaspaComLogoIcon(iconSize = 26.dp)
                        }

                        // Item 4: Kaspa.stream
                        SpeedDialCircleItem(
                            label = "Kaspa.stream",
                            iconColor = Color(0xFF8B5CF6),
                            onClick = { onNavigate("https://kaspa.stream") }
                        ) {
                            KaspaStreamLogoIcon(iconSize = 26.dp)
                        }

                        // Item 5: igralabs.com
                        SpeedDialCircleItem(
                            label = "igralabs.com",
                            iconColor = ElectricCyan,
                            onClick = { onNavigate("https://igralabs.com") }
                        ) {
                            IgraLabsLogoIcon(iconSize = 26.dp)
                        }

                        // Item 6: Kaskad.live
                        SpeedDialCircleItem(
                            label = "Kaskad.live",
                            iconColor = ElectricCyan,
                            onClick = { onNavigate("https://kaskad.live") }
                        ) {
                            KaskadLogoIcon(iconSize = 26.dp)
                        }

                        // Item 7: KaChat
                        SpeedDialCircleItem(
                            label = "KaChat",
                            iconColor = ElectricCyan,
                            onClick = { onNavigate("https://kachat.app/home/") }
                        ) {
                            KaChatLogoIcon(iconSize = 26.dp)
                        }

                        // Item 8: Kasplay
                        SpeedDialCircleItem(
                            label = "Kasplay",
                            iconColor = Color(0xFF35B1B1),
                            onClick = { onNavigate("https://kasplay.fun/") }
                        ) {
                            KasplayLogoIcon(iconSize = 26.dp)
                        }

                        // Item 9: Dotk
                        SpeedDialCircleItem(
                            label = "Dotk",
                            iconColor = ElectricCyan,
                            onClick = { onNavigate("https://dotk.name/") }
                        ) {
                            DotkLogoIcon(iconSize = 26.dp)
                        }

                        // Item 10: Kasmap
                        SpeedDialCircleItem(
                            label = "Kasmap",
                            iconColor = ElectricCyan,
                            onClick = { onNavigate("https://kasmap.org") }
                        ) {
                            KasmapLogoIcon(iconSize = 26.dp)
                        }

                        // Item 11: KaspaRocket
                        SpeedDialCircleItem(
                            label = "KaspaRocket",
                            iconColor = Color(0xFF3CD9B8),
                            onClick = { onNavigate("https://kasparocket.fun") }
                        ) {
                            KaspaRocketLogoIcon(iconSize = 22.dp)
                        }

                        // Custom User Shortcuts
                        customShortcuts.forEach { shortcut ->
                            SpeedDialCircleItem(
                                label = shortcut.name,
                                iconColor = ElectricCyan,
                                onClick = { onNavigate(shortcut.url) },
                                onLongClick = { shortcutToDelete = shortcut }
                            ) {
                                CustomShortcutLogo(
                                    url = shortcut.url,
                                    name = shortcut.name,
                                    iconSize = 24.dp
                                )
                            }
                        }

                        // Item: Add New
                        SpeedDialCircleItem(
                            label = "Add new",
                            iconColor = ElectricCyan,
                            onClick = {
                                addShortcutName = ""
                                addShortcutUrl = "https://"
                                showAddDialog = true
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0xFF1A1E28), CircleShape)
                                    .border(
                                        width = 0.85.dp,
                                        color = Color.White.copy(alpha = 0.14f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add new shortcut",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            // SCROLLABLE KASPA NEWS FEED (pulled closer toward search bar & dock)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = 14.dp)
            ) {
                KaspaNewsSection(
                    viewModel = viewModel,
                    onNavigate = onNavigate
                )
            }

            // BOTTOM NAVIGATION BAR (Sleek refined dark bar)
            Surface(
                color = SurfaceDark.copy(alpha = 0.96f),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        thickness = 0.75.dp,
                        color = Color.White.copy(alpha = 0.08f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeBottomNavItem(
                            icon = Icons.Default.Home,
                            label = "Home",
                            isSelected = true,
                            onClick = {},
                            modifier = Modifier.weight(1f)
                        )
                        HomeBottomNavItem(
                            icon = Icons.Default.Bookmark,
                            label = "Bookmarks",
                            isSelected = false,
                            onClick = onOpenBookmarks,
                            modifier = Modifier.weight(1f)
                        )
                        HomeBottomNavItem(
                            icon = Icons.Default.AccountBalanceWallet,
                            label = "Wallet",
                            isSelected = false,
                            onClick = { onSelectTab(AppTab.KASPA_WALLET) },
                            modifier = Modifier.weight(1f)
                        )
                        HomeBottomNavItem(
                            icon = Icons.Default.AutoAwesome,
                            label = "SI Agent",
                            isSelected = false,
                            onClick = { onSelectTab(AppTab.SI_AGENT) },
                            modifier = Modifier.weight(1f)
                        )
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomEnd) {
                            HomeBottomNavItem(
                                icon = Icons.Default.MoreHoriz,
                                label = "More",
                                isSelected = false,
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.fillMaxWidth()
                            )

                            ScrollableThreeDotsMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                maxMenuHeight = 740.dp,
                                scrollState = moreMenuScrollState,
                                border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.10f)),
                                modifier = Modifier.testTag("home_more_menu_popup")
                            ) {

                        DropdownMenuItem(
                            text = { Text("New Tab", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF70C7BA)) },
                            onClick = {
                                showMoreMenu = false
                                onOpenNewTab()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Find in Page", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF70C7BA)) },
                            onClick = {
                                showMoreMenu = false
                                onOpenBrowser()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Dev Console", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFF70C7BA)) },
                            onClick = {
                                showMoreMenu = false
                                onOpenDevConsole()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Desktop site", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color(0xFF70C7BA)) },
                            trailingIcon = {
                                Checkbox(
                                    checked = desktopModeEnabled,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF70C7BA),
                                        checkmarkColor = Color.Black,
                                        uncheckedColor = Color(0xFF94A3B8)
                                    )
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onToggleDesktopMode()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Page", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF70C7BA)) },
                            onClick = {
                                showMoreMenu = false
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "https://kaspa.org")
                                }
                                context.startActivity(Intent.createChooser(intent, "Share URL"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to device", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.InstallMobile, contentDescription = null, tint = Color(0xFF70C7BA)) },
                            onClick = {
                                showMoreMenu = false
                                onOpenInstallSheet()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isReaderMode) "Exit Reader Mode" else "Reader Mode", color = TextPrimary) },
                            leadingIcon = { Icon(if (isReaderMode) Icons.Default.Close else Icons.AutoMirrored.Filled.Article, contentDescription = null, tint = Color(0xFF70C7BA)) },
                            onClick = {
                                showMoreMenu = false
                                onToggleReaderMode()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isPageTranslated) "Page Translator (Active)" else "Translate Page", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Translate, contentDescription = "Translate Page", tint = Color(0xFF70C7BA)) },
                            onClick = {
                                showMoreMenu = false
                                onOpenTranslate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Customize Background", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = Color(0xFF70C7BA)) },
                            onClick = {
                                showMoreMenu = false
                                showPhotoThemeDialog = true
                                onOpenThemeDialog()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Offline Arcade", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF70C7BA)) },
                            onClick = {
                                showMoreMenu = false
                                onNavigate("kaspa://offline-game")
                            }
                        )
                        HorizontalDivider(color = SurfaceCardBorder, modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("Network (Mesh Radar)", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Hub, contentDescription = null, tint = Color(0xFF70C7BA)) },
                            onClick = {
                                showMoreMenu = false
                                onSelectTab(AppTab.MESH_RADAR)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Passwords & Passkeys", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF70C7BA)) },
                            onClick = {
                                showMoreMenu = false
                                viewModel.setShowPasswordVaultSheet(true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Passkey Diagnostics", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFF70C7BA)) },
                            onClick = {
                                showMoreMenu = false
                                viewModel.setShowWebAuthnRpIdDialog(true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings & Traffic Audit", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF70C7BA)) },
                            onClick = {
                                showMoreMenu = false
                                onSelectTab(AppTab.TRAFFIC_AUDIT)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Update Zone", color = TextPrimary)
                                    if (hasUpdateAvailable) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF70C7BA).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "NEW",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF70C7BA),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = Color(0xFF70C7BA)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onSelectTab(AppTab.TRAFFIC_AUDIT)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Library (History & Bookmarks)", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, tint = Color(0xFF70C7BA)) },
                            onClick = {
                                showMoreMenu = false
                                onOpenHistory()
                            }
                        )
                            }
                        }
                    }
                }
            }
        }
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

                    val trimmedPreviewUrl = addShortcutUrl.trim()
                    if (trimmedPreviewUrl.length > 8 && trimmedPreviewUrl != "https://") {
                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SurfaceCardBorder),
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

    if (showPhotoThemeDialog) {
        PhotoThemeSelectorDialog(
            currentTheme = currentPhotoTheme,
            isAutoRotate = isAutoRotate,
            onSelectTheme = { theme ->
                currentPhotoTheme = theme
                photoThemePrefs.saveTheme(theme)
            },
            onToggleAutoRotate = { enabled ->
                isAutoRotate = enabled
                photoThemePrefs.setAutoRotateEnabled(enabled)
            },
            onDismissRequest = { showPhotoThemeDialog = false }
        )
    }
}

@Composable
private fun HomeBottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(ElectricCyan)
            )
            Spacer(modifier = Modifier.height(3.dp))
        } else {
            Spacer(modifier = Modifier.height(5.5.dp))
        }
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) ElectricCyan else TextMuted,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) ElectricCyan else TextMuted
        )
    }
}

@Composable
fun BrowserTabSwitcherButton(
    tabCount: Int,
    onClick: () -> Unit,
    tint: Color = TextPrimary,
    modifier: Modifier = Modifier,
    testTag: String = "tab_switcher_square_button"
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(1.5.dp, tint, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center
        ) {
            val effectiveTabCount = tabCount.coerceAtLeast(1)
            val countText = if (effectiveTabCount > 99) "99+" else effectiveTabCount.toString()
            val textSize = when {
                countText.length >= 3 -> 7.5.sp
                countText.length == 2 -> 9.sp
                else -> 10.5.sp
            }
            Text(
                text = countText,
                fontSize = textSize,
                fontWeight = FontWeight.Bold,
                color = tint,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(
                        includeFontPadding = false
                    ),
                    lineHeight = textSize,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both
                    )
                ),
                modifier = Modifier.wrapContentSize(Alignment.Center)
            )
        }
    }
}
