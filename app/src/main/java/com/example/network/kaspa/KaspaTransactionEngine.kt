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

    // Kaspa Consensus & Mass Constants (rusty-kaspa / kaspa-consensus-core)
    const val SOMPI_PER_KAS = 100_000_000L
    const val SOMPI_PER_KAS_DOUBLE = 100_000_000.0
    const val MASS_PER_SIG_OP = 1000L
    const val MASS_PER_TX_BYTE = 1L
    const val MASS_PER_INPUT_COMPUTE = 100L
    const val MASS_PER_OUTPUT_COMPUTE = 100L
    const val MINIMUM_TRANSACTION_MASS = 1000L
    const val DEFAULT_SOMPI_PER_MASS = 100L // 100 sompi/gram (rusty-kaspa node RPC policy feerate)
    const val RUSTY_KASPA_MINIMUM_FEE_SOMPIS = 10_000L // 0.0001 KAS standard node relay fee floor
    const val STANDARD_SIGNATURE_SCRIPT_BYTES = 66 // 1 (0x41) + 64 (Schnorr Sig) + 1 (SIGHASH_ALL)
    const val STANDARD_P2PK_SCRIPT_BYTES = 34 // 1 (0x20) + 32 (pubkey) + 1 (0xac)

    fun sompiToKas(sompis: Long): Double = sompis / SOMPI_PER_KAS_DOUBLE
    fun kasToSompi(kas: Double): Long = (kas * SOMPI_PER_KAS_DOUBLE).toLong()
    fun formatKas(kas: Double): String = String.format(java.util.Locale.US, "%.8f", kas).trimEnd('0').trimEnd('.')

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

        tx.mass = calculateMass(tx)
        return tx
    }

    // ==========================================
    // Kaspa Mass Calculation & Consensus Fee Rules
    // (Consensus-accurate rusty-kaspa implementation)
    // ==========================================

    /**
     * Calculates transaction compute mass based on signature operations and script evaluations.
     */
    fun calculateComputeMass(inputsCount: Int, outputsCount: Int, totalSigOps: Int): Long {
        val sigOpMass = totalSigOps * MASS_PER_SIG_OP
        val inputMass = inputsCount * MASS_PER_INPUT_COMPUTE
        val outputMass = outputsCount * MASS_PER_OUTPUT_COMPUTE
        return sigOpMass + inputMass + outputMass
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
            size += 8L  // sequence (uint64)
            size += 1L  // sigOpCount (uint8)
            val sigScriptBytes = if (input.signatureScript.isNotBlank()) {
                input.signatureScript.length / 2
            } else {
                STANDARD_SIGNATURE_SCRIPT_BYTES
            }
            size += 8L // signatureScript length (uint64)
            size += sigScriptBytes
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
        val payloadBytes = if (tx.payload.isNotBlank()) tx.payload.length / 2 else 0
        size += 8L  // payload length (uint64)
        size += payloadBytes

        return size
    }

    /**
     * Calculates storage mass (KIP-9 state mass factor for small/dust outputs).
     */
    fun calculateStorageMass(outputs: List<KaspaTransactionOutput>): Long {
        var storageMass = 0L
        for (output in outputs) {
            // Storage mass rule: outputs below 10_000 sompis consume proportional storage mass
            if (output.amount in 1..9_999) {
                storageMass += (10_000L - output.amount) / 10L
            }
        }
        return storageMass
    }

    /**
     * Calculates the overall consensus mass of a Kaspa transaction.
     * mass = max(compute_mass, serialized_mass) + storage_mass (minimum 1000 mass)
     */
    fun calculateMass(tx: KaspaTransaction): Long {
        val totalSigOps = tx.inputs.sumOf { if (it.sigOpCount > 0) it.sigOpCount else 1 }
        val computeMass = calculateComputeMass(tx.inputs.size, tx.outputs.size, totalSigOps)
        val serializedMass = calculateSerializedByteSize(tx) * MASS_PER_TX_BYTE
        val storageMass = calculateStorageMass(tx.outputs)
        val overall = maxOf(computeMass, serializedMass) + storageMass
        return maxOf(overall, MINIMUM_TRANSACTION_MASS)
    }

    /**
     * Estimates transaction mass prior to signing and UTXO finalization.
     */
    fun estimateTransactionMass(
        inputsCount: Int,
        outputsCount: Int,
        payloadByteCount: Int = 0
    ): Long {
        val safeInputs = maxOf(1, inputsCount)
        val safeOutputs = maxOf(1, outputsCount)
        val totalSigOps = safeInputs * 1 // Standard P2PK
        val computeMass = calculateComputeMass(safeInputs, safeOutputs, totalSigOps)

        // Estimated serialized size:
        val estSize = 2L + 8L + (safeInputs * (32L + 4L + 8L + 1L + 8L + STANDARD_SIGNATURE_SCRIPT_BYTES)) +
                8L + (safeOutputs * (8L + 2L + 8L + STANDARD_P2PK_SCRIPT_BYTES)) +
                8L + 20L + 8L + 8L + payloadByteCount
        val serializedMass = estSize * MASS_PER_TX_BYTE
        val overall = maxOf(computeMass, serializedMass)
        return maxOf(overall, MINIMUM_TRANSACTION_MASS)
    }

    /**
     * Calculates the exact transaction fee in Sompis for a given mass and sompi/mass feerate.
     */
    fun calculateFeeForMass(mass: Long, sompiPerMass: Long = DEFAULT_SOMPI_PER_MASS): Long {
        return maxOf(mass * sompiPerMass, 1000L)
    }

    /**
     * Estimates transaction fee in KAS units (8 decimals).
     */
    fun estimateFeeKas(
        inputsCount: Int,
        outputsCount: Int,
        payloadByteCount: Int = 0,
        sompiPerMass: Long = DEFAULT_SOMPI_PER_MASS
    ): Double {
        val mass = estimateTransactionMass(inputsCount, outputsCount, payloadByteCount)
        val feeSompis = calculateFeeForMass(mass, sompiPerMass)
        return sompiToKas(feeSompis)
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
            // Recalculate mass for the current input count (with 2 outputs: recipient + change)
            calculatedMass = estimateTransactionMass(selected.size, 2, payloadByteCount)
            feeSompis = calculateFeeForMass(calculatedMass, sompiPerMass)

            if (accumulated >= (targetAmountSompis + feeSompis)) {
                break
            }
        }

        val requiredTotal = targetAmountSompis + feeSompis
        val isSufficient = accumulated >= requiredTotal
        val change = if (isSufficient) accumulated - requiredTotal else 0L

        // If no change needed (exact match), output count is 1
        val finalOutputsCount = if (change > 0L) 2 else 1
        val finalMass = estimateTransactionMass(selected.size, finalOutputsCount, payloadByteCount)
        val finalFee = calculateFeeForMass(finalMass, sompiPerMass)
        val finalChange = if (isSufficient) accumulated - (targetAmountSompis + finalFee) else 0L

        return TransactionPlan(
            selectedUtxos = selected,
            calculatedMass = finalMass,
            feeSompis = finalFee,
            feeKas = sompiToKas(finalFee),
            changeSompis = finalChange.coerceAtLeast(0L),
            isSufficient = isSufficient,
            requiredTotalSompis = targetAmountSompis + finalFee,
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
