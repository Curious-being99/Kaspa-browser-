package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Token
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Krc20TokenBalance
import com.example.model.Krc20TokenMetadata
import com.example.model.Krc20TokenTransaction
import com.example.ui.theme.AmberCentral
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMesh
import com.example.ui.theme.KaspaTea
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RedTamper
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletBridge
import kotlinx.coroutines.launch

/**
 * Pinned Tokens Section displayed prominently on the Kaspa Wallet screen.
 */
@Composable
fun PinnedTokensSection(
    pinnedTokens: List<Krc20TokenBalance>,
    isLoading: Boolean,
    onTokenClick: (Krc20TokenBalance) -> Unit,
    onTogglePin: (String) -> Unit,
    onOpenSwap: (String) -> Unit,
    onAddTokenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("pinned_tokens_section")
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = KaspaTea
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (pinnedTokens.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceCard.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SurfaceCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No tokens added yet",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                pinnedTokens.forEach { token ->
                    PinnedTokenCard(
                        token = token,
                        onClick = { onTokenClick(token) },
                        onTogglePin = { onTogglePin(token.tick) },
                        onOpenSwap = { onOpenSwap(token.tick) }
                    )
                }
            }
        }
    }
}

@Composable
fun PinnedTokenCard(
    token: Krc20TokenBalance,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onOpenSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accentColor = when (token.tick.uppercase()) {
        "NACHO" -> AmberCentral
        "KASPER" -> VioletBridge
        "SOMI" -> KaspaTea
        "BOMB" -> RedTamper
        "KASPUMP" -> AmberCentral
        else -> KaspaTea
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, SurfaceCardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("pinned_token_card_${token.tick.lowercase()}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Token Avatar & Info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = token.tick.take(2).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = accentColor
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = token.tick,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = accentColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = (token.metadata?.protocol ?: "krc-20").uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentColor,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }

                            // Bonding Curve Status Pill
                            if (token.isBondingCurve) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AmberCentral.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = "Bonding Curve",
                                            tint = AmberCentral,
                                            modifier = Modifier.size(9.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "Curve %.0f%%".format(token.bondingCurveProgress),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AmberCentral
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        if (token.tokenId.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Token ID", token.tokenId))
                                }
                            ) {
                                Text(
                                    text = "ID: ${token.tokenId.take(6)}...${token.tokenId.takeLast(4)}",
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Token ID",
                                    tint = TextMuted,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "Standard Inscription",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                // Balance & Pin Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "%,.2f".format(token.balance),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = token.tick,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }


                }
            }

            // Bonding curve progress meter bar
            if (token.isBondingCurve) {
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Bonding Curve Progress",
                            fontSize = 9.5.sp,
                            color = TextMuted
                        )
                        Text(
                            text = "%.1f%% / 100%%".format(token.bondingCurveProgress),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberCentral
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { (token.bondingCurveProgress / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = AmberCentral,
                        trackColor = SurfaceDark
                    )
                }
            }
        }
    }
}

