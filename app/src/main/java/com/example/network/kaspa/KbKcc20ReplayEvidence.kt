package com.example.network.kaspa

import org.json.JSONArray
import org.json.JSONObject

/**
 * Evidence gate for a real KB covenant spend.
 *
 * This mirrors the Kaspire-style rule: reconstruct the transaction from its exact
 * live input UTXOs before attempting local script execution. It deliberately does
 * not claim consensus acceptance or replace a Rusty-Kaspa TxScriptEngine replay.
 */
object KbKcc20ReplayEvidence {
    data class InputEvidence(
        val transactionId: String,
        val outputIndex: Long,
        val amountSompi: Long,
        val scriptPublicKeyVersion: Int,
        val scriptPublicKeyHex: String,
        val blockDaaScore: Long,
        val isCoinbase: Boolean,
        val signatureScriptHex: String
    ) {
        val outpoint: String
            get() = "${transactionId.lowercase()}:$outputIndex"
    }

    sealed class Assessment {
        data class ReadyForConsensusReplay(
            val inputCount: Int,
            val totalInputSompi: Long,
            val inputs: List<InputEvidence>
        ) : Assessment()

        data class Rejected(val reason: String) : Assessment()
    }

    /**
     * Requires one complete, current UTXO record for every signed transaction input.
     * Input order is preserved because sighash and covenant authorization are indexed.
     *
     * Expected transaction shape follows Kaspa RPC JSON:
     * inputs[].previousOutpoint.{transactionId,index}, inputs[].signatureScript
     *
     * Expected UTXO shape follows getUtxosByAddresses:
     * { outpoint:{transactionId,index}, utxoEntry:{amount,scriptPublicKey,blockDaaScore,isCoinbase} }
     */
    fun assess(transaction: JSONObject, liveUtxos: JSONArray): Assessment {
        val inputs = transaction.optJSONArray("inputs")
            ?: return Assessment.Rejected("Signed transaction has no inputs array")
        if (inputs.length() == 0) return Assessment.Rejected("Signed transaction has no inputs")

        val evidenceByOutpoint = LinkedHashMap<String, InputEvidence>()
        for (i in 0 until liveUtxos.length()) {
            val row = liveUtxos.optJSONObject(i)
                ?: return Assessment.Rejected("Live UTXO row $i is not an object")
            val parsed = parseUtxo(row)
                ?: return Assessment.Rejected("Live UTXO row $i is incomplete or malformed")
            if (evidenceByOutpoint.put(parsed.outpoint, parsed) != null) {
                return Assessment.Rejected("Duplicate live UTXO: ${parsed.outpoint}")
            }
        }

        val ordered = ArrayList<InputEvidence>(inputs.length())
        val seen = HashSet<String>()
        var total = 0L

        for (i in 0 until inputs.length()) {
            val input = inputs.optJSONObject(i)
                ?: return Assessment.Rejected("Transaction input $i is malformed")
            val previous = input.optJSONObject("previousOutpoint")
                ?: input.optJSONObject("previous_outpoint")
                ?: return Assessment.Rejected("Transaction input $i has no previous outpoint")
            val txid = previous.optString("transactionId",
                previous.optString("transaction_id", "")).lowercase()
            val index = if (previous.has("index")) previous.optLong("index", -1L) else -1L
            if (!TXID.matches(txid) || index < 0L) {
                return Assessment.Rejected("Transaction input $i has an invalid outpoint")
            }
            val outpoint = "$txid:$index"
            if (!seen.add(outpoint)) return Assessment.Rejected("Transaction spends an input twice: $outpoint")

            val signature = input.optString("signatureScript", input.optString("signature_script", ""))
            if (!isSignedScript(signature)) {
                return Assessment.Rejected("Transaction input $i has no non-zero signature/witness")
            }

            val utxo = evidenceByOutpoint[outpoint]
                ?: return Assessment.Rejected("Missing live UTXO context for input $outpoint")
            if (utxo.isCoinbase) {
                return Assessment.Rejected("Coinbase input is not accepted for this KB replay evidence")
            }
            if (Long.MAX_VALUE - total < utxo.amountSompi) {
                return Assessment.Rejected("Input amount sum overflows")
            }
            total += utxo.amountSompi
            ordered += utxo.copy(signatureScriptHex = signature.lowercase())
        }

        if (evidenceByOutpoint.size != inputs.length()) {
            return Assessment.Rejected("UTXO context contains entries not referenced by the signed transaction")
        }

        return Assessment.ReadyForConsensusReplay(
            inputCount = ordered.size,
            totalInputSompi = total,
            inputs = ordered
        )
    }

    private fun parseUtxo(row: JSONObject): InputEvidence? {
        val outpoint = row.optJSONObject("outpoint") ?: return null
        val txid = outpoint.optString("transactionId",
            outpoint.optString("transaction_id", "")).lowercase()
        val index = if (outpoint.has("index")) outpoint.optLong("index", -1L) else -1L
        if (!TXID.matches(txid) || index < 0L) return null

        val entry = row.optJSONObject("utxoEntry")
            ?: row.optJSONObject("utxo_entry")
            ?: return null
        val amountValue = entry.opt("amount")
        val amount = when (amountValue) {
            is Number -> amountValue.toLong()
            is String -> amountValue.toLongOrNull()
            else -> null
        } ?: return null
        if (amount <= 0L) return null

        val script = entry.optJSONObject("scriptPublicKey")
            ?: entry.optJSONObject("script_public_key")
            ?: return null
        val version = if (script.has("version")) script.optInt("version", -1) else -1
        val scriptHex = script.optString("script", script.optString("scriptPublicKey", ""))
            .lowercase()
        if (version < 0 || scriptHex.isBlank() || !HEX.matches(scriptHex)) return null

        val daa = if (entry.has("blockDaaScore")) entry.optLong("blockDaaScore", -1L)
            else if (entry.has("block_daa_score")) entry.optLong("block_daa_score", -1L) else -1L
        if (daa < 0L) return null

        val coinbaseKey = if (entry.has("isCoinbase")) "isCoinbase"
            else if (entry.has("is_coinbase")) "is_coinbase" else return null
        val coinbase = entry.optBoolean(coinbaseKey, false)

        return InputEvidence(
            transactionId = txid,
            outputIndex = index,
            amountSompi = amount,
            scriptPublicKeyVersion = version,
            scriptPublicKeyHex = scriptHex,
            blockDaaScore = daa,
            isCoinbase = coinbase,
            signatureScriptHex = ""
        )
    }

    private fun isSignedScript(value: String): Boolean {
        val normalized = value.trim().lowercase()
        if (normalized.isEmpty() || normalized == "00" || normalized == "0x") return false
        val hex = normalized.removePrefix("0x")
        if (hex.isEmpty() || hex.length % 2 != 0 || !HEX.matches(hex)) return false
        return hex.any { it != '0' }
    }

    private val TXID = Regex("^[0-9a-f]{64}$")
    private val HEX = Regex("^(?:[0-9a-f]{2})+$")
}
