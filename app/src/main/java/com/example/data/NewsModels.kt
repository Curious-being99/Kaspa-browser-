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
    val imageUrl: String? = null,
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
    imageUrl = imageUrl,
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
    imageUrl = imageUrl,
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
    // RSS item links are direct text nodes; read them before Atom metadata links.
    // Atom rel="self" points to a feed representation, not the article.
    val rssLink = Regex(
        "<link(?:\\s[^>]*)?>(.*?)</link>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).find(xml)?.groupValues?.get(1)?.let(::cleanXmlText)?.trim().orEmpty()

    val linkTags = Regex("<(?:[A-Za-z_][A-Za-z0-9_.-]*:)?link[^>]*>", RegexOption.IGNORE_CASE).findAll(xml).toList()
    val hrefRegex = Regex("""\bhref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    val relRegex = Regex("""\brel\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    val alternateTag = linkTags.firstOrNull { tag ->
        val rel = relRegex.find(tag.value)?.groupValues?.get(1)
        (rel == null || rel.equals("alternate", ignoreCase = true)) &&
            hrefRegex.containsMatchIn(tag.value)
    }
    var url = rssLink
    if (url.isBlank()) {
        url = alternateTag?.let {
            hrefRegex.find(it.value)?.groupValues?.get(1)?.let(::cleanXmlText)?.trim()
        }.orEmpty()
    }
    if (url.contains("nitter.") || url.contains("xcancel.com") || url.contains("twitter.com")) {
        val path = url.substringAfter("://").substringAfter("/")
        if (path.isNotBlank()) return "https://x.com/$path"
    }
    return url
}

private fun isFeedEndpoint(url: String): Boolean {
    return try {
        val uri = java.net.URI(url)
        val host = uri.host.orEmpty().lowercase()
        val path = uri.path.orEmpty().lowercase()
        if (host == "news.google.com" && path.startsWith("/rss/articles/")) {
            false
        } else {
            path.endsWith(".rss") || path.endsWith(".atom") || path.endsWith(".xml") ||
                path == "/rss" || path.startsWith("/rss/") || path.endsWith("/feed") ||
                path.endsWith("/feed/")
        }
    } catch (_: Exception) {
        true
    }
}

fun isRelevantKaspaNews(item: KaspaNewsItem): Boolean {
    if (item.url.isBlank() || !(item.url.startsWith("https://") || item.url.startsWith("http://"))) return false
    if (isFeedEndpoint(item.url)) return false
    if (item.category == "YouTube" || item.category == "Reddit" || item.category == "GitHub") return true
    val haystack = item.title + " " + item.desc + " " + item.author
    return Regex(
        """(?i)\b(kaspa|ghostdag|blockdag|krc[\s-]?20|krc[\s-]?721|kasplex|toccata|kaspium|kasware|kaspire|kaspad|rusty-kaspa)\b"""
    ).containsMatchIn(haystack) || Regex("""\bKAS\b""").containsMatchIn(haystack)
}

fun extractImageUrl(xml: String): String? {
    // Robust image extraction that handles CDATA and multiple tag variants
    var cleanXml = xml.replace(Regex("<!\\[CDATA\\[(.*?)\\]\\]>", RegexOption.DOT_MATCHES_ALL)) { it.groupValues[1] }
    
    // Sometimes CDATA is nested or escaped
    cleanXml = cleanXml.replace("&lt;![CDATA[", "").replace("]]&gt;", "")

    // 1. Comprehensive list of image sources in priority order
    val sources = listOf(
        Regex("<media:content[^>]+url=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE),
        Regex("<enclosure[^>]+url=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE),
        Regex("<media:thumbnail[^>]+url=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE),
        Regex("<thumbnail>([^<]+)</thumbnail>", RegexOption.IGNORE_CASE),
        Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE),
        Regex("<image>([^<]+)</image>", RegexOption.IGNORE_CASE),
        Regex("<og:image[^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE),
        Regex("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE),
        Regex("<photo>([^<]+)</photo>", RegexOption.IGNORE_CASE)
    )
    
    for (regex in sources) {
        val match = regex.find(cleanXml)
        if (match != null) {
            val url = unescapeHtmlEntities(match.groupValues[1]).trim()
            if (url.startsWith("http")) return url
        }
    }
    
    // Look inside content:encoded if description failed
    val contentEncoded = Regex("<content:encoded(?:\\s+[^>]*)?>(.*?)</content:encoded>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(cleanXml)
    if (contentEncoded != null) {
        val imgMatch = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(contentEncoded.groupValues[1])
        if (imgMatch != null) {
            val url = unescapeHtmlEntities(imgMatch.groupValues[1]).trim()
            if (url.startsWith("http")) return url
        }
    }

    return null
}

fun formatEpochToDisplay(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Recently"
    val now = System.currentTimeMillis()
    val diff = now - epochMillis

    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val date = java.util.Date(epochMillis)
    val formattedTime = timeFormat.format(date)
    val formattedDate = dateFormat.format(date)

    val itemCal = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    val nowCal = java.util.Calendar.getInstance().apply { timeInMillis = now }

    val isToday = itemCal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
            itemCal.get(java.util.Calendar.DAY_OF_YEAR) == nowCal.get(java.util.Calendar.DAY_OF_YEAR)
    val isYesterday = itemCal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
            itemCal.get(java.util.Calendar.DAY_OF_YEAR) == nowCal.get(java.util.Calendar.DAY_OF_YEAR) - 1

    return when {
        isToday -> {
            when {
                diff < 60_000 -> "Today, $formattedTime (Just now)"
                diff < 3600_000 -> "Today, $formattedTime (${diff / 60_000}m ago)"
                diff < 86400_000 -> "Today, $formattedTime (${diff / 3600_000}h ago)"
                else -> "Today, $formattedTime"
            }
        }
        isYesterday -> "Yesterday, $formattedTime"
        else -> "$formattedDate • $formattedTime"
    }
}

fun parseDateToEpoch(dateStr: String): Long {
    if (dateStr.isBlank() || dateStr == "Recently" || dateStr == "Just now") return 0L
    val clean = dateStr.trim()

    // Modern ISO 8601 parsing first (OffsetDateTime & ZonedDateTime handle offsets like +00:00 before Instant)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        try {
            return java.time.OffsetDateTime.parse(clean).toInstant().toEpochMilli()
        } catch (_: Exception) {}
        try {
            return java.time.ZonedDateTime.parse(clean).toInstant().toEpochMilli()
        } catch (_: Exception) {}
        try {
            return java.time.Instant.parse(clean).toEpochMilli()
        } catch (_: Exception) {}
    }

    val formats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss z",
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm:ss z",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "MMM dd, yyyy HH:mm",
        "MMM dd, yyyy",
        "dd MMM yyyy HH:mm:ss z",
        "dd MMM yyyy HH:mm:ss Z"
    )
    for (fmt in formats) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val parsed = sdf.parse(clean)
            if (parsed != null) return parsed.time
        } catch (_: Exception) {}
    }
    return 0L
}

fun extractYouTubeVideoId(url: String): String? {
    val clean = url.trim()
    if (clean.isBlank() || clean.equals("undefined", ignoreCase = true) || clean.equals("null", ignoreCase = true)) return null
    if (clean.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) return clean
    val vMatch = Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(clean)
    if (vMatch != null && !vMatch.groupValues[1].equals("undefined", ignoreCase = true)) return vMatch.groupValues[1]
    val beMatch = Regex("youtu\\.be/([a-zA-Z0-9_-]{11})").find(clean)
    if (beMatch != null && !beMatch.groupValues[1].equals("undefined", ignoreCase = true)) return beMatch.groupValues[1]
    val embedMatch = Regex("embed/([a-zA-Z0-9_-]{11})").find(clean)
    if (embedMatch != null && !embedMatch.groupValues[1].equals("undefined", ignoreCase = true)) return embedMatch.groupValues[1]
    val shortsMatch = Regex("shorts/([a-zA-Z0-9_-]{11})").find(clean)
    if (shortsMatch != null && !shortsMatch.groupValues[1].equals("undefined", ignoreCase = true)) return shortsMatch.groupValues[1]
    val liveMatch = Regex("live/([a-zA-Z0-9_-]{11})").find(clean)
    if (liveMatch != null && !liveMatch.groupValues[1].equals("undefined", ignoreCase = true)) return liveMatch.groupValues[1]
    val vPathMatch = Regex("/v/([a-zA-Z0-9_-]{11})").find(clean)
    if (vPathMatch != null && !vPathMatch.groupValues[1].equals("undefined", ignoreCase = true)) return vPathMatch.groupValues[1]
    return null
}

fun parseRssXml(xml: String, defaultCategory: String): List<KaspaNewsItem> {
    val items = mutableListOf<KaspaNewsItem>()
    try {
        var index = 0
        val itemOpenTag = Regex("<item(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE)
        val entryOpenTag = Regex("<entry(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE)
        while (index < xml.length) {
            val itemMatch = itemOpenTag.find(xml, index)
            val entryMatch = entryOpenTag.find(xml, index)
            if (itemMatch == null && entryMatch == null) break

            val isAtom = entryMatch != null &&
                (itemMatch == null || entryMatch.range.first < itemMatch.range.first)
            val openingMatch = if (isAtom) entryMatch!! else itemMatch!!
            val itemStart = openingMatch.range.first
            val closingTag = if (isAtom) "</entry>" else "</item>"
            val itemEnd = xml.indexOf(closingTag, openingMatch.range.last + 1, ignoreCase = true)
            if (itemEnd == -1) break

            val itemXml = xml.substring(itemStart, itemEnd)
            index = itemEnd + closingTag.length

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

            var rawDate = if (defaultCategory == "YouTube" || itemXml.contains("<published>")) {
                extractTagContent(itemXml, "published").ifEmpty {
                    extractTagContent(itemXml, "updated").ifEmpty {
                        extractTagContent(itemXml, "pubDate")
                    }
                }
            } else {
                extractTagContent(itemXml, "pubDate").ifEmpty {
                    extractTagContent(itemXml, "updated").ifEmpty {
                        extractTagContent(itemXml, "published").ifEmpty {
                            extractTagContent(itemXml, "dc:date")
                        }
                    }
                }
            }

            val parsedEpoch = parseDateToEpoch(rawDate)
            val epochMillis = if (parsedEpoch > 0L) {
                parsedEpoch
            } else {
                System.currentTimeMillis() - (items.size * 60_000L)
            }
            val displayDate = if (parsedEpoch > 0L) formatEpochToDisplay(epochMillis) else "Recently"

            var author = extractTagContent(itemXml, "name")
            if (author.isEmpty()) author = extractTagContent(itemXml, "author")
            if (author.isEmpty()) author = extractTagContent(itemXml, "dc:creator")
            if (author.isEmpty()) author = extractTagContent(itemXml, "source")
            if (author.isEmpty() && title.contains(" - ")) {
                val candidate = title.substringAfterLast(" - ").trim()
                if (candidate.isNotBlank() && candidate.length < 35) {
                    author = candidate
                }
            }
            if (author.isEmpty() && defaultCategory == "News") author = "Kaspa News"
            if (author.isEmpty() && defaultCategory == "YouTube") author = "Kaspa Channel"
            if (author.isEmpty() && defaultCategory == "X") author = "@KaspaCurrency"

            var videoId: String? = extractTagContent(itemXml, "yt:videoId").trim().takeIf {
                it.isNotBlank() && !it.equals("undefined", ignoreCase = true) && !it.equals("null", ignoreCase = true)
            }
            if (videoId == null && link.isNotEmpty() && !link.equals("undefined", ignoreCase = true)) {
                videoId = extractYouTubeVideoId(link)
            }

            var imageUrl = extractImageUrl(itemXml)
            if (imageUrl.isNullOrBlank() && !videoId.isNullOrBlank()) {
                imageUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            }

            if (link.contains("x.com") || link.contains("twitter.com") || link.contains("nitter") || defaultCategory == "X") {
                continue
            }

            val finalCategory = when {
                !videoId.isNullOrBlank() || defaultCategory == "YouTube" || title.endsWith(" - YouTube", ignoreCase = true) || link.contains("youtube.com") || link.contains("youtu.be") -> "YouTube"
                defaultCategory == "Reddit" || link.contains("reddit.com") -> "Reddit"
                defaultCategory == "GitHub" || link.contains("github.com") -> "GitHub"
                else -> "News"
            }

            val parsedItem = KaspaNewsItem(
                title = title,
                desc = desc.ifBlank { "Click to view full blockDAG update." },
                url = link,
                category = finalCategory,
                timestamp = displayDate,
                author = author,
                videoId = videoId,
                imageUrl = imageUrl,
                epochMillis = epochMillis
            )
            if (title.isNotBlank() && isRelevantKaspaNews(parsedItem)) {
                items.add(
                    parsedItem
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

    // Safely unwrap CDATA tags in a single pass to avoid infinite loop risks
    cleaned = cleaned.replace(Regex("<!\\[CDATA\\[(.*?)\\]\\]>", RegexOption.DOT_MATCHES_ALL)) { match ->
        match.groupValues[1]
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
    val vid = item.videoId?.trim()?.takeIf { it.isNotBlank() && it.length == 11 && !it.equals("undefined", ignoreCase = true) }
        ?: extractYouTubeVideoId(item.url)?.trim()?.takeIf { it.isNotBlank() && it.length == 11 && !it.equals("undefined", ignoreCase = true) }
    if (!vid.isNullOrBlank()) {
        return "video_${vid.lowercase()}"
    }
    val normTitle = item.title.lowercase()
        .replace(Regex("-\\s*(youtube|twitter|x|reddit|kaspa news|cointelegraph|google news).*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[^a-z0-9]"), "")
    return if (normTitle.length > 8) "title_$normTitle" else "url_${item.url.lowercase().trim()}"
}

suspend fun fetchLatestKaspaFeeds(): List<KaspaNewsItem> = withContext(Dispatchers.IO) {
    val client = okhttp3.OkHttpClient.Builder()
        .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
        .connectTimeout(7, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(7, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val feeds = listOf(
        Pair("https://news.google.com/rss/search?q=Kaspa+cryptocurrency&hl=en-US&gl=US&ceid=US:en", "News"),
        Pair("https://news.google.com/rss/search?q=Kaspa+crypto&hl=en-US&gl=US&ceid=US:en", "News"),
        Pair("https://news.google.com/rss/search?q=Kaspa+BlockDAG&hl=en-US&gl=US&ceid=US:en", "News"),
        Pair("https://news.google.com/rss/search?q=Kaspa+KAS+trading&hl=en-US&gl=US&ceid=US:en", "News"),
        Pair("https://news.google.com/rss/search?q=Kaspa+KRC20+KAS&hl=en-US&gl=US&ceid=US:en", "News"),
        Pair("https://cointelegraph.com/rss/tag/kaspa", "News"),
        Pair("https://www.crypto-news-flash.com/tag/kaspa/feed/", "News"),
        Pair("https://captainaltcoin.com/tag/kaspa/feed/", "News"),

        Pair("https://news.google.com/rss/search?q=Kaspa+cryptocurrency+market+news&hl=en-US&gl=US&ceid=US:en", "News"),
        Pair("https://news.google.com/rss/search?q=Kaspa+BlockDAG+GHOSTDAG+news&hl=en-US&gl=US&ceid=US:en", "News"),
        Pair("https://news.google.com/rss/search?q=Kaspa+KAS+blockchain+development&hl=en-US&gl=US&ceid=US:en", "News"),

        Pair("https://www.youtube.com/feeds/videos.xml?channel_id=UCsnbLKm_lpCUj63_HPW17og", "YouTube"),
        Pair("https://www.youtube.com/feeds/videos.xml?channel_id=UCv8-2oyrfqDigJAKjZ_RCzQ", "YouTube"),
        Pair("https://www.youtube.com/feeds/videos.xml?channel_id=UCZ-FjVIxrICs_FmJUGL3R-Q", "YouTube"),
        Pair("https://www.youtube.com/feeds/videos.xml?channel_id=UC4Y7sxOP3dG5z-G6zJX94Kw", "YouTube"),
        Pair("https://news.google.com/rss/search?q=site:youtube.com+Kaspa&hl=en-US&gl=US&ceid=US:en", "YouTube"),
        Pair("https://news.google.com/rss/search?q=site:youtube.com+%22Kaspa+Silver%22&hl=en-US&gl=US&ceid=US:en", "YouTube"),
        Pair("https://news.google.com/rss/search?q=site:youtube.com+Kaspa+BlockDAG&hl=en-US&gl=US&ceid=US:en", "YouTube"),

        Pair("https://www.reddit.com/r/kaspa/.rss", "Reddit"),
        Pair("https://www.reddit.com/r/KaspaCurrency/.rss", "Reddit"),
        Pair("https://github.com/kaspanet/kaspad/commits/master.atom", "GitHub"),
        Pair("https://github.com/kaspanet/rusty-kaspa/commits/master.atom", "GitHub"),
        Pair("https://github.com/kaspanet/kaspad/releases.atom", "GitHub"),
        Pair("https://github.com/kaspanet/rusty-kaspa/releases.atom", "GitHub")
    )

    val list: MutableList<KaspaNewsItem> = coroutineScope {
        val rssResults = feeds.map { (url, cat) ->
            async(Dispatchers.IO) {
                val feedItems = mutableListOf<KaspaNewsItem>()
                try {
                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                        .build()
                    client.newCall(request).execute().use { response ->
                        val bodyStr = response.body?.string().orEmpty()
                        if (response.isSuccessful && bodyStr.isNotBlank()) {
                            feedItems.addAll(parseRssXml(bodyStr, cat))
                        }
                        val feedHost = request.url.host
                        android.util.Log.d(
                            "KaspaNews",
                            "Feed result category=$cat host=$feedHost status=${response.code} bytes=${bodyStr.length} parsed=${feedItems.size}"
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.w(
                        "KaspaNews",
                        "Feed failure category=$cat url=$url type=${e.javaClass.simpleName} message=${e.message}"
                    )
                }
                feedItems
            }
        }.awaitAll().flatten().toMutableList()

        val filtered = rssResults.filter { item ->
            val isUnavailable = item.title.contains("private video", ignoreCase = true) ||
                    item.title.contains("deleted video", ignoreCase = true) ||
                    item.title.contains("video unavailable", ignoreCase = true) ||
                    item.desc.contains("this video is unavailable", ignoreCase = true) ||
                    item.title.contains("404", ignoreCase = true)

            val isKaspaCommunityFake = item.author.contains("Kaspa Community", ignoreCase = true) ||
                    item.author.contains("Kaspa Video", ignoreCase = true) ||
                    item.title.contains("Kaspa Community", ignoreCase = true) ||
                    item.desc.contains("Kaspa Community", ignoreCase = true)

            val isFakeXFeed = item.category.equals("X", ignoreCase = true) ||
                    item.url.contains("x.com") ||
                    item.url.contains("twitter.com") ||
                    item.url.contains("nitter")

            val isSpamOrAirdrop = item.title.contains("airdrop claim", ignoreCase = true) ||
                    item.title.contains("free kaspa", ignoreCase = true) ||
                    item.desc.contains("airdrop claim", ignoreCase = true)

            val isKaspaCurrencyYoutube = (item.category == "YouTube" || !item.videoId.isNullOrBlank() || item.url.contains("youtube.com") || item.url.contains("youtu.be")) &&
                    (item.author.contains("Kaspa Currency", ignoreCase = true) ||
                     item.author.contains("KaspaCurrency", ignoreCase = true) ||
                     item.title.contains("Kaspa Currency", ignoreCase = true) ||
                     item.title.contains("KaspaCurrency", ignoreCase = true) ||
                     item.desc.contains("Kaspa Currency", ignoreCase = true) ||
                     item.desc.contains("KaspaCurrency", ignoreCase = true))

            val isInvalidYoutube = item.category == "YouTube" && (item.videoId.isNullOrBlank() || item.videoId.length != 11)

            !isUnavailable && !isKaspaCommunityFake && !isFakeXFeed && !isKaspaCurrencyYoutube && !isSpamOrAirdrop && !isInvalidYoutube &&
            !item.title.contains("ecosystem", ignoreCase = true) &&
            !item.url.contains("ecosystem", ignoreCase = true)
        }

        filtered
            .distinctBy { getKaspaNewsDeduplicationKey(it) }
            .sortedByDescending { it.epochMillis }
            .take(250)
            .toMutableList()
    }
    list
}
