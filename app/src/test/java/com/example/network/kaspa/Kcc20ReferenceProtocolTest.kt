package com.example.network.kaspa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        val plan = Kcc20ReferenceTransferBuilder.plan(
            cells = listOf(
                Kcc20ReferenceTransferBuilder.SpendCell("aa".repeat(32), 0, 50_000_000, family, leaderProgram),
                Kcc20ReferenceTransferBuilder.SpendCell("bb".repeat(32), 1, 50_000_000, family, delegatorProgram)
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
        Kcc20ReferenceTransferBuilder.plan(
            cells = listOf(
                Kcc20ReferenceTransferBuilder.SpendCell(
                    "aa".repeat(32), 0, 50_000_000, "22".repeat(32),
                    Kcc20ReferenceProtocol.programFor(state(100, 0x11))
                )
            ),
            expectedCovenantId = "11".repeat(32),
            senderPublicKey = owner,
            recipientPublicKey = ByteArray(32) { 0x22 },
            amountBaseUnits = BigInteger.TEN
        )
    }
}
