package com.example.network.kaspa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPInputStream

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

    private fun liveProgramFixture(): ByteArray {
        val encoded = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("kb-live-cell-program.bin.gz.b64")
        ).bufferedReader().use { it.readText() }
        val compressed = Base64.getDecoder().decode(encoded)
        return GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
    }


    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Test
    fun reproducesArgentTemplateAndViewHashesFromTheFullLiveProgram() {
        val program = liveProgramFixture()
        assertEquals(11458, program.size)
        assertEquals(
            "7397eced4df96486564fc19e5f6ceffa3aa24d15070363259788cf14db87aed9",
            MessageDigest.getInstance("SHA-256").digest(program).toHex()
        )
        val verification = KbKcc20Protocol.verifyArgentTemplate(program)
        assertEquals(KbKcc20Protocol.TEMPLATE_HASH, verification.templateHash)
        assertEquals(KbKcc20Protocol.VIEW_TEMPLATE_HASH, verification.viewTemplateHash)
        assertEquals(1, verification.templateStateStart)
        assertEquals(145, verification.templateStateLength)
        assertEquals(34, verification.stateStart)
        assertEquals(112, verification.stateLength)
        assertEquals(1, verification.prefixLength)
        assertEquals(11312, verification.suffixLength)

        val state = KbKcc20Protocol.decodeLiveCellProgram(program)
        assertEquals(BigInteger("562778453"), state.amountBaseUnits)
        assertEquals(0x04, state.ownerScheme)
        assertEquals(0x00, state.borrowScheme)
        assertTrue(state.borrowGuard.all { it == 0.toByte() })
        assertTrue(state.extensionCommitment.all { it == 0.toByte() })
        val hex = program.toHex()
        assertTrue(hex.contains(KbKcc20Protocol.TRANSFER_DISPATCH))
        assertTrue(hex.contains(KbKcc20Protocol.TRANSFER_DELEGATOR_DISPATCH))
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
