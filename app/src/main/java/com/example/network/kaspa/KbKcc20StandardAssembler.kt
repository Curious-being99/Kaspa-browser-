package com.example.network.kaspa

import java.math.BigInteger

/**
 * Real KCC-20 **standard** transfer assembly for the pinned KB layout (`kcc0020-112`,
 * Argent state at offset 34).
 *
 * Ported from the production standard-wallet model (leader/delegator seats, successor
 * program splice, 65-byte SIGHASH_ALL placeholders). Does **not** invent plain-KAS sends.
 *
 * Scope of this module:
 * - split / successor programs
 * - transfer argument encoding + leader/delegator signatureScript seats
 * - patch real Schnorr signatures into seats
 *
 * Live cell discovery, funding, script-engine preflight and node submission are
 * handled by the separate Kcc20StandardTransactionExecutor.
 */
object KbKcc20StandardAssembler {

    const val PROGRAM_PREFIX_LEN = 34 // matches KbKcc20Protocol.PROGRAM_STATE_OFFSET
    const val STATE_LEN = KbKcc20Protocol.ENCODED_STATE_SIZE // 112
    const val STATE_END = PROGRAM_PREFIX_LEN + STATE_LEN // 146
    const val SIG_LEN = 65
    const val SIGHASH_ALL: Int = 0x01
    /** Leader normal owner-authorized path (KCC-20 § transfer witness). */
    const val WITNESS_PATH_TRANSFER: Int = 0x00

    private val TRANSFER_TAG: ByteArray =
        KbKcc20ScriptCodec.hexToBytes(KbKcc20Protocol.TRANSFER_DISPATCH)
    private val TRANSFER_DELEGATOR_TAG: ByteArray =
        KbKcc20ScriptCodec.hexToBytes(KbKcc20Protocol.TRANSFER_DELEGATOR_DISPATCH)
    /** `04` + 4-byte tag */
    private val TAG_PUSH_LEN: Int = 1 + TRANSFER_TAG.size

