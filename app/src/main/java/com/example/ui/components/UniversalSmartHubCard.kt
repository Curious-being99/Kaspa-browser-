package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.SmartOmnibarEngine
import com.example.ui.theme.KaspaTea
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

/**
 * Universal Intelligent Hub Card.
 * Clean, compact, native intelligence card for multi-currency, geography, science,
 * world time, unit conversions, web3 definitions, and math calculations.
 */
@Composable
fun UniversalSmartHubCard(
    result: SmartOmnibarEngine.SmartResult,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var copiedNotice by remember { mutableStateOf(false) }

    val icon: ImageVector = when (result.category) {
        SmartOmnibarEngine.SmartCategory.MATH -> Icons.Default.Calculate
        SmartOmnibarEngine.SmartCategory.CURRENCY -> Icons.Default.CurrencyExchange
        SmartOmnibarEngine.SmartCategory.CRYPTO -> Icons.Default.Paid
        SmartOmnibarEngine.SmartCategory.GEOGRAPHY -> Icons.Default.Public
        SmartOmnibarEngine.SmartCategory.SCIENCE -> Icons.Default.Science
        SmartOmnibarEngine.SmartCategory.TIME -> Icons.Default.AccessTime
        SmartOmnibarEngine.SmartCategory.UNIT -> Icons.Default.SquareFoot
        SmartOmnibarEngine.SmartCategory.DICTIONARY -> Icons.Default.MenuBook
        SmartOmnibarEngine.SmartCategory.COLOR -> Icons.Default.Language
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("universal_smart_hub_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Header Row: Category Badge + Query Description + Copy Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(KaspaTea.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = result.category.label,
                            tint = KaspaTea,
                            modifier = Modifier.size(11.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = result.queryDisplay,
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = if (result.category == SmartOmnibarEngine.SmartCategory.MATH) FontFamily.Monospace else FontFamily.SansSerif,
                        maxLines = 1
                    )
                }

                // Copy Answer Button
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, SurfaceCardBorder),
                    modifier = Modifier.clickable {
                        clipboardManager.setText(AnnotatedString(result.copyValue))
                        copiedNotice = true
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = if (copiedNotice) KaspaTea else TextMuted,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (copiedNotice) "Copied" else "Copy",
                            color = if (copiedNotice) KaspaTea else TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Result Answer (Bold & Large)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.mainResult,
                    color = if (result.category == SmartOmnibarEngine.SmartCategory.MATH || result.category == SmartOmnibarEngine.SmartCategory.CURRENCY || result.category == SmartOmnibarEngine.SmartCategory.CRYPTO) KaspaTea else TextPrimary,
                    fontSize = if (result.mainResult.length > 25) 14.sp else 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif
                )

                if (result.extraBadge != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, SurfaceCardBorder)
                    ) {
                        Text(
                            text = result.extraBadge,
                            color = KaspaTea,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Secondary Details / Context
            if (result.secondaryDetails != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = result.secondaryDetails,
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}
