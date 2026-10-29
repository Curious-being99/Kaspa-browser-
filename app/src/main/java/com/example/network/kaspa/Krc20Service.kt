package com.example.network.kaspa

import android.content.Context
import com.example.model.Krc20TokenBalance
import com.example.model.Krc20TokenMetadata
import com.example.model.Krc20TokenTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.pow

class Krc20Service(private val context: Context) {

    private val prefs = context.getSharedPreferences("krc20_pinned_tokens_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_PINNED_TICKERS = "pinned_tickers"
        private const val PREF_LOCAL_TRANSACTIONS = "local_token_transactions"
        private const val KASPLEX_BASE_URL = "https://api.kasplex.org/v1/krc20"

        // Well-known Kaspa Mainnet KRC-20 token definitions for instant offline reliability
        val KNOWN_TOKENS = emptyMap<String, Krc20TokenMetadata>()
    }

    /**
     * Get set of currently pinned token tickers.
     */
    fun getPinnedTickers(): Set<String> {
        val saved = prefs.getStringSet(PREF_PINNED_TICKERS, null)
        return saved ?: emptySet()
    }

    /**
     * Pin or unpin a token ticker.
     */
    fun setPinned(tick: String, isPinned: Boolean) {
        val upper = tick.uppercase().trim()
        val current = getPinnedTickers().toMutableSet()
        if (isPinned) {
            current.add(upper)
        } else {
            current.remove(upper)
        }
        prefs.edit().putStringSet(PREF_PINNED_TICKERS, current).apply()
    }

    /**
     * Record a transaction (e.g. from BraidSwap buy/swap or wallet transfer).
     */
    fun recordTransaction(tx: Krc20TokenTransaction) {
        val existing = getLocalTransactions().toMutableList()
        // Deduplicate by txId
        existing.removeAll { it.txId.equals(tx.txId, ignoreCase = true) }
        existing.add(0, tx)
        val array = JSONArray()
        existing.take(100).forEach { item ->
            val obj = JSONObject()
            obj.put("txId", item.txId)
            obj.put("tokenId", item.tokenId)
            obj.put("tick", item.tick)
            obj.put("op", item.op)
            obj.put("amount", item.amount)
            obj.put("amountRaw", item.amountRaw)
            obj.put("fromAddress", item.fromAddress)
            obj.put("toAddress", item.toAddress)
            obj.put("timestamp", item.timestamp)
            obj.put("status", item.status)
            obj.put("feeKas", item.feeKas)
            obj.put("rawJson", item.rawJson)
            array.put(obj)
        }
        prefs.edit().putString(PREF_LOCAL_TRANSACTIONS, array.toString()).apply()
    }

    fun getLocalTransactions(tick: String? = null): List<Krc20TokenTransaction> {
        val jsonString = prefs.getString(PREF_LOCAL_TRANSACTIONS, null) ?: return emptyList()
        val result = mutableListOf<Krc20TokenTransaction>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val itemTick = obj.optString("tick")
                if (tick == null || itemTick.equals(tick, ignoreCase = true)) {
                    result.add(
                        Krc20TokenTransaction(
                            txId = obj.optString("txId"),
                            tokenId = obj.optString("tokenId"),
                            tick = itemTick,
                            op = obj.optString("op", "SWAP"),
                            amount = obj.optDouble("amount", 0.0),
                            amountRaw = obj.optString("amountRaw", "0"),
                            fromAddress = obj.optString("fromAddress"),
                            toAddress = obj.optString("toAddress"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            status = obj.optString("status", "ACCEPTED"),
                            feeKas = obj.optDouble("feeKas", 0.0001),
                            rawJson = obj.optString("rawJson")
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return result
    }

    /**
     * Fetch real token metadata from Kasplex indexer REST API.
     */
    suspend fun fetchTokenMetadata(tick: String): Result<Krc20TokenMetadata> = withContext(Dispatchers.IO) {
        val upperTick = tick.uppercase().trim()
        val known = KNOWN_TOKENS[upperTick]
        try {
            val url = URL("$KASPLEX_BASE_URL/token/$upperTick")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0")
            }
            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val root = JSONObject(responseText)
                val resultArray = root.optJSONArray("result")
                if (resultArray != null && resultArray.length() > 0) {
                    val item = resultArray.getJSONObject(0)
                    val decimals = item.optInt("dec", 8)
                    val metadata = Krc20TokenMetadata(
                        tick = item.optString("tick", upperTick),
                        tokenId = item.optString("hashRev", known?.tokenId ?: ""),
                        protocol = "krc-20",
                        maxSupply = item.optString("max", "0"),
                        mintedSupply = item.optString("minted", "0"),
                        decimals = decimals,
                        limitPerMint = item.optString("lim", "0"),
                        premint = item.optString("pre", "0"),
                        deployer = item.optString("to", ""),
                        state = item.optString("state", "deployed"),
                        holderTotal = item.optLong("holderTotal", known?.holderTotal ?: 0L),
                        transferTotal = item.optLong("transferTotal", known?.transferTotal ?: 0L),
                        rawMetadataJson = item.toString(2)
                    )
                    return@withContext Result.success(metadata)
                }
            }
        } catch (e: Exception) {
            // In case of transient network timeout, return known cached metadata if present
            if (known != null) {
                return@withContext Result.success(known)
            }
        }

        if (known != null) {
            return@withContext Result.success(known)
        }

        Result.failure(Exception("Could not retrieve KRC-20 metadata for $upperTick"))
    }

    /**
     * Fetch user balance for a specific token from Kasplex indexer.
     */
    suspend fun fetchTokenBalance(address: String, tick: String, decimals: Int = 8): Pair<Double, String> = withContext(Dispatchers.IO) {
        val upperTick = tick.uppercase().trim()
        if (address.isBlank()) return@withContext Pair(0.0, "0")
        try {
            val url = URL("$KASPLEX_BASE_URL/address/$address/token/$upperTick")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0")
            }
            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val root = JSONObject(responseText)
                val resultArray = root.optJSONArray("result")
                if (resultArray != null && resultArray.length() > 0) {
                    val item = resultArray.getJSONObject(0)
                    val rawBalance = item.optString("balance", "0")
                    val dec = item.optInt("dec", decimals)
                    val doubleBal = rawBalance.toDoubleOrNull()?.div(10.0.pow(dec)) ?: 0.0
                    return@withContext Pair(doubleBal, rawBalance)
                }
            }
        } catch (_: Exception) {}
        Pair(0.0, "0")
    }

