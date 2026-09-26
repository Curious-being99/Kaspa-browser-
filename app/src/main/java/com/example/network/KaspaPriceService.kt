package com.example.network

import android.util.Log
import com.example.model.KaspaPriceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service to retrieve 100% real-time, authentic Kaspa (KAS) market prices,
 * real on-chain market cap, verified circulating supply, real exchange volume,
 * and genuine historical candlestick/sparkline data.
 */
object KaspaPriceService {
    private const val TAG = "KaspaPriceService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val _priceState = MutableStateFlow(KaspaPriceInfo(isLoading = true))
    val priceState: StateFlow<KaspaPriceInfo> = _priceState.asStateFlow()

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var autoRefreshJob: Job? = null

    init {
        refreshPrice("24H")
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = serviceScope.launch {
            while (isActive) {
                delay(30_000) // Refresh every 30 seconds for real-time accuracy
                fetchRealtimeMarketData(_priceState.value.selectedTimeframe)
            }
        }
    }

    /**
     * Checks if a user's search query is asking for Kaspa price / chart.
     */
    fun isKaspaPriceQuery(query: String?): Boolean {
        if (query.isNullOrBlank()) return false
        val clean = query.trim().lowercase()

        val triggers = listOf(
            "kaspa price", "kas price", "kaspa chart", "kas chart",
            "price of kaspa", "kaspa value", "kaspa rate", "kaspa usd",
            "kas usd", "kasusd", "\$kas", "kaspa live price",
            "kaspa coin price", "kaspa market", "kaspa to usd", "kas/usd",
            "kaspa token price", "how much is kaspa", "kaspa ticker", "kas marketcap"
        )

        if (triggers.any { clean == it || clean.startsWith(it) || clean.endsWith(it) || clean.contains(it) }) {
            return true
        }

        val hasKaspa = clean.contains("kaspa") || clean.split(" ").contains("kas") || clean.startsWith("kas ") || clean.endsWith(" kas")
        val hasPriceIntent = clean.contains("price") || clean.contains("chart") || clean.contains("rate") ||
                clean.contains("value") || clean.contains("market") || clean.contains("usd") || clean.contains("ticker") ||
                clean.contains("worth") || clean.contains("cap")

        return hasKaspa && hasPriceIntent
    }

    /**
     * Refreshes Kaspa price and real chart points for the specified timeframe.
     */
    fun refreshPrice(timeframe: String = _priceState.value.selectedTimeframe) {
        serviceScope.launch {
            _priceState.value = _priceState.value.copy(isLoading = true, selectedTimeframe = timeframe)
            fetchRealtimeMarketData(timeframe)
        }
    }

    private suspend fun fetchRealtimeMarketData(timeframe: String) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        var currentPrice = _priceState.value.priceUsd
        var change24h = _priceState.value.change24hPercent
        var high24h = _priceState.value.high24hUsd
        var low24h = _priceState.value.low24hUsd
        var mcap = _priceState.value.marketCapUsd
        var vol = _priceState.value.volume24hUsd
        var circulating = _priceState.value.circulatingSupply
        var realChartPoints: List<Float> = _priceState.value.chartPoints
        var isLive = false

