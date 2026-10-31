package com.example.network.kaspa

import java.math.BigInteger

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

    fun decodeLiveCellProgram(program: ByteArray): KbKcc20State {
        require(program.size >= PROGRAM_STATE_OFFSET + ENCODED_STATE_SIZE) {
            "KB cell program is too short to contain the state window"
        }
        require(program[0] == 0x6b.toByte() && program[1] == 0x20.toByte()) {
            "Unexpected KB Argent commitment header"
        }
        val embeddedHash = program.copyOfRange(2, 34).toHex()
        require(embeddedHash == TEMPLATE_HASH) {
            "KB program commitment does not match the pinned template hash"
        }
        return decodeState(
            program.copyOfRange(
                PROGRAM_STATE_OFFSET,
                PROGRAM_STATE_OFFSET + ENCODED_STATE_SIZE
            )
        )
    }

    fun verifyTemplateHash(
        prefix: ByteArray,
        suffix: ByteArray,
        hashTemplate: (prefix: ByteArray, suffix: ByteArray) -> ByteArray
    ): Boolean = hashTemplate(prefix, suffix).toHex() == TEMPLATE_HASH

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
