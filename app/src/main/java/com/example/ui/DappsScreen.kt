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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.AppTab
import com.example.viewmodel.DecentralViewModel

@Composable
fun DappsScreen(
    viewModel: DecentralViewModel,
    modifier: Modifier = Modifier
) {
    val tokens by viewModel.kcc20Tokens.collectAsState()
    val contractIds by viewModel.kcc20ContractIds.collectAsState()
    val loading by viewModel.isLoadingKcc20.collectAsState()
    val selectedNetwork by viewModel.selectedNetwork.collectAsState()
    var contractInput by remember { mutableStateOf("") }

    BackHandler { viewModel.setTab(AppTab.KASPA_WALLET) }
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
                IconButton(onClick = { viewModel.refreshKcc20Tokens() }) {
                    if (loading) CircularProgressIndicator()
                    else Icon(Icons.Default.Refresh, contentDescription = "Refresh KCC-20 tokens", tint = ElectricCyan)
                }
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Track KCC-20 contracts by covenant ID. Token balances and metadata are loaded from the configured indexer; no token is preselected or forced.",
                color = TextSecondary,
                fontSize = 12.sp
            )
            OutlinedTextField(
                value = contractInput,
                onValueChange = { contractInput = it },
                modifier = Modifier.fillMaxWidth().testTag("kcc20_contract_id_input"),
                label = { Text("KCC-20 covenant ID") },
                placeholder = { Text("64-character contract ID") },
                singleLine = true
            )
            Button(
                onClick = {
                    viewModel.addTrackedKcc20Contract(contractInput)
                    contractInput = ""
                },
                enabled = contractInput.trim().matches(Regex("[0-9a-fA-F]{64}")),
                modifier = Modifier.fillMaxWidth().testTag("add_kcc20_contract_button")
            ) {
                Text("Add KCC-20 token")
            }
            Text("Tracked contracts: ${contractIds.size}", color = TextMuted, fontSize = 11.sp)
            if (loading && tokens.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            } else if (tokens.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard), shape = RoundedCornerShape(14.dp)) {
                    Text(
                        "No KCC-20 token balances loaded. Add a contract ID above or refresh after selecting a wallet.",
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 12.sp
                    )
                }
            } else {
                tokens.forEach { token ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    token.ticker.ifBlank { "KCC-20" },
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(token.balanceFormatted, color = ElectricCyan, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text(token.name, color = TextSecondary, fontSize = 12.sp)
                            Text("Contract: ${token.contractId}", color = TextMuted, fontSize = 10.sp)
                            Text("Validation: ${token.validationStatus}", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
