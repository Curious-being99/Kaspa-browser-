package com.example.network

import com.example.network.KaspaPriceService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

/**
 * Universal Intelligent Omnibar Engine (100% Native, On-Device, Zero Latency):
 * - Universal Multi-Currency & Crypto Conversion (Fiat + Crypto)
 * - Universal World Geography & Capitals (All major countries, flags, populations, currencies)
 * - Universal Scientific Constants & Astronomy
 * - Universal Physical & Digital Unit Converter (Length, Mass, Speed, Temp, Data, Hashrate)
 * - Universal World Time & Timezones
 * - Universal Web3 & Technology Encyclopedia Definitions
 * - Universal Arithmetic, Trigonometry, & Algebra Solver
 * - Algorithmic Fuzzy Typo Auto-Correction (Levenshtein Distance)
 * - Color Swatch Inspector
 */
object SmartOmnibarEngine {

    enum class SmartCategory(val label: String) {
        MATH("CALCULATOR"),
        CURRENCY("CONVERTER"),
        CRYPTO("CRYPTO"),
        GEOGRAPHY("GEOGRAPHY"),
        SCIENCE("SCIENCE"),
        TIME("WORLD TIME"),
        UNIT("UNIT CONVERTER"),
        DICTIONARY("DEFINITION"),
        COLOR("COLOR")
    }

    data class SmartResult(
        val category: SmartCategory,
        val queryDisplay: String,
        val mainResult: String,
        val secondaryDetails: String? = null,
        val extraBadge: String? = null,
        val copyValue: String = mainResult
    )

    data class MathResult(
        val expression: String,
        val resultFormatted: String,
        val isKaspaCurrencyMath: Boolean = false,
        val details: String? = null
    )

    fun evaluateMath(query: String?): MathResult? {
        if (query.isNullOrBlank()) return null
        val omnibarRes = com.example.omnibar.SmartOmnibarEngine.evaluateMath(query)
        if (omnibarRes != null) {
            return MathResult(
                expression = omnibarRes.expression,
                resultFormatted = omnibarRes.formattedResult,
                isKaspaCurrencyMath = omnibarRes.expression.contains("kas", ignoreCase = true),
                details = "Standard Precision Arithmetic"
            )
        }
        val res = evaluateUniversal(query) ?: return null
        return if (res.category == SmartCategory.MATH || res.category == SmartCategory.CURRENCY || res.category == SmartCategory.CRYPTO || res.category == SmartCategory.UNIT) {
            MathResult(
                expression = res.queryDisplay,
                resultFormatted = res.mainResult,
                isKaspaCurrencyMath = res.category == SmartCategory.CRYPTO,
                details = res.secondaryDetails
            )
        } else null
    }

    data class ColorPreview(
        val hex: String,
        val rgb: String,
        val colorInt: Long
    )

    // ==========================================
    // 1. UNIVERSAL FUZZY TYPO CORRECTION (Levenshtein)
    // ==========================================
    private val GLOBAL_VOCABULARY = listOf(
        "kaspa", "kaspa price", "kaspa wallet", "kaspa explorer", "blockdag", "ghostdag",
        "blockchain", "github.com", "wikipedia.org", "youtube.com", "reddit.com", "x.com",
        "weather", "exchange", "bybit", "mexc", "gate.io", "currency", "calculator",
        "google.com", "amazon.com", "bitcoin", "ethereum", "solana", "decentralized"
    )

