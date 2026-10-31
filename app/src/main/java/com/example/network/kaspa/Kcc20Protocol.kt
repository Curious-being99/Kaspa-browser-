package com.example.network.kaspa

import java.math.BigInteger

/**
 * Reviewed mainnet configuration for the Kaspa Browser ($KB) KCC-0020 token.
 *
 * The program artifact is unpublished. These values pin the reviewed indexer
 * configuration; they do not replace fetching and hashing the actual covenant
 * program before constructing or signing a transfer.
 */
object KbKcc20Protocol {
    const val TOKEN_COVENANT_ID =
        "b75a6e90f569965bdeec23c460ebd59e0f0445d8aa8f5d4330fe15c66a47b4d8"
    const val STATE_LAYOUT = "kcc0020-112"
    const val TOKEN_SUPPLY_BASE_UNITS = "1000000000"

    const val TRANSFER_DISPATCH = "79c71c23"
    const val TRANSFER_DELEGATOR_DISPATCH = "fd3ef14a"

    const val TEMPLATE_HASH =
        "ff3ae732e3a2819bf9181f5aed6b1e9d0f7abea70a1dd655b8e777cbcbd2422b"
    const val VIEW_TEMPLATE_HASH =
        "3d689d6273b7631e69f672d5ca744cb266ae997b04974196439858675cc04fe1"

    const val ENCODED_STATE_SIZE = 112
    const val AMOUNT_SIZE = 8

    val supportedOwnerSchemes: Set<Int> = setOf(0x00, 0x01, 0x02, 0x03, 0x04)
    val supportedBorrowSchemes: Set<Int> = setOf(0x00, 0x01, 0x02, 0x03)

    /**
     * Decode the six canonical KCC-1 direct-push fields.
     *
     * This validates state framing and field constraints only. It does not
     * establish that a covenant program matches TEMPLATE_HASH or authorize a
     * transfer. The caller must independently verify the program/template.
     */
    fun decodeState(encoded: ByteArray): KbKcc20State {
        require(encoded.size == ENCODED_STATE_SIZE) {
            "KB KCC-20 state must be exactly $ENCODED_STATE_SIZE bytes"
        }

        var offset = 0
        fun readPush(expectedLength: Int, field: String): ByteArray {
            require(offset < encoded.size) { "Missing $field push prefix" }
            val pushLength = encoded[offset++].toInt() and 0xff
            require(pushLength == expectedLength) {
                "Non-canonical $field push: expected $expectedLength bytes, got $pushLength"
            }
            require(offset + pushLength <= encoded.size) { "Truncated $field field" }
            return encoded.copyOfRange(offset, offset + pushLength).also {
                offset += pushLength
            }
        }

        val amountBytes = readPush(AMOUNT_SIZE, "amount")
        val owner = readPush(32, "owner")
        val ownerScheme = readPush(1, "owner_scheme")[0].toInt() and 0xff
        val borrowScheme = readPush(1, "borrow_scheme")[0].toInt() and 0xff
        val borrowGuard = readPush(32, "borrow_guard")
        val extensionCommitment = readPush(32, "extension_commitment")

        require(offset == encoded.size) { "Unexpected trailing bytes in KB KCC-20 state" }
        require(supportedOwnerSchemes.contains(ownerScheme)) {
            "Unsupported KB owner scheme: $ownerScheme"
        }
        require(supportedBorrowSchemes.contains(borrowScheme)) {
            "Unsupported KB borrow scheme: $borrowScheme"
        }

        // KCC-1 state integers are eight-byte little-endian signed-magnitude.
        // The sign bit is the high bit of the final byte; negative values and
        // negative zero are invalid for token quantities.
        require((amountBytes[7].toInt() and 0x80) == 0) {
            "KCC-20 token amount cannot be negative"
        }
        val amount = BigInteger(1, amountBytes.reversedArray())

        return KbKcc20State(
            amountBaseUnits = amount,
            amountEncoding = amountBytes,
            owner = owner,
            ownerScheme = ownerScheme,
            borrowScheme = borrowScheme,
            borrowGuard = borrowGuard,
            extensionCommitment = extensionCommitment
        )
    }

    /**
     * Fail-closed gate. Program bytes must be supplied from the proven on-chain
     * issuer/covenant source; no program bytes are embedded in this config.
     */
    fun verifyTemplateHash(
        prefix: ByteArray,
        suffix: ByteArray,
        hashTemplate: (prefix: ByteArray, suffix: ByteArray) -> ByteArray
    ): Boolean {
        val actual = hashTemplate(prefix, suffix).joinToString("") { "%02x".format(it) }
        return actual.equals(TEMPLATE_HASH, ignoreCase = true)
    }
}

data class KbKcc20State(
    val amountBaseUnits: BigInteger,
    val amountEncoding: ByteArray,
    val owner: ByteArray,
    val ownerScheme: Int,
    val borrowScheme: Int,
    val borrowGuard: ByteArray,
    val extensionCommitment: ByteArray
)
