package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.AppTab
import com.example.viewmodel.DecentralViewModel

@Composable
fun DappsScreen(
    viewModel: DecentralViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.setTab(AppTab.KASPA_WALLET) }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("dapps_screen"),
        containerColor = ObsidianBg
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("DApps", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("KCC-20 transfers", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Open a supported dApp in the browser to prepare a KCC-20 transfer. Review the transaction and approve signing and broadcast in the wallet. Token balances remain on the main wallet page.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
