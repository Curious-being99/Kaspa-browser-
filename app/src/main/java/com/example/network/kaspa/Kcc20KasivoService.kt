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
            val strippedAddress = normalizedAddress?.removePrefix("kaspa:")
            fun isWalletOwner(row: JSONObject): Boolean {
                val rowOwner = row.optString("owner").trim().lowercase().removePrefix("0x")
                val rowAddress = row.optString("owner_address", row.optString("address", row.optString("holder", ""))).trim().lowercase()
                val schemeName = row.optString("owner_scheme_name").trim().lowercase()
                val scheme = row.optString("owner_scheme").trim().lowercase()
                val directPublicKeyMatch = rowOwner == owner || rowOwner.contains(owner)
                val addressMatch = normalizedAddress != null && normalizedAddress.isNotBlank() && (
                    rowAddress == normalizedAddress ||
                    (strippedAddress != null && rowAddress.removePrefix("kaspa:") == strippedAddress) ||
                    rowOwner == normalizedAddress ||
                    (strippedAddress != null && rowOwner.removePrefix("kaspa:") == strippedAddress) ||
                    (strippedAddress != null && rowAddress.contains(strippedAddress)) ||
                    (strippedAddress != null && rowOwner.contains(strippedAddress))
                )
                val isSchnorrKeyHashOwner =
                    rowOwner.startsWith("p2pkh:") &&
                    (schemeName == "p2pkh-schnorr/v1" || scheme == "p2pkh-schnorr/v1" || schemeName.isBlank() || scheme.isBlank())
                val schnorrKeyHashMatch = if (isSchnorrKeyHashOwner) {
                    val digest = Blake3Digest()
                    val publicKeyBytes = owner.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    val hash = ByteArray(32)
                    digest.update(publicKeyBytes, 0, publicKeyBytes.size)
                    digest.doFinal(hash, 0)
                    rowOwner == "p2pkh:" + hash.joinToString("") { "%02x".format(it) }
                } else false
                if (rowOwner.startsWith("p2pkh-ecdsa:") || schemeName == "p2pkh-ecdsa/v1" || scheme == "p2pkh-ecdsa/v1") {
                    return addressMatch
                }
                return directPublicKeyMatch || schnorrKeyHashMatch || addressMatch
            }
            fun exactBalance(row: JSONObject): String? {
                return row.optString("balance_str").takeIf { it.isNotBlank() && it != "null" }
                    ?: row.optString("balance").takeIf { it.isNotBlank() && it != "null" }
                    ?: row.optString("amount").takeIf { it.isNotBlank() && it != "null" }
                    ?: row.optString("value").takeIf { it.isNotBlank() && it != "null" }
                    ?: row.optString("total_balance").takeIf { it.isNotBlank() && it != "null" }
                    ?: row.opt("balance")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            }
            var balanceRaw: String? = null
            for (i in 0 until balances.length()) {
                val row = balances.optJSONObject(i) ?: continue
                if (isWalletOwner(row)) {
                    balanceRaw = exactBalance(row)
                    break
                }
            }

            if (balanceRaw == null) {
                try {
                    val page = getJson("token/$tokenId/holders?limit=100")
                    val rows = page.optJSONArray("holders")
                    if (rows != null) {
                        for (i in 0 until rows.length()) {
                            val row = rows.optJSONObject(i) ?: continue
                            if (isWalletOwner(row)) {
                                balanceRaw = exactBalance(row)
                                break
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Failover gracefully
                }
            }
            if ((balanceRaw == null || balanceRaw == "0") && !walletAddress.isNullOrBlank()) {
                try {
                    val historyRes = fetchKcc20History(walletAddress, tokenId).getOrNull()
                    if (!historyRes.isNullOrEmpty()) {
                        var netBaseUnits = 0L
                        val cleanTarget = walletAddress.trim().lowercase().removePrefix("kaspa:")
                        for (item in historyRes) {
                            val itemTo = item.toAddress.trim().lowercase().removePrefix("kaspa:")
                            val itemFrom = item.fromAddress.trim().lowercase().removePrefix("kaspa:")
                            val rawAmt = item.amountRaw.toLongOrNull() ?: (item.amount * 1000.0).toLong()
                            if (itemTo == cleanTarget) {
                                netBaseUnits += rawAmt
                            }
                            if (itemFrom == cleanTarget) {
                                netBaseUnits -= rawAmt
                            }
                        }
                        if (netBaseUnits > 0) {
                            balanceRaw = netBaseUnits.toString()
                        }
                    }
                } catch (_: Exception) {}
            }
            if (balanceRaw == null) {
                balanceRaw = "0"
            }

            val marketResponse = try { getJson("token/$tokenId/market") } catch (_: Exception) { null }
            val market = detail.optJSONObject("market")
                ?: marketResponse?.optJSONObject("market")
                ?: marketResponse

            fun parseJsonBigDecimal(obj: JSONObject?, vararg keys: String): java.math.BigDecimal? {
                if (obj == null) return null
                for (key in keys) {
                    if (!obj.has(key) || obj.isNull(key)) continue
                    val str = obj.optString(key, "").trim()
                    if (str.isNotBlank() && str != "null") {
                        val parsed = str.toBigDecimalOrNull()
                        if (parsed != null) return parsed
                    }
                    val d = obj.optDouble(key)
                    if (!d.isNaN()) return java.math.BigDecimal.valueOf(d)
                }
                return null
            }

            var priceKas: String? = null
            var priceUsd: String? = null
            var priceSource: String? = null
            var priceUpdatedAt: Long? = null

            val claimedDecimals = if (token.has("claimed_decimals") && !token.isNull("claimed_decimals")) token.optInt("claimed_decimals") else null
            val displayDecimals = if (token.has("display_decimals") && !token.isNull("display_decimals")) token.optInt("display_decimals") else null
            val rawDecimals = if (token.has("decimals") && !token.isNull("decimals")) token.optInt("decimals") else null
            
            val ticker = token.optString("claimed_ticker").ifBlank {
                token.optString("ticker").ifBlank {
                    token.optJSONObject("fields")?.optString("tick", "")?.ifBlank { "KB" } ?: "KB"
                }
            }
            // KB (Kaspa Browser) token on Kaspa Mainnet typically uses 3 decimals, but we respect metadata if present
            val isKb = ticker.equals("KB", ignoreCase = true) || tokenId.contains("b75a6e90f569965bdeec23c460ebd59e0f0445d8aa8f5d4330fe15c66a47b4d8", ignoreCase = true)
            val decimals = (claimedDecimals ?: displayDecimals ?: rawDecimals) ?: if (isKb) 3 else 8
            
            val isVerified = token.optString("status") == "verified"
            val spotNumSompi = parseJsonBigDecimal(market, "spot_num_sompi", "last_quote_sompi", "quote_sompi")
                ?: parseJsonBigDecimal(detail.optJSONObject("market"), "spot_num_sompi", "last_quote_sompi", "quote_sompi")
            val spotDen = parseJsonBigDecimal(market, "spot_den", "last_base_amount", "base_amount")
                ?: parseJsonBigDecimal(detail.optJSONObject("market"), "spot_den", "last_base_amount", "base_amount")

            var currentPriceSompi = if (spotNumSompi != null && spotDen != null && spotDen.signum() > 0) {
                spotNumSompi.multiply(java.math.BigDecimal.TEN.pow(decimals))
                    .divide(spotDen, 18, java.math.RoundingMode.HALF_UP)
            } else null

            if (currentPriceSompi != null) {
                val kas = currentPriceSompi.divide(java.math.BigDecimal("100000000"), 18, java.math.RoundingMode.HALF_UP)
                priceKas = kas.stripTrailingZeros().toPlainString()

                val liveKasUsd = kasUsd?.toBigDecimalOrNull() 
                    ?: java.math.BigDecimal.valueOf(com.example.network.KaspaPriceService.priceState.value.priceUsd).takeIf { it.signum() > 0 }
                    ?: try {
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
                            pJson.optString("kas_usd").toBigDecimalOrNull()
                        } else null
                    } finally {
                        conn.disconnect()
                    }
                } catch (_: Exception) {
                    null
                }
                
                if (liveKasUsd != null && liveKasUsd.signum() > 0) {
                    priceUsd = kas.multiply(liveKasUsd).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                }
                priceSource = "Kascov live spot quote"
                priceUpdatedAt = market?.optLong("last_time_ms")?.takeIf { it > 0 }
                    ?: market?.optLong("updated_at_ms")?.takeIf { it > 0 }
            }

            val change24hBps = parseJsonBigDecimal(market, "change_24h_bps")
                ?: parseJsonBigDecimal(detail.optJSONObject("market"), "change_24h_bps")
            val change24h = change24hBps?.divide(java.math.BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP)
                ?.stripTrailingZeros()?.toPlainString()

            val claimedImg = token.optString("claimed_image")
            val iconImg = token.optString("icon")
            val imageImg = token.optString("image")
            val logoImg = token.optString("logo")
            val metaImg = token.optJSONObject("metadata")?.optString("image") ?: ""
            val resolvedImage = listOf(claimedImg, iconImg, imageImg, logoImg, metaImg)
                .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                ?: if (ticker.equals("KB", ignoreCase = true) || tokenId.contains("b75a6e90f569965bdeec23c460ebd59e0f0445d8aa8f5d4330fe15c66a47b4d8", ignoreCase = true)) {
                    "https://storage.googleapis.com/kaspa-rocket-prod-uploads/7c70c6f4-faa9-4d84-ba81-62553168f1a9.png"
                } else null

            Result.success(Kcc20WalletToken(
                contractId = token.optString("covenant_id", tokenId),
                ticker = ticker,
                name = token.optString("claimed_name").ifBlank { token.optString("name", ticker) },
                balanceRaw = balanceRaw,
                decimals = decimals,
                holderCount = if (token.has("holders")) token.optLong("holders") else null,
                validationStatus = token.optString("status"),
                marketPriceKas = priceKas,
                marketPriceUsd = priceUsd,
                marketPriceSource = priceSource,
                priceUpdatedAtMs = priceUpdatedAt,
                priceChange24hPercent = change24h,
                imageUrl = resolvedImage
            ))
        } catch (error: Throwable) {
            Result.failure(error)
        }
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

        for (baseEndpoint in endpointBases) {
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
    data class DeploymentConformance(
        val tokenStatus: String,
        val layout: String,
        val kcc: Int,
        val review: String,
        val conformant: Boolean?,
        val artifact: String?,
        val templateHash: String,
        val viewTemplateHash: String,
        val dispatch: String,
        val nonconformities: List<String>
    ) {
        val safeForStandardTransfer: Boolean
            get() = tokenStatus == "verified" &&
                layout == "kcc0020-112" &&
                kcc == 20 &&
                review == "reviewed" &&
                conformant == true &&
                artifact != "unpublished" &&
                templateHash.equals(KbKcc20Protocol.TEMPLATE_HASH, true) &&
                viewTemplateHash.equals(KbKcc20Protocol.VIEW_TEMPLATE_HASH, true) &&
                dispatch in setOf("kcc1-tags", "silverc-selector", "custom-tags") &&
                nonconformities.isEmpty()
    }

    suspend fun fetchDeploymentConformance(
        contractId: String
    ): Result<DeploymentConformance> = withContext(Dispatchers.IO) {
        val tokenId = contractId.trim().lowercase()
        if (!tokenId.matches(Regex("[0-9a-f]{64}"))) {
            return@withContext Result.failure(IllegalArgumentException("KCC-20 covenant ID must be 32-byte hex"))
        }
        try {
            val url = URL("https://kascov.io/data/mainnet/token/$tokenId")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw java.io.IOException(
                        "Kascov token conformance endpoint returned HTTP ${connection.responseCode}"
                    )
                }
                val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val token = root.optJSONObject("token") ?: root
                val standard = token.optJSONObject("standard")
                    ?: root.optJSONObject("standard")
                    ?: throw java.io.IOException("Kascov did not publish the KCC-20 standard build verdict")
                val nonconformitiesJson = standard.optJSONArray("nonconformities") ?: JSONArray()
                val nonconformities = (0 until nonconformitiesJson.length()).mapNotNull { index ->
                    nonconformitiesJson.optString(index).takeIf { it.isNotBlank() && it != "null" }
                }
                Result.success(
                    DeploymentConformance(
                        tokenStatus = token.optString("status"),
                        layout = token.optString("layout"),
                        kcc = standard.optInt("kcc", -1),
                        review = standard.optString("review"),
                        conformant = if (standard.has("conformant") && !standard.isNull("conformant")) {
                            standard.optBoolean("conformant")
                        } else null,
                        artifact = standard.optString("artifact").takeIf { it.isNotBlank() && it != "null" },
                        templateHash = standard.optString("template_hash"),
                        viewTemplateHash = standard.optString("view_template_hash"),
                        dispatch = standard.optString("dispatch"),
                        nonconformities = nonconformities
                    )
                )
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun fetchLiveTokenCells(
        contractId: String,
        publicKeyHex: String,
        walletAddress: String? = null
    ): Result<List<KbKcc20StandardAssembler.SpendPiece>> = withContext(Dispatchers.IO) {
        val owner = publicKeyHex.trim().lowercase().removePrefix("0x")
        val tokenId = contractId.trim().lowercase()
        if (!owner.matches(Regex("[0-9a-f]{64}"))) {
            return@withContext Result.failure(IllegalArgumentException("Wallet public key must be 32-byte x-only hex"))
        }
        if (!tokenId.matches(Regex("[0-9a-f]{64}"))) {
            return@withContext Result.failure(IllegalArgumentException("KCC-20 covenant ID must be 32-byte hex"))
        }
        try {
            val url = URL("https://kascov.io/data/mainnet/token/$tokenId/cells")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0 (kascov.io)")
            }
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    throw java.io.IOException("Kascov HTTP ${conn.responseCode} for token cells")
                }
                val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val responseTokenId = root.optString("covenant_id", root.optString("token_id"))
                    .trim().lowercase()
                require(responseTokenId.isBlank() || responseTokenId == tokenId) {
                    "Kascov returned cells for a different covenant"
                }
                val cellsArray = root.optJSONArray("cells")
                    ?: throw java.io.IOException("Kascov response has no proven cells array")
                val pieces = mutableListOf<KbKcc20StandardAssembler.SpendPiece>()
                val seen = mutableSetOf<Pair<String, Int>>()

                for (i in 0 until cellsArray.length()) {
                    val cell = cellsArray.optJSONObject(i)
                        ?: throw java.io.IOException("Kascov returned a malformed token cell at index $i")
                    val ownerScheme = cell.optString("owner_scheme").trim().lowercase()
                    if (ownerScheme != "00") continue
                    val cellOwner = cell.optString(
                        "owner",
                        cell.optString("owner_identifier")
                    ).trim().lowercase().removePrefix("0x")
                    if (cellOwner != owner) continue

                    val outpoint = cell.optString("outpoint").trim().lowercase()
                    val separator = outpoint.lastIndexOf(':')
                    require(separator == 64) { "Kascov returned a malformed token-cell outpoint" }
                    val txid = outpoint.substring(0, separator)
                    require(txid.matches(Regex("[0-9a-f]{64}"))) {
                        "Kascov returned an invalid token-cell transaction ID"
                    }
                    val outputIndex = outpoint.substring(separator + 1).toIntOrNull()
                        ?: throw IllegalArgumentException("Kascov returned an invalid token-cell output index")
                    require(outputIndex >= 0) { "Kascov returned a negative token-cell output index" }
                    require(seen.add(txid to outputIndex)) {
                        "Kascov returned a duplicate token-cell outpoint"
                    }

                    val value = cell.opt("value_sompi")
                        ?: throw IllegalArgumentException("Kascov token cell has no exact value_sompi")
                    val valueSompi = when (value) {
                        is Number -> value.toLong()
                        is String -> value.toLongOrNull()
                            ?: throw IllegalArgumentException("Kascov token cell value_sompi is not an integer")
                        else -> throw IllegalArgumentException("Kascov token cell value_sompi has an invalid type")
                    }
                    require(valueSompi > 0L) { "Kascov token cell has a non-positive KAS value" }

                    val programHex = cell.optString("program").trim().lowercase()
                    require(programHex.isNotEmpty() && programHex.length % 2 == 0 &&
                        programHex.matches(Regex("[0-9a-f]+"))
                    ) { "Kascov did not provide a complete, valid revealed program for a proven token cell" }
                    val redeem = KbKcc20ScriptCodec.hexToBytes(programHex)
                    require(CryptoUtils.blake2b256(redeem).isNotEmpty()) {
                        "Unable to hash the revealed KCC-20 program"
                    }
                    pieces += KbKcc20StandardAssembler.SpendPiece(
                        transactionId = txid,
                        index = outputIndex,
                        valueSompi = valueSompi,
                        redeem = redeem
                    )
                }
                Result.success(pieces)
            } finally {
                conn.disconnect()
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
