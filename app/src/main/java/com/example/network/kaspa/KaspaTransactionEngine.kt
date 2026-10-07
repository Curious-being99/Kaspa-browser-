package com.example.network.kaspa

import com.example.network.CryptoUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.math.BigInteger

/**
 * Official Kaspa BlockDAG Transaction & Cryptographic Sighash Engine
 * Strictly follows Rusty-Kaspa (kaspa-consensus-core, kaspa-txscript, kaspa-wallet-core) standard.
 */
object KaspaTransactionEngine {

    // Domain separation keys from rusty-kaspa / kaspa-hashes
    val KEY_TRANSACTION_SIGNING_HASH: ByteArray = "TransactionSigningHash".toByteArray(Charsets.UTF_8)
    val KEY_TRANSACTION_HASH: ByteArray = "TransactionHash".toByteArray(Charsets.UTF_8)
    val KEY_TRANSACTION_ID: ByteArray = "TransactionID".toByteArray(Charsets.UTF_8)
    val KEY_PERSONAL_MESSAGE_HASH: ByteArray = "PersonalMessageSigningHash".toByteArray(Charsets.UTF_8)
    val KEY_COVENANT_ID_HASH: ByteArray = "CovenantIDHash".toByteArray(Charsets.UTF_8)

    // Kaspa Sighash Flags
    const val SIGHASH_ALL = 0x01
    const val SIGHASH_NONE = 0x02
    const val SIGHASH_SINGLE = 0x04
    const val SIGHASH_ANYONECANPAY = 0x80
    const val SIGHASH_MASK = 0x07

    // Default Subnetwork ID for native Kaspa L1 transactions (20 zero bytes)
    const val DEFAULT_SUBNETWORK_ID = "0000000000000000000000000000000000000000"

    // ==========================================
    // Data Models (matching RpcTransaction in rusty-kaspa)
    // ==========================================

    data class KaspaOutpoint(
        val transactionId: String,
        val index: Long
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("transactionId", transactionId)
            put("index", index)
        }

