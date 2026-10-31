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
        require(transaction.inputs.size >= pieces.size) {
            "Transaction is missing KCC-20 token inputs"
        }

        val derivedPublicKey = CryptoUtils.to32ByteArray(
            CryptoUtils.pointMultiply(privateKey).x
        )
        require(derivedPublicKey.contentEquals(pubkey32)) {
            "Private key does not match the approved wallet public key"
        }

        val plan = KbKcc20StandardAssembler.planStandardTransfer(
            pieces = pieces,
            pubkey32 = pubkey32,
            recipientOwner = pieces.firstOrNull()?.let { pubkey32 } ?: pubkey32,
            amountBaseUnits = BigInteger.ONE
        )
        require(plan.seats.size == pieces.size) {
            "KCC-20 authorization seat count does not match token inputs"
        }

        val signedIndexes = mutableListOf<Int>()
        for ((pieceIndex, piece) in pieces.withIndex()) {
            val seat = plan.seats[pieceIndex]
            val inputIndex = seat.inputIndex
            require(inputIndex == pieceIndex) {
                "KCC-20 token input order is inconsistent"
            }
            require(inputIndex in transaction.inputs.indices) {
                "KCC-20 input index is outside the transaction"
            }
            val input = transaction.inputs[inputIndex]
            require(input.previousOutpoint.transactionId.equals(piece.transactionId, true) &&
                input.previousOutpoint.index == piece.index.toLong()
            ) { "KCC-20 input outpoint does not match the selected live cell" }
            require(utxoEntries[inputIndex].amount == piece.valueSompi) {
                "KCC-20 UTXO value changed; refresh live cells"
            }
            require(seat.script.copyOfRange(seat.sigOffset, seat.sigOffset + KbKcc20StandardAssembler.SIG_LEN)
                .all { it == 0.toByte() }
            ) { "KCC-20 signature seat is not an empty placeholder" }

            input.signatureScript = KbKcc20ScriptCodec.bytesToHex(seat.script)
            val sighash = KaspaTransactionEngine.calcSchnorrSignatureHash(
                transaction,
                inputIndex,
                KaspaTransactionEngine.SIGHASH_ALL,
                utxoEntries[inputIndex]
            )
            val signature64 = CryptoUtils.signSchnorr(privateKey, sighash)
            val signature65 = KbKcc20StandardAssembler.signaturePayload(signature64)
            val patched = KbKcc20StandardAssembler.patchSeat(seat, signature65)
            require(
                CryptoUtils.verifySchnorrSignature(
                    signature64,
                    sighash,
                    pubkey32
                )
            ) { "Generated KCC-20 Schnorr signature failed local verification" }
            input.signatureScript = KbKcc20ScriptCodec.bytesToHex(patched)
            signedIndexes += inputIndex
        }

        transaction.mass = KaspaTransactionEngine.calculateMass(transaction)
        return SignedTransfer(transaction, signedIndexes)
    }
}
