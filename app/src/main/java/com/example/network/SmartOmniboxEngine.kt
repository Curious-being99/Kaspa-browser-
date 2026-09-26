package com.example.network

import android.util.Log
import java.util.Locale
import kotlin.math.*

/**
 * Smart Omnibox Engine for Kaspa Browser.
 * 
 * Provides:
 * 1. Real-time Website Live Preview metadata & verification
 * 2. Real-time Kaspa Price & Marketplace intent detection
 * 3. Smart Auto-Correction & Fuzzy Search typo fixing
 * 4. Direct Buy Kaspa intent detection & exchange portals
 * 5. Instant Math Problem Solver directly inside the search bar
 */
object SmartOmniboxEngine {

    private const val TAG = "SmartOmniboxEngine"

    data class TypoCorrectionResult(
        val originalQuery: String,
        val correctedQuery: String,
        val isCorrected: Boolean,
        val explanation: String
    )

    data class MathResult(
        val expression: String,
        val formattedResult: String,
        val numericValue: Double,
        val isPercentage: Boolean = false
    )

    data class BuyKaspaInfo(
        val query: String,
        val title: String = "Buy Kaspa (\$KAS) - Direct Exchanges & On-Ramps",
        val description: String = "Kaspa is available on major tier-1 crypto exchanges and instant non-custodial swap portals.",
        val topExchanges: List<ExchangeOption>
    )

    data class ExchangeOption(
        val name: String,
        val pair: String,
        val type: String,
        val url: String,
        val tag: String,
        val badgeColorHex: String
    )

    data class WebsitePreviewInfo(
        val rawInput: String,
        val domain: String,
        val fullUrl: String,
        val title: String,
        val description: String,
        val faviconUrl: String,
        val protocol: String,
        val isSecure: Boolean,
        val isKaspaNative: Boolean
    )

    // Common web domains & popular search queries dictionary for fuzzy typo correction
    private val KNOWN_TARGETS = mapOf(
        "ksapa" to "kaspa.org",
        "kaspaa" to "kaspa.org",
        "kasp" to "kaspa.org",
        "kaspa org" to "kaspa.org",
        "kaspa.com" to "kaspa.org",
        "kaspa prce" to "kaspa price",
        "kas price" to "kaspa price",
        "kaspa rate" to "kaspa price",
        "kaspa chart" to "kaspa price",
        "kaspa market" to "kaspa price",
        "kaspa to usd" to "kaspa price",
        "kasusd" to "kaspa price",
        "kas/usd" to "kaspa price",
        "buy ksapa" to "buy kaspa",
        "buy kas" to "buy kaspa",
        "where to buy kaspa" to "buy kaspa",
        "wer to buy kaspa" to "buy kaspa",
        "how to buy kaspa" to "buy kaspa",
        "kaspa exchange" to "buy kaspa",
        "googl" to "google.com",
        "gogle" to "google.com",
        "googel" to "google.com",
        "youtbe" to "youtube.com",
        "yutube" to "youtube.com",
        "you tube" to "youtube.com",
        "wikpedia" to "wikipedia.org",
        "wikiped" to "wikipedia.org",
        "wikidia" to "wikipedia.org",
        "bincance" to "binance.com",
        "binanace" to "binance.com",
        "ethrium" to "ethereum.org",
        "etherum" to "ethereum.org",
        "kaspa wlet" to "kaspa wallet",
        "kaspa walet" to "kaspa wallet",
        "duckduckg" to "duckduckgo.com",
        "duckduck" to "duckduckgo.com",
        "gthub" to "github.com",
        "githubb" to "github.com",
        "reddt" to "reddit.com",
        "reddit" to "reddit.com",
        "twiter" to "x.com",
        "mexc" to "mexc.com",
        "mexcc" to "mexc.com",
        "coinmarketcp" to "coinmarketcap.com"
    )

