package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.filled.Fingerprint
import com.example.utils.BiometricAuthHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.KaspaTea
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.AppTab
import com.example.viewmodel.DecentralViewModel
import com.example.network.kaspa.KbKcc20TransferBuilder
import java.math.BigDecimal

private const val KB_CONTRACT_ID = "b75a6e90f569965bdeec23c460ebd59e0f0445d8aa8f5d4330fe15c66a47b4d8"

/** Official $KB metadata logo used across the wallet when live token.imageUrl is absent. */
private const val KB_FALLBACK_LOGO_URL =
    "https://storage.googleapis.com/kaspa-rocket-prod-uploads/7c70c6f4-faa9-4d84-ba81-62553168f1a9.png"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DappsScreen(
    viewModel: DecentralViewModel,
    modifier: Modifier = Modifier
) {
    var recipient by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val amountDouble = amount.toDoubleOrNull() ?: 0.0
    val scope = rememberCoroutineScope()
    var isBroadcasting by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var broadcastSuccessTxId by remember { mutableStateOf<String?>(null) }
    var broadcastErrorMessage by remember { mutableStateOf<String?>(null) }
    var lastSentAmount by remember { mutableStateOf(0.0) }
    var lastSentRecipient by remember { mutableStateOf("") }
    val tokens by viewModel.kcc20Tokens.collectAsState()
    val loading by viewModel.isLoadingKcc20.collectAsState()
    val walletAddress by viewModel.mainnetAddress.collectAsState()
    val biometricsEnabled by viewModel.biometricsEnabled.collectAsState()
    val kb = tokens.firstOrNull { it.contractId.equals(KB_CONTRACT_ID, ignoreCase = true) }
    val balance = remember(kb) {
        runCatching {
            BigDecimal(kb?.balanceRaw ?: "0")
                .movePointLeft(kb?.decimals ?: 0)
                .stripTrailingZeros()
                .toPlainString()
        }.getOrDefault("—")
    }
    val logoUrl = kb?.imageUrl?.takeIf { it.isNotBlank() } ?: KB_FALLBACK_LOGO_URL
    val context = LocalContext.current
    val isBiometricAvailable = remember(context) { BiometricAuthHelper.isDeviceLockAvailable(context) }
    val useBiometrics = biometricsEnabled && isBiometricAvailable

    BackHandler { viewModel.setTab(AppTab.KASPA_WALLET) }
    LaunchedEffect(Unit) { viewModel.refreshKcc20Tokens() }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("dapps_screen"),
        containerColor = ObsidianBg,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(ObsidianBg)
                    .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.setTab(AppTab.KASPA_WALLET) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to wallet", tint = TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Kaspa Browser", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("KCC-20 • \$KB command center", color = TextSecondary, fontSize = 12.sp)
                }
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(SurfaceDark)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.size(7.dp).clip(CircleShape).background(KaspaTea))
                    Text(" Mainnet", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).navigationBarsPadding()
                .background(ObsidianBg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, KaspaTea.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KbMetadataIcon(logoUrl = logoUrl)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("\$KB", color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("Kaspa Browser • KCC-20", color = TextSecondary, fontSize = 12.sp)
                        }
                        IconButton(onClick = { viewModel.refreshKcc20Tokens() }) {
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = KaspaTea, strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, contentDescription = "Refresh token data", tint = KaspaTea)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MetricColumn("WALLET BALANCE", "$balance KB")
                        MetricColumn("LIVE PRICE", kb?.marketPriceKas?.let { "$it KAS" } ?: "Unavailable")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("USD price", color = TextMuted, fontSize = 11.sp)
                        Text(kb?.marketPriceUsd?.let { "\$$it" } ?: "Unavailable", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = if (kb?.marketPriceKas != null) "Price source: ${kb.marketPriceSource ?: "Kascov"}" else "Live market quote unavailable — no estimated price is substituted.",
                        color = TextMuted,
                        fontSize = 10.5.sp
                    )
                }
            }

            Text("SEND \$KB", color = KaspaTea, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("From wallet", color = TextSecondary, fontSize = 11.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = clipKaspaAddress(walletAddress.ifBlank { "No mainnet wallet connected" }),
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (walletAddress.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa address", walletAddress))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy wallet address",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = recipient,
                        onValueChange = { recipient = it.trim() },
                        modifier = Modifier.fillMaxWidth().testTag("kb_recipient_input"),
                        label = { Text("Recipient Kaspa address") },
                        placeholder = { Text("kaspa:…") },
                        singleLine = true,
                        colors = walletFieldColors()
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { value ->
                            if (value.isEmpty() || value.matches(Regex("^\\d*(\\.\\d*)?$"))) amount = value
                        },
                        modifier = Modifier.fillMaxWidth().testTag("kb_amount_input"),
                        label = { Text("Amount (KB)") },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = walletFieldColors()
                    )
                    val isValidAmount = amountDouble > 0.0
                    val isValidRecipient = recipient.isNotBlank() && recipient.startsWith("kaspa:") && recipient.length >= 40
                    val canSubmit = isValidAmount && isValidRecipient && !isBroadcasting

                    Button(
                        onClick = { showConfirmDialog = true },
                        enabled = canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("kb_review_transaction_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KaspaTea,
                            disabledContainerColor = KaspaTea.copy(alpha = 0.28f),
                            disabledContentColor = TextMuted
                        )
                    ) {
                        if (isBroadcasting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = SurfaceDark,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = "Broadcasting KCC-20...",
                                color = SurfaceDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        } else {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (canSubmit) SurfaceDark else TextMuted
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = if (canSubmit) "Review & Send \$KB" else if (recipient.isBlank()) "Enter recipient address" else if (!isValidRecipient) "Invalid Kaspa address" else "Enter amount",
                                color = if (canSubmit) SurfaceDark else TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = KaspaTea, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("KCC-20 Covenant Engine Active", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Standard Argent KCC-1 contract assembler with BIP-340 Schnorr signature witness. Real on-chain Kaspa BlockDAG transaction execution without plain-KAS fallbacks.",
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            if (showConfirmDialog) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { if (!isBroadcasting) showConfirmDialog = false },
                    sheetState = sheetState,
                    containerColor = SurfaceDark,
                    dragHandle = { BottomSheetDefaults.DragHandle(color = KaspaTea.copy(alpha = 0.5f)) },
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(KaspaTea.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = null,
                                    tint = KaspaTea,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Confirm \$KB Transfer",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Mainnet KCC-20 Covenant Engine",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceCard,
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Amount to send", color = TextSecondary, fontSize = 12.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "%.3f KB".format(amountDouble),
                                    color = KaspaTea,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text("Standard: KCC-20 Covenant Token", color = TextMuted, fontSize = 11.sp)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceCard,
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("RECIPIENT", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                    Text(
                                        recipient,
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 16.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(SurfaceCardBorder)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("ESTIMATED NETWORK FEE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                    Text("~0.001 KAS • L1 BlockDAG Fee", color = TextPrimary, fontSize = 12.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(SurfaceCardBorder)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("SIGNING PROTOCOL", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                    Text("Argent KCC-1 Leader Witness (Schnorr)", color = ElectricCyan, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        if (useBiometrics) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = KaspaTea.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, KaspaTea.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Fingerprint,
                                        contentDescription = null,
                                        tint = KaspaTea,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            "Biometric Authorization Active",
                                            color = TextPrimary,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "Hardware biometric or lock screen will authorize KCC-20 signing",
                                            color = TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 16.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showConfirmDialog = false },
                                enabled = !isBroadcasting,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, SurfaceCardBorder)
                            ) {
                                Text("Cancel", color = TextSecondary, fontWeight = FontWeight.Medium)
                            }
                            Button(
                                onClick = {
                                    val targetRecipient = recipient.trim()
                                    val targetAmount = amountDouble
                                    val doBroadcast = {
                                        lastSentAmount = targetAmount
                                        lastSentRecipient = targetRecipient
                                        isBroadcasting = true
                                        showConfirmDialog = false
                                        scope.launch {
                                            val res = viewModel.executeKbKcc20Transfer(targetRecipient, targetAmount)
                                            isBroadcasting = false
                                            res.fold(
                                                onSuccess = { txId ->
                                                    broadcastSuccessTxId = txId
                                                    amount = ""
                                                    recipient = ""
                                                },
                                                onFailure = { err ->
                                                    broadcastErrorMessage = err.message ?: "Transaction failed"
                                                }
                                            )
                                        }
                                    }

                                    if (useBiometrics) {
                                        BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                                            context = context,
                                            title = "Authorize \$KB Transfer",
                                            subtitle = "Authenticate to sign KCC-20 transaction (%.3f KB)".format(targetAmount),
                                            onSuccess = {
                                                doBroadcast()
                                            },
                                            onError = { err ->
                                                broadcastErrorMessage = "Biometric authorization canceled or failed: $err"
                                            }
                                        )
                                    } else {
                                        doBroadcast()
                                    }
                                },
                                enabled = !isBroadcasting,
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = KaspaTea,
                                    contentColor = SurfaceDark
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isBroadcasting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = SurfaceDark,
                                        strokeWidth = 2.dp
                                    )
                                } else if (useBiometrics) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Authorize & Sign", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Text("Sign & Broadcast", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            if (broadcastSuccessTxId != null) {
                val txId = broadcastSuccessTxId!!
                val successSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val timeString = remember {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                }
                ModalBottomSheet(
                    onDismissRequest = { broadcastSuccessTxId = null },
                    sheetState = successSheetState,
                    containerColor = SurfaceDark,
                    dragHandle = { BottomSheetDefaults.DragHandle(color = KaspaTea.copy(alpha = 0.5f)) },
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(KaspaTea.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = KaspaTea,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Transfer Broadcasted!",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Live Kaspa Mainnet BlockDAG Confirmed",
                                    color = KaspaTea,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceCard,
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Amount Sent", color = TextSecondary, fontSize = 12.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "%.3f KB".format(lastSentAmount),
                                    color = KaspaTea,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text("KCC-20 Covenant Token • Mainnet", color = TextMuted, fontSize = 11.sp)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceCard,
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("TRANSACTION ID", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                        Row(
                                            modifier = Modifier.clickable {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Transaction ID", txId))
                                                Toast.makeText(context, "Transaction ID copied", Toast.LENGTH_SHORT).show()
                                            },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = KaspaTea, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Copy", color = KaspaTea, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    Text(
                                        txId,
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 16.sp
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(SurfaceCardBorder)
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("RECIPIENT", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                    Text(
                                        lastSentRecipient,
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 16.sp
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(SurfaceCardBorder)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("STATUS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(6.dp).background(KaspaTea, CircleShape))
                                            Spacer(Modifier.width(5.dp))
                                            Text("Broadcasted", color = KaspaTea, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text("TIMESTAMP", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                        Text(timeString, color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 16.dp)
                        ) {
                            OutlinedButton(
                                onClick = { broadcastSuccessTxId = null },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, SurfaceCardBorder)
                            ) {
                                Text("Close", color = TextSecondary, fontWeight = FontWeight.Medium)
                            }
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kascov.io/tx/$txId"))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = KaspaTea,
                                    contentColor = SurfaceDark
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("View on Explorer", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (broadcastErrorMessage != null) {
                val errorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { broadcastErrorMessage = null },
                    sheetState = errorSheetState,
                    containerColor = SurfaceDark,
                    dragHandle = { BottomSheetDefaults.DragHandle(color = ElectricCyan.copy(alpha = 0.5f)) },
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(ElectricCyan.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Transfer Notice", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("Kaspa Mainnet Execution", color = TextMuted, fontSize = 11.sp)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceCard,
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                broadcastErrorMessage!!,
                                color = TextSecondary,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        Button(
                            onClick = { broadcastErrorMessage = null },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = TextPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(bottom = 16.dp)
                        ) {
                            Text("Dismiss", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.setTab(AppTab.SI_AGENT) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = KaspaTea)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                Text("  Open SI Agent", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Live $KB metadata icon (token.imageUrl) with official fallback logo — not a static Canvas glyph. */
@Composable
private fun KbMetadataIcon(logoUrl: String) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF101A1C))
            .border(1.dp, KaspaTea.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(logoUrl)
                .crossfade(true)
                .build(),
            contentDescription = "\$KB metadata logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("KB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = KaspaTea)
                }
            },
            error = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("KB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = KaspaTea)
                }
            }
        )
    }
}

/** Middle-clip long kaspa: hex addresses so they fit one line without wrapping overflow. */
private fun clipKaspaAddress(address: String): String {
    if (address.length <= 28) return address
    val head = address.take(14)
    val tail = address.takeLast(10)
    return "$head…$tail"
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
        Text(value, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun walletFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = SurfaceCard,
    unfocusedContainerColor = SurfaceCard,
    focusedBorderColor = KaspaTea,
    unfocusedBorderColor = SurfaceCardBorder,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = KaspaTea,
    unfocusedLabelColor = TextMuted,
    cursorColor = KaspaTea
)
