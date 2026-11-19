package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.network.CryptoUtils
import com.example.network.kaspa.KaspaTransactionEngine
import com.example.network.kaspa.Kcc20WalletToken
import com.example.ui.theme.AmberCentral
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.KaspaTea
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RedTamper
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.DecentralViewModel
import java.math.BigDecimal

internal fun formatKcc20BalanceDisplay(raw: String, decimals: Int): String {
    val bd = raw.trim().toBigDecimalOrNull() ?: return "0"
    if (bd.compareTo(BigDecimal.ZERO) <= 0) return "0"
    val safeDec = decimals.coerceIn(0, 18)
    return bd.movePointLeft(safeDec).stripTrailingZeros().toPlainString()
}

internal enum class Kcc20AmountValidationError {
    NONE,
    EMPTY,
    INVALID_NUMBER,
    TOO_MANY_DECIMALS,
    INSUFFICIENT_BALANCE
}

internal fun validateKcc20SendAmount(
    amountInput: String,
    balanceRaw: String,
    decimals: Int
): Pair<BigDecimal?, Kcc20AmountValidationError> {
    val trimmed = amountInput.trim()
    if (trimmed.isBlank()) return null to Kcc20AmountValidationError.EMPTY
    val parsed = try {
        BigDecimal(trimmed)
    } catch (_: Exception) {
        return null to Kcc20AmountValidationError.INVALID_NUMBER
    }
    if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
        return null to Kcc20AmountValidationError.INVALID_NUMBER
    }
    val safeDec = decimals.coerceIn(0, 18)
    val baseUnits = try {
        parsed.movePointRight(safeDec).toBigIntegerExact()
    } catch (_: Exception) {
        return null to Kcc20AmountValidationError.TOO_MANY_DECIMALS
    }
    val availUnits = balanceRaw.trim().toBigIntegerOrNull() ?: java.math.BigInteger.ZERO
    if (baseUnits > availUnits) {
        return null to Kcc20AmountValidationError.INSUFFICIENT_BALANCE
    }
    return parsed to Kcc20AmountValidationError.NONE
}

