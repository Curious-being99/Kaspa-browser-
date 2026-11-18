package com.example.network.kaspa

import java.math.BigInteger

/** Builds reference KCC20 leader/delegator signature-script seats from verified reference cells. */
object Kcc20ReferenceTransferBuilder {
    data class SignatureSeat(
        val inputIndex: Int,
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

    fun selectMinimalSpendCells(
        candidates: List<Kcc20ReferenceCellVerifier.VerifiedCell>,
        amountBaseUnits: BigInteger
    ): List<Kcc20ReferenceCellVerifier.VerifiedCell> {
        require(amountBaseUnits.signum() > 0) { "Transfer amount must be positive" }
        val ordered = candidates
            .distinctBy { it.transactionId.lowercase() + ":" + it.outputIndex }
            .sortedWith(
                compareByDescending<Kcc20ReferenceCellVerifier.VerifiedCell> {
                    Kcc20ReferenceProtocol.verifyProgram(it.program).amount
                }.thenBy { it.transactionId }.thenBy { it.outputIndex }
            )
        val selected = mutableListOf<Kcc20ReferenceCellVerifier.VerifiedCell>()
        var total = BigInteger.ZERO
        for (cell in ordered) {
            if (selected.size == Kcc20ReferenceProtocol.MAX_TOKEN_INPUTS) break
            selected += cell
            total += Kcc20ReferenceProtocol.verifyProgram(cell.program).amount
            if (total >= amountBaseUnits) return selected
        }
        throw IllegalArgumentException(
            "Insufficient verified reference KCC20 cells: available $total base units, required $amountBaseUnits"
        )
    }

}
