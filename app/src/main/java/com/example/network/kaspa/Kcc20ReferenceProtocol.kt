package com.example.network.kaspa

import com.example.network.Blake3
import com.example.network.CryptoUtils
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.GZIPInputStream

/**
 * Literal implementation of `/kcc20-Guide.md` (Version 2, 2026-11-17) for KCC-20 on Kaspa L1:
 * - Reference KCC-20 contract (4,031B, template `9703112e...3cf7`, max 3 inputs / 3 outputs)
 * - KB 10x10 compilation (11,458B, template `ff3ae732...422b`, max 10 inputs / 10 outputs)
 * - KCC-1 explicit/minimal push primitives, 145-byte & 112-byte state encodings
 * - §3 / A.2 `transferArgs`, `leaderScript`, `delegatorScript`, `p2shScript`, `p2shAddress`
 * - A.3 `lastPushes`, `programFromSpend`
 * - §2.3 / A.4 `Kcc20CellVerifier` (all 5 checks + caller-supplied covenantId check 6)
 */
object Kcc20ReferenceProtocol {

    const val REF_TEMPLATE_HASH =
        "9703112ee6e3555107cd168858992b77d3b74f655205b2b463b1f9ec2ec73cf7"
    const val REF_VIEW_TEMPLATE_HASH =
        "734850b0af0aeef49f009167bf9ddd238fd8c5cdbe214234f97181ac6f5cc498"
    const val REF_PROGRAM_LEN = 4031
    const val REF_STATE_START = 1
    const val REF_STATE_LEN = 145
    const val REF_SUFFIX_LEN = 3885
    const val REF_MAX_TOKEN_INPUTS = 3
    const val REF_MAX_TOKEN_OUTPUTS = 3

    const val KB_TEMPLATE_HASH = KbKcc20Protocol.TEMPLATE_HASH
    const val KB_VIEW_TEMPLATE_HASH = KbKcc20Protocol.VIEW_TEMPLATE_HASH
    const val KB_PROGRAM_LEN = 11458
    const val KB_SUFFIX_LEN = 11312
    const val KB_MAX_TOKEN_INPUTS = KbKcc20Protocol.MAX_TOKEN_INPUTS
    const val KB_MAX_TOKEN_OUTPUTS = KbKcc20Protocol.MAX_TOKEN_OUTPUTS

    const val TAG_TRANSFER = "79c71c23"
    const val TAG_DELEGATOR = "fd3ef14a"

    /** BLAKE2b-256(0x6a) = P2SH of a lone OP_RETURN (Guide §3.6 unspendable burn owner, scheme 0x03). */
    const val BURN_OP_RETURN_OWNER_HASH =
        "d379e50f3e2203f9a92584916335bb7cd014b237ceaa8b36f1d3d80d0cc88b32"

    /** Guide §4.2: below ~0.08 KAS a cell output alone exceeds 500,000 g storage mass. */
    const val MIN_CELL_KAS_STORAGE_FLOOR_SOMPI = 8_000_000L
    /** Guide §3 / mainnet transfers commonly use >= 0.2 KAS per cell. */
    const val RECOMMENDED_CELL_KAS_SOMPI = 20_000_000L

    val ZERO_32: ByteArray = ByteArray(32)
    private val HEX_32_REGEX = Regex("(?i)[0-9a-f]{64}")

    enum class TemplateKind(
        val id: String,
        val templateHash: String,
        val viewTemplateHash: String,
        val programLength: Int,
        val suffixLength: Int,
        val maxInputs: Int,
        val maxOutputs: Int
    ) {
        REFERENCE(
            id = "ref",
            templateHash = REF_TEMPLATE_HASH,
            viewTemplateHash = REF_VIEW_TEMPLATE_HASH,
            programLength = REF_PROGRAM_LEN,
            suffixLength = REF_SUFFIX_LEN,
            maxInputs = REF_MAX_TOKEN_INPUTS,
            maxOutputs = REF_MAX_TOKEN_OUTPUTS
        ),
        KB_10X10(
            id = "kb",
            templateHash = KB_TEMPLATE_HASH,
            viewTemplateHash = KB_VIEW_TEMPLATE_HASH,
            programLength = KB_PROGRAM_LEN,
            suffixLength = KB_SUFFIX_LEN,
            maxInputs = KB_MAX_TOKEN_INPUTS,
            maxOutputs = KB_MAX_TOKEN_OUTPUTS
        )
    }

