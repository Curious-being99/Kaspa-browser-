file_path = 'app/src/main/java/com/example/ui/KaspaTestnetWalletScreen.kt'
with open(file_path, 'r') as f:
    code = f.read()

# 1. Update ManageTabContent signature and cleanup
old_manage_top = '''@Composable
private fun ManageTabContent(
    viewModel: DecentralViewModel,
    activeAccountHandle: String,
    activeAccountSeedPhrase: String,
    activeAccountId: String,
    selectedEvmChainId: Long,
    onSelectEvmNetwork: (Long) -> Unit,
    testnetAddress: String,
    allAccountsCount: Int,
    onOpenRecovery: () -> Unit,
    onSignOut: () -> Unit,
    onOpenNetworkSwitch: () -> Unit
) {
    val selectedNetwork by viewModel.selectedNetwork.collectAsState()
    val selectedExplorerUrl by viewModel.selectedExplorerUrl.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val evmNetworks = com.example.dapp.EvmWalletEngine.networks
    val savedPassphrase = remember(activeAccountId) { runCatching { com.example.dapp.EvmPassphraseVault.load(context, activeAccountId) } }
    var evmPassphrase by remember(activeAccountId) { mutableStateOf(savedPassphrase.getOrDefault("")) }
    var evmPassphraseError by remember(activeAccountId) { mutableStateOf(savedPassphrase.exceptionOrNull()?.message) }
    var evmPassphraseSaved by remember(activeAccountId) { mutableStateOf(false) }
    var evmPassphraseDirty by remember(activeAccountId) { mutableStateOf(false) }
    var showEvmHistory by remember { mutableStateOf(false) }
    var evmHistoryRefreshKey by remember { mutableIntStateOf(0) }
    var evmHistoryLoading by remember { mutableStateOf(false) }
    var evmHistoryError by remember { mutableStateOf<String?>(null) }
    var evmHistory by remember { mutableStateOf<com.example.dapp.EvmActivityRepository.Result?>(null) }
    val evmAddress = remember(activeAccountSeedPhrase, evmPassphrase, evmPassphraseError) { if (evmPassphraseError != null) "" else runCatching { com.example.dapp.EvmWalletEngine.address(activeAccountSeedPhrase, evmPassphrase) }.getOrDefault("") }
    val isStandardEvmMnemonic = remember(activeAccountSeedPhrase) { runCatching { org.web3j.crypto.MnemonicUtils.validateMnemonic(CryptoUtils.getDecryptedSeed(activeAccountSeedPhrase)) }.getOrDefault(false) }
    var legacyPhraseConfirmed by remember(activeAccountId, evmAddress) { mutableStateOf(runCatching { com.example.dapp.EvmPassphraseVault.isLegacyPhraseConfirmed(context, activeAccountId, evmAddress) }.getOrDefault(false)) }
    val legacyPhraseRequiresConfirmation = activeAccountSeedPhrase.isNotBlank() && !isStandardEvmMnemonic && !legacyPhraseConfirmed
    LaunchedEffect(showEvmHistory, selectedEvmChainId, evmAddress, evmHistoryRefreshKey) {
        if (showEvmHistory) {
            if (evmAddress.isBlank()) { evmHistoryError = evmPassphraseError ?: "EVM address is unavailable for this recovery phrase."; evmHistory = null }
            else {
                evmHistoryLoading = true; evmHistoryError = null
                try { evmHistory = withContext(Dispatchers.IO) { com.example.dapp.EvmActivityRepository.load(com.example.dapp.EvmWalletEngine.network(selectedEvmChainId), evmAddress, 100L) } }
                catch (e: Exception) { evmHistoryError = e.message ?: "Unable to load real EVM activity from the selected RPC."; evmHistory = null }
                finally { evmHistoryLoading = false }
            }
        }
    }
    val selectedRpcEndpoint = when {
        selectedNetwork.contains("Mainnet", ignoreCase = true) -> "https://api.kaspa.org"
        selectedNetwork.contains("11", ignoreCase = true) -> "https://api-tn11.kaspa.org"
        else -> "https://api-tn10.kaspa.org"
    }
    val selectedAddressPrefix = if (selectedNetwork.contains("Mainnet", ignoreCase = true)) "kaspa:" else "kaspatest:"
    var showNetworkDetailsSheet by remember { mutableStateOf(false) }
    var showRecoverySheet by remember { mutableStateOf(false) }
    var showSignOutSheet by remember { mutableStateOf(false) }'''

