package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

private val PositiveGreen = Color(0xFF10B981)
private val NegativeRed = Color(0xFFEF4444)

/**
 * Compact, lightweight small hub rectangle displaying real-time Kaspa price,
 * mini live candlestick sparkline, timeframe selector, and core market stats.
 */
@Composable
fun KaspaPriceHubCard(
    modifier: Modifier = Modifier,
    onNavigateToUrl: ((String) -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    val priceInfo by KaspaPriceService.priceState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var copiedNotice by remember { mutableStateOf(false) }
    var scrubbedPrice by remember { mutableStateOf<Float?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("kaspa_price_hub_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Row 1: Logo + "Kaspa KAS" + LIVE + Refresh --- Price + 24h Change Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Logo & Identity
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
                        text = "Kaspa",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "KAS",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    // Pulse dot
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(PositiveGreen.copy(alpha = pulseAlpha))
                    )

                    IconButton(
                        onClick = { KaspaPriceService.refreshPrice() },
                        modifier = Modifier.size(20.dp)
                    ) {
                        if (priceInfo.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                color = ElectricCyan,
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = TextMuted,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }

                // Right: Live Price + 24h Change Pill
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val displayPrice = scrubbedPrice?.let { String.format("$%.4f", it) } ?: priceInfo.formattedPrice
                    Text(
                        text = displayPrice,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    val isPos = priceInfo.isPositive
                    val pillBg = if (isPos) PositiveGreen.copy(alpha = 0.15f) else NegativeRed.copy(alpha = 0.15f)
                    val pillColor = if (isPos) PositiveGreen else NegativeRed

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(pillBg)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPos) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = pillColor,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = priceInfo.formattedChange,
                            color = pillColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Compact Interactive Sparkline Chart (42dp height)
            if (priceInfo.chartPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceDark.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = KaspaTea,
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Loading real exchange candles...",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }
            } else {
                CompactKaspaSparkline(
                    points = priceInfo.chartPoints,
                    isPositive = priceInfo.isPositive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceDark.copy(alpha = 0.5f)),
                    onScrub = { pointPrice ->
                        scrubbedPrice = pointPrice
                    },
                    onScrubEnd = {
                        scrubbedPrice = null
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 3: Timeframe Chips + Stats + Quick Links in a single compact bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timeframe Chips (24H, 7D, 30D, 1Y)
                val timeframes = listOf("24H", "7D", "30D", "1Y")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    timeframes.forEach { tf ->
                        val isSelected = priceInfo.selectedTimeframe == tf
                        val bg = if (isSelected) KaspaTea.copy(alpha = 0.25f) else SurfaceDark
                        val border = if (isSelected) KaspaTea else Color.Transparent
                        val txtColor = if (isSelected) KaspaTea else TextMuted

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(bg)
                                .border(0.5.dp, border, RoundedCornerShape(4.dp))
                                .clickable {
                                    KaspaPriceService.refreshPrice(tf)
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tf,
                                color = txtColor,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // Inline Compact Stats (High | Low | MCap | Vol)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniStat(label = "H", value = priceInfo.formattedHigh24h)
                    MiniStat(label = "L", value = priceInfo.formattedLow24h)
                    MiniStat(label = "MCap", value = priceInfo.formattedMarketCap)
                    MiniStat(label = "Vol", value = priceInfo.formattedVolume)
                }

                // Copy & Explorer Shortcuts
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfaceDark)
                            .clickable {
                                clipboardManager.setText(AnnotatedString("${priceInfo.formattedPrice} USD (KAS)"))
                                copiedNotice = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = if (copiedNotice) ElectricCyan else TextMuted,
                            modifier = Modifier.size(10.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfaceDark)
                            .clickable {
                                onNavigateToUrl?.invoke("https://kaspa.stream")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Kaspa.stream",
                            tint = ElectricCyan,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label:",
            color = TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Compact Anti-aliased Canvas Sparkline for small rectangle hub
 */
@Composable
private fun CompactKaspaSparkline(
    points: List<Float>,
    isPositive: Boolean,
    modifier: Modifier = Modifier,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit
) {
    if (points.size < 2) return

    val lineColor = if (isPositive) KaspaTea else NegativeRed
    val gradientTop = if (isPositive) KaspaTea.copy(alpha = 0.3f) else NegativeRed.copy(alpha = 0.3f)
    val gradientBottom = Color.Transparent

    var activeScrubX by remember { mutableStateOf<Float?>(null) }

    Canvas(
        modifier = modifier
            .pointerInput(points) {
                detectTapGestures(
                    onPress = { offset ->
                        activeScrubX = offset.x
                        val index = ((offset.x / size.width) * (points.size - 1))
                            .toInt()
                            .coerceIn(0, points.size - 1)
                        onScrub(points[index])
                        tryAwaitRelease()
                        activeScrubX = null
                        onScrubEnd()
                    }
                )
            }
            .pointerInput(points) {
                detectDragGestures(
                    onDragStart = { offset ->
                        activeScrubX = offset.x
                        val index = ((offset.x / size.width) * (points.size - 1))
                            .toInt()
                            .coerceIn(0, points.size - 1)
                        onScrub(points[index])
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        activeScrubX = change.position.x
                        val index = ((change.position.x / size.width) * (points.size - 1))
                            .toInt()
                            .coerceIn(0, points.size - 1)
                        onScrub(points[index])
                    },
                    onDragEnd = {
                        activeScrubX = null
                        onScrubEnd()
                    },
                    onDragCancel = {
                        activeScrubX = null
                        onScrubEnd()
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        val minVal = (points.minOrNull() ?: 0f) * 0.997f
        val maxVal = (points.maxOrNull() ?: 1f) * 1.003f
        val range = (maxVal - minVal).coerceAtLeast(0.0001f)

        val stepX = width / (points.size - 1).toFloat()

        val path = Path()
        val fillPath = Path()

        val firstX = 0f
        val firstY = height - ((points[0] - minVal) / range * height * 0.75f + height * 0.12f)

        path.moveTo(firstX, firstY)
        fillPath.moveTo(firstX, height)
        fillPath.lineTo(firstX, firstY)

        for (i in 1 until points.size) {
            val prevX = (i - 1) * stepX
            val prevY = height - ((points[i - 1] - minVal) / range * height * 0.75f + height * 0.12f)
            val currentX = i * stepX
            val currentY = height - ((points[i] - minVal) / range * height * 0.75f + height * 0.12f)

            val cX1 = prevX + (currentX - prevX) / 2f
            val cY1 = prevY
            val cX2 = prevX + (currentX - prevX) / 2f
            val cY2 = currentY

            path.cubicTo(cX1, cY1, cX2, cY2, currentX, currentY)
            fillPath.cubicTo(cX1, cY1, cX2, cY2, currentX, currentY)
        }

        fillPath.lineTo(width, height)
        fillPath.close()

        // Background Gradient
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(gradientTop, gradientBottom),
                startY = 0f,
                endY = height
            )
        )

        // Stroke Line
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        )

        // Last Point Dot
        val lastX = (points.size - 1) * stepX
        val lastY = height - ((points.last() - minVal) / range * height * 0.75f + height * 0.12f)
        drawCircle(
            color = lineColor,
            radius = 2.5.dp.toPx(),
            center = Offset(lastX, lastY)
        )

        // Scrubber line if touched
        activeScrubX?.let { scrubX ->
            val clampedX = scrubX.coerceIn(0f, width)
            drawLine(
                color = ElectricCyan,
                start = Offset(clampedX, 0f),
                end = Offset(clampedX, height),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