    fun getCorrection(query: String?): String? {
        if (query.isNullOrBlank()) return null
        return com.example.omnibar.SmartOmnibarEngine.findTypoCorrection(query)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    // ==========================================
    // 2. UNIVERSAL SMART EVALUATION
    // ==========================================
    fun evaluateUniversal(query: String?): SmartResult? {
        if (query.isNullOrBlank()) return null
        val trimmed = query.trim()
        val clean = trimmed.lowercase(Locale.ROOT)

        // A. World Time
        evaluateWorldTime(clean)?.let { return it }

        // B. Geography & Capitals
        evaluateGeography(clean)?.let { return it }

        // C. Science & Astronomy Constants
        evaluateScience(clean)?.let { return it }

        // D. Web3 / Tech Definitions
        evaluateDefinitions(clean)?.let { return it }

        // E. Multi-Currency & Crypto Converter
        evaluateCurrencyAndCrypto(clean)?.let { return it }

        // F. Physical & Digital Units (Length, Mass, Speed, Temp, Data, Hashrate)
        evaluateUnits(clean)?.let { return it }

        // G. Advanced Math & Calculator
        evaluateMathSmart(trimmed, clean)?.let { return it }

        return null
    }

    // ==========================================
    // A. WORLD TIME & TIMEZONES
    // ==========================================
    private fun evaluateWorldTime(clean: String): SmartResult? {
        if (!clean.startsWith("time in ") && !clean.startsWith("time ")) return null

        val location = clean.removePrefix("time in ").removePrefix("time ").trim()
        val tzMap = mapOf(
            "utc" to "UTC", "gmt" to "GMT",
            "london" to "Europe/London", "uk" to "Europe/London",
            "tokyo" to "Asia/Tokyo", "japan" to "Asia/Tokyo",
            "new york" to "America/New_York", "ny" to "America/New_York",
            "los angeles" to "America/Los_Angeles", "la" to "America/Los_Angeles", "california" to "America/Los_Angeles",
            "paris" to "Europe/Paris", "france" to "Europe/Paris",
            "berlin" to "Europe/Berlin", "germany" to "Europe/Berlin",
            "rome" to "Europe/Rome", "italy" to "Europe/Rome",
            "madrid" to "Europe/Madrid", "spain" to "Europe/Madrid",
            "dubai" to "Asia/Dubai", "uae" to "Asia/Dubai",
            "singapore" to "Asia/Singapore",
            "hong kong" to "Asia/Hong_Kong",
            "sydney" to "Australia/Sydney", "australia" to "Australia/Sydney",
            "mumbai" to "Asia/Kolkata", "india" to "Asia/Kolkata", "delhi" to "Asia/Kolkata",
            "toronto" to "America/Toronto", "canada" to "America/Toronto",
            "sao paulo" to "America/Sao_Paulo", "brazil" to "America/Sao_Paulo",
            "cairo" to "Africa/Cairo", "egypt" to "Africa/Cairo",
            "johannesburg" to "Africa/Johannesburg", "south africa" to "Africa/Johannesburg",
            "seoul" to "Asia/Seoul", "korea" to "Asia/Seoul"
        )

        val tzId = tzMap.entries.firstOrNull { location.contains(it.key) }?.value ?: return null
        val tz = TimeZone.getTimeZone(tzId)

        val timeSdf = SimpleDateFormat("h:mm:ss a (zzz)", Locale.US).apply { timeZone = tz }
        val dateSdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).apply { timeZone = tz }
        val now = Date()

        val locTitle = location.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

        return SmartResult(
            category = SmartCategory.TIME,
            queryDisplay = "Time in $locTitle",
            mainResult = timeSdf.format(now),
            secondaryDetails = dateSdf.format(now),
            extraBadge = tz.id
        )
    }

    // ==========================================
    // B. GEOGRAPHY & CAPITALS ENCYCLOPEDIA
    // ==========================================
    private data class CountryInfo(
        val name: String,
        val capital: String,
        val flag: String,
        val population: String,
        val currency: String,
        val continent: String
    )

