package com.example.data

import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class KaspaNewsItem(
    val title: String,
    val desc: String,
    val url: String,
    val category: String, // "Reddit", "GitHub", "X", "YouTube", "News"
    val timestamp: String,
    val author: String = "",
    val videoId: String? = null,
    val duration: String? = null,
    val epochMillis: Long = System.currentTimeMillis()
)

fun NewsArticleEntity.toKaspaNewsItem(): KaspaNewsItem = KaspaNewsItem(
    title = title,
    desc = desc,
    url = url,
    category = category,
    timestamp = timestamp,
    author = author,
    videoId = videoId,
    duration = duration,
    epochMillis = epochMillis
)

fun KaspaNewsItem.toEntity(): NewsArticleEntity = NewsArticleEntity(
    deduplicationKey = getKaspaNewsDeduplicationKey(this),
    title = title,
    desc = desc,
    url = url,
    category = category,
    timestamp = timestamp,
    author = author,
    videoId = videoId,
    duration = duration,
    epochMillis = epochMillis
)

fun extractTagContent(xml: String, tagName: String): String {
    val regex = Regex("<$tagName(?:\\s+[^>]*)?>(.*?)</$tagName>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val match = regex.find(xml)
    if (match != null) {
        return cleanXmlText(match.groupValues[1])
    }
    return ""
}

fun extractLinkUrl(xml: String): String {
    val hrefMatch = Regex("<link[^>]+href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(xml)
    var url = ""
    if (hrefMatch != null) {
        url = cleanXmlText(hrefMatch.groupValues[1]).trim()
    } else {
        val content = extractTagContent(xml, "link")
        if (content.isNotEmpty()) url = content.trim()
    }
    if (url.contains("nitter.") || url.contains("xcancel.com") || url.contains("twitter.com")) {
        val path = url.substringAfter("://").substringAfter("/")
        if (path.isNotBlank()) {
            return "https://x.com/$path"
        }
    }
    return url
}

fun formatEpochToDisplay(epochMillis: Long): String {
    val diff = System.currentTimeMillis() - epochMillis
    if (diff < 0) return "Just now"
    if (diff < 60_000) return "Just now"
    if (diff < 3600_000) return "${diff / 60_000}m ago"
    if (diff < 86400_000) return "${diff / 3600_000}h ago"
    val days = diff / 86400_000
    if (days < 7) return "${days}d ago"
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    return sdf.format(java.util.Date(epochMillis))
}

fun parseDateToEpoch(dateStr: String): Long {
    if (dateStr.isBlank() || dateStr == "Recently" || dateStr == "Just now") return System.currentTimeMillis()
    val formats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss z",
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ss",
        "MMM dd, yyyy"
    )
    for (fmt in formats) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val parsed = sdf.parse(dateStr)
            if (parsed != null) return parsed.time
        } catch (_: Exception) {}
    }
    return System.currentTimeMillis()
}

fun extractYouTubeVideoId(url: String): String? {
    val clean = url.trim()
    if (clean.isBlank() || clean.equals("undefined", ignoreCase = true) || clean.equals("null", ignoreCase = true)) return null
    if (clean.matches(Regex("^[a-zA-Z0-9_-]{11}$")) && !clean.equals("undefined", ignoreCase = true)) return clean
    val vMatch = Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(clean)
    if (vMatch != null && !vMatch.groupValues[1].equals("undefined", ignoreCase = true)) return vMatch.groupValues[1]
    val beMatch = Regex("youtu\\.be/([a-zA-Z0-9_-]{11})").find(clean)
    if (beMatch != null && !beMatch.groupValues[1].equals("undefined", ignoreCase = true)) return beMatch.groupValues[1]
    val embedMatch = Regex("embed/([a-zA-Z0-9_-]{11})").find(clean)
    if (embedMatch != null && !embedMatch.groupValues[1].equals("undefined", ignoreCase = true)) return embedMatch.groupValues[1]
    return null
}

