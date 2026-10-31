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
        val redeemScript: String = ""
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
            val item = signInputsJson.optJSONObject(index)
                ?: throw IllegalArgumentException("Invalid signInputs entry")
            val inputIndex = item.optInt("index", -1)
            val sighashType = item.optInt("sighashType", -1)
            require(sighashType == KaspaTransactionEngine.SIGHASH_ALL) {
                "Only SIGHASH_ALL (1) is supported"
            }
            SignInput(inputIndex, sighashType, item.optString("redeemScript", ""))
        }
        require(requests.map { it.index }.distinct().size == requests.size) {
            "Duplicate input index requested"
        }
        val prepared = parseAndValidate(txJsonString, requests, redeemScript)
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
            if (prepared.utxoEntries[request.index].scriptPublicKey.version == 0 && utxoScript == expectedScript) {
                continue
            }
            val p2shHash = Regex("aa20([0-9a-f]{64})87").matchEntire(utxoScript)?.groupValues?.get(1)
                ?: throw IllegalArgumentException("Selected input ${request.index} is not an active-wallet P2PK or supported P2SH input")
            val redeem = request.redeemScript.takeIf { it.isNotBlank() }
                ?: prepared.redeemScript.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("P2SH input ${request.index} requires its redeemScript")
            require(redeem.matches(Regex("(?i)([0-9a-f]{2})+"))) {
                "P2SH redeemScript for input ${request.index} is malformed"
            }
            val redeemBytes = hexToBytes(redeem)
            val actualHash = CryptoUtils.blake2b256(redeemBytes)
                .joinToString("") { "%02x".format(it) }
            require(actualHash.equals(p2shHash, ignoreCase = true)) {
                "P2SH redeemScript does not match input ${request.index} locking script"
            }
            val redeemHex = redeem.lowercase()
            require(redeemHex.contains(expectedScript)) {
                "P2SH input ${request.index} redeemScript is not controlled by the active wallet key"
            }
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
            val outpoint = item.optJSONObject("previousOutpoint")
                ?: item.optJSONObject("previous_outpoint")
                ?: throw IllegalArgumentException("Input $index is missing its previous outpoint")
            val txId = outpoint.optString("transactionId", outpoint.optString("transaction_id", ""))
            require(txId.matches(Regex("[0-9a-fA-F]{64}"))) { "Input $index has an invalid transaction id" }
            val outIndex = outpoint.optLong("index", -1L)
            require(outIndex >= 0L && outIndex <= 0xffffffffL) { "Input $index has an invalid outpoint index" }
            require(seenOutpoints.add("${txId.lowercase()}:$outIndex")) {
                "Transaction contains duplicate outpoints"
            }
            val utxoJson = item.optJSONObject("utxoEntry")
                ?: item.optJSONObject("utxo_entry")
                ?: item.optJSONObject("utxo")
                ?: throw IllegalArgumentException("Input $index is missing UTXO data required for safe signing/review")
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
            val amount = item.optLong("amount", item.optString("amount", "-1").toLongOrNull() ?: -1L)
            require(amount >= 0L) { "Output $index has an invalid amount" }
            val scriptJson = item.optJSONObject("scriptPublicKey")
                ?: item.optJSONObject("script_public_key")
                ?: throw IllegalArgumentException("Output $index is missing its script public key")
            val script = KaspaTransactionEngine.KaspaScriptPublicKey.fromJson(scriptJson)
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
        require(fee >= KaspaTransactionEngine.MINIMUM_WALLET_TRANSACTION_FEE_SOMPIS) {
            "Transaction fee is below the wallet minimum of 0.0056 KAS"
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
        lines += "Wallet: $address"
        lines += "Inputs: ${prepared.transaction.inputs.size}"
        lines += "Outputs: ${prepared.transaction.outputs.size}"
        lines += "Total input: ${KaspaTransactionEngine.formatKas(KaspaTransactionEngine.sompiToKas(prepared.inputSumSompi))} KAS"
        lines += "Total output: ${KaspaTransactionEngine.formatKas(KaspaTransactionEngine.sompiToKas(prepared.outputSumSompi))} KAS"
        lines += "Fee: ${prepared.feeKas} KAS"
        prepared.transaction.outputs.forEachIndexed { index, output ->
            lines += "Output ${index + 1}: ${KaspaTransactionEngine.formatKas(KaspaTransactionEngine.sompiToKas(output.amount))} KAS · script ${output.scriptPublicKey.script.take(22)}…"
        }
        lines += "Review every output before approving. Covenant logic and marketplace intent are not inferred by this generic signer."
        return lines.joinToString("\n")
    }
}
