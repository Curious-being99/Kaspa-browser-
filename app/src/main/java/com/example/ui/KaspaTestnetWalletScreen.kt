package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.KaspaTransactionItem
import com.example.network.CryptoUtils
import com.example.ui.components.KaspaQrCodeView
import com.example.ui.theme.AmberCentral
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.KaspaTea
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RedTamper
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.AppTab
import com.example.viewmodel.DecentralViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KaspaTestnetWalletScreen(
    viewModel: DecentralViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val walletState by viewModel.kaspaWalletState.collectAsState()
    val testnetAddress by viewModel.testnetAddress.collectAsState()
    val activeAccount by viewModel.activeAccount.collectAsState()
    val allAccounts by viewModel.allAccounts.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showSeedPhraseDialog by remember { mutableStateOf(false) }
    var showCreateWalletDialog by remember { mutableStateOf(false) }
    var showImportWalletDialog by remember { mutableStateOf(false) }
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }

    // Send Form State
    var recipientInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var showConfirmSendDialog by remember { mutableStateOf(false) }

    // Handle Hardware/System Back -> Return to Browser Gateway
    BackHandler {
        viewModel.setTab(AppTab.BROWSER_GATEWAY)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshTestnetWallet()
    }

    if (activeAccount == null) {
        WalletSetupFlow(
            viewModel = viewModel,
            onBackToBrowser = { viewModel.setTab(AppTab.BROWSER_GATEWAY) },
            modifier = modifier
        )
        return
    }

    val rotationTransition = rememberInfiniteTransition(label = "refresh_rotation")
    val refreshAngle by rotationTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refresh_angle"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("kaspa_testnet_wallet_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { viewModel.setTab(AppTab.BROWSER_GATEWAY) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("wallet_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Browser",
                            tint = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Kaspa Wallet",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = KaspaTea.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, KaspaTea.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "TN10",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KaspaTea,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = activeAccount?.handle ?: "Kaspa Testnet 10",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { showCreateWalletDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("wallet_create_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Wallet",
                            tint = KaspaTea
                        )
                    }

                    IconButton(
                        onClick = { viewModel.refreshTestnetWallet() },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("refresh_wallet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Wallet",
                            tint = KaspaTea,
                            modifier = if (walletState.isLoading) Modifier.rotate(refreshAngle) else Modifier
                        )
                    }

                    IconButton(
                        onClick = { showSeedPhraseDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("wallet_keys_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Keys & Backup",
                            tint = TextSecondary
                        )
                    }
                }
            }

            // Testnet Warning Banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                color = SurfaceDark,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, SurfaceCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = KaspaTea,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Isolated Testnet 10 environment · Free testnet KAS",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Main Hero Balance Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("wallet_balance_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(KaspaTea.copy(alpha = 0.5f), CyanGlow.copy(alpha = 0.2f), SurfaceCardBorder)
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(KaspaTea)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "api-tn10.kaspa.org",
                                fontSize = 10.5.sp,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Text(
                                text = "${walletState.utxosCount} UTXOs",
                                fontSize = 10.sp,
                                color = KaspaTea,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "%.4f KAS".format(walletState.balanceKas),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "≈ %,d Sompis".format(walletState.balanceSompis),
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Address Pill with One-Tap Copy
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (testnetAddress.isNotBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Address", testnetAddress))
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Address copied to clipboard!")
                                    }
                                }
                            }
                            .testTag("copy_address_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (testnetAddress.isNotBlank()) testnetAddress else "Deriving address...",
                                fontSize = 10.5.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = KaspaTea,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Standalone Action Button Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickActionButton(
                    icon = Icons.Default.Send,
                    label = "Send",
                    color = KaspaTea,
                    onClick = { selectedTabIndex = 1 }
                )
                QuickActionButton(
                    icon = Icons.AutoMirrored.Filled.CallReceived,
                    label = "Receive",
                    color = ElectricCyan,
                    onClick = { selectedTabIndex = 2 }
                )
                QuickActionButton(
                    icon = Icons.Default.WaterDrop,
                    label = "Faucet",
                    color = KaspaTea,
                    onClick = { viewModel.openTestnetFaucet(context) }
                )
                QuickActionButton(
                    icon = Icons.Default.OpenInBrowser,
                    label = "Explorer",
                    color = TextSecondary,
                    onClick = { viewModel.openTestnetExplorer() }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Single-Line Non-Wrapping Tab Navigation
            val tabs = listOf("History", "Send", "Receive", "Manage")
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = ObsidianBg,
                contentColor = KaspaTea,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = KaspaTea,
                            height = 2.5.dp
                        )
                    }
                },
                divider = { HorizontalDivider(color = SurfaceCardBorder) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == index) KaspaTea else TextMuted,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (selectedTabIndex) {
                    0 -> HistoryTabContent(
                        transactions = walletState.recentTransactions,
                        isLoading = walletState.isLoading,
                        onOpenTx = { txId -> viewModel.openTestnetTxExplorer(txId) },
                        onGetFaucetCoins = { viewModel.openTestnetFaucet(context) }
                    )
                    1 -> SendTabContent(
                        senderAddress = testnetAddress,
                        balanceKas = walletState.balanceKas,
                        isSending = walletState.isSending,
                        statusNotice = walletState.statusNotice,
                        recipientInput = recipientInput,
                        onRecipientChange = { recipientInput = it },
                        amountInput = amountInput,
                        onAmountChange = { amountInput = it },
                        onSendClick = { showConfirmSendDialog = true }
                    )
                    2 -> ReceiveTabContent(
                        address = testnetAddress,
                        onOpenFaucet = { viewModel.openTestnetFaucet(context) }
                    )
                    3 -> ManageTabContent(
                        activeAccountHandle = activeAccount?.handle ?: "Native Testnet Account",
                        testnetAddress = testnetAddress,
                        allAccountsCount = allAccounts.size,
                        onViewSeed = { showSeedPhraseDialog = true },
                        onCreateNewWallet = { showCreateWalletDialog = true },
                        onImportWallet = { showImportWalletDialog = true },
                        onSignOut = { showSignOutConfirmDialog = true }
                    )
                }
            }
        }

        // Floating Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    // Confirmation Dialog for Sending
    if (showConfirmSendDialog) {
        val amountNum = amountInput.toDoubleOrNull() ?: 0.0
        AlertDialog(
            onDismissRequest = { showConfirmSendDialog = false },
            containerColor = SurfaceCard,
            title = {
                Text(
                    text = "Confirm Testnet 10 Send",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "You are sending to Testnet 10 BlockDAG:",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Amount: %.8f KAS".format(amountNum),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = KaspaTea
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Recipient: $recipientInput",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Estimated Mass: ~1,200 | Fee: ~0.0001 KAS",
                                fontSize = 11.sp,
                                color = AmberCentral
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmSendDialog = false
                        viewModel.sendKaspaTransaction(recipientInput, amountNum)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTea)
                ) {
                    Text("Confirm & Broadcast", color = SurfaceDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmSendDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Seed Phrase Backup Dialog
    if (showSeedPhraseDialog) {
        val seedPhrase = viewModel.getActiveSeedPhrase() ?: ""
        var isSeedRevealed by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showSeedPhraseDialog = false },
            containerColor = SurfaceCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = AmberCentral)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Wallet Recovery Phrase", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        text = "12-word recovery seed for this Kaspa Testnet 10 wallet. Keep it secret.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (isSeedRevealed) {
                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = seedPhrase,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = KaspaTea,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = { isSeedRevealed = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Visibility, contentDescription = null, tint = KaspaTea)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tap to Reveal Recovery Phrase", color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                if (isSeedRevealed) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Seed Phrase", seedPhrase))
                            scope.launch { snackbarHostState.showSnackbar("Seed phrase copied securely!") }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KaspaTea)
                    ) {
                        Text("Copy Phrase", color = SurfaceDark, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSeedPhraseDialog = false }) {
                    Text("Close", color = TextMuted)
                }
            }
        )
    }

    // Create New Testnet 10 Wallet Dialog
    if (showCreateWalletDialog) {
        var walletNameInput by remember { mutableStateOf("Kaspa TN10 Wallet") }
        var passphraseInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateWalletDialog = false },
            containerColor = SurfaceCard,
            title = { Text("Create New Testnet 10 Wallet", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Generate a new BIP-39 12-word wallet dedicated to Kaspa Testnet 10.", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = walletNameInput,
                        onValueChange = { walletNameInput = it },
                        label = { Text("Wallet Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KaspaTea,
                            unfocusedBorderColor = SurfaceCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passphraseInput,
                        onValueChange = { passphraseInput = it },
                        label = { Text("BIP-39 Passphrase (Optional)") },
                        placeholder = { Text("Leave blank if none") },
                        supportingText = { Text("Optional 13th word extension for additional security", fontSize = 11.sp, color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KaspaTea,
                            unfocusedBorderColor = SurfaceCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateWalletDialog = false
                        viewModel.createNewTestnetWallet(walletNameInput, passphraseInput)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTea)
                ) {
                    Text("Create & Scan", color = SurfaceDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateWalletDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Import Testnet 10 Wallet Dialog
    if (showImportWalletDialog) {
        var mnemonicInput by remember { mutableStateOf("") }
        var walletNameInput by remember { mutableStateOf("Imported TN10 Wallet") }
        var passphraseInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportWalletDialog = false },
            containerColor = SurfaceCard,
            title = { Text("Import Testnet 10 Wallet", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter your 12 or 24-word recovery phrase separated by spaces:", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = mnemonicInput,
                        onValueChange = { mnemonicInput = it },
                        label = { Text("12/24 Word Seed Phrase") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KaspaTea,
                            unfocusedBorderColor = SurfaceCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = walletNameInput,
                        onValueChange = { walletNameInput = it },
                        label = { Text("Wallet Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KaspaTea,
                            unfocusedBorderColor = SurfaceCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passphraseInput,
                        onValueChange = { passphraseInput = it },
                        label = { Text("BIP-39 Passphrase (Optional)") },
                        placeholder = { Text("Optional 13th/25th word extension") },
                        supportingText = { Text("Leave blank if this wallet has no passphrase", fontSize = 11.sp, color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KaspaTea,
                            unfocusedBorderColor = SurfaceCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImportWalletDialog = false
                        viewModel.importTestnetWallet(mnemonicInput, walletNameInput, passphraseInput)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                    enabled = mnemonicInput.isNotBlank()
                ) {
                    Text("Import & Scan", color = SurfaceDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportWalletDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Sign Out Confirmation Dialog
    if (showSignOutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmDialog = false },
            containerColor = SurfaceCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = RedTamper)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out of Wallet", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            text = {
                Text(
                    text = "Sign out of your active Kaspa Testnet 10 identity? Your encrypted recovery keys remain safely stored on this device. You will return to the browser.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirmDialog = false
                        viewModel.signOutActiveAccount()
                        viewModel.setTab(AppTab.BROWSER_GATEWAY)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedTamper)
                ) {
                    Text("Sign Out", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun HistoryTabContent(
    transactions: List<KaspaTransactionItem>,
    isLoading: Boolean,
    onOpenTx: (String) -> Unit,
    onGetFaucetCoins: () -> Unit
) {
    if (isLoading && transactions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = KaspaTea)
        }
        return
    }

    if (transactions.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = SurfaceCard,
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = KaspaTea,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "No Testnet 10 Transactions Yet",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Your Kaspa Testnet 10 wallet is ready. Request free test coins from the faucet to begin.",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onGetFaucetCoins,
                colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.WaterDrop, contentDescription = null, tint = SurfaceDark)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Get Free Testnet 10 KAS", color = SurfaceDark, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(transactions, key = { it.txId }) { tx ->
                TransactionCard(tx = tx, onClick = { onOpenTx(tx.txId) })
            }
        }
    }
}

@Composable
private fun TransactionCard(
    tx: KaspaTransactionItem,
    onClick: () -> Unit
) {
    val isReceive = tx.type == "RECEIVED"
    val dateStr = remember(tx.blockTime) {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        sdf.format(Date(if (tx.blockTime > 1000000000000L) tx.blockTime else tx.blockTime * 1000L))
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, SurfaceCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (isReceive) KaspaTea.copy(alpha = 0.15f) else ElectricCyan.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isReceive) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade,
                            contentDescription = tx.type,
                            tint = if (isReceive) KaspaTea else ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = if (isReceive) "Received KAS" else "Sent KAS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "$dateStr • Tx: ${tx.txId.take(8)}...",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isReceive) "+" else "-"}%.4f KAS".format(tx.amountKas),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isReceive) KaspaTea else TextPrimary
                )
                Text(
                    text = if (tx.isAccepted) "Accepted on DAG" else "Pending",
                    fontSize = 10.sp,
                    color = if (tx.isAccepted) KaspaTea else AmberCentral
                )
            }
        }
    }
}

@Composable
private fun SendTabContent(
    senderAddress: String,
    balanceKas: Double,
    isSending: Boolean,
    statusNotice: String?,
    recipientInput: String,
    onRecipientChange: (String) -> Unit,
    amountInput: String,
    onAmountChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    val amountNum = amountInput.toDoubleOrNull() ?: 0.0
    val isTestnetPrefixValid = recipientInput.isBlank() || recipientInput.startsWith("kaspatest:")
    val canSend = !isSending && recipientInput.isNotBlank() && recipientInput.startsWith("kaspatest:") && amountNum > 0.0 && amountNum <= balanceKas

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // Notice banner if set
            if (!statusNotice.isNullOrBlank()) {
                val isError = statusNotice.startsWith("Error", ignoreCase = true)
                Surface(
                    color = if (isError) RedTamper.copy(alpha = 0.15f) else KaspaTea.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (isError) RedTamper.copy(alpha = 0.5f) else KaspaTea.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusNotice,
                        fontSize = 12.sp,
                        color = if (isError) RedTamper else KaspaTea,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            // Recipient Address Input
            OutlinedTextField(
                value = recipientInput,
                onValueChange = onRecipientChange,
                label = { Text("Recipient Testnet Address") },
                placeholder = { Text("kaspatest:qq...") },
                supportingText = {
                    if (!isTestnetPrefixValid) {
                        Text("Address must start with 'kaspatest:'", color = RedTamper)
                    } else {
                        Text("Kaspa Testnet 10 CashAddr destination", color = TextMuted)
                    }
                },
                isError = !isTestnetPrefixValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("send_recipient_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KaspaTea,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                singleLine = true
            )
        }

        item {
            // Amount Input with Max button
            OutlinedTextField(
                value = amountInput,
                onValueChange = onAmountChange,
                label = { Text("Amount (KAS)") },
                placeholder = { Text("0.0") },
                trailingIcon = {
                    Surface(
                        onClick = {
                            val maxSendable = (balanceKas - 0.0002).coerceAtLeast(0.0)
                            onAmountChange("%.8f".format(maxSendable).trimEnd('0').trimEnd('.'))
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = SurfaceDark,
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "MAX",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = KaspaTea,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                },
                supportingText = {
                    Text("Available: %.8f KAS".format(balanceKas), color = TextMuted)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("send_amount_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KaspaTea,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                singleLine = true
            )
        }

        item {
            // Fee & Speed Breakdown
            Surface(
                color = SurfaceDark,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Confirmation Speed", fontSize = 12.sp, color = TextSecondary)
                        Text("Instant Confirmation", fontSize = 12.sp, color = KaspaTea, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Standard Network Fee", fontSize = 12.sp, color = TextSecondary)
                        Text("~0.0001 KAS (10,000 Sompi)", fontSize = 12.sp, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Consensus Engine", fontSize = 12.sp, color = TextSecondary)
                        Text("GHOSTDAG / Rusty-Kaspa", fontSize = 12.sp, color = TextMuted)
                    }
                }
            }
        }

        item {
            Button(
                onClick = onSendClick,
                enabled = canSend,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KaspaTea,
                    disabledContainerColor = SurfaceCardBorder
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("send_submit_button")
            ) {
                if (isSending) {
                    CircularProgressIndicator(color = SurfaceDark, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Signing & Broadcasting to TN10...", color = SurfaceDark, fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = SurfaceDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send KAS on Testnet 10", color = SurfaceDark, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReceiveTabContent(
    address: String,
    onOpenFaucet: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Your Testnet 10 Address",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Scan or copy this address to receive test coins.",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        item {
            // QR Code View
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                KaspaQrCodeView(
                    data = address,
                    size = 220.dp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        item {
            // Address Box
            Surface(
                color = SurfaceDark,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = address,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = KaspaTea,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Address", address))
                        isCopied = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = SurfaceDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isCopied) "Copied!" else "Copy Address", color = SurfaceDark, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, address)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Testnet 10 Address"))
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", color = TextPrimary)
                }
            }
        }

        item {
            // Faucet Call-To-Action
            Surface(
                color = SurfaceElevated,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, KaspaTea.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(imageVector = Icons.Default.WaterDrop, contentDescription = null, tint = KaspaTea)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Need Test Coins?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Open official Kaspa Testnet 10 faucet", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                    Button(
                        onClick = onOpenFaucet,
                        colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Open Faucet", color = SurfaceDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageTabContent(
    activeAccountHandle: String,
    testnetAddress: String,
    allAccountsCount: Int,
    onViewSeed: () -> Unit,
    onCreateNewWallet: () -> Unit,
    onImportWallet: () -> Unit,
    onSignOut: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Wallet Security & Keys", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        item {
            ManagementOptionCard(
                icon = Icons.Default.Key,
                iconColor = KaspaTea,
                title = "Backup Recovery Phrase",
                subtitle = "View your 12-word recovery mnemonic seed phrase",
                onClick = onViewSeed
            )
        }

        item {
            ManagementOptionCard(
                icon = Icons.Default.Add,
                iconColor = KaspaTea,
                title = "Create New Testnet 10 Wallet",
                subtitle = "Generate a fresh BIP-39 mnemonic wallet for Testnet 10",
                onClick = onCreateNewWallet
            )
        }

        item {
            ManagementOptionCard(
                icon = Icons.Default.Lock,
                iconColor = ElectricCyan,
                title = "Import Existing Wallet",
                subtitle = "Restore an existing 12 or 24-word seed phrase",
                onClick = onImportWallet
            )
        }

        item {
            ManagementOptionCard(
                icon = Icons.AutoMirrored.Filled.Logout,
                iconColor = RedTamper,
                title = "Sign Out / Disconnect Wallet",
                subtitle = "Sign out of active Testnet 10 identity and return to browser",
                onClick = onSignOut
            )
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            Text("Network Details", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        item {
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    DetailRow(label = "Network", value = "Kaspa Testnet 10 (TN10)")
                    DetailRow(label = "Prefix", value = "kaspatest:")
                    DetailRow(label = "RPC Endpoint", value = "https://api-tn10.kaspa.org")
                    DetailRow(label = "Finality", value = "Instant BlockDAG")
                    DetailRow(label = "Explorer", value = "https://explorer-tn10.kaspa.org")
                }
            }
        }
    }
}

@Composable
private fun ManagementOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, SurfaceCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.15f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(text = subtitle, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
        Text(text = value, fontSize = 12.sp, color = KaspaTea, fontFamily = FontFamily.Monospace)
    }
}

// ============================================================================
// Wallet Setup / Onboarding Flow (Before Wallet is Active)
// ============================================================================

enum class WalletSetupStep {
    WELCOME,
    CREATE_DETAILS,
    CREATE_BACKUP,
    IMPORT_PHRASE
}

@Composable
fun WalletSetupFlow(
    viewModel: DecentralViewModel,
    onBackToBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(WalletSetupStep.WELCOME) }
    var walletName by remember { mutableStateOf("Kaspa TN10 Wallet") }
    var walletPassword by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var generatedMnemonic by remember { mutableStateOf("") }
    var importMnemonicInput by remember { mutableStateOf("") }
    var hasConfirmedBackup by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler {
        if (step != WalletSetupStep.WELCOME) {
            step = WalletSetupStep.WELCOME
        } else {
            onBackToBrowser()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("wallet_setup_flow")
    ) {
        when (step) {
            WalletSetupStep.WELCOME -> {
                WelcomeSetupView(
                    onBack = onBackToBrowser,
                    onCreateClick = {
                        generatedMnemonic = CryptoUtils.generateMnemonic()
                        hasConfirmedBackup = false
                        step = WalletSetupStep.CREATE_DETAILS
                    },
                    onImportClick = {
                        step = WalletSetupStep.IMPORT_PHRASE
                    }
                )
            }
            WalletSetupStep.CREATE_DETAILS -> {
                CreateDetailsSetupView(
                    walletName = walletName,
                    onWalletNameChange = { walletName = it },
                    walletPassword = walletPassword,
                    onWalletPasswordChange = { walletPassword = it },
                    passphrase = passphrase,
                    onPassphraseChange = { passphrase = it },
                    onBack = { step = WalletSetupStep.WELCOME },
                    onContinue = { step = WalletSetupStep.CREATE_BACKUP }
                )
            }
            WalletSetupStep.CREATE_BACKUP -> {
                CreateBackupSetupView(
                    mnemonic = generatedMnemonic,
                    hasConfirmed = hasConfirmedBackup,
                    onConfirmedChange = { hasConfirmedBackup = it },
                    onBack = { step = WalletSetupStep.CREATE_DETAILS },
                    onComplete = {
                        if (walletPassword.length >= 8) {
                            viewModel.setWalletPassword(walletPassword)
                        }
                        viewModel.importTestnetWallet(generatedMnemonic, walletName, passphrase)
                    },
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Recovery Phrase", generatedMnemonic))
                        scope.launch { snackbarHostState.showSnackbar("Recovery phrase copied to clipboard!") }
                    }
                )
            }
            WalletSetupStep.IMPORT_PHRASE -> {
                ImportPhraseSetupView(
                    mnemonicInput = importMnemonicInput,
                    onMnemonicChange = { importMnemonicInput = it },
                    walletName = walletName,
                    onWalletNameChange = { walletName = it },
                    walletPassword = walletPassword,
                    onWalletPasswordChange = { walletPassword = it },
                    passphrase = passphrase,
                    onPassphraseChange = { passphrase = it },
                    onBack = { step = WalletSetupStep.WELCOME },
                    onImport = {
                        if (walletPassword.length >= 8) {
                            viewModel.setWalletPassword(walletPassword)
                        }
                        viewModel.importTestnetWallet(importMnemonicInput, walletName, passphrase)
                    }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun WelcomeSetupView(
    onBack: () -> Unit,
    onCreateClick: () -> Unit,
    onImportClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp).testTag("setup_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Browser",
                    tint = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = CircleShape,
            color = KaspaTea.copy(alpha = 0.12f),
            border = BorderStroke(1.5.dp, KaspaTea.copy(alpha = 0.4f)),
            modifier = Modifier.size(70.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = KaspaTea,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Kaspa Wallet",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Testnet 10 • Non-Custodial BlockDAG",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = KaspaTea,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Fast, secure, and decentralized. Set up your wallet to start transacting on the live Testnet 10 DAG.",
            fontSize = 12.5.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Feature Highlights
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetupFeatureItem(
                    icon = Icons.Default.Shield,
                    title = "Client-Side Cryptography",
                    subtitle = "BIP-39 & BIP-44 key derivation stored exclusively on this device."
                )
                SetupFeatureItem(
                    icon = Icons.Default.Key,
                    title = "BIP-39 Passphrase Support",
                    subtitle = "Optional 13th-word salt for layered recovery security."
                )
                SetupFeatureItem(
                    icon = Icons.Default.Science,
                    title = "Automatic On-Chain Scan",
                    subtitle = "Instant UTXO and history sync against Testnet 10 RPC nodes."
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onCreateClick,
                colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("setup_create_wallet_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = SurfaceDark)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create New Wallet", color = SurfaceDark, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            }

            OutlinedButton(
                onClick = onImportClick,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("setup_import_wallet_button")
            ) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = TextPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Existing Wallet", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
            }
        }
    }
}

@Composable
private fun CreateDetailsSetupView(
    walletName: String,
    onWalletNameChange: (String) -> Unit,
    walletPassword: String,
    onWalletPasswordChange: (String) -> Unit,
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    var isPasswordVisible by remember { mutableStateOf(false) }
    val isPasswordValid = walletPassword.length >= 8

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Configure Wallet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Step 1 of 2: Security & Passphrase", fontSize = 12.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = walletName,
                onValueChange = onWalletNameChange,
                label = { Text("Wallet Name") },
                modifier = Modifier.fillMaxWidth().testTag("setup_wallet_name_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KaspaTea,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = walletPassword,
                onValueChange = onWalletPasswordChange,
                label = { Text("Wallet Password (8 chars minimum)") },
                placeholder = { Text("Enter 8+ character password") },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                            tint = KaspaTea
                        )
                    }
                },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                supportingText = {
                    if (isPasswordValid) {
                        Text("✓ Password length valid (8+ characters)", color = KaspaTea, fontSize = 11.sp)
                    } else {
                        Text(
                            text = if (walletPassword.isEmpty()) "Mandatory: Minimum 8 characters required before activating wallet." else "Must be at least 8 characters (${walletPassword.length}/8)",
                            color = if (walletPassword.isEmpty()) TextMuted else MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("setup_wallet_password_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isPasswordValid) KaspaTea else SurfaceCardBorder,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = passphrase,
                onValueChange = onPassphraseChange,
                label = { Text("BIP-39 Passphrase (Optional)") },
                placeholder = { Text("Leave blank for standard wallet") },
                supportingText = {
                    Text("Optional 13th word. If set, this passphrase must be entered every time you restore the wallet.", color = TextMuted, fontSize = 11.sp)
                },
                modifier = Modifier.fillMaxWidth().testTag("setup_passphrase_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KaspaTea,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onContinue,
            enabled = walletName.isNotBlank() && isPasswordValid,
            colors = ButtonDefaults.buttonColors(containerColor = KaspaTea, disabledContainerColor = SurfaceCardBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("setup_continue_backup_button")
        ) {
            Text("Generate Recovery Phrase", color = if (walletName.isNotBlank() && isPasswordValid) SurfaceDark else TextMuted, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun CreateBackupSetupView(
    mnemonic: String,
    hasConfirmed: Boolean,
    onConfirmedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onCopy: () -> Unit
) {
    val words = remember(mnemonic) { mnemonic.trim().split("\\s+".toRegex()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Backup Recovery Phrase", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Step 2 of 2: Write down your 12 words", fontSize = 12.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = SurfaceDark,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Write down these 12 words in sequential order and store them securely. Anyone with these words can access your wallet.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 12-Word Grid
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, KaspaTea.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0 until 4) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (col in 0 until 3) {
                                val index = row * 3 + col
                                val word = words.getOrNull(index) ?: ""
                                Surface(
                                    color = SurfaceDark,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, SurfaceCardBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${index + 1}.",
                                            fontSize = 10.sp,
                                            color = TextMuted,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = word,
                                            fontSize = 12.sp,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onCopy,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = KaspaTea, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Words", color = KaspaTea, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = hasConfirmed,
                    onCheckedChange = onConfirmedChange,
                    colors = CheckboxDefaults.colors(checkedColor = KaspaTea, checkmarkColor = SurfaceDark)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "I have written down and securely backed up my 12-word phrase.",
                    fontSize = 12.sp,
                    color = TextPrimary
                )
            }
        }

        Button(
            onClick = onComplete,
            enabled = hasConfirmed,
            colors = ButtonDefaults.buttonColors(containerColor = KaspaTea, disabledContainerColor = SurfaceCardBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("setup_complete_button")
        ) {
            Text("Complete & Scan BlockDAG", color = if (hasConfirmed) SurfaceDark else TextMuted, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun ImportPhraseSetupView(
    mnemonicInput: String,
    onMnemonicChange: (String) -> Unit,
    walletName: String,
    onWalletNameChange: (String) -> Unit,
    walletPassword: String,
    onWalletPasswordChange: (String) -> Unit,
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    onBack: () -> Unit,
    onImport: () -> Unit
) {
    var isPasswordVisible by remember { mutableStateOf(false) }
    val wordCount = remember(mnemonicInput) {
        mnemonicInput.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size
    }
    val isValidCount = wordCount == 12 || wordCount == 24
    val isPasswordValid = walletPassword.length >= 8

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Import Existing Wallet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Enter 12 or 24-word recovery phrase", fontSize = 12.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = mnemonicInput,
                onValueChange = onMnemonicChange,
                label = { Text("12 or 24-Word Recovery Phrase") },
                placeholder = { Text("Enter words separated by spaces") },
                supportingText = {
                    Text("Words entered: $wordCount (expected 12 or 24)", color = if (isValidCount) KaspaTea else TextMuted, fontSize = 11.sp)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .testTag("setup_import_mnemonic_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KaspaTea,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = walletName,
                onValueChange = onWalletNameChange,
                label = { Text("Wallet Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KaspaTea,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = walletPassword,
                onValueChange = onWalletPasswordChange,
                label = { Text("Wallet Password (8 chars minimum)") },
                placeholder = { Text("Enter 8+ character password") },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                            tint = KaspaTea
                        )
                    }
                },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                supportingText = {
                    if (isPasswordValid) {
                        Text("✓ Password length valid (8+ characters)", color = KaspaTea, fontSize = 11.sp)
                    } else {
                        Text(
                            text = if (walletPassword.isEmpty()) "Mandatory: Minimum 8 characters required before activating wallet." else "Must be at least 8 characters (${walletPassword.length}/8)",
                            color = if (walletPassword.isEmpty()) TextMuted else MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("setup_import_password_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isPasswordValid) KaspaTea else SurfaceCardBorder,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = passphrase,
                onValueChange = onPassphraseChange,
                label = { Text("BIP-39 Passphrase (Optional)") },
                placeholder = { Text("Leave blank if none") },
                supportingText = {
                    Text("Leave blank unless this wallet was created with a passphrase.", color = TextMuted, fontSize = 11.sp)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KaspaTea,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onImport,
            enabled = isValidCount && isPasswordValid,
            colors = ButtonDefaults.buttonColors(containerColor = KaspaTea, disabledContainerColor = SurfaceCardBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("setup_import_submit_button")
        ) {
            Text("Import & Scan BlockDAG", color = if (isValidCount && isPasswordValid) SurfaceDark else TextMuted, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun SetupFeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = KaspaTea.copy(alpha = 0.12f),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = KaspaTea, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = subtitle, fontSize = 11.sp, color = TextSecondary, lineHeight = 14.sp)
        }
    }
}
