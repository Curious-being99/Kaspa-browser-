package com.example.dapp

import com.example.network.CryptoUtils
import com.example.network.kaspa.KaspaTransactionEngine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DappPsktTransactionTest {
    private val privateKey = BigInteger.ONE
    private val publicKeyHex = CryptoUtils.to32ByteArray(CryptoUtils.pointMultiply(privateKey).x)
        .joinToString("") { "%02x".format(it) }

    private fun transactionJson(nested: Boolean = false): String {
        val userScript = "20" + publicKeyHex + "ac"
        val covenantScript = "aa20" + "11".repeat(32) + "87"
        val inputs = JSONArray()
            .put(JSONObject()
                .put("previousOutpoint", JSONObject().put("transactionId", "01".repeat(32)).put("index", 0))
                .put("signatureScript", "")
                .put("sequence", 0)
                .put("sigOpCount", 1)
                .put("utxoEntry", JSONObject().put("amount", "10000000").put("scriptPublicKey", JSONObject().put("version", 0).put("script", userScript)).put("blockDaaScore", "1").put("isCoinbase", false)))
            .put(JSONObject()
                .put("previousOutpoint", JSONObject().put("transactionId", "02".repeat(32)).put("index", 1))
                .put("signatureScript", "51")
                .put("sequence", 0)
                .put("sigOpCount", 0)
                .put("utxoEntry", JSONObject().put("amount", "10000000").put("scriptPublicKey", JSONObject().put("version", 0).put("script", covenantScript)).put("blockDaaScore", "1").put("isCoinbase", false)))
        val outputs = JSONArray()
            .put(JSONObject().put("amount", "19400000").put("scriptPublicKey", JSONObject().put("version", 0).put("script", userScript)))
        val tx = JSONObject()
            .put("version", 0)
            .put("inputs", inputs)
            .put("outputs", outputs)
            .put("lockTime", 0)
            .put("subnetworkId", KaspaTransactionEngine.DEFAULT_SUBNETWORK_ID)
            .put("gas", 0)
            .put("payload", "")
            .put("mass", 0)
        return if (nested) JSONObject().put("transaction", tx).put("meta", JSONObject().put("kept", true)).toString() else tx.toString()
    }

    @Test
    fun signsOnlyRequestedP2pkInputAndPreservesCovenantInputAndMetadata() {
        val original = JSONObject(transactionJson(nested = true))
        val beforeCovenantInput = original.getJSONObject("transaction").getJSONArray("inputs").getJSONObject(1).toString()
        val prepared = DappPsktTransaction.prepareForSigning(
            transactionJson(nested = true),
            JSONArray().put(JSONObject().put("index", 0).put("sighashType", KaspaTransactionEngine.SIGHASH_ALL)),
            publicKeyHex
        )
        val signed = JSONObject(DappPsktTransaction.sign(prepared, privateKey))
        val signedInputs = signed.getJSONObject("transaction").getJSONArray("inputs")
        assertTrue(signedInputs.getJSONObject(0).getString("signatureScript").isNotBlank())
        assertEquals(beforeCovenantInput, signedInputs.getJSONObject(1).toString())
        assertTrue(signed.getJSONObject("meta").getBoolean("kept"))
    }

    @Test
    fun enforcesExistingWalletMinimumFeePolicy() {
        val prepared = DappPsktTransaction.prepareForSigning(
            transactionJson(),
            JSONArray().put(JSONObject().put("index", 0).put("sighashType", KaspaTransactionEngine.SIGHASH_ALL)),
            publicKeyHex
        )
        assertEquals(600000L, prepared.feeSompi)
        assertTrue(prepared.feeSompi >= KaspaTransactionEngine.MINIMUM_WALLET_TRANSACTION_FEE_SOMPIS)
    }

    @Test
    fun submissionAllowsPreservedNonP2pkScriptButRequiresP2pkSignature() {
        val prepared = DappPsktTransaction.prepareForSigning(
            transactionJson(),
            JSONArray().put(JSONObject().put("index", 0).put("sighashType", KaspaTransactionEngine.SIGHASH_ALL)),
            publicKeyHex
        )
        val signedJson = DappPsktTransaction.sign(prepared, privateKey)
        val inspected = DappPsktTransaction.inspectForSubmission(signedJson)
        assertFalse(inspected.transaction.inputs[0].signatureScript.isBlank())
        assertEquals("51", inspected.transaction.inputs[1].signatureScript)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateOutpointsBeforeSigning() {
        val json = JSONObject(transactionJson())
        val inputs = json.getJSONArray("inputs")
        inputs.getJSONObject(1).put("previousOutpoint", JSONObject().put("transactionId", "01".repeat(32)).put("index", 0))
        DappPsktTransaction.prepareForSigning(
            json.toString(),
            JSONArray().put(JSONObject().put("index", 0).put("sighashType", KaspaTransactionEngine.SIGHASH_ALL)),
            publicKeyHex
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToSignInputNotOwnedByActiveWallet() {
        val otherKey = CryptoUtils.to32ByteArray(CryptoUtils.pointMultiply(BigInteger.valueOf(2)).x)
            .joinToString("") { "%02x".format(it) }
        DappPsktTransaction.prepareForSigning(
            transactionJson(),
            JSONArray().put(JSONObject().put("index", 0).put("sighashType", KaspaTransactionEngine.SIGHASH_ALL)),
            otherKey
        )
    }
}
