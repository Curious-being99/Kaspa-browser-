package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.math.BigDecimal
import java.math.RoundingMode

private const val KB_CONTRACT_ID = "b75a6e90f569965bdeec23c460ebd59e0f0445d8aa8f5d4330fe15c66a47b4d8"

@Composable
fun DappsScreen(
    viewModel: DecentralViewModel,
    modifier: Modifier = Modifier
) {
    var recipient by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val tokens by viewModel.kcc20Tokens.collectAsState()
    val loading by viewModel.isLoadingKcc20.collectAsState()
    val walletAddress by viewModel.mainnetAddress.collectAsState()
    val kb = tokens.firstOrNull { it.contractId.equals(KB_CONTRACT_ID, ignoreCase = true) }
    val balance = remember(kb) {
        runCatching {
            BigDecimal(kb?.balanceRaw ?: "0")
                .movePointLeft(kb?.decimals ?: 0)
                .stripTrailingZeros()
                .toPlainString()
        }.getOrDefault("—")
    }

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
                    Text("KCC-20 • $KB command center", color = TextSecondary, fontSize = 12.sp)
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
                .background(ObsidianBg).padding(horizontal = 18.dp, vertical = 12.dp),
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
                        BoxLogoKB()
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("\\$KB", color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold)
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
                        Text(kb?.marketPriceUsd?.let { "\\$it" } ?: "Unavailable", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = if (kb?.marketPriceKas != null) "Price source: ${kb.marketPriceSource ?: "Kascov"}" else "Live market quote unavailable — no estimated price is substituted.",
                        color = TextMuted,
                        fontSize = 10.5.sp
                    )
                }
            }

            Text("SEND $KB", color = KaspaTea, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("From wallet", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        text = walletAddress.ifBlank { "No mainnet wallet connected" },
                        color = TextPrimary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    OutlinedTextField(
                        value = recipient,
                        onValueChange = { recipient = it.trim() },
                        modifier = Modifier.fillMaxWidth().testTag("kb_recipient_input"),
                        label = { Text("Recipient Kaspa address") },
                        placeholder = { Text("kaspa:…") },
                        singleLine = false,
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
                    Button(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("kb_review_transaction_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KaspaTea,
                            disabledContainerColor = KaspaTea.copy(alpha = 0.25f),
                            disabledContentColor = TextMuted
                        )
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(17.dp))
                        Text("  Transfer signing not yet available", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                    Icon(Icons.Default.Security, contentDescription = null, tint = KaspaTea, modifier = Modifier.size(19.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Wallet signing protection", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "The wallet's PSKT signer can sign validated Kaspa transactions, but this repository does not yet contain a verified KB KCC-20 covenant transfer builder. Sending is intentionally disabled rather than broadcasting a plain KAS payment as a token transfer.",
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp
                        )
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

@Composable
private fun BoxLogoKB() {
    androidx.compose.foundation.Canvas(
        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF101A1C))
            .border(1.dp, KaspaTea.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
    ) {
        val stroke = size.width * 0.11f
        val cx = size.width * 0.5f
        drawLine(KaspaTea, androidx.compose.ui.geometry.Offset(cx, size.height * 0.22f), androidx.compose.ui.geometry.Offset(cx, size.height * 0.78f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(KaspaTea, androidx.compose.ui.geometry.Offset(cx, size.height * 0.5f), androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.28f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(KaspaTea, androidx.compose.ui.geometry.Offset(cx, size.height * 0.5f), androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.72f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(KaspaTea, size.width * 0.045f, androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.5f))
    }
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
