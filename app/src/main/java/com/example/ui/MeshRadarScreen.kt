// Trigger rebuild
package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PeerEntity
import com.example.model.NetworkMetrics
import com.example.ui.theme.AmberCentral
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMesh
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletBridge
import com.example.viewmodel.DecentralViewModel

@Composable
fun MeshRadarScreen(viewModel: DecentralViewModel, modifier: Modifier = Modifier) {
    val metrics by viewModel.metrics.collectAsState()
    val peers by viewModel.peers.collectAsState()
    val nsdState by viewModel.nsdState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    var peerFilter by remember { mutableStateOf("ALL") }
    var showConnectDialog by remember { mutableStateOf(false) }

    val filteredPeers = remember(peers, peerFilter) {
        when (peerFilter) {
            "LAN" -> peers.filter { it.region.contains("NSD") || it.region.contains("Local Subnet") || (!it.isBootstrap && !it.multiaddress.contains("dns4")) }
            "DIRECT" -> peers.filter { !it.isBootstrap }
            "BOOTSTRAP" -> peers.filter { it.isBootstrap }
            else -> peers
        }
    }

    if (showConnectDialog) {
        ManualP2PConnectionDialog(
            onDismiss = { showConnectDialog = false },
            onConnect = { host, port ->
                showConnectDialog = false
                viewModel.connectToP2PPeer(host, port)
            }
        )
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.setTab(com.example.viewmodel.AppTab.BROWSER_GATEWAY) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Browser",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (metrics.isDaemonRunning) EmeraldMesh else AmberCentral)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Peer Nodes & Gateways",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                    Text(
                        text = "Local mDNS Discovery & Public Relays",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Real System Daemon Status Hero Card
            val fullLocalMultiaddr = "/ip4/${metrics.localIp}/tcp/${viewModel.nodeManager.listeningPort}/p2p/${metrics.localNodeId}"
            RealDaemonStatusCard(
                metrics = metrics,
                daemonPort = viewModel.nodeManager.listeningPort,
                onCopyNodeId = {
                    clipboardManager.setText(AnnotatedString(fullLocalMultiaddr))
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Real NsdManager Discovery Service Card (mDNS / DNS-SD P2P Node Discovery)
            NsdDiscoveryCard(
                nsdState = nsdState,
                onScanLocalNetwork = { viewModel.scanLocalNsdNetwork() },
                onManualConnect = { showConnectDialog = true }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Telemetry Metrics Grid (2x2 spacious high contrast layout)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RealMetricTile(
                        label = "Active Peers",
                        value = "${metrics.activePeers}",
                        unit = if (metrics.activePeers <= 1) "1 Local Node" else "${metrics.activePeers} Swarm Nodes",
                        icon = Icons.Default.Hub,
                        color = EmeraldMesh,
                        modifier = Modifier.weight(1f)
                    )
                    RealMetricTile(
                        label = "Public Gateways",
                        value = "${peers.count { it.isBootstrap && it.isOnline }}/${peers.count { it.isBootstrap }.coerceAtLeast(1)}",
                        unit = "Live Relays",
                        icon = Icons.Default.Language,
                        color = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RealMetricTile(
                        label = "Bandwidth Saved",
                        value = "${(metrics.p2pBandwidthSavedBytes / 1024 / 1024.0).let { "%.1f".format(it) }} MB",
                        unit = "P2P Offloaded",
                        icon = Icons.Default.Wifi,
                        color = VioletBridge,
                        modifier = Modifier.weight(1f)
                    )
                    RealMetricTile(
                        label = "Cross Checks",
                        value = "${metrics.hybridCrossVerifications}",
                        unit = "Verified Blocks",
                        icon = Icons.Default.Security,
                        color = AmberCentral,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Peer Swarm Section Header & Filter Tabs
            val onlineCount = peers.count { it.isOnline }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connected Peers & Gateways ($onlineCount/${peers.size} Online)",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.refreshPeerLatencies() },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("refresh_peers_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Gateways",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    FilterChip(
                        selected = peerFilter == "ALL",
                        onClick = { peerFilter = "ALL" },
                        label = { Text("All", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SurfaceElevated,
                            selectedLabelColor = TextPrimary
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                    FilterChip(
                        selected = peerFilter == "LAN",
                        onClick = { peerFilter = "LAN" },
                        label = { Text("LAN (NSD)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SurfaceElevated,
                            selectedLabelColor = EmeraldMesh
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                    FilterChip(
                        selected = peerFilter == "BOOTSTRAP",
                        onClick = { peerFilter = "BOOTSTRAP" },
                        label = { Text("Relays", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SurfaceElevated,
                            selectedLabelColor = VioletBridge
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (filteredPeers.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(0.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.SensorsOff,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (peerFilter == "LAN") "No LAN Peers Found" else "No Nodes in Current Filter",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (peerFilter == "LAN") "Run a local scan or tap 'Direct Connect' above to connect to another device running this app on your Wi-Fi." else "Tap 'Refresh' or switch filters above to view active gateways.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        items(filteredPeers) { peer ->
            RealPeerItemCard(
                peer = peer,
                onCopyAddress = {
                    clipboardManager.setText(AnnotatedString(peer.multiaddress))
                },
                onPingPeer = {
                    val parts = peer.multiaddress.split("/")
                    val ipIdx = parts.indexOfFirst { it == "ip4" }
                    val tcpIdx = parts.indexOfFirst { it == "tcp" }
                    if (ipIdx != -1 && tcpIdx != -1 && ipIdx + 1 < parts.size && tcpIdx + 1 < parts.size) {
                        val host = parts[ipIdx + 1]
                        val port = parts[tcpIdx + 1].toIntOrNull() ?: 8080
                        viewModel.connectToP2PPeer(host, port)
                    } else {
                        viewModel.refreshPeerLatencies()
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun RealDaemonStatusCard(metrics: NetworkMetrics, daemonPort: Int = 8080, onCopyNodeId: () -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    val isNodeActive = metrics.isOnline && metrics.isDaemonRunning

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("node_telemetry_card"),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isNodeActive) EmeraldMesh.copy(alpha = 0.15f) else AmberCentral.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "Active Node",
                            tint = if (isNodeActive) EmeraldMesh else AmberCentral,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Active Node",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isNodeActive) "ONLINE" else "OFFLINE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isNodeActive) EmeraldMesh else AmberCentral
                            )
                        }
                        Text(
                            text = if (isNodeActive) "P2P Daemon Listening • Port $daemonPort" else "Node Offline • Tap Power to Connect",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextSecondary
                )
            }

            if (expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Clean Node Swarm Address
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Node Multiaddress",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "p2p/${metrics.localNodeId}",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan,
                                    maxLines = 1
                                )
                            }

                            IconButton(
                                onClick = onCopyNodeId,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("copy_node_id_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Node Multiaddress",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SurfaceElevated,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Protocol:", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                                Text(text = "/dnet/p2p/1.0", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SurfaceElevated,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Mesh State:", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (isNodeActive) "${metrics.meshHealthPercentage}% ACTIVE" else "0% OFFLINE",
                                    fontSize = 11.sp,
                                    color = if (isNodeActive) EmeraldMesh else AmberCentral,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transport: TCP Swarm Socket",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Port: $daemonPort",
                            fontSize = 11.sp,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RealMetricTile(
    label: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(0.dp),
        color = SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = unit,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
    }
}

@Composable
fun BridgeTopologyOverviewCard(daemonPort: Int = 8080) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("topology_radar_card"),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Open Source Internet Configuration & Routing",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 14.sp
            )
            Text(
                text = "P2P daemon and decentralized gateway topologies operating in parallel",
                fontSize = 11.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Visual pathway rows (Real network architecture layout)
            PathwayRow(
                title = "1. Local P2P Node (LAN / mDNS)",
                protocol = "NsdManager DNS-SD / Direct TCP (Port $daemonPort)",
                status = "Primary Zero-Trust",
                statusColor = EmeraldMesh,
                icon = Icons.Default.Hub
            )

            Spacer(modifier = Modifier.height(8.dp))

            PathwayRow(
                title = "2. Cryptographic Bridge Gateway",
                protocol = "Local Daemon Proxy (Port $daemonPort)",
                status = "Bridging Protocols",
                statusColor = VioletBridge,
                icon = Icons.Default.Router
            )

            Spacer(modifier = Modifier.height(8.dp))

            PathwayRow(
                title = "3. Centralized Edge CDNs & Mirrors",
                protocol = "TLS 1.3 / Cloudflare / HTTPS",
                status = "Edge Mirror",
                statusColor = AmberCentral,
                icon = Icons.Default.Cloud
            )
        }
    }
}

@Composable
fun PathwayRow(
    title: String,
    protocol: String,
    status: String,
    statusColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(imageVector = icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = protocol, fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = statusColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = status,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun RealPeerItemCard(
    peer: PeerEntity,
    onCopyAddress: () -> Unit,
    onPingPeer: () -> Unit = {}
) {
    val isNsdPeer = peer.region.contains("NSD") || peer.region.contains("Local Subnet")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("peer_card_${peer.peerId}"),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                peer.isBootstrap -> VioletBridge.copy(alpha = 0.15f)
                                isNsdPeer -> EmeraldMesh.copy(alpha = 0.15f)
                                else -> ElectricCyan.copy(alpha = 0.15f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            peer.isBootstrap -> Icons.Default.Cloud
                            isNsdPeer -> Icons.Default.Sensors
                            else -> Icons.Default.Hub
                        },
                        contentDescription = null,
                        tint = when {
                            peer.isBootstrap -> VioletBridge
                            isNsdPeer -> EmeraldMesh
                            else -> ElectricCyan
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = peer.name,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                        if (peer.isBootstrap) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = VioletBridge.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "RELAY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VioletBridge,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        } else if (isNsdPeer) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = EmeraldMesh.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "mDNS / NSD",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldMesh,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "p2p/${peer.peerId}",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Blocks: ${peer.blocksShared}",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (peer.isOnline) EmeraldMesh else AmberCentral)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${peer.latencyMs}ms",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = if (peer.isOnline) "Connected" else "Standby",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (peer.isOnline) EmeraldMesh else TextMuted
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onPingPeer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Handshake Peer",
                        tint = if (isNsdPeer) EmeraldMesh else ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NsdDiscoveryCard(
    nsdState: com.example.network.NsdDiscoveryState,
    onScanLocalNetwork: () -> Unit,
    onManualConnect: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("nsd_discovery_card"),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(EmeraldMesh.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (nsdState.isBroadcasting) Icons.Default.Sensors else Icons.Default.SensorsOff,
                            contentDescription = null,
                            tint = if (nsdState.isBroadcasting) EmeraldMesh else AmberCentral,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "mDNS / NSD Local Discovery",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Zero-Config LAN Peer Resolution",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextSecondary
                )
            }

            if (expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                shape = RoundedCornerShape(10.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Service Type:", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        Text(
                            text = nsdState.serviceType,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Registered As:", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        Text(
                            text = nsdState.registeredServiceName ?: "Initializing...",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Status Feed:", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        Text(
                            text = nsdState.lastDiscoveryEvent,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = if (nsdState.isScanning) ElectricCyan else TextPrimary,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onScanLocalNetwork,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("scan_local_nsd_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    if (nsdState.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = ElectricCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Scanning...", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Scan LAN (NSD)", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onManualConnect,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manual_p2p_connect_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cable,
                        contentDescription = null,
                        tint = EmeraldMesh,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Direct Connect", color = EmeraldMesh, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
                }
            }
        }
    }
}

@Composable
fun ManualP2PConnectionDialog(
    onDismiss: () -> Unit,
    onConnect: (String, Int) -> Unit
) {
    var host by remember { mutableStateOf("192.168.1.") }
    var portText by remember { mutableStateOf("8080") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Manual P2P Connection",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "Initiate a direct TCP cryptographic handshake with another node on the local subnet or network.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Peer Host / IP Address") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = SurfaceCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("manual_host_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("Peer Port") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = SurfaceCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("manual_port_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portText.toIntOrNull() ?: 8080
                    if (host.isNotBlank()) {
                        onConnect(host.trim(), port)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldMesh),
                modifier = Modifier.testTag("confirm_manual_connect_button")
            ) {
                Text("Connect", color = ObsidianBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(0.dp)
    )
}

private fun maskIpAddress(ip: String): String {
    if (ip.isBlank()) return "Unknown"
    if (ip == "127.0.0.1" || ip.lowercase() == "localhost") return "127.0.0.x"
    val parts = ip.split(".")
    if (parts.size == 4) {
        return "${parts[0]}.${parts[1]}.x.x"
    }
    return "[Protected Address]"
}

private fun maskMultiaddress(addr: String): String {
    if (addr.isBlank()) return ""
    val parts = addr.split("/")
    val ipIdx = parts.indexOfFirst { it == "ip4" }
    if (ipIdx != -1 && ipIdx + 1 < parts.size) {
        val ip = parts[ipIdx + 1]
        val masked = maskIpAddress(ip)
        val mutableParts = parts.toMutableList()
        mutableParts[ipIdx + 1] = masked
        return mutableParts.joinToString("/")
    }
    return addr
}


