package com.example.network.kaspa

import java.math.BigInteger

/**
 * Fail-closed KB KCC-20 transfer planner.
 *
 * Responsibilities:
 * - Encode / decode 112-byte KCC-20 states (round-trip with [KbKcc20Protocol])
 * - Plan amount conservation (input sum == output sum; no mint/burn via transfer)
 * - Refuse to emit a signable / broadcastable transaction until the pinned
 *   deployment artifact is published AND a verified covenant tx assembler exists
 *
 * Intentionally does NOT:
 * - Fall back to a plain KAS payment presented as a token transfer
 * - Sign or broadcast anything while ARTIFACT_STATUS is "unpublished"
 */
object KbKcc20TransferBuilder {

    data class TransferRequest(
        val senderOwner: ByteArray,
        val senderOwnerScheme: Int = 0x00,
        val recipientOwner: ByteArray,
        val recipientOwnerScheme: Int = 0x00,
        val amountBaseUnits: BigInteger,
        val inputAmountBaseUnits: BigInteger,
        val borrowScheme: Int = 0x00,
        val borrowGuard: ByteArray = ByteArray(32),
        val extensionCommitment: ByteArray = ByteArray(32)
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TransferRequest) return false
            return senderOwner.contentEquals(other.senderOwner) &&
                senderOwnerScheme == other.senderOwnerScheme &&
                recipientOwner.contentEquals(other.recipientOwner) &&
                recipientOwnerScheme == other.recipientOwnerScheme &&
                amountBaseUnits == other.amountBaseUnits &&
                inputAmountBaseUnits == other.inputAmountBaseUnits &&
                borrowScheme == other.borrowScheme &&
                borrowGuard.contentEquals(other.borrowGuard) &&
                extensionCommitment.contentEquals(other.extensionCommitment)
        }

        override fun hashCode(): Int {
            var result = senderOwner.contentHashCode()
            result = 31 * result + senderOwnerScheme
            result = 31 * result + recipientOwner.contentHashCode()
            result = 31 * result + recipientOwnerScheme
            result = 31 * result + amountBaseUnits.hashCode()
            result = 31 * result + inputAmountBaseUnits.hashCode()
            result = 31 * result + borrowScheme
            result = 31 * result + borrowGuard.contentHashCode()
            result = 31 * result + extensionCommitment.contentHashCode()
            return result
        }
    }

    data class PlannedState(
        val role: String,
        val state: KbKcc20State,
        val encoded: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PlannedState) return false
            return role == other.role && encoded.contentEquals(other.encoded)
        }

        override fun hashCode(): Int = 31 * role.hashCode() + encoded.contentHashCode()
    }

    data class TransferPlan(
        val nextStates: List<PlannedState>,
        val transferDispatch: String,
        val transferDelegatorDispatch: String,
        val covenantId: String,
        val inputSum: BigInteger,
        val outputSum: BigInteger
    )

    sealed class BuildResult {
        data class Planned(val plan: TransferPlan) : BuildResult()
        data class Rejected(val reason: String) : BuildResult()
    }

    /**
     * Human-readable gate status for UI (button label / protection card).
     * Signing must stay disabled until this returns [TransferGate.Ready].
     */
    sealed class TransferGate {
        data class Blocked(val reason: String) : TransferGate()
        object Ready : TransferGate()
    }

    fun gateStatus(): TransferGate {
        val config = KbKcc20Protocol.reviewedConfig
        if (config.artifactStatus != "published") {
            return TransferGate.Blocked(
                "KB transfer artifact is ${config.artifactStatus}; " +
                    "standard seat assembler is present but artifact is not published"
            )
        }
        if (config.conformant != true) {
            return TransferGate.Blocked("KB covenant conformance is not claimed; refusing transfers")
        }
        // Assembler can plan seats + successors; full tx still needs live UTXOs, funding,
        // script-engine preflight, and broadcast wiring before Ready.
        return TransferGate.Blocked(
            "Standard transfer seats/successors can be planned, but live-cell funding " +
                "and script-engine preflight are not enabled yet — refusing broadcast"
        )
    }

    fun isSigningAllowed(): Boolean = gateStatus() is TransferGate.Ready

    /**
     * Plan a simple 1-in → 2-out (or 1-out full-send) transfer with amount conservation.
     * Does not produce network transactions.
     */
    fun planTransfer(request: TransferRequest): BuildResult {
        try {
            KbKcc20Protocol.validateConfig()
        } catch (e: IllegalArgumentException) {
            return BuildResult.Rejected(e.message ?: "Invalid KB deployment config")
        }

        if (request.senderOwner.size != 32 || request.recipientOwner.size != 32) {
            return BuildResult.Rejected("Owner identifiers must be 32 bytes")
        }
        if (request.amountBaseUnits.signum() <= 0) {
            return BuildResult.Rejected("Transfer amount must be positive")
        }
        if (request.inputAmountBaseUnits.signum() <= 0) {
            return BuildResult.Rejected("Input token amount must be positive")
        }
        if (request.amountBaseUnits > request.inputAmountBaseUnits) {
            return BuildResult.Rejected("Transfer amount exceeds input token balance")
        }
        if (request.senderOwnerScheme !in KbKcc20Protocol.supportedOwnerSchemes) {
            return BuildResult.Rejected("Unsupported sender owner scheme")
        }
        if (request.recipientOwnerScheme !in KbKcc20Protocol.supportedOwnerSchemes) {
            return BuildResult.Rejected("Unsupported recipient owner scheme")
        }
        if (request.borrowScheme !in KbKcc20Protocol.supportedBorrowSchemes) {
            return BuildResult.Rejected("Unsupported borrow scheme")
        }

        val change = request.inputAmountBaseUnits - request.amountBaseUnits
        val next = mutableListOf<PlannedState>()

        // Recipient output
        val recipientState = KbKcc20State(
            amountBaseUnits = request.amountBaseUnits,
            amountEncoding = KbKcc20Protocol.encodeAmount(request.amountBaseUnits),
            owner = request.recipientOwner.copyOf(),
            ownerScheme = request.recipientOwnerScheme,
            borrowScheme = request.borrowScheme,
            borrowGuard = request.borrowGuard.copyOf(),
            extensionCommitment = request.extensionCommitment.copyOf()
        )
        next += PlannedState(
            role = "recipient",
            state = recipientState,
            encoded = KbKcc20Protocol.encodeState(recipientState)
        )

        // Change back to sender when not a full spend
        if (change.signum() > 0) {
            val changeState = KbKcc20State(
                amountBaseUnits = change,
                amountEncoding = KbKcc20Protocol.encodeAmount(change),
                owner = request.senderOwner.copyOf(),
                ownerScheme = request.senderOwnerScheme,
                borrowScheme = request.borrowScheme,
                borrowGuard = request.borrowGuard.copyOf(),
                extensionCommitment = request.extensionCommitment.copyOf()
            )
            next += PlannedState(
                role = "change",
                state = changeState,
                encoded = KbKcc20Protocol.encodeState(changeState)
            )
        }

        if (next.size > KbKcc20Protocol.MAX_TOKEN_OUTPUTS) {
            return BuildResult.Rejected("Exceeds max token outputs")
        }

        val outputSum = next.fold(BigInteger.ZERO) { acc, p -> acc + p.state.amountBaseUnits }
        if (outputSum != request.inputAmountBaseUnits) {
            return BuildResult.Rejected("Amount conservation failed: in=$request.inputAmountBaseUnits out=$outputSum")
        }

        val plan = TransferPlan(
            nextStates = next,
            transferDispatch = KbKcc20Protocol.TRANSFER_DISPATCH,
            transferDelegatorDispatch = KbKcc20Protocol.TRANSFER_DELEGATOR_DISPATCH,
            covenantId = KbKcc20Protocol.TOKEN_COVENANT_ID,
            inputSum = request.inputAmountBaseUnits,
            outputSum = outputSum
        )
        return BuildResult.Planned(plan)
    }

    /**
     * Build a network transaction only when the gate is open.
     * Currently always refuses — prevents plain-KAS misuse.
     */
    fun buildSignableTransaction(request: TransferRequest): BuildResult {
        when (val gate = gateStatus()) {
            is TransferGate.Blocked -> return BuildResult.Rejected(gate.reason)
            TransferGate.Ready -> { /* continue */ }
        }
        val planned = planTransfer(request)
        if (planned is BuildResult.Rejected) return planned
        return BuildResult.Rejected(
            "Live-cell funding + script-engine preflight not enabled; refusing signable payload"
        )
    }

    /**
     * Plan seats/successors from real redeem programs (no broadcast).
     * Requires live [KbKcc20StandardAssembler.SpendPiece] cells owned by [pubkey32].
     */
    fun planFromLiveCells(
        pieces: List<KbKcc20StandardAssembler.SpendPiece>,
        pubkey32: ByteArray,
        recipientOwner: ByteArray,
        amountBaseUnits: java.math.BigInteger
    ): BuildResult {
        return try {
            val plan = KbKcc20StandardAssembler.planStandardTransfer(
                pieces = pieces,
                pubkey32 = pubkey32,
                recipientOwner = recipientOwner,
                amountBaseUnits = amountBaseUnits
            )
            BuildResult.Planned(
                TransferPlan(
                    nextStates = plan.nextStates.mapIndexed { i, s ->
                        PlannedState(
                            role = if (i == 0) "recipient" else "change",
                            state = s,
                            encoded = KbKcc20Protocol.encodeState(s)
                        )
                    },
                    transferDispatch = KbKcc20Protocol.TRANSFER_DISPATCH,
                    transferDelegatorDispatch = KbKcc20Protocol.TRANSFER_DELEGATOR_DISPATCH,
                    covenantId = plan.covenantId,
                    inputSum = plan.inputSum,
                    outputSum = plan.outputSum
                )
            )
        } catch (e: IllegalArgumentException) {
            BuildResult.Rejected(e.message ?: "standard plan failed")
        }
    }
}
