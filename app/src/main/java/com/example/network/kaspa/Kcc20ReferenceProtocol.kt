package com.example.network.kaspa

import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.digests.Blake3Digest
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reference KCC-20PublicMint KCC20 actor pinned to argent-lang/kcc20-reference c8a0871. */
object Kcc20ReferenceProtocol {
    const val TEMPLATE_HASH = Kcc20ArgentArtifact.TEMPLATE_HASH
    const val ARTIFACT_ID = Kcc20ArgentArtifact.ARTIFACT_ID
    const val REFERENCE_SOURCE_COMMIT = "c8a087117735a1f87c5c6d115fcddeaf2562c784"
    const val ARGENT_COMPILER_REVISION = "9a9f4b107116d0b259fae629f200fa3b663e7e9d"
    const val STATE_START = 1
    const val STATE_SIZE = 145
    const val PROGRAM_SIZE = 4031
    const val MAX_TOKEN_INPUTS = 3
    const val MAX_TOKEN_OUTPUTS = 3
    const val MAX_COMPUTE_MASS = 500_000L
    const val MAX_STORAGE_MASS = 500_000L
    const val MAX_TRANSIENT_MASS = 1_000_000L

    private val suffix = hexToBytes(Kcc20ArgentArtifact.SUFFIX_HEX)
    private val templatePrefix = byteArrayOf(0x6b)

    data class State(
        val amount: BigInteger,
        val owner: ByteArray,
        val ownerScheme: Int,
        val borrowScheme: Int,
        val borrowGuard: ByteArray,
        val extensionCommitment: ByteArray
    )

    fun verifyCompilerArtifactTemplate(artifactId: String, prefixHex: String, suffixHex: String) {
        require(artifactId == ARTIFACT_ID) { "Unknown Argent KCC20 artifact" }
        val expectedPrefix = hexToBytes(Kcc20ArgentArtifact.PREFIX_HEX)
        require(hexToBytes(prefixHex).contentEquals(expectedPrefix)) {
            "Argent KCC20 artifact prefix differs from the pinned reference"
        }
        require(hexToBytes(suffixHex).contentEquals(suffix)) {
            "Argent/Silverscript compiled KCC20 bytecode differs from the pinned reference"
        }
    }

    fun encodeState(state: State): ByteArray {
        require(state.amount.signum() >= 0) { "Reference KCC20 amount cannot be negative" }
        require(state.owner.size == 32 && state.borrowGuard.size == 32 && state.extensionCommitment.size == 32)
        require(state.ownerScheme in 0..4) { "Unsupported owner scheme" }
        require(state.borrowScheme in 0..3) { "Unsupported borrow scheme" }
        return concat(
            pushExplicit(hexToBytes(TEMPLATE_HASH)),
            pushExplicit(encodeSignedMagnitude(state.amount)),
            pushExplicit(state.owner),
            pushExplicit(byteArrayOf(state.ownerScheme.toByte())),
            pushExplicit(byteArrayOf(state.borrowScheme.toByte())),
            pushExplicit(state.borrowGuard),
            pushExplicit(state.extensionCommitment)
        ).also { require(it.size == STATE_SIZE) { "Encoded state length mismatch" } }
    }

    fun decodeState(encoded: ByteArray): State {
        require(encoded.size == STATE_SIZE) { "Reference KCC20 state must be exactly 145 bytes" }
        var offset = 0
        fun field(length: Int, name: String): ByteArray {
            require(offset < encoded.size && (encoded[offset++].toInt() and 0xff) == length) {
                "Invalid $name push"
            }
            require(offset + length <= encoded.size) { "Truncated $name" }
            return encoded.copyOfRange(offset, offset + length).also { offset += length }
        }
        val template = field(32, "template")
        require(bytesToHex(template) == TEMPLATE_HASH) { "Unknown KCC20 reference template hash" }
        val amount = decodeSignedMagnitude(field(8, "amount"))
        require(amount.signum() >= 0) { "Negative KCC20 amount" }
        val owner = field(32, "owner")
        val ownerScheme = field(1, "owner_scheme")[0].toInt() and 0xff
        val borrowScheme = field(1, "borrow_scheme")[0].toInt() and 0xff
        val borrowGuard = field(32, "borrow_guard")
        val extensionCommitment = field(32, "extension_commitment")
        require(offset == STATE_SIZE) { "Unexpected trailing state bytes" }
        require(ownerScheme in 0..4 && borrowScheme in 0..3) { "Reserved KCC20 scheme value" }
        return State(amount, owner, ownerScheme, borrowScheme, borrowGuard, extensionCommitment)
    }