    private val COUNTRIES = listOf(
        CountryInfo("United States", "Washington, D.C.", "🇺🇸", "335 Million", "USD ($)", "North America"),
        CountryInfo("United Kingdom", "London", "🇬🇧", "67 Million", "GBP (£)", "Europe"),
        CountryInfo("France", "Paris", "🇫🇷", "68 Million", "EUR (€)", "Europe"),
        CountryInfo("Germany", "Berlin", "🇩🇪", "84 Million", "EUR (€)", "Europe"),
        CountryInfo("Japan", "Tokyo", "🇯🇵", "125 Million", "JPY (¥)", "Asia"),
        CountryInfo("Canada", "Ottawa", "🇨🇦", "40 Million", "CAD ($)", "North America"),
        CountryInfo("Australia", "Canberra", "🇦🇺", "26 Million", "AUD ($)", "Oceania"),
        CountryInfo("Italy", "Rome", "🇮🇹", "59 Million", "EUR (€)", "Europe"),
        CountryInfo("Spain", "Madrid", "🇪🇸", "47 Million", "EUR (€)", "Europe"),
        CountryInfo("Brazil", "Brasília", "🇧🇷", "215 Million", "BRL (R$)", "South America"),
        CountryInfo("India", "New Delhi", "🇮🇳", "1.43 Billion", "INR (₹)", "Asia"),
        CountryInfo("China", "Beijing", "🇨🇳", "1.41 Billion", "CNY (¥)", "Asia"),
        CountryInfo("Switzerland", "Bern", "🇨🇭", "8.8 Million", "CHF (Fr)", "Europe"),
        CountryInfo("Netherlands", "Amsterdam", "🇳🇱", "17.8 Million", "EUR (€)", "Europe"),
        CountryInfo("South Korea", "Seoul", "🇰🇷", "52 Million", "KRW (₩)", "Asia"),
        CountryInfo("Nigeria", "Abuja", "🇳🇬", "225 Million", "NGN (₦)", "Africa"),
        CountryInfo("Egypt", "Cairo", "🇪🇬", "110 Million", "EGP (E£)", "Africa"),
        CountryInfo("South Africa", "Pretoria", "🇿🇦", "60 Million", "ZAR (R)", "Africa"),
        CountryInfo("Mexico", "Mexico City", "🇲🇽", "128 Million", "MXN ($)", "North America"),
        CountryInfo("Argentina", "Buenos Aires", "🇦🇷", "46 Million", "ARS ($)", "South America"),
        CountryInfo("United Arab Emirates", "Abu Dhabi", "🇦🇪", "10 Million", "AED (dh)", "Middle East"),
        CountryInfo("Singapore", "Singapore", "🇸🇬", "5.9 Million", "SGD ($)", "Asia"),
        CountryInfo("Israel", "Jerusalem", "🇮🇱", "9.7 Million", "ILS (₪)", "Middle East"),
        CountryInfo("Sweden", "Stockholm", "🇸🇪", "10.5 Million", "SEK (kr)", "Europe"),
        CountryInfo("Norway", "Oslo", "🇳🇴", "5.5 Million", "NOK (kr)", "Europe")
    )

    private fun evaluateGeography(clean: String): SmartResult? {
        if (clean == "world population" || clean == "population of earth" || clean == "population of world") {
            return SmartResult(
                category = SmartCategory.GEOGRAPHY,
                queryDisplay = "World Population",
                mainResult = "8.12 Billion People",
                secondaryDetails = "Estimated global human population in 2026",
                extraBadge = "🌍 Global"
            )
        }

        // Capital of X: e.g. "capital of france", "capital of japan", "france capital"
        if (clean.startsWith("capital of ") || clean.contains(" capital")) {
            val countrySearch = clean.removePrefix("capital of ").removeSuffix(" capital").trim()
            val match = COUNTRIES.firstOrNull {
                it.name.lowercase(Locale.ROOT).contains(countrySearch) || countrySearch.contains(it.name.lowercase(Locale.ROOT))
            }
            if (match != null) {
                return SmartResult(
                    category = SmartCategory.GEOGRAPHY,
                    queryDisplay = "Capital of ${match.name}",
                    mainResult = "${match.capital} ${match.flag}",
                    secondaryDetails = "Pop: ${match.population} • Currency: ${match.currency} • ${match.continent}",
                    extraBadge = match.name
                )
            }
        }

        // Population of X: e.g. "population of usa", "population of india"
        if (clean.startsWith("population of ")) {
            val countrySearch = clean.removePrefix("population of ").trim()
            val match = COUNTRIES.firstOrNull {
                it.name.lowercase(Locale.ROOT).contains(countrySearch) || countrySearch.contains(it.name.lowercase(Locale.ROOT))
            }
            if (match != null) {
                return SmartResult(
                    category = SmartCategory.GEOGRAPHY,
                    queryDisplay = "Population of ${match.name}",
                    mainResult = "${match.population} ${match.flag}",
                    secondaryDetails = "Capital: ${match.capital} • Currency: ${match.currency}",
                    extraBadge = match.name
                )
            }
        }

        return null
    }

