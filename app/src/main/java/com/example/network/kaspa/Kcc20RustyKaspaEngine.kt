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

    @JvmStatic
    private external fun planTransferNative(requestJson: String): String

    @JvmStatic
    private external fun assembleWitnessNative(requestJson: String): String

    fun assembleReferenceWitness(
        program: ByteArray,
        isLeader: Boolean,
        signature65: ByteArray?,
        successorPrograms: List<ByteArray>
    ): ByteArray {
        check(nativeAvailable) { "Rust KCC20 witness assembler is unavailable for this Android ABI" }
        require(program.isNotEmpty() && (signature65 == null || signature65.size == 65))
        val request = JSONObject().apply {
            put("templateHash", Kcc20ReferenceProtocol.TEMPLATE_HASH)
            put("templateSuffix", Kcc20ReferenceProtocol.templateSuffixHex())
            put("program", Kcc20ReferenceProtocol.bytesToHex(program))
            put("leader", isLeader)
            put("signature65", signature65?.let(Kcc20ReferenceProtocol::bytesToHex) ?: JSONObject.NULL)
            put("nextStates", JSONArray().apply {
                successorPrograms.forEach { put(Kcc20ReferenceProtocol.bytesToHex(it)) }
            })
        }
        val response = JSONObject(assembleWitnessNative(request.toString()))
        check(response.optBoolean("valid", false)) {
            response.optString("error", "Rust KCC20 witness assembly failed")
        }
        return Kcc20ReferenceProtocol.decodeHex(response.getString("script"))
    }

    fun assembleReferenceWitness(
        plan: Kcc20ReferenceTransferBuilder.TransferPlan,
        inputIndex: Int,
        signature65: ByteArray
    ): ByteArray {
        require(inputIndex in plan.seats.indices)
        val seat = plan.seats[inputIndex]
        return assembleReferenceWitness(
            seat.program,
            seat.isLeader,
            signature65,
            if (seat.isLeader) plan.successorPrograms else emptyList()
        )
    }

    private fun legacyAssemblerPlaceholder(): Unit {
        error("Unreachable")
    }

    fun planReferenceTransfer(
        cells: List<Kcc20ReferenceCellVerifier.VerifiedCell>,
        covenantId: String,
        senderPublicKey: ByteArray,
        recipientPublicKey: ByteArray,
        amountBaseUnits: java.math.BigInteger
    ): Kcc20ReferenceTransferBuilder.TransferPlan {
        check(nativeAvailable) { "Rust KCC20 planner is unavailable for this Android ABI" }
        require(amountBaseUnits.signum() > 0 && amountBaseUnits.bitLength() <= 63)
        require(cells.isNotEmpty() && cells.size <= Kcc20ReferenceProtocol.MAX_TOKEN_INPUTS)
        val request = JSONObject().apply {
            put("covenantId", covenantId)
            put("senderPublicKey", Kcc20ReferenceProtocol.bytesToHex(senderPublicKey))
            put("recipientPublicKey", Kcc20ReferenceProtocol.bytesToHex(recipientPublicKey))
            put("amount", amountBaseUnits.toString())
            put("templateHash", Kcc20ReferenceProtocol.TEMPLATE_HASH)
            put("templateSuffix", Kcc20ReferenceProtocol.templateSuffixHex())
            put("cells", JSONArray().apply {
                cells.forEach { cell ->
                    put(JSONObject().apply {
                        put("txid", cell.transactionId)
                        put("index", cell.outputIndex)
                        put("covenantId", cell.covenantId)
                        put("program", Kcc20ReferenceProtocol.bytesToHex(cell.program))
                    })
                }
            })
        }
        val response = JSONObject(planTransferNative(request.toString()))
        check(response.optBoolean("valid", false)) {
            response.optString("error", "Rust KCC20 transfer planning failed")
        }
        val consumedJson = response.getJSONArray("consumed")
        val consumed = (0 until consumedJson.length()).map { i ->
            val program = Kcc20ReferenceProtocol.decodeHex(consumedJson.getJSONObject(i).getString("program"))
            Kcc20ReferenceProtocol.verifyProgram(program)
        }
        val successorJson = response.getJSONArray("successorPrograms")
        val successors = (0 until successorJson.length()).map { i ->
            Kcc20ReferenceProtocol.decodeHex(successorJson.getString(i))
        }
        val nextStates = successors.map(Kcc20ReferenceProtocol::verifyProgram)
        val inputAmount = java.math.BigInteger(response.getString("inputAmount"))
        val outputAmount = java.math.BigInteger(response.getString("outputAmount"))
        check(consumed.size == cells.size && successors.isNotEmpty())
        check(consumed.fold(java.math.BigInteger.ZERO) { sum, state -> sum + state.amount } == inputAmount)
        check(nextStates.fold(java.math.BigInteger.ZERO) { sum, state -> sum + state.amount } == outputAmount)
        check(inputAmount == outputAmount)
        val seats = cells.mapIndexed { index, cell ->
            Kcc20ReferenceTransferBuilder.SignatureSeat(index, cell.program.copyOf(), index == 0)
        }
        return Kcc20ReferenceTransferBuilder.TransferPlan(
            covenantId = covenantId.lowercase(),
            consumed = consumed,
            nextStates = nextStates,
            successorPrograms = successors,
            seats = seats,
            inputAmount = inputAmount,
            outputAmount = outputAmount
        )
    }

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
