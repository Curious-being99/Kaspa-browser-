package com.example.model

enum class NetworkProtocol {
    CENTRALIZED_HTTP,
    DECENTRALIZED_P2P,
    HYBRID_COEXISTENCE
}

enum class VerificationStatus {
    VERIFIED_TAMPER_PROOF,
    MIRROR_MATCHED,
    TAMPERED_HASH_MISMATCH,
    UNVERIFIED
}

data class ResolvedResource(
    val url: String,
    val resolvedProtocol: NetworkProtocol,
    val cid: String,
    val title: String,
    val content: String,
    val contentType: String,
    val sizeBytes: Long,
    val latencyMs: Long,
    val centralizedUrl: String? = null,
    val centralizedLatencyMs: Long? = null,
    val centralizedIp: String? = null,
    val decentralizedPeersCount: Int = 0,
    val decentralizedLatencyMs: Long? = null,
    val verificationStatus: VerificationStatus = VerificationStatus.VERIFIED_TAMPER_PROOF,
    val cryptographicHash: String,
    val routedVia: String,
    val kaspaProof: KaspaProof? = null,
    val kaspaVerificationSummary: String? = null
)

data class PeerNode(
    val peerId: String,
    val name: String,
    val multiaddress: String,
    val latencyMs: Long,
    val isOnline: Boolean,
    val blocksShared: Int,
    val region: String,
    val isBootstrap: Boolean = false,
    val protocols: List<String> = listOf("/dnet/kad/1.0.0", "/dnet/bitswap/1.2.0")
)

data class NetworkMetrics(
    val localNodeId: String,
    val activePeers: Int,
    val totalPinnedBlocks: Int,
    val p2pBandwidthSavedBytes: Long,
    val centralRequestsResolved: Int,
    val decentralizedRequestsResolved: Int,
    val hybridCrossVerifications: Int,
    val meshHealthPercentage: Int,
    val isDaemonRunning: Boolean,
    val activeInterface: String = "Detecting...",
    val localIp: String = "127.0.0.1",
    val natType: String = "Local Subnet / Direct",
    val isOnline: Boolean = true,
    val currentStreamSpeedKbps: Double = 0.0,
    val totalDataStreamedBytes: Long = 0L,
    val activeConnectedNodesCount: Int = 0,
    val streamHistory: List<Float> = emptyList()
)

data class KaspaProof(
    val address: String,
    val blockDagTxHash: String,
    val blockHeight: Long,
    val schnorrSignature: String,
    val isVerified: Boolean = true
)
