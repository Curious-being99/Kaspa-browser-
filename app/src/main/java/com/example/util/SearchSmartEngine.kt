package com.example.util

import com.example.network.KaspaPriceService
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Intelligent parser and evaluation engine for search bar queries:
 * 1. Instant Math Expressions & Currency Conversions
 * 2. Smart Typo Corrections ("Did you mean")
 * 3. Buy Kaspa & Exchange intent detection
 */
object SearchSmartEngine {

    data class MathResult(
        val expression: String,
        val resultString: String,
        val numericResult: Double,
        val isCryptoConversion: Boolean = false,
        val details: String? = null
    )

    data class TypoSuggestion(
        val original: String,
        val corrected: String,
        val isUrl: Boolean
    )

    data class BuyExchange(
        val name: String,
        val type: String, // "Instant Swap", "Top CEX", "Hardware/Wallet", "Fiat On-Ramp"
        val pairs: String,
        val url: String,
        val badgeColor: String = "#70C7BA"
    )

    private val commonTypoDictionary = mapOf(
        "kasps" to "kaspa",
        "kasp" to "kaspa",
        "kasppa" to "kaspa",
        "kaspaa" to "kaspa",
        "kaspa.og" to "kaspa.org",
        "kaspa.cm" to "kaspa.org",
        "kaspastream" to "kaspa.stream",
        "kaspa.strem" to "kaspa.stream",
        "gogle" to "google.com",
        "gogle.com" to "google.com",
        "googl.com" to "google.com",
        "youtub" to "youtube.com",
        "youtub.com" to "youtube.com",
        "youube.com" to "youtube.com",
        "wikipdia" to "wikipedia.org",
        "wikpedia" to "wikipedia.org",
        "reddt" to "reddit.com",
        "reddt.com" to "reddit.com",
        "twiter" to "x.com",
        "twiter.com" to "x.com",
        "facebok" to "facebook.com",
        "amzon" to "amazon.com",
        "amzon.com" to "amazon.com",
        "gthub" to "github.com",
        "gthub.com" to "github.com",
        "coingeko" to "coingecko.com",
        "coingeko.com" to "coingecko.com",
        "coinmcap" to "coinmarketcap.com",
        "binanc" to "binance.com",
        "dexscrener" to "dexscreener.com"
    )

    val verifiedKaspaExchanges = listOf(
        BuyExchange("ChangeNow", "Instant Swap (No KYC)", "Card / BTC / ETH -> KAS", "https://changenow.io/?to=kas"),
        BuyExchange("MEXC Global", "Top Liquidity CEX", "KAS/USDT, KAS/USDC", "https://www.mexc.com/exchange/KAS_USDT"),
        BuyExchange("Gate.io", "Tier-1 Global Exchange", "KAS/USDT", "https://www.gate.io/trade/KAS_USDT"),
        BuyExchange("Tangem Wallet", "Hardware / Card On-Ramp", "Apple Pay / Visa -> KAS", "https://tangem.com"),
        BuyExchange("Bybit", "Global Exchange", "KAS/USDT", "https://www.bybit.com/trade/spot/KAS/USDT"),
        BuyExchange("Uphold", "Regulated Multi-Asset", "Debit / Bank -> KAS", "https://uphold.com/assets/crypto/buy-kaspa")
    )

    /**
     * Detects if the query represents a "Buy Kaspa" intent.
     */
    fun isBuyKaspaQuery(query: String?): Boolean {
        if (query.isNullOrBlank()) return false
        val clean = query.trim().lowercase(Locale.ROOT)

        val triggers = listOf(
            "buy kaspa", "buy kas", "where to buy kaspa", "where to buy kas",
            "how to buy kaspa", "how to buy kas", "how to purchase kaspa",
            "purchase kaspa", "swap kaspa", "swap kas", "buy kaspa with card",
            "kaspa exchange", "kaspa exchanges", "kaspa onramp", "get kaspa",
            "buy \$kas", "swap to kaspa", "convert to kaspa", "kaspa fiat"
        )

        return triggers.any { clean == it || clean.startsWith(it) || clean.contains(it) }
    }

    /**
     * Checks if the query has a known typo and returns a correction if found.
     */
    fun checkTypo(query: String?): TypoSuggestion? {
        if (query.isNullOrBlank()) return null
        val clean = query.trim().lowercase(Locale.ROOT)

        // Exact match in dictionary
        val directMatch = commonTypoDictionary[clean]
        if (directMatch != null && !directMatch.equals(clean, ignoreCase = true)) {
            return TypoSuggestion(query, directMatch, directMatch.contains("."))
        }

        // Check word parts for typos
        val words = clean.split(" ")
        val correctedWords = words.map { word ->
            commonTypoDictionary[word] ?: word
        }
        val joined = correctedWords.joinToString(" ")
        if (joined != clean) {
            return TypoSuggestion(query, joined, joined.contains("."))
        }

        return null
    }

