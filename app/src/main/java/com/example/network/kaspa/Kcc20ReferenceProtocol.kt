package com.example.network.kaspa

import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.digests.Blake3Digest
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reference KCC-20PublicMint KCC20 actor pinned to argent-lang/kcc20-reference c8a0871. */
object Kcc20ReferenceProtocol {
    const val TEMPLATE_HASH = "9703112ee6e3555107cd168858992b77d3b74f655205b2b463b1f9ec2ec73cf7"
    const val ARTIFACT_ID = "b48c90c8e287d227821ac40f0875a753e329e1ab3c8fdfa1477f55419cfa26ab"
    const val STATE_START = 1
    const val STATE_SIZE = 145
    const val PROGRAM_SIZE = 4031
    const val MAX_TOKEN_INPUTS = 3
    const val MAX_TOKEN_OUTPUTS = 3
    const val TRANSFER_TAG = "79c71c23"
    const val DELEGATOR_TAG = "fd3ef14a"
    const val SIGHASH_ALL = 0x01
    const val MAX_COMPUTE_MASS = 500_000L
    const val MAX_STORAGE_MASS = 500_000L
    const val MAX_TRANSIENT_MASS = 1_000_000L

    private val suffix = hexToBytes("6c760479c71c238763755779825197009d755d7982765897009d7c755896765e798276012097009d7c750120969d765d7982765197009d7c759d765c7982765197009d7c759d765b798276012097009d7c750120969d765a798276012097009d7c750120969d750401000000042d0f0000b9cf7600d1b99c6976d051947600a2697652a169000000000000005779787853799452a1697652799f63765b7951527993d176bf785279c90111790291009301117993945379c9011279029100930112799394011279029100930112799393bcaa02000001aa7e01207e7c7e01877e880115795f7958cd52795379c90112790291009301127993945479c901137902910093011379939401137993bc7e5f7958cd53795479c901137902910093011379939401137993029100935579c9011479029100930114799394011479930291009301137993bc7e7ed9887678c96079029100936079939460795193935279c9011179029100930111799394011179519393012093bc785279c9011179029100930111799394011179012293935379c9011279029100930112799394011279012293935893bc52795379c9011279029100930112799394011279012b93935479c9011379029100930113799394011379012b9393012093bc53795479c9011379029100930113799394011379014c93935579c9011479029100930114799394011479014c93935193bc54795579c9011479029100930114799394011479014e93935679c9011579029100930115799394011579014e93935193bc55795679c9011579029100930115799394011579015093935779c901167902910093011679939401167901509393012093bc56795779c9011679029100930116799394011679017193935879c901177902910093011779939401177901719393012093bc011179567958cd7e01117956797e01117956797e01117956797e01117956797e01117956797e557901187a75557901187a75557901187a75557901187a75557901187a75557901187a75567a75567a75567a75567a75567a75567a755e795193607a75577a75577a75577a75577a75577a75577a75577a75577a75577a756b567a6b567a6b6c6c6c687652799f63765b7951527993d176bf785279c90111790291009301117993945379c9011279029100930112799394011279029100930112799393bcaa02000001aa7e01207e7c7e01877e880115795f7958cd52795379c90112790291009301127993945479c901137902910093011379939401137993bc7e5f7958cd53795479c901137902910093011379939401137993029100935579c9011479029100930114799394011479930291009301137993bc7e7ed9887678c96079029100936079939460795193935279c9011179029100930111799394011179519393012093bc785279c9011179029100930111799394011179012293935379c9011279029100930112799394011279012293935893bc52795379c9011279029100930112799394011279012b93935479c9011379029100930113799394011379012b9393012093bc53795479c9011379029100930113799394011379014c93935579c9011479029100930114799394011479014c93935193bc54795579c9011479029100930114799394011479014e93935679c9011579029100930115799394011579014e93935193bc55795679c9011579029100930115799394011579015093935779c901167902910093011679939401167901509393012093bc56795779c9011679029100930116799394011679017193935879c901177902910093011779939401177901719393012093bc011179567958cd7e01117956797e01117956797e01117956797e01117956797e01117956797e557901187a75557901187a75557901187a75557901187a75557901187a75557901187a75567a75567a75567a75567a75567a75567a755e795193607a75577a75577a75577a75577a75577a75577a75577a75577a75577a756b567a6b567a6b6c6c6c68b9cb7651a2697653a1695b79d2b9cb9c69011b79827c755896789c69011579827c75519600a069011379005c79787853799452a1697652799f63767660797800a26978789f69785c7978012095760120937f011779876953790113797800a26978789f69785a79011579527958957658937f935b7a7557795193597a757b757b757b757b757b757b757b75727b687652799f63767660797800a26978789f69785c7978012095760120937f011779876953790113797800a26978789f69785a79011579527958957658937f935b7a7557795193597a757b757b757b757b757b757b757b75727b680000012179827c755896787853799453a1697652799f63760124797858957658937f00a2690122797851957651937f7601007e010401007ea169012279527951957651937f7601007e010301007ea1690121795379012095760120937f011a7987695679012779547958957658937f93577a7553795193557a757b757b757b75727b687652799f63760124797858957658937f00a2690122797851957651937f7601007e010401007ea169012279527951957651937f7601007e010301007ea1690121795379012095760120937f011a7987695679012779547958957658937f93577a7553795193557a757b757b757b75727b687652799f63760124797858957658937f00a2690122797851957651937f7601007e010401007ea169012279527951957651937f7601007e010301007ea1690121795379012095760120937f011a7987695679012779547958957658937f93577a7553795193557a757b757b757b75727b68537958799c69011d790051957651937f011e7951012079827c7551967f7801008763011c79011c7952797801008763765379ac6967780101876376827c75519601619c69760001207f78012001617f7876d9765779876952795479ac697575757567780102876376827c75519601629c69760001217f78012101627f7876d9765779876952795479ab697575757567780103876376827c755196519c69760051957651937f01007e76bf547902000001aa7e01207e7c7e01877e87697567780104876376827c755196009c695279d000a069670069686868686875757567780101876300011a79011c7901008763006967011c79010187635279827c755196009c69011b7900587f7b7576009f6300776867011c79010287635279011c79ac6967011c79010387635279827c7551960281009c6952790001207f775279012001407f537901400281007f527952797ed9011e798769765279ac69757567006968687c687c6801267900012095760120937f011f7987690125790051957651937f011e7987690124790051957651937f011d79876901237900012095760120937f7887690127790058957658937f01207994765379a069b900ccc2b9bea2697575756700696868012579827c7558965b799c69005b79787853799453a1697652799f63760129797858957658937f0129795279012095760120937f012979537951957651937f012979547951957651937f0129795579012095760120937f0129795679012095760120937f012879567956795679567956795679012057797e0108577958cd7e012057797e010157797e010157797e012057797e012057797e7e7e7e7e7e7eb976c902bf0f94765193bc7c7eb976c976022d8f937cbc7eaa02000001aa7e01207e7c7e01877eb95f79ccc3885d7951935f7a757777777777777777777777777777687652799f63760129797858957658937f0129795279012095760120937f012979537951957651937f012979547951957651937f0129795579012095760120937f0129795679012095760120937f012879567956795679567956795679012057797e0108577958cd7e012057797e010157797e010157797e012057797e012057797e7e7e7e7e7e7eb976c902bf0f94765193bc7c7eb976c976022d8f937cbc7eaa02000001aa7e01207e7c7e01877eb95f79ccc3885d7951935f7a757777777777777777777777777777687652799f63760129797858957658937f0129795279012095760120937f012979537951957651937f012979547951957651937f0129795579012095760120937f0129795679012095760120937f012879567956795679567956795679012057797e0108577958cd7e012057797e010157797e010157797e012057797e012057797e7e7e7e7e7e7eb976c902bf0f94765193bc7c7eb976c976022d8f937cbc7eaa02000001aa7e01207e7c7e01877eb95f79ccc3885d7951935f7a75777777777777777777777777777768757575757575757575757575757575757575757575757575757575757575757575757575757575757551677604fd3ef14a8763755779825197009d750401000000042d0f0000b9cf76d052a2697600d1b99e697600d176bf785279c9567902910093567993945379c95779029100935779939457790291009357799393bcaa02000001aa7e01207e7c7e01877e885a79547958cd52795379c9577902910093577993945479c958790291009358799394587993bc7e547958cd53795479c958790291009358799394587993029100935579c95979029100935979939459799302910093587993bc7e7ed9887678c95579029100935579939455795193935279c9567902910093567993945679519393012093bc785279c9567902910093567993945679012293935379c9577902910093577993945779012293935893bc52795379c9577902910093577993945779012b93935479c9587902910093587993945879012b9393012093bc53795479c9587902910093587993945879014c93935579c9597902910093597993945979014c93935193bc54795579c9597902910093597993945979014e93935679c95a79029100935a7993945a79014e93935193bc55795679c95a79029100935a7993945a79015093935779c95b79029100935b7993945b7901509393012093bc56795779c95b79029100935b7993945b79017193935879c95c79029100935c7993945c7901719393012093bcb9cb009c695f795f790114797801008763765379ac6967780101876376827c75519601619c69760001207f78012001617f7876d9765779876952795479ac697575757567780102876376827c75519601629c69760001217f78012101627f7876d9765779876952795479ab697575757567780103876376827c755196519c69760051957651937f01007e76bf547902000001aa7e01207e7c7e01877e87697567780104876376827c755196009c695279d000a06967006968686868687575757575757575757575757575757575757575757551676a6868")
    private val templatePrefix = byteArrayOf(0x6b)

