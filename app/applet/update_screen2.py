with open('app/src/main/java/com/example/ui/KaspaTestnetWalletScreen.kt', 'r') as f:
    content = f.read()

# 1. Update SendTabContent
old_send_tab_content = """@Composable
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
}"""

new_send_tab_content = """@Composable
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
    val isEvm = selectedNetwork.contains("Kasplex", ignoreCase = true) || selectedNetwork.contains("Igra", ignoreCase = true)
    val currencySymbol = when {
        selectedNetwork.contains("Igra", ignoreCase = true) -> "iKAS"
        selectedNetwork.contains("Testnet", ignoreCase = true) -> "tKAS"
        else -> "KAS"
    }
    val amountNum = amountInput.toDoubleOrNull() ?: 0.0
    val isRecipientEntered = recipientInput.trim().isNotBlank()
    val cleanRecipient = recipientInput.trim()

    val isAddressValidOnNetwork = if (isEvm) {
        cleanRecipient.matches(Regex("^0x[0-9a-fA-F]{40}$"))
    } else {
        val cleanKaspa = cleanRecipient.lowercase()
        val isTestnetWallet = senderAddress.startsWith("kaspatest:")
        val expectedPrefix = if (isTestnetWallet) "kaspatest" else "kaspa"
        val isPrefixMismatch = if (isTestnetWallet) cleanKaspa.startsWith("kaspa:") else cleanKaspa.startsWith("kaspatest:")
        val isRealAddressValid = com.example.viewmodel.DecentralViewModel.isValidKaspaAddress(cleanKaspa) ||
            com.example.network.CryptoUtils.isValidKaspaAddress(cleanKaspa, expectedPrefix)
        isRealAddressValid && !isPrefixMismatch
    }
    val isAddressError = isRecipientEntered && !isAddressValidOnNetwork
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
    val estEvmFee = 0.00042
    val feeForValidation = if (isEvm) estEvmFee else breakdown.feeKas
    val isInsufficientBalance = amountNum > 0.0 && (amountNum + feeForValidation) > balanceKas
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
                label = { Text(if (isEvm) "Recipient Address ($selectedNetwork)" else "Recipient Address") },
                placeholder = { Text(if (isEvm) "0x..." else if (senderAddress.startsWith("kaspatest:")) "kaspatest:q..." else "kaspa:q...") },
                supportingText = {
                    when {
                        isAddressSuccess -> Text(
                            if (isEvm) "✓ Valid $selectedNetwork EVM address" else "✓ Valid Kaspa address",
                            color = KaspaTea,
                            fontWeight = FontWeight.SemiBold
                        )
                        isAddressError -> Text(
                            if (isEvm) "Invalid EVM address (must be a valid 0x recipient address for $selectedNetwork)"
                            else if (senderAddress.startsWith("kaspatest:") && cleanRecipient.lowercase().startsWith("kaspa:")) "Network mismatch: Current wallet is on Testnet 10 (kaspatest:)"
                            else if (!senderAddress.startsWith("kaspatest:") && cleanRecipient.lowercase().startsWith("kaspatest:")) "Network mismatch: Current wallet is on Mainnet (kaspa:)"
                            else "Invalid Kaspa address (must start with ${if (senderAddress.startsWith("kaspatest:")) "kaspatest:" else "kaspa:"})",
                            color = RedTamper
                        )
                        else -> Text(
                            if (isEvm) "Only valid 0x EVM address ($selectedNetwork)" else "Only valid Kaspa address",
                            color = TextMuted
                        )
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
                            val maxSendable = (balanceKas - feeForValidation).coerceAtLeast(0.0)
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
                        isInsufficientBalance -> Text("Insufficient balance: Required %.6f $currencySymbol exceeds available %.6f $currencySymbol".format(amountNum + feeForValidation, balanceKas), color = RedTamper)
                        amountInput.isNotBlank() && amountNum <= 0.0 -> Text("Amount must be greater than 0 $currencySymbol", color = RedTamper)
                        else -> Text("Available: %.8f $currencySymbol".format(balanceKas), color = TextMuted)
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
            // Live Interactive Kaspa / EVM Fee & Execution Details
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
                    Text("Signing & Broadcasting to $selectedNetwork...", color = SurfaceDark, fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = SurfaceDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send $currencySymbol", color = SurfaceDark, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}"""

