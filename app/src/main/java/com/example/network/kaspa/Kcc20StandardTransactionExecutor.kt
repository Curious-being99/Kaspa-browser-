package com.example.network.kaspa

import com.example.network.CryptoUtils
import com.example.network.KaspaWalletService
import java.math.BigInteger

class Kcc20StandardTransactionExecutor(
    private val walletService: KaspaWalletService,
    private val kcc20Service: Kcc20KasivoService
) {
    companion object {
        const val TOKEN_CELL_DEPOSIT_SOMPI = 20_000_000L
        const val MAX_FUNDING_INPUTS = 20
        private const val CHANGE_ITERATIONS = 12
        private const val SIGNATURE_PLACEHOLDER = "41" + "00".repeat(64) + "01"
    }

    data class PreparedTransfer internal constructor(
        val senderAddress: String,
        val publicKeyHex: String,
        val transaction: KaspaTransactionEngine.KaspaTransaction,
        val feeSompi: Long,
        val mass: Long,
        val changeSompi: Long,
        val tokenInputCount: Int,
        val fundingInputCount: Int,
        internal val tokenPieces: List<KbKcc20StandardAssembler.SpendPiece>,
        internal val fundingUtxos: List<KaspaTransactionEngine.KaspaUtxo>,
        internal val utxoEntries: List<KaspaTransactionEngine.KaspaUtxoEntry>,
        internal val plan: KbKcc20StandardAssembler.StandardPlan
    )

    data class SignedTransfer internal constructor(
        val transactionId: String,
        val transaction: KaspaTransactionEngine.KaspaTransaction,
        val feeSompi: Long,
        val mass: Long,
        val changeSompi: Long,
        internal val prepared: PreparedTransfer
    )

    data class BroadcastReceipt(
        val transactionId: String,
        val submittedToNode: Boolean
    )

    suspend fun prepare(
        senderAddress: String,
        publicKeyHex: String,
        pieces: List<KbKcc20StandardAssembler.SpendPiece>,
        plan: KbKcc20StandardAssembler.StandardPlan
    ): PreparedTransfer {
        val normalizedAddress = senderAddress.trim().lowercase()
        require(normalizedAddress.startsWith("kaspa:")) {
            "KB KCC-20 standard transfers are Mainnet-only"
        }
        require(CryptoUtils.isValidKaspaAddress(normalizedAddress, "kaspa")) {
            "Sender must be a valid Kaspa Mainnet address"
        }
        val normalizedPublicKeyHex = publicKeyHex.trim().removePrefix("0x").lowercase()
        val publicKey = KbKcc20ScriptCodec.hexToBytes(normalizedPublicKeyHex)
        require(publicKey.size == 32) { "Wallet public key must be 32-byte x-only hex" }
        val senderScript = KaspaTransactionEngine.decodeAddressToScriptPublicKey(normalizedAddress)
        require(senderScript.script.equals("20" + normalizedPublicKeyHex + "ac", true)) {
            "Sender address does not belong to the supplied Schnorr public key"
        }
        require(pieces.isNotEmpty() && pieces.size <= KbKcc20Protocol.MAX_TOKEN_INPUTS) {
            "Invalid number of KCC-20 token cells"
        }
        require(plan.covenantId == KbKcc20Protocol.TOKEN_COVENANT_ID) {
            "Transfer plan is not for the pinned KB covenant"
        }
        require(plan.seats.size == pieces.size && plan.consumed.size == pieces.size) {
            "Transfer plan does not match the selected token cells"
        }
        require(plan.successors.size == plan.nextStates.size && plan.successors.isNotEmpty()) {
            "Transfer plan has incomplete successor programs"
        }
        require(plan.inputSum == plan.outputSum) {
            "KCC-20 transfer plan does not conserve token units"
        }

        val normalizedPieces = pieces.map { piece ->
            require(piece.transactionId.matches(Regex("[0-9a-fA-F]{64}"))) {
                "KCC-20 cell has an invalid transaction ID"
            }
            require(piece.index >= 0 && piece.valueSompi > 0L && piece.redeem.isNotEmpty()) {
                "KCC-20 cell has invalid outpoint, value or redeem program"
            }
            piece.copy(redeem = piece.redeem.copyOf())
        }
        require(normalizedPieces.map { it.transactionId.lowercase() to it.index }.distinct().size == normalizedPieces.size) {
            "Duplicate KCC-20 token cell outpoint"
        }

        val tokenInputs = normalizedPieces.mapIndexed { index, piece ->
            KaspaTransactionEngine.KaspaTransactionInput(
                previousOutpoint = KaspaTransactionEngine.KaspaOutpoint(
                    piece.transactionId.lowercase(),
                    piece.index.toLong()
                ),
                signatureScript = KbKcc20ScriptCodec.bytesToHex(plan.seats[index].script),
                sequence = 0L,
                sigOpCount = 1
            )
        }
        val tokenEntries = normalizedPieces.map { piece ->
            val redeemHash = CryptoUtils.blake2b256(piece.redeem)
            KaspaTransactionEngine.KaspaUtxoEntry(
                amount = piece.valueSompi,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(
                    version = 0,
                    script = "aa20" + KbKcc20ScriptCodec.bytesToHex(redeemHash) + "87"
                ),
                blockDaaScore = 0L,
                isCoinbase = false
            )
        }
        val successorOutputs = plan.successors.map { successor ->
            val hash = CryptoUtils.blake2b256(successor)
            KaspaTransactionEngine.KaspaTransactionOutput(
                amount = TOKEN_CELL_DEPOSIT_SOMPI,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(
                    version = 0,
                    script = "aa20" + KbKcc20ScriptCodec.bytesToHex(hash) + "87"
                )
            )
        }

        val tokenOutpoints = normalizedPieces.map {
            it.transactionId.lowercase() to it.index.toLong()
        }.toSet()
        val fundingCandidates = walletService.fetchLiveUtxos(normalizedAddress)
            .asSequence()
            .filter { utxo ->
                !utxo.utxoEntry.isCoinbase &&
                    utxo.utxoEntry.amount > 0L &&
                    utxo.outpoint.transactionId.matches(Regex("[0-9a-fA-F]{64}")) &&
                    utxo.outpoint.index >= 0L &&
                    utxo.utxoEntry.scriptPublicKey.version == senderScript.version &&
                    utxo.utxoEntry.scriptPublicKey.script.equals(senderScript.script, true) &&
                    (utxo.outpoint.transactionId.lowercase() to utxo.outpoint.index) !in tokenOutpoints
            }
            .distinctBy { it.outpoint.transactionId.lowercase() to it.outpoint.index }
            .sortedByDescending { it.utxoEntry.amount }
            .take(MAX_FUNDING_INPUTS)
            .toList()

        val emptyFundingCandidate = buildCandidate(
            tokenInputs, tokenEntries, emptyList(), emptyList(), successorOutputs, senderScript
        )
        if (emptyFundingCandidate != null) {
            return PreparedTransfer(
                normalizedAddress, normalizedPublicKeyHex,
                emptyFundingCandidate.transaction, emptyFundingCandidate.feeSompi,
                emptyFundingCandidate.mass, emptyFundingCandidate.changeSompi,
                normalizedPieces.size, 0, normalizedPieces, emptyList(), tokenEntries, plan
            )
        }

        val selectedFunding = mutableListOf<KaspaTransactionEngine.KaspaUtxo>()
        for (candidate in fundingCandidates) {
            selectedFunding += candidate
            val fundingInputs = selectedFunding.map { utxo ->
                KaspaTransactionEngine.KaspaTransactionInput(
                    previousOutpoint = utxo.outpoint,
                    signatureScript = SIGNATURE_PLACEHOLDER,
                    sequence = 0L,
                    sigOpCount = 1
                )
            }
            val fundingEntries = selectedFunding.map { it.utxoEntry }
            val built = buildCandidate(
                tokenInputs, tokenEntries, fundingInputs, fundingEntries, successorOutputs, senderScript
            ) ?: continue
            return PreparedTransfer(
                normalizedAddress, normalizedPublicKeyHex,
                built.transaction, built.feeSompi, built.mass, built.changeSompi,
                normalizedPieces.size, selectedFunding.size, normalizedPieces,
                selectedFunding.toList(), tokenEntries + fundingEntries, plan
            )
        }
        throw IllegalStateException(
            "Insufficient confirmed native KAS UTXOs to fund KB covenant successor deposits and the calculated network fee"
        )
    }

    fun sign(
        prepared: PreparedTransfer,
        pubkey32: ByteArray,
        privateKey: BigInteger
    ): SignedTransfer {
        require(pubkey32.size == 32 && pubkey32.contentEquals(
            KbKcc20ScriptCodec.hexToBytes(prepared.publicKeyHex)
        )) { "Signing key does not match the prepared wallet" }

        val signed = Kcc20StandardSigner.signStandardTransfer(
            transaction = prepared.transaction,
            utxoEntries = prepared.utxoEntries,
            pieces = prepared.tokenPieces,
            plan = prepared.plan,
            pubkey32 = pubkey32,
            privateKey = privateKey
        ).transaction

        for (index in prepared.tokenInputCount until signed.inputs.size) {
            val sighash = KaspaTransactionEngine.calcSchnorrSignatureHash(
                signed, index, KaspaTransactionEngine.SIGHASH_ALL, prepared.utxoEntries[index]
            )
            val signatureHex = CryptoUtils.signSchnorr(privateKey, sighash)
            val signatureBytes = KbKcc20ScriptCodec.hexToBytes(signatureHex)
            require(CryptoUtils.verifySchnorrSignature(signatureBytes, sighash, pubkey32)) {
                "Funding-input Schnorr signature failed local verification"
            }
            signed.inputs[index].signatureScript = "41" + signatureHex + "01"
        }

        val inputAmounts = prepared.utxoEntries.map { it.amount }
        val exactMass = KaspaTransactionEngine.calculateMass(signed, inputAmounts)
        require(exactMass <= KaspaTransactionEngine.MAXIMUM_STANDARD_TRANSACTION_MASS) {
            "KCC-20 transaction exceeds the standard mempool mass limit"
        }
        val inputTotal = inputAmounts.fold(0L) { total, amount -> Math.addExact(total, amount) }
        val outputTotal = signed.outputs.fold(0L) { total, output -> Math.addExact(total, output.amount) }
        val actualFee = Math.subtractExact(inputTotal, outputTotal)
        require(actualFee >= requiredFee(signed, inputAmounts, exactMass)) {
            "Final signed transaction fee is below the current network minimum"
        }
        signed.mass = exactMass
        val txId = KaspaTransactionEngine.calcTransactionId(signed)
        return SignedTransfer(
            txId, signed, actualFee, exactMass, prepared.changeSompi, prepared
        )
    }

    suspend fun preflight(signed: SignedTransfer): Long {
        val nodeMass = walletService.verifyTransactionMass(signed.transaction.toJson(), isTestnet = false)
        require(nodeMass > 0L) {
            "Kaspa node mass endpoint did not validate the signed transaction; refusing broadcast"
        }
        require(nodeMass == signed.mass) {
            "Local transaction mass (" + signed.mass + ") differs from Kaspa node mass (" +
                nodeMass + "); refusing broadcast"
        }
        return nodeMass
    }

    suspend fun broadcast(signed: SignedTransfer, senderAddress: String): BroadcastReceipt {
        require(senderAddress.trim().equals(signed.prepared.senderAddress, true)) {
            "Active sender address changed; KCC-20 broadcast cancelled"
        }
        preflight(signed)

        val refreshedCells = kcc20Service.fetchLiveTokenCells(
            contractId = KbKcc20Protocol.TOKEN_COVENANT_ID,
            publicKeyHex = signed.prepared.publicKeyHex,
            walletAddress = signed.prepared.senderAddress
        ).getOrElse {
            throw IllegalStateException("Unable to revalidate live KCC-20 token cells before broadcast", it)
        }
        val liveCellsByOutpoint = refreshedCells.associateBy {
            it.transactionId.lowercase() to it.index
        }
        for (piece in signed.prepared.tokenPieces) {
            val current = liveCellsByOutpoint[piece.transactionId.lowercase() to piece.index]
                ?: throw IllegalStateException("A selected KCC-20 token cell is no longer unspent")
            require(current.valueSompi == piece.valueSompi && current.redeem.contentEquals(piece.redeem)) {
                "A selected KCC-20 token cell changed; refresh and rebuild the transfer"
            }
        }

        val refreshedFunding = walletService.fetchLiveUtxos(signed.prepared.senderAddress)
            .associateBy { it.outpoint.transactionId.lowercase() to it.outpoint.index }
        for (selected in signed.prepared.fundingUtxos) {
            val current = refreshedFunding[selected.outpoint.transactionId.lowercase() to selected.outpoint.index]
                ?: throw IllegalStateException("A selected KAS funding UTXO is no longer unspent")
            require(
                current.utxoEntry.amount == selected.utxoEntry.amount &&
                    current.utxoEntry.scriptPublicKey.script.equals(
                        selected.utxoEntry.scriptPublicKey.script, true
                    )
            ) { "A selected KAS funding UTXO changed; refresh and rebuild the transfer" }
        }

        val receipt = walletService.submitDappTransaction(
            signed.transaction, signed.prepared.senderAddress
        )
        require(receipt.accepted) {
            receipt.error.ifBlank { "Kaspa node rejected the KCC-20 transaction" }
        }
        require(receipt.transactionId.equals(signed.transactionId, true)) {
            "Kaspa node returned a transaction ID that differs from the locally computed ID"
        }
        return BroadcastReceipt(receipt.transactionId, submittedToNode = true)
    }

    private data class Candidate(
        val transaction: KaspaTransactionEngine.KaspaTransaction,
        val feeSompi: Long,
        val mass: Long,
        val changeSompi: Long
    )

    private fun buildCandidate(
        tokenInputs: List<KaspaTransactionEngine.KaspaTransactionInput>,
        tokenEntries: List<KaspaTransactionEngine.KaspaUtxoEntry>,
        fundingInputs: List<KaspaTransactionEngine.KaspaTransactionInput>,
        fundingEntries: List<KaspaTransactionEngine.KaspaUtxoEntry>,
        successorOutputs: List<KaspaTransactionEngine.KaspaTransactionOutput>,
        senderScript: KaspaTransactionEngine.KaspaScriptPublicKey
    ): Candidate? {
        val inputAmounts = (tokenEntries + fundingEntries).map { it.amount }
        val totalInput = inputAmounts.fold(0L) { total, amount -> Math.addExact(total, amount) }
        val successorTotal = successorOutputs.fold(0L) { total, output -> Math.addExact(total, output.amount) }
        val available = totalInput - successorTotal
        if (available <= 0L) return null

        fun transaction(change: Long): KaspaTransactionEngine.KaspaTransaction {
            val outputs = successorOutputs.toMutableList()
            if (change > 0L) {
                outputs += KaspaTransactionEngine.KaspaTransactionOutput(change, senderScript)
            }
            return KaspaTransactionEngine.KaspaTransaction(
                version = 0,
                inputs = tokenInputs + fundingInputs,
                outputs = outputs,
                lockTime = 0L,
                subnetworkId = KaspaTransactionEngine.DEFAULT_SUBNETWORK_ID,
                gas = 0L,
                payload = "",
                mass = 0L
            )
        }

        var change = if (
            available > KaspaTransactionEngine.DUST_THRESHOLD_SOMPIS +
                KaspaTransactionEngine.MINIMUM_WALLET_TRANSACTION_FEE_SOMPIS
        ) {
            available - KaspaTransactionEngine.MINIMUM_WALLET_TRANSACTION_FEE_SOMPIS
        } else 0L

        repeat(CHANGE_ITERATIONS) {
            val withChange = transaction(change)
            val massWithChange = KaspaTransactionEngine.calculateMass(withChange, inputAmounts)
            val feeWithChange = requiredFee(withChange, inputAmounts, massWithChange)
            if (available >= feeWithChange + KaspaTransactionEngine.DUST_THRESHOLD_SOMPIS) {
                val nextChange = available - feeWithChange
                if (nextChange == change) {
                    withChange.mass = massWithChange
                    return Candidate(withChange, feeWithChange, massWithChange, nextChange)
                }
                change = nextChange
            } else {
                val withoutChange = transaction(0L)
                val massWithoutChange = KaspaTransactionEngine.calculateMass(withoutChange, inputAmounts)
                val feeWithoutChange = requiredFee(withoutChange, inputAmounts, massWithoutChange)
                if (available < feeWithoutChange) return null
                withoutChange.mass = massWithoutChange
                return Candidate(withoutChange, available, massWithoutChange, 0L)
            }
        }
        return null
    }

    private fun requiredFee(
        transaction: KaspaTransactionEngine.KaspaTransaction,
        inputAmounts: List<Long>,
        mass: Long
    ): Long {
        val exactMass = if (mass > 0L) mass else KaspaTransactionEngine.calculateMass(transaction, inputAmounts)
        return maxOf(
            KaspaTransactionEngine.MINIMUM_WALLET_TRANSACTION_FEE_SOMPIS,
            KaspaTransactionEngine.calculateToccataMinimumStandardFee(transaction),
            KaspaTransactionEngine.calculateFeeForMass(
                exactMass, KaspaTransactionEngine.DEFAULT_SOMPI_PER_MASS
            )
        )
    }
}