    data class State(
        val amount: BigInteger,
        val owner: ByteArray,
        val ownerScheme: Int,
        val borrowScheme: Int,
        val borrowGuard: ByteArray,
        val extensionCommitment: ByteArray
    )

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

    fun transferArguments(states: List<State>): ByteArray {
        require(states.size in 1..MAX_TOKEN_OUTPUTS) { "Reference KCC20 transfer requires 1 to 3 continuations" }
        require(states.all { it.borrowScheme == 0 && it.extensionCommitment.all { b -> b == 0.toByte() } }) {
            "Only the guide-verified normal path with zero borrow scheme and extension commitment is supported"
        }
        fun column(value: (State) -> ByteArray) = pushMinimal(concat(*states.map(value).toTypedArray()))
        return concat(
            column { encodeSignedMagnitude(it.amount) },
            column { it.owner },
            column { byteArrayOf(it.ownerScheme.toByte()) },
            column { byteArrayOf(it.borrowScheme.toByte()) },
            column { it.borrowGuard },
            column { it.extensionCommitment }
        )
    }

    fun leaderSignatureScript(states: List<State>, signature65: ByteArray, program: ByteArray): ByteArray {
        require(signature65.size == 65 && (signature65.last().toInt() and 0xff) == SIGHASH_ALL)
        val current = verifyProgram(program)
        require(current.ownerScheme == 0x00 && current.borrowScheme == 0x00)
        return concat(
            transferArguments(states),
            pushMinimal(concat(byteArrayOf(0x00), signature65)),
            hexToBytes("04$TRANSFER_TAG"),
            pushMinimal(program)
        )
    }

