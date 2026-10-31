package com.example.network.kaspa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger

class KbKcc20ProtocolTest {
    private fun stateBytes(
        amount: ByteArray = ByteArray(8),
        ownerScheme: Byte = 0,
        borrowScheme: Byte = 0
    ): ByteArray {
        require(amount.size == 8)
        return byteArrayOf(0x08) + amount +
            byteArrayOf(0x20) + ByteArray(32) +
            byteArrayOf(0x01, ownerScheme) +
            byteArrayOf(0x01, borrowScheme) +
            byteArrayOf(0x20) + ByteArray(32) +
            byteArrayOf(0x20) + ByteArray(32)
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
    }
}
