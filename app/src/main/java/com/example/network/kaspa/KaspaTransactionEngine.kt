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
    val KEY_PERSONAL_MESSAGE_HASH: ByteArray = "PersonalMessageSigningHash".toByteArray(Charsets.UTF_8)

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
            put("script", script)
        }

        companion object {
            fun fromJson(json: JSONObject): KaspaScriptPublicKey {
                val version = json.optInt("version", 0)
                val script = json.optString("script", "")
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
                    KaspaScriptPublicKey(0, json.optString("script_public_key", ""))
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
    )

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

    data class KaspaTransactionOutput(
        val amount: Long,
        val scriptPublicKey: KaspaScriptPublicKey
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("amount", amount)
            put("scriptPublicKey", scriptPublicKey.toJson())
        }
    }

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
        fun calculateComputeMass(): Long {
            // Rusty-kaspa compute mass formula: inputs * 1000 + outputs * 1000 + payload
            val baseMass = (inputs.size * 1000L) + (outputs.size * 1000L) + (payload.length / 2)
            return baseMass.coerceAtLeast(1000L)
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
            put("mass", if (mass > 0L) mass else calculateComputeMass())
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
        writeByte(totalBos, sigHashType.toByte())

        // Final digest with TransactionSigningHash key
        return CryptoUtils.blake2b256(totalBos.toByteArray(), KEY_TRANSACTION_SIGNING_HASH)
    }

    // ==========================================
    // Kaspa Non-Malleable Transaction ID (calc_tx_id)
    // ==========================================

    /**
     * Calculates the non-malleable Kaspa Transaction ID (Blake2b-256 with key "TransactionHash")
     * following rusty-kaspa consensus rules.
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
            writeUInt64LE(bos, input.sequence)
            writeByte(bos, input.sigOpCount.toByte())
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

        tx.mass = tx.calculateComputeMass()
        return tx
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
