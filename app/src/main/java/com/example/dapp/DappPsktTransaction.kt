package com.example.dapp

import com.example.network.CryptoUtils
import com.example.network.kaspa.KaspaTransactionEngine
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger

internal object DappPsktTransaction {
    private const val MAX_TRANSACTION_JSON_CHARS = 256 * 1024
    private const val MAX_INPUTS = 256
    private const val MAX_OUTPUTS = 256

    data class SignInput(val index: Int, val sighashType: Int, val redeemScript: String = "")

    data class Prepared(
        val originalJson: JSONObject,
        val transaction: KaspaTransactionEngine.KaspaTransaction,
        val utxoEntries: List<KaspaTransactionEngine.KaspaUtxoEntry>,
        val signInputs: List<SignInput>,
        val inputSumSompi: Long,
        val outputSumSompi: Long,
        val feeSompi: Long,
        val redeemScript: String = "",
        val walletPublicKeyHex: String = ""
    ) {
        val feeKas: String
            get() = KaspaTransactionEngine.formatKas(KaspaTransactionEngine.sompiToKas(feeSompi))
    }

    fun prepareForSigning(
        txJsonString: String,
        signInputsJson: JSONArray,
        walletPublicKeyHex: String,
        isPolicy: Boolean = false,
        redeemScript: String = ""
    ): Prepared {
        require(walletPublicKeyHex.matches(Regex("(?i)[0-9a-f]{64}"))) {
            "Invalid active wallet public key"
        }
        require(signInputsJson.length() in 1..MAX_INPUTS) { "Select at least one input to sign" }
        val requests = (0 until signInputsJson.length()).map { index ->
            val numOrObj = signInputsJson.opt(index)
            val (inputIndex, sighashType, redeemScript) = when (numOrObj) {
                is JSONObject -> {
                    val idx = numOrObj.optInt("index", numOrObj.optInt("inputIndex", -1))
                    val shType = if (numOrObj.has("sighashType")) numOrObj.optInt("sighashType", KaspaTransactionEngine.SIGHASH_ALL) else KaspaTransactionEngine.SIGHASH_ALL
                    val rScript = numOrObj.optString("redeemScript", numOrObj.optString("redeem_script", ""))
                    Triple(idx, shType, rScript)
                }
                is Number -> {
                    Triple(numOrObj.toInt(), KaspaTransactionEngine.SIGHASH_ALL, "")
                }
                is String -> {
                    Triple(numOrObj.toIntOrNull() ?: -1, KaspaTransactionEngine.SIGHASH_ALL, "")
                }
                else -> throw IllegalArgumentException("Invalid signInputs entry")
            }
            require(sighashType == KaspaTransactionEngine.SIGHASH_ALL) {
                "Only SIGHASH_ALL (1) is supported"
            }
            SignInput(inputIndex, sighashType, redeemScript)
        }
        require(requests.map { it.index }.distinct().size == requests.size) {
            "Duplicate input index requested"
        }
        val prepared = parseAndValidate(txJsonString, requests, redeemScript).copy(
            walletPublicKeyHex = walletPublicKeyHex.lowercase()
        )
        val expectedScript = "20" + walletPublicKeyHex.lowercase() + "ac"
        for (request in requests) {
            require(request.index in prepared.transaction.inputs.indices) {
                "Input index is out of range"
            }
            val input = prepared.transaction.inputs[request.index]
            require(input.signatureScript.isBlank()) {
                "Refusing to overwrite an existing signature script"
            }
            val utxoScript = prepared.utxoEntries[request.index].scriptPublicKey.script
                .replace(" ", "").lowercase()
            val pubHex = walletPublicKeyHex.lowercase()

            val utxo = prepared.utxoEntries[request.index]
            require(utxo.amount > 0L) {
                "Input ${request.index} has an invalid or missing UTXO amount"
            }

            // Only sign standard version-0 P2PK outputs whose complete locking script
            // exactly commits to this wallet key. Substring matches are not ownership proofs.
            if (utxo.scriptPublicKey.version == 0 && utxoScript == expectedScript) {
                continue
            }

            val p2shHash = Regex("aa20([0-9a-f]{64})87").matchEntire(utxoScript)?.groupValues?.get(1)
            if (utxo.scriptPublicKey.version == 0 && p2shHash != null) {
                val redeem = request.redeemScript.takeIf { it.isNotBlank() }
                    ?: prepared.redeemScript.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("P2SH input ${request.index} requires its redeemScript")
                require(redeem.matches(Regex("(?i)([0-9a-f]{2})+"))) {
                    "P2SH redeemScript for input ${request.index} is malformed"
                }
                val redeemHex = redeem.lowercase()
                val redeemBytes = hexToBytes(redeemHex)
                val actualHash = CryptoUtils.blake2b256(redeemBytes)
                    .joinToString("") { "%02x".format(it) }
                require(actualHash.equals(p2shHash, ignoreCase = true)) {
                    "P2SH redeemScript does not match input ${request.index} locking script"
                }
                // Until full script execution is available in the wallet, only support
                // the exact single-key P2PK redeem script. Never infer ownership because
                // the active key merely appears somewhere in an arbitrary script.
                require(redeemHex == expectedScript) {
                    "P2SH input ${request.index} is not a supported single-key P2PK deposit for the active wallet key"
                }
                continue
            }

            throw IllegalArgumentException(
                "Input ${request.index} is not an exact supported P2PK/P2SH-P2PK deposit for the active wallet key " +
                    "(scriptVersion=${utxo.scriptPublicKey.version}, amountSompi=${utxo.amount})"
            )
        }
        return prepared
    }