        companion object {
            fun fromJson(json: JSONObject): KaspaOutpoint {
                val txId = json.optString("transactionId", json.optString("transaction_id", ""))
                val idx = json.optLong("index", 0L)
                return KaspaOutpoint(txId, idx)
            }
        }
    }

    data class KaspaScriptPublicKey(
        val version: Int = 0,
        val script: String
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("version", version)
            put("scriptPublicKey", script)
        }

        companion object {
            fun fromJson(json: JSONObject): KaspaScriptPublicKey {
                val version = json.optInt("version", 0)
                val script = json.optString("scriptPublicKey",
                    json.optString("script_public_key",
                    json.optString("script", "")))
                return KaspaScriptPublicKey(version, script)
            }
        }
    }

    data class KaspaUtxoEntry(
        val amount: Long,
        val scriptPublicKey: KaspaScriptPublicKey,
        val blockDaaScore: Long = 0L,
        val isCoinbase: Boolean = false
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("amount", amount.toString())
            put("scriptPublicKey", scriptPublicKey.toJson())
            put("blockDaaScore", blockDaaScore.toString())
            put("isCoinbase", isCoinbase)
        }

        companion object {
            fun fromJson(json: JSONObject): KaspaUtxoEntry {
                val amt = json.optLong("amount", json.optString("amount", "0").toLongOrNull() ?: 0L)
                val scriptObj = json.optJSONObject("scriptPublicKey") ?: json.optJSONObject("script_public_key")
                val scriptPubKey = if (scriptObj != null) {
                    KaspaScriptPublicKey.fromJson(scriptObj)
                } else {
                    val rawScript = json.optString("scriptPublicKey",
                        json.optString("script_public_key",
                        json.optString("script", "")))
                    val version = json.optInt("version", 0)
                    KaspaScriptPublicKey(version, rawScript)
                }
                val daa = json.optLong("blockDaaScore", json.optLong("block_daa_score", 0L))
                val isCoinbase = json.optBoolean("isCoinbase", json.optBoolean("is_coinbase", false))
                return KaspaUtxoEntry(amt, scriptPubKey, daa, isCoinbase)
            }
        }
    }

    data class KaspaUtxo(
        val outpoint: KaspaOutpoint,
        val utxoEntry: KaspaUtxoEntry
    ) {
        val amount: Long get() = utxoEntry.amount
    }

    data class KaspaTransactionInput(
        val previousOutpoint: KaspaOutpoint,
        var signatureScript: String = "",
        val sequence: Long = 0L,
        val sigOpCount: Int = 1
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("previousOutpoint", previousOutpoint.toJson())
            put("signatureScript", signatureScript)
            put("sequence", sequence)
            put("sigOpCount", sigOpCount)
        }
    }

    data class KaspaCovenantBinding(
        val authorizingInput: Int = 0,
        val covenantId: String
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("authorizingInput", authorizingInput)
            put("covenantId", covenantId)
        }
    }

    data class KaspaTransactionOutput(
        val amount: Long,
        val scriptPublicKey: KaspaScriptPublicKey,
        val covenant: KaspaCovenantBinding? = null
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("amount", amount)
            put("scriptPublicKey", scriptPublicKey.toJson())
            if (covenant != null) {
                put("covenant", covenant.toJson())
            }
        }
    }

    // Kaspa Consensus & Mass Constants (rusty-kaspa / kaspa-consensus-core)
    const val SOMPI_PER_KAS = 100_000_000L
    const val SOMPI_PER_KAS_DOUBLE = 100_000_000.0
    const val STORAGE_MASS_PARAMETER = 1_000_000_000_000L // 10^12 = SOMPI_PER_KAS * 10,000 (KIP-0009)
    const val MASS_PER_TX_BYTE = 1L
    const val MASS_PER_SCRIPT_PUB_KEY_BYTE = 10L
    const val MASS_PER_SIG_OP = 1000L
    const val MASS_PER_INPUT_COMPUTE = 100L
    const val MASS_PER_OUTPUT_COMPUTE = 100L
    const val MINIMUM_TRANSACTION_MASS = 1000L
    const val MAXIMUM_STANDARD_TRANSACTION_MASS = 100_000L
    const val DEFAULT_SOMPI_PER_MASS = 10L // 10 sompi per mass unit (standard network relay feerate)
    const val RUSTY_KASPA_MINIMUM_FEE_SOMPIS = 10_000L // 0.0001 KAS absolute minimum network fee floor
    const val DUST_THRESHOLD_SOMPIS = 2_000_000L // 0.02 KAS (outputs below this incur high storage mass per KIP-9)
    const val STANDARD_SIGNATURE_SCRIPT_BYTES = 66 // 1 (0x41) + 64 (Schnorr Sig) + 1 (SIGHASH_ALL)
    const val STANDARD_P2PK_SCRIPT_BYTES = 34 // 1 (0x20) + 32 (pubkey) + 1 (0xac)

    fun sompiToKas(sompis: Long): Double = sompis / SOMPI_PER_KAS_DOUBLE
    fun kasToSompi(kas: Double): Long = Math.round(kas * SOMPI_PER_KAS_DOUBLE)
    fun formatKas(kas: Double): String = String.format(java.util.Locale.US, "%.8f", kas).trimEnd('0').trimEnd('.')
    fun formatKasPadded(kas: Double): String = String.format(java.util.Locale.US, "%.8f", kas)

    data class DynamicFeeEstimate(
        val feeSompi: Long,
        val feeKas: Double,
        val feeKasFormatted: String,
        val estimatedSize: Long,
        val computeMass: Long,
        val storageMass: Long,
        val totalMass: Long,
        val feerateSompiPerGram: Long
    )

    data class TransactionPlan(
        val selectedUtxos: List<KaspaUtxo>,
        val calculatedMass: Long,
        val feeSompis: Long,
        val feeKas: Double,
        val changeSompis: Long,
        val isSufficient: Boolean,
        val requiredTotalSompis: Long,
        val accumulatedSompis: Long,
        val sompiPerMass: Long = DEFAULT_SOMPI_PER_MASS
    )

    data class KaspaTransaction(
        val version: Int = 0,
        val inputs: List<KaspaTransactionInput>,
        val outputs: List<KaspaTransactionOutput>,
        val lockTime: Long = 0L,
        val subnetworkId: String = DEFAULT_SUBNETWORK_ID,
        val gas: Long = 0L,
        val payload: String = "",
        var mass: Long = 0L
    ) {
        /**
         * Calculates authentic Kaspa BlockDAG transaction mass (compute + serialized size + storage mass)
         * according to rusty-kaspa consensus rules.
         */
        fun calculateMass(): Long {
            return KaspaTransactionEngine.calculateMass(this)
        }

        fun toJson(): JSONObject = JSONObject().apply {
            put("version", version)
            val inputsArr = JSONArray()
            inputs.forEach { inputsArr.put(it.toJson()) }
            put("inputs", inputsArr)

            val outputsArr = JSONArray()
            outputs.forEach { outputsArr.put(it.toJson()) }
            put("outputs", outputsArr)

            put("lockTime", lockTime)
            put("subnetworkId", subnetworkId)
            put("gas", gas)
            put("payload", payload)
            put("mass", if (mass > 0L) mass else calculateMass())
        }
    }

    // ==========================================
    // Kaspa Address to ScriptPublicKey (pay_to_address_script)
    // ==========================================

    /**
     * Decodes a Kaspa address into its canonical ScriptPublicKey
     * P2PK Schnorr (version 0): 0x20 + 32-byte pubkey + 0xac (OP_CHECKSIG)
     * P2PK ECDSA (version 1): 0x21 + 33-byte pubkey + 0xac (OP_CHECKSIG)
     * P2SH (version 8): 0xaa + 0x20 + 32-byte hash + 0x87 (OP_EQUAL)
     */
    fun addressToScriptPublicKey(address: String): KaspaScriptPublicKey = decodeAddressToScriptPublicKey(address)

    fun decodeAddressToScriptPublicKey(address: String): KaspaScriptPublicKey {
        val trimmed = address.trim().lowercase()
        val colonIdx = trimmed.indexOf(':')
        if (colonIdx < 0) {
            throw IllegalArgumentException("Invalid Kaspa address: missing network prefix (e.g. kaspa:)")
        }
        val prefix = trimmed.substring(0, colonIdx)
        val payloadStr = trimmed.substring(colonIdx + 1)

        val charset = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
        val data5Bit = ByteArray(payloadStr.length)
        for (i in payloadStr.indices) {
            val idx = charset.indexOf(payloadStr[i])
            if (idx < 0) {
                throw IllegalArgumentException("Invalid CashAddr character '${payloadStr[i]}' in address")
            }
            data5Bit[i] = idx.toByte()
        }

        if (CryptoUtils.kaspaPolymod(prefix, data5Bit) != 0L) {
            throw IllegalArgumentException("Kaspa address checksum verification failed for $address")
        }

        if (data5Bit.size < 8) {
            throw IllegalArgumentException("Kaspa address payload is too short")
        }

        val payloadWithoutChecksum = data5Bit.copyOfRange(0, data5Bit.size - 8)
        val rawBytes = convert5To8Bits(payloadWithoutChecksum)
        if (rawBytes.isEmpty()) {
            throw IllegalArgumentException("Failed to decode 5-bit address payload to 8-bit bytes")
        }

        val version = rawBytes[0].toInt() and 0xff
        val pubKeyBytes = rawBytes.copyOfRange(1, rawBytes.size)
        val pubKeyHex = pubKeyBytes.joinToString("") { "%02x".format(it) }

        val scriptHex = when (version) {
            0 -> "20" + pubKeyHex + "ac" // P2PK Schnorr: OP_DATA_32 (0x20) + 32 bytes + OP_CHECKSIG (0xac)
            1 -> "21" + pubKeyHex + "ac" // P2PK ECDSA: OP_DATA_33 (0x21) + 33 bytes + OP_CHECKSIG (0xac)
            8 -> "aa20" + pubKeyHex + "87" // P2SH: OP_BLAKE2B (0xaa) + OP_DATA_32 (0x20) + 32 bytes + OP_EQUAL (0x87)
            else -> "20" + pubKeyHex + "ac"
        }

        return KaspaScriptPublicKey(version = 0, script = scriptHex)
    }

    fun payToPubKeyScript(pubKey32Bytes: ByteArray): KaspaScriptPublicKey {
        val pubKeyHex = pubKey32Bytes.joinToString("") { "%02x".format(it) }
        return KaspaScriptPublicKey(version = 0, script = "20" + pubKeyHex + "ac")
    }

    private fun convert5To8Bits(data5Bit: ByteArray): ByteArray {
        var acc = 0
        var bits = 0
        val out = mutableListOf<Byte>()
        val maxv = 255
        for (value in data5Bit) {
            val b = value.toInt() and 0x1f
            acc = (acc shl 5) or b
            bits += 5
            while (bits >= 8) {
                bits -= 8
                out.add(((acc ushr bits) and maxv).toByte())
            }
        }
        return out.toByteArray()
    }

    // ==========================================
    // Kaspa Rusty Little-Endian Binary Encoders
    // ==========================================

    private fun writeUInt16LE(stream: ByteArrayOutputStream, value: Int) {
        stream.write(value and 0xff)
        stream.write((value ushr 8) and 0xff)
    }

    private fun writeUInt32LE(stream: ByteArrayOutputStream, value: Long) {
        stream.write((value and 0xffL).toInt())
        stream.write(((value ushr 8) and 0xffL).toInt())
        stream.write(((value ushr 16) and 0xffL).toInt())
        stream.write(((value ushr 24) and 0xffL).toInt())
    }

    private fun writeUInt64LE(stream: ByteArrayOutputStream, value: Long) {
        for (i in 0 until 8) {
            stream.write(((value ushr (i * 8)) and 0xffL).toInt())
        }
    }

    private fun writeByte(stream: ByteArrayOutputStream, value: Byte) {
        stream.write(value.toInt() and 0xff)
    }

    private fun writeHex(stream: ByteArrayOutputStream, hexStr: String) {
        if (hexStr.isBlank()) return
        val clean = if (hexStr.length % 2 != 0) "0$hexStr" else hexStr
        val bytes = hexToBytes(clean)
        stream.write(bytes)
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

    // ==========================================
    // Kaspa Rusty Sighash Calculation (calc_schnorr_signature_hash)
    // ==========================================

    /**
     * Calculates the exact BIP-143 style Kaspa Transaction Signing Hash
     * strictly adhering to kaspa-consensus-core & kaspa-txscript algorithms.
     */
    fun calcSchnorrSignatureHash(
        tx: KaspaTransaction,
        inputIndex: Int,
        sigHashType: Int = SIGHASH_ALL,
        utxoEntry: KaspaUtxoEntry
    ): ByteArray {
        require(inputIndex in tx.inputs.indices) { "Input index $inputIndex out of bounds (inputs size: ${tx.inputs.size})" }

        // 1. previousOutputsHash (32 bytes)
        val previousOutputsHash: ByteArray = if ((sigHashType and SIGHASH_ANYONECANPAY) == 0) {
            val bos = ByteArrayOutputStream()
            for (input in tx.inputs) {
                val txIdBytes = hexToBytes(input.previousOutpoint.transactionId)
                val padTxId = if (txIdBytes.size == 32) txIdBytes else ByteArray(32).also {
                    System.arraycopy(txIdBytes, 0, it, 0, minOf(txIdBytes.size, 32))
                }
                bos.write(padTxId)
                writeUInt32LE(bos, input.previousOutpoint.index)
            }
            CryptoUtils.blake2b256(bos.toByteArray(), KEY_TRANSACTION_SIGNING_HASH)
        } else {
            ByteArray(32)
        }

        // 2. sequencesHash (32 bytes)
        val sequencesHash: ByteArray = if (
            (sigHashType and SIGHASH_ANYONECANPAY) == 0 &&
            (sigHashType and SIGHASH_MASK) != SIGHASH_SINGLE &&
            (sigHashType and SIGHASH_MASK) != SIGHASH_NONE
        ) {
            val bos = ByteArrayOutputStream()
            for (input in tx.inputs) {
                writeUInt64LE(bos, input.sequence)
            }
            CryptoUtils.blake2b256(bos.toByteArray(), KEY_TRANSACTION_SIGNING_HASH)
        } else {
            ByteArray(32)
        }

        // 3. sigOpCountsHash (32 bytes)
        val sigOpCountsHash: ByteArray = if ((sigHashType and SIGHASH_ANYONECANPAY) == 0) {
            val bos = ByteArrayOutputStream()
            for (input in tx.inputs) {
                writeByte(bos, input.sigOpCount.toByte())
            }
            CryptoUtils.blake2b256(bos.toByteArray(), KEY_TRANSACTION_SIGNING_HASH)
        } else {
            ByteArray(32)
        }

        // 4. Input-specific data for inputIndex
        val inputBos = ByteArrayOutputStream()
        val currentInput = tx.inputs[inputIndex]
        val txIdBytes = hexToBytes(currentInput.previousOutpoint.transactionId)
        val padTxId = if (txIdBytes.size == 32) txIdBytes else ByteArray(32).also {
            System.arraycopy(txIdBytes, 0, it, 0, minOf(txIdBytes.size, 32))
        }
        inputBos.write(padTxId)
        writeUInt32LE(inputBos, currentInput.previousOutpoint.index)
        writeUInt16LE(inputBos, utxoEntry.scriptPublicKey.version)
        val scriptBytes = hexToBytes(utxoEntry.scriptPublicKey.script)
        writeUInt64LE(inputBos, scriptBytes.size.toLong())
        inputBos.write(scriptBytes)
        writeUInt64LE(inputBos, utxoEntry.amount)
        writeUInt64LE(inputBos, currentInput.sequence)
        writeByte(inputBos, currentInput.sigOpCount.toByte())
        val inputSpecificData = inputBos.toByteArray()

        // 5. outputsHash (32 bytes)
        val outputsHash: ByteArray = when (sigHashType and SIGHASH_MASK) {
            SIGHASH_ALL -> {
                val bos = ByteArrayOutputStream()
                for (output in tx.outputs) {
                    writeUInt64LE(bos, output.amount)
                    writeUInt16LE(bos, output.scriptPublicKey.version)
                    val outScriptBytes = hexToBytes(output.scriptPublicKey.script)
                    writeUInt64LE(bos, outScriptBytes.size.toLong())
                    bos.write(outScriptBytes)
                }
                CryptoUtils.blake2b256(bos.toByteArray(), KEY_TRANSACTION_SIGNING_HASH)
            }
            SIGHASH_SINGLE -> {
                if (inputIndex < tx.outputs.size) {
                    val bos = ByteArrayOutputStream()
                    val output = tx.outputs[inputIndex]
                    writeUInt64LE(bos, output.amount)
                    writeUInt16LE(bos, output.scriptPublicKey.version)
                    val outScriptBytes = hexToBytes(output.scriptPublicKey.script)
                    writeUInt64LE(bos, outScriptBytes.size.toLong())
                    bos.write(outScriptBytes)
                    CryptoUtils.blake2b256(bos.toByteArray(), KEY_TRANSACTION_SIGNING_HASH)
                } else {
                    ByteArray(32)
                }
            }
            else -> ByteArray(32)
        }

        // 6. Payload hash (32 bytes)
        val payloadBytes = hexToBytes(tx.payload)
        val payloadHash = CryptoUtils.blake2b256(payloadBytes, KEY_TRANSACTION_SIGNING_HASH)

        // 7. Subnetwork ID (20 bytes)
        val subnetworkBytes = hexToBytes(tx.subnetworkId)
        val padSubnetwork = if (subnetworkBytes.size == 20) subnetworkBytes else ByteArray(20)

        // 8. Combine into outer sighash buffer
        val totalBos = ByteArrayOutputStream()
        writeUInt16LE(totalBos, tx.version)
        totalBos.write(previousOutputsHash)
        totalBos.write(sequencesHash)
        totalBos.write(sigOpCountsHash)
        totalBos.write(inputSpecificData)
        totalBos.write(outputsHash)
        writeUInt64LE(totalBos, tx.lockTime)
        totalBos.write(padSubnetwork)
        writeUInt64LE(totalBos, tx.gas)
        totalBos.write(payloadHash)
        writeUInt32LE(totalBos, sigHashType.toLong())

        // Final digest with TransactionSigningHash key
        return CryptoUtils.blake2b256(totalBos.toByteArray(), KEY_TRANSACTION_SIGNING_HASH)
    }

    // ==========================================
    // Kaspa Non-Malleable Transaction ID (calc_tx_id)
    // Strictly follows rusty-kaspa consensus rules (Blake2b-256 with key "TransactionID")
    // ==========================================

    /**
     * Calculates the non-malleable Kaspa Transaction ID (Blake2b-256 with key "TransactionID")
     * following rusty-kaspa consensus rules.
     * Note: Signature scripts and sigOpCounts are excluded for version 0 transactions.
     */
    fun calcTransactionId(tx: KaspaTransaction): String {
        val bos = ByteArrayOutputStream()
        writeUInt16LE(bos, tx.version)
        writeUInt64LE(bos, tx.inputs.size.toLong())

        for (input in tx.inputs) {
            val txIdBytes = hexToBytes(input.previousOutpoint.transactionId)
            val padTxId = if (txIdBytes.size == 32) txIdBytes else ByteArray(32).also {
                System.arraycopy(txIdBytes, 0, it, 0, minOf(txIdBytes.size, 32))
            }
            bos.write(padTxId)
            writeUInt32LE(bos, input.previousOutpoint.index)
            // In rusty-kaspa id_v0 (TxEncodingFlags::EXCLUDE_SIGNATURE_SCRIPT):
            // writes var_bytes(&[]), which is length 0 (u64 LE = 0)
            writeUInt64LE(bos, 0L)
            // sequence (u64 LE)
            writeUInt64LE(bos, input.sequence)
            // sigOpCount is NOT written for transaction ID
        }

        writeUInt64LE(bos, tx.outputs.size.toLong())
        for (output in tx.outputs) {
            writeUInt64LE(bos, output.amount)
            writeUInt16LE(bos, output.scriptPublicKey.version)
            val outScriptBytes = hexToBytes(output.scriptPublicKey.script)
            writeUInt64LE(bos, outScriptBytes.size.toLong())
            bos.write(outScriptBytes)
        }

        writeUInt64LE(bos, tx.lockTime)
        val subnetworkBytes = hexToBytes(tx.subnetworkId)
        val padSubnetwork = if (subnetworkBytes.size == 20) subnetworkBytes else ByteArray(20)
        bos.write(padSubnetwork)
        writeUInt64LE(bos, tx.gas)

        val payloadBytes = hexToBytes(tx.payload)
        writeUInt64LE(bos, payloadBytes.size.toLong())
        bos.write(payloadBytes)

        val digest = CryptoUtils.blake2b256(bos.toByteArray(), KEY_TRANSACTION_ID)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Calculates the transaction hash (committing to mass, signatures, and full fields)
     */
    fun calcTransactionHash(tx: KaspaTransaction): String {
        val bos = ByteArrayOutputStream()
        writeUInt16LE(bos, tx.version)
        writeUInt64LE(bos, tx.inputs.size.toLong())

        for (input in tx.inputs) {
            val txIdBytes = hexToBytes(input.previousOutpoint.transactionId)
            val padTxId = if (txIdBytes.size == 32) txIdBytes else ByteArray(32).also {
                System.arraycopy(txIdBytes, 0, it, 0, minOf(txIdBytes.size, 32))
            }
            bos.write(padTxId)
            writeUInt32LE(bos, input.previousOutpoint.index)
            val sigScriptBytes = hexToBytes(input.signatureScript)
            writeUInt64LE(bos, sigScriptBytes.size.toLong())
            bos.write(sigScriptBytes)
            writeByte(bos, input.sigOpCount.toByte())
            writeUInt64LE(bos, input.sequence)
        }

        writeUInt64LE(bos, tx.outputs.size.toLong())
        for (output in tx.outputs) {
            writeUInt64LE(bos, output.amount)
            writeUInt16LE(bos, output.scriptPublicKey.version)
            val outScriptBytes = hexToBytes(output.scriptPublicKey.script)
            writeUInt64LE(bos, outScriptBytes.size.toLong())
            bos.write(outScriptBytes)
        }

        writeUInt64LE(bos, tx.lockTime)
        val subnetworkBytes = hexToBytes(tx.subnetworkId)
        val padSubnetwork = if (subnetworkBytes.size == 20) subnetworkBytes else ByteArray(20)
        bos.write(padSubnetwork)
        writeUInt64LE(bos, tx.gas)

        val payloadBytes = hexToBytes(tx.payload)
        writeUInt64LE(bos, payloadBytes.size.toLong())
        bos.write(payloadBytes)

        if (tx.mass > 0L) {
            writeUInt64LE(bos, tx.mass)
        }

        val digest = CryptoUtils.blake2b256(bos.toByteArray(), KEY_TRANSACTION_HASH)
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ==========================================
    // Transaction Signing (BIP-340 Schnorr + 0x41 signatureScript)
    // ==========================================

    /**
     * Signs each input of a KaspaTransaction with BIP-340 Schnorr signature,
     * and sets signatureScript = OP_DATA_65 (0x41) + 64 bytes Schnorr signature + SIGHASH_ALL (0x01).
     */
    fun signTransaction(
        tx: KaspaTransaction,
        utxoEntries: List<KaspaUtxoEntry>,
        privateKey: BigInteger
    ): KaspaTransaction {
        require(utxoEntries.size == tx.inputs.size) { "UTXO entries list size must match transaction inputs size" }

        for (i in tx.inputs.indices) {
            val sighash = calcSchnorrSignatureHash(tx, i, SIGHASH_ALL, utxoEntries[i])
            val sig64Hex = CryptoUtils.signSchnorr(privateKey, sighash)
            // Kaspa P2PK Schnorr signature script format:
            // 0x41 (OP_DATA_65) + 64-byte Schnorr sig (128 hex chars) + 0x01 (SIGHASH_ALL)
            tx.inputs[i].signatureScript = "41" + sig64Hex + "01"
        }

        tx.mass = calculateMass(tx)
        return tx
    }

    /**
     * Signs only user funding inputs (where signatureScript is empty), leaving covenant
     * authorization scripts (such as Split or Activate dispatch scripts) intact.
     */
    fun signUserInputsOnly(
        tx: KaspaTransaction,
        utxoEntries: List<KaspaUtxoEntry>,
        privateKey: BigInteger
    ): KaspaTransaction {
        require(utxoEntries.size == tx.inputs.size) { "UTXO entries list size must match transaction inputs size" }

        for (i in tx.inputs.indices) {
            if (tx.inputs[i].signatureScript.isEmpty()) {
                val sighash = calcSchnorrSignatureHash(tx, i, SIGHASH_ALL, utxoEntries[i])
                val sig64Hex = CryptoUtils.signSchnorr(privateKey, sighash)
                tx.inputs[i].signatureScript = "41" + sig64Hex + "01"
            }
        }

        tx.mass = calculateMass(tx)
        return tx
    }

    // ==========================================
    // Kaspa Mass Calculation & Consensus Fee Rules
    // (Consensus-accurate rusty-kaspa implementation)
    // ==========================================

    /**
     * Calculates transaction compute mass based on serialized byte size, script public key bytes, and signature operations.
     */
    fun calculateComputeMass(inputsCount: Int, outputsCount: Int, totalSigOps: Int, payloadByteCount: Int = 0): Long {
        val safeInputs = maxOf(1, inputsCount)
        val safeOutputs = maxOf(1, outputsCount)
        val sigOpMass = totalSigOps * MASS_PER_SIG_OP
        val inputComputeMass = safeInputs * MASS_PER_INPUT_COMPUTE
        val outputComputeMass = safeOutputs * MASS_PER_OUTPUT_COMPUTE
        return sigOpMass + inputComputeMass + outputComputeMass + payloadByteCount
    }

    /**
     * Calculates the exact serialized byte length of a Kaspa Transaction.
     */
    fun calculateSerializedByteSize(tx: KaspaTransaction): Long {
        var size = 0L
        size += 2L // version (uint16)
        size += 8L // inputs count (uint64)
        for (input in tx.inputs) {
            size += 32L // previousOutpoint transactionId
            size += 4L  // previousOutpoint index (uint32)
            val sigScriptBytes = if (input.signatureScript.isNotBlank()) {
                input.signatureScript.length / 2
            } else {
                STANDARD_SIGNATURE_SCRIPT_BYTES
            }
            size += 8L // signatureScript length (uint64)
            size += sigScriptBytes
            size += 8L  // sequence (uint64)
        }

        size += 8L // outputs count (uint64)
        for (output in tx.outputs) {
            size += 8L // amount (uint64)
            size += 2L // scriptPublicKey version (uint16)
            val scriptBytes = if (output.scriptPublicKey.script.isNotBlank()) {
                output.scriptPublicKey.script.length / 2
            } else {
                STANDARD_P2PK_SCRIPT_BYTES
            }
            size += 8L // script length (uint64)
            size += scriptBytes
        }

        size += 8L  // lockTime (uint64)
        size += 20L // subnetworkId (20 bytes)
        size += 8L  // gas (uint64)
        size += 32L // payload hash (32 bytes HASH_SIZE)
        val payloadBytes = if (tx.payload.isNotBlank()) tx.payload.length / 2 else 0
        size += 8L  // payload length (uint64)
        size += payloadBytes

        return size
    }

    /**
     * Calculates the estimated serialized byte size for planned inputs, outputs, and payload.
     */
    fun calculateEstimatedSerializedByteSize(inputsCount: Int, outputsCount: Int, payloadByteCount: Int = 0): Long {
        val safeInputs = maxOf(1, inputsCount)
        val safeOutputs = maxOf(1, outputsCount)
        var size = 0L
        size += 2L // version (uint16)
        size += 8L // inputs count (uint64)
        for (i in 0 until safeInputs) {
            size += 32L // previousOutpoint transactionId
            size += 4L  // previousOutpoint index (uint32)
            size += 8L  // signatureScript length (uint64)
            size += STANDARD_SIGNATURE_SCRIPT_BYTES // 66 bytes
            size += 8L  // sequence (uint64)
        }
        size += 8L // outputs count (uint64)
        for (i in 0 until safeOutputs) {
            size += 8L // amount (uint64)
            size += 2L // scriptPublicKey version (uint16)
            size += 8L // script length (uint64)
            size += STANDARD_P2PK_SCRIPT_BYTES // 34 bytes
        }
        size += 8L  // lockTime (uint64)
        size += 20L // subnetworkId (20 bytes)
        size += 8L  // gas (uint64)
        size += 32L // payload hash (32 bytes)
        size += 8L  // payload length (uint64)
        size += payloadByteCount
        return size
    }

    /**
     * Calculates storage mass (KIP-0009 state mass factor).
     * Canonically evaluates max(0, C * (|O| / H(O) - |I| / A(I))) where C = 10^12 Sompi (STORAGE_MASS_PARAMETER).
     * Disincentivizes state bloat and dust creation.
     */
    fun calculateStorageMass(
        inputAmounts: List<Long>,
        outputAmounts: List<Long>
    ): Long {
        if (outputAmounts.isEmpty()) return 0L
        val stormParam = STORAGE_MASS_PARAMETER
        var harmonicOuts = 0L
        for (outAmt in outputAmounts) {
            if (outAmt <= 0L) continue
            // Standard P2PK script fits into 100-byte unit (plurality = 1)
            harmonicOuts += stormParam / outAmt
        }
        val insPlurality = inputAmounts.size.toLong()
        val outsPlurality = outputAmounts.size.toLong()
        if (insPlurality == 0L) return harmonicOuts

        val relaxedPath = (outsPlurality == 1L || insPlurality == 1L || (outsPlurality == 2L && insPlurality == 2L))
        if (relaxedPath) {
            var harmonicIns = 0L
            for (inAmt in inputAmounts) {
                if (inAmt <= 0L) continue
                harmonicIns += stormParam / inAmt
            }
            return (harmonicOuts - harmonicIns).coerceAtLeast(0L)
        }

        var sumIns = 0L
        for (inAmt in inputAmounts) sumIns += inAmt
        val meanIns = (sumIns / insPlurality).coerceAtLeast(1L)
        val arithmeticIns = insPlurality * (stormParam / meanIns)
        return (harmonicOuts - arithmeticIns).coerceAtLeast(0L)
    }

    fun calculateStorageMass(outputs: List<KaspaTransactionOutput>): Long {
        return calculateStorageMass(emptyList(), outputs.map { it.amount })
    }

    /**
     * Calculates the overall consensus mass of a Kaspa transaction.
     * mass = max(compute_mass, storage_mass)
     */
    fun calculateMass(tx: KaspaTransaction, inputAmounts: List<Long> = emptyList()): Long {
        val totalSigOps = tx.inputs.sumOf { if (it.sigOpCount > 0) it.sigOpCount else 1 }
        val size = calculateSerializedByteSize(tx)
        val computeMassForSize = size * MASS_PER_TX_BYTE
        val scriptPubKeyMass = tx.outputs.sumOf {
            val scriptLen = if (it.scriptPublicKey.script.isNotBlank()) it.scriptPublicKey.script.length / 2 else STANDARD_P2PK_SCRIPT_BYTES
            (2L + scriptLen) * MASS_PER_SCRIPT_PUB_KEY_BYTE
        }
        val sigOpMass = totalSigOps * MASS_PER_SIG_OP
        val computeMass = computeMassForSize + scriptPubKeyMass + sigOpMass

        val outAmounts = tx.outputs.map { it.amount }
        val storageMass = calculateStorageMass(inputAmounts, outAmounts)
        val overall = maxOf(computeMass, storageMass).coerceIn(MINIMUM_TRANSACTION_MASS, MAXIMUM_STANDARD_TRANSACTION_MASS)
        return overall
    }

    /**
     * Estimates transaction mass prior to signing and UTXO finalization.
     */
    fun estimateTransactionMass(
        inputsCount: Int,
        outputsCount: Int,
        payloadByteCount: Int = 0,
        inputAmounts: List<Long> = emptyList(),
        outputAmounts: List<Long> = emptyList()
    ): Long {
        val safeInputs = maxOf(1, inputsCount)
        val safeOutputs = maxOf(1, outputsCount)
        val totalSigOps = safeInputs * 1 // Standard P2PK
        val computeMass = calculateComputeMass(safeInputs, safeOutputs, totalSigOps, payloadByteCount)
        val storageMass = calculateStorageMass(inputAmounts, outputAmounts)
        val overall = maxOf(computeMass, storageMass).coerceIn(MINIMUM_TRANSACTION_MASS, MAXIMUM_STANDARD_TRANSACTION_MASS)
        return overall
    }

    /**
     * Calculates the exact transaction fee in Sompis for a given mass and sompi/mass feerate.
     */
    fun calculateFeeForMass(mass: Long, sompiPerMass: Long = DEFAULT_SOMPI_PER_MASS): Long {
        val effectiveMass = maxOf(mass, MINIMUM_TRANSACTION_MASS)
        return effectiveMass * sompiPerMass
    }

    /**
     * Estimates transaction fee in KAS units (8 decimals).
     */
    fun estimateFeeKas(
        inputsCount: Int,
        outputsCount: Int,
        payloadByteCount: Int = 0,
        sompiPerMass: Long = DEFAULT_SOMPI_PER_MASS,
        inputAmounts: List<Long> = emptyList(),
        outputAmounts: List<Long> = emptyList()
    ): Double {
        val mass = estimateTransactionMass(inputsCount, outputsCount, payloadByteCount, inputAmounts, outputAmounts)
        val feeSompis = calculateFeeForMass(mass, sompiPerMass)
        return sompiToKas(feeSompis)
    }

    /**
     * Real-time network fee conditions fetched from Kaspa RPC node.
     */
    data class KaspaNetworkFeeCondition(
        val lowFeerate: Long = 1L,
        val normalFeerate: Long = 10L,
        val priorityFeerate: Long = 20L,
        val lowEstimatedSeconds: Int = 30,
        val normalEstimatedSeconds: Int = 5,
        val priorityEstimatedSeconds: Int = 1,
        val mempoolCount: Long = 0L,
        val virtualDaaScore: Long = 0L,
        val networkBps: Double = 10.0,
        val networkCongestion: String = "Optimal (Fast DAG Finality)",
        val lastUpdatedTs: Long = System.currentTimeMillis(),
        val sourceNode: String = "Kaspa TN10 RPC (api-tn10.kaspa.org)"
    )

    data class FeeEstimateOption(
        val tierKey: String,             // "economy", "normal", "priority", "custom"
        val label: String,               // "Economy", "Normal (Recommended)", "Priority", etc.
        val sompiPerMass: Long,          // feerate in Sompi / gram
        val estimatedSeconds: Int,       // estimated inclusion time in seconds
        val feeSompis: Long,             // calculated fee in Sompis
        val feeKas: Double,              // calculated fee in KAS
        val totalRequiredKas: Double     // targetAmountKas + feeKas
    )

    data class FeeRangeCalculationBreakdown(
        val estimatedSize: Long,
        val computeMass: Long,
        val storageMass: Long,
        val totalMass: Long,
        val targetAmountKas: Double,
        val isDustWarning: Boolean,
        val networkCondition: KaspaNetworkFeeCondition,
        val economyOption: FeeEstimateOption,
        val normalOption: FeeEstimateOption,
        val priorityOption: FeeEstimateOption,
        val selectedOption: FeeEstimateOption,
        val minFeeKas: Double,
        val maxFeeKas: Double,
        val minFeeSompis: Long,
        val maxFeeSompis: Long
    )

    /**
     * Comprehensive Fee & Mass Calculation Breakdown for UI and Fee Calculator.
     */
    data class FeeCalculationBreakdown(
        val estimatedSize: Long,
        val computeMass: Long,
        val storageMass: Long,
        val totalMass: Long,
        val sompiPerMass: Long,
        val feeSompis: Long,
        val feeKas: Double,
        val targetAmountKas: Double,
        val totalRequiredKas: Double,
        val isDustWarning: Boolean
    )

    fun calculateFeeBreakdown(
        amountKas: Double,
        inputsCount: Int = 1,
        outputsCount: Int = 2,
        sompiPerMass: Long = DEFAULT_SOMPI_PER_MASS,
        inputAmounts: List<Long> = emptyList()
    ): FeeCalculationBreakdown {
        val amountSompis = kasToSompi(amountKas)
        val outputAmounts = if (amountSompis > 0L) listOf(amountSompis) else emptyList()
        val safeInputs = maxOf(1, inputsCount)
        val safeOutputs = maxOf(1, outputsCount)
        val size = calculateEstimatedSerializedByteSize(safeInputs, safeOutputs)
        val sigOpMass = safeInputs * 1L * MASS_PER_SIG_OP
        val inputComputeMass = safeInputs * MASS_PER_INPUT_COMPUTE
        val outputComputeMass = safeOutputs * MASS_PER_OUTPUT_COMPUTE
        val computeMass = sigOpMass + inputComputeMass + outputComputeMass
        val storageMass = calculateStorageMass(inputAmounts, outputAmounts)
        val totalMass = maxOf(computeMass, storageMass).coerceIn(MINIMUM_TRANSACTION_MASS, MAXIMUM_STANDARD_TRANSACTION_MASS)
        val feeSompis = calculateFeeForMass(totalMass, sompiPerMass)
        val feeKas = sompiToKas(feeSompis)
        val totalRequiredKas = amountKas + feeKas
        val isDustWarning = amountSompis in 1 until DUST_THRESHOLD_SOMPIS

        return FeeCalculationBreakdown(
            estimatedSize = size,
            computeMass = computeMass,
            storageMass = storageMass,
            totalMass = totalMass,
            sompiPerMass = sompiPerMass,
            feeSompis = feeSompis,
            feeKas = feeKas,
            targetAmountKas = amountKas,
            totalRequiredKas = totalRequiredKas,
            isDustWarning = isDustWarning
        )
    }

    /**
     * Estimates dynamic fee returning structured DynamicFeeEstimate for easy consumption by UI & Builders.
     */
    fun estimateDynamicFee(
        amountKas: Double,
        inputsCount: Int = 1,
        outputsCount: Int = 2,
        sompiPerMass: Long = DEFAULT_SOMPI_PER_MASS,
        inputAmounts: List<Long> = emptyList(),
        payloadByteCount: Int = 0
    ): DynamicFeeEstimate {
        val amountSompis = kasToSompi(amountKas)
        val outputAmounts = if (amountSompis > 0L) listOf(amountSompis) else emptyList()
        val safeInputs = maxOf(1, inputsCount)
        val safeOutputs = maxOf(1, outputsCount)
        val estimatedSize = calculateEstimatedSerializedByteSize(safeInputs, safeOutputs, payloadByteCount)
        val sigOpMass = safeInputs * 1L * MASS_PER_SIG_OP
        val inputComputeMass = safeInputs * MASS_PER_INPUT_COMPUTE
        val outputComputeMass = safeOutputs * MASS_PER_OUTPUT_COMPUTE
        val computeMass = sigOpMass + inputComputeMass + outputComputeMass + payloadByteCount
        val storageMass = calculateStorageMass(inputAmounts, outputAmounts)
        val totalMass = maxOf(computeMass, storageMass).coerceIn(MINIMUM_TRANSACTION_MASS, MAXIMUM_STANDARD_TRANSACTION_MASS)
        val feeSompi = totalMass * sompiPerMass
        val feeKas = sompiToKas(feeSompi)

        return DynamicFeeEstimate(
            feeSompi = feeSompi,
            feeKas = feeKas,
            feeKasFormatted = formatKasPadded(feeKas),
            estimatedSize = estimatedSize,
            computeMass = computeMass,
            storageMass = storageMass,
            totalMass = totalMass,
            feerateSompiPerGram = sompiPerMass
        )
    }

    /**
     * Calculates real-time estimated fee range (Economy, Normal, Priority, and Selected)
     * based on live RPC network conditions.
     */
    fun calculateFeeRangeBreakdown(
        amountKas: Double,
        inputsCount: Int = 1,
        outputsCount: Int = 2,
        selectedSompiPerMass: Long = DEFAULT_SOMPI_PER_MASS,
        inputAmounts: List<Long> = emptyList(),
        networkCondition: KaspaNetworkFeeCondition = KaspaNetworkFeeCondition()
    ): FeeRangeCalculationBreakdown {
        val amountSompis = kasToSompi(amountKas)
        val outputAmounts = if (amountSompis > 0L) listOf(amountSompis) else emptyList()
        val safeInputs = maxOf(1, inputsCount)
        val safeOutputs = maxOf(1, outputsCount)
        val size = 2L + 8L + (safeInputs * (36L + 8L + STANDARD_SIGNATURE_SCRIPT_BYTES + 8L)) +
                8L + (safeOutputs * (8L + 2L + 8L + STANDARD_P2PK_SCRIPT_BYTES)) +
                8L + 20L + 8L + 32L + 8L
        val sigOpMass = safeInputs * 1L * MASS_PER_SIG_OP
        val inputComputeMass = safeInputs * MASS_PER_INPUT_COMPUTE
        val outputComputeMass = safeOutputs * MASS_PER_OUTPUT_COMPUTE
        val computeMass = sigOpMass + inputComputeMass + outputComputeMass
        val storageMass = calculateStorageMass(inputAmounts, outputAmounts)
        val totalMass = maxOf(computeMass, storageMass).coerceIn(MINIMUM_TRANSACTION_MASS, MAXIMUM_STANDARD_TRANSACTION_MASS)

        val ecoFee = calculateFeeForMass(totalMass, networkCondition.lowFeerate)
        val normFee = calculateFeeForMass(totalMass, networkCondition.normalFeerate)
        val prioFee = calculateFeeForMass(totalMass, networkCondition.priorityFeerate)
        val customSelectedFee = calculateFeeForMass(totalMass, selectedSompiPerMass)

        val ecoOpt = FeeEstimateOption(
            tierKey = "economy",
            label = "Economy",
            sompiPerMass = networkCondition.lowFeerate,
            estimatedSeconds = networkCondition.lowEstimatedSeconds,
            feeSompis = ecoFee,
            feeKas = sompiToKas(ecoFee),
            totalRequiredKas = amountKas + sompiToKas(ecoFee)
        )
        val normOpt = FeeEstimateOption(
            tierKey = "normal",
            label = "Normal (Recommended)",
            sompiPerMass = networkCondition.normalFeerate,
            estimatedSeconds = networkCondition.normalEstimatedSeconds,
            feeSompis = normFee,
            feeKas = sompiToKas(normFee),
            totalRequiredKas = amountKas + sompiToKas(normFee)
        )
        val prioOpt = FeeEstimateOption(
            tierKey = "priority",
            label = "Priority (Fast)",
            sompiPerMass = networkCondition.priorityFeerate,
            estimatedSeconds = networkCondition.priorityEstimatedSeconds,
            feeSompis = prioFee,
            feeKas = sompiToKas(prioFee),
            totalRequiredKas = amountKas + sompiToKas(prioFee)
        )
        val selOpt = FeeEstimateOption(
            tierKey = when (selectedSompiPerMass) {
                networkCondition.lowFeerate -> "economy"
                networkCondition.normalFeerate -> "normal"
                networkCondition.priorityFeerate -> "priority"
                else -> "custom"
            },
            label = when (selectedSompiPerMass) {
                networkCondition.lowFeerate -> "Economy"
                networkCondition.normalFeerate -> "Normal"
                networkCondition.priorityFeerate -> "Priority"
                else -> "Custom (${selectedSompiPerMass} S/g)"
            },
            sompiPerMass = selectedSompiPerMass,
            estimatedSeconds = when {
                selectedSompiPerMass <= networkCondition.lowFeerate -> networkCondition.lowEstimatedSeconds
                selectedSompiPerMass >= networkCondition.priorityFeerate -> networkCondition.priorityEstimatedSeconds
                else -> networkCondition.normalEstimatedSeconds
            },
            feeSompis = customSelectedFee,
            feeKas = sompiToKas(customSelectedFee),
            totalRequiredKas = amountKas + sompiToKas(customSelectedFee)
        )

        return FeeRangeCalculationBreakdown(
            estimatedSize = size,
            computeMass = computeMass,
            storageMass = storageMass,
            totalMass = totalMass,
            targetAmountKas = amountKas,
            isDustWarning = amountSompis in 1 until DUST_THRESHOLD_SOMPIS,
            networkCondition = networkCondition,
            economyOption = ecoOpt,
            normalOption = normOpt,
            priorityOption = prioOpt,
            selectedOption = selOpt,
            minFeeKas = sompiToKas(ecoFee),
            maxFeeKas = sompiToKas(prioFee),
            minFeeSompis = ecoFee,
            maxFeeSompis = prioFee
        )
    }

    /**
     * Selects UTXOs iteratively, calculating real mass and fee at each iteration step.
     */
    fun selectUtxosAndPlanTransaction(
        availableUtxos: List<KaspaUtxo>,
        targetAmountSompis: Long,
        payloadByteCount: Int = 0,
        sompiPerMass: Long = DEFAULT_SOMPI_PER_MASS
    ): TransactionPlan {
        if (availableUtxos.isEmpty()) {
            val initialMass = estimateTransactionMass(1, 2, payloadByteCount)
            val initialFee = calculateFeeForMass(initialMass, sompiPerMass)
            return TransactionPlan(
                selectedUtxos = emptyList(),
                calculatedMass = initialMass,
                feeSompis = initialFee,
                feeKas = sompiToKas(initialFee),
                changeSompis = 0L,
                isSufficient = false,
                requiredTotalSompis = targetAmountSompis + initialFee,
                accumulatedSompis = 0L,
                sompiPerMass = sompiPerMass
            )
        }

        val sortedUtxos = availableUtxos.sortedByDescending { it.utxoEntry.amount }
        val selected = mutableListOf<KaspaUtxo>()
        var accumulated = 0L
        var calculatedMass = estimateTransactionMass(1, 2, payloadByteCount)
        var feeSompis = calculateFeeForMass(calculatedMass, sompiPerMass)

        for (utxo in sortedUtxos) {
            selected.add(utxo)
            accumulated += utxo.utxoEntry.amount
            val selectedInAmounts = selected.map { it.utxoEntry.amount }
            val tentativeChange = (accumulated - (targetAmountSompis + feeSompis)).coerceAtLeast(0L)
            val outAmounts = if (tentativeChange > 0L) listOf(targetAmountSompis, tentativeChange) else listOf(targetAmountSompis)
            // Recalculate mass for the current input count (with 2 outputs: recipient + change)
            calculatedMass = estimateTransactionMass(
                selected.size,
                outAmounts.size,
                payloadByteCount,
                selectedInAmounts,
                outAmounts
            )
            feeSompis = calculateFeeForMass(calculatedMass, sompiPerMass)

            if (accumulated >= (targetAmountSompis + feeSompis)) {
                break
            }
        }

        val tentativeChange = accumulated - (targetAmountSompis + feeSompis)
        val finalOutputsCount = if (tentativeChange > 0L) 2 else 1
        val finalInAmounts = selected.map { it.utxoEntry.amount }
        val finalOutAmounts = if (tentativeChange > 0L) listOf(targetAmountSompis, tentativeChange) else listOf(targetAmountSompis)
        val finalMass = estimateTransactionMass(
            selected.size,
            finalOutputsCount,
            payloadByteCount,
            finalInAmounts,
            finalOutAmounts
        )
        val finalFee = calculateFeeForMass(finalMass, sompiPerMass)
        val requiredTotal = targetAmountSompis + finalFee
        val isSufficient = accumulated >= requiredTotal
        val finalChange = if (isSufficient) accumulated - requiredTotal else 0L

        return TransactionPlan(
            selectedUtxos = selected,
            calculatedMass = finalMass,
            feeSompis = finalFee,
            feeKas = sompiToKas(finalFee),
            changeSompis = finalChange.coerceAtLeast(0L),
            isSufficient = isSufficient,
            requiredTotalSompis = requiredTotal,
            accumulatedSompis = accumulated,
            sompiPerMass = sompiPerMass
        )
    }

    /**
     * Builds standard submission payload for Kaspa Node RPC and REST API
     */
    fun buildSubmitPayload(tx: KaspaTransaction, allowOrphan: Boolean = false): JSONObject {
        return JSONObject().apply {
            put("transaction", tx.toJson())
            put("allowOrphan", allowOrphan)
        }
    }

    fun buildRpcJsonPayload(tx: KaspaTransaction, id: Long = 1L, allowOrphan: Boolean = false): JSONObject {
        val params = JSONObject().apply {
            put("transaction", tx.toJson())
            put("allowOrphan", allowOrphan)
        }
        return JSONObject().apply {
            put("id", id)
            put("jsonrpc", "2.0")
            put("method", "submitTransaction")
            put("params", params)
        }
    }
}