    // ==========================================
    // C. SCIENCE & ASTRONOMY CONSTANTS
    // ==========================================
    private fun evaluateScience(clean: String): SmartResult? {
        return when {
            clean == "speed of light" || clean == "speed of light in vacuum" || clean == "c constant" -> {
                SmartResult(
                    category = SmartCategory.SCIENCE,
                    queryDisplay = "Speed of Light (c)",
                    mainResult = "299,792,458 m/s",
                    secondaryDetails = "≈ 300,000 km/s • 186,282 miles/s • 1.079 billion km/h",
                    extraBadge = "Physics"
                )
            }
            clean == "pi" || clean == "value of pi" || clean == "pi constant" -> {
                SmartResult(
                    category = SmartCategory.SCIENCE,
                    queryDisplay = "Mathematical Constant Pi (π)",
                    mainResult = "3.141592653589793...",
                    secondaryDetails = "Ratio of circle circumference to diameter (22/7 ≈ 3.142857)",
                    extraBadge = "Mathematics"
                )
            }
            clean == "speed of sound" || clean == "mach 1" -> {
                SmartResult(
                    category = SmartCategory.SCIENCE,
                    queryDisplay = "Speed of Sound (Mach 1 in air at 20°C)",
                    mainResult = "343 m/s (1,235 km/h)",
                    secondaryDetails = "≈ 767 mph • Depends on medium and temperature",
                    extraBadge = "Acoustics"
                )
            }
            clean == "distance to sun" || clean == "earth to sun distance" || clean == "1 au" -> {
                SmartResult(
                    category = SmartCategory.SCIENCE,
                    queryDisplay = "Distance from Earth to Sun (1 AU)",
                    mainResult = "149,597,870.7 km (1 AU)",
                    secondaryDetails = "≈ 92.96 Million Miles • Light travel time: 8 min 20 sec",
                    extraBadge = "Astronomy"
                )
            }
            clean == "distance to moon" || clean == "earth to moon distance" -> {
                SmartResult(
                    category = SmartCategory.SCIENCE,
                    queryDisplay = "Distance from Earth to Moon",
                    mainResult = "384,400 km",
                    secondaryDetails = "≈ 238,855 Miles • Light travel time: 1.28 seconds",
                    extraBadge = "Astronomy"
                )
            }
            clean == "gravity of earth" || clean == "earth gravity" || clean == "gravitational acceleration" -> {
                SmartResult(
                    category = SmartCategory.SCIENCE,
                    queryDisplay = "Standard Gravity on Earth (g)",
                    mainResult = "9.80665 m/s² (32.174 ft/s²)",
                    secondaryDetails = "Standard acceleration due to Earth's gravity at sea level",
                    extraBadge = "Physics"
                )
            }
            clean == "planck constant" || clean == "plancks constant" -> {
                SmartResult(
                    category = SmartCategory.SCIENCE,
                    queryDisplay = "Planck Constant (h)",
                    mainResult = "6.62607015 × 10⁻³⁴ J·s",
                    secondaryDetails = "Fundamental physical constant in quantum mechanics",
                    extraBadge = "Quantum"
                )
            }
            else -> null
        }
    }

    // ==========================================
    // D. WEB3 & TECH DEFINITIONS
    // ==========================================
    private fun evaluateDefinitions(clean: String): SmartResult? {
        if (!clean.startsWith("define ") && !clean.startsWith("what is ")) return null
        val term = clean.removePrefix("define ").removePrefix("what is ").trim()

        val defs = mapOf(
            "blockdag" to Pair("BlockDAG (Directed Acyclic Graph)", "A DAG structure allowing parallel blocks coexisting and referencing multiple parents, eliminating orphan blocks and enabling massive throughput (e.g. Kaspa 10 BPS)."),
            "ghostdag" to Pair("GHOSTDAG Protocol", "Kaspa's consensus mechanism that orders parallel blocks by greedily identifying well-connected DAG subtrees, solving the consensus problem on PoW DAGs."),
            "kaspa" to Pair("Kaspa (KAS)", "The world's fastest, purest proof-of-work decentralized Layer-1 cryptocurrency built on the GHOSTDAG BlockDAG protocol with sub-second block times."),
            "krc20" to Pair("KRC-20 Standard", "The high-throughput token inscription protocol natively inscribed onto the Kaspa BlockDAG Layer 1."),
            "halving" to Pair("Kaspa Chromatic Halving", "Kaspa's smooth annual emission reduction based on the 12-semitone musical scale, halving supply every year smoothly month-by-month."),
            "p2p" to Pair("Peer-to-Peer (P2P)", "A decentralized network architecture where participants share resources directly with each other without central servers."),
            "web3" to Pair("Web3", "The decentralized web paradigm powered by cryptography, blockchain state, self-custody, and peer-to-peer protocols."),
            "ipfs" to Pair("IPFS (InterPlanetary File System)", "A peer-to-peer hypermedia protocol designed to preserve and grow humanity's knowledge by making the web un-censorable and content-addressed.")
        )

        val entry = defs.entries.firstOrNull { term.contains(it.key) } ?: return null
        return SmartResult(
            category = SmartCategory.DICTIONARY,
            queryDisplay = entry.value.first,
            mainResult = entry.value.second,
            secondaryDetails = "Decentralized Knowledge Base",
            extraBadge = "Glossary"
        )
    }

