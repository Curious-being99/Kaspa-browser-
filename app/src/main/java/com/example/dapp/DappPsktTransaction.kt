package com.example.dapp

import com.example.network.kaspa.Kcc20ReferenceProtocol

import com.example.network.CryptoUtils
import com.example.network.kaspa.KaspaTransactionEngine
import com.example.network.kaspa.Kcc20NoteScanner
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger

internal object DappPsktTransaction {
    private const val MAX_TRANSACTION_JSON_CHARS = 256 * 1024
    private const val MAX_INPUTS = 256
    private const val MAX_OUTPUTS = 256

    data class SignInput(val index: Int, val sighashType: Int, val redeemScript: String = "")

    data class Prepared(
        val originalJson: JSONObject,
        val transaction: KaspaTransactionEngine.KaspaTransaction,
        val utxoEntries: List<KaspaTransactionEngine.KaspaUtxoEntry>,
        val signInputs: List<SignInput>,
        val inputSumSompi: Long,
        val outputSumSompi: Long,
        val feeSompi: Long,
        val redeemScript: String = "",
        val walletPublicKeyHex: String = "",
        val inputRedeemScripts: List<String> = emptyList(),
        val outputRedeemScripts: List<String> = emptyList()
    ) {
        val feeKas: String
            get() = KaspaTransactionEngine.formatKas(KaspaTransactionEngine.sompiToKas(feeSompi))
    }

    fun prepareForSigning(
        txJsonString: String,
        signInputsJson: JSONArray,
        walletPublicKeyHex: String,
        isPolicy: Boolean = false,
        redeemScript: String = ""
    ): Prepared {
        require(walletPublicKeyHex.matches(Regex("(?i)[0-9a-f]{64}"))) {
            "Invalid active wallet public key"
        }
        require(signInputsJson.length() in 1..MAX_INPUTS) { "Select at least one input to sign" }
        val requests = (0 until signInputsJson.length()).map { index ->
            val numOrObj = signInputsJson.opt(index)
            val (inputIndex, sighashType, redeemScript) = when (numOrObj) {
                is JSONObject -> {
                    val idx = numOrObj.optInt("index", numOrObj.optInt("inputIndex", -1))
                    val shType = if (numOrObj.has("sighashType")) numOrObj.optInt("sighashType", KaspaTransactionEngine.SIGHASH_ALL) else KaspaTransactionEngine.SIGHASH_ALL
                    val rScript = sequenceOf(
                        "redeemScript", "redeem_script", "redeem", "redeemScriptHex", "redeem_script_hex",
                        "witnessScript", "witness_script", "covenantRedeemScript", "covenant_redeem_script"
                    ).mapNotNull { key ->
                        numOrObj.optString(key, "").takeIf { it.isNotBlank() }
                    }.firstOrNull().orEmpty()
                    Triple(idx, shType, rScript)
                }
                is Number -> {
                    Triple(numOrObj.toInt(), KaspaTransactionEngine.SIGHASH_ALL, "")
                }
                is String -> {
                    Triple(numOrObj.toIntOrNull() ?: -1, KaspaTransactionEngine.SIGHASH_ALL, "")
                }
                else -> throw IllegalArgumentException("Invalid signInputs entry")
            }
            require(sighashType == KaspaTransactionEngine.SIGHASH_ALL) {
                "Only SIGHASH_ALL (1) is supported"
            }
            SignInput(inputIndex, sighashType, redeemScript)
        }
        require(requests.map { it.index }.distinct().size == requests.size) {
            "Duplicate input index requested"
        }
        val prepared = parseAndValidate(txJsonString, requests, redeemScript).copy(
            walletPublicKeyHex = walletPublicKeyHex.lowercase()
        )
        val expectedScript = "20" + walletPublicKeyHex.lowercase() + "ac"
        val resolvedRequests = mutableListOf<SignInput>()
        for (request in requests) {
            require(request.index in prepared.transaction.inputs.indices) {
                "Input index is out of range"
            }
            val input = prepared.transaction.inputs[request.index]
            val utxoScript = prepared.utxoEntries[request.index].scriptPublicKey.script
                .replace(" ", "").lowercase()
            val pubHex = walletPublicKeyHex.lowercase()

            val utxo = prepared.utxoEntries[request.index]
            require(utxo.amount > 0L) {
                "Input ${request.index} has an invalid or missing UTXO amount"
            }

            // Standard wallet funding inputs must be unsigned and match the active key exactly.
            if (utxo.scriptPublicKey.version == 0 && utxoScript == expectedScript) {
                require(input.signatureScript.isBlank()) {
                    "Refusing to overwrite an existing signature script"
                }
                resolvedRequests.add(request)
                continue
            }

            val p2shHash = Regex("aa20([0-9a-f]{64})87").matchEntire(utxoScript)?.groupValues?.get(1)
            if (utxo.scriptPublicKey.version == 0 && p2shHash != null) {
                var redeem = request.redeemScript.takeIf { it.isNotBlank() }
                    ?: prepared.redeemScript.takeIf { it.isNotBlank() }

                if (redeem.isNullOrBlank()) {
                    val prevOut = prepared.transaction.inputs.getOrNull(request.index)?.previousOutpoint
                    redeem = Kcc20NoteScanner.findRedeemScript(prevOut?.transactionId, prevOut?.index, p2shHash)
                }

                if (redeem.isNullOrBlank()) {
                    val expectedP2shHash = CryptoUtils.blake2b256(hexToBytes(expectedScript))
                        .joinToString("") { "%02x".format(it) }
                    if (expectedP2shHash.equals(p2shHash, ignoreCase = true)) {
                        redeem = expectedScript
                    }
                }

                val finalRedeem = redeem?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("P2SH input ${request.index} requires its redeemScript")
                require(finalRedeem.matches(Regex("(?i)([0-9a-f]{2})+"))) {
                    "P2SH redeemScript for input ${request.index} is malformed"
                }
                val redeemHex = finalRedeem.lowercase()
                val redeemBytes = hexToBytes(redeemHex)
                val actualHash = CryptoUtils.blake2b256(redeemBytes)
                    .joinToString("") { "%02x".format(it) }
                require(actualHash.equals(p2shHash, ignoreCase = true)) {
                    "P2SH redeemScript does not match input ${request.index} locking script"
                }
                val isStandardP2pk = redeemHex == expectedScript
                val covenantState = runCatching {
                    com.example.network.kaspa.Kcc20ReferenceProtocol.verifyProgram(redeemBytes)
                }.getOrNull()
                val isWalletOwnedReferenceNote = covenantState != null &&
                    covenantState.ownerScheme == 0x00 &&
                    covenantState.owner.contentEquals(hexToBytes(pubHex))

                require(isStandardP2pk || isWalletOwnedReferenceNote) {
                    "P2SH input ${request.index} is not an exact pinned KCC20 reference note owned by the active wallet, or a standard P2PK deposit"
                }
                require(input.signatureScript.isBlank()) {
                    "Refusing to overwrite an existing signature script"
                }
                resolvedRequests.add(request.copy(redeemScript = finalRedeem))
                continue
            }

            throw IllegalArgumentException(
                "Input ${request.index} is not an exact supported P2PK/P2SH-P2PK deposit for the active wallet key " +
                    "(scriptVersion=${utxo.scriptPublicKey.version}, amountSompi=${utxo.amount})"
            )
        }
        require(resolvedRequests.isNotEmpty()) {
            "No unsigned input owned by the active wallet was selected for signing"
        }
        return prepared.copy(signInputs = resolvedRequests)
    }

