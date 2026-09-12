package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AccountEntity
import com.example.model.KaspaWalletState
import com.example.network.CryptoUtils
import com.example.network.DomainConstants
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
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DecentralizedAccountDialog(
    activeAccount: AccountEntity?,
    allAccounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onCreateAccount: (handle: String, mnemonic: String?) -> Unit,
    onLinkGoogle: (email: String, displayName: String) -> Unit,
    onOpenGoogleLogin: () -> Unit,
    onSwitchAccount: (did: String) -> Unit,
    onDeleteAccount: (account: AccountEntity) -> Unit,
    walletState: KaspaWalletState = KaspaWalletState(),
    onRefreshWallet: () -> Unit = {},
    onSendKaspa: (recipient: String, amount: Double) -> Unit = { _, _ -> },
    onOpenUrl: (url: String) -> Unit = {},
    initialTab: Int = 0
) {
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var copiedNotice by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(38.dp)
                    .height(4.dp),
                color = SurfaceCardBorder,
                shape = CircleShape
            ) {}
        },
        modifier = Modifier.testTag("decentralized_account_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
        ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCard)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (activeAccount?.accountType == "GOOGLE_ZK_BRIDGE") Color(0xFF1E293B) else SurfaceDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (activeAccount?.accountType == "GOOGLE_ZK_BRIDGE") {
                                    GoogleLogoIcon(iconSize = 18.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Decentralized Account & Wallet",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = activeAccount?.handle ?: "No Active Wallet",
                                fontSize = 11.sp,
                                color = if (activeAccount != null) EmeraldMesh else TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Tab Navigation
                val tabTitles = listOf("Kaspa Wallet", "Active Profile", "Create / Import Wallet", "Google Login", "All Accounts (${allAccounts.size})")
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SurfaceDark,
                    contentColor = ElectricCyan,
                    edgePadding = 8.dp,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = ElectricCyan,
                                height = 2.dp
                            )
                        }
                    },
                    divider = { HorizontalDivider(color = SurfaceCardBorder) }
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) ElectricCyan else TextMuted
                                )
                            }
                        )
                    }
                }

                // Copy Toast Notification Banner
                if (copiedNotice != null) {
                    Surface(
                        color = EmeraldMesh.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldMesh, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "$copiedNotice copied to clipboard", fontSize = 11.sp, color = EmeraldMesh)
                        }
                    }
                }

                // Tab Content Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> KaspaWalletView(
                            activeAccount = activeAccount,
                            walletState = walletState,
                            onRefresh = onRefreshWallet,
                            onSendKaspa = onSendKaspa,
                            onOpenUrl = onOpenUrl,
                            onNavigateToCreateOrImport = { selectedTab = 2 }
                        )
                        1 -> ActiveProfileTab(
                            account = activeAccount,
                            onCopy = { label, text ->
                                clipboardManager.setText(AnnotatedString(text))
                                copiedNotice = label
                            },
                            onNavigateToCreate = { selectedTab = 2 },
                            onNavigateToGoogle = { selectedTab = 3 },
                            onNavigateToWallet = { selectedTab = 0 }
                        )
                        2 -> CreateAccountTab(
                            onCreateAccount = { handle, mnemonic ->
                                onCreateAccount(handle, mnemonic)
                                selectedTab = 0
                            },
                            onCopy = { label, text ->
                                clipboardManager.setText(AnnotatedString(text))
                                copiedNotice = label
                            }
                        )
                        3 -> GoogleBridgeTab(
                            activeAccount = activeAccount,
                            onOpenGoogleLogin = onOpenGoogleLogin,
                            onLinkGoogle = { email, name ->
                                onLinkGoogle(email, name)
                                selectedTab = 0
                            }
                        )
                        4 -> AllAccountsTab(
                            accounts = allAccounts,
                            activeAccount = activeAccount,
                            onSwitch = { did ->
                                onSwitchAccount(did)
                                selectedTab = 0
                            },
                            onDelete = { acc ->
                                onDeleteAccount(acc)
                            },
                            onAddNew = { selectedTab = 2 }
                        )
                    }
                }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveProfileTab(
    account: AccountEntity?,
    onCopy: (label: String, text: String) -> Unit,
    onNavigateToCreate: () -> Unit,
    onNavigateToGoogle: () -> Unit,
    onNavigateToWallet: () -> Unit
) {
    if (account == null) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("No Active Decentralized Account", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Create a new self-sovereign identity or bridge your Google account.", color = TextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onNavigateToCreate,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Create Account", color = ObsidianBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onNavigateToGoogle,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Google Login", color = ElectricCyan, fontSize = 12.sp)
                }
            }
        }
        return
    }

    var showMnemonic by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Identity Hero Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = SurfaceDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (account.accountType == "GOOGLE_ZK_BRIDGE") {
                                    GoogleLogoIcon(iconSize = 20.dp)
                                } else {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = account.handle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            val badgeLabel = if (account.accountType == "GOOGLE_ZK_BRIDGE") "Google zk-Bridge Identity" else "Native Self-Custodial BlockDAG"
                            Text(
                                text = badgeLabel,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SurfaceCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Active", fontSize = 10.sp, color = ElectricCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (!account.googleEmail.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Bridged Google Account: ${account.googleEmail}",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Open Wallet Action Button
        Button(
            onClick = onNavigateToWallet,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("active_profile_open_wallet_button"),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Built-in Kaspa Wallet", color = ObsidianBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        // DID Key String
        IdentityFieldCard(
            title = "Decentralized Identifier (DID)",
            subtitle = "W3C Cryptographic Public Key ID",
            value = account.did,
            icon = Icons.Default.Key,
            onCopy = { onCopy("DID", account.did) }
        )

        // Kaspa Bech32 Address
        IdentityFieldCard(
            title = "Kaspa BlockDAG Address",
            subtitle = "Native P2PKH Layer-1 Address",
            value = account.kaspaAddress,
            icon = Icons.Default.Hub,
            onCopy = { onCopy("Kaspa Address", account.kaspaAddress) }
        )

        // P2P Swarm Peer ID
        IdentityFieldCard(
            title = "P2P Swarm Node ID",
            subtitle = "Decentralized Peer Network Routing Key",
            value = account.peerId,
            icon = Icons.Default.Language,
            onCopy = { onCopy("Peer ID", account.peerId) }
        )

        // Secret Mnemonic Seed Phrase (Collapsible & Secure)
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = AmberCentral, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("12-Word Recovery Seed Phrase", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Client-side zero-trust key backup", fontSize = 10.sp, color = TextMuted)
                        }
                    }

                    Row {
                        IconButton(
                            onClick = { showMnemonic = !showMnemonic },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (showMnemonic) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Mnemonic",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (showMnemonic) {
                            val decryptedMnemonic = CryptoUtils.getDecryptedSeed(account.seedPhrase)
                            IconButton(
                                onClick = { onCopy("12-Word Seed Phrase", decryptedMnemonic) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Phrase",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                if (showMnemonic) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val decryptedMnemonic = CryptoUtils.getDecryptedSeed(account.seedPhrase)
                    val words = decryptedMnemonic.split(" ")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        words.forEachIndexed { i, word ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceCard,
                                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${i + 1}.", fontSize = 9.sp, color = TextMuted)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(word, fontSize = 11.sp, color = ElectricCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Warning: Never share these 12 words with anyone. They allow full recovery of your identity.",
                        fontSize = 10.sp,
                        color = AmberCentral,
                        lineHeight = 13.sp
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("•••• •••• •••• •••• •••• •••• •••• •••• •••• •••• •••• ••••", fontSize = 12.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun IdentityFieldCard(
    title: String,
    subtitle: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onCopy: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = icon, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(text = subtitle, fontSize = 10.sp, color = TextMuted)
                    }
                }

                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy $title",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = value,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = ElectricCyan,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateAccountTab(
    onCreateAccount: (handle: String, mnemonic: String?) -> Unit,
    onCopy: (label: String, text: String) -> Unit = { _, _ -> }
) {
    var isImportMode by remember { mutableStateOf(false) }
    var handleInput by remember { mutableStateOf("") }
    var importMnemonicInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !isImportMode,
                onClick = { isImportMode = false },
                label = { Text("Create New Wallet", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricCyan,
                    selectedLabelColor = ObsidianBg,
                    containerColor = SurfaceDark,
                    labelColor = TextMuted
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = isImportMode,
                onClick = { isImportMode = true },
                label = { Text("Import Wallet", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricCyan,
                    selectedLabelColor = ObsidianBg,
                    containerColor = SurfaceDark,
                    labelColor = TextMuted
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = if (isImportMode) "Import Existing Kaspa Wallet & Seed Phrase" else "Generate Self-Sovereign Identity & Kaspa Wallet",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = if (isImportMode)
                "Enter your existing 12-word seed phrase to restore your Kaspa L1 address and decentralized handle."
            else
                "Keys and seed phrases are generated 100% locally on this device via SHA-256 BlockDAG entropy. No seed phrase is exposed or transmitted during creation.",
            fontSize = 11.sp,
            color = TextMuted,
            lineHeight = 15.sp
        )

        // Handle Input
        OutlinedTextField(
            value = handleInput,
            onValueChange = { handleInput = it },
            label = { Text("Custom Handle / Domain (${DomainConstants.PRIMARY_DOMAIN_SUFFIX})", fontSize = 12.sp) },
            placeholder = { Text("e.g. alice.kab or satoshi.kab", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("create_account_handle_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = SurfaceCardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark
            ),
            shape = RoundedCornerShape(10.dp)
        )

        if (isImportMode) {
            // Import Seed Input Field
            OutlinedTextField(
                value = importMnemonicInput,
                onValueChange = { importMnemonicInput = it },
                label = { Text("12-Word Recovery Seed Phrase", fontSize = 12.sp) },
                placeholder = { Text("Enter 12 space-separated recovery words...", fontSize = 12.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .testTag("import_seed_phrase_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                shape = RoundedCornerShape(10.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Action Button
        Button(
            onClick = {
                val seedToUse = if (isImportMode) importMnemonicInput.trim() else null
                onCreateAccount(handleInput.trim(), seedToUse)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("submit_create_account_button"),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.VpnKey, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isImportMode) "Import Wallet & Restore Identity" else "Create Kaspa Wallet",
                color = ObsidianBg,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun GoogleBridgeTab(
    activeAccount: AccountEntity?,
    onOpenGoogleLogin: () -> Unit,
    onLinkGoogle: (email: String, displayName: String) -> Unit
) {
    var googleEmailInput by remember { mutableStateOf(activeAccount?.googleEmail ?: "") }
    var displayNameInput by remember { mutableStateOf(activeAccount?.handle?.let { DomainConstants.removeDomainSuffix(it.removePrefix("@")) } ?: "") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Explainer Hero
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoogleLogoIcon(iconSize = 22.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Google Support & zk-Decentralized Bridge",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Seamlessly use your Google Account across Web2 services (Google Search, YouTube, OAuth) while binding it to a zero-knowledge self-sovereign BlockDAG identity. Your keys remain non-custodial.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 16.sp
                )
            }
        }

        // Direct Web Gateway Login
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("1. In-Browser Google Accounts Login", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Open Google Accounts login directly inside the DecentralNet browser gateway with full OAuth and cookie persistence support.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onOpenGoogleLogin,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_google_login_browser_button")
                ) {
                    GoogleLogoIcon(iconSize = 16.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Launch Google Sign-In in Gateway", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Bind Google Account to zk-DID
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("2. Connect Google ID to Decentralized Identity", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Generates a deterministic zero-knowledge DID and Kaspa address bound to your Google account with local cryptographic recovery.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = googleEmailInput,
                    onValueChange = { googleEmailInput = it },
                    label = { Text("Google Account Email", fontSize = 12.sp) },
                    placeholder = { Text("user@gmail.com", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldMesh,
                        unfocusedBorderColor = SurfaceCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = displayNameInput,
                    onValueChange = { displayNameInput = it },
                    label = { Text("Display Name / Alias", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Satoshi", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldMesh,
                        unfocusedBorderColor = SurfaceCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val email = googleEmailInput.trim().ifBlank { "user@gmail.com" }
                        val name = displayNameInput.trim().ifBlank { "GoogleUser" }
                        onLinkGoogle(email, name)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldMesh),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("link_google_bridge_button")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate & Activate zk-Decentralized Bridge", color = ObsidianBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AllAccountsTab(
    accounts: List<AccountEntity>,
    activeAccount: AccountEntity?,
    onSwitch: (did: String) -> Unit,
    onDelete: (account: AccountEntity) -> Unit,
    onAddNew: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Stored Identities (${accounts.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Button(
                onClick = onAddNew,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Identity", color = ObsidianBg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (accounts.isEmpty()) {
            Text("No accounts stored.", color = TextMuted, fontSize = 12.sp)
        }

        accounts.forEach { acc ->
            val isActive = acc.did == activeAccount?.did
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isActive) SurfaceCard else SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (!isActive) onSwitch(acc.did) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (acc.accountType == "GOOGLE_ZK_BRIDGE") Color(0xFF1E293B) else SurfaceDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (acc.accountType == "GOOGLE_ZK_BRIDGE") {
                                    GoogleLogoIcon(iconSize = 18.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isActive) ElectricCyan else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = acc.handle,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) ElectricCyan else TextPrimary
                                )
                                if (isActive) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = EmeraldMesh.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            fontSize = 9.sp,
                                            color = EmeraldMesh,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = acc.did.take(24) + "...",
                                fontSize = 10.sp,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isActive) {
                            OutlinedButton(
                                onClick = { onSwitch(acc.did) },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Switch", fontSize = 10.sp, color = ElectricCyan)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { onDelete(acc) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedTamper, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