    data class CellState(
        val templateHashHex: String = REF_TEMPLATE_HASH,
        val amount: BigInteger,
        val owner: ByteArray,
        val ownerScheme: Int = 0,
        val borrowScheme: Int = 0,
        val borrowGuard: ByteArray = ByteArray(32),
        val extensionCommitment: ByteArray = ByteArray(32)
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CellState) return false
            return templateHashHex.equals(other.templateHashHex, ignoreCase = true) &&
                amount == other.amount &&
                owner.contentEquals(other.owner) &&
                ownerScheme == other.ownerScheme &&
                borrowScheme == other.borrowScheme &&
                borrowGuard.contentEquals(other.borrowGuard) &&
                extensionCommitment.contentEquals(other.extensionCommitment)
        }

        override fun hashCode(): Int {
            var res = templateHashHex.lowercase().hashCode()
            res = 31 * res + amount.hashCode()
            res = 31 * res + owner.contentHashCode()
            res = 31 * res + ownerScheme
            res = 31 * res + borrowScheme
            res = 31 * res + borrowGuard.contentHashCode()
            res = 31 * res + extensionCommitment.contentHashCode()
            return res
        }
    }

    data class SpendProgramInfo(
        val tagHex: String,
        val program: ByteArray,
        val programBytes: Int,
        val templateHash: String,
        val stateTemplateField: String,
        val selfConsistent: Boolean
    )

    data class VerifiedCell(
        val ok: Boolean,
        val templateKind: TemplateKind,
        val state: CellState,
        val address: String,
        val kasSompi: Long,
        val covenantId: String,
        val program: ByteArray
    )

    data class CandidateCell(
        val redeemScriptHex: String,
        val address: String,
        val txid: String,
        val vout: Int,
        val kasSompi: Long? = null
    )

    data class NodeUtxo(
        val txid: String,
        val vout: Int,
        val amount: Long,
        val covenantId: String?
    )

    val pinnedRefSuffix: ByteArray? by lazy {
        loadGzipB64Resource("kcc20-ref-code.bin.gz.b64")
    }

    val pinnedRefProgram: ByteArray? by lazy {
        val suffix = pinnedRefSuffix ?: return@lazy null
        if (suffix.size != REF_SUFFIX_LEN) return@lazy null
        val placeholderState = encodeState145(
            CellState(
                templateHashHex = REF_TEMPLATE_HASH,
                amount = BigInteger.ZERO,
                owner = ByteArray(32),
                ownerScheme = 0,
                borrowScheme = 0,
                borrowGuard = ByteArray(32),
                extensionCommitment = ByteArray(32)
            ),
            templateHashHex = REF_TEMPLATE_HASH
        )
        byteArrayOf(0x6b.toByte()) + placeholderState + suffix
    }

    val pinnedKbProgram: ByteArray? by lazy {
        loadGzipB64Resource("kb-live-cell-program.bin.gz.b64")?.takeIf { it.size == KB_PROGRAM_LEN }
    }

    private fun loadGzipB64Resource(resourceName: String): ByteArray? {
        return try {
            val stream = Kcc20ReferenceProtocol::class.java.classLoader?.getResourceAsStream(resourceName)
                ?: Kcc20ReferenceProtocol::class.java.getResourceAsStream("/$resourceName")
                ?: return null
            val b64 = stream.bufferedReader().use { it.readText().trim() }
            val compressed = Base64.getDecoder().decode(b64)
            GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
        } catch (_: Throwable) {
            null
        }
    }

    fun pinnedProgramFor(kind: TemplateKind): ByteArray? = when (kind) {
        TemplateKind.REFERENCE -> pinnedRefProgram
        TemplateKind.KB_10X10 -> pinnedKbProgram
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

    /** KCC-1 signed-magnitude 8-byte little-endian integer (supports +/- up to 2^63-1). */
    fun int8(value: BigInteger): ByteArray {
        val max = BigInteger.valueOf(Long.MAX_VALUE)
        require(value <= max && value >= -max) { "int8 out of signed-magnitude 64-bit range: $value" }
        val neg = value.signum() < 0
        val mag = if (neg) value.negate() else value
        val le = KbKcc20Protocol.encodeAmount(mag)
        if (neg) le[7] = (le[7].toInt() or 0x80).toByte()
        return le
    }

    fun decodeInt8(bytes: ByteArray): BigInteger {
        require(bytes.size == 8) { "int8 must be 8 bytes, got ${bytes.size}" }
        val copy = bytes.copyOf()
        val neg = (copy[7].toInt() and 0x80) != 0
        copy[7] = (copy[7].toInt() and 0x7f).toByte()
        val mag = BigInteger(1, copy.reversedArray())
        return if (neg && mag.signum() != 0) mag.negate() else mag
    }

    fun pushExplicit(bytes: ByteArray): ByteArray = KbKcc20ScriptCodec.pushExplicit(bytes)

    fun pushMinimal(bytes: ByteArray): ByteArray = KbKcc20ScriptCodec.pushMinimal(bytes)

    /** Guide §1.1 / A.2 145-byte state push sequence (0x20 template || 112-byte fields). */
    fun encodeState145(
        state: CellState,
        templateHashHex: String = state.templateHashHex
    ): ByteArray {
        val tpl = KbKcc20ScriptCodec.hexToBytes(templateHashHex)
        require(tpl.size == 32) { "template must be 32 bytes" }
        require(state.owner.size == 32) { "owner must be 32 bytes" }
        require(state.ownerScheme in 0..0xff) { "invalid owner_scheme byte" }
        require(state.borrowScheme in 0..0xff) { "invalid borrow_scheme byte" }
        require(state.borrowGuard.size == 32) { "borrow_guard must be 32 bytes" }
        require(state.extensionCommitment.size == 32) { "extension_commitment must be 32 bytes" }

        val out = KbKcc20ScriptCodec.concat(
            pushExplicit(tpl),
            pushExplicit(int8(state.amount)),
            pushExplicit(state.owner),
            pushExplicit(byteArrayOf(state.ownerScheme.toByte())),
            pushExplicit(byteArrayOf(state.borrowScheme.toByte())),
            pushExplicit(state.borrowGuard),
            pushExplicit(state.extensionCommitment)
        )
        require(out.size == REF_STATE_LEN) { "145-byte state size mismatch: ${out.size}" }
        return out
    }

    fun parseState145(stateBytes: ByteArray): CellState {
        require(stateBytes.size == REF_STATE_LEN) {
            "state length must be $REF_STATE_LEN, got ${stateBytes.size}"
        }
        val expectedOps = intArrayOf(0x20, 0x08, 0x20, 0x01, 0x01, 0x20, 0x20)
        val slices = ArrayList<ByteArray>(7)
        var i = 0
        for (len in expectedOps) {
            val op = stateBytes[i].toInt() and 0xff
            require(op == len) { "bad state push at byte $i: expected $len, got $op" }
            slices += stateBytes.copyOfRange(i + 1, i + 1 + len)
            i += 1 + len
        }
        require(i == REF_STATE_LEN) { "state parse consumed $i of $REF_STATE_LEN bytes" }

        return CellState(
            templateHashHex = KbKcc20ScriptCodec.bytesToHex(slices[0]),
            amount = decodeInt8(slices[1]),
            owner = slices[2],
            ownerScheme = slices[3][0].toInt() and 0xff,
            borrowScheme = slices[4][0].toInt() and 0xff,
            borrowGuard = slices[5],
            extensionCommitment = slices[6]
        )
    }

    fun stateOf(program: ByteArray): CellState {
        require(program.size >= REF_STATE_START + REF_STATE_LEN) {
            "program too short (${program.size})"
        }
        return parseState145(program.copyOfRange(REF_STATE_START, REF_STATE_START + REF_STATE_LEN))
    }

    fun programFor(state: CellState, pinnedProgram: ByteArray): ByteArray {
        val kind = TemplateKind.entries.firstOrNull { it.programLength == pinnedProgram.size }
            ?: throw IllegalArgumentException("not a pinned KCC-20 program size: ${pinnedProgram.size}")
        val encoded = encodeState145(state, kind.templateHash)
        return KbKcc20ScriptCodec.concat(
            pinnedProgram.copyOfRange(0, REF_STATE_START),
            encoded,
            pinnedProgram.copyOfRange(REF_STATE_START + REF_STATE_LEN, pinnedProgram.size)
        )
    }

    fun identifyTemplate(program: ByteArray): TemplateKind {
        require(program.size >= REF_STATE_START + REF_STATE_LEN) { "KCC-20 program is too short" }
        require(program[0] == 0x6b.toByte()) { "Unexpected Argent prefix opcode" }
        require(program[1] == 0x20.toByte()) { "Missing 32-byte Argent template slot" }

        val kind = TemplateKind.entries.firstOrNull { it.programLength == program.size }
            ?: throw IllegalArgumentException("Unrecognized KCC-20 program length ${program.size} (expected 4031 or 11458)")

        val prefix1 = program.copyOfRange(0, REF_STATE_START)
        val suffix = program.copyOfRange(REF_STATE_START + REF_STATE_LEN, program.size)
        val computedTpl = KbKcc20ScriptCodec.bytesToHex(templateHash(prefix1, suffix))
        val embeddedTpl = KbKcc20ScriptCodec.bytesToHex(program.copyOfRange(2, 34))
        require(computedTpl == embeddedTpl) { "Argent KCC-1 self template hash mismatch" }
        require(computedTpl == kind.templateHash) {
            "Template hash $computedTpl does not match pinned ${kind.id} (${kind.templateHash})"
        }

        val viewPrefix = program.copyOfRange(0, KbKcc20Protocol.PROGRAM_STATE_OFFSET)
        val viewSuffix = program.copyOfRange(
            KbKcc20Protocol.PROGRAM_STATE_OFFSET + KbKcc20Protocol.ENCODED_STATE_SIZE,
            program.size
        )
        val computedView = KbKcc20ScriptCodec.bytesToHex(templateHash(viewPrefix, viewSuffix))
        require(computedView == kind.viewTemplateHash) {
            "State-view hash $computedView does not match pinned ${kind.id} (${kind.viewTemplateHash})"
        }

        val pinned = pinnedProgramFor(kind)
        if (pinned != null) {
            require(program[0] == pinned[0]) { "Program prefix mismatch vs pinned ${kind.id}" }
            require(
                suffix.contentEquals(pinned.copyOfRange(REF_STATE_START + REF_STATE_LEN, pinned.size))
            ) {
                "Not the pinned ${kind.id} KCC-20 program outside the 145-byte state window"
            }
        }
        return kind
    }

    fun verifyReferenceTemplate(program: ByteArray): TemplateKind {
        require(program.size == REF_PROGRAM_LEN) {
            "Reference KCC-20 program must be $REF_PROGRAM_LEN bytes, got ${program.size}"
        }
        return identifyTemplate(program)
    }

    fun p2shScript(program: ByteArray): String {
        val hash = CryptoUtils.blake2b256(program)
        return "aa20" + KbKcc20ScriptCodec.bytesToHex(hash) + "87"
    }

    fun p2shAddress(program: ByteArray, networkPrefix: String): String {
        val hash = CryptoUtils.blake2b256(program)
        return CryptoUtils.encodeP2shAddress(hash, networkPrefix)
    }

    /** Guide §3 / A.2 `transfer(next_states, witness)` argument pushes. */
    fun transferArgs(states: List<CellState>): ByteArray {
        require(states.isNotEmpty()) { "transfer needs at least one next state" }
        fun col(pick: (CellState) -> ByteArray): ByteArray =
            pushMinimal(KbKcc20ScriptCodec.concat(*states.map(pick).toTypedArray()))

        return KbKcc20ScriptCodec.concat(
            col { int8(it.amount) },
            col { it.owner },
            col { byteArrayOf(it.ownerScheme.toByte()) },
            col { byteArrayOf(it.borrowScheme.toByte()) },
            col { it.borrowGuard },
            col { it.extensionCommitment }
        )
    }

    private val SIG65_REGEX = Regex("(?i)^41([0-9a-f]{128})01$")

    /** Extract raw 65-byte `sig64 || 0x01` from a wallet SIGHASH_ALL P2PK script `41 <64B sig> 01`. */
    fun sig65(walletSigScriptHex: String): ByteArray {
        val clean = walletSigScriptHex.trim().lowercase()
        require(SIG65_REGEX.matches(clean)) {
            "expected 41 || 64-byte signature || 01 (SIGHASH_ALL)"
        }
        return KbKcc20ScriptCodec.hexToBytes(clean.substring(2))
    }

    /** Guide A.2 `leaderScript`: args || push(0x00 || sig65) || 04 79c71c23 || push(program). */
    fun leaderScript(
        nextStates: List<CellState>,
        walletSigScriptHex: String,
        program: ByteArray
    ): ByteArray {
        val witness = KbKcc20ScriptCodec.concat(byteArrayOf(0x00), sig65(walletSigScriptHex))
        return KbKcc20ScriptCodec.concat(
            transferArgs(nextStates),
            pushMinimal(witness),
            KbKcc20ScriptCodec.hexToBytes("04$TAG_TRANSFER"),
            pushMinimal(program)
        )
    }

    /** Guide A.2 `delegatorScript`: push(sig65) || 04 fd3ef14a || push(program). No path byte. */
    fun delegatorScript(
        walletSigScriptHex: String,
        program: ByteArray
    ): ByteArray {
        return KbKcc20ScriptCodec.concat(
            pushMinimal(sig65(walletSigScriptHex)),
            KbKcc20ScriptCodec.hexToBytes("04$TAG_DELEGATOR"),
            pushMinimal(program)
        )
    }

    /** Guide A.3 `lastPushes`: push-only script parser returning all pushes (or tail). */
    fun parseAllPushes(sigScriptHex: String): List<ByteArray> {
        val b = KbKcc20ScriptCodec.hexToBytes(sigScriptHex)
        val out = ArrayList<ByteArray>()
        var i = 0
        while (i < b.size) {
            val op = b[i++].toInt() and 0xff
            val len = when {
                op == 0x00 -> {
                    out += ByteArray(0)
                    continue
                }
                op in 0x51..0x60 -> {
                    out += byteArrayOf((op - 0x50).toByte())
                    continue
                }
                op == 0x4f -> {
                    out += byteArrayOf(0x81.toByte())
                    continue
                }
                op <= 0x4b -> op
                op == 0x4c -> {
                    require(i < b.size) { "truncated OP_PUSHDATA1 at byte ${i - 1}" }
                    b[i++].toInt() and 0xff
                }
                op == 0x4d -> {
                    require(i + 1 < b.size) { "truncated OP_PUSHDATA2 at byte ${i - 1}" }
                    val n = (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8)
                    i += 2
                    n
                }
                op == 0x4e -> {
                    require(i + 3 < b.size) { "truncated OP_PUSHDATA4 at byte ${i - 1}" }
                    val n = (b[i].toInt() and 0xff) or
                        ((b[i + 1].toInt() and 0xff) shl 8) or
                        ((b[i + 2].toInt() and 0xff) shl 16) or
                        ((b[i + 3].toInt() and 0xff) shl 24)
                    i += 4
                    n
                }
                else -> throw IllegalArgumentException("not push-only at byte ${i - 1} (op=0x${op.toString(16)})")
            }
            require(len >= 0 && i + len <= b.size) { "push length $len out of bounds at $i" }
            out += b.copyOfRange(i, i + len)
            i += len
        }
        return out
    }

    fun lastPushes(sigScriptHex: String, n: Int): List<ByteArray> {
        val all = parseAllPushes(sigScriptHex)
        require(all.size >= n) { "expected at least $n pushes, got ${all.size}" }
        return all.subList(all.size - n, all.size)
    }

    /** Guide A.3 `programFromSpend`: recovers program + template hash from a spend sigScript. */
    fun programFromSpend(sigScriptHex: String): SpendProgramInfo {
        val lastTwo = lastPushes(sigScriptHex, 2)
        val tag = lastTwo[0]
        val program = lastTwo[1]
        require(program.size >= REF_STATE_START + REF_STATE_LEN) {
            "spent program too short: ${program.size}"
        }
        val prefix = program.copyOfRange(0, REF_STATE_START)
        val suffix = program.copyOfRange(REF_STATE_START + REF_STATE_LEN, program.size)
        val computedTplHex = KbKcc20ScriptCodec.bytesToHex(templateHash(prefix, suffix))
        val stateTplHex = KbKcc20ScriptCodec.bytesToHex(
            program.copyOfRange(REF_STATE_START + 1, REF_STATE_START + 33)
        )
        return SpendProgramInfo(
            tagHex = KbKcc20ScriptCodec.bytesToHex(tag),
            program = program,
            programBytes = program.size,
            templateHash = computedTplHex,
            stateTemplateField = stateTplHex,
            selfConsistent = computedTplHex == stateTplHex
        )
    }

    /**
     * Decodes `next_states` from a leader input's `transfer(next_states, witness)` signature script.
     * Layout: [amounts, owners, owner_schemes, borrow_schemes, borrow_guards, ext_commitments, witness, tag(79c71c23), program].
     */
    fun decodeLeaderTransferNextStates(sigScriptHex: String): List<CellState>? {
        val pushes = try {
            parseAllPushes(sigScriptHex)
        } catch (_: Exception) {
            return null
        }
        if (pushes.size < 9) return null
        val n = pushes.size
        val tag = KbKcc20ScriptCodec.bytesToHex(pushes[n - 2])
        if (tag != TAG_TRANSFER) return null
        val program = pushes[n - 1]
        if (program.size < REF_STATE_START + 33) return null
        val tplHex = KbKcc20ScriptCodec.bytesToHex(
            program.copyOfRange(REF_STATE_START + 1, REF_STATE_START + 33)
        )

        val amountsCol = pushes[n - 9]
        val ownersCol = pushes[n - 8]
        val ownerSchemesCol = pushes[n - 7]
        val borrowSchemesCol = pushes[n - 6]
        val borrowGuardsCol = pushes[n - 5]
        val extCommitmentsCol = pushes[n - 4]

        val count = ownerSchemesCol.size
        if (count <= 0) return null
        if (amountsCol.size != count * 8 ||
            ownersCol.size != count * 32 ||
            borrowSchemesCol.size != count ||
            borrowGuardsCol.size != count * 32 ||
            extCommitmentsCol.size != count * 32
        ) {
            return null
        }

        return (0 until count).map { idx ->
            CellState(
                templateHashHex = tplHex,
                amount = decodeInt8(amountsCol.copyOfRange(idx * 8, (idx + 1) * 8)),
                owner = ownersCol.copyOfRange(idx * 32, (idx + 1) * 32),
                ownerScheme = ownerSchemesCol[idx].toInt() and 0xff,
                borrowScheme = borrowSchemesCol[idx].toInt() and 0xff,
                borrowGuard = borrowGuardsCol.copyOfRange(idx * 32, (idx + 1) * 32),
                extensionCommitment = extCommitmentsCol.copyOfRange(idx * 32, (idx + 1) * 32)
            )
        }
    }

    /**
     * Guide §2.3 & A.4 `verifyCell`: validates every single cell candidate against pinned
     * program bytes, parsed state, rebuilt program bytes, P2SH address, and node UTXO set.
     */
    suspend fun verifyCell(
        coin: CandidateCell,
        expectedCovenantId: String,
        networkPrefix: String,
        utxosAt: suspend (String) -> List<NodeUtxo>,
        creatingTxCovenantAt: (suspend (String, Int) -> String?)? = null
    ): VerifiedCell {
        val cleanExpectCov = expectedCovenantId.trim().lowercase().removePrefix("0x")
        require(HEX_32_REGEX.matches(cleanExpectCov)) {
            "Family covenantId must be 32-byte hex (Guide §2.3 check 6)"
        }
        require(networkPrefix == "kaspa" || networkPrefix == "kaspatest") {
            "Unsupported Kaspa network prefix: $networkPrefix"
        }

        val program = KbKcc20ScriptCodec.hexToBytes(coin.redeemScriptHex)
        // Check 1: exact pinned program outside the 145-byte state window
        val kind = identifyTemplate(program)
        val pinned = pinnedProgramFor(kind)
            ?: throw IllegalStateException("Pinned KCC-20 ${kind.id} program bytes missing from resources")

        // Check 2: window parses as the seven canonical pushes, template matches pinned hash, amount >= 0
        val state = stateOf(program)
        require(state.templateHashHex == kind.templateHash) {
            "state template field ${state.templateHashHex} != pinned ${kind.templateHash}"
        }
        require(state.amount >= BigInteger.ZERO) { "negative cell amount: ${state.amount}" }
        require(state.ownerScheme in 0..0x04) { "unsupported owner_scheme ${state.ownerScheme}" }
        require(state.borrowScheme in 0..0x03) { "unsupported borrow_scheme ${state.borrowScheme}" }

        // Check 3: rebuilding the program from parsed state alone produces the exact same bytes
        val rebuilt = programFor(state, pinned)
        require(rebuilt.contentEquals(program)) { "cell program rebuild mismatch" }

        // Check 4: derived P2SH address on target network equals the cell's claimed address
        val derivedAddr = p2shAddress(program, networkPrefix)
        require(derivedAddr.equals(coin.address.trim(), ignoreCase = true)) {
            "P2SH of program is $derivedAddr, coin claimed ${coin.address}"
        }

        // Check 5: node holds outpoint with expected KAS and matching covenant ID
        val cleanTxid = coin.txid.trim().lowercase()
        val u = utxosAt(derivedAddr).firstOrNull {
            it.txid.equals(cleanTxid, ignoreCase = true) && it.vout == coin.vout
        } ?: throw IllegalStateException(
            "outpoint $cleanTxid:${coin.vout} is not in node UTXO set at $derivedAddr (spent, unaccepted, or forged)"
        )
        if (coin.kasSompi != null) {
            require(u.amount == coin.kasSompi) {
                "KAS amount ${u.amount} differs from claimed ${coin.kasSompi}"
            }
        }
        val observedCovenant = u.covenantId
            ?.trim()?.lowercase()?.removePrefix("0x")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?: creatingTxCovenantAt?.invoke(cleanTxid, coin.vout)?.trim()?.lowercase()?.removePrefix("0x")
        require(observedCovenant == cleanExpectCov) {
            "UTXO carries covenant ${observedCovenant ?: "none"}, expected $cleanExpectCov"
        }

        return VerifiedCell(
            ok = true,
            templateKind = kind,
            state = state,
            address = derivedAddr,
            kasSompi = u.amount,
            covenantId = cleanExpectCov,
            program = program
        )
    }

    fun toKbState(state: CellState): KbKcc20State {
        require(state.amount.signum() >= 0) { "KCC-20 amount cannot be negative" }
        return KbKcc20State(
            amountBaseUnits = state.amount,
            amountEncoding = KbKcc20Protocol.encodeAmount(state.amount),
            owner = state.owner.copyOf(),
            ownerScheme = state.ownerScheme,
            borrowScheme = state.borrowScheme,
            borrowGuard = state.borrowGuard.copyOf(),
            extensionCommitment = state.extensionCommitment.copyOf()
        )
    }

    fun fromKbState(state: KbKcc20State, templateHashHex: String = KB_TEMPLATE_HASH): CellState =
        CellState(
            templateHashHex = templateHashHex,
            amount = state.amountBaseUnits,
            owner = state.owner.copyOf(),
            ownerScheme = state.ownerScheme,
            borrowScheme = state.borrowScheme,
            borrowGuard = state.borrowGuard.copyOf(),
            extensionCommitment = state.extensionCommitment.copyOf()
        )
}
