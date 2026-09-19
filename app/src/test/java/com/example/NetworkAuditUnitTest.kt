package com.example

import com.example.network.CryptoUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
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
    fun KaspaLinkProofVerification() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val keyPair = CryptoUtils.deriveKaspaKeyPair(mnemonic)
        val payload = "<h1>Kaspa Decentralized Content</h1>"
        val url = "kaspa://dnet/content/index.html"
        val messageBytes = (payload + url).toByteArray(Charsets.UTF_8)
        val signature = CryptoUtils.signKaspaPersonalMessage(keyPair.privateKey, messageBytes)

        val proof = CryptoUtils.verifyKaspaLinkProof(payload, url, keyPair.publicKeyHex, signature)
        assertNotNull(proof)
        assertTrue("Proof must verify successfully", proof.isVerified)
        assertTrue(CryptoUtils.isValidKaspaAddress(proof.address))
        assertEquals(128, proof.schnorrSignature.length)
        assertTrue("Blockheight must be a live blockDAG score from mainnet (> 500,000,000)", proof.blockHeight > 500000000L)
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

        // Google Account login, OAuth, profile, and authentication endpoints must NEVER be blocked
        assertFalse(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://accounts.google.com/signin/v2/identifier"))
        assertFalse(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://myaccount.google.com/"))
        assertFalse(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://apis.google.com/js/platform.js"))
        assertFalse(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://oauth2.googleapis.com/token"))
        assertFalse(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://identitytoolkit.googleapis.com/v1/accounts"))
        assertFalse(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://ssl.gstatic.com/accounts/ui/avatar_2x.png"))
        assertFalse(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://accounts.google.com/gsi/client"))
        assertFalse(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://www.google.com/recaptcha/api.js"))
        assertFalse(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://lh3.googleusercontent.com/a/default-user"))
        assertFalse(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://accounts.google.co.uk/signin"))

        assertFalse(com.example.network.UBlockEngine.shouldBlock("https://accounts.google.com/ServiceLogin"))
        assertFalse(com.example.network.UBlockEngine.shouldBlock("https://myaccount.google.com/security"))
        assertFalse(com.example.network.UBlockEngine.shouldBlock("https://apis.google.com/js/api.js"))
        assertFalse(com.example.network.UBlockEngine.shouldBlock("https://oauth2.googleapis.com/token"))
        assertFalse(com.example.network.UBlockEngine.shouldBlock("https://ssl.gstatic.com/accounts/ui/avatar.png"))

        val headers = com.example.network.KaspaPrivacyEngine.getPrivacyHeaders()
        assertEquals("1", headers["DNT"])
        assertEquals("1", headers["Sec-GPC"])
    }

    @Test
    fun testKaspaAddressToScriptPublicKeyDecoding() {
        val pubKey32 = CryptoUtils.sha256Raw("KaspaScriptPubKeyTestVector".toByteArray(Charsets.UTF_8))
        val address = CryptoUtils.encodeRealKaspaAddress(pubKey32, "kaspa")
        val scriptPubKey = com.example.network.kaspa.KaspaTransactionEngine.decodeAddressToScriptPublicKey(address)
        
        assertEquals(0, scriptPubKey.version)
        val expectedPubKeyHex = pubKey32.joinToString("") { "%02x".format(it) }
        assertEquals("20" + expectedPubKeyHex + "ac", scriptPubKey.script)
    }

    @Test
    fun testKaspaTransactionSigningAndIdGeneration() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val keyPair = CryptoUtils.deriveKaspaKeyPair(mnemonic)
        val senderAddress = keyPair.kaspaAddress
        val recipientAddress = "kaspa:qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqkx9awp4e"
        
        val senderScript = com.example.network.kaspa.KaspaTransactionEngine.decodeAddressToScriptPublicKey(senderAddress)
        val recipientScript = com.example.network.kaspa.KaspaTransactionEngine.decodeAddressToScriptPublicKey(recipientAddress)

        val outpoint = com.example.network.kaspa.KaspaTransactionEngine.KaspaOutpoint(
            transactionId = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90",
            index = 0L
        )
        val utxoEntry = com.example.network.kaspa.KaspaTransactionEngine.KaspaUtxoEntry(
            amount = 500_000_000L,
            scriptPublicKey = senderScript
        )

        val input = com.example.network.kaspa.KaspaTransactionEngine.KaspaTransactionInput(previousOutpoint = outpoint)
        val output1 = com.example.network.kaspa.KaspaTransactionEngine.KaspaTransactionOutput(
            amount = 100_000_000L,
            scriptPublicKey = recipientScript
        )
        val output2 = com.example.network.kaspa.KaspaTransactionEngine.KaspaTransactionOutput(
            amount = 399_990_000L,
            scriptPublicKey = senderScript
        )

        val tx = com.example.network.kaspa.KaspaTransactionEngine.KaspaTransaction(
            version = 0,
            inputs = listOf(input),
            outputs = listOf(output1, output2),
            lockTime = 0L
        )

        val signedTx = com.example.network.kaspa.KaspaTransactionEngine.signTransaction(
            tx = tx,
            utxoEntries = listOf(utxoEntry),
            privateKey = keyPair.privateKey
        )

        val sigScript = signedTx.inputs[0].signatureScript
        assertTrue("Signature script must start with OP_DATA_65 (0x41)", sigScript.startsWith("41"))
        assertTrue("Signature script must end with SIGHASH_ALL (0x01)", sigScript.endsWith("01"))
        assertEquals("Signature script length must be 132 hex characters (1 + 64 + 1 bytes = 66 bytes)", 132, sigScript.length)

        val txId = com.example.network.kaspa.KaspaTransactionEngine.calcTransactionId(signedTx)
        assertEquals("Kaspa Transaction ID must be 64 hex characters", 64, txId.length)

        val submitJson = com.example.network.kaspa.KaspaTransactionEngine.buildSubmitPayload(signedTx)
        assertTrue(submitJson.has("transaction"))
        val txJson = submitJson.getJSONObject("transaction")
        assertEquals(0, txJson.getInt("version"))
        assertEquals(1, txJson.getJSONArray("inputs").length())
        assertEquals(2, txJson.getJSONArray("outputs").length())
    }

    @Test
    fun testBackupAndDataExtractionRulesExclusions() {
        val backupRulesFile = java.io.File("src/main/res/xml/backup_rules.xml")
        val dataExtractionRulesFile = java.io.File("src/main/res/xml/data_extraction_rules.xml")
        
        val backupContent = if (backupRulesFile.exists()) backupRulesFile.readText() else java.io.File("app/src/main/res/xml/backup_rules.xml").readText()
        val extractionContent = if (dataExtractionRulesFile.exists()) dataExtractionRulesFile.readText() else java.io.File("app/src/main/res/xml/data_extraction_rules.xml").readText()

        // Verify databases excluded
        assertTrue("Backup rules must exclude decentralnet_db", backupContent.contains("path=\"decentralnet_db\""))
        assertTrue("Backup rules must exclude decentralnet_db-wal", backupContent.contains("path=\"decentralnet_db-wal\""))
        assertTrue("Backup rules must exclude decentralnet_db-shm", backupContent.contains("path=\"decentralnet_db-shm\""))
        assertTrue("Backup rules must exclude kaspa_node_prefs.xml", backupContent.contains("path=\"kaspa_node_prefs.xml\""))

        // Verify data extraction rules for Android 12+ cloud and device transfer
        assertTrue("Data extraction rules must have cloud-backup", extractionContent.contains("<cloud-backup>"))
        assertTrue("Data extraction rules must have device-transfer", extractionContent.contains("<device-transfer>"))
        assertTrue("Data extraction rules must exclude decentralnet_db from cloud", extractionContent.contains("path=\"decentralnet_db\""))
        assertTrue("Data extraction rules must exclude kaspa_node_prefs.xml from device transfer", extractionContent.contains("path=\"kaspa_node_prefs.xml\""))
    }

    @Test
    fun testMathematicalZeroKnowledgeProofEngine() {
        val mnemonic = "cat dog elephant fox giraffe horse jaguar koala lion monkey nest owl"
        val keyPair = com.example.network.CryptoUtils.deriveKaspaKeyPair(mnemonic)

        // 1. Generate Non-Interactive Zero-Knowledge Proof (NIZKP)
        val statement = "zk-identity:test|${keyPair.kaspaAddress}"
        val zkProof = com.example.network.zk.ZkProofEngine.generateZkProof(keyPair.privateKey, statement)

        assertEquals(64, zkProof.publicKeyHex.length)
        assertEquals(64, zkProof.commitmentRxHex.length)
        assertEquals(64, zkProof.commitmentRyHex.length)
        assertEquals(64, zkProof.challengeHex.length)
        assertEquals(64, zkProof.responseHex.length)
        assertEquals(statement, zkProof.statement)

        // 2. Verify mathematically: s·G == R + e·P
        val verifyResult = com.example.network.zk.ZkProofEngine.verifyZkProof(zkProof)
        assertTrue("ZK Proof must verify mathematically valid", verifyResult.isValid)

        // 3. Verify that tampered challenge or response fails verification
        val tamperedProof = zkProof.copy(responseHex = zkProof.responseHex.dropLast(1) + if (zkProof.responseHex.takeLast(1) == "0") "1" else "0")
        val tamperedResult = com.example.network.zk.ZkProofEngine.verifyZkProof(tamperedProof)
        assertFalse("Tampered ZK proof must fail verification", tamperedResult.isValid)

        // 4. Test Google zk-Bridge proof generation and verification
        val googleZkProof = com.example.network.zk.ZkProofEngine.generateGoogleBridgeZkProof(
            privateKey = keyPair.privateKey,
            googleEmail = "satoshi@gmail.com",
            did = "did:key:z6MkgTest"
        )
        val googleVerifyResult = com.example.network.zk.ZkProofEngine.verifyZkProof(googleZkProof)
        assertTrue("Google zk-bridge proof must verify mathematically valid", googleVerifyResult.isValid)
    }

    @Test
    fun testKabDomainNormalizationAndValidation() {
        // Test suffix formatting and normalization
        assertEquals("satoshi.k", com.example.network.DomainConstants.formatDomain("satoshi"))
        assertEquals("satoshi.k", com.example.network.DomainConstants.formatDomain("satoshi.k"))
        assertEquals("mesh-node-1.k", com.example.network.DomainConstants.formatDomain("mesh-node-1"))

        // Test custom domain suffix detection
        assertTrue(com.example.network.DomainConstants.isCustomDomain("satoshi.k"))
        assertTrue(com.example.network.DomainConstants.isCustomDomain("alice-123.k"))
        assertTrue(com.example.network.DomainConstants.isCustomDomain("node.kasbrowser"))

        // Test non-custom domains
        assertFalse(com.example.network.DomainConstants.isCustomDomain("google.com"))
        assertFalse(com.example.network.DomainConstants.isCustomDomain("wikipedia.org"))
    }

    @Test
    fun testKabDomainPayloadAndSchnorrSignature() {
        val mnemonic = "cat dog elephant fox giraffe horse jaguar koala lion monkey nest owl"
        val keyPair = com.example.network.CryptoUtils.deriveKaspaKeyPair(mnemonic)
        val domain = "satoshi.k"

        // Construct KNS JSON payload
        val knsJson = org.json.JSONObject().apply {
            put("protocol", "kns-k")
            put("op", "register")
            put("domain", domain)
            put("owner", keyPair.kaspaAddress)
            put("pubkey", keyPair.publicKeyHex)
            put("timestamp", 1700000000000L)
        }
        val payloadStr = knsJson.toString()
        val payloadHash = CryptoUtils.sha256Raw(payloadStr.toByteArray(Charsets.UTF_8))
        val signature = CryptoUtils.signSchnorr(keyPair.privateKey, payloadHash)

        assertTrue(CryptoUtils.verifySchnorrSignature(signature, payloadHash, keyPair.publicKeyBytes))
    }

    @Test
    fun testKaspaMassCalculationEngine() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val keyPair = CryptoUtils.deriveKaspaKeyPair(mnemonic)
        val senderAddress = keyPair.kaspaAddress
        val recipientAddress = "kaspa:qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqkx9awp4e"
        
        val senderScript = com.example.network.kaspa.KaspaTransactionEngine.decodeAddressToScriptPublicKey(senderAddress)
        val recipientScript = com.example.network.kaspa.KaspaTransactionEngine.decodeAddressToScriptPublicKey(recipientAddress)

        val outpoint1 = com.example.network.kaspa.KaspaTransactionEngine.KaspaOutpoint("a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90", 0L)
        val outpoint2 = com.example.network.kaspa.KaspaTransactionEngine.KaspaOutpoint("b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1", 1L)

        val input1 = com.example.network.kaspa.KaspaTransactionEngine.KaspaTransactionInput(previousOutpoint = outpoint1, sigOpCount = 1)
        val input2 = com.example.network.kaspa.KaspaTransactionEngine.KaspaTransactionInput(previousOutpoint = outpoint2, sigOpCount = 1)

        val output1 = com.example.network.kaspa.KaspaTransactionEngine.KaspaTransactionOutput(amount = 200_000_000L, scriptPublicKey = recipientScript)
        val output2 = com.example.network.kaspa.KaspaTransactionEngine.KaspaTransactionOutput(amount = 299_900_000L, scriptPublicKey = senderScript)

        val tx = com.example.network.kaspa.KaspaTransactionEngine.KaspaTransaction(
            version = 0,
            inputs = listOf(input1, input2),
            outputs = listOf(output1, output2),
            lockTime = 0L,
            payload = "7b2270726f746f636f6c223a226b6e732d6b227d" // 20 bytes payload
        )

        // 1. Verify compute mass calculation: 2 sigops * 1000 + 2 inputs * 100 + 2 outputs * 100 = 2400
        val computeMass = com.example.network.kaspa.KaspaTransactionEngine.calculateComputeMass(
            inputsCount = 2,
            outputsCount = 2,
            totalSigOps = 2
        )
        assertEquals(2400L, computeMass)

        // 2. Verify serialized size calculation
        val serializedBytes = com.example.network.kaspa.KaspaTransactionEngine.calculateSerializedByteSize(tx)
        assertTrue("Serialized byte size must be > 300 bytes", serializedBytes > 300L)

        // 3. Verify total mass calculation (at least max(compute, serialized))
        val totalMass = com.example.network.kaspa.KaspaTransactionEngine.calculateMass(tx)
        assertTrue("Total mass must be >= 2400", totalMass >= 2400L)
    }

    @Test
    fun testKaspaDynamicFeeCalculation() {
        // Test standard 1 sompi/mass fee calculation
        val mass1 = 1200L
        val fee1 = com.example.network.kaspa.KaspaTransactionEngine.calculateFeeForMass(mass1, sompiPerMass = 1L)
        assertEquals(1200L, fee1)

        // Test fee in KAS (0.00001200 KAS)
        val feeKas1 = fee1 / 100_000_000.0
        assertEquals(0.000012, feeKas1, 0.000000001)

        // Test priority fee (2 sompis/mass)
        val feePriority = com.example.network.kaspa.KaspaTransactionEngine.calculateFeeForMass(mass1, sompiPerMass = 2L)
        assertEquals(2400L, feePriority)

        // Test minimum fee threshold (1000 Sompi)
        val smallMass = 500L
        val minFee = com.example.network.kaspa.KaspaTransactionEngine.calculateFeeForMass(smallMass, sompiPerMass = 1L)
        assertEquals(1000L, minFee)
    }

    @Test
    fun testKaspaUtxoSelectionAndTransactionPlanner() {
        val senderScript = com.example.network.kaspa.KaspaTransactionEngine.KaspaScriptPublicKey(0, "20abcdefac")
        
        val utxo1 = com.example.network.kaspa.KaspaTransactionEngine.KaspaUtxo(
            outpoint = com.example.network.kaspa.KaspaTransactionEngine.KaspaOutpoint("tx1", 0L),
            utxoEntry = com.example.network.kaspa.KaspaTransactionEngine.KaspaUtxoEntry(100_000_000L, senderScript) // 1.0 KAS
        )
        val utxo2 = com.example.network.kaspa.KaspaTransactionEngine.KaspaUtxo(
            outpoint = com.example.network.kaspa.KaspaTransactionEngine.KaspaOutpoint("tx2", 0L),
            utxoEntry = com.example.network.kaspa.KaspaTransactionEngine.KaspaUtxoEntry(50_000_000L, senderScript) // 0.5 KAS
        )

        // Target: 1.2 KAS (120,000,000 Sompi) -> needs both UTXOs
        val plan = com.example.network.kaspa.KaspaTransactionEngine.selectUtxosAndPlanTransaction(
            availableUtxos = listOf(utxo1, utxo2),
            targetAmountSompis = 120_000_000L,
            payloadByteCount = 0
        )

        assertTrue("Plan must be sufficient", plan.isSufficient)
        assertEquals(2, plan.selectedUtxos.size)
        assertEquals(150_000_000L, plan.accumulatedSompis)
        assertTrue("Calculated mass must be >= 1000", plan.calculatedMass >= 1000L)
        assertTrue("Dynamic fee must be > 0 and calculated from mass", plan.feeSompis == plan.calculatedMass * plan.sompiPerMass)
        assertEquals(plan.feeSompis / 100_000_000.0, plan.feeKas, 0.000000001)
        assertEquals(150_000_000L - 120_000_000L - plan.feeSompis, plan.changeSompis)
    }

    @Test
    fun testAuthenticKaspaDomainTransactionIdGeneration() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val keyPair = CryptoUtils.deriveKaspaKeyPair(mnemonic)
        val senderAddress = keyPair.kaspaAddress
        val domain = "alice.k"

        val senderScript = com.example.network.kaspa.KaspaTransactionEngine.decodeAddressToScriptPublicKey(senderAddress)
        val outpoint = com.example.network.kaspa.KaspaTransactionEngine.KaspaOutpoint("1111222233334444555566667777888899990000aaaabbbbccccddddeeeeffff", 0L)
        val utxoEntry = com.example.network.kaspa.KaspaTransactionEngine.KaspaUtxoEntry(200_000_000L, senderScript)

        val knsJson = org.json.JSONObject().apply {
            put("protocol", "kns-k")
            put("op", "register")
            put("domain", domain)
            put("owner", senderAddress)
            put("pubkey", keyPair.publicKeyHex)
            put("timestamp", 1700000000000L)
        }
        val payloadHex = knsJson.toString().toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }

        val tx = com.example.network.kaspa.KaspaTransactionEngine.KaspaTransaction(
            version = 0,
            inputs = listOf(com.example.network.kaspa.KaspaTransactionEngine.KaspaTransactionInput(previousOutpoint = outpoint)),
            outputs = listOf(com.example.network.kaspa.KaspaTransactionEngine.KaspaTransactionOutput(amount = 100_000_000L, scriptPublicKey = senderScript)),
            lockTime = 0L,
            payload = payloadHex
        )

        val signedTx = com.example.network.kaspa.KaspaTransactionEngine.signTransaction(tx, listOf(utxoEntry), keyPair.privateKey)
        val txId = com.example.network.kaspa.KaspaTransactionEngine.calcTransactionId(signedTx)

        assertEquals("On-chain transaction ID must be 64 hexadecimal characters", 64, txId.length)
        assertTrue("Transaction ID must be valid lowercase hex", txId.matches(Regex("^[0-9a-f]{64}$")))
        assertFalse("TxID must not contain mock prefix", txId.startsWith("mock") || txId.startsWith("kas_") || txId.contains("local"))
    }
}