    fun verifyProgram(program: ByteArray): State {
        require(program.size == PROGRAM_SIZE) { "Not a 4,031-byte reference KCC20 program" }
        require(program[0] == templatePrefix[0]) { "Reference program prefix mismatch" }
        require(program.copyOfRange(STATE_START + STATE_SIZE, program.size).contentEquals(suffix)) {
            "Program is not the pinned KCC20 reference template"
        }
        val state = decodeState(program.copyOfRange(STATE_START, STATE_START + STATE_SIZE))
        val calculated = calculateTemplateHash(program.copyOfRange(0, STATE_START), program.copyOfRange(STATE_START + STATE_SIZE, program.size))
        require(bytesToHex(calculated) == TEMPLATE_HASH) { "Reference template hash mismatch" }
        require(programFor(state).contentEquals(program)) { "Program rebuild mismatch" }
        return state
    }

    fun programFor(state: State): ByteArray = concat(templatePrefix, encodeState(state), suffix).also {
        require(it.size == PROGRAM_SIZE)
    }

    fun p2shScript(program: ByteArray): ByteArray {
        verifyProgram(program)
        val digest = Blake2bDigest(256)
        val hash = ByteArray(32)
        digest.update(program, 0, program.size)
        digest.doFinal(hash, 0)
        return concat(byteArrayOf(0xaa.toByte(), 0x20), hash, byteArrayOf(0x87.toByte()))
    }

    fun p2shAddress(program: ByteArray, networkPrefix: String): String {
        require(networkPrefix == "kaspa" || networkPrefix == "kaspatest") { "Unsupported Kaspa address prefix" }
        verifyProgram(program)
        val digest = Blake2bDigest(256)
        val hash = ByteArray(32)
        digest.update(program, 0, program.size)
        digest.doFinal(hash, 0)
        return com.example.network.CryptoUtils.encodeKaspaAddress(hash, version = 0x08, prefix = networkPrefix)
    }

    private fun decodeSignedMagnitude(raw: ByteArray): BigInteger {
        require(raw.size == 8)
        val bytes = raw.copyOf()
        val negative = (bytes[7].toInt() and 0x80) != 0
        bytes[7] = (bytes[7].toInt() and 0x7f).toByte()
        var value = BigInteger.ZERO
        for (i in 7 downTo 0) value = value.shiftLeft(8).or(BigInteger.valueOf((bytes[i].toInt() and 0xff).toLong()))
        return if (negative) value.negate() else value
    }

    private fun calculateTemplateHash(prefix: ByteArray, tail: ByteArray): ByteArray {
        val lengths = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(prefix.size.toLong()).putLong(tail.size.toLong()).array()
        val digest = Blake3Digest()
        digest.update(lengths, 0, 8)
        digest.update(prefix, 0, prefix.size)
        digest.update(lengths, 8, 8)
        digest.update(tail, 0, tail.size)
        return ByteArray(32).also { digest.doFinal(it, 0) }
    }

    private fun pushExplicit(value: ByteArray): ByteArray {
        val size = value.size
        val prefix = when {
            size == 0 -> byteArrayOf(0)
            size <= 75 -> byteArrayOf(size.toByte())
            size <= 255 -> byteArrayOf(0x4c, size.toByte())
            size <= 65535 -> byteArrayOf(0x4d, size.toByte(), (size ushr 8).toByte())
            else -> throw IllegalArgumentException("Push exceeds supported KCC20 script length")
        }
        return concat(prefix, value)
    }

    private fun concat(vararg arrays: ByteArray): ByteArray {
        val total = arrays.sumOf { it.size }
        val result = ByteArray(total)
        var offset = 0
        arrays.forEach { System.arraycopy(it, 0, result, offset, it.size); offset += it.size }
        return result
    }

    private fun hexToBytes(value: String): ByteArray {
        require(value.length % 2 == 0 && value.matches(Regex("[0-9a-fA-F]*"))) { "Invalid hex" }
        return ByteArray(value.length / 2) { i -> value.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    fun bytesToHex(value: ByteArray): String = value.joinToString("") { "%02x".format(it) }

    internal fun templateSuffixHex(): String = bytesToHex(suffix)

    fun decodeHex(value: String): ByteArray = hexToBytes(value)
}
