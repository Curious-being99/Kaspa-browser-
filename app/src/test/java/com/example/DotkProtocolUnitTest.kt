package com.example

import com.example.network.Blake3
import com.example.network.CryptoUtils
import com.example.network.kaspa.DotkProtocol
import com.example.network.kaspa.KaspaTransactionEngine
import org.junit.Assert.*
import org.junit.Test

class DotkProtocolUnitTest {

    @Test
    fun testBlake3EmptyHash() {
        val emptyHash = Blake3.hash(ByteArray(0))
        val hex = emptyHash.joinToString("") { "%02x".format(it) }
        // Standard BLAKE3 official test vector for empty string:
        // af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9adc112b7cc9a93cae41f3262
        assertEquals("af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9adc112b7cc9a93cae41f3262", hex)
    }

    @Test
    fun testBlake3NonEmptyHash() {
        val data = "kaspa".toByteArray(Charsets.UTF_8)
        val hash = Blake3.hash(data)
        assertEquals(32, hash.size)
        // CryptoUtils.blake3 wrapper check
        val hashViaCryptoUtils = CryptoUtils.blake3(data)
        assertArrayEquals(hash, hashViaCryptoUtils)
    }

    @Test
    fun testDataPushEncodings() {
        val smallData = byteArrayOf(0x01, 0x02, 0x03)
        val pushed = DotkProtocol.pushData(smallData)
        assertEquals(4, pushed.size)
        assertEquals(0x03.toByte(), pushed[0]) // length prefix 3
        assertEquals(0x01.toByte(), pushed[1])

        val bytePush = DotkProtocol.pushByte(0x02)
        assertEquals(2, bytePush.size)
        assertEquals(0x01.toByte(), bytePush[0])
        assertEquals(0x02.toByte(), bytePush[1])
    }

    @Test
    fun testDeedStateSize() {
        val key = ByteArray(32) { 0x11 }
        val owner = ByteArray(32) { 0x22 }
        val name = ByteArray(32) { 0x33 }

        val deedState = DotkProtocol.buildDeedState(
            status = 0x01.toByte(),
            key = key,
            ownerType = 0x00.toByte(),
            owner = owner,
            name = name
        )

        // Protocol requirement: exactly 103 bytes
        // 2 (status) + 33 (key) + 2 (ownerType) + 33 (owner) + 33 (name) = 103 bytes
        assertEquals(103, deedState.size)
    }

    @Test
    fun testGapStateSize() {
        val lo = ByteArray(32) { 0x00 }
        val hi = ByteArray(32) { 0xff.toByte() }

        val gapState = DotkProtocol.buildGapState(lo, hi)
        // Protocol requirement: exactly 66 bytes
        // 33 (lo) + 33 (hi) = 66 bytes
        assertEquals(66, gapState.size)
    }

    @Test
    fun testFeeTiers() {
        assertEquals(DotkProtocol.FEE_1CH, DotkProtocol.getFeeForDomain("a"))
        assertEquals(DotkProtocol.FEE_2CH, DotkProtocol.getFeeForDomain("ab"))
        assertEquals(DotkProtocol.FEE_3CH, DotkProtocol.getFeeForDomain("abc"))
        assertEquals(DotkProtocol.FEE_4CH, DotkProtocol.getFeeForDomain("abcd"))
        assertEquals(DotkProtocol.FEE_5PLUS, DotkProtocol.getFeeForDomain("kaspa"))
        assertEquals(DotkProtocol.FEE_5PLUS, DotkProtocol.getFeeForDomain("kaspa.k"))
    }

    @Test
    fun testP2shScriptPubKey() {
        val redeem = ByteArray(100) { 0x42 }
        val spk = DotkProtocol.p2shScriptPubKey(redeem)
        // OP_BLAKE2B (0xaa) + 0x20 (32 bytes) + 32-byte hash + OP_EQUAL (0x87) = 35 bytes
        assertEquals(35, spk.size)
        assertEquals(0xaa.toByte(), spk[0])
        assertEquals(0x20.toByte(), spk[1])
        assertEquals(0x87.toByte(), spk[34])
    }