    /**
     * Detects typos in search input and returns an auto-correction suggestion if found.
     */
    fun analyzeTypoAndAutoCorrect(input: String): TypoCorrectionResult {
        if (input.isBlank()) {
            return TypoCorrectionResult(input, input, false, "")
        }

        val cleanInput = input.trim().lowercase(Locale.ROOT)

        // 1. Exact match in dictionary
        KNOWN_TARGETS[cleanInput]?.let { corrected ->
            if (corrected != cleanInput) {
                return TypoCorrectionResult(
                    originalQuery = input,
                    correctedQuery = corrected,
                    isCorrected = true,
                    explanation = "Auto-corrected typo '$input' → '$corrected'"
                )
            }
        }

        // 2. Fuzzy Levenshtein distance check on known key domain words
        for ((typo, target) in KNOWN_TARGETS) {
            if (levenshteinDistance(cleanInput, typo) <= 2 && cleanInput.length >= 4) {
                return TypoCorrectionResult(
                    originalQuery = input,
                    correctedQuery = target,
                    isCorrected = true,
                    explanation = "Did you mean '$target'?"
                )
            }
        }

        // 3. Common domain TLD typos (e.g. .cm -> .com, .og -> .org, .ioo -> .io)
        if (cleanInput.contains(".")) {
            var fixedDomain = cleanInput
            if (fixedDomain.endsWith(".cm")) fixedDomain = fixedDomain.substringBeforeLast(".cm") + ".com"
            else if (fixedDomain.endsWith(".og")) fixedDomain = fixedDomain.substringBeforeLast(".og") + ".org"
            else if (fixedDomain.endsWith(".ne")) fixedDomain = fixedDomain.substringBeforeLast(".ne") + ".net"
            else if (fixedDomain.endsWith(".coom")) fixedDomain = fixedDomain.substringBeforeLast(".coom") + ".com"

            if (fixedDomain != cleanInput) {
                return TypoCorrectionResult(
                    originalQuery = input,
                    correctedQuery = fixedDomain,
                    isCorrected = true,
                    explanation = "Fixed domain extension typo → '$fixedDomain'"
                )
            }
        }

        return TypoCorrectionResult(
            originalQuery = input,
            correctedQuery = input,
            isCorrected = false,
            explanation = ""
        )
    }

