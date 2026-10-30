package com.example.network.kaspa

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
    KCC20_COVENANT("KCC-20", "L2 Covenant", "Kaspa Covenant & Smart Contract Token Protocol (KasCov Indexer)"),
    KRC20_INSCRIPTION("KRC-20", "L2 Inscription", "Kasplex Inscription Protocol (Commit-Reveal Payloads)");

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
    val balanceRaw: String?,
    val displayDecimals: Int?,
    val holders: Long?,
    val validationStatus: String,
    val metadataSource: String,
    val image: String?,
    val balanceOwnerMatched: Boolean
)

class Kcc20KasivoService(private val context: Context? = null) {

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
    suspend fun fetchWalletToken(
        address: String,
        contractId: String,
        network: String = "mainnet"
    ): Result<Kcc20WalletToken> = withContext(Dispatchers.IO) {
        if (address.isBlank()) return@withContext Result.failure(IllegalArgumentException("Wallet address is empty"))
        if (network != "mainnet") return@withContext Result.failure(IllegalArgumentException("KCC-20 index lookup is currently mainnet-only"))
        try {
            fun getJson(path: String): JSONObject {
                val connection = (URL("https://kascov.io/data/$network/$path").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0")
                }
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) throw java.io.IOException("Kascov returned HTTP ${connection.responseCode}")
                    return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } finally { connection.disconnect() }
            }
            val detail = getJson("token/$contractId")
            val token = detail.optJSONObject("token") ?: throw java.io.IOException("Token metadata is missing from Kascov")
            val holderPage = getJson("token/$contractId/holders?limit=100")
            val holdersArray = holderPage.optJSONArray("holders") ?: JSONArray()
            val normalizedAddress = address.trim().lowercase()
            var matchedBalance: String? = null
            for (index in 0 until holdersArray.length()) {
                val holder = holdersArray.optJSONObject(index) ?: continue
                val ownerAddress = holder.optString("owner_address", "").trim().lowercase()
                if (ownerAddress.isNotBlank() && ownerAddress == normalizedAddress) {
                    matchedBalance = holder.optString("balance_str").takeIf { it.isNotBlank() }
                        ?: holder.opt("balance")?.toString()?.takeIf { it.isNotBlank() }
                    break
                }
            }
            val fields = token.optJSONObject("fields")
            val ticker = token.optString("claimed_ticker").ifBlank { fields?.optString("tick", "")?.ifBlank { "KCC20" } ?: "KCC20" }
            Result.success(Kcc20WalletToken(
                contractId = token.optString("covenant_id", contractId),
                ticker = ticker,
                name = token.optString("claimed_name").ifBlank { token.optString("name", ticker) },
                balanceRaw = matchedBalance,
                displayDecimals = if (token.has("display_decimals") && !token.isNull("display_decimals")) token.optInt("display_decimals") else null,
                holders = if (token.has("holders")) token.optLong("holders") else null,
                validationStatus = if (token.optString("status") == "verified") "verified" else "unvalidated",
                metadataSource = token.optString("metadata_source", "chain indexer"),
                image = token.optString("claimed_image").takeIf { it.startsWith("https://") },
                balanceOwnerMatched = matchedBalance != null
            ))
        } catch (error: Exception) {
            Result.failure(error)
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

        for (baseEndpoint in KASCOV_ENDPOINTS) {
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

        Result.success(
            Kcc20TokenBalanceResult(
                tokenContractId = identifier,
                ticker = identifier,
                balance = 0.0,
                balanceRaw = "0",
                decimals = 8,
                protocolType = KaspaProtocolType.KCC20_COVENANT,
                contractState = "active",
                holderCount = 0L,
                rawMetadataJson = JSONObject().apply {
                    put("p", "kcc-20")
                    put("indexer", "https://kascov.io")
                    put("contractId", identifier)
                    put("tick", identifier)
                    put("status", "unindexed")
                }.toString(2)
            )
        )
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
}
