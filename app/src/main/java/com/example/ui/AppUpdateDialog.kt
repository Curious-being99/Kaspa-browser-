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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .padding(vertical = 12.dp)
                .testTag("app_update_dialog"),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
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
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Update Available",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = KaspaTea.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "NEW",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KaspaTea,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "GitHub Direct Release",
                                fontSize = 10.5.sp,
                                color = TextMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Release Meta Banner
                currentUpdateInfo?.let { info ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Version v${info.latestVersionName}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = "Current: v${info.currentVersionName}",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceCard.copy(alpha = 0.4f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = info.apkSizeFormatted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Release Notes / What's New Box
                    Text(
                        text = "Release Notes",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextPrimary,
                        modifier = Modifier.padding(start = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 90.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceDark.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, SurfaceCardBorder.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = info.releaseNotes,
                                fontSize = 10.5.sp,
                                color = TextSecondary,
                                lineHeight = 14.sp
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
                                    text = "Downloading APK release package...",
                                    fontSize = 11.5.sp,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = "${status.progressPercent}%",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { status.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = ElectricCyan,
                                trackColor = SurfaceCard
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val downloadedMb = "%.1f".format(status.downloadedBytes / (1024.0 * 1024.0))
                            val totalMb = "%.1f".format(status.totalBytes / (1024.0 * 1024.0))
                            Text(
                                text = "$downloadedMb MB / $totalMb MB",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    is UpdateStatus.ReadyToInstall -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = EmeraldMesh.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldMesh.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldMesh,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Package verified and ready to install!",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = EmeraldMesh
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    is UpdateStatus.Error -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = RedTamper.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RedTamper.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = RedTamper,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = status.message,
                                    fontSize = 11.sp,
                                    color = RedTamper
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    else -> {}
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.dismissUpdateDialog()
                            viewModel.setTab(AppTab.TRAFFIC_AUDIT)
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Text("Update Zone", fontSize = 12.sp)
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
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(40.dp)
                            .testTag("dialog_update_action_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (updateStatus is UpdateStatus.ReadyToInstall) Icons.Default.InstallMobile else Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (updateStatus) {
                                     is UpdateStatus.ReadyToInstall -> "Install Now"
                                     is UpdateStatus.Downloading -> "Downloading..."
                                     else -> "Direct Update"
                                 },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