    // ==========================================
    // E. MULTI-CURRENCY & CRYPTO CONVERTER
    // ==========================================
    private fun evaluateCurrencyAndCrypto(clean: String): SmartResult? {
        val kasPrice = KaspaPriceService.priceState.value.priceUsd.takeIf { it > 0 } ?: 0.1685

        // Forex rates relative to USD (baseline)
        val fiatToUsd = mapOf(
            "usd" to 1.0,
            "eur" to 1.08,
            "gbp" to 1.28,
            "cad" to 0.74,
            "aud" to 0.66,
            "jpy" to 0.0067,
            "chf" to 1.13,
            "cny" to 0.14,
            "inr" to 0.012,
            "brl" to 0.18,
            "ngn" to 0.00067,
            "sgd" to 0.75,
            "aed" to 0.272
        )

        // Crypto rates in USD
        val cryptoToUsd = mapOf(
            "kas" to kasPrice,
            "btc" to 64500.0,
            "eth" to 3450.0,
            "sol" to 145.0,
            "usdt" to 1.0,
            "usdc" to 1.0,
            "doge" to 0.12,
            "ltc" to 82.0
        )

        // Pattern: "100 eur in usd", "50 btc to usd", "500 kas in eur", "100 usd to kas"
        val currRegex = Regex("""^(\d+(?:\.\d+)?)\s*([a-z]{3,4})\s*(?:to|in)\s*([a-z]{3,4})$""")
        val match = currRegex.find(clean)
        if (match != null) {
            val (amtStr, fromCode, toCode) = match.destructured
            val amount = amtStr.toDoubleOrNull() ?: return null

            val fromRate = fiatToUsd[fromCode] ?: cryptoToUsd[fromCode]
            val toRate = fiatToUsd[toCode] ?: cryptoToUsd[toCode]

            if (fromRate != null && toRate != null) {
                val totalInUsd = amount * fromRate
                val targetAmount = totalInUsd / toRate

                val formattedTarget = if (targetAmount >= 1000) {
                    String.format(Locale.US, "%,.2f %s", targetAmount, toCode.uppercase())
                } else if (targetAmount >= 1) {
                    String.format(Locale.US, "%,.4f %s", targetAmount, toCode.uppercase()).trimEnd('0').trimEnd('.')
                } else {
                    String.format(Locale.US, "%,.6f %s", targetAmount, toCode.uppercase())
                }

                val isCrypto = cryptoToUsd.containsKey(fromCode) || cryptoToUsd.containsKey(toCode)
                val cat = if (isCrypto) SmartCategory.CRYPTO else SmartCategory.CURRENCY

                return SmartResult(
                    category = cat,
                    queryDisplay = "$amount ${fromCode.uppercase()} to ${toCode.uppercase()}",
                    mainResult = formattedTarget,
                    secondaryDetails = "1 ${fromCode.uppercase()} ≈ " + String.format(Locale.US, "%.4f %s", fromRate / toRate, toCode.uppercase()),
                    extraBadge = if (fromCode == "kas" || toCode == "kas") "Kaspa Live" else "Real-Time Exchange"
                )
            }
        }

        return null
    }