        // 1. Fetch Real-time Cap & Change from CoinPaprika Global Index
        try {
            val paprikaUrl = "https://api.coinpaprika.com/v1/tickers/kas-kaspa"
            val req = Request.Builder().url(paprikaUrl).header("User-Agent", "KaspaBrowser/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val quotes = json.optJSONObject("quotes")?.optJSONObject("USD")
                        if (quotes != null) {
                            val p = quotes.optDouble("price", 0.0)
                            val chg = quotes.optDouble("percent_change_24h", 0.0)
                            val cap = quotes.optDouble("market_cap", 0.0)
                            val v = quotes.optDouble("volume_24h", 0.0)

                            if (p > 0) currentPrice = p
                            if (chg != 0.0) change24h = chg
                            if (cap > 0) mcap = cap
                            if (v > 0) vol = v
                            isLive = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "CoinPaprika notice: ${e.message}")
        }

        // 2. Fetch Gate.io Spot Ticker (Real-Time Price & 24h Change %)
        try {
            val gateUrl = "https://api.gateio.ws/api/v4/spot/tickers?currency_pair=KAS_USDT"
            val req = Request.Builder().url(gateUrl).header("User-Agent", "KaspaBrowser/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val arr = JSONArray(body)
                        if (arr.length() > 0) {
                            val item = arr.getJSONObject(0)
                            val last = item.optString("last", "").toDoubleOrNull() ?: 0.0
                            val chg = item.optString("change_percentage", "").toDoubleOrNull() ?: 0.0
                            val h = item.optString("high_24h", "").toDoubleOrNull() ?: 0.0
                            val l = item.optString("low_24h", "").toDoubleOrNull() ?: 0.0
                            val quoteVol = item.optString("quote_volume", "").toDoubleOrNull() ?: 0.0
                            if (last > 0) {
                                currentPrice = last
                                change24h = chg
                                if (h > 0) high24h = h
                                if (l > 0) low24h = l
                                if (quoteVol > 0) vol = quoteVol
                                isLive = true
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Gate.io notice: ${e.message}")
        }

        // 2. Fetch MEXC Spot Ticker
        try {
            val mexcTickerUrl = "https://api.mexc.com/api/v3/ticker/24hr?symbol=KASUSDT"
            val req = Request.Builder().url(mexcTickerUrl).header("User-Agent", "KaspaBrowser/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val last = json.optDouble("lastPrice", 0.0)
                        val high = json.optDouble("highPrice", 0.0)
                        val low = json.optDouble("lowPrice", 0.0)
                        val priceChangePct = json.optDouble("priceChangePercent", 0.0)
                        val quoteVol = json.optDouble("quoteVolume", 0.0)

                        if (last > 0) {
                            currentPrice = last
                            if (high > 0) high24h = high
                            if (low > 0) low24h = low
                            if (priceChangePct != 0.0) change24h = priceChangePct
                            if (quoteVol > 0) vol = quoteVol
                            isLive = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "MEXC Ticker notice: ${e.message}")
        }

        // 3. Fetch Bybit Spot Ticker
        try {
            val bybitUrl = "https://api.bybit.com/v5/market/tickers?category=spot&symbol=KASUSDT"
            val req = Request.Builder().url(bybitUrl).header("User-Agent", "KaspaBrowser/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val result = json.optJSONObject("result")
                        val list = result?.optJSONArray("list")
                        if (list != null && list.length() > 0) {
                            val ticker = list.getJSONObject(0)
                            val last = ticker.optDouble("lastPrice", 0.0)
                            val chgPcnt = ticker.optDouble("price24hPcnt", 0.0) * 100.0
                            val h = ticker.optDouble("highPrice24h", 0.0)
                            val l = ticker.optDouble("lowPrice24h", 0.0)
                            val turnover = ticker.optDouble("turnover24h", 0.0)

                            if (last > 0) {
                                if (currentPrice <= 0) currentPrice = last
                                if (change24h == 0.0 && chgPcnt != 0.0) change24h = chgPcnt
                                if (high24h <= 0 && h > 0) high24h = h
                                if (low24h <= 0 && l > 0) low24h = l
                                if (vol <= 0 && turnover > 0) vol = turnover
                                isLive = true
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Bybit notice: ${e.message}")
        }

        // 4. Fetch CoinCap Global Market Rank & Cap
        try {
            val ccUrl = "https://api.coincap.io/v2/assets/kaspa"
            val req = Request.Builder().url(ccUrl).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val data = json.optJSONObject("data")
                        if (data != null) {
                            val ccMcap = data.optDouble("marketCapUsd", 0.0)
                            if (ccMcap > 0) mcap = ccMcap
                            val ccVol = data.optDouble("volumeUsd24Hr", 0.0)
                            if (ccVol > 0 && vol <= 0) vol = ccVol
                            val ccPrice = data.optDouble("priceUsd", 0.0)
                            if (currentPrice <= 0 && ccPrice > 0) currentPrice = ccPrice
                            val ccChg = data.optDouble("changePercent24Hr", 0.0)
                            if (change24h == 0.0 && ccChg != 0.0) change24h = ccChg
                            val ccSupply = data.optDouble("supply", 0.0)
                            if (circulating <= 0 && ccSupply > 0) circulating = ccSupply
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "CoinCap API notice: ${e.message}")
        }

        // 5. Fetch CoinGecko Rank & Data
        try {
            val cgUrl = "https://api.coingecko.com/api/v3/coins/kaspa?localization=false&tickers=false&market_data=true&community_data=false&developer_data=false&sparkline=true"
            val request = Request.Builder()
                .url(cgUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "KaspaBrowser/1.0")
                .build()

            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val marketData = json.optJSONObject("market_data")
                        if (marketData != null) {
                            if (currentPrice <= 0) {
                                val currentPriceObj = marketData.optJSONObject("current_price")
                                currentPrice = currentPriceObj?.optDouble("usd", currentPrice) ?: currentPrice
                            }
                            val cgChange = marketData.optDouble("price_change_percentage_24h", 0.0)
                            if (cgChange != 0.0 && change24h == 0.0) {
                                change24h = cgChange
                            }
                            if (high24h <= 0) {
                                high24h = marketData.optJSONObject("high_24h")?.optDouble("usd", high24h) ?: high24h
                            }
                            if (low24h <= 0) {
                                low24h = marketData.optJSONObject("low_24h")?.optDouble("usd", low24h) ?: low24h
                            }
                            val cgMcap = marketData.optJSONObject("market_cap")?.optDouble("usd", 0.0) ?: 0.0
                            if (cgMcap > 0) mcap = cgMcap
                            val cgVol = marketData.optJSONObject("total_volume")?.optDouble("usd", 0.0) ?: 0.0
                            if (cgVol > 0) vol = cgVol
                            val cgCirc = marketData.optDouble("circulating_supply", 0.0)
                            if (cgCirc > 0) circulating = cgCirc

                            if (realChartPoints.isEmpty()) {
                                val sparklineObj = marketData.optJSONObject("sparkline_7d")
                                val sparklineArr = sparklineObj?.optJSONArray("price")
                                if (sparklineArr != null && sparklineArr.length() > 0) {
                                    val points = mutableListOf<Float>()
                                    val step = when (timeframe) {
                                        "24H" -> (sparklineArr.length() / 24).coerceAtLeast(1)
                                        "7D" -> 1
                                        else -> 1
                                    }
                                    val startIndex = if (timeframe == "24H") (sparklineArr.length() - 24).coerceAtLeast(0) else 0
                                    for (i in startIndex until sparklineArr.length() step step) {
                                        points.add(sparklineArr.optDouble(i).toFloat())
                                    }
                                    if (points.isNotEmpty()) realChartPoints = points
                                }
                            }
                            isLive = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "CoinGecko API notice: ${e.message}")
        }

        // 6. Fetch On-Chain Circulating Supply & Market Cap from official api.kaspa.org
        try {
            val supplyUrl = "https://api.kaspa.org/info/coinsupply"
            val req = Request.Builder().url(supplyUrl).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val circ = json.optDouble("circulating", 0.0)
                        if (circ > 0) {
                            circulating = circ
                            if (currentPrice > 0) {
                                mcap = circulating * currentPrice
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Kaspa supply API notice: ${e.message}")
        }

        // 7. Fetch Real Kline Candlestick Data from MEXC
        try {
            val (interval, limit) = when (timeframe) {
                "24H" -> Pair("1h", 24)
                "7D" -> Pair("4h", 42)
                "30D" -> Pair("1d", 30)
                "1Y" -> Pair("1w", 52)
                else -> Pair("1h", 24)
            }
            val klinesUrl = "https://api.mexc.com/api/v3/klines?symbol=KASUSDT&interval=$interval&limit=$limit"
            val req = Request.Builder().url(klinesUrl).header("User-Agent", "KaspaBrowser/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val array = JSONArray(body)
                        if (array.length() > 0) {
                            val points = mutableListOf<Float>()
                            for (i in 0 until array.length()) {
                                val kline = array.getJSONArray(i)
                                val closePrice = kline.optDouble(4, 0.0).toFloat()
                                if (closePrice > 0f) points.add(closePrice)
                            }
                            if (points.isNotEmpty()) {
                                realChartPoints = points
                                isLive = true
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "MEXC Klines notice: ${e.message}")
        }

        // If market cap is calculated from circulating supply * current price
        if (mcap <= 0 && circulating > 0 && currentPrice > 0) {
            mcap = circulating * currentPrice
        }

        _priceState.value = KaspaPriceInfo(
            priceUsd = currentPrice,
            change24hPercent = change24h,
            high24hUsd = high24h,
            low24hUsd = low24h,
            marketCapUsd = mcap,
            volume24hUsd = vol,
            circulatingSupply = circulating,
            selectedTimeframe = timeframe,
            chartPoints = realChartPoints,
            lastUpdatedTimestamp = System.currentTimeMillis(),
            isLoading = false,
            isLive = isLive && currentPrice > 0,
            errorMessage = null
        )
    }
}
