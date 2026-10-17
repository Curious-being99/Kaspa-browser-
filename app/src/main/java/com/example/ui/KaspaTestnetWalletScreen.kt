package com.example.ui

import com.example.data.AccountEntity
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults

import com.example.network.kaspa.KaspaTransactionEngine
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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
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
    val isMainnet = selectedNetwork.contains("Mainnet", ignoreCase = true)

    val currentPhotoTheme by viewModel.currentPhotoTheme.collectAsState()
    val isAutoRotatePhotoTheme by viewModel.isAutoRotatePhotoTheme.collectAsState()
    var showThemeSelectorDialog by remember { mutableStateOf(false) }

    val isLocked by viewModel.isWalletLocked.collectAsState()
    val biometricsEnabled by viewModel.biometricsEnabled.collectAsState()

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
        } else if (showCompoundDialog) {
            showCompoundDialog = false
        } else if (selectedTransactionForDetails != null) {
            selectedTransactionForDetails = null
        } else if (showConfirmSendDialog) {
            showConfirmSendDialog = false
        } else if (showRecoveryScreen) {
            showRecoveryScreen = false
        } else if (showFullWalletSetup) {
            showFullWalletSetup = false
        } else if (showSendScreen) {
            showSendScreen = false
        } else if (showReceiveScreen) {
            showReceiveScreen = false
        } else if (showNetworkSwitchScreen) {
            showNetworkSwitchScreen = false
        } else if (showManageScreen) {
            showManageScreen = false
        } else {
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showAccountsDialog = true }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = selectedNetwork,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch Wallet",
                                    tint = KaspaTea,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            
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
                    Text(
                        text = "%.4f KAS".format(walletState.balanceKas),
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
                    isMainnet = isMainnet
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
                onSendClick = { showConfirmSendDialog = true },
                onClose = { showSendScreen = false }
            )
        }

        // Receive Screen Overlay
        if (showReceiveScreen) {
            ReceiveScreenOverlay(
                address = testnetAddress,
                isMainnet = isMainnet,
                onOpenFaucet = { viewModel.openTestnetFaucet(context) },
                onClose = { showReceiveScreen = false }
            )
        }

        // Manage Settings Screen Overlay
        if (showManageScreen) {
            ManageScreenOverlay(
                viewModel = viewModel,
                activeAccountHandle = activeAccount?.handle ?: "Native Testnet Account",
                testnetAddress = testnetAddress,
                allAccountsCount = allAccounts.size,
                onViewSeed = { showRecoveryScreen = true },
                onSignOut = { showSignOutConfirmDialog = true },
                onOpenNetworkSwitch = {
                    showManageScreen = false
                    showNetworkSwitchScreen = true
                },
                onClose = { showManageScreen = false }
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
                onClose = { showRecoveryScreen = false }
            )
        }

        // Full-Page Setup Flow (Create / Import Wallet)
        if (showFullWalletSetup) {
            WalletSetupFlow(
                viewModel = viewModel,
                onBackToBrowser = { showFullWalletSetup = false },
                initialStep = fullSetupInitialStep,
                modifier = modifier
            )
        }

        // Accounts Dialog
        if (showAccountsDialog) {
            WalletAccountsDialog(
                allAccounts = allAccounts,
                activeAccount = activeAccount,
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
                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = seedPhrase,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                color = KaspaTea,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
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

    // Sign Out Confirmation Dialog (Professional with Password Protection)
    if (showSignOutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSignOutConfirmDialog = false 
                signOutPasswordInput = ""
                signOutError = null
            },
            containerColor = SurfaceCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = RedTamper)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out & Disconnect", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        text = "To sign out and disconnect your wallet from this session, please enter your security password to confirm.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = signOutPasswordInput,
                        onValueChange = { signOutPasswordInput = it },
                        label = { Text("Wallet Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth(0.92f)
                            .align(Alignment.CenterHorizontally),
                        isError = signOutError != null,
                        supportingText = {
                            if (signOutError != null) Text(signOutError!!, color = RedTamper)
                        },
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
                        if (viewModel.unlockWalletWithPassword(signOutPasswordInput)) {
                            showSignOutConfirmDialog = false
                            signOutPasswordInput = ""
                            signOutError = null
                            viewModel.signOutActiveAccount()
                            viewModel.setTab(AppTab.BROWSER_GATEWAY)
                        } else {
                            signOutError = "Incorrect wallet password."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedTamper)
                ) {
                    Text("Confirm Sign Out", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showSignOutConfirmDialog = false 
                    signOutPasswordInput = ""
                    signOutError = null
                }) {
                    Text("Cancel", color = TextMuted)
                }
            }
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
    isMainnet: Boolean
) {
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
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = tabTextColor
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "$count",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = badgeTextColor
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
                        1 -> "You have not broadcasted any sent transactions from this wallet yet."
                        2 -> "No incoming transactions recorded yet for this wallet address."
                        else -> if (isMainnet) "Your Kaspa mainnet wallet is ready for transactions." else "Your Kaspa wallet is ready. Request test coins from the faucet to begin."
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
                                Text("Send KAS", color = Color.White, fontWeight = FontWeight.Bold)
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
                                Text("Receive KAS", color = SurfaceDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> {
                        if (!isMainnet) {
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
                                Text("Receive KAS", color = SurfaceDark, fontWeight = FontWeight.Bold)
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
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            TransactionCard(tx = tx, onClick = { onOpenTx(tx) })
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
    onClick: () -> Unit
) {
    val isReceive = tx.type == "RECEIVED"
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
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isReceive) KaspaTea.copy(alpha = 0.12f) else RedTamper.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isReceive) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade,
                            contentDescription = tx.type,
                            tint = if (isReceive) KaspaTea else RedTamper,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = if (isReceive) "Received KAS" else "Sent KAS",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isReceive) KaspaTea else RedTamper
                        )
                        Text(
                            text = dateStr,
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (isReceive) "+" else "-"}%.4f KAS".format(tx.amountKas),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isReceive) KaspaTea else RedTamper
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
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Extended Details: TxID & Fee / Counterparty
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = SurfaceCardBorder.copy(alpha = 0.25f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tx: ${tx.txId.take(10)}...${tx.txId.takeLast(6)}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
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
    onRefreshFeeCondition: () -> Unit
) {
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
                        text = "Fee & Mass Calculator",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fee options use RPC rate hints, but never fall below the Toccata minimum-standard rate.
            // The final fee is rechecked against the actual selected UTXOs and signed transaction.
            val normRate = maxOf(KaspaTransactionEngine.TOCCATA_MINIMUM_STANDARD_FEE_RATE_SOMPI_PER_GRAM, networkCondition.normalFeerate)
            val fastRate = maxOf(normRate * 2, networkCondition.priorityFeerate)
            // UTXOs are not selected on this screen, so a one-input/two-output
            // fee amount would be misleading. Show rates until the send planner
            // has selected the real inputs and determined recipient/change outputs.

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
    onSendClick: () -> Unit
) {
    val amountNum = amountInput.toDoubleOrNull() ?: 0.0
    val isRecipientEntered = recipientInput.trim().isNotBlank()
    val cleanRecipient = recipientInput.trim().lowercase()
    val isTestnetWallet = senderAddress.startsWith("kaspatest:")
    val expectedPrefix = if (isTestnetWallet) "kaspatest" else "kaspa"
    val isPrefixMismatch = if (isTestnetWallet) cleanRecipient.startsWith("kaspa:") else cleanRecipient.startsWith("kaspatest:")
    val isRealAddressValid = com.example.viewmodel.DecentralViewModel.isValidKaspaAddress(cleanRecipient) ||
        com.example.network.CryptoUtils.isValidKaspaAddress(cleanRecipient, expectedPrefix)
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
                label = { Text("Recipient Address") },
                placeholder = { Text(if (isTestnetWallet) "kaspatest:q..." else "kaspa:q...") },
                supportingText = {
                    when {
                        isAddressSuccess -> Text("✓ Valid Kaspa address", color = KaspaTea, fontWeight = FontWeight.SemiBold)
                        isPrefixMismatch -> Text("Network mismatch: Current wallet is on ${if (isTestnetWallet) "Testnet 10 (kaspatest:)" else "Mainnet (kaspa:)"}", color = RedTamper)
                        isAddressError -> Text("Invalid Kaspa address (must start with ${if (isTestnetWallet) "kaspatest:" else "kaspa:"})", color = RedTamper)
                        else -> Text("Only valid Kaspa address", color = TextMuted)
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
                label = { Text("Amount (KAS)") },
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
                        isInsufficientBalance -> Text("Insufficient balance: Required %.6f KAS exceeds available %.6f KAS".format(amountNum + breakdown.feeKas, balanceKas), color = RedTamper)
                        amountInput.isNotBlank() && amountNum <= 0.0 -> Text("Amount must be greater than 0 KAS", color = RedTamper)
                        else -> Text("Available: %.8f KAS".format(balanceKas), color = TextMuted)
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
                onRefreshFeeCondition = onRefreshFeeCondition
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
                    Text("Send KAS", color = SurfaceDark, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReceiveTabContent(
    address: String,
    isMainnet: Boolean,
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
                text = "Your Kaspa Address",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isMainnet) "Scan or copy this address to receive Kaspa." else "Scan or copy this address to receive test coins.",
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
    val networks = listOf("Kaspa Mainnet", "Kaspa Testnet 10", "Kaspa Testnet 11")

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = SurfaceCard,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 8.dp)
        ) {
            Text("Network Switch", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                "Choose the network this wallet displays and uses.",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )

            networks.forEach { name ->
                val isSelected = selectedNetwork == name
                Surface(
                    onClick = { if (!isSelected) onSelectNetwork(name) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) KaspaTea.copy(alpha = 0.12f) else SurfaceCard,
                    border = BorderStroke(1.dp, if (isSelected) KaspaTea else SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { if (!isSelected) onSelectNetwork(name) },
                            colors = RadioButtonDefaults.colors(selectedColor = KaspaTea)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isSelected) KaspaTea else TextPrimary)
                            Text(
                                when {
                                    name.contains("Mainnet") -> "Production network • kaspa:"
                                    name.contains("11") -> "Test network 11 • kaspatest:"
                                    else -> "Test network 10 • kaspatest:"
                                },
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        if (isSelected) Text("ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = KaspaTea)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ManageTabContent(
    viewModel: DecentralViewModel,
    activeAccountHandle: String,
    testnetAddress: String,
    allAccountsCount: Int,
    onOpenRecovery: () -> Unit,
    onSignOut: () -> Unit,
    onOpenNetworkSwitch: () -> Unit
) {
    val selectedNetwork by viewModel.selectedNetwork.collectAsState()
    val selectedExplorerUrl by viewModel.selectedExplorerUrl.collectAsState()
    val selectedRpcEndpoint = when {
        selectedNetwork.contains("Mainnet", ignoreCase = true) -> "https://api.kaspa.org"
        selectedNetwork.contains("11", ignoreCase = true) -> "https://api-tn11.kaspa.org"
        else -> "https://api-tn10.kaspa.org"
    }
    val selectedAddressPrefix = if (selectedNetwork.contains("Mainnet", ignoreCase = true)) "kaspa:" else "kaspatest:"
    var showNetworkDetailsSheet by remember { mutableStateOf(false) }
    var showRecoverySheet by remember { mutableStateOf(false) }
    var showSignOutSheet by remember { mutableStateOf(false) }

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
                onClick = { showNetworkDetailsSheet = true }
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
                onClick = { showRecoverySheet = true }
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
                onClick = { showSignOutSheet = true }
            )
        }
    }
    if (showNetworkDetailsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNetworkDetailsSheet = false },
            containerColor = SurfaceCard,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Network Details", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Current wallet connection", fontSize = 13.sp, color = TextSecondary)
                HorizontalDivider(color = SurfaceCardBorder)
                DetailRow(label = "Network", value = selectedNetwork)
                DetailRow(label = "Address Prefix", value = selectedAddressPrefix)
                DetailRow(label = "API Endpoint", value = selectedRpcEndpoint)
                DetailRow(label = "Finality", value = "Instant BlockDAG (10 BPS)")
                DetailRow(label = "Explorer", value = selectedExplorerUrl)
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    if (showRecoverySheet) {
        ModalBottomSheet(
            onDismissRequest = { showRecoverySheet = false },
            containerColor = SurfaceCard,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = KaspaTea, modifier = Modifier.size(28.dp))
                Text("Wallet Recovery", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "Your recovery phrase controls access to your wallet. Only reveal it in a private place and never share it with anyone.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Button(
                    onClick = {
                        showRecoverySheet = false
                        onOpenRecovery()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTea)
                ) {
                    Text("Continue to Recovery", color = SurfaceDark, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showSignOutSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSignOutSheet = false },
            containerColor = SurfaceCard,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = RedTamper, modifier = Modifier.size(28.dp))
                Text("Sign Out / Disconnect", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "Your wallet will be disconnected from this session. You must confirm with your wallet password before signing out.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Button(
                    onClick = {
                        showSignOutSheet = false
                        onSignOut()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RedTamper)
                ) {
                    Text("Continue to Password Confirmation", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = { showSignOutSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = TextSecondary)
                }
                Spacer(modifier = Modifier.height(12.dp))
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
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
    initialStep: WalletSetupStep = WalletSetupStep.WELCOME,
    modifier: Modifier = Modifier
) {
    val selectedNetwork by viewModel.selectedNetwork.collectAsState()
    var step by remember(initialStep) { mutableStateOf(initialStep) }
    val effectiveWalletName = remember(selectedNetwork) {
        when {
            selectedNetwork.contains("Mainnet", ignoreCase = true) -> "Kaspa Mainnet Wallet"
            selectedNetwork.contains("11") -> "Kaspa TN11 Wallet"
            else -> "Kaspa TN10 Wallet"
        }
    }
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
                    walletPassword = walletPassword,
                    onWalletPasswordChange = { walletPassword = it },
                    passphrase = passphrase,
                    onPassphraseChange = { passphrase = it },
                    selectedNetwork = selectedNetwork,
                    onSelectNetwork = { viewModel.selectNetwork(it) },
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
                        viewModel.importTestnetWallet(generatedMnemonic, effectiveWalletName, passphrase)
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
                    walletPassword = walletPassword,
                    onWalletPasswordChange = { walletPassword = it },
                    passphrase = passphrase,
                    onPassphraseChange = { passphrase = it },
                    selectedNetwork = selectedNetwork,
                    onSelectNetwork = { viewModel.selectNetwork(it) },
                    onBack = { step = WalletSetupStep.WELCOME },
                    onImport = {
                        if (walletPassword.length >= 8) {
                            viewModel.setWalletPassword(walletPassword)
                        }
                        viewModel.importTestnetWallet(importMnemonicInput, effectiveWalletName, passphrase)
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

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            shape = CircleShape,
            color = KaspaTea.copy(alpha = 0.12f),
            border = BorderStroke(1.5.dp, KaspaTea.copy(alpha = 0.4f)),
            modifier = Modifier.size(76.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = KaspaTea,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Kaspa Wallet",
            fontSize = 25.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Fast, secure, and decentralized. Set up your wallet to start transacting.",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Feature Highlights
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    icon = Icons.Default.Public,
                    title = "Multi-Network DAG Support",
                    subtitle = "Choose between Kaspa Mainnet, Testnet 10, or Testnet 11 during setup."
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons - Always ready and enabled without sitting on network switch
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
                border = BorderStroke(1.dp, KaspaTea),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkSelectionCard(
    selectedNetwork: String,
    onSelectNetwork: (String) -> Unit
) {
    val networks = listOf("Kaspa Mainnet", "Kaspa Testnet 10", "Kaspa Testnet 11")
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, SurfaceCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = KaspaTea,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kaspa Network Zone",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (selectedNetwork.contains("Mainnet", ignoreCase = true)) KaspaTea.copy(alpha = 0.2f) else EmeraldMesh.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (selectedNetwork.contains("Mainnet", ignoreCase = true)) KaspaTea.copy(alpha = 0.6f) else EmeraldMesh.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = if (selectedNetwork.contains("Mainnet", ignoreCase = true)) "MAINNET" else "TESTNET",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedNetwork.contains("Mainnet", ignoreCase = true)) KaspaTea else EmeraldMesh,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedNetwork,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Active Network") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KaspaTea,
                        unfocusedBorderColor = SurfaceCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    networks.forEach { network ->
                        val isCurrent = network == selectedNetwork
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = network,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) KaspaTea else TextPrimary
                                    )
                                    Text(
                                        text = if (network.contains("Mainnet")) "Address prefix: kaspa: • Real DAG funds" else "Address prefix: kaspatest: • Test tokens",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            },
                            onClick = {
                                onSelectNetwork(network)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateDetailsSetupView(
    walletPassword: String,
    onWalletPasswordChange: (String) -> Unit,
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    selectedNetwork: String,
    onSelectNetwork: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    var isPasswordVisible by remember { mutableStateOf(false) }
    val isPasswordValid = walletPassword.length >= 8

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Text("Configure Wallet - $selectedNetwork", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Step 1 of 2: Network & Security Credentials", fontSize = 12.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Kaspa Network Zone
            NetworkSelectionCard(
                selectedNetwork = selectedNetwork,
                onSelectNetwork = onSelectNetwork
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(0.92f)
                    .align(Alignment.CenterHorizontally)
                    .testTag("setup_wallet_password_input"),
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
            enabled = isPasswordValid,
            colors = ButtonDefaults.buttonColors(containerColor = KaspaTea, disabledContainerColor = SurfaceCardBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("setup_continue_backup_button")
        ) {
            Text("Generate Recovery Phrase", color = if (isPasswordValid) SurfaceDark else TextMuted, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
    var isMnemonicVisible by remember(mnemonic) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Recovery phrase", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        IconButton(
                            onClick = { isMnemonicVisible = !isMnemonicVisible },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isMnemonicVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isMnemonicVisible) "Hide recovery phrase" else "Reveal recovery phrase",
                                tint = KaspaTea
                            )
                        }
                    }
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
                                            text = if (isMnemonicVisible) word else "••••••",
                                            fontSize = 12.sp,
                                            color = if (isMnemonicVisible) TextPrimary else TextMuted,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Clip
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isMnemonicVisible) {
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
            Text("Complete Setup", color = if (hasConfirmed) SurfaceDark else TextMuted, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun ImportPhraseSetupView(
    mnemonicInput: String,
    onMnemonicChange: (String) -> Unit,
    walletPassword: String,
    onWalletPasswordChange: (String) -> Unit,
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    selectedNetwork: String,
    onSelectNetwork: (String) -> Unit,
    onBack: () -> Unit,
    onImport: () -> Unit
) {
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isMnemonicVisible by remember { mutableStateOf(false) }
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
                    Text(
                        if (selectedNetwork.isNotBlank()) "Import Existing Wallet - $selectedNetwork" else "Import Existing Wallet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text("Enter 12 or 24-word recovery phrase", fontSize = 12.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Kaspa Network Zone
            NetworkSelectionCard(
                selectedNetwork = selectedNetwork,
                onSelectNetwork = onSelectNetwork
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = mnemonicInput,
                onValueChange = onMnemonicChange,
                label = { Text("12 or 24-Word Recovery Phrase") },
                placeholder = { Text("Enter words separated by spaces") },
                visualTransformation = if (isMnemonicVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isMnemonicVisible = !isMnemonicVisible }) {
                        Icon(
                            imageVector = if (isMnemonicVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Mnemonic Visibility",
                            tint = KaspaTea
                        )
                    }
                },
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
                )
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
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(0.92f)
                    .align(Alignment.CenterHorizontally)
                    .testTag("setup_import_password_input"),
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
            Text("Import Wallet", color = if (isValidCount && isPasswordValid) SurfaceDark else TextMuted, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
    onSendClick: () -> Unit,
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
                Text("Send KAS", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
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
                onSendClick = onSendClick
            )
        }
    }
}

@Composable
private fun ReceiveScreenOverlay(
    address: String,
    isMainnet: Boolean,
    onOpenFaucet: () -> Unit,
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
                Text("Receive Address", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            
            ReceiveTabContent(
                address = address,
                isMainnet = isMainnet,
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
    onViewOnExplorer: () -> Unit,
    onClose: () -> Unit
) {
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
                text = if (isSuccess) "Your transaction has been submitted to the Kaspa BlockDAG mempool." else errorMessage,
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
                        Text("${KaspaTransactionEngine.formatKas(transaction.amountKas)} KAS", fontSize = 12.sp, color = TextPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Network fee", fontSize = 12.sp, color = TextSecondary)
                        Text("${KaspaTransactionEngine.formatKas(transaction.feeKas)} KAS", fontSize = 12.sp, color = TextPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total deduction", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("${KaspaTransactionEngine.formatKas(transaction.amountKas + transaction.feeKas)} KAS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = KaspaTea)
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
                        Text("Transaction ID", fontSize = 10.sp, color = TextMuted)
                        Text(
                            text = txId,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ElectricCyan,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onViewOnExplorer,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View on Explorer", fontWeight = FontWeight.Bold, color = SurfaceDark)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Dismiss", color = TextMuted, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
@Composable
private fun ManageScreenOverlay(
    viewModel: DecentralViewModel,
    activeAccountHandle: String,
    testnetAddress: String,
    allAccountsCount: Int,
    onViewSeed: () -> Unit,
    onSignOut: () -> Unit,
    onOpenNetworkSwitch: () -> Unit,
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
                testnetAddress = testnetAddress,
                allAccountsCount = allAccountsCount,
                onOpenRecovery = onViewSeed,
                onSignOut = onSignOut,
                onOpenNetworkSwitch = onOpenNetworkSwitch
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
    onCopyTxId: (String) -> Unit,
    onViewOnExplorer: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isReceive = tx.type == "RECEIVED"
    val dateStr = remember(tx.blockTime) {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        sdf.format(Date(if (tx.blockTime > 1000000000000L) tx.blockTime else tx.blockTime * 1000L))
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
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isReceive) "Received Transaction" else "Sent Transaction",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                }
                Surface(
                    color = if (tx.isAccepted) KaspaTea.copy(alpha = 0.12f) else AmberCentral.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (tx.isAccepted) "Accepted" else "Pending",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (tx.isAccepted) KaspaTea else AmberCentral,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = SurfaceCardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Amount", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        text = "${if (isReceive) "+" else "-"}%.8f KAS".format(tx.amountKas),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isReceive) KaspaTea else RedTamper
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Timestamp", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        text = dateStr,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }

                // Network Fee
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Network Fee", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        text = "${KaspaTransactionEngine.formatKas(tx.feeKas)} KAS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isReceive) KaspaTea else AmberCentral
                    )
                }

                // Counterparty Address
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    Text(if (isReceive) "Sender Address" else "Recipient Address", fontSize = 11.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (tx.counterpartyAddress.isNotBlank()) {
                            tx.counterpartyAddress
                        } else {
                            if (isReceive) "Kaspa BlockDAG Network" else "Self / Change Output"
                        },
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ElectricCyan
                    )
                }

                if (tx.acceptingBlueScore != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Accepting Blue Score", fontSize = 12.sp, color = TextSecondary)
                        Text(
                            text = "${tx.acceptingBlueScore}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Transaction ID (TXID)", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = tx.txId,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ElectricCyan,
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { onCopyTxId(tx.txId) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy TXID",
                                tint = KaspaTea,
                                modifier = Modifier.size(16.dp)
                            )
                        }
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
                Text("View on Explorer", color = SurfaceDark, fontWeight = FontWeight.Bold)
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
    viewModel: DecentralViewModel,
    onBack: () -> Unit,
    onBroadcastConfirmed: () -> Unit
) {
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
                                text = "%.8f KAS".format(amountKas),
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
                                        text = "Verified CashAddr",
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
                                text = "Fee Estimate & Transaction Details",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Selected Fee Rate:", fontSize = 12.sp, color = TextMuted)
                                Text(
                                    text = "$selectedSompiPerMass sompi/gram",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Estimated BlockDAG Mass:", fontSize = 12.sp, color = TextMuted)
                                Text(
                                    text = "${breakdown.totalMass} mass units",
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
                .heightIn(max = 550.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .background(TextMuted.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Wallets & Accounts", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, KaspaTea.copy(alpha = 0.4f))
                    ) {
                        Text(
                            "${allAccounts.size} Wallets",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = KaspaTea,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Select an active wallet or add a new identity:", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
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
                                modifier = Modifier.padding(12.dp),
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
                                    Column {
                                        Text(account.handle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isActive) KaspaTea else TextPrimary)
                                        Text(
                                            account.kaspaAddress.take(12) + "..." + account.kaspaAddress.takeLast(6),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextMuted
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

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = SurfaceCardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(16.dp))

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
                        modifier = Modifier.weight(1f).height(46.dp)
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
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Default.Download, null, tint = SurfaceDark, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import", color = SurfaceDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun RecoveryScreenOverlay(
    seedPhrase: String,
    viewModel: DecentralViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasPassword by viewModel.hasWalletPassword.collectAsState()
    val biometricsEnabled by viewModel.biometricsEnabled.collectAsState()

    var isRevealed by remember { mutableStateOf(!hasPassword) }
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
                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Your 12-Word Recovery Phrase",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextMuted
                                )
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
                                                modifier = Modifier.weight(1f)
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
                                                        text = words[leftIdx],
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = KaspaTea
                                                    )
                                                }
                                            }
                                        }

                                        if (rightIdx < words.size) {
                                            Surface(
                                                color = SurfaceCard,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, SurfaceCardBorder.copy(alpha = 0.6f)),
                                                modifier = Modifier.weight(1f)
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
                                                        text = words[rightIdx],
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = KaspaTea
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
