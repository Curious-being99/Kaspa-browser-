package com.example.network.kaspa

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.Locale

/**
 * KaspaRocket SDK Core Math & Precision Handlers.
 *
 * Implements lossless sompi & raw units conversions:
 * - Kaspa amounts: u64 sompi (1 KAS = 100,000,000 sompi). Truncates below 1 sompi.
 * - Token amounts: u64 raw units (1 token = 1,000 raw units). Truncates below 1 raw unit.
 * - Prices: Sompi per raw unit, multiplied by RAW_PER_TOKEN to get sompi per token.
 */
object KaspaRocketMath {
    const val RAW_PER_TOKEN = 1000L
    const val SOMPI_PER_KAS = 100_000_000L

    /**
     * Converts a Kaspa decimal string (e.g. "1.5", "0.00012") to sompi integer.
     * Truncates strictly below one sompi rather than rounding.
     */
    fun kasToSompi(kasStr: String): Long {
        val clean = kasStr.trim()
        if (clean.isBlank()) return 0L
        val parts = clean.split(".")
        val wholePart = parts[0].toLongOrNull() ?: 0L
        val decPart = if (parts.size > 1) parts[1] else ""
        // Truncate to 8 decimal places (sompi precision)
        val decPadded = (decPart + "00000000").take(8)
        val sompiDec = decPadded.toLongOrNull() ?: 0L
        return (wholePart * SOMPI_PER_KAS) + sompiDec
    }

    /**
     * Formats sompi integer losslessly to decimal KAS string (e.g. 150_000_000L -> "1.5").
     */
    fun formatKas(sompi: Long): String {
        val isNegative = sompi < 0
        val absSompi = kotlin.math.abs(sompi)
        val whole = absSompi / SOMPI_PER_KAS
        val rem = absSompi % SOMPI_PER_KAS
        if (rem == 0L) {
            return if (isNegative) "-$whole" else "$whole"
        }
        val remStr = String.format(Locale.US, "%08d", rem).trimEnd('0')
        val res = "$whole.$remStr"
        return if (isNegative) "-$res" else res
    }

    /**
     * Converts a token display string (e.g. "1.234") to integer raw units (1234).
     * Truncates strictly below one raw unit (e.g. "0.0009" -> 0).
     */
    fun tokensToRaw(tokenStr: String): Long {
        val clean = tokenStr.trim()
        if (clean.isBlank()) return 0L
        val parts = clean.split(".")
        val wholePart = parts[0].toLongOrNull() ?: 0L
        val decPart = if (parts.size > 1) parts[1] else ""
        // 1 token = 1000 raw units (3 decimal places)
        val decPadded = (decPart + "000").take(3)
        val rawDec = decPadded.toLongOrNull() ?: 0L
        return (wholePart * RAW_PER_TOKEN) + rawDec
    }

    /**
     * Formats integer raw units to human-readable token string.
     * e.g. 5_000_000 -> "5000", 1 -> "0.001", 1234 -> "1.234"
     */
    fun formatTokens(rawUnits: Long): String {
        val isNegative = rawUnits < 0
        val absRaw = kotlin.math.abs(rawUnits)
        val whole = absRaw / RAW_PER_TOKEN
        val rem = absRaw % RAW_PER_TOKEN
        if (rem == 0L) {
            return if (isNegative) "-$whole" else "$whole"
        }
        val remStr = String.format(Locale.US, "%03d", rem).trimEnd('0')
        val res = "$whole.$remStr"
        return if (isNegative) "-$res" else res
    }

    /**
     * Price scaling: Sompi per raw unit multiplied by RAW_PER_TOKEN gives sompi per 1 whole token.
     * e.g. tokenPriceSompi(23_000) -> 23_000 * 1000 = 23_000_000 sompi (0.23 KAS per token).
     */
    fun tokenPriceSompi(sompiPerRawUnit: Long): Long {
        return sompiPerRawUnit * RAW_PER_TOKEN
    }
}