    fun inspectForSubmission(txJsonString: String): Prepared {
        val prepared = parseAndValidate(txJsonString, emptyList(), "")
        prepared.transaction.inputs.forEachIndexed { index, input ->
            require(input.signatureScript.length % 2 == 0 &&
                input.signatureScript.matches(Regex("[0-9a-fA-F]*"))) {
                "Input $index has a malformed signature script"
            }
            val lockingScript = prepared.utxoEntries[index].scriptPublicKey.script
                .replace(" ", "").lowercase()
            if (lockingScript.matches(Regex("20[0-9a-f]{64}ac"))) {
                require(input.signatureScript.isNotBlank()) {
                    "Input $index is a P2PK input without a signature"
                }
            }
        }
        return prepared
    }

    fun sign(prepared: Prepared, privateKey: BigInteger, isDappSigning: Boolean = false): String {
        val signedRoot = JSONObject(prepared.originalJson.toString())
        val txRoot = signedRoot.optJSONObject("transaction") ?: signedRoot.optJSONObject("tx") ?: signedRoot
        val inputArray = txRoot.optJSONArray("inputs")
            ?: throw IllegalArgumentException("Transaction inputs are missing")
        require(inputArray.length() == prepared.transaction.inputs.size) {
            "PSKT input count changed before signing"
        }
        for (request in prepared.signInputs) {
            val input = prepared.transaction.inputs[request.index]
            val utxoEntry = prepared.utxoEntries[request.index]
            
            val sighash = KaspaTransactionEngine.calcSchnorrSignatureHash(
                prepared.transaction,
                request.index,
                request.sighashType,
                utxoEntry
            )
            val signature = CryptoUtils.signSchnorr(privateKey, sighash)
            val walletPublicKey = hexToBytes(prepared.walletPublicKeyHex)
            require(walletPublicKey.size == 32 &&
                CryptoUtils.verifySchnorrSignature(signature, sighash, walletPublicKey)
            ) {
                "Input ${request.index} signature failed local Schnorr verification for the active wallet key"
            }
            val lockingScript = utxoEntry.scriptPublicKey.script.replace(" ", "").lowercase()
            val redeem = if (!isDappSigning && Regex("aa20[0-9a-f]{64}87").matches(lockingScript)) {
                val prevOut = input.previousOutpoint
                val p2shHash = Regex("aa20([0-9a-f]{64})87").matchEntire(lockingScript)?.groupValues?.get(1)
                val redeemScript = request.redeemScript.takeIf { it.isNotBlank() }
                    ?: prepared.redeemScript.takeIf { it.isNotBlank() }
                    ?: Kcc20NoteScanner.findRedeemScript(prevOut.transactionId, prevOut.index, p2shHash)
                    ?: throw IllegalArgumentException("P2SH redeemScript is required for input ${request.index}")
                pushDataScript(redeemScript.lowercase())
            } else {
                ""
            }
            // For external dApp PSKT signing (isDappSigning=true), the dApp coordinator requires a plain
            // SIGHASH_ALL Schnorr signature script: 0x41 (OP_DATA_65) + 64-byte Schnorr signature + 0x01 (SIGHASH_ALL).
            // Exactly 66 bytes (132 hex characters), matching Braid SDK / Kaspa dApp sigsOf regex /^41[0-9a-f]{128}01$/i.
            val signatureScript = "41" + signature + "01" + redeem
            input.signatureScript = signatureScript
            val originalInput = inputArray.optJSONObject(request.index)
                ?: throw IllegalArgumentException("Transaction input is malformed")
            // Safe-JSON carries the serialized unlocking script in this canonical field.
            // Do not add wallet-specific signature aliases to the transaction input.
            originalInput.put("signatureScript", signatureScript)
        }
        return signedRoot.toString()
    }

