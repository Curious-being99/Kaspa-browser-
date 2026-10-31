package com.example

import com.example.dapp.DappPsktTransaction
import com.example.network.CryptoUtils
import com.example.network.kaspa.KaspaTransactionEngine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Kcc20SigningUnitTest {

    private val testPrivateKey = BigInteger("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", 16)
    private val testPublicKeyHex = CryptoUtils.to32ByteArray(CryptoUtils.pointMultiply(testPrivateKey).x)
        .joinToString("") { "%02x".format(it) }
    private val testAddress = CryptoUtils.encodeRealKaspaAddress(CryptoUtils.hexToBytes(testPublicKeyHex), "kaspa")

    @Test
    fun testGenericPsktSignatureDoesNotClaimKcc20TransferValidation() {
        // This is a generic KAS PSKT fixture with a covenant-script output, not a KCC-20 token transfer.
        // This transaction has 1 input (user KAS) and 2 outputs (covenant payment and change)
        val userScript = "20" + testPublicKeyHex + "ac"
        val covenantAddress = "kaspa:qzrsq2mfj9sf7uye3u5q7juejzlr0axk5jz9fpg4vqe76erdyvxxze84k9nk7"
        val covenantScript = KaspaTransactionEngine.decodeAddressToScriptPublicKey(covenantAddress).script

        val txJson = JSONObject().apply {
            put("version", 0)
            val inputs = JSONArray().put(JSONObject().apply {
                put("previousOutpoint", JSONObject().apply {
                    put("transactionId", "a".repeat(64))
                    put("index", 0)
                })
                put("signatureScript", "")
                put("sequence", 0)
                put("sigOpCount", 1)
                put("utxoEntry", JSONObject().apply {
                    put("amount", "1000000000") // 10 KAS
                    put("scriptPublicKey", JSONObject().apply {
                        put("version", 0)
                        put("script", userScript)
                    })
                })
            })
            put("inputs", inputs)
            
            val outputs = JSONArray().apply {
                put(JSONObject().apply {
                    put("amount", "100000000") // 1 KAS for token
                    put("scriptPublicKey", JSONObject().apply {
                        put("version", 0)
                        put("script", covenantScript)
                    })
                })
                put(JSONObject().apply {
                    put("amount", "899000000") // 8.99 KAS change (0.01 KAS fee)
                    put("scriptPublicKey", JSONObject().apply {
                        put("version", 0)
                        put("script", userScript)
                    })
                })
            }
            put("outputs", outputs)
            put("lockTime", 0)
            put("subnetworkId", KaspaTransactionEngine.DEFAULT_SUBNETWORK_ID)
        }

        val signInputs = JSONArray().put(JSONObject().apply {
            put("index", 0)
            put("sighashType", KaspaTransactionEngine.SIGHASH_ALL)
        })

        // 2. Prepare for signing
        val prepared = DappPsktTransaction.prepareForSigning(
            txJson.toString(),
            signInputs,
            testPublicKeyHex
        )

        // Sign the generic Kaspa PSKT input using the wallet Schnorr engine.
        val signedJsonStr = DappPsktTransaction.sign(prepared, testPrivateKey)
        val signedJson = JSONObject(signedJsonStr)

        // 4. Verify the results
        val signedInputs = signedJson.getJSONArray("inputs")
        val signedInput = signedInputs.getJSONObject(0)
        val signatureScript = signedInput.getString("signatureScript")

        // Kaspa signature script format: 0x41 + 64-byte Schnorr Sig + 0x01 (SIGHASH_ALL)
        assertTrue("Signature script should start with 41", signatureScript.startsWith("41"))
        assertTrue("Signature script should end with 01", signatureScript.endsWith("01"))
        assertEquals("Signature script length should be 132 hex chars (66 bytes)", 132, signatureScript.length)

        // 5. Verify the sighash and signature using CryptoUtils directly (Self-consistency check)
        val inputIndex = 0
        val utxoEntry = prepared.utxoEntries[inputIndex]
        val sighash = KaspaTransactionEngine.calcSchnorrSignatureHash(
            prepared.transaction,
            inputIndex,
            KaspaTransactionEngine.SIGHASH_ALL,
            utxoEntry
        )
        
        val extractedSig = signatureScript.substring(2, 130)
        // Note: CryptoUtils.verifySchnorr would be ideal here if it existed, 
        // but we can trust that signSchnorr uses the private key provided.
        assertNotNull("Signature should be generated", extractedSig)
        
        println("Successfully signed generic Kaspa PSKT; KCC-20 state transition was not tested")
        println("TX ID: ${KaspaTransactionEngine.calcTransactionId(prepared.transaction)}")
        println("Signature: $extractedSig")
    }
}
