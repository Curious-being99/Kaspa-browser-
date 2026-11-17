package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
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
            SupportedLanguage("zh-TW", "Chinese (Trad.)", "中文 (繁體)"),
            SupportedLanguage("ja", "Japanese", "日本語"),
            SupportedLanguage("ru", "Russian", "Русский"),
            SupportedLanguage("pt", "Portuguese", "Português"),
            SupportedLanguage("ar", "Arabic", "العربية"),
            SupportedLanguage("he", "Hebrew", "עברית"),
            SupportedLanguage("kri", "Krio (West African Pidgin)", "Krio"),
            SupportedLanguage("tpi", "Tok Pisin (Pidgin English)", "Tok Pisin"),
            SupportedLanguage("jam", "Jamaican Patois", "Patwa"),
            SupportedLanguage("hi", "Hindi", "हिन्दी"),
            SupportedLanguage("bn", "Bengali", "বাংলা"),
            SupportedLanguage("ur", "Urdu", "اردو"),
            SupportedLanguage("fa", "Persian", "فارسی"),
            SupportedLanguage("ko", "Korean", "한국어"),
            SupportedLanguage("it", "Italian", "Italiano"),
            SupportedLanguage("tr", "Turkish", "Türkçe"),
            SupportedLanguage("nl", "Dutch", "Nederlands"),
            SupportedLanguage("pl", "Polish", "Polski"),
            SupportedLanguage("id", "Indonesian", "Bahasa Indonesia"),
            SupportedLanguage("ms", "Malay", "Bahasa Melayu"),
            SupportedLanguage("tl", "Filipino", "Tagalog"),
            SupportedLanguage("vi", "Vietnamese", "Tiếng Việt"),
            SupportedLanguage("uk", "Ukrainian", "Українська"),
            SupportedLanguage("th", "Thai", "ไทย"),
            SupportedLanguage("el", "Greek", "Ελληνικά"),
            SupportedLanguage("sw", "Swahili", "Kiswahili"),
            SupportedLanguage("yo", "Yoruba", "Èdè Yorùbá"),
            SupportedLanguage("ha", "Hausa", "Hausa"),
            SupportedLanguage("ig", "Igbo", "Asụsụ Igbo"),
            SupportedLanguage("am", "Amharic", "አማርኛ"),
            SupportedLanguage("zu", "Zulu", "isiZulu"),
            SupportedLanguage("pa", "Punjabi", "ਪੰਜਾਬੀ"),
            SupportedLanguage("ta", "Tamil", "தமிழ்"),
            SupportedLanguage("te", "Telugu", "తెలుగు"),
            SupportedLanguage("mr", "Marathi", "मराठी"),
            SupportedLanguage("sv", "Swedish", "Svenska"),
            SupportedLanguage("no", "Norwegian", "Norsk"),
            SupportedLanguage("da", "Danish", "Dansk"),
            SupportedLanguage("fi", "Finnish", "Suomi"),
            SupportedLanguage("cs", "Czech", "Čeština"),
            SupportedLanguage("ro", "Romanian", "Română"),
            SupportedLanguage("hu", "Hungarian", "Magyar")
        )

        fun fromCode(code: String): SupportedLanguage {
            val normalized = if (code.trim().equals("iw", ignoreCase = true)) "he" else code.trim()
            return ALL.find { it.code.equals(normalized, ignoreCase = true) }
                ?: SupportedLanguage(normalized, normalized.uppercase(), normalized)
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
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier
    ) {
        var showFullLanguageMenu by remember { mutableStateOf(false) }

        val quickLanguages = remember(selectedTargetLanguage) {
            val base = SupportedLanguage.ALL.take(8)
            val active = SupportedLanguage.ALL.find {
                it.code.equals(selectedTargetLanguage, ignoreCase = true)
            }
            if (active != null && base.none { it.code.equals(active.code, ignoreCase = true) }) {
                listOf(active) + base.take(7)
            } else {
                base
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("page_translator_bar"),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 8.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Compact Translator Status Icon / Spinner
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isTranslated || isTranslating) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isTranslating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Web Translator",
                            tint = if (isTranslated) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Compact scrollable quick language chips
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    quickLanguages.forEach { lang ->
                        val isSelected = lang.code.equals(selectedTargetLanguage, ignoreCase = true)
                        val chipBg = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                        val chipBorder = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        }
                        val textColor = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = chipBg,
                            border = BorderStroke(0.75.dp, chipBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isTranslating) {
                                    onSelectLanguage(lang.code)
                                    onTranslate(lang.code)
                                }
                                .testTag("translator_lang_${lang.code}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isSelected && isTranslated) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Translated",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                                Text(
                                    text = lang.name,
                                    color = textColor,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Compact trailing actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (isTranslated) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isTranslating, onClick = onRevert)
                                .testTag("translator_revert_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Original",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Original",
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // All Languages Menu
                    Box {
                        IconButton(
                            onClick = { showFullLanguageMenu = true },
                            enabled = !isTranslating,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(34.dp)
                                .testTag("translator_more_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "More languages",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showFullLanguageMenu,
                            onDismissRequest = { showFullLanguageMenu = false },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .heightIn(max = 360.dp)
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
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
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

                    // Close Translator Bar
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(34.dp)
                            .testTag("translator_close_button")
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
        }
    }
}
