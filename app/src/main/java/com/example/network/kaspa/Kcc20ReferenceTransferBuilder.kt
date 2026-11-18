package com.example.network.kaspa

import java.math.BigInteger

/** Builds reference KCC20 leader/delegator signature-script seats from verified reference cells. */
object Kcc20ReferenceTransferBuilder {
    data class SignatureSeat(
        val inputIndex: Int,
        val scriptPrefix: ByteArray,
        val program: ByteArray,
        val isLeader: Boolean
    )

    data class TransferPlan(
        val covenantId: String,
        val consumed: List<Kcc20ReferenceProtocol.State>,
        val nextStates: List<Kcc20ReferenceProtocol.State>,
        val successorPrograms: List<ByteArray>,
        val seats: List<SignatureSeat>,
        val inputAmount: BigInteger,
        val outputAmount: BigInteger
    )

    fun plan(
        cells: List<Kcc20ReferenceCellVerifier.VerifiedCell>,
        expectedCovenantId: String,
        senderPublicKey: ByteArray,
        recipientPublicKey: ByteArray,
        amountBaseUnits: BigInteger
    ): TransferPlan {
        val family = expectedCovenantId.trim().lowercase()
        require(family.matches(Regex("[0-9a-f]{64}"))) { "Expected covenant ID must be 32-byte hex" }
        require(senderPublicKey.size == 32 && recipientPublicKey.size == 32)
        require(amountBaseUnits.signum() > 0) { "Transfer amount must be positive" }
        require(cells.isNotEmpty() && cells.size <= Kcc20ReferenceProtocol.MAX_TOKEN_INPUTS) {
            "Reference KCC20 spends require 1 to 3 cell inputs"
        }
        require(cells.map { it.transactionId.lowercase() + ":" + it.outputIndex }.distinct().size == cells.size) {
            "Duplicate KCC20 cell outpoint"
        }
        require(cells.all { it.outputIndex >= 0 && it.valueSompi > 0L }) { "Invalid cell outpoint or KAS value" }

        val states = cells.map { cell ->
            require(cell.covenantId.equals(family, ignoreCase = true)) { "Cell belongs to a different covenant family" }
            Kcc20ReferenceProtocol.verifyProgram(cell.program).also { state ->
                require(state.ownerScheme == 0x00) { "Only P2PK Schnorr-owned cells are supported by this transfer path" }
                require(state.owner.contentEquals(senderPublicKey)) { "Cell owner does not match the active wallet" }
                require(state.borrowScheme == 0x00) { "Borrow paths are not covered by the guide" }
                require(state.extensionCommitment.all { it == 0.toByte() }) { "Non-zero extension commitments are not covered by the guide" }
            }
        }
        val first = states.first()
        require(states.all {
            it.extensionCommitment.contentEquals(first.extensionCommitment) &&
                it.borrowScheme == first.borrowScheme
        }) { "All cells in a family spend must share the leader extension commitment and supported borrow policy" }

        val inputAmount = states.fold(BigInteger.ZERO) { total, state -> total + state.amount }
        require(amountBaseUnits <= inputAmount) { "Transfer amount exceeds selected token cells" }

        val next = mutableListOf(
            Kcc20ReferenceProtocol.State(
                amount = amountBaseUnits,
                owner = recipientPublicKey.copyOf(),
                ownerScheme = 0x00,
                borrowScheme = 0x00,
                borrowGuard = ByteArray(32),
                extensionCommitment = first.extensionCommitment.copyOf()
            )
        )
        val change = inputAmount - amountBaseUnits
        if (change.signum() > 0) {
            next += Kcc20ReferenceProtocol.State(
                amount = change,
                owner = first.owner.copyOf(),
                ownerScheme = first.ownerScheme,
                borrowScheme = first.borrowScheme,
                borrowGuard = first.borrowGuard.copyOf(),
                extensionCommitment = first.extensionCommitment.copyOf()
            )
        }
        require(next.size <= Kcc20ReferenceProtocol.MAX_TOKEN_OUTPUTS)
        require(next.fold(BigInteger.ZERO) { total, state -> total + state.amount } == inputAmount) {
            "KCC20 transfer amount conservation failed"
        }

        val seats = cells.mapIndexed { index, cell ->
            val state = states[index]
            val scriptPrefix = if (index == 0) {
                Kcc20ReferenceProtocol.transferArguments(next)
            } else {
                ByteArray(0)
            }
            SignatureSeat(
                inputIndex = index,
                scriptPrefix = scriptPrefix,
                program = cell.program.copyOf(),
                isLeader = index == 0
            )
        }
        val successors = next.map { Kcc20ReferenceProtocol.programFor(it) }
        return TransferPlan(
            covenantId = family,
            consumed = states,
            nextStates = next,
            successorPrograms = successors,
            seats = seats,
            inputAmount = inputAmount,
            outputAmount = inputAmount
        )
    }

    fun assembleSignatureScript(
        plan: TransferPlan,
        inputIndex: Int,
        signature65: ByteArray
    ): ByteArray {
        require(inputIndex in plan.seats.indices) { "Unknown KCC20 input index" }
        val seat = plan.seats[inputIndex]
        return if (seat.isLeader) {
            Kcc20ReferenceProtocol.leaderSignatureScript(
                plan.nextStates,
                signature65,
                seat.program
            )
        } else {
            Kcc20ReferenceProtocol.delegatorSignatureScript(signature65, seat.program)
        }
    }
}
