package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TrafficAuditEntity
import com.example.ui.theme.AmberCentral
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMesh
import com.example.ui.theme.ObsidianBg
import com.example.R
import androidx.compose.ui.res.painterResource
import com.example.ui.theme.RedTamper
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletBridge
import com.example.viewmodel.DecentralViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrafficAuditScreen(viewModel: DecentralViewModel, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val audits by viewModel.trafficAudits.collectAsState()
    val metrics by viewModel.metrics.collectAsState()

    val blockTrackers by viewModel.blockTrackers.collectAsState()
    val enableDownloads by viewModel.enableDownloads.collectAsState()
    val enableUploads by viewModel.enableUploads.collectAsState()
    val encryptedLocalStorage by viewModel.encryptedLocalStorage.collectAsState()
    val thirdPartyCookies by viewModel.thirdPartyCookies.collectAsState()
    val blockThirdPartyCookies by viewModel.blockThirdPartyCookies.collectAsState()
    val strictDecentralizedMode by viewModel.strictDecentralizedMode.collectAsState()
    val sendDntHeaders by viewModel.sendDntHeaders.collectAsState()
    val incognitoMode by viewModel.incognitoMode.collectAsState()
    val httpsOnlyMode by viewModel.httpsOnlyMode.collectAsState()
    val webAuthEnabled by viewModel.webAuthEnabled.collectAsState()
    val desktopModeEnabled by viewModel.desktopModeEnabled.collectAsState()
    val blockedTrackersCount by viewModel.blockedTrackersCount.collectAsState()
    val blockedTrackerLogs by viewModel.blockedTrackerLogs.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()

    var filterMode by remember { mutableStateOf("ALL") }
    var purgeMsg by remember { mutableStateOf<String?>(null) }

    val filteredAudits = remember(audits, filterMode) {
        when (filterMode) {
            "P2P" -> audits.filter { it.protocol.contains("DECENTRALIZED") || it.protocol.contains("HYBRID") }
            "CENTRAL" -> audits.filter { it.protocol.contains("CENTRALIZED") }
            "ALERTS" -> audits.filter { !it.isTamperProof }
            else -> audits
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))

            // Screen Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Settings & Privacy",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                IconButton(
                    onClick = { viewModel.clearAuditLogs() },
                    modifier = Modifier.testTag("clear_audits_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Logs",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. PRIVACY & SECURITY CONTROLS CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("privacy_settings_card"),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(0.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(SurfaceCardBorder)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Kaspa Privacy Engine Settings",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PrivacyToggleRow(
                        title = "Kaspa Tracker & Ad Blocker",
                        desc = "KaspaPrivacyEngine intercepts tracking scripts, ad pixels & telemetry ($blockedTrackersCount blocked)",
                        icon = Icons.Default.Shield,
                        checked = blockTrackers,
                        onCheckedChange = { viewModel.toggleBlockTrackers(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = SurfaceCardBorder)

                    PrivacyToggleRow(
                        title = "Third-Party Cookies",
                        desc = "Allow cross-origin cookies required for YouTube video embeds, OAuth & web media",
                        painter = painterResource(R.drawable.ic_cookie),
                        checked = thirdPartyCookies,
                        onCheckedChange = { viewModel.toggleThirdPartyCookies(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = SurfaceCardBorder)

                    PrivacyToggleRow(
                        title = "AES-256 Storage Encryptor",
                        desc = "Encrypt web cache, history, DOM storage & offline state with local master key",
                        icon = Icons.Default.Lock,
                        checked = encryptedLocalStorage,
                        onCheckedChange = { viewModel.toggleEncryptedLocalStorage(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = SurfaceCardBorder)

                    PrivacyToggleRow(
                        title = "Global Privacy Control (DNT/GPC)",
                        desc = "Inject DNT:1 and navigator.globalPrivacyControl signals into all sessions",
                        icon = Icons.Default.Tune,
                        checked = sendDntHeaders,
                        onCheckedChange = { viewModel.toggleSendDntHeaders(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = SurfaceCardBorder)

                    PrivacyToggleRow(
                        title = "Incognito Mode",
                        desc = "Don't save history, cookies or site data globally",
                        icon = Icons.Default.VisibilityOff,
                        checked = incognitoMode,
                        onCheckedChange = { viewModel.toggleIncognitoMode(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = SurfaceCardBorder)

                    PrivacyToggleRow(
                        title = "HTTPS-Only Mode",
                        desc = "Attempt to upgrade all HTTP connections to secure HTTPS automatically",
                        icon = Icons.Default.Lock,
                        checked = httpsOnlyMode,
                        onCheckedChange = { viewModel.toggleHttpsOnlyMode(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = SurfaceCardBorder)

                    PrivacyToggleRow(
                        title = "Strict Decentralized Mode",
                        desc = "Block all centralized Web2 traffic; only allow P2P and Hybrid mesh resolution",
                        icon = Icons.Default.Hub,
                        checked = strictDecentralizedMode,
                        onCheckedChange = { viewModel.toggleStrictDecentralizedMode(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = SurfaceCardBorder)

                    PrivacyToggleRow(
                        title = "WebAuth Support (FIDO2)",
                        desc = "Enable hardware security keys and Passkey support for WebAuthn authentication",
                        icon = Icons.Default.Fingerprint,
                        checked = webAuthEnabled,
                        onCheckedChange = { viewModel.toggleWebAuth(it) }
                    )


                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = SurfaceCardBorder)

                    PrivacyToggleRow(
                        title = "Native File Downloads",
                        desc = "Allow websites to download files to your device storage",
                        icon = Icons.Default.Cloud,
                        checked = enableDownloads,
                        onCheckedChange = { viewModel.toggleDownloads(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = SurfaceCardBorder)

                    PrivacyToggleRow(
                        title = "File Selection Uploads",
                        desc = "Allow websites to trigger Android file picker for uploads",
                        icon = Icons.Default.Storage,
                        checked = enableUploads,
                        onCheckedChange = { viewModel.toggleUploads(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = SurfaceCardBorder)

                    // SEARCH ENGINE SELECTION
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "Default Search Engine", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            com.example.viewmodel.SearchEngine.values().forEach { engine ->
                                val isSelected = viewModel.searchEngine.collectAsState().value == engine
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) ElectricCyan else SurfaceCard,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                                    modifier = Modifier.weight(1f).clickable { viewModel.setSearchEngine(engine) }
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = engine.displayName,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.Black else TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = SurfaceCardBorder)
                    
                    PrivacyToggleRow(
                        title = "Desktop Mode",
                        desc = "Request desktop version of websites by default",
                        icon = Icons.Default.Devices,
                        checked = desktopModeEnabled,
                        onCheckedChange = { viewModel.toggleDesktopMode(it) }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { viewModel.clearBrowsingData(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("clear_data_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RedTamper.copy(alpha = 0.1f),
                            contentColor = RedTamper
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RedTamper.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear All Browsing Data", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Intercepted Tracker Log Section inside Settings Page
            if (blockedTrackerLogs.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blocked_trackers_log_card"),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(0.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(SurfaceCardBorder)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "INTERCEPTED TRACKER LOGS (${blockedTrackerLogs.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                            Text(
                                text = "Clear Log",
                                fontSize = 11.sp,
                                color = ElectricCyan,
                                modifier = Modifier.clickable { viewModel.clearBlockedTrackerLogs() }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ObsidianBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                            ) {
                                blockedTrackerLogs.forEach { log ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = null, tint = RedTamper, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = log, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Production Traffic Stats Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("audit_summary_card"),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(0.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(SurfaceCardBorder)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Gateway Resolution Stats",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Web2 CDN", fontSize = 10.sp, color = TextMuted)
                                Text("${metrics.centralRequestsResolved}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AmberCentral)
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Mesh Swarm", fontSize = 10.sp, color = TextMuted)
                                Text("${metrics.decentralizedRequestsResolved}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ElectricCyan)
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Cross-Verified", fontSize = 10.sp, color = TextMuted)
                                Text("${metrics.hybridCrossVerifications}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = EmeraldMesh)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // REAL-TIME DOWNLOADS MONITOR CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(0.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(SurfaceCardBorder)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Downloads",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    if (activeDownloads.isEmpty()) {
                        Text(
                            text = "No active downloads registered.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            activeDownloads.asReversed().forEach { download ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ObsidianBg,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (download.status == "Success") EmeraldMesh.copy(alpha = 0.3f) else SurfaceCardBorder
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            com.example.ui.openDownloadedFile(context, download.downloadId, download.fileName, download.url)
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                Text(
                                                    text = download.fileName,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                if (download.status == "Success") {
                                                    Text(
                                                        text = "Tap to open",
                                                        fontSize = 9.sp,
                                                        color = EmeraldMesh,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                } else if (download.status == "Failed") {
                                                    Text(
                                                        text = "Download failed. Tap to retry",
                                                        fontSize = 9.sp,
                                                        color = Color.Red,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            val statusColor = when (download.status) {
                                                "Success" -> EmeraldMesh
                                                "Failed" -> Color.Red
                                                else -> ElectricCyan
                                            }

                                            if (download.status == "Failed") {
                                                androidx.compose.material3.Button(
                                                    onClick = {
                                                        viewModel.redownload(context, download.url, download.fileName)
                                                    },
                                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                        containerColor = Color.Red.copy(alpha = 0.15f),
                                                        contentColor = Color.Red
                                                    ),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(26.dp),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text("Redownload", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                Surface(
                                                    color = statusColor.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.padding(start = 4.dp)
                                                ) {
                                                    Text(
                                                        text = download.status,
                                                        color = statusColor,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        // Real-Time Progress Bar
                                        androidx.compose.material3.LinearProgressIndicator(
                                            progress = { download.progress },
                                            color = ElectricCyan,
                                            trackColor = SurfaceCard,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            val progressPct = (download.progress * 100).toInt()
                                            Text(
                                                text = "$progressPct% Completed",
                                                fontSize = 10.sp,
                                                color = TextMuted
                                            )

                                            val formattedDownloaded = if (download.bytesDownloaded > 0) {
                                                if (download.bytesDownloaded > 1024 * 1024) {
                                                    String.format("%.1f MB", download.bytesDownloaded.toDouble() / (1024 * 1024))
                                                } else {
                                                    String.format("%.1f KB", download.bytesDownloaded.toDouble() / 1024)
                                                }
                                            } else "0 KB"

                                            val formattedTotal = if (download.bytesTotal > 0) {
                                                if (download.bytesTotal > 1024 * 1024) {
                                                    String.format("%.1f MB", download.bytesTotal.toDouble() / (1024 * 1024))
                                                } else {
                                                    String.format("%.1f KB", download.bytesTotal.toDouble() / 1024)
                                                }
                                            } else "Unknown"

                                            Text(
                                                text = "$formattedDownloaded / $formattedTotal",
                                                fontSize = 10.sp,
                                                color = TextMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // About Section
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(0.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(SurfaceCardBorder)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "About Kaspa Browser",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Version 1.0.0\nA decentralized, peer-to-peer web browsing experience powered by the speed and security of blockDAG technology.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filter Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Request Trail (${filteredAudits.size})",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = filterMode == "ALL",
                        onClick = { filterMode = "ALL" },
                        label = { Text("All", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SurfaceElevated,
                            selectedLabelColor = TextPrimary
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                    FilterChip(
                        selected = filterMode == "P2P",
                        onClick = { filterMode = "P2P" },
                        label = { Text("P2P", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SurfaceElevated,
                            selectedLabelColor = ElectricCyan
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                    FilterChip(
                        selected = filterMode == "ALERTS",
                        onClick = { filterMode = "ALERTS" },
                        label = { Text("Alerts", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SurfaceElevated,
                            selectedLabelColor = RedTamper
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (filteredAudits.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(0.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (filterMode == "ALERTS") "No Security Alerts" else "No Traffic Audited Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (filterMode == "ALERTS") "All resolved requests are cryptographic-verified and tamper-free." else "Navigate to websites or IPFS hashes in the Gateway tab to monitor real live resolution trails and cryptographic hash attestations.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        items(filteredAudits) { audit ->
            RealTrafficAuditItemCard(audit = audit)
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun RealTrafficAuditItemCard(audit: TrafficAuditEntity) {
    val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timeString = timeFormatter.format(Date(audit.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audit_item_${audit.id}"),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(0.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (audit.isTamperProof) SurfaceCardBorder else RedTamper.copy(alpha = 0.5f)
            )
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when {
                            audit.protocol.contains("HYBRID") -> VioletBridge.copy(alpha = 0.15f)
                            audit.protocol.contains("CENTRALIZED") -> AmberCentral.copy(alpha = 0.15f)
                            else -> ElectricCyan.copy(alpha = 0.15f)
                        }
                    ) {
                        Text(
                            text = when {
                                audit.protocol.contains("HYBRID") -> "HYBRID"
                                audit.protocol.contains("CENTRALIZED") -> "HTTP"
                                else -> "P2P"
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                audit.protocol.contains("HYBRID") -> VioletBridge
                                audit.protocol.contains("CENTRALIZED") -> AmberCentral
                                else -> ElectricCyan
                            },
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = audit.url,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                Text(
                    text = timeString,
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = audit.routeTaken,
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (audit.isTamperProof) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (audit.isTamperProof) EmeraldMesh else RedTamper,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (audit.isTamperProof) "Verified Authentic" else "Tamper Warning",
                        fontSize = 10.sp,
                        color = if (audit.isTamperProof) EmeraldMesh else RedTamper,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "${audit.latencyMs}ms",
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun PrivacyToggleRow(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top
        ) {
            if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = if (checked) ElectricCyan else TextMuted,
                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) ElectricCyan else TextMuted,
                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = desc, fontSize = 10.sp, color = TextSecondary, lineHeight = 13.sp)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = ElectricCyan,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = SurfaceCard
            )
        )
    }
}

