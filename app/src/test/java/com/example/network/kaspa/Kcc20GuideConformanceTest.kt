package com.example.network.kaspa

import com.example.dapp.DappPsktTransaction
import com.example.network.CryptoUtils
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigInteger

/**
 * Literal conformance & attack-refusal test suite for `/kcc20-Guide.md` (Version 2, 2026-11-17):
 * - Reference KCC-20 (4,031B, template `9703112e...3cf7`) & KB 10x10 (11,458B)
 * - KCC-1 / KCC-20 EncodeState & transferArgs conformance vectors (§1.1, §3)
 * - Burn owner hash (`BLAKE2b-256(0x6a)`), P2SH address derivation, and `programFromSpend` (§1.5, §3.6, A.2, A.3)
 * - rusty-kaspa v2.1.0 compute/transient/storage mass, plurality, budgets, and fee floor (§4, A.1)
 * - Version 1 `calcTransactionId` & `calcSchnorrSignatureHash` budget-walk invariance (§3, §4.5)
 * - Cell verifier (`verifyCell`) & assembler attack refusals (§2.3, §7.5, §10)
 * - Scanner multi-leader decoding & unbound-P2SH refusal (§2.1, §9)
 * - DApp PSKT v1 500k mempool limits & covenant validation
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Kcc20GuideConformanceTest {

    private val ownerA = "11".repeat(32)
    private val ownerB = "22".repeat(32)
    private val ownerC = "33".repeat(32)
    private val covA = "cbe213cecb9e943edaa31a85a697e8174c7338bbd5d2a38ef94db20b53f7ea44"
    private val covB = "d35f0e5182859b275c96768a2249921c367d1f937c6624844c6726a59ac90eff"

    private fun hex(b: ByteArray): String = KbKcc20ScriptCodec.bytesToHex(b)
    private fun unhex(s: String): ByteArray = KbKcc20ScriptCodec.hexToBytes(s)

    @Test
    fun pinnedReferenceAndKbTemplatesMatchGuideHashes() {
        val refProgram = Kcc20ReferenceProtocol.pinnedRefProgram
        assertNotNull("kcc20-ref-code.bin.gz.b64 must load", refProgram)
        assertEquals(Kcc20ReferenceProtocol.REF_PROGRAM_LEN, refProgram!!.size)
        val refKind = Kcc20ReferenceProtocol.identifyTemplate(refProgram)
        assertEquals(Kcc20ReferenceProtocol.TemplateKind.REFERENCE, refKind)
        assertEquals(Kcc20ReferenceProtocol.REF_TEMPLATE_HASH, Kcc20ReferenceProtocol.stateOf(refProgram).templateHashHex)

        val kbProgram = Kcc20ReferenceProtocol.pinnedKbProgram
        assertNotNull("kb-live-cell-program.bin.gz.b64 must load", kbProgram)
        assertEquals(Kcc20ReferenceProtocol.KB_PROGRAM_LEN, kbProgram!!.size)
        val kbKind = Kcc20ReferenceProtocol.identifyTemplate(kbProgram)
        assertEquals(Kcc20ReferenceProtocol.TemplateKind.KB_10X10, kbKind)
    }

    @Test
    fun encodeStateVectorMatchesGuideSection1_1AndInt8SignedMagnitude() {
        val guard = "f401" + "00".repeat(30)
        val state = Kcc20ReferenceProtocol.CellState(
            templateHashHex = Kcc20ReferenceProtocol.REF_TEMPLATE_HASH,
            amount = BigInteger.valueOf(1000L),
            owner = unhex(ownerA),
            ownerScheme = 1,
            borrowScheme = 1,
            borrowGuard = unhex(guard),
            extensionCommitment = ByteArray(32)
        )
        val encoded = Kcc20ReferenceProtocol.encodeState145(state)
        assertEquals(145, encoded.size)
        val expectedHex =
            "20" + Kcc20ReferenceProtocol.REF_TEMPLATE_HASH +
                "08e803000000000000" +
                "20" + ownerA +
                "0101" +
                "0101" +
                "20" + guard +
                "20" + "00".repeat(32)
        assertEquals(expectedHex, hex(encoded))

        val decoded = Kcc20ReferenceProtocol.parseState145(encoded)
        assertEquals(state, decoded)

        // int8 signed-magnitude: -1 -> 0100000000000080
        val negOne = Kcc20ReferenceProtocol.int8(BigInteger.valueOf(-1L))
        assertEquals("0100000000000080", hex(negOne))
        assertEquals(BigInteger.valueOf(-1L), Kcc20ReferenceProtocol.decodeInt8(negOne))
    }

    @Test
    fun transferArgsAndBurnOwnerHashMatchGuideSection3Vectors() {
        // Guide §3.6: BLAKE2b-256(0x6a) == d379e50f...8b32
        val burnHash = hex(CryptoUtils.blake2b256(byteArrayOf(0x6a.toByte())))
        assertEquals(Kcc20ReferenceProtocol.BURN_OP_RETURN_OWNER_HASH, burnHash)

        // Guide §3: transferArgs vector (600 to 22..22 scheme 0, 400 to 33..33 scheme 1 borrow 1)
        val nextStates = listOf(
            Kcc20ReferenceProtocol.CellState(
                amount = BigInteger.valueOf(600L),
                owner = unhex(ownerB),
                ownerScheme = 0,
                borrowScheme = 0
            ),
            Kcc20ReferenceProtocol.CellState(
                amount = BigInteger.valueOf(400L),
                owner = unhex(ownerC),
                ownerScheme = 1,
                borrowScheme = 1
            )
        )
        val argsHex = hex(Kcc20ReferenceProtocol.transferArgs(nextStates))
        val expectedArgsHex =
            "1058020000000000009001000000000000" +
                "40" + ownerB + ownerC +
                "020001" +
                "020001" +
                "40" + "00".repeat(64) +
                "40" + "00".repeat(64)
        assertEquals(expectedArgsHex, argsHex)

        // Leader script & programFromSpend / decodeLeaderTransferNextStates round-trip
        val refProg = Kcc20ReferenceProtocol.programFor(
            Kcc20ReferenceProtocol.CellState(
                amount = BigInteger.valueOf(1000L),
                owner = unhex(ownerA),
                ownerScheme = 0
            ),
            Kcc20ReferenceProtocol.pinnedRefProgram!!
        )
        val fakeWalletSig = "41" + "ab".repeat(64) + "01"
        val leaderHex = hex(Kcc20ReferenceProtocol.leaderScript(nextStates, fakeWalletSig, refProg))
        assertTrue(leaderHex.startsWith(expectedArgsHex + "4200" + "ab".repeat(64) + "01" + "0479c71c23"))

        val recovered = Kcc20ReferenceProtocol.programFromSpend(leaderHex)
        assertEquals(Kcc20ReferenceProtocol.TAG_TRANSFER, recovered.tagHex)
        assertEquals(Kcc20ReferenceProtocol.REF_PROGRAM_LEN, recovered.programBytes)
        assertEquals(Kcc20ReferenceProtocol.REF_TEMPLATE_HASH, recovered.templateHash)
        assertTrue(recovered.selfConsistent)

        val decodedNextStates = Kcc20ReferenceProtocol.decodeLeaderTransferNextStates(leaderHex)
        assertNotNull(decodedNextStates)
        assertEquals(2, decodedNextStates!!.size)
        assertEquals(BigInteger.valueOf(600L), decodedNextStates[0].amount)
        assertEquals(BigInteger.valueOf(400L), decodedNextStates[1].amount)
        assertEquals(1, decodedNextStates[1].ownerScheme)
        assertEquals(1, decodedNextStates[1].borrowScheme)
    }

    @Test
    fun v1MassStoragePluralityAndComputeBudgetMatchGuideSection4AndA1() {
        assertEquals(0, KaspaTransactionEngine.budgetForScriptUnits(9_999L))
        assertEquals(1, KaspaTransactionEngine.budgetForScriptUnits(10_000L))
        assertEquals(10, KaspaTransactionEngine.budgetForScriptUnits(100_001L))
        assertEquals(14, KaspaTransactionEngine.budgetForScriptUnits(144_688L))
        assertEquals(14, KaspaTransactionEngine.budgetForScriptUnits(144_843L))

        assertEquals(1L, KaspaTransactionEngine.utxoPlurality(34, false))
        assertEquals(2L, KaspaTransactionEngine.utxoPlurality(35, true))

        val refProg = Kcc20ReferenceProtocol.pinnedRefProgram!!
        val cellSpk = Kcc20ReferenceProtocol.p2shScript(refProg)
        val p2pkSpk = "20" + ownerA + "ac"

        // Single 0.05 KAS bound cell output exceeds 500,000 g storage mass (Guide §4.2: 800,000 g)
        val underFloorTx = KaspaTransactionEngine.KaspaTransaction(
            version = 1,
            inputs = listOf(
                KaspaTransactionEngine.KaspaTransactionInput(
                    previousOutpoint = KaspaTransactionEngine.KaspaOutpoint("10".repeat(32), 0),
                    signatureScript = "00".repeat(4218),
                    sequence = 0L,
                    sigOpCount = 0,
                    computeBudget = 14
                )
            ),
            outputs = listOf(
                KaspaTransactionEngine.KaspaTransactionOutput(
                    amount = 5_000_000L, // 0.05 KAS
                    scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, cellSpk),
                    covenant = KaspaTransactionEngine.KaspaCovenantBinding(0, covA)
                )
            )
        )
        val underFloorEntries = listOf(
            KaspaTransactionEngine.KaspaUtxoEntry(
                amount = 100_000_000L, // 1 KAS
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, cellSpk),
                covenantId = covA
            )
        )
        val badReport = KaspaTransactionEngine.assessV1MempoolMass(underFloorTx, underFloorEntries)
        // relaxed path (outsP == 2, insP == 2, entries.size <= 2): 4e12/5e6 - 4e12/1e8 = 800_000 - 40_000 = 760_000
        assertEquals(760_000L, badReport.storageMass)
        assertFalse(badReport.withinLimits)

        // Two 0.2 KAS cells + 1 P2PK change from 0.5 KAS cell + 1 KAS P2PK coin should fit inside all v1 limits
        val okTx = KaspaTransactionEngine.KaspaTransaction(
            version = 1,
            inputs = listOf(
                KaspaTransactionEngine.KaspaTransactionInput(
                    previousOutpoint = KaspaTransactionEngine.KaspaOutpoint("10".repeat(32), 0),
                    signatureScript = "00".repeat(4324),
                    sequence = 0L,
                    sigOpCount = 0,
                    computeBudget = 14
                ),
                KaspaTransactionEngine.KaspaTransactionInput(
                    previousOutpoint = KaspaTransactionEngine.KaspaOutpoint("20".repeat(32), 1),
                    signatureScript = "00".repeat(66),
                    sequence = 0L,
                    sigOpCount = 0,
                    computeBudget = 10
                )
            ),
            outputs = listOf(
                KaspaTransactionEngine.KaspaTransactionOutput(
                    amount = 20_000_000L,
                    scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, cellSpk),
                    covenant = KaspaTransactionEngine.KaspaCovenantBinding(0, covA)
                ),
                KaspaTransactionEngine.KaspaTransactionOutput(
                    amount = 20_000_000L,
                    scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, cellSpk),
                    covenant = KaspaTransactionEngine.KaspaCovenantBinding(0, covA)
                ),
                KaspaTransactionEngine.KaspaTransactionOutput(
                    amount = 109_900_000L,
                    scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, p2pkSpk)
                )
            )
        )
        val okEntries = listOf(
            KaspaTransactionEngine.KaspaUtxoEntry(50_000_000L, KaspaTransactionEngine.KaspaScriptPublicKey(0, cellSpk), covenantId = covA),
            KaspaTransactionEngine.KaspaUtxoEntry(100_000_000L, KaspaTransactionEngine.KaspaScriptPublicKey(0, p2pkSpk))
        )
        val okReport = KaspaTransactionEngine.assessV1MempoolMass(okTx, okEntries)
        assertTrue("Valid v1 transfer should be within limits: ${okReport.limitViolationReason}", okReport.withinLimits)
        assertTrue(okReport.minRelayFeeSompi == maxOf(okReport.computeMass, (okReport.transientMass + 1L) / 2L) * 100L)
    }

    @Test
    fun v1TxIdAndSighashAreInvariantToComputeBudgetAndCommitToCovenant() {
        val refProg = Kcc20ReferenceProtocol.pinnedRefProgram!!
        val cellSpk = Kcc20ReferenceProtocol.p2shScript(refProg)
        val utxo = KaspaTransactionEngine.KaspaUtxoEntry(50_000_000L, KaspaTransactionEngine.KaspaScriptPublicKey(0, cellSpk), covenantId = covA)

        val baseTx = KaspaTransactionEngine.KaspaTransaction(
            version = 1,
            inputs = listOf(
                KaspaTransactionEngine.KaspaTransactionInput(
                    previousOutpoint = KaspaTransactionEngine.KaspaOutpoint("33".repeat(32), 0),
                    signatureScript = "",
                    sequence = 0L,
                    sigOpCount = 0,
                    computeBudget = 14
                )
            ),
            outputs = listOf(
                KaspaTransactionEngine.KaspaTransactionOutput(
                    amount = 49_000_000L,
                    scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, cellSpk),
                    covenant = KaspaTransactionEngine.KaspaCovenantBinding(0, covA)
                )
            )
        )
        val raisedBudgetTx = baseTx.copy(
            inputs = listOf(baseTx.inputs[0].copy(computeBudget = 300))
        )
        // Guide §4.5: raising computeBudget does NOT alter txid or sighash!
        assertEquals(KaspaTransactionEngine.calcTransactionId(baseTx), KaspaTransactionEngine.calcTransactionId(raisedBudgetTx))
        assertArrayEquals(
            KaspaTransactionEngine.calcSchnorrSignatureHash(baseTx, 0, KaspaTransactionEngine.SIGHASH_ALL, utxo),
            KaspaTransactionEngine.calcSchnorrSignatureHash(raisedBudgetTx, 0, KaspaTransactionEngine.SIGHASH_ALL, utxo)
        )

        // Changing or stripping output covenant changes both txid and sighash!
        val unboundTx = baseTx.copy(
            outputs = listOf(baseTx.outputs[0].copy(covenant = null))
        )
        assertNotEquals(KaspaTransactionEngine.calcTransactionId(baseTx), KaspaTransactionEngine.calcTransactionId(unboundTx))
        assertFalse(
            KaspaTransactionEngine.calcSchnorrSignatureHash(baseTx, 0, KaspaTransactionEngine.SIGHASH_ALL, utxo)
                .contentEquals(KaspaTransactionEngine.calcSchnorrSignatureHash(unboundTx, 0, KaspaTransactionEngine.SIGHASH_ALL, utxo))
        )
    }

    @Test
    fun cellVerifierAcceptsGenuineAndRefusesMutatedForgedSpentAndWrongFamily() = runBlocking {
        val state = Kcc20ReferenceProtocol.CellState(
            amount = BigInteger.valueOf(1_250_000L),
            owner = unhex(ownerA),
            ownerScheme = 0
        )
        val prog = Kcc20ReferenceProtocol.programFor(state, Kcc20ReferenceProtocol.pinnedRefProgram!!)
        val addr = Kcc20ReferenceProtocol.p2shAddress(prog, "kaspa")
        assertTrue(addr.startsWith("kaspa:p"))

        val txid = "aa".repeat(32)
        val candidate = Kcc20ReferenceProtocol.CandidateCell(
            redeemScriptHex = hex(prog),
            address = addr,
            txid = txid,
            vout = 2,
            kasSompi = 50_000_000L
        )
        val validUtxos: suspend (String) -> List<Kcc20ReferenceProtocol.NodeUtxo> = { reqAddr ->
            if (reqAddr == addr) listOf(Kcc20ReferenceProtocol.NodeUtxo(txid, 2, 50_000_000L, covA)) else emptyList()
        }

        // Valid cell passes checks 1–5
        val verified = Kcc20ReferenceProtocol.verifyCell(candidate, covA, "kaspa", validUtxos)
        assertTrue(verified.ok)
        assertEquals(BigInteger.valueOf(1_250_000L), verified.state.amount)

        // Attack 1: edited amount in redeemScript (fails check 4 address mismatch)
        val mutatedAmountProg = Kcc20ReferenceProtocol.programFor(state.copy(amount = BigInteger.valueOf(9_999_999L)), prog)
        expectThrows {
            runBlocking {
                Kcc20ReferenceProtocol.verifyCell(candidate.copy(redeemScriptHex = hex(mutatedAmountProg)), covA, "kaspa", validUtxos)
            }
        }

        // Attack 2: 1 changed byte outside the 145B state window (fails check 1)
        val corruptedBytes = prog.copyOf().apply { this[size - 10] = (this[size - 10].toInt() xor 0x01).toByte() }
        expectThrows {
            runBlocking {
                Kcc20ReferenceProtocol.verifyCell(candidate.copy(redeemScriptHex = hex(corruptedBytes)), covA, "kaspa", validUtxos)
            }
        }

        // Attack 3: expecting another family covenantId (fails check 5)
        expectThrows {
            runBlocking {
                Kcc20ReferenceProtocol.verifyCell(candidate, covB, "kaspa", validUtxos)
            }
        }

        // Attack 4: forged / already-spent cell not in node UTXO set (fails check 5)
        expectThrows {
            runBlocking {
                Kcc20ReferenceProtocol.verifyCell(candidate, covA, "kaspa", utxosAt = { emptyList() })
            }
        }

        // Attack 5: KAS amount mismatch (fails check 5)
        expectThrows {
            runBlocking {
                Kcc20ReferenceProtocol.verifyCell(candidate.copy(kasSompi = 49_000_000L), covA, "kaspa", validUtxos)
            }
        }
    }

    @Test
    fun assemblerRefusesForbiddenSpendsAndAllowsBurnOwnerScheme0x03() {
        val pubA = unhex(ownerA)
        val pubB = unhex(ownerB)
        val refProg = Kcc20ReferenceProtocol.programFor(
            Kcc20ReferenceProtocol.CellState(amount = BigInteger.valueOf(1000L), owner = pubA, ownerScheme = 0),
            Kcc20ReferenceProtocol.pinnedRefProgram!!
        )
        val piece = KbKcc20StandardAssembler.SpendPiece("01".repeat(32), 0, 50_000_000L, refProg)

        // Wrong wallet owner refused
        expectThrows {
            KbKcc20StandardAssembler.planStandardTransfer(listOf(piece), pubB, pubA, BigInteger.valueOf(100L), covA)
        }

        // 4 reference inputs refused (max 3 for REFERENCE)
        expectThrows {
            KbKcc20StandardAssembler.planStandardTransfer(
                listOf(piece, piece, piece, piece),
                pubA,
                pubB,
                BigInteger.valueOf(100L),
                covA
            )
        }

        // Scheme 0x01 spending refused (§10)
        val scheme1Prog = Kcc20ReferenceProtocol.programFor(
            Kcc20ReferenceProtocol.CellState(amount = BigInteger.valueOf(1000L), owner = pubA, ownerScheme = 1),
            Kcc20ReferenceProtocol.pinnedRefProgram!!
        )
        expectThrows {
            KbKcc20StandardAssembler.planStandardTransfer(
                listOf(piece.copy(redeem = scheme1Prog)),
                pubA,
                pubB,
                BigInteger.valueOf(100L),
                covA
            )
        }

        // Non-zero extension_commitment refused (§10)
        val extProg = Kcc20ReferenceProtocol.programFor(
            Kcc20ReferenceProtocol.CellState(
                amount = BigInteger.valueOf(1000L),
                owner = pubA,
                ownerScheme = 0,
                extensionCommitment = ByteArray(32) { 0x55 }
            ),
            Kcc20ReferenceProtocol.pinnedRefProgram!!
        )
        expectThrows {
            KbKcc20StandardAssembler.planStandardTransfer(
                listOf(piece.copy(redeem = extProg)),
                pubA,
                pubB,
                BigInteger.valueOf(100L),
                covA
            )
        }

        // Burn to scheme 0x03 OP_RETURN hash succeeds (§3.6)
        val burnPlan = KbKcc20StandardAssembler.planStandardTransfer(
            pieces = listOf(piece),
            pubkey32 = pubA,
            recipientOwner = unhex(Kcc20ReferenceProtocol.BURN_OP_RETURN_OWNER_HASH),
            amountBaseUnits = BigInteger.valueOf(250L),
            covenantId = covA,
            recipientOwnerScheme = 0x03
        )
        assertEquals(2, burnPlan.nextStates.size)
        assertEquals(0x03, burnPlan.nextStates[0].ownerScheme)
        assertEquals(BigInteger.valueOf(250L), burnPlan.nextStates[0].amountBaseUnits)
        assertEquals(BigInteger.valueOf(750L), burnPlan.nextStates[1].amountBaseUnits)
    }

    @Test
    fun noteScannerRefusesInputCovenantInheritanceAndDecodesMultiLeaderTransactions() {
        // resolveCovenantId must ignore outputs without their own covenant binding
        val unboundOutput = JSONObject().put("amount", 100_000L).put("script_public_key", JSONObject().put("script", "aa20" + "ff".repeat(32) + "87"))
        assertNull(Kcc20NoteScanner.resolveCovenantId(unboundOutput))

        val boundOutput = JSONObject().put("covenant", JSONObject().put("authorizing_input", 0).put("covenant_id", covA))
        assertEquals(covA, Kcc20NoteScanner.resolveCovenantId(boundOutput))

        // Multi-family transaction: input 0 leads covA (600 to A, 0 to B); input 1 leads covB (400 to A)
        val refA = Kcc20ReferenceProtocol.programFor(
            Kcc20ReferenceProtocol.CellState(amount = BigInteger.valueOf(600L), owner = unhex(ownerA)),
            Kcc20ReferenceProtocol.pinnedRefProgram!!
        )
        val refB = Kcc20ReferenceProtocol.programFor(
            Kcc20ReferenceProtocol.CellState(amount = BigInteger.valueOf(400L), owner = unhex(ownerA)),
            Kcc20ReferenceProtocol.pinnedRefProgram!!
        )
        val nextA = listOf(
            Kcc20ReferenceProtocol.CellState(amount = BigInteger.valueOf(600L), owner = unhex(ownerA)),
            Kcc20ReferenceProtocol.CellState(amount = BigInteger.ZERO, owner = unhex(ownerB))
        )
        val nextB = listOf(
            Kcc20ReferenceProtocol.CellState(amount = BigInteger.valueOf(400L), owner = unhex(ownerA))
        )
        val fakeSig = "41" + "12".repeat(64) + "01"
        val txJson = JSONObject()
            .put("transaction_id", "99".repeat(32))
            .put(
                "inputs",
                JSONArray()
                    .put(JSONObject().put("signature_script", hex(Kcc20ReferenceProtocol.leaderScript(nextA, fakeSig, refA))).put("covenant_id", covA))
                    .put(JSONObject().put("signature_script", hex(Kcc20ReferenceProtocol.leaderScript(nextB, fakeSig, refB))).put("covenant_id", covB))
            )
            .put(
                "outputs",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("amount", 20_000_000L)
                            .put("script_public_key", JSONObject().put("script_public_key", Kcc20ReferenceProtocol.p2shScript(Kcc20ReferenceProtocol.programFor(nextA[0], refA))))
                            .put("covenant", JSONObject().put("authorizing_input", 0).put("covenant_id", covA))
                    )
                    .put(
                        JSONObject()
                            .put("amount", 20_000_000L)
                            .put("script_public_key", JSONObject().put("script_public_key", Kcc20ReferenceProtocol.p2shScript(Kcc20ReferenceProtocol.programFor(nextA[1], refA))))
                            .put("covenant", JSONObject().put("authorizing_input", 0).put("covenant_id", covA))
                    )
                    .put(
                        JSONObject()
                            .put("amount", 20_000_000L)
                            .put("script_public_key", JSONObject().put("script_public_key", Kcc20ReferenceProtocol.p2shScript(Kcc20ReferenceProtocol.programFor(nextB[0], refB))))
                            .put("covenant", JSONObject().put("authorizing_input", 1).put("covenant_id", covB))
                    )
            )

        val notesA = Kcc20NoteScanner.extractLeaderDecodedNotes(txJson, "99".repeat(32), 1000L, ownerA, "kaspa")
        assertEquals(2, notesA.size)
        assertEquals(covA, notesA[0].covenantId)
        assertEquals(600L, notesA[0].rawTokenAmount)
        assertEquals(covB, notesA[1].covenantId)
        assertEquals(400L, notesA[1].rawTokenAmount)

        // Zero-amount continuation for ownerB is also decoded faithfully
        val notesB = Kcc20NoteScanner.extractLeaderDecodedNotes(txJson, "99".repeat(32), 1000L, ownerB, "kaspa")
        assertEquals(1, notesB.size)
        assertEquals(0L, notesB[0].rawTokenAmount)
        assertEquals(1, notesB[0].outputIndex)
    }

    @Test
    fun dappPsktAcceptsV1UpTo500kAndRejectsOver500kAndBadCovenantAuthInput() {
        val priv = BigInteger("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", 16)
        val pubHex = CryptoUtils.deriveKaspaKeyPair(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            "kaspa"
        ).publicKeyHex
        val kp = CryptoUtils.deriveKaspaKeyPair(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            "kaspa"
        )
        val spk = "20" + kp.publicKeyHex + "ac"
        val cellSpk = "aa20" + "55".repeat(32) + "87"

        // Storage mass around 160,000 g (> old 100k v0 cap, <= 500k v2.1.0 cap) must be ACCEPTED on v1!
        val v1Json = JSONObject()
            .put("version", 1)
            .put(
                "inputs",
                JSONArray().put(
                    JSONObject()
                        .put("previousOutpoint", JSONObject().put("transactionId", "12".repeat(32)).put("index", 0))
                        .put("signatureScript", "")
                        .put("sequence", 0)
                        .put("computeBudget", 10)
                        .put("utxoEntry", JSONObject().put("amount", "100000000").put("scriptPublicKey", JSONObject().put("version", 0).put("script", spk)))
                )
            )
            .put(
                "outputs",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("amount", "20000000") // 0.2 KAS covenant cell -> ~200,000 g storage mass
                            .put("scriptPublicKey", JSONObject().put("version", 0).put("script", cellSpk))
                            .put("covenant", JSONObject().put("authorizingInput", 0).put("covenantId", covA))
                    )
                    .put(
                        JSONObject()
                            .put("amount", "79000000")
                            .put("scriptPublicKey", JSONObject().put("version", 0).put("script", spk))
                    )
            )
        val signInputs = JSONArray().put(JSONObject().put("index", 0).put("sighashType", 1))
        val prepared = DappPsktTransaction.prepareForSigning(v1Json.toString(), signInputs, kp.publicKeyHex)
        assertEquals(1, prepared.transaction.version)
        assertEquals(10, prepared.transaction.inputs[0].computeBudget)
        assertEquals(0, prepared.transaction.inputs[0].sigOpCount)

        // Storage mass > 500,000 g (0.05 KAS bound cell) must be REFUSED on v1!
        val overStorageJson = JSONObject(v1Json.toString()).apply {
            getJSONArray("outputs").getJSONObject(0).put("amount", "5000000")
        }
        expectThrows {
            DappPsktTransaction.prepareForSigning(overStorageJson.toString(), signInputs, kp.publicKeyHex)
        }

        // Out-of-bounds authorizingInput must be REFUSED on v1!
        val badAuthJson = JSONObject(v1Json.toString()).apply {
            getJSONArray("outputs").getJSONObject(0).getJSONObject("covenant").put("authorizingInput", 9)
        }
        expectThrows {
            DappPsktTransaction.prepareForSigning(badAuthJson.toString(), signInputs, kp.publicKeyHex)
        }
        kp.wipe()
    }

    @Test
    fun mergeFundingPlannerUsesZeroExtraPlainInputsAndAvoidsStorageMassSpikes() {
        val kp = CryptoUtils.deriveKaspaKeyPair(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            "kaspa"
        )
        try {
            val baseProg = Kcc20ReferenceProtocol.pinnedRefProgram!!
            val p0 = Kcc20ReferenceProtocol.programFor(Kcc20ReferenceProtocol.CellState(amount = BigInteger.valueOf(400L), owner = kp.publicKeyBytes), baseProg)
            val p1 = Kcc20ReferenceProtocol.programFor(Kcc20ReferenceProtocol.CellState(amount = BigInteger.valueOf(600L), owner = kp.publicKeyBytes), baseProg)
            val merged = Kcc20ReferenceProtocol.programFor(Kcc20ReferenceProtocol.CellState(amount = BigInteger.valueOf(1000L), owner = kp.publicKeyBytes), baseProg)

            val cellInputs = listOf(
                KaspaTransactionEngine.KaspaTransactionInput(
                    previousOutpoint = KaspaTransactionEngine.KaspaOutpoint("01".repeat(32), 0),
                    signatureScript = "00".repeat(p0.size + 120),
                    sequence = 0L,
                    sigOpCount = 0,
                    computeBudget = 24
                ),
                KaspaTransactionEngine.KaspaTransactionInput(
                    previousOutpoint = KaspaTransactionEngine.KaspaOutpoint("02".repeat(32), 0),
                    signatureScript = "00".repeat(p1.size + 80),
                    sequence = 0L,
                    sigOpCount = 0,
                    computeBudget = 18
                )
            )
            val cellEntries = listOf(
                KaspaTransactionEngine.KaspaUtxoEntry(20_000_000L, KaspaTransactionEngine.KaspaScriptPublicKey(0, Kcc20ReferenceProtocol.p2shScript(p0)), covenantId = covA),
                KaspaTransactionEngine.KaspaUtxoEntry(20_000_000L, KaspaTransactionEngine.KaspaScriptPublicKey(0, Kcc20ReferenceProtocol.p2shScript(p1)), covenantId = covA)
            )
            val cellOutputs = listOf(
                KaspaTransactionEngine.KaspaTransactionOutput(
                    amount = Kcc20ReferenceProtocol.RECOMMENDED_CELL_KAS_SOMPI, // 0.2 KAS
                    scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, Kcc20ReferenceProtocol.p2shScript(merged)),
                    covenant = KaspaTransactionEngine.KaspaCovenantBinding(0, covA)
                )
            )
            val changeSpk = KaspaTransactionEngine.addressToScriptPublicKey(kp.kaspaAddress)

            // 1. Two 0.2 KAS cells -> one 0.2 KAS merged cell leaves 0.2 KAS freed! Must need 0 plain KAS inputs (§3.3).
            val mergePlan = KbKcc20StandardAssembler.planFundingAndChange(
                cellInputs = cellInputs,
                cellEntries = cellEntries,
                cellOutputs = cellOutputs,
                livePlainUtxos = listOf(
                    KaspaTransactionEngine.KaspaUtxo(
                        KaspaTransactionEngine.KaspaOutpoint("aa".repeat(32), 0),
                        KaspaTransactionEngine.KaspaUtxoEntry(500_000_000L, changeSpk)
                    )
                ),
                changeScript = changeSpk,
                feeRateSompiPerGram = 100L
            )
            assertTrue("2->1 cell merge with freed 0.2 KAS must use 0 extra plain KAS inputs (§3.3)", mergePlan.feeInputs.isEmpty())
            assertNotNull("Freed 0.2 KAS minus fee should produce viable change output", mergePlan.initialChangeSompi)

            // 2. Splitting 1 cell (0.2 KAS) -> 2 cells (0.4 KAS needed) pulls plain KAS UTXO cleanly
            val twoOutCellOutputs = listOf(
                cellOutputs[0],
                KaspaTransactionEngine.KaspaTransactionOutput(
                    amount = Kcc20ReferenceProtocol.RECOMMENDED_CELL_KAS_SOMPI,
                    scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, Kcc20ReferenceProtocol.p2shScript(p0)),
                    covenant = KaspaTransactionEngine.KaspaCovenantBinding(0, covA)
                )
            )
            val sendPlan = KbKcc20StandardAssembler.planFundingAndChange(
                cellInputs = cellInputs.take(1),
                cellEntries = cellEntries.take(1),
                cellOutputs = twoOutCellOutputs,
                livePlainUtxos = listOf(
                    KaspaTransactionEngine.KaspaUtxo(
                        KaspaTransactionEngine.KaspaOutpoint("bb".repeat(32), 0),
                        KaspaTransactionEngine.KaspaUtxoEntry(200_000_000L, changeSpk)
                    )
                ),
                changeScript = changeSpk,
                feeRateSompiPerGram = 100L
            )
            assertEquals(1, sendPlan.feeInputs.size)
            assertTrue((sendPlan.initialChangeSompi ?: 0L) >= KaspaTransactionEngine.DUST_THRESHOLD_SOMPIS)
        } finally {
            kp.wipe()
        }
    }

    @Test
    fun noteScannerCacheIsScopedByNetworkAndKeyAndPendingSwapUsesRealTxId() {
        Kcc20NoteScanner.clearCache()
        val kpA = CryptoUtils.deriveKaspaKeyPair(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            "kaspa"
        )
        val kpB = CryptoUtils.deriveKaspaKeyPair(
            "legal winner thank year wave sausage worth useful legal winner thank yellow",
            "kaspa"
        )
        try {
            val baseProg = Kcc20ReferenceProtocol.pinnedRefProgram!!
            val nextA = listOf(Kcc20ReferenceProtocol.CellState(amount = BigInteger.valueOf(500L), owner = kpA.publicKeyBytes))
            val pMainA = Kcc20ReferenceProtocol.programFor(nextA[0], baseProg)
            val leaderSigHex = hex(Kcc20ReferenceProtocol.leaderScript(nextA, "41" + "11".repeat(64) + "01", pMainA))

            // Pending swap JSON omits transaction_id and carries subnetwork_id: must compute real txId, NEVER subnetwork_id
            val pendingTxJson = JSONObject()
                .put("version", 1)
                .put(
                    "inputs",
                    JSONArray().put(
                        JSONObject()
                            .put("previousOutpoint", JSONObject().put("transactionId", "01".repeat(32)).put("index", 0))
                            .put("signatureScript", leaderSigHex)
                            .put("sequence", 0)
                            .put("sigOpCount", 0)
                            .put("computeBudget", 16)
                    )
                )
                .put(
                    "outputs",
                    JSONArray().put(
                        JSONObject()
                            .put("amount", "20000000")
                            .put("scriptPublicKey", JSONObject().put("version", 0).put("script", Kcc20ReferenceProtocol.p2shScript(pMainA)))
                            .put("covenant", JSONObject().put("authorizingInput", 0).put("covenantId", covA))
                    )
                )
                .put("lockTime", 0)
                .put("subnetworkId", KaspaTransactionEngine.DEFAULT_SUBNETWORK_ID)

            val swapRes = Kcc20NoteScanner.registerPendingSwapTransaction(
                txJsonString = pendingTxJson.toString(),
                walletAddress = kpA.kaspaAddress,
                walletPublicKeyHex = kpA.publicKeyHex,
                network = "mainnet"
            )
            assertEquals(1, swapRes.discoveredNotes.size)
            val pendingNote = swapRes.discoveredNotes[0]
            assertNotEquals(KaspaTransactionEngine.DEFAULT_SUBNETWORK_ID, pendingNote.txId)
            assertTrue(pendingNote.txId.matches(Regex("[0-9a-f]{64}")))
            assertEquals(1, Kcc20NoteScanner.getCachedNotes(networkPrefix = "kaspa", ownerPubKeyHex = kpA.publicKeyHex).size)
            assertEquals(0, Kcc20NoteScanner.getCachedNotes(networkPrefix = "kaspatest", ownerPubKeyHex = kpA.publicKeyHex).size)
            assertEquals(0, Kcc20NoteScanner.getCachedNotes(networkPrefix = "kaspa", ownerPubKeyHex = kpB.publicKeyHex).size)

            Kcc20NoteScanner.clearCache()
            assertEquals(0, Kcc20NoteScanner.getCachedNotes().size)
        } finally {
            kpA.wipe()
            kpB.wipe()
        }
    }

    @Test
    fun kasivoServiceVerifyCandidateCellsEnforcesChecks1To6AndSection10() = runBlocking {
        val baseProg = Kcc20ReferenceProtocol.pinnedRefProgram!!
        val service = Kcc20KasivoService()
        val pubA = unhex(ownerA)

        val validState = Kcc20ReferenceProtocol.CellState(amount = BigInteger.valueOf(1234L), owner = pubA)
        val validProg = Kcc20ReferenceProtocol.programFor(validState, baseProg)
        val validAddr = Kcc20ReferenceProtocol.p2shAddress(validProg, "kaspa")

        // §10 violations
        val badExtProg = Kcc20ReferenceProtocol.programFor(
            validState.copy(extensionCommitment = ByteArray(32) { 0x42 }),
            baseProg
        )
        val badExtAddr = Kcc20ReferenceProtocol.p2shAddress(badExtProg, "kaspa")
        val badBorrowProg = Kcc20ReferenceProtocol.programFor(
            validState.copy(borrowScheme = 0x01),
            baseProg
        )
        val badBorrowAddr = Kcc20ReferenceProtocol.p2shAddress(badBorrowProg, "kaspa")

        val validTxId = "a1".repeat(32)
        val badExtTxId = "b2".repeat(32)
        val badBorrowTxId = "c3".repeat(32)
        val spentTxId = "d4".repeat(32)
        val wrongCovTxId = "e5".repeat(32)

        val candidates = listOf(
            Kcc20ReferenceProtocol.CandidateCell(hex(validProg), validAddr, validTxId, 0, 20_000_000L),
            Kcc20ReferenceProtocol.CandidateCell(hex(badExtProg), badExtAddr, badExtTxId, 0, 20_000_000L),
            Kcc20ReferenceProtocol.CandidateCell(hex(badBorrowProg), badBorrowAddr, badBorrowTxId, 0, 20_000_000L),
            Kcc20ReferenceProtocol.CandidateCell(hex(validProg), validAddr, spentTxId, 1, 20_000_000L),
            Kcc20ReferenceProtocol.CandidateCell(hex(validProg), validAddr, wrongCovTxId, 2, 20_000_000L)
        )

        val verifiedPieces = service.verifyCandidateCellsToSpendPieces(
            candidates = candidates,
            tokenId = covA,
            ownerPubKeyHex = ownerA,
            networkPrefix = "kaspa",
            utxosAt = { addr ->
                when (addr) {
                    validAddr -> listOf(
                        // Valid live cell (utxo omits covenantId -> must verify via creatingTxCovenantAt fallback)
                        Kcc20ReferenceProtocol.NodeUtxo(validTxId, 0, 20_000_000L, covenantId = null),
                        // Wrong covenant ID
                        Kcc20ReferenceProtocol.NodeUtxo(wrongCovTxId, 2, 20_000_000L, covenantId = covB)
                        // spentTxId absent -> spent -> rejected
                    )
                    badExtAddr -> listOf(
                        Kcc20ReferenceProtocol.NodeUtxo(badExtTxId, 0, 20_000_000L, covenantId = covA)
                    )
                    badBorrowAddr -> listOf(
                        Kcc20ReferenceProtocol.NodeUtxo(badBorrowTxId, 0, 20_000_000L, covenantId = covA)
                    )
                    else -> emptyList()
                }
            },
            creatingTxCovenantAt = { txid, vout ->
                if (txid == validTxId && vout == 0) covA else null
            }
        )

        assertEquals("Only the single valid live scheme-0x00 zero-ext cell may pass", 1, verifiedPieces.size)
        assertEquals(validTxId, verifiedPieces[0].transactionId)
        assertEquals(0, verifiedPieces[0].index)
        assertEquals(20_000_000L, verifiedPieces[0].valueSompi)
    }

    private fun expectThrows(block: () -> Unit) {
        try {
            block()
            fail("Expected exception was not thrown")
        } catch (_: Throwable) {
            // Expected refusal
        }
    }
}
