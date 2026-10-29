package com.example.network.kaspa

import com.example.network.CryptoUtils
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger

internal object KaspaRocketNativeSigner {
    data class SignedPlanTransaction(
        val transaction: JSONObject,
        val signedInputCount: Int
    )

    fun sign(
        transactionJson: JSONObject,
        instructions: JSONArray,
        privateKey: BigInteger,
        publicKeyHex: String
    ): SignedPlanTransaction {
        require(publicKeyHex.matches(Regex("(?i)[0-9a-f]{64}"))) { "Invalid wallet public key" }
        val txJson = transactionJson.optJSONObject("tx") ?: transactionJson.optJSONObject("transaction") ?: transactionJson
        val inputsJson = txJson.optJSONArray("inputs") ?: throw IllegalArgumentException("Plan transaction inputs are missing")
        val outputsJson = txJson.optJSONArray("outputs") ?: throw IllegalArgumentException("Plan transaction outputs are missing")
        require(txJson.optInt("version", -1) == 1) { "KaspaRocket trades must use transaction version 1" }

        val inputs = ArrayList<KaspaTransactionEngine.KaspaTransactionInput>(inputsJson.length())
        val utxos = ArrayList<KaspaTransactionEngine.KaspaUtxoEntry>(inputsJson.length())
        val utxoArray = transactionJson.optJSONArray("utxoEntries")
            ?: transactionJson.optJSONArray("utxo_entries")
            ?: (transactionJson.optJSONObject("tx")?.optJSONArray("utxoEntries"))
            ?: (transactionJson.optJSONObject("transaction")?.optJSONArray("utxoEntries"))
        for (index in 0 until inputsJson.length()) {
            val input = inputsJson.optJSONObject(index) ?: throw IllegalArgumentException("Malformed input $index")
            val outpointJson = input.optJSONObject("previousOutpoint")
                ?: input.optJSONObject("previous_outpoint")
                ?: throw IllegalArgumentException("Input $index has no previous outpoint")
            val outpoint = KaspaTransactionEngine.KaspaOutpoint.fromJson(outpointJson)
            val sequence = input.optLong("sequence", 0L)
            val computeBudget = input.optInt("computeBudget", input.optInt("compute_budget", 0))
            val sigOpCount = input.optInt("sigOpCount", input.optInt("sig_op_count", 1))
            inputs += KaspaTransactionEngine.KaspaTransactionInput(
                previousOutpoint = outpoint,
                signatureScript = input.optString("signatureScript", input.optString("signature_script", "")),
                sequence = sequence,
                sigOpCount = sigOpCount,
                computeBudget = computeBudget
            )
            val utxoJson = input.optJSONObject("utxoEntry")
                ?: input.optJSONObject("utxo_entry")
                ?: utxoArray?.optJSONObject(index)
                ?: throw IllegalArgumentException("Input $index is missing its UTXO entry")
            utxos += KaspaTransactionEngine.KaspaUtxoEntry.fromJson(utxoJson)
        }

        val outputs = ArrayList<KaspaTransactionEngine.KaspaTransactionOutput>(outputsJson.length())
        for (index in 0 until outputsJson.length()) {
            val output = outputsJson.optJSONObject(index) ?: throw IllegalArgumentException("Malformed output $index")
            val scriptJson = output.optJSONObject("scriptPublicKey")
                ?: output.optJSONObject("script_public_key")
                ?: throw IllegalArgumentException("Output $index has no script public key")
            val bindingJson = output.optJSONObject("covenant")
                ?: output.optJSONObject("covenantBinding")
                ?: output.optJSONObject("covenant_binding")
            val binding = bindingJson?.let {
                KaspaTransactionEngine.KaspaCovenantBinding(
                    authorizingInput = it.optInt("authorizingInput", it.optInt("authorizing_input", 0)),
                    covenantId = it.optString("covenantId", it.optString("covenant_id", ""))
                )
            }
            outputs += KaspaTransactionEngine.KaspaTransactionOutput(
                amount = readLong(output, "amount", "value"),
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey.fromJson(scriptJson),
                covenant = binding
            )
        }

        val tx = KaspaTransactionEngine.KaspaTransaction(
            version = 1,
            inputs = inputs,
            outputs = outputs,
            lockTime = txJson.optLong("lockTime", txJson.optLong("lock_time", 0L)),
            subnetworkId = txJson.optString("subnetworkId", txJson.optString("subnetwork_id", KaspaTransactionEngine.DEFAULT_SUBNETWORK_ID)),
            gas = txJson.optLong("gas", 0L),
            payload = txJson.optString("payload", ""),
            mass = txJson.optLong("mass", 0L)
        )

        val signedTx = JSONObject(transactionJson.toString())
        val signedTxRoot = signedTx.optJSONObject("tx")
            ?: signedTx.optJSONObject("transaction")
            ?: signedTx
        val signedInputs = signedTxRoot.optJSONArray("inputs")
            ?: throw IllegalArgumentException("Plan transaction inputs are missing")
        var signedCount = 0
        val seen = HashSet<Int>()

        for (instructionIndex in 0 until instructions.length()) {
            val instruction = instructions.optJSONObject(instructionIndex)
                ?: throw IllegalArgumentException("Malformed signing instruction")
            val kind = instruction.optString("kind", "")
            val inputIndex = instruction.optInt("inputIndex", instruction.optInt("input_index", -1))
            require(inputIndex in inputs.indices) { "Signing instruction input index is out of range" }
            require(seen.add(inputIndex)) { "Duplicate signing instruction for input $inputIndex" }

            val signedInput = signedInputs.optJSONObject(inputIndex)
                ?: throw IllegalArgumentException("Malformed input $inputIndex")
            val currentScript = signedInput.optString("signatureScript", signedInput.optString("signature_script", ""))
            require(currentScript.matches(Regex("(?i)([0-9a-f]{2})*"))) {
                "Input $inputIndex has malformed signature script"
            }

            if (kind == "final") {
                require(currentScript.isNotBlank()) {
                    "Final input $inputIndex has no prebuilt signature script"
                }
                continue
            }
            require(kind == "p2pkh" || kind == "covenant-leader") {
                "Unsupported KaspaRocket signing instruction: $kind"
            }

            val expectedDigest = instruction.optString("sighash", "").removePrefix("0x").lowercase()
            require(expectedDigest.matches(Regex("[0-9a-f]{64}"))) {
                "Signing instruction $inputIndex has no valid expected sighash"
            }
            val localDigest = KaspaTransactionEngine.calcSchnorrSignatureHash(
                tx,
                inputIndex,
                KaspaTransactionEngine.SIGHASH_ALL,
                utxos[inputIndex]
            )
            require(CryptoUtils.bytesToHex(localDigest).equals(expectedDigest, ignoreCase = true)) {
                "Transaction digest mismatch on input $inputIndex; refusing to sign"
            }

            val signatureHex = CryptoUtils.signSchnorr(privateKey, localDigest) + "01"
            val script = when (kind) {
                "p2pkh" -> {
                    require(currentScript.isBlank()) { "Refusing to overwrite input $inputIndex signature script" }
                    "41$signatureHex" + "20" + publicKeyHex.lowercase()
                }
                else -> spliceCovenantLeaderSignature(instruction, currentScript, signatureHex, inputIndex)
            }
            if (signedInput.has("signature_script") && !signedInput.has("signatureScript")) {
                signedInput.put("signature_script", script)
            } else {
                signedInput.put("signatureScript", script)
            }
            signedCount++
        }

        require(seen.size == inputs.size) {
            "Signing instructions must explicitly classify every transaction input; refusing to sign an incomplete plan"
        }
        require(signedCount > 0 || inputs.all { it.signatureScript.isNotBlank() }) {
            "The trade transaction has neither a signable input nor prebuilt final input scripts"
        }
        return SignedPlanTransaction(JSONObject(signedTxRoot.toString()), signedCount)
    }