    fun delegatorSignatureScript(signature65: ByteArray, program: ByteArray): ByteArray {
        require(signature65.size == 65 && (signature65.last().toInt() and 0xff) == SIGHASH_ALL)
        val current = verifyProgram(program)
        require(current.ownerScheme == 0x00 && current.borrowScheme == 0x00)
        return concat(
            pushMinimal(signature65),
            hexToBytes("04$DELEGATOR_TAG"),
            pushMinimal(program)
        )
    }

    fun encodeSignedMagnitude(value: BigInteger): ByteArray {
        val max = BigInteger("7fffffffffffffff", 16)
        require(value.signum() >= 0 && value <= max) { "Amount is outside reference int64 range" }
        val raw = value.toByteArray()
        val out = ByteArray(8)
        for (i in 0 until minOf(8, raw.size)) out[i] = raw[raw.size - 1 - i]
        return out
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

    private fun pushMinimal(value: ByteArray): ByteArray {
        if (value.isEmpty() || (value.size == 1 && value[0].toInt() == 0)) return byteArrayOf(0)
        if (value.size == 1 && value[0].toInt() in 1..16) return byteArrayOf((0x50 + value[0].toInt()).toByte())
        if (value.size == 1 && (value[0].toInt() and 0xff) == 0x81) return byteArrayOf(0x4f)
        return pushExplicit(value)
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

    fun decodeHex(value: String): ByteArray = hexToBytes(value)
}
