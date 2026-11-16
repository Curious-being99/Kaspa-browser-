package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

enum class PhotoTheme(
    val id: String,
    val displayName: String,
    val description: String,
    @DrawableRes val drawableRes: Int?,
    val primaryAccent: Color
) {
    EMERALD_KASPA(
        id = "emerald_kaspa",
        displayName = "Emerald Kaspa",
        description = "Deep teal crystal backdrop",
        drawableRes = R.drawable.img_theme_emerald_kaspa,
        primaryAccent = Color(0xFF63C3B5)
    ),
    OBSIDIAN_GALAXY(
        id = "obsidian_galaxy",
        displayName = "Obsidian Night",
        description = "Subtle deep starfield horizon",
        drawableRes = R.drawable.img_theme_obsidian_galaxy,
        primaryAccent = Color(0xFF56B4E9)
    ),
    AURORA_TEALS(
        id = "aurora_teals",
        displayName = "Aurora Dusk",
        description = "Northern twilight over dark peaks",
        drawableRes = R.drawable.img_theme_aurora_teals,
        primaryAccent = Color(0xFF4FB8A8)
    ),
    KASPA_LATTICE(
        id = "kaspa_lattice",
        displayName = "Kaspa Lattice",
        description = "Minimalist geometric weave",
        drawableRes = R.drawable.img_theme_kaspa_lattice,
        primaryAccent = Color(0xFF49A89A)
    ),
    CYBER_SUNSET(
        id = "cyber_sunset",
        displayName = "Warm Horizon",
        description = "Amber dusk silhouette",
        drawableRes = R.drawable.img_theme_cyber_sunset,
        primaryAccent = Color(0xFFE07A3D)
    ),
    COSMIC_OCEAN(
        id = "cosmic_ocean",
        displayName = "Deep Ocean",
        description = "Midnight pelagic gradient",
        drawableRes = R.drawable.img_theme_cosmic_ocean,
        primaryAccent = Color(0xFF7C83E8)
    ),
    CYBER_MATRIX(
        id = "cyber_matrix",
        displayName = "Graphite Matte",
        description = "Clean dark charcoal canvas",
        drawableRes = null,
        primaryAccent = Color(0xFF63C3B5)
    );

    val cardBgColor: Color
        get() = Color(0xFF12141B).copy(alpha = 0.86f)

    val cardBorderColor: Color
        get() = Color.White.copy(alpha = 0.08f)

    val topBarBgColor: Color
        get() = Color(0xFF0A0B0E).copy(alpha = 0.88f)

    companion object {
        fun fromId(id: String?): PhotoTheme {
            return entries.find { it.id == id } ?: EMERALD_KASPA
        }

        fun nextTheme(current: PhotoTheme): PhotoTheme {
            val entries = entries
            val nextIndex = (current.ordinal + 1) % entries.size
            return entries[nextIndex]
        }
    }
}

val LocalPhotoTheme = staticCompositionLocalOf { PhotoTheme.EMERALD_KASPA }

class PhotoThemePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("photo_theme_prefs", Context.MODE_PRIVATE)

    fun getSavedTheme(): PhotoTheme {
        val id = prefs.getString("selected_photo_theme", PhotoTheme.EMERALD_KASPA.id)
        return PhotoTheme.fromId(id)
    }

    fun saveTheme(theme: PhotoTheme) {
        prefs.edit().putString("selected_photo_theme", theme.id).apply()
    }

    fun isAutoRotateEnabled(): Boolean {
        return prefs.getBoolean("auto_rotate_photo_theme", true)
    }

    fun setAutoRotateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_rotate_photo_theme", enabled).apply()
    }
}

@Composable
fun PhotoBackgroundWrapper(
    currentTheme: PhotoTheme,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalPhotoTheme provides currentTheme) {
        Box(modifier = modifier.fillMaxSize()) {
            Crossfade(
                targetState = currentTheme,
                animationSpec = tween(durationMillis = 600),
                label = "photo_background_crossfade"
            ) { theme ->
                Box(modifier = Modifier.fillMaxSize()) {
                    if (theme.drawableRes != null) {
                        Image(
                            painter = painterResource(id = theme.drawableRes),
                            contentDescription = "Theme Background Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Procedural fallback gradient for Cyber Matrix
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF0C0D10),
                                            Color(0xFF141824),
                                            Color(0xFF0A0C12)
                                        )
                                    )
                                )
                        )
                    }

                    // Subtle dark scrim preserving wallpaper clarity while keeping text legible
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF0A0B0E).copy(alpha = 0.42f),
                                        Color(0xFF0A0B0E).copy(alpha = 0.72f),
                                        Color(0xFF0A0B0E).copy(alpha = 0.90f)
                                    )
                                )
                            )
                    )
                }
            }

            content()
        }
    }
}

@Composable
fun PhotoThemeSelectorDialog(
    currentTheme: PhotoTheme,
    isAutoRotate: Boolean,
    onSelectTheme: (PhotoTheme) -> Unit,
    onToggleAutoRotate: (Boolean) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = currentTheme.primaryAccent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Photo Background Themes",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Choose a photo wallpaper theme or enable auto-rotate to switch automatically.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                // Auto Rotate Toggle Box
                Surface(
                    onClick = { onToggleAutoRotate(!isAutoRotate) },
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, if (isAutoRotate) currentTheme.primaryAccent else SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoMode,
                                contentDescription = null,
                                tint = if (isAutoRotate) currentTheme.primaryAccent else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Auto-Rotate Photo Themes",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Automatically switches background themes",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }
                        Switch(
                            checked = isAutoRotate,
                            onCheckedChange = { onToggleAutoRotate(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SurfaceDark,
                                checkedTrackColor = currentTheme.primaryAccent
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Theme Options List
                PhotoTheme.entries.forEach { theme ->
                    val isSelected = theme == currentTheme
                    Surface(
                        onClick = { onSelectTheme(theme) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) SurfaceDark else SurfaceCard,
                        border = BorderStroke(1.dp, if (isSelected) theme.primaryAccent else SurfaceCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(theme.primaryAccent)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = theme.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = theme.description,
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = theme.primaryAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Done", color = currentTheme.primaryAccent, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = ObsidianBg,
        shape = RoundedCornerShape(16.dp)
    )
}
