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
    if (dateStr.isBlank() || dateStr == "Recently" || dateStr == "Just now") return System.currentTimeMillis()
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

            val epochMillis = parseDateToEpoch(rawDate)
            val displayDate = if (rawDate.isNotBlank()) formatEpochToDisplay(epochMillis) else "Recently"

            var author = extractTagContent(itemXml, "name")
            if (author.isEmpty()) author = extractTagContent(itemXml, "author")
            if (author.isEmpty()) author = extractTagContent(itemXml, "dc:creator")
            if (author.isEmpty()) author = extractTagContent(itemXml, "source")
            if (author.isEmpty() && defaultCategory == "News") author = "Kaspa News"
            if (author.isEmpty() && defaultCategory == "YouTube") author = "Kaspa Community"

            var videoId: String? = extractTagContent(itemXml, "yt:videoId").trim().takeIf {
                it.isNotBlank() && !it.equals("undefined", ignoreCase = true) && !it.equals("null", ignoreCase = true)
            }
            if (videoId == null && link.isNotEmpty() && !link.equals("undefined", ignoreCase = true)) {
                videoId = extractYouTubeVideoId(link)
            }

            val imageUrl = extractImageUrl(itemXml)

            val finalCategory = when {
                !videoId.isNullOrBlank() || defaultCategory == "YouTube" -> "YouTube"
                link.contains("x.com") || link.contains("twitter.com") || link.contains("nitter") || defaultCategory == "X" -> "X"
                defaultCategory == "Reddit" || link.contains("reddit.com") -> "Reddit"
                defaultCategory == "GitHub" || link.contains("github.com") -> "GitHub"
                else -> if (defaultCategory.isBlank()) "Kaspa News" else defaultCategory
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
                        imageUrl = imageUrl,
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
    if (!item.videoId.isNullOrBlank()) {
        return "video_${item.videoId!!.lowercase().trim()}"
    }
    val cleanTitle = item.title.lowercase().replace(Regex("[^a-z0-9]"), "")
    return if (cleanTitle.length > 8) cleanTitle else item.url.lowercase().trim()
}

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
        Pair("https://www.youtube.com/feeds/videos.xml?channel_id=UCv8-2oyrfqDigJAKjZ_RCzQ", "YouTube"),
        Pair("https://www.youtube.com/feeds/videos.xml?channel_id=UCZ-FjVIxrICs_FmJUGL3R-Q", "YouTube"),
        Pair("https://www.youtube.com/feeds/videos.xml?channel_id=UC4Y7sxOP3dG5z-G6zJX94Kw", "YouTube"),
        Pair("https://news.google.com/rss/search?q=Kaspa+cryptocurrency&hl=en-US&gl=US&ceid=US:en", "News"),
        Pair("https://news.google.com/rss/search?q=Kaspa+BlockDAG&hl=en-US&gl=US&ceid=US:en", "News"),
        Pair("https://news.google.com/rss/search?q=Kaspa+network&hl=en-US&gl=US&ceid=US:en", "News"),
        Pair("https://news.google.com/rss/search?q=Kaspa+KCC20+KRC20&hl=en-US&gl=US&ceid=US:en", "News"),
        Pair("https://news.google.com/rss/search?q=Kaspa+vprog+smart+contracts&hl=en-US&gl=US&ceid=US:en", "News"),
        Pair("https://news.google.com/rss/search?q=Kaspa+covenant+KIP20&hl=en-US&gl=US&ceid=US:en", "News"),
        Pair("https://news.google.com/rss/search?q=Kaspa+Silver&hl=en-US&gl=US&ceid=US:en", "News"),
        Pair("https://news.google.com/rss/search?q=Kaspa+KAS+crypto&hl=en-US&gl=US&ceid=US:en", "News"),
        Pair("https://news.google.com/rss/search?q=Kaspa+crypto&hl=en-GB&gl=GB&ceid=GB:en", "News"),
        Pair("https://cryptoslate.com/news/kaspa/feed/", "News"),
        Pair("https://www.crypto-news-flash.com/tag/kaspa/feed/", "News"),
        Pair("https://captainaltcoin.com/tag/kaspa/feed/", "News"),
        Pair("https://cointelegraph.com/rss/tag/kaspa", "News"),
        Pair("https://coingape.com/tag/kaspa/feed/", "News"),
        Pair("https://u.today/rss/kaspa", "News"),
        Pair("https://xcancel.com/kaspadotnews/rss", "X"),
        Pair("https://xcancel.com/kaspaunchained/rss", "X")
    )

    val list: MutableList<KaspaNewsItem> = coroutineScope {
        val rssResults = feeds.map { (url, cat) ->
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

        // Removed Discord Widget fetch per user request to replace Discord with Telegram
        rssResults.filter { 
            !it.title.contains("ecosystem", ignoreCase = true) && !it.author.contains("ecosystem", ignoreCase = true) && !it.url.contains("ecosystem", ignoreCase = true) &&
            !it.title.contains("kaspacurrency", ignoreCase = true) && !it.author.contains("kaspacurrency", ignoreCase = true) && !it.url.contains("kaspacurrency", ignoreCase = true)
        }
            .distinctBy { getKaspaNewsDeduplicationKey(it) }
            .sortedByDescending { it.epochMillis }
            .take(120)
            .toMutableList()
    }
    list
}
