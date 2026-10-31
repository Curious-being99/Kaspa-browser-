package com.example.network.kaspa

import com.example.network.Blake3
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    const val PROGRAM_STATE_OFFSET = 34
    const val ARGENT_TEMPLATE_STATE_OFFSET = 1
    const val ARGENT_TEMPLATE_STATE_SIZE = 145
    const val AMOUNT_SIZE = 8

    const val ARTIFACT_STATUS = "unpublished"
    const val REVIEW_STATUS = "reviewed"
    const val KCC_VERSION = 20
    const val MAX_TOKEN_INPUTS = 10
    const val MAX_TOKEN_OUTPUTS = 10

    val supportedOwnerSchemes: Set<Int> = setOf(0x00, 0x01, 0x02, 0x03, 0x04)
    val supportedBorrowSchemes: Set<Int> = setOf(0x00, 0x01, 0x02, 0x03)

    val reviewedConfig = KbKcc20DeploymentConfig(
        covenantId = TOKEN_COVENANT_ID,
        stateLayout = STATE_LAYOUT,
        supplyBaseUnits = TOKEN_SUPPLY_BASE_UNITS,
        templateHash = TEMPLATE_HASH,
        viewTemplateHash = VIEW_TEMPLATE_HASH,
        transferDispatch = TRANSFER_DISPATCH,
        transferDelegatorDispatch = TRANSFER_DELEGATOR_DISPATCH,
        kccVersion = KCC_VERSION,
        ownerSchemes = supportedOwnerSchemes,
        borrowSchemes = supportedBorrowSchemes,
        maxTokenInputs = MAX_TOKEN_INPUTS,
        maxTokenOutputs = MAX_TOKEN_OUTPUTS,
        artifactStatus = ARTIFACT_STATUS,
        reviewStatus = REVIEW_STATUS,
        conformant = null
    )

    fun validateConfig(config: KbKcc20DeploymentConfig = reviewedConfig) {
        require(config.covenantId == TOKEN_COVENANT_ID) { "Unexpected KB covenant ID" }
        require(config.stateLayout == STATE_LAYOUT) { "Unsupported KB state layout" }
        require(config.supplyBaseUnits == TOKEN_SUPPLY_BASE_UNITS) { "Unexpected KB supply" }
        require(config.templateHash == TEMPLATE_HASH) { "Unexpected KB template hash" }
        require(config.viewTemplateHash == VIEW_TEMPLATE_HASH) { "Unexpected KB view template hash" }
        require(config.transferDispatch == TRANSFER_DISPATCH) { "Unexpected transfer dispatch tag" }
        require(config.transferDelegatorDispatch == TRANSFER_DELEGATOR_DISPATCH) {
            "Unexpected transfer_delegator dispatch tag"
        }
        require(config.kccVersion == KCC_VERSION) { "Unexpected KCC version" }
        require(config.ownerSchemes == supportedOwnerSchemes) { "KB owner scheme set mismatch" }
        require(config.borrowSchemes == supportedBorrowSchemes) { "KB borrow scheme set mismatch" }
        require(config.maxTokenInputs == MAX_TOKEN_INPUTS) { "KB input limit mismatch" }
        require(config.maxTokenOutputs == MAX_TOKEN_OUTPUTS) { "KB output limit mismatch" }
        require(config.artifactStatus == ARTIFACT_STATUS) { "KB artifact status mismatch" }
        require(config.reviewStatus == REVIEW_STATUS) { "KB review status mismatch" }
        require(config.conformant == null) { "KB conformance must remain unclaimed" }
    }

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
        require(ownerScheme in supportedOwnerSchemes) {
            "Unsupported KB owner scheme: $ownerScheme"
        }
        require(borrowScheme in supportedBorrowSchemes) {
            "Unsupported KB borrow scheme: $borrowScheme"
        }
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

    fun templateHash(prefix: ByteArray, suffix: ByteArray): ByteArray {
        val framed = ByteBuffer.allocate(8 + prefix.size + 8 + suffix.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(prefix.size.toLong())
            .put(prefix)
            .putLong(suffix.size.toLong())
            .put(suffix)
            .array()
        return Blake3.hash(framed)
    }

    fun verifyArgentTemplate(program: ByteArray): KbKcc20TemplateVerification {
        require(program.size >= ARGENT_TEMPLATE_STATE_OFFSET + ARGENT_TEMPLATE_STATE_SIZE) {
            "KB Argent program is too short"
        }
        require(program[0] == 0x6b.toByte()) { "Unexpected Argent template prefix opcode" }
        require(program[1] == 0x20.toByte()) { "Missing 32-byte Argent template slot" }

        val stateEnd = ARGENT_TEMPLATE_STATE_OFFSET + ARGENT_TEMPLATE_STATE_SIZE
        val prefix = program.copyOfRange(0, ARGENT_TEMPLATE_STATE_OFFSET)
        val suffix = program.copyOfRange(stateEnd, program.size)
        val computedTemplateHash = templateHash(prefix, suffix)
        val embeddedTemplateHash = program.copyOfRange(2, 34)
        require(computedTemplateHash.contentEquals(embeddedTemplateHash)) {
            "Argent KCC-1 template hash mismatch"
        }
        require(computedTemplateHash.toHex() == TEMPLATE_HASH) {
            "Argent KCC-1 template hash does not match the pinned KB build"
        }

        val viewPrefix = program.copyOfRange(0, PROGRAM_STATE_OFFSET)
        val viewSuffix = program.copyOfRange(
            PROGRAM_STATE_OFFSET + ENCODED_STATE_SIZE,
            program.size
        )
        val computedViewHash = templateHash(viewPrefix, viewSuffix)
        require(computedViewHash.toHex() == VIEW_TEMPLATE_HASH) {
            "KB KCC-20 state-view template hash mismatch"
        }

        return KbKcc20TemplateVerification(
            templateHash = computedTemplateHash.toHex(),
            viewTemplateHash = computedViewHash.toHex(),
            stateStart = PROGRAM_STATE_OFFSET,
            stateLength = ENCODED_STATE_SIZE,
            templateStateStart = ARGENT_TEMPLATE_STATE_OFFSET,
            templateStateLength = ARGENT_TEMPLATE_STATE_SIZE,
            prefixLength = prefix.size,
            suffixLength = suffix.size
        )
    }

    fun decodeLiveCellProgram(program: ByteArray): KbKcc20State {
        verifyArgentTemplate(program)
        return decodeState(
            program.copyOfRange(
                PROGRAM_STATE_OFFSET,
                PROGRAM_STATE_OFFSET + ENCODED_STATE_SIZE
            )
        )
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }

}

data class KbKcc20DeploymentConfig(
    val covenantId: String,
    val stateLayout: String,
    val supplyBaseUnits: String,
    val templateHash: String,
    val viewTemplateHash: String,
    val transferDispatch: String,
    val transferDelegatorDispatch: String,
    val kccVersion: Int,
    val ownerSchemes: Set<Int>,
    val borrowSchemes: Set<Int>,
    val maxTokenInputs: Int,
    val maxTokenOutputs: Int,
    val artifactStatus: String,
    val reviewStatus: String,
    val conformant: Boolean?
)

data class KbKcc20State(
    val amountBaseUnits: BigInteger,
    val amountEncoding: ByteArray,
    val owner: ByteArray,
    val ownerScheme: Int,
    val borrowScheme: Int,
    val borrowGuard: ByteArray,
    val extensionCommitment: ByteArray
)

data class KbKcc20TemplateVerification(
    val templateHash: String,
    val viewTemplateHash: String,
    val stateStart: Int,
    val stateLength: Int,
    val templateStateStart: Int,
    val templateStateLength: Int,
    val prefixLength: Int,
    val suffixLength: Int
)
