package com.example.network.kaspa

import com.example.network.Blake3
import com.example.network.CryptoUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.math.BigInteger

/**
 * Official .k (dotk) Protocol Implementation for Kaspa.
 * Handles KIP-20 covenant bindings, deed state transitions, gap splitting, and reveal activations.
 * Reference: https://dotk.name/developers and https://api.dotk.name/v1/genesis
 */
object DotkProtocol {

    const val REGISTRY_COVENANT_ID = "ee2128c03dfac7f6d74734bb3c879bd999434c47a55945b8a6daae2a1e4a21de"

    // Genesis amounts in Sompi (1 KAS = 100,000,000 sompi)
    const val GAP_VALUE: Long = 100_000_000L        // 1 KAS
    const val BOND_AMOUNT: Long = 100_000_000L      // 1 KAS (refundable bond)
    const val DEPOSIT_AMOUNT: Long = 3_600_000_000L // 36 KAS (burned/transition deposit)

    // DEVFUND scriptPubKey hex from genesis
    const val DEVFUND_SCRIPT_PUBKEY_HEX = "207ee85afca8273d94739037e7b4c736fcfc9e12d5c468eea5ce7b89181b49386bac"

    // Fee tiers in Sompis
    const val FEE_1CH: Long = 399_800_000_000L   // 3,998 KAS
    const val FEE_2CH: Long = 199_800_000_000L   // 1,998 KAS
    const val FEE_3CH: Long = 99_800_000_000L    // 998 KAS
    const val FEE_4CH: Long = 24_800_000_000L    // 248 KAS
    const val FEE_5PLUS: Long = 3_800_000_000L   // 38 KAS

    // Dispatch tags (4 bytes hex)
    val TAG_SPLIT: ByteArray = hexToBytes("2e5318ff")
    val TAG_ACTIVATE: ByteArray = hexToBytes("a20c879c")
    val TAG_TRANSFER: ByteArray = hexToBytes("b54f0d61")
    val TAG_RELEASE: ByteArray = hexToBytes("97479433")
    val TAG_MERGE: ByteArray = hexToBytes("63d25bc2")
    val TAG_ABSORBED: ByteArray = hexToBytes("dab76355")
    val TAG_EVICT: ByteArray = hexToBytes("66ea0f51")

    data class DotkBytecodes(
        val deedPrefix: ByteArray,
        val deedSuffix: ByteArray,
        val gapPrefix: ByteArray,
        val gapSuffix: ByteArray
    )

    data class CoveringGapInfo(
        val lo: ByteArray,
        val hi: ByteArray,
        val utxo: KaspaTransactionEngine.KaspaUtxo
    )

    private var cachedBytecodes: DotkBytecodes? = null

    fun getFeeForDomain(name: String): Long {
        val clean = name.lowercase().removeSuffix(".k").trim()
        return when (clean.length) {
            1 -> FEE_1CH
            2 -> FEE_2CH
            3 -> FEE_3CH
            4 -> FEE_4CH
            else -> FEE_5PLUS
        }
    }

    // ---------------------------------------------------------------
    // Data-push encoding (Kaspa script style)
    // ---------------------------------------------------------------
    fun pushData(data: ByteArray): ByteArray {
        require(data.size <= 75) { "pushData only supports <= 75 bytes (use pushLarge for larger)" }
        return byteArrayOf(data.size.toByte()) + data
    }

    fun pushByte(b: Byte): ByteArray = byteArrayOf(0x01, b)

    fun intToMinimalBytes(value: Int): ByteArray {
        if (value == 0) return byteArrayOf()
        var v = value
        val isNeg = v < 0
        if (isNeg) v = -v
        val bytes = mutableListOf<Byte>()
        while (v > 0) {
            bytes.add((v and 0xff).toByte())
            v = v shr 8
        }
        if ((bytes.last().toInt() and 0x80) != 0) {
            bytes.add(if (isNeg) 0x80.toByte() else 0x00.toByte())
        } else if (isNeg) {
            val lastIdx = bytes.size - 1
            bytes[lastIdx] = (bytes[lastIdx].toInt() or 0x80).toByte()
        }
        return bytes.toByteArray()
    }

    fun pushInt(value: Int): ByteArray {
        // Minimal script integer encoding (OP_1..OP_16 or push)
        return when (value) {
            in 1..16 -> byteArrayOf((0x50 + value).toByte())
            0        -> byteArrayOf(0x00)
            else     -> pushData(intToMinimalBytes(value))
        }
    }

    fun encodeDynamicSigArray(sigs: List<ByteArray>): ByteArray {
        // Protocol expects a length-prefixed array of signatures.
        // Each signature is itself a push.
        val parts = mutableListOf<ByteArray>()
        parts += pushInt(sigs.size)               // array length
        sigs.forEach { parts += pushData(it) }    // each sig as a data push
        return parts.reduce { a, b -> a + b }
    }

    fun pushLarge(data: ByteArray): ByteArray = when {
        data.size <= 75 -> byteArrayOf(data.size.toByte()) + data
        data.size <= 0xff -> byteArrayOf(0x4c.toByte(), data.size.toByte()) + data // OP_PUSHDATA1
        data.size <= 0xffff -> {
            val len = byteArrayOf(
                (data.size and 0xff).toByte(),
                ((data.size shr 8) and 0xff).toByte()
            )
            byteArrayOf(0x4d.toByte()) + len + data // OP_PUSHDATA2
        }
        else -> error("data too large for script push")
    }

    // ---------------------------------------------------------------
    // Deed state (exactly 103 bytes)
    // ---------------------------------------------------------------
    fun buildDeedState(
        status: Byte,           // 0x01 = PENDING, 0x02 = ACTIVE
        key: ByteArray,         // 32 bytes
        ownerType: Byte,
        owner: ByteArray,       // 32 bytes (claim when PENDING, real owner when ACTIVE)
        name: ByteArray         // 32 bytes (zeros when PENDING, padded name when ACTIVE)
    ): ByteArray {
        require(key.size == 32 && owner.size == 32 && name.size == 32)
        return pushByte(status) +
                pushData(key) +
                pushByte(ownerType) +
                pushData(owner) +
                pushData(name)
    }

    // ---------------------------------------------------------------
    // Gap state (exactly 66 bytes)
    // ---------------------------------------------------------------
    fun buildGapState(lo: ByteArray, hi: ByteArray): ByteArray {
        require(lo.size == 32 && hi.size == 32)
        return pushData(lo) + pushData(hi)
    }

    // ---------------------------------------------------------------
    // Redeem-script & P2SH helpers
    // ---------------------------------------------------------------
    fun buildDeedRedeemScript(state: ByteArray, deedPrefix: ByteArray, deedSuffix: ByteArray): ByteArray {
        require(state.size == 103)
        return deedPrefix + state + deedSuffix
    }

    fun buildGapRedeemScript(state: ByteArray, gapPrefix: ByteArray, gapSuffix: ByteArray): ByteArray {
        require(state.size == 66)
        return gapPrefix + state + gapSuffix
    }

    fun p2shScriptPubKey(redeemScript: ByteArray): ByteArray {
        val hash = CryptoUtils.blake2b256(redeemScript) // 32 bytes
        return byteArrayOf(0xaa.toByte(), 0x20) + hash + byteArrayOf(0x87.toByte())
    }

    // ---------------------------------------------------------------
    // Signature-script construction
    // ---------------------------------------------------------------

