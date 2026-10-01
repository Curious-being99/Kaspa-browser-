package com.example.network

import org.json.JSONObject
import java.io.InputStream

/**
 * Kaspa Relay Protocol (KRP/1) Node Model
 */
data class RelayNodeInfo(
    val id: String,
    val name: String,
    val countryCode: String,
    val countryName: String,
    val host: String,
    val port: Int,
    val publicKeyHex: String,
    val isEntry: Boolean,
    val isExit: Boolean,
    val latencyMs: Long,
    val isActive: Boolean
) {
    companion object {
        fun fromJson(json: JSONObject): RelayNodeInfo {
            return RelayNodeInfo(
                id = json.optString("id", ""),
                name = json.optString("name", "Kaspa Relay"),
                countryCode = json.optString("country_code", "UN"),
                countryName = json.optString("country_name", "Decentralized Mesh"),
                host = json.optString("host", "127.0.0.1"),
                port = json.optInt("port", 8443),
                publicKeyHex = json.optString("public_key_hex", ""),
                isEntry = json.optBoolean("is_entry", false),
                isExit = json.optBoolean("is_exit", false),
                latencyMs = json.optLong("latency_ms", 30L),
                isActive = json.optBoolean("is_active", true)
            )
        }
    }
}

/**
 * Active KRP/1 Dual-Hop Circuit Model
 */
data class KaspaRelayCircuit(
    val circuitId: String,
    val createdAtEpochSec: Long,
    val expiresAtEpochSec: Long,
    val entryNode: RelayNodeInfo,
    val exitNode: RelayNodeInfo,
    val ephemeralSessionId: String,
    val totalBytesRelayed: Long,
    val isActive: Boolean,
    val dnsLeakProtected: Boolean = true,
    val webrtcLeakProtected: Boolean = true,
    val ipv6LeakProtected: Boolean = true,
    val timingShieldProtected: Boolean = true,
    val trafficMorphingActive: Boolean = true
) {
    val remainingLifetimeSeconds: Long
        get() = maxOf(0L, expiresAtEpochSec - (System.currentTimeMillis() / 1000L))

    companion object {
        fun fromJson(json: JSONObject): KaspaRelayCircuit {
            val entryObj = json.optJSONObject("entry_node") ?: JSONObject()
            val exitObj = json.optJSONObject("exit_node") ?: JSONObject()
            return KaspaRelayCircuit(
                circuitId = json.optString("circuit_id", "krp-circuit-001"),
                createdAtEpochSec = json.optLong("created_at_epoch_sec", System.currentTimeMillis() / 1000L),
                expiresAtEpochSec = json.optLong("expires_at_epoch_sec", (System.currentTimeMillis() / 1000L) + 600L),
                entryNode = RelayNodeInfo.fromJson(entryObj),
                exitNode = RelayNodeInfo.fromJson(exitObj),
                ephemeralSessionId = json.optString("ephemeral_session_id", "session-ephemeral-key"),
                totalBytesRelayed = json.optLong("total_bytes_relayed", 0L),
                isActive = json.optBoolean("is_active", true),
                dnsLeakProtected = json.optBoolean("dns_leak_protected", true),
                webrtcLeakProtected = json.optBoolean("webrtc_leak_protected", true),
                ipv6LeakProtected = json.optBoolean("ipv6_leak_protected", true)
            )
        }
    }
}

/**
 * Result of a relayed request through KRP/1
 */
data class RelayResponse(
    val statusCode: Int,
    val statusMessage: String,
    val headers: Map<String, String>,
    val bodyStream: InputStream?,
    val latencyMs: Long,
    val isEncryptedCircuit: Boolean,
    val exitNodeName: String
)
