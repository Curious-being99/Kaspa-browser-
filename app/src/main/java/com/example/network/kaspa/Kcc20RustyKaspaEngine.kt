package com.example.network.kaspa

import org.json.JSONArray
import org.json.JSONObject

object Kcc20RustyKaspaEngine {
    private val nativeAvailable = runCatching {
        System.loadLibrary("kazium_kcc20_engine")
        true
    }.getOrDefault(false)

    fun isAvailable(): Boolean = nativeAvailable

    @JvmStatic
    private external fun validateNative(requestJson: String): String

    data class SpentUtxo(
        val amount: Long,
        val scriptPublicKeyVersion: Int,
        val scriptPublicKeyHex: String,
        val daaScore: Long,
        val isCoinbase: Boolean,
        val covenantId: String?
    )

    data class ValidationResult(
        val valid: Boolean,
        val txId: String,
        val computeMass: Long,
        val transientMass: Long,
        val storageMass: Long?,
        val inputResults: List<InputResult>,
        val error: String? = null
    )

    data class InputResult(
        val index: Int,
        val valid: Boolean,
        val error: String?,
        val usedScriptUnits: Long,
        val allowedScriptUnits: Long
    )

    fun validate(
        transaction: KaspaTransactionEngine.KaspaTransaction,
        spentUtxos: List<SpentUtxo>
    ): ValidationResult {
        check(nativeAvailable) { "Rusty-Kaspa v2.1.0 script engine is unavailable for this Android ABI" }
        require(transaction.version == 1) { "KCC20 reference transactions must use version 1" }
        require(transaction.inputs.size == spentUtxos.size) {
            "Every input must have one matching UTXO from the configured node"
        }
        require(transaction.inputs.all { it.sigOpCount == 0 && it.computeBudget in 1..0xffff }) {
            "Every version-1 input needs sigOpCount=0 and a measured compute budget"
        }

        val request = JSONObject().apply {
            put("version", transaction.version)
            put("lockTime", transaction.lockTime)
            put("gas", transaction.gas)
            put("payload", transaction.payload)
            put("inputs", JSONArray().apply {
                transaction.inputs.forEach { input ->
                    put(JSONObject().apply {
                        put("txid", input.previousOutpoint.transactionId)
                        put("index", input.previousOutpoint.index)
                        put("sigScript", input.signatureScript)
                        put("sequence", input.sequence)
                        put("computeBudget", input.computeBudget)
                    })
                }
            })
            put("outputs", JSONArray().apply {
                transaction.outputs.forEach { output ->
                    put(JSONObject().apply {
                        put("value", output.amount)
                        put("spkVersion", output.scriptPublicKey.version)
                        put("script", output.scriptPublicKey.script)
                        val binding = output.covenant
                        if (binding == null) {
                            put("covenant", JSONObject.NULL)
                        } else {
                            put("covenant", JSONObject().apply {
                                put("authorizingInput", binding.authorizingInput)
                                put("covenantId", binding.covenantId)
                            })
                        }
                    })
                }
            })
            put("utxos", JSONArray().apply {
                spentUtxos.forEachIndexed { index, utxo ->
                    val input = transaction.inputs[index]
                    put(JSONObject().apply {
                        put("txid", input.previousOutpoint.transactionId)
                        put("index", input.previousOutpoint.index)
                        put("amount", utxo.amount.toString())
                        put("spkVersion", utxo.scriptPublicKeyVersion)
                        put("script", utxo.scriptPublicKeyHex)
                        put("daaScore", utxo.daaScore.toString())
                        put("isCoinbase", utxo.isCoinbase)
                        put("covenantId", utxo.covenantId ?: JSONObject.NULL)
                    })
                }
            })
        }

        val response = JSONObject(validateNative(request.toString()))
        val inputs = response.optJSONArray("inputs") ?: JSONArray()
        val results = (0 until inputs.length()).map { index ->
            val item = inputs.getJSONObject(index)
            InputResult(
                index = item.optInt("index", index),
                valid = item.optBoolean("valid", false),
                error = item.optString("error").takeIf { it.isNotBlank() && it != "null" },
                usedScriptUnits = item.optLong("usedScriptUnits", -1L),
                allowedScriptUnits = item.optLong("allowedScriptUnits", -1L)
            )
        }
        val valid = response.optBoolean("valid", false) &&
            results.size == transaction.inputs.size &&
            results.all { it.valid }
        val masses = response.optJSONObject("mass")
        val storageMass = if (masses == null || masses.isNull("storage")) {
            null
        } else {
            masses.optLong("storage", -1L).takeIf { it >= 0L }
        }
        return ValidationResult(
            valid = valid,
            txId = response.optString("txid"),
            computeMass = masses?.optLong("compute", -1L) ?: -1L,
            transientMass = masses?.optLong("transient", -1L) ?: -1L,
            storageMass = storageMass,
            inputResults = results,
            error = response.optString("error").takeIf { it.isNotBlank() && it != "null" }
        )
    }
}