    fun signForDapp(prepared: Prepared, privateKey: BigInteger): String =
        sign(prepared, privateKey, isDappSigning = true)

    fun assembleReferenceCovenantWitnesses(signedJson: String, prepared: Prepared): String {
        val root = JSONObject(signedJson)
        val txRoot = root.optJSONObject("transaction") ?: root.optJSONObject("tx") ?: root
        val inputs = txRoot.optJSONArray("inputs")
            ?: throw IllegalArgumentException("Signed PSKT inputs are missing")
        require(inputs.length() == prepared.transaction.inputs.size) {
            "Signed PSKT input count changed before KCC20 witness assembly"
        }

        val families = linkedMapOf<String, MutableList<Int>>()
        prepared.utxoEntries.forEachIndexed { index, utxo ->
            val script = utxo.scriptPublicKey.script.replace(" ", "").lowercase()
            if (utxo.scriptPublicKey.version != 0 || !Regex("aa20[0-9a-f]{64}87").matches(script)) {
                return@forEachIndexed
            }
            val programHex = prepared.inputRedeemScripts.getOrNull(index).orEmpty()
            if (programHex.isBlank()) return@forEachIndexed
            val program = hexToBytes(programHex)
            if (runCatching { Kcc20ReferenceProtocol.verifyProgram(program) }.isFailure) {
                return@forEachIndexed
            }
            require(Kcc20ReferenceProtocol.bytesToHex(Kcc20ReferenceProtocol.p2shScript(program))
                .equals(script, ignoreCase = true)) {
                "KCC20 input $index redeem program does not match its P2SH locking script"
            }
            val family = utxo.covenantId?.trim()?.lowercase()
                ?: throw IllegalArgumentException("KCC20 input $index has no node-declared covenant ID")
            require(family.matches(Regex("[0-9a-f]{64}"))) {
                "KCC20 input $index has a malformed covenant ID"
            }
            families.getOrPut(family) { mutableListOf() }.add(index)
        }
        require(families.isNotEmpty()) { "No reference KCC20 covenant inputs were supplied" }

        val outputPrograms = prepared.outputRedeemScripts.mapIndexedNotNull { index, hex ->
            if (hex.isBlank()) return@mapIndexedNotNull null
            val program = runCatching { hexToBytes(hex) }.getOrNull() ?: return@mapIndexedNotNull null
            val state = runCatching { Kcc20ReferenceProtocol.verifyProgram(program) }.getOrNull()
                ?: return@mapIndexedNotNull null
            val output = prepared.transaction.outputs.getOrNull(index) ?: return@mapIndexedNotNull null
            require(output.scriptPublicKey.version == 0 &&
                Kcc20ReferenceProtocol.bytesToHex(Kcc20ReferenceProtocol.p2shScript(program))
                    .equals(output.scriptPublicKey.script.replace(" ", ""), ignoreCase = true)
            ) { "KCC20 continuation output $index has an invalid P2SH program" }
            val binding = output.covenant
                ?: throw IllegalArgumentException("KCC20 continuation output $index is missing its covenant binding")
            require(binding.covenantId.matches(Regex("[0-9a-fA-F]{64}")) &&
                binding.authorizingInput in prepared.transaction.inputs.indices
            ) { "KCC20 continuation output $index has an invalid covenant binding" }
            Triple(index, binding, program) to state
        }

        for ((family, familyInputs) in families) {
            require(familyInputs.size in 1..Kcc20ReferenceProtocol.MAX_TOKEN_INPUTS)
            val leaderIndex = familyInputs.first()
            val familyOutputs = outputPrograms.filter { (entry, _) ->
                entry.second.covenantId.equals(family, ignoreCase = true)
            }
            require(familyOutputs.isNotEmpty() && familyOutputs.size <= Kcc20ReferenceProtocol.MAX_TOKEN_OUTPUTS) {
                "KCC20 family $family has no valid continuation outputs"
            }
            familyOutputs.forEach { (entry, _) ->
                require(entry.second.authorizingInput == leaderIndex) {
                    "KCC20 continuation output ${entry.first} is not authorized by family leader $leaderIndex"
                }
            }
            val leaderState = Kcc20ReferenceProtocol.verifyProgram(hexToBytes(prepared.inputRedeemScripts[leaderIndex]))
            val inputsTotal = familyInputs.fold(java.math.BigInteger.ZERO) { total, index ->
                val state = Kcc20ReferenceProtocol.verifyProgram(hexToBytes(prepared.inputRedeemScripts[index]))
                require(state.borrowScheme == 0 && state.borrowGuard.all { it == 0.toByte() } &&
                    state.extensionCommitment.contentEquals(leaderState.extensionCommitment)) {
                    "KCC20 input $index uses an unsupported or incompatible state policy"
                }
                total + state.amount
            }
            val nextStates = familyOutputs.map { (_, state) ->
                require(state.borrowScheme == 0 && state.borrowGuard.all { it == 0.toByte() } &&
                    state.extensionCommitment.contentEquals(leaderState.extensionCommitment)) {
                    "KCC20 continuation uses an unsupported or incompatible state policy"
                }
                state
            }
            val outputsTotal = nextStates.fold(java.math.BigInteger.ZERO) { total, state -> total + state.amount }
            require(inputsTotal == outputsTotal) { "KCC20 family $family token amounts do not balance" }
            val successorPrograms = familyOutputs.map { it.first.third }
            familyInputs.forEachIndexed { position, inputIndex ->
                val state = Kcc20ReferenceProtocol.verifyProgram(hexToBytes(prepared.inputRedeemScripts[inputIndex]))
                val inputJson = inputs.getJSONObject(inputIndex)
                val signatureScript = inputJson.optString("signatureScript",
                    inputJson.optString("signature_script", "")).lowercase()
                val signature = when {
                    position == 0 && state.ownerScheme == 0x04 -> {
                        require(signatureScript.isBlank()) { "Covenant-owned KCC20 leader must not carry a wallet signature" }
                        null
                    }
                    else -> {
                        require(state.ownerScheme == 0x00 && signatureScript.matches(Regex("41[0-9a-f]{128}01"))) {
                            "KCC20 input $inputIndex requires its owner's SIGHASH_ALL signature"
                        }
                        hexToBytes(signatureScript.substring(2))
                    }
                }
                val witness = com.example.network.kaspa.Kcc20RustyKaspaEngine.assembleReferenceWitness(
                    hexToBytes(prepared.inputRedeemScripts[inputIndex]),
                    position == 0,
                    signature,
                    if (position == 0) successorPrograms else emptyList()
                )
                inputJson.put("signatureScript", Kcc20ReferenceProtocol.bytesToHex(witness))
                inputJson.remove("signature_script")
            }
        }
        return root.toString()
    }