/**
 * Detailed Modal Bottom Sheet / Dialog showing full Token Metadata, Bonding Curve Buy, JSON inspection, and Transaction History.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Krc20TokenDetailSheet(
    token: Krc20TokenBalance,
    onDismiss: () -> Unit,
    onTogglePin: (String) -> Unit,
    onOpenSwap: (String) -> Unit,
    onBuyBondingCurve: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMetadataJsonDialog by remember { mutableStateOf(false) }
    var showBuyBondingCurveModal by remember { mutableStateOf(false) }
    var selectedTxForJson by remember { mutableStateOf<Krc20TokenTransaction?>(null) }
    var copyNotice by remember { mutableStateOf<String?>(null) }

    val accentColor = when (token.tick.uppercase()) {
        "NACHO" -> AmberCentral
        "KASPER" -> VioletBridge
        "SOMI" -> KaspaTea
        "BOMB" -> RedTamper
        "KASPUMP" -> AmberCentral
        else -> KaspaTea
    }

    val meta = token.metadata ?: Krc20TokenMetadata(
        tick = token.tick,
        tokenId = token.tokenId
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = KaspaTea)
            ) {
                Text("Close", color = ObsidianBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (token.isBondingCurve) {
                Button(
                    onClick = { showBuyBondingCurveModal = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberCentral)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Buy on Bonding Curve",
                        tint = ObsidianBg,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Buy on Bonding Curve", color = ObsidianBg, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = { onOpenSwap(token.tick) },
                    border = BorderStroke(1.dp, KaspaTea.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Swap",
                        tint = KaspaTea,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Trade on BraidSwap", color = KaspaTea)
                }
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = token.tick.take(2).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = accentColor
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${token.tick} Token",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (token.isBondingCurve) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AmberCentral.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Curve",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberCentral,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "KRC-20 Protocol",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }


            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Balance Banner Card
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Your Token Balance",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = token.formattedBalance(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bonding Curve Progress Card
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (token.isBondingCurve) AmberCentral.copy(alpha = 0.4f) else SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (token.isBondingCurve) Icons.Default.Bolt else Icons.Default.Check,
                                    contentDescription = "Curve",
                                    tint = if (token.isBondingCurve) AmberCentral else EmeraldMesh,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (token.isBondingCurve) "Bonding Curve Stage" else "DEX Status",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (token.isBondingCurve) AmberCentral.copy(alpha = 0.15f) else EmeraldMesh.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (token.isBondingCurve) "Active Curve" else "Graduated to DEX",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (token.isBondingCurve) AmberCentral else EmeraldMesh,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Graduation Progress",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = "%.2f%%".format(token.bondingCurveProgress),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (token.isBondingCurve) AmberCentral else EmeraldMesh
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        LinearProgressIndicator(
                            progress = { (token.bondingCurveProgress / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (token.isBondingCurve) AmberCentral else EmeraldMesh,
                            trackColor = SurfaceCardBorder
                        )



                        if (token.isBondingCurve) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showBuyBondingCurveModal = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberCentral)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Buy",
                                    tint = ObsidianBg,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Buy ${token.tick} on Bonding Curve",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = ObsidianBg
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Metadata Card
                Text(
                    text = "Token Metadata",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Token ID / Inscription
                        MetadataRow(
                            label = "Token ID (Deploy Tx)",
                            value = if (meta.tokenId.isNotBlank()) "${meta.tokenId.take(8)}...${meta.tokenId.takeLast(6)}" else "Standard Hash",
                            copyValue = meta.tokenId,
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Token ID", meta.tokenId))
                                copyNotice = "Token ID copied!"
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = SurfaceCardBorder)

                        MetadataRow(label = "Protocol", value = meta.protocol.uppercase())
                        MetadataRow(label = "Decimals", value = "${meta.decimals}")
                        MetadataRow(label = "State", value = meta.state.replaceFirstChar { it.uppercase() })
                        if (meta.holderTotal > 0L) {
                            MetadataRow(label = "Total Holders", value = "%,d".format(meta.holderTotal))
                        }
                        if (meta.transferTotal > 0L) {
                            MetadataRow(label = "Total Transfers", value = "%,d".format(meta.transferTotal))
                        }
                        if (meta.deployer.isNotBlank()) {
                            MetadataRow(
                                label = "Deployer Address",
                                value = "${meta.deployer.take(12)}...${meta.deployer.takeLast(6)}",
                                copyValue = meta.deployer,
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Deployer", meta.deployer))
                                    copyNotice = "Deployer address copied!"
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // View Raw Metadata JSON Button
                        Surface(
                            onClick = { showMetadataJsonDialog = true },
                            shape = RoundedCornerShape(6.dp),
                            color = SurfaceCard,
                            border = BorderStroke(1.dp, KaspaTea.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("view_metadata_json_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = "Metadata JSON",
                                    tint = KaspaTea,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "View Token Metadata JSON",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KaspaTea
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Transaction History Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Transaction History",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${token.recentTransactions.size} recorded",
                        fontSize = 10.5.sp,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (token.recentTransactions.isEmpty()) {
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No recorded transactions",
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        token.recentTransactions.forEach { tx ->
                            TokenTransactionRow(
                                tx = tx,
                                onViewJson = { selectedTxForJson = tx },
                                onCopyTxId = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Transaction Hash", tx.txId))
                                    copyNotice = "Tx ID copied!"
                                },
                                onOpenExplorer = {
                                    val url = "https://kaspa.stream/txs/${tx.txId}"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }

                if (copyNotice != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = copyNotice.orEmpty(),
                        fontSize = 11.sp,
                        color = EmeraldMesh,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        containerColor = SurfaceElevated
    )

    // Buy Bonding Curve Dialog
    if (showBuyBondingCurveModal) {
        BuyBondingCurveDialog(
            token = token,
            onDismiss = { showBuyBondingCurveModal = false },
            onConfirmBuy = { batches ->
                showBuyBondingCurveModal = false
                onBuyBondingCurve?.invoke(batches)
            }
        )
    }

    // Raw Metadata JSON Dialog
    if (showMetadataJsonDialog) {
        RawJsonViewerDialog(
            title = "${token.tick} Token Metadata JSON",
            jsonContent = meta.formattedJson(),
            onDismiss = { showMetadataJsonDialog = false }
        )
    }

    // Raw Transaction JSON Dialog
    if (selectedTxForJson != null) {
        RawJsonViewerDialog(
            title = "Transaction JSON (${selectedTxForJson?.op} ${selectedTxForJson?.tick})",
            jsonContent = selectedTxForJson?.formattedJson().orEmpty(),
            onDismiss = { selectedTxForJson = null }
        )
    }
}

/**
 * Dialog to execute a real Buy operation on the Token's Bonding Curve via Kaspa Mainnet Inscription Mint.
 */
