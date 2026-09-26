package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.KaspaPriceService
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.KaspaTea
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

data class ExchangeRoute(
    val name: String,
    val type: String,
    val url: String,
    val badge: String
)

/**
 * Direct "Buy & Swap Kaspa" Little Hub Rectangle.
 * Routes users instantly to verified fiat onramps and instant non-custodial swaps.
 */
@Composable
fun BuyKaspaHubCard(
    modifier: Modifier = Modifier,
    onNavigateToUrl: (String) -> Unit
) {
    val priceInfo by KaspaPriceService.priceState.collectAsState()
    val kasPrice = if (priceInfo.priceUsd > 0) priceInfo.priceUsd else 0.1685
    val estimated100Usd = 100.0 / kasPrice

    val routes = listOf(
        ExchangeRoute("MEXC Global", "Spot / Card", "https://www.mexc.com/exchange/KAS_USDT", "Top Volume"),
        ExchangeRoute("ChangeNOW", "Instant Swap", "https://changenow.io/?to=kas", "No KYC / Fast"),
        ExchangeRoute("Bybit", "Spot / Fiat", "https://www.bybit.com/trade/spot/KAS/USDT", "Global Tier 1"),
        ExchangeRoute("Gate.io", "Spot & Onramp", "https://www.gate.io/trade/KAS_USDT", "Card & SEPA")
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("buy_kaspa_hub_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Header: Logo + "Buy & Swap Kaspa" + Live Price Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = com.example.R.drawable.ic_kaspa_official_pebble),
                        contentDescription = "Kaspa Logo",
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Buy & Swap Kaspa",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Estimated live rate pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = KaspaTea.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, KaspaTea.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = String.format("$100 ≈ %,.0f KAS", estimated100Usd),
                        color = KaspaTea,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle description
            Text(
                text = "Verified onramps and instant cross-chain swaps (BTC, ETH, SOL, USDT to KAS):",
                color = TextMuted,
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Exchange Routes Grid (2 columns)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                routes.take(2).forEach { route ->
                    ExchangeRouteButton(
                        route = route,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToUrl(route.url) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                routes.drop(2).take(2).forEach { route ->
                    ExchangeRouteButton(
                        route = route,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToUrl(route.url) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExchangeRouteButton(
    route: ExchangeRoute,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, SurfaceCardBorder),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = route.name,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${route.type} • ${route.badge}",
                    color = KaspaTea,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Open",
                tint = TextMuted,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}
