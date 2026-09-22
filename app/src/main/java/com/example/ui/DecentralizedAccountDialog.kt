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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.LaunchedEffect
import com.example.data.AccountEntity
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
    onLinkGoogle: (email: String, displayName: String) -> Unit,
    onOpenGoogleLogin: () -> Unit,
    onSwitchAccount: (did: String) -> Unit,
    onDeleteAccount: (account: AccountEntity) -> Unit,
    onOpenUrl: (url: String) -> Unit = {},
    onSignOut: () -> Unit = {},
    initialTab: Int = 0,
    hasAccountPassword: Boolean = false,
    biometricEnabled: Boolean = true,
    onVerifyPassword: (password: String) -> Boolean = { false }
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
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Decentralized Account & Identity",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = activeAccount?.let { acc ->
                                    if (acc.accountType == "GOOGLE_ZK_BRIDGE") acc.googleEmail ?: acc.handle
                                    else "${acc.handle} • ${acc.kaspaAddress.take(14)}..."
                                } ?: "No Active Account",
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
                val tabTitles = listOf("Active Profile", "Google Login", "All Accounts (${allAccounts.size})")
                ScrollableTabRow(
                    selectedTabIndex = selectedTab.coerceIn(0, tabTitles.size - 1),
                    containerColor = SurfaceDark,
                    contentColor = ElectricCyan,
                    edgePadding = 8.dp,
                    indicator = { tabPositions ->
                        val currentTab = selectedTab.coerceIn(0, tabPositions.size - 1)
                        if (currentTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[currentTab]),
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
                        0 -> ActiveProfileTab(
                            account = activeAccount,
                            onCopy = { label, text ->
                                clipboardManager.setText(AnnotatedString(text))
                                copiedNotice = label
                            },
                            onNavigateToGoogle = { selectedTab = 1 },
                            hasAccountPassword = hasAccountPassword,
                            biometricEnabled = biometricEnabled,
                            onVerifyPassword = onVerifyPassword
                        )
                        1 -> GoogleBridgeTab(
                            activeAccount = activeAccount,
                            onOpenGoogleLogin = onOpenGoogleLogin,
                            onLinkGoogle = { email, name ->
                                onLinkGoogle(email, name)
                                selectedTab = 0
                            }
                        )
                        2 -> AllAccountsTab(
                            accounts = allAccounts,
                            activeAccount = activeAccount,
                            onSwitch = { did ->
                                onSwitchAccount(did)
                                selectedTab = 0
                            },
                            onDelete = { acc ->
                                onDeleteAccount(acc)
                            },
                            onVerifyPassword = onVerifyPassword,
                            onAddNew = { selectedTab = 1 }
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
    onNavigateToGoogle: () -> Unit,
    hasAccountPassword: Boolean,
    biometricEnabled: Boolean,
    onVerifyPassword: (password: String) -> Boolean
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
            Text("Bridge your Google account to create a zero-knowledge identity.", color = TextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onNavigateToGoogle,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                GoogleLogoIcon(iconSize = 16.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Google Login", color = ObsidianBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        return
    }

    var showMnemonic by remember { mutableStateOf(false) }
    var showSeedVerify by remember { mutableStateOf(false) }
    var seedVerifyPasswordInput by remember { mutableStateOf("") }
    var seedVerifyError by remember { mutableStateOf<String?>(null) }
    var isSeedVerifyPasswordVisible by remember { mutableStateOf(false) }

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
                            onClick = { 
                                if (!showMnemonic) {
                                    if (hasAccountPassword) {
                                        showSeedVerify = true
                                    } else {
                                        showMnemonic = true
                                    }
                                } else {
                                    showMnemonic = false
                                }
                            },
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

        // Mathematical Zero-Knowledge Proof of Knowledge (Sigma Protocol with Fiat-Shamir on secp256k1)
        if (!account.zkProofJson.isNullOrBlank()) {
            var showZkProofDetails by remember { mutableStateOf(false) }
            var zkVerificationMessage by remember { mutableStateOf<String?>(null) }
            var isZkValid by remember { mutableStateOf<Boolean?>(null) }

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isZkValid == true) EmeraldMesh.copy(alpha = 0.6f) else SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = if (isZkValid == true) EmeraldMesh else ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Zero-Knowledge Proof (NIZKP)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("Schnorr-Sigma Fiat-Shamir (secp256k1)", fontSize = 10.sp, color = TextMuted)
                            }
                        }

                        Row {
                            IconButton(
                                onClick = { showZkProofDetails = !showZkProofDetails },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (showZkProofDetails) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle ZK Details",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onCopy("ZK Proof JSON", account.zkProofJson) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy ZK Proof",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val json = org.json.JSONObject(account.zkProofJson)
                                    val proof = com.example.network.zk.ZkProofEngine.ZkProof.fromJson(json)
                                    val result = com.example.network.zk.ZkProofEngine.verifyZkProof(proof)
                                    isZkValid = result.isValid
                                    zkVerificationMessage = result.message
                                } catch (e: Exception) {
                                    isZkValid = false
                                    zkVerificationMessage = "Verification error: ${e.message}"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isZkValid == true) EmeraldMesh else SurfaceDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                if (isZkValid == true) Icons.Default.CheckCircle else Icons.Default.Security,
                                contentDescription = null,
                                tint = if (isZkValid == true) ObsidianBg else ElectricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isZkValid == true) "ZK Proof Verified (s·G == R + e·P)" else "Verify Real ZK Proof",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isZkValid == true) ObsidianBg else ElectricCyan
                            )
                        }
                    }

                    if (zkVerificationMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = zkVerificationMessage ?: "",
                            fontSize = 10.sp,
                            color = if (isZkValid == true) EmeraldMesh else AmberCentral
                        )
                    }

                    if (showZkProofDetails) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = account.zkProofJson,
                            fontSize = 9.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (showSeedVerify) {
            val context = androidx.compose.ui.platform.LocalContext.current
            Dialog(onDismissRequest = { 
                showSeedVerify = false
                seedVerifyPasswordInput = ""
                seedVerifyError = null
            }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ElectricCyan.copy(alpha = 0.1f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Text(
                            text = "Security Verification",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Text(
                            text = "To view your 12-word recovery seed phrase, please verify your identity. Never share this phrase with anyone.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        if (seedVerifyError != null) {
                            Text(
                                text = seedVerifyError!!,
                                color = Color(0xFFFF5555),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        OutlinedTextField(
                            value = seedVerifyPasswordInput,
                            onValueChange = {
                                seedVerifyPasswordInput = it
                                seedVerifyError = null
                            },
                            label = { Text("Account Password", fontSize = 11.sp) },
                            singleLine = true,
                            visualTransformation = if (isSeedVerifyPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isSeedVerifyPasswordVisible = !isSeedVerifyPasswordVisible }) {
                                    Icon(
                                        if (isSeedVerifyPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = SurfaceCardBorder,
                                focusedLabelColor = ElectricCyan,
                                unfocusedLabelColor = TextMuted,
                                cursorColor = ElectricCyan
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { 
                                    showSeedVerify = false
                                    seedVerifyPasswordInput = ""
                                    seedVerifyError = null
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                            ) {
                                Text("Cancel", color = TextSecondary, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    if (onVerifyPassword(seedVerifyPasswordInput)) {
                                        showSeedVerify = false
                                        showMnemonic = true
                                    } else {
                                        seedVerifyError = "Incorrect account password"
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                            ) {
                                Text("Verify", color = ObsidianBg, fontSize = 12.sp)
                            }
                        }

                        if (biometricEnabled) {
                            HorizontalDivider(color = SurfaceCardBorder, modifier = Modifier.padding(vertical = 4.dp))
                            
                            OutlinedButton(
                                onClick = {
                                    com.example.utils.BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                                        context = context,
                                        title = "Reveal Recovery Phrase",
                                        subtitle = "Verify identity to view seed phrase",
                                        onSuccess = {
                                            showSeedVerify = false
                                            showMnemonic = true
                                        },
                                        onError = { err ->
                                            seedVerifyError = err
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricCyan)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Unlock with Biometric", color = ElectricCyan, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
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
    onVerifyPassword: (String) -> Boolean,
    onAddNew: () -> Unit
) {
    var accountToDelete by remember { mutableStateOf<AccountEntity?>(null) }
    var showDeleteVerify by remember { mutableStateOf(false) }
    var deleteVerifyPasswordInput by remember { mutableStateOf("") }
    var deleteVerifyError by remember { mutableStateOf<String?>(null) }
    var isDeletePasswordVisible by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val biometricEnabled = true 

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
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(34.dp)
                    .wrapContentWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "New Identity",
                        color = ObsidianBg,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
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
                                onClick = { 
                                    accountToDelete = acc
                                    showDeleteVerify = true
                                    deleteVerifyPasswordInput = ""
                                    deleteVerifyError = null
                                },
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

    if (showDeleteVerify && accountToDelete != null) {
        Dialog(onDismissRequest = { 
            showDeleteVerify = false
            accountToDelete = null
            deleteVerifyPasswordInput = ""
            deleteVerifyError = null
        }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, RedTamper.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = RedTamper.copy(alpha = 0.1f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = RedTamper,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Text(
                        text = "Confirm Sign Out",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = "Are you sure you want to sign out and remove '${accountToDelete?.handle}'? You must have your recovery seed phrase to restore access later.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    if (deleteVerifyError != null) {
                        Text(
                            text = deleteVerifyError!!,
                            color = RedTamper,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedTextField(
                        value = deleteVerifyPasswordInput,
                        onValueChange = {
                            deleteVerifyPasswordInput = it
                            deleteVerifyError = null
                        },
                        label = { Text("Enter Account Password to Confirm", fontSize = 11.sp) },
                        singleLine = true,
                        visualTransformation = if (isDeletePasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isDeletePasswordVisible = !isDeletePasswordVisible }) {
                                Icon(
                                    if (isDeletePasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RedTamper,
                            unfocusedBorderColor = SurfaceCardBorder,
                            focusedLabelColor = RedTamper,
                            unfocusedLabelColor = TextMuted,
                            cursorColor = RedTamper
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { 
                                showDeleteVerify = false
                                accountToDelete = null
                                deleteVerifyPasswordInput = ""
                                deleteVerifyError = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Text("Cancel", color = TextSecondary, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (onVerifyPassword(deleteVerifyPasswordInput)) {
                                    onDelete(accountToDelete!!)
                                    showDeleteVerify = false
                                    accountToDelete = null
                                    deleteVerifyPasswordInput = ""
                                    deleteVerifyError = null
                                } else {
                                    deleteVerifyError = "Incorrect account password"
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RedTamper)
                        ) {
                            Text("Sign Out", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (biometricEnabled) {
                        HorizontalDivider(color = SurfaceCardBorder, modifier = Modifier.padding(vertical = 4.dp))
                        
                        OutlinedButton(
                            onClick = {
                                com.example.utils.BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                                    context = context,
                                    title = "Confirm Sign Out",
                                    subtitle = "Verify identity to remove account",
                                    onSuccess = {
                                        onDelete(accountToDelete!!)
                                        showDeleteVerify = false
                                        accountToDelete = null
                                    },
                                    onError = { err ->
                                        deleteVerifyError = err
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RedTamper)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp), tint = RedTamper)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Confirm with Biometric", color = RedTamper, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
