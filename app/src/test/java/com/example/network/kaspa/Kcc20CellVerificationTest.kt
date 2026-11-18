package com.example.network.kaspa

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class Kcc20CellVerificationTest {
    private val family = "ab".repeat(32)

    private fun program(): ByteArray = Kcc20ReferenceProtocol.programFor(
        Kcc20ReferenceProtocol.State(
            amount = BigInteger.valueOf(1000),
            owner = ByteArray(32) { 0x11 },
            ownerScheme = 0x00,
            borrowScheme = 0x00,
            borrowGuard = ByteArray(32),
            extensionCommitment = ByteArray(32)
        )
    )

    @Test
    fun scannerAcceptsOnlyCovenantIdDeclaredOnTheOutput() {
        assertEquals(
            family,
            Kcc20NoteScanner.resolveCovenantId(JSONObject().put("covenantId", family))
        )
        assertEquals(
            family,
            Kcc20NoteScanner.resolveCovenantId(
                JSONObject().put("covenant", JSONObject().put("covenant_id", family))
            )
        )
        assertEquals(
            family,
            Kcc20NoteScanner.resolveCovenantId(
                JSONObject().put("verbose_data", JSONObject().put("covenant_id", family))
            )
        )
    }

    @Test
    fun scannerDoesNotInheritAnUnrelatedInputCovenantId() {
        assertNull(Kcc20NoteScanner.resolveCovenantId(JSONObject()))
    }

    @Test
    fun referenceCellVerifierRequiresMatchingNodeOutpointValueScriptAndFamily() {
        val program = program()
        val address = Kcc20ReferenceProtocol.p2shAddress(program, "kaspa")
        val script = Kcc20ReferenceProtocol.bytesToHex(Kcc20ReferenceProtocol.p2shScript(program))
        val candidate = Kcc20ReferenceCellVerifier.CandidateCell(
            transactionId = "cd".repeat(32),
            outputIndex = 2,
            valueSompi = 50_000_000,
            covenantId = family,
            address = address,
            program = program
        )
        val nodeUtxo = Kcc20ReferenceCellVerifier.NodeUtxo(
            transactionId = candidate.transactionId,
            outputIndex = candidate.outputIndex,
            valueSompi = candidate.valueSompi,
            covenantId = family,
            address = address,
            scriptPublicKeyVersion = 0,
            scriptPublicKeyHex = script,
            daaScore = 1,
            isCoinbase = false
        )
        val verified = Kcc20ReferenceCellVerifier.verify(
            candidate,
            family,
            "kaspa",
            listOf(nodeUtxo)
        )
        assertEquals(family, verified.covenantId)
        assertTrue(verified.program.contentEquals(program))
    }

    @Test(expected = IllegalArgumentException::class)
    fun referenceCellVerifierRejectsIndexerOnlyCell() {
        val program = program()
        val address = Kcc20ReferenceProtocol.p2shAddress(program, "kaspa")
        Kcc20ReferenceCellVerifier.verify(
            Kcc20ReferenceCellVerifier.CandidateCell(
                transactionId = "cd".repeat(32),
                outputIndex = 2,
                valueSompi = 50_000_000,
                covenantId = family,
                address = address,
                program = program
            ),
            family,
            "kaspa",
            emptyList()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun referenceCellVerifierRejectsDifferentNodeCovenantFamily() {
        val program = program()
        val address = Kcc20ReferenceProtocol.p2shAddress(program, "kaspa")
        val script = Kcc20ReferenceProtocol.bytesToHex(Kcc20ReferenceProtocol.p2shScript(program))
        Kcc20ReferenceCellVerifier.verify(
            Kcc20ReferenceCellVerifier.CandidateCell(
                transactionId = "cd".repeat(32),
                outputIndex = 2,
                valueSompi = 50_000_000,
                covenantId = family,
                address = address,
                program = program
            ),
            family,
            "kaspa",
            listOf(
                Kcc20ReferenceCellVerifier.NodeUtxo(
                    transactionId = "cd".repeat(32),
                    outputIndex = 2,
                    valueSompi = 50_000_000,
                    covenantId = "ef".repeat(32),
                    address = address,
                    scriptPublicKeyVersion = 0,
                    scriptPublicKeyHex = script,
                    daaScore = 1,
                    isCoinbase = false
                )
            )
        )
    }
}
