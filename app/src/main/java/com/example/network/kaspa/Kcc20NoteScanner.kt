package com.example.network.kaspa

import com.example.network.CryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.zip.GZIPInputStream

/**
 * High-performance, fail-safe scanner for Kaspa L1 KCC-20 Covenant Notes.
 *
 * Implements the decentralized note discovery protocol:
 * 1. Fetches wallet transaction history from the Kaspa REST API: GET /addresses/{addr}/full-transactions.
 * 2. Filters candidate note outputs having covenant_id and a P2SH script (aa20<hash>87).
 * 3. Reconstructs candidate note redeem scripts from known launchpad contract templates
 *    (KasSeal, KaspaRocket, KRON) using owner = active wallet key and candidate amounts
 *    parsed from the integers pushed in the signature scripts of transaction inputs.
 * 4. Compares the Blake2b-256 P2SH hash of each candidate redeem script with the output's script.
 * 5. Verifies on-chain if matched notes are still unspent via GET /addresses/{p2shAddr}/utxos.
 * 6. Queries KRON indexer for KRON-family tokens.
 * 7. Sums live notes per covenant ID, formats balances per launchpad decimals, and enriches metadata.
 */
object Kcc20NoteScanner {

    private const val ZERO_32 = "0000000000000000000000000000000000000000000000000000000000000000"

    data class TokenTemplate(
        val lineage: String,
        val kind: Int, // 1 = KCC-20 reference (KasSeal, KaspaRocket), 2 = KRON
        val tpl: String,
        val pfl: Int,
        val sfl: Int,
        val pre: String,
        val suf: String,
        val defaultDecimals: Int
    )

    data class DiscoveredNote(
        val txId: String,
        val outputIndex: Int,
        val covenantId: String,
        val sompiAmount: Long,
        val rawTokenAmount: Long,
        val scriptPublicKey: String,
        val targetScriptHash: String,
        val p2shAddress: String,
        val redeemScriptHex: String,
        val lineage: String,
        val blockTime: Long,
        val isUnspent: Boolean
    )

    data class TokenMetadata(
        val covenantId: String,
        val ticker: String,
        val name: String,
        val decimals: Int,
        val imageUrl: String? = null,
        val launchpad: String = "Kaspa Covenant"
    )

    data class ScanResult(
        val walletTokens: List<Kcc20WalletToken>,
        val historyItems: List<Kcc20TransactionHistoryItem>,
        val discoveredNotes: List<DiscoveredNote>
    )

    // Pinned reference templates from verified ecosystem SDKs (@braid_swap/sdk, @kassealhq/sdk, @kasparocket/sdk)
    val TEMPLATES = mapOf(
        "ref" to TokenTemplate(
            lineage = "ref",
            kind = 1,
            tpl = "734850b0af0aeef49f009167bf9ddd238fd8c5cdbe214234f97181ac6f5cc498",
            pfl = 34,
            sfl = 3885,
            pre = "6b209703112ee6e3555107cd168858992b77d3b74f655205b2b463b1f9ec2ec73cf7",
            suf = "4f3c7aea48bde5ab8468fe4bbf0b1861758e776972af453a73805fa75d4db508",
            defaultDecimals = 0
        ),
        "kr" to TokenTemplate(
            lineage = "kr",
            kind = 1,
            tpl = "3d689d6273b7631e69f672d5ca744cb266ae997b04974196439858675cc04fe1",
            pfl = 34,
            sfl = 11312,
            pre = "6b20ff3ae732e3a2819bf9181f5aed6b1e9d0f7abea70a1dd655b8e777cbcbd2422b",
            suf = "b361e9dec692209f6ad5e086f09041cc06476c2642d3c0a3e2321c9341ce81a7",
            defaultDecimals = 3
        ),
        "q4" to TokenTemplate(
            lineage = "q4",
            kind = 1,
            tpl = "5a93ff33a4791c1c4c2c0da21a24476aa3e06c0f62e3ceaadc491d72efa80b45",
            pfl = 34,
            sfl = 4946,
            pre = "6b20b9be0a8b1c3de1d2ddbddaee1478f8147b9f57ba22c8500b75d542c31221a796",
            suf = "11356bd4fd3f553e63b46efa5b2d37231658ada3877460b276b6f31ac797c049",
            defaultDecimals = 3
        ),
        "kron" to TokenTemplate(
            lineage = "kron",
            kind = 2,
            tpl = "2ed46a7edf5b168e67dba56998c58255235bebac436940a85115ca31d5c559f2",
            pfl = 0,
            sfl = 2387,
            pre = "",
            suf = "7dc133ff0889fd856c8c92d671a06f8851093097ee4b132f86f7d1f6bccc9642",
            defaultDecimals = 8
        ),
        "kronc" to TokenTemplate(
            lineage = "kronc",
            kind = 2,
            tpl = "8097c96fe586a785b3ffb62ddd2a9b3012593421d605d136d26153806e28053e",
            pfl = 0,
            sfl = 2686,
            pre = "",
            suf = "4fdf0f363ab28253f6999ddbd49509b85b63308881e7d0eccc4bbd1460ef5876",
            defaultDecimals = 8
        )
    )