class KaspaRocketClient(
    private val apiKey: String? = null,
    val network: String = "mainnet",
    private val baseUrl: String = "https://kasparocket.fun"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * 0 — Resolve the pool covenant ID for a given token.
     * Every trading call requires lpCovenantId.
     */
    suspend fun getPoolCovenant(tokenId: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/api/poolCovenant?tokenId=$tokenId"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use null
                    val json = JSONObject(body)
                    val lpId = json.optString("lp_amm_covenant_id", "")
                    if (lpId.isNotBlank()) return@use lpId
                    val altId = json.optString("covenant_id", "")
                    if (altId.isNotBlank()) return@use altId
                }
                null
            }
        } catch (e: Exception) {
            Log.e("KaspaRocketClient", "Error fetching pool covenant: ${e.message}")
            null
        }
    }

    /**
     * 1 — Get Quote (commits nothing).
     *
     * In BUY direction:
     *   kasBudgetSompi is provided.
     *   Returns amountTokens (raw) & amountTokensDisplay.
     *
     * In SELL direction:
     *   tokensRaw is provided.
     *   Returns netKasSompi (user pockets, fees deducted, can be negative if uneconomic).
     */
    suspend fun getQuote(
        tokenId: String,
        lpCovenantId: String,
        direction: String, // "buy" or "sell"
        kasBudgetSompi: Long? = null,
        tokensRaw: Long? = null
    ): KaspaRocketQuote? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/pro-api/quote"
            val jsonBody = JSONObject().apply {
                put("tokenId", tokenId)
                put("lpCovenantId", lpCovenantId)
                put("direction", direction)
                if (kasBudgetSompi != null && kasBudgetSompi > 0) {
                    put("kasBudgetSompi", kasBudgetSompi.toString())
                }
                if (tokensRaw != null && tokensRaw > 0) {
                    put("tokenAmountRaw", tokensRaw.toString())
                    put("tokensRaw", tokensRaw.toString())
                    put("amountTokens", tokensRaw.toString())
                }
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .apply {
                    apiKey?.let { addHeader("Authorization", "Bearer $it") }
                }
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use null
                    val json = JSONObject(body)
                    
                    val amountTokensRaw = json.optLong("amountTokens", 0L)
                    val serverDisplay = json.optString("amountTokensDisplay", "")
                    val amountTokensDisplay = if (serverDisplay.isNotBlank()) {
                        serverDisplay
                    } else {
                        KaspaRocketMath.formatTokens(amountTokensRaw)
                    }

                    val feeObj = json.optJSONObject("fee")
                    val feeTotalSompi = feeObj?.optLong("total", 0L)
                        ?: json.optLong("feeSompi", json.optLong("fee_total", 0L))

                    val uneconomic = json.optBoolean("uneconomic", false)
                    val netKasSompi = json.optLong("netKasSompi", 0L)
                    val pricePerRaw = json.optLong("priceSompiPerRaw", 0L)

                    return@use KaspaRocketQuote(
                        amountTokensRaw = amountTokensRaw,
                        amountTokensDisplay = amountTokensDisplay,
                        feeTotalSompi = feeTotalSompi,
                        uneconomic = uneconomic,
                        netKasSompi = netKasSompi,
                        priceSompiPerRaw = pricePerRaw
                    )
                }
                null
            }
        } catch (e: Exception) {
            Log.e("KaspaRocketClient", "Error fetching quote: ${e.message}")
            null
        }
    }

    /**
     * 2 — Preview the route (market or limit).
     */
    suspend fun getRoute(
        tokenId: String,
        lpCovenantId: String,
        address: String,
        side: String, // "buy" or "sell"
        budgetSompi: Long,
        tokensRaw: Long? = null
    ): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/pro-api/route"
            val jsonBody = JSONObject().apply {
                put("tokenId", tokenId)
                put("lpCovenantId", lpCovenantId)
                put("address", address)
                put("side", side)
                put("kind", "market")
                if (budgetSompi > 0L) put("budgetSompi", budgetSompi.toString())
                if (tokensRaw != null && tokensRaw > 0L) {
                    put("tokensRaw", tokensRaw.toString())
                    put("tokenAmountRaw", tokensRaw.toString())
                }
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .apply {
                    apiKey?.let { addHeader("Authorization", "Bearer $it") }
                }
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use null
                    return@use JSONObject(body)
                }
                null
            }
        } catch (e: Exception) {
            Log.e("KaspaRocketClient", "Error fetching route: ${e.message}")
            null
        }
    }

    /**
     * 3 — Plan Trade. Carrying the preview's commitment.
     */
    suspend fun planTrade(
        tokenId: String,
        lpCovenantId: String,
        address: String,
        side: String,
        budgetSompi: Long,
        expected: String,
        tokensRaw: Long? = null
    ): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/pro-api/plan-trade"
            val jsonBody = JSONObject().apply {
                put("tokenId", tokenId)
                put("lpCovenantId", lpCovenantId)
                put("address", address)
                put("side", side)
                put("kind", "market")
                if (budgetSompi > 0L) put("budgetSompi", budgetSompi.toString())
                if (tokensRaw != null && tokensRaw > 0L) {
                    put("tokensRaw", tokensRaw.toString())
                    put("tokenAmountRaw", tokensRaw.toString())
                }
                put("expected", expected)
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .apply {
                    apiKey?.let { addHeader("Authorization", "Bearer $it") }
                }
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use null
                    return@use JSONObject(body)
                }
                null
            }
        } catch (e: Exception) {
            Log.e("KaspaRocketClient", "Error planning trade: ${e.message}")
            null
        }
    }

    /**
     * Submit already-signed KaspaRocket plan transactions in their original order.
     * The caller must sign and validate every transaction before calling this method.
     */
    suspend fun submitSignedTransactions(
        transactions: List<JSONObject>,
        orderRow: JSONObject? = null
    ): JSONObject? = withContext(Dispatchers.IO) {
        require(transactions.isNotEmpty()) { "No signed transactions to submit" }
        try {
            val payload = JSONObject().apply {
                val items = org.json.JSONArray()
                transactions.forEach { items.put(JSONObject(it.toString())) }
                put("transactions", items)
                if (orderRow != null) {
                    put("order_row", JSONObject(orderRow.toString()))
                    put("orderRow", JSONObject(orderRow.toString()))
                }
            }
            val request = Request.Builder()
                .url("$baseUrl/pro-api/submit")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .apply { apiKey?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) {
                    Log.e("KaspaRocketClient", "Trade submission failed with HTTP ${response.code}")
                    null
                } else {
                    JSONObject(body)
                }
            }
        } catch (e: Exception) {
            Log.e("KaspaRocketClient", "Trade submission failed: ${e.message}")
            null
        }
    }

    /**
     * Fetch Token Metadata and Logo from KaspaRocket catalogue:
     * GET /api/tokens/:tokenId
     */
    suspend fun getTokenDetail(tokenId: String): KaspaRocketTokenDetail? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/api/tokens/$tokenId"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use null
                    val json = JSONObject(body)
                    return@use KaspaRocketTokenDetail(
                        tokenId = json.optString("token_id", tokenId),
                        name = json.optString("name", json.optString("token_name", "")),
                        ticker = json.optString("ticker", ""),
                        description = json.optString("description", ""),
                        imageUrl = json.optString("image", json.optString("image_url", "")),
                        creator = json.optString("creator", ""),
                        bondingCurveProgress = json.optDouble("bondingCurveProgress", 0.0),
                        twitter = json.optJSONObject("socials")?.optString("twitter", ""),
                        telegram = json.optJSONObject("socials")?.optString("telegram", ""),
                        website = json.optJSONObject("socials")?.optString("website", "")
                    )
                }
                null
            }
        } catch (e: Exception) {
            Log.e("KaspaRocketClient", "Error fetching token detail: ${e.message}")
            null
        }
    }
}

data class KaspaRocketTokenDetail(
    val tokenId: String,
    val name: String,
    val ticker: String,
    val description: String,
    val imageUrl: String,
    val creator: String,
    val bondingCurveProgress: Double = 0.0,
    val twitter: String? = null,
    val telegram: String? = null,
    val website: String? = null
)

data class KaspaRocketQuote(
    val amountTokensRaw: Long,
    val amountTokensDisplay: String,
    val feeTotalSompi: Long,
    val uneconomic: Boolean,
    val netKasSompi: Long,
    val priceSompiPerRaw: Long = 0L
) {
    /**
     * Calculated price per whole token in Sompi.
     */
    val pricePerTokenSompi: Long
        get() = if (priceSompiPerRaw > 0) {
            KaspaRocketMath.tokenPriceSompi(priceSompiPerRaw)
        } else if (amountTokensRaw > 0 && netKasSompi > 0) {
            (netKasSompi * KaspaRocketMath.RAW_PER_TOKEN) / amountTokensRaw
        } else {
            0L
        }
}
