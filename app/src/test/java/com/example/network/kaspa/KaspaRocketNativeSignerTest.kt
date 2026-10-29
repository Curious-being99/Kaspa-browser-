package com.example.network.kaspa

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test

class KaspaRocketNativeSignerTest {
    private fun instructions(vararg entries: Pair<String, Int>) = JSONArray().apply {
        entries.forEach { (kind, index) ->
            put(JSONObject().put("kind", kind).put("inputIndex", index))
        }
    }

    private fun inputs(vararg scripts: String) = JSONArray().apply {
        scripts.forEach { script -> put(JSONObject().put("signatureScript", script)) }
    }

    @Test
    fun acceptsCompleteExplicitInputClassification() {
        KaspaRocketNativeSigner.validateInstructionCoverage(
            instructions("p2pkh" to 0, "final" to 1),
            2,
            inputs("", "abcd")
        )
    }

    @Test
    fun rejectsMissingInputInstruction() {
        assertThrows(IllegalArgumentException::class.java) {
            KaspaRocketNativeSigner.validateInstructionCoverage(
                instructions("p2pkh" to 0),
                2,
                inputs("", "")
            )
        }
    }

    @Test
    fun rejectsDuplicateInputInstruction() {
        assertThrows(IllegalArgumentException::class.java) {
            KaspaRocketNativeSigner.validateInstructionCoverage(
                instructions("p2pkh" to 0, "p2pkh" to 0),
                1,
                inputs("")
            )
        }
    }

    @Test
    fun rejectsFinalInstructionWithoutPrebuiltScript() {
        assertThrows(IllegalArgumentException::class.java) {
            KaspaRocketNativeSigner.validateInstructionCoverage(
                instructions("final" to 0),
                1,
                inputs("")
            )
        }
    }

    @Test
    fun rejectsMalformedSignatureScript() {
        assertThrows(IllegalArgumentException::class.java) {
            KaspaRocketNativeSigner.validateInstructionCoverage(
                instructions("final" to 0),
                1,
                inputs("xyz")
            )
        }
    }

    @Test
    fun rejectsP2pkhInstructionThatWouldOverwriteScript() {
        assertThrows(IllegalArgumentException::class.java) {
            KaspaRocketNativeSigner.validateInstructionCoverage(
                instructions("p2pkh" to 0),
                1,
                inputs("abcd")
            )
        }
    }
}
