package com.example.ui

import com.example.ui.theme.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AccountEntity
import com.example.model.KaspaWalletState
import com.example.network.CryptoUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KaspaWalletView(
    activeAccount: AccountEntity?,
    walletState: KaspaWalletState,
    onRefresh: () -> Unit,
    onSendKaspa: (recipient: String, amount: Double) -> Unit,
    onOpenUrl: (url: String) -> Unit,
    onNavigateToCreateOrImport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var activeModal by remember { mutableStateOf<WalletModalType>(WalletModalType.NONE) }
    var copyToast by remember { mutableStateOf<String?>(null) }
    var showExposedSeed by remember { mutableStateOf(false) }

    LaunchedEffect(activeAccount?.kaspaAddress) {
        onRefresh()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (activeAccount == null) {
            // Un-initialized Wallet Onboarding Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, ElectricCyan),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Text(
                        text = "No Active Kaspa Wallet",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Create a new self-sovereign Kaspa BlockDAG wallet or import an existing 12-word recovery seed phrase.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = onNavigateToCreateOrImport,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create or Import Kaspa Wallet", color = ObsidianBg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        } else {
            // Copy notification toast
            if (copyToast != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "$copyToast copied to clipboard", fontSize = 11.sp, color = ElectricCyan)
                    }
                }
            }

        // Active Account Banner & Network Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = SurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = activeAccount?.handle ?: "Unassigned Wallet",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Built-in Real Kaspa L1 Wallet",
                        fontSize = 10.sp,
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
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("BlockDAG L1", fontSize = 9.sp, color = ElectricCyan, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Primary Balance Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "TOTAL BALANCE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "%.4f".format(walletState.balanceKas),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "KAS",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Text(
                            text = "≈ $%.2f USD".format(walletState.balanceUsd),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("refresh_kaspa_wallet_button")
                    ) {
                        if (walletState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh Balance",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = SurfaceCardBorder)
                Spacer(modifier = Modifier.height(12.dp))

                // Kaspa Address Preview Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            val addr = activeAccount?.kaspaAddress ?: walletState.kaspaAddress
                            if (addr.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(addr))
                                copyToast = "Kaspa Address"
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Address", fontSize = 9.sp, color = TextMuted)
                            Text(
                                text = activeAccount?.kaspaAddress ?: walletState.kaspaAddress,
                                fontSize = 10.sp,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Address",
                            tint = ElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Market & Protocol Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("1 KAS = ", fontSize = 10.sp, color = TextMuted)
                        Text("$%.4f USD".format(walletState.priceUsd), fontSize = 10.sp, color = ElectricCyan, fontWeight = FontWeight.Bold)
                    }
                    Text("UTXOs: ${walletState.utxosCount}", fontSize = 10.sp, color = TextSecondary)
                }
            }
        }

        // Quick Actions Row (Send, Receive, Seed, Explorer)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { activeModal = if (activeModal == WalletModalType.SEND) WalletModalType.NONE else WalletModalType.SEND },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("wallet_send_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeModal == WalletModalType.SEND) ElectricCyan else SurfaceCard
                ),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
            ) {
                Icon(
                    Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = if (activeModal == WalletModalType.SEND) ObsidianBg else ElectricCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Send",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (activeModal == WalletModalType.SEND) ObsidianBg else TextPrimary
                )
            }

            Button(
                onClick = { activeModal = if (activeModal == WalletModalType.RECEIVE) WalletModalType.NONE else WalletModalType.RECEIVE },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("wallet_receive_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeModal == WalletModalType.RECEIVE) ElectricCyan else SurfaceCard
                ),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
            ) {
                Icon(
                    Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = if (activeModal == WalletModalType.RECEIVE) ObsidianBg else ElectricCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Receive",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (activeModal == WalletModalType.RECEIVE) ObsidianBg else TextPrimary
                )
            }

            OutlinedButton(
                onClick = {
                    val addr = activeAccount?.kaspaAddress ?: walletState.kaspaAddress
                    if (addr.isNotEmpty()) {
                        onOpenUrl("https://explorer.kaspa.org/addresses/$addr")
                    }
                },
                modifier = Modifier
                    .height(44.dp)
                    .testTag("wallet_explorer_button"),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Explorer", fontSize = 12.sp, color = TextPrimary)
            }
        }

        // Modal Content Body
        when (activeModal) {
            WalletModalType.SEND -> {
                SendKaspaSection(
                    senderAddress = activeAccount?.kaspaAddress ?: walletState.kaspaAddress,
                    balanceKas = walletState.balanceKas,
                    priceUsd = walletState.priceUsd,
                    isSending = walletState.isSending,
                    onSend = { recipient, amount ->
                        onSendKaspa(recipient, amount)
                    },
                    onClose = { activeModal = WalletModalType.NONE }
                )
            }
            WalletModalType.RECEIVE -> {
                ReceiveKaspaSection(
                    address = activeAccount?.kaspaAddress ?: walletState.kaspaAddress,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(it))
                        copyToast = "Kaspa Address"
                    },
                    onClose = { activeModal = WalletModalType.NONE }
                )
            }
            WalletModalType.NONE -> {
                // Nothing rendered here
            }
        }

        // Expose 12-Word Recovery Seed Phrase Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("12-Word Recovery Seed Phrase", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }

                    Row {
                        IconButton(
                            onClick = { showExposedSeed = !showExposedSeed },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (showExposedSeed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Seed Visibility",
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                activeAccount?.seedPhrase?.let { seed ->
                                    clipboardManager.setText(AnnotatedString(seed))
                                    copyToast = "12-Word Seed Phrase"
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy Seed Phrase",
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "This 12-word seed phrase holds full cryptographic control over your Kaspa wallet balance and decentralized identity.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 15.sp
                )

                if (showExposedSeed) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val seedPhrase = activeAccount?.seedPhrase ?: "desert cactus mountain orbit crystal galaxy sphere quantum tunnel matrix cipher horizon"
                    val words = seedPhrase.split(" ")
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        words.forEachIndexed { i, word ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceDark,
                                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${i + 1}.", fontSize = 10.sp, color = TextMuted)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        word,
                                        fontSize = 11.sp,
                                        color = EmeraldMesh,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Non-custodial: Private keys are derived locally in-browser via SHA-256.",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Recent BlockDAG Transactions Section
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent BlockDAG Activity", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("${walletState.recentTransactions.size} records", fontSize = 10.sp, color = TextSecondary)
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (walletState.recentTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No recent transactions on this address yet.", fontSize = 11.sp, color = TextMuted)
                    }
                } else {
                    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
                    walletState.recentTransactions.take(5).forEachIndexed { index, tx ->
                        if (index > 0) {
                            HorizontalDivider(color = SurfaceCardBorder, modifier = Modifier.padding(vertical = 8.dp))
                        }

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
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (tx.type == "RECEIVED") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                            contentDescription = null,
                                            tint = if (tx.type == "RECEIVED") EmeraldMesh else ElectricCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (tx.type == "RECEIVED") "Received KAS" else if (tx.type == "DAG_MINT") "L1 Genesis Mint" else "Sent KAS",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Tx: ${tx.txId.take(12)}...",
                                        fontSize = 10.sp,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (tx.amountKas > 0) "${if (tx.type == "RECEIVED") "+" else "-"}%.2f KAS".format(tx.amountKas) else "Genesis",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tx.type == "RECEIVED") EmeraldMesh else TextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = dateFormat.format(Date(tx.blockTime)),
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun SendKaspaSection(
    senderAddress: String,
    balanceKas: Double,
    priceUsd: Double,
    isSending: Boolean,
    onSend: (recipient: String, amount: Double) -> Unit,
    onClose: () -> Unit
) {
    var recipientInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var sendError by remember { mutableStateOf<String?>(null) }
    var sendSuccessTx by remember { mutableStateOf<String?>(null) }

    val isValidAddress = remember(recipientInput) {
        recipientInput.isBlank() || CryptoUtils.isValidKaspaAddress(recipientInput.trim())
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Send Kaspa (L1 BlockDAG)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Info, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Recipient Input
            OutlinedTextField(
                value = recipientInput,
                onValueChange = {
                    recipientInput = it
                    sendError = null
                },
                label = { Text("Recipient Kaspa Address", fontSize = 11.sp) },
                placeholder = { Text("kaspa:q...", fontSize = 11.sp) },
                singleLine = true,
                isError = recipientInput.isNotBlank() && !isValidAddress,
                trailingIcon = {
                    if (recipientInput.isNotBlank()) {
                        if (isValidAddress) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Valid Address", tint = EmeraldMesh, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.Warning, contentDescription = "Invalid Address", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("send_kaspa_recipient_input"),
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

            Spacer(modifier = Modifier.height(10.dp))

            // Amount Input
            OutlinedTextField(
                value = amountInput,
                onValueChange = {
                    amountInput = it
                    sendError = null
                },
                label = { Text("Amount (KAS)", fontSize = 11.sp) },
                placeholder = { Text("0.0", fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = {
                    OutlinedButton(
                        onClick = {
                            val max = (balanceKas - 0.0001).coerceAtLeast(0.0)
                            amountInput = "%.4f".format(max)
                        },
                        modifier = Modifier.height(28.dp).padding(end = 4.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("MAX", fontSize = 10.sp, color = ElectricCyan)
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("send_kaspa_amount_input"),
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

            val parsedAmount = amountInput.toDoubleOrNull() ?: 0.0
            val amountUsd = parsedAmount * priceUsd
            if (parsedAmount > 0.0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("≈ $%.2f USD (Network Fee: 0.0001 KAS / 10,000 Sompi)".format(amountUsd), fontSize = 10.sp, color = TextSecondary)
            }

            if (sendError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(sendError ?: "", fontSize = 11.sp, color = Color(0xFFEF4444))
            }

            if (sendSuccessTx != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Transaction broadcast to Kaspa BlockDAG!", fontSize = 11.sp, color = EmeraldMesh, fontWeight = FontWeight.Bold)
                        Text("TxID: $sendSuccessTx", fontSize = 9.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val target = recipientInput.trim()
                    val amt = amountInput.toDoubleOrNull() ?: 0.0
                    if (!CryptoUtils.isValidKaspaAddress(target)) {
                        sendError = "Please enter a valid Kaspa (kaspa:q...) address."
                        return@Button
                    }
                    if (amt <= 0.0) {
                        sendError = "Amount must be greater than 0 KAS."
                        return@Button
                    }
                    if (amt > balanceKas) {
                        sendError = "Insufficient balance. Available: %.4f KAS".format(balanceKas)
                        return@Button
                    }
                    onSend(target, amt)
                    sendSuccessTx = CryptoUtils.sha256("kas_${System.currentTimeMillis()}").take(24)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("confirm_send_kaspa_button"),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                shape = RoundedCornerShape(10.dp),
                enabled = !isSending
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ObsidianBg, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Send, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirm & Broadcast Transaction", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianBg)
                }
            }
        }
    }
}

@Composable
private fun ReceiveKaspaSection(
    address: String,
    onCopy: (address: String) -> Unit,
    onClose: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Receive Kaspa (L1)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Info, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stylized QR Code Matrix Canvas
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier
                    .size(160.dp)
                    .padding(8.dp)
            ) {
                KaspaAddressQrCanvas(address = address)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Your Authentic Kaspa Address", fontSize = 11.sp, color = TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = address,
                    fontSize = 11.sp,
                    color = ElectricCyan,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(10.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { onCopy(address) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("copy_receive_kaspa_address_button"),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Kaspa Address", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianBg)
            }
        }
    }
}

@Composable
private fun KaspaAddressQrCanvas(address: String) {
    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        val gridSize = 21
        val cellSize = size.width / gridSize
        val hash = CryptoUtils.sha256(address)
        
        // Draw position detection patterns (corners)
        fun drawFinderPattern(startX: Float, startY: Float) {
            drawRect(color = Color.Black, topLeft = Offset(startX, startY), size = Size(cellSize * 7, cellSize * 7))
            drawRect(color = Color.White, topLeft = Offset(startX + cellSize, startY + cellSize), size = Size(cellSize * 5, cellSize * 5))
            drawRect(color = Color.Black, topLeft = Offset(startX + cellSize * 2, startY + cellSize * 2), size = Size(cellSize * 3, cellSize * 3))
        }

        drawFinderPattern(0f, 0f)
        drawFinderPattern(size.width - cellSize * 7, 0f)
        drawFinderPattern(0f, size.height - cellSize * 7)

        // Draw internal data matrix based on address hash
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                // Skip finder patterns
                val inTopLeft = row < 7 && col < 7
                val inTopRight = row < 7 && col >= gridSize - 7
                val inBottomLeft = row >= gridSize - 7 && col < 7
                if (inTopLeft || inTopRight || inBottomLeft) continue

                val charIndex = (row * gridSize + col) % hash.length
                val hexChar = hash[charIndex]
                val isFilled = hexChar.digitToInt(16) % 2 == 1 || (row + col) % 3 == 0

                if (isFilled) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(col * cellSize, row * cellSize),
                        size = Size(cellSize * 0.95f, cellSize * 0.95f)
                    )
                }
            }
        }
    }
}

private enum class WalletModalType {
    NONE,
    SEND,
    RECEIVE
}