@Composable
fun BuyBondingCurveDialog(
    token: Krc20TokenBalance,
    onDismiss: () -> Unit,
    onConfirmBuy: (Int) -> Unit
) {
    var batches by remember { mutableIntStateOf(1) }
    val limitPerMint = token.metadata?.limitPerMint?.toDoubleOrNull()?.div(100_000_000.0) ?: 1000.0
    val totalTokensToReceive = limitPerMint * batches
    val kasCost = batches * 0.2 // Standard inscription network fee ~0.2 KAS

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onConfirmBuy(batches) },
                colors = ButtonDefaults.buttonColors(containerColor = AmberCentral)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Confirm Buy",
                    tint = ObsidianBg,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Confirm Buy on BlockDAG", color = ObsidianBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Bonding Curve",
                    tint = AmberCentral,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Buy ${token.tick} on Bonding Curve",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {


                Spacer(modifier = Modifier.height(12.dp))

                // Batches Selector Card
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Select Quantity / Batches:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(1, 2, 5, 10).forEach { count ->
                                Surface(
                                    onClick = { batches = count },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (batches == count) AmberCentral.copy(alpha = 0.2f) else SurfaceCard,
                                    border = BorderStroke(1.dp, if (batches == count) AmberCentral else SurfaceCardBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "$count Batch",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (batches == count) AmberCentral else TextPrimary
                                        )
                                        Text(
                                            text = "${count * 0.2} KAS",
                                            fontSize = 9.5.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Calculation Summary Card
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tokens to Receive:", fontSize = 11.sp, color = TextSecondary)
                            Text("%,.2f %s".format(totalTokensToReceive, token.tick), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Network Inscription Fee:", fontSize = 11.sp, color = TextSecondary)
                            Text("%.2f KAS".format(kasCost), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberCentral)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Protocol Method:", fontSize = 11.sp, color = TextSecondary)
                            Text("KRC-20 Commit-Reveal", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = KaspaTea)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Raw inscription preview
                Surface(
                    color = ObsidianBg,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = """{"p":"krc-20","op":"mint","tick":"${token.tick}"}""",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = KaspaTea,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        containerColor = SurfaceElevated
    )
}

@Composable
fun MetadataRow(
    label: String,
    value: String,
    copyValue: String? = null,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextSecondary
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (onCopy != null) Modifier.clickable { onCopy() } else Modifier
        ) {
            Text(
                text = value,
                fontSize = 11.sp,
                fontFamily = if (copyValue != null) FontFamily.Monospace else FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            if (onCopy != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = KaspaTea,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

@Composable
fun TokenTransactionRow(
    tx: Krc20TokenTransaction,
    onViewJson: () -> Unit,
    onCopyTxId: () -> Unit,
    onOpenExplorer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val opColor = when (tx.op.uppercase()) {
        "BUY", "SWAP" -> KaspaTea
        "MINT", "MINT (BONDING CURVE)" -> AmberCentral
        "TRANSFER" -> VioletBridge
        else -> TextSecondary
    }

    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, SurfaceCardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = opColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = tx.op,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = opColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ID: ${tx.txId.take(6)}...${tx.txId.takeLast(4)}",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary,
                        modifier = Modifier.clickable { onCopyTxId() }
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = EmeraldMesh.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = tx.status,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldMesh,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (tx.amount > 0) "%,.2f %s".format(tx.amount, tx.tick) else tx.tick,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // View Tx JSON Button
                    Surface(
                        onClick = onViewJson,
                        shape = RoundedCornerShape(4.dp),
                        color = SurfaceCard,
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.testTag("view_tx_json_${tx.txId.take(6)}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "JSON",
                                tint = KaspaTea,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "JSON",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KaspaTea
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onOpenExplorer,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Explorer",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Raw JSON Viewer Dialog displaying formatted, syntax-readable JSON with one-tap Copy button.
 */
@Composable
fun RawJsonViewerDialog(
    title: String,
    jsonContent: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = KaspaTea)
            ) {
                Text("Done", color = ObsidianBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("JSON Payload", jsonContent))
                    isCopied = true
                },
                border = BorderStroke(1.dp, if (isCopied) EmeraldMesh else KaspaTea)
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = if (isCopied) EmeraldMesh else KaspaTea,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isCopied) "Copied!" else "Copy JSON",
                    color = if (isCopied) EmeraldMesh else KaspaTea
                )
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "JSON",
                    tint = KaspaTea,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Surface(
                color = SurfaceDark,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = jsonContent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = KaspaTea,
                        lineHeight = 15.sp
                    )
                }
            }
        },
        containerColor = SurfaceElevated
    )
}