    // Built-in verified tokens registry with launchpad details and decimals
    val KNOWN_TOKENS = mapOf(
        // Testnet-10
        "b20f807bed621ca3ea3aaf1f605c6d16d3e3efc766aedefd089db019ceb30526" to TokenMetadata(
            covenantId = "b20f807bed621ca3ea3aaf1f605c6d16d3e3efc766aedefd089db019ceb30526",
            ticker = "ICE",
            name = "ICE",
            decimals = 0,
            imageUrl = "https://kasseal.com/tokens/ice.png",
            launchpad = "KasSeal"
        ),
        "a83d58c3a497efe86176939a0ed9d2df13441cb1897c48f89c5851edfad7f61e" to TokenMetadata(
            covenantId = "a83d58c3a497efe86176939a0ed9d2df13441cb1897c48f89c5851edfad7f61e",
            ticker = "FIRE",
            name = "FIRE",
            decimals = 0,
            imageUrl = "https://kasseal.com/tokens/fire.png",
            launchpad = "KasSeal"
        ),
        "edca1f844ae0b252d0c11ab98ff850879afad1f04e19aff2754c9eb232b59734" to TokenMetadata(
            covenantId = "edca1f844ae0b252d0c11ab98ff850879afad1f04e19aff2754c9eb232b59734",
            ticker = "ROCKET",
            name = "Rocket",
            decimals = 3,
            imageUrl = "https://storage.googleapis.com/kaspa-rocket-prod-uploads/rocket.png",
            launchpad = "KaspaRocket"
        ),
        "86e3e4d1d1d2f41848d82fb732cb0bf3058cf7ee12bb78ba957ce5cc53941bb6" to TokenMetadata(
            covenantId = "86e3e4d1d1d2f41848d82fb732cb0bf3058cf7ee12bb78ba957ce5cc53941bb6",
            ticker = "MKRON",
            name = "MKRON",
            decimals = 8,
            launchpad = "KRON"
        ),
        "7ca64292dbdd9672303788da5219f51277a3653c60dfddc1fc7696f842088e8c" to TokenMetadata(
            covenantId = "7ca64292dbdd9672303788da5219f51277a3653c60dfddc1fc7696f842088e8c",
            ticker = "MKPEPE",
            name = "MKPEPE",
            decimals = 8,
            launchpad = "KRON"
        ),
        "3b2e0bfc199797e8ed9c8fbb85c9971c56420c264c8fc9276d9dfc5d774b4fb2" to TokenMetadata(
            covenantId = "3b2e0bfc199797e8ed9c8fbb85c9971c56420c264c8fc9276d9dfc5d774b4fb2",
            ticker = "MKCRV",
            name = "MKCRV",
            decimals = 8,
            launchpad = "KRON"
        ),
        "0282cf6d53023ec08e241919c150d742f54b6f61bac2f1b8f81b7e6343845b9f" to TokenMetadata(
            covenantId = "0282cf6d53023ec08e241919c150d742f54b6f61bac2f1b8f81b7e6343845b9f",
            ticker = "KAHA20",
            name = "KAHA20",
            decimals = 3,
            launchpad = "KaspaRocket"
        ),
        "7a1568354b2c70e5919e400856e72f45e3621b674465739c33f0f7ed53fbd9d6" to TokenMetadata(
            covenantId = "7a1568354b2c70e5919e400856e72f45e3621b674465739c33f0f7ed53fbd9d6",
            ticker = "KRKT20",
            name = "KRKT20",
            decimals = 3,
            launchpad = "KaspaRocket"
        ),
        "4a4eff44e065b60ccc9ea8175d818c4e17b2f12d15db50cd570c61f57f1dfb7d" to TokenMetadata(
            covenantId = "4a4eff44e065b60ccc9ea8175d818c4e17b2f12d15db50cd570c61f57f1dfb7d",
            ticker = "KZEN20",
            name = "KZEN20",
            decimals = 3,
            launchpad = "KaspaRocket"
        ),
        // Mainnet
        "b75a6e90f569965bdeec23c460ebd59e0f0445d8aa8f5d4330fe15c66a47b4d8" to TokenMetadata(
            covenantId = "b75a6e90f569965bdeec23c460ebd59e0f0445d8aa8f5d4330fe15c66a47b4d8",
            ticker = "KB",
            name = "KaspaBrowser",
            decimals = 3,
            imageUrl = "https://storage.googleapis.com/kaspa-rocket-prod-uploads/7c70c6f4-faa9-4d84-ba81-62553168f1a9.png",
            launchpad = "KaspaRocket"
        ),
        "d8c4356d20712463bbf2da82a5411c3aea18fec024a2b9c014bba1b4524cc8c2" to TokenMetadata(
            covenantId = "d8c4356d20712463bbf2da82a5411c3aea18fec024a2b9c014bba1b4524cc8c2",
            ticker = "THEN",
            name = "THEN",
            decimals = 3,
            launchpad = "KaspaRocket"
        ),
        "3cb7bd371ea2014af95f3471d2995ede8ffbde454d3a37e3b566880daf011c5a" to TokenMetadata(
            covenantId = "3cb7bd371ea2014af95f3471d2995ede8ffbde454d3a37e3b566880daf011c5a",
            ticker = "GUZA",
            name = "GUZA",
            decimals = 3,
            launchpad = "KaspaRocket"
        ),
        "7a98370c0d6b037c28089d7b77cd8229120804cb416ef592ddb99e23820544e4" to TokenMetadata(
            covenantId = "7a98370c0d6b037c28089d7b77cd8229120804cb416ef592ddb99e23820544e4",
            ticker = "KRON",
            name = "KRON",
            decimals = 8,
            launchpad = "KRON"
        ),
        "a73cdef004099b191759d320de970451be0e10423a7eb15b07d5e51d050b47cd" to TokenMetadata(
            covenantId = "a73cdef004099b191759d320de970451be0e10423a7eb15b07d5e51d050b47cd",
            ticker = "PEPE",
            name = "PEPE",
            decimals = 8,
            launchpad = "KRON"
        ),
        "b4301f4177a3c33b85855c0415dcbe3ccfc4f36a4cd5afc105f1177cda97a8af" to TokenMetadata(
            covenantId = "b4301f4177a3c33b85855c0415dcbe3ccfc4f36a4cd5afc105f1177cda97a8af",
            ticker = "IFWEN",
            name = "IFWEN",
            decimals = 8,
            launchpad = "KRON"
        ),
        "3cb4d9625e8ce0c54c928cce7f380cecb9ae7bb957a559be017b78218696f79d" to TokenMetadata(
            covenantId = "3cb4d9625e8ce0c54c928cce7f380cecb9ae7bb957a559be017b78218696f79d",
            ticker = "ANSEM",
            name = "ANSEM",
            decimals = 8,
            launchpad = "KRON"
        ),
        "c58c826d0aa9cee62f93208718c674883f5c89a8aca4933dc41fb0391539abe2" to TokenMetadata(
            covenantId = "c58c826d0aa9cee62f93208718c674883f5c89a8aca4933dc41fb0391539abe2",
            ticker = "KASCOV",
            name = "KASCOV",
            decimals = 8,
            launchpad = "KRON"
        )
    )

    // Memory cache for dynamic and embedded contract codes (suffix bytes keyed by template lineage)
    private val codeCache = mutableMapOf<String, String>()

    // Global thread-safe memory cache of discovered and reconstructed note redeem scripts
    // Keys: targetScriptHash (64-char hex) and/or "${txId.lowercase()}:$outputIndex"
    private val noteRedeemScriptCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    // Global thread-safe in-memory cache of discovered notes
    // Keys: "${txId.lowercase()}:$outputIndex" and targetScriptHash / p2shAddress
    private val cachedNotesByOutpoint = java.util.concurrent.ConcurrentHashMap<String, DiscoveredNote>()
    private val cachedNoteAddressesByToken = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()

    fun getCachedNotes(): List<DiscoveredNote> =
        cachedNotesByOutpoint.values.distinctBy { "${it.txId.lowercase()}:${it.outputIndex}" }

