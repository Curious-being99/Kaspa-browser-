package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SupportedLanguage(
    val code: String,
    val name: String,
    val nativeName: String
) {
    companion object {
        val ALL = listOf(
            SupportedLanguage("en", "English", "English"),
            SupportedLanguage("es", "Spanish", "Español"),
            SupportedLanguage("fr", "French", "Français"),
            SupportedLanguage("de", "German", "Deutsch"),
            SupportedLanguage("zh-CN", "Chinese", "中文 (简体)"),
            SupportedLanguage("ja", "Japanese", "日本語"),
            SupportedLanguage("ru", "Russian", "Русский"),
            SupportedLanguage("pt", "Portuguese", "Português"),
            SupportedLanguage("ar", "Arabic", "العربية"),
            SupportedLanguage("hi", "Hindi", "हिन्दी"),
            SupportedLanguage("ko", "Korean", "한국어"),
            SupportedLanguage("it", "Italian", "Italiano"),
            SupportedLanguage("tr", "Turkish", "Türkçe"),
            SupportedLanguage("nl", "Dutch", "Nederlands"),
            SupportedLanguage("pl", "Polish", "Polski"),
            SupportedLanguage("id", "Indonesian", "Bahasa Indonesia"),
            SupportedLanguage("vi", "Vietnamese", "Tiếng Việt"),
            SupportedLanguage("uk", "Ukrainian", "Українська"),
            SupportedLanguage("th", "Thai", "ไทย"),
            SupportedLanguage("el", "Greek", "Ελληνικά")
        )

        fun fromCode(code: String): SupportedLanguage {
            return ALL.find { it.code.equals(code, ignoreCase = true) }
                ?: SupportedLanguage(code, code.uppercase(), code)
        }
    }
}

@Composable
fun PageTranslatorBar(
    isVisible: Boolean,
    selectedTargetLanguage: String,
    isTranslated: Boolean,
    isTranslating: Boolean,
    onSelectLanguage: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onRevert: () -> Unit,
    onWebGatewayTranslate: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = modifier
    ) {
        var showFullLanguageMenu by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Top Row: Title, active status, Revert, and Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Translator",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = "Web Translator",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (isTranslated) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        if (isTranslating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isTranslated) {
                            // Revert button
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(enabled = !isTranslating, onClick = onRevert)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Original",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Original",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Close Bar
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Translator",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Row: Quick Language Selector Chips + Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Scrollable language chips
                    val scrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(scrollState),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val popularLanguages = SupportedLanguage.ALL.take(8)
                        popularLanguages.forEach { lang ->
                            val isSelected = lang.code.equals(selectedTargetLanguage, ignoreCase = true)
                            val chipBg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            val chipBorder = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            val textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = chipBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, chipBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(enabled = !isTranslating) {
                                        onSelectLanguage(lang.code)
                                        onTranslate(lang.code)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isSelected && isTranslated) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                    Text(
                                        text = lang.name,
                                        color = textColor,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // More Languages button
                        Box {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF1E222D),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2E39)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(enabled = !isTranslating) {
                                        showFullLanguageMenu = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = "More languages",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "More...",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showFullLanguageMenu,
                                onDismissRequest = { showFullLanguageMenu = false },
                                modifier = Modifier.background(Color(0xFF1E222D))
                            ) {
                                SupportedLanguage.ALL.forEach { lang ->
                                    val isSelected = lang.code.equals(selectedTargetLanguage, ignoreCase = true)
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${lang.name} (${lang.nativeName})",
                                                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFFE0E3EB),
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color(0xFF00E5FF),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            showFullLanguageMenu = false
                                            onSelectLanguage(lang.code)
                                            onTranslate(lang.code)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Gateway Translate button (Full Proxy)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF70C7BA).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF70C7BA).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isTranslating) {
                                onWebGatewayTranslate(selectedTargetLanguage)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Web Proxy Translate",
                                tint = Color(0xFF70C7BA),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Gateway",
                                color = Color(0xFF70C7BA),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