    /**
     * Fetch on-chain KRC-20 transaction history for an address and token.
     */
    suspend fun fetchTokenTransactions(address: String, tick: String, tokenId: String): List<Krc20TokenTransaction> = withContext(Dispatchers.IO) {
        val upperTick = tick.uppercase().trim()
        val combined = mutableListOf<Krc20TokenTransaction>()

        // 1. Add locally recorded transactions (e.g. from BraidSwap buys/swaps)
        combined.addAll(getLocalTransactions(upperTick))

        // 2. Fetch live on-chain operations from Kasplex indexer
        if (address.isNotBlank()) {
            try {
                val url = URL("$KASPLEX_BASE_URL/oplist?address=$address&tick=$upperTick")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 7000
                    readTimeout = 7000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0")
                }
                if (conn.responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                    val root = JSONObject(responseText)
                    val resultArray = root.optJSONArray("result")
                    if (resultArray != null) {
                        for (i in 0 until resultArray.length()) {
                            val item = resultArray.getJSONObject(i)
                            val txId = item.optString("hashRev", "")
                            val op = item.optString("op", "transfer").uppercase()
                            val rawAmt = item.optString("amt", "0")
                            val from = item.optString("from", "")
                            val to = item.optString("to", "")
                            val timestamp = item.optLong("mtsAdd", System.currentTimeMillis())
                            val opAccept = item.optString("opAccept", "1")
                            val status = if (opAccept == "1") "ACCEPTED" else "REJECTED"
                            val amtDouble = rawAmt.toDoubleOrNull()?.div(10.0.pow(8)) ?: 0.0

                            val txItem = Krc20TokenTransaction(
                                txId = txId,
                                tokenId = tokenId,
                                tick = upperTick,
                                op = op,
                                amount = amtDouble,
                                amountRaw = rawAmt,
                                fromAddress = from,
                                toAddress = to,
                                timestamp = timestamp,
                                status = status,
                                feeKas = 0.0001,
                                rawJson = item.toString(2)
                            )
                            // Avoid duplicates with local transactions
                            if (combined.none { it.txId.equals(txId, ignoreCase = true) }) {
                                combined.add(txItem)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Sort latest first
        combined.sortedByDescending { it.timestamp }
    }

    /**
     * Get complete token balance representation with metadata and transactions.
     */
    suspend fun getTokenBalance(address: String, tick: String, isPinned: Boolean): Krc20TokenBalance {
        val upper = tick.uppercase().trim()
        val meta = fetchTokenMetadata(upper).getOrNull() ?: KNOWN_TOKENS[upper] ?: Krc20TokenMetadata(
            tick = upper,
            tokenId = "",
            protocol = "krc-20"
        )
        val (balance, balanceRaw) = fetchTokenBalance(address, upper, meta.decimals)
        val history = fetchTokenTransactions(address, upper, meta.tokenId)

        return Krc20TokenBalance(
            tick = upper,
            balance = balance,
            balanceRaw = balanceRaw,
            isPinned = isPinned,
            metadata = meta,
            recentTransactions = history
        )
    }
}