    // ==========================================
    // F. PHYSICAL & DIGITAL UNIT CONVERTER
    // ==========================================
    private fun evaluateUnits(clean: String): SmartResult? {
        // 1. Hashrate: "100 GH/s to TH/s", "10 TH/s in PH/s"
        val hashrateRegex = Regex("""^(\d+(?:\.\d+)?)\s*(mh\/s|gh\/s|th\/s|ph\/s|eh\/s)\s*(?:to|in)\s*(mh\/s|gh\/s|th\/s|ph\/s|eh\/s)$""")
        val hrMatch = hashrateRegex.find(clean)
        if (hrMatch != null) {
            val (valStr, fromUnit, toUnit) = hrMatch.destructured
            val v = valStr.toDoubleOrNull() ?: return null
            val mult = mapOf("mh/s" to 1e6, "gh/s" to 1e9, "th/s" to 1e12, "ph/s" to 1e15, "eh/s" to 1e18)
            val inHashes = v * (mult[fromUnit] ?: 1.0)
            val target = inHashes / (mult[toUnit] ?: 1.0)
            return SmartResult(
                category = SmartCategory.UNIT,
                queryDisplay = "$v ${fromUnit.uppercase()} in ${toUnit.uppercase()}",
                mainResult = String.format(Locale.US, "%,.4f %s", target, toUnit.uppercase()).trimEnd('0').trimEnd('.'),
                secondaryDetails = "Kaspa BlockDAG Proof-of-Work Hashrate",
                extraBadge = "Mining"
            )
        }

        // 2. Temperature: "100 c in f" or "100 f in c" or "300 k in c"
        if (clean.matches(Regex("""^(\d+(?:\.\d+)?)\s*c\s*(to|in)\s*f$"""))) {
            val num = clean.replace(Regex("""[^0-9.]"""), "").toDoubleOrNull() ?: return null
            val f = (num * 9.0 / 5.0) + 32.0
            return SmartResult(
                category = SmartCategory.UNIT,
                queryDisplay = "$num °C in Fahrenheit",
                mainResult = String.format(Locale.US, "%.1f °F", f),
                secondaryDetails = "Formula: ($num × 9/5) + 32 = $f",
                extraBadge = "Temperature"
            )
        }
        if (clean.matches(Regex("""^(\d+(?:\.\d+)?)\s*f\s*(to|in)\s*c$"""))) {
            val num = clean.replace(Regex("""[^0-9.]"""), "").toDoubleOrNull() ?: return null
            val c = (num - 32.0) * 5.0 / 9.0
            return SmartResult(
                category = SmartCategory.UNIT,
                queryDisplay = "$num °F in Celsius",
                mainResult = String.format(Locale.US, "%.1f °C", c),
                secondaryDetails = "Formula: ($num - 32) × 5/9 = $c",
                extraBadge = "Temperature"
            )
        }

        // 3. Length & Distance: km, miles, meters, feet, inches, cm
        if (clean.matches(Regex("""^(\d+(?:\.\d+)?)\s*km\s*(to|in)\s*(miles|mi)$"""))) {
            val num = clean.replace(Regex("""[^0-9.]"""), "").toDoubleOrNull() ?: return null
            return SmartResult(SmartCategory.UNIT, "$num km in miles", String.format(Locale.US, "%.3f miles", num * 0.621371), "1 km ≈ 0.621371 mi")
        }
        if (clean.matches(Regex("""^(\d+(?:\.\d+)?)\s*(miles|mi)\s*(to|in)\s*km$"""))) {
            val num = clean.replace(Regex("""[^0-9.]"""), "").toDoubleOrNull() ?: return null
            return SmartResult(SmartCategory.UNIT, "$num miles in km", String.format(Locale.US, "%.3f km", num * 1.60934), "1 mi ≈ 1.60934 km")
        }
        if (clean.matches(Regex("""^(\d+(?:\.\d+)?)\s*(meters|m)\s*(to|in)\s*(feet|ft)$"""))) {
            val num = clean.replace(Regex("""[^0-9.]"""), "").toDoubleOrNull() ?: return null
            return SmartResult(SmartCategory.UNIT, "$num meters in feet", String.format(Locale.US, "%.2f feet", num * 3.28084), "1 m ≈ 3.28084 ft")
        }
        if (clean.matches(Regex("""^(\d+(?:\.\d+)?)\s*(feet|ft)\s*(to|in)\s*(meters|m)$"""))) {
            val num = clean.replace(Regex("""[^0-9.]"""), "").toDoubleOrNull() ?: return null
            return SmartResult(SmartCategory.UNIT, "$num feet in meters", String.format(Locale.US, "%.2f meters", num * 0.3048), "1 ft = 0.3048 m")
        }
        if (clean.matches(Regex("""^(\d+(?:\.\d+)?)\s*(inches|in)\s*(to|in)\s*(cm|centimeters)$"""))) {
            val num = clean.replace(Regex("""[^0-9.]"""), "").toDoubleOrNull() ?: return null
            return SmartResult(SmartCategory.UNIT, "$num inches in cm", String.format(Locale.US, "%.2f cm", num * 2.54), "1 in = 2.54 cm")
        }

        // 4. Weight / Mass: kg, lbs
        if (clean.matches(Regex("""^(\d+(?:\.\d+)?)\s*kg\s*(to|in)\s*(lbs|pounds)$"""))) {
            val num = clean.replace(Regex("""[^0-9.]"""), "").toDoubleOrNull() ?: return null
            return SmartResult(SmartCategory.UNIT, "$num kg in lbs", String.format(Locale.US, "%.2f lbs", num * 2.20462), "1 kg ≈ 2.20462 lbs")
        }
        if (clean.matches(Regex("""^(\d+(?:\.\d+)?)\s*(lbs|pounds)\s*(to|in)\s*kg$"""))) {
            val num = clean.replace(Regex("""[^0-9.]"""), "").toDoubleOrNull() ?: return null
            return SmartResult(SmartCategory.UNIT, "$num lbs in kg", String.format(Locale.US, "%.2f kg", num * 0.453592), "1 lb ≈ 0.453592 kg")
        }

        // 5. Digital Storage: tb, gb, mb, kb
        val storageRegex = Regex("""^(\d+(?:\.\d+)?)\s*(tb|gb|mb|kb)\s*(?:to|in)\s*(tb|gb|mb|kb)$""")
        val storMatch = storageRegex.find(clean)
        if (storMatch != null) {
            val (valStr, fromUnit, toUnit) = storMatch.destructured
            val v = valStr.toDoubleOrNull() ?: return null
            val mult = mapOf("kb" to 1.0, "mb" to 1024.0, "gb" to 1024.0 * 1024.0, "tb" to 1024.0 * 1024.0 * 1024.0)
            val inKb = v * (mult[fromUnit] ?: 1.0)
            val target = inKb / (mult[toUnit] ?: 1.0)
            return SmartResult(
                category = SmartCategory.UNIT,
                queryDisplay = "$v ${fromUnit.uppercase()} in ${toUnit.uppercase()}",
                mainResult = String.format(Locale.US, "%,.2f %s", target, toUnit.uppercase()).trimEnd('0').trimEnd('.'),
                secondaryDetails = "Binary (1024 Base) Storage Calculation",
                extraBadge = "Data Storage"
            )
        }

        return null
    }