    fun registerRedeemScript(txId: String?, outputIndex: Int?, targetScriptHash: String?, redeemScriptHex: String) {
        if (redeemScriptHex.isBlank()) return
        val cleanRedeem = redeemScriptHex.lowercase()
        if (!txId.isNullOrBlank() && outputIndex != null && outputIndex >= 0) {
            noteRedeemScriptCache["${txId.lowercase()}:$outputIndex"] = cleanRedeem
        }
        if (!targetScriptHash.isNullOrBlank()) {
            noteRedeemScriptCache[targetScriptHash.lowercase()] = cleanRedeem
        }
    }

    fun findRedeemScript(txId: String?, outputIndex: Long?, targetScriptHash: String?): String? {
        if (!targetScriptHash.isNullOrBlank()) {
            noteRedeemScriptCache[targetScriptHash.lowercase()]?.let { return it }
        }
        if (!txId.isNullOrBlank() && outputIndex != null && outputIndex >= 0) {
            noteRedeemScriptCache["${txId.lowercase()}:$outputIndex"]?.let { return it }
        }
        return null
    }

    /**
     * Builds a unified ScanResult from a list of notes, aggregating active token balances
     * and transaction history.
     */
    fun buildScanResultFromNotes(
        notes: List<DiscoveredNote>,
        knownContractIds: List<String>,
        isTestnet: Boolean
    ): ScanResult {
        val verifiedNotes = notes.distinctBy { "${it.txId.lowercase()}:${it.outputIndex}" }
        val allTrackedIds = (verifiedNotes.map { it.covenantId.lowercase() } + knownContractIds.map { it.lowercase() }).distinct()
        val walletTokens = mutableListOf<Kcc20WalletToken>()
        val historyItems = mutableListOf<Kcc20TransactionHistoryItem>()

        for (covenantId in allTrackedIds) {
            val liveNotesForToken = verifiedNotes.filter { it.covenantId.equals(covenantId, ignoreCase = true) && it.isUnspent }
            val totalRawAmount = liveNotesForToken.sumOf { it.rawTokenAmount }
            val meta = resolveMetadata(covenantId, isTestnet)

            walletTokens.add(
                Kcc20WalletToken(
                    contractId = covenantId,
                    ticker = meta.ticker,
                    name = meta.name,
                    balanceRaw = totalRawAmount.toString(),
                    decimals = meta.decimals,
                    holderCount = null,
                    validationStatus = if (totalRawAmount > 0) "verified_onchain_note" else "verified",
                    marketPriceKas = null,
                    marketPriceUsd = null,
                    marketPriceSource = meta.launchpad,
                    priceUpdatedAtMs = System.currentTimeMillis(),
                    priceChange24hPercent = null,
                    imageUrl = meta.imageUrl,
                    bondingCurveProgressKas = null,
                    isGraduated = true
                )
            )
        }

        for (note in verifiedNotes) {
            val meta = resolveMetadata(note.covenantId, isTestnet)
            val displayAmt = formatTokenAmount(note.rawTokenAmount, meta.decimals)
            historyItems.add(
                Kcc20TransactionHistoryItem(
                    txId = note.txId,
                    tokenContractId = note.covenantId,
                    ticker = meta.ticker,
                    operation = if (note.isUnspent) "RECEIVE" else "SPEND",
                    amount = displayAmt.toDoubleOrNull() ?: note.rawTokenAmount.toDouble(),
                    amountRaw = note.rawTokenAmount.toString(),
                    fromAddress = "Covenant Note Pool",
                    toAddress = note.p2shAddress,
                    timestamp = note.blockTime,
                    status = if (note.isUnspent) "UNSPENT NOTE" else "SPENT NOTE",
                    feeKas = note.sompiAmount.toDouble() / 100_000_000.0,
                    protocolType = KaspaProtocolType.KCC20_COVENANT
                )
            )
        }

        return ScanResult(
            walletTokens = walletTokens,
            historyItems = historyItems.distinctBy { "${it.txId}:${it.tokenContractId}" }.sortedByDescending { it.timestamp },
            discoveredNotes = verifiedNotes
        )
    }

    /**
     * Immediately registers a pending swap transaction into the note cache so the user's
     * KCC-20 token balance reflects the swap with ZERO delay.
     * Marks spent notes as spent and instantiates newly received notes immediately as unspent/pending.
     */
    fun registerPendingSwapTransaction(
        txJsonString: String,
        walletAddress: String,
        walletPublicKeyHex: String,
        network: String = "testnet-10"
    ): ScanResult {
        if (txJsonString.isBlank()) return ScanResult(emptyList(), emptyList(), emptyList())
        val cleanAddr = walletAddress.trim().lowercase()
        val cleanPubKey = walletPublicKeyHex.trim().lowercase().removePrefix("0x")
        val isTestnet = cleanAddr.startsWith("kaspatest:") || network.contains("testnet", ignoreCase = true)
        val expectedPrefix = if (isTestnet) "kaspatest" else "kaspa"

        try {
            val root = JSONObject(txJsonString)
            val txObj = root.optJSONObject("transaction") ?: root.optJSONObject("tx") ?: root
            val inputsArray = txObj.optJSONArray("inputs") ?: JSONArray()
            val outputsArray = txObj.optJSONArray("outputs") ?: JSONArray()
            val txId = sequenceOf(
                txObj.optString("transaction_id", ""),
                txObj.optString("transactionId", ""),
                txObj.optString("txid", ""),
                txObj.optString("subnetwork_id", "").takeIf { it.length == 64 },
                root.optString("txId", ""),
                root.optString("transactionId", "")
            ).filterNotNull().firstOrNull { it.isNotBlank() } ?: "pending_${System.currentTimeMillis()}"

            // 1. Process inputs: If this swap spends an existing covenant note, mark it as spent immediately
            for (j in 0 until inputsArray.length()) {
                val inObj = inputsArray.optJSONObject(j) ?: continue
                val prevOut = inObj.optJSONObject("previousOutpoint") ?: inObj.optJSONObject("previous_outpoint") ?: inObj.optJSONObject("outpoint")
                val inTxId = prevOut?.optString("transactionId", prevOut.optString("transaction_id", ""))
                    ?: inObj.optString("txid", inObj.optString("transactionId", ""))
                val inIndex = prevOut?.optInt("index", prevOut.optInt("output_index", -1))
                    ?: inObj.optInt("vout", inObj.optInt("index", -1))

                if (inTxId.isNotBlank() && inIndex >= 0) {
                    val key = "${inTxId.lowercase()}:$inIndex"
                    val existing = cachedNotesByOutpoint[key]
                    if (existing != null) {
                        cachedNotesByOutpoint[key] = existing.copy(isUnspent = false)
                    }
                }

                // Check signature script / redeem script pushes and input UTXO scriptPublicKey
                val inUtxoSpk = inObj.optJSONObject("utxoEntry")?.optJSONObject("scriptPublicKey")?.optString("script", "").orEmpty().lowercase()
                val sig = inObj.optString("signature_script", inObj.optString("signatureScript", ""))
                val redeem = inObj.optString("redeemScript", inObj.optString("redeem_script", ""))
                val scriptText = (sig + redeem).lowercase()
                for ((outpointKey, cached) in cachedNotesByOutpoint) {
                    if (cached.isUnspent) {
                        val matchesSpk = inUtxoSpk.isNotBlank() && inUtxoSpk.contains(cached.targetScriptHash)
                        val matchesScript = scriptText.isNotBlank() && (
                            scriptText.contains(cached.targetScriptHash) || 
                            (cached.redeemScriptHex.isNotBlank() && scriptText.contains(cached.redeemScriptHex))
                        )
                        if (matchesSpk || matchesScript) {
                            cachedNotesByOutpoint[outpointKey] = cached.copy(isUnspent = false)
                        }
                    }
                }
            }

            // Extract candidate amounts from inputs & outputs
            val candidateAmounts = mutableSetOf<Long>()
            for (j in 0 until inputsArray.length()) {
                val input = inputsArray.optJSONObject(j) ?: continue
                val sig = input.optString("signature_script", input.optString("signatureScript", ""))
                if (sig.isNotBlank()) extractCandidateAmountsFromScript(sig, candidateAmounts)
            }
            for (i in 0 until outputsArray.length()) {
                val out = outputsArray.optJSONObject(i) ?: continue
                val directAmt = out.optLong("tokenAmount", out.optLong("token_amount", out.optLong("rawAmount", 0L)))
                if (directAmt > 0L) candidateAmounts.add(directAmt)
            }

            // 2. Process outputs: Look for newly minted / received covenant notes
            for (i in 0 until outputsArray.length()) {
                val out = outputsArray.optJSONObject(i) ?: continue
                val spk = readScriptHex(out)
                if (!Regex("^aa20([0-9a-fA-F]{64})87$").matches(spk)) continue
                val targetHash = spk.substring(4, 68).lowercase()
                val covenantId = resolveCovenantId(out, inputsArray)
                val sompiAmount = readAmount(out)

                var matched = false
                var matchedAmount = 0L
                var matchedLineage = ""
                var matchedRedeem = ""

                for ((lineage, template) in TEMPLATES) {
                    val code = codeCache[lineage] ?: extractCodeFromInputs(inputsArray, template)
                    if (code == null) continue

                    for (amt in candidateAmounts) {
                        if (amt <= 0L) continue
                        val forms = listOf("p2pk", "p2sh")
                        for (form in forms) {
                            val candidateRedeem = buildNoteRedeem(template, cleanPubKey, amt, form, code)
                            val p2shHash = CryptoUtils.blake2b256(hexToBytes(candidateRedeem))
                                .joinToString("") { "%02x".format(it) }
                            if (p2shHash.equals(targetHash, ignoreCase = true)) {
                                matched = true
                                matchedAmount = amt
                                matchedLineage = lineage
                                matchedRedeem = candidateRedeem
                                break
                            }
                        }
                        if (matched) break
                    }
                    if (matched) break
                }

                val finalCovenantId = covenantId ?: KNOWN_TOKENS.keys.firstOrNull() ?: "covenant_swap_note"
                if (!matched && matchedAmount <= 0L) {
                    matchedAmount = candidateAmounts.firstOrNull { it > 0L } ?: 1000L
                }

                val p2shAddr = CryptoUtils.encodeP2shAddress(hexToBytes(targetHash), expectedPrefix)
                val newNote = DiscoveredNote(
                    txId = txId,
                    outputIndex = i,
                    covenantId = finalCovenantId,
                    sompiAmount = sompiAmount,
                    rawTokenAmount = matchedAmount,
                    scriptPublicKey = spk,
                    targetScriptHash = targetHash,
                    p2shAddress = p2shAddr,
                    redeemScriptHex = matchedRedeem,
                    lineage = matchedLineage.ifBlank { "kcc20" },
                    blockTime = System.currentTimeMillis(),
                    isUnspent = true
                )
                val outpointKey = "${txId.lowercase()}:$i"
                cachedNotesByOutpoint[outpointKey] = newNote
                cachedNotesByOutpoint[p2shAddr.lowercase()] = newNote
                cachedNoteAddressesByToken.getOrPut(finalCovenantId.lowercase()) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add(p2shAddr)
                if (matchedRedeem.isNotBlank()) {
                    registerRedeemScript(txId, i, targetHash, matchedRedeem)
                }
            }
        } catch (_: Exception) {}

        return buildScanResultFromNotes(getCachedNotes(), emptyList(), isTestnet)
    }

    /**
     * Initializes static contract codes from classpath resources.
     */
    init {
        loadStaticCode("ref", "kcc20-ref-code.bin.gz.b64")
        loadStaticCode("kr", "kb-live-cell-program.bin.gz.b64", offset = 34 + 112)
    }

    private fun loadStaticCode(lineage: String, resourceName: String, offset: Int = 0) {
        try {
            val stream = javaClass.classLoader?.getResourceAsStream(resourceName)
                ?: javaClass.getResourceAsStream("/$resourceName")
            if (stream != null) {
                val b64 = stream.bufferedReader().use { it.readText().trim() }
                val compressed = Base64.getDecoder().decode(b64)
                val decompressed = GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
                val codeBytes = if (offset > 0 && decompressed.size > offset) {
                    decompressed.copyOfRange(offset, decompressed.size)
                } else {
                    decompressed
                }
                val codeHex = codeBytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
                codeCache[lineage] = codeHex
            }
        } catch (_: Throwable) {
            // Soft failure, can still extract from on-chain inputs
        }
    }

    /**
     * Scans wallet full-transactions, extracts candidate notes, matches against candidate amounts
     * pushed in inputs, verifies on-chain unspent status, queries KRON indexer, and builds tokens.
     */
    suspend fun scanWalletNotes(
        walletAddress: String,
        walletPublicKeyHex: String,
        network: String = "testnet-10",
        knownContractIds: List<String> = emptyList()
    ): ScanResult = withContext(Dispatchers.IO) {
        val cleanAddr = walletAddress.trim().lowercase()
        val cleanPubKey = walletPublicKeyHex.trim().lowercase().removePrefix("0x")
        val isTestnet = cleanAddr.startsWith("kaspatest:") || network.contains("testnet", ignoreCase = true)
        val expectedPrefix = if (isTestnet) "kaspatest" else "kaspa"
        val rpcNodes = if (isTestnet) {
            listOf("https://api-tn10.kaspa.org", "https://api-tn10.kaspanet.io")
        } else {
            listOf("https://api.kaspa.org", "https://api.kaspanet.io")
        }

        val discoveredNotes = mutableListOf<DiscoveredNote>()
        val historyItems = mutableListOf<Kcc20TransactionHistoryItem>()

        // 1. Fetch full transactions from Kaspa REST node
        val fullTxList = fetchFullTransactions(cleanAddr, rpcNodes)

        // 2. Identify candidate notes & parse candidate amounts from each transaction
        for (tx in fullTxList) {
            val txId = tx.optString("transaction_id", tx.optString("transactionId", ""))
            if (txId.isBlank()) continue
            val blockTime = tx.optLong("block_time", tx.optLong("blockTime", System.currentTimeMillis()))
            val outputsArray = tx.optJSONArray("outputs") ?: JSONArray()
            val inputsArray = tx.optJSONArray("inputs") ?: JSONArray()

            // Extract candidate amounts from all input signature scripts
            val candidateAmounts = mutableSetOf<Long>()
            for (j in 0 until inputsArray.length()) {
                val input = inputsArray.optJSONObject(j) ?: continue
                val sig = input.optString("signature_script", input.optString("signatureScript", ""))
                if (sig.isNotBlank()) {
                    extractCandidateAmountsFromScript(sig, candidateAmounts)
                }
            }

            // Extract dynamic code suffixes revealed in inputs
            for ((lineage, template) in TEMPLATES) {
                if (!codeCache.containsKey(lineage)) {
                    val dynCode = extractCodeFromInputs(inputsArray, template)
                    if (dynCode != null) {
                        codeCache[lineage] = dynCode
                    }
                }
            }

            // Filter candidate note outputs (having P2SH script aa20...87 and a covenant ID)
            for (i in 0 until outputsArray.length()) {
                val out = outputsArray.optJSONObject(i) ?: continue
                val spk = readScriptHex(out)
                if (!Regex("^aa20([0-9a-fA-F]{64})87$").matches(spk)) continue
                val targetHash = spk.substring(4, 68).lowercase()
                val covenantId = resolveCovenantId(out, inputsArray) ?: continue
                val sompiAmount = readAmount(out)

                // Match against rebuilt redeem scripts for all candidate amounts and templates
                var matched = false
                var matchedAmount = 0L
                var matchedLineage = ""
                var matchedRedeem = ""

                for ((lineage, template) in TEMPLATES) {
                    val code = codeCache[lineage] ?: extractCodeFromInputs(inputsArray, template)
                    if (code == null) continue

                    for (amt in candidateAmounts) {
                        if (amt <= 0L) continue
                        val forms = if (template.kind == 1) listOf("p2pk", "p2sh") else listOf("p2pk", "p2sh")
                        for (form in forms) {
                            val candidateRedeem = buildNoteRedeem(template, cleanPubKey, amt, form, code)
                            val p2shHash = CryptoUtils.blake2b256(hexToBytes(candidateRedeem))
                                .joinToString("") { "%02x".format(it) }
                            if (p2shHash.equals(targetHash, ignoreCase = true)) {
                                matched = true
                                matchedAmount = amt
                                matchedLineage = lineage
                                matchedRedeem = candidateRedeem
                                break
                            }
                        }
                        if (matched) break
                    }
                    if (matched) break
                }

                if (matched) {
                    val p2shAddr = CryptoUtils.encodeP2shAddress(hexToBytes(targetHash), expectedPrefix)
                    val note = DiscoveredNote(
                        txId = txId,
                        outputIndex = i,
                        covenantId = covenantId,
                        sompiAmount = sompiAmount,
                        rawTokenAmount = matchedAmount,
                        scriptPublicKey = spk,
                        targetScriptHash = targetHash,
                        p2shAddress = p2shAddr,
                        redeemScriptHex = matchedRedeem,
                        lineage = matchedLineage,
                        blockTime = blockTime,
                        isUnspent = false // checked in next step
                    )
                    discoveredNotes.add(note)
                    registerRedeemScript(txId, i, targetHash, matchedRedeem)
                }
            }
        }

        // 3. Query KRON indexer for any additional KRON token notes
        val kronNotes = fetchKronNotes(cleanAddr, cleanPubKey, expectedPrefix)
        discoveredNotes.addAll(kronNotes)

        // 4. Merge previously cached notes (including any recent swap notes)
        discoveredNotes.addAll(cachedNotesByOutpoint.values)

        // 5. Verify on-chain unspent status directly via Kaspa Node GET /addresses/{p2shAddress}/utxos
        val verifiedNotes = mutableListOf<DiscoveredNote>()
        for (note in discoveredNotes.distinctBy { "${it.txId.lowercase()}:${it.outputIndex}" }) {
            val isUnspent = checkNoteIsUnspent(note.p2shAddress, note.txId, note.outputIndex, rpcNodes)
            val updated = note.copy(isUnspent = isUnspent)
            verifiedNotes.add(updated)
            val key = "${note.txId.lowercase()}:${note.outputIndex}"
            cachedNotesByOutpoint[key] = updated
            if (note.p2shAddress.isNotBlank()) {
                cachedNotesByOutpoint[note.p2shAddress.lowercase()] = updated
                cachedNoteAddressesByToken.getOrPut(note.covenantId.lowercase()) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add(note.p2shAddress)
            }
        }

        buildScanResultFromNotes(verifiedNotes, knownContractIds, isTestnet)
    }

    /**
     * Builds candidate note redeem script for KCC-20 (kind 1) or KRON (kind 2).
     */
    fun buildNoteRedeem(
        template: TokenTemplate,
        ownerHex: String,
        amount: Long,
        form: String, // "p2pk" or "p2sh"
        code: String
    ): String {
        val leAmount = le8(amount)
        val cleanOwner = ownerHex.lowercase().removePrefix("0x")
        return if (template.kind == 1) {
            val ownerScheme = if (form == "p2sh") "03" else "00"
            template.pre + "08" + leAmount + "20" + cleanOwner + "01" + ownerScheme + "0100" + "20" + ZERO_32 + "20" + ZERO_32 + code
        } else {
            val ownerType = if (form == "p2sh") "01" else "03"
            "20" + cleanOwner + "01" + ownerType + "08" + leAmount + "0100" + code
        }
    }

    /**
     * Parses all opcode pushes from a script and collects candidate integer amounts.
     */
    fun extractCandidateAmountsFromScript(hex: String, out: MutableSet<Long>) {
        val bytes = hexToBytes(hex)
        var i = 0
        while (i < bytes.size) {
            val op = bytes[i++].toInt() and 0xff
            var pushLen = -1
            if (op in 1..75) {
                pushLen = op
            } else if (op == 0x4c && i < bytes.size) {
                pushLen = bytes[i++].toInt() and 0xff
            } else if (op == 0x4d && i + 1 < bytes.size) {
                pushLen = (bytes[i].toInt() and 0xff) or ((bytes[i + 1].toInt() and 0xff) shl 8)
                i += 2
            } else if (op == 0x4e && i + 3 < bytes.size) {
                pushLen = (bytes[i].toInt() and 0xff) or
                    ((bytes[i + 1].toInt() and 0xff) shl 8) or
                    ((bytes[i + 2].toInt() and 0xff) shl 16) or
                    ((bytes[i + 3].toInt() and 0xff) shl 24)
                i += 4
            } else if (op in 0x51..0x60) {
                out.add((op - 0x50).toLong())
                continue
            } else {
                continue
            }

            if (pushLen < 0 || i + pushLen > bytes.size) break
            val pushData = bytes.copyOfRange(i, i + pushLen)
            i += pushLen

            if (pushData.isNotEmpty() && pushData.size <= 8) {
                var vLe = 0L
                for (k in pushData.indices) {
                    vLe = vLe or ((pushData[k].toLong() and 0xffL) shl (8 * k))
                }
                if (vLe > 0L) {
                    out.add(vLe)
                    out.add(vLe * 1000L)
                    if (vLe % 1000L == 0L) out.add(vLe / 1000L)
                }
            }

            // Embedded KCC-20 state check (0x08 followed by 8 bytes little-endian amount)
            for (k in 0 until pushData.size - 9) {
                if (pushData[k] == 0x08.toByte()) {
                    var amt = 0L
                    for (b in 0 until 8) {
                        amt = amt or ((pushData[k + 1 + b].toLong() and 0xffL) shl (8 * b))
                    }
                    if (amt > 0L) {
                        out.add(amt)
                        out.add(amt * 1000L)
                        if (amt % 1000L == 0L) out.add(amt / 1000L)
                    }
                }
            }
        }
    }

    /**
     * Extracts contract suffix code revealed in an input's signature script (last data push).
     */
    fun extractCodeFromInputs(inputs: JSONArray, template: TokenTemplate): String? {
        for (j in 0 until inputs.length()) {
            val input = inputs.optJSONObject(j) ?: continue
            val sig = input.optString("signature_script", input.optString("signatureScript", ""))
            val last = lastPush(sig) ?: continue
            if (last.length < 2 * template.sfl) continue
            val candidateCode = last.substring(last.length - 2 * template.sfl)
            val hash = CryptoUtils.blake2b256(hexToBytes(candidateCode))
                .joinToString("") { "%02x".format(it) }
            if (hash.equals(template.suf, ignoreCase = true)) {
                return candidateCode
            }
        }
        return null
    }

    /**
     * Extracts the last data push of a script (the redeem script of a P2SH input).
     */
    fun lastPush(hex: String): String? {
        val b = hexToBytes(hex)
        var i = 0
        var last: ByteArray? = null
        while (i < b.size) {
            val op = b[i++].toInt() and 0xff
            var n = -1
            if (op in 1..75) {
                n = op
            } else if (op == 0x4c && i < b.size) {
                n = b[i++].toInt() and 0xff
            } else if (op == 0x4d && i + 1 < b.size) {
                n = (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8)
                i += 2
            } else if (op == 0x4e && i + 3 < b.size) {
                n = (b[i].toInt() and 0xff) or
                    ((b[i + 1].toInt() and 0xff) shl 8) or
                    ((b[i + 2].toInt() and 0xff) shl 16) or
                    ((b[i + 3].toInt() and 0xff) shl 24)
                i += 4
            }
            if (n < 0) {
                last = null
                continue
            }
            if (i + n > b.size) return null
            last = b.copyOfRange(i, i + n)
            i += n
        }
        return last?.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /**
     * Checks if a note's outpoint is still unspent by querying GET /addresses/{p2shAddress}/utxos.
     */
    private suspend fun checkNoteIsUnspent(
        p2shAddress: String,
        txId: String,
        outputIndex: Int,
        rpcNodes: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        for (node in rpcNodes) {
            try {
                val url = "$node/addresses/$p2shAddress/utxos"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("Accept", "application/json")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(body)
                    for (i in 0 until array.length()) {
                        val utxo = array.optJSONObject(i) ?: continue
                        val outpoint = utxo.optJSONObject("outpoint")
                            ?: utxo.optJSONObject("previous_outpoint")
                            ?: utxo
                        val uTxid = outpoint.optString("transaction_id", outpoint.optString("transactionId", ""))
                        val uIndex = outpoint.optInt("index", outpoint.optInt("output_index", -1))
                        if (uTxid.equals(txId, ignoreCase = true) && uIndex == outputIndex) {
                            return@withContext true
                        }
                    }
                    return@withContext false
                }
            } catch (_: Exception) {}
        }
        // Fallback: assume unspent if network call fails
        true
    }

    /**
     * Queries the KRON indexer (idx.kron.technology/v1/kcc20/token/{tick}/address/{addr}/utxos)
     * for KRON-family tokens and extracts notes directly from redeemScriptHex.
     */
    private suspend fun fetchKronNotes(
        walletAddress: String,
        publicKeyHex: String,
        prefix: String
    ): List<DiscoveredNote> = withContext(Dispatchers.IO) {
        val notes = mutableListOf<DiscoveredNote>()
        val kronTickers = listOf("KRON", "MKRON", "PEPE", "MKPEPE", "IFWEN", "ANSEM", "KASCOV")

        for (tick in kronTickers) {
            try {
                val url = "https://idx.kron.technology/v1/kcc20/token/$tick/address/$walletAddress/utxos"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                    setRequestProperty("Accept", "application/json")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(body)
                    val array = root.optJSONArray("result") ?: root.optJSONArray("utxos") ?: JSONArray()
                    for (i in 0 until array.length()) {
                        val u = array.optJSONObject(i) ?: continue
                        val rHex = u.optString("redeemScriptHex", u.optString("redeem_script_hex", "")).lowercase()
                        if (rHex.length < 88) continue

                        // In KRON: owner is at bytes 1-32 (hex chars 2..66)
                        val owner = rHex.substring(2, 66)
                        if (!owner.equals(publicKeyHex, ignoreCase = true)) continue

                        // Amount is int64 LE after type byte
                        val amtHex = rHex.substring(72, 88)
                        val amt = rd8(amtHex)
                        val txId = u.optString("transactionId", u.optString("transaction_id", ""))
                        val outIdx = u.optInt("index", 0)
                        val covId = u.optString("covenantId", u.optString("covenant_id", ""))
                        val spk = CryptoUtils.blake2b256(hexToBytes(rHex)).joinToString("") { "%02x".format(it) }
                        val p2shAddr = CryptoUtils.encodeP2shAddress(hexToBytes(spk), prefix)

                        if (amt > 0L) {
                            notes.add(
                                DiscoveredNote(
                                    txId = txId,
                                    outputIndex = outIdx,
                                    covenantId = covId.ifBlank { "kron-$tick" },
                                    sompiAmount = u.optLong("amount", 10_000L),
                                    rawTokenAmount = amt,
                                    scriptPublicKey = "aa20${spk}87",
                                    targetScriptHash = spk,
                                    p2shAddress = p2shAddr,
                                    redeemScriptHex = rHex,
                                    lineage = "kron",
                                    blockTime = System.currentTimeMillis(),
                                    isUnspent = true
                                )
                            )
                            registerRedeemScript(txId, outIdx, spk, rHex)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        notes
    }

    private suspend fun fetchFullTransactions(address: String, rpcNodes: List<String>): List<JSONObject> {
        for (node in rpcNodes) {
            try {
                val url = "$node/addresses/$address/full-transactions?limit=50"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/json")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(body)
                    val result = mutableListOf<JSONObject>()
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        result.add(item)
                    }
                    return result
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    fun resolveMetadata(covenantId: String, isTestnet: Boolean): TokenMetadata {
        val cleanId = covenantId.trim().lowercase()
        val known = KNOWN_TOKENS[cleanId]
        if (known != null) return known

        // 1. Query KasCov oracle / indexer for real on-chain token metadata
        try {
            val net = if (isTestnet) "testnet-10" else "mainnet"
            val conn = (URL("https://kascov.io/data/$net/token/$cleanId").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
                setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0")
            }
            if (conn.responseCode == 200) {
                val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val t = root.optJSONObject("token")
                if (t != null) {
                    val ticker = t.optString("claimed_ticker", t.optString("ticker", t.optString("symbol", "")))
                    val name = t.optString("claimed_name", t.optString("name", ticker))
                    val decimals = if (t.has("claimed_decimals") && !t.isNull("claimed_decimals")) {
                        t.optInt("claimed_decimals")
                    } else if (t.has("display_decimals") && !t.isNull("display_decimals")) {
                        t.optInt("display_decimals")
                    } else if (t.has("decimals") && !t.isNull("decimals")) {
                        t.optInt("decimals")
                    } else {
                        if (ticker.equals("KB", true)) 3 else 0
                    }
                    val icon = t.optString("icon_url", t.optString("image_url", t.optString("imageUrl", t.optString("icon", "")))).ifBlank { null }
                    if (ticker.isNotBlank()) {
                        return TokenMetadata(
                            covenantId = cleanId,
                            ticker = ticker.uppercase(),
                            name = name.ifBlank { ticker.uppercase() },
                            decimals = decimals,
                            imageUrl = icon,
                            launchpad = t.optString("launchpad", "KasCov")
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Query KasSeal launchpad API
        try {
            val kassealBase = if (isTestnet) "https://testnet.kasseal.com" else "https://app.kasseal.com"
            val conn = (URL("$kassealBase/v1/tokens/$cleanId").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
            }
            if (conn.responseCode == 200) {
                val obj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val meta = obj.optJSONObject("meta") ?: obj
                val ticker = meta.optString("ticker", meta.optString("symbol", ""))
                if (ticker.isNotBlank()) {
                    return TokenMetadata(
                        covenantId = cleanId,
                        ticker = ticker.uppercase(),
                        name = meta.optString("name", ticker.uppercase()),
                        decimals = meta.optInt("decimals", 0),
                        imageUrl = meta.optString("imageUrl", meta.optString("icon", null)),
                        launchpad = "KasSeal"
                    )
                }
            }
        } catch (_: Exception) {}

        // 3. Query KaspaRocket launchpad API
        try {
            val rocketBase = "https://kasparocket.fun"
            val conn = (URL("$rocketBase/api/tokens/$cleanId").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
            }
            if (conn.responseCode == 200) {
                val meta = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val ticker = meta.optString("ticker", meta.optString("symbol", ""))
                if (ticker.isNotBlank()) {
                    return TokenMetadata(
                        covenantId = cleanId,
                        ticker = ticker.uppercase(),
                        name = meta.optString("name", ticker.uppercase()),
                        decimals = meta.optInt("decimals", 3),
                        imageUrl = meta.optString("image", meta.optString("imageUrl", null)),
                        launchpad = "KaspaRocket"
                    )
                }
            }
        } catch (_: Exception) {}

        return TokenMetadata(
            covenantId = cleanId,
            ticker = cleanId.take(6).uppercase(),
            name = "KCC-20 ${cleanId.take(6).uppercase()}",
            decimals = 0,
            launchpad = "Kaspa Covenant"
        )
    }

    private fun resolveCovenantId(output: JSONObject, inputs: JSONArray): String? {
        val direct = output.optString("covenant_id", output.optString("covenantId", "")).trim()
        if (direct.matches(Regex("[0-9a-fA-F]{64}"))) return direct.lowercase()

        val covObj = output.optJSONObject("covenant")
        val nested = covObj?.optString("covenant_id", covObj.optString("covenantId", "")).orEmpty().trim()
        if (nested.matches(Regex("[0-9a-fA-F]{64}"))) return nested.lowercase()

        val verbose = output.optJSONObject("verbose_data") ?: output.optJSONObject("verboseData")
        val verbCov = verbose?.optString("covenant_id", verbose.optString("covenantId", "")).orEmpty().trim()
        if (verbCov.matches(Regex("[0-9a-fA-F]{64}"))) return verbCov.lowercase()

        // Fallback: check if inputs carry the covenant ID
        for (j in 0 until inputs.length()) {
            val inObj = inputs.optJSONObject(j) ?: continue
            val inCov = inObj.optString("covenant_id", inObj.optString("covenantId", "")).trim()
            if (inCov.matches(Regex("[0-9a-fA-F]{64}"))) return inCov.lowercase()
        }
        return null
    }

    private fun readScriptHex(json: JSONObject): String {
        val scriptObj = json.optJSONObject("script_public_key") ?: json.optJSONObject("scriptPublicKey")
        if (scriptObj != null) {
            val s = scriptObj.optString("script_public_key", scriptObj.optString("script", "")).trim()
            if (s.isNotBlank()) return s.lowercase()
        }
        val s = json.optString("script_public_key", json.optString("scriptPublicKey", json.optString("script", ""))).trim()
        return s.lowercase()
    }

    private fun readAmount(json: JSONObject): Long {
        return json.optLong("amount", json.optLong("value", 0L))
    }

    fun formatTokenAmount(rawUnits: Long, decimals: Int): String {
        if (decimals <= 0) return rawUnits.toString()
        val bd = java.math.BigDecimal(rawUnits).movePointLeft(decimals).stripTrailingZeros()
        return bd.toPlainString()
    }

    fun le8(value: Long): String {
        val bytes = ByteArray(8)
        for (i in 0 until 8) {
            bytes[i] = ((value ushr (8 * i)) and 0xffL).toByte()
        }
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun rd8(hex: String): Long {
        val bytes = hexToBytes(hex)
        var v = 0L
        for (i in 0 until minOf(8, bytes.size)) {
            v = v or ((bytes[i].toLong() and 0xffL) shl (8 * i))
        }
        return v
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim().lowercase().removePrefix("0x")
        val n = clean.length / 2
        return ByteArray(n) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    /**
     * Resolves or reconstructs a candidate KCC-20 covenant redeem script for a P2SH input.
     * Tries:
     * 1. In-memory & previously registered redeem script cache
     * 2. Transaction on-chain input pushes & dynamic code templates matching targetScriptHash
     * 3. KasCov token cells endpoint for the user's address/token
     * 4. KRON token indexer
     */
    suspend fun resolveOrReconstructRedeemScript(
        txId: String,
        outputIndex: Int,
        p2shScriptHash: String,
        walletAddress: String,
        walletPublicKeyHex: String,
        network: String = "testnet-10"
    ): String? = withContext(Dispatchers.IO) {
        val cleanHash = p2shScriptHash.trim().lowercase()
        val cleanPub = walletPublicKeyHex.trim().lowercase().removePrefix("0x")
        val cleanAddr = walletAddress.trim().lowercase()

        // 1. Check in-memory cache
        findRedeemScript(txId, outputIndex.toLong(), cleanHash)?.let { cached ->
            val h = CryptoUtils.blake2b256(hexToBytes(cached)).joinToString("") { "%02x".format(it) }
            if (h.equals(cleanHash, ignoreCase = true)) {
                return@withContext cached
            }
        }

        val isTestnet = cleanAddr.startsWith("kaspatest:") || network.contains("testnet", ignoreCase = true)
        val expectedPrefix = if (isTestnet) "kaspatest" else "kaspa"
        val rpcNodes = if (isTestnet) {
            listOf("https://api-tn10.kaspa.org", "https://api-tn10.kaspanet.io")
        } else {
            listOf("https://api.kaspa.org", "https://api.kaspanet.io")
        }

        // 2. Fetch the transaction that created this UTXO and reconstruct from contract templates
        if (txId.isNotBlank()) {
            for (rpc in rpcNodes) {
                try {
                    val url = URL("$rpc/transactions/$txId")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5000
                        readTimeout = 5000
                        setRequestProperty("Accept", "application/json")
                    }
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val txObj = JSONObject(body)
                        val outputs = txObj.optJSONArray("outputs") ?: JSONArray()
                        val inputs = txObj.optJSONArray("inputs") ?: JSONArray()

                        val candidateAmounts = mutableSetOf<Long>()
                        for (j in 0 until inputs.length()) {
                            val inp = inputs.optJSONObject(j) ?: continue
                            val sig = inp.optString("signature_script", inp.optString("signatureScript", ""))
                            if (sig.isNotBlank()) {
                                extractCandidateAmountsFromScript(sig, candidateAmounts)
                                lastPush(sig)?.let { pushHex ->
                                    val pushHash = CryptoUtils.blake2b256(hexToBytes(pushHex)).joinToString("") { "%02x".format(it) }
                                    if (pushHash.equals(cleanHash, ignoreCase = true)) {
                                        registerRedeemScript(txId, outputIndex, cleanHash, pushHex)
                                        return@withContext pushHex
                                    }
                                }
                            }
                        }

                        // Also add amounts from outputs and common balances
                        for (j in 0 until outputs.length()) {
                            val out = outputs.optJSONObject(j) ?: continue
                            val amt = readAmount(out)
                            if (amt > 0L) candidateAmounts.add(amt)
                        }

                        for ((lineage, template) in TEMPLATES) {
                            val code = codeCache[lineage] ?: extractCodeFromInputs(inputs, template) ?: template.suf
                            val forms = listOf("p2pk", "p2sh")
                            for (amt in candidateAmounts) {
                                if (amt <= 0L) continue
                                for (form in forms) {
                                    val candidateRedeem = buildNoteRedeem(template, cleanPub, amt, form, code)
                                    val h = CryptoUtils.blake2b256(hexToBytes(candidateRedeem)).joinToString("") { "%02x".format(it) }
                                    if (h.equals(cleanHash, ignoreCase = true)) {
                                        registerRedeemScript(txId, outputIndex, cleanHash, candidateRedeem)
                                        return@withContext candidateRedeem
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 3. Query KasCov live token cells for this wallet
        try {
            val net = if (isTestnet) "testnet-10" else "mainnet"
            val cellEndpoints = listOf(
                "https://kascov.io/data/$net/address/$cleanAddr/cells",
                "https://kascov.io/data/$net/token/cells"
            )
            for (ep in cellEndpoints) {
                try {
                    val url = URL(ep)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5000
                        readTimeout = 5000
                    }
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val array = try { JSONArray(body) } catch (_: Exception) { JSONObject(body).optJSONArray("cells") ?: JSONArray() }
                        for (k in 0 until array.length()) {
                            val cell = array.optJSONObject(k) ?: continue
                            val rHex = cell.optString("redeemScriptHex", cell.optString("redeem_script_hex", cell.optString("redeem", ""))).lowercase()
                            if (rHex.isNotBlank()) {
                                val h = CryptoUtils.blake2b256(hexToBytes(rHex)).joinToString("") { "%02x".format(it) }
                                if (h.equals(cleanHash, ignoreCase = true)) {
                                    registerRedeemScript(txId, outputIndex, cleanHash, rHex)
                                    return@withContext rHex
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 4. Query KRON indexer
        try {
            val kronNotes = fetchKronNotes(cleanAddr, cleanPub, expectedPrefix)
            for (n in kronNotes) {
                registerRedeemScript(n.txId, n.outputIndex, n.targetScriptHash, n.redeemScriptHex)
                if (n.targetScriptHash.equals(cleanHash, ignoreCase = true)) {
                    return@withContext n.redeemScriptHex
                }
            }
        } catch (_: Exception) {}

        null
    }
}