    data class ProgramParts(
        val prefix: ByteArray,
        val state: KbKcc20State,
        val suffix: ByteArray,
        val full: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ProgramParts) return false
            return full.contentEquals(other.full)
        }

        override fun hashCode(): Int = full.contentHashCode()
    }

    data class Seat(
        val inputIndex: Int,
        val script: ByteArray,
        /** Byte offset of the 65-byte signature inside [script]. */
        val sigOffset: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Seat) return false
            return inputIndex == other.inputIndex && script.contentEquals(other.script) &&
                sigOffset == other.sigOffset
        }

        override fun hashCode(): Int =
            31 * (31 * inputIndex + script.contentHashCode()) + sigOffset
    }

    data class SpendPiece(
        val transactionId: String,
        val index: Int,
        val valueSompi: Long,
        /** Full redeem / program bytes R for this cell. */
        val redeem: ByteArray
    )

    data class StandardPlan(
        val consumed: List<KbKcc20State>,
        val nextStates: List<KbKcc20State>,
        val successors: List<ByteArray>,
        val seats: List<Seat>,
        val inputSum: BigInteger,
        val outputSum: BigInteger,
        val covenantId: String
    )

    /**
     * Split R = prefix[0:34] || state[34:146] || suffix[146:].
     * Verifies the 112-byte region decodes and (when possible) matches the pinned Argent template.
     */
    /**
     * @param requireArgentTemplate when true (default), [KbKcc20Protocol.verifyArgentTemplate]
     * must succeed. Production planning always uses true. Layout-only unit tests may pass false.
     */
    fun splitProgram(program: ByteArray, requireArgentTemplate: Boolean = true): ProgramParts {
        require(program.size >= STATE_END) {
            "program too short for standard KCC-20 layout (${program.size} < $STATE_END)"
        }
        val prefix = program.copyOfRange(0, PROGRAM_PREFIX_LEN)
        val stateBytes = program.copyOfRange(PROGRAM_PREFIX_LEN, STATE_END)
        val suffix = program.copyOfRange(STATE_END, program.size)
        val state = KbKcc20Protocol.decodeState(stateBytes)
        if (requireArgentTemplate) {
            // Fail closed: do not plan spends against an unverified program.
            KbKcc20Protocol.verifyArgentTemplate(program)
        }
        return ProgramParts(prefix = prefix, state = state, suffix = suffix, full = program)
    }

    /** R' = prefix || encodeState(next) || suffix — same length, same template body. */
    fun successorProgram(
        program: ByteArray,
        next: KbKcc20State,
        requireArgentTemplate: Boolean = true
    ): ByteArray {
        val parts = splitProgram(program, requireArgentTemplate = requireArgentTemplate)
        val encoded = KbKcc20Protocol.encodeState(next)
        require(encoded.size == STATE_LEN)
        val successor = KbKcc20ScriptCodec.concat(parts.prefix, encoded, parts.suffix)
        require(successor.size == program.size) {
            "successor program length changed (${successor.size} vs ${program.size})"
        }
        // Template hash region (bytes after first prefix byte through end of prefix) must match.
        require(
            successor.copyOfRange(2, PROGRAM_PREFIX_LEN)
                .contentEquals(program.copyOfRange(2, PROGRAM_PREFIX_LEN))
        ) { "successor program altered template region" }
        return successor
    }

    /**
     * Owner witness with zeroed 65-byte signature slot.
     * Leader includes path byte 0x00 (normal transfer); delegator has no path byte.
     */
    fun ownerWitnessPlaceholder(
        state: KbKcc20State,
        pubkey32: ByteArray,
        leader: Boolean
    ): ByteArray {
        require(pubkey32.size == 32) { "pubkey32 must be 32 bytes" }
        // Match planStandardTransfer: only p2pk-schnorr with exact owner match.
        // p2pkh is rejected until a verified P2PKHHash(pubkey) check exists.
        require(state.ownerScheme == 0x00) {
            if (state.ownerScheme == 0x01) {
                "owner_scheme p2pkh-schnorr is not accepted without a verified pubkey-hash check"
            } else {
                "owner_scheme ${state.ownerScheme} is not signable by a Schnorr hot wallet"
            }
        }
        require(state.owner.contentEquals(pubkey32)) {
            "cell owner does not match wallet pubkey"
        }
        val path = if (leader) byteArrayOf(WITNESS_PATH_TRANSFER.toByte()) else ByteArray(0)
        val sig = ByteArray(SIG_LEN) // zeros → patched after sighash
        return KbKcc20ScriptCodec.concat(path, sig)
    }

    /**
     * `transfer(next_states, witness)` argument stack encoding (column-major state fields),
     * then PushMinimal(witness), then PushExplicit(transfer tag).
     */
    fun encodeTransferArgs(states: List<KbKcc20State>, witness: ByteArray): ByteArray {
        require(states.isNotEmpty()) { "transfer needs at least one next state" }
        fun column(pick: (KbKcc20State) -> ByteArray): ByteArray {
            val merged = KbKcc20ScriptCodec.concat(*states.map(pick).toTypedArray())
            return KbKcc20ScriptCodec.pushMinimal(merged)
        }
        return KbKcc20ScriptCodec.concat(
            column { KbKcc20Protocol.encodeAmount(it.amountBaseUnits) },
            column { it.owner },
            column { byteArrayOf(it.ownerScheme.toByte()) },
            column { byteArrayOf(it.borrowScheme.toByte()) },
            column { it.borrowGuard },
            column { it.extensionCommitment },
            KbKcc20ScriptCodec.pushMinimal(witness),
            KbKcc20ScriptCodec.pushExplicit(TRANSFER_TAG)
        )
    }

    private fun seat(inputIndex: Int, args: ByteArray, redeem: ByteArray): Seat {
        val script = KbKcc20ScriptCodec.concat(
            args,
            KbKcc20ScriptCodec.pushMinimal(redeem)
        )
        // Signature is the last 65 bytes of the witness push, immediately before the 5-byte tag push.
        val sigOffset = args.size - TAG_PUSH_LEN - SIG_LEN
        require(sigOffset >= 0) { "seat args too short for signature slot" }
        return Seat(inputIndex = inputIndex, script = script, sigOffset = sigOffset)
    }

    fun encodeLeaderSeat(
        inputIndex: Int,
        nextStates: List<KbKcc20State>,
        witness: ByteArray,
        redeem: ByteArray
    ): Seat = seat(inputIndex, encodeTransferArgs(nextStates, witness), redeem)

    fun encodeDelegatorSeat(
        inputIndex: Int,
        witness: ByteArray,
        redeem: ByteArray
    ): Seat = seat(
        inputIndex,
        KbKcc20ScriptCodec.concat(
            KbKcc20ScriptCodec.pushMinimal(witness),
            KbKcc20ScriptCodec.pushExplicit(TRANSFER_DELEGATOR_TAG)
        ),
        redeem
    )

    fun patchSeat(seat: Seat, signature65: ByteArray): ByteArray {
        require(signature65.size == SIG_LEN) { "signature must be 65 bytes (64 sig + sighash type)" }
        require((signature65[SIG_LEN - 1].toInt() and 0xff) == SIGHASH_ALL) {
            "only SIGHASH_ALL (0x01) is supported for KCC-20 seats"
        }
        val at = seat.script.copyOfRange(seat.sigOffset, seat.sigOffset + SIG_LEN)
        require(at.all { it == 0.toByte() }) { "no zero placeholder at sigOffset" }
        val out = seat.script.copyOf()
        System.arraycopy(signature65, 0, out, seat.sigOffset, SIG_LEN)
        return out
    }

    /**
     * Plan a standard transfer from one or more live cells owned by [pubkey32].
     * [amountBaseUnits] is the amount to send to [recipientOwner] (p2pk-schnorr).
     * Change (if any) returns to the leader cell's owner/borrow policy.
     */
    fun planStandardTransfer(
        pieces: List<SpendPiece>,
        pubkey32: ByteArray,
        recipientOwner: ByteArray,
        amountBaseUnits: BigInteger
    ): StandardPlan {
        require(pubkey32.size == 32 && recipientOwner.size == 32)
        require(amountBaseUnits.signum() > 0) { "amount must be positive" }
        require(pieces.isNotEmpty()) { "need at least one token cell" }
        require(pieces.size <= KbKcc20Protocol.MAX_TOKEN_INPUTS)

        val parsed = pieces.map { p -> p to splitProgram(p.redeem) }
        val leader = parsed[0].second
        for ((_, prog) in parsed) {
            require(prog.prefix.contentEquals(leader.prefix) && prog.suffix.contentEquals(leader.suffix)) {
                "token cells do not share one program template"
            }
        }

        val consumed = parsed.map { it.second.state }
        // Ownership check: leader (and all) must be controlled by pubkey for p2pk
        for (s in consumed) {
            when (s.ownerScheme) {
                0x00 -> require(s.owner.contentEquals(pubkey32)) {
                    "cell owner does not match wallet pubkey"
                }
                // Refuse p2pkh until a verified P2PKHHash(pubkey) check is implemented.
                0x01 -> throw IllegalArgumentException(
                    "owner_scheme p2pkh-schnorr is not accepted without a verified pubkey-hash check"
                )
                else -> throw IllegalArgumentException("unsupported owner scheme ${s.ownerScheme}")
            }
        }

        val inputSum = consumed.fold(BigInteger.ZERO) { a, s -> a + s.amountBaseUnits }
        require(amountBaseUnits <= inputSum) { "amount exceeds token inputs" }

        val ext = leader.state.extensionCommitment
        val nextStates = mutableListOf(
            KbKcc20State(
                amountBaseUnits = amountBaseUnits,
                amountEncoding = KbKcc20Protocol.encodeAmount(amountBaseUnits),
                owner = recipientOwner.copyOf(),
                ownerScheme = 0x00,
                borrowScheme = 0x00,
                borrowGuard = ByteArray(32),
                extensionCommitment = ext.copyOf()
            )
        )
        val change = inputSum - amountBaseUnits
        if (change.signum() > 0) {
            nextStates += KbKcc20State(
                amountBaseUnits = change,
                amountEncoding = KbKcc20Protocol.encodeAmount(change),
                owner = leader.state.owner.copyOf(),
                ownerScheme = leader.state.ownerScheme,
                borrowScheme = leader.state.borrowScheme,
                borrowGuard = leader.state.borrowGuard.copyOf(),
                extensionCommitment = ext.copyOf()
            )
        }
        require(nextStates.size <= KbKcc20Protocol.MAX_TOKEN_OUTPUTS)

        val leaderWitness = ownerWitnessPlaceholder(leader.state, pubkey32, leader = true)
        val seats = parsed.mapIndexed { i, (piece, prog) ->
            if (i == 0) {
                encodeLeaderSeat(0, nextStates, leaderWitness, piece.redeem)
            } else {
                val w = ownerWitnessPlaceholder(prog.state, pubkey32, leader = false)
                encodeDelegatorSeat(i, w, piece.redeem)
            }
        }

        val successors = nextStates.map { successorProgram(pieces[0].redeem, it) }
        val outputSum = nextStates.fold(BigInteger.ZERO) { a, s -> a + s.amountBaseUnits }
        require(outputSum == inputSum) { "conservation failed" }

        return StandardPlan(
            consumed = consumed,
            nextStates = nextStates,
            successors = successors,
            seats = seats,
            inputSum = inputSum,
            outputSum = outputSum,
            covenantId = KbKcc20Protocol.TOKEN_COVENANT_ID
        )
    }

    /**
     * Build 65-byte signature payload: 64-byte Schnorr + SIGHASH_ALL.
     * [raw64] must be the raw Schnorr signature over the seat's sighash.
     */
    fun signaturePayload(raw64Hex: String): ByteArray {
        val raw = KbKcc20ScriptCodec.hexToBytes(raw64Hex)
        require(raw.size == 64) { "Schnorr signature must be 64 bytes" }
        return KbKcc20ScriptCodec.concat(raw, byteArrayOf(SIGHASH_ALL.toByte()))
    }
}
