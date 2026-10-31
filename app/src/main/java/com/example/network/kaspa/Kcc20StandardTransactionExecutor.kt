package com.example.network.kaspa

import com.example.network.CryptoUtils
import com.example.network.KaspaWalletService
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class Kcc20StandardTransactionExecutor(
    private val walletService: KaspaWalletService,
    private val kcc20Service: Kcc20KasivoService
) {
    companion object {
        const val TOKEN_CELL_DEPOSIT_SOMPI = 20_000_000L
        const val MAX_FUNDING_INPUTS = 20
        private const val CHANGE_ITERATIONS = 12
        private val SIGNATURE_PLACEHOLDER = "41" + "00".repeat(64) + "01"
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
            val cellAddress = CryptoUtils.encodeP2shAddress(redeemHash, "kaspa")
            val liveEntry = walletService.fetchLiveUtxos(cellAddress).firstOrNull { utxo ->
                utxo.outpoint.transactionId.equals(piece.transactionId, true) &&
                    utxo.outpoint.index == piece.index.toLong()
            } ?: throw IllegalStateException(
                "Kaspa node did not return the selected live KCC-20 cell UTXO; refusing to invent its consensus metadata"
            )
            require(liveEntry.utxoEntry.amount == piece.valueSompi) {
                "KasCov cell amount differs from the live Kaspa UTXO amount"
            }
            val expectedScript = "aa20" + KbKcc20ScriptCodec.bytesToHex(redeemHash) + "87"
            require(liveEntry.utxoEntry.scriptPublicKey.script.equals(expectedScript, true)) {
                "Live Kaspa UTXO locking script does not match the KCC-20 redeem program"
            }
            require(liveEntry.utxoEntry.covenantId?.equals(
                KbKcc20Protocol.TOKEN_COVENANT_ID, true
            ) == true) {
                "Live token-cell UTXO is missing the exact KB covenant ID; consensus preflight is unavailable"
            }
            liveEntry.utxoEntry
        }
        val successorOutputs = plan.successors.map { successor ->
            val hash = CryptoUtils.blake2b256(successor)
            KaspaTransactionEngine.KaspaTransactionOutput(
                amount = TOKEN_CELL_DEPOSIT_SOMPI,
                scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(
                    version = 0,
                    script = "aa20" + KbKcc20ScriptCodec.bytesToHex(hash) + "87"
                ),
                covenant = KaspaTransactionEngine.KaspaCovenantBinding(
                    authorizingInput = 0,
                    covenantId = KbKcc20Protocol.TOKEN_COVENANT_ID
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
            require(CryptoUtils.verifySchnorrSignature(signatureHex, sighash, pubkey32)) {
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

    suspend fun verifyConsensusScriptEngine(signed: SignedTransfer): JSONObject = withContext(Dispatchers.IO) {
        val request = signed.transaction.toJson()
        val inputs = request.getJSONArray("inputs")
        require(inputs.length() == signed.prepared.utxoEntries.size) {
            "Consensus preflight requires exact populated UTXO metadata for every input"
        }
        for (index in 0 until inputs.length()) {
            val entry = signed.prepared.utxoEntries[index]
            inputs.getJSONObject(index).put("utxo", entry.toJson().apply {
                put("amount", entry.amount)
                entry.covenantId?.let { put("covenantId", it) }
            })
        }

        val outputs = request.getJSONArray("outputs")
        for (index in 0 until outputs.length()) {
            val output = outputs.getJSONObject(index)
            output.put("value", output.optLong("amount"))
            output.remove("amount")
        }

        val connection = (URL("https://kascov.io/data/mainnet/preflight")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 25_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Cache-Control", "no-store")
        }

        try {
            connection.outputStream.use { stream ->
                stream.write(request.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            require(status in 200..299) {
                "Kascov consensus preflight unavailable (HTTP $status): " +
                    body.take(500).ifBlank { "empty response" }
            }
            val result = JSONObject(body)
            require(result.optBoolean("ok", false)) {
                "Kascov consensus preflight did not complete: " +
                    result.optString("error", result.optString("verdict", "unknown result"))
            }
            require(result.optString("verdict") == "ready") {
                val findings = result.optJSONArray("findings") ?: JSONArray()
                val details = (0 until findings.length()).joinToString("; ") { i ->
                    val finding = findings.optJSONObject(i) ?: JSONObject()
                    finding.optString("code") + ": " + finding.optString("message")
                }
                "Kaspa consensus script-engine preflight rejected or could not validate this transaction: " +
                    details.ifBlank { result.optString("verdict", "not ready") }
            }
            val executed = result.optJSONArray("executed")
                ?: throw IllegalStateException("Preflight did not return per-input script-engine results")
            require(executed.length() == signed.transaction.inputs.size) {
                "Preflight executed " + executed.length() + " inputs; expected " +
                    signed.transaction.inputs.size
            }
            for (index in 0 until executed.length()) {
                val row = executed.getJSONObject(index)
                require(row.optInt("input_index", -1) == index) {
                    "Preflight returned an unexpected input execution order"
                }
                require(row.optBoolean("pass", false)) {
                    "Kaspa consensus script engine rejected input " + row.optInt("input_index", index)
                }
            }
            result
        } finally {
            connection.disconnect()
        }
    }

    suspend fun verifyNodeMass(signed: SignedTransfer): Long {
        val tx = signed.transaction
        require(tx.inputs.size == signed.prepared.utxoEntries.size) {
            "Transaction input and live UTXO metadata counts differ"
        }
        require(tx.inputs.map {
            it.previousOutpoint.transactionId.lowercase() to it.previousOutpoint.index
        }.distinct().size == tx.inputs.size) {
            "Transaction contains duplicate input outpoints"
        }
        require(tx.inputs.all { it.signatureScript.isNotBlank() &&
            it.signatureScript.length % 2 == 0 &&
            it.signatureScript.matches(Regex("[0-9a-fA-F]+"))
        }) { "Transaction contains an empty or malformed input signature script" }
        val inputTotal = signed.prepared.utxoEntries.fold(0L) { total, entry ->
            Math.addExact(total, entry.amount)
        }
        val outputTotal = tx.outputs.fold(0L) { total, output ->
            require(output.amount > 0L) { "Transaction contains a non-positive output" }
            Math.addExact(total, output.amount)
        }
        require(Math.subtractExact(inputTotal, outputTotal) == signed.feeSompi) {
            "Transaction input/output conservation does not match the prepared fee"
        }
        require(tx.mass == signed.mass &&
            signed.mass <= KaspaTransactionEngine.MAXIMUM_STANDARD_TRANSACTION_MASS
        ) { "Signed transaction mass is inconsistent or exceeds the standard limit" }

        val nodeMass = walletService.verifyTransactionMass(tx.toJson(), isTestnet = false)
        require(nodeMass > 0L) {
            "Kaspa node mass endpoint did not return a valid mass estimate; refusing broadcast"
        }
        require(nodeMass == signed.mass) {
            "Local transaction mass (" + signed.mass + ") differs from Kaspa node mass (" +
                nodeMass + "); refusing broadcast"
        }
        verifyConsensusScriptEngine(signed)
        return nodeMass
    }

    suspend fun broadcast(signed: SignedTransfer, senderAddress: String): BroadcastReceipt {
        require(senderAddress.trim().equals(signed.prepared.senderAddress, true)) {
            "Active sender address changed; KCC-20 broadcast cancelled"
        }
        val deployment = kcc20Service.fetchDeploymentConformance(
            KbKcc20Protocol.TOKEN_COVENANT_ID
        ).getOrElse {
            throw IllegalStateException(
                "Unable to verify the live KB deployment conformance; broadcast remains disabled",
                it
            )
        }
        require(deployment.safeForStandardTransfer) {
            "Live KB deployment is not cleared for standard KCC-20 transfers: " +
                "token=${deployment.tokenStatus}, layout=${deployment.layout}, " +
                "review=${deployment.review}, conformant=${deployment.conformant}, " +
                "artifact=${deployment.artifact ?: "published"}, dispatch=${deployment.dispatch}, " +
                "template=${deployment.templateHash}, view=${deployment.viewTemplateHash}, " +
                "nonconformities=${deployment.nonconformities.joinToString()}"
        }
        verifyNodeMass(signed)

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
        for ((index, piece) in signed.prepared.tokenPieces.withIndex()) {
            val current = liveCellsByOutpoint[piece.transactionId.lowercase() to piece.index]
                ?: throw IllegalStateException("A selected KCC-20 token cell is no longer unspent")
            require(current.valueSompi == piece.valueSompi && current.redeem.contentEquals(piece.redeem)) {
                "A selected KCC-20 token cell changed; refresh and rebuild the transfer"
            }
            val redeemHash = CryptoUtils.blake2b256(piece.redeem)
            val p2shAddress = CryptoUtils.encodeP2shAddress(redeemHash, "kaspa")
            val currentUtxo = walletService.fetchLiveUtxos(p2shAddress).firstOrNull {
                it.outpoint.transactionId.equals(piece.transactionId, true) &&
                    it.outpoint.index == piece.index.toLong()
            } ?: throw IllegalStateException("Kaspa node no longer reports a selected token-cell UTXO")
            val preparedEntry = signed.prepared.utxoEntries[index]
            require(
                currentUtxo.utxoEntry.amount == preparedEntry.amount &&
                    currentUtxo.utxoEntry.scriptPublicKey.version == preparedEntry.scriptPublicKey.version &&
                    currentUtxo.utxoEntry.scriptPublicKey.script.equals(preparedEntry.scriptPublicKey.script, true) &&
                    currentUtxo.utxoEntry.blockDaaScore == preparedEntry.blockDaaScore &&
                    currentUtxo.utxoEntry.isCoinbase == preparedEntry.isCoinbase &&
                    currentUtxo.utxoEntry.covenantId.equals(preparedEntry.covenantId, true)
            ) { "A selected KCC-20 token UTXO changed; rebuild the transaction" }
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

    suspend fun awaitDagAcceptance(
        signed: SignedTransfer,
        timeoutMs: Long = 60_000L
    ): KaspaWalletService.ConfirmationResult {
        require(timeoutMs in 1_000L..300_000L) {
            "DAG acceptance timeout must be between 1 and 300 seconds"
        }
        return walletService.waitForAcceptance(
            txId = signed.transactionId,
            networkName = "Kaspa Mainnet",
            timeoutMs = timeoutMs
        )
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
