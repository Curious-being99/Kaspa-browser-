package com.example.dapp

import com.example.network.CryptoUtils
import com.example.network.kaspa.KaspaTransactionEngine
import com.example.network.kaspa.Kcc20NoteScanner
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

    private fun transactionJson(nested: Boolean = false, covenantSigScript: String = "51"): String {
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
                .put("signatureScript", covenantSigScript)
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
        val signedSignature = signedInputs.getJSONObject(0).getString("signatureScript").substring(2, 130)
        val sighash = KaspaTransactionEngine.calcSchnorrSignatureHash(
            prepared.transaction, 0, KaspaTransactionEngine.SIGHASH_ALL, prepared.utxoEntries[0]
        )
        assertTrue(CryptoUtils.verifySchnorrSignature(signedSignature, sighash, CryptoUtils.hexToBytes(publicKeyHex)))
        assertTrue(signedInputs.getJSONObject(0).getString("signatureScript").isNotBlank())
        assertEquals(beforeCovenantInput, signedInputs.getJSONObject(1).toString())
        assertTrue(signed.getJSONObject("meta").getBoolean("kept"))
    }

    @Test
    fun signsWalletOwnedP2shInputUsingLockingScriptAndPreservesMass() {
        val redeemScript = "20" + publicKeyHex + "ac"
        val p2shHash = CryptoUtils.blake2b256(
            redeemScript.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        ).joinToString("") { "%02x".format(it) }
        val json = JSONObject(transactionJson(covenantSigScript = ""))
        val p2shInput = json.getJSONArray("inputs").getJSONObject(1)
        p2shInput.getJSONObject("utxoEntry").getJSONObject("scriptPublicKey")
            .put("version", 0)
            .put("script", "aa20" + p2shHash + "87")
        val originalMass = json.getLong("mass")
        val prepared = DappPsktTransaction.prepareForSigning(
            json.toString(),
            JSONArray().put(JSONObject()
                .put("index", 1)
                .put("sighashType", KaspaTransactionEngine.SIGHASH_ALL)
                .put("redeemScript", redeemScript)),
            publicKeyHex
        )
        val signed = JSONObject(DappPsktTransaction.sign(prepared, privateKey))
        val signedInput = signed.getJSONArray("inputs").getJSONObject(1)
        val signatureScript = signedInput.getString("signatureScript")
        assertTrue(signatureScript.startsWith("41"))
        assertTrue(signatureScript.endsWith("20" + publicKeyHex + "ac"))
        assertEquals(originalMass, signed.getLong("mass"))
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

    @Test
    fun submissionAllowsEmptyProtocolInputScriptWhilePreservingP2pkSignature() {
        val prepared = DappPsktTransaction.prepareForSigning(
            transactionJson(covenantSigScript = ""),
            JSONArray().put(JSONObject().put("index", 0).put("sighashType", KaspaTransactionEngine.SIGHASH_ALL)),
            publicKeyHex
        )
        val signedJson = DappPsktTransaction.sign(prepared, privateKey)
        val inspected = DappPsktTransaction.inspectForSubmission(signedJson)
        assertFalse(inspected.transaction.inputs[0].signatureScript.isBlank())
        assertEquals("", inspected.transaction.inputs[1].signatureScript)
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
    fun rejectsScriptsThatOnlyContainTheWalletPublicKeyAsASubstring() {
        val json = JSONObject(transactionJson())
        val utxoScript = json.getJSONArray("inputs").getJSONObject(0)
            .getJSONObject("utxoEntry").getJSONObject("scriptPublicKey")
        utxoScript.put("script", "51" + publicKeyHex + "51")
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

    @Test
    fun parsesFlatInputsWithTxidAndVoutAliases() {
        val userScript = "20" + publicKeyHex + "ac"
        val flatInputs = JSONArray().put(JSONObject()
            .put("txid", "aa".repeat(32))
            .put("vout", 0)
            .put("signatureScript", "")
            .put("sequence", 0)
            .put("sigOpCount", 1)
            .put("amount", "10000000")
            .put("scriptPublicKey", JSONObject().put("version", 0).put("script", userScript))
            .put("blockDaaScore", "1")
            .put("isCoinbase", false)
        )
        val outputs = JSONArray().put(
            JSONObject().put("amount", "9400000").put("scriptPublicKey", JSONObject().put("version", 0).put("script", userScript))
        )
        val tx = JSONObject()
            .put("version", 0)
            .put("inputs", flatInputs)
            .put("outputs", outputs)

        val prepared = DappPsktTransaction.prepareForSigning(
            tx.toString(),
            JSONArray().put(JSONObject().put("index", 0).put("sighashType", KaspaTransactionEngine.SIGHASH_ALL)),
            publicKeyHex
        )
        assertEquals("aa".repeat(32), prepared.transaction.inputs[0].previousOutpoint.transactionId)
        assertEquals(0L, prepared.transaction.inputs[0].previousOutpoint.index)
        assertEquals(10000000L, prepared.utxoEntries[0].amount)
    }

    @Test
    fun parsesSnakeCasePreviousOutpointAndAliases() {
        val userScript = "20" + publicKeyHex + "ac"
        val snakeInputs = JSONArray().put(JSONObject()
            .put("previous_outpoint", JSONObject().put("transaction_id", "bb".repeat(32)).put("output_index", 2))
            .put("signature_script", "")
            .put("sequence", 0)
            .put("sig_op_count", 1)
            .put("utxo_entry", JSONObject().put("amount", "15000000").put("script_public_key", JSONObject().put("version", 0).put("script", userScript)))
        )
        val outputs = JSONArray().put(
            JSONObject().put("amount", "14400000").put("script_public_key", JSONObject().put("version", 0).put("script", userScript))
        )
        val tx = JSONObject()
            .put("version", 0)
            .put("inputs", snakeInputs)
            .put("outputs", outputs)

        val prepared = DappPsktTransaction.prepareForSigning(
            tx.toString(),
            JSONArray().put(JSONObject().put("index", 0).put("sighashType", KaspaTransactionEngine.SIGHASH_ALL)),
            publicKeyHex
        )
        assertEquals("bb".repeat(32), prepared.transaction.inputs[0].previousOutpoint.transactionId)
        assertEquals(2L, prepared.transaction.inputs[0].previousOutpoint.index)
        assertEquals(15000000L, prepared.utxoEntries[0].amount)
    }

    @Test
    fun signAndFormatReturnsExpectedJsonStructure() {
        val prepared = DappPsktTransaction.prepareForSigning(
            transactionJson(),
            JSONArray().put(JSONObject().put("index", 0).put("sighashType", KaspaTransactionEngine.SIGHASH_ALL)),
            publicKeyHex
        )
        val result = DappPsktTransaction.signAndFormat(prepared, privateKey)
        assertTrue(result.has("psktTransactionJson"))
        assertTrue(result.has("signedTxJson"))
        assertTrue(result.has("inputs"))

        val signedTxJson = result.getString("psktTransactionJson")
        val signedObj = JSONObject(signedTxJson)
        val firstInput = signedObj.getJSONArray("inputs").getJSONObject(0)
        assertTrue(firstInput.getString("signatureScript").startsWith("41"))
        val returnedInput = result.getJSONArray("inputs").getJSONObject(0)
        val rawSignature = returnedInput.getString("signature")
        assertEquals(128, rawSignature.length)
        assertTrue(rawSignature.matches(Regex("(?i)[0-9a-f]{128}")))
        assertEquals(KaspaTransactionEngine.SIGHASH_ALL, returnedInput.getInt("sighashType"))
        assertTrue(returnedInput.getString("signatureScript").startsWith("41$rawSignature" + "01"))
        assertEquals(
            firstInput.getString("signatureScript"),
            returnedInput.getString("signatureScript")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsWhenInput0IsMissingPreviousOutpoint() {
        val userScript = "20" + publicKeyHex + "ac"
        // Completely missing outpoint info
        val badInputs = JSONArray().put(JSONObject()
            .put("signatureScript", "")
            .put("sequence", 0)
            .put("amount", "10000000")
            .put("scriptPublicKey", JSONObject().put("version", 0).put("script", userScript))
        )
        val outputs = JSONArray().put(
            JSONObject().put("amount", "9400000").put("scriptPublicKey", JSONObject().put("version", 0).put("script", userScript))
        )
        val tx = JSONObject().put("inputs", badInputs).put("outputs", outputs)
        DappPsktTransaction.prepareForSigning(
            tx.toString(),
            JSONArray().put(JSONObject().put("index", 0).put("sighashType", KaspaTransactionEngine.SIGHASH_ALL)),
            publicKeyHex
        )
    }

    @Test
    fun parsesOutpointObjectAndIntegerSignInputs() {
        val userScript = "20" + publicKeyHex + "ac"
        val inputs = JSONArray().put(JSONObject()
            .put("outpoint", JSONObject().put("transactionId", "cc".repeat(32)).put("index", 1))
            .put("signatureScript", "")
            .put("sequence", 0)
            .put("utxoEntry", JSONObject().put("amount", "20000000").put("scriptPublicKey", JSONObject().put("version", 0).put("script", userScript)))
        )
        val outputs = JSONArray().put(
            JSONObject().put("amount", "19400000").put("scriptPublicKey", JSONObject().put("version", 0).put("script", userScript))
        )
        val tx = JSONObject().put("inputs", inputs).put("outputs", outputs)
        val prepared = DappPsktTransaction.prepareForSigning(
            tx.toString(),
            JSONArray().put(0), // Raw integer signInputs
            publicKeyHex
        )
        assertEquals("cc".repeat(32), prepared.transaction.inputs[0].previousOutpoint.transactionId)
        assertEquals(1L, prepared.transaction.inputs[0].previousOutpoint.index)
        assertEquals(20000000L, prepared.utxoEntries[0].amount)
    }

    @Test
    fun parsesStringOutpointFormat() {
        val userScript = "20" + publicKeyHex + "ac"
        val txId = "dd".repeat(32)
        val inputs = JSONArray().put(JSONObject()
            .put("outpoint", "$txId:3")
            .put("signatureScript", "")
            .put("sequence", 0)
            .put("utxoEntry", JSONObject().put("amount", "25000000").put("scriptPublicKey", JSONObject().put("version", 0).put("script", userScript)))
        )
        val outputs = JSONArray().put(
            JSONObject().put("amount", "24400000").put("scriptPublicKey", JSONObject().put("version", 0).put("script", userScript))
        )
        val tx = JSONObject().put("inputs", inputs).put("outputs", outputs)
        val prepared = DappPsktTransaction.prepareForSigning(
            tx.toString(),
            JSONArray().put(JSONObject().put("index", 0)), // signInputs without explicit sighashType
            publicKeyHex
        )
        assertEquals(txId, prepared.transaction.inputs[0].previousOutpoint.transactionId)
        assertEquals(3L, prepared.transaction.inputs[0].previousOutpoint.index)
    }

    @Test
    fun parsesStandardKaspaValueOutputsAsNumbersAndStrings() {
        val userScript = "20" + publicKeyHex + "ac"
        val inputs = JSONArray().put(JSONObject()
            .put("previousOutpoint", JSONObject().put("transactionId", "ee".repeat(32)).put("index", 0))
            .put("signatureScript", "")
            .put("sequence", 0)
            .put("sigOpCount", 1)
            .put("utxoEntry", JSONObject()
                .put("amount", "10000000000")
                .put("scriptPublicKey", JSONObject().put("version", 0).put("script", userScript))
                .put("blockDaaScore", "1")
                .put("isCoinbase", false))
        )
        val outputs = JSONArray()
            .put(JSONObject().put("value", 4000000000L)
                .put("scriptPublicKey", JSONObject().put("version", 0).put("script", userScript)))
            .put(JSONObject().put("value", "5000000000")
                .put("scriptPublicKey", JSONObject().put("version", 0).put("script", userScript)))
        val tx = JSONObject()
            .put("version", 0)
            .put("inputs", inputs)
            .put("outputs", outputs)
            .put("lockTime", 0)
            .put("subnetworkId", KaspaTransactionEngine.DEFAULT_SUBNETWORK_ID)
            .put("gas", 0)
            .put("payload", "")
            .put("mass", 0)

        val prepared = DappPsktTransaction.prepareForSigning(
            tx.toString(),
            JSONArray().put(0),
            publicKeyHex
        )

        assertEquals(9000000000L, prepared.outputSumSompi)
        assertEquals(1000000000L, prepared.feeSompi)
        assertEquals(4000000000L, prepared.transaction.outputs[0].amount)
        assertEquals(5000000000L, prepared.transaction.outputs[1].amount)
    }

    @Test
    fun parsesFlatScriptPublicKeyOutputFields() {
        val userScript = "20" + publicKeyHex + "ac"
        val tx = JSONObject(transactionJson())
        val outputs = tx.getJSONArray("outputs")
        val first = outputs.getJSONObject(0)
        first.remove("scriptPublicKey")
        first.put("scriptPublicKey", userScript)

        val prepared = DappPsktTransaction.prepareForSigning(
            tx.toString(), JSONArray().put(0), publicKeyHex
        )

        assertEquals(userScript, prepared.transaction.outputs[0].scriptPublicKey.script)
    }

    @Test
    fun parsesWasmSafeJsonFlatVersionPrefixedInputAndOutputScriptsBeforeSigning() {
        val userScript = "20" + publicKeyHex + "ac"
        val json = JSONObject(transactionJson())
        val inputUtxo = json.getJSONArray("inputs").getJSONObject(0)
            .getJSONObject("utxoEntry")
        inputUtxo.put("scriptPublicKey", "0000" + userScript)
        val output = json.getJSONArray("outputs").getJSONObject(0)
        output.put("scriptPublicKey", "0000" + userScript)

        val prepared = DappPsktTransaction.prepareForSigning(
            json.toString(),
            JSONArray().put(0),
            publicKeyHex
        )

        assertEquals(0, prepared.utxoEntries[0].scriptPublicKey.version)
        assertEquals(userScript, prepared.utxoEntries[0].scriptPublicKey.script)
        assertEquals(0, prepared.transaction.outputs[0].scriptPublicKey.version)
        assertEquals(userScript, prepared.transaction.outputs[0].scriptPublicKey.script)

        val signed = JSONObject(DappPsktTransaction.sign(prepared, privateKey))
        val signature = signed.getJSONArray("inputs").getJSONObject(0)
            .getString("signatureScript").substring(2, 130)
        val sighash = KaspaTransactionEngine.calcSchnorrSignatureHash(
            prepared.transaction, 0, KaspaTransactionEngine.SIGHASH_ALL, prepared.utxoEntries[0]
        )
        assertTrue(CryptoUtils.verifySchnorrSignature(
            signature, sighash, CryptoUtils.hexToBytes(publicKeyHex)
        ))
    }

    @Test
    fun prefersValueAndFallsBackToAmountForOutputAmounts() {
        val userScript = "20" + publicKeyHex + "ac"
        val inputs = JSONArray().put(JSONObject()
            .put("previousOutpoint", JSONObject().put("transactionId", "ff".repeat(32)).put("index", 0))
            .put("signatureScript", "")
            .put("sequence", 0)
            .put("sigOpCount", 1)
            .put("utxoEntry", JSONObject()
                .put("amount", "10000000000")
                .put("scriptPublicKey", JSONObject().put("version", 0).put("script", userScript))
                .put("blockDaaScore", "1")
                .put("isCoinbase", false))
        )
        val outputs = JSONArray()
            .put(JSONObject().put("value", "3000000000").put("amount", "1")
                .put("scriptPublicKey", JSONObject().put("version", 0).put("script", userScript)))
            .put(JSONObject().put("amount", 6000000000L)
                .put("scriptPublicKey", JSONObject().put("version", 0).put("script", userScript)))
        val tx = JSONObject().put("inputs", inputs).put("outputs", outputs)

        val prepared = DappPsktTransaction.prepareForSigning(
            tx.toString(), JSONArray().put(0), publicKeyHex
        )

        assertEquals(9000000000L, prepared.outputSumSompi)
        assertEquals(3000000000L, prepared.transaction.outputs[0].amount)
        assertEquals(6000000000L, prepared.transaction.outputs[1].amount)
    }

    @Test
    fun versionOneSighashDoesNotCommitToSigOpCounts() {
        val script = "20" + publicKeyHex + "ac"
        val utxo = KaspaTransactionEngine.KaspaUtxoEntry(10_000_000L, KaspaTransactionEngine.KaspaScriptPublicKey(0, script))
        fun tx(count: Int) = KaspaTransactionEngine.KaspaTransaction(1,
            listOf(KaspaTransactionEngine.KaspaTransactionInput(KaspaTransactionEngine.KaspaOutpoint("11".repeat(32), 0), sequence = 7, sigOpCount = count)),
            listOf(KaspaTransactionEngine.KaspaTransactionOutput(9_400_000L, KaspaTransactionEngine.KaspaScriptPublicKey(0, script))))
        assertTrue(KaspaTransactionEngine.calcSchnorrSignatureHash(tx(1), 0, 1, utxo)
            .contentEquals(KaspaTransactionEngine.calcSchnorrSignatureHash(tx(99), 0, 1, utxo)))
    }

    @Test
    fun versionZeroSighashStillCommitsToSigOpCounts() {
        val script = "20" + publicKeyHex + "ac"
        val utxo = KaspaTransactionEngine.KaspaUtxoEntry(10_000_000L, KaspaTransactionEngine.KaspaScriptPublicKey(0, script))
        fun tx(count: Int) = KaspaTransactionEngine.KaspaTransaction(0,
            listOf(KaspaTransactionEngine.KaspaTransactionInput(KaspaTransactionEngine.KaspaOutpoint("22".repeat(32), 0), sequence = 7, sigOpCount = count)),
            listOf(KaspaTransactionEngine.KaspaTransactionOutput(9_400_000L, KaspaTransactionEngine.KaspaScriptPublicKey(0, script))))
        assertFalse(KaspaTransactionEngine.calcSchnorrSignatureHash(tx(1), 0, 1, utxo)
            .contentEquals(KaspaTransactionEngine.calcSchnorrSignatureHash(tx(99), 0, 1, utxo)))
    }

    @Test
    fun versionOneSighashNormalizesPrefixedUppercaseCovenantId() {
        val script = "20" + publicKeyHex + "ac"
        val utxo = KaspaTransactionEngine.KaspaUtxoEntry(10_000_000L, KaspaTransactionEngine.KaspaScriptPublicKey(0, script))
        fun hash(covenantId: String): ByteArray {
            val tx = KaspaTransactionEngine.KaspaTransaction(
                version = 1,
                inputs = listOf(KaspaTransactionEngine.KaspaTransactionInput(
                    KaspaTransactionEngine.KaspaOutpoint("33".repeat(32), 0), sequence = 7
                )),
                outputs = listOf(KaspaTransactionEngine.KaspaTransactionOutput(
                    9_400_000L,
                    KaspaTransactionEngine.KaspaScriptPublicKey(0, script),
                    KaspaTransactionEngine.KaspaCovenantBinding(0, covenantId)
                ))
            )
            return KaspaTransactionEngine.calcSchnorrSignatureHash(tx, 0, 1, utxo)
        }
        val canonical = "ab".repeat(32)
        assertTrue(hash(canonical).contentEquals(hash("0X" + canonical.uppercase())))
    }

    @Test(expected = IllegalArgumentException::class)
    fun versionOneSighashRejectsMalformedCovenantId() {
        val script = "20" + publicKeyHex + "ac"
        val utxo = KaspaTransactionEngine.KaspaUtxoEntry(10_000_000L, KaspaTransactionEngine.KaspaScriptPublicKey(0, script))
        val tx = KaspaTransactionEngine.KaspaTransaction(
            version = 1,
            inputs = listOf(KaspaTransactionEngine.KaspaTransactionInput(
                KaspaTransactionEngine.KaspaOutpoint("33".repeat(32), 0), sequence = 7
            )),
            outputs = listOf(KaspaTransactionEngine.KaspaTransactionOutput(
                9_400_000L,
                KaspaTransactionEngine.KaspaScriptPublicKey(0, script),
                KaspaTransactionEngine.KaspaCovenantBinding(0, "0x1234")
            ))
        )
        KaspaTransactionEngine.calcSchnorrSignatureHash(tx, 0, 1, utxo)
    }

    @Test
    fun versionOneSighashCommitsToCovenantPresenceAndFields() {
        val script = "20" + publicKeyHex + "ac"
        val utxo = KaspaTransactionEngine.KaspaUtxoEntry(10_000_000L, KaspaTransactionEngine.KaspaScriptPublicKey(0, script))
        fun tx(binding: KaspaTransactionEngine.KaspaCovenantBinding?) =
            KaspaTransactionEngine.KaspaTransaction(1,
                listOf(KaspaTransactionEngine.KaspaTransactionInput(KaspaTransactionEngine.KaspaOutpoint("33".repeat(32), 0), sequence = 7)),
                listOf(KaspaTransactionEngine.KaspaTransactionOutput(9_400_000L, KaspaTransactionEngine.KaspaScriptPublicKey(0, script), binding)))
        val absent = KaspaTransactionEngine.calcSchnorrSignatureHash(tx(null), 0, 1, utxo)
        val present = KaspaTransactionEngine.calcSchnorrSignatureHash(tx(KaspaTransactionEngine.KaspaCovenantBinding(0, "44".repeat(32))), 0, 1, utxo)
        val changed = KaspaTransactionEngine.calcSchnorrSignatureHash(tx(KaspaTransactionEngine.KaspaCovenantBinding(0, "55".repeat(32))), 0, 1, utxo)
        assertFalse(absent.contentEquals(present))
        assertFalse(present.contentEquals(changed))
    }

    @Test
    fun braidSwapKcc20SwapAutoResolvesRedeemScriptFromCacheAndSignsSuccessfully() {
        // Construct a realistic KCC-20 covenant note redeem script containing the user's public key
        // Format: <covenant header/state> + 20<publicKeyHex> + <covenant validator tail>
        val covenantPrefix = "6b20" + "ee".repeat(32)
        val userPubkeyPush = "20" + publicKeyHex
        val covenantTail = "8876a914" + "aa".repeat(20) + "88ac"
        val covenantRedeemScript = (covenantPrefix + userPubkeyPush + covenantTail).lowercase()
        assertTrue(covenantRedeemScript.length >= 68)

        // Calculate P2SH hash for the covenant note UTXO
        val p2shHash = CryptoUtils.blake2b256(
            CryptoUtils.hexToBytes(covenantRedeemScript)
        ).joinToString("") { "%02x".format(it) }
        val covenantLockingScript = "aa20" + p2shHash + "87"

        val txId = "4a".repeat(32)
        val vout = 0

        // Simulate Braid Swap PSKT spending this KCC-20 note UTXO at input 0
        val inputs = JSONArray().put(JSONObject()
            .put("previousOutpoint", JSONObject().put("transactionId", txId).put("index", vout))
            .put("signatureScript", "")
            .put("sequence", 0)
            .put("sigOpCount", 1)
            .put("utxoEntry", JSONObject()
                .put("amount", "2500000000") // 25 KAS
                .put("scriptPublicKey", JSONObject().put("version", 0).put("script", covenantLockingScript))
                .put("blockDaaScore", "100")
                .put("isCoinbase", false)
            )
        )
        // Output delivers KAS back to user wallet
        val outputs = JSONArray().put(JSONObject()
            .put("amount", "2207210000") // ~22.0721 KAS received
            .put("scriptPublicKey", JSONObject().put("version", 0).put("script", "20" + publicKeyHex + "ac"))
        )
        val tx = JSONObject()
            .put("version", 0)
            .put("inputs", inputs)
            .put("outputs", outputs)
            .put("lockTime", 0)
            .put("subnetworkId", KaspaTransactionEngine.DEFAULT_SUBNETWORK_ID)
            .put("gas", 0)
            .put("payload", "")
            .put("mass", 0)

        // Precondition: Kcc20NoteScanner registered the note redeem script into cache
        Kcc20NoteScanner.registerRedeemScript(txId, vout, p2shHash, covenantRedeemScript)

        // Braid Swap requests signature for input 0 WITHOUT specifying redeemScript in signInputs
        val signInputs = JSONArray().put(JSONObject().put("index", 0))

        val prepared = DappPsktTransaction.prepareForSigning(
            tx.toString(),
            signInputs,
            publicKeyHex
        )

        // Verify that redeemScript was automatically resolved
        assertEquals(covenantRedeemScript, prepared.signInputs[0].redeemScript)

        // Sign the prepared transaction with the wallet's private key
        val result = DappPsktTransaction.signAndFormat(prepared, privateKey)
        assertTrue(result.has("psktTransactionJson"))
        assertTrue(result.has("rawTx"))

        val signedTxJson = JSONObject(result.getString("psktTransactionJson"))
        val signedInput0 = signedTxJson.getJSONArray("inputs").getJSONObject(0)
        val sigScript = signedInput0.getString("signatureScript")

        // Signature script must contain the 65-byte Schnorr signature push + pushed covenant redeem script
        assertTrue("Signature script must not be empty", sigScript.isNotBlank())
        assertTrue("Signature script must start with Schnorr signature push", sigScript.startsWith("41"))
        assertTrue("Signature script must contain the pushed covenant redeem script", sigScript.contains(covenantRedeemScript))

        // Verify cryptographic Schnorr signature
        val schnorrSigHex = sigScript.substring(2, 130)
        val sighash = KaspaTransactionEngine.calcSchnorrSignatureHash(
            prepared.transaction, 0, KaspaTransactionEngine.SIGHASH_ALL, prepared.utxoEntries[0]
        )
        assertTrue(CryptoUtils.verifySchnorrSignature(schnorrSigHex, sighash, CryptoUtils.hexToBytes(publicKeyHex)))
    }

    @Test
    fun signsKcc20CovenantNoteWithExplicitRedeemScriptProvidedByDapp() {
        val covenantRedeemScript = ("6b20" + "ff".repeat(32) + "20" + publicKeyHex + "5152ac").lowercase()
        val p2shHash = CryptoUtils.blake2b256(CryptoUtils.hexToBytes(covenantRedeemScript))
            .joinToString("") { "%02x".format(it) }

        val inputs = JSONArray().put(JSONObject()
            .put("previousOutpoint", JSONObject().put("transactionId", "5b".repeat(32)).put("index", 0))
            .put("signatureScript", "")
            .put("sequence", 0)
            .put("utxoEntry", JSONObject()
                .put("amount", "10000000")
                .put("scriptPublicKey", JSONObject().put("version", 0).put("script", "aa20" + p2shHash + "87"))
            )
        )
        val outputs = JSONArray().put(JSONObject()
            .put("amount", "9000000")
            .put("scriptPublicKey", JSONObject().put("version", 0).put("script", "20" + publicKeyHex + "ac"))
        )
        val tx = JSONObject().put("inputs", inputs).put("outputs", outputs)

        val prepared = DappPsktTransaction.prepareForSigning(
            tx.toString(),
            JSONArray().put(JSONObject().put("index", 0).put("redeemScript", covenantRedeemScript)),
            publicKeyHex
        )
        assertEquals(covenantRedeemScript, prepared.signInputs[0].redeemScript)

        val signed = JSONObject(DappPsktTransaction.sign(prepared, privateKey))
        val sigScript = signed.getJSONArray("inputs").getJSONObject(0).getString("signatureScript")
        assertTrue(sigScript.contains(covenantRedeemScript))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCovenantNoteBelongingToAnotherWalletPublicKey() {
        val otherKey = "33".repeat(32)
        val covenantRedeemScript = ("6b20" + "11".repeat(32) + "20" + otherKey + "5152ac").lowercase()
        val p2shHash = CryptoUtils.blake2b256(CryptoUtils.hexToBytes(covenantRedeemScript))
            .joinToString("") { "%02x".format(it) }

        val inputs = JSONArray().put(JSONObject()
            .put("previousOutpoint", JSONObject().put("transactionId", "6c".repeat(32)).put("index", 0))
            .put("signatureScript", "")
            .put("sequence", 0)
            .put("utxoEntry", JSONObject()
                .put("amount", "10000000")
                .put("scriptPublicKey", JSONObject().put("version", 0).put("script", "aa20" + p2shHash + "87"))
            )
        )
        val outputs = JSONArray().put(JSONObject()
            .put("amount", "9000000")
            .put("scriptPublicKey", JSONObject().put("version", 0).put("script", "20" + publicKeyHex + "ac"))
        )
        val tx = JSONObject().put("inputs", inputs).put("outputs", outputs)

        // Attempt to sign input 0 with other wallet's key in redeemScript
        DappPsktTransaction.prepareForSigning(
            tx.toString(),
            JSONArray().put(JSONObject().put("index", 0).put("redeemScript", covenantRedeemScript)),
            publicKeyHex
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsP2shInputWhenRedeemScriptIsMissingAndNotCached() {
        val unknownP2shScript = "aa20" + "99".repeat(32) + "87"
        val inputs = JSONArray().put(JSONObject()
            .put("previousOutpoint", JSONObject().put("transactionId", "7d".repeat(32)).put("index", 0))
            .put("signatureScript", "")
            .put("sequence", 0)
            .put("utxoEntry", JSONObject()
                .put("amount", "10000000")
                .put("scriptPublicKey", JSONObject().put("version", 0).put("script", unknownP2shScript))
            )
        )
        val outputs = JSONArray().put(JSONObject()
            .put("amount", "9000000")
            .put("scriptPublicKey", JSONObject().put("version", 0).put("script", "20" + publicKeyHex + "ac"))
        )
        val tx = JSONObject().put("inputs", inputs).put("outputs", outputs)

        // Braid Swap sends signInputs without redeemScript and it's not in cache
        DappPsktTransaction.prepareForSigning(
            tx.toString(),
            JSONArray().put(JSONObject().put("index", 0)),
            publicKeyHex
        )
    }
}
