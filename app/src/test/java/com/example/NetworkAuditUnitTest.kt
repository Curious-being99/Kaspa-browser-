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

    @Test
    fun testKaspaAddressEncodingAndValidation() {
        val pubKey32 = CryptoUtils.sha256Raw("KaspaOriginSecp256k1SchnorrKey".toByteArray(Charsets.UTF_8))
        val address = CryptoUtils.encodeRealKaspaAddress(pubKey32, "kaspa")
        assertTrue("Address must start with kaspa: prefix", address.startsWith("kaspa:"))
        assertEquals(67, address.length) // "kaspa:" (6) + 61 characters
        assertTrue("Address must validate with Kaspa polymod", CryptoUtils.isValidKaspaAddress(address, "kaspa"))
        assertFalse("Altered address must fail validation", CryptoUtils.isValidKaspaAddress(address.dropLast(1) + "q", "kaspa"))
    }

    @Test
    fun testKaspaBip39AndBip32DerivationDeterminism() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val keyPair1 = CryptoUtils.deriveKaspaKeyPair(mnemonic)
        val keyPair2 = CryptoUtils.deriveKaspaKeyPair(mnemonic)

        assertEquals("Private keys must be deterministic", keyPair1.privateKey, keyPair2.privateKey)
        assertEquals("Public keys must be deterministic", keyPair1.publicKeyHex, keyPair2.publicKeyHex)
        assertEquals("Kaspa addresses must be deterministic", keyPair1.kaspaAddress, keyPair2.kaspaAddress)
        assertTrue("Derived address must be valid Kaspa format", CryptoUtils.isValidKaspaAddress(keyPair1.kaspaAddress))
    }

    @Test
    fun testKaspaBip340SchnorrSignatureAndVerification() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val kaspaKey = CryptoUtils.deriveKaspaKeyPair(mnemonic)
        val message = "Kaspa BlockDAG Transaction Payload 2026"
        val messageHash = CryptoUtils.sha256Raw(message.toByteArray(Charsets.UTF_8))

        val signatureHex = CryptoUtils.signSchnorr(kaspaKey.privateKey, messageHash)
        assertEquals(128, signatureHex.length) // 64 bytes = 128 hex chars

        val isValid = CryptoUtils.verifySchnorrSignature(signatureHex, messageHash, kaspaKey.publicKeyBytes)
        assertTrue("Schnorr signature must be valid", isValid)

        val tamperedHash = CryptoUtils.sha256Raw("Tampered Message Payload".toByteArray(Charsets.UTF_8))
        val isTamperedValid = CryptoUtils.verifySchnorrSignature(signatureHex, tamperedHash, kaspaKey.publicKeyBytes)
        assertFalse("Tampered message signature verification must fail", isTamperedValid)
    }

    @Test
    fun testBip340AuditedTestVector0() {
        val sk = java.math.BigInteger.valueOf(3)
        val msg = ByteArray(32) // all zeros
        val aux = ByteArray(32) // all zeros
        val sigHex = CryptoUtils.signSchnorr(sk, msg, aux)
        assertEquals(128, sigHex.length)

        val pubPoint = CryptoUtils.pointMultiply(sk, CryptoUtils.ECPoint.G)
        val expectedPubKeyHex = "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9"
        assertEquals(expectedPubKeyHex, pubPoint.x.toString(16).padStart(64, '0'))

        val pubKeyBytes = CryptoUtils.to32ByteArray(pubPoint.x)
        val verified = CryptoUtils.verifySchnorrSignature(sigHex, msg, pubKeyBytes)
        assertTrue("Audited BIP-340 test vector signature must verify", verified)
    }

    @Test
    fun testKaspaLinkProofVerification() {
        val proof = CryptoUtils.verifyKaspaLinkProof("<h1>Kaspa Decentralized Content</h1>", "kaspa://dnet/content/index.html")
        assertNotNull(proof)
        assertTrue(proof.isVerified)
        assertTrue(CryptoUtils.isValidKaspaAddress(proof.address))
        assertEquals(128, proof.schnorrSignature.length)
        assertTrue(proof.blockHeight > 8000000L)
    }

    @Test
    fun testBlake2b256Rfc7693EmptyStringTestVector() {
        val emptyDigest = CryptoUtils.blake2b256(ByteArray(0))
        val hex = emptyDigest.joinToString("") { "%02x".format(it) }
        assertEquals("0e5751c026e543b2e8ab2eb06099daa1d1e5df47778f7787faab45cdf12fe3a8", hex)
    }

    @Test
    fun testRustyKaspaDomainSeparation() {
        val payload = "transfer_100_kas".toByteArray(Charsets.UTF_8)
        val personalHash = CryptoUtils.kaspaPersonalMessageHash(payload)
        val txHash = CryptoUtils.kaspaTransactionSigningHash(payload)
        assertEquals(32, personalHash.size)
        assertEquals(32, txHash.size)
        assertFalse("Domain separation must produce different hashes for personal messages vs transactions",
            personalHash.contentEquals(txHash))
    }

    @Test
    fun testRustyKaspaPersonalMessageSigningAndVerification() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val message = "Login to Kaspa Decentralized Gateway"
        val sigHex = CryptoUtils.signMessage(message, mnemonic)
        assertEquals(128, sigHex.length)

        val kaspaKey = CryptoUtils.deriveKaspaKeyPair(mnemonic)
        val isValid = CryptoUtils.verifyMessage(message, sigHex, kaspaKey.publicKeyBytes)
        assertTrue("Personal message signature must verify under rusty-kaspa standard", isValid)

        val isTampered = CryptoUtils.verifyMessage("Tampered Login Request", sigHex, kaspaKey.publicKeyBytes)
        assertFalse("Tampered personal message must fail verification", isTampered)
    }

    @Test
    fun testRustyKaspaTransactionSigningAndVerification() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val txPayload = "kaspa:qpm2qsznhks23z7629mms6s4cwef74vcwvi22gd86a_to_kaspa:qq366y5g29q58zfq2y5g29q58zfq2y5g29q58zfq2y5g29q58zfq27w9g4y_100000000_sompis"
        val sigHex = CryptoUtils.signTransaction(txPayload, mnemonic)
        assertEquals(128, sigHex.length)

        val kaspaKey = CryptoUtils.deriveKaspaKeyPair(mnemonic)
        val isValid = CryptoUtils.verifyTransaction(txPayload, sigHex, kaspaKey.publicKeyBytes)
        assertTrue("Transaction signature must verify under rusty-kaspa standard", isValid)

        val isTampered = CryptoUtils.verifyTransaction(txPayload + "_tampered", sigHex, kaspaKey.publicKeyBytes)
        assertFalse("Tampered transaction must fail verification", isTampered)
    }

    @Test
    fun testKaspaPrivacyEngineInterception() {
        assertTrue(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://www.google-analytics.com/analytics.js"))
        assertTrue(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://connect.facebook.net/en_US/fbevents.js"))
        assertTrue(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://ad.doubleclick.net/pixel"))
        assertTrue(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://browser.sentry-cdn.com/bundle.min.js"))

        // Decentralized schemes & legit pages must not be blocked
        assertFalse(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("kaspa://dnet/gateway/index.html"))
        assertFalse(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://kaspa.org"))
        assertFalse(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://wikipedia.org/wiki/Kaspa"))

        val headers = com.example.network.KaspaPrivacyEngine.getPrivacyHeaders()
        assertEquals("1", headers["DNT"])
        assertEquals("1", headers["Sec-GPC"])
    }
}