fun parseRssXml(xml: String, defaultCategory: String): List<KaspaNewsItem> {
    val items = mutableListOf<KaspaNewsItem>()
    try {
        var index = 0
        while (index < xml.length) {
            var itemStart = xml.indexOf("<item>", index)
            var isAtom = false
            if (itemStart == -1) {
                itemStart = xml.indexOf("<entry>", index)
                isAtom = true
            }
            if (itemStart == -1) break

            val itemEnd = if (isAtom) {
                xml.indexOf("</entry>", itemStart)
            } else {
                xml.indexOf("</item>", itemStart)
            }
            if (itemEnd == -1) break

            val itemXml = xml.substring(itemStart, itemEnd)
            index = itemEnd

            var title = extractTagContent(itemXml, "title")
            if (title.isEmpty()) title = extractTagContent(itemXml, "media:title")

            val link = extractLinkUrl(itemXml)

            var desc = extractTagContent(itemXml, "description")
            if (desc.isEmpty()) desc = extractTagContent(itemXml, "summary")
            if (desc.isEmpty()) desc = extractTagContent(itemXml, "content")
            if (desc.isEmpty()) desc = extractTagContent(itemXml, "media:description")
            if (desc.length > 200) {
                desc = desc.take(197) + "..."
            }

            var rawDate = extractTagContent(itemXml, "pubDate")
            if (rawDate.isEmpty()) rawDate = extractTagContent(itemXml, "updated")
            if (rawDate.isEmpty()) rawDate = extractTagContent(itemXml, "published")
            if (rawDate.isEmpty()) rawDate = extractTagContent(itemXml, "dc:date")

            val epochMillis = parseDateToEpoch(rawDate)
            val displayDate = if (rawDate.isNotBlank()) formatEpochToDisplay(epochMillis) else "Recently"

            var author = extractTagContent(itemXml, "name")
            if (author.isEmpty()) author = extractTagContent(itemXml, "author")
            if (author.isEmpty()) author = extractTagContent(itemXml, "dc:creator")
            if (author.isEmpty()) author = extractTagContent(itemXml, "source")
            if (author.isEmpty() && defaultCategory == "News") author = "Kaspa News"

            var videoId: String? = extractTagContent(itemXml, "yt:videoId").trim().takeIf {
                it.isNotBlank() && !it.equals("undefined", ignoreCase = true) && !it.equals("null", ignoreCase = true)
            }
            if (videoId == null && link.isNotEmpty() && !link.equals("undefined", ignoreCase = true)) {
                videoId = extractYouTubeVideoId(link)
            }

            val finalCategory = if (!videoId.isNullOrBlank() || defaultCategory == "YouTube" || link.contains("youtube.com") || link.contains("youtu.be")) {
                "YouTube"
            } else if (link.contains("x.com") || link.contains("twitter.com") || link.contains("nitter") || defaultCategory == "X") {
                "X"
            } else if (defaultCategory == "Reddit" || link.contains("reddit.com")) {
                "Reddit"
            } else if (defaultCategory == "GitHub" || link.contains("github.com")) {
                "GitHub"
            } else {
                if (defaultCategory.isBlank()) "Kaspa News" else defaultCategory
            }

            if (title.isNotBlank()) {
                items.add(
                    KaspaNewsItem(
                        title = title,
                        desc = desc.ifBlank { "Click to view full blockDAG update." },
                        url = link.ifBlank { "https://kaspa.org" },
                        category = finalCategory,
                        timestamp = displayDate,
                        author = author,
                        videoId = videoId,
                        epochMillis = epochMillis
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return items
}

fun unescapeHtmlEntities(input: String): String {
    if (input.isBlank()) return ""
    return try {
        @Suppress("DEPRECATION")
        android.text.Html.fromHtml(input).toString()
    } catch (_: Throwable) {
        input.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#32;", " ")
            .replace("&nbsp;", " ")
    }
}

fun cleanXmlText(text: String): String {
    if (text.isBlank()) return ""
    var cleaned = text

    while (cleaned.contains("<![CDATA[")) {
        cleaned = cleaned.replace(Regex("<!\\[CDATA\\[(.*?)\\]\\]>", RegexOption.DOT_MATCHES_ALL)) { match ->
            match.groupValues[1]
        }
    }

    cleaned = unescapeHtmlEntities(cleaned)
    cleaned = cleaned.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    cleaned = cleaned.replace(Regex("<[^>]+>"), " ")
    cleaned = unescapeHtmlEntities(cleaned)
    cleaned = cleaned.replace(Regex("submitted by\\s+/u/\\S+", RegexOption.IGNORE_CASE), "")
    cleaned = cleaned.replace(Regex("\\[link\\]", RegexOption.IGNORE_CASE), "")
    cleaned = cleaned.replace(Regex("\\[comments\\]", RegexOption.IGNORE_CASE), "")
    cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

    return cleaned
}

fun getKaspaNewsDeduplicationKey(item: KaspaNewsItem): String {
    val cleanTitle = item.title.lowercase().replace(Regex("[^a-z0-9]"), "")
    return if (cleanTitle.length > 8) cleanTitle else item.url.lowercase().trim()
}

fun getDefaultCuratedNews(): List<KaspaNewsItem> = listOf(
    KaspaNewsItem(
        title = "@KaspaCurrency: Kaspad v0.15.2 released with DagKnight sync optimizations and mainnet BPS enhancements",
        desc = "Latest node update delivers major performance improvements for peer sync, UTXO set validation, and block propagation latency.",
        url = "https://x.com/KaspaCurrency",
        category = "X",
        timestamp = "Sep 10, 2026",
        author = "@KaspaCurrency",
        epochMillis = 1788998400000L
    ),
    KaspaNewsItem(
        title = "@Kaspa_Ecosystem: \$KAS ecosystem surges with 10M+ KRC-20 transactions and zero congestion",
        desc = "High-speed BlockDAG transaction throughput easily handles millions of smart token transfers without fee spikes or network backlog.",
        url = "https://x.com/Kaspa_Ecosystem",
        category = "X",
        timestamp = "Sep 10, 2026",
        author = "@Kaspa_Ecosystem",
        epochMillis = 1788998200000L
    ),
    KaspaNewsItem(
        title = "kaspanet/kaspad: Release v0.15.2 mainnet binaries & DagKnight DAG engine",
        desc = "Official release binaries compiled with Rust 1.80. High-performance peer-to-peer block ordering with zero latency assumptions.",
        url = "https://github.com/kaspanet/kaspad",
        category = "GitHub",
        timestamp = "Sep 10, 2026",
        author = "shaiwy",
        epochMillis = 1788998000000L
    ),
    KaspaNewsItem(
        title = "@YonatanSompo: \$KAS Proof-of-Work solves Satoshi's original scaling vision without compromises",
        desc = "By structuring blocks into an acyclic graph rather than an isolated single chain, Kaspa enables parallel block creation with mathematical consensus security.",
        url = "https://x.com/YonatanSompo",
        category = "X",
        timestamp = "Sep 10, 2026",
        author = "@YonatanSompo",
        epochMillis = 1788997500000L
    ),
    KaspaNewsItem(
        title = "r/kaspa: Kaspad v0.15.2 is live! DagKnight performance tests inside",
        desc = "Community node operators reporting 30% reduction in sync times and ultra-low RAM usage across desktop and server nodes.",
        url = "https://reddit.com/r/kaspa",
        category = "Reddit",
        timestamp = "Sep 10, 2026",
        author = "u/BlockDAGLover",
        epochMillis = 1788997000000L
    ),
    KaspaNewsItem(
        title = "@DesheShai: DagKnight formal security proofs published: parameterless PoW BlockDAG",
        desc = "Zero latency bounds, adaptive ordering, and sub-second confirmation speed. Proof-of-Work has reached its theoretical optimum.",
        url = "https://x.com/DesheShai",
        category = "X",
        timestamp = "Sep 09, 2026",
        author = "@DesheShai",
        epochMillis = 1788913000000L
    ),
    KaspaNewsItem(
        title = "Kaspa BPS Upgrade & DagKnight Consensus Live Demo",
        desc = "Dr. Yonatan Sompolinsky and core developers demonstrate parameterless proof-of-work DAG consensus achieving unprecedented throughput.",
        url = "https://www.youtube.com/watch?v=By_Zw58PN6o",
        category = "YouTube",
        timestamp = "Sep 10, 2026",
        author = "Kaspa Official",
        videoId = "By_Zw58PN6o",
        duration = "16:45",
        epochMillis = 1788996000000L
    ),
    KaspaNewsItem(
        title = "@Kaspa_Ecosystem: New decentralised bridge & KCC-20 indexer live on testnet",
        desc = "Developers can now build cross-chain dApps on Kaspa BlockDAG with sub-second finality and zero latency overhead.",
        url = "https://x.com/Kaspa_Ecosystem",
        category = "X",
        timestamp = "Sep 09, 2026",
        author = "@Kaspa_Ecosystem",
        epochMillis = 1788912000000L
    ),
    KaspaNewsItem(
        title = "@KaspaCurrency: \$KAS mining network hash rate reaches historic all-time high",
        desc = "Global ASIC and decentralised mining pool distribution reinforces Kaspa as the fastest and most secure PoW layer in existence.",
        url = "https://x.com/KaspaCurrency",
        category = "X",
        timestamp = "Sep 08, 2026",
        author = "@KaspaCurrency",
        epochMillis = 1788825600000L
    ),
    KaspaNewsItem(
        title = "Yonatan Sompolinsky at AusCryptoCon: BlockDAG & Scalability",
        desc = "Dr. Yonatan Sompolinsky discusses the fundamentals of BlockDAG architecture, parameterless consensus, and high throughput decentralization.",
        url = "https://www.youtube.com/watch?v=By_Zw58PN6o",
        category = "YouTube",
        timestamp = "Sep 07, 2026",
        author = "Kaspa Official",
        videoId = "By_Zw58PN6o",
        duration = "14:20",
        epochMillis = 1788739200000L
    ),
    KaspaNewsItem(
        title = "Kaspa Commons X Space Featuring Kaskad",
        desc = "Community discussion covering the latest network upgrades, ecosystem development, and decentralized applications.",
        url = "https://www.youtube.com/watch?v=BbUSm6inXhg",
        category = "YouTube",
        timestamp = "Sep 06, 2026",
        author = "Kaspa Official",
        videoId = "BbUSm6inXhg",
        duration = "18:45",
        epochMillis = 1788652800000L
    ),
    KaspaNewsItem(
        title = "@KaspaCurrency: DagKnight consensus protocol adapts dynamically to live internet latency",
        desc = "Parameterless proof-of-work is the ultimate solution to the blockchain trilemma. Sub-second confirmations without hardcoded assumptions.",
        url = "https://x.com/KaspaCurrency",
        category = "X",
        timestamp = "Sep 07, 2026",
        author = "@KaspaCurrency",
        epochMillis = 1788739200000L
    ),
    KaspaNewsItem(
        title = "@Kaspa_Ecosystem: KCC-20 token indexer performance hits record highs",
        desc = "Community node operators have processed millions of KCC-20 requests seamlessly. High-speed DAG token minting and smart contracts at scale.",
        url = "https://x.com/KaspaCurrency",
        category = "X",
        timestamp = "Sep 06, 2026",
        author = "@Kaspa_Ecosystem",
        epochMillis = 1788652800000L
    ),
    KaspaNewsItem(
        title = "kaspanet/rusty-kaspa: DagKnight consensus dynamic ordering engine (PR #2491)",
        desc = "Parameterless DAG reachability tree and adaptive confirmation times. Mainnet benchmark tests achieving 32 blocks per second.",
        url = "https://github.com/kaspanet/kaspad",
        category = "GitHub",
        timestamp = "Sep 06, 2026",
        author = "shaiwy",
        epochMillis = 1788652800000L
    ),
    KaspaNewsItem(
        title = "kaspa-core/kcc20-protocol: Release v1.2.0-alpha for smart contracts",
        desc = "High-throughput token inscription standard, automated UTXO batching and validation engine for KCC-20 composable contracts.",
        url = "https://github.com/kaspanet/kaspad",
        category = "GitHub",
        timestamp = "Sep 05, 2026",
        author = "michaels",
        epochMillis = 1788566400000L
    ),
    KaspaNewsItem(
        title = "@YonatanSompo: DagKnight achieves near-optimal 49% BFT security",
        desc = "Unlike protocols with fixed latency bounds, DagKnight dynamically tightens confirmation times as network conditions improve.",
        url = "https://x.com/YonatanSompo",
        category = "X",
        timestamp = "Sep 05, 2026",
        author = "@YonatanSompo",
        epochMillis = 1788566400000L
    ),
    KaspaNewsItem(
        title = "r/kaspa: DagKnight is the true endgame for Proof-of-Work scalability",
        desc = "Why parameterless consensus changes everything: zero latency assumptions, dynamic confirmation times, and 100 BPS capability.",
        url = "https://reddit.com/r/kaspa",
        category = "Reddit",
        timestamp = "Sep 07, 2026",
        author = "u/DagMaster",
        epochMillis = 1788739200000L
    ),
    KaspaNewsItem(
        title = "r/kaspa: KCC-20 tokens are taking off! What are your favorite projects?",
        desc = "Community discussion about newly launched KCC-20 projects, volume milestones, and decentralized indexer incentives.",
        url = "https://reddit.com/r/kaspa",
        category = "Reddit",
        timestamp = "Sep 06, 2026",
        author = "u/BlockExplorer",
        epochMillis = 1788652800000L
    ),
    KaspaNewsItem(
        title = "Kaspa Core Research: High-Throughput GHOSTDAG and Sub-Second Confirmations",
        desc = "An in-depth technical dive into the DAG mathematical formulation and fast confirmation guarantees under adversarial conditions.",
        url = "https://medium.com/@kaspanet",
        category = "News",
        timestamp = "Aug 28, 2026",
        author = "Kaspa Research",
        epochMillis = 1787875200000L
    ),
    KaspaNewsItem(
        title = "kaspanet/kaspad: Rust Node Engine Complete Transition Milestone",
        desc = "Full deprecation of legacy Go node code base in favor of high-speed multi-threaded Rust p2p engine.",
        url = "https://github.com/kaspanet/kaspad",
        category = "GitHub",
        timestamp = "Aug 15, 2026",
        author = "shaiwy",
        epochMillis = 1786752000000L
    )
).distinctBy { getKaspaNewsDeduplicationKey(it) }

suspend fun fetchLatestKaspaFeeds(): List<KaspaNewsItem> = withContext(Dispatchers.IO) {
    val baseOkHttpClient = okhttp3.OkHttpClient.Builder()
        .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
        .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
    val client = com.example.network.CronetClientFactory.buildClient(baseOkHttpClient)

    val feeds = listOf(
        Pair("https://kaspa.org/feed/", "News"),
        Pair("https://medium.com/feed/@kaspanet", "News"),
        Pair("https://www.reddit.com/r/kaspa/.rss", "Reddit"),
        Pair("https://www.reddit.com/r/KaspaCurrency/.rss", "Reddit"),
        Pair("https://github.com/kaspanet/kaspad/commits/master.atom", "GitHub"),
        Pair("https://github.com/kaspanet/rusty-kaspa/commits/master.atom", "GitHub"),
        Pair("https://github.com/kaspanet/kaspad/releases.atom", "GitHub"),
        Pair("https://github.com/kaspanet/rusty-kaspa/releases.atom", "GitHub"),
        Pair("https://www.youtube.com/feeds/videos.xml?channel_id=UCsnbLKm_lpCUj63_HPW17og", "YouTube"),
        Pair("https://www.youtube.com/feeds/videos.xml?channel_id=UCZ-FjVIxrICs_FmJUGL3R-Q", "YouTube"),
        Pair("https://cointelegraph.com/rss/tag/kaspa", "News"),
        Pair("https://coingape.com/tag/kaspa/feed/", "News"),
        Pair("https://xcancel.com/KaspaCurrency/rss", "X"),
        Pair("https://xcancel.com/Kaspa_Ecosystem/rss", "X")
    )

    val list: MutableList<KaspaNewsItem> = coroutineScope {
        feeds.map { (url, cat) ->
            async(Dispatchers.IO) {
                val feedItems = mutableListOf<KaspaNewsItem>()
                try {
                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) KaspaBrowser/1.0")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string() ?: ""
                            feedItems.addAll(parseRssXml(bodyStr, cat))
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.d("KaspaNews", "Feed update notice for $url: ${e.message}")
                }
                feedItems
            }
        }.awaitAll().flatten().toMutableList()
    }
    list
}
