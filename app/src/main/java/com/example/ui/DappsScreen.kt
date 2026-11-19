package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.kaspa.Kcc20WalletToken
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

@Composable
fun DappsScreen(
    viewModel: DecentralViewModel,
    modifier: Modifier = Modifier
) {
    val tokens by viewModel.kcc20Tokens.collectAsState()
    val loading by viewModel.isLoadingKcc20.collectAsState()
    val selectedNetwork by viewModel.selectedNetwork.collectAsState()
    val walletState by viewModel.kaspaWalletState.collectAsState()
    val testnetAddress by viewModel.testnetAddress.collectAsState()
    val kcc20TransferState by viewModel.kcc20TransferState.collectAsState()

    var showTrackDialog by remember { mutableStateOf(false) }
    var sendModalToken by remember { mutableStateOf<Kcc20WalletToken?>(null) }

    BackHandler {
        if (sendModalToken != null) {
            if (!kcc20TransferState.isSending) {
                sendModalToken = null
                viewModel.clearKcc20TransferState()
            }
        } else if (showTrackDialog) {
            showTrackDialog = false
        } else {
            viewModel.setTab(AppTab.KASPA_WALLET)
        }
    }
    LaunchedEffect(Unit) { viewModel.refreshKcc20Tokens() }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("dapps_screen"),
        containerColor = ObsidianBg,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.setTab(AppTab.KASPA_WALLET) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to wallet", tint = TextPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text("KCC-20 Tokens", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(selectedNetwork, color = TextSecondary, fontSize = 12.sp)
                }
                IconButton(
                    onClick = { showTrackDialog = true },
                    modifier = Modifier.testTag("track_kcc20_contract_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Track KCC-20 covenant contract ID", tint = KaspaTea)
                }
                IconButton(onClick = { viewModel.refreshKcc20Tokens() }) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ElectricCyan)
                    else Icon(Icons.Default.Refresh, contentDescription = "Refresh verified KCC-20 holdings", tint = ElectricCyan)
                }
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (loading && tokens.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            } else if (tokens.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "No verified KCC-20 holdings were found for this wallet on $selectedNetwork. Refresh or track a custom covenant contract ID.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = { showTrackDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = SurfaceDark, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Track Contract ID", color = SurfaceDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                tokens.forEach { token ->
                    val rawBd = token.balanceRaw.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val hasBalance = rawBd > BigDecimal.ZERO
                    val formattedBal = formatKcc20BalanceDisplay(token.balanceRaw, token.decimals)
                    val displayTicker = token.ticker.ifBlank { token.contractId.take(6).uppercase().ifBlank { "KCC-20" } }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    displayTicker,
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("$formattedBal $displayTicker", color = ElectricCyan, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text("${token.name.ifBlank { displayTicker }} • ${token.decimals} decimals", color = TextSecondary, fontSize = 12.sp)
                            Text("Contract: ${token.contractId}", color = TextMuted, fontSize = 10.sp)
                            Text("Source status: ${token.validationStatus}", color = TextMuted, fontSize = 10.sp)
                            if (hasBalance) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        viewModel.clearKcc20TransferState()
                                        sendModalToken = token
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("dapps_send_kcc20_${token.contractId.take(8)}")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = SurfaceDark, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Send $displayTicker", color = SurfaceDark, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTrackDialog) {
        TrackKcc20ContractDialog(
            onTrackContract = { id -> viewModel.trackKcc20Contract(id) },
            onDismiss = { showTrackDialog = false }
        )
    }

    sendModalToken?.let { activeToken ->
        SendKcc20ModalDialog(
            token = activeToken,
            senderAddress = testnetAddress,
            gasBalanceKas = walletState.balanceKas,
            selectedNetwork = selectedNetwork,
            transferState = kcc20TransferState,
            onSend = { toAddr, amt -> viewModel.sendKcc20Transaction(activeToken, toAddr, amt) },
            onDismiss = {
                sendModalToken = null
                viewModel.clearKcc20TransferState()
            }
        )
    }
}

