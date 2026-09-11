package com.example

import com.example.network.CryptoUtils
import org.junit.Assert.*
import org.junit.Test

class NetworkAuditUnitTest {

    @Test
    fun testSha256Integrity() {
        val payload = "DecentralNet Merkle Root Test"
        val hash = CryptoUtils.sha256(payload)
        assertNotNull(hash)
        assertEquals(64, hash.length)
        assertEquals(hash, CryptoUtils.sha256(payload))
    }

    @Test
    fun testCidGeneration() {
        val payload = "Hello Decentralized World"
        val cid = CryptoUtils.generateCid(payload)
        assertTrue(cid.startsWith("Qm") || cid.startsWith("bafy"))
    }

    @Test
    fun testPeerIdFormat() {
        val peerId = CryptoUtils.generatePeerId()
        assertTrue(peerId.startsWith("12D3KooW"))
        assertTrue(peerId.length > 20)
    }

    @Test
    fun testOpenSourceInternetBootstrapGateways() {
        val gateways = listOf(
            "https://ipfs.io/ipfs/",
            "https://cloudflare-ipfs.com/ipfs/",
            "https://dweb.link/ipfs/",
            "https://gateway.pinata.cloud/ipfs/"
        )
        gateways.forEach { gw ->
            assertTrue(gw.startsWith("https://"))
            assertTrue(gw.contains("ipfs"))
        }
    }

    @Test
    fun testNsdServiceTypeFormat() {
        val serviceType = "_dnet-p2p._tcp."
        assertTrue(serviceType.startsWith("_"))
        assertTrue(serviceType.contains("._tcp."))
    }

    @Test
    fun testP2PHandshakeResult() {
        val result = com.example.network.P2PHandshakeResult(
            success = true,
            peerId = "12D3KooWTestNode",
            nodeName = "AndroidNode-Pixel",
            host = "192.168.1.100",
            port = 8080,
            latencyMs = 12
        )
        assertTrue(result.success)
        assertEquals("192.168.1.100", result.host)
        assertEquals(8080, result.port)
        assertEquals(12L, result.latencyMs)
    }
}