assert old_send_tab_content in content, 'old_send_tab_content not found'
content = content.replace(old_send_tab_content, new_send_tab_content, 1)

# 2. Update ReceiveTabContent & ReceiveScreenOverlay
old_receive = """@Composable
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
        val isEvmAddress = address.startsWith("0x")
        val derivationPath = if (isEvmAddress) "m/44'/60'/0'/0/0" else "m/44'/111111'/0'/0/0"

        item {
            Text(
                text = if (isEvmAddress) "Your EVM Address" else if (isMainnet) "Your Kaspa Mainnet Address" else "Your Kaspa Testnet Address",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isEvmAddress) "Scan or copy this address to receive tokens on EVM layer." else if (isMainnet) "Scan or copy this address to receive Kaspa." else "Scan or copy this address to receive test coins.",
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
        }"""

new_receive = """@Composable
private fun ReceiveTabContent(
    address: String,
    isMainnet: Boolean,
    selectedNetwork: String = "",
    onOpenFaucet: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    val isEvmAddress = address.startsWith("0x") || selectedNetwork.contains("Kasplex", ignoreCase = true) || selectedNetwork.contains("Igra", ignoreCase = true)
    val currencySymbol = when {
        selectedNetwork.contains("Igra", ignoreCase = true) -> "iKAS"
        selectedNetwork.contains("Testnet", ignoreCase = true) -> "tKAS"
        else -> "KAS"
    }
    val derivationPath = if (isEvmAddress) "m/44'/60'/0'/0/0" else "m/44'/111111'/0'/0/0"
    val derivationDesc = if (isEvmAddress) "m/44'/60'/0'/0/0 (EVM Standard • secp256k1)" else "m/44'/111111'/0'/0/0 (Kaspa Native • Schnorr)"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = when {
                    selectedNetwork.contains("Igra", ignoreCase = true) -> "Your Igra EVM Address"
                    selectedNetwork.contains("Kasplex", ignoreCase = true) -> "Your Kasplex EVM Address"
                    isEvmAddress -> "Your EVM Address"
                    isMainnet -> "Your Kaspa Mainnet Address"
                    else -> "Your Kaspa Testnet Address"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    isEvmAddress -> "Scan or copy this 0x address to receive $currencySymbol on $selectedNetwork."
                    isMainnet -> "Scan or copy this address to receive Kaspa."
                    else -> "Scan or copy this address to receive test coins."
                },
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Derivation Path: $derivationDesc",
                fontSize = 11.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
        }"""

assert old_receive in content, 'old_receive not found'
content = content.replace(old_receive, new_receive, 1)

old_receive_overlay = """@Composable
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
}"""

new_receive_overlay = """@Composable
private fun ReceiveScreenOverlay(
    address: String,
    isMainnet: Boolean,
    selectedNetwork: String = "",
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
                selectedNetwork = selectedNetwork,
                onOpenFaucet = onOpenFaucet
            )
        }
    }
}"""

assert old_receive_overlay in content, 'old_receive_overlay not found'
content = content.replace(old_receive_overlay, new_receive_overlay, 1)

# 3. Update TransactionCard & HistoryTabContent
old_tx_card = """@Composable
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
}"""

