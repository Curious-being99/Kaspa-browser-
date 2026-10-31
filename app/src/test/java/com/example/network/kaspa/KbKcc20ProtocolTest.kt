package com.example.network.kaspa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class KbKcc20ProtocolTest {
    private fun stateBytes(
        amount: ByteArray = ByteArray(8),
        ownerScheme: Byte = 0,
        borrowScheme: Byte = 0,
        owner: ByteArray = ByteArray(32),
        borrowGuard: ByteArray = ByteArray(32),
        extensionCommitment: ByteArray = ByteArray(32)
    ): ByteArray {
        require(amount.size == 8)
        require(owner.size == 32)
        require(borrowGuard.size == 32)
        require(extensionCommitment.size == 32)
        return byteArrayOf(0x08) + amount +
            byteArrayOf(0x20) + owner +
            byteArrayOf(0x01, ownerScheme) +
            byteArrayOf(0x01, borrowScheme) +
            byteArrayOf(0x20) + borrowGuard +
            byteArrayOf(0x20) + extensionCommitment
    }

    private fun liveProgram(state: ByteArray): ByteArray {
        require(state.size == KbKcc20Protocol.ENCODED_STATE_SIZE)
        val header = byteArrayOf(0x6b, 0x20) +
            KbKcc20Protocol.TEMPLATE_HASH.chunked(2)
                .map { it.toInt(16).toByte() }.toByteArray()
        return header + state
    }

    @Test
    fun decodesCanonical112ByteState() {
        val amount = byteArrayOf(0x15, 0xCD.toByte(), 0x5B, 0x07, 0, 0, 0, 0)
        val decoded = KbKcc20Protocol.decodeState(stateBytes(amount))
        assertEquals(123456789L, decoded.amountBaseUnits.longValueExact())
        assertEquals(0, decoded.ownerScheme)
        assertEquals(0, decoded.borrowScheme)
    }

    @Test
    fun decodesTheSuppliedLiveCellStateAtFixedProgramOffset() {
        val amount = byteArrayOf(0x55, 0x51, 0x8b.toByte(), 0x21, 0, 0, 0, 0)
        val owner = "2eec0a5bb96a3ff6030d365fb5cd966eafd8b54dae036020c6350f44b57c7258"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val decoded = KbKcc20Protocol.decodeLiveCellProgram(
            liveProgram(stateBytes(amount, ownerScheme = 0x04, owner = owner))
        )
        assertEquals(BigInteger("562778453"), decoded.amountBaseUnits)
        assertEquals(0x04, decoded.ownerScheme)
        assertEquals(0x00, decoded.borrowScheme)
        assertTrue(decoded.borrowGuard.all { it == 0.toByte() })
        assertTrue(decoded.extensionCommitment.all { it == 0.toByte() })
    }

    @Test
    fun rejectsWrongProgramCommitment() {
        val program = liveProgram(stateBytes()).also { it[2] = 0 }
        assertThrows(IllegalArgumentException::class.java) {
            KbKcc20Protocol.decodeLiveCellProgram(program)
        }
    }

    @Test
    fun rejectsNegativeSignedMagnitudeAmount() {
        val negative = ByteArray(8).also { it[7] = 0x80.toByte() }
        assertThrows(IllegalArgumentException::class.java) {
            KbKcc20Protocol.decodeState(stateBytes(negative))
        }
    }

    @Test
    fun rejectsUnsupportedOwnerScheme() {
        assertThrows(IllegalArgumentException::class.java) {
            KbKcc20Protocol.decodeState(stateBytes(ownerScheme = 0x05))
        }
    }

    @Test
    fun rejectsUnsupportedBorrowScheme() {
        assertThrows(IllegalArgumentException::class.java) {
            KbKcc20Protocol.decodeState(stateBytes(borrowScheme = 0x04))
        }
    }

    @Test
    fun rejectsWrongLengthAndMalformedPushes() {
        assertThrows(IllegalArgumentException::class.java) {
            KbKcc20Protocol.decodeState(ByteArray(111))
        }
        val malformed = stateBytes().also { it[0] = 0x07 }
        assertThrows(IllegalArgumentException::class.java) {
            KbKcc20Protocol.decodeState(malformed)
        }
    }

    @Test
    fun rejectsMismatchedDeploymentConfiguration() {
        KbKcc20Protocol.validateConfig()
        val invalid = KbKcc20Protocol.reviewedConfig.copy(maxTokenInputs = 9)
        assertThrows(IllegalArgumentException::class.java) {
            KbKcc20Protocol.validateConfig(invalid)
        }
    }

    @Test
    fun pinsTheSuppliedKbConfiguration() {
        assertEquals(
            "b75a6e90f569965bdeec23c460ebd59e0f0445d8aa8f5d4330fe15c66a47b4d8",
            KbKcc20Protocol.TOKEN_COVENANT_ID
        )
        assertEquals("79c71c23", KbKcc20Protocol.TRANSFER_DISPATCH)
        assertEquals("fd3ef14a", KbKcc20Protocol.TRANSFER_DELEGATOR_DISPATCH)
        assertEquals(
            "ff3ae732e3a2819bf9181f5aed6b1e9d0f7abea70a1dd655b8e777cbcbd2422b",
            KbKcc20Protocol.TEMPLATE_HASH
        )
        assertEquals(10, KbKcc20Protocol.MAX_TOKEN_INPUTS)
        assertEquals(10, KbKcc20Protocol.MAX_TOKEN_OUTPUTS)
        assertEquals("unpublished", KbKcc20Protocol.ARTIFACT_STATUS)
        assertEquals("reviewed", KbKcc20Protocol.REVIEW_STATUS)
    }
}