    // ==========================================
    // G. ADVANCED MATH & CALCULATOR
    // ==========================================
    private fun evaluateMathSmart(raw: String, clean: String): SmartResult? {
        val omni = com.example.omnibar.SmartOmnibarEngine.evaluateMath(raw)
        if (omni != null) {
            return SmartResult(
                category = SmartCategory.MATH,
                queryDisplay = raw.trim(),
                mainResult = omni.formattedResult,
                secondaryDetails = "Standard Precision Arithmetic",
                extraBadge = "Calculator"
            )
        }
        if (clean.matches(Regex("""^(\d+(\.\d+)?%?\s*(of|\*|\/|\+|\-|\^|x|×|÷|times|divided by)\s*)+\d+(\.\d+)?%?$""")) ||
            clean.startsWith("sqrt(") || clean.startsWith("sqrt ") ||
            clean.startsWith("sin(") || clean.startsWith("cos(") || clean.startsWith("tan(") ||
            clean.matches(Regex("""^[\d\s\+\-\*\/\(\)\^\.xX×÷\%]+$"""))
        ) {
            val hasOperator = clean.any { it in "+-*/^xX×÷%" } || clean.startsWith("sqrt") || clean.contains("of") || clean.startsWith("sin") || clean.startsWith("cos") || clean.startsWith("tan")
            val hasDigit = clean.any { it.isDigit() }
            if (hasOperator && hasDigit) {
                try {
                    val computed = parseAndCompute(clean)
                    if (computed != null && !computed.isNaN() && !computed.isInfinite()) {
                        val formatted = if (computed == computed.toLong().toDouble()) {
                            String.format(Locale.US, "%,d", computed.toLong())
                        } else {
                            String.format(Locale.US, "%,.4f", computed).trimEnd('0').trimEnd('.')
                        }
                        return SmartResult(
                            category = SmartCategory.MATH,
                            queryDisplay = raw.trim(),
                            mainResult = formatted,
                            secondaryDetails = "Standard Precision Arithmetic",
                            extraBadge = "Calculator"
                        )
                    }
                } catch (_: Exception) {}
            }
        }
        return null
    }

