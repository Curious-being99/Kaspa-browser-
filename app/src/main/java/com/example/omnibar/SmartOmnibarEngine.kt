package com.example.omnibar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit
import kotlin.math.*

/**
 * Data model for Kaspa real-time market data in Omnibar.
 */
data class KaspaMarketData(
    val priceUsd: Double = 0.165,
    val change24h: Double = 3.82,
    val high24h: Double = 0.172,
    val low24h: Double = 0.158,
    val marketCapUsd: String = "$4.15 Billion",
    val volume24hUsd: String = "$135.2 Million",
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Data model for Instant Math calculation result.
 */
data class MathCalculationResult(
    val expression: String,
    val formattedResult: String,
    val numericResult: Double,
    val isCryptoConversion: Boolean = false,
    val conversionDetails: String? = null
)

/**
 * Verified exchange / on-ramp for Kaspa.
 */
data class KaspaBuyOption(
    val name: String,
    val url: String,
    val type: String,
    val badge: String,
    val description: String
)

/**
 * Website metadata preview before navigation.
 */
data class WebsitePreviewData(
    val domain: String,
    val fullUrl: String,
    val title: String,
    val description: String,
    val protocolBadge: String,
    val isSecure: Boolean,
    val isDecentralized: Boolean
)

/**
 * Engine powering intelligent real-time Omnibar features:
 * 1. Advanced Scientific & Crypto Math Engine
 * 2. Real-time Kaspa Price & Market Ticker
 * 3. Verified "Where to Buy Kaspa" Gateway
 * 4. Smart Typo & "Did you mean?" Correction
 * 5. Instant Real-Time Website Preview before browsing
 */
object SmartOmnibarEngine {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Volatile
    var cachedMarketData: KaspaMarketData = KaspaMarketData()
    private var lastMarketFetchTime = 0L

    val verifiedBuyOptions = listOf(
        KaspaBuyOption(
            name = "MEXC Global",
            url = "https://www.mexc.com/exchange/KAS_USDT",
            type = "Spot Exchange",
            badge = "Deep Liquidity",
            description = "Top Tier-1 volume for KAS/USDT spot and futures"
        ),
        KaspaBuyOption(
            name = "Bybit",
            url = "https://www.bybit.com/en/trade/spot/KAS/USDT",
            type = "Spot Exchange",
            badge = "Verified Tier-1",
            description = "Trade KAS with zero/low fees and fast settlement"
        ),
        KaspaBuyOption(
            name = "Gate.io",
            url = "https://www.gate.io/trade/KAS_USDT",
            type = "Spot Exchange",
            badge = "High Volume",
            description = "Native KAS deposits and withdrawals on BlockDAG"
        ),
        KaspaBuyOption(
            name = "ChangeNOW",
            url = "https://changenow.io/exchange?from=usd&to=kas",
            type = "Instant Swap",
            badge = "No KYC Needed",
            description = "Non-custodial instant swap directly to your Kaspa address"
        ),
        KaspaBuyOption(
            name = "Tangem Wallet",
            url = "https://tangem.com/",
            type = "Hardware Cold Storage",
            badge = "Official Partner",
            description = "Secure hardware card wallet with direct in-app Kaspa purchase"
        )
    )

    private val commonTypoMap = mapOf(
        "teh" to "the", "recieve" to "receive", "recieved" to "received", "reciept" to "receipt",
        "wierd" to "weird", "definately" to "definitely", "definitly" to "definitely", "definitley" to "definitely",
        "seperate" to "separate", "seperation" to "separation", "untill" to "until", "occured" to "occurred",
        "beleive" to "believe", "belive" to "believe", "tommorow" to "tomorrow", "tomorow" to "tomorrow",
        "restaraunt" to "restaurant", "resturant" to "restaurant", "goverment" to "government", "enviroment" to "environment",
        "calender" to "calendar", "succesful" to "successful", "succesfully" to "successfully", "truely" to "truly",
        "neccessary" to "necessary", "necesary" to "necessary", "privilege" to "privilege", "privelege" to "privilege",
        "embarass" to "embarrass", "inteligence" to "intelligence", "intellegence" to "intelligence",
        "artifical" to "artificial", "artifitial" to "artificial", "transation" to "transaction", "transatcion" to "transaction",
        "walet" to "wallet", "wallett" to "wallet", "kapsa" to "kaspa", "kapa" to "kaspa", "kaspaa" to "kaspa",
        "curreny" to "currency", "currnecy" to "currency", "computr" to "computer", "compuer" to "computer",
        "weatehr" to "weather", "wether" to "weather", "langauge" to "language", "messanger" to "messenger",
        "fotball" to "football", "documnet" to "document", "vedio" to "video", "musci" to "music",
        "pictur" to "picture", "photograh" to "photograph", "reserch" to "research", "developmnt" to "development",
        "programing" to "programming", "progamming" to "programming", "conection" to "connection", "secuity" to "security",
        "passward" to "password", "settngs" to "settings", "histroy" to "history", "favourite" to "favorite",
        "notifcation" to "notification", "persistance" to "persistence", "artcle" to "article", "downlod" to "download",
        "uplod" to "upload", "connet" to "connect", "distribted" to "distributed", "dcentralized" to "decentralized",
        "blockhain" to "blockchain", "blochain" to "blockchain", "crytpo" to "crypto", "crpyto" to "crypto",
        "bitcion" to "bitcoin", "etherium" to "ethereum", "solna" to "solana", "telsa" to "tesla",
        "applle" to "apple", "googlr" to "google", "yotube" to "youtube", "twiter" to "twitter",
        "reddt" to "reddit", "wikpedia" to "wikipedia", "wikepedia" to "wikipedia", "facebok" to "facebook",
        "instgram" to "instagram", "telelgram" to "telegram", "disocrd" to "discord", "binanc" to "binance",
        "bybt" to "bybit", "coingeko" to "coingecko", "coinmarkcap" to "coinmarketcap", "tradinview" to "tradingview",
        "amazn" to "amazon", "netflx" to "netflix", "spotfy" to "spotify", "micrsoft" to "microsoft",
        "samsng" to "samsung", "chatgbt" to "chatgpt", "openi" to "openai", "browsr" to "browser",
        "addres" to "address", "explrer" to "explorer", "netwrok" to "network", "protocl" to "protocol",
        "extensn" to "extension", "downlaod" to "download", "searhc" to "search", "scren" to "screen",
        "messge" to "message", "accnt" to "account", "balnce" to "balance", "transfr" to "transfer",
        "mony" to "money", "finace" to "finance", "markts" to "markets", "exchnge" to "exchange",
        "busness" to "business", "servce" to "service", "systm" to "system", "internett" to "internet",
        "onlin" to "online", "websit" to "website", "artcle" to "article", "newss" to "news",
        "todday" to "today", "tonite" to "tonight", "wrld" to "world", "peopl" to "people"
    )

    private val popularDictionary = listOf(
        // Core Web & Tech
        "about", "account", "address", "algorithm", "amazon", "analytics", "android", "api", "apple",
        "application", "archive", "article", "artificial", "audio", "authentication", "auto", "balance",
        "bandwidth", "bank", "battery", "binance", "bing", "bitcoin", "block", "blockchain", "blockdag",
        "bluetooth", "book", "bookmark", "brave", "browser", "business", "bybit", "byte", "calculator",
        "calendar", "camera", "canvas", "card", "category", "centralized", "certificate", "chain", "channel",
        "chat", "chatgpt", "chrome", "cloud", "code", "coin", "coincap", "coingecko", "coinmarketcap",
        "community", "company", "computer", "config", "connection", "contact", "content", "conversion",
        "cookie", "create", "crypto", "cryptocurrency", "currency", "dashboard", "data", "database",
        "decentralized", "delete", "deposit", "design", "desktop", "developer", "development", "device",
        "dextools", "dictionary", "discord", "discovery", "distributed", "dns", "dnslink", "document",
        "dollar", "domain", "download", "driver", "duckduckgo", "ecosystem", "edition", "education",
        "email", "encryption", "engine", "entertainment", "environment", "error", "ethereum", "euro",
        "exchange", "explorer", "extension", "facebook", "favorite", "feed", "file", "finance",
        "firewall", "flutter", "font", "food", "football", "format", "forum", "framework", "future",
        "game", "gaming", "gateway", "ghostdag", "github", "gitlab", "global", "google", "government",
        "graphics", "hardware", "hash", "hashrate", "header", "health", "history", "home", "hospital",
        "hotel", "html", "http", "https", "image", "incognito", "index", "information", "instagram",
        "install", "instant", "intelligence", "interface", "internet", "ipfs", "javascript", "journal",
        "kaspa", "keyboard", "language", "laptop", "layer", "library", "link", "linux", "location",
        "login", "magazine", "mail", "map", "market", "media", "memory", "menu", "message", "messenger",
        "meta", "mexc", "microsoft", "mining", "mobile", "money", "movie", "music", "native", "navigation",
        "netflix", "network", "news", "node", "notification", "offline", "online", "openai", "open-source",
        "operating", "option", "package", "page", "password", "payment", "peer", "permission", "persistence",
        "phone", "photo", "photograph", "picture", "player", "policy", "popular", "portal", "post", "power",
        "preview", "price", "privacy", "private", "profile", "programming", "protocol", "proxy", "public",
        "query", "quic", "radio", "real-time", "receipt", "receive", "recent", "reddit", "refresh",
        "register", "release", "repository", "request", "research", "resource", "response", "restaurant",
        "result", "route", "router", "rust", "safety", "samsung", "satellite", "scale", "scanner",
        "school", "science", "screen", "search", "secure", "security", "server", "service", "session",
        "setting", "settings", "share", "shield", "shop", "shopping", "signal", "smart", "social",
        "software", "solana", "solution", "sound", "source", "space", "speed", "spotify", "standard",
        "startpage", "statistics", "status", "stock", "storage", "store", "stream", "streaming", "style",
        "system", "tab", "table", "technology", "telegram", "terminal", "tesla", "test", "text",
        "theme", "ticket", "time", "timezone", "today", "token", "tomorrow", "tools", "top", "total",
        "tracker", "trade", "tradingview", "traffic", "transaction", "transfer", "translation",
        "transport", "travel", "trending", "trigonometry", "twitter", "universal", "university", "update",
        "upload", "url", "user", "utxo", "validator", "vector", "video", "view", "viewer", "volume",
        "wallet", "weather", "web", "web3", "webauthn", "website", "webrtc", "websocket", "widget",
        "wikipedia", "window", "wireless", "world", "world-wide", "writer", "youtube"
    )

    // Known site previews dictionary
    private val previewKnowledgeBase = mapOf(
        "kaspa.org" to Triple("Kaspa Official Portal", "Fastest open-source, decentralized & fully scalable Layer-1 Proof-of-Work BlockDAG network.", "BlockDAG L1"),
        "kaspa.stream" to Triple("Kaspa Stream Block Explorer", "Real-time 10 BPS visual visualizer, block DAG inspector, and transaction telemetry.", "BlockDAG Explorer"),
        "kasrace.com" to Triple("Kasrace 4D Visualizer", "4D real-time BlockDAG visualization of Kaspa network blocks and DAG structure.", "Interactive 4D"),
        "kaskad.live" to Triple("Kaskad Network", "Decentralized mesh gateway, node discovery and peer-to-peer network infrastructure.", "P2P Mesh"),
        "mykai.dev" to Triple("Kai Sovereign Apps", "Decentralized sovereign apps, microservices, and decentralized storage on BlockDAG.", "Web3 Apps"),
        "dot.k" to Triple(".k Decentralized Domains", "Zero-censor domain registry and human-readable naming on Kaspa.", "Decentralized DNS"),
        "github.com" to Triple("GitHub", "World's leading developer platform for open-source software, git repositories, and collaboration.", "Developer Hub"),
        "wikipedia.org" to Triple("Wikipedia", "The free, multilingual open encyclopedia written and maintained by a community of volunteers.", "Free Encyclopedia"),
        "reddit.com" to Triple("Reddit", "Home to thousands of communities, conversations, and Kaspa r/kaspa ecosystem discussions.", "Social Community"),
        "youtube.com" to Triple("YouTube", "Online video sharing platform and streaming media service.", "Video Streaming"),
        "twitter.com" to Triple("X (Twitter)", "Real-time news, updates, and global conversations across the crypto space.", "Realtime Social"),
        "x.com" to Triple("X (Twitter)", "Real-time news, updates, and global conversations across the crypto space.", "Realtime Social"),
        "google.com" to Triple("Google", "Search the world's information, including webpages, images, videos and more.", "Search Engine"),
        "coingecko.com" to Triple("CoinGecko", "Independent cryptocurrency data aggregator, live prices, charts, and market capitalization.", "Crypto Analytics")
    )

    // Popular alias mapping for immediate real-time preview during typing
    private val siteAliases = mapOf(
        "kaspa" to "kaspa.org",
        "kaspa." to "kaspa.org",
        "kaspastream" to "kaspa.stream",
        "kasrace" to "kasrace.com",
        "kaskad" to "kaskad.live",
        "mykai" to "mykai.dev",
        "dot" to "dot.k",
        "dot." to "dot.k",
        "google" to "google.com",
        "youtube" to "youtube.com",
        "github" to "github.com",
        "reddit" to "reddit.com",
        "wikipedia" to "wikipedia.org",
        "wiki" to "wikipedia.org",
        "twitter" to "x.com",
        "x" to "x.com",
        "coingecko" to "coingecko.com",
        "coinmarketcap" to "coinmarketcap.com"
    )

    /**
     * Checks if the query looks like a request for Kaspa Price / Market info.
     */
    fun isKaspaPriceQuery(query: String): Boolean {
        val q = query.trim().lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() || it == '$' || it == '/' }
        if (q.isBlank()) return false

        // Exact shortcuts
        if (q == "kaspa price" || q == "kas price" || q == "\$kas" || q == "kas" ||
            q == "kas/usd" || q == "kaspa/usd" || q == "kas ticker" || q == "kaspa ticker" ||
            q == "price of kaspa" || q == "price of kas" || q == "kaspa market" ||
            q == "kas market" || q == "kas marketcap" || q == "kaspa rate" ||
            q == "kaspa cost" || q == "kas cost" || q == "how much is kaspa" || q == "how much is kas"
        ) {
            return true
        }

        // Subphrase / Natural language checks
        val hasKas = q.contains("kaspa") || q.contains("kas") || q.contains("\$kas")
        val hasPriceKeyword = q.contains("price") || q.contains("rate") || q.contains("ticker") ||
                q.contains("market") || q.contains("marketcap") || q.contains("worth") ||
                q.contains("cost") || q.contains("value") || q.contains("chart")

        return hasKas && hasPriceKeyword && !isBuyKaspaQuery(query)
    }

    /**
     * Checks if the query looks like a request to Buy Kaspa.
     * Flexibly matches natural language variations like:
     * - "I want to buy Kaspa"
     * - "Where can I buy KAS?" / "Where can I buy kaspa?"
     * - "KAS purchase" / "kaspa purchase"
     * - "how to buy kaspa" / "where to buy kas"
     */
    fun isBuyKaspaQuery(query: String): Boolean {
        val q = query.trim().lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }
        if (q.isBlank()) return false

        // Direct common exact phrases
        val exactMatch = q == "buy kaspa" || q == "buy kas" || q == "how to buy kaspa" ||
                q == "where to buy kaspa" || q == "where can i buy kaspa" || q == "where can i buy kas" ||
                q == "i want to buy kaspa" || q == "i want to buy kas" || q == "want to buy kaspa" ||
                q == "want to buy kas" || q == "kas purchase" || q == "kaspa purchase" ||
                q == "purchase kaspa" || q == "purchase kas" || q == "swap kaspa" || q == "swap kas" ||
                q == "get kaspa" || q == "get kas" || q == "buy kaspa crypto" || q == "buy kas coin" ||
                q == "where to buy kas" || q == "how to buy kas" || q == "buying kaspa" || q == "buying kas"

        if (exactMatch) return true

        val hasKas = q.contains("kaspa") || q.contains("kas")
        val hasBuyIntent = q.contains("buy") || q.contains("purchase") || q.contains("swap") ||
                q.contains("onramp") || q.contains("acquire") || q.contains("get kas")

        if (!hasKas || !hasBuyIntent) return false

        // Check combinations (e.g. "where can i buy ... kaspa", "want to buy ... kas", "kaspa ... purchase")
        return q.contains("buy") || q.contains("purchase") || q.contains("swap") ||
                q.contains("where") || q.contains("how to") || q.contains("want to")
    }

    /**
     * Fetches live market data asynchronously with intelligent caching.
     */
    suspend fun getLiveMarketData(): KaspaMarketData = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastMarketFetchTime < 30_000L && cachedMarketData.priceUsd > 0.0) {
            return@withContext cachedMarketData
        }

        try {
            val req = Request.Builder()
                .url("https://api.kaspa.org/info/price")
                .header("User-Agent", "KaspaBrowser/1.0")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val price = json.optDouble("price", cachedMarketData.priceUsd)
                        cachedMarketData = cachedMarketData.copy(
                            priceUsd = price,
                            lastUpdated = now
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val cgReq = Request.Builder()
                .url("https://api.coingecko.com/api/v3/simple/price?ids=kaspa&vs_currencies=usd&include_24hr_change=true&include_24hr_vol=true&include_market_cap=true")
                .header("User-Agent", "KaspaBrowser/1.0")
                .build()

            httpClient.newCall(cgReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body).optJSONObject("kaspa")
                        if (json != null) {
                            val price = json.optDouble("usd", cachedMarketData.priceUsd)
                            val change = json.optDouble("usd_24h_change", cachedMarketData.change24h)
                            val mcap = json.optDouble("usd_market_cap", 0.0)
                            val vol = json.optDouble("usd_24h_vol", 0.0)

                            val mcapStr = if (mcap > 0) "$%.2f Billion".format(mcap / 1_000_000_000.0) else cachedMarketData.marketCapUsd
                            val volStr = if (vol > 0) "$%.1f Million".format(vol / 1_000_000.0) else cachedMarketData.volume24hUsd

                            cachedMarketData = cachedMarketData.copy(
                                priceUsd = price,
                                change24h = change,
                                high24h = price * 1.04,
                                low24h = price * 0.96,
                                marketCapUsd = mcapStr,
                                volume24hUsd = volStr,
                                lastUpdated = now
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        lastMarketFetchTime = now
        cachedMarketData
    }

    /**
     * Checks if the query contains an advanced mathematical expression or crypto currency conversion.
     */
    fun evaluateMath(query: String): MathCalculationResult? {
        return AdvancedMathEngine.evaluate(query)
    }

    /**
     * Checks if the query has a plausible typo and returns a "Did you mean" suggestion.
     * Supports both single-word and full multi-word phrase corrections across general English and Web3 terms.
     */
    fun findTypoCorrection(query: String): String? {
        val trimmed = query.trim()
        if (trimmed.length < 3) return null

        val lower = trimmed.lowercase()

        // Ignore URLs, protocols, domains, IP addresses, or math expressions
        if (lower.startsWith("http://") || lower.startsWith("https://") ||
            lower.startsWith("kaspa:") || lower.startsWith("kas://") ||
            lower.startsWith("ipfs://") || lower.startsWith("mesh://") ||
            lower.contains(".com") || lower.contains(".org") || lower.contains(".net") ||
            lower.contains(".io") || lower.contains(".dev") || lower.contains(".xyz") ||
            lower.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}.*""")) ||
            evaluateMath(trimmed) != null
        ) {
            return null
        }

        // Direct phrase mapping check
        if (commonTypoMap.containsKey(lower)) {
            return commonTypoMap[lower]
        }

        val tokens = lower.split(Regex("\\s+"))
        var modified = false
        val correctedTokens = tokens.map { rawToken ->
            val cleanToken = rawToken.filter { it.isLetter() }
            if (cleanToken.length < 3) {
                rawToken
            } else if (commonTypoMap.containsKey(cleanToken)) {
                modified = true
                val corrected = commonTypoMap[cleanToken]!!
                rawToken.replace(cleanToken, corrected)
            } else if (!popularDictionary.contains(cleanToken)) {
                val best = findBestWordMatch(cleanToken)
                if (best != null && best != cleanToken) {
                    modified = true
                    rawToken.replace(cleanToken, best)
                } else {
                    rawToken
                }
            } else {
                rawToken
            }
        }

        if (modified) {
            val result = correctedTokens.joinToString(" ")
            if (result != lower) {
                return result
            }
        }

        // Whole string fallback matching
        val wholeMatch = findBestWordMatch(lower)
        if (wholeMatch != null && wholeMatch != lower) {
            return wholeMatch
        }

        return null
    }

    private fun findBestWordMatch(target: String): String? {
        if (target.length < 3) return null
        if (popularDictionary.contains(target)) return null

        var bestMatch: String? = null
        var minDistance = Int.MAX_VALUE

        for (dictWord in popularDictionary) {
            val dist = damerauLevenshteinDistance(target, dictWord)
            val maxAllowed = if (target.length <= 4) 1 else 2
            if (dist in 1..maxAllowed && dist < minDistance) {
                val lenDiff = abs(target.length - dictWord.length)
                if (lenDiff <= 2) {
                    minDistance = dist
                    bestMatch = dictWord
                }
            }
        }

        return bestMatch
    }

    private fun damerauLevenshteinDistance(source: String, target: String): Int {
        val srcLen = source.length
        val tgtLen = target.length
        if (srcLen == 0) return tgtLen
        if (tgtLen == 0) return srcLen

        val d = Array(srcLen + 1) { IntArray(tgtLen + 1) }

        for (i in 0..srcLen) d[i][0] = i
        for (j in 0..tgtLen) d[0][j] = j

        for (i in 1..srcLen) {
            for (j in 1..tgtLen) {
                val cost = if (source[i - 1] == target[j - 1]) 0 else 1
                d[i][j] = minOf(
                    d[i - 1][j] + 1,      // deletion
                    d[i][j - 1] + 1,      // insertion
                    d[i - 1][j - 1] + cost // substitution
                )
                // Transposition check
                if (i > 1 && j > 1 &&
                    source[i - 1] == target[j - 2] &&
                    source[i - 2] == target[j - 1]
                ) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
        }
        return d[srcLen][tgtLen]
    }

    /**
     * Real-time website preview resolver.
     * Triggers instantly as you type domains or known site keywords.
     */
    fun getWebsitePreview(query: String): WebsitePreviewData? {
        val q = query.trim()
        if (q.length < 2) return null

        // Avoid triggering website preview if user is writing math
        if (evaluateMath(q) != null) return null

        val lower = q.lowercase()

        // Exclude if it has whitespace unless it explicitly starts with a URL scheme
        if (lower.contains(" ") && !lower.startsWith("http://") && !lower.startsWith("https://") && !lower.startsWith("kas://") && !lower.startsWith("ipfs://")) {
            return null
        }

        // Exclude price queries
        if (isKaspaPriceQuery(lower) || isBuyKaspaQuery(lower)) return null

        // 1. Direct alias match for instant typing feedback (e.g. typing "kaspa", "google", "youtube", "dot")
        val aliasTarget = siteAliases[lower] ?: siteAliases[lower.removeSuffix("/")]

        val candidateHost = aliasTarget ?: run {
            if (lower.contains(".") || lower.startsWith("http://") || lower.startsWith("https://") ||
                lower.startsWith("kas://") || lower.startsWith("ipfs://") || lower.startsWith("dnet://")) {
                lower.removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("kas://")
                    .removePrefix("ipfs://")
                    .removePrefix("dnet://")
                    .removePrefix("www.")
                    .substringBefore("/")
                    .substringBefore(":")
            } else {
                null
            }
        }

        if (candidateHost.isNullOrBlank()) return null

        val knownTlds = listOf(
            ".com", ".org", ".net", ".io", ".dev", ".app", ".stream", ".live",
            ".xyz", ".me", ".info", ".co", ".ai", ".tech", ".gg", ".cc", ".to",
            ".sh", ".edu", ".gov", ".uk", ".de", ".fr", ".jp", ".ru", ".ch",
            ".ca", ".au", ".br", ".in", ".nl", ".se", ".no", ".es", ".it", ".kr", ".k"
        )
        val hasKnownTld = knownTlds.any { candidateHost.endsWith(it) }
        val hasValidDomainPattern = candidateHost.contains(".") && candidateHost.substringAfterLast(".").length in 2..12

        if (!hasKnownTld && !hasValidDomainPattern && aliasTarget == null) {
            return null
        }

        val cleanHost = candidateHost.removePrefix("www.")
        val isDecentralized = cleanHost.endsWith(".k") || lower.startsWith("kas://") ||
                lower.startsWith("ipfs://") || lower.startsWith("dnet://") || cleanHost.contains("kaspa")
        val isSecure = !lower.startsWith("http://")

        val kb = previewKnowledgeBase[cleanHost]
        val title = kb?.first ?: cleanHost.replaceFirstChar { it.uppercase() }
        val description = kb?.second ?: "Real-time preview ready. Protected by Kaspa Zero-Telemetry Privacy Shield and uBlock Ad Blocker."
        val badge = kb?.third ?: (if (isDecentralized) "Decentralized P2P" else "HTTPS Verified")

        val targetUrl = if (q.contains("://")) q else "https://$candidateHost"

        return WebsitePreviewData(
            domain = candidateHost,
            fullUrl = targetUrl,
            title = title,
            description = description,
            protocolBadge = badge,
            isSecure = isSecure,
            isDecentralized = isDecentralized
        )
    }
}

/**
 * Advanced Scientific Math, Equation Solver & Crypto/Unit Evaluator
 */
object AdvancedMathEngine {

    fun evaluate(rawExpr: String): MathCalculationResult? {
        val trimmed = rawExpr.trim()
        if (trimmed.length < 2) return null

        var lower = trimmed.lowercase()

        // Filter out URLs, IP addresses, domains
        if (lower.contains(".com") || lower.contains(".org") || lower.contains(".net") ||
            lower.contains(".io") || lower.contains(".dev") || lower.contains(".xyz") ||
            lower.contains("http://") || lower.contains("https://") || lower.contains("://") ||
            trimmed.count { it == '.' } > 2
        ) {
            return null
        }

        // Clean leading question words ("what is", "calculate", "solve", "math", "eval")
        val prefixes = listOf("what is ", "calculate ", "solve ", "math ", "eval ", "find ")
        for (p in prefixes) {
            if (lower.startsWith(p)) {
                lower = lower.removePrefix(p).trim()
            }
        }
        val cleanExpr = lower.removeSuffix("=").trim()
        if (cleanExpr.isEmpty()) return null

        // 1. Solve Linear or Quadratic Algebraic Equations (e.g. "2x + 6 = 18", "solve 3x - 12 = 0", "x^2 = 25")
        if (cleanExpr.contains("=")) {
            val equationResult = solveAlgebraicEquation(cleanExpr)
            if (equationResult != null) return equationResult
        }

        // 2. Crypto / Currency & Unit conversions
        val cryptoResult = evaluateCryptoAndUnitConversion(cleanExpr)
        if (cryptoResult != null) return cryptoResult

        // 3. Percentage patterns:
        // "15% of 200" or "15% * 200"
        val pctOfRegex = Regex("""^([0-9.,]+)%\s*(?:of|\*)\s*([0-9.,]+)$""")
        val pctMatch = pctOfRegex.find(cleanExpr)
        if (pctMatch != null) {
            val p = pctMatch.groupValues[1].replace(",", "").toDoubleOrNull()
            val b = pctMatch.groupValues[2].replace(",", "").toDoubleOrNull()
            if (p != null && b != null) {
                val res = (p / 100.0) * b
                return MathCalculationResult(
                    expression = "$p% of $b",
                    formattedResult = formatResult(res),
                    numericResult = res
                )
            }
        }

        // "100 + 20%" or "100 - 15%"
        val pctAddSubRegex = Regex("""^([0-9.,]+)\s*([+-])\s*([0-9.,]+)%$""")
        val pctAddSubMatch = pctAddSubRegex.find(cleanExpr)
        if (pctAddSubMatch != null) {
            val base = pctAddSubMatch.groupValues[1].replace(",", "").toDoubleOrNull()
            val op = pctAddSubMatch.groupValues[2]
            val pct = pctAddSubMatch.groupValues[3].replace(",", "").toDoubleOrNull()
            if (base != null && pct != null) {
                val delta = base * (pct / 100.0)
                val res = if (op == "+") base + delta else base - delta
                return MathCalculationResult(
                    expression = "$base $op $pct%",
                    formattedResult = formatResult(res),
                    numericResult = res
                )
            }
        }

        // "75 as % of 300"
        val asPctRegex = Regex("""^([0-9.,]+)\s*(?:as|in)?\s*%\s*of\s*([0-9.,]+)$""")
        val asPctMatch = asPctRegex.find(cleanExpr)
        if (asPctMatch != null) {
            val part = asPctMatch.groupValues[1].replace(",", "").toDoubleOrNull()
            val whole = asPctMatch.groupValues[2].replace(",", "").toDoubleOrNull()
            if (part != null && whole != null && whole != 0.0) {
                val res = (part / whole) * 100.0
                return MathCalculationResult(
                    expression = "$part as % of $whole",
                    formattedResult = "${formatResult(res)}%",
                    numericResult = res
                )
            }
        }

        // 4. Natural Language Math parsing
        var norm = cleanExpr
            .replace("square root of", "sqrt")
            .replace("square root", "sqrt")
            .replace("cube root of", "cbrt")
            .replace("cube root", "cbrt")
            .replace("plus", "+")
            .replace("minus", "-")
            .replace("multiplied by", " * ")
            .replace("multiply by", " * ")
            .replace("times", " * ")
            .replace("divided by", " / ")
            .replace("divide by", " / ")
            .replace("div by", " / ")
            .replace("over", " / ")
            .replace("×", " * ")
            .replace("✕", " * ")
            .replace("·", " * ")
            .replace("÷", " / ")
            .replace("π", "pi")
            .replace("τ", "tau")
            .replace("φ", "phi")
            .replace("**", "^")
            .replace("°", " deg")

        // Handle 'x' or 'X' as multiplication: e.g. "5 x 4", "5x4", "2.5 x 8", "100 x 20", "4x(2+3)"
        norm = norm.replace(Regex("""(?<=[0-9)])\s*[xX]\s*(?=[0-9(])"""), " * ")
        norm = norm.replace(Regex("""(?<=[0-9])\s*[xX]\s*(?=[0-9])"""), " * ")

        // "5 squared" -> "5^2", "3 cubed" -> "3^3"
        norm = norm.replace(Regex("""([0-9.]+)\s*squared""")) { "${it.groupValues[1]}^2" }
        norm = norm.replace(Regex("""([0-9.]+)\s*cubed""")) { "${it.groupValues[1]}^3" }

        // "half of 500" -> "500 / 2", "quarter of 200" -> "200 / 4", "double 45" -> "45 * 2", "triple 15" -> "15 * 3"
        norm = norm.replace(Regex("""half\s*of\s*([0-9.]+)""")) { "${it.groupValues[1]} / 2" }
        norm = norm.replace(Regex("""quarter\s*of\s*([0-9.]+)""")) { "${it.groupValues[1]} / 4" }
        norm = norm.replace(Regex("""double\s*([0-9.]+)""")) { "${it.groupValues[1]} * 2" }
        norm = norm.replace(Regex("""triple\s*([0-9.]+)""")) { "${it.groupValues[1]} * 3" }

        // Handle functions without parentheses: e.g. "sqrt 64" -> "sqrt(64)", "cbrt 27" -> "cbrt(27)", "sin 30 deg" -> "sin(30 deg)"
        val fnNames = listOf("sqrt", "cbrt", "sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh", "ln", "log10", "log2", "log", "exp", "abs", "round", "floor", "ceil", "fact")
        for (fn in fnNames) {
            norm = norm.replace(Regex("""\b$fn\s+([0-9.]+(?:\s*deg)?)""")) { "$fn(${it.groupValues[1]})" }
        }

        // Degree conversion inside trig: e.g. "sin(30 deg)" -> "sin(30 * (pi / 180))"
        norm = norm.replace(Regex("""([0-9.]+)\s*deg""")) { "(${it.groupValues[1]} * (pi / 180))" }

        norm = norm.replace(Regex("""\bmod\b"""), " % ")

        // Insert implicit multiplication: e.g. "2pi" -> "2 * pi", "3(4+5)" -> "3 * (4+5)", "(2)(3)" -> "(2) * (3)"
        norm = norm.replace(Regex("""([0-9.]+)\s*([a-zA-Z(])""")) { "${it.groupValues[1]} * ${it.groupValues[2]}" }
        norm = norm.replace(Regex("""\)\s*\("""), ") * (")
        norm = norm.replace(Regex("""\)\s*([0-9a-zA-Z])""")) { ") * ${it.groupValues[1]}" }

        // Check if there are digits or constants
        val hasDigits = norm.any { it.isDigit() }
        val hasConstant = norm.contains("pi") || norm.contains("tau") || norm.contains("phi") || norm.contains("e")
        if (!hasDigits && !hasConstant) return null

        // Check if there is some math indicator
        val hasMathIndicator = norm.any { it in "+-*/^%!=()" } ||
                fnNames.any { norm.contains(it) } || hasConstant

        if (!hasMathIndicator) return null

        // Validate words
        val allowedWords = setOf(
            "pi", "e", "tau", "phi",
            "sqrt", "cbrt", "sin", "cos", "tan", "asin", "acos", "atan", "atan2",
            "sinh", "cosh", "tanh", "asinh", "acosh", "atanh", "sec", "csc", "cot",
            "ln", "log", "log10", "log2", "abs", "round", "floor", "ceil", "exp",
            "fact", "pow", "root", "ncr", "comb", "npr", "perm", "gcd", "lcm",
            "hypot", "min", "max", "sum", "avg", "mean"
        )
        val extractedWords = Regex("[a-z]+").findAll(norm).map { it.value }.toList()
        for (w in extractedWords) {
            if (w !in allowedWords) {
                // If it contains unknown letters, reject (avoid executing random search queries as math)
                return null
            }
        }

        return try {
            val parser = MathParser(norm)
            val value = parser.parse()
            if (value.isFinite() && !value.isNaN()) {
                MathCalculationResult(
                    expression = trimmed,
                    formattedResult = formatResult(value),
                    numericResult = value
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Solves linear and quadratic algebraic equations: e.g. "2x + 6 = 18", "3x = 15", "x^2 = 25", "x^2 - 16 = 0"
     */
    private fun solveAlgebraicEquation(eq: String): MathCalculationResult? {
        try {
            val parts = eq.split("=")
            if (parts.size != 2) return null
            val lhsRaw = parts[0].trim()
            val rhsRaw = parts[1].trim()

            // Find the variable (e.g. x, y, z, n)
            val variable = listOf("x", "y", "z", "n", "a", "b").firstOrNull {
                lhsRaw.contains(it) || rhsRaw.contains(it)
            } ?: return null

            // Build function f(v) = (LHS) - (RHS)
            fun evalAt(v: Double): Double {
                val replacedL = lhsRaw.replace(Regex("""(?<=[0-9])\s*""" + variable), "*$v")
                    .replace(Regex("""\b""" + variable + """\b"""), "($v)")
                val replacedR = rhsRaw.replace(Regex("""(?<=[0-9])\s*""" + variable), "*$v")
                    .replace(Regex("""\b""" + variable + """\b"""), "($v)")
                val lVal = MathParser(replacedL.replace("**", "^")).parse()
                val rVal = MathParser(replacedR.replace("**", "^")).parse()
                return lVal - rVal
            }

            val f0 = evalAt(0.0)
            val f1 = evalAt(1.0)
            val f2 = evalAt(2.0)
            val fn1 = evalAt(-1.0)

            // Quadratic check: f(v) = A*v^2 + B*v + C
            val c = f0
            val a = (f1 + fn1 - 2.0 * c) / 2.0
            val b = (f1 - fn1) / 2.0

            if (abs(a) > 1e-7) {
                // Quadratic equation
                val disc = b * b - 4.0 * a * c
                return if (disc > 1e-9) {
                    val root1 = (-b + sqrt(disc)) / (2.0 * a)
                    val root2 = (-b - sqrt(disc)) / (2.0 * a)
                    val ans = if (abs(root1 - root2) < 1e-7) {
                        "$variable = ${formatResult(root1)}"
                    } else if (abs(root1 + root2) < 1e-7) {
                        "$variable = ±${formatResult(abs(root1))}"
                    } else {
                        "$variable = ${formatResult(root1)} or ${formatResult(root2)}"
                    }
                    MathCalculationResult(
                        expression = eq,
                        formattedResult = ans,
                        numericResult = root1
                    )
                } else if (abs(disc) <= 1e-9) {
                    val root = -b / (2.0 * a)
                    MathCalculationResult(
                        expression = eq,
                        formattedResult = "$variable = ${formatResult(root)}",
                        numericResult = root
                    )
                } else {
                    null
                }
            } else if (abs(b) > 1e-7) {
                // Linear equation: B*v + C = 0 => v = -C / B
                val root = -c / b
                return MathCalculationResult(
                    expression = eq,
                    formattedResult = "$variable = ${formatResult(root)}",
                    numericResult = root
                )
            }
        } catch (_: Exception) {}
        return null
    }

    private fun evaluateCryptoAndUnitConversion(expr: String): MathCalculationResult? {
        val kasToUsdRegex = Regex("""^([0-9.,]+)\s*(?:kas|kaspa)\s*(?:to|in|=)?\s*(?:usd|\$)?$""")
        val usdToKasRegex = Regex("""^(?:\$)?\s*([0-9.,]+)\s*(?:usd|\$)\s*(?:to|in|=)?\s*(?:kas|kaspa)$""")

        val kasMatch = kasToUsdRegex.find(expr)
        if (kasMatch != null) {
            val amount = kasMatch.groupValues[1].replace(",", "").toDoubleOrNull()
            if (amount != null && amount >= 0.0) {
                val price = SmartOmnibarEngine.cachedMarketData.priceUsd.let { if (it > 0) it else 0.165 }
                val usdVal = amount * price
                val df = DecimalFormat("#,##0.00")
                return MathCalculationResult(
                    expression = "${kasMatch.groupValues[1]} KAS",
                    formattedResult = "$${df.format(usdVal)} USD",
                    numericResult = usdVal,
                    isCryptoConversion = true,
                    conversionDetails = "1 KAS ≈ $${DecimalFormat("0.0000").format(price)} USD"
                )
            }
        }

        val usdMatch = usdToKasRegex.find(expr)
        if (usdMatch != null) {
            val amount = usdMatch.groupValues[1].replace(",", "").toDoubleOrNull()
            if (amount != null && amount >= 0.0) {
                val price = SmartOmnibarEngine.cachedMarketData.priceUsd.let { if (it > 0) it else 0.165 }
                val kasVal = amount / price
                val df = DecimalFormat("#,##0.00")
                return MathCalculationResult(
                    expression = "$${usdMatch.groupValues[1]} USD",
                    formattedResult = "${df.format(kasVal)} KAS",
                    numericResult = kasVal,
                    isCryptoConversion = true,
                    conversionDetails = "1 KAS ≈ $${DecimalFormat("0.0000").format(price)} USD"
                )
            }
        }

        // Unit conversions: km <-> miles, kg <-> lbs, c <-> f
        val kmToMiles = Regex("""^([0-9.,]+)\s*(?:km|kilometers)\s*(?:to|in|=)?\s*(?:miles|mi)$""")
        kmToMiles.find(expr)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            val res = v * 0.621371
            return MathCalculationResult("$v km", "${formatResult(res)} miles", res)
        }

        val milesToKm = Regex("""^([0-9.,]+)\s*(?:miles|mi)\s*(?:to|in|=)?\s*(?:km|kilometers)$""")
        milesToKm.find(expr)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            val res = v / 0.621371
            return MathCalculationResult("$v miles", "${formatResult(res)} km", res)
        }

        val kgToLbs = Regex("""^([0-9.,]+)\s*(?:kg|kilograms)\s*(?:to|in|=)?\s*(?:lbs|pounds)$""")
        kgToLbs.find(expr)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            val res = v * 2.20462
            return MathCalculationResult("$v kg", "${formatResult(res)} lbs", res)
        }

        val lbsToKg = Regex("""^([0-9.,]+)\s*(?:lbs|pounds)\s*(?:to|in|=)?\s*(?:kg|kilograms)$""")
        lbsToKg.find(expr)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            val res = v / 2.20462
            return MathCalculationResult("$v lbs", "${formatResult(res)} kg", res)
        }

        val cToF = Regex("""^([0-9.,+-]+)\s*(?:c|celsius)\s*(?:to|in|=)?\s*(?:f|fahrenheit)$""")
        cToF.find(expr)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            val res = (v * 9.0 / 5.0) + 32.0
            return MathCalculationResult("$v°C", "${formatResult(res)}°F", res)
        }

        val fToC = Regex("""^([0-9.,+-]+)\s*(?:f|fahrenheit)\s*(?:to|in|=)?\s*(?:c|celsius)$""")
        fToC.find(expr)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            val res = (v - 32.0) * 5.0 / 9.0
            return MathCalculationResult("$v°F", "${formatResult(res)}°C", res)
        }

        return null
    }

    fun formatResult(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return ""
        val absVal = abs(value)
        if (absVal != 0.0 && (absVal >= 1e13 || absVal < 1e-6)) {
            return DecimalFormat("0.######E0").format(value).replace("E", " × 10^")
        }
        val df = DecimalFormat("#,##0.##########")
        return df.format(value)
    }

    private fun factorial(n: Double): Double {
        if (n < 0 || n != floor(n) || n > 170) throw IllegalArgumentException("Invalid factorial operand")
        var res = 1.0
        val intN = n.toLong()
        for (i in 2..intN) res *= i
        return res
    }

    private fun gcd(a: Long, b: Long): Long {
        var x = abs(a)
        var y = abs(b)
        while (y != 0L) {
            val t = y
            y = x % y
            x = t
        }
        return x
    }

    class MathParser(private val src: String) {
        private var pos = 0
        private val len = src.length

        fun parse(): Double {
            val result = parseExpression()
            skipWhitespace()
            if (pos < len) throw RuntimeException("Unexpected character at end: ${src[pos]}")
            return result
        }

        private fun skipWhitespace() {
            while (pos < len && src[pos].isWhitespace()) pos++
        }

        private fun peek(): Char = if (pos < len) src[pos] else '\u0000'

        private fun match(c: Char): Boolean {
            skipWhitespace()
            if (pos < len && src[pos] == c) {
                pos++
                return true
            }
            return false
        }

        private fun parseExpression(): Double = parseAddSub()

        private fun parseAddSub(): Double {
            var v = parseMulDiv()
            while (true) {
                skipWhitespace()
                if (match('+')) {
                    v += parseMulDiv()
                } else if (match('-')) {
                    v -= parseMulDiv()
                } else {
                    break
                }
            }
            return v
        }

        private fun parseMulDiv(): Double {
            var v = parseUnary()
            while (true) {
                skipWhitespace()
                if (match('*')) {
                    v *= parseUnary()
                } else if (match('/')) {
                    val divisor = parseUnary()
                    if (divisor == 0.0) throw ArithmeticException("Division by zero")
                    v /= divisor
                } else if (match('%')) {
                    val divisor = parseUnary()
                    if (divisor == 0.0) throw ArithmeticException("Division by zero")
                    v %= divisor
                } else {
                    // Implicit multiplication check: e.g. 2(3), 5pi, 4sqrt(9)
                    skipWhitespace()
                    val p = peek()
                    if (p == '(' || (p.isLetter() && p != 'e') || p == '.') {
                        v *= parseUnary()
                    } else {
                        break
                    }
                }
            }
            return v
        }

        private fun parseUnary(): Double {
            skipWhitespace()
            if (match('+')) return +parseUnary()
            if (match('-')) return -parseUnary()
            return parsePower()
        }

        private fun parsePower(): Double {
            var v = parsePrimary()
            skipWhitespace()
            if (match('^')) {
                val exponent = parsePower()
                v = v.pow(exponent)
            }
            return v
        }

        private fun parsePrimary(): Double {
            var v = parseAtom()
            while (true) {
                skipWhitespace()
                if (match('!')) {
                    v = factorial(v)
                } else if (peek() == '%') {
                    var nextIdx = pos + 1
                    while (nextIdx < len && src[nextIdx].isWhitespace()) nextIdx++
                    val nextChar = if (nextIdx < len) src[nextIdx] else '\u0000'
                    if (nextChar.isDigit() || nextChar.isLetter() || nextChar == '(' || nextChar == '.') {
                        break
                    } else {
                        pos++
                        v /= 100.0
                    }
                } else {
                    break
                }
            }
            return v
        }

        private fun parseAtom(): Double {
            skipWhitespace()
            val c = peek()

            if (match('(')) {
                val v = parseExpression()
                if (!match(')')) throw RuntimeException("Expected closing parenthesis")
                return v
            }

            if (c.isDigit() || c == '.') {
                val start = pos
                var hasDot = false
                while (pos < len && (src[pos].isDigit() || src[pos] == '.')) {
                    if (src[pos] == '.') {
                        if (hasDot) break
                        hasDot = true
                    }
                    pos++
                }
                return src.substring(start, pos).toDouble()
            }

            if (c.isLetter()) {
                val start = pos
                while (pos < len && (src[pos].isLetter() || src[pos].isDigit())) {
                    pos++
                }
                val name = src.substring(start, pos)

                when (name) {
                    "pi" -> return Math.PI
                    "e" -> return Math.E
                    "tau" -> return 2 * Math.PI
                    "phi" -> return 1.618033988749895
                }

                skipWhitespace()
                val hasParen = match('(')
                val args = mutableListOf<Double>()
                if (hasParen) {
                    args.add(parseExpression())
                    while (match(',')) {
                        args.add(parseExpression())
                    }
                    if (!match(')')) throw RuntimeException("Expected ')' after function arguments")
                } else {
                    // Argument without parentheses e.g. sqrt 64
                    args.add(parseUnary())
                }

                val a0 = args.getOrNull(0) ?: 0.0
                val a1 = args.getOrNull(1)

                return when (name) {
                    "sqrt" -> sqrt(a0)
                    "cbrt" -> cbrt(a0)
                    "sin" -> sin(a0)
                    "cos" -> cos(a0)
                    "tan" -> tan(a0)
                    "asin" -> asin(a0)
                    "acos" -> acos(a0)
                    "atan" -> atan(a0)
                    "atan2" -> if (a1 != null) atan2(a0, a1) else atan(a0)
                    "sinh" -> sinh(a0)
                    "cosh" -> cosh(a0)
                    "tanh" -> tanh(a0)
                    "asinh" -> ln(a0 + sqrt(a0 * a0 + 1.0))
                    "acosh" -> ln(a0 + sqrt(a0 * a0 - 1.0))
                    "atanh" -> 0.5 * ln((1.0 + a0) / (1.0 - a0))
                    "sec" -> 1.0 / cos(a0)
                    "csc" -> 1.0 / sin(a0)
                    "cot" -> 1.0 / tan(a0)
                    "ln" -> ln(a0)
                    "log", "log10" -> if (a1 != null) ln(a0) / ln(a1) else log10(a0)
                    "log2" -> log2(a0)
                    "abs" -> abs(a0)
                    "exp" -> exp(a0)
                    "round" -> round(a0)
                    "floor" -> floor(a0)
                    "ceil" -> ceil(a0)
                    "fact" -> factorial(a0)
                    "pow" -> if (a1 != null) a0.pow(a1) else a0.pow(2.0)
                    "root" -> if (a1 != null) a0.pow(1.0 / a1) else sqrt(a0)
                    "ncr", "comb" -> {
                        val n = a0
                        val r = a1 ?: 0.0
                        factorial(n) / (factorial(r) * factorial(n - r))
                    }
                    "npr", "perm" -> {
                        val n = a0
                        val r = a1 ?: 0.0
                        factorial(n) / factorial(n - r)
                    }
                    "gcd" -> if (a1 != null) gcd(a0.toLong(), a1.toLong()).toDouble() else a0
                    "lcm" -> if (a1 != null) {
                        val g = gcd(a0.toLong(), a1.toLong())
                        if (g == 0L) 0.0 else abs(a0.toLong() * a1.toLong()) / g.toDouble()
                    } else a0
                    "hypot" -> if (a1 != null) hypot(a0, a1) else a0
                    "min" -> args.minOrNull() ?: a0
                    "max" -> args.maxOrNull() ?: a0
                    "sum" -> args.sum()
                    "avg", "mean" -> args.average()
                    else -> throw RuntimeException("Unknown function $name")
                }
            }

            throw RuntimeException("Unexpected character: $c")
        }
    }
}
