package com.example.ui

import com.example.data.AccountEntity
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults

import com.example.network.kaspa.KaspaTransactionEngine
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import com.example.ui.theme.PhotoThemeSelectorDialog
import com.example.utils.BiometricAuthHelper
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material.icons.filled.Public
import com.example.ui.theme.AmberCentral
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMesh
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val networkFeeCondition by viewModel.networkFeeCondition.collectAsState()
    val isFetchingFeeCondition by viewModel.isFetchingFeeCondition.collectAsState()
    val selectedExplorerUrl by viewModel.selectedExplorerUrl.collectAsState()
    val selectedNetwork by viewModel.selectedNetwork.collectAsState()
    val evmNetworkPrefs = remember {
        context.getSharedPreferences(com.example.dapp.EvmWalletEngine.WALLET_NETWORK_PREFS, Context.MODE_PRIVATE)
    }
    var selectedEvmChainId by remember {
        mutableStateOf(
            runCatching {
                com.example.dapp.EvmWalletEngine.network(evmNetworkPrefs.getLong(com.example.dapp.EvmWalletEngine.WALLET_NETWORK_KEY, 202555L)).chainId
            }.getOrDefault(202555L)
        )
    }
    DisposableEffect(evmNetworkPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == com.example.dapp.EvmWalletEngine.WALLET_NETWORK_KEY) {
                selectedEvmChainId = runCatching { com.example.dapp.EvmWalletEngine.network(prefs.getLong(com.example.dapp.EvmWalletEngine.WALLET_NETWORK_KEY, 202555L)).chainId }.getOrDefault(202555L)
            }
        }
        evmNetworkPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { evmNetworkPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun selectEvmNetwork(chainId: Long) {
        val target = runCatching { com.example.dapp.EvmWalletEngine.network(chainId) }.getOrNull() ?: return
        scope.launch {
            val verified = runCatching { withContext(Dispatchers.IO) { com.example.dapp.EvmWalletEngine.verifyNetwork(target) } }
            if (verified.isFailure) {
                snackbarHostState.showSnackbar("${target.name} RPC connection failed or returned the wrong chain ID. Network was not changed.")
                return@launch
            }
            selectedEvmChainId = chainId
            evmNetworkPrefs.edit().putLong(com.example.dapp.EvmWalletEngine.WALLET_NETWORK_KEY, chainId).apply()
            val dappChainPrefs = context.getSharedPreferences("evm_dapp_chains", Context.MODE_PRIVATE)
            val editor = dappChainPrefs.edit()
            dappChainPrefs.all.keys.forEach { origin -> editor.putLong(origin, chainId) }
            editor.apply()
            snackbarHostState.showSnackbar("${target.name} RPC connected and chain ID verified.")
        }
    }
    val isMainnet = selectedNetwork.contains("Mainnet", ignoreCase = true)

    val currentPhotoTheme by viewModel.currentPhotoTheme.collectAsState()
    val isAutoRotatePhotoTheme by viewModel.isAutoRotatePhotoTheme.collectAsState()
    var showThemeSelectorDialog by remember { mutableStateOf(false) }

    // Auto-lock wallet whenever leaving the wallet screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.lockWallet()
        }
    }

    val isLocked by viewModel.isWalletLocked.collectAsState()
    val biometricsEnabled by viewModel.biometricsEnabled.collectAsState()

    var showUnlockBeforeSeedSheet by remember { mutableStateOf(false) }
    var isSeedPhrasePreUnlocked by remember { mutableStateOf(false) }

    var showSeedPhraseDialog by remember { mutableStateOf(false) }
    var seedPhrasePasswordInput by remember { mutableStateOf("") }
    var isSeedPhraseRevealed by remember { mutableStateOf(false) }
    var seedPhraseError by remember { mutableStateOf<String?>(null) }

    var showAccountsDialog by remember { mutableStateOf(false) }
    var showRecoveryScreen by remember { mutableStateOf(false) }
    var showFullWalletSetup by remember { mutableStateOf(false) }
    var fullSetupInitialStep by remember { mutableStateOf(WalletSetupStep.CREATE_DETAILS) }
    var showCreateWalletDialog by remember { mutableStateOf(false) }
    var showImportWalletDialog by remember { mutableStateOf(false) }

    var showSignOutConfirmDialog by remember { mutableStateOf(false) }
    var signOutPasswordInput by remember { mutableStateOf("") }
    var signOutError by remember { mutableStateOf<String?>(null) }

    // Screen Overlays State (Full Page Button UIs)
    var showSendScreen by remember { mutableStateOf(false) }
    var showReceiveScreen by remember { mutableStateOf(false) }
    var showManageScreen by remember { mutableStateOf(false) }
    var showNetworkDetailsScreen by remember { mutableStateOf(false) }
    var showNetworkSwitchScreen by remember { mutableStateOf(false) }
    var showResultOverlay by remember { mutableStateOf(false) }
    var lastResultIsSuccess by remember { mutableStateOf(true) }
    var lastResultTxId by remember { mutableStateOf("") }
    var lastResultError by remember { mutableStateOf("") }
    var selectedTransactionForDetails by remember { mutableStateOf<KaspaTransactionItem?>(null) }

    // Send Form State
    var recipientInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var selectedSompiPerMass by remember { mutableStateOf<Long>(KaspaTransactionEngine.DEFAULT_SOMPI_PER_MASS) }
    var showConfirmSendDialog by remember { mutableStateOf(false) }
    var showCompoundDialog by remember { mutableStateOf(false) }

    // Observe transaction state to show result overlay
    LaunchedEffect(walletState.isSending, walletState.lastBroadcastTxId, walletState.statusNotice) {
        if (!walletState.isSending) {
            if (walletState.lastBroadcastTxId != null) {
                lastResultIsSuccess = true
                lastResultTxId = walletState.lastBroadcastTxId ?: ""
                showResultOverlay = true
                // Close send screen and reset inputs on successful broadcast
                showSendScreen = false
                recipientInput = ""
                amountInput = ""
                viewModel.refreshTestnetWallet()
            } else if (walletState.statusNotice?.startsWith("Error:") == true) {
                lastResultIsSuccess = false
                lastResultError = walletState.statusNotice?.removePrefix("Error:")?.trim() ?: "Unknown error"
                showResultOverlay = true
            }
        }
    }

    // Handle Hardware/System Back -> Close overlays or Return to Browser Gateway
    BackHandler(enabled = true) {
        if (showResultOverlay) {
            showResultOverlay = false
            viewModel.clearWalletStatusNotice()
        } else if (showUnlockBeforeSeedSheet) {
            showUnlockBeforeSeedSheet = false
        } else if (showCompoundDialog) {
            showCompoundDialog = false
        } else if (selectedTransactionForDetails != null) {
            selectedTransactionForDetails = null
        } else if (showConfirmSendDialog) {
            showConfirmSendDialog = false
        } else if (showRecoveryScreen) {
            showRecoveryScreen = false
            isSeedPhrasePreUnlocked = false
        } else if (showFullWalletSetup) {
            showFullWalletSetup = false
        } else if (showSendScreen) {
            showSendScreen = false
        } else if (showReceiveScreen) {
            showReceiveScreen = false
        } else if (showNetworkSwitchScreen) {
            showNetworkSwitchScreen = false
        } else if (showNetworkDetailsScreen) {
            showNetworkDetailsScreen = false
        } else if (showManageScreen) {
            showManageScreen = false
        } else {
            viewModel.lockWallet()
            viewModel.setTab(AppTab.BROWSER_GATEWAY)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshTestnetWallet()
        viewModel.refreshNetworkFeeCondition()
    }

    LaunchedEffect(testnetAddress) {
        if (testnetAddress.isNotBlank()) {
            viewModel.refreshTestnetWallet()
            viewModel.refreshNetworkFeeCondition()
        }
    }

    LaunchedEffect(showSendScreen) {
        if (showSendScreen) {
            viewModel.refreshNetworkFeeCondition()
            // Dynamically sync fee rate with live RPC network conditions
            selectedSompiPerMass = maxOf(KaspaTransactionEngine.TOCCATA_MINIMUM_STANDARD_FEE_RATE_SOMPI_PER_GRAM, networkFeeCondition.normalFeerate)
        }
    }

    if (activeAccount == null) {
        WalletSetupFlow(
            viewModel = viewModel,
            onBackToBrowser = { viewModel.setTab(AppTab.BROWSER_GATEWAY) },
            onWalletReady = {
                viewModel.refreshTestnetWallet()
            },
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
            .background(ObsidianBg.copy(alpha = 0.35f))
            .testTag("kaspa_testnet_wallet_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
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

                    Spacer(modifier = Modifier.width(4.dp))

                    // Single Top-Bar Sub-Wallet Button with Wallet Icon
                    Surface(
                        onClick = { showAccountsDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        color = SurfaceCard,
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.testTag("top_account_pill")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = "Sub-wallet",
                                tint = KaspaTea,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeAccount?.handle?.ifBlank { "Wallet" } ?: "Wallet",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Switch Sub-Wallet",
                                tint = KaspaTea,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { showManageScreen = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("manage_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Manage",
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
                }
            }

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
                    val currencySymbol = when {
                        selectedNetwork.contains("Igra", ignoreCase = true) -> "iKAS"
                        selectedNetwork.contains("Testnet", ignoreCase = true) -> "tKAS"
                        else -> "KAS"
                    }
                    val heroDerivationPath = if (viewModel.isEvmNetwork(selectedNetwork)) "m/44'/60'/0'/0/0" else "m/44'/111111'/0'/0/0"

                    Text(
                        text = "%.4f %s".format(walletState.balanceKas, currencySymbol),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1
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
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Wallet Address", testnetAddress))
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

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Derivation: $heroDerivationPath",
                        fontSize = 10.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Standalone Action Button Bar (No clunky bottom tabs, action buttons open their own pages!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickActionButton(
                    icon = Icons.Default.Send,
                    label = "Send",
                    color = RedTamper.copy(alpha = 0.8f),
                    onClick = { showSendScreen = true }
                )
                QuickActionButton(
                    icon = Icons.AutoMirrored.Filled.CallReceived,
                    label = "Receive",
                    color = ElectricCyan,
                    onClick = { showReceiveScreen = true }
                )
                QuickActionButton(
                    iconRes = R.drawable.ic_compound_utxo,
                    label = "Compound",
                    color = Color(0xFFF2F2F2),
                    onClick = {
                        if (walletState.utxosCount <= 1) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (walletState.utxosCount == 1) "Wallet already optimal (1 UTXO unspent)"
                                    else "No unspent UTXOs to compound"
                                )
                            }
                        } else {
                            showCompoundDialog = true
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section Header: Transaction History
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Transaction History",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                if (walletState.recentTransactions.isNotEmpty()) {
                    Text(
                        text = "View Explorer",
                        fontSize = 11.sp,
                        color = KaspaTea,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { viewModel.openTestnetExplorer() }
                    )
                }
            }

            // Transaction History List directly on Main Screen (pins dashboard up, no scroll cutoffs!)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                HistoryTabContent(
                    transactions = walletState.recentTransactions,
                    isLoading = walletState.isLoading,
                    onOpenTx = { tx -> selectedTransactionForDetails = tx },
                    onGetFaucetCoins = { viewModel.openTestnetFaucet(context) },
                    onSendKaspa = { showSendScreen = true },
                    onReceiveKaspa = { showReceiveScreen = true },
                    isMainnet = isMainnet,
                    selectedNetwork = selectedNetwork
                )
            }
        }

        // --- NATIVE MOBILE APP FULL-PAGE OVERLAYS ---

        // Send Screen Overlay
        if (showSendScreen) {
            SendScreenOverlay(
                senderAddress = testnetAddress,
                balanceKas = walletState.balanceKas,
                estimatedInputCount = maxOf(1, walletState.utxosCount),
                isSending = walletState.isSending,
                recipientInput = recipientInput,
                onRecipientChange = { recipientInput = it },
                amountInput = amountInput,
                onAmountChange = { amountInput = it },
                selectedSompiPerMass = selectedSompiPerMass,
                onFeerateChange = { selectedSompiPerMass = it },
                networkFeeCondition = networkFeeCondition,
                isFetchingFeeCondition = isFetchingFeeCondition,
                onRefreshFeeCondition = { viewModel.refreshNetworkFeeCondition() },
                selectedNetwork = selectedNetwork,
                onSendClick = { showConfirmSendDialog = true },
                onClose = { showSendScreen = false }
            )
        }

        // Receive Screen Overlay
        if (showReceiveScreen) {
            ReceiveScreenOverlay(
                address = testnetAddress,
                isMainnet = isMainnet,
                selectedNetwork = selectedNetwork,
                onOpenFaucet = { viewModel.openTestnetFaucet(context) },
                onClose = { showReceiveScreen = false }
            )
        }

        // Manage Settings Screen Overlay
        if (showManageScreen) {
            ManageScreenOverlay(
                viewModel = viewModel,
                activeAccountHandle = activeAccount?.handle ?: "Native Testnet Account",
                activeAccountSeedPhrase = activeAccount?.seedPhrase.orEmpty(),
                activeAccountId = activeAccount?.did?.takeIf { it.isNotBlank() } ?: activeAccount?.handle ?: "active-wallet",
                selectedEvmChainId = selectedEvmChainId,
                onSelectEvmNetwork = ::selectEvmNetwork,
                testnetAddress = testnetAddress,
                allAccountsCount = allAccounts.size,
                selectedNetwork = selectedNetwork,
                onViewSeed = { showUnlockBeforeSeedSheet = true },
                onSignOut = { showSignOutConfirmDialog = true },
                onOpenNetworkSwitch = {
                    showManageScreen = false
                    showNetworkSwitchScreen = true
                },
                onOpenNetworkDetails = {
                    showNetworkDetailsScreen = true
                },
                onClose = { showManageScreen = false }
            )
        }

        // Security Verification BottomSheet Before Viewing Recovery Seed Phrase (Pops up from below)
        if (showUnlockBeforeSeedSheet) {
            UnlockRecoveryBottomSheet(
                viewModel = viewModel,
                onSuccess = {
                    showUnlockBeforeSeedSheet = false
                    isSeedPhrasePreUnlocked = true
                    showRecoveryScreen = true
                },
                onDismiss = {
                    showUnlockBeforeSeedSheet = false
                }
            )
        }

        if (showNetworkDetailsScreen) {
            NetworkDetailsScreenOverlay(
                selectedNetwork = selectedNetwork,
                testnetAddress = testnetAddress,
                onOpenNetworkSwitch = {
                    showNetworkDetailsScreen = false
                    showNetworkSwitchScreen = true
                },
                onClose = { showNetworkDetailsScreen = false }
            )
        }

        if (showNetworkSwitchScreen) {
            NetworkSwitchOverlay(
                selectedNetwork = selectedNetwork,
                onSelectNetwork = { network ->
                    viewModel.selectNetwork(network)
                    showNetworkSwitchScreen = false
                },
                onClose = { showNetworkSwitchScreen = false }
            )
        }

        // Full-Page Recovery Screen Overlay
        if (showRecoveryScreen) {
            RecoveryScreenOverlay(
                seedPhrase = viewModel.getActiveSeedPhrase() ?: "",
                viewModel = viewModel,
                isPreUnlocked = isSeedPhrasePreUnlocked,
                onClose = {
                    showRecoveryScreen = false
                    isSeedPhrasePreUnlocked = false
                }
            )
        }

        // Full-Page Setup Flow (Create / Import Wallet)
        if (showFullWalletSetup) {
            WalletSetupFlow(
                viewModel = viewModel,
                onBackToBrowser = { showFullWalletSetup = false },
                onWalletReady = {
                    showFullWalletSetup = false
                    viewModel.refreshTestnetWallet()
                },
                initialStep = fullSetupInitialStep,
                modifier = modifier
            )
        }

        // Accounts Dialog
        if (showAccountsDialog) {
            WalletAccountsDialog(
                allAccounts = allAccounts,
                activeAccount = activeAccount,
                selectedNetwork = selectedNetwork,
                onOpenNetworkSwitch = {
                    showAccountsDialog = false
                    showNetworkSwitchScreen = true
                },
                onSelectAccount = { account ->
                    viewModel.switchAccount(account.did)
                    viewModel.refreshTestnetWallet()
                },
                onCreateNewWallet = {
                    fullSetupInitialStep = WalletSetupStep.CREATE_DETAILS
                    showFullWalletSetup = true
                },
                onImportExistingWallet = {
                    fullSetupInitialStep = WalletSetupStep.IMPORT_PHRASE
                    showFullWalletSetup = true
                },
                onDismiss = { showAccountsDialog = false }
            )
        }

        // Transaction Result Overlay
        if (showResultOverlay) {
            TransactionResultOverlay(
                isSuccess = lastResultIsSuccess,
                txId = lastResultTxId,
                errorMessage = lastResultError,
                transaction = walletState.recentTransactions.firstOrNull { it.txId == lastResultTxId },
                selectedNetwork = selectedNetwork,
                onViewOnExplorer = {
                    showResultOverlay = false
                    viewModel.openTestnetTxExplorer(lastResultTxId)
                    viewModel.clearWalletStatusNotice()
                },
                onClose = {
                    showResultOverlay = false
                    viewModel.clearWalletStatusNotice()
                }
            )
        }

        // Transaction Details Popup Dialog
        if (selectedTransactionForDetails != null) {
            TransactionDetailDialog(
                tx = selectedTransactionForDetails!!,
                selectedNetwork = selectedNetwork,
                onCopyTxId = { txId ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Transaction ID", txId))
                    scope.launch {
                        snackbarHostState.showSnackbar("Transaction ID copied to clipboard!")
                    }
                },
                onViewOnExplorer = { txId ->
                    selectedTransactionForDetails = null
                    viewModel.openTestnetTxExplorer(txId)
                },
                onDismiss = { selectedTransactionForDetails = null }
            )
        }

        // Compound UTXO Confirmation Dialog
        if (showCompoundDialog) {
            CompoundUtxoDialog(
                utxoCount = walletState.utxosCount,
                balanceKas = walletState.balanceKas,
                isCompounding = walletState.isSending,
                onConfirm = {
                    showCompoundDialog = false
                    viewModel.compoundUtxos()
                },
                onDismiss = { showCompoundDialog = false }
            )
        }

        // Floating Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )

        // --- AUTHENTIC LOCK SCREEN OVERLAY ---
        if (isLocked) {
            WalletLockOverlay(
                viewModel = viewModel,
                onUnlock = { 
                    // Clear any local lock screen state if needed
                }
            )
        }
    }

    // Full-Page Confirmation Overlay for Sending
    if (showConfirmSendDialog) {
        val amountNum = amountInput.toDoubleOrNull() ?: 0.0
        val breakdown = viewModel.calculateFeeBreakdown(amountNum, selectedSompiPerMass)
        ConfirmSendScreenOverlay(
            recipientAddress = recipientInput,
            amountKas = amountNum,
            selectedSompiPerMass = selectedSompiPerMass,
            breakdown = breakdown,
            priceUsd = walletState.priceUsd,
            isSending = walletState.isSending,
            selectedNetwork = selectedNetwork,
            viewModel = viewModel,
            onBack = { showConfirmSendDialog = false },
            onBroadcastConfirmed = {
                showConfirmSendDialog = false
                viewModel.sendKaspaTransaction(recipientInput, amountNum, selectedSompiPerMass)
            }
        )
    }

    // Seed Phrase Backup Dialog (Secure Click-to-Reveal)
    if (showSeedPhraseDialog) {
        val seedPhrase = viewModel.getActiveSeedPhrase() ?: ""
        var isSeedRevealed by remember { mutableStateOf(false) }
        var passwordInput by remember { mutableStateOf("") }
        var errorText by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { 
                showSeedPhraseDialog = false 
                isSeedRevealed = false
                passwordInput = ""
                errorText = null
            },
            containerColor = SurfaceCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = AmberCentral)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Secure Recovery Phrase", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Your 12-word recovery seed is the master key to your Kaspa TN10 funds. Never share it with anyone.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (isSeedRevealed) {
                        val words = remember(seedPhrase) {
                            if (seedPhrase.isNotBlank()) seedPhrase.trim().split(Regex("\\s+")) else emptyList()
                        }
                        var isMasked by remember { mutableStateOf(false) }
                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Recovery Phrase Words",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextMuted
                                    )
                                    IconButton(
                                        onClick = { isMasked = !isMasked },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (!isMasked) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (!isMasked) "Mask Phrase" else "Click to Reveal Phrase",
                                            tint = if (!isMasked) KaspaTea else TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                for (rowIdx in 0 until (words.size + 1) / 2) {
                                    val leftIdx = rowIdx * 2
                                    val rightIdx = leftIdx + 1
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (leftIdx < words.size) {
                                            Surface(
                                                color = SurfaceCard,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, SurfaceCardBorder.copy(alpha = 0.6f)),
                                                modifier = Modifier.weight(1f).clickable { if (isMasked) isMasked = false }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("${leftIdx + 1}.", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                                                    Text(if (!isMasked) words[leftIdx] else "••••••", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = if (!isMasked) KaspaTea else TextMuted)
                                                }
                                            }
                                        }
                                        if (rightIdx < words.size) {
                                            Surface(
                                                color = SurfaceCard,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, SurfaceCardBorder.copy(alpha = 0.6f)),
                                                modifier = Modifier.weight(1f).clickable { if (isMasked) isMasked = false }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("${rightIdx + 1}.", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                                                    Text(if (!isMasked) words[rightIdx] else "••••••", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = if (!isMasked) KaspaTea else TextMuted)
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }
                    } else {
                        Column {
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { 
                                    passwordInput = it
                                    errorText = null 
                                },
                                label = { Text("Enter Wallet Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                isError = errorText != null,
                                supportingText = {
                                    if (errorText != null) Text(errorText!!, color = RedTamper)
                                    else Text("Verify password to reveal your phrase", color = TextMuted)
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = KaspaTea,
                                    unfocusedBorderColor = SurfaceCardBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { 
                                    if (viewModel.unlockWalletWithPassword(passwordInput)) {
                                        isSeedRevealed = true
                                        errorText = null
                                    } else {
                                        errorText = "Incorrect password"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Visibility, contentDescription = null, tint = KaspaTea)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reveal Recovery Phrase", color = TextPrimary)
                            }
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
                TextButton(onClick = { 
                    showSeedPhraseDialog = false 
                    isSeedRevealed = false
                    passwordInput = ""
                    errorText = null
                }) {
                    Text("Close", color = TextMuted)
                }
            }
        )
    }

    if (showThemeSelectorDialog) {
        PhotoThemeSelectorDialog(
            currentTheme = currentPhotoTheme,
            isAutoRotate = isAutoRotatePhotoTheme,
            onSelectTheme = { viewModel.selectPhotoTheme(it) },
            onToggleAutoRotate = { viewModel.toggleAutoRotatePhotoTheme(it) },
            onDismissRequest = { showThemeSelectorDialog = false }
        )
    }

    // Create New Wallet Dialog
    if (showCreateWalletDialog) {
        val activeNet by viewModel.selectedNetwork.collectAsState()
        var passphraseInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateWalletDialog = false },
            containerColor = SurfaceCard,
            title = { Text("Create $activeNet Wallet", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Generate a new BIP-39 12-word wallet for $activeNet.", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
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
                        viewModel.createNewTestnetWallet(null, passphraseInput)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTea)
                ) {
                    Text("Create Wallet", color = SurfaceDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateWalletDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Import Wallet Dialog
    if (showImportWalletDialog) {
        val activeNet by viewModel.selectedNetwork.collectAsState()
        var mnemonicInput by remember { mutableStateOf("") }
        var passphraseInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportWalletDialog = false },
            containerColor = SurfaceCard,
            title = { Text("Import $activeNet Wallet", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter your 12 or 24-word recovery phrase separated by spaces for $activeNet:", fontSize = 12.sp, color = TextSecondary)
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
                        viewModel.importTestnetWallet(mnemonicInput, null, passphraseInput)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                    enabled = mnemonicInput.isNotBlank()
                ) {
                    Text("Import Wallet", color = SurfaceDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportWalletDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Sign Out Confirmation BottomSheet (Pops from below the phone edge)
    if (showSignOutConfirmDialog) {
        SignOutBottomSheet(
            viewModel = viewModel,
            onDismiss = { showSignOutConfirmDialog = false }
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconRes: Int? = null,
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
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                } else if (iconRes != null) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = label,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
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
    onOpenTx: (KaspaTransactionItem) -> Unit,
    onGetFaucetCoins: () -> Unit,
    onSendKaspa: (() -> Unit)? = null,
    onReceiveKaspa: (() -> Unit)? = null,
    isMainnet: Boolean,
    selectedNetwork: String = ""
) {
    val isIgra = selectedNetwork.contains("Igra", ignoreCase = true)
    val isKasplex = selectedNetwork.contains("Kasplex", ignoreCase = true)
    val isEvm = isIgra || isKasplex
    val currencySymbol = when {
        isIgra -> "iKAS"
        selectedNetwork.contains("Testnet", ignoreCase = true) && !isKasplex -> "tKAS"
        else -> "KAS"
    }
    var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0: All, 1: Sent, 2: Received
    val filterTabs = listOf("All", "Sent", "Received")

    val filteredTransactions = remember(transactions, selectedFilterIndex) {
        when (selectedFilterIndex) {
            1 -> transactions.filter { it.type == "SENT" }
            2 -> transactions.filter { it.type == "RECEIVED" }
            else -> transactions
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Transaction History Filter Tabs: All, Sent, Received
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterTabs.forEachIndexed { index, title ->
                val isSelected = selectedFilterIndex == index
                val count = when (index) {
                    1 -> transactions.count { it.type == "SENT" }
                    2 -> transactions.count { it.type == "RECEIVED" }
                    else -> transactions.size
                }
                val tabBorderColor = when {
                    isSelected && index == 1 -> RedTamper
                    isSelected && index == 2 -> KaspaTea
                    isSelected -> KaspaTea
                    else -> SurfaceCardBorder
                }
                val tabTextColor = when {
                    isSelected && index == 1 -> RedTamper
                    isSelected && index == 2 -> KaspaTea
                    isSelected -> KaspaTea
                    else -> TextSecondary
                }
                val badgeColor = when (index) {
                    1 -> RedTamper.copy(alpha = if (isSelected) 0.95f else 0.25f)
                    2 -> KaspaTea.copy(alpha = if (isSelected) 0.95f else 0.25f)
                    else -> if (isSelected) KaspaTea else SurfaceCardBorder.copy(alpha = 0.7f)
                }
                val badgeTextColor = when {
                    isSelected -> SurfaceDark
                    index == 1 -> RedTamper
                    index == 2 -> KaspaTea
                    else -> TextMuted
                }

                Surface(
                    onClick = { selectedFilterIndex = index },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) tabBorderColor.copy(alpha = 0.15f) else SurfaceCard,
                    border = BorderStroke(1.dp, tabBorderColor),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = tabTextColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = badgeColor
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "$count",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = badgeTextColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isLoading && transactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = KaspaTea)
            }
            return
        }

        if (filteredTransactions.isEmpty()) {
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
                            imageVector = when (selectedFilterIndex) {
                                1 -> Icons.AutoMirrored.Filled.CallMade
                                2 -> Icons.AutoMirrored.Filled.CallReceived
                                else -> Icons.Default.WaterDrop
                            },
                            contentDescription = null,
                            tint = if (selectedFilterIndex == 1) RedTamper else KaspaTea,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = when (selectedFilterIndex) {
                        1 -> "No Sent Transactions"
                        2 -> "No Received Transactions"
                        else -> "No Transactions Yet"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (selectedFilterIndex) {
                        1 -> when {
                            isIgra -> "You have not broadcasted any sent transactions on Igra L2 yet."
                            isKasplex -> "You have not broadcasted any sent transactions on Kasplex EVM yet."
                            else -> "You have not broadcasted any sent transactions from this wallet yet."
                        }
                        2 -> when {
                            isIgra -> "No incoming iKAS transactions recorded yet on Igra L2."
                            isKasplex -> "No incoming KAS transactions recorded yet on Kasplex EVM."
                            else -> "No incoming transactions recorded yet for this wallet address."
                        }
                        else -> when {
                            isIgra -> "Your Igra L2 wallet is ready for execution."
                            isKasplex -> "Your Kasplex EVM wallet is ready for transactions."
                            isMainnet -> "Your Kaspa mainnet wallet is ready for transactions."
                            else -> "Your Kaspa wallet is ready. Request test coins from the faucet to begin."
                        }
                    },
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                when (selectedFilterIndex) {
                    1 -> {
                        if (onSendKaspa != null) {
                            Button(
                                onClick = onSendKaspa,
                                colors = ButtonDefaults.buttonColors(containerColor = RedTamper.copy(alpha = 0.85f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Send $currencySymbol", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    2 -> {
                        if (onReceiveKaspa != null) {
                            Button(
                                onClick = onReceiveKaspa,
                                colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.CallReceived, contentDescription = null, tint = SurfaceDark, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Receive $currencySymbol", color = SurfaceDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> {
                        if (!isMainnet && !isEvm) {
                            Button(
                                onClick = onGetFaucetCoins,
                                colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.WaterDrop, contentDescription = null, tint = SurfaceDark, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Get Free Testnet KAS", color = SurfaceDark, fontWeight = FontWeight.Bold)
                            }
                        } else if (onReceiveKaspa != null) {
                            Button(
                                onClick = onReceiveKaspa,
                                colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.CallReceived, contentDescription = null, tint = SurfaceDark, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Receive $currencySymbol", color = SurfaceDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            val groupedTransactions = remember(filteredTransactions) {
                filteredTransactions.groupBy { tx ->
                    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    sdf.format(Date(if (tx.blockTime > 1000000000000L) tx.blockTime else tx.blockTime * 1000L))
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                groupedTransactions.forEach { (date, txs) ->
                    item {
                        Text(
                            text = date,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                    items(txs, key = { it.txId }) { tx ->
                        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp)) {
                            TransactionCard(tx = tx, selectedNetwork = selectedNetwork, onClick = { onOpenTx(tx) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionCard(
    tx: KaspaTransactionItem,
    selectedNetwork: String = "",
    onClick: () -> Unit
) {
    val isReceive = tx.type == "RECEIVED"
    val isIgra = selectedNetwork.contains("Igra", ignoreCase = true)
    val isKasplex = selectedNetwork.contains("Kasplex", ignoreCase = true)
    val currencySymbol = when {
        isIgra -> "iKAS"
        selectedNetwork.contains("Testnet", ignoreCase = true) && !isKasplex -> "tKAS"
        else -> "KAS"
    }
    val dateStr = remember(tx.blockTime) {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        sdf.format(Date(if (tx.blockTime > 1000000000000L) tx.blockTime else tx.blockTime * 1000L))
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, SurfaceCardBorder.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isReceive) KaspaTea.copy(alpha = 0.12f) else RedTamper.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isReceive) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade,
                            contentDescription = tx.type,
                            tint = if (isReceive) KaspaTea else RedTamper,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = if (isReceive) "Received $currencySymbol" else "Sent $currencySymbol",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isReceive) KaspaTea else RedTamper,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dateStr,
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (isReceive) "+" else "-"}%.4f $currencySymbol".format(tx.amountKas),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isReceive) KaspaTea else RedTamper,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        color = if (tx.isAccepted) KaspaTea.copy(alpha = 0.12f) else AmberCentral.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (tx.isAccepted) "Accepted" else "Pending",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (tx.isAccepted) KaspaTea else AmberCentral,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Extended Details: TxID & Fee / Counterparty
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = SurfaceCardBorder.copy(alpha = 0.25f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(6.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tx: ${tx.txId.take(10)}...${tx.txId.takeLast(6)}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "Fee: ${KaspaTransactionEngine.formatKas(tx.feeKas)} KAS",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Default,
                    color = if (isReceive) KaspaTea else AmberCentral
                )
            }
        }
    }
}

@Composable
private fun KaspaFeeCalculatorSection(
    amountKas: Double,
    balanceKas: Double,
    selectedSompiPerMass: Long,
    onFeerateChange: (Long) -> Unit,
    breakdown: KaspaTransactionEngine.FeeCalculationBreakdown,
    networkCondition: KaspaTransactionEngine.KaspaNetworkFeeCondition,
    isFetchingFeeCondition: Boolean,
    onRefreshFeeCondition: () -> Unit,
    selectedNetwork: String = "",
    currencySymbol: String = "KAS"
) {
    val isEvm = selectedNetwork.contains("Kasplex", ignoreCase = true) || selectedNetwork.contains("Igra", ignoreCase = true)
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, SurfaceCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("kaspa_fee_calculator")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Fee Calculator",
                        tint = KaspaTea,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEvm) "EVM Gas & Execution Parameters" else "Fee & Mass Calculator",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isEvm) {
                val isIgra = selectedNetwork.contains("Igra", ignoreCase = true)
                val chainId = if (isIgra) (if (selectedNetwork.contains("Galleon", true)) 38836L else 38833L) else (if (selectedNetwork.contains("Testnet", true)) 167012L else 202555L)
                val rpcEndpoint = when {
                    selectedNetwork.contains("Igra Mainnet", true) -> "https://rpc.igralabs.com:8545"
                    selectedNetwork.contains("Galleon", true) -> "https://galleon-testnet.igralabs.com:8545"
                    selectedNetwork.contains("Kasplex Testnet", true) -> "https://rpc.kasplextest.xyz"
                    else -> "https://evmrpc.kasplex.org"
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Active Protocol:", fontSize = 11.sp, color = TextSecondary)
                        Text(if (isIgra) "Igra L2 EVM Execution" else "Kasplex EVM Layer 2", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sign Derivation Path:", fontSize = 11.sp, color = TextSecondary)
                        Text("m/44'/60'/0'/0/0 (secp256k1)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = KaspaTea)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Chain ID / Standard Gas:", fontSize = 11.sp, color = TextSecondary)
                        Text("$chainId • 21,000 gas units", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Broadcast Endpoint:", fontSize = 11.sp, color = TextSecondary)
                        Text(rpcEndpoint.substringAfter("://"), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = ElectricCyan)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = SurfaceCardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isIgra) {
                        "Estimate only. Transactions on Igra L2 use EVM sign derivation path m/44'/60'/0'/0/0 (EIP-155) and broadcast directly to Igra Execution Layer RPC. Gas execution fee is settled in iKAS."
                    } else {
                        "Estimate only. Transactions on Kasplex use EVM sign derivation path m/44'/60'/0'/0/0 (EIP-155) and broadcast directly to Kasplex Layer 2 RPC. Gas execution fee is settled in KAS."
                    },
                    fontSize = 10.sp,
                    color = TextSecondary,
                    lineHeight = 14.sp
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Fee Options", fontSize = 11.sp, color = TextSecondary)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onRefreshFeeCondition() }
                    ) {
                        if (isFetchingFeeCondition) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                color = KaspaTea,
                                strokeWidth = 1.5.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = "Live RPC Sync",
                            fontSize = 9.sp,
                            color = KaspaTea
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = SurfaceCardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sign Derivation Path:", fontSize = 11.sp, color = TextSecondary)
                    Text("m/44'/111111'/0'/0/0 (Schnorr)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = KaspaTea)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Estimate only. The final fee depends on the actual inputs, outputs, and signed transaction mass. Toccata minimum-standard relay policy is applied; the node's acceptance policy can still vary by network and version.",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    lineHeight = 14.sp
                )

                // Dust warning if output is < 0.02 KAS
                if (breakdown.isDustWarning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = AmberCentral.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AmberCentral.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = AmberCentral,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Notice: Outputs below 0.02 KAS incur higher storage mass to maintain network performance.",
                                fontSize = 10.sp,
                                color = AmberCentral,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SendTabContent(
    senderAddress: String,
    balanceKas: Double,
    estimatedInputCount: Int,
    isSending: Boolean,
    recipientInput: String,
    onRecipientChange: (String) -> Unit,
    amountInput: String,
    onAmountChange: (String) -> Unit,
    selectedSompiPerMass: Long,
    onFeerateChange: (Long) -> Unit,
    networkFeeCondition: KaspaTransactionEngine.KaspaNetworkFeeCondition,
    isFetchingFeeCondition: Boolean,
    onRefreshFeeCondition: () -> Unit,
    selectedNetwork: String = "",
    onSendClick: () -> Unit
) {
    val isIgra = selectedNetwork.contains("Igra", ignoreCase = true)
    val isKasplex = selectedNetwork.contains("Kasplex", ignoreCase = true)
    val isEvm = isIgra || isKasplex || senderAddress.startsWith("0x")
    val currencySymbol = when {
        isIgra -> "iKAS"
        selectedNetwork.contains("Testnet", ignoreCase = true) && !isKasplex -> "tKAS"
        else -> "KAS"
    }

    val amountNum = amountInput.toDoubleOrNull() ?: 0.0
    val isRecipientEntered = recipientInput.trim().isNotBlank()
    val cleanRecipient = recipientInput.trim().lowercase()
    val isTestnetWallet = senderAddress.startsWith("kaspatest:")
    val expectedPrefix = if (isTestnetWallet) "kaspatest" else "kaspa"
    val isPrefixMismatch = if (!isEvm) (if (isTestnetWallet) cleanRecipient.startsWith("kaspa:") else cleanRecipient.startsWith("kaspatest:")) else false

    val isRealAddressValid = if (isEvm) {
        cleanRecipient.startsWith("0x") && cleanRecipient.length == 42 && cleanRecipient.drop(2).all { it in "0123456789abcdef" }
    } else {
        com.example.viewmodel.DecentralViewModel.isValidKaspaAddress(cleanRecipient) ||
            com.example.network.CryptoUtils.isValidKaspaAddress(cleanRecipient, expectedPrefix)
    }

    val isAddressValidOnNetwork = isRealAddressValid && !isPrefixMismatch
    val isAddressError = isRecipientEntered && (!isRealAddressValid || isPrefixMismatch)
    val isAddressSuccess = isRecipientEntered && isAddressValidOnNetwork

    val safeEstimatedInputCount = estimatedInputCount.coerceAtLeast(1)
    val breakdown = remember(amountNum, safeEstimatedInputCount, selectedSompiPerMass) {
        KaspaTransactionEngine.calculateFeeBreakdown(
            amountKas = amountNum,
            inputsCount = safeEstimatedInputCount,
            outputsCount = 2,
            sompiPerMass = selectedSompiPerMass
        )
    }
    val isInsufficientBalance = amountNum > 0.0 && (amountNum + breakdown.feeKas) > balanceKas
    val isAmountError = (amountInput.isNotBlank() && amountNum <= 0.0) || isInsufficientBalance
    val canSend = !isSending && isAddressSuccess && amountNum > 0.0 && !isInsufficientBalance

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // Recipient Address Input
            OutlinedTextField(
                value = recipientInput,
                onValueChange = onRecipientChange,
                label = { Text(if (isIgra) "Recipient Igra L2 Address" else if (isKasplex) "Recipient Kasplex EVM Address" else "Recipient Address") },
                placeholder = { Text(if (isEvm) "0x..." else (if (isTestnetWallet) "kaspatest:q..." else "kaspa:q...")) },
                supportingText = {
                    when {
                        isAddressSuccess -> Text(if (isIgra) "✓ Valid Igra L2 address" else if (isKasplex) "✓ Valid Kasplex EVM address" else "✓ Valid Kaspa address", color = KaspaTea, fontWeight = FontWeight.SemiBold)
                        isPrefixMismatch -> Text("Network mismatch: Current wallet is on ${if (isTestnetWallet) "Testnet 10 (kaspatest:)" else "Mainnet (kaspa:)"}", color = RedTamper)
                        isAddressError -> Text(if (isEvm) "Invalid EVM hex address (must start with 0x and be 42 characters)" else "Invalid Kaspa address (must start with ${if (isTestnetWallet) "kaspatest:" else "kaspa:"})", color = RedTamper)
                        else -> Text(if (isIgra) "Enter destination 0x address on Igra L2" else if (isKasplex) "Enter destination 0x address on Kasplex EVM" else "Only valid Kaspa address", color = TextMuted)
                    }
                },
                isError = isAddressError,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("send_recipient_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KaspaTea,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    errorBorderColor = RedTamper
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
                label = { Text("Amount ($currencySymbol)") },
                placeholder = { Text("0.0") },
                isError = isAmountError,
                trailingIcon = {
                    Surface(
                        onClick = {
                            val maxSendable = (balanceKas - breakdown.feeKas).coerceAtLeast(0.0)
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
                    when {
                        isInsufficientBalance -> Text("Insufficient balance: Required %.6f %s exceeds available %.6f %s".format(amountNum + breakdown.feeKas, currencySymbol, balanceKas, currencySymbol), color = RedTamper)
                        amountInput.isNotBlank() && amountNum <= 0.0 -> Text("Amount must be greater than 0 $currencySymbol", color = RedTamper)
                        else -> Text("Available: %.8f %s".format(balanceKas, currencySymbol), color = TextMuted)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("send_amount_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KaspaTea,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    errorBorderColor = RedTamper
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                singleLine = true
            )
        }

        item {
            // Live Interactive Kaspa Fee & Mass Calculator
            KaspaFeeCalculatorSection(
                amountKas = amountNum,
                balanceKas = balanceKas,
                selectedSompiPerMass = selectedSompiPerMass,
                onFeerateChange = onFeerateChange,
                breakdown = breakdown,
                networkCondition = networkFeeCondition,
                isFetchingFeeCondition = isFetchingFeeCondition,
                onRefreshFeeCondition = onRefreshFeeCondition,
                selectedNetwork = selectedNetwork,
                currencySymbol = currencySymbol
            )
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
                    Text("Signing & Broadcasting...", color = SurfaceDark, fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = SurfaceDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send $currencySymbol", color = SurfaceDark, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReceiveTabContent(
    address: String,
    isMainnet: Boolean,
    selectedNetwork: String = "",
    onOpenFaucet: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    val isIgra = selectedNetwork.contains("Igra", ignoreCase = true)
    val isKasplex = selectedNetwork.contains("Kasplex", ignoreCase = true)
    val isEvmAddress = address.startsWith("0x") || isIgra || isKasplex
    val derivationPath = if (isEvmAddress) "m/44'/60'/0'/0/0 (secp256k1 EIP-155)" else "m/44'/111111'/0'/0/0 (Schnorr)"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val titleText = when {
                isIgra -> "Your Igra L2 Address"
                isKasplex -> "Your Kasplex EVM Address"
                isMainnet -> "Your Kaspa Mainnet Address"
                else -> "Your Kaspa Testnet Address"
            }
            val subtitleText = when {
                isIgra -> "Scan or copy this address to receive iKAS and tokens on Igra Execution Layer."
                isKasplex -> "Scan or copy this address to receive KAS and tokens on Kasplex Layer 2."
                isMainnet -> "Scan or copy this address to receive Kaspa."
                else -> "Scan or copy this address to receive test coins."
            }
            Text(
                text = titleText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitleText,
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Derivation Path: $derivationPath",
                fontSize = 11.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
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
                        context.startActivity(Intent.createChooser(sendIntent, "Share Kaspa Address"))
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

        if (!isMainnet) {
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
                                Text("Open official Kaspa faucet", fontSize = 11.sp, color = TextSecondary)
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

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkSwitchOverlay(
    selectedNetwork: String,
    onSelectNetwork: (String) -> Unit,
    onClose: () -> Unit
) {
    data class NetChoice(
        val name: String,
        val category: String,
        val derivationPath: String,
        val addressFormat: String,
        val description: String,
        val isEvm: Boolean,
        val isMainnet: Boolean
    )

    val allNetworks = listOf(
        // Kaspa L1 Native
        NetChoice(
            name = "Kaspa Mainnet",
            category = "Kaspa Native L1",
            derivationPath = "m/44'/111111'/0'/0/0",
            addressFormat = "kaspa:...",
            description = "Production BlockDAG • 1 BPS L1 • Real KAS",
            isEvm = false,
            isMainnet = true
        ),
        NetChoice(
            name = "Kaspa Testnet 10",
            category = "Kaspa Native L1",
            derivationPath = "m/44'/111111'/0'/0/0",
            addressFormat = "kaspatest:...",
            description = "Public Testnet 10 • Standard L1 • Test tokens",
            isEvm = false,
            isMainnet = false
        ),
        NetChoice(
            name = "Kaspa Testnet 11",
            category = "Kaspa Native L1",
            derivationPath = "m/44'/111111'/0'/0/0",
            addressFormat = "kaspatest:...",
            description = "Public Testnet 11 • High-speed 10 BPS DAG",
            isEvm = false,
            isMainnet = false
        ),
        // Kaspa EVM Ecosystem
        NetChoice(
            name = "Kasplex Mainnet",
            category = "Kaspa EVM Layer 2",
            derivationPath = "m/44'/60'/0'/0/0",
            addressFormat = "0x...",
            description = "Chain ID 202555 • EVM Layer 2 Smart Contracts",
            isEvm = true,
            isMainnet = true
        ),
        NetChoice(
            name = "Igra Mainnet",
            category = "Kaspa EVM Layer 2",
            derivationPath = "m/44'/60'/0'/0/0",
            addressFormat = "0x...",
            description = "Chain ID 38833 • Igra L2 Execution Layer",
            isEvm = true,
            isMainnet = true
        ),
        NetChoice(
            name = "Kasplex Testnet",
            category = "Kaspa EVM Testnet",
            derivationPath = "m/44'/60'/0'/0/0",
            addressFormat = "0x...",
            description = "Chain ID 167012 • Kasplex EVM Testnet",
            isEvm = true,
            isMainnet = false
        ),
        NetChoice(
            name = "Igra Galleon Testnet",
            category = "Kaspa EVM Testnet",
            derivationPath = "m/44'/60'/0'/0/0",
            addressFormat = "0x...",
            description = "Chain ID 38836 • Igra Galleon Dev & Testnet",
            isEvm = true,
            isMainnet = false
        )
    )

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = SurfaceCard,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Switch Network", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                "Choose network. The wallet instantly switches to that network's address, derivation path, and state.",
                fontSize = 12.5.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )

            val grouped = allNetworks.groupBy { it.category }
            grouped.forEach { (category, list) ->
                Text(
                    text = category.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                list.forEach { net ->
                    val isSelected = selectedNetwork == net.name
                    val themeColor = when {
                        net.name.contains("Kasplex", ignoreCase = true) -> Color(0xFF00E5FF)
                        net.name.contains("Igra", ignoreCase = true) -> Color(0xFF7C4DFF)
                        net.isMainnet -> KaspaTea
                        else -> Color(0xFF00B0FF)
                    }

                    Surface(
                        onClick = {
                            onSelectNetwork(net.name)
                            onClose()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) themeColor.copy(alpha = 0.12f) else SurfaceElevated,
                        border = BorderStroke(1.dp, if (isSelected) themeColor else SurfaceCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSelectNetwork(net.name)
                                    onClose()
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = themeColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = net.name,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isSelected) themeColor else TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (net.isMainnet) KaspaTea.copy(alpha = 0.15f) else Color(0xFF00B0FF).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = if (net.isMainnet) "MAINNET" else "TESTNET",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            color = if (net.isMainnet) KaspaTea else Color(0xFF00B0FF),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${net.description} • ${net.addressFormat}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Path: ${net.derivationPath}",
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (isSelected) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = themeColor.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        color = themeColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ManageTabContent(
    viewModel: DecentralViewModel,
    activeAccountHandle: String,
    activeAccountSeedPhrase: String,
    activeAccountId: String,
    selectedEvmChainId: Long,
    onSelectEvmNetwork: (Long) -> Unit,
    testnetAddress: String,
    allAccountsCount: Int,
    selectedNetwork: String = "",
    onOpenRecovery: () -> Unit,
    onSignOut: () -> Unit,
    onOpenNetworkSwitch: () -> Unit,
    onOpenNetworkDetails: () -> Unit
) {
    val activeNet = selectedNetwork.ifBlank { viewModel.selectedNetwork.value }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("wallet_manager_account_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, SurfaceCardBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = KaspaTea.copy(alpha = 0.14f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = KaspaTea,
                                modifier = Modifier.size(25.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeAccountHandle.ifBlank { "Kaspa Wallet" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (testnetAddress.isNotBlank()) {
                                "${testnetAddress.take(10)}…${testnetAddress.takeLast(6)}"
                            } else {
                                "Wallet address is being prepared"
                            },
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(7.dp))
                        Text(
                            text = "$allAccountsCount account${if (allAccountsCount == 1) "" else "s"} available",
                            fontSize = 11.sp,
                            color = KaspaTea,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        item {
            Text("NETWORK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Spacer(modifier = Modifier.height(7.dp))
            ManagementOptionCard(
                icon = Icons.Default.Language,
                iconColor = KaspaTea,
                title = selectedNetwork,
                subtitle = "Active wallet network · Tap to switch",
                onClick = onOpenNetworkSwitch
            )
            Spacer(modifier = Modifier.height(9.dp))
            ManagementOptionCard(
                icon = Icons.Default.Info,
                iconColor = KaspaTea,
                title = "Network Details",
                subtitle = "Address format, API endpoint and explorer",
                onClick = onOpenNetworkDetails
            )
        }



        item {
            Text("SECURITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Spacer(modifier = Modifier.height(7.dp))
            ManagementOptionCard(
                icon = Icons.Default.Lock,
                iconColor = KaspaTea,
                title = "Recovery Phrase",
                subtitle = "Securely view or back up your phrase",
                onClick = onOpenRecovery
            )
        }

        item {
            Text("ACCOUNT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Spacer(modifier = Modifier.height(7.dp))
            ManagementOptionCard(
                icon = Icons.AutoMirrored.Filled.Logout,
                iconColor = RedTamper,
                title = "Sign Out / Disconnect",
                subtitle = "Confirm with your wallet password",
                onClick = onSignOut
            )
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

@Composable
private fun WalletLockOverlay(
    viewModel: DecentralViewModel,
    onUnlock: () -> Unit
) {
    var passwordInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val biometricsEnabled by viewModel.biometricsEnabled.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .clickable(enabled = false) {}, // prevent click through
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = KaspaTea.copy(alpha = 0.1f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = KaspaTea,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Wallet Locked",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enter your security password to access your wallet.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = passwordInput,
                onValueChange = { 
                    passwordInput = it
                    errorText = null
                },
                label = { Text("Wallet Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = errorText != null,
                supportingText = {
                    if (errorText != null) Text(errorText!!, color = RedTamper)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KaspaTea,
                    unfocusedBorderColor = SurfaceCardBorder
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (viewModel.unlockWalletWithPassword(passwordInput)) {
                        onUnlock()
                    } else {
                        errorText = "Incorrect password"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Unlock Wallet", color = SurfaceDark, fontWeight = FontWeight.Bold)
            }

            if (biometricsEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                            context = context,
                            title = "Unlock Wallet",
                            subtitle = "Use biometrics to unlock your Kaspa wallet",
                            onSuccess = {
                                viewModel.unlockWalletWithBiometric()
                                onUnlock()
                            },
                            onError = { err ->
                                errorText = err
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, KaspaTea.copy(alpha = 0.5f))
                ) {
                    Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = KaspaTea)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock with Biometrics", color = KaspaTea)
                }
            }
        }
    }
}

@Composable
private fun SendScreenOverlay(
    senderAddress: String,
    balanceKas: Double,
    estimatedInputCount: Int,
    isSending: Boolean,
    recipientInput: String,
    onRecipientChange: (String) -> Unit,
    amountInput: String,
    onAmountChange: (String) -> Unit,
    selectedSompiPerMass: Long,
    onFeerateChange: (Long) -> Unit,
    networkFeeCondition: KaspaTransactionEngine.KaspaNetworkFeeCondition,
    isFetchingFeeCondition: Boolean,
    onRefreshFeeCondition: () -> Unit,
    selectedNetwork: String = "",
    onSendClick: () -> Unit,
    onClose: () -> Unit
) {
    val currencySymbol = when {
        selectedNetwork.contains("Igra", ignoreCase = true) -> "iKAS"
        selectedNetwork.contains("Testnet", ignoreCase = true) -> "tKAS"
        else -> "KAS"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .clickable(enabled = false) {} // prevent click through
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send $currencySymbol", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            
            SendTabContent(
                senderAddress = senderAddress,
                balanceKas = balanceKas,
                estimatedInputCount = estimatedInputCount,
                isSending = isSending,
                recipientInput = recipientInput,
                onRecipientChange = onRecipientChange,
                amountInput = amountInput,
                onAmountChange = onAmountChange,
                selectedSompiPerMass = selectedSompiPerMass,
                onFeerateChange = onFeerateChange,
                networkFeeCondition = networkFeeCondition,
                isFetchingFeeCondition = isFetchingFeeCondition,
                onRefreshFeeCondition = onRefreshFeeCondition,
                selectedNetwork = selectedNetwork,
                onSendClick = onSendClick
            )
        }
    }
}

@Composable
private fun ReceiveScreenOverlay(
    address: String,
    isMainnet: Boolean,
    selectedNetwork: String = "",
    onOpenFaucet: () -> Unit,
    onClose: () -> Unit
) {
    val isIgra = selectedNetwork.contains("Igra", ignoreCase = true)
    val isKasplex = selectedNetwork.contains("Kasplex", ignoreCase = true)
    val title = when {
        isIgra -> "Receive iKAS (Igra L2)"
        isKasplex -> "Receive KAS (Kasplex EVM)"
        isMainnet -> "Receive KAS (Mainnet)"
        else -> "Receive KAS (Testnet)"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .clickable(enabled = false) {} // prevent click through
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            
            ReceiveTabContent(
                address = address,
                isMainnet = isMainnet,
                selectedNetwork = selectedNetwork,
                onOpenFaucet = onOpenFaucet
            )
        }
    }
}

@Composable
private fun TransactionResultOverlay(
    isSuccess: Boolean,
    txId: String,
    errorMessage: String,
    transaction: KaspaTransactionItem?,
    selectedNetwork: String = "",
    onViewOnExplorer: () -> Unit,
    onClose: () -> Unit
) {
    val isIgra = selectedNetwork.contains("Igra", ignoreCase = true)
    val isKasplex = selectedNetwork.contains("Kasplex", ignoreCase = true)
    val currencySymbol = when {
        isIgra -> "iKAS"
        selectedNetwork.contains("Testnet", ignoreCase = true) && !isKasplex -> "tKAS"
        else -> "KAS"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg.copy(alpha = 0.95f))
            .clickable(enabled = false) {}
            .testTag("transaction_result_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(SurfaceCard, RoundedCornerShape(24.dp))
                .border(1.dp, if (isSuccess) KaspaTea.copy(alpha = 0.5f) else RedTamper.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (isSuccess) KaspaTea.copy(alpha = 0.15f) else RedTamper.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (isSuccess) KaspaTea else RedTamper,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isSuccess) "Broadcast Successful" else "Broadcast Failed",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isSuccess) {
                    when {
                        isIgra -> "Your transaction has been submitted to the Igra Execution Layer RPC."
                        isKasplex -> "Your transaction has been submitted to the Kasplex EVM Layer 2 RPC."
                        else -> "Your transaction has been submitted to the Kaspa BlockDAG mempool."
                    }
                } else errorMessage,
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            if (isSuccess && transaction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Amount sent", fontSize = 12.sp, color = TextSecondary)
                        Text("${KaspaTransactionEngine.formatKas(transaction.amountKas)} $currencySymbol", fontSize = 12.sp, color = TextPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (isIgra) "Igra gas fee" else if (isKasplex) "Kasplex gas fee" else "Network fee", fontSize = 12.sp, color = TextSecondary)
                        Text("${KaspaTransactionEngine.formatKas(transaction.feeKas)} $currencySymbol", fontSize = 12.sp, color = TextPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total deduction", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("${KaspaTransactionEngine.formatKas(transaction.amountKas + transaction.feeKas)} $currencySymbol", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = KaspaTea)
                    }
                }
            }

            if (isSuccess && txId.isNotBlank()) {
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Transaction ID", fontSize = 11.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = txId,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ElectricCyan,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onViewOnExplorer,
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, tint = SurfaceDark, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "View on ${if (isIgra) "Igra" else if (isKasplex) "Kasplex" else "Kaspa"} Explorer",
                        color = SurfaceDark,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close", color = TextSecondary)
            }
        }
    }
}

@Composable
private fun ManageScreenOverlay(
    viewModel: DecentralViewModel,
    activeAccountHandle: String,
    activeAccountSeedPhrase: String,
    activeAccountId: String,
    selectedEvmChainId: Long,
    onSelectEvmNetwork: (Long) -> Unit,
    testnetAddress: String,
    allAccountsCount: Int,
    selectedNetwork: String = "",
    onViewSeed: () -> Unit,
    onSignOut: () -> Unit,
    onOpenNetworkSwitch: () -> Unit,
    onOpenNetworkDetails: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .clickable(enabled = false) {} // prevent click through
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Wallet Settings & Management", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            
            ManageTabContent(
                viewModel = viewModel,
                activeAccountHandle = activeAccountHandle,
                activeAccountSeedPhrase = activeAccountSeedPhrase,
                activeAccountId = activeAccountId,
                selectedEvmChainId = selectedEvmChainId,
                onSelectEvmNetwork = onSelectEvmNetwork,
                testnetAddress = testnetAddress,
                allAccountsCount = allAccountsCount,
                selectedNetwork = selectedNetwork,
                onOpenRecovery = onViewSeed,
                onSignOut = onSignOut,
                onOpenNetworkSwitch = onOpenNetworkSwitch,
                onOpenNetworkDetails = onOpenNetworkDetails
            )
        }
    }
}


@Composable
private fun CompoundUtxoDialog(
    utxoCount: Int,
    balanceKas: Double,
    isCompounding: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val countToCompound = minOf(utxoCount, 80)
    AlertDialog(
        onDismissRequest = { if (!isCompounding) onDismiss() },
        containerColor = SurfaceCard,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF2F2F2).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFF2F2F2).copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_compound_utxo),
                        contentDescription = "Compound UTXOs",
                        tint = Color(0xFFF2F2F2),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Compound UTXOs",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Consolidate $countToCompound fragmented UTXO(s) into 1 single unspent output. This optimizes your wallet performance and reduces future transaction mass.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Current UTXO Count", fontSize = 12.sp, color = TextMuted)
                            Text("$utxoCount UTXOs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Compounding Batch", fontSize = 12.sp, color = TextMuted)
                            Text("$countToCompound -> 1 UTXO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = KaspaTea)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Balance", fontSize = 12.sp, color = TextMuted)
                            Text("${KaspaTransactionEngine.formatKas(balanceKas)} KAS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElectricCyan)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isCompounding,
                colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isCompounding) {
                    CircularProgressIndicator(color = SurfaceDark, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compounding...", color = SurfaceDark, fontWeight = FontWeight.Bold)
                } else {
                    Icon(painterResource(id = R.drawable.ic_compound_utxo), contentDescription = null, tint = SurfaceDark, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sweep & Compound Now", color = SurfaceDark, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!isCompounding) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        }
    )
}

@Composable
private fun TransactionDetailDialog(
    tx: KaspaTransactionItem,
    selectedNetwork: String = "",
    onCopyTxId: (String) -> Unit,
    onViewOnExplorer: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isReceive = tx.type == "RECEIVED"
    val isIgra = selectedNetwork.contains("Igra", ignoreCase = true)
    val isKasplex = selectedNetwork.contains("Kasplex", ignoreCase = true)
    val isEvm = isIgra || isKasplex
    val currencySymbol = when {
        isIgra -> "iKAS"
        selectedNetwork.contains("Testnet", ignoreCase = true) && !isKasplex -> "tKAS"
        else -> "KAS"
    }

    val dateStr = remember(tx.blockTime) {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        sdf.format(Date(if (tx.blockTime > 1000000000000L) tx.blockTime else tx.blockTime * 1000L))
    }

    val networkProtocolName = when {
        isIgra -> if (selectedNetwork.contains("Galleon", true)) "Igra Galleon Dev & Testnet" else "Igra L2 Execution Layer"
        isKasplex -> if (selectedNetwork.contains("Testnet", true)) "Kasplex EVM Testnet" else "Kasplex EVM Layer 2"
        selectedNetwork.contains("Mainnet", true) -> "Kaspa Native L1 BlockDAG"
        selectedNetwork.contains("11", true) -> "Kaspa Testnet 11 (10 BPS DAG)"
        else -> "Kaspa Testnet 10 (1 BPS DAG)"
    }

    val signDerivationPath = when {
        isEvm -> "m/44'/60'/0'/0/0 (secp256k1 EIP-155)"
        else -> "m/44'/111111'/0'/0/0 (Schnorr / secp256k1)"
    }

    val broadcastEndpoint = when {
        selectedNetwork.contains("Igra Mainnet", true) -> "rpc.igralabs.com:8545"
        selectedNetwork.contains("Galleon", true) -> "galleon-testnet.igralabs.com:8545"
        selectedNetwork.contains("Kasplex Testnet", true) -> "rpc.kasplextest.xyz"
        isKasplex -> "evmrpc.kasplex.org"
        selectedNetwork.contains("Mainnet", true) -> "api.kaspa.org (P2P gRPC)"
        selectedNetwork.contains("11", true) -> "api-tn11.kaspa.org"
        else -> "api-tn10.kaspa.org"
    }

    val explorerName = when {
        isIgra -> "Igra Explorer"
        isKasplex -> "Kasplex Explorer"
        else -> "Kaspa Explorer"
    }

    val counterpartyLabel = if (isReceive) "Sender Address" else "Recipient Address"
    val counterpartyAddress = if (tx.counterpartyAddress.isNotBlank()) {
        tx.counterpartyAddress
    } else {
        if (isReceive) {
            if (isIgra) "Igra L2 Sequencer / Execution Contract" else if (isKasplex) "Kasplex EVM Sequencer" else "Kaspa BlockDAG Network"
        } else {
            "Self / Change Output"
        }
    }

    val addressTypeBadge = if (isEvm) {
        if (isIgra) "Verified Igra L2" else "Verified Kasplex EVM"
    } else {
        "Verified CashAddr"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        shape = RoundedCornerShape(20.dp),
        title = {
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
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isReceive) KaspaTea.copy(alpha = 0.15f) else RedTamper.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isReceive) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade,
                            contentDescription = null,
                            tint = if (isReceive) KaspaTea else RedTamper,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = if (isReceive) "Received Transaction" else "Sent Transaction",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isReceive) "Incoming funds transfer" else "Outgoing broadcast",
                            fontSize = 10.5.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = if (tx.isAccepted) KaspaTea.copy(alpha = 0.14f) else AmberCentral.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(
                        text = if (tx.isAccepted) "Accepted" else "Pending",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (tx.isAccepted) KaspaTea else AmberCentral,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(color = SurfaceCardBorder, thickness = 0.5.dp)

                // Hero Cardboard Details (Exact same pattern for both Send & Receive)
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Transaction Amount", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = "${if (isReceive) "+" else "-"}%.8f $currencySymbol".format(tx.amountKas),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isReceive) KaspaTea else RedTamper
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isIgra) "Igra Gas Fee" else if (isKasplex) "Kasplex Gas Fee" else "Network Miner Fee", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = "${KaspaTransactionEngine.formatKas(tx.feeKas)} $currencySymbol" + if (isReceive) " (Paid by sender)" else "",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isReceive) TextMuted else AmberCentral
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Timestamp", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = dateStr,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // Counterparty Address Card (Exact same card pattern for both Send & Receive)
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(counterpartyLabel, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                            Surface(
                                color = KaspaTea.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = addressTypeBadge,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KaspaTea,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = counterpartyAddress,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ElectricCyan,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Network & Execution Details Card (Dynamic write-up for Igra, Kasplex, and Kaspa)
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Execution & Broadcast Details", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Network:", fontSize = 11.5.sp, color = TextMuted)
                            Text(networkProtocolName, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Sign Derivation Path:", fontSize = 11.5.sp, color = TextMuted)
                            Text(signDerivationPath, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = KaspaTea, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Broadcast RPC:", fontSize = 11.5.sp, color = TextMuted)
                            Text(broadcastEndpoint, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = ElectricCyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (tx.acceptingBlueScore != null) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Accepting Blue Score:", fontSize = 11.5.sp, color = TextMuted)
                                Text("${tx.acceptingBlueScore}", fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            }
                        }
                    }
                }

                // TXID Card
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Transaction Hash (TXID)", fontSize = 11.sp, color = TextSecondary)
                            IconButton(
                                onClick = { onCopyTxId(tx.txId) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy TXID",
                                    tint = KaspaTea,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tx.txId,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ElectricCyan,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onViewOnExplorer(tx.txId) },
                colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.OpenInBrowser, null, tint = SurfaceDark, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("View on $explorerName", color = SurfaceDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun ConfirmSendScreenOverlay(
    recipientAddress: String,
    amountKas: Double,
    selectedSompiPerMass: Long,
    breakdown: KaspaTransactionEngine.FeeCalculationBreakdown,
    priceUsd: Double,
    isSending: Boolean,
    selectedNetwork: String = "",
    viewModel: DecentralViewModel,
    onBack: () -> Unit,
    onBroadcastConfirmed: () -> Unit
) {
    val isIgra = selectedNetwork.contains("Igra", ignoreCase = true)
    val isKasplex = selectedNetwork.contains("Kasplex", ignoreCase = true)
    val isEvm = isIgra || isKasplex || recipientAddress.startsWith("0x")
    val currencySymbol = when {
        isIgra -> "iKAS"
        selectedNetwork.contains("Testnet", ignoreCase = true) && !isKasplex -> "tKAS"
        else -> "KAS"
    }

    val signDerivationPath = when {
        isEvm -> "m/44'/60'/0'/0/0 (secp256k1 EIP-155)"
        else -> "m/44'/111111'/0'/0/0 (Schnorr / secp256k1)"
    }

    val broadcastEndpoint = when {
        selectedNetwork.contains("Igra Mainnet", true) -> "rpc.igralabs.com:8545"
        selectedNetwork.contains("Galleon", true) -> "galleon-testnet.igralabs.com:8545"
        selectedNetwork.contains("Kasplex Testnet", true) -> "rpc.kasplextest.xyz"
        isKasplex -> "evmrpc.kasplex.org"
        selectedNetwork.contains("Mainnet", true) -> "api.kaspa.org"
        selectedNetwork.contains("11", true) -> "api-tn11.kaspa.org"
        else -> "api-tn10.kaspa.org"
    }
    val context = LocalContext.current
    val hasPassword by viewModel.hasWalletPassword.collectAsState()
    val biometricsEnabled by viewModel.biometricsEnabled.collectAsState()

    var passwordInput by remember { mutableStateOf("") }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var showPasswordFallback by remember { mutableStateOf(false) }

    fun proceedToBroadcast() {
        authErrorMessage = null
        onBroadcastConfirmed()
    }

    fun initiateAuthentication() {
        if (isSending) return
        val isProtected = hasPassword || biometricsEnabled
        if (!isProtected) {
            proceedToBroadcast()
            return
        }

        if (biometricsEnabled && BiometricAuthHelper.isDeviceLockAvailable(context)) {
            BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                context = context,
                title = "Authorize KAS Broadcast",
                subtitle = "Authenticate to confirm sending %.4f KAS".format(amountKas),
                onSuccess = {
                    proceedToBroadcast()
                },
                onError = { err ->
                    if (hasPassword) {
                        showPasswordFallback = true
                        authErrorMessage = "$err. Please enter your wallet password below."
                    } else {
                        authErrorMessage = err
                    }
                }
            )
        } else if (hasPassword) {
            showPasswordFallback = true
        } else {
            proceedToBroadcast()
        }
    }

    fun verifyPasswordAndProceed() {
        if (passwordInput.isBlank()) {
            authErrorMessage = "Please enter your wallet password to authorize broadcast."
            return
        }
        val isValid = viewModel.unlockWalletWithPassword(passwordInput)
        if (isValid) {
            proceedToBroadcast()
        } else {
            authErrorMessage = "Incorrect wallet password. Please try again."
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("confirm_send_screen_overlay")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Clean Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, enabled = !isSending) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Review & Confirm",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    // Header Amount Hero Card
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, KaspaTea.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(KaspaTea.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    tint = KaspaTea,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "You are sending",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "%.8f $currencySymbol".format(amountKas),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = KaspaTea
                            )
                            if (priceUsd > 0.0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "≈ $%.2f USD".format(amountKas * priceUsd),
                                    fontSize = 13.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }

                // Recipient Card
                item {
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recipient Address",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Surface(
                                    color = KaspaTea.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (isEvm) (if (isIgra) "Verified Igra L2" else "Verified Kasplex EVM") else "Verified CashAddr",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KaspaTea,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = recipientAddress,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = ElectricCyan,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Network Fee & Mass Details Card
                item {
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (isEvm) "EVM Execution & Gas Details" else "Fee Estimate & Transaction Details",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Sign Derivation Path:", fontSize = 12.sp, color = TextMuted)
                                Text(
                                    text = signDerivationPath,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = KaspaTea
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Broadcast RPC:", fontSize = 12.sp, color = TextMuted)
                                Text(
                                    text = broadcastEndpoint,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = ElectricCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (isEvm) "Execution Units:" else "Estimated BlockDAG Mass:", fontSize = 12.sp, color = TextMuted)
                                Text(
                                    text = if (isEvm) "21,000 standard gas units" else "${breakdown.totalMass} mass units",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                        }
                    }
                }

                // Security State Card
                item {
                    val isProtected = hasPassword || biometricsEnabled
                    Surface(
                        color = if (isProtected) KaspaTea.copy(alpha = 0.08f) else SurfaceCard,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (isProtected) KaspaTea.copy(alpha = 0.3f) else SurfaceCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isProtected) Icons.Default.Lock else Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (isProtected) KaspaTea else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isProtected) "Security Verification Active" else "Standard Authorization",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isProtected) KaspaTea else TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isProtected) {
                                    if (biometricsEnabled) "Hardware biometric confirmation or PIN is required prior to broadcasting on-chain."
                                    else "Security password verification is required before broadcast."
                                } else {
                                    "No lock protection active. Instant confirmation enabled."
                                },
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )

                            if (showPasswordFallback) {
                                Spacer(modifier = Modifier.height(14.dp))
                                OutlinedTextField(
                                    value = passwordInput,
                                    onValueChange = {
                                        passwordInput = it
                                        authErrorMessage = null
                                    },
                                    label = { Text("Wallet Password") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = KaspaTea,
                                        unfocusedBorderColor = SurfaceCardBorder,
                                        focusedLabelColor = KaspaTea,
                                        unfocusedLabelColor = TextMuted,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    singleLine = true,
                                    modifier = Modifier
                                        .widthIn(max = 420.dp)
                                        .fillMaxWidth(0.92f)
                                        .align(Alignment.CenterHorizontally)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { verifyPasswordAndProceed() },
                                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Authorize with Password", color = SurfaceDark, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (!authErrorMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = authErrorMessage ?: "",
                                    fontSize = 11.sp,
                                    color = RedTamper,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Bottom Actions
            Surface(
                color = SurfaceElevated,
                border = BorderStroke(1.dp, SurfaceCardBorder.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            if (showPasswordFallback) {
                                verifyPasswordAndProceed()
                            } else {
                                initiateAuthentication()
                            }
                        },
                        enabled = !isSending,
                        colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("confirm_and_broadcast_button")
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = SurfaceDark,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Broadcasting to DAG...", color = SurfaceDark, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = if (hasPassword || biometricsEnabled) Icons.Default.Fingerprint else Icons.Default.Send,
                                contentDescription = null,
                                tint = SurfaceDark,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hasPassword || biometricsEnabled) "Authorize & Broadcast" else "Confirm & Broadcast",
                                color = SurfaceDark,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = onBack,
                        enabled = !isSending,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text("Cancel", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}


@Composable
private fun WalletAccountsDialog(
    allAccounts: List<AccountEntity>,
    activeAccount: AccountEntity?,
    selectedNetwork: String = "",
    onOpenNetworkSwitch: (() -> Unit)? = null,
    onSelectAccount: (AccountEntity) -> Unit,
    onCreateNewWallet: () -> Unit,
    onImportExistingWallet: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss)
            .testTag("wallet_accounts_overlay"),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .background(TextMuted.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Wallets & Accounts",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.5.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (selectedNetwork.isNotBlank() && onOpenNetworkSwitch != null) {
                            Surface(
                                onClick = onOpenNetworkSwitch,
                                shape = RoundedCornerShape(8.dp),
                                color = SurfaceDark,
                                border = BorderStroke(1.dp, SurfaceCardBorder)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = selectedNetwork,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondary,
                                        maxLines = 1
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Switch Network",
                                        tint = TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, KaspaTea.copy(alpha = 0.4f))
                        ) {
                            Text(
                                "${allAccounts.size} Wallets",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = KaspaTea,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Select an active wallet or add a new identity:", fontSize = 11.5.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allAccounts) { account ->
                        val isActive = account.kaspaAddress == activeAccount?.kaspaAddress
                        Surface(
                            onClick = {
                                onSelectAccount(account)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceDark,
                            border = BorderStroke(1.dp, if (isActive) KaspaTea else SurfaceCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = if (isActive) KaspaTea else TextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = account.handle,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isActive) KaspaTea else TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = account.kaspaAddress.take(12) + "..." + account.kaspaAddress.takeLast(6),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextMuted,
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (isActive) {
                                    Icon(Icons.Default.Check, contentDescription = "Active", tint = KaspaTea, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = SurfaceCardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onCreateNewWallet()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KaspaTea),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceDark),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.Add, null, tint = KaspaTea, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create", color = KaspaTea, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onImportExistingWallet()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.Download, null, tint = SurfaceDark, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import", color = SurfaceDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnlockRecoveryBottomSheet(
    viewModel: DecentralViewModel,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hasPassword by viewModel.hasWalletPassword.collectAsState()
    val biometricsEnabled by viewModel.biometricsEnabled.collectAsState()

    var passwordInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isBiometricAvailable = remember { BiometricAuthHelper.isDeviceLockAvailable(context) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = KaspaTea.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = KaspaTea,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Security Verification",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Confirm with your wallet password",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Text(
                text = "Your 12-word seed phrase grants master access to all wallet funds. Confirm your password or device authentication to proceed.",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )

            if (hasPassword) {
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        errorMessage = null
                    },
                    label = { Text("Wallet Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(errorMessage ?: "", color = RedTamper, fontSize = 11.sp)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KaspaTea,
                        unfocusedBorderColor = SurfaceCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        errorBorderColor = RedTamper
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("seed_unlock_password_input")
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AmberCentral.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, AmberCentral.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Your wallet is currently protected by device credentials. Authorize to reveal your recovery seed phrase.",
                        fontSize = 12.5.sp,
                        color = AmberCentral,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = {
                    if (hasPassword) {
                        if (passwordInput.isBlank()) {
                            errorMessage = "Please enter your wallet password"
                            return@Button
                        }
                        if (viewModel.unlockWalletWithPassword(passwordInput)) {
                            onSuccess()
                        } else {
                            errorMessage = "Incorrect wallet password"
                        }
                    } else if (biometricsEnabled && isBiometricAvailable) {
                        BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                            context = context,
                            title = "Unlock Recovery Phrase",
                            subtitle = "Authenticate to view seed phrase",
                            onSuccess = { onSuccess() },
                            onError = { err -> errorMessage = err }
                        )
                    } else {
                        onSuccess()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("seed_unlock_confirm_button"),
                colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = SurfaceDark, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasPassword) "Unlock & View Phrase" else "Authenticate & View",
                    color = SurfaceDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            if (biometricsEnabled && isBiometricAvailable) {
                OutlinedButton(
                    onClick = {
                        BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                            context = context,
                            title = "Unlock Recovery Phrase",
                            subtitle = "Authenticate to view seed phrase",
                            onSuccess = { onSuccess() },
                            onError = { err -> errorMessage = err }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("seed_unlock_biometric_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = KaspaTea, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Authorize with Biometrics", color = TextPrimary)
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", color = TextSecondary)
            }
        }
    }
}

@Composable
private fun RecoveryScreenOverlay(
    seedPhrase: String,
    viewModel: DecentralViewModel,
    isPreUnlocked: Boolean = false,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasPassword by viewModel.hasWalletPassword.collectAsState()
    val biometricsEnabled by viewModel.biometricsEnabled.collectAsState()

    var isRevealed by remember { mutableStateOf(isPreUnlocked) }
    var passwordInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var copyNotice by remember { mutableStateOf(false) }

    val words = remember(seedPhrase) {
        if (seedPhrase.isNotBlank()) seedPhrase.trim().split(Regex("\\s+")) else emptyList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .clickable(enabled = false) {}
            .testTag("recovery_screen_overlay")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Recovery Phrase",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Security Warning Card
                    Surface(
                        color = AmberCentral.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, AmberCentral.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = AmberCentral,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Confidential Master Key",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = AmberCentral
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Your 12-word seed phrase gives full control of your wallet funds. Never share it, photograph it, or enter it on untrusted sites.",
                                    fontSize = 11.5.sp,
                                    color = TextSecondary,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                if (!isRevealed) {
                    item {
                        Surface(
                            color = SurfaceCard,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = KaspaTea,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Security Verification Required",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Enter your wallet password or authenticate to reveal the phrase.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = passwordInput,
                                    onValueChange = {
                                        passwordInput = it
                                        errorMessage = null
                                    },
                                    label = { Text("Wallet Password") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = KaspaTea,
                                        unfocusedBorderColor = SurfaceCardBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (!errorMessage.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(errorMessage ?: "", color = RedTamper, fontSize = 11.sp)
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        if (viewModel.unlockWalletWithPassword(passwordInput)) {
                                            isRevealed = true
                                            errorMessage = null
                                        } else {
                                            errorMessage = "Incorrect password. Please try again."
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Text("Reveal Seed Phrase", color = SurfaceDark, fontWeight = FontWeight.Bold)
                                }

                                if (biometricsEnabled && BiometricAuthHelper.isDeviceLockAvailable(context)) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = {
                                            BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                                                context = context,
                                                title = "Authorize Seed Reveal",
                                                subtitle = "Authenticate to view recovery phrase",
                                                onSuccess = { isRevealed = true },
                                                onError = { err -> errorMessage = err }
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, KaspaTea.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Icon(Icons.Default.Fingerprint, null, tint = KaspaTea, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Unlock with Biometrics", color = KaspaTea)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        var isPhraseMasked by remember { mutableStateOf(false) }
                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Your 12-Word Recovery Phrase",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextMuted
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { isPhraseMasked = !isPhraseMasked },
                                            modifier = Modifier.size(32.dp).testTag("mask_reveal_icon")
                                        ) {
                                            Icon(
                                                imageVector = if (!isPhraseMasked) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (!isPhraseMasked) "Mask Phrase" else "Click to Reveal Phrase",
                                                tint = if (!isPhraseMasked) KaspaTea else TextMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(2.dp))
                                        TextButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Recovery Phrase", seedPhrase))
                                                copyNotice = true
                                            }
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = KaspaTea, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (copyNotice) "Copied!" else "Copy", color = KaspaTea, fontSize = 12.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                // Numbered Grid of Words
                                for (rowIdx in 0 until (words.size + 1) / 2) {
                                    val leftIdx = rowIdx * 2
                                    val rightIdx = leftIdx + 1
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (leftIdx < words.size) {
                                            Surface(
                                                color = SurfaceCard,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, SurfaceCardBorder.copy(alpha = 0.6f)),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { if (isPhraseMasked) isPhraseMasked = false }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${leftIdx + 1}.",
                                                        fontSize = 11.sp,
                                                        color = TextMuted,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.width(22.dp)
                                                    )
                                                    Text(
                                                        text = if (!isPhraseMasked) words[leftIdx] else "••••••",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = if (!isPhraseMasked) KaspaTea else TextMuted
                                                    )
                                                }
                                            }
                                        }

                                        if (rightIdx < words.size) {
                                            Surface(
                                                color = SurfaceCard,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, SurfaceCardBorder.copy(alpha = 0.6f)),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { if (isPhraseMasked) isPhraseMasked = false }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${rightIdx + 1}.",
                                                        fontSize = 11.sp,
                                                        color = TextMuted,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.width(22.dp)
                                                    )
                                                    Text(
                                                        text = if (!isPhraseMasked) words[rightIdx] else "••••••",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = if (!isPhraseMasked) KaspaTea else TextMuted
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Recovery Phrase", seedPhrase))
                                copyNotice = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, tint = SurfaceDark, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (copyNotice) "Phrase Copied!" else "Copy Recovery Phrase",
                                color = SurfaceDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Bottom Close Action
            Surface(
                color = SurfaceElevated,
                border = BorderStroke(1.dp, SurfaceCardBorder.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text("Done", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignOutBottomSheet(
    viewModel: DecentralViewModel,
    onDismiss: () -> Unit
) {
    var passwordInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val biometricsEnabled by viewModel.biometricsEnabled.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = RedTamper.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = RedTamper,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Sign Out & Disconnect",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Confirm with your wallet password",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Text(
                text = "To disconnect your wallet from this session, enter your wallet password below. Active cryptographic keys will be securely cleared from memory.",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )

            OutlinedTextField(
                value = passwordInput,
                onValueChange = {
                    passwordInput = it
                    errorMessage = null
                },
                label = { Text("Wallet Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("sign_out_password_input"),
                isError = errorMessage != null,
                supportingText = {
                    if (errorMessage != null) Text(errorMessage!!, color = RedTamper)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KaspaTea,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    errorBorderColor = RedTamper
                ),
                singleLine = true
            )

            Button(
                onClick = {
                    if (passwordInput.isBlank()) {
                        errorMessage = "Please enter your wallet password"
                        return@Button
                    }
                    if (viewModel.unlockWalletWithPassword(passwordInput)) {
                        viewModel.signOutActiveAccount()
                        viewModel.setTab(AppTab.BROWSER_GATEWAY)
                        onDismiss()
                    } else {
                        errorMessage = "Incorrect wallet password"
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_confirm_button"),
                colors = ButtonDefaults.buttonColors(containerColor = RedTamper),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Confirm Disconnect", color = Color.White, fontWeight = FontWeight.Bold)
            }

            if (biometricsEnabled && BiometricAuthHelper.isDeviceLockAvailable(context)) {
                OutlinedButton(
                    onClick = {
                        BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                            context = context,
                            title = "Sign Out Confirmation",
                            subtitle = "Authenticate to confirm disconnecting wallet",
                            onSuccess = {
                                viewModel.signOutActiveAccount()
                                viewModel.setTab(AppTab.BROWSER_GATEWAY)
                                onDismiss()
                            },
                            onError = { err ->
                                errorMessage = err
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = KaspaTea, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Authorize with Biometrics", color = TextPrimary)
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun NetworkDetailsScreenOverlay(
    selectedNetwork: String,
    testnetAddress: String,
    onOpenNetworkSwitch: () -> Unit,
    onClose: () -> Unit
) {
    val isEvm = selectedNetwork.contains("Kasplex", ignoreCase = true) || selectedNetwork.contains("Igra", ignoreCase = true)
    val isIgra = selectedNetwork.contains("Igra", ignoreCase = true)
    val isKasplex = selectedNetwork.contains("Kasplex", ignoreCase = true)
    val isMainnet = selectedNetwork.contains("Mainnet", ignoreCase = true)

    val currencySymbol = when {
        isIgra -> "iKAS"
        selectedNetwork.contains("Testnet", ignoreCase = true) && !isKasplex -> "tKAS"
        else -> "KAS"
    }

    val networkProtocolName = when {
        isIgra -> if (selectedNetwork.contains("Galleon", true)) "Igra Galleon Dev & Testnet" else "Igra L2 Execution Layer"
        isKasplex -> if (selectedNetwork.contains("Testnet", true)) "Kasplex EVM Testnet" else "Kasplex EVM Layer 2"
        selectedNetwork.contains("Mainnet", true) -> "Kaspa Native L1 BlockDAG"
        selectedNetwork.contains("11", true) -> "Kaspa Testnet 11 (10 BPS DAG)"
        else -> "Kaspa Testnet 10 (1 BPS DAG)"
    }

    val signDerivationPath = when {
        isEvm -> "m/44'/60'/0'/0/0 (secp256k1 EIP-155)"
        else -> "m/44'/111111'/0'/0/0 (Schnorr / secp256k1)"
    }

    val broadcastEndpoint = when {
        selectedNetwork.contains("Igra Mainnet", true) -> "https://rpc.igralabs.com:8545"
        selectedNetwork.contains("Galleon", true) -> "https://galleon-testnet.igralabs.com:8545"
        selectedNetwork.contains("Kasplex Testnet", true) -> "https://rpc.kasplextest.xyz"
        isKasplex -> "https://evmrpc.kasplex.org"
        selectedNetwork.contains("Mainnet", true) -> "https://api.kaspa.org"
        selectedNetwork.contains("11", true) -> "https://api-tn11.kaspa.org"
        else -> "https://api-tn10.kaspa.org"
    }

    val explorerUrl = when {
        selectedNetwork.contains("Kasplex Mainnet", ignoreCase = true) -> "https://explorer.kasplex.org"
        selectedNetwork.contains("Igra Mainnet", ignoreCase = true) -> "https://tidx.igralabs.com/explore"
        selectedNetwork.contains("Kasplex Testnet", ignoreCase = true) -> "https://testnet.kasplex.org"
        selectedNetwork.contains("Galleon", ignoreCase = true) -> "https://galleon.igralabs.com"
        isMainnet -> "https://kaspa.stream"
        selectedNetwork.contains("11", true) -> "https://tn11.kaspa.stream"
        else -> "https://tn10.kaspa.stream"
    }

    val themeColor = when {
        isKasplex -> Color(0xFF00E5FF)
        isIgra -> Color(0xFF7C4DFF)
        isMainnet -> KaspaTea
        else -> Color(0xFF00B0FF)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .clickable(enabled = false) {}
            .testTag("network_details_screen_overlay")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Network Details",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, themeColor.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = themeColor.copy(alpha = 0.15f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        tint = themeColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = selectedNetwork,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = themeColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (isEvm) "EVM LAYER 2" else if (isMainnet) "LAYER 1 MAINNET" else "LAYER 1 TESTNET",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = themeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Derivation & Sign Path Card
                item {
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Sign Derivation Path",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = signDerivationPath,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = KaspaTea
                            )
                            Text(
                                text = if (isIgra) {
                                    "Transactions on Igra L2 use standard EVM sign derivation path m/44'/60'/0'/0/0 (EIP-155 secp256k1) and sign execution payloads settled on Igra."
                                } else if (isKasplex) {
                                    "Transactions on Kasplex use EVM sign derivation path m/44'/60'/0'/0/0 (EIP-155 secp256k1) and sign smart contract state transitions."
                                } else {
                                    "Transactions on Kaspa Native use Schnorr/secp256k1 derivation path m/44'/111111'/0'/0/0 for instant UTXO BlockDAG transfers."
                                },
                                fontSize = 11.5.sp,
                                color = TextMuted,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Broadcast Endpoint & RPC Card
                item {
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Broadcast RPC Endpoint",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isEvm) "JSON-RPC (EVM)" else "gRPC / REST",
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = broadcastEndpoint,
                                fontSize = 12.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = ElectricCyan
                            )
                            Text(
                                text = if (isIgra) {
                                    "Transactions broadcast directly to Igra Execution Layer RPC (rpc.igralabs.com:8545). Gas execution fee is settled in iKAS."
                                } else if (isKasplex) {
                                    "Transactions broadcast directly to Kasplex Layer 2 RPC (evmrpc.kasplex.org). Gas fee is settled in KAS."
                                } else {
                                    "Transactions broadcast directly to Kaspa BlockDAG P2P node mempool with 10 BPS instant consensus finality."
                                },
                                fontSize = 11.5.sp,
                                color = TextMuted,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Address & Protocol Specifications Card
                item {
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Protocol Specifications",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Architecture:", fontSize = 12.sp, color = TextMuted)
                                Text(networkProtocolName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Settlement Currency:", fontSize = 12.sp, color = TextMuted)
                                Text(currencySymbol, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = KaspaTea)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Address Format:", fontSize = 12.sp, color = TextMuted)
                                Text(if (isEvm) "0x... (EVM Standard)" else if (isMainnet) "kaspa: (CashAddr)" else "kaspatest: (CashAddr)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Explorer:", fontSize = 12.sp, color = TextMuted)
                                Text(explorerUrl.substringAfter("://"), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = ElectricCyan)
                            }
                        }
                    }
                }

                // Switch Network Button
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onOpenNetworkSwitch,
                        colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Language, contentDescription = null, tint = SurfaceDark)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Switch Network", color = SurfaceDark, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

enum class WalletSetupStep {
    WELCOME,
    CREATE_DETAILS,
    IMPORT_PHRASE,
    BACKUP_PHRASE
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
        color = SurfaceDark,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, SurfaceCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 12.sp, color = TextSecondary)
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletSetupFlow(
    viewModel: DecentralViewModel,
    onBackToBrowser: () -> Unit,
    onWalletReady: () -> Unit = {},
    initialStep: WalletSetupStep = WalletSetupStep.WELCOME,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableStateOf(initialStep) }
    var walletName by remember { mutableStateOf("") }
    var walletPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var bip39Passphrase by remember { mutableStateOf("") }
    var importMnemonic by remember { mutableStateOf("") }
    var generatedMnemonic by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var isCreatePasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var isCreatePassphraseVisible by remember { mutableStateOf(false) }

    var isImportPasswordVisible by remember { mutableStateOf(false) }
    var isImportPassphraseVisible by remember { mutableStateOf(false) }
    var isImportPhraseMasked by remember { mutableStateOf(false) }

    var selectedSetupNetwork by remember { mutableStateOf("Kaspa Testnet 10") }
    var expandedNetworkDropdown by remember { mutableStateOf(false) }
    val availableNetworks = listOf(
        "Kaspa Testnet 10",
        "Kaspa Testnet 11",
        "Kaspa Mainnet",
        "Kasplex Testnet",
        "Kasplex Mainnet",
        "Igra Mainnet"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (currentStep == WalletSetupStep.WELCOME) {
                        onBackToBrowser()
                    } else {
                        currentStep = WalletSetupStep.WELCOME
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
            }

            // Content based on step - moved upward with space below
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                when (currentStep) {
                    WalletSetupStep.WELCOME -> {
                        Surface(
                            shape = CircleShape,
                            color = KaspaTea.copy(alpha = 0.15f),
                            modifier = Modifier.size(84.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = KaspaTea,
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Welcome to Kaspa Wallet",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Secure non-custodial multi-network wallet for Kaspa BlockDAG, Kasplex EVM, and Igra L2 with BIP39 seed phrase & passphrase support.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        Button(
                            onClick = { currentStep = WalletSetupStep.CREATE_DETAILS },
                            colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text("Create New Wallet", color = SurfaceDark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { currentStep = WalletSetupStep.IMPORT_PHRASE },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text("Import Existing Wallet", fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, softWrap = false)
                        }
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                    WalletSetupStep.CREATE_DETAILS -> {
                        Text(
                            text = "Create Wallet",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Configure wallet name, password, network, and optional passphrase.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Network Dropdown Box
                        ExposedDropdownMenuBox(
                            expanded = expandedNetworkDropdown,
                            onExpandedChange = { expandedNetworkDropdown = !expandedNetworkDropdown },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedSetupNetwork,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Network") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedNetworkDropdown) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedNetworkDropdown,
                                onDismissRequest = { expandedNetworkDropdown = false }
                            ) {
                                availableNetworks.forEach { net ->
                                    DropdownMenuItem(
                                        text = { Text(net) },
                                        onClick = {
                                            selectedSetupNetwork = net
                                            expandedNetworkDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = walletName,
                            onValueChange = { walletName = it },
                            label = { Text("Wallet Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = walletPassword,
                            onValueChange = { walletPassword = it },
                            label = { Text("App Encryption Password (min 6 chars)") },
                            visualTransformation = if (isCreatePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isCreatePasswordVisible = !isCreatePasswordVisible }) {
                                    Icon(
                                        imageVector = if (isCreatePasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isCreatePasswordVisible) "Hide Password" else "Show Password",
                                        tint = if (isCreatePasswordVisible) KaspaTea else TextMuted
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm App Password") },
                            visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isConfirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isConfirmPasswordVisible) "Hide Password" else "Show Password",
                                        tint = if (isConfirmPasswordVisible) KaspaTea else TextMuted
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = bip39Passphrase,
                            onValueChange = { bip39Passphrase = it },
                            label = { Text("Passphrase (Optional / 13th word)") },
                            visualTransformation = if (isCreatePassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isCreatePassphraseVisible = !isCreatePassphraseVisible }) {
                                    Icon(
                                        imageVector = if (isCreatePassphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isCreatePassphraseVisible) "Hide Passphrase" else "Show Passphrase",
                                        tint = if (isCreatePassphraseVisible) KaspaTea else TextMuted
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = errorMessage ?: "", color = RedTamper, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = {
                                if (walletPassword.length < 6) {
                                    errorMessage = "Password must be at least 6 characters"
                                    return@Button
                                }
                                if (walletPassword != confirmPassword) {
                                    errorMessage = "Passwords do not match"
                                    return@Button
                                }
                                viewModel.selectNetwork(selectedSetupNetwork)
                                val mnemonic = com.example.network.CryptoUtils.generateMnemonic()
                                generatedMnemonic = mnemonic
                                currentStep = WalletSetupStep.BACKUP_PHRASE
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("Continue to Seed Backup", color = SurfaceDark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                    WalletSetupStep.IMPORT_PHRASE -> {
                        Text(
                            text = "Import Recovery Phrase",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enter your 12 or 24-word recovery phrase, network, and optional passphrase.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Network Dropdown Box
                        ExposedDropdownMenuBox(
                            expanded = expandedNetworkDropdown,
                            onExpandedChange = { expandedNetworkDropdown = !expandedNetworkDropdown },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedSetupNetwork,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Network") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedNetworkDropdown) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedNetworkDropdown,
                                onDismissRequest = { expandedNetworkDropdown = false }
                            ) {
                                availableNetworks.forEach { net ->
                                    DropdownMenuItem(
                                        text = { Text(net) },
                                        onClick = {
                                            selectedSetupNetwork = net
                                            expandedNetworkDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = walletName,
                            onValueChange = { walletName = it },
                            label = { Text("Wallet Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = importMnemonic,
                            onValueChange = { importMnemonic = it },
                            label = { Text("Recovery Phrase (space-separated 12/24 words)") },
                            visualTransformation = if (isImportPhraseMasked) PasswordVisualTransformation() else VisualTransformation.None,
                            trailingIcon = {
                                IconButton(onClick = { isImportPhraseMasked = !isImportPhraseMasked }) {
                                    Icon(
                                        imageVector = if (!isImportPhraseMasked) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (!isImportPhraseMasked) "Mask Recovery Phrase" else "Show Recovery Phrase",
                                        tint = if (!isImportPhraseMasked) KaspaTea else TextMuted
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            maxLines = 5
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = walletPassword,
                            onValueChange = { walletPassword = it },
                            label = { Text("New App Encryption Password") },
                            visualTransformation = if (isImportPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isImportPasswordVisible = !isImportPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isImportPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isImportPasswordVisible) "Hide Password" else "Show Password",
                                        tint = if (isImportPasswordVisible) KaspaTea else TextMuted
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = bip39Passphrase,
                            onValueChange = { bip39Passphrase = it },
                            label = { Text("Passphrase (Optional / 13th word)") },
                            visualTransformation = if (isImportPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isImportPassphraseVisible = !isImportPassphraseVisible }) {
                                    Icon(
                                        imageVector = if (isImportPassphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isImportPassphraseVisible) "Hide Passphrase" else "Show Passphrase",
                                        tint = if (isImportPassphraseVisible) KaspaTea else TextMuted
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = errorMessage ?: "", color = RedTamper, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(28.dp))
                        Button(
                            onClick = {
                                val wordCount = importMnemonic.trim().split("\\s+".toRegex()).size
                                if (wordCount != 12 && wordCount != 24) {
                                    errorMessage = "Please enter a valid 12 or 24-word recovery phrase (got $wordCount words)"
                                    return@Button
                                }
                                if (walletPassword.length < 6) {
                                    errorMessage = "Password must be at least 6 characters"
                                    return@Button
                                }
                                if (walletPassword.isNotBlank()) {
                                    viewModel.setWalletPassword(walletPassword, enableBiometrics = true)
                                }
                                viewModel.selectNetwork(selectedSetupNetwork)
                                viewModel.importTestnetWallet(
                                    mnemonic = importMnemonic.trim(),
                                    customName = if (walletName.isBlank()) "Imported Wallet" else walletName,
                                    passphrase = bip39Passphrase.trim()
                                )
                                viewModel.refreshTestnetWallet()
                                onWalletReady()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("Import Wallet", color = SurfaceDark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                    WalletSetupStep.BACKUP_PHRASE -> {
                        val context = LocalContext.current
                        var isRevealed by remember { mutableStateOf(false) }
                        var copyNotice by remember { mutableStateOf(false) }
                        val backupWords = remember(generatedMnemonic) {
                            if (generatedMnemonic.isNotBlank()) generatedMnemonic.trim().split(Regex("\\s+")) else emptyList()
                        }

                        Text(
                            text = "Backup Recovery Phrase",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Write down these words in exact order. This recovery phrase controls your multi-network keys.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = { isRevealed = true },
                                        onTap = { if (!isRevealed) isRevealed = true }
                                    )
                                }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Your Recovery Phrase",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextMuted
                                        )
                                        if (!isRevealed) {
                                            Text(
                                                text = "Click or hold eye icon to reveal",
                                                fontSize = 10.sp,
                                                color = KaspaTea.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Mask / Click or Hold to reveal icon
                                        IconButton(
                                            onClick = { isRevealed = !isRevealed },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .pointerInput(Unit) {
                                                    detectTapGestures(
                                                        onLongPress = { isRevealed = true },
                                                        onTap = { isRevealed = !isRevealed }
                                                    )
                                                }
                                        ) {
                                            Icon(
                                                imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (isRevealed) "Mask Phrase" else "Click or Hold to Reveal Phrase",
                                                tint = if (isRevealed) KaspaTea else TextMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        TextButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Recovery Phrase", generatedMnemonic))
                                                copyNotice = true
                                            }
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = KaspaTea, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copy", color = KaspaTea, fontSize = 12.sp)
                                        }
                                    }
                                }
                                if (copyNotice) {
                                    Text(text = "Copied to clipboard!", color = KaspaTea, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                // Numbered Grid of Words
                                for (rowIdx in 0 until (backupWords.size + 1) / 2) {
                                    val leftIdx = rowIdx * 2
                                    val rightIdx = leftIdx + 1
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (leftIdx < backupWords.size) {
                                            Surface(
                                                color = SurfaceCard,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, SurfaceCardBorder.copy(alpha = 0.6f)),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { if (!isRevealed) isRevealed = true }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${leftIdx + 1}.",
                                                        fontSize = 11.sp,
                                                        color = TextMuted,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.width(22.dp)
                                                    )
                                                    Text(
                                                        text = if (isRevealed) backupWords[leftIdx] else "••••••",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = if (isRevealed) KaspaTea else TextMuted
                                                    )
                                                }
                                            }
                                        }

                                        if (rightIdx < backupWords.size) {
                                            Surface(
                                                color = SurfaceCard,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, SurfaceCardBorder.copy(alpha = 0.6f)),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { if (!isRevealed) isRevealed = true }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${rightIdx + 1}.",
                                                        fontSize = 11.sp,
                                                        color = TextMuted,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.width(22.dp)
                                                    )
                                                    Text(
                                                        text = if (isRevealed) backupWords[rightIdx] else "••••••",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = if (isRevealed) KaspaTea else TextMuted
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = {
                                if (walletPassword.isNotBlank()) {
                                    viewModel.setWalletPassword(walletPassword, enableBiometrics = true)
                                }
                                viewModel.selectNetwork(selectedSetupNetwork)
                                viewModel.importTestnetWallet(
                                    mnemonic = generatedMnemonic,
                                    customName = if (walletName.isBlank()) "My Wallet" else walletName,
                                    passphrase = bip39Passphrase.trim()
                                )
                                viewModel.refreshTestnetWallet()
                                onWalletReady()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("I Have Saved My Recovery Phrase", color = SurfaceDark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }
    }
}
