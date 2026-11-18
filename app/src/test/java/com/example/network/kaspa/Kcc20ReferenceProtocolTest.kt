package com.example.network.kaspa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import com.example.network.Blake3
import org.junit.Test
import java.math.BigInteger

class Kcc20ReferenceProtocolTest {
    private fun state(amount: Long, ownerByte: Byte) = Kcc20ReferenceProtocol.State(
        amount = BigInteger.valueOf(amount),
        owner = ByteArray(32) { ownerByte },
        ownerScheme = 0x00,
        borrowScheme = 0x00,
        borrowGuard = ByteArray(32),
        extensionCommitment = ByteArray(32)
    )

    @Test
    fun stateEncodingMatchesReference145ByteLayout() {
        val encoded = Kcc20ReferenceProtocol.encodeState(state(1000, 0x11))
        assertEquals(145, encoded.size)
        val decoded = Kcc20ReferenceProtocol.decodeState(encoded)
        assertEquals(BigInteger.valueOf(1000), decoded.amount)
        assertEquals(0x00, decoded.ownerScheme)
        assertTrue(decoded.owner.contentEquals(ByteArray(32) { 0x11 }))
        assertEquals(Kcc20ReferenceProtocol.TEMPLATE_HASH, Kcc20ReferenceProtocol.bytesToHex(encoded.copyOfRange(1, 33)))
    }

    @Test
    fun programRebuildMatchesPinnedPublicMintArtifactTemplate() {
        val program = Kcc20ReferenceProtocol.programFor(state(1000, 0x11))
        assertEquals(4031, program.size)
        val decoded = Kcc20ReferenceProtocol.verifyProgram(program)
        assertEquals(BigInteger.valueOf(1000), decoded.amount)
        assertEquals(35, Kcc20ReferenceProtocol.p2shScript(program).size)
    }