    fun inspectForSubmission(txJsonString: String): Prepared {
        val prepared = parseAndValidate(txJsonString, emptyList(), "")
        prepared.transaction.inputs.forEachIndexed { index, input ->
            require(input.signatureScript.length % 2 == 0 &&
                input.signatureScript.matches(Regex("[0-9a-fA-F]*"))) {
                "Input $index has a malformed signature script"
            }
            val lockingScript = prepared.utxoEntries[index].scriptPublicKey.script
                .replace(" ", "").lowercase()
            if (lockingScript.matches(Regex("20[0-9a-f]{64}ac"))) {
                require(input.signatureScript.isNotBlank()) {
                    "Input $index is a P2PK input without a signature"
                }
            }
        }
        return prepared
    }

    fun sign(prepared: Prepared, privateKey: BigInteger): String {
        val signedRoot = JSONObject(prepared.originalJson.toString())
        val txRoot = signedRoot.optJSONObject("transaction") ?: signedRoot
        val inputArray = txRoot.optJSONArray("inputs")
            ?: throw IllegalArgumentException("Transaction inputs are missing")
        for (request in prepared.signInputs) {
            val input = prepared.transaction.inputs[request.index]
            val utxoEntry = prepared.utxoEntries[request.index]
            
            val sighash = KaspaTransactionEngine.calcSchnorrSignatureHash(
                prepared.transaction,
                request.index,
                request.sighashType,
                utxoEntry
            )
            val signature = CryptoUtils.signSchnorr(privateKey, sighash)
            val walletPublicKey = hexToBytes(prepared.walletPublicKeyHex)
            require(walletPublicKey.size == 32 &&
                CryptoUtils.verifySchnorrSignature(signature, sighash, walletPublicKey)
            ) {
                "Input ${request.index} signature failed local Schnorr verification for the active wallet key"
            }
            val lockingScript = utxoEntry.scriptPublicKey.script.replace(" ", "").lowercase()
            val redeem = if (Regex("aa20[0-9a-f]{64}87").matches(lockingScript)) {
                val redeemScript = request.redeemScript.takeIf { it.isNotBlank() }
                    ?: prepared.redeemScript.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("P2SH redeemScript is required for input ${request.index}")
                pushDataScript(redeemScript.lowercase())
            } else {
                ""
            }
            val signatureScript = "41" + signature + "01" + redeem
            input.signatureScript = signatureScript
            val originalInput = inputArray.optJSONObject(request.index)
                ?: throw IllegalArgumentException("Transaction input is malformed")
            if (originalInput.has("signature_script") && !originalInput.has("signatureScript")) {
                originalInput.put("signature_script", signatureScript)
            } else {
                originalInput.put("signatureScript", signatureScript)
            }
        }
        return signedRoot.toString()
    }

    fun signAndFormat(prepared: Prepared, privateKey: BigInteger): JSONObject {
        val signedJsonString = sign(prepared, privateKey)
        val inputsArray = JSONArray()
        for (request in prepared.signInputs) {
            val input = prepared.transaction.inputs[request.index]
            inputsArray.put(
                JSONObject()
                    .put("index", request.index)
                    .put("signatureScript", input.signatureScript)
            )
        }
        return JSONObject()
            .put("inputs", inputsArray)
            .put("psktTransactionJson", signedJsonString)
            .put("signedTxJson", signedJsonString)
    }

