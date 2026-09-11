package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pinned_contents")
data class ContentEntity(
    @PrimaryKey val cid: String,
    val title: String,
    val content: String,
    val contentType: String,
    val sizeBytes: Long,
    val isPinned: Boolean,
    val isSeeding: Boolean,
    val centralizedMirrorUrl: String?,
    val authorPeerId: String,
    val createdAt: Long,
    val sha256Hash: String,
    val protocolPrefix: String = "mesh://"
)

@Entity(tableName = "traffic_audits")
data class TrafficAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val url: String,
    val protocol: String,
    val routeTaken: String,
    val latencyMs: Long,
    val isTamperProof: Boolean,
    val verificationSummary: String
)

@Entity(tableName = "peer_nodes")
data class PeerEntity(
    @PrimaryKey val peerId: String,
    val name: String,
    val multiaddress: String,
    val latencyMs: Long,
    val isOnline: Boolean,
    val blocksShared: Int,
    val region: String,
    val isBootstrap: Boolean
)

@Entity(tableName = "decentralized_accounts")
data class AccountEntity(
    @PrimaryKey val did: String,
    val handle: String,
    val kaspaAddress: String,
    val peerId: String,
    val publicKeyHex: String,
    val seedPhrase: String,
    val accountType: String, // "DECENTRALIZED_NATIVE" or "GOOGLE_ZK_BRIDGE"
    val googleEmail: String? = null,
    val googleDisplayName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

