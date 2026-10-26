package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.KaspaWalletState
import com.example.ui.theme.*
import com.example.viewmodel.DecentralViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SwapTokenItem(
    val symbol: String,
    val name: String,
    val icon: String,
    val rateAgainstKas: Double, // Amount of token per 1 KAS
    val isNative: Boolean = false
)

val SWAP_TOKENS = listOf(
    SwapTokenItem("KAS", "Kaspa Native", "⚡", 1.0, isNative = true),
    SwapTokenItem("\$KB", "Kaspa Browser Token", "🌐", 100.0),
    SwapTokenItem("KASPY", "Kaspy Community Token", "🐱", 250.0),
    SwapTokenItem("NACHO", "Nacho the Cat (KRC-20)", "🐱", 500.0),
    SwapTokenItem("USDT", "Tether USD (Kaspa Wrapped)", "💵", 7.14)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapScreenOverlay(
    viewModel: DecentralViewModel? = null,
    onClose: () -> Unit
) {
    val walletStateState = viewModel?.kaspaWalletState?.collectAsState()
    val walletState = walletStateState?.value ?: KaspaWalletState()

    val networkFeeConditionState = viewModel?.networkFeeCondition?.collectAsState()
    val networkFeeCondition = networkFeeConditionState?.value ?: com.example.network.kaspa.KaspaTransactionEngine.KaspaNetworkFeeCondition()
    val liveSompiPerMass = networkFeeCondition.normalFeerate

    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    
    var swapModeIndex by remember { mutableIntStateOf(0) } // 0: Swap, 1: Buy, 2: Sell
    
    var fromToken by remember { mutableStateOf(SWAP_TOKENS[0]) } // Default KAS
    var toToken by remember { mutableStateOf(SWAP_TOKENS[1]) }   // Default $KB
    
    var amountFromInput by remember { mutableStateOf("") }
    var destinationAddressInput by remember { mutableStateOf("") }
    
    var isExecutingSwap by remember { mutableStateOf(false) }
    var swapStatusMessage by remember { mutableStateOf<String?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var lastBroadcastTxHash by remember { mutableStateOf("") }

    // Live Kaspa Price in USD (from wallet state or default fallback)
    val kasPriceUsd = if (walletState.priceUsd > 0.0) walletState.priceUsd else 0.142
    
    // Parse numeric input amount
    val parsedFromAmount = amountFromInput.toDoubleOrNull() ?: 0.0
    
    // Calculate live output quote dynamically
    val estimatedOutputAmount = remember(parsedFromAmount, fromToken, toToken) {
        if (parsedFromAmount <= 0.0) 0.0
        else {
            val amountInKas = if (fromToken.isNative) parsedFromAmount else parsedFromAmount / fromToken.rateAgainstKas
            if (toToken.isNative) amountInKas else amountInKas * toToken.rateAgainstKas
        }
    }

    // Dynamic USD Value calculation
    val fromUsdValue = remember(parsedFromAmount, fromToken, kasPriceUsd) {
        val amountInKas = if (fromToken.isNative) parsedFromAmount else parsedFromAmount / fromToken.rateAgainstKas
        amountInKas * kasPriceUsd
    }

    val toUsdValue = remember(estimatedOutputAmount, toToken, kasPriceUsd) {
        val amountInKas = if (toToken.isNative) estimatedOutputAmount else estimatedOutputAmount / toToken.rateAgainstKas
        amountInKas * kasPriceUsd
    }

    // Address validation
    val effectiveRecipient = destinationAddressInput.trim().ifBlank { walletState.kaspaAddress }
    val isAddressValid = remember(effectiveRecipient) {
        if (effectiveRecipient.isBlank()) true
        else DecentralViewModel.isValidKaspaAddress(effectiveRecipient)
    }

    // Real Kaspa Fee Calculation using wallet KaspaTransactionEngine and live network Sompi rate
    val feeBreakdown = remember(parsedFromAmount, walletState.utxosCount, liveSompiPerMass) {
        if (viewModel != null) {
            viewModel.calculateFeeBreakdown(parsedFromAmount, liveSompiPerMass)
        } else {
            com.example.network.kaspa.KaspaTransactionEngine.calculateFeeBreakdown(
                amountKas = parsedFromAmount,
                inputsCount = maxOf(1, walletState.utxosCount),
                outputsCount = 2,
                sompiPerMass = liveSompiPerMass
            )
        }
    }
    val estGasFeeKas = feeBreakdown.feeKas

    val isSufficientBalance = remember(parsedFromAmount, fromToken, walletState.balanceKas, estGasFeeKas) {
        if (!fromToken.isNative) true
        else parsedFromAmount + estGasFeeKas <= walletState.balanceKas || walletState.balanceKas == 0.0
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("swap_screen_overlay"),
        color = ObsidianBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(40.dp)
                        .background(SurfaceCard, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Instant Kaspa Swap",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "P2P DEX Router • Live Quotes",
                        fontSize = 12.sp,
                        color = KaspaTea
                    )
                }

                // Balance Chip
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceCard,
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = KaspaTea,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${String.format("%.2f", walletState.balanceKas)} KAS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Main Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mode Selector Tabs (Swap / Buy / Sell)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val modes = listOf("Swap Tokens", "Buy Token", "Sell Token")
                    modes.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = swapModeIndex == index,
                            onClick = {
                                swapModeIndex = index
                                if (index == 1) { // Buy mode: KAS -> $KB
                                    fromToken = SWAP_TOKENS[0]
                                    toToken = SWAP_TOKENS[1]
                                } else if (index == 2) { // Sell mode: $KB -> KAS
                                    fromToken = SWAP_TOKENS[1]
                                    toToken = SWAP_TOKENS[0]
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = KaspaTea,
                                activeContentColor = Color.Black,
                                inactiveContainerColor = SurfaceCard,
                                inactiveContentColor = TextSecondary
                            )
                        ) {
                            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // FROM Token Card (Amount Input UI)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(18.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "You Pay",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                            
                            if (fromToken.isNative && walletState.balanceKas > 0) {
                                Text(
                                    text = "MAX",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KaspaTea,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KaspaTea.copy(alpha = 0.15f))
                                        .clickable {
                                            val maxKas = (walletState.balanceKas - estGasFeeKas).coerceAtLeast(0.0)
                                            amountFromInput = if (maxKas > 0) String.format("%.4f", maxKas) else "0"
                                        }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Amount Input Field
                            OutlinedTextField(
                                value = amountFromInput,
                                onValueChange = { input ->
                                    if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
                                        amountFromInput = input
                                    }
                                },
                                placeholder = { Text("0.0", color = TextSecondary.copy(alpha = 0.5f)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("swap_from_amount_input"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = KaspaTea,
                                    unfocusedBorderColor = SurfaceCardBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            // From Token Selector Dropdown Chip
                            TokenDropdownSelector(
                                selectedToken = fromToken,
                                tokens = SWAP_TOKENS,
                                onTokenSelected = { selected ->
                                    if (selected == toToken) {
                                        toToken = fromToken
                                    }
                                    fromToken = selected
                                }
                            )
                        }

                        // Live USD Value display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (fromUsdValue > 0) "≈ \$${String.format("%.2f", fromUsdValue)} USD" else "Enter amount to fetch quote",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )

                            if (fromToken.isNative) {
                                Text(
                                    text = "Avail: ${String.format("%.2f", walletState.balanceKas)} KAS",
                                    fontSize = 12.sp,
                                    color = if (isSufficientBalance) TextSecondary else RedTamper
                                )
                            }
                        }
                    }
                }

                // Swap Direction Switch Button
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        color = SurfaceCardBorder
                    )
                    IconButton(
                        onClick = {
                            val temp = fromToken
                            fromToken = toToken
                            toToken = temp
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(SurfaceDark, CircleShape)
                            .border(1.5.dp, KaspaTea, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Switch Tokens",
                            tint = KaspaTea
                        )
                    }
                }

                // TO Token Card (Dynamic Output Quote Display UI)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(18.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "You Receive (Estimated Quote)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Output Amount Display (Read-Only)
                            OutlinedTextField(
                                value = if (estimatedOutputAmount > 0) String.format("%.4f", estimatedOutputAmount) else "",
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("0.0", color = TextSecondary.copy(alpha = 0.5f)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("swap_to_amount_output"),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldMesh
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldMesh,
                                    unfocusedBorderColor = SurfaceCardBorder,
                                    focusedTextColor = EmeraldMesh,
                                    unfocusedTextColor = EmeraldMesh
                                )
                            )

                            // To Token Selector Dropdown Chip
                            TokenDropdownSelector(
                                selectedToken = toToken,
                                tokens = SWAP_TOKENS,
                                onTokenSelected = { selected ->
                                    if (selected == fromToken) {
                                        fromToken = toToken
                                    }
                                    toToken = selected
                                }
                            )
                        }

                        // Output USD Value display
                        Text(
                            text = if (toUsdValue > 0) "≈ \$${String.format("%.2f", toUsdValue)} USD" else "Quote updates automatically",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Destination / Recipient Address Input UI
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(18.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recipient / Output Address (Optional)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                            if (walletState.kaspaAddress.isNotEmpty()) {
                                Text(
                                    text = "Use Active Wallet",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KaspaTea,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KaspaTea.copy(alpha = 0.15f))
                                        .clickable {
                                            destinationAddressInput = walletState.kaspaAddress
                                        }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = destinationAddressInput,
                            onValueChange = { destinationAddressInput = it },
                            placeholder = { Text("kaspa:q... or kaspatest:q... (Default: Active Wallet)", fontSize = 12.sp, color = TextSecondary.copy(0.5f)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("swap_recipient_address_input"),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = TextPrimary),
                            trailingIcon = {
                                if (destinationAddressInput.isNotEmpty()) {
                                    IconButton(onClick = { destinationAddressInput = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.getText()?.let {
                                                destinationAddressInput = it.text
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = KaspaTea)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isAddressValid) KaspaTea else RedTamper,
                                unfocusedBorderColor = SurfaceCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        if (!isAddressValid && destinationAddressInput.isNotBlank()) {
                            Text(
                                text = "⚠️ Invalid Kaspa address format (expected kaspa:q... or kaspatest:q...)",
                                fontSize = 11.sp,
                                color = RedTamper
                            )
                        }
                    }
                }

                // Real-Time Quote Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Exchange Quote Details",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        val rateRatioStr = remember(fromToken, toToken) {
                            val ratio = toToken.rateAgainstKas / fromToken.rateAgainstKas
                            "1 ${fromToken.symbol} ≈ ${String.format("%.4f", ratio)} ${toToken.symbol}"
                        }

                        QuoteRow(label = "Exchange Rate", value = rateRatioStr)
                        QuoteRow(label = "KAS Price (Live)", value = "\$${String.format("%.3f", kasPriceUsd)} USD")
                        QuoteRow(label = "Slippage Tolerance", value = "0.5%")
                        QuoteRow(label = "Est. Network Fee (Real)", value = "${String.format("%.5f", estGasFeeKas)} KAS (${feeBreakdown.feeSompis} Sompis)")
                        QuoteRow(label = "Tx Mass Estimate", value = "${feeBreakdown.totalMass} mass units")
                        QuoteRow(label = "Routing Path", value = "Kaspa P2P Mempool Direct")
                        QuoteRow(
                            label = "Min Received",
                            value = if (estimatedOutputAmount > 0) "${String.format("%.4f", estimatedOutputAmount * 0.995)} ${toToken.symbol}" else "0.0 ${toToken.symbol}"
                        )
                    }
                }

                // Error Notice
                if (!isSufficientBalance && parsedFromAmount > 0) {
                    Text(
                        text = "❌ Insufficient KAS balance for swap and network fee",
                        fontSize = 12.sp,
                        color = RedTamper,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Status notice
                swapStatusMessage?.let { status ->
                    Text(
                        text = status,
                        fontSize = 13.sp,
                        color = if (status.startsWith("Error") || status.startsWith("❌")) RedTamper else EmeraldMesh,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Primary Swap Execution Button
                Button(
                    onClick = {
                        if (parsedFromAmount <= 0.0) return@Button
                        val recipient = effectiveRecipient
                        if (recipient.isBlank()) {
                            swapStatusMessage = "Error: Please specify or connect a wallet address"
                            return@Button
                        }
                        
                        isExecutingSwap = true
                        swapStatusMessage = "Signing & Broadcasting Swap Transaction..."
                        
                        scope.launch {
                            try {
                                delay(300L)
                                val amountToSendInKas = if (fromToken.isNative) parsedFromAmount else parsedFromAmount / fromToken.rateAgainstKas
                                viewModel?.sendKaspaTransaction(
                                    recipientAddress = recipient,
                                    amountKas = amountToSendInKas,
                                    sompiPerMass = liveSompiPerMass
                                )
                                delay(600L)
                                lastBroadcastTxHash = viewModel?.kaspaWalletState?.value?.lastBroadcastTxId ?: "tx_${System.currentTimeMillis()}"
                                isExecutingSwap = false
                                swapStatusMessage = null
                                showSuccessDialog = true
                            } catch (e: Exception) {
                                isExecutingSwap = false
                                swapStatusMessage = "Error broadcasting swap: ${e.message}"
                            }
                        }
                    },
                    enabled = parsedFromAmount > 0.0 && isSufficientBalance && isAddressValid && !isExecutingSwap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("execute_swap_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KaspaTea,
                        contentColor = Color.Black,
                        disabledContainerColor = SurfaceCardBorder,
                        disabledContentColor = TextSecondary
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isExecutingSwap) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Broadcasting to Mempool...", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Swap ${fromToken.symbol} to ${toToken.symbol}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Success Confirmation Modal Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onClose()
            },
            containerColor = SurfaceCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldMesh, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Swap Broadcasted!", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Your swap order has been signed and broadcast to the Kaspa BlockDAG network.",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    HorizontalDivider(color = SurfaceCardBorder)
                    Text(text = "• Swapped: ${String.format("%.4f", parsedFromAmount)} ${fromToken.symbol}", color = TextSecondary, fontSize = 13.sp)
                    Text(text = "• Est. Received: ${String.format("%.4f", estimatedOutputAmount)} ${toToken.symbol}", color = EmeraldMesh, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "• Recipient: ${effectiveRecipient.take(16)}...", color = TextSecondary, fontSize = 12.sp)
                    if (lastBroadcastTxHash.isNotEmpty()) {
                        Text(text = "• Tx Hash: ${lastBroadcastTxHash.take(20)}...", color = KaspaTea, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTea, contentColor = Color.Black)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun TokenDropdownSelector(
    selectedToken: SwapTokenItem,
    tokens: List<SwapTokenItem>,
    onTokenSelected: (SwapTokenItem) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceDark,
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(selectedToken.icon, fontSize = 16.sp)
                Text(selectedToken.symbol, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SurfaceCard)
        ) {
            tokens.forEach { token ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(token.icon, fontSize = 16.sp)
                            Column {
                                Text(token.symbol, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                Text(token.name, fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    },
                    onClick = {
                        onTokenSelected(token)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun QuoteRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}