new_manage_top = '''@Composable
private fun ManageTabContent(
    viewModel: DecentralViewModel,
    activeAccountHandle: String,
    activeAccountSeedPhrase: String,
    activeAccountId: String,
    selectedEvmChainId: Long,
    onSelectEvmNetwork: (Long) -> Unit,
    testnetAddress: String,
    allAccountsCount: Int,
    onOpenRecovery: () -> Unit,
    onSignOut: () -> Unit,
    onOpenNetworkSwitch: () -> Unit,
    onOpenNetworkDetails: () -> Unit
) {
    val selectedNetwork by viewModel.selectedNetwork.collectAsState()'''

assert old_manage_top in code, 'old_manage_top not found'
code = code.replace(old_manage_top, new_manage_top, 1)

# Update Network Details, Recovery, and Sign Out click actions in ManageTabContent
old_manage_cards = '''        item {
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
    }'''

new_manage_cards = '''        item {
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
    }'''

assert old_manage_cards in code, 'old_manage_cards not found'
code = code.replace(old_manage_cards, new_manage_cards, 1)

# Remove old sheets and dialogs in ManageTabContent
target_to_remove_start = '    if (showNetworkDetailsSheet) {'
target_to_remove_end = '    if (showEvmHistory) {'

# Find the full block of old sheets/dialogs up to the end of showEvmHistory
assert target_to_remove_start in code, 'target_to_remove_start not found'
start_idx = code.find(target_to_remove_start)

# Find where showEvmHistory block ends (before SetupFeatureItem or next composable)
next_composable = '@Composable\nprivate fun SetupFeatureItem'
end_idx = code.find(next_composable, start_idx)
assert end_idx != -1, 'next_composable not found'

# Replace everything from start_idx to end_idx with clean closing of ManageTabContent
code = code[:start_idx] + '}\n\n' + code[end_idx:]

# Update ManageScreenOverlay signature and call to ManageTabContent
old_manage_overlay = '''@Composable
private fun ManageScreenOverlay(
    viewModel: DecentralViewModel,
    activeAccountHandle: String,
    activeAccountSeedPhrase: String,
    activeAccountId: String,
    selectedEvmChainId: Long,
    onSelectEvmNetwork: (Long) -> Unit,
    testnetAddress: String,
    allAccountsCount: Int,
    selectedNetwork: String,
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
                onOpenRecovery = onViewSeed,
                onSignOut = onSignOut,
                onOpenNetworkSwitch = onOpenNetworkSwitch
            )
        }
    }
}'''

new_manage_overlay = '''@Composable
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
                onOpenRecovery = onViewSeed,
                onSignOut = onSignOut,
                onOpenNetworkSwitch = onOpenNetworkSwitch,
                onOpenNetworkDetails = onOpenNetworkDetails
            )
        }
    }
}'''

# Check if old_manage_overlay without selectedNetwork is present
orig_manage_overlay = '''@Composable
private fun ManageScreenOverlay(
    viewModel: DecentralViewModel,
    activeAccountHandle: String,
    activeAccountSeedPhrase: String,
    activeAccountId: String,
    selectedEvmChainId: Long,
    onSelectEvmNetwork: (Long) -> Unit,
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
                activeAccountSeedPhrase = activeAccountSeedPhrase,
                activeAccountId = activeAccountId,
                selectedEvmChainId = selectedEvmChainId,
                onSelectEvmNetwork = onSelectEvmNetwork,
                testnetAddress = testnetAddress,
                allAccountsCount = allAccountsCount,
                onOpenRecovery = onViewSeed,
                onSignOut = onSignOut,
                onOpenNetworkSwitch = onOpenNetworkSwitch
            )
        }
    }
}'''

assert orig_manage_overlay in code, 'orig_manage_overlay not found'
code = code.replace(orig_manage_overlay, new_manage_overlay, 1)

# 2. Update TransactionResultOverlay
old_res_ov = '''@Composable
private fun TransactionResultOverlay(
    isSuccess: Boolean,
    txId: String,
    errorMessage: String,
    transaction: KaspaTransactionItem?,
    onViewOnExplorer: () -> Unit,
    onClose: () -> Unit
) {'''

new_res_ov = '''@Composable
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
    }'''

assert old_res_ov in code, 'old_res_ov not found'
code = code.replace(old_res_ov, new_res_ov, 1)

old_res_text = '''            Text(
                text = if (isSuccess) "Your transaction has been submitted to the Kaspa BlockDAG mempool." else errorMessage,
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )'''

new_res_text = '''            Text(
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
            )'''

assert old_res_text in code, 'old_res_text not found'
code = code.replace(old_res_text, new_res_text, 1)

old_res_items = '''                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                    }'''

new_res_items = '''                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                    }'''

assert old_res_items in code, 'old_res_items not found'
code = code.replace(old_res_items, new_res_items, 1)

with open(file_path, 'w') as f:
    f.write(code)

print('PART 4A APPLIED SUCCESSFULLY')