    /**
     * Evaluates whether the query is a math calculation or currency/KAS conversion.
     */
    fun evaluateMath(query: String?): MathResult? {
        if (query.isNullOrBlank()) return null
        val raw = query.trim()
        val clean = raw.lowercase(Locale.ROOT)

        // 1. Kaspa Crypto Converter: e.g. "100 kas in usd", "500 usd in kas", "1000 kas to usd", "50 usd to kas"
        val kasConversionResult = evaluateKaspaConversion(clean)
        if (kasConversionResult != null) return kasConversionResult

        // 2. Percentage calculation: e.g. "25% of 800", "15% of 250"
        val percentRegex = Regex("""^(\d+(?:\.\d+)?)\s*%\s*(?:of|\*)\s*(\d+(?:\.\d+)?)$""")
        val percentMatch = percentRegex.find(clean)
        if (percentMatch != null) {
            val (pStr, valStr) = percentMatch.destructured
            val percent = pStr.toDoubleOrNull() ?: return null
            val baseVal = valStr.toDoubleOrNull() ?: return null
            val res = (percent / 100.0) * baseVal
            return MathResult(
                expression = "$percent% of $baseVal",
                resultString = formatResult(res),
                numericResult = res,
                details = "$percent% × $baseVal"
            )
        }

        // 3. Square root: e.g. "sqrt(144)", "sqrt 64"
        val sqrtRegex = Regex("""^sqrt\(?(\d+(?:\.\d+)?)\)?$""")
        val sqrtMatch = sqrtRegex.find(clean)
        if (sqrtMatch != null) {
            val num = sqrtMatch.groupValues[1].toDoubleOrNull() ?: return null
            if (num < 0) return null
            val res = sqrt(num)
            return MathResult(
                expression = "√$num",
                resultString = formatResult(res),
                numericResult = res
            )
        }

        // 4. Arithmetic Expression: e.g. "54 * 12", "150 + 350", "100 / 4", "2^8", "45 - 18"
        if (isArithmeticCandidate(clean)) {
            try {
                val res = evalSimpleExpression(clean)
                if (res != null && !res.isNaN() && !res.isInfinite()) {
                    return MathResult(
                        expression = raw,
                        resultString = formatResult(res),
                        numericResult = res
                    )
                }
            } catch (_: Exception) {}
        }

        return null
    }

    private fun evaluateKaspaConversion(query: String): MathResult? {
        val currentPrice = KaspaPriceService.priceState.value.priceUsd.takeIf { it > 0 } ?: 0.1685

        // "100 kas to usd" or "100 kas in usd" or "100 kas"
        val kasToUsdRegex = Regex("""^(\d+(?:\.\d+)?)\s*(?:kas|kaspa)\s*(?:in|to|=|as)?\s*(?:usd|\$)?$""")
        val kasMatch = kasToUsdRegex.find(query)
        if (kasMatch != null) {
            val amount = kasMatch.groupValues[1].toDoubleOrNull() ?: return null
            val usdTotal = amount * currentPrice
            return MathResult(
                expression = "$amount KAS → USD",
                resultString = String.format("$%.2f USD", usdTotal),
                numericResult = usdTotal,
                isCryptoConversion = true,
                details = "1 KAS = $${String.format("%.4f", currentPrice)} USD"
            )
        }

        // "50 usd to kas" or "$50 in kas"
        val usdToKasRegex = Regex("""^(?:\$)?\s*(\d+(?:\.\d+)?)\s*(?:usd|\$)?\s*(?:in|to|=|as)?\s*(?:kas|kaspa)$""")
        val usdMatch = usdToKasRegex.find(query)
        if (usdMatch != null) {
            val amount = usdMatch.groupValues[1].toDoubleOrNull() ?: return null
            val kasTotal = amount / currentPrice
            return MathResult(
                expression = "$$amount USD → KAS",
                resultString = String.format("%.2f KAS", kasTotal),
                numericResult = kasTotal,
                isCryptoConversion = true,
                details = "1 KAS = $${String.format("%.4f", currentPrice)} USD"
            )
        }

        return null
    }

    private fun isArithmeticCandidate(query: String): Boolean {
        // Needs at least one digit and at least one math operator (+, -, *, /, ^, x)
        val hasDigits = query.any { it.isDigit() }
        val hasOperators = query.any { it in "+-*/^x" }
        val hasOnlyMathChars = query.all { it.isDigit() || it in "+-*/^x(). %,\t\n\r" }
        return hasDigits && hasOperators && hasOnlyMathChars && query.length >= 3
    }

    private fun evalSimpleExpression(expr: String): Double? {
        val sanitized = expr.replace("x", "*").replace(" ", "")
        // Handle basic binary operations
        val operators = listOf("+", "-", "*", "/", "^")
        for (op in listOf("+", "-", "*", "/", "^")) {
            val lastIdx = sanitized.lastIndexOf(op)
            if (lastIdx > 0 && lastIdx < sanitized.length - 1) {
                val left = sanitized.substring(0, lastIdx).toDoubleOrNull()
                val right = sanitized.substring(lastIdx + 1).toDoubleOrNull()
                if (left != null && right != null) {
                    return when (op) {
                        "+" -> left + right
                        "-" -> left - right
                        "*" -> left * right
                        "/" -> if (right != 0.0) left / right else null
                        "^" -> left.pow(right)
                        else -> null
                    }
                }
            }
        }
        return null
    }

    private fun formatResult(num: Double): String {
        return if (num % 1.0 == 0.0 && num < 1_000_000_000) {
            String.format(Locale.ROOT, "%,d", num.toLong())
        } else {
            String.format(Locale.ROOT, "%,.4f", num).trimEnd('0').trimEnd('.')
        }
    }
}
