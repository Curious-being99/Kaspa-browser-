package com.example.network.kaspa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class KbKcc20StandardAssemblerTest {

    private fun owner(fill: Int): ByteArray = ByteArray(32) { fill.toByte() }

    private fun syntheticProgram(state: KbKcc20State): ByteArray {
        // Minimal layout-compatible blob: 34-byte prefix + 112 state + short suffix.
        // Prefix byte0 free; bytes 2..33 held constant for successor template check.
        val prefix = ByteArray(KbKcc20StandardAssembler.PROGRAM_PREFIX_LEN).also {
            it[0] = 0x01
            for (i in 2 until it.size) it[i] = 0xAA.toByte()
        }
        val suffix = ByteArray(32) { 0xBB.toByte() }
        return KbKcc20ScriptCodec.concat(prefix, KbKcc20Protocol.encodeState(state), suffix)
    }

    @Test
    fun transferDispatchTagIsPinned() {
        val tag = KbKcc20ScriptCodec.hexToBytes(KbKcc20Protocol.TRANSFER_DISPATCH)
        assertEquals(4, tag.size)
        assertEquals("79c71c23", KbKcc20ScriptCodec.bytesToHex(tag))
    }

    @Test
    fun encodeTransferArgsEndsWithTransferTag() {
        val amount = BigInteger.TEN
        val st = KbKcc20State(
            amountBaseUnits = amount,
            amountEncoding = KbKcc20Protocol.encodeAmount(amount),
            owner = owner(0x11),
            ownerScheme = 0x00,
            borrowScheme = 0x00,
            borrowGuard = ByteArray(32),
            extensionCommitment = ByteArray(32)
        )
        val witness = ByteArray(1 + 65) // path + zero sig
        val args = KbKcc20StandardAssembler.encodeTransferArgs(listOf(st), witness)
        val hex = KbKcc20ScriptCodec.bytesToHex(args)
        assertTrue(hex.endsWith("0479c71c23"))
    }

    @Test
    fun leaderSeatHas65ByteZeroPlaceholder() {
        val amount = BigInteger.valueOf(100)
        val st = KbKcc20State(
            amountBaseUnits = amount,
            amountEncoding = KbKcc20Protocol.encodeAmount(amount),
            owner = owner(0x22),
            ownerScheme = 0x00,
            borrowScheme = 0x00,
            borrowGuard = ByteArray(32),
            extensionCommitment = ByteArray(32)
        )
        val program = syntheticProgram(st)
        val witness = KbKcc20StandardAssembler.ownerWitnessPlaceholder(st, owner(0x22), leader = true)
        val seat = KbKcc20StandardAssembler.encodeLeaderSeat(0, listOf(st), witness, program)
        val slot = seat.script.copyOfRange(seat.sigOffset, seat.sigOffset + 65)
        assertTrue(slot.all { it == 0.toByte() })
        val patched = KbKcc20StandardAssembler.patchSeat(
            seat,
            ByteArray(64) { 0x01 } + byteArrayOf(0x01)
        )
        assertEquals(0x01.toByte(), patched[seat.sigOffset + 64])
    }

    @Test
    fun planRejectsSyntheticProgramWithoutArgentTemplate() {
        // Production planning requires verifyArgentTemplate; synthetic layout blobs must not pass.
        val pk = owner(0x33)
        val st = KbKcc20State(
            amountBaseUnits = BigInteger.valueOf(100),
            amountEncoding = KbKcc20Protocol.encodeAmount(BigInteger.valueOf(100)),
            owner = pk,
            ownerScheme = 0x00,
            borrowScheme = 0x00,
            borrowGuard = ByteArray(32),
            extensionCommitment = ByteArray(32) { 0x05 }
        )
        val program = syntheticProgram(st)
        val piece = KbKcc20StandardAssembler.SpendPiece(
            transactionId = "ab".repeat(32),
            index = 0,
            valueSompi = 1_000_000L,
            redeem = program
        )
        try {
            KbKcc20StandardAssembler.planStandardTransfer(
                pieces = listOf(piece),
                pubkey32 = pk,
                recipientOwner = owner(0x44),
                amountBaseUnits = BigInteger.valueOf(40)
            )
            throw AssertionError("expected planStandardTransfer to reject synthetic program")
        } catch (e: Exception) {
            // verifyArgentTemplate or size/layout failure — fail closed
            assertTrue(e is IllegalArgumentException || e.message != null)
        }
    }

    @Test
    fun layoutOnlySplitAllowsSyntheticWhenArgentNotRequired() {
        val pk = owner(0x33)
        val st = KbKcc20State(
            amountBaseUnits = BigInteger.valueOf(100),
            amountEncoding = KbKcc20Protocol.encodeAmount(BigInteger.valueOf(100)),
            owner = pk,
            ownerScheme = 0x00,
            borrowScheme = 0x00,
            borrowGuard = ByteArray(32),
            extensionCommitment = ByteArray(32)
        )
        val program = syntheticProgram(st)
        val parts = KbKcc20StandardAssembler.splitProgram(program, requireArgentTemplate = false)
        assertEquals(BigInteger.valueOf(100), parts.state.amountBaseUnits)
        assertTrue(parts.state.owner.contentEquals(pk))
    }

    @Test
    fun successorPreservesProgramLength() {
        val st = KbKcc20State(
            amountBaseUnits = BigInteger.ONE,
            amountEncoding = KbKcc20Protocol.encodeAmount(BigInteger.ONE),
            owner = owner(0x01),
            ownerScheme = 0x00,
            borrowScheme = 0x00,
            borrowGuard = ByteArray(32),
            extensionCommitment = ByteArray(32)
        )
        val program = syntheticProgram(st)
        val next = st.copy(
            amountBaseUnits = BigInteger.TWO,
            amountEncoding = KbKcc20Protocol.encodeAmount(BigInteger.TWO),
            owner = owner(0x02)
        )
        val succ = KbKcc20StandardAssembler.successorProgram(program, next, requireArgentTemplate = false)
        assertEquals(program.size, succ.size)
        val decoded = KbKcc20Protocol.decodeState(
            succ.copyOfRange(
                KbKcc20StandardAssembler.PROGRAM_PREFIX_LEN,
                KbKcc20StandardAssembler.STATE_END
            )
        )
        assertEquals(BigInteger.TWO, decoded.amountBaseUnits)
        assertTrue(decoded.owner.contentEquals(owner(0x02)))
    }
}