    @Test
    fun testBuildCommitTransactionStructure() {
        val gapLo = ByteArray(32) { 0x10 }
        val gapHi = ByteArray(32) { 0x50 }
        val gapUtxo = KaspaTransactionEngine.KaspaUtxo(
            outpoint = KaspaTransactionEngine.KaspaOutpoint("11".repeat(32), 0L),
            utxoEntry = KaspaTransactionEngine.KaspaUtxoEntry(
                amount = DotkProtocol.GAP_VALUE,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, "aabb"),
                blockDaaScore = 0L,
                isCoinbase = false
            )
        )
        val fundingUtxo = KaspaTransactionEngine.KaspaUtxo(
            outpoint = KaspaTransactionEngine.KaspaOutpoint("22".repeat(32), 0L),
            utxoEntry = KaspaTransactionEngine.KaspaUtxoEntry(
                amount = 10_000_000_000L, // 100 KAS
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, "ccdd"),
                blockDaaScore = 0L,
                isCoinbase = false
            )
        )

        val deedPrefix = byteArrayOf(0x6b.toByte())
        val deedSuffix = ByteArray(100) { 0x01 }
        val gapPrefix = byteArrayOf(0x6b.toByte())
        val gapSuffix = ByteArray(100) { 0x02 }

        val testChangeAddress = CryptoUtils.encodeRealKaspaAddress(ByteArray(32) { 0x01 })

        val tx = DotkProtocol.buildCommitTransaction(
            gapUtxo = gapUtxo,
            gapLo = gapLo,
            gapHi = gapHi,
            name = "vitalik.k",
            ownerType = 0x00.toByte(),
            ownerKey = ByteArray(32) { 0x99.toByte() },
            fundingUtxos = listOf(fundingUtxo),
            changeAddress = testChangeAddress,
            fee = 10_000L,
            deedPrefix = deedPrefix,
            deedSuffix = deedSuffix,
            gapPrefix = gapPrefix,
            gapSuffix = gapSuffix
        )

        assertEquals(1, tx.version)
        assertEquals(2, tx.inputs.size)
        // Input 0 is the gap with split signature script
        assertEquals("11".repeat(32), tx.inputs[0].previousOutpoint.transactionId)
        assertTrue(tx.inputs[0].signatureScript.isNotEmpty())

        // Protocol outputs
        assertEquals(4, tx.outputs.size)
        // Output 0: left gap
        assertEquals(DotkProtocol.GAP_VALUE, tx.outputs[0].amount)
        assertEquals(0, tx.outputs[0].covenant?.authorizingInput)
        assertEquals(DotkProtocol.REGISTRY_COVENANT_ID, tx.outputs[0].covenant?.covenantId)

        // Output 1: right gap
        assertEquals(DotkProtocol.GAP_VALUE, tx.outputs[1].amount)
        assertEquals(0, tx.outputs[1].covenant?.authorizingInput)
        assertEquals(DotkProtocol.REGISTRY_COVENANT_ID, tx.outputs[1].covenant?.covenantId)

        // Output 2: PENDING deed
        assertEquals(DotkProtocol.BOND_AMOUNT + DotkProtocol.DEPOSIT_AMOUNT, tx.outputs[2].amount)
        assertEquals(0, tx.outputs[2].covenant?.authorizingInput)
        assertEquals(DotkProtocol.REGISTRY_COVENANT_ID, tx.outputs[2].covenant?.covenantId)

        // Output 3: change
        assertTrue(tx.outputs[3].amount > 0)
        assertNull(tx.outputs[3].covenant)
    }

    @Test
    fun testBuildRevealTransactionStructure() {
        val pendingDeedUtxo = KaspaTransactionEngine.KaspaUtxo(
            outpoint = KaspaTransactionEngine.KaspaOutpoint("33".repeat(32), 2L),
            utxoEntry = KaspaTransactionEngine.KaspaUtxoEntry(
                amount = DotkProtocol.BOND_AMOUNT + DotkProtocol.DEPOSIT_AMOUNT,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, "eeff"),
                blockDaaScore = 0L,
                isCoinbase = false
            )
        )
        val fundingUtxo = KaspaTransactionEngine.KaspaUtxo(
            outpoint = KaspaTransactionEngine.KaspaOutpoint("44".repeat(32), 1L),
            utxoEntry = KaspaTransactionEngine.KaspaUtxoEntry(
                amount = 5_000_000_000L,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, "1122"),
                blockDaaScore = 0L,
                isCoinbase = false
            )
        )

        val deedPrefix = byteArrayOf(0x6b.toByte())
        val deedSuffix = ByteArray(100) { 0x01 }

        val testChangeAddress = CryptoUtils.encodeRealKaspaAddress(ByteArray(32) { 0x01 })

        val tx = DotkProtocol.buildRevealTransaction(
            pendingDeedUtxo = pendingDeedUtxo,
            name = "mytestname.k",
            ownerType = 0x00.toByte(),
            ownerKey = ByteArray(32) { 0x77.toByte() },
            fundingUtxos = listOf(fundingUtxo),
            changeAddress = testChangeAddress,
            fee = 10_000L,
            deedPrefix = deedPrefix,
            deedSuffix = deedSuffix
        )

        assertEquals(1, tx.version)
        assertEquals(2, tx.inputs.size)
        // Input 0: spends pending deed at output 2
        assertEquals(2L, tx.inputs[0].previousOutpoint.index)
        assertTrue(tx.inputs[0].signatureScript.isNotEmpty())

        // Protocol outputs
        assertTrue(tx.outputs.size >= 2)
        // Output 0: ACTIVE continuation (BOND_AMOUNT)
        assertEquals(DotkProtocol.BOND_AMOUNT, tx.outputs[0].amount)
        assertEquals(0, tx.outputs[0].covenant?.authorizingInput)
        assertEquals(DotkProtocol.REGISTRY_COVENANT_ID, tx.outputs[0].covenant?.covenantId)

        // Output 1: Devfund fee output
        assertEquals(DotkProtocol.FEE_5PLUS, tx.outputs[1].amount)
        assertEquals(DotkProtocol.DEVFUND_SCRIPT_PUBKEY_HEX, tx.outputs[1].scriptPublicKey.script)
        assertNull(tx.outputs[1].covenant)
    }

    @Test
    fun testAddressDerivations() {
        val lo = ByteArray(32) { 0x11 }
        val hi = ByteArray(32) { 0x22 }
        val prefix = byteArrayOf(0x6b.toByte())
        val suffix = ByteArray(10) { 0xaa.toByte() }

        val gapAddress = DotkProtocol.deriveGapP2shAddress(lo, hi, prefix, suffix, "kaspa")
        assertTrue(gapAddress.startsWith("kaspa:p")) // P2SH addresses in Kaspa bech32 start with 'p'
    }

    @Test
    fun testDispatchTags() {
        val splitHex = DotkProtocol.TAG_SPLIT.joinToString("") { "%02x".format(it) }
        val activateHex = DotkProtocol.TAG_ACTIVATE.joinToString("") { "%02x".format(it) }
        val transferHex = DotkProtocol.TAG_TRANSFER.joinToString("") { "%02x".format(it) }
        val releaseHex = DotkProtocol.TAG_RELEASE.joinToString("") { "%02x".format(it) }
        val evictHex = DotkProtocol.TAG_EVICT.joinToString("") { "%02x".format(it) }

        assertEquals("2e5318ff", splitHex)
        assertEquals("a20c879c", activateHex)
        assertEquals("b54f0d61", transferHex)
        assertEquals("97479433", releaseHex)
        assertEquals("66ea0f51", evictHex)
    }

    @Test
    fun testExtractPublicKeyFromAddress() {
        val mnemonic = CryptoUtils.generateMnemonic()
        val keyPair = CryptoUtils.deriveKaspaKeyPair(mnemonic)
        val kaspaAddress = CryptoUtils.encodeRealKaspaAddress(keyPair.publicKeyBytes, "kaspa")
        assertTrue(kaspaAddress.startsWith("kaspa:q"))
        assertTrue(CryptoUtils.isValidKaspaAddress(kaspaAddress))

        val extracted = CryptoUtils.extractPublicKeyFromAddress(kaspaAddress)
        assertNotNull(extracted)
        assertArrayEquals(keyPair.publicKeyBytes, extracted)

        // Invalid address cases
        assertNull(CryptoUtils.extractPublicKeyFromAddress("invalid:address"))
        assertNull(CryptoUtils.extractPublicKeyFromAddress("kaspa:qpinvalid"))
    }

    @Test
    fun testDeriveActiveDeedP2shAddress() {
        val deedPrefix = byteArrayOf(0x6b.toByte())
        val deedSuffix = ByteArray(100) { 0x01 }
        val ownerKey = ByteArray(32) { 0x44 }

        val activeAddr = DotkProtocol.deriveActiveDeedP2shAddress(
            name = "mybrand",
            ownerType = 0x00.toByte(),
            ownerKey = ownerKey,
            deedPrefix = deedPrefix,
            deedSuffix = deedSuffix,
            prefix = "kaspa"
        )
        assertTrue(activeAddr.startsWith("kaspa:p"))
    }

    @Test
    fun testBuildTransferTransactionStructure() {
        val deedPrefix = byteArrayOf(0x6b.toByte())
        val deedSuffix = ByteArray(100) { 0x01 }
        val currentOwnerKey = ByteArray(32) { 0x11 }
        val newOwnerKey = ByteArray(32) { 0x22 }

        val activeDeedUtxo = KaspaTransactionEngine.KaspaUtxo(
            outpoint = KaspaTransactionEngine.KaspaOutpoint("55".repeat(32), 0L),
            utxoEntry = KaspaTransactionEngine.KaspaUtxoEntry(
                amount = DotkProtocol.BOND_AMOUNT,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, "beef"),
                blockDaaScore = 0L,
                isCoinbase = false
            )
        )
        val fundingUtxo = KaspaTransactionEngine.KaspaUtxo(
            outpoint = KaspaTransactionEngine.KaspaOutpoint("66".repeat(32), 0L),
            utxoEntry = KaspaTransactionEngine.KaspaUtxoEntry(
                amount = 1_000_000_000L, // 10 KAS
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, "cafe"),
                blockDaaScore = 0L,
                isCoinbase = false
            )
        )

        val dummySig = ByteArray(64) { 0x77 }
        val testChangeAddress = CryptoUtils.encodeRealKaspaAddress(ByteArray(32) { 0x01 })
        val tx = DotkProtocol.buildTransferTransaction(
            activeDeedUtxo = activeDeedUtxo,
            name = "cryptoname.k",
            currentOwnerType = 0x00.toByte(),
            currentOwnerKey = currentOwnerKey,
            newOwnerType = 0x00.toByte(),
            newOwnerKey = newOwnerKey,
            fundingUtxos = listOf(fundingUtxo),
            changeAddress = testChangeAddress,
            fee = 20_000L,
            deedPrefix = deedPrefix,
            deedSuffix = deedSuffix,
            ownerSignature = dummySig
        )

        assertEquals(1, tx.version)
        assertEquals(2, tx.inputs.size)
        // Input 0: spends active deed UTXO
        assertEquals("55".repeat(32), tx.inputs[0].previousOutpoint.transactionId)
        assertTrue(tx.inputs[0].signatureScript.startsWith("b54f0d61")) // TAG_TRANSFER

        // Output 0: transferred active deed with covenant
        assertEquals(DotkProtocol.BOND_AMOUNT, tx.outputs[0].amount)
        assertEquals(0, tx.outputs[0].covenant?.authorizingInput)
        assertEquals(DotkProtocol.REGISTRY_COVENANT_ID, tx.outputs[0].covenant?.covenantId)

        // Output 1: change
        assertEquals(2, tx.outputs.size)
        val expectedChange = 1_000_000_000L - 20_000L
        assertEquals(expectedChange, tx.outputs[1].amount)
    }

    @Test
    fun testPushIntAndDynamicSigArray() {
        val zero = DotkProtocol.pushInt(0)
        assertEquals(1, zero.size)
        assertEquals(0x00.toByte(), zero[0])

        val one = DotkProtocol.pushInt(1)
        assertEquals(1, one.size)
        assertEquals(0x51.toByte(), one[0]) // OP_1

        val sixteen = DotkProtocol.pushInt(16)
        assertEquals(1, sixteen.size)
        assertEquals(0x60.toByte(), sixteen[0]) // OP_16

        val sig1 = ByteArray(64) { 0x11 }
        val sig2 = ByteArray(64) { 0x22 }
        val encodedSingle = DotkProtocol.encodeDynamicSigArray(listOf(sig1))
        // 1 byte (length 1 = 0x51) + 1 byte (push length 64 = 0x40) + 64 bytes data = 66 bytes
        assertEquals(66, encodedSingle.size)
        assertEquals(0x51.toByte(), encodedSingle[0])

        val encodedDouble = DotkProtocol.encodeDynamicSigArray(listOf(sig1, sig2))
        // 1 byte (length 2 = 0x52) + (1+64) + (1+64) = 131 bytes
        assertEquals(131, encodedDouble.size)
        assertEquals(0x52.toByte(), encodedDouble[0])
    }

    @Test
    fun testMergeReleaseAbsorbedSignatureScripts() {
        val dummyGapRedeem = ByteArray(70) { 0x12 }
        val dummyDeedRedeem = ByteArray(105) { 0x34 }
        val dummySig = ByteArray(64) { 0x56 }

        val mergeScript = DotkProtocol.buildMergeSignatureScript(dummyGapRedeem)
        val mergeHex = mergeScript.joinToString("") { "%02x".format(it) }
        assertTrue(mergeHex.startsWith("63d25bc2"))

        val releaseScript = DotkProtocol.buildReleaseSignatureScript(dummySig, dummyDeedRedeem)
        val releaseHex = releaseScript.joinToString("") { "%02x".format(it) }
        assertTrue(releaseHex.startsWith("97479433"))

        val absorbedScript = DotkProtocol.buildAbsorbedSignatureScript(dummyGapRedeem)
        val absorbedHex = absorbedScript.joinToString("") { "%02x".format(it) }
        assertTrue(absorbedHex.startsWith("dab76355"))
    }

    @Test
    fun testBuildReleaseTransactionStructure() {
        val gapPrefix = byteArrayOf(0x6b.toByte())
        val gapSuffix = ByteArray(100) { 0x02 }
        val deedPrefix = byteArrayOf(0x6b.toByte())
        val deedSuffix = ByteArray(100) { 0x01 }

        val predLo = ByteArray(32) { 0x01 }
        val deedKey = CryptoUtils.blake3("releaseme".toByteArray())
        val succHi = ByteArray(32) { 0xfe.toByte() }

        val predGapUtxo = KaspaTransactionEngine.KaspaUtxo(
            outpoint = KaspaTransactionEngine.KaspaOutpoint("10".repeat(32), 0L),
            utxoEntry = KaspaTransactionEngine.KaspaUtxoEntry(
                amount = DotkProtocol.GAP_VALUE,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, "aaaa"),
                blockDaaScore = 0L,
                isCoinbase = false
            )
        )
        val activeDeedUtxo = KaspaTransactionEngine.KaspaUtxo(
            outpoint = KaspaTransactionEngine.KaspaOutpoint("20".repeat(32), 0L),
            utxoEntry = KaspaTransactionEngine.KaspaUtxoEntry(
                amount = DotkProtocol.BOND_AMOUNT,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, "bbbb"),
                blockDaaScore = 0L,
                isCoinbase = false
            )
        )
        val succGapUtxo = KaspaTransactionEngine.KaspaUtxo(
            outpoint = KaspaTransactionEngine.KaspaOutpoint("30".repeat(32), 0L),
            utxoEntry = KaspaTransactionEngine.KaspaUtxoEntry(
                amount = DotkProtocol.GAP_VALUE,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, "cccc"),
                blockDaaScore = 0L,
                isCoinbase = false
            )
        )

        val ownerKey = ByteArray(32) { 0x88.toByte() }
        val dummySig = ByteArray(64) { 0x99.toByte() }
        val ownerAddress = CryptoUtils.encodeRealKaspaAddress(ByteArray(32) { 0x05 })
        val changeAddress = CryptoUtils.encodeRealKaspaAddress(ByteArray(32) { 0x06 })

        val tx = DotkProtocol.buildReleaseTransaction(
            predecessorGapUtxo = predGapUtxo,
            predecessorLo = predLo,
            activeDeedUtxo = activeDeedUtxo,
            deedKey = deedKey,
            successorGapUtxo = succGapUtxo,
            successorHi = succHi,
            ownerAddress = ownerAddress,
            fundingUtxos = emptyList(),
            changeAddress = changeAddress,
            fee = 10_000L,
            gapPrefix = gapPrefix,
            gapSuffix = gapSuffix,
            deedPrefix = deedPrefix,
            deedSuffix = deedSuffix,
            ownerSignature = dummySig,
            ownerType = 0x00.toByte(),
            ownerKey = ownerKey,
            name = "releaseme.k"
        )

        assertEquals(1, tx.version)
        // 3 protocol inputs: seat 0 (merge), seat 1 (release), seat 2 (absorbed)
        assertEquals(3, tx.inputs.size)
        assertEquals("10".repeat(32), tx.inputs[0].previousOutpoint.transactionId)
        assertTrue(tx.inputs[0].signatureScript.startsWith("63d25bc2")) // TAG_MERGE

        assertEquals("20".repeat(32), tx.inputs[1].previousOutpoint.transactionId)
        assertTrue(tx.inputs[1].signatureScript.startsWith("97479433")) // TAG_RELEASE

        assertEquals("30".repeat(32), tx.inputs[2].previousOutpoint.transactionId)
        assertTrue(tx.inputs[2].signatureScript.startsWith("dab76355")) // TAG_ABSORBED

        // Outputs:
        // Output 0: Widened gap lineage output bound to REGISTRY_COVENANT_ID
        assertEquals(DotkProtocol.GAP_VALUE, tx.outputs[0].amount)
        assertEquals(0, tx.outputs[0].covenant?.authorizingInput)
        assertEquals(DotkProtocol.REGISTRY_COVENANT_ID, tx.outputs[0].covenant?.covenantId)

        // Output 1: Bond refund (1 KAS) to owner
        assertEquals(DotkProtocol.BOND_AMOUNT, tx.outputs[1].amount)
        assertNull(tx.outputs[1].covenant)

        // Output 2: Change from absorbed gap minus network fee (1 KAS - 10,000 sompi)
        assertEquals(3, tx.outputs.size)
        assertEquals(DotkProtocol.GAP_VALUE - 10_000L, tx.outputs[2].amount)
        assertNull(tx.outputs[2].covenant)
    }

    @Test
    fun testEncodeRecordsBlob() {
        // 1. Empty map -> 0xa0
        val emptyBlob = DotkProtocol.encodeRecordsBlob(emptyMap())
        assertEquals(1, emptyBlob.size)
        assertEquals(0xa0.toByte(), emptyBlob[0])

        // 2. Map with standard records
        val records = mapOf(
            "url" to "https://kaspa.org",
            "primary" to true,
            "avatar" to "ipfs://bafybeic..."
        )
        val blob = DotkProtocol.encodeRecordsBlob(records)
        assertTrue(blob.isNotEmpty())
        assertTrue(blob.size <= 16_384)

        // 3. Check deterministic sorting of keys: 'avatar' < 'primary' < 'url'
        val blobHex = blob.joinToString("") { "%02x".format(it) }
        val avatarIdx = blobHex.indexOf("66617661746172") // 0x66 + "avatar"
        val primaryIdx = blobHex.indexOf("677072696d617279") // 0x67 + "primary"
        val urlIdx = blobHex.indexOf("6375726c") // 0x63 + "url"

        assertTrue(avatarIdx >= 0)
        assertTrue(primaryIdx >= 0)
        assertTrue(urlIdx >= 0)
        assertTrue(avatarIdx < primaryIdx)
        assertTrue(primaryIdx < urlIdx)
    }

    @Test
    fun testBuildCardRedeemScriptStructure() {
        val key = CryptoUtils.blake3("mycard".toByteArray())
        val blob = DotkProtocol.encodeRecordsBlob(mapOf("url" to "https://kas.org"))
        val recHash = DotkProtocol.recordsHash(blob)
        val spender = ByteArray(32) { 0x77.toByte() }
        val spenderType = 0x00.toByte()

        val redeem = DotkProtocol.buildCardRedeemScript(key, recHash, spenderType, spender)
        val redeemHex = redeem.joinToString("") { "%02x".format(it) }

        // Expected script pushes:
        // push(key) [20...] + push(recHash) [20...] + 7575 (OP_DROP OP_DROP) + 04646f746b (push 'dotk') + 88 (OP_EQUALVERIFY) + 0100 (push 0x00) + 20 [spender] + ac (OP_CHECKSIG)
        assertTrue(redeemHex.startsWith("20" + key.joinToString("") { "%02x".format(it) }))
        assertTrue(redeemHex.contains("7575")) // OP_DROP OP_DROP
        assertTrue(redeemHex.contains("04646f746b88")) // push "dotk" + OP_EQUALVERIFY
        assertTrue(redeemHex.endsWith("ac")) // OP_CHECKSIG
    }

    @Test
    fun testBuildRecordsTransaction() {
        val deedPrefix = byteArrayOf(0x6b.toByte())
        val deedSuffix = ByteArray(100) { 0x01 }
        val ownerKey = ByteArray(32) { 0x88.toByte() }
        val dummySig = ByteArray(64) { 0x99.toByte() }
        val changeAddress = CryptoUtils.encodeRealKaspaAddress(ByteArray(32) { 0x06 })

        val activeDeedUtxo = KaspaTransactionEngine.KaspaUtxo(
            outpoint = KaspaTransactionEngine.KaspaOutpoint("44".repeat(32), 0L),
            utxoEntry = KaspaTransactionEngine.KaspaUtxoEntry(
                amount = DotkProtocol.BOND_AMOUNT,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, "ffff"),
                blockDaaScore = 0L,
                isCoinbase = false
            )
        )

        val fundingUtxo = KaspaTransactionEngine.KaspaUtxo(
            outpoint = KaspaTransactionEngine.KaspaOutpoint("55".repeat(32), 0L),
            utxoEntry = KaspaTransactionEngine.KaspaUtxoEntry(
                amount = 100_000_000L, // 1 KAS funding
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, "eeee"),
                blockDaaScore = 0L,
                isCoinbase = false
            )
        )

        val records = mapOf(
            "url" to "https://mydomain.k",
            "primary" to true
        )

        val cardValue = DotkProtocol.DEFAULT_CARD_VALUE // 50_000_000 sompi (0.5 KAS)
        val fee = 20_000L

        val tx = DotkProtocol.buildRecordsTransaction(
            activeDeedUtxo = activeDeedUtxo,
            name = "mycard.k",
            currentOwnerType = 0x00.toByte(),
            currentOwnerKey = ownerKey,
            newOwnerType = 0x00.toByte(),
            newOwnerKey = ownerKey,
            records = records,
            cardValue = cardValue,
            fundingUtxos = listOf(fundingUtxo),
            changeAddress = changeAddress,
            fee = fee,
            deedPrefix = deedPrefix,
            deedSuffix = deedSuffix,
            ownerSignature = dummySig
        )

        assertEquals(1, tx.version)
        assertEquals(2, tx.inputs.size) // Input 0: ACTIVE deed, Input 1: funding

        // Input 0 spends active deed with transfer dispatch tag
        assertEquals("44".repeat(32), tx.inputs[0].previousOutpoint.transactionId)
        assertTrue(tx.inputs[0].signatureScript.startsWith("b54f0d61")) // TAG_TRANSFER

        // Outputs
        // Output 0: ACTIVE deed continuation (covenant-bound, authorizingInput 0)
        assertEquals(DotkProtocol.BOND_AMOUNT, tx.outputs[0].amount)
        assertEquals(0, tx.outputs[0].covenant?.authorizingInput)
        assertEquals(DotkProtocol.REGISTRY_COVENANT_ID, tx.outputs[0].covenant?.covenantId)

        // Output 1: Card output at 0.5 KAS (no covenant)
        assertEquals(cardValue, tx.outputs[1].amount)
        assertNull(tx.outputs[1].covenant)

        // Output 2: Leftover change (100_000_000 - 50_000_000 - 20_000 = 49_980_000 sompi)
        assertEquals(3, tx.outputs.size)
        assertEquals(100_000_000L - cardValue - fee, tx.outputs[2].amount)
        assertNull(tx.outputs[2].covenant)
    }

    @Test
    fun testBuildCardSweepSignatureScript() {
        val sig = ByteArray(64) { 0x33.toByte() }
        val dummyRedeem = ByteArray(105) { 0x44.toByte() }

        val sigScript = DotkProtocol.buildCardSweepSignatureScript(sig, dummyRedeem)
        val sigScriptHex = sigScript.joinToString("") { "%02x".format(it) }

        // Expected: pushData(64 bytes) = 40 + sigHex + pushLarge(105 bytes) = 4c 69 + dummyRedeemHex
        assertTrue(sigScriptHex.startsWith("40" + sig.joinToString("") { "%02x".format(it) }))
        assertTrue(sigScriptHex.contains("4c69" + dummyRedeem.joinToString("") { "%02x".format(it) }))
    }

    @Test
    fun testBuildCardSweepTransactionSingleAndBatch() {
        val key1 = CryptoUtils.blake3("domain1".toByteArray())
        val blob1 = DotkProtocol.encodeRecordsBlob(mapOf("url" to "https://domain1.k"))
        val recHash1 = DotkProtocol.recordsHash(blob1)
        val spender = ByteArray(32) { 0x11.toByte() }
        val redeem1 = DotkProtocol.buildCardRedeemScript(key1, recHash1, 0x00.toByte(), spender)

        val card1 = DotkProtocol.CardUtxo(
            outpoint = KaspaTransactionEngine.KaspaOutpoint("aa".repeat(32), 1L),
            amount = 50_000_000L,
            key = key1,
            recordsHash = recHash1,
            spenderType = 0x00.toByte(),
            spender = spender,
            redeemScript = redeem1
        )

        val destAddress = CryptoUtils.encodeRealKaspaAddress(ByteArray(32) { 0x99.toByte() })

        // 1. Single card sweep
        val txSingle = DotkProtocol.buildCardSweepTransaction(
            cardsToSweep = listOf(card1),
            destination = destAddress,
            fee = 10_000L
        )

        assertEquals(1, txSingle.version)
        assertEquals(1, txSingle.inputs.size)
        assertEquals("aa".repeat(32), txSingle.inputs[0].previousOutpoint.transactionId)
        assertEquals(1L, txSingle.inputs[0].previousOutpoint.index)
        assertEquals(1, txSingle.outputs.size)
        assertEquals(49_990_000L, txSingle.outputs[0].amount) // 50M - 10k fee
        assertNull(txSingle.outputs[0].covenant)

        // 2. Batch sweep (3 retired cards)
        val key2 = CryptoUtils.blake3("domain2".toByteArray())
        val redeem2 = DotkProtocol.buildCardRedeemScript(key2, recHash1, 0x00.toByte(), spender)
        val card2 = card1.copy(
            outpoint = KaspaTransactionEngine.KaspaOutpoint("bb".repeat(32), 1L),
            key = key2,
            redeemScript = redeem2
        )

        val key3 = CryptoUtils.blake3("domain3".toByteArray())
        val redeem3 = DotkProtocol.buildCardRedeemScript(key3, recHash1, 0x00.toByte(), spender)
        val card3 = card1.copy(
            outpoint = KaspaTransactionEngine.KaspaOutpoint("cc".repeat(32), 1L),
            key = key3,
            redeemScript = redeem3
        )

        val txBatch = DotkProtocol.buildCardSweepTransaction(
            cardsToSweep = listOf(card1, card2, card3),
            destination = destAddress,
            fee = 15_000L
        )

        assertEquals(3, txBatch.inputs.size)
        assertEquals(1, txBatch.outputs.size)
        assertEquals(150_000_000L - 15_000L, txBatch.outputs[0].amount)
        assertNull(txBatch.outputs[0].covenant)
    }

    @Test
    fun testSeedPhraseImportAndKaspaAddressDerivation() {
        val rawSeedWithCommas = "abandon, ability, able, about, above, absent, absorb, abstract, absurd, abuse, access, accident"
        val decrypted = CryptoUtils.getDecryptedSeed(rawSeedWithCommas)
        val words = decrypted.split(" ")
        assertEquals(12, words.size)
        assertEquals("abandon", words[0])
        assertEquals("accident", words[11])

        val kaspaKey = CryptoUtils.deriveKaspaKeyPair(rawSeedWithCommas)
        assertTrue(kaspaKey.kaspaAddress.startsWith("kaspa:q"))
        assertTrue(CryptoUtils.isValidKaspaAddress(kaspaKey.kaspaAddress))
    }
}


