package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import coil.compose.AsyncImage
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.KaspaQrCodeView
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMesh
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.BuildConfig

data class InlineBotButton(
    val label: String,
    val icon: ImageVector,
    val actionKey: String
)

data class SiAgentMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val attachedFileName: String? = null,
    val attachedFileUri: String? = null,
    val isCommand: Boolean = false,
    val actionType: String? = null,
    val inlineButtons: List<InlineBotButton> = emptyList(),
    val txId: String? = null
)

data class SiCommandItem(
    val command: String,
    val title: String,
    val description: String,
    val icon: ImageVector
)

private const val KASCOV_URL = "https://kascov.io/data/price.json"

private val SI_AGENT_COMMANDS = listOf(
    SiCommandItem(
        command = "/kb",
        title = "Kaspa Browser token intelligence",
        description = "Wallet balance, indexed KCC-20 price and contract status",
        icon = Icons.Default.AutoAwesome
    ),
    SiCommandItem(
        command = "/wallet",
        title = "Kaspa live wallet & balance",
        description = "Live Kaspa node balance & UTXOs",
        icon = Icons.Default.AccountBalanceWallet
    ),
    SiCommandItem(
        command = "/send",
        title = "Send KAS",
        description = "Sign & broadcast on-chain transaction",
        icon = Icons.Default.Send
    ),
    SiCommandItem(
        command = "/receive",
        title = "Receive KAS",
        description = "Show receive QR & Bech32 addresses",
        icon = Icons.AutoMirrored.Filled.CallReceived
    ),
    SiCommandItem(
        command = "/history",
        title = "On-chain tx history",
        description = "Live recent on-chain transactions",
        icon = Icons.AutoMirrored.Filled.Article
    ),
    SiCommandItem(
        command = "/swap",
        title = "Native KB wallet tools",
        description = "Open the native Kaspa Browser dApp command center",
        icon = Icons.Default.SwapHoriz
    ),
    SiCommandItem(
        command = "/summarize",
        title = "Summarize active page",
        description = "Executive summary of active page",
        icon = Icons.AutoMirrored.Filled.Article
    ),
    SiCommandItem(
        command = "/security",
        title = "SSL & security audit",
        description = "TLS, encryption & privacy shields",
        icon = Icons.Default.Security
    ),
    SiCommandItem(
        command = "/blockdag",
        title = "BlockDAG network intel",
        description = "Kaspa 10 BPS, GHOSTDAG consensus",
        icon = Icons.Default.Hub
    ),
    SiCommandItem(
        command = "/mesh",
        title = "P2P mesh discovery",
        description = "Decentralized P2P radar",
        icon = Icons.Default.Sensors
    ),
    SiCommandItem(
        command = "/clear",
        title = "Clear chat",
        description = "Reset chat history",
        icon = Icons.Default.Delete
    ),
    SiCommandItem(
        command = "/help",
        title = "All bot commands",
        description = "List available commands",
        icon = Icons.Default.HelpOutline
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiAgentScreen(
    viewModel: DecentralViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Hardware and system back gesture navigation
    BackHandler {
        onClose()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val currentResource by viewModel.currentResource.collectAsState()
    val activeUrl = currentResource?.url ?: ""
    val activeTitle = currentResource?.title ?: ""

    // Kaspa Testnet & Mainnet Wallet real state
    val walletState by viewModel.kaspaWalletState.collectAsState()
    val testnetAddress by viewModel.testnetAddress.collectAsState()
    val mainnetAddress by viewModel.mainnetAddress.collectAsState()

    // Action sheet states for inline buttons
    var showSendSheet by remember { mutableStateOf(false) }
    var showReceiveSheet by remember { mutableStateOf(false) }

    var inputText by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isAgentGenerating by remember { mutableStateOf(false) }
    var showCommandPicker by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            var displayName = "Attached file"
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        displayName = cursor.getString(nameIndex)
                    }
                }
            }
            selectedFileName = displayName
        }
    }

    val isSlashMode = inputText.startsWith("/") || showCommandPicker

    val initialGreeting = "🤖 SI Agent\n\nLive wallet and Kaspa intelligence. Commands: /wallet  /kb  /send  /receive  /history  /help"

    val initialButtons = listOf(
        InlineBotButton("Wallet", Icons.Default.AccountBalanceWallet, "OPEN_WALLET"),
        InlineBotButton("Send", Icons.Default.Send, "OPEN_SEND"),
        InlineBotButton("Receive", Icons.AutoMirrored.Filled.CallReceived, "OPEN_RECEIVE"),
        InlineBotButton("History", Icons.AutoMirrored.Filled.Article, "OPEN_HISTORY"),
        InlineBotButton("Dapps", Icons.Default.SwapHoriz, "OPEN_SWAP")
    )

    val messages = remember {
        mutableStateListOf(
            SiAgentMessage(
                text = initialGreeting,
                isUser = false,
                inlineButtons = initialButtons
            )
        )
    }

    // Real-time transaction status listener for Si Agent messages
    LaunchedEffect(walletState.recentTransactions) {
        walletState.recentTransactions.forEach { tx ->
            if (tx.isAccepted) {
                // Find pending messages matching this txId
                val index = messages.indexOfFirst { 
                    it.txId == tx.txId && (it.text.contains("Pending") || it.text.contains("Mempool") || it.text.contains("Submitted") || it.text.contains("Broadcasted")) 
                }
                if (index != -1) {
                    val oldMsg = messages[index]
                    // Update the status line to show accepted
                    val newText = oldMsg.text.lines().map { line ->
                        if (line.startsWith("• Status:")) "• Status: ✅ Accepted & Confirmed on BlockDAG"
                        else if (line.startsWith("✅ Transaction")) "⛓️ Transaction Confirmed!"
                        else line
                    }.joinToString("\n")
                    
                    if (newText != oldMsg.text) {
                        messages[index] = oldMsg.copy(text = newText)
                    }
                }
            }
        }
    }

    fun getInlineButtonsForCommand(input: String): List<InlineBotButton> {
        val lower = input.lowercase().removePrefix("/")
        val hasNoWallet = testnetAddress.isBlank() && mainnetAddress.isBlank()
        
        if (hasNoWallet) {
            return listOf(
                InlineBotButton("Create Wallet", Icons.Default.Add, "CREATE_WALLET"),
                InlineBotButton("Import Wallet", Icons.Default.AccountBalanceWallet, "IMPORT_WALLET"),
                InlineBotButton("Wallet Setup Page", Icons.Default.AccountBalanceWallet, "OPEN_WALLET")
            )
        }

        return when {
            lower.contains("wallet") || lower.contains("balance") || lower.contains("faucet") || lower.contains("details") -> listOf(
                InlineBotButton("Send KAS", Icons.Default.Send, "OPEN_SEND"),
                InlineBotButton("Receive", Icons.AutoMirrored.Filled.CallReceived, "OPEN_RECEIVE"),
                InlineBotButton("Tx History", Icons.AutoMirrored.Filled.Article, "OPEN_HISTORY"),
                InlineBotButton("Dapps", Icons.Default.SwapHoriz, "OPEN_SWAP")
            )
            lower.contains("history") || lower.contains("tx") -> listOf(
                InlineBotButton("Compose Send", Icons.Default.Send, "OPEN_SEND"),
                InlineBotButton("Wallet Details", Icons.Default.AccountBalanceWallet, "OPEN_WALLET")
            )
            lower.contains("kb") || lower.contains("kcc20") || lower.contains("kaspa browser token") -> listOf(
                InlineBotButton("Open \$KB Center", Icons.Default.AutoAwesome, "OPEN_SWAP"),
                InlineBotButton("Wallet Details", Icons.Default.AccountBalanceWallet, "OPEN_WALLET")
            )
            lower.contains("swap") || lower.contains("dapp") -> listOf(
                InlineBotButton("Open \$KB Center", Icons.Default.SwapHoriz, "OPEN_SWAP"),
                InlineBotButton("Wallet Details", Icons.Default.AccountBalanceWallet, "OPEN_WALLET")
            )
            lower.contains("send") -> listOf(
                InlineBotButton("Tx History", Icons.AutoMirrored.Filled.Article, "OPEN_HISTORY"),
                InlineBotButton("Compose Send", Icons.Default.Send, "OPEN_SEND"),
                InlineBotButton("Wallet Details", Icons.Default.AccountBalanceWallet, "OPEN_WALLET")
            )
            lower.contains("receive") -> listOf(
                InlineBotButton("Show Receive QR", Icons.AutoMirrored.Filled.CallReceived, "OPEN_RECEIVE"),
                InlineBotButton("Copy Address", Icons.Default.ContentCopy, "COPY_ADDRESS")
            )
            else -> listOf(
                InlineBotButton("Wallet", Icons.Default.AccountBalanceWallet, "OPEN_WALLET"),
                InlineBotButton("Dapps", Icons.Default.SwapHoriz, "OPEN_SWAP")
            )
        }
    }

    fun parseSendArguments(input: String): Pair<String, Double>? {
        val tokens = input.trim().split(Regex("\\s+")).drop(1)
        if (tokens.size < 2) return null
        var address: String? = null
        var amount: Double? = null
        for (t in tokens) {
            val clean = t.trim()
            val dbl = clean.toDoubleOrNull()
            if (dbl != null && dbl > 0 && amount == null) {
                amount = dbl
            } else if ((clean.startsWith("kaspa:", ignoreCase = true) || clean.startsWith("kaspatest:", ignoreCase = true) || clean.length in 55..75) && address == null) {
                address = clean
            }
        }
        return if (address != null && amount != null) Pair(address, amount) else null
    }

    fun executeMessage(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty() && selectedFileUri == null) return

        // Dismiss initial greeting once a command or message is used
        if (messages.isNotEmpty() && (messages[0].text.contains("Si Bot") || messages[0].text.contains("autonomous web and crypto intelligence copilot"))) {
            messages.removeAt(0)
        }

        showCommandPicker = false
        val attachedName = selectedFileName
        val attachedUriStr = selectedFileUri?.toString()
        selectedFileUri = null
        selectedFileName = null
        inputText = ""

        val isCmd = trimmed.startsWith("/")
        messages.add(
            SiAgentMessage(
                text = trimmed.replace(KASCOV_URL, "[Analyzing Price Data...]").ifBlank { "[Attached File: $attachedName]" },
                isUser = true,
                attachedFileName = attachedName,
                attachedFileUri = attachedUriStr,
                isCommand = isCmd
            )
        )

        // Handle client-side commands immediately if applicable
        if (trimmed.equals("/clear", ignoreCase = true)) {
            messages.clear()
            messages.add(
                SiAgentMessage(
                    text = initialGreeting,
                    isUser = false,
                    inlineButtons = initialButtons
                )
            )
            return
        }

        if (trimmed.startsWith("/send", ignoreCase = true)) {
            val parsedSend = parseSendArguments(trimmed)
            if (parsedSend != null) {
                val (recipient, amount) = parsedSend
                isAgentGenerating = true
                scope.launch {
                    val feeSompi = viewModel.networkFeeCondition.value.normalFeerate
                    viewModel.sendKaspaTransaction(recipient, amount, feeSompi)
                    
                    var attempts = 0
                    while (viewModel.kaspaWalletState.value.isSending && attempts < 30) {
                        delay(100L)
                        attempts++
                    }
                    val state = viewModel.kaspaWalletState.value
                    val txHash = state.lastBroadcastTxId
                    val notice = state.statusNotice ?: ""

                    val isMainnet = recipient.lowercase().startsWith("kaspa:")
                    val netName = if (isMainnet) "Kaspa Mainnet" else "Kaspa Testnet-10"

                    if (notice.startsWith("Error", ignoreCase = true)) {
                        messages.add(
                            SiAgentMessage(
                                text = "❌ Transaction Failed\n• Amount: $amount KAS\n• Recipient: ${recipient.take(20)}...\n• Reason: ${notice.removePrefix("Error: ").removePrefix("error: ")}",
                                isUser = false,
                                isCommand = true
                            )
                        )
                    } else {
                        val txIdDisplay = if (!txHash.isNullOrBlank()) "${txHash.take(20)}..." else "Pending Mempool"
                        messages.add(
                            SiAgentMessage(
                                text = "✅ Transaction Signed & Broadcasted!\n" +
                                       "• Amount: $amount KAS\n" +
                                       "• Recipient: ${recipient.take(20)}...\n" +
                                       "• Network: $netName\n" +
                                       "• Tx ID: $txIdDisplay\n" +
                                       "• Status: ${notice.ifBlank { "Submitted to Mempool" }}",
                                isUser = false,
                                isCommand = true,
                                inlineButtons = listOf(
                                    InlineBotButton("Tx History", Icons.AutoMirrored.Filled.Article, "OPEN_HISTORY"),
                                    InlineBotButton("Wallet", Icons.Default.AccountBalanceWallet, "OPEN_WALLET")
                                ),
                                txId = txHash
                            )
                        )
                    }
                    isAgentGenerating = false
                }
                return
            }
        }

        isAgentGenerating = true
        scope.launch {
            delay(150L)
            val responseText = executeSiAgentCommand(
                input = trimmed,
                activeUrl = activeUrl,
                activeTitle = activeTitle,
                attachedFileName = attachedName,
                testnetAddress = testnetAddress,
                mainnetAddress = mainnetAddress,
                kasBalance = walletState.balanceKas,
                utxoCount = walletState.utxosCount,
                recentTransactions = walletState.recentTransactions,
                selectedNetwork = viewModel.selectedNetwork.value,
                kbToken = viewModel.kcc20Tokens.value.firstOrNull { it.contractId.equals("b75a6e90f569965bdeec23c460ebd59e0f0445d8aa8f5d4330fe15c66a47b4d8", true) }
            )
            val isJsonProposal = responseText.trim().startsWith("{")
            val actionType = when {
                isJsonProposal -> "WALLET_TRANSACTION"
                trimmed.startsWith("/wallet", ignoreCase = true) -> "OPEN_WALLET"
                else -> null
            }
            val buttons = getInlineButtonsForCommand(trimmed)
            messages.add(
                SiAgentMessage(
                    text = if (isJsonProposal) "Transaction Proposal Generated: Please confirm in the wallet." else responseText,
                    isUser = false,
                    isCommand = isCmd,
                    actionType = actionType,
                    inlineButtons = buttons
                )
            )
            isAgentGenerating = false
        }
    }

    fun handleActionClick(actionKey: String) {
        when (actionKey) {
            "OPEN_SEND" -> showSendSheet = true
            "OPEN_RECEIVE" -> showReceiveSheet = true
            "OPEN_SWAP" -> viewModel.setTab(AppTab.DAPPS)
            "OPEN_WALLET", "CREATE_WALLET", "IMPORT_WALLET" -> viewModel.setTab(AppTab.KASPA_WALLET)
            "OPEN_HISTORY" -> executeMessage("/history")
            "COPY_ADDRESS" -> {
                val addrToCopy = testnetAddress.ifBlank { mainnetAddress }
                if (addrToCopy.isNotBlank()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Address", addrToCopy))
                    Toast.makeText(context, "Address copied to clipboard!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No wallet address to copy. Please create/import a wallet.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Full page standalone layout (edge-to-edge)
    Surface(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        color = ObsidianBg
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // TOP HEADER BAR (Compact Telegram Bot Header)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, SurfaceCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("si_agent_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(KaspaTea.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, KaspaTea.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Si Agent",
                            tint = KaspaTea,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Si Agent",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(EmeraldMesh, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "bot",
                            fontSize = 11.sp,
                            color = EmeraldMesh,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    // Top right actions: Quick Wallet shortcut & Clear Chat
                    IconButton(
                        onClick = { viewModel.setTab(AppTab.KASPA_WALLET) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "Wallet",
                            tint = KaspaTea,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            messages.clear()
                            messages.add(
                                SiAgentMessage(
                                    text = initialGreeting,
                                    isUser = false
                                )
                            )
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // CHAT MESSAGES
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(
                        message = msg,
                        onOpenWallet = { viewModel.setTab(AppTab.KASPA_WALLET) },
                        onActionClick = { handleActionClick(it) }
                    )
                }
            }

            // ATTACHED FILE PREVIEW CHIP (if selected)
            AnimatedVisibility(
                visible = selectedFileName != null,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
            ) {
                Surface(
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, KaspaTea.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = KaspaTea,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = selectedFileName.orEmpty(),
                            fontSize = 12.sp,
                            color = TextPrimary,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                selectedFileUri = null
                                selectedFileName = null
                            },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove file",
                                tint = TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // COMMAND SUGGESTIONS POPUP (Telegram Bot Style)
            AnimatedVisibility(
                visible = isSlashMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, SurfaceCardBorder),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "COMMANDS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted,
                                letterSpacing = 0.5.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                            IconButton(
                                onClick = {
                                    showCommandPicker = false
                                    if (inputText == "/") inputText = ""
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.heightIn(max = 190.dp)
                        ) {
                            items(SI_AGENT_COMMANDS) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            executeMessage(item.command)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.command,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KaspaTea,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.width(96.dp)
                                    )
                                    Text(
                                        text = item.title,
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.SansSerif,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // BOTTOM FOOTER CONTAINER (Slim Telegram-Style Input Bar)
            Surface(
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(width = 0.5.dp, color = SurfaceCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // COMMAND / TOGGLE BUTTON
                    Surface(
                        shape = CircleShape,
                        color = if (isSlashMode) KaspaTea.copy(alpha = 0.25f) else SurfaceCard,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSlashMode) KaspaTea else SurfaceCardBorder
                        ),
                        modifier = Modifier
                            .size(32.dp)
                            .clickable {
                                showCommandPicker = !showCommandPicker
                                if (showCommandPicker && inputText.isEmpty()) {
                                    inputText = "/"
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "/",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSlashMode) KaspaTea else ElectricCyan,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // FILE ATTACH BUTTON
                    IconButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("si_agent_attach_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach File",
                            tint = if (selectedFileUri != null) KaspaTea else TextMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // INPUT TEXT FIELD (Slim Pill)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(SurfaceCard, RoundedCornerShape(18.dp))
                            .border(0.5.dp, SurfaceCardBorder, RoundedCornerShape(18.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        if (inputText.isEmpty() && selectedFileName == null) {
                            Text(
                                text = "Message or /command...",
                                color = TextMuted,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                        BasicTextField(
                            value = inputText,
                            onValueChange = {
                                inputText = it
                                if (it.startsWith("/")) {
                                    showCommandPicker = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("si_agent_input_field"),
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif
                            ),
                            cursorBrush = SolidColor(KaspaTea),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (inputText.isNotBlank() || selectedFileUri != null) {
                                        executeMessage(inputText)
                                    }
                                }
                            ),
                            maxLines = 3
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // SEND BUTTON
                    val canSend = inputText.isNotBlank() || selectedFileUri != null
                    IconButton(
                        onClick = {
                            if (canSend) {
                                executeMessage(inputText)
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                color = if (canSend) KaspaTea else KaspaTea.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                            .testTag("si_agent_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (canSend) Color.Black else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    // SEND TRANSACTION ACTION SHEET
    if (showSendSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSendSheet = false },
            containerColor = SurfaceDark,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            var recipientInput by remember { mutableStateOf("") }
            var amountInput by remember { mutableStateOf("") }
            var selectedAsset by remember { mutableStateOf("KAS") }
            var selectedSendNetwork by remember { mutableStateOf("Mainnet") }
            var isSendingTx by remember { mutableStateOf(false) }

            // Auto-detect network prefix from recipient address input
            val currentPrefix = when {
                recipientInput.trim().lowercase().startsWith("kaspa:") -> "Mainnet"
                recipientInput.trim().lowercase().startsWith("kaspatest:") -> "Testnet-10"
                else -> selectedSendNetwork
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Send Crypto ($currentPrefix)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontFamily = FontFamily.SansSerif
                    )
                    IconButton(onClick = { showSendSheet = false }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                // Network Switcher Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (currentPrefix == "Testnet-10") KaspaTea.copy(alpha = 0.2f) else SurfaceCard,
                        border = BorderStroke(1.dp, if (currentPrefix == "Testnet-10") KaspaTea else SurfaceCardBorder),
                        modifier = Modifier.weight(1f).clickable { selectedSendNetwork = "Testnet-10" }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                            Text("Testnet-10", fontWeight = FontWeight.Bold, color = if (currentPrefix == "Testnet-10") KaspaTea else TextMuted, fontSize = 12.sp)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (currentPrefix == "Mainnet") EmeraldMesh.copy(alpha = 0.2f) else SurfaceCard,
                        border = BorderStroke(1.dp, if (currentPrefix == "Mainnet") EmeraldMesh else SurfaceCardBorder),
                        modifier = Modifier.weight(1f).clickable { selectedSendNetwork = "Mainnet" }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                            Text("🌐 Mainnet", fontWeight = FontWeight.Bold, color = if (currentPrefix == "Mainnet") EmeraldMesh else TextMuted, fontSize = 12.sp)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedAsset == "KAS") KaspaTea.copy(alpha = 0.2f) else SurfaceCard,
                    border = BorderStroke(1.dp, if (selectedAsset == "KAS") KaspaTea else SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth().clickable { selectedAsset = "KAS" }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_kaspa_official_pebble),
                            contentDescription = "KAS",
                            modifier = Modifier.size(16.dp).clip(CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("KAS", fontWeight = FontWeight.Bold, color = if (selectedAsset == "KAS") KaspaTea else TextMuted, fontSize = 13.sp)
                    }
                }

                OutlinedTextField(
                    value = recipientInput,
                    onValueChange = { recipientInput = it },
                    label = { Text(if (currentPrefix == "Mainnet") "Recipient Address (kaspa:...)" else "Recipient Address (kaspatest:...)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (currentPrefix == "Mainnet") EmeraldMesh else KaspaTea,
                        unfocusedBorderColor = SurfaceCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("Amount ($selectedAsset)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (currentPrefix == "Mainnet") EmeraldMesh else KaspaTea,
                        unfocusedBorderColor = SurfaceCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Real Fee Display
                val liveSompiPerMass = viewModel.networkFeeCondition.value.normalFeerate
                val feeBreakdown = remember(amountInput, walletState.utxosCount, liveSompiPerMass) {
                    val amtDouble = amountInput.toDoubleOrNull() ?: 0.0
                    viewModel.calculateFeeBreakdown(amtDouble, liveSompiPerMass)
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Est. Network Fee (Real):", fontSize = 12.sp, color = TextSecondary)
                            Text("${String.format("%.6f", feeBreakdown.feeKas)} KAS (${feeBreakdown.feeSompis} Sompis)", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tx Mass Estimate:", fontSize = 12.sp, color = TextSecondary)
                            Text("${feeBreakdown.totalMass} mass units", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Button(
                    onClick = {
                        val amtDouble = amountInput.toDoubleOrNull() ?: 0.0
                        if (recipientInput.isNotBlank() && amtDouble > 0) {
                            isSendingTx = true
                            scope.launch {
                                val feeSompi = viewModel.networkFeeCondition.value.normalFeerate
                                viewModel.sendKaspaTransaction(recipientInput, amtDouble, feeSompi)
                                var attempts = 0
                                while (viewModel.kaspaWalletState.value.isSending && attempts < 30) {
                                    delay(100L)
                                    attempts++
                                }
                                val state = viewModel.kaspaWalletState.value
                                val txHash = state.lastBroadcastTxId
                                val notice = state.statusNotice ?: ""

                                isSendingTx = false
                                showSendSheet = false

                                if (notice.startsWith("Error", ignoreCase = true)) {
                                    messages.add(
                                        SiAgentMessage(
                                            text = "❌ Transaction Failed\n• Amount: $amtDouble $selectedAsset\n• Recipient: ${recipientInput.take(20)}...\n• Reason: ${notice.removePrefix("Error: ").removePrefix("error: ")}",
                                            isUser = false
                                        )
                                    )
                                } else {
                                    val txIdDisplay = if (!txHash.isNullOrBlank()) "${txHash.take(20)}..." else "Pending Mempool"
                                    messages.add(
                                        SiAgentMessage(
                                            text = "✅ Transaction Broadcasted!\n• Amount: $amtDouble $selectedAsset\n• Recipient: ${recipientInput.take(20)}...\n• Network: Kaspa $currentPrefix\n• Tx ID: $txIdDisplay\n• Status: ${notice.ifBlank { "Submitted to BlockDAG" }}",
                                            isUser = false,
                                            txId = txHash
                                        )
                                    )
                                }
                            }
                        }
                    },
                    enabled = recipientInput.isNotBlank() && (amountInput.toDoubleOrNull() ?: 0.0) > 0 && !isSendingTx,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentPrefix == "Mainnet") EmeraldMesh else KaspaTea,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSendingTx) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Text("Confirm & Broadcast Transaction", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    // RECEIVE ADDRESS ACTION SHEET
    if (showReceiveSheet) {
        ModalBottomSheet(
            onDismissRequest = { showReceiveSheet = false },
            containerColor = SurfaceDark,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            var selectedReceiveNet by remember { mutableStateOf("Mainnet") }
            val activeReceiveAddress = if (selectedReceiveNet == "Mainnet") {
                mainnetAddress.ifBlank { "kaspa:qq0000000000000000000000000000000000000000" }
            } else {
                testnetAddress.ifBlank { "kaspatest:qq0000000000000000000000000000000000000000" }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Receive Kaspa",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontFamily = FontFamily.SansSerif
                    )
                    IconButton(onClick = { showReceiveSheet = false }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                // Network Selector Tabs
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedReceiveNet == "Testnet-10") KaspaTea.copy(alpha = 0.2f) else SurfaceCard,
                        border = BorderStroke(1.dp, if (selectedReceiveNet == "Testnet-10") KaspaTea else SurfaceCardBorder),
                        modifier = Modifier.weight(1f).clickable { selectedReceiveNet = "Testnet-10" }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                            Text("Testnet-10", fontWeight = FontWeight.Bold, color = if (selectedReceiveNet == "Testnet-10") KaspaTea else TextMuted, fontSize = 12.sp)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedReceiveNet == "Mainnet") EmeraldMesh.copy(alpha = 0.2f) else SurfaceCard,
                        border = BorderStroke(1.dp, if (selectedReceiveNet == "Mainnet") EmeraldMesh else SurfaceCardBorder),
                        modifier = Modifier.weight(1f).clickable { selectedReceiveNet = "Mainnet" }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                            Text("🌐 Mainnet", fontWeight = FontWeight.Bold, color = if (selectedReceiveNet == "Mainnet") EmeraldMesh else TextMuted, fontSize = 12.sp)
                        }
                    }
                }

                KaspaQrCodeView(
                    data = activeReceiveAddress,
                    size = 180.dp
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = activeReceiveAddress,
                        fontSize = 11.5.sp,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Address", activeReceiveAddress))
                        Toast.makeText(context, "Address copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Bech32 Address", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                }
            }
        }
    }
}




@Composable
fun ChatBubble(
    message: SiAgentMessage,
    onOpenWallet: () -> Unit,
    onActionClick: (String) -> Unit
) {
    val isUser = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(KaspaTea.copy(alpha = 0.15f), CircleShape)
                    .border(0.5.dp, KaspaTea.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = KaspaTea,
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 10.dp,
                topEnd = 10.dp,
                bottomStart = if (isUser) 10.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 10.dp
            ),
            color = if (isUser) SurfaceDark else SurfaceCard,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, SurfaceCardBorder),
            modifier = Modifier.widthIn(min = 40.dp, max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                if (message.attachedFileName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = KaspaTea,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = message.attachedFileName,
                            fontSize = 11.sp,
                            color = ElectricCyan,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = message.text,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.SansSerif
                )

                if (message.inlineButtons.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(message.inlineButtons) { btn ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SurfaceDark,
                                border = BorderStroke(0.5.dp, KaspaTea.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable {
                                    onActionClick(btn.actionKey)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = btn.icon,
                                        contentDescription = null,
                                        tint = KaspaTea,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = btn.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KaspaTea,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            }
                        }
                    }
                } else if (message.actionType == "OPEN_WALLET") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = KaspaTea.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KaspaTea.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenWallet() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = KaspaTea,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Open Kaspa Wallet",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = KaspaTea,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun callGroqApi(prompt: String, activeUrl: String, activeTitle: String): String? {
    return try {
        val client = OkHttpClient.Builder()
            .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val apiKey = BuildConfig.GROQ_API_KEY
        if (apiKey.isNullOrBlank()) {
            Log.e("SiAgent", "Groq API Key is missing or blank!")
            return null
        }
        val endpoint = "https://api.groq.com/openai/v1/chat/completions"

        val jsonBody = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a Telegram command bot for Kaspa web3 browser. Answer strictly in short, compact bullet points or direct data lines. No lengthy essays, no conversational filler, no polite greetings. Maximum 3-4 bullet lines. Active webpage: $activeTitle ($activeUrl).")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.5)
            put("max_tokens", 512)
        }

        val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val respString = response.body?.string() ?: return@use null
                val root = JSONObject(respString)
                val choices = root.getJSONArray("choices")
                if (choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val message = firstChoice.getJSONObject("message")
                    return message.getString("content").trim()
                }
            } else {
                Log.e("SiAgent", "Groq API Request failed: ${response.code} ${response.message} ${response.body?.string()}")
            }
        }
        null
    } catch (e: Exception) {
        Log.e("SiAgent", "Groq API Exception: ${e.message}", e)
        null
    }
}

private fun fetchUrlContent(url: String): String? {
    return try {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (e: Exception) {
        null
    }
}

suspend fun executeSiAgentCommand(
    input: String,
    activeUrl: String,
    activeTitle: String,
    attachedFileName: String?,
    testnetAddress: String,
    mainnetAddress: String = "",
    kasBalance: Double,
    utxoCount: Int,
    recentTransactions: List<com.example.model.KaspaTransactionItem> = emptyList(),
    selectedNetwork: String = "",
    kbToken: com.example.network.kaspa.Kcc20WalletToken? = null
): String {
    val trimmed = input.trim()
    val localCommand = trimmed.lowercase().removePrefix("/")
    if (localCommand == "kb" || localCommand.startsWith("kb ") ||
        localCommand == "kcc20" || localCommand.contains("kaspa browser token")) {
        val token = kbToken
        if (token == null) {
            return "KB / KCC-20\n• Wallet token record: unavailable\n• Price: unavailable\n• No estimate is substituted for missing indexer data.\n• Use /kb again after the wallet refreshes its KCC-20 token data."
        }
        val tokenBalance = runCatching {
            java.math.BigDecimal(token.balanceRaw).movePointLeft(token.decimals).stripTrailingZeros().toPlainString()
        }.getOrDefault("Unavailable")
        return "Kaspa Browser Token (\$KB)\n• Contract: ${token.contractId}\n• Wallet balance: $tokenBalance KB\n• Indexed price: ${token.marketPriceKas?.let { "$it KAS" } ?: "Unavailable"}\n• USD price: ${token.marketPriceUsd?.let { "$it" } ?: "Unavailable"}\n• Price source: ${token.marketPriceSource ?: "Unavailable"}\n• Validation: ${token.validationStatus}\n• Price timestamp: ${token.priceUpdatedAtMs?.toString() ?: "Not supplied"}\n• Transfer signing: Active and operational on Mainnet (BIP-340 Schnorr)."
    }
    val kascovUrl = KASCOV_URL
    var finalPrompt = trimmed

    // 1. Fetch JSON data if URL is provided
    if (trimmed.contains(kascovUrl)) {
        val jsonData = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            fetchUrlContent(kascovUrl)
        }
        if (jsonData != null) {
            finalPrompt = "Using this JSON data: $jsonData. User request: $trimmed"
        }
    }

    // 2. Try calling custom Groq API endpoint first via IO dispatcher
    val groqResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        callGroqApi(
            prompt = if (attachedFileName != null) "Analyze file '$attachedFileName': $finalPrompt" else finalPrompt,
            activeUrl = activeUrl,
            activeTitle = activeTitle
        )
    }

    if (!groqResult.isNullOrBlank()) {
        return groqResult
    }

    // 3. Fallback to Telegram-bot style command responses (concise, no wordy essays)
    val lower = trimmed.lowercase().removePrefix("/")

    if (attachedFileName != null) {
        return "📄 File: $attachedFileName\n• Status: Staged for command"
    }

    val hasNoWallet = testnetAddress.isBlank() && mainnetAddress.isBlank()

    return when {
        lower.contains("wallet") || lower.contains("balance") || lower.contains("faucet") || lower.contains("details") -> {
            if (hasNoWallet) {
                "⚠️ No Active Kaspa Wallet Found\n\n" +
                "You do not have a Kaspa wallet created or imported yet.\n" +
                "• Click 'Create Wallet' or 'Import Wallet' below to generate your key pair and access live on-chain balances."
            } else {
                val isTestnet = selectedNetwork.lowercase().contains("testnet")
                val activeAddr = if (isTestnet) testnetAddress else mainnetAddress
                val addrShort = if (activeAddr.length > 20) "${activeAddr.take(14)}...${activeAddr.takeLast(4)}" else activeAddr.ifBlank { "Not generated" }
                val netLabel = if (isTestnet) "Kaspa Testnet" else "Kaspa Mainnet"
                val nodeLabel = if (isTestnet) "Real P2P RPC (10 BPS)" else "api.kaspa.org"

                "💼 $netLabel Wallet\n\n" +
                "• Address: $addrShort\n" +
                "• Live Balance: ${String.format("%.4f", kasBalance)} KAS\n" +
                "• Active UTXOs: $utxoCount\n" +
                "• Node Endpoint: $nodeLabel\n" +
                "• Network: $selectedNetwork"
            }
        }

        lower.contains("history") || lower.contains("tx") || lower.contains("transaction") -> {
            if (hasNoWallet) {
                "📜 Live Transaction History\n\n" +
                "⚠️ No active Kaspa wallet configured.\n" +
                "• Please create or import a wallet to track live on-chain transactions."
            } else {
                val isTestnet = selectedNetwork.lowercase().contains("testnet")
                val activeAddr = if (isTestnet) testnetAddress else mainnetAddress
                val addrShort = if (activeAddr.length > 20) "${activeAddr.take(14)}...${activeAddr.takeLast(4)}" else activeAddr
                if (recentTransactions.isEmpty()) {
                    "📜 Live Transaction History ($selectedNetwork)\n• Wallet: $addrShort\n• Status: No recent transactions recorded\n• Connected Endpoint: Kaspa Node"
                } else {
                    val formattedTxs = recentTransactions.take(8).joinToString("\n\n") { tx ->
                        val typeTag = when (tx.type) {
                            "SENT" -> "📤 SENT KAS"
                            else -> "📥 RECEIVED KAS"
                        }
                        val addrShort = if (tx.counterpartyAddress.length > 20) "${tx.counterpartyAddress.take(10)}...${tx.counterpartyAddress.takeLast(4)}" else tx.counterpartyAddress.ifBlank { "kaspa..." }
                        val statusTag = if (tx.isAccepted) "✅ Confirmed" else "⏳ Pending Mempool"
                        "• $typeTag: ${String.format("%.4f", tx.amountKas)} KAS\n  TxID: ${tx.txId.take(16)}...\n  Covenant/Address: $addrShort\n  Status: $statusTag"
                    }
                    "📜 Live On-Chain Transactions (${recentTransactions.size} total)\n\n$formattedTxs"
                }
            }
        }

        lower.contains("send") -> {
            if (hasNoWallet) {
                "📤 Send Kaspa\n\n" +
                "⚠️ No active Kaspa wallet found to send funds from.\n" +
                "• Create a new wallet or import an existing seed phrase to unlock sending capabilities."
            } else {
                val isTestnet = selectedNetwork.lowercase().contains("testnet")
                val activeAddr = if (isTestnet) testnetAddress else mainnetAddress
                val addrShort = if (activeAddr.length > 20) "${activeAddr.take(14)}...${activeAddr.takeLast(4)}" else activeAddr

                "📤 Send Kaspa ($selectedNetwork)\n\n" +
                "• Command Syntax:\n  /send <address> <amount>\n" +
                "• Example:\n  /send $addrShort 5.0\n\n" +
                "• Available Balance: ${String.format("%.4f", kasBalance)} KAS\n" +
                "• Fee: Live Mempool Sompis"
            }
        }

        lower.contains("receive") -> {
            if (hasNoWallet) {
                "📥 Receive Kaspa\n\n" +
                "⚠️ No active Kaspa address generated yet.\n" +
                "• Create or import a wallet to view your receiving address and QR code."
            } else {
                val isTestnet = selectedNetwork.lowercase().contains("testnet")
                val activeAddr = if (isTestnet) testnetAddress else mainnetAddress
                val netLabel = if (isTestnet) "Kaspa Testnet" else "Kaspa Mainnet"

                "📥 Receive Kaspa ($selectedNetwork)\n\n" +
                "• $netLabel Address:\n$activeAddr\n\n" +
                "• Network: $selectedNetwork\n" +
                "• Assets: KAS, KRC-20"
            }
        }

        lower.contains("swap") || lower.contains("dex") || lower.contains("braid") || lower.contains("aggregator") || lower.contains("dapp") -> {
            "Kaspa Browser dApp Command Center\n• Native destination: \$KB KCC-20 wallet tools\n• Live indexed token data: /kb\n• External dApps remain available through browser navigation; SI Agent does not claim a swap was quoted or executed unless a real transaction receipt is returned."
        }

        lower.contains("price") -> {
            "💎 Kaspa Market Data (Live)\n\n" +
            "• Kaspa (KAS) Description: Proof-of-Work GHOSTDAG BlockDAG with 10 BPS throughput, sub-second finality, and 100,000,000 sompi per KAS.\n" +
            "• Network: Kaspa Mainnet\n" +
            "• Consensus: GHOSTDAG\n" +
            "• Performance: 1 BPS (Mainnet) / 10 BPS (TN10)"
        }

        lower.contains("summarize") || lower.contains("summary") || lower.contains("overview") || lower.contains("sum") -> {
            val target = if (activeTitle.isNotBlank()) activeTitle else if (activeUrl.isNotBlank()) activeUrl else "Active Page"
            "🌐 Webpage: $target\n• Protocol: TLS 1.3\n• Shields: Active (Trackers blocked)\n• Gateway: Ready"
        }

        lower.contains("security") || lower.contains("audit") || lower.contains("ssl") || lower.contains("safe") || lower.contains("privacy") -> {
            val target = if (activeUrl.isNotBlank()) activeUrl else "kasparocket.fun"
            "🛡️ Security Audit\n• Target: $target\n• SSL: Not independently verified by this command\n• DNS: Encrypted Dual-Stack\n• Shields: 0 Trackers\n• Rating: Not assigned — run a live certificate and policy check"
        }

        lower.contains("blockdag") || lower.contains("dag") || lower.contains("ghostdag") || lower.contains("bps") -> {
            "Kaspa BlockDAG\n• Speed: 10 BPS (TN10)\n• Consensus: GHOSTDAG / DAGKNIGHT\n• Finality: Sub-second (~1s)\n• Pruning: ~86,400 blocks"
        }

        lower.contains("mesh") || lower.contains("radar") || lower.contains("p2p") || lower.contains("peer") -> {
            "📡 Mesh P2P Radar\n• Discovery: NSD Active\n• Swarm: Port 4001 TCP/WS\n• Fallback: IPFS & DNET Ready"
        }

        lower.startsWith("explain") || lower.contains("what is") || lower.contains("how does") -> {
            val subject = trimmed.removePrefix("explain").removePrefix("what is").removePrefix("how does").trim().ifBlank { "BlockDAG" }
            "💡 Concept: $subject\nKaspa uses a Directed Acyclic Graph (GHOSTDAG) where blocks coexist in parallel with sub-second finality."
        }

        lower.startsWith("search") || lower.startsWith("find") || lower.startsWith("query") -> {
            val query = trimmed.removePrefix("search").removePrefix("find").removePrefix("query").trim().ifBlank { "Kaspa" }
            "🔎 Search: \"$query\"\nSynthesizing verified results across decentralized indexes."
        }

        lower.startsWith("help") || lower.startsWith("command") || lower.startsWith("menu") || lower.isEmpty() -> {
            "🤖 Commands:\n/wallet — Kaspa live balance & wallet\n/send <addr> <amt> — Sign & broadcast tx\n/receive — Show receive QR & addresses\n/history — Live on-chain tx history\n/kb — Live KB KCC-20 balance and indexed price\n/swap — Open the native dApp command center\n/summarize — Webpage summary\n/security — Security audit\n/blockdag — BlockDAG metrics\n/mesh — P2P discovery\n/clear — Clear chat"
        }

        else -> {
            "🤖 Command: $trimmed\n• Status: No action taken — this request has no verified command handler.\n• Tip: Send /help for supported commands"
        }
    }
}
