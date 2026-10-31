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
            javaClass.classLoader?.getResourceAsStream("kb-live-cell-program.hex.gz.b64")
        ).bufferedReader().use { it.readText() }
        val compressed = Base64.getDecoder().decode(encoded)
        return GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
    }