    @Test
    fun genericTransferPlanConservesTokenAmountAndCreatesLeaderAndDelegatorSeats() {
        val sender = ByteArray(32) { 0x11 }
        val recipient = ByteArray(32) { 0x22 }
        val family = "11".repeat(32)
        val leaderProgram = Kcc20ReferenceProtocol.programFor(state(600, 0x11))
        val delegatorProgram = Kcc20ReferenceProtocol.programFor(state(400, 0x11))
        fun verified(txid: String, index: Int, program: ByteArray): Kcc20ReferenceCellVerifier.VerifiedCell {
            val address = Kcc20ReferenceProtocol.p2shAddress(program, "kaspa")
            val candidate = Kcc20ReferenceCellVerifier.CandidateCell(
                transactionId = txid,
                outputIndex = index,
                valueSompi = 50_000_000,
                covenantId = family,
                address = address,
                program = program
            )
            return Kcc20ReferenceCellVerifier.verify(
                candidate = candidate,
                expectedCovenantId = family,
                networkPrefix = "kaspa",
                nodeUtxosAtAddress = listOf(
                    Kcc20ReferenceCellVerifier.NodeUtxo(
                        transactionId = txid,
                        outputIndex = index,
                        valueSompi = 50_000_000,
                        covenantId = family,
                        address = address,
                        scriptPublicKeyVersion = 0,
                        scriptPublicKeyHex = Kcc20ReferenceProtocol.bytesToHex(Kcc20ReferenceProtocol.p2shScript(program))
                    )
                )
            )
        }
        val plan = Kcc20ReferenceTransferBuilder.plan(
            cells = listOf(
                verified("aa".repeat(32), 0, leaderProgram),
                verified("bb".repeat(32), 1, delegatorProgram)
            ),
            expectedCovenantId = family,
            senderPublicKey = sender,
            recipientPublicKey = recipient,
            amountBaseUnits = BigInteger.valueOf(700)
        )
        assertEquals(BigInteger.valueOf(1000), plan.inputAmount)
        assertEquals(plan.inputAmount, plan.outputAmount)
        assertEquals(2, plan.nextStates.size)
        assertEquals(BigInteger.valueOf(700), plan.nextStates[0].amount)
        assertEquals(BigInteger.valueOf(300), plan.nextStates[1].amount)
        assertEquals(2, plan.seats.size)
        assertTrue(plan.seats[0].isLeader)
        assertTrue(!plan.seats[1].isLeader)
        assertEquals(family, plan.covenantId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun transferRejectsCellFromAnotherCovenantFamily() {
        val owner = ByteArray(32) { 0x11 }
        val program = Kcc20ReferenceProtocol.programFor(state(100, 0x11))
        val family = "11".repeat(32)
        val txid = "aa".repeat(32)
        val candidate = Kcc20ReferenceCellVerifier.CandidateCell(
            txid, 0, 50_000_000, "22".repeat(32),
            Kcc20ReferenceProtocol.p2shAddress(program, "kaspa"), program
        )
        val verified = Kcc20ReferenceCellVerifier.verify(
            candidate, "11".repeat(32), "kaspa",
            listOf(
                Kcc20ReferenceCellVerifier.NodeUtxo(
                    transactionId = txid,
                    outputIndex = 0,
                    valueSompi = 50_000_000,
                    covenantId = family,
                    address = Kcc20ReferenceProtocol.p2shAddress(program, "kaspa"),
                    scriptPublicKeyVersion = 0,
                    scriptPublicKeyHex = Kcc20ReferenceProtocol.bytesToHex(Kcc20ReferenceProtocol.p2shScript(program))
                )
            )
        )
        Kcc20ReferenceTransferBuilder.plan(
            cells = listOf(verified),
            expectedCovenantId = family,
            senderPublicKey = owner,
            recipientPublicKey = ByteArray(32) { 0x22 },
            amountBaseUnits = BigInteger.TEN
        )
    }
    @Test
    fun versionOneTransactionIdCommitsCovenantBindingButNotComputeBudget() {
        val script = "aa20" + "11".repeat(32) + "87"
        val input = KaspaTransactionEngine.KaspaTransactionInput(
            previousOutpoint = KaspaTransactionEngine.KaspaOutpoint("22".repeat(32), 0),
            sequence = 0,
            sigOpCount = 0,
            computeBudget = 14
        )
        val boundOutput = KaspaTransactionEngine.KaspaTransactionOutput(
            amount = 50_000_000,
            scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, script),
            covenant = KaspaTransactionEngine.KaspaCovenantBinding(0, "33".repeat(32))
        )
        val transaction = KaspaTransactionEngine.KaspaTransaction(
            version = 1,
            inputs = listOf(input),
            outputs = listOf(boundOutput)
        )
        val higherBudget = transaction.copy(
            inputs = listOf(input.copy(computeBudget = 32))
        )
        val changedFamily = transaction.copy(
            outputs = listOf(boundOutput.copy(
                covenant = KaspaTransactionEngine.KaspaCovenantBinding(0, "44".repeat(32))
            ))
        )
        assertEquals(
            KaspaTransactionEngine.calcTransactionId(transaction),
            KaspaTransactionEngine.calcTransactionId(higherBudget)
        )
        assertNotEquals(
            KaspaTransactionEngine.calcTransactionId(transaction),
            KaspaTransactionEngine.calcTransactionId(changedFamily)
        )
    }

    @Test
    fun versionOneMassIncludesComputeBudgetsAndCovenantBindings() {
        val input = KaspaTransactionEngine.KaspaTransactionInput(
            previousOutpoint = KaspaTransactionEngine.KaspaOutpoint("55".repeat(32), 0),
            sequence = 0,
            sigOpCount = 0,
            computeBudget = 14
        )
        val script = "aa20" + "66".repeat(32) + "87"
        val unbound = KaspaTransactionEngine.KaspaTransactionOutput(
            50_000_000,
            KaspaTransactionEngine.KaspaScriptPublicKey(0, script)
        )
        val bound = unbound.copy(
            covenant = KaspaTransactionEngine.KaspaCovenantBinding(0, "77".repeat(32))
        )
        val unboundTx = KaspaTransactionEngine.KaspaTransaction(1, listOf(input), listOf(unbound))
        val boundTx = unboundTx.copy(outputs = listOf(bound))
        assertEquals(
            34L,
            KaspaTransactionEngine.calculateSerializedByteSize(boundTx) -
                KaspaTransactionEngine.calculateSerializedByteSize(unboundTx)
        )
        assertTrue(
            KaspaTransactionEngine.calculateReferenceV1ComputeMass(boundTx) >
                KaspaTransactionEngine.calculateSerializedByteSize(boundTx)
        )
    }

    @Test
    fun keyedBlake3MatchesRustyKaspaPayloadDigestVector() {
        assertEquals(
            "9c0ca2acb45e92ffe6ceb4ae29188b35c82d9676cdd3ce067fd6ccc30a9c4a38",
            Kcc20ReferenceProtocol.bytesToHex(Blake3.keyedHash("PayloadDigest", ByteArray(0)))
        )
    }

    @Test
    fun versionOneTransactionIdMatchesRustyKaspaV210Vector() {
        val transaction = KaspaTransactionEngine.KaspaTransaction(
            version = 1,
            inputs = listOf(
                KaspaTransactionEngine.KaspaTransactionInput(
                    previousOutpoint = KaspaTransactionEngine.KaspaOutpoint("00".repeat(32), 0),
                    signatureScript = "",
                    sequence = 0,
                    sigOpCount = 0,
                    computeBudget = 111
                )
            ),
            outputs = emptyList(),
            lockTime = 0,
            subnetworkId = "00".repeat(20),
            gas = 0,
            payload = ""
        )
        assertEquals(
            "5978e7aa1a9ba8fdf12dae6aa39aa198a91985e91192b291e207d4d6246349e6",
            KaspaTransactionEngine.calcTransactionId(transaction)
        )
        assertEquals(
            KaspaTransactionEngine.calcTransactionId(transaction),
            KaspaTransactionEngine.calcTransactionId(
                transaction.copy(inputs = listOf(transaction.inputs.single().copy(computeBudget = 222)))
            )
        )
    }

}