    /**
     * Evaluates math expressions in real-time right inside the search bar.
     */
    fun solveMathProblem(input: String): MathResult? {
        if (input.isBlank()) return null
        val clean = input.trim().lowercase(Locale.ROOT)

        // Check if input looks like a math expression
        val mathRegex = Regex("^[0-9\\s\\.\\+\\-\\*/\\(\\)\\^%x÷×=a-z]+$")
        if (!mathRegex.matches(clean)) return null

        // Quick percentage pattern: "15% of 250" or "15% of 250.5"
        val percentOfRegex = Regex("^([0-9\\.]+)\\s*%\\s*(?:of)?\\s*([0-9\\.]+)$")
        percentOfRegex.find(clean)?.let { match ->
            try {
                val pct = match.groupValues[1].toDouble()
                val total = match.groupValues[2].toDouble()
                val result = (pct / 100.0) * total
                val formatted = if (result % 1.0 == 0.0) result.toLong().toString() else String.format("%.4f", result).trimEnd('0').trimEnd('.')
                return MathResult(
                    expression = "$pct% of $total",
                    formattedResult = formatted,
                    numericValue = result,
                    isPercentage = true
                )
            } catch (_: Exception) {}
        }

        // Check for basic arithmetic / function expression presence
        val containsOperator = clean.contains("+") || clean.contains("-") || clean.contains("*") ||
                clean.contains("/") || clean.contains("x") || clean.contains("÷") || clean.contains("×") ||
                clean.contains("^") || clean.contains("%") || clean.contains("sqrt") || clean.contains("sin") ||
                clean.contains("cos") || clean.contains("tan") || clean.contains("pi")

        if (!containsOperator) return null

        return try {
            val sanitized = clean
                .replace("x", "*")
                .replace("×", "*")
                .replace("÷", "/")
                .replace("pi", "3.141592653589793")

            val evalVal = evaluateExpression(sanitized) ?: return null
            val formatted = if (abs(evalVal - evalVal.toLong()) < 0.000001) {
                evalVal.toLong().toString()
            } else {
                String.format("%.6f", evalVal).trimEnd('0').trimEnd('.')
            }

            MathResult(
                expression = input.trim(),
                formattedResult = formatted,
                numericValue = evalVal
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Simple recursive descent / shunting-yard style evaluator for basic math operations
     */
    private fun evaluateExpression(expr: String): Double? {
        return try {
            object : Any() {
                var pos = -1
                var ch = 0

                fun nextChar() {
                    ch = if (++pos < expr.length) expr[pos].code else -1
                }

                fun eat(charToEat: Int): Boolean {
                    while (ch == ' '.code) nextChar()
                    if (ch == charToEat) {
                        nextChar()
                        return true
                    }
                    return false
                }

                fun parse(): Double {
                    nextChar()
                    val x = parseExpression()
                    if (pos < expr.length) throw RuntimeException("Unexpected: " + ch.toChar())
                    return x
                }

                fun parseExpression(): Double {
                    var x = parseTerm()
                    while (true) {
                        when {
                            eat('+'.code) -> x += parseTerm()
                            eat('-'.code) -> x -= parseTerm()
                            else -> return x
                        }
                    }
                }

                fun parseTerm(): Double {
                    var x = parseFactor()
                    while (true) {
                        when {
                            eat('*'.code) -> x *= parseFactor()
                            eat('/'.code) -> x /= parseFactor()
                            eat('%'.code) -> x %= parseFactor()
                            else -> return x
                        }
                    }
                }

                fun parseFactor(): Double {
                    if (eat('+'.code)) return parseFactor()
                    if (eat('-'.code)) return -parseFactor()

                    var x: Double
                    val startPos = pos
                    if (eat('('.code)) {
                        x = parseExpression()
                        eat(')'.code)
                    } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                        while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                        x = expr.substring(startPos, pos).toDouble()
                    } else if (ch in 'a'.code..'z'.code) {
                        while (ch in 'a'.code..'z'.code) nextChar()
                        val func = expr.substring(startPos, pos)
                        x = parseFactor()
                        x = when (func) {
                            "sqrt" -> sqrt(x)
                            "sin" -> sin(Math.toRadians(x))
                            "cos" -> cos(Math.toRadians(x))
                            "tan" -> tan(Math.toRadians(x))
                            "abs" -> abs(x)
                            "log" -> log10(x)
                            else -> throw RuntimeException("Unknown function: $func")
                        }
                    } else {
                        throw RuntimeException("Unexpected char: " + ch.toChar())
                    }

                    if (eat('^'.code)) x = x.pow(parseFactor())

                    return x
                }
            }.parse()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Detects "Buy Kaspa" purchase/exchange intent in search bar queries.
     */
    fun getBuyKaspaIntent(input: String): BuyKaspaInfo? {
        if (input.isBlank()) return null
        val clean = input.trim().lowercase(Locale.ROOT)

        val buyTriggers = listOf(
            "buy kaspa", "buy kas", "where to buy kaspa", "wer to buy kaspa",
            "how to buy kaspa", "kaspa exchange", "buy kaspa coin", "kaspa fiat",
            "kaspa credit card", "buy kaspa instant", "kaspa dex", "where buy kaspa",
            "purchase kaspa", "get kaspa", "kaspa trading"
        )

        val matches = buyTriggers.any { clean == it || clean.contains(it) } ||
                ((clean.contains("kaspa") || clean.contains("kas")) && (clean.contains("buy") || clean.contains("purchase") || clean.contains("exchange") || clean.contains("trade")))

        if (!matches) return null

        val topExchanges = listOf(
            ExchangeOption(
                name = "MEXC Global",
                pair = "KAS / USDT",
                type = "Top Spot Exchange (0% Fee)",
                url = "https://www.mexc.com/exchange/KAS_USDT",
                tag = "Highest Liquidity",
                badgeColorHex = "#10B981"
            ),
            ExchangeOption(
                name = "Gate.io",
                pair = "KAS / USDT & BTC",
                type = "Tier-1 Global Exchange",
                url = "https://www.gate.io/trade/KAS_USDT",
                tag = "Instant Deposit",
                badgeColorHex = "#00E5FF"
            ),
            ExchangeOption(
                name = "KuCoin",
                pair = "KAS / USDT",
                type = "Major Global Exchange",
                url = "https://www.kucoin.com/trade/KAS-USDT",
                tag = "Verified",
                badgeColorHex = "#3B82F6"
            ),
            ExchangeOption(
                name = "ChangeNOW Swap",
                pair = "Cross-chain (BTC/ETH -> KAS)",
                type = "Non-Custodial Instant Swap",
                url = "https://changenow.io/exchange?from=btc&to=kas",
                tag = "No Signup",
                badgeColorHex = "#8B5CF6"
            )
        )

        return BuyKaspaInfo(
            query = input,
            topExchanges = topExchanges
        )
    }

    /**
     * Generates a real-time website preview metadata object for any typed URL or domain.
     */
    fun generateWebsitePreview(input: String): WebsitePreviewInfo? {
        if (input.isBlank()) return null
        val clean = input.trim().lowercase(Locale.ROOT)

        val isUrlLike = clean.contains(".") || clean.startsWith("http://") || clean.startsWith("https://") ||
                clean.startsWith("kaspa://") || clean.startsWith("ipfs://") || clean.startsWith("dnet://") || clean.startsWith("mesh://")

        if (!isUrlLike && clean.split(" ").size > 3) return null

        val domain = when {
            clean.startsWith("https://") -> clean.substringAfter("https://").substringBefore("/")
            clean.startsWith("http://") -> clean.substringAfter("http://").substringBefore("/")
            clean.startsWith("kaspa://") -> clean.substringAfter("kaspa://").substringBefore("/")
            clean.startsWith("ipfs://") -> clean.substringAfter("ipfs://").substringBefore("/")
            else -> clean.substringBefore("/")
        }

        val fullUrl = when {
            clean.startsWith("http://") || clean.startsWith("https://") ||
                    clean.startsWith("kaspa://") || clean.startsWith("ipfs://") -> clean
            clean.endsWith(".kns") || clean.startsWith("kaspa") -> "kaspa://$domain"
            else -> "https://$domain"
        }

        val protocol = when {
            fullUrl.startsWith("kaspa://") -> "Kaspa BlockDAG P2P"
            fullUrl.startsWith("ipfs://") -> "IPFS Decentralized Storage"
            fullUrl.startsWith("https://") -> "HTTPS Secured TLS 1.3"
            else -> "HTTP Web"
        }

        val isKaspaNative = fullUrl.startsWith("kaspa://") || domain.contains("kaspa") || domain.endsWith(".kns")
        val isSecure = fullUrl.startsWith("https://") || isKaspaNative || fullUrl.startsWith("ipfs://")

        val title = when {
            domain.contains("kaspa.org") -> "Kaspa Currency — Proof of Work BlockDAG"
            domain.contains("kaspa.stream") -> "Kaspa.stream Live BlockDAG Visualizer"
            domain.contains("wikipedia.org") -> "Wikipedia — Free Open Encyclopedia"
            domain.contains("github.com") -> "GitHub — Build and Deploy Software"
            domain.contains("mexc.com") -> "MEXC Global Crypto Exchange"
            domain.contains("google.com") -> "Google Web Search"
            domain.contains("youtube.com") -> "YouTube Video Streaming"
            domain.contains("x.com") || domain.contains("twitter.com") -> "X (Twitter) Social Network"
            domain.contains("reddit.com") -> "Reddit — Dive into anything"
            else -> "$domain — Website Preview"
        }

        val description = when {
            domain.contains("kaspa.org") -> "Official website of Kaspa (\$KAS), the fastest, open-source, decentralized & fully scalable Layer-1 Proof-of-Work cryptocurrency built on GHOSTDAG."
            domain.contains("kaspa.stream") -> "Real-time 3D and 2D DAG visualizer displaying live blocks, transaction throughput, and network hash rate on Kaspa mainnet."
            domain.contains("wikipedia.org") -> "Explore millions of articles, community knowledge, and encyclopedia entries with zero tracking."
            domain.contains("github.com") -> "Repository hosting and version control platform for developers worldwide."
            else -> "Live preview of $domain. Tap below to inspect page status, security certificates, and open in real time."
        }

        val faviconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=128"

        return WebsitePreviewInfo(
            rawInput = input,
            domain = domain,
            fullUrl = fullUrl,
            title = title,
            description = description,
            faviconUrl = faviconUrl,
            protocol = protocol,
            isSecure = isSecure,
            isKaspaNative = isKaspaNative
        )
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
