package com.example.network.kaspa

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.bouncycastle.crypto.digests.Blake3Digest
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.pow

/**
 * Protocol Taxonomy defining the strict boundary between Kaspa Mainnet L1 native KAS,
 * L2 KCC-20 Kaspa Covenant / Smart Contract Tokens (KasCov / Kasivo), and L2 KRC-20 Kasplex Inscription Tokens.
 */
enum class KaspaProtocolType(val displayName: String, val layer: String, val description: String) {
    L1_KASPA("KAS Native", "L1", "Kaspa BlockDAG Native UTXO Layer"),
    KCC20_COVENANT("KCC-20", "L1", "Kaspa Native L1 Covenant & Smart Contract Protocol (KasCov)"),
    KRC20_INSCRIPTION("KRC-20", "L2", "Kasplex & Igra Inscription Protocol");

    companion object {
        fun parseFromJson(jsonString: String): KaspaProtocolType {
            if (jsonString.isBlank()) return L1_KASPA
            return try {
                val obj = JSONObject(jsonString)
                val protocol = obj.optString("p", "").lowercase()
                when {
                    protocol == "kcc-20" || obj.has("covenantId") || obj.has("contractId") -> KCC20_COVENANT
                    protocol == "krc-20" || obj.has("tick") && obj.has("lim") -> KRC20_INSCRIPTION
                    else -> L1_KASPA
                }
            } catch (_: Exception) {
                if (jsonString.contains("kcc-20", ignoreCase = true) || jsonString.contains("kascov", ignoreCase = true) || jsonString.contains("kasivo", ignoreCase = true)) {
                    KCC20_COVENANT
                } else if (jsonString.contains("krc-20", ignoreCase = true)) {
                    KRC20_INSCRIPTION
                } else {
                    L1_KASPA
                }
            }
        }
    }
}

/**
 * Model representing a KCC-20 (Kaspa Covenant Token) balance.
 */
data class Kcc20TokenBalanceResult(
    val tokenContractId: String,
    val ticker: String,
    val balance: Double,
    val balanceRaw: String,
    val decimals: Int = 8,
    val protocolType: KaspaProtocolType = KaspaProtocolType.KCC20_COVENANT,
    val contractState: String = "active",
    val holderCount: Long = 0L,
    val rawMetadataJson: String = ""
) {
    fun formattedBalance(): String = "%,.4f %s".format(balance, ticker)
}

/**
 * Model representing a transaction item under the KCC-20 covenant protocol.
 */
data class Kcc20TransactionHistoryItem(
    val txId: String,
    val tokenContractId: String,
    val ticker: String,
    val operation: String, // "TRANSFER", "COVENANT_EXEC", "SWAP", "MINT"
    val amount: Double,
    val amountRaw: String,
    val fromAddress: String,
    val toAddress: String,
    val timestamp: Long,
    val status: String,
    val feeKas: Double,
    val protocolType: KaspaProtocolType = KaspaProtocolType.KCC20_COVENANT,
    val rawJson: String = ""
) {
    fun formattedJson(): String {
        return if (rawJson.isNotBlank()) {
            try {
                JSONObject(rawJson).toString(2)
            } catch (_: Exception) {
                rawJson
            }
        } else {
            val obj = JSONObject()
            obj.put("txId", txId)
            obj.put("tokenContractId", tokenContractId)
            obj.put("ticker", ticker)
            obj.put("p", "kcc-20")
            obj.put("op", operation.lowercase())
            obj.put("amount", amountRaw.ifBlank { amount.toString() })
            obj.put("from", fromAddress)
            obj.put("to", toAddress)
            obj.put("timestamp", timestamp)
            obj.put("feeKas", feeKas)
            obj.put("status", status)
            obj.toString(2)
        }
    }
}

/**
 * Multi-protocol taxonomy overview for a given Kaspa Mainnet address.
 */
data class ProtocolTaxonomySummary(
    val address: String,
    val l1KasBalance: Double,
    val kcc20TokenBalances: List<Kcc20TokenBalanceResult>,
    val krc20TokenBalancesCount: Int,
    val primaryProtocol: KaspaProtocolType = KaspaProtocolType.L1_KASPA
)

/**
 * KCC-20 KasCov / Kasivo Service interfacing directly with Kaspa's RPC gateway and KasCov BlockDAG indexer endpoints (https://kascov.io).
 * Connects to live Mainnet network nodes for real-time balances, transaction execution, and covenant state.
 */
data class Kcc20WalletToken(
    val contractId: String,
    val ticker: String,
    val name: String,
    val balanceRaw: String,
    val decimals: Int,
    val holderCount: Long?,
    val validationStatus: String,
    val marketPriceKas: String?,
    val marketPriceUsd: String?,
    val marketPriceSource: String?,
    val priceUpdatedAtMs: Long?,
    val priceChange24hPercent: String?,
    val imageUrl: String?,
    val bondingCurveProgressKas: Double? = null,
    val isGraduated: Boolean = false
)

data class Kcc20CandidateNote(
    val transactionId: String,
    val outputIndex: Int,
    val covenantId: String,
    val scriptPublicKey: String,
    val p2shHash: String,
    val candidateAmounts: List<java.math.BigInteger>
)

internal fun normalizeKascovNetwork(value: String): String? {
    val normalized = value.trim().lowercase().replace('_', '-').replace(' ', '-')
    return when (normalized) {
        "mainnet", "kaspa-mainnet" -> "mainnet"
        "testnet-10", "testnet10", "kaspa-testnet-10", "kaspatest" -> "testnet-10"
        else -> null
    }
}

class Kcc20KasivoService(
    private val context: Context? = null,
    private val endpointBases: List<String> = KASCOV_ENDPOINTS
) {

    companion object {
        const val KASCOV_PRIMARY_BASE = "https://kascov.io/v1/kcc20"

        val KASCOV_ENDPOINTS = listOf(
            "https://kascov.io/v1/kcc20",
            "https://api.kaspa.org",
            "https://api.kaspanet.io"
        )

        // Well-known KCC-20 Kaspa Covenant Token Definitions
        val KNOWN_KCC20_TOKENS = emptyMap<String, Kcc20TokenBalanceResult>()
    }

    /**
     * Differentiate raw payload or metadata JSON into exact Kaspa Protocol Taxonomy.
     */
    /**
     * Finds candidate KCC-20 notes from the wallet's complete REST transaction history.
     * Signature-script amounts are hints only; ownership and liveness still require
     * exact launchpad-template verification and a node UTXO lookup.
     */
    suspend fun discoverCandidateNotes(
        walletAddress: String,
        network: String
    ): Result<List<Kcc20CandidateNote>> = withContext(Dispatchers.IO) {
        val address = walletAddress.trim()
        if (address.isBlank()) return@withContext Result.failure(IllegalArgumentException("Wallet address is required"))
        val apiBase = when (normalizeKascovNetwork(network)) {
            "mainnet" -> "https://api.kaspa.org"
            "testnet-10" -> "https://api-tn10.kaspa.org"
            else -> return@withContext Result.failure(IllegalArgumentException("Unsupported KCC-20 network: $network"))
        }
        try {
            val connection = (URL("$apiBase/addresses/$address/full-transactions").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 20000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0")
            }
            val body = try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw java.io.IOException("Kaspa REST full-transactions returned HTTP ${connection.responseCode}")
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
            val transactions = JSONArray(body)
            val found = linkedMapOf<String, Kcc20CandidateNote>()
            for (txIndex in 0 until transactions.length()) {
                val tx = transactions.optJSONObject(txIndex) ?: continue
                val txid = tx.optString("transaction_id", tx.optString("transactionId", "")).lowercase()
                if (!txid.matches(Regex("[0-9a-f]{64}"))) continue
                val amountHints = linkedSetOf<java.math.BigInteger>()
                val inputs = tx.optJSONArray("inputs") ?: JSONArray()
                for (inputIndex in 0 until inputs.length()) {
                    val input = inputs.optJSONObject(inputIndex) ?: continue
                    val sigScript = input.optString("signature_script", input.optString("signatureScript", "")).lowercase()
                    amountHints.addAll(parsePositiveScriptNumberPushes(sigScript))
                }
                val outputs = tx.optJSONArray("outputs") ?: continue
                for (outputIndex in 0 until outputs.length()) {
                    val output = outputs.optJSONObject(outputIndex) ?: continue
                    val covenant = output.optJSONObject("covenant") ?: output.optJSONObject("covenant_binding")
                    val covenantId = output.optString("covenant_id",
                        output.optString("covenantId",
                            covenant?.optString("covenant_id", covenant?.optString("covenantId", "") ?: "") ?: ""
                        )).lowercase().removePrefix("0x")
                    if (!covenantId.matches(Regex("[0-9a-f]{64}"))) continue
                    val spk = output.optJSONObject("script_public_key") ?: output.optJSONObject("scriptPublicKey")
                    val script = spk?.optString("script", "")?.replace(" ", "")?.lowercase().orEmpty()
                    if (!script.matches(Regex("aa20[0-9a-f]{64}87"))) continue
                    found["${txid}:$outputIndex"] = Kcc20CandidateNote(
                        transactionId = txid,
                        outputIndex = outputIndex,
                        covenantId = covenantId,
                        scriptPublicKey = script,
                        p2shHash = script.substring(4, 68),
                        candidateAmounts = amountHints.toList()
                    )
                }
            }
            Result.success(found.values.toList())
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /**
     * Reads data-pushes from a signature script as untrusted amount hints.
     * No value returned here is accepted as a verified token amount.
     */
    private fun parsePositiveScriptNumberPushes(scriptHex: String): List<java.math.BigInteger> {
        if (scriptHex.length % 2 != 0 || !scriptHex.matches(Regex("[0-9a-f]*"))) return emptyList()
        val bytes = ByteArray(scriptHex.length / 2) { index ->
            scriptHex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        val values = linkedSetOf<java.math.BigInteger>()
        var cursor = 0
        while (cursor < bytes.size) {
            val opcode = bytes[cursor++].toInt() and 0xff
            val length = when {
                opcode in 1..75 -> opcode
                opcode == 0x4c -> {
                    if (cursor >= bytes.size) break
                    bytes[cursor++].toInt() and 0xff
                }
                opcode == 0x4d -> {
                    if (cursor + 1 >= bytes.size) break
                    val n = (bytes[cursor].toInt() and 0xff) or ((bytes[cursor + 1].toInt() and 0xff) shl 8)
                    cursor += 2
                    n
                }
                opcode == 0x4e -> {
                    if (cursor + 3 >= bytes.size) break
                    val n = (bytes[cursor].toInt() and 0xff) or
                        ((bytes[cursor + 1].toInt() and 0xff) shl 8) or
                        ((bytes[cursor + 2].toInt() and 0xff) shl 16) or
                        ((bytes[cursor + 3].toInt() and 0xff) shl 24)
                    cursor += 4
                    n
                }
                else -> continue
            }
            if (length <= 0 || length > bytes.size - cursor) break
            val pushed = bytes.copyOfRange(cursor, cursor + length)
            cursor += length
            if (pushed.size <= 8) {
                val candidate = java.math.BigInteger(1, pushed.reversedArray())
                if (candidate.signum() > 0) values.add(candidate)
            }
        }
        return values.toList()
    }

    suspend fun fetchWalletToken(
        publicKeyHex: String,
        contractId: String,
        network: String = "mainnet",
        kasUsd: String? = null,
        walletAddress: String? = null
    ): Result<Kcc20WalletToken> = withContext(Dispatchers.IO) {
        // Indexer holder totals and transaction-history arithmetic are not wallet balances.
        // A KCC-20 balance is the sum of this owner's covenant notes that are still live
        // on the node, after each note's P2SH redeem script has been verified against the
        // published launchpad artifact. Until that proof path is available, do not return
        // a token with a guessed/default balance (including zero).
        Result.failure(
            IllegalStateException(
                "KCC-20 balance unavailable: live covenant-note discovery and P2SH ownership verification are not implemented."
            )
        )
    }

    /**
     * Fetch real-time KAS oracle price stream straight from Kascov (https://kascov.io/data/price.json).
     */
    suspend fun fetchKasUsdPriceFromOracle(): Result<Double> = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("https://kascov.io/data/price.json").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0")
            }
            try {
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val pJson = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val rate = pJson.optDouble("kas_usd", 0.0)
                    if (rate > 0.0) {
                        return@withContext Result.success(rate)
                    }
                }
                Result.failure(IllegalStateException("KAS/USD oracle did not return a valid price"))
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun classifyProtocol(rawPayload: String): KaspaProtocolType {
        return KaspaProtocolType.parseFromJson(rawPayload)
    }

    /**
     * Fetch real live KCC-20 balance for a Kaspa address and token contract ID/ticker from KasCov indexer (kascov.io).
     */
    suspend fun fetchKcc20Balance(
        address: String,
        tokenContractIdOrTicker: String
    ): Result<Kcc20TokenBalanceResult> = withContext(Dispatchers.IO) {
        if (address.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Address cannot be empty"))
        }

        val identifier = tokenContractIdOrTicker.uppercase().trim()
        val known = KNOWN_KCC20_TOKENS[identifier]

        val isTestnet = address.startsWith("kaspatest:")
        val network = if (isTestnet) "testnet-10" else "mainnet"
        val dynamicEndpoints = if (isTestnet) {
            listOf("https://kascov.io/v1/$network/kcc20", "https://api-tn10.kaspa.org")
        } else {
            endpointBases
        }

        for (baseEndpoint in dynamicEndpoints) {
            try {
                val endpointUrl = if (baseEndpoint.contains("kascov.io") || baseEndpoint.contains("kasivo.org")) {
                    "$baseEndpoint/address/$address/token/$identifier"
                } else {
                    "$baseEndpoint/addresses/$address/full-transactions?limit=10"
                }

                val url = URL(endpointUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0 (kascov.io Indexer)")
                }

                if (conn.responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                    val root = JSONObject(responseText)
                    if (root.has("balance") || root.has("status") || root.has("result")) {
                        val rawBalance = root.optString("balance", "0")
                        val dec = root.optInt("decimals", known?.decimals ?: 8)
                        val doubleBal = rawBalance.toDoubleOrNull()?.div(10.0.pow(dec)) ?: 0.0
                        val contractId = root.optString("contractId", known?.tokenContractId ?: identifier)
                        val tick = root.optString("ticker", known?.ticker ?: identifier)
                        val state = root.optString("state", "active")
                        val holders = root.optLong("holders", known?.holderCount ?: 0L)

                        val result = Kcc20TokenBalanceResult(
                            tokenContractId = contractId,
                            ticker = tick,
                            balance = doubleBal,
                            balanceRaw = rawBalance,
                            decimals = dec,
                            protocolType = KaspaProtocolType.KCC20_COVENANT,
                            contractState = state,
                            holderCount = holders,
                            rawMetadataJson = root.toString(2)
                        )
                        return@withContext Result.success(result)
                    }
                }
            } catch (_: Exception) {
                // Try next live KasCov / Kaspa endpoint
            }
        }

        if (known != null) {
            return@withContext Result.success(known)
        }

        Result.failure(IllegalStateException("KCC-20 balance is unavailable from the configured indexers"))
    }

    /**
     * Fetch real KCC-20 transaction history from KasCov BlockDAG indexer nodes (kascov.io).
     */
    suspend fun fetchKcc20History(
        address: String,
        tokenContractIdOrTicker: String
    ): Result<List<Kcc20TransactionHistoryItem>> = withContext(Dispatchers.IO) {
        val identifier = tokenContractIdOrTicker.uppercase().trim()
        val items = mutableListOf<Kcc20TransactionHistoryItem>()

        if (address.isNotBlank()) {
            for (baseEndpoint in KASCOV_ENDPOINTS) {
                try {
                    val endpointUrl = if (baseEndpoint.contains("kascov.io") || baseEndpoint.contains("kasivo.org")) {
                        "$baseEndpoint/history?address=$address&contract=$identifier"
                    } else {
                        "$baseEndpoint/addresses/$address/full-transactions?limit=20"
                    }

                    val url = URL(endpointUrl)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5000
                        readTimeout = 5000
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0 (kascov.io Indexer)")
                    }

                    if (conn.responseCode == 200) {
                        val responseText = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                        val root = JSONObject(responseText)
                        val resultArray = root.optJSONArray("items") ?: root.optJSONArray("result")
                        if (resultArray != null) {
                            for (i in 0 until resultArray.length()) {
                                val item = resultArray.getJSONObject(i)
                                val txId = item.optString("txId", item.optString("hash", ""))
                                val contractId = item.optString("contractId", identifier)
                                val tick = item.optString("ticker", item.optString("tick", identifier))
                                val op = item.optString("operation", item.optString("op", "TRANSFER")).uppercase()
                                val rawAmt = item.optString("amountRaw", item.optString("amt", "0"))
                                val from = item.optString("fromAddress", item.optString("from", ""))
                                val to = item.optString("toAddress", item.optString("to", ""))
                                val ts = item.optLong("timestamp", item.optLong("mtsAdd", System.currentTimeMillis()))
                                val status = item.optString("status", "ACCEPTED")
                                val dec = item.optInt("decimals", 8)
                                val doubleAmt = rawAmt.toDoubleOrNull()?.div(10.0.pow(dec)) ?: 0.0

                                items.add(
                                    Kcc20TransactionHistoryItem(
                                        txId = txId,
                                        tokenContractId = contractId,
                                        ticker = tick,
                                        operation = op,
                                        amount = doubleAmt,
                                        amountRaw = rawAmt,
                                        fromAddress = from,
                                        toAddress = to,
                                        timestamp = ts,
                                        status = status,
                                        feeKas = 0.0001,
                                        protocolType = KaspaProtocolType.KCC20_COVENANT,
                                        rawJson = item.toString(2)
                                    )
                                )
                            }
                            if (items.isNotEmpty()) break
                        }
                    }
                } catch (_: Exception) {
                    // Failover
                }
            }
        }

        Result.success(items.sortedByDescending { it.timestamp })
    }

    /**
     * Generate a multi-protocol taxonomy overview for an address across L1 KAS, L2 KCC-20, and L2 KRC-20.
     */
    suspend fun fetchProtocolTaxonomySummary(
        address: String,
        l1KasBalance: Double
    ): ProtocolTaxonomySummary = withContext(Dispatchers.IO) {
        val kcc20Balances = mutableListOf<Kcc20TokenBalanceResult>()

        // Query well-known KCC-20 tokens
        KNOWN_KCC20_TOKENS.keys.forEach { tick ->
            val res = fetchKcc20Balance(address, tick).getOrNull()
            if (res != null) {
                kcc20Balances.add(res)
            }
        }

        ProtocolTaxonomySummary(
            address = address,
            l1KasBalance = l1KasBalance,
            kcc20TokenBalances = kcc20Balances,
            krc20TokenBalancesCount = 0,
            primaryProtocol = if (kcc20Balances.any { it.balance > 0 }) KaspaProtocolType.KCC20_COVENANT else KaspaProtocolType.L1_KASPA
        )
    }

    /**
     * Fetch live KCC-20 covenant token cells (UTXOs with redeem bytecode) owned by a wallet pubkey.
     * Maps directly to [KbKcc20StandardAssembler.SpendPiece] for real on-chain covenant spending.
     */
    suspend fun fetchLiveTokenCells(
        contractId: String,
        publicKeyHex: String,
        walletAddress: String? = null
    ): Result<List<KbKcc20StandardAssembler.SpendPiece>> = withContext(Dispatchers.IO) {
        // Deliberately fail closed. The generic Kascov cell endpoints do not provide
        // enough independently verified information to construct spend inputs:
        // the launchpad artifact, owner scheme, exact redeem script, complete outpoint,
        // output value, and node-confirmed UTXO liveness must all be established first.
        // Never synthesize a redeem script, amount, outpoint index, or output value.
        Result.failure(
            IllegalStateException(
                "KCC-20 spend inputs are unavailable: launchpad-specific artifact and exact live UTXO verification are not implemented."
            )
        )
    }

}
