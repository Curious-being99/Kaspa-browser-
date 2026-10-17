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

    data class SignInput(val index: Int, val sighashType: Int)

    data class Prepared(
        val originalJson: JSONObject,
        val transaction: KaspaTransactionEngine.KaspaTransaction,
        val utxoEntries: List<KaspaTransactionEngine.KaspaUtxoEntry>,
        val signInputs: List<SignInput>,
        val inputSumSompi: Long,
        val outputSumSompi: Long,
        val feeSompi: Long
    ) {
        val feeKas: String
            get() = KaspaTransactionEngine.formatKas(KaspaTransactionEngine.sompiToKas(feeSompi))
    }

    fun prepareForSigning(
        txJsonString: String,
        signInputsJson: JSONArray,
        walletPublicKeyHex: String
    ): Prepared {
        require(signInputsJson.length() in 1..MAX_INPUTS) { "Select at least one input to sign" }
        val requests = (0 until signInputsJson.length()).map { index ->
            val item = signInputsJson.optJSONObject(index)
                ?: throw IllegalArgumentException("Invalid signInputs entry")
            val inputIndex = item.optInt("index", -1)
            val sighashType = item.optInt("sighashType", -1)
            require(sighashType == KaspaTransactionEngine.SIGHASH_ALL) {
                "Only SIGHASH_ALL (1) is supported"
            }
            SignInput(inputIndex, sighashType)
        }
        require(requests.map { it.index }.distinct().size == requests.size) {
            "Duplicate input index requested"
        }
        val prepared = parseAndValidate(txJsonString, requests)
        val expectedScript = "20" + walletPublicKeyHex.lowercase() + "ac"
        for (request in requests) {
            require(request.index in prepared.transaction.inputs.indices) {
                "Input index is out of range"
            }
            val input = prepared.transaction.inputs[request.index]
            require(input.signatureScript.isBlank()) {
                "Refusing to overwrite an existing signature script"
            }
            val script = prepared.utxoEntries[request.index].scriptPublicKey.script
                .replace(" ", "").lowercase()
            require(script == expectedScript) {
                "Selected input ${request.index} is not a standard P2PK input owned by the active wallet"
            }
        }
        return prepared
    }

    fun inspectForSubmission(txJsonString: String): Prepared {
        val prepared = parseAndValidate(txJsonString, emptyList())
        require(prepared.transaction.inputs.all { input ->
            input.signatureScript.isNotBlank() &&
                input.signatureScript.length % 2 == 0 &&
                input.signatureScript.matches(Regex("[0-9a-fA-F]+"))
        }) {
            "Refusing to submit a transaction with unsigned or malformed inputs"
        }
        return prepared
    }

    fun sign(prepared: Prepared, privateKey: BigInteger): String {
        val signedRoot = JSONObject(prepared.originalJson.toString())
        val inputArray = signedRoot.optJSONArray("inputs")
            ?: throw IllegalArgumentException("Transaction inputs are missing")
        for (request in prepared.signInputs) {
            val input = prepared.transaction.inputs[request.index]
            val sighash = KaspaTransactionEngine.calcSchnorrSignatureHash(
                prepared.transaction,
                request.index,
                request.sighashType,
                prepared.utxoEntries[request.index]
            )
            val signature = CryptoUtils.signSchnorr(privateKey, sighash)
            val signatureScript = "41" + signature + "01"
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

    private fun parseAndValidate(txJsonString: String, signInputs: List<SignInput>): Prepared {
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
            require(outIndex >= 0L) { "Input $index has an invalid outpoint index" }
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
        require(tx.calculateMass() <= KaspaTransactionEngine.MAXIMUM_STANDARD_TRANSACTION_MASS) {
            "Transaction mass exceeds the standard mempool limit"
        }
        return Prepared(root, tx, utxoEntries, signInputs, inputSum, outputSum, fee)
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