    private fun parseAndValidate(txJsonString: String, signInputs: List<SignInput>, redeemScript: String): Prepared {
        require(txJsonString.isNotBlank() && txJsonString.length <= MAX_TRANSACTION_JSON_CHARS) {
            "Transaction JSON is empty or too large"
        }
        val root = try {
            JSONObject(txJsonString)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid transaction JSON")
        }
        val txRoot = root.optJSONObject("transaction") ?: root
        val inputArray = txRoot.optJSONArray("inputs")
            ?: throw IllegalArgumentException("Transaction inputs are missing")
        val outputArray = txRoot.optJSONArray("outputs")
            ?: throw IllegalArgumentException("Transaction outputs are missing")
        require(inputArray.length() in 1..MAX_INPUTS) { "Invalid input count" }
        require(outputArray.length() in 1..MAX_OUTPUTS) { "Invalid output count" }

        val utxoEntries = ArrayList<KaspaTransactionEngine.KaspaUtxoEntry>(inputArray.length())
        val inputs = ArrayList<KaspaTransactionEngine.KaspaTransactionInput>(inputArray.length())
        val seenOutpoints = HashSet<String>()
        var inputSum = 0L
        for (index in 0 until inputArray.length()) {
            val item = inputArray.optJSONObject(index)
                ?: throw IllegalArgumentException("Transaction input $index is malformed")

            val outpointRaw = item.opt("previousOutpoint")
                ?: item.opt("previous_outpoint")
                ?: item.opt("outpoint")
                ?: item.opt("prevOutpoint")
                ?: item.opt("prev_outpoint")

            var parsedTxId = ""
            var parsedOutIndex = -1L

            if (outpointRaw is String) {
                val str = outpointRaw.trim()
                if (str.contains(":") || str.contains("/")) {
                    val delimiter = if (str.contains(":")) ":" else "/"
                    parsedTxId = str.substringBefore(delimiter).trim()
                    parsedOutIndex = str.substringAfter(delimiter).trim().toLongOrNull() ?: -1L
                } else if (str.matches(Regex("(?i)[0-9a-f]{64}"))) {
                    parsedTxId = str
                }
            }

            val outpointObj = when (outpointRaw) {
                is JSONObject -> outpointRaw
                else -> item
            }

            if (parsedTxId.isBlank()) {
                parsedTxId = outpointObj.optString("transactionId", 
                    outpointObj.optString("transaction_id", 
                    outpointObj.optString("txId", 
                    outpointObj.optString("tx_id", 
                    outpointObj.optString("txid", 
                    outpointObj.optString("hash",
                    outpointObj.optString("tx_hash", 
                    outpointObj.optString("id",
                    item.optString("transactionId",
                    item.optString("transaction_id",
                    item.optString("txId",
                    item.optString("tx_id",
                    item.optString("txid",
                    item.optString("hash", ""))))))))))))))
            }

            if (parsedOutIndex < 0L) {
                parsedOutIndex = outpointObj.optLong("index", 
                    outpointObj.optLong("outputIndex", 
                    outpointObj.optLong("output_index",
                    outpointObj.optLong("outpointIndex",
                    outpointObj.optLong("outpoint_index",
                    outpointObj.optLong("vout", 
                    outpointObj.optLong("n",
                    item.optLong("index",
                    item.optLong("outputIndex",
                    item.optLong("output_index",
                    item.optLong("vout", -1L)))))))))))
            }

            val txId = parsedTxId
            val outIndex = parsedOutIndex
            require(txId.matches(Regex("(?i)[0-9a-f]{64}"))) { "Input $index is missing its previous outpoint or has an invalid transaction id" }
            require(outIndex >= 0L && outIndex <= 0xffffffffL) { "Input $index has an invalid outpoint index" }
            require(seenOutpoints.add("${txId.lowercase()}:$outIndex")) {
                "Transaction contains duplicate outpoints"
            }
            val utxoJson = item.optJSONObject("utxoEntry")
                ?: item.optJSONObject("utxo_entry")
                ?: item.optJSONObject("utxo")
                ?: item.optJSONObject("entry")
                ?: txRoot.optJSONArray("utxoEntries")?.optJSONObject(index)
                ?: root.optJSONArray("utxoEntries")?.optJSONObject(index)
                ?: txRoot.optJSONArray("utxos")?.optJSONObject(index)
                ?: root.optJSONArray("utxos")?.optJSONObject(index)
                ?: item // Fallback: check if UTXO fields are at the top level
            val utxo = KaspaTransactionEngine.KaspaUtxoEntry.fromJson(utxoJson)
            require(utxo.amount > 0L) { "Input $index has an invalid UTXO amount" }
            require(utxo.scriptPublicKey.script.matches(Regex("(?i)([0-9a-f]{2})+"))) {
                "Input $index has an invalid UTXO script"
            }
            inputSum = Math.addExact(inputSum, utxo.amount)
            utxoEntries += utxo
            inputs += KaspaTransactionEngine.KaspaTransactionInput(
                previousOutpoint = KaspaTransactionEngine.KaspaOutpoint(txId.lowercase(), outIndex),
                signatureScript = item.optString("signatureScript", item.optString("signature_script", "")),
                sequence = item.optLong("sequence", 0L),
                sigOpCount = item.optInt("sigOpCount", item.optInt("sig_op_count", 1))
            )
        }

        val outputs = ArrayList<KaspaTransactionEngine.KaspaTransactionOutput>(outputArray.length())
        var outputSum = 0L
        for (index in 0 until outputArray.length()) {
            val item = outputArray.optJSONObject(index)
                ?: throw IllegalArgumentException("Transaction output $index is malformed")
            val amount = sequenceOf("value", "amount")
                .mapNotNull { key -> if (item.has(key)) item.optString(key).trim().toLongOrNull() else null }
                .firstOrNull() ?: -1L
            require(amount >= 0L) { "Output $index has an invalid amount" }
            val script = parseOutputScriptPublicKey(item, index)
            require(script.script.matches(Regex("(?i)([0-9a-f]{2})+"))) {
                "Output $index has an invalid script"
            }
            outputSum = Math.addExact(outputSum, amount)
            val covenantJson = item.optJSONObject("covenant")
            val covenant = covenantJson?.let {
                KaspaTransactionEngine.KaspaCovenantBinding(
                    authorizingInput = it.optInt("authorizingInput", it.optInt("authorizing_input", 0)),
                    covenantId = it.optString("covenantId", it.optString("covenant_id", ""))
                )
            }
            outputs += KaspaTransactionEngine.KaspaTransactionOutput(amount, script, covenant)
        }
        require(inputSum >= outputSum) { "Transaction outputs exceed the declared input value" }
        val fee = inputSum - outputSum
        require(fee >= 0L) {
            "Transaction outputs ($outputSum sompi) exceed inputs ($inputSum sompi)"
        }

        val tx = KaspaTransactionEngine.KaspaTransaction(
            version = txRoot.optInt("version", 0),
            inputs = inputs,
            outputs = outputs,
            lockTime = txRoot.optLong("lockTime", txRoot.optLong("lock_time", 0L)),
            subnetworkId = txRoot.optString("subnetworkId", txRoot.optString("subnetwork_id", KaspaTransactionEngine.DEFAULT_SUBNETWORK_ID)),
            gas = txRoot.optLong("gas", 0L),
            payload = txRoot.optString("payload", ""),
            mass = txRoot.optLong("mass", 0L)
        )
        val calculatedMass = KaspaTransactionEngine.calculateMass(tx, utxoEntries.map { it.amount })
        require(calculatedMass <= KaspaTransactionEngine.MAXIMUM_STANDARD_TRANSACTION_MASS) {
            "Transaction mass exceeds the standard mempool limit"
        }
        return Prepared(root, tx, utxoEntries, signInputs, inputSum, outputSum, fee, redeemScript)
    }