    fun kcc20TokenDetails(prepared: Prepared): String {
        data class Totals(var inputs: BigInteger = BigInteger.ZERO, var outputs: BigInteger = BigInteger.ZERO)
        val totals = sortedMapOf<String, Totals>()
        prepared.utxoEntries.forEachIndexed { index, utxo ->
            val covenantId = utxo.covenantId?.trim()?.lowercase()
                ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) } ?: return@forEachIndexed
            val programHex = prepared.inputRedeemScripts.getOrNull(index).orEmpty()
            if (programHex.isBlank()) return@forEachIndexed
            val state = runCatching {
                Kcc20ReferenceProtocol.verifyProgram(Kcc20ReferenceProtocol.decodeHex(programHex))
            }.getOrNull() ?: return@forEachIndexed
            val amounts = totals.getOrPut(covenantId) { Totals() }
            amounts.inputs = amounts.inputs.add(state.amount)
        }
        prepared.transaction.outputs.forEachIndexed { index, output ->
            val binding = output.covenant ?: return@forEachIndexed
            val covenantId = binding.covenantId.trim().lowercase()
                .takeIf { it.matches(Regex("[0-9a-f]{64}")) } ?: return@forEachIndexed
            val programHex = prepared.outputRedeemScripts.getOrNull(index).orEmpty()
            if (programHex.isBlank()) return@forEachIndexed
            val state = runCatching {
                Kcc20ReferenceProtocol.verifyProgram(Kcc20ReferenceProtocol.decodeHex(programHex))
            }.getOrNull() ?: return@forEachIndexed
            val amounts = totals.getOrPut(covenantId) { Totals() }
            amounts.outputs = amounts.outputs.add(state.amount)
        }
        return totals.entries.joinToString("\\n\\n") { (covenantId, amounts) ->
            val metadata = Kcc20NoteScanner.KNOWN_TOKENS[covenantId]
            val trustedMetadata = metadata?.let {
                "Name: ${it.name}\\nTicker: ${it.ticker}\\nDecimals: ${it.decimals}\\nMetadata source: pinned token list\\n"
            }.orEmpty()
            trustedMetadata +
                "Covenant ID: $covenantId\\nInput base units: ${amounts.inputs}\\nOutput base units: ${amounts.outputs}"
        }
    }
    fun applyMeasuredComputeBudgets(signedJson: String, budgets: List<Int>, storageMass: Long? = null): String {
        val root = JSONObject(signedJson)
        val txRoot = root.optJSONObject("transaction") ?: root.optJSONObject("tx") ?: root
        val inputs = txRoot.optJSONArray("inputs")
            ?: throw IllegalArgumentException("Signed PSKT inputs are missing")
        require(inputs.length() == budgets.size && budgets.all { it in 1..0xffff }) {
            "Measured compute budgets do not match the signed PSKT inputs"
        }
        for (index in budgets.indices) {
            val input = inputs.optJSONObject(index)
                ?: throw IllegalArgumentException("Signed PSKT input is malformed")
            input.put("sigOpCount", 0)
            input.put("sig_op_count", 0)
            input.put("computeBudget", budgets[index])
            input.put("compute_budget", budgets[index])
        }
        if (storageMass != null) {
            require(storageMass >= 0L) { "Rusty-Kaspa returned a negative contextual storage mass" }
            txRoot.put("mass", storageMass)
        }
        return root.toString()
    }

    private fun parseAndValidate(txJsonString: String, signInputs: List<SignInput>, redeemScript: String): Prepared {
        require(txJsonString.isNotBlank() && txJsonString.length <= MAX_TRANSACTION_JSON_CHARS) {
            "Transaction JSON is empty or too large"
        }
        val root = try {
            JSONObject(txJsonString)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid transaction JSON")
        }
        val txRoot = root.optJSONObject("transaction") ?: root.optJSONObject("tx") ?: root
        val inputArray = txRoot.optJSONArray("inputs")
            ?: throw IllegalArgumentException("Transaction inputs are missing")
        val outputArray = txRoot.optJSONArray("outputs")
            ?: throw IllegalArgumentException("Transaction outputs are missing")
        require(inputArray.length() in 1..MAX_INPUTS) { "Invalid input count" }
        require(outputArray.length() in 1..MAX_OUTPUTS) { "Invalid output count" }

        val utxoEntries = ArrayList<KaspaTransactionEngine.KaspaUtxoEntry>(inputArray.length())
        val inputs = ArrayList<KaspaTransactionEngine.KaspaTransactionInput>(inputArray.length())
        val seenOutpoints = HashSet<String>()
        val inputRedeemScripts = ArrayList<String>(inputArray.length())
        var inputSum = 0L
        for (index in 0 until inputArray.length()) {
            val item = inputArray.optJSONObject(index)
                ?: throw IllegalArgumentException("Transaction input $index is malformed")

            val outpointRaw = item.opt("previousOutpoint")
                ?: item.opt("previous_outpoint")
                ?: item.opt("outpoint")
                ?: item.opt("prevOutpoint")
                ?: item.opt("prev_outpoint")

            var parsedTxId = ""
            var parsedOutIndex = -1L

            if (outpointRaw is String) {
                val str = outpointRaw.trim()
                if (str.contains(":") || str.contains("/")) {
                    val delimiter = if (str.contains(":")) ":" else "/"
                    parsedTxId = str.substringBefore(delimiter).trim()
                    parsedOutIndex = str.substringAfter(delimiter).trim().toLongOrNull() ?: -1L
                } else if (str.matches(Regex("(?i)[0-9a-f]{64}"))) {
                    parsedTxId = str
                }
            }

            val outpointObj = when (outpointRaw) {
                is JSONObject -> outpointRaw
                else -> item
            }

            if (parsedTxId.isBlank()) {
                parsedTxId = outpointObj.optString("transactionId", 
                    outpointObj.optString("transaction_id", 
                    outpointObj.optString("txId", 
                    outpointObj.optString("tx_id", 
                    outpointObj.optString("txid", 
                    outpointObj.optString("hash",
                    outpointObj.optString("tx_hash", 
                    outpointObj.optString("id",
                    item.optString("transactionId",
                    item.optString("transaction_id",
                    item.optString("txId",
                    item.optString("tx_id",
                    item.optString("txid",
                    item.optString("hash", ""))))))))))))))
            }

            if (parsedOutIndex < 0L) {
                parsedOutIndex = outpointObj.optLong("index", 
                    outpointObj.optLong("outputIndex", 
                    outpointObj.optLong("output_index",
                    outpointObj.optLong("outpointIndex",
                    outpointObj.optLong("outpoint_index",
                    outpointObj.optLong("vout", 
                    outpointObj.optLong("n",
                    item.optLong("index",
                    item.optLong("outputIndex",
                    item.optLong("output_index",
                    item.optLong("vout", -1L)))))))))))
            }

            val txId = parsedTxId
            val outIndex = parsedOutIndex
            require(txId.matches(Regex("(?i)[0-9a-f]{64}"))) { "Input $index is missing its previous outpoint or has an invalid transaction id" }
            require(outIndex >= 0L && outIndex <= 0xffffffffL) { "Input $index has an invalid outpoint index" }
            require(seenOutpoints.add("${txId.lowercase()}:$outIndex")) {
                "Transaction contains duplicate outpoints"
            }
            val utxoJson = item.optJSONObject("utxoEntry")
                ?: item.optJSONObject("utxo_entry")
                ?: item.optJSONObject("utxo")
                ?: item.optJSONObject("entry")
                ?: txRoot.optJSONArray("utxoEntries")?.optJSONObject(index)
                ?: root.optJSONArray("utxoEntries")?.optJSONObject(index)
                ?: txRoot.optJSONArray("utxos")?.optJSONObject(index)
                ?: root.optJSONArray("utxos")?.optJSONObject(index)
                ?: item // Fallback: check if UTXO fields are at the top level
            val utxo = KaspaTransactionEngine.KaspaUtxoEntry.fromJson(utxoJson)
            val inputRedeemScript = sequenceOf(
                "redeemScript", "redeem_script", "redeem", "redeemScriptHex", "redeem_script_hex",
                "witnessScript", "witness_script", "covenantRedeemScript", "covenant_redeem_script"
            ).mapNotNull { key ->
                item.optString(key, "").takeIf { it.isNotBlank() }
                    ?: utxoJson.optString(key, "").takeIf { it.isNotBlank() }
                    ?: item.optJSONObject("covenant")?.optString(key, "")?.takeIf { it.isNotBlank() }
                    ?: utxoJson.optJSONObject("covenant")?.optString(key, "")?.takeIf { it.isNotBlank() }
            }.firstOrNull()
                ?: txRoot.optJSONArray("redeemScripts")?.optString(index, "")?.takeIf { it.isNotBlank() }
                ?: root.optJSONArray("redeemScripts")?.optString(index, "")?.takeIf { it.isNotBlank() }
                ?: txRoot.optJSONObject("redeemScripts")?.optString(index.toString(), "")?.takeIf { it.isNotBlank() }
                ?: root.optJSONObject("redeemScripts")?.optString(index.toString(), "")?.takeIf { it.isNotBlank() }
                ?: sequenceOf("redeemScript", "redeem_script", "redeem", "redeemScriptHex", "redeem_script_hex")
                    .mapNotNull { key ->
                        txRoot.optString(key, "").takeIf { it.isNotBlank() }
                            ?: root.optString(key, "").takeIf { it.isNotBlank() }
                    }.firstOrNull().orEmpty().ifBlank {
                        val p2shMatch = Regex("aa20([0-9a-f]{64})87").matchEntire(utxo.scriptPublicKey.script.replace(" ", "").lowercase())?.groupValues?.get(1)
                        Kcc20NoteScanner.findRedeemScript(txId, outIndex, p2shMatch) ?: ""
                    }
            inputRedeemScripts += inputRedeemScript
            require(utxo.amount > 0L) { "Input $index has an invalid UTXO amount" }
            require(utxo.scriptPublicKey.script.matches(Regex("(?i)([0-9a-f]{2})+"))) {
                "Input $index has an invalid UTXO script"
            }
            inputSum = Math.addExact(inputSum, utxo.amount)
            utxoEntries += utxo
            inputs += KaspaTransactionEngine.KaspaTransactionInput(
                previousOutpoint = KaspaTransactionEngine.KaspaOutpoint(txId.lowercase(), outIndex),
                signatureScript = item.optString("signatureScript", item.optString("signature_script", "")),
                sequence = item.optLong("sequence", item.optLong("sequence_number", 0L)),
                sigOpCount = item.optInt("sigOpCount", item.optInt("sig_op_count", 1)),
                computeBudget = item.optInt("computeBudget", item.optInt("compute_budget", 0))
            )
        }

        val outputs = ArrayList<KaspaTransactionEngine.KaspaTransactionOutput>(outputArray.length())
        val outputRedeemScripts = ArrayList<String>(outputArray.length())
        var outputSum = 0L
        for (index in 0 until outputArray.length()) {
            val item = outputArray.optJSONObject(index)
                ?: throw IllegalArgumentException("Transaction output $index is malformed")
            val amount = sequenceOf("value", "amount")
                .mapNotNull { key -> if (item.has(key)) item.optString(key).trim().toLongOrNull() else null }
                .firstOrNull() ?: -1L
            require(amount >= 0L) { "Output $index has an invalid amount" }
            val script = parseOutputScriptPublicKey(item, index)
            require(script.script.matches(Regex("(?i)([0-9a-f]{2})+"))) {
                "Output $index has an invalid script"
            }
            val outputRedeem = sequenceOf("redeemScript", "redeem_script", "redeemScriptHex", "redeem_script_hex")
                .mapNotNull { key -> item.optString(key, "").takeIf { it.isNotBlank() } }
                .firstOrNull().orEmpty().lowercase()
            if (outputRedeem.isNotBlank()) {
                require(outputRedeem.matches(Regex("(?i)([0-9a-f]{2})+"))) {
                    "Output $index redeem script is malformed"
                }
                val p2shHash = Regex("aa20([0-9a-f]{64})87").matchEntire(script.script.replace(" ", "").lowercase())?.groupValues?.get(1)
                    ?: throw IllegalArgumentException("Output $index supplies a redeem script for a non-P2SH output")
                val actualHash = CryptoUtils.blake2b256(hexToBytes(outputRedeem)).joinToString("") { "%02x".format(it) }
                require(actualHash.equals(p2shHash, ignoreCase = true)) {
                    "Output $index redeem script does not match its P2SH locking script"
                }
            }
            outputRedeemScripts += outputRedeem
            outputSum = Math.addExact(outputSum, amount)
            val covenantJson = item.optJSONObject("covenant")
            val covenant = covenantJson?.let { binding ->
                val authorizingKey = sequenceOf("authorizingInput", "authorizing_input")
                    .firstOrNull { key -> binding.has(key) && !binding.isNull(key) }
                    ?: throw IllegalArgumentException("Output $index covenant binding is missing authorizingInput")
                val authorizingInput = binding.optInt(authorizingKey, -1)
                require(authorizingInput in inputs.indices) {
                    "Output $index covenant authorizingInput is outside the transaction input range"
                }
                val covenantId = binding.optString("covenantId", binding.optString("covenant_id", ""))
                    .trim().lowercase()
                require(covenantId.matches(Regex("[0-9a-f]{64}"))) {
                    "Output $index covenant ID must be exactly 32-byte hex"
                }
                KaspaTransactionEngine.KaspaCovenantBinding(
                    authorizingInput = authorizingInput,
                    covenantId = covenantId
                )
            }
            outputs += KaspaTransactionEngine.KaspaTransactionOutput(amount, script, covenant)
        }
        require(inputSum >= outputSum) { "Transaction outputs exceed the declared input value" }
        val fee = inputSum - outputSum
        require(fee >= 0L) {
            "Transaction outputs ($outputSum sompi) exceed inputs ($inputSum sompi)"
        }

        val tx = KaspaTransactionEngine.KaspaTransaction(
            version = txRoot.optInt("version", 0),
            inputs = inputs,
            outputs = outputs,
            lockTime = txRoot.optLong("lockTime", txRoot.optLong("lock_time", 0L)),
            subnetworkId = txRoot.optString("subnetworkId", txRoot.optString("subnetwork_id", KaspaTransactionEngine.DEFAULT_SUBNETWORK_ID)),
            gas = txRoot.optLong("gas", 0L),
            payload = txRoot.optString("payload", ""),
            mass = txRoot.optLong("mass", 0L)
        )
        val hasCovenantTransaction = tx.version == 1 && (
            utxoEntries.any { !it.covenantId.isNullOrBlank() } ||
                outputs.any { it.covenant != null }
            )
        if (!hasCovenantTransaction) {
            val calculatedMass = KaspaTransactionEngine.calculateMass(tx, utxoEntries.map { it.amount })
            require(calculatedMass <= KaspaTransactionEngine.MAXIMUM_STANDARD_TRANSACTION_MASS) {
                "Transaction mass exceeds the standard mempool limit"
            }
        }
        val resolvedSignInputs = signInputs.map { request ->
            if (request.redeemScript.isNotBlank()) request
            else {
                val fromList = inputRedeemScripts.getOrNull(request.index).orEmpty()
                val prevOut = inputs.getOrNull(request.index)?.previousOutpoint
                val utxo = utxoEntries.getOrNull(request.index)
                val p2shMatch = utxo?.let { Regex("aa20([0-9a-f]{64})87").matchEntire(it.scriptPublicKey.script.replace(" ", "").lowercase())?.groupValues?.get(1) }
                val fromCache = Kcc20NoteScanner.findRedeemScript(prevOut?.transactionId, prevOut?.index, p2shMatch).orEmpty()
                request.copy(
                    redeemScript = fromList.ifBlank { fromCache }
                )
            }
        }
        return Prepared(
            root, tx, utxoEntries, resolvedSignInputs, inputSum, outputSum, fee, redeemScript,
            inputRedeemScripts = inputRedeemScripts.toList(),
            outputRedeemScripts = outputRedeemScripts.toList()
        )
    }

    private fun parseOutputScriptPublicKey(
        output: JSONObject,
        index: Int
    ): KaspaTransactionEngine.KaspaScriptPublicKey {
        val nested = output.optJSONObject("scriptPublicKey")
            ?: output.optJSONObject("script_public_key")
        if (nested != null) {
            return KaspaTransactionEngine.KaspaScriptPublicKey.fromJson(nested)
        }

        // Some PSKT producers flatten the script public key to its hex script.
        // Accept only an explicit script field; never derive one from an address or input.
        val flatScript = sequenceOf(
            "scriptPublicKey",
            "script_public_key",
            "script",
            "scriptPublicKeyHex",
            "script_public_key_hex"
        ).mapNotNull { key ->
            if (!output.has(key) || output.isNull(key)) null
            else output.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
        }.firstOrNull()

        if (flatScript != null) {
            val explicitVersion = sequenceOf(
                "scriptPublicKeyVersion",
                "script_public_key_version",
                "version"
            ).firstOrNull { output.has(it) && !output.isNull(it) }
                ?.let { output.optInt(it, 0) }
            return KaspaTransactionEngine.parseFlatScriptPublicKey(flatScript, explicitVersion)
        }

        throw IllegalArgumentException(
            "Output $index is missing its script public key (expected scriptPublicKey or script_public_key object, or an explicit script hex field)"
        )
    }

    private fun hexToBytes(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun pushDataScript(dataHex: String): String {
        val byteLength = dataHex.length / 2
        val prefix = when {
            byteLength <= 75 -> "%02x".format(byteLength)
            byteLength <= 255 -> "4c" + "%02x".format(byteLength)
            byteLength <= 65535 -> "4d" + "%04x".format(Integer.reverseBytes(byteLength).ushr(16))
            else -> throw IllegalArgumentException("P2SH redeemScript is too large")
        }
        return prefix + dataHex
    }

    fun reviewText(prepared: Prepared, origin: String, network: String, address: String): String {
        val lines = ArrayList<String>()
        lines += "Website: $origin"
        lines += "Network: $network"
        lines += "Account: $address"
        lines += "Inputs to sign: ${prepared.signInputs.size}"
        lines += "Total amount: ${KaspaTransactionEngine.formatKas(KaspaTransactionEngine.sompiToKas(prepared.outputSumSompi))} KAS"
        lines += "Estimated fee: ${prepared.feeKas} KAS"
        return lines.joinToString("\n")
    }
}
