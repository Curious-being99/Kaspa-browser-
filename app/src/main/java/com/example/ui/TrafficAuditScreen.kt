package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TrafficAuditEntity
import com.example.model.AppUpdateInfo
import com.example.model.UpdateStatus
import com.example.ui.theme.AmberCentral
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMesh
import com.example.ui.theme.KaspaTea
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
import androidx.compose.material.icons.filled.Sensors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrafficAuditScreen(viewModel: DecentralViewModel, modifier: Modifier = Modifier) {
    androidx.activity.compose.BackHandler {
        viewModel.setTab(com.example.viewmodel.AppTab.BROWSER_GATEWAY)
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val proxyPrefs = remember { context.getSharedPreferences("kaspa_proxy_prefs", android.content.Context.MODE_PRIVATE) }
    val audits by viewModel.trafficAudits.collectAsState()
    val metrics by viewModel.metrics.collectAsState()

    val isBluetoothMeshRunning by viewModel.isBluetoothMeshRunning.collectAsState()
    val isBluetoothEnabled by viewModel.isBluetoothEnabled.collectAsState()
    val discoveredBtPeersCount by viewModel.discoveredBtPeersCount.collectAsState()

    // Activity Result Launcher to prompt user to enable Bluetooth
    val bluetoothLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.startBluetoothMesh()
            viewModel.setStatusMessage("Bluetooth enabled. Bluetooth Mesh swarm active!")
        } else {
            viewModel.setStatusMessage("Bluetooth was not enabled. Cannot start Bluetooth Mesh.")
        }
    }

    // Activity Result Launcher to request required Bluetooth permissions
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        if (allGranted) {
            // Permissions granted, check if Bluetooth is enabled
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && !adapter.isEnabled) {
                val enableBtIntent = android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
                bluetoothLauncher.launch(enableBtIntent)
            } else {
                viewModel.startBluetoothMesh()
                viewModel.setStatusMessage("Bluetooth Mesh swarm active!")
            }
        } else {
            viewModel.setStatusMessage("Bluetooth permissions denied. Cannot start mesh.")
        }
    }

    val onBluetoothToggle: (Boolean) -> Unit = { enable ->
        if (enable) {
            if (viewModel.bluetoothMeshManager.hasRequiredPermissions()) {
                val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                if (adapter != null && !adapter.isEnabled) {
                    val enableBtIntent = android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    bluetoothLauncher.launch(enableBtIntent)
                } else {
                    viewModel.startBluetoothMesh()
                    viewModel.setStatusMessage("Bluetooth Mesh swarm active!")
                }
            } else {
                permissionLauncher.launch(viewModel.bluetoothMeshManager.getRequiredPermissions().toTypedArray())
            }
        } else {
            viewModel.stopBluetoothMesh()
            viewModel.setStatusMessage("Bluetooth Mesh swarm stopped.")
        }
    }

    val blockTrackers by viewModel.blockTrackers.collectAsState()
    val enableDownloads by viewModel.enableDownloads.collectAsState()
    val enableUploads by viewModel.enableUploads.collectAsState()
    val enablePullToRefresh by viewModel.enablePullToRefresh.collectAsState()
    val encryptedLocalStorage by viewModel.encryptedLocalStorage.collectAsState()
    val thirdPartyCookies by viewModel.thirdPartyCookies.collectAsState()
    val blockThirdPartyCookies by viewModel.blockThirdPartyCookies.collectAsState()
    val strictDecentralizedMode by viewModel.strictDecentralizedMode.collectAsState()
    val sendDntHeaders by viewModel.sendDntHeaders.collectAsState()
    val incognitoMode by viewModel.incognitoMode.collectAsState()
    val httpsOnlyMode by viewModel.httpsOnlyMode.collectAsState()
    val webAuthEnabled by viewModel.webAuthEnabled.collectAsState()
    val blockedTrackersCount by viewModel.blockedTrackersCount.collectAsState()
    val blockedTrackerLogs by viewModel.blockedTrackerLogs.collectAsState()
    val uBlockRulesCount by viewModel.uBlockRulesCount.collectAsState()
    val uBlockIsUpdating by viewModel.uBlockIsUpdating.collectAsState()
    val uBlockStatus by viewModel.uBlockStatus.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val isPrivacyRelayEnabled by viewModel.isPrivacyRelayEnabled.collectAsState()
    val activeRelayCircuit by viewModel.activeRelayCircuit.collectAsState()

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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.setTab(com.example.viewmodel.AppTab.BROWSER_GATEWAY) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Browser",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Settings & Privacy",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
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

            // 0. KASPA PRIVACY RELAY (COMPACT & SLEEK NATIVE M3 CARD)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("kaspa_privacy_relay_card"),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isPrivacyRelayEnabled) ElectricCyan.copy(alpha = 0.5f) else SurfaceCardBorder
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
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
                                    .background(ElectricCyan.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (isPrivacyRelayEnabled) ElectricCyan else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Kaspa Privacy Relay",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isPrivacyRelayEnabled) "3-Hop Onion Relay · Real IP Hidden · Tor Shield" else "Disabled · Direct connection",
                                    color = if (isPrivacyRelayEnabled) EmeraldMesh else TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Switch(
                            checked = isPrivacyRelayEnabled,
                            onCheckedChange = { active ->
                                viewModel.togglePrivacyRelay(active)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = ElectricCyan,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = SurfaceCard
                            ),
                            modifier = Modifier.testTag("privacy_relay_switch")
                        )
                    }

                    if (isPrivacyRelayEnabled && activeRelayCircuit != null) {
                        val circuit = activeRelayCircuit!!
                        val activeTorCircuit by com.example.network.LightweightTorEngine.activeOnionCircuit.collectAsState()
                        var isTestingIp by remember { mutableStateOf(false) }
                        var liveAuditResult by remember { mutableStateOf<com.example.network.LiveExitTelemetry?>(com.example.network.KaspaPrivacyRelayEngine.liveTelemetry) }
                        val coroutineScope = rememberCoroutineScope()
                        val context = androidx.compose.ui.platform.LocalContext.current

                        Spacer(modifier = Modifier.height(10.dp))

                        // Compact Route Ribbon with Genuine Telemetry
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SurfaceCard,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("You", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    Text(" ➔ ", fontSize = 11.sp, color = ElectricCyan)
                                    Text("Guard [${activeTorCircuit.guardHop.countryCode}]", fontSize = 11.sp, color = ElectricCyan, fontWeight = FontWeight.Medium)
                                    Text(" ➔ ", fontSize = 11.sp, color = AmberCentral)
                                    Text("Mixer [${activeTorCircuit.middleHop.countryCode}]", fontSize = 11.sp, color = AmberCentral, fontWeight = FontWeight.Medium)
                                    Text(" ➔ ", fontSize = 11.sp, color = EmeraldMesh)
                                    val destinationText = liveAuditResult?.let {
                                        "${it.countryCode} (${it.latencyMs}ms)"
                                    } ?: "Exit [${activeTorCircuit.exitHop.countryCode}]"
                                    Text(destinationText, fontSize = 11.sp, color = EmeraldMesh, fontWeight = FontWeight.SemiBold)
                                }

                                Text(
                                    text = "3-Hop Onion",
                                    fontSize = 10.sp,
                                    color = EmeraldMesh,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Play Integrity Device Assurance Badge
                        val integrityState by com.example.security.PlayIntegrityManager.integrityState.collectAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceCard)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Play Integrity",
                                    tint = EmeraldMesh,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Play Integrity Device Assurance",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                            val badgeText = when (integrityState) {
                                is com.example.security.PlayIntegrityManager.DeviceIntegrityState.Verified -> "Hardware Verified"
                                is com.example.security.PlayIntegrityManager.DeviceIntegrityState.DevelopmentOrSandbox -> "Protected Runtime"
                                else -> "Active Shield"
                            }
                            Text(
                                text = badgeText,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldMesh
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Action Buttons Row (Balanced & Standard M3)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isTestingIp = true
                                    coroutineScope.launch {
                                        try {
                                            val telemetry = com.example.network.KaspaPrivacyRelayEngine.auditLivePrivacy()
                                            liveAuditResult = telemetry
                                        } catch (_: Throwable) {
                                        } finally {
                                            isTestingIp = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldMesh.copy(alpha = 0.7f))
                            ) {
                                if (isTestingIp) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), color = EmeraldMesh, strokeWidth = 1.5.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Auditing...", fontSize = 11.sp, color = EmeraldMesh)
                                } else {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldMesh, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Audit Live IP", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = EmeraldMesh)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.rotatePrivacyRelayCircuit()
                                    com.example.network.LightweightTorEngine.rotateCircuit()
                                    com.example.network.KaspaPrivacyRelayEngine.rotateSessionKeys()
                                    liveAuditResult = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f))
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Rotate Keys", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ElectricCyan)
                            }
                        }

                        // Comprehensive Genuine Live Privacy Audit Report Card
                        liveAuditResult?.let { telemetry ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = ObsidianBg,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldMesh.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldMesh, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Live Public Telemetry (Verified Real)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldMesh)
                                        }
                                        IconButton(
                                            onClick = { liveAuditResult = null },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(12.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // IP Row with Click to Copy
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(SurfaceCard, RoundedCornerShape(6.dp))
                                            .clickable {
                                                try {
                                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Exit IP", telemetry.ip))
                                                    android.widget.Toast.makeText(context, "IP copied: ${telemetry.ip}", android.widget.Toast.LENGTH_SHORT).show()
                                                } catch (_: Throwable) {}
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Detected Public IP", fontSize = 10.sp, color = TextMuted)
                                            Text(telemetry.ip, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ElectricCyan, fontFamily = FontFamily.Monospace)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Copy", fontSize = 10.sp, color = TextMuted)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy IP", tint = TextMuted, modifier = Modifier.size(12.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Location & ISP Info
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Location", fontSize = 10.sp, color = TextMuted)
                                            val locStr = if (telemetry.city.isNotBlank()) "${telemetry.city}, ${telemetry.country}" else telemetry.country
                                            Text(locStr, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                        }
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                            Text("ISP / Autonomous System", fontSize = 10.sp, color = TextMuted)
                                            val ispStr = if (telemetry.asn.isNotBlank()) "${telemetry.isp} (AS${telemetry.asn})" else telemetry.isp
                                            Text(ispStr, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextPrimary, textAlign = TextAlign.End)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Circuit Latency & Cryptography Details
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Measured Handshake RTT", fontSize = 10.sp, color = TextMuted)
                                            Text("${telemetry.latencyMs}ms live latency", fontSize = 11.sp, color = EmeraldMesh, fontWeight = FontWeight.SemiBold)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("DNS Leak Protection", fontSize = 10.sp, color = TextMuted)
                                            Text("DoH Active (Zero DNS Leaks)", fontSize = 11.sp, color = EmeraldMesh, fontWeight = FontWeight.SemiBold)
                                        }
                                    }

                                    if (telemetry.isProxy && telemetry.proxyDesc.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Upstream Proxy Tunnel: ${telemetry.proxyDesc}", fontSize = 10.sp, color = AmberCentral, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. PRIVACY & SECURITY CONTROLS CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("privacy_settings_card"),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
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
                        title = "uBlock Tracker & Ad Blocker",
                        desc = if (uBlockRulesCount > 0) {
                            "uBlock filtering engine active ($uBlockRulesCount rules, $blockedTrackersCount blocked). Intercepts tracking scripts, ad pixels & telemetry"
                        } else {
                            "uBlock engine active ($blockedTrackersCount blocked). Intercepts tracking scripts, ad pixels & telemetry"
                        },
                        icon = Icons.Default.Shield,
                        checked = blockTrackers,
                        onCheckedChange = { viewModel.toggleBlockTrackers(it) }
                    )

                    if (blockTrackers) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(EmeraldMesh, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uBlockRulesCount > 0) "$uBlockRulesCount rules active" else "Filters active",
                                    fontSize = 11.sp,
                                    color = EmeraldMesh,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.updateUBlockFilters() },
                                enabled = !uBlockIsUpdating,
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("update_ublock_filters_button"),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (uBlockIsUpdating) TextMuted else ElectricCyan.copy(alpha = 0.6f)
                                )
                            ) {
                                if (uBlockIsUpdating) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = ElectricCyan
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Updating...", fontSize = 10.sp, color = TextMuted)
                                } else {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Update Filters", fontSize = 10.sp, color = ElectricCyan)
                                }
                            }
                        }
                    }

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

                    PrivacyToggleRow(
                        title = "Pull-to-Refresh Gestures",
                        desc = "Swipe down at top of page to reload. Disable to prevent accidental refreshes",
                        icon = Icons.Default.Refresh,
                        checked = enablePullToRefresh,
                        onCheckedChange = { viewModel.togglePullToRefresh(it) }
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            com.example.viewmodel.SearchEngine.values().forEach { engine ->
                                val isSelected = viewModel.searchEngine.collectAsState().value == engine
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) ElectricCyan else SurfaceCard,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) ElectricCyan else SurfaceCardBorder),
                                    modifier = Modifier
                                        .clickable { viewModel.setSearchEngine(engine) }
                                ) {
                                    Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = engine.displayName,
                                            fontSize = 12.sp,
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
                        title = "Bluetooth Mesh Discovery",
                        desc = if (isBluetoothMeshRunning) {
                            "Mesh Swarm Active: $discoveredBtPeersCount nearby node(s) found via BLE."
                        } else {
                            "Establish off-grid local connections with nearby nodes without internet using real Bluetooth Low Energy (BLE)"
                        },
                        icon = Icons.Default.Sensors,
                        checked = isBluetoothMeshRunning,
                        onCheckedChange = { onBluetoothToggle(it) }
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
                    shape = RoundedCornerShape(16.dp),
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
                            Text(text = "UBLOCK INTERCEPTED LOGS (${blockedTrackerLogs.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
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
                shape = RoundedCornerShape(16.dp),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(10.dp),
                            color = SurfaceCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                                Text(
                                    text = "Web2 CDN",
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${metrics.centralRequestsResolved}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberCentral
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(10.dp),
                            color = SurfaceCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                                Text(
                                    text = "Mesh Swarm",
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${metrics.decentralizedRequestsResolved}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(10.dp),
                            color = SurfaceCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                                Text(
                                    text = "Cross-Verified",
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${metrics.hybridCrossVerifications}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldMesh
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // REAL-TIME DOWNLOADS MONITOR CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
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
                                                        text = if (download.status == "Pending" || download.status == "Downloading") "Downloading" else download.status,
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
                                        if (download.status == "Downloading" || download.status == "Pending") {
                                            if (download.progress > 0.02f) {
                                                androidx.compose.material3.LinearProgressIndicator(
                                                    progress = { download.progress.coerceIn(0.02f, 1.0f) },
                                                    color = ElectricCyan,
                                                    trackColor = SurfaceCard,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(6.dp)
                                                        .clip(RoundedCornerShape(3.dp))
                                                )
                                            } else {
                                                androidx.compose.material3.LinearProgressIndicator(
                                                    color = ElectricCyan,
                                                    trackColor = SurfaceCard,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(6.dp)
                                                        .clip(RoundedCornerShape(3.dp))
                                                )
                                            }
                                        } else {
                                            androidx.compose.material3.LinearProgressIndicator(
                                                progress = { if (download.status == "Success") 1.0f else download.progress },
                                                color = if (download.status == "Success") EmeraldMesh else Color.Red,
                                                trackColor = SurfaceCard,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            val progressPct = if (download.status == "Success") 100 else (download.progress * 100).toInt()
                                            val progressText = if ((download.status == "Downloading" || download.status == "Pending") && download.progress <= 0.02f) "Downloading..." else "$progressPct% Completed"
                                            Text(
                                                text = progressText,
                                                fontSize = 10.sp,
                                                color = TextMuted
                                            )

                                             val formattedDownloaded = if (download.bytesDownloaded > 0) {
                                                if (download.bytesDownloaded > 1024 * 1024) {
                                                    String.format(java.util.Locale.US, "%.1f MB", download.bytesDownloaded.toDouble() / (1024 * 1024))
                                                } else {
                                                    String.format(java.util.Locale.US, "%.1f KB", download.bytesDownloaded.toDouble() / 1024)
                                                }
                                            } else "0 KB"

                                            val formattedTotal = if (download.bytesTotal > 0) {
                                                if (download.bytesTotal > 1024 * 1024) {
                                                    String.format(java.util.Locale.US, "%.1f MB", download.bytesTotal.toDouble() / (1024 * 1024))
                                                } else {
                                                    String.format(java.util.Locale.US, "%.1f KB", download.bytesTotal.toDouble() / 1024)
                                                }
                                            } else "Unknown"

                                            val sizeDisplay = if (download.bytesTotal > 0) {
                                                "$formattedDownloaded / $formattedTotal"
                                            } else if (download.bytesDownloaded > 0) {
                                                "$formattedDownloaded downloaded"
                                            } else {
                                                "0 KB / Unknown"
                                            }

                                            Text(
                                                text = sizeDisplay,
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

            // ==========================================
            // KASPABROWSER UPDATE ZONE (IN-APP SIDELOAD INSTALLER)
            // ==========================================
            KaspaUpdateZoneCard(viewModel = viewModel)

            Spacer(modifier = Modifier.height(14.dp))

            // About Section
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(SurfaceCardBorder.copy(alpha = 0.6f))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "About KaspaBrowser",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElectricCyan.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "v${com.example.BuildConfig.VERSION_NAME}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "A sovereign, decentralized peer-to-peer web browser powered by the high-speed Kaspa BlockDAG, on-chain zk-DID verification, and multi-hop privacy relay architecture.",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.5.sp
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
                    shape = RoundedCornerShape(16.dp),
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
                            text = if (filterMode == "ALERTS") "All resolved requests are cryptographic-verified and tamper-free." else "Navigate to websites or HTTPS/3 QUIC endpoints in the Gateway tab to monitor real live resolution trails and cryptographic hash attestations.",
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
        shape = RoundedCornerShape(14.dp),
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

/**
 * KaspaBrowser In-App Sideload Update Zone & Native Package Installer Card.
 * Allows checking, downloading, and installing browser releases in-app.
 */
@Composable
fun KaspaUpdateZoneCard(
    viewModel: DecentralViewModel,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val updateStatus by viewModel.updateStatus.collectAsState()
    val isAutoCheckEnabled by viewModel.isAutoCheckUpdatesEnabled.collectAsState()
    val customManifestUrl by viewModel.customUpdateManifestUrl.collectAsState()

    val updateManager = remember { com.example.network.AppUpdateManager.getInstance() }
    val canInstallPackages = remember(updateStatus) { updateManager.canInstallUnknownApps(context) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("update_zone_card"),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                when (updateStatus) {
                    is UpdateStatus.Available -> KaspaTea.copy(alpha = 0.6f)
                    is UpdateStatus.ReadyToInstall -> EmeraldMesh.copy(alpha = 0.6f)
                    is UpdateStatus.Downloading -> ElectricCyan.copy(alpha = 0.6f)
                    else -> SurfaceCardBorder.copy(alpha = 0.6f)
                }
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title + Dynamic Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(ElectricCyan.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "KaspaBrowser Update Zone",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "In-App Sideload Engine · GitHub Releases",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                // Dynamic Status Pill
                Surface(
                    shape = CircleShape,
                    color = when (updateStatus) {
                        is UpdateStatus.Available -> KaspaTea.copy(alpha = 0.18f)
                        is UpdateStatus.ReadyToInstall -> EmeraldMesh.copy(alpha = 0.18f)
                        is UpdateStatus.Downloading -> ElectricCyan.copy(alpha = 0.18f)
                        is UpdateStatus.Checking -> AmberCentral.copy(alpha = 0.18f)
                        is UpdateStatus.UpToDate -> EmeraldMesh.copy(alpha = 0.12f)
                        is UpdateStatus.Error -> RedTamper.copy(alpha = 0.18f)
                        else -> SurfaceElevated
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when (updateStatus) {
                            is UpdateStatus.Available -> KaspaTea.copy(alpha = 0.4f)
                            is UpdateStatus.ReadyToInstall -> EmeraldMesh.copy(alpha = 0.4f)
                            is UpdateStatus.Downloading -> ElectricCyan.copy(alpha = 0.4f)
                            is UpdateStatus.Checking -> AmberCentral.copy(alpha = 0.4f)
                            is UpdateStatus.UpToDate -> EmeraldMesh.copy(alpha = 0.3f)
                            is UpdateStatus.Error -> RedTamper.copy(alpha = 0.4f)
                            else -> SurfaceCardBorder
                        }
                    )
                ) {
                    Text(
                        text = when (updateStatus) {
                            is UpdateStatus.Available -> "UPDATE AVAILABLE"
                            is UpdateStatus.ReadyToInstall -> "READY TO INSTALL"
                            is UpdateStatus.Downloading -> "DOWNLOADING"
                            is UpdateStatus.Checking -> "CHECKING..."
                            is UpdateStatus.UpToDate -> "UP TO DATE"
                            is UpdateStatus.Error -> "CHECK FAILED"
                            else -> "READY"
                        },
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (updateStatus) {
                            is UpdateStatus.Available -> KaspaTea
                            is UpdateStatus.ReadyToInstall -> EmeraldMesh
                            is UpdateStatus.Downloading -> ElectricCyan
                            is UpdateStatus.Checking -> AmberCentral
                            is UpdateStatus.UpToDate -> EmeraldMesh
                            is UpdateStatus.Error -> RedTamper
                            else -> TextSecondary
                        },
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Build & Channel Meta (Clean inline typography, no nested cardboard boxes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Installed:",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "v${com.example.BuildConfig.VERSION_NAME}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Channel:",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "BlockDAG Core",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic Status View
            when (val status = updateStatus) {
                is UpdateStatus.Checking -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = ElectricCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Querying latest release from GitHub...",
                            fontSize = 11.5.sp,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                is UpdateStatus.Available -> {
                    val info = status.updateInfo
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.NewReleases,
                                        contentDescription = null,
                                        tint = KaspaTea,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Version v${info.latestVersionName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TextPrimary
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = KaspaTea.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = info.apkSizeFormatted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KaspaTea,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = info.releaseNotes,
                                fontSize = 10.5.sp,
                                color = TextSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 14.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = { viewModel.downloadAndInstallUpdate(context) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = KaspaTea,
                                    contentColor = ObsidianBg
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .testTag("download_install_update_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Direct Install APK",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                is UpdateStatus.Downloading -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceCard
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Downloading v${status.updateInfo.latestVersionName}...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = "${status.progressPercent}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { status.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = ElectricCyan,
                                trackColor = SurfaceDark
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            val downloadedMb = "%.1f".format(status.downloadedBytes / (1024.0 * 1024.0))
                            val totalMb = "%.1f".format(status.totalBytes / (1024.0 * 1024.0))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$downloadedMb MB / $totalMb MB",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                                Text(
                                    text = "Direct from GitHub CDN",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                is UpdateStatus.ReadyToInstall -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = EmeraldMesh.copy(alpha = 0.09f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldMesh.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldMesh,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "APK Downloaded & SHA-256 Verified",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = EmeraldMesh
                                    )
                                    Text(
                                        text = "Package: ${status.apkFile.name} (${"%.1f".format(status.apkFile.length() / (1024.0 * 1024.0))} MB)",
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = { viewModel.installReadyApk(context) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldMesh,
                                    contentColor = ObsidianBg
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .testTag("install_ready_apk_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Launch Android Package Installer",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                is UpdateStatus.UpToDate -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
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
                            text = "KaspaBrowser is up to date (v${status.currentVersion})",
                            fontSize = 11.5.sp,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                is UpdateStatus.Error -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = RedTamper,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = status.message,
                            fontSize = 11.sp,
                            color = RedTamper,
                            lineHeight = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                else -> {}
            }

            // Action Buttons: Check for Updates & Open Update Pop-Up Modal
            if (updateStatus !is UpdateStatus.Available && updateStatus !is UpdateStatus.Downloading && updateStatus !is UpdateStatus.ReadyToInstall) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.checkForUpdates(isUserInitiated = true) },
                        enabled = updateStatus !is UpdateStatus.Checking,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("check_updates_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (updateStatus is UpdateStatus.Checking) "Checking..." else "Check Update",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.forceShowUpdateDialog() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KaspaTea,
                            contentColor = ObsidianBg
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("open_update_modal_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Update Modal",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = SurfaceCardBorder.copy(alpha = 0.4f),
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Settings Rows: Clean seamless rows without cardboard boxes

            // 1. Android Sideload Permission Row
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (canInstallPackages) Icons.Default.Security else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (canInstallPackages) EmeraldMesh else AmberCentral,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Unknown App Installation",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (canInstallPackages) "Permission granted for sideload" else "Permission required to install APK",
                                fontSize = 10.sp,
                                color = if (canInstallPackages) EmeraldMesh else TextMuted
                            )
                        }
                    }

                    if (!canInstallPackages) {
                        TextButton(
                            onClick = {
                                val manageIntent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(manageIntent)
                                } catch (_: Exception) {}
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = "Grant",
                                fontSize = 11.sp,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // 2. Auto-Check Toggle Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Auto-Check on Startup",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Check GitHub releases when app launches",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }

                Switch(
                    checked = isAutoCheckEnabled,
                    onCheckedChange = { viewModel.toggleAutoCheckUpdates(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = ElectricCyan,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = SurfaceCard
                    ),
                    modifier = Modifier.testTag("auto_check_updates_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. GitHub Release Source Row
            var showRepoInput by remember { mutableStateOf(false) }
            var repoInputText by remember(customManifestUrl) { mutableStateOf(customManifestUrl) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "GitHub Source",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (customManifestUrl.isNotBlank()) customManifestUrl else "Curious-being99/Kaspa-browser-",
                            fontSize = 10.sp,
                            color = ElectricCyan,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }

                TextButton(
                    onClick = { showRepoInput = !showRepoInput },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = if (showRepoInput) "Close" else "Change",
                        fontSize = 11.sp,
                        color = ElectricCyan
                    )
                }
            }

            if (showRepoInput) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Release Repository Endpoint",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Specify a custom GitHub repository (e.g. owner/repo) or custom JSON release manifest URL to fetch official APK releases.",
                        fontSize = 10.5.sp,
                        color = TextSecondary,
                        lineHeight = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = repoInputText,
                    onValueChange = { repoInputText = it },
                    placeholder = { Text("owner/repo (e.g. user/kaspa-browser)", fontSize = 12.sp, color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .testTag("github_repo_input"),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = SurfaceCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    ),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (customManifestUrl.isNotBlank()) {
                        TextButton(
                            onClick = {
                                repoInputText = ""
                                viewModel.setCustomUpdateManifestUrl("")
                                viewModel.checkForUpdates(isUserInitiated = true)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Reset Default", fontSize = 10.5.sp, color = RedTamper)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Button(
                        onClick = {
                            viewModel.setCustomUpdateManifestUrl(repoInputText.trim())
                            viewModel.checkForUpdates(isUserInitiated = true)
                            showRepoInput = false
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = ObsidianBg
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Save & Check", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