new_tx_card = """@Composable
private fun TransactionCard(
    tx: KaspaTransactionItem,
    selectedNetwork: String = "",
    onClick: () -> Unit
) {
    val isReceive = tx.type == "RECEIVED"
    val isEvm = selectedNetwork.contains("Kasplex", ignoreCase = true) || selectedNetwork.contains("Igra", ignoreCase = true)
    val currencySymbol = when {
        selectedNetwork.contains("Igra", ignoreCase = true) -> "iKAS"
        selectedNetwork.contains("Testnet", ignoreCase = true) -> "tKAS"
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
                            text = if (isReceive) "Received $currencySymbol" else "Sent $currencySymbol",
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
                        text = "${if (isReceive) "+" else "-"}%.4f $currencySymbol".format(tx.amountKas),
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
                            softWrap = false,
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
                    text = "${if (isEvm) "Gas: " else "Fee: "}${KaspaTransactionEngine.formatKas(tx.feeKas)} $currencySymbol",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Default,
                    color = if (isReceive) KaspaTea else AmberCentral
                )
            }
        }
    }
}"""

assert old_tx_card in content, 'old_tx_card not found'
content = content.replace(old_tx_card, new_tx_card, 1)

# In HistoryTabContent:
old_history = """private fun HistoryTabContent(
    transactions: List<KaspaTransactionItem>,
    isLoading: Boolean,
    onOpenTx: (KaspaTransactionItem) -> Unit,
    onGetFaucetCoins: () -> Unit,
    onSendKaspa: (() -> Unit)? = null,
    onReceiveKaspa: (() -> Unit)? = null,
    isMainnet: Boolean
) {"""

new_history = """private fun HistoryTabContent(
    transactions: List<KaspaTransactionItem>,
    isLoading: Boolean,
    onOpenTx: (KaspaTransactionItem) -> Unit,
    onGetFaucetCoins: () -> Unit,
    onSendKaspa: (() -> Unit)? = null,
    onReceiveKaspa: (() -> Unit)? = null,
    isMainnet: Boolean,
    selectedNetwork: String = ""
) {
    val currencySymbol = when {
        selectedNetwork.contains("Igra", ignoreCase = true) -> "iKAS"
        selectedNetwork.contains("Testnet", ignoreCase = true) -> "tKAS"
        else -> "KAS"
    }"""

assert old_history in content, 'old_history not found'
content = content.replace(old_history, new_history, 1)

# In HistoryTabContent empty state:
old_empty_tx = """                            Text(
                                text = when (selectedFilterIndex) {
                                    1 -> "No Sent Transactions"
                                    2 -> "No Received Transactions"
                                    else -> "No Transactions Yet"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )"""

new_empty_tx = """                            Text(
                                text = when (selectedFilterIndex) {
                                    1 -> "No Sent $currencySymbol Transactions"
                                    2 -> "No Received $currencySymbol Transactions"
                                    else -> if (selectedNetwork.isNotBlank()) "No Transactions on $selectedNetwork" else "No Transactions Yet"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )"""

assert old_empty_tx in content, 'old_empty_tx not found'
content = content.replace(old_empty_tx, new_empty_tx, 1)

old_receive_btn = """                                Text("Receive KAS", color = SurfaceDark, fontWeight = FontWeight.Bold)"""
new_receive_btn = """                                Text("Receive $currencySymbol", color = SurfaceDark, fontWeight = FontWeight.Bold)"""
assert old_receive_btn in content, 'old_receive_btn not found'
content = content.replace(old_receive_btn, new_receive_btn, 1)

old_tx_card_call = """                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            TransactionCard(tx = tx, onClick = { onOpenTx(tx) })
                        }"""
new_tx_card_call = """                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            TransactionCard(tx = tx, selectedNetwork = selectedNetwork, onClick = { onOpenTx(tx) })
                        }"""
assert old_tx_card_call in content, 'old_tx_card_call not found'
content = content.replace(old_tx_card_call, new_tx_card_call, 1)

with open('app/src/main/java/com/example/ui/KaspaTestnetWalletScreen.kt', 'w') as f:
    f.write(content)

print('Phase 2 of KaspaTestnetWalletScreen updated!')