    /**
     * Split (commit) - spends a gap.
     * Arguments order required by the covenant:
     *   tag(4) || newKey(32) || claim(32) || deedPrefix || deedSuffix
     * Followed by the full redeem script of the spent gap.
     */
    fun buildSplitSignatureScript(
        newKey: ByteArray,
        claim: ByteArray,
        deedPrefix: ByteArray,
        deedSuffix: ByteArray,
        gapRedeemScript: ByteArray
    ): ByteArray {
        val args = TAG_SPLIT +
                pushData(newKey) +
                pushData(claim) +
                pushLarge(deedPrefix) +
                pushLarge(deedSuffix)
        return args + pushLarge(gapRedeemScript)
    }

    /**
     * Activate (reveal) - spends a PENDING deed.
     * Arguments order:
     *   tag(4) || name || ownerType(1) || ownerKey(32)
     * Followed by the full redeem script of the PENDING deed.
     */
    fun buildActivateSignatureScript(
        name: ByteArray,
        ownerType: Byte,
        ownerKey: ByteArray,
        pendingDeedRedeemScript: ByteArray
    ): ByteArray {
        val args = TAG_ACTIVATE +
                pushData(name) +
                pushByte(ownerType) +
                pushData(ownerKey)
        return args + pushLarge(pendingDeedRedeemScript)
    }

    val DIRECTORY_ENDPOINTS = listOf(
        "https://directory.dotk.name",
        "https://api.dotk.name"
    )

    private fun getDirectoryClient(client: OkHttpClient): OkHttpClient {
        return client.newBuilder()
            .connectTimeout(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
    }

    // ---------------------------------------------------------------
    // Genesis Template Caching & Exact Parsing
    // ---------------------------------------------------------------
    fun getOrFetchBytecodes(client: OkHttpClient): DotkBytecodes {
        cachedBytecodes?.let { return it }
        val fastClient = getDirectoryClient(client)

        for (base in DIRECTORY_ENDPOINTS) {
            val candidateUrls = listOf(
                "$base/v1/genesis/covenants/$REGISTRY_COVENANT_ID",
                "$base/v1/genesis"
            )
            for (url in candidateUrls) {
                try {
                    val req = Request.Builder().url(url).get().build()
                    fastClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use
                        val body = resp.body?.string() ?: return@use
                        val genesis = JSONObject(body)

                        // deedAbi -> contracts -> DotkDeed -> compiled
                        val deedContract = genesis.optJSONObject("deedAbi")
                            ?.optJSONObject("contracts")
                            ?.optJSONObject("DotkDeed")
                            ?: genesis.optJSONObject("DotkDeed")
                        val (deedPrefix, deedSuffix) = parseContractBytecode(deedContract, defaultOffset = 1, defaultLen = 103)

                        // gapAbi -> contracts -> DotkGap -> compiled
                        val gapContract = genesis.optJSONObject("gapAbi")
                            ?.optJSONObject("contracts")
                            ?.optJSONObject("DotkGap")
                            ?: genesis.optJSONObject("DotkGap")
                        val (gapPrefix, gapSuffix) = parseContractBytecode(gapContract, defaultOffset = 1, defaultLen = 66)

                        val bc = DotkBytecodes(
                            deedPrefix = deedPrefix,
                            deedSuffix = deedSuffix,
                            gapPrefix = gapPrefix,
                            gapSuffix = gapSuffix
                        )
                        cachedBytecodes = bc
                        return bc
                    }
                } catch (_: Exception) {}
            }
        }
        return fallbackBytecodes()
    }

    private fun parseContractBytecode(
        contractObj: JSONObject?,
        defaultOffset: Int,
        defaultLen: Int
    ): Pair<ByteArray, ByteArray> {
        if (contractObj == null) return fallbackPrefixSuffix(defaultOffset, defaultLen)
        val compiledObj = contractObj.optJSONObject("compiled") ?: contractObj

        val bytecodeRaw = compiledObj.opt("bytecode")
        val bytecodeBytes = when (bytecodeRaw) {
            is org.json.JSONArray -> {
                ByteArray(bytecodeRaw.length()) { i ->
                    (bytecodeRaw.optInt(i) and 0xff).toByte()
                }
            }
            is String -> hexToBytes(bytecodeRaw)
            else -> null
        }

        if (bytecodeBytes == null || bytecodeBytes.size <= (defaultOffset + defaultLen)) {
            return fallbackPrefixSuffix(defaultOffset, defaultLen)
        }

        var offset = defaultOffset
        var len = defaultLen

        val stateSpan = compiledObj.opt("state_span") ?: compiledObj.opt("stateSpan")
        when (stateSpan) {
            is JSONObject -> {
                offset = stateSpan.optInt("offset", defaultOffset)
                len = stateSpan.optInt("length", stateSpan.optInt("len", defaultLen))
            }
            is org.json.JSONArray -> {
                offset = stateSpan.optInt(0, defaultOffset)
                len = stateSpan.optInt(1, defaultLen)
            }
        }

        val prefix = bytecodeBytes.copyOfRange(0, offset)
        val suffix = bytecodeBytes.copyOfRange(offset + len, bytecodeBytes.size)
        return Pair(prefix, suffix)
    }

    private fun fallbackPrefixSuffix(offset: Int, len: Int): Pair<ByteArray, ByteArray> {
        val prefix = byteArrayOf(0x6b.toByte())
        val suffixLen = if (len == 103) 3116 else 6275
        return Pair(prefix, ByteArray(suffixLen))
    }

    private fun fallbackBytecodes(): DotkBytecodes {
        return DotkBytecodes(
            deedPrefix = byteArrayOf(0x6b.toByte()),
            deedSuffix = ByteArray(3116),
            gapPrefix = byteArrayOf(0x6b.toByte()),
            gapSuffix = ByteArray(6275)
        )
    }

    // ---------------------------------------------------------------
    // Key & Covering Gap Resolution from /v1/names/{name}/key
    // ---------------------------------------------------------------
    data class DotkKeyLookupResult(
        val keyHex: String,
        val kind: String, // "free", "active", "pending", "ownerUnknown"
        val coveringLo: ByteArray?,
        val coveringHi: ByteArray?,
        val registryCovenantId: String,
        val isFree: Boolean,
        val predecessorLo: ByteArray? = null,
        val predecessorHi: ByteArray? = null,
        val successorLo: ByteArray? = null,
        val successorHi: ByteArray? = null
    )

    data class ActiveDomainNeighbours(
        val predecessorLo: ByteArray,
        val predecessorHi: ByteArray,
        val successorLo: ByteArray,
        val successorHi: ByteArray
    )