@Composable
internal fun Kcc20SendTabContent(
    token: Kcc20WalletToken,
    senderAddress: String,
    gasBalanceKas: Double,
    selectedNetwork: String,
    isSending: Boolean,
    recipientInput: String,
    onRecipientChange: (String) -> Unit,
    amountInput: String,
    onAmountChange: (String) -> Unit,
    onSubmitClick: () -> Unit
) {
    val isMainnet = selectedNetwork.contains("Mainnet", ignoreCase = true) || senderAddress.startsWith("kaspa:", ignoreCase = true)
    val expectedPrefix = if (isMainnet) "kaspa" else "kaspatest"
    val gasSymbol = if (isMainnet) "KAS" else "tKAS"
    val ticker = token.ticker.ifBlank { token.contractId.take(6).uppercase().ifBlank { "KCC-20" } }
    val availableDisplay = remember(token.balanceRaw, token.decimals) {
        formatKcc20BalanceDisplay(token.balanceRaw, token.decimals)
    }

    val cleanRecipient = recipientInput.trim().lowercase()
    val isRecipientEntered = cleanRecipient.isNotBlank()
    val isPrefixMismatch = if (isMainnet) cleanRecipient.startsWith("kaspatest:") else cleanRecipient.startsWith("kaspa:")
    val isValidRecipientFormat = DecentralViewModel.isValidKaspaAddress(cleanRecipient) ||
        CryptoUtils.isValidKaspaAddress(cleanRecipient, expectedPrefix)
    val isAddressSuccess = isRecipientEntered && isValidRecipientFormat && !isPrefixMismatch
    val isAddressError = isRecipientEntered && (!isValidRecipientFormat || isPrefixMismatch)

    val (parsedAmount, amountErr) = remember(amountInput, token.balanceRaw, token.decimals) {
        validateKcc20SendAmount(amountInput, token.balanceRaw, token.decimals)
    }
    val isAmountError = amountInput.isNotBlank() && amountErr != Kcc20AmountValidationError.NONE
    val lowPlainGasWarning = gasBalanceKas < 0.21
    val canSend = !isSending && isAddressSuccess && parsedAmount != null && amountErr == Kcc20AmountValidationError.NONE

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Token, contentDescription = null, tint = KaspaTea, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(token.name.ifBlank { ticker }, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("L1 KCC-20 Covenant • ${token.decimals} decimals", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                        Text("$availableDisplay $ticker", color = KaspaTea, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(
                        text = "Covenant: ${token.contractId}",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = recipientInput,
                onValueChange = onRecipientChange,
                label = { Text("Recipient Address ($expectedPrefix:q...)") },
                placeholder = { Text("$expectedPrefix:q...") },
                supportingText = {
                    when {
                        isAddressSuccess -> Text("✓ Valid $selectedNetwork recipient address", color = KaspaTea, fontWeight = FontWeight.SemiBold)
                        isPrefixMismatch -> Text("Network mismatch: wallet is on $selectedNetwork ($expectedPrefix:)", color = RedTamper)
                        isAddressError -> Text("Enter a valid Kaspa P2PK address starting with $expectedPrefix:", color = RedTamper)
                        else -> Text("Recipient receives the token covenant cell on L1", color = TextMuted)
                    }
                },
                isError = isAddressError,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("kcc20_send_recipient_input"),
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
            OutlinedTextField(
                value = amountInput,
                onValueChange = onAmountChange,
                label = { Text("Amount ($ticker)") },
                placeholder = { Text(if (token.decimals == 0) "0" else "0.0") },
                isError = isAmountError,
                trailingIcon = {
                    Surface(
                        onClick = { onAmountChange(availableDisplay) },
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
                    when (amountErr) {
                        Kcc20AmountValidationError.INSUFFICIENT_BALANCE ->
                            Text("Insufficient balance: available $availableDisplay $ticker", color = RedTamper)
                        Kcc20AmountValidationError.TOO_MANY_DECIMALS ->
                            Text(
                                if (token.decimals == 0) "$ticker uses whole units (0 decimal places)"
                                else "$ticker supports at most ${token.decimals} decimal places",
                                color = RedTamper
                            )
                        Kcc20AmountValidationError.INVALID_NUMBER ->
                            Text("Enter a positive $ticker amount", color = RedTamper)
                        else ->
                            Text("Available: $availableDisplay $ticker", color = TextMuted)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("kcc20_send_amount_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KaspaTea,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    errorBorderColor = RedTamper
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (token.decimals == 0) KeyboardType.Number else KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                singleLine = true
            )
        }

        item {
            Surface(
                color = SurfaceDark,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (!lowPlainGasWarning) SurfaceCardBorder else AmberCentral.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "L1 Covenant Cell & v1 Fee Notice",
                        color = if (!lowPlainGasWarning) ElectricCyan else AmberCentral,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Executes a v1 covenant transfer (0.2 KAS cell deposit per output + v1 compute/transient fee). Available plain $gasSymbol: ${KaspaTransactionEngine.formatKas(gasBalanceKas)} $gasSymbol.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    if (lowPlainGasWarning) {
                        Text(
                            "Low plain $gasSymbol: partial transfers creating a change cell need ~0.21+ $gasSymbol; whole-cell or multi-cell merges use existing cell KAS when sufficient.",
                            color = AmberCentral,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = onSubmitClick,
                enabled = canSend,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KaspaTea,
                    disabledContainerColor = SurfaceCardBorder
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("kcc20_send_submit_button")
            ) {
                if (isSending) {
                    CircularProgressIndicator(color = SurfaceDark, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Signing & Broadcasting...", color = SurfaceDark, fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = SurfaceDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send $ticker", color = SurfaceDark, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun ConfirmKcc20SendDialog(
    token: Kcc20WalletToken,
    recipientAddress: String,
    amountDisplay: String,
    selectedNetwork: String,
    isSending: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val ticker = token.ticker.ifBlank { token.contractId.take(6).uppercase().ifBlank { "KCC-20" } }
    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        containerColor = SurfaceDark,
        title = {
            Text(
                "Confirm $ticker Transfer",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("$amountDisplay $ticker", color = KaspaTea, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text("KCC-20 Covenant Transfer • $selectedNetwork", color = TextSecondary, fontSize = 11.sp)
                    }
                }
                Column {
                    Text("TO ADDRESS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(recipientAddress, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Column {
                    Text("COVENANT ID", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(token.contractId, color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSending,
                colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp).testTag("kcc20_confirm_send_button")
            ) {
                Text("Sign & Broadcast", color = SurfaceDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending, modifier = Modifier.height(48.dp)) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
internal fun TrackKcc20ContractDialog(
    onTrackContract: (String) -> Result<String>,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text("Track KCC-20 Covenant", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste any 64-character hex KCC-20 covenant ID to scan holdings, metadata, and enable transfers.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        errorMsg = null
                    },
                    label = { Text("Covenant Contract ID (64 hex)") },
                    placeholder = { Text("b20f807bed62...") },
                    isError = errorMsg != null,
                    supportingText = errorMsg?.let { msg -> { Text(msg, color = RedTamper) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("track_kcc20_contract_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KaspaTea,
                        unfocusedBorderColor = SurfaceCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        errorBorderColor = RedTamper
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val res = onTrackContract(input)
                    res.fold(
                        onSuccess = { onDismiss() },
                        onFailure = { err -> errorMsg = err.message ?: "Invalid contract ID" }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp).testTag("track_kcc20_contract_confirm")
            ) {
                Text("Track Token", color = SurfaceDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
internal fun SendKcc20ModalDialog(
    token: Kcc20WalletToken,
    senderAddress: String,
    gasBalanceKas: Double,
    selectedNetwork: String,
    transferState: DecentralViewModel.Kcc20TransferUiState,
    onSend: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var recipient by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val ticker = token.ticker.ifBlank { token.contractId.take(6).uppercase().ifBlank { "KCC-20" } }

    Dialog(
        onDismissRequest = { if (!transferState.isSending) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !transferState.isSending)
    ) {
        Surface(
            color = ObsidianBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Send $ticker", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, enabled = !transferState.isSending) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                if (transferState.txId != null && transferState.contractId.equals(token.contractId, ignoreCase = true)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Broadcast Successful", color = KaspaTea, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${transferState.amountDisplay} $ticker sent",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "TxID: ${transferState.txId}",
                            color = ElectricCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Done", color = SurfaceDark, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    if (transferState.error != null && transferState.contractId.equals(token.contractId, ignoreCase = true)) {
                        Surface(
                            color = RedTamper.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, RedTamper.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Text(
                                transferState.error,
                                color = RedTamper,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Column(modifier = Modifier.height(480.dp)) {
                        Kcc20SendTabContent(
                            token = token,
                            senderAddress = senderAddress,
                            gasBalanceKas = gasBalanceKas,
                            selectedNetwork = selectedNetwork,
                            isSending = transferState.isSending,
                            recipientInput = recipient,
                            onRecipientChange = { recipient = it },
                            amountInput = amount,
                            onAmountChange = { amount = it },
                            onSubmitClick = { onSend(recipient, amount) }
                        )
                    }
                }
            }
        }
    }
}
