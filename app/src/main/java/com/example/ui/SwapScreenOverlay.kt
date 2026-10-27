package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import coil.compose.AsyncImage

data class SwapTokenItem(
    val symbol: String,
    val name: String,
    val icon: String,
    val isNative: Boolean = false,
    val tokenId: String? = null,
    val logoUrl: String? = null,
    val drawableRes: Int? = null
)

val SWAP_TOKENS = listOf(
    SwapTokenItem(
        symbol = "KAS",
        name = "Kaspa Native",
        icon = "",
        isNative = true,
        drawableRes = com.example.R.drawable.ic_kaspa_official_pebble
    ),
    SwapTokenItem(
        symbol = "\$KB",
        name = "Kaspabrowser (KCC-20)",
        icon = "🌐",
        tokenId = "b75a6e90f569965bdeec23c460ebd59e0f0445d8aa8f5d4330fe15c66a47b4d8",
        logoUrl = "https://storage.googleapis.com/kaspa-rocket-prod-uploads/7c70c6f4-faa9-4d84-ba81-62553168f1a9.png"
    )
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
    var isRecipientExpanded by remember { mutableStateOf(false) }
    var isDetailsExpanded by remember { mutableStateOf(false) }
    
    var isExecutingSwap by remember { mutableStateOf(false) }
    var swapStatusMessage by remember { mutableStateOf<String?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var lastBroadcastTxHash by remember { mutableStateOf("") }

    // Live Kaspa Price in USD (from wallet state or default fallback)
    val kasPriceUsd = walletState.priceUsd.takeIf { it > 0.0 } ?: 0.0
    
    // Parse numeric input amount
    val parsedFromAmount = amountFromInput.toDoubleOrNull() ?: 0.0
    
    // KaspaRocket Client for real quotes
    val kaspaRocket = remember { com.example.network.kaspa.KaspaRocketClient() }
    var lpCovenantId by remember { mutableStateOf<String?>(null) }
    var krQuote by remember { mutableStateOf<com.example.network.kaspa.KaspaRocketQuote?>(null) }
    var spotQuote by remember { mutableStateOf<com.example.network.kaspa.KaspaRocketQuote?>(null) }
    var isFetchingQuote by remember { mutableStateOf(false) }

    // Resolve Pool Covenant & Spot Price for $KB token
    LaunchedEffect(fromToken, toToken) {
        val targetToken = if (!fromToken.isNative) fromToken else toToken
        targetToken.tokenId?.let { id ->
            val lpId = kaspaRocket.getPoolCovenant(id)
            lpCovenantId = lpId
            if (lpId != null) {
                // Fetch live spot quote for 10 KAS benchmark budget
                scope.launch {
                    val benchQuote = kaspaRocket.getQuote(
                        tokenId = id,
                        lpCovenantId = lpId,
                        direction = "buy",
                        kasBudgetSompi = 1_000_000_000L // 10 KAS budget
                    )
                    if (benchQuote != null) {
                        spotQuote = benchQuote
                    }
                }
            }
        }
    }

    LaunchedEffect(amountFromInput, fromToken, toToken, lpCovenantId) {
        krQuote = null
        val cleanInput = amountFromInput.trim()
        val lpId = lpCovenantId
        val isBuy = fromToken.isNative
        val targetToken = if (isBuy) toToken else fromToken
        val tokenId = targetToken.tokenId
        val amountUnits = if (isBuy) {
            com.example.network.kaspa.KaspaRocketMath.kasToSompi(cleanInput)
        } else {
            com.example.network.kaspa.KaspaRocketMath.tokensToRaw(cleanInput)
        }
        if (cleanInput.isBlank() || lpId == null || tokenId == null || amountUnits <= 0L) {
            isFetchingQuote = false
            return@LaunchedEffect
        }
        isFetchingQuote = true
        try {
            krQuote = if (isBuy) {
                kaspaRocket.getQuote(tokenId, lpId, "buy", kasBudgetSompi = amountUnits)
            } else {
                kaspaRocket.getQuote(tokenId, lpId, "sell", tokensRaw = amountUnits)
            }
        } finally {
            isFetchingQuote = false
        }
    }

    // Always-available live spot price in Sompi (from active quote, benchmark quote, or live pool rate)
    val effectivePriceSompi = remember(krQuote, spotQuote) {
        if (krQuote != null && krQuote!!.pricePerTokenSompi > 0) {
            krQuote!!.pricePerTokenSompi
        } else if (spotQuote != null && spotQuote!!.pricePerTokenSompi > 0) {
            spotQuote!!.pricePerTokenSompi
        } else {
            0L
        }
    }

    // Dynamic USD Value calculation (lossless Sompi/Raw unit math)
    val fromUsdValue = remember(amountFromInput, fromToken, kasPriceUsd, krQuote) {
        if (fromToken.isNative) {
            val sompi = com.example.network.kaspa.KaspaRocketMath.kasToSompi(amountFromInput)
            (sompi.toDouble() / 100_000_000.0) * (kasPriceUsd)
        } else {
            krQuote?.let {
                (it.netKasSompi.toDouble() / 100_000_000.0) * (kasPriceUsd)
            } ?: 0.0
        }
    }

    val toUsdValue = remember(toToken, kasPriceUsd, krQuote) {
        if (toToken.isNative) {
            krQuote?.let {
                (it.netKasSompi.toDouble() / 100_000_000.0) * (kasPriceUsd)
            } ?: 0.0
        } else {
            krQuote?.let {
                val tokenCount = it.amountTokensRaw.toDouble() / 1000.0
                val priceKas = it.pricePerTokenSompi.toDouble() / 100_000_000.0
                tokenCount * priceKas * (kasPriceUsd)
            } ?: 0.0
        }
    }

    val estimatedOutputAmount = remember(toToken, krQuote) {
        if (toToken.isNative) {
            krQuote?.let { it.netKasSompi.toDouble() / 100_000_000.0 } ?: 0.0
        } else {
            krQuote?.amountTokensDisplay?.toDoubleOrNull() ?: 0.0
        }
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

    val isSufficientBalance = remember(parsedFromAmount, fromToken, walletState.balanceKas, walletState.tokenBalanceKb, estGasFeeKas) {
        if (fromToken.isNative) {
            walletState.balanceKas == 0.0 || (parsedFromAmount + estGasFeeKas <= walletState.balanceKas)
        } else {
            walletState.tokenBalanceKb == 0.0 || (parsedFromAmount <= walletState.tokenBalanceKb)
        }
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
                            text = if (walletState.tokenBalanceKb > 0) {
                                "${String.format(java.util.Locale.US, "%.2f", walletState.balanceKas)} KAS | ${String.format(java.util.Locale.US, "%.1f", walletState.tokenBalanceKb)} \$KB"
                            } else {
                                "${String.format(java.util.Locale.US, "%.2f", walletState.balanceKas)} KAS"
                            },
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
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Mode Selector Tabs (Swap / Buy / Sell)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val modes = listOf("Swap", "Buy", "Sell")
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
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }

                // FROM Token Card (Amount Input UI)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KaspaTea,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KaspaTea.copy(alpha = 0.15f))
                                        .clickable {
                                            val maxKas = (walletState.balanceKas - estGasFeeKas).coerceAtLeast(0.0)
                                            amountFromInput = if (maxKas > 0) String.format(java.util.Locale.US, "%.4f", maxKas) else "0"
                                        }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            } else if (!fromToken.isNative && walletState.tokenBalanceKb > 0) {
                                Text(
                                    text = "MAX",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KaspaTea,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KaspaTea.copy(alpha = 0.15f))
                                        .clickable {
                                            amountFromInput = String.format(java.util.Locale.US, "%.4f", walletState.tokenBalanceKb)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Amount Input Field (Clean borderless)
                            TextField(
                                value = amountFromInput,
                                onValueChange = { input ->
                                    if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
                                        amountFromInput = input
                                    }
                                },
                                placeholder = { Text("0.0", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextSecondary.copy(alpha = 0.4f)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("swap_from_amount_input"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
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

                        // Live USD Value and Available balance display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (fromUsdValue > 0) "≈ \$${String.format(java.util.Locale.US, "%.2f", fromUsdValue)} USD" else "Live quote",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )

                            if (fromToken.isNative) {
                                Text(
                                    text = "Avail: ${String.format(java.util.Locale.US, "%.2f", walletState.balanceKas)} KAS",
                                    fontSize = 12.sp,
                                    color = if (isSufficientBalance) TextSecondary else RedTamper,
                                    maxLines = 1
                                )
                            } else {
                                Text(
                                    text = "Avail: ${String.format(java.util.Locale.US, "%.2f", walletState.tokenBalanceKb)} \$KB",
                                    fontSize = 12.sp,
                                    color = if (isSufficientBalance) TextSecondary else RedTamper,
                                    maxLines = 1
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
                            .size(40.dp)
                            .background(SurfaceDark, CircleShape)
                            .border(1.5.dp, KaspaTea, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Switch Tokens",
                            tint = KaspaTea,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // TO Token Card (Dynamic Output Quote Display UI)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Output Amount Display (Read-Only)
                            val outputDisplayVal = remember(toToken, krQuote) {
                                if (toToken.isNative) {
                                    krQuote?.let { com.example.network.kaspa.KaspaRocketMath.formatKas(it.netKasSompi) } ?: ""
                                } else {
                                    krQuote?.amountTokensDisplay ?: ""
                                }
                            }

                            TextField(
                                value = outputDisplayVal,
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("0.0", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextSecondary.copy(alpha = 0.4f)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("swap_to_amount_output"),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldMesh
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
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

                        // Output USD Value display & network tag
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isFetchingQuote) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = KaspaTea)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Fetching quote...", fontSize = 12.sp, color = TextSecondary, maxLines = 1)
                                }
                            } else {
                                Text(
                                    text = if (toUsdValue > 0) "≈ \$${String.format(java.util.Locale.US, "%.2f", toUsdValue)} USD" else "Quote updates automatically",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }

                            Text(
                                text = if (toToken.isNative) "Kaspa Native" else "KCC-20 Covenant",
                                fontSize = 11.sp,
                                color = TextSecondary.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    }
                }

                // Destination / Recipient Address Input UI (Compact Expandable)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isRecipientExpanded = !isRecipientExpanded },
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = KaspaTea, modifier = Modifier.size(15.dp))
                                Text(
                                    text = if (destinationAddressInput.isNotBlank()) "To: ${destinationAddressInput.take(12)}..." else "To: Active Wallet",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isRecipientExpanded) "Collapse" else "Custom Recipient",
                                    fontSize = 11.sp,
                                    color = KaspaTea,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (isRecipientExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = KaspaTea,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (isRecipientExpanded) {
                            OutlinedTextField(
                                value = destinationAddressInput,
                                onValueChange = { destinationAddressInput = it },
                                placeholder = { Text("kaspa:q... or kaspatest:q...", fontSize = 12.sp, color = TextSecondary.copy(0.5f)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("swap_recipient_address_input"),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.5.sp, color = TextPrimary),
                                trailingIcon = {
                                    if (destinationAddressInput.isNotEmpty()) {
                                        IconButton(onClick = { destinationAddressInput = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                                        }
                                    } else {
                                        IconButton(onClick = { clipboardManager.getText()?.let { destinationAddressInput = it.text } }) {
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
                        }

                        if (!isAddressValid && destinationAddressInput.isNotBlank()) {
                            Text(
                                text = "⚠️ Invalid Kaspa address format (expected kaspa:q... or kaspatest:q...)",
                                fontSize = 11.sp,
                                color = RedTamper
                            )
                        }
                    }
                }

                // Real-Time Quote Details Card (Sleek Expandable)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val rateRatioStr = remember(fromToken, toToken, effectivePriceSompi, kasPriceUsd) {
                            if (effectivePriceSompi <= 0L) {
                                "Live \\$KB price unavailable"
                            } else {
                                val priceKasStr = com.example.network.kaspa.KaspaRocketMath.formatKas(effectivePriceSompi)
                                val priceUsdVal = kasPriceUsd?.let { (priceKasStr.toDoubleOrNull() ?: 0.0) * it }
                                val usdSuffix = priceUsdVal?.let { " (≈ \\$" + String.format(java.util.Locale.US, "%.4f", it) + " USD)" }.orEmpty()
                                "1 \\$KB ≈ " + priceKasStr + " KAS" + usdSuffix
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isDetailsExpanded = !isDetailsExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Rate: $rateRatioStr",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isDetailsExpanded) "Less" else "Details & Covenants",
                                    fontSize = 11.sp,
                                    color = KaspaTea,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = if (isDetailsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = KaspaTea,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (isDetailsExpanded) {
                            HorizontalDivider(color = SurfaceCardBorder, modifier = Modifier.padding(vertical = 4.dp))

                            QuoteRow(label = "Live price", value = if (effectivePriceSompi > 0L) com.example.network.kaspa.KaspaRocketMath.formatKas(effectivePriceSompi) + " KAS / \\$KB" else "Unavailable")
                            QuoteRowWithCopy(
                                label = "Token Covenant ID",
                                fullValue = "b75a6e90f569965bdeec23c460ebd59e0f0445d8aa8f5d4330fe15c66a47b4d8",
                                displayValue = "b75a6e90f...6a47b4d8"
                            )
                            val poolCovenantHex = lpCovenantId ?: "2eec0a5bb96a3ff6030d365fb5cd966eafd8b54dae036020c6350f44b57c7258"
                            val poolCovenantDisplay = if (poolCovenantHex.length > 16) "${poolCovenantHex.take(9)}...${poolCovenantHex.takeLast(8)}" else poolCovenantHex
                            QuoteRowWithCopy(
                                label = "Covenant Pool ID",
                                fullValue = poolCovenantHex,
                                displayValue = poolCovenantDisplay
                            )
                            QuoteRow(label = "KAS Price (USD)", value = kasPriceUsd?.let { "$" + String.format(java.util.Locale.US, "%.3f", it) + " USD" } ?: "Unavailable")
                            QuoteRow(label = "Slippage Tolerance", value = "0.5%")
                            
                            val dexFeeKasStr = com.example.network.kaspa.KaspaRocketMath.formatKas(krQuote?.feeTotalSompi ?: 0L)
                            QuoteRow(label = "KaspaRocket Protocol Fee", value = "$dexFeeKasStr KAS (${krQuote?.feeTotalSompi ?: 0L} Sompis)")
                            QuoteRow(label = "Network Gas Fee (Est.)", value = "${String.format(java.util.Locale.US, "%.5f", estGasFeeKas)} KAS")
                            if (!fromToken.isNative) {
                                val netSompi = krQuote?.netKasSompi ?: 0L
                                val netKasStr = com.example.network.kaspa.KaspaRocketMath.formatKas(netSompi)
                                QuoteRow(label = "Net Proceeds (Fees Deducted)", value = "$netKasStr KAS")
                            }
                            QuoteRow(label = "Routing Path", value = "KaspaRocket LP Covenant (Testnet-10 / Mainnet)")
                            QuoteRow(
                                label = "Min Received (0.5% Slippage)",
                                value = if (toToken.isNative) {
                                    if (krQuote != null && krQuote!!.netKasSompi > 0) {
                                        val minSompi = (krQuote!!.netKasSompi * 0.995).toLong()
                                        "${com.example.network.kaspa.KaspaRocketMath.formatKas(minSompi)} KAS"
                                    } else "0.0 KAS"
                                } else {
                                    if (krQuote != null && krQuote!!.amountTokensRaw > 0) {
                                        val minRaw = (krQuote!!.amountTokensRaw * 0.995).toLong()
                                        "${com.example.network.kaspa.KaspaRocketMath.formatTokens(minRaw)} ${toToken.symbol}"
                                    } else "0.0 ${toToken.symbol}"
                                }
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Est. Network Gas", fontSize = 11.5.sp, color = TextSecondary)
                                Text(text = "${String.format(java.util.Locale.US, "%.5f", estGasFeeKas)} KAS", fontSize = 11.5.sp, color = TextPrimary)
                            }
                        }
                    }
                }

                // Uneconomic Warning Notice Card
                if (krQuote?.uneconomic == true) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = RedTamper.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RedTamper.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = RedTamper, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Refused: This sale costs more in fees than it returns. The protocol fee floor exceeds the returned KAS.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RedTamper
                            )
                        }
                    }
                }

                // Insufficient Balance Error Notice
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

                // Primary Swap Execution Button (Matching Buy Token button size and styling)
                val isUneconomic = krQuote?.uneconomic == true
                val isExecutable = false

                Button(
                    onClick = {
                        swapStatusMessage = "Trading is temporarily unavailable: KaspaRocket covenant signing and on-chain verification are not connected. No funds were sent and no token balance was changed."
                    }
                    },
                    enabled = isExecutable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("execute_swap_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KaspaTea,
                        contentColor = Color.Black,
                        disabledContainerColor = SurfaceCardBorder,
                        disabledContentColor = TextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isExecutingSwap) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Broadcasting to Mempool...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    } else if (isUneconomic) {
                        Text("Trade Refused (Uneconomic)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Swap ${fromToken.symbol} to ${toToken.symbol}",
                            fontSize = 14.sp,
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
                    Text(text = "• Credited to Wallet: +${String.format("%.4f", estimatedOutputAmount)} ${toToken.symbol}", color = EmeraldMesh, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "• Token Covenant: b75a6e90f...6a47b4d8", color = TextSecondary, fontSize = 11.sp)
                    Text(text = "• Covenant Pool: 2eec0a5bb...4b57c7258", color = TextSecondary, fontSize = 11.sp)
                    Text(text = "• Recipient: ${effectiveRecipient.take(16)}...", color = TextSecondary, fontSize = 12.sp)
                    if (lastBroadcastTxHash.isNotEmpty()) {
                        Text(text = "• Tx Hash: ${lastBroadcastTxHash.take(20)}...", color = KaspaTea, fontSize = 11.sp)
                    }
                    val currentKbBal = viewModel?.kaspaWalletState?.value?.tokenBalanceKb ?: 0.0
                    if (currentKbBal > 0.0) {
                        Text(text = "• New \$KB Balance: ${String.format("%.4f", currentKbBal)} \$KB", color = KaspaTea, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
    modifier: Modifier = Modifier,
    selectedToken: SwapTokenItem,
    tokens: List<SwapTokenItem>,
    onTokenSelected: (SwapTokenItem) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (selectedToken.logoUrl != null) {
                    AsyncImage(
                        model = selectedToken.logoUrl,
                        contentDescription = selectedToken.symbol,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                    )
                } else if (selectedToken.drawableRes != null) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = selectedToken.drawableRes),
                        contentDescription = selectedToken.symbol,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Text(selectedToken.icon, fontSize = 16.sp)
                }
                Text(selectedToken.symbol, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
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
                            if (token.logoUrl != null) {
                                AsyncImage(
                                    model = token.logoUrl,
                                    contentDescription = token.symbol,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                )
                            } else if (token.drawableRes != null) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = token.drawableRes),
                                    contentDescription = token.symbol,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Text(token.icon, fontSize = 16.sp)
                            }
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

@Composable
private fun QuoteRowWithCopy(label: String, fullValue: String, displayValue: String) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(fullValue))
                android.widget.Toast.makeText(context, "Copied $label", android.widget.Toast.LENGTH_SHORT).show()
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = displayValue,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = KaspaTea
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy $label",
                tint = KaspaTea,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

