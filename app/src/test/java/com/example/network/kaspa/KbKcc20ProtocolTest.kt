package com.example.network.kaspa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.security.MessageDigest

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
        val encoded = requireNotNull(javaClass.classLoader?.getResourceAsStream("kb-live-cell-program.hex.gz.b64"))
            .bufferedReader().use { it.readText() }
        val compressed = Base64.getDecoder().decode(encoded)
        val hex = GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader().use { it.readText() }
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    @Test
    fun reproducesArgentTemplateAndViewHashesFromTheFullLiveProgram() {
        val program = liveProgramFixture()
        assertEquals(11458, program.size)
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(program).joinToString("") { "%02x".format(it) }
        assertEquals(
            "7397eced4df96486564fc19e5f6ceffa3aa24d15070363259788cf14db87aed9",
            sha256
        )

        val verification = KbKcc20Protocol.verifyArgentTemplate(program)
        assertEquals(KbKcc20Protocol.TEMPLATE_HASH, verification.templateHash)
        assertEquals(KbKcc20Protocol.VIEW_TEMPLATE_HASH, verification.viewTemplateHash)
        assertEquals(1, verification.templateStateStart)
        assertEquals(145, verification.templateStateLength)
        assertEquals(34, verification.stateStart)
        assertEquals(112, verification.stateLength)
        assertEquals(11312, verification.suffixLength)

        val state = KbKcc20Protocol.decodeLiveCellProgram(program)
        assertEquals(BigInteger("562778453"), state.amountBaseUnits)
        assertEquals(0x04, state.ownerScheme)
        assertEquals(0x00, state.borrowScheme)
        assertTrue(state.borrowGuard.all { it == 0.toByte() })
        assertTrue(state.extensionCommitment.all { it == 0.toByte() })
        assertTrue(program.toHex().contains(KbKcc20Protocol.TRANSFER_DISPATCH))
        assertTrue(program.toHex().contains(KbKcc20Protocol.TRANSFER_DELEGATOR_DISPATCH))
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }

