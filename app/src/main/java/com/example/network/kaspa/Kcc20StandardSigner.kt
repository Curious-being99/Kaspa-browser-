package com.example.network.kaspa

import com.example.network.CryptoUtils
import java.math.BigInteger

object Kcc20StandardSigner {
    data class SignedTransfer(
        val transaction: KaspaTransactionEngine.KaspaTransaction,
        val signedInputIndexes: List<Int>
    )

    fun signStandardTransfer(
        transaction: KaspaTransactionEngine.KaspaTransaction,
        utxoEntries: List<KaspaTransactionEngine.KaspaUtxoEntry>,
        pieces: List<KbKcc20StandardAssembler.SpendPiece>,
        plan: KbKcc20StandardAssembler.StandardPlan,
        pubkey32: ByteArray,
        privateKey: BigInteger
    ): SignedTransfer {
        require(pubkey32.size == 32) { "Wallet public key must be 32 bytes" }
        require(privateKey.signum() > 0) { "Wallet private key is invalid" }
        require(transaction.inputs.size == utxoEntries.size) {
            "UTXO entries must match transaction inputs"
        }
        require(pieces.isNotEmpty()) { "KCC-20 token inputs are required" }
        require(pieces.size <= KbKcc20Protocol.MAX_TOKEN_INPUTS) {
            "KCC-20 token input limit exceeded"
        }
        require(plan.covenantId == KbKcc20Protocol.TOKEN_COVENANT_ID) {
            "KCC-20 transfer plan covenant does not match the pinned token"
        }
        require(plan.seats.size == pieces.size && plan.consumed.size == pieces.size) {
            "KCC-20 transfer plan does not match the selected token inputs"
        }
        require(plan.inputSum == plan.outputSum) {
            "KCC-20 transfer plan violates token amount conservation"
        }
        require(plan.successors.size == plan.nextStates.size && plan.successors.isNotEmpty()) {
            "KCC-20 transfer plan has incomplete successor programs"
        }
        require(plan.inputSum == plan.consumed.fold(BigInteger.ZERO) { sum, state ->
            sum + state.amountBaseUnits
        } && plan.outputSum == plan.nextStates.fold(BigInteger.ZERO) { sum, state ->
            sum + state.amountBaseUnits
        }) { "KCC-20 transfer plan state totals are inconsistent" }
        require(plan.successors.indices.all { index ->
            plan.successors[index].contentEquals(
                KbKcc20StandardAssembler.successorProgram(
                    pieces.first().redeem,
                    plan.nextStates[index]
                )
            )
        }) { "KCC-20 successor programs do not match the planned next states" }
        for (successor in plan.successors) {
            val matchingOutputs = transaction.outputs.filter { output ->
                isMatchingP2sh(output.scriptPublicKey.script, successor)
            }
            require(matchingOutputs.size == 1) {
                "Transaction outputs do not contain each planned KCC-20 successor exactly once"
            }
            val binding = matchingOutputs.single().covenant
                ?: throw IllegalArgumentException("KCC-20 successor output is missing its KIP-20 covenant binding")
            require(binding.authorizingInput == 0) {
                "KCC-20 successor output must be authorized by the leader input"
            }
            require(binding.covenantId.equals(KbKcc20Protocol.TOKEN_COVENANT_ID, true)) {
                "KCC-20 successor output is bound to a different covenant"
            }
        }
        require(transaction.inputs.size >= pieces.size) {
            "Transaction is missing KCC-20 token inputs"
        }

        val derivedPublicKey = CryptoUtils.to32ByteArray(
            CryptoUtils.pointMultiply(privateKey).x
        )
        require(derivedPublicKey.contentEquals(pubkey32)) {
            "Private key does not match the approved wallet public key"
        }

        for ((index, piece) in pieces.withIndex()) {
            val seat = plan.seats[index]
            require(seat.inputIndex == index) {
                "KCC-20 token input order is inconsistent"
            }
            require(index in transaction.inputs.indices) {
                "KCC-20 input index is outside the transaction"
            }
            val input = transaction.inputs[index]
            require(
                input.signatureScript.isEmpty() ||
                    input.signatureScript.equals(KbKcc20ScriptCodec.bytesToHex(plan.seats[index].script), true)
            ) {
                "KCC-20 token input contains an unexpected pre-existing signature script"
            }
            require(input.previousOutpoint.transactionId.equals(piece.transactionId, true) &&
                input.previousOutpoint.index == piece.index.toLong()
            ) { "KCC-20 input outpoint does not match the selected live cell" }
            require(utxoEntries[index].amount == piece.valueSompi) {
                "KCC-20 UTXO value changed; refresh live cells"
            }
            val liveState = KbKcc20Protocol.decodeLiveCellProgram(piece.redeem)
            require(
                KbKcc20Protocol.encodeState(plan.consumed[index])
                    .contentEquals(KbKcc20Protocol.encodeState(liveState))
            ) { "KCC-20 plan input state no longer matches the selected live cell" }
            require(liveState.ownerScheme == 0x00 && liveState.owner.contentEquals(pubkey32)) {
                "KCC-20 input is not controlled by the approved Schnorr wallet key"
            }
            val expectedSeat = if (index == 0) {
                val witness = KbKcc20StandardAssembler.ownerWitnessPlaceholder(
                    liveState, pubkey32, leader = true
                )
                KbKcc20StandardAssembler.encodeLeaderSeat(
                    index, plan.nextStates, witness, piece.redeem
                )
            } else {
                val witness = KbKcc20StandardAssembler.ownerWitnessPlaceholder(
                    liveState, pubkey32, leader = false
                )
                KbKcc20StandardAssembler.encodeDelegatorSeat(index, witness, piece.redeem)
            }
            require(seat.sigOffset == expectedSeat.sigOffset &&
                seat.script.contentEquals(expectedSeat.script)
            ) { "KCC-20 authorization seat does not match the verified transfer plan" }
            require(isMatchingP2sh(utxoEntries[index].scriptPublicKey.script, piece.redeem)) {
                "KCC-20 redeem program does not match the input P2SH locking script"
            }
            require(seat.script.copyOfRange(
                seat.sigOffset,
                seat.sigOffset + KbKcc20StandardAssembler.SIG_LEN
            ).all { it == 0.toByte() }) {
                "KCC-20 signature seat is not an empty placeholder"
            }
        }

        val signedIndexes = mutableListOf<Int>()
        for ((index, seat) in plan.seats.withIndex()) {
            val input = transaction.inputs[index]
            input.signatureScript = KbKcc20ScriptCodec.bytesToHex(seat.script)
            val sighash = KaspaTransactionEngine.calcSchnorrSignatureHash(
                transaction,
                index,
                KaspaTransactionEngine.SIGHASH_ALL,
                utxoEntries[index]
            )
            val signature64 = CryptoUtils.signSchnorr(privateKey, sighash)
            require(CryptoUtils.verifySchnorrSignature(signature64, sighash, pubkey32)) {
                "Generated KCC-20 Schnorr signature failed local verification"
            }
            val signature65 = KbKcc20StandardAssembler.signaturePayload(signature64)
            val patched = KbKcc20StandardAssembler.patchSeat(seat, signature65)
            input.signatureScript = KbKcc20ScriptCodec.bytesToHex(patched)
            signedIndexes += index
        }

        transaction.mass = KaspaTransactionEngine.calculateMass(transaction)
        return SignedTransfer(transaction, signedIndexes)
    }

    private fun isMatchingP2sh(lockingScriptHex: String, redeemProgram: ByteArray): Boolean {
        val lockingScript = KbKcc20ScriptCodec.hexToBytes(lockingScriptHex)
        if (lockingScript.size != 35 ||
            lockingScript[0] != 0xaa.toByte() ||
            lockingScript[1] != 0x20.toByte() ||
            lockingScript[34] != 0x87.toByte()
        ) return false

        val redeemHash = CryptoUtils.blake2b256(redeemProgram)
        return redeemHash.contentEquals(lockingScript.copyOfRange(2, 34))
    }
}
