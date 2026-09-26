package com.example.model

/**
 * Data model representing real-time, authentic Kaspa market price, metrics, and chart points.
 */
data class KaspaPriceInfo(
    val priceUsd: Double = 0.0,
    val change24hPercent: Double = 0.0,
    val high24hUsd: Double = 0.0,
    val low24hUsd: Double = 0.0,
    val marketCapUsd: Double = 0.0,
    val volume24hUsd: Double = 0.0,
    val circulatingSupply: Double = 0.0,
    val selectedTimeframe: String = "24H",
    val chartPoints: List<Float> = emptyList(),
    val lastUpdatedTimestamp: Long = 0L,
    val isLoading: Boolean = true,
    val isLive: Boolean = false,
    val errorMessage: String? = null
) {
    val isPositive: Boolean get() = change24hPercent >= 0.0

    val formattedPrice: String
        get() = if (priceUsd > 0) String.format("$%.4f", priceUsd) else "---"

    val formattedChange: String
        get() {
            if (priceUsd <= 0 && change24hPercent == 0.0) return "---"
            val sign = if (change24hPercent >= 0) "+" else ""
            return String.format("%s%.2f%%", sign, change24hPercent)
        }

    val formattedHigh24h: String
        get() = if (high24hUsd > 0) String.format("$%.4f", high24hUsd) else "---"

    val formattedLow24h: String
        get() = if (low24hUsd > 0) String.format("$%.4f", low24hUsd) else "---"

    val formattedMarketCap: String
        get() = if (marketCapUsd > 0) formatLargeNumber(marketCapUsd) else "---"

    val formattedVolume: String
        get() = if (volume24hUsd > 0) formatLargeNumber(volume24hUsd) else "---"

    private fun formatLargeNumber(number: Double): String {
        return when {
            number >= 1_000_000_000_000.0 -> String.format("$%.2fT", number / 1_000_000_000_000.0)
            number >= 1_000_000_000.0 -> String.format("$%.2fB", number / 1_000_000_000.0)
            number >= 1_000_000.0 -> String.format("$%.2fM", number / 1_000_000.0)
            number >= 1_000.0 -> String.format("$%.2fK", number / 1_000.0)
            number > 0 -> String.format("$%.2f", number)
            else -> "---"
        }
    }
}
