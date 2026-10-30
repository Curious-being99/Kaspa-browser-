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
    val balanceRaw: String,
    val decimals: Int,
    val holderCount: Long?,
    val validationStatus: String,
    val marketPriceKas: String?,
    val marketPriceUsd: String?,
    val marketPriceSource: String?,
    val priceUpdatedAtMs: Long?,
    val priceChange24hPercent: String?,
    val imageUrl: String?
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
        publicKeyHex: String,
        contractId: String,
        network: String = "mainnet",
        kasUsd: String? = null,
        walletAddress: String? = null
    ): Result<Kcc20WalletToken> = withContext(Dispatchers.IO) {
        val owner = publicKeyHex.trim().lowercase().removePrefix("0x")
        val tokenId = contractId.trim().lowercase()
        if (!owner.matches(Regex("[0-9a-f]{64}"))) {
            return@withContext Result.failure(IllegalArgumentException("Wallet public key must be 32-byte x-only hex"))
        }
        if (!tokenId.matches(Regex("[0-9a-f]{64}"))) {
            return@withContext Result.failure(IllegalArgumentException("KCC-20 covenant ID must be 32-byte hex"))
        }
        if (network != "mainnet") {
            return@withContext Result.failure(IllegalArgumentException("Kascov KCC-20 token directory is currently queried on mainnet"))
        }
        try {
            fun getJson(path: String): JSONObject {
                val connection = (URL("https://kascov.io/data/$network/$path").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0")
                }
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        throw java.io.IOException("Kascov HTTP ${connection.responseCode} for $path")
                    }
                    return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    connection.disconnect()
                }
            }

            val detail = getJson("token/$tokenId")
            val token = detail.optJSONObject("token")
                ?: throw java.io.IOException("Kascov did not return token metadata")
            val balances = detail.optJSONArray("balances") ?: JSONArray()
            val normalizedAddress = walletAddress?.trim()?.lowercase()
            fun isWalletOwner(row: JSONObject): Boolean {
                val rowOwner = row.optString("owner").trim().lowercase().removePrefix("0x")
                val rowAddress = row.optString("owner_address").trim().lowercase()
                return rowOwner == owner || (normalizedAddress != null && rowAddress.isNotBlank() && rowAddress == normalizedAddress)
            }
            var balanceRaw: String? = null
            for (i in 0 until balances.length()) {
                val row = balances.optJSONObject(i) ?: continue
                if (isWalletOwner(row)) {
                    balanceRaw = row.optString("balance_str").takeIf { it.isNotBlank() }
                        ?: row.opt("balance")?.toString()
                    break
                }
            }

            if (balanceRaw == null) {
                var afterBalance: String? = null
                var afterOwner: String? = null
                var afterClass: String? = null
                var matched = false
                var pageCount = 0
                do {
                    val query = buildString {
                        append("token/$tokenId/holders?limit=100")
                        if (afterBalance != null && afterOwner != null) {
                            append("&after_balance=").append(java.net.URLEncoder.encode(afterBalance, "UTF-8"))
                            append("&after_owner=").append(java.net.URLEncoder.encode(afterOwner, "UTF-8"))
                            if (afterClass != null) append("&after_class=").append(java.net.URLEncoder.encode(afterClass, "UTF-8"))
                        }
                    }
                    val page = getJson(query)
                    val rows = page.optJSONArray("holders") ?: JSONArray()
                    for (i in 0 until rows.length()) {
                        val row = rows.optJSONObject(i) ?: continue
                        if (isWalletOwner(row)) {
                            balanceRaw = row.optString("balance_str").takeIf { it.isNotBlank() }
                                ?: row.opt("balance")?.toString()
                            matched = true
                            break
                        }
                    }
                    if (matched) break
                    afterBalance = page.optString("next_after_balance").takeIf { it.isNotBlank() && it != "null" }
                    afterOwner = page.optString("next_after_owner").takeIf { it.isNotBlank() && it != "null" }
                    afterClass = page.optString("next_after_class").takeIf { it.isNotBlank() && it != "null" }
                    pageCount++
                } while (afterBalance != null && afterOwner != null && pageCount < 500)
                if (balanceRaw == null && afterBalance != null && afterOwner != null) {
                    throw java.io.IOException("Holder scan reached its safety limit; wallet balance is not confirmed")
                }
            }

            val claimedDecimals = if (token.has("claimed_decimals") && !token.isNull("claimed_decimals")) token.optInt("claimed_decimals") else null
            val displayDecimals = if (token.has("display_decimals") && !token.isNull("display_decimals")) token.optInt("display_decimals") else null
            val decimals = claimedDecimals ?: displayDecimals ?: 0
            val marketResponse = try { getJson("token/$tokenId/market") } catch (_: Exception) { null }
            val market = marketResponse?.optJSONObject("market") ?: marketResponse
            var priceKas: String? = null
            var priceUsd: String? = null
            var priceSource: String? = null
            var priceUpdatedAt: Long? = null

            val liveFeed = try {
                val feedConnection = (URL("https://kascov.io/data/$network-live.json").openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 10000 }
                try { JSONObject(feedConnection.inputStream.bufferedReader().use { it.readText() }) } finally { feedConnection.disconnect() }
            } catch (_: Exception) { JSONObject() }
            val currentDaa = liveFeed.optLong("tip_daa")
            val tradeRows = try { getJson("token/$tokenId/trades?limit=100").optJSONArray("trades") ?: JSONArray() } catch (_: Exception) { JSONArray() }
            var oldTradePrice: java.math.BigDecimal? = null
            if (currentDaa > 0L) {
                for (i in 0 until tradeRows.length()) {
                    val trade = tradeRows.optJSONObject(i) ?: continue
                    if (trade.has("market_recognized") && !trade.optBoolean("market_recognized")) continue
                    val daa = trade.optLong("accepting_daa", trade.optLong("daa"))
                    if (daa <= 0L || daa > currentDaa - 86400L) continue
                    val quote = trade.optString("quote_sompi").toBigDecimalOrNull() ?: continue
                    val base = trade.optString("base_amount").toBigDecimalOrNull() ?: continue
                    if (base.signum() <= 0) continue
                    val scale = if (trade.has("display_decimals")) trade.optInt("display_decimals") else decimals
                    oldTradePrice = quote.multiply(java.math.BigDecimal.TEN.pow(scale))
                        .divide(base, 18, java.math.RoundingMode.HALF_UP)
                    break
                }
            }
            val currentPriceSompi = market?.let {
                val numerator = it.optString("spot_num_sompi").toBigDecimalOrNull()
                val denominator = it.optString("spot_den").toBigDecimalOrNull()
                if (numerator != null && denominator != null && denominator.signum() > 0) {
                    numerator.multiply(java.math.BigDecimal.TEN.pow(decimals))
                        .divide(denominator, 18, java.math.RoundingMode.HALF_UP)
                } else {
                    val quote = it.optString("quote_sompi").toBigDecimalOrNull()
                    val base = it.optString("base_amount").toBigDecimalOrNull()
                    if (quote != null && base != null && base.signum() > 0) {
                        quote.multiply(java.math.BigDecimal.TEN.pow(decimals))
                            .divide(base, 18, java.math.RoundingMode.HALF_UP)
                    } else null
                }
            }
            val change24h = if (currentPriceSompi != null && oldTradePrice != null && oldTradePrice.signum() > 0) {
                currentPriceSompi.subtract(oldTradePrice).multiply(java.math.BigDecimal("100"))
                    .divide(oldTradePrice, 8, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            } else null

            val isVerified = token.optString("status") == "verified"
            if (isVerified && currentPriceSompi != null) {
                val kas = currentPriceSompi.divide(java.math.BigDecimal("100000000"), 18, java.math.RoundingMode.HALF_UP)
                priceKas = kas.stripTrailingZeros().toPlainString()
                val usdRate = kasUsd?.toBigDecimalOrNull()
                if (usdRate != null) priceUsd = kas.multiply(usdRate).stripTrailingZeros().toPlainString()
                priceSource = market?.optString("source")?.takeIf { it.isNotBlank() } ?: "Kascov verified market quote"
                priceUpdatedAt = market?.optLong("updated_at_ms")?.takeIf { it > 0 }
            }
            val ticker = token.optString("claimed_ticker").ifBlank {
                token.optJSONObject("fields")?.optString("tick", "")?.ifBlank { "KCC20" } ?: "KCC20"
            }
            Result.success(Kcc20WalletToken(
                contractId = token.optString("covenant_id", tokenId),
                ticker = ticker,
                name = token.optString("claimed_name").ifBlank { token.optString("name", ticker) },
                balanceRaw = balanceRaw ?: "0",
                decimals = decimals,
                holderCount = if (token.has("holders")) token.optLong("holders") else null,
                validationStatus = if (isVerified) "verified" else token.optString("status").ifBlank { "unvalidated" },
                marketPriceKas = priceKas,
                marketPriceUsd = priceUsd,
                marketPriceSource = priceSource,
                priceUpdatedAtMs = priceUpdatedAt,
                priceChange24hPercent = change24h,
                imageUrl = token.optString("claimed_image").takeIf { it.startsWith("https://") }
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
