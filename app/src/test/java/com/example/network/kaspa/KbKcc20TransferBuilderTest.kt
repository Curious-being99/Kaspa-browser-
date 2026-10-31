package com.example.network.kaspa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class KbKcc20TransferBuilderTest {

    private fun owner(fill: Byte = 0x11): ByteArray = ByteArray(32) { fill }

    @Test
    fun encodeStateRoundTripsWithDecodeState() {
        val amount = BigInteger.valueOf(12_000_000L) // 12 KB at 6 decimals style example
        val state = KbKcc20State(
            amountBaseUnits = amount,
            amountEncoding = KbKcc20Protocol.encodeAmount(amount),
            owner = owner(0xAB.toByte()),
            ownerScheme = 0x00,
            borrowScheme = 0x00,
            borrowGuard = ByteArray(32),
            extensionCommitment = ByteArray(32)
        )
        val encoded = KbKcc20Protocol.encodeState(state)
        assertEquals(112, encoded.size)
        val decoded = KbKcc20Protocol.decodeState(encoded)
        assertEquals(amount, decoded.amountBaseUnits)
        assertEquals(0x00, decoded.ownerScheme)
        assertTrue(decoded.owner.contentEquals(state.owner))
    }

    @Test
    fun planTransferConservesAmountWithChange() {
        val req = KbKcc20TransferBuilder.TransferRequest(
            senderOwner = owner(0x01),
            recipientOwner = owner(0x02),
            amountBaseUnits = BigInteger.valueOf(3),
            inputAmountBaseUnits = BigInteger.valueOf(10)
        )
        val result = KbKcc20TransferBuilder.planTransfer(req)
        assertTrue(result is KbKcc20TransferBuilder.BuildResult.Planned)
        val plan = (result as KbKcc20TransferBuilder.BuildResult.Planned).plan
        assertEquals(2, plan.nextStates.size)
        assertEquals(BigInteger.valueOf(10), plan.inputSum)
        assertEquals(BigInteger.valueOf(10), plan.outputSum)
        assertEquals(KbKcc20Protocol.TRANSFER_DISPATCH, plan.transferDispatch)
        assertEquals(KbKcc20Protocol.TOKEN_COVENANT_ID, plan.covenantId)
    }

    @Test
    fun planTransferFullSendHasSingleOutput() {
        val req = KbKcc20TransferBuilder.TransferRequest(
            senderOwner = owner(0x01),
            recipientOwner = owner(0x02),
            amountBaseUnits = BigInteger.valueOf(10),
            inputAmountBaseUnits = BigInteger.valueOf(10)
        )
        val result = KbKcc20TransferBuilder.planTransfer(req)
        assertTrue(result is KbKcc20TransferBuilder.BuildResult.Planned)
        val plan = (result as KbKcc20TransferBuilder.BuildResult.Planned).plan
        assertEquals(1, plan.nextStates.size)
        assertEquals("recipient", plan.nextStates[0].role)
    }

    @Test
    fun planTransferRejectsOverspend() {
        val req = KbKcc20TransferBuilder.TransferRequest(
            senderOwner = owner(0x01),
            recipientOwner = owner(0x02),
            amountBaseUnits = BigInteger.valueOf(11),
            inputAmountBaseUnits = BigInteger.valueOf(10)
        )
        val result = KbKcc20TransferBuilder.planTransfer(req)
        assertTrue(result is KbKcc20TransferBuilder.BuildResult.Rejected)
    }

    @Test
    fun signingRemainsDisabledWhileArtifactUnpublished() {
        assertFalse(KbKcc20TransferBuilder.isSigningAllowed())
        val gate = KbKcc20TransferBuilder.gateStatus()
        assertTrue(gate is KbKcc20TransferBuilder.TransferGate.Blocked)
        val build = KbKcc20TransferBuilder.buildSignableTransaction(
            KbKcc20TransferBuilder.TransferRequest(
                senderOwner = owner(0x01),
                recipientOwner = owner(0x02),
                amountBaseUnits = BigInteger.ONE,
                inputAmountBaseUnits = BigInteger.TEN
            )
        )
        assertTrue(build is KbKcc20TransferBuilder.BuildResult.Rejected)
        val reason = (build as KbKcc20TransferBuilder.BuildResult.Rejected).reason
        assertTrue(reason.contains("unpublished") || reason.contains("assembler") || reason.contains("refusing"))
    }
}
