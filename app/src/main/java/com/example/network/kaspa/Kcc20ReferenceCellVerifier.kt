package com.example.network.kaspa

/** Verifies indexer cell claims against the wallet's own node UTXO set before planning a spend. */
object Kcc20ReferenceCellVerifier {
    data class CandidateCell(
        val transactionId: String,
        val outputIndex: Int,
        val valueSompi: Long,
        val covenantId: String,
        val address: String,
        val program: ByteArray
    )

    data class NodeUtxo(
        val transactionId: String,
        val outputIndex: Int,
        val valueSompi: Long,
        val covenantId: String?
    )

    class VerifiedCell internal constructor(
        val transactionId: String,
        val outputIndex: Int,
        val valueSompi: Long,
        val covenantId: String,
        val program: ByteArray,
        val address: String
    )

    fun verify(
        candidate: CandidateCell,
        expectedCovenantId: String,
        networkPrefix: String,
        nodeUtxosAtAddress: List<NodeUtxo>
    ): VerifiedCell {
        val family = expectedCovenantId.trim().lowercase()
        require(family.matches(Regex("[0-9a-f]{64}"))) { "Expected covenant ID must be 32-byte hex" }
        require(candidate.covenantId.equals(family, ignoreCase = true)) { "Indexer cell belongs to another covenant family" }
        require(candidate.transactionId.matches(Regex("[0-9a-fA-F]{64}")) && candidate.outputIndex >= 0) {
            "Invalid indexed cell outpoint"
        }
        require(candidate.valueSompi > 0L) { "Indexed cell has invalid KAS value" }

        val state = Kcc20ReferenceProtocol.verifyProgram(candidate.program)
        require(state.amount.signum() >= 0)
        val expectedAddress = Kcc20ReferenceProtocol.p2shAddress(candidate.program, networkPrefix)
        require(candidate.address.equals(expectedAddress, ignoreCase = true)) {
            "Indexer cell address does not match the pinned program's P2SH"
        }

        val live = nodeUtxosAtAddress.singleOrNull {
            it.transactionId.equals(candidate.transactionId, ignoreCase = true) &&
                it.outputIndex == candidate.outputIndex
        } ?: throw IllegalArgumentException("Indexed cell outpoint is not present in the node UTXO set")
        require(live.valueSompi == candidate.valueSompi) { "Node UTXO KAS value differs from the indexer claim" }
        require(live.covenantId?.equals(family, ignoreCase = true) == true) {
            "Node UTXO does not carry the requested KCC20 covenant ID"
        }

        return VerifiedCell(
            transactionId = candidate.transactionId.lowercase(),
            outputIndex = candidate.outputIndex,
            valueSompi = live.valueSompi,
            covenantId = family,
            program = candidate.program.copyOf(),
            address = expectedAddress
        )
    }
}
