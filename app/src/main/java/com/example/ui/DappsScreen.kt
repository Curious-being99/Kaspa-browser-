package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

data class DappItem(
    val id: String,
    val name: String,
    val url: String,
    val category: String,
    val description: String,
    val isFeatured: Boolean = false,
    val badge: String? = null
)

val KASPA_DAPPS = listOf(
    DappItem(
        id = "braidswap",
        name = "Braid",
        url = "https://app.braidswap.com",
        category = "DEX & Swap",
        description = "Premier self-custody Kaspa BlockDAG DEX aggregator & token swap protocol.",
        isFeatured = true,
        badge = "Official Partner"
    )
)

/**
 * Braid logo rendering interlocking rings at exactly 30dp size.
 */
@Composable
fun BraidLogoIcon(
    modifier: Modifier = Modifier,
    iconSize: Dp = 30.dp
) {
    Box(
        modifier = modifier
            .size(iconSize)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0F1117))
            .border(1.dp, Color(0xFF2E3440), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
        ) {
            val w = size.width
            val h = size.height
            val strokeWidth = w * 0.12f

            val ringRadius = w * 0.26f
            val gold1 = Color(0xFFF59E0B)
            val gold2 = Color(0xFFFBBF24)
            val gold3 = Color(0xFFEAB308)

            // Top Center Ring
            drawCircle(
                color = gold1,
                radius = ringRadius,
                center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.36f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )
            // Bottom Left Ring
            drawCircle(
                color = gold2,
                radius = ringRadius,
                center = androidx.compose.ui.geometry.Offset(w * 0.36f, h * 0.64f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )
            // Bottom Right Ring
            drawCircle(
                color = gold3,
                radius = ringRadius,
                center = androidx.compose.ui.geometry.Offset(w * 0.64f, h * 0.64f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )
        }
    }
}

@Composable
fun DappsScreen(
    viewModel: DecentralViewModel,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    BackHandler {
        viewModel.setTab(AppTab.KASPA_WALLET)
    }

    val featuredBraid = KASPA_DAPPS.first { it.id == "braidswap" }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("dapps_screen"),
        containerColor = ObsidianBg,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianBg)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.setTab(AppTab.KASPA_WALLET) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Wallet",
                            tint = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "dApps Ecosystem",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Kaspa Mainnet L1 & KRC-20 Web3 Gateway",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDark)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(KaspaTea)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Mainnet",
                                fontSize = 11.5.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search Kaspa dApps or paste URL...", color = TextMuted, fontSize = 13.5.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotBlank()) {
                                viewModel.openUrlInBrowser(searchQuery, isExternal = false)
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = SurfaceCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Featured App (Braid) - Always visible at top and unobstructed
            item {
                Text(
                    text = "FEATURED DAPP",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                )

                FeaturedBraidAppCard(
                    dapp = featuredBraid,
                    onOpen = {
                        viewModel.openSwapDapp()
                    }
                )
            }

            // Section 2: Custom URL or search target if typed
            if (searchQuery.isNotBlank()) {
                item {
                    Text(
                        text = "SEARCH / OPEN URL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp, start = 4.dp)
                    )

                    StandardDappCard(
                        dapp = DappItem(
                            id = "custom_search",
                            name = "Open in Browser",
                            url = searchQuery,
                            category = "Web Navigation",
                            description = "Navigate to $searchQuery in sovereign browser"
                        ),
                        onOpen = {
                            viewModel.openUrlInBrowser(searchQuery, isExternal = false)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Featured Card at top of dApps page:
 * Shows 30dp Braid logo, Title "Braid", and target URL "https://app.braidswap.com" written beneath the title.
 */
@Composable
private fun FeaturedBraidAppCard(
    dapp: DappItem,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, KaspaTea.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("featured_braid_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 30dp Braid logo at top as requested
                BraidLogoIcon(iconSize = 30.dp)

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Write up title is "Braid" ONLY
                    Text(
                        text = "Braid",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    // URL link added beneath title
                    Text(
                        text = "https://app.braidswap.com",
                        fontSize = 12.5.sp,
                        color = ElectricCyan,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(KaspaTea.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "L1 / L2 DEX",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = KaspaTea
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = dapp.description,
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint = KaspaTea,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Verified Self-Custody Bridge",
                        fontSize = 11.5.sp,
                        color = TextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(KaspaTea)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Launch dApp",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ObsidianBg
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = ObsidianBg,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardDappCard(
    dapp: DappItem,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dapp.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = dapp.url,
                    fontSize = 12.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