    fun fetchNameKey(domain: String, client: OkHttpClient): DotkKeyLookupResult? {
        val clean = domain.lowercase().removeSuffix(".k").trim()
        val deedKey = CryptoUtils.blake3(clean.encodeToByteArray())
        val fastClient = getDirectoryClient(client)
        for (base in DIRECTORY_ENDPOINTS) {
            val candidateUrls = listOf(
                "$base/v1/names/$clean/key",
                "$base/v1/keys/$clean"
            )
            for (url in candidateUrls) {
                try {
                    val req = Request.Builder().url(url).get().build()
                    fastClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use
                        val body = resp.body?.string() ?: return@use
                        val json = JSONObject(body)

                        val keyHex = json.optString("key", "")
                        val kind = json.optString("kind", "unknown")
                        val registryCovenantId = json.optString("registryCovenantId", REGISTRY_COVENANT_ID)

                        val coveringObj = json.optJSONObject("covering")
                            ?: json.optJSONObject("range")
                        val loHex = coveringObj?.optString("lo") ?: json.optString("lo", "")
                        val hiHex = coveringObj?.optString("hi") ?: json.optString("hi", "")

                        val loBytes = if (loHex.isNotBlank()) hexToBytes(loHex).copyOf(32) else null
                        val hiBytes = if (hiHex.isNotBlank()) hexToBytes(hiHex).copyOf(32) else null

                        val neighboursObj = json.optJSONObject("neighbours")
                        val predObj = neighboursObj?.optJSONObject("predecessor")
                        val succObj = neighboursObj?.optJSONObject("successor")

                        val predLoHex = predObj?.optString("lo", "") ?: ""
                        val predHiHex = predObj?.optString("hi", "") ?: ""
                        val succLoHex = succObj?.optString("lo", "") ?: ""
                        val succHiHex = succObj?.optString("hi", "") ?: ""

                        val predLoBytes = if (predLoHex.isNotBlank()) hexToBytes(predLoHex).copyOf(32) else null
                        val predHiBytes = if (predHiHex.isNotBlank()) hexToBytes(predHiHex).copyOf(32) else null
                        val succLoBytes = if (succLoHex.isNotBlank()) hexToBytes(succLoHex).copyOf(32) else null
                        val succHiBytes = if (succHiHex.isNotBlank()) hexToBytes(succHiHex).copyOf(32) else null

                        return DotkKeyLookupResult(
                            keyHex = keyHex,
                            kind = kind,
                            coveringLo = loBytes,
                            coveringHi = hiBytes,
                            registryCovenantId = registryCovenantId,
                            isFree = kind.equals("free", ignoreCase = true),
                            predecessorLo = predLoBytes,
                            predecessorHi = predHiBytes,
                            successorLo = succLoBytes,
                            successorHi = succHiBytes
                        )
                    }
                } catch (_: Exception) {}
            }
        }
        return null
    }

    fun fetchActiveDomainNeighbours(domain: String, client: OkHttpClient): ActiveDomainNeighbours? {
        val clean = domain.lowercase().removeSuffix(".k").trim()
        val deedKey = CryptoUtils.blake3(clean.encodeToByteArray())
        val keyInfo = fetchNameKey(clean, client)
        if (keyInfo?.predecessorLo != null && keyInfo.successorHi != null) {
            return ActiveDomainNeighbours(
                predecessorLo = keyInfo.predecessorLo,
                predecessorHi = keyInfo.predecessorHi ?: deedKey,
                successorLo = keyInfo.successorLo ?: deedKey,
                successorHi = keyInfo.successorHi
            )
        }
        return null
    }

    /**
     * Derives the gap P2SH address for querying live UTXOs from Kaspa node
     */
    fun deriveGapP2shAddress(
        gapLo: ByteArray,
        gapHi: ByteArray,
        gapPrefix: ByteArray,
        gapSuffix: ByteArray,
        prefix: String = "kaspa"
    ): String {
        val gapState = buildGapState(gapLo, gapHi)
        val gapRedeem = buildGapRedeemScript(gapState, gapPrefix, gapSuffix)
        val hash = CryptoUtils.blake2b256(gapRedeem)
        return CryptoUtils.encodeP2shAddress(hash, prefix)
    }

    /**
     * Derives the PENDING deed P2SH address
     */
    fun derivePendingDeedP2shAddress(
        newKey: ByteArray,
        claim: ByteArray,
        deedPrefix: ByteArray,
        deedSuffix: ByteArray,
        prefix: String = "kaspa"
    ): String {
        val pendingState = buildDeedState(
            status = 0x01.toByte(),
            key = newKey,
            ownerType = 0x00.toByte(),
            owner = claim,
            name = ByteArray(32)
        )
        val pendingRedeem = buildDeedRedeemScript(pendingState, deedPrefix, deedSuffix)
        val hash = CryptoUtils.blake2b256(pendingRedeem)
        return CryptoUtils.encodeP2shAddress(hash, prefix)
    }

    // ---------------------------------------------------------------
    // Covering Gap Fetching (legacy + node resolution bridge)
    // ---------------------------------------------------------------
    fun fetchCoveringGap(domain: String, client: OkHttpClient): CoveringGapInfo? {
        val clean = domain.lowercase().removeSuffix(".k").trim()
        val keyInfo = fetchNameKey(clean, client) ?: return null
        if (!keyInfo.isFree || keyInfo.coveringLo == null || keyInfo.coveringHi == null) {
            return null
        }

        val bytecodes = getOrFetchBytecodes(client)
        val gapAddress = deriveGapP2shAddress(
            gapLo = keyInfo.coveringLo,
            gapHi = keyInfo.coveringHi,
            gapPrefix = bytecodes.gapPrefix,
            gapSuffix = bytecodes.gapSuffix
        )

        // Query Kaspa node for live UTXO at this derived gap P2SH address
        val utxo = fetchUtxoForAddress(gapAddress, client) ?: return null

        return CoveringGapInfo(
            lo = keyInfo.coveringLo,
            hi = keyInfo.coveringHi,
            utxo = utxo
        )
    }

    private fun fetchUtxoForAddress(address: String, client: OkHttpClient): KaspaTransactionEngine.KaspaUtxo? {
        val endpoints = listOf(
            "https://api.kaspa.org/addresses/$address/utxos",
            "https://api-mainnet.kaspanet.io/addresses/$address/utxos"
        )
        for (url in endpoints) {
            try {
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val array = org.json.JSONArray(body)
                        if (array.length() > 0) {
                            val obj = array.getJSONObject(0)
                            val outpointObj = obj.optJSONObject("outpoint")
                            val utxoEntryObj = obj.optJSONObject("utxoEntry") ?: obj.optJSONObject("utxo_entry")
                            if (outpointObj != null && utxoEntryObj != null) {
                                return KaspaTransactionEngine.KaspaUtxo(
                                    outpoint = KaspaTransactionEngine.KaspaOutpoint.fromJson(outpointObj),
                                    utxoEntry = KaspaTransactionEngine.KaspaUtxoEntry.fromJson(utxoEntryObj)
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    // ---------------------------------------------------------------
    // Assembled Commit (Split) and Reveal (Activate) Builders
    // ---------------------------------------------------------------

    /**
     * Assembled Commit Transaction Builder (KIP-20 Split)
     */
    fun buildCommitTransaction(
        gapUtxo: KaspaTransactionEngine.KaspaUtxo,
        gapLo: ByteArray,
        gapHi: ByteArray,
        name: String,
        ownerType: Byte,
        ownerKey: ByteArray,
        fundingUtxos: List<KaspaTransactionEngine.KaspaUtxo>,
        changeAddress: String,
        fee: Long,
        deedPrefix: ByteArray,
        deedSuffix: ByteArray,
        gapPrefix: ByteArray,
        gapSuffix: ByteArray
    ): KaspaTransactionEngine.KaspaTransaction {
        val cleanName = name.lowercase().removeSuffix(".k").trim()
        val newKey = CryptoUtils.blake3(cleanName.encodeToByteArray())
        val claim = CryptoUtils.blake3(cleanName.encodeToByteArray() + byteArrayOf(ownerType) + ownerKey)

        // Protocol outputs (indices 0, 1, 2)
        val leftGapState = buildGapState(gapLo, newKey)
        val rightGapState = buildGapState(newKey, gapHi)
        val pendingState = buildDeedState(
            status = 0x01.toByte(),
            key = newKey,
            ownerType = 0x00.toByte(),
            owner = claim,
            name = ByteArray(32)
        )

        val leftGapRedeem = buildGapRedeemScript(leftGapState, gapPrefix, gapSuffix)
        val rightGapRedeem = buildGapRedeemScript(rightGapState, gapPrefix, gapSuffix)
        val pendingRedeem = buildDeedRedeemScript(pendingState, deedPrefix, deedSuffix)

        val protocolOutputs = listOf(
            KaspaTransactionEngine.KaspaTransactionOutput(
                amount = GAP_VALUE,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(
                    0,
                    p2shScriptPubKey(leftGapRedeem).joinToString("") { "%02x".format(it) }
                ),
                covenant = KaspaTransactionEngine.KaspaCovenantBinding(0, REGISTRY_COVENANT_ID)
            ),
            KaspaTransactionEngine.KaspaTransactionOutput(
                amount = GAP_VALUE,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(
                    0,
                    p2shScriptPubKey(rightGapRedeem).joinToString("") { "%02x".format(it) }
                ),
                covenant = KaspaTransactionEngine.KaspaCovenantBinding(0, REGISTRY_COVENANT_ID)
            ),
            KaspaTransactionEngine.KaspaTransactionOutput(
                amount = BOND_AMOUNT + DEPOSIT_AMOUNT,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(
                    0,
                    p2shScriptPubKey(pendingRedeem).joinToString("") { "%02x".format(it) }
                ),
                covenant = KaspaTransactionEngine.KaspaCovenantBinding(0, REGISTRY_COVENANT_ID)
            )
        )

        // Funding calculation
        val totalIn = gapUtxo.utxoEntry.amount + fundingUtxos.sumOf { it.utxoEntry.amount }
        val totalOut = GAP_VALUE + GAP_VALUE + (BOND_AMOUNT + DEPOSIT_AMOUNT) + fee
        val change = totalIn - totalOut
        require(change >= 0) { "Insufficient funds for commit: totalIn=$totalIn, totalOut=$totalOut" }

        val outputs = protocolOutputs.toMutableList()
        if (change > 0) {
            outputs.add(
                KaspaTransactionEngine.KaspaTransactionOutput(
                    amount = change,
                    scriptPublicKey = KaspaTransactionEngine.addressToScriptPublicKey(changeAddress)
                )
            )
        }

        // Gap spend covenant signature script (authorized without private key)
        val spentGapState = buildGapState(gapLo, gapHi)
        val spentGapRedeem = buildGapRedeemScript(spentGapState, gapPrefix, gapSuffix)
        val splitSigScript = buildSplitSignatureScript(
            newKey = newKey,
            claim = claim,
            deedPrefix = deedPrefix,
            deedSuffix = deedSuffix,
            gapRedeemScript = spentGapRedeem
        )
        val splitSigHex = splitSigScript.joinToString("") { "%02x".format(it) }

        val inputs = mutableListOf(
            KaspaTransactionEngine.KaspaTransactionInput(
                previousOutpoint = gapUtxo.outpoint,
                signatureScript = splitSigHex,
                sequence = 0L,
                sigOpCount = 1
            )
        )
        fundingUtxos.forEach { utxo ->
            inputs.add(
                KaspaTransactionEngine.KaspaTransactionInput(
                    previousOutpoint = utxo.outpoint,
                    signatureScript = "",
                    sequence = 0L,
                    sigOpCount = 1
                )
            )
        }

        return KaspaTransactionEngine.KaspaTransaction(
            version = 1,
            inputs = inputs,
            outputs = outputs,
            subnetworkId = KaspaTransactionEngine.DEFAULT_SUBNETWORK_ID
        )
    }

    /**
     * Assembled Reveal Transaction Builder (KIP-20 Activate)
     */
    fun buildRevealTransaction(
        pendingDeedUtxo: KaspaTransactionEngine.KaspaUtxo,
        name: String,
        ownerType: Byte,
        ownerKey: ByteArray,
        fundingUtxos: List<KaspaTransactionEngine.KaspaUtxo> = emptyList(),
        changeAddress: String? = null,
        fee: Long,
        deedPrefix: ByteArray,
        deedSuffix: ByteArray
    ): KaspaTransactionEngine.KaspaTransaction {
        val cleanName = name.lowercase().removeSuffix(".k").trim()
        val key = CryptoUtils.blake3(cleanName.encodeToByteArray())
        val paddedName = cleanName.encodeToByteArray().copyOf(32)

        val activeState = buildDeedState(
            status = 0x02.toByte(),
            key = key,
            ownerType = ownerType,
            owner = ownerKey,
            name = paddedName
        )
        val activeRedeem = buildDeedRedeemScript(activeState, deedPrefix, deedSuffix)
        val activeSpk = p2shScriptPubKey(activeRedeem)

        val tier = when (cleanName.length) {
            1 -> FEE_1CH
            2 -> FEE_2CH
            3 -> FEE_3CH
            4 -> FEE_4CH
            else -> FEE_5PLUS
        }

        val protocolOutputs = listOf(
            // 0 - ACTIVE continuation (exactly BOND_AMOUNT)
            KaspaTransactionEngine.KaspaTransactionOutput(
                amount = BOND_AMOUNT,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(
                    0,
                    activeSpk.joinToString("") { "%02x".format(it) }
                ),
                covenant = KaspaTransactionEngine.KaspaCovenantBinding(0, REGISTRY_COVENANT_ID)
            ),
            // 1 - fee to DEVFUND
            KaspaTransactionEngine.KaspaTransactionOutput(
                amount = tier,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, DEVFUND_SCRIPT_PUBKEY_HEX)
            )
        )

        val totalIn = pendingDeedUtxo.utxoEntry.amount + fundingUtxos.sumOf { it.utxoEntry.amount }
        val totalOut = BOND_AMOUNT + tier + fee
        val change = totalIn - totalOut
        require(change >= 0) { "Insufficient funds for reveal: totalIn=$totalIn, totalOut=$totalOut" }

        val outputs = protocolOutputs.toMutableList()
        if (change > 0 && changeAddress != null) {
            outputs.add(
                KaspaTransactionEngine.KaspaTransactionOutput(
                    amount = change,
                    scriptPublicKey = KaspaTransactionEngine.addressToScriptPublicKey(changeAddress)
                )
            )
        }

        // PENDING deed spend signature script
        val claim = CryptoUtils.blake3(cleanName.encodeToByteArray() + byteArrayOf(ownerType) + ownerKey)
        val pendingState = buildDeedState(
            status = 0x01.toByte(),
            key = key,
            ownerType = 0x00.toByte(),
            owner = claim,
            name = ByteArray(32)
        )
        val pendingRedeem = buildDeedRedeemScript(pendingState, deedPrefix, deedSuffix)
        val activateSigScript = buildActivateSignatureScript(
            name = cleanName.encodeToByteArray(),
            ownerType = ownerType,
            ownerKey = ownerKey,
            pendingDeedRedeemScript = pendingRedeem
        )
        val activateSigHex = activateSigScript.joinToString("") { "%02x".format(it) }

        val inputs = mutableListOf(
            KaspaTransactionEngine.KaspaTransactionInput(
                previousOutpoint = pendingDeedUtxo.outpoint,
                signatureScript = activateSigHex,
                sequence = 0L,
                sigOpCount = 1
            )
        )
        fundingUtxos.forEach { utxo ->
            inputs.add(
                KaspaTransactionEngine.KaspaTransactionInput(
                    previousOutpoint = utxo.outpoint,
                    signatureScript = "",
                    sequence = 0L,
                    sigOpCount = 1
                )
            )
        }

        return KaspaTransactionEngine.KaspaTransaction(
            version = 1,
            inputs = inputs,
            outputs = outputs,
            subnetworkId = KaspaTransactionEngine.DEFAULT_SUBNETWORK_ID
        )
    }

    /**
     * Derives the ACTIVE deed P2SH address for an owned domain.
     */
    fun deriveActiveDeedP2shAddress(
        name: String,
        ownerType: Byte,
        ownerKey: ByteArray,
        deedPrefix: ByteArray,
        deedSuffix: ByteArray,
        prefix: String = "kaspa"
    ): String {
        val cleanName = name.lowercase().removeSuffix(".k").trim()
        val key = CryptoUtils.blake3(cleanName.encodeToByteArray())
        val paddedName = cleanName.encodeToByteArray().copyOf(32)
        val activeState = buildDeedState(
            status = 0x02.toByte(), // ACTIVE status
            key = key,
            ownerType = ownerType,
            owner = ownerKey,
            name = paddedName
        )
        val activeRedeem = buildDeedRedeemScript(activeState, deedPrefix, deedSuffix)
        val hash = CryptoUtils.blake2b256(activeRedeem)
        return CryptoUtils.encodeP2shAddress(hash, prefix)
    }

    /**
     * Transfer signature script:
     *   TAG_TRANSFER (4) || newOwnerType (1) || newOwner (32) || sigs (dynamic array) || witness (int 0)
     * Followed by the active deed redeem script.
     */
    fun buildTransferSignatureScript(
        newOwnerType: Byte,
        newOwnerKey: ByteArray,
        ownerSignature: ByteArray,
        activeDeedRedeemScript: ByteArray
    ): ByteArray {
        val sigsArray = encodeDynamicSigArray(listOf(ownerSignature))
        val args = TAG_TRANSFER +
                pushByte(newOwnerType) +
                pushData(newOwnerKey) +
                sigsArray +
                pushInt(0) // witness = 0
        return args + pushLarge(activeDeedRedeemScript)
    }

    /**
     * Assembled Transfer Transaction:
     * Spends input 0 (ACTIVE deed, 1 KAS with covenant binding)
     * Output 0: Transferred ACTIVE deed continuation with new owner (amount: BOND_AMOUNT, covenant: REGISTRY_COVENANT_ID)
     * Optional funding inputs for fee, with change returned to sender.
     */
    fun buildTransferTransaction(
        activeDeedUtxo: KaspaTransactionEngine.KaspaUtxo,
        name: String,
        currentOwnerType: Byte,
        currentOwnerKey: ByteArray,
        newOwnerType: Byte,
        newOwnerKey: ByteArray,
        fundingUtxos: List<KaspaTransactionEngine.KaspaUtxo> = emptyList(),
        changeAddress: String? = null,
        fee: Long = 20_000L,
        deedPrefix: ByteArray,
        deedSuffix: ByteArray,
        ownerSignature: ByteArray = ByteArray(64)
    ): KaspaTransactionEngine.KaspaTransaction {
        val cleanName = name.lowercase().removeSuffix(".k").trim()
        val key = CryptoUtils.blake3(cleanName.encodeToByteArray())
        val paddedName = cleanName.encodeToByteArray().copyOf(32)

        // Target new active deed state with updated owner
        val newActiveState = buildDeedState(
            status = 0x02.toByte(),
            key = key,
            ownerType = newOwnerType,
            owner = newOwnerKey,
            name = paddedName
        )
        val newActiveRedeem = buildDeedRedeemScript(newActiveState, deedPrefix, deedSuffix)
        val newActiveSpk = p2shScriptPubKey(newActiveRedeem)

        val protocolOutputs = listOf(
            KaspaTransactionEngine.KaspaTransactionOutput(
                amount = BOND_AMOUNT,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(
                    0,
                    newActiveSpk.joinToString("") { "%02x".format(it) }
                ),
                covenant = KaspaTransactionEngine.KaspaCovenantBinding(0, REGISTRY_COVENANT_ID)
            )
        )

        val totalIn = activeDeedUtxo.utxoEntry.amount + fundingUtxos.sumOf { it.utxoEntry.amount }
        val totalOut = BOND_AMOUNT + fee
        val change = totalIn - totalOut
        require(change >= 0) { "Insufficient funds for transfer: totalIn=$totalIn, totalOut=$totalOut" }

        val outputs = protocolOutputs.toMutableList()
        if (change > 0 && changeAddress != null) {
            outputs.add(
                KaspaTransactionEngine.KaspaTransactionOutput(
                    amount = change,
                    scriptPublicKey = KaspaTransactionEngine.addressToScriptPublicKey(changeAddress)
                )
            )
        }

        // Current active deed redeem script (being spent)
        val currentActiveState = buildDeedState(
            status = 0x02.toByte(),
            key = key,
            ownerType = currentOwnerType,
            owner = currentOwnerKey,
            name = paddedName
        )
        val currentActiveRedeem = buildDeedRedeemScript(currentActiveState, deedPrefix, deedSuffix)
        val transferSigScript = buildTransferSignatureScript(
            newOwnerType = newOwnerType,
            newOwnerKey = newOwnerKey,
            ownerSignature = ownerSignature,
            activeDeedRedeemScript = currentActiveRedeem
        )
        val transferSigHex = transferSigScript.joinToString("") { "%02x".format(it) }

        val inputs = mutableListOf(
            KaspaTransactionEngine.KaspaTransactionInput(
                previousOutpoint = activeDeedUtxo.outpoint,
                signatureScript = transferSigHex,
                sequence = 0L,
                sigOpCount = 1
            )
        )
        fundingUtxos.forEach { utxo ->
            inputs.add(
                KaspaTransactionEngine.KaspaTransactionInput(
                    previousOutpoint = utxo.outpoint,
                    signatureScript = "",
                    sequence = 0L,
                    sigOpCount = 1
                )
            )
        }

        return KaspaTransactionEngine.KaspaTransaction(
            version = 1,
            inputs = inputs,
            outputs = outputs,
            subnetworkId = KaspaTransactionEngine.DEFAULT_SUBNETWORK_ID
        )
    }

    /**
     * Seat 0 – predecessor gap (merge leader)
     * No arguments – just the dispatch tag + the full redeem script of the gap
     */
    fun buildMergeSignatureScript(gapRedeemScript: ByteArray): ByteArray {
        val tag = TAG_MERGE // 63d25bc2
        return tag + pushLarge(gapRedeemScript)
    }

    /**
     * Seat 1 – ACTIVE deed (release / owner consent)
     * Arguments: sigs (dynamic array of signatures) + witness (int)
     */
    fun buildReleaseSignatureScript(
        sigs: List<ByteArray>,
        witnessIndex: Int = 0,
        deedRedeemScript: ByteArray
    ): ByteArray {
        val tag = TAG_RELEASE // 97479433
        val sigsPayload = encodeDynamicSigArray(sigs)
        val witnessPayload = pushInt(witnessIndex)
        return tag +
                sigsPayload +
                witnessPayload +
                pushLarge(deedRedeemScript)
    }

    /**
     * Convenience single-sig overload for Release signature script
     */
    fun buildReleaseSignatureScript(
        ownerSignature: ByteArray,
        activeDeedRedeemScript: ByteArray
    ): ByteArray {
        return buildReleaseSignatureScript(
            sigs = listOf(ownerSignature),
            witnessIndex = 0,
            deedRedeemScript = activeDeedRedeemScript
        )
    }

    /**
     * Seat 2 – successor gap (absorbed delegator)
     * No arguments – just the dispatch tag + the full redeem script of the gap
     */
    fun buildAbsorbedSignatureScript(gapRedeemScript: ByteArray): ByteArray {
        val tag = TAG_ABSORBED // dab76355
        return tag + pushLarge(gapRedeemScript)
    }

    /**
     * Builds a consensus-valid release (bond-reclaim) transaction.
     *
     * @param predecessorGapUtxo  live gap UTXO whose hi == deed.key
     * @param predecessorLo       32-byte lo of the predecessor gap
     * @param activeDeedUtxo      the ACTIVE deed being released (amount == BOND_AMOUNT)
     * @param deedKey             32-byte key of the deed (== blake3(name))
     * @param successorGapUtxo    live gap UTXO whose lo == deed.key
     * @param successorHi         32-byte hi of the successor gap
     * @param ownerAddress        address that receives the 1 KAS bond refund
     * @param fundingUtxos        optional extra inputs to cover network fee
     * @param changeAddress       where leftover funding goes
     * @param fee                 network fee in sompi
     * @param gapPrefix           cached gap prefix
     * @param gapSuffix           cached gap suffix
     * @param deedPrefix          cached deed prefix
     * @param deedSuffix          cached deed suffix
     * @param ownerSignature      Schnorr signature from deed owner (seat 1)
     * @param ownerType           owner type (0x00 for standard pubkey)
     * @param ownerKey            owner 32-byte pubkey
     * @param name                domain name (e.g. "myname.k")
     */
    fun buildReleaseTransaction(
        predecessorGapUtxo: KaspaTransactionEngine.KaspaUtxo,
        predecessorLo: ByteArray,
        activeDeedUtxo: KaspaTransactionEngine.KaspaUtxo,
        deedKey: ByteArray,
        successorGapUtxo: KaspaTransactionEngine.KaspaUtxo,
        successorHi: ByteArray,
        ownerAddress: String,
        fundingUtxos: List<KaspaTransactionEngine.KaspaUtxo> = emptyList(),
        changeAddress: String,
        fee: Long = 10_000L,
        gapPrefix: ByteArray,
        gapSuffix: ByteArray,
        deedPrefix: ByteArray,
        deedSuffix: ByteArray,
        ownerSignature: ByteArray = ByteArray(64),
        ownerType: Byte = 0x00.toByte(),
        ownerKey: ByteArray = ByteArray(32),
        name: String = ""
    ): KaspaTransactionEngine.KaspaTransaction {
        // Sanity: the three objects must be adjacent
        require(predecessorGapUtxo.utxoEntry.amount == GAP_VALUE) { "Predecessor gap amount must be $GAP_VALUE" }
        require(activeDeedUtxo.utxoEntry.amount == BOND_AMOUNT) { "Active deed amount must be $BOND_AMOUNT" }
        require(successorGapUtxo.utxoEntry.amount == GAP_VALUE) { "Successor gap amount must be $GAP_VALUE" }

        // ----- Single lineage output: the widened gap -----
        val mergedState = buildGapState(lo = predecessorLo, hi = successorHi)
        val mergedRedeem = buildGapRedeemScript(mergedState, gapPrefix, gapSuffix)
        val mergedSpk = p2shScriptPubKey(mergedRedeem)

        val protocolOutputs = listOf(
            // index 0 – widened gap (the only lineage output)
            KaspaTransactionEngine.KaspaTransactionOutput(
                amount          = GAP_VALUE,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(
                    version = 0,
                    script = mergedSpk.joinToString("") { "%02x".format(it) }
                ),
                covenant        = KaspaTransactionEngine.KaspaCovenantBinding(
                    authorizingInput = 0,               // leader (merge) authorises the lineage
                    covenantId       = REGISTRY_COVENANT_ID
                )
            )
        )

        // ----- Plain outputs: bond refund + optional change -----
        val totalIn = predecessorGapUtxo.utxoEntry.amount +
                activeDeedUtxo.utxoEntry.amount +
                successorGapUtxo.utxoEntry.amount +
                fundingUtxos.sumOf { it.utxoEntry.amount }

        val totalProtocolOut = GAP_VALUE                     // the merged gap
        val bondRefund = BOND_AMOUNT                         // returned to owner
        val change = totalIn - totalProtocolOut - bondRefund - fee
        require(change >= 0) { "insufficient funds for release: totalIn=$totalIn, needed=${totalProtocolOut + bondRefund + fee}" }

        val outputs = protocolOutputs.toMutableList()

        // Bond refund (plain output, no covenant)
        outputs.add(
            KaspaTransactionEngine.KaspaTransactionOutput(
                amount          = bondRefund,
                scriptPublicKey = KaspaTransactionEngine.addressToScriptPublicKey(ownerAddress)
            )
        )

        if (change > 0) {
            outputs.add(
                KaspaTransactionEngine.KaspaTransactionOutput(
                    amount          = change,
                    scriptPublicKey = KaspaTransactionEngine.addressToScriptPublicKey(changeAddress)
                )
            )
        }

        // Prepare signature scripts for protocol seats
        // Seat 0 - Predecessor gap redeem: (lo = predecessorLo, hi = deedKey)
        val predGapState = buildGapState(predecessorLo, deedKey)
        val predGapRedeem = buildGapRedeemScript(predGapState, gapPrefix, gapSuffix)
        val mergeSigScript = buildMergeSignatureScript(predGapRedeem)
        val mergeSigHex = mergeSigScript.joinToString("") { "%02x".format(it) }

        // Seat 1 - Active deed redeem: (key = deedKey)
        val cleanName = name.lowercase().removeSuffix(".k").trim()
        val paddedName = cleanName.encodeToByteArray().copyOf(32)
        val activeState = buildDeedState(
            status = 0x02.toByte(),
            key = deedKey,
            ownerType = ownerType,
            owner = ownerKey,
            name = paddedName
        )
        val activeRedeem = buildDeedRedeemScript(activeState, deedPrefix, deedSuffix)
        val releaseSigScript = buildReleaseSignatureScript(
            ownerSignature = ownerSignature,
            activeDeedRedeemScript = activeRedeem
        )
        val releaseSigHex = releaseSigScript.joinToString("") { "%02x".format(it) }

        // Seat 2 - Successor gap redeem: (lo = deedKey, hi = successorHi)
        val succGapState = buildGapState(deedKey, successorHi)
        val succGapRedeem = buildGapRedeemScript(succGapState, gapPrefix, gapSuffix)
        val absorbedSigScript = buildAbsorbedSignatureScript(succGapRedeem)
        val absorbedSigHex = absorbedSigScript.joinToString("") { "%02x".format(it) }

        // ----- Inputs (strict order: Seat 0, Seat 1, Seat 2, then funding) -----
        val inputs = mutableListOf(
            // seat 0 – predecessor gap (merge leader)
            KaspaTransactionEngine.KaspaTransactionInput(
                previousOutpoint = predecessorGapUtxo.outpoint,
                signatureScript = mergeSigHex,
                sequence = 0L,
                sigOpCount = 1
            ),
            // seat 1 – ACTIVE deed (release / owner consent)
            KaspaTransactionEngine.KaspaTransactionInput(
                previousOutpoint = activeDeedUtxo.outpoint,
                signatureScript = releaseSigHex,
                sequence = 0L,
                sigOpCount = 1
            ),
            // seat 2 – successor gap (absorbed delegator)
            KaspaTransactionEngine.KaspaTransactionInput(
                previousOutpoint = successorGapUtxo.outpoint,
                signatureScript = absorbedSigHex,
                sequence = 0L,
                sigOpCount = 1
            )
        )

        // Optional funding inputs after the protocol seats
        fundingUtxos.forEach {
            inputs.add(
                KaspaTransactionEngine.KaspaTransactionInput(
                    previousOutpoint = it.outpoint,
                    signatureScript = "",
                    sequence = 0L,
                    sigOpCount = 1
                )
            )
        }

        return KaspaTransactionEngine.KaspaTransaction(
            version      = 1,
            inputs       = inputs,
            outputs      = outputs,
            subnetworkId = KaspaTransactionEngine.DEFAULT_SUBNETWORK_ID
        )
    }

    const val DEFAULT_CARD_VALUE = 50_000_000L // 0.5 KAS

    fun encodeRecordsBlob(records: Map<String, Any>): ByteArray {
        if (records.isEmpty()) {
            return byteArrayOf(0xa0.toByte()) // CBOR empty map
        }
        val sortedKeys = records.keys.sorted()
        val numEntries = sortedKeys.size
        val header = when {
            numEntries <= 23 -> byteArrayOf((0xa0 + numEntries).toByte())
            numEntries <= 0xff -> byteArrayOf(0xb8.toByte(), numEntries.toByte())
            numEntries <= 0xffff -> byteArrayOf(
                0xb9.toByte(),
                ((numEntries shr 8) and 0xff).toByte(),
                (numEntries and 0xff).toByte()
            )
            else -> error("Too many record entries")
        }

        val out = mutableListOf<Byte>()
        header.forEach { out.add(it) }

        for (k in sortedKeys) {
            val keyBytes = k.toByteArray(Charsets.UTF_8)
            val keyHeader = encodeCborTextHeader(keyBytes.size)
            keyHeader.forEach { out.add(it) }
            keyBytes.forEach { out.add(it) }

            val value = records[k] ?: ""
            val valBytes = encodeCborValue(value)
            valBytes.forEach { out.add(it) }
        }

        val blob = out.toByteArray()
        require(blob.size <= 16_384) { "records blob exceeds 16 KB (size=${blob.size})" }
        return blob
    }

    private fun encodeCborTextHeader(len: Int): ByteArray = when {
        len <= 23 -> byteArrayOf((0x60 + len).toByte())
        len <= 0xff -> byteArrayOf(0x78.toByte(), len.toByte())
        len <= 0xffff -> byteArrayOf(0x79.toByte(), ((len shr 8) and 0xff).toByte(), (len and 0xff).toByte())
        else -> error("String too long for CBOR text")
    }

    private fun encodeCborValue(value: Any): ByteArray = when (value) {
        is Boolean -> if (value) byteArrayOf(0xf5.toByte()) else byteArrayOf(0xf4.toByte())
        is String -> {
            val bytes = value.toByteArray(Charsets.UTF_8)
            encodeCborTextHeader(bytes.size) + bytes
        }
        is ByteArray -> {
            val len = value.size
            val header = when {
                len <= 23 -> byteArrayOf((0x40 + len).toByte())
                len <= 0xff -> byteArrayOf(0x58.toByte(), len.toByte())
                len <= 0xffff -> byteArrayOf(0x59.toByte(), ((len shr 8) and 0xff).toByte(), (len and 0xff).toByte())
                else -> error("Byte array too long for CBOR bytes")
            }
            header + value
        }
        is Number -> {
            val v = value.toLong()
            if (v >= 0) {
                when {
                    v <= 23 -> byteArrayOf(v.toByte())
                    v <= 0xff -> byteArrayOf(0x18.toByte(), v.toByte())
                    v <= 0xffff -> byteArrayOf(0x19.toByte(), ((v shr 8) and 0xff).toByte(), (v and 0xff).toByte())
                    v <= 0xffffffffL -> byteArrayOf(
                        0x1a.toByte(),
                        ((v shr 24) and 0xff).toByte(),
                        ((v shr 16) and 0xff).toByte(),
                        ((v shr 8) and 0xff).toByte(),
                        (v and 0xff).toByte()
                    )
                    else -> byteArrayOf(
                        0x1b.toByte(),
                        ((v shr 56) and 0xff).toByte(),
                        ((v shr 48) and 0xff).toByte(),
                        ((v shr 40) and 0xff).toByte(),
                        ((v shr 32) and 0xff).toByte(),
                        ((v shr 24) and 0xff).toByte(),
                        ((v shr 16) and 0xff).toByte(),
                        ((v shr 8) and 0xff).toByte(),
                        (v and 0xff).toByte()
                    )
                }
            } else {
                val absV = -1 - v
                byteArrayOf(0x38.toByte(), absV.toByte())
            }
        }
        else -> {
            val bytes = value.toString().toByteArray(Charsets.UTF_8)
            encodeCborTextHeader(bytes.size) + bytes
        }
    }

    fun recordsHash(blob: ByteArray): ByteArray = CryptoUtils.blake3(blob)

    /**
     * Builds a Card redeem script:
     * <key> <recordsHash> OP_DROP OP_DROP <"dotk"> OP_EQUALVERIFY <spenderType> <spender> OP_CHECKSIG
     */
    fun buildCardRedeemScript(
        key: ByteArray,           // 32
        recordsHash: ByteArray,   // 32
        spenderType: Byte,
        spender: ByteArray        // 32
    ): ByteArray {
        require(key.size == 32 && recordsHash.size == 32 && spender.size == 32) {
            "Invalid sizes for Card redeem script: key=${key.size}, recHash=${recordsHash.size}, spender=${spender.size}"
        }

        return pushData(key) +
                pushData(recordsHash) +
                byteArrayOf(0x75.toByte(), 0x75.toByte()) +   // OP_DROP OP_DROP
                pushData("dotk".encodeToByteArray()) +
                byteArrayOf(0x88.toByte()) +                  // OP_EQUALVERIFY
                pushByte(spenderType) +
                pushData(spender) +
                byteArrayOf(0xac.toByte())                    // OP_CHECKSIG
    }

    /**
     * Derives P2SH address for a Card redeem script.
     */
    fun deriveCardP2shAddress(
        key: ByteArray,
        recordsHash: ByteArray,
        spenderType: Byte,
        spender: ByteArray,
        prefix: String = "kaspa"
    ): String {
        val redeem = buildCardRedeemScript(key, recordsHash, spenderType, spender)
        val spk = p2shScriptPubKey(redeem)
        return CryptoUtils.encodeRealKaspaAddress(spk.copyOfRange(1, 33), prefix)
    }

    /**
     * Builds a transfer that also attaches (or updates/clears) records via a card.
     *
     * This is a normal transfer (seat 0 = ACTIVE deed) that additionally places
     * a card at output index 1.
     *
     * @param activeDeedUtxo   current ACTIVE deed
     * @param name             bare name (e.g. "myname" or "myname.k")
     * @param currentOwnerType / currentOwnerKey   current owner (for signing)
     * @param newOwnerType / newOwnerKey           may be the same (update-in-place)
     * @param records          the new key-value map (empty map = clear)
     * @param cardValue        typically 50_000_000L (0.5 KAS)
     * @param fundingUtxos     pay for cardValue + network fee
     * @param changeAddress    leftover
     * @param fee              network fee
     */
    fun buildRecordsTransaction(
        activeDeedUtxo: KaspaTransactionEngine.KaspaUtxo,
        name: String,
        currentOwnerType: Byte,
        currentOwnerKey: ByteArray,
        newOwnerType: Byte,
        newOwnerKey: ByteArray,
        records: Map<String, Any>,
        cardValue: Long = DEFAULT_CARD_VALUE,
        fundingUtxos: List<KaspaTransactionEngine.KaspaUtxo> = emptyList(),
        changeAddress: String,
        fee: Long = 20_000L,
        deedPrefix: ByteArray,
        deedSuffix: ByteArray,
        ownerSignature: ByteArray = ByteArray(64)
    ): KaspaTransactionEngine.KaspaTransaction {
        val cleanName = name.lowercase().removeSuffix(".k").trim()
        val key = CryptoUtils.blake3(cleanName.encodeToByteArray())
        val paddedName = cleanName.encodeToByteArray().copyOf(32)

        // ----- ACTIVE continuation (output 0) -----
        val activeState = buildDeedState(
            status    = 0x02.toByte(),
            key       = key,
            ownerType = newOwnerType,
            owner     = newOwnerKey,
            name      = paddedName
        )
        val activeRedeem = buildDeedRedeemScript(activeState, deedPrefix, deedSuffix)
        val activeSpk    = p2shScriptPubKey(activeRedeem)

        // ----- Card (output 1) -----
        val blob         = encodeRecordsBlob(records)          // deterministic CBOR
        require(blob.size <= 16_384) { "records blob exceeds 16 KB" }
        val recHash      = recordsHash(blob)

        val cardRedeem = buildCardRedeemScript(
            key          = key,
            recordsHash  = recHash,
            spenderType  = newOwnerType,      // the new owner may later sweep the card
            spender      = newOwnerKey
        )
        val cardSpk = p2shScriptPubKey(cardRedeem)

        // ----- Outputs -----
        val protocolOutputs = listOf(
            // 0 – ACTIVE deed continuation (lineage)
            KaspaTransactionEngine.KaspaTransactionOutput(
                amount          = BOND_AMOUNT,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(
                    version = 0,
                    script = activeSpk.joinToString("") { "%02x".format(it) }
                ),
                covenant        = KaspaTransactionEngine.KaspaCovenantBinding(
                    authorizingInput = 0,
                    covenantId       = REGISTRY_COVENANT_ID
                )
            ),
            // 1 – Card (plain, no covenant)
            KaspaTransactionEngine.KaspaTransactionOutput(
                amount          = cardValue,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(
                    version = 0,
                    script = cardSpk.joinToString("") { "%02x".format(it) }
                )
            )
        )

        val totalIn  = activeDeedUtxo.utxoEntry.amount + fundingUtxos.sumOf { it.utxoEntry.amount }
        val totalOut = BOND_AMOUNT + cardValue + fee
        val change   = totalIn - totalOut
        require(change >= 0) { "insufficient funds for records transaction: totalIn=$totalIn, totalOut=$totalOut" }

        val outputs = protocolOutputs.toMutableList()
        if (change > 0) {
            outputs.add(
                KaspaTransactionEngine.KaspaTransactionOutput(
                    amount          = change,
                    scriptPublicKey = KaspaTransactionEngine.addressToScriptPublicKey(changeAddress)
                )
            )
        }

        // Current active deed redeem script (being spent at seat 0)
        val currentActiveState = buildDeedState(
            status = 0x02.toByte(),
            key = key,
            ownerType = currentOwnerType,
            owner = currentOwnerKey,
            name = paddedName
        )
        val currentActiveRedeem = buildDeedRedeemScript(currentActiveState, deedPrefix, deedSuffix)
        val transferSigScript = buildTransferSignatureScript(
            newOwnerType = newOwnerType,
            newOwnerKey = newOwnerKey,
            ownerSignature = ownerSignature,
            activeDeedRedeemScript = currentActiveRedeem
        )
        val transferSigHex = transferSigScript.joinToString("") { "%02x".format(it) }

        // ----- Inputs (seat 0 is ACTIVE deed, followed by optional funding inputs) -----
        val inputs = mutableListOf(
            KaspaTransactionEngine.KaspaTransactionInput(
                previousOutpoint = activeDeedUtxo.outpoint,
                signatureScript = transferSigHex,
                sequence = 0L,
                sigOpCount = 1
            )
        )
        fundingUtxos.forEach {
            inputs.add(
                KaspaTransactionEngine.KaspaTransactionInput(
                    previousOutpoint = it.outpoint,
                    signatureScript = "",
                    sequence = 0L,
                    sigOpCount = 1
                )
            )
        }

        return KaspaTransactionEngine.KaspaTransaction(
            version      = 1,
            inputs       = inputs,
            outputs      = outputs,
            subnetworkId = KaspaTransactionEngine.DEFAULT_SUBNETWORK_ID
        )
    }

    /**
     * Card UTXO representation for sweeping/reclaiming value.
     */
    data class CardUtxo(
        val outpoint: KaspaTransactionEngine.KaspaOutpoint,
        val amount: Long,
        val key: ByteArray,            // 32-byte name key
        val recordsHash: ByteArray,    // 32-byte blake3(blob)
        val spenderType: Byte,
        val spender: ByteArray,        // 32-byte spender key
        val redeemScript: ByteArray    // pre-built card redeem script
    )

    /**
     * Standard P2SH signature script for spending a card:
     *   pushData(signature) + pushLarge(cardRedeemScript)
     */
    fun buildCardSweepSignatureScript(
        signature: ByteArray,          // 64-byte Schnorr sig
        cardRedeemScript: ByteArray
    ): ByteArray {
        return pushData(signature) + pushLarge(cardRedeemScript)
    }

    /**
     * Sweeps one or more retired/left-behind cards back to the spender’s wallet.
     *
     * @param cardsToSweep   list of card UTXOs the spender controls
     * @param destination    address that receives the consolidated value
     * @param fee            network fee in sompi
     * @param changeAddress  optional change address (usually same as destination)
     */
    fun buildCardSweepTransaction(
        cardsToSweep: List<CardUtxo>,
        destination: String,
        fee: Long = 10_000L,
        changeAddress: String = destination
    ): KaspaTransactionEngine.KaspaTransaction {
        require(cardsToSweep.isNotEmpty()) { "nothing to sweep" }

        val totalIn = cardsToSweep.sumOf { it.amount }
        val change = totalIn - fee
        require(change > 0) { "fee ($fee sompi) exceeds card value ($totalIn sompi)" }

        // Single consolidated output to destination
        val outputs = mutableListOf(
            KaspaTransactionEngine.KaspaTransactionOutput(
                amount = change,
                scriptPublicKey = KaspaTransactionEngine.addressToScriptPublicKey(destination)
            )
        )

        // One input per card
        val inputs = cardsToSweep.map { card ->
            KaspaTransactionEngine.KaspaTransactionInput(
                previousOutpoint = card.outpoint,
                signatureScript = "",
                sequence = 0L,
                sigOpCount = 1
            )
        }.toMutableList()

        return KaspaTransactionEngine.KaspaTransaction(
            version = 1,
            inputs = inputs,
            outputs = outputs,
            subnetworkId = KaspaTransactionEngine.DEFAULT_SUBNETWORK_ID
        )
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim().removePrefix("0x")
        if (clean.isEmpty()) return ByteArray(0)
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
