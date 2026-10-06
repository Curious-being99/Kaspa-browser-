package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AppUpdateInfo
import com.example.model.UpdateStatus
import com.example.ui.theme.*
import com.example.viewmodel.AppTab
import com.example.viewmodel.DecentralViewModel

@Composable
fun AppUpdateDialog(
    viewModel: DecentralViewModel,
    onDismissRequest: () -> Unit = { viewModel.dismissUpdateDialog() }
) {
    val updateStatus by viewModel.updateStatus.collectAsState()
    val context = LocalContext.current

    val currentUpdateInfo = when (val status = updateStatus) {
        is UpdateStatus.Available -> status.updateInfo
        is UpdateStatus.Downloading -> status.updateInfo
        is UpdateStatus.ReadyToInstall -> status.updateInfo
        is UpdateStatus.Installing -> status.updateInfo
        else -> null
    }

    if (currentUpdateInfo == null && updateStatus !is UpdateStatus.Checking) {
        return
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight()
                .padding(vertical = 12.dp)
                .testTag("app_update_dialog"),
            shape = RoundedCornerShape(18.dp),
            color = SurfaceCard,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Brush.verticalGradient(listOf(ElectricCyan.copy(alpha = 0.6f), Color.Transparent))
            ),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Header with Update Badge & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Update App",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = TextPrimary,
                                    letterSpacing = (-0.3).sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = KaspaTea.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "v${currentUpdateInfo?.latestVersionName ?: ""}",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = KaspaTea,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Enhance your secure experience",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Release Meta Banner (Compact)
                currentUpdateInfo?.let { info ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceDark.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, SurfaceCardBorder.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, null, tint = ElectricCyan, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Verified Release",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }

                            Text(
                                text = info.apkSizeFormatted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Release Notes (Reduced Height)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 70.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceDark.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, SurfaceCardBorder.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = info.releaseNotes.ifBlank { "Regular performance updates and security patches." },
                                fontSize = 10.sp,
                                color = TextSecondary,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress Bar or Status Notice
                when (val status = updateStatus) {
                    is UpdateStatus.Downloading -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Downloading update...",
                                    fontSize = 11.sp,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = "${status.progressPercent}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            LinearProgressIndicator(
                                progress = { status.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = ElectricCyan,
                                trackColor = SurfaceDark
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    is UpdateStatus.ReadyToInstall -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = EmeraldMesh.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, EmeraldMesh.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldMesh,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Update ready to install",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldMesh
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    is UpdateStatus.Error -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = RedTamper.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, RedTamper.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = RedTamper,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = status.message,
                                    fontSize = 10.5.sp,
                                    color = RedTamper,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    else -> {}
                }

                // Action Buttons (Compact)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.dismissUpdateDialog()
                            viewModel.setTab(AppTab.TRAFFIC_AUDIT)
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Text("Later", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            when (updateStatus) {
                                is UpdateStatus.ReadyToInstall -> {
                                    viewModel.installReadyApk(context)
                                }
                                else -> {
                                    viewModel.downloadAndInstallUpdate(context)
                                }
                            }
                        },
                        enabled = updateStatus !is UpdateStatus.Downloading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (updateStatus is UpdateStatus.ReadyToInstall) EmeraldMesh else ElectricCyan,
                            contentColor = ObsidianBg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(38.dp)
                            .testTag("dialog_update_action_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (updateStatus is UpdateStatus.ReadyToInstall) Icons.Default.InstallMobile else Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (updateStatus) {
                                     is UpdateStatus.ReadyToInstall -> "Install"
                                     is UpdateStatus.Downloading -> "Wait..."
                                     else -> "Update Now"
                                 },
                                fontWeight = FontWeight.Black,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