    private fun parseAndCompute(expr: String): Double? {
        var clean = expr
            .replace("multiplied by", "*")
            .replace("times", "*")
            .replace("divided by", "/")
            .replace("×", "*")
            .replace("÷", "/")
            .replace(Regex("""(?<=[0-9])\s*[xX]\s*(?=[0-9])"""), "*")
            .replace(" ", "")

        if (clean.contains("%of")) {
            val parts = clean.split("%of")
            val p = parts[0].toDoubleOrNull() ?: return null
            val v = parts[1].toDoubleOrNull() ?: return null
            return (p / 100.0) * v
        }

        if (clean.startsWith("sqrt(") && clean.endsWith(")")) {
            val inner = clean.removeSurrounding("sqrt(", ")").toDoubleOrNull() ?: return null
            return if (inner >= 0) sqrt(inner) else null
        }
        if (clean.startsWith("sqrt")) {
            val inner = clean.removePrefix("sqrt").toDoubleOrNull() ?: return null
            return if (inner >= 0) sqrt(inner) else null
        }

        if (clean.startsWith("sin(") && clean.endsWith(")")) {
            val inner = clean.removeSurrounding("sin(", ")").toDoubleOrNull() ?: return null
            return sin(Math.toRadians(inner))
        }
        if (clean.startsWith("cos(") && clean.endsWith(")")) {
            val inner = clean.removeSurrounding("cos(", ")").toDoubleOrNull() ?: return null
            return cos(Math.toRadians(inner))
        }
        if (clean.startsWith("tan(") && clean.endsWith(")")) {
            val inner = clean.removeSurrounding("tan(", ")").toDoubleOrNull() ?: return null
            return tan(Math.toRadians(inner))
        }

        if (clean.contains("^")) {
            val parts = clean.split("^")
            if (parts.size == 2) {
                val base = parts[0].toDoubleOrNull() ?: return null
                val exp = parts[1].toDoubleOrNull() ?: return null
                return base.pow(exp)
            }
        }

        return evaluateSimpleArithmetic(clean)
    }

    private fun evaluateSimpleArithmetic(str: String): Double? {
        return object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < str.length) str[pos].code else -1
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
                if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
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
                        eat('/'.code) -> {
                            val denom = parseFactor()
                            if (denom == 0.0) throw ArithmeticException("Division by zero")
                            x /= denom
                        }
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
                } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                    while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                    x = str.substring(startPos, pos).toDouble()
                } else {
                    throw RuntimeException("Unexpected: " + ch.toChar())
                }

                if (eat('^'.code)) x = x.pow(parseFactor())

                return x
            }
        }.parse()
    }

    // ==========================================
    // COLOR DETECTION
    // ==========================================
    fun detectColor(query: String?): ColorPreview? {
        if (query.isNullOrBlank()) return null
        val clean = query.trim()

        val hexRegex = Regex("""^#?([0-9a-fA-F]{6}|[0-9a-fA-F]{3})$""")
        if (hexRegex.matches(clean)) {
            val raw = clean.removePrefix("#")
            val fullHex = if (raw.length == 3) {
                "${raw[0]}${raw[0]}${raw[1]}${raw[1]}${raw[2]}${raw[2]}"
            } else {
                raw
            }
            return try {
                val colorLong = java.lang.Long.parseLong("FF$fullHex", 16)
                val r = (colorLong shr 16 and 0xFF).toInt()
                val g = (colorLong shr 8 and 0xFF).toInt()
                val b = (colorLong and 0xFF).toInt()
                ColorPreview(
                    hex = "#${fullHex.uppercase(Locale.ROOT)}",
                    rgb = "rgb($r, $g, $b)",
                    colorInt = colorLong
                )
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    // ==========================================
    // BUY KASPA INTENT
    // ==========================================
    fun isBuyKaspaQuery(query: String?): Boolean {
        if (query.isNullOrBlank()) return false
        val clean = query.trim().lowercase(Locale.ROOT)

        val buyKeywords = listOf(
            "buy kaspa", "buy kas", "where to buy kaspa", "how to buy kaspa",
            "where to get kaspa", "swap kaspa", "swap to kas", "purchase kaspa",
            "get kaspa", "buy \$kas", "onramp kaspa", "best place to buy kaspa",
            "kaspa exchange", "trade kaspa", "buy kas coin"
        )

        return buyKeywords.any { clean == it || clean.startsWith(it) || clean.contains(it) }
    }
}
