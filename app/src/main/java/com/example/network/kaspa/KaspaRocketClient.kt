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
import java.math.BigDecimal
import java.math.RoundingMode

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
        if (!clean.matches(Regex("^\\d+(?:\\.\\d{0,8})?$"))) return 0L
        return try {
            BigDecimal(clean)
                .movePointRight(8)
                .setScale(0, RoundingMode.DOWN)
                .longValueExact()
        } catch (_: ArithmeticException) {
            0L
        } catch (_: NumberFormatException) {
            0L
        }
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
        if (!clean.matches(Regex("^\\d+(?:\\.\\d{0,3})?$"))) return 0L
        return try {
            BigDecimal(clean)
                .movePointRight(3)
                .setScale(0, RoundingMode.DOWN)
                .longValueExact()
        } catch (_: ArithmeticException) {
            0L
        } catch (_: NumberFormatException) {
            0L
        }
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

    private fun isValidId(value: String): Boolean =
        value.matches(Regex("^[0-9a-fA-F]{64}$"))

    private fun isValidSide(side: String): Boolean = side == "buy" || side == "sell"

    /**
     * Resolve only the protocol's canonical LP AMM covenant ID.
     * Missing or malformed IDs fail closed; a generic covenant_id is not a pool proof.
     */
    suspend fun getPoolCovenant(tokenId: String): String? = withContext(Dispatchers.IO) {
        if (!isValidId(tokenId)) return@withContext null
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
                    if (isValidId(lpId)) return@use lpId.lowercase(Locale.ROOT)
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
        direction: String,
        kasBudgetSompi: Long? = null,
        tokensRaw: Long? = null
    ): KaspaRocketQuote? = withContext(Dispatchers.IO) {
        if (!isValidId(tokenId) || !isValidId(lpCovenantId) || !isValidSide(direction)) return@withContext null
        if (direction == "buy" && (kasBudgetSompi == null || kasBudgetSompi <= 0L)) return@withContext null
        if (direction == "sell" && (tokensRaw == null || tokensRaw <= 0L)) return@withContext null

        try {
            val jsonBody = JSONObject().apply {
                put("tokenId", tokenId)
                put("lpCovenantId", lpCovenantId)
                put("direction", direction)
                if (direction == "buy") {
                    put("kasBudgetSompi", kasBudgetSompi.toString())
                } else {
                    put("tokenAmountRaw", tokensRaw.toString())
                    put("tokensRaw", tokensRaw.toString())
                    put("amountTokens", tokensRaw.toString())
                }
            }

            val request = Request.Builder()
                .url("$baseUrl/pro-api/quote")
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .apply { apiKey?.let { addHeader("Authorization", "Bearer $it") } }
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) {
                    Log.w("KaspaRocketClient", "Quote request failed: HTTP ${response.code}")
                    return@use null
                }

                val root = JSONObject(body)
                val json = root.optJSONObject("quote") ?: root
                val amountTokensFromResponse = json.optLongValue("amountTokens")
                    .takeIf { it > 0L }
                    ?: json.optLongValue("amountTokensRaw")
                val amountTokensRaw = if (direction == "sell") {
                    tokensRaw ?: amountTokensFromResponse
                } else {
                    amountTokensFromResponse
                }
                // Raw token units are for arithmetic only. The display string must
                // represent the same quantity using the protocol's fixed 1,000 raw/token scale.
                // Never show an unvalidated server string that could disagree with the raw amount.
                val canonicalDisplay = KaspaRocketMath.formatTokens(amountTokensRaw)
                val serverDisplay = json.optString("amountTokensDisplay", "")
                val amountTokensDisplay = if (
                    direction == "buy" &&
                    serverDisplay.isNotBlank() &&
                    serverDisplay == canonicalDisplay
                ) {
                    serverDisplay
                } else {
                    canonicalDisplay
                }

                val feeObj = json.optJSONObject("fee")
                val feeTotalSompi = feeObj?.optLongValue("total")
                    ?: json.optLongValue("feeSompi")
                    ?: json.optLongValue("fee_total")
                    ?: 0L
                val uneconomic = json.optBoolean("uneconomic", false)
                val netKasSompi = json.optLongValue("netKasSompi")
                    ?: json.optLongValue("net_kas_sompi")
                    ?: 0L
                val pricePerRaw = json.optLongValue("priceSompiPerRaw")
                    ?: json.optLongValue("price_sompi_per_raw")
                    ?: 0L

                if (direction == "buy" && amountTokensRaw <= 0L) {
                    Log.w("KaspaRocketClient", "Quote response contained no positive token output")
                    return@use null
                }
                if (direction == "sell" && netKasSompi <= 0L && !uneconomic) {
                    Log.w("KaspaRocketClient", "Sell quote contained no positive net proceeds")
                    return@use null
                }

                KaspaRocketQuote(
                    amountTokensRaw = amountTokensRaw,
                    amountTokensDisplay = amountTokensDisplay,
                    feeTotalSompi = feeTotalSompi,
                    uneconomic = uneconomic,
                    netKasSompi = netKasSompi,
                    priceSompiPerRaw = pricePerRaw
                )
            }
        } catch (e: Exception) {
            Log.e("KaspaRocketClient", "Error fetching quote: ${e.message}")
            null
        }
    }

    private fun JSONObject.optLongValue(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        val value = opt(key) ?: return null
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
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
        budgetSompi: Long
    ): JSONObject? = withContext(Dispatchers.IO) {
        if (!isValidId(tokenId) || !isValidId(lpCovenantId) ||
            address.isBlank() || !isValidSide(side) || budgetSompi <= 0L
        ) return@withContext null
        try {
            val url = "$baseUrl/pro-api/route"
            val jsonBody = JSONObject().apply {
                put("tokenId", tokenId)
                put("lpCovenantId", lpCovenantId)
                put("address", address)
                put("side", side)
                put("kind", "market")
                put("budgetSompi", budgetSompi.toString())
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
        expected: String
    ): JSONObject? = withContext(Dispatchers.IO) {
        if (!isValidId(tokenId) || !isValidId(lpCovenantId) ||
            address.isBlank() || !isValidSide(side) || budgetSompi <= 0L || expected.isBlank()
        ) return@withContext null
        try {
            val url = "$baseUrl/pro-api/plan-trade"
            val jsonBody = JSONObject().apply {
                put("tokenId", tokenId)
                put("lpCovenantId", lpCovenantId)
                put("address", address)
                put("side", side)
                put("kind", "market")
                put("budgetSompi", budgetSompi.toString())
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
     * Fetch Token Metadata and Logo from KaspaRocket catalogue:
     * GET /api/tokens/:tokenId
     */
    suspend fun getTokenDetail(tokenId: String): KaspaRocketTokenDetail? = withContext(Dispatchers.IO) {
        if (!isValidId(tokenId)) return@withContext null
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