    private fun spliceCovenantLeaderSignature(
        instruction: JSONObject,
        scriptHex: String,
        signatureHex: String,
        inputIndex: Int
    ): String {
        require(scriptHex.isNotBlank()) { "Covenant leader input $inputIndex has no prebuilt script" }
        val offset = readInt(
            instruction,
            "signatureOffset", "signature_offset", "spliceOffset", "splice_offset", "offset"
        ) ?: throw IllegalArgumentException("Covenant leader instruction has no signature offset")
        val length = readInt(instruction, "signatureLength", "signature_length", "spliceLength", "splice_length")
            ?: 65
        require(length == 65) { "Covenant leader signature slot must be exactly 65 bytes" }
        val start = Math.multiplyExact(offset, 2)
        val end = Math.addExact(start, length * 2)
        require(offset >= 0 && end <= scriptHex.length) { "Covenant leader signature slot is outside the script" }
        val placeholder = scriptHex.substring(start, end)
        require(placeholder.matches(Regex("(?i)0{130}"))) {
            "Covenant leader signature slot is not an untouched zero placeholder"
        }
        return scriptHex.substring(0, start) + signatureHex + scriptHex.substring(end)
    }

    private fun readInt(json: JSONObject, vararg keys: String): Int? {
        for (key in keys) {
            if (json.has(key) && !json.isNull(key)) {
                val value = json.optInt(key, Int.MIN_VALUE)
                if (value != Int.MIN_VALUE) return value
            }
        }
        return null
    }

    private fun readLong(json: JSONObject, vararg keys: String): Long {
        for (key in keys) {
            if (!json.has(key) || json.isNull(key)) continue
            val value = json.opt(key)
            when (value) {
                is Number -> return value.toLong()
                is String -> value.toLongOrNull()?.let { return it }
            }
        }
        throw IllegalArgumentException("Missing integer field: ${keys.joinToString("/")}")
    }
}
