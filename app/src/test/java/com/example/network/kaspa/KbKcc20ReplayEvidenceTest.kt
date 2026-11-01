package com.example.network.kaspa

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KbKcc20ReplayEvidenceTest {
    private val previousTx = "a".repeat(64)

    private fun transaction(signature: String = "41" + "ab".repeat(64) + "01") =
        JSONObject().put("inputs", JSONArray().put(
            JSONObject()
                .put("previousOutpoint", JSONObject()
                    .put("transactionId", previousTx)
                    .put("index", 2))
                .put("signatureScript", signature)
        ))

    private fun utxos() = JSONArray().put(
        JSONObject()
            .put("outpoint", JSONObject()
                .put("transactionId", previousTx)
                .put("index", 2))
            .put("utxoEntry", JSONObject()
                .put("amount", "1200000")
                .put("scriptPublicKey", JSONObject()
                    .put("version", 0)
                    .put("script", "20" + "ab".repeat(32) + "ac"))
                .put("blockDaaScore", "123456")
                .put("isCoinbase", false))
    )

    @Test
    fun completeSignedContextIsReadyOnlyForConsensusReplay() {
        val result = KbKcc20ReplayEvidence.assess(transaction(), utxos())
        assertTrue(result is KbKcc20ReplayEvidence.Assessment.ReadyForConsensusReplay)
        val ready = result as KbKcc20ReplayEvidence.Assessment.ReadyForConsensusReplay
        assertEquals(1, ready.inputCount)
        assertEquals(1_200_000L, ready.totalInputSompi)
    }

    @Test
    fun missingLiveInputUtxoIsRejected() {
        val result = KbKcc20ReplayEvidence.assess(transaction(), JSONArray())
        assertTrue(result is KbKcc20ReplayEvidence.Assessment.Rejected)
        assertTrue((result as KbKcc20ReplayEvidence.Assessment.Rejected)
            .reason.contains("Missing live UTXO"))
    }

    @Test
    fun unsignedInputIsRejected() {
        val result = KbKcc20ReplayEvidence.assess(transaction(""), utxos())
        assertTrue(result is KbKcc20ReplayEvidence.Assessment.Rejected)
    }

    @Test
    fun extraOrDuplicateContextIsRejected() {
        val rows = JSONArray().put(utxos().getJSONObject(0)).put(utxos().getJSONObject(0))
        val result = KbKcc20ReplayEvidence.assess(transaction(), rows)
        assertTrue(result is KbKcc20ReplayEvidence.Assessment.Rejected)
    }
}