    private fun parseOutputScriptPublicKey(
        output: JSONObject,
        index: Int
    ): KaspaTransactionEngine.KaspaScriptPublicKey {
        val nested = output.optJSONObject("scriptPublicKey")
            ?: output.optJSONObject("script_public_key")
        if (nested != null) {
            return KaspaTransactionEngine.KaspaScriptPublicKey.fromJson(nested)
        }

        // Some PSKT producers flatten the script public key to its hex script.
        // Accept only an explicit script field; never derive one from an address or input.
        val flatScript = sequenceOf(
            "scriptPublicKey",
            "script_public_key",
            "script",
            "scriptPublicKeyHex",
            "script_public_key_hex"
        ).mapNotNull { key ->
            if (!output.has(key) || output.isNull(key)) null
            else output.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
        }.firstOrNull()

        if (flatScript != null) {
            val version = output.optInt(
                "scriptPublicKeyVersion",
                output.optInt("script_public_key_version", output.optInt("version", 0))
            )
            return KaspaTransactionEngine.KaspaScriptPublicKey(version, flatScript)
        }

        throw IllegalArgumentException(
            "Output $index is missing its script public key (expected scriptPublicKey or script_public_key object, or an explicit script hex field)"
        )
    }

    private fun hexToBytes(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun pushDataScript(dataHex: String): String {
        val byteLength = dataHex.length / 2
        val prefix = when {
            byteLength <= 75 -> "%02x".format(byteLength)
            byteLength <= 255 -> "4c" + "%02x".format(byteLength)
            byteLength <= 65535 -> "4d" + "%04x".format(Integer.reverseBytes(byteLength).ushr(16))
            else -> throw IllegalArgumentException("P2SH redeemScript is too large")
        }
        return prefix + dataHex
    }

    fun reviewText(prepared: Prepared, origin: String, network: String, address: String): String {
        val lines = ArrayList<String>()
        lines += "Website: $origin"
        lines += "Network: $network"
        lines += "Account: $address"
        lines += "Inputs to sign: ${prepared.signInputs.size}"
        lines += "Total amount: ${KaspaTransactionEngine.formatKas(KaspaTransactionEngine.sompiToKas(prepared.outputSumSompi))} KAS"
        lines += "Estimated fee: ${prepared.feeKas} KAS"
        return lines.joinToString("\n")
    }
}
