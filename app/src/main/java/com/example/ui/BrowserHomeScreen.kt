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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ContentEntity
import com.example.ui.theme.PhotoBackgroundWrapper
import com.example.ui.theme.PhotoTheme
import com.example.ui.theme.PhotoThemePreferences
import com.example.ui.theme.PhotoThemeSelectorDialog
import com.example.ui.theme.RedTamper
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.AppTab
import com.example.viewmodel.DecentralViewModel

/**
 * Pixel-accurate implementation of the Kaspa Browser Home/Start Screen
 * matching the user reference design:
 * - Top Header & Hero Banner with Anime character, Kaspa cap/hoodie, and "Kaspa The Fastest Blockchain"
 * - Interactive hotspot overlays for Logo (Account), Shield (Security), and Gear (Settings / 3-dot menu)
 * - Rounded Pill Search Omnibox with QR Scanner
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
    hasUpdateAvailable: Boolean = false
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    var customShortcuts by remember {
        mutableStateOf(CustomShortcutManager.loadShortcuts(context))
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var shortcutToDelete by remember { mutableStateOf<CustomShortcut?>(null) }
    var addShortcutName by remember { mutableStateOf("") }
    var addShortcutUrl by remember { mutableStateOf("https://") }
    var showFullNewsPage by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val photoThemePrefs = remember { PhotoThemePreferences(context) }
    var currentPhotoTheme by remember { mutableStateOf(photoThemePrefs.getSavedTheme()) }
    var isAutoRotate by remember { mutableStateOf(photoThemePrefs.isAutoRotateEnabled()) }
    var showPhotoThemeDialog by remember { mutableStateOf(false) }

    // Trigger live background fetch of real Kaspa feeds on startup
    LaunchedEffect(Unit) {
        viewModel.refreshNewsFeeds()
    }

    PhotoBackgroundWrapper(currentTheme = currentPhotoTheme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
        // Fixed Top Header + Sticky Search Omnibox (Does NOT scroll away when scrolling!)
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // TOP HEADER + HERO BANNER (Auto-cycling avatar carousel covering top edge)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
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

                androidx.compose.animation.Crossfade(
                    targetState = bannerDrawables[bannerIndex],
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000),
                    modifier = Modifier.fillMaxSize(),
                    label = "avatar_banner_carousel"
                ) { drawableRes ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = drawableRes),
                            contentDescription = "Kaspa Avatar Showcase",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Dim background lighting overlay for contrast
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                        )
                    }
                }



                // Hotspot for Settings Gear (top-right) -> Opens Settings & Traffic Audit
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 12.dp, top = 6.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { onOpenSettings() }
                        .testTag("home_header_settings_button")
                )

                // Title overlay: Kaspa BlockDAG Real Time Decentralized
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 16.dp)
                ) {
                    Text(
                        text = "Kaspa BlockDAG",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Real Time Decentralized",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF70C7BA)
                    )
                }
            }

            // PINNED SEARCH OMNIBOX (Overlaps slightly with curved bottom banner edge)
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF0B141E),
                border = BorderStroke(1.5.dp, Color(0xFF70C7BA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-54).dp)
                    .height(48.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_kaspa_launch_square),
                        contentDescription = "Kaspa Browser Logo",
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search or enter address...",
                                color = Color(0xFF8E9BAE),
                                fontSize = 14.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            cursorBrush = SolidColor(Color(0xFF70C7BA)),
                            textStyle = TextStyle(color = Color.White, fontSize = 14.5.sp),
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
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onOpenQrScanner,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR",
                            tint = Color(0xFF70C7BA),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(0.dp))

        // FIXED SPEED DIAL DOCK: stays visible while the news feed scrolls.
            // 1. ORIGINAL SPEED DIAL SHORTCUTS CONTAINER (Floating modern rounded dock)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .offset(y = (-36).dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 12.dp),
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

                    // Item 2: Braid
                    SpeedDialCircleItem(
                        label = "Braid",
                        iconColor = Color(0xFFFBBF24),
                        onClick = { onNavigate("https://app.braidswap.com") }
                    ) {
                        BraidSwapLogoIcon(iconSize = 30.dp)
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

                    // Item 10: Kasmap
                    SpeedDialCircleItem(
                        label = "Kasmap",
                        iconColor = Color(0xFF70C7BA),
                        onClick = { onNavigate("https://kasmap.org") }
                    ) {
                        KasmapLogoIcon(iconSize = 36.dp)
                    }

                    // Item 11: KaspaRocket
                    SpeedDialCircleItem(
                        label = "KaspaRocket",
                        iconColor = Color(0xFF3CD9B8),
                        onClick = { onNavigate("https://kasparocket.fun") }
                    ) {
                        KaspaRocketLogoIcon(iconSize = 30.dp)
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
                                .size(36.dp)
                                .background(Color(0xFF161D2B), CircleShape)
                                .border(
                                    width = 1.5.dp,
                                    color = SurfaceCardBorder,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add new shortcut",
                                tint = Color(0xFF70C7BA),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }


        
        // SCROLLABLE KASPA NEWS FEED: shortcuts remain outside this scroll container.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .offset(y = (-28).dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 2. ORIGINAL KASPA NEWS SECTION & CARD PAGE
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Section Header with "See All" action toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF70C7BA))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kaspa News",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showFullNewsPage = !showFullNewsPage }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (showFullNewsPage) "Collapse" else "See All",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF70C7BA)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "See All News",
                            tint = Color(0xFF70C7BA),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Render Original Live Kaspa News Feed (Categories, Videos, News Cards)
                KaspaNewsSection(
                    viewModel = viewModel,
                    onNavigate = onNavigate
                )
            }

        }

        // BOTTOM NAVIGATION BAR (Shown ONLY on Home Screen!)
        Surface(
            color = Color(0xFF070C12),
            border = BorderStroke(1.dp, Color(0xFF131D28)),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
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
                    icon = Icons.Default.SmartToy,
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

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = SurfaceDark,
                        tonalElevation = 6.dp,
                        shadowElevation = 12.dp,
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.widthIn(min = 240.dp, max = 290.dp)
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
                    .width(28.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFF70C7BA))
            )
            Spacer(modifier = Modifier.height(4.dp))
        } else {
            Spacer(modifier = Modifier.height(6.5.dp))
        }
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Color(0xFF70C7BA) else Color(0xFF94A3B8),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF70C7BA) else Color(0xFF94A3B8)
        )
    }
}
