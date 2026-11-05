package com.example.network.siagent

import android.util.Log
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl
import org.jsoup.Jsoup
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Bounded Android-side public web search and article extraction. */
object AndroidWebResearch {
    private const val TAG = "SiAgentWeb"
    private const val SEARCH_URL = "https://html.duckduckgo.com/html/"
    private const val MAX_SEARCH_RESULTS = 5
    private const val MAX_CRAWL_PAGES = 3
    private const val MAX_REDIRECTS = 3
    private const val MAX_BODY_BYTES = 1_500_000L
    private const val MAX_ROBOTS_BYTES = 32_000L
    private const val MAX_TEXT_CHARS = 7_000
    private val robotsCache = ConcurrentHashMap<String, String>()

    data class Source(val title: String, val url: String, val excerpt: String, val fetched: Boolean)
    data class ResearchResult(val query: String, val sources: List<Source>, val error: String? = null) {
        fun toPromptContext(): String = buildString {
            appendLine("LIVE WEB RESEARCH FOR: $query")
            sources.forEachIndexed { index, source ->
                appendLine("[${index + 1}] ${source.title}")
                appendLine("URL: ${source.url}")
                appendLine("Content: ${source.excerpt}")
                appendLine()
            }
            if (sources.isEmpty()) appendLine("No sources were retrieved. Do not invent sources or facts.")
        }
        fun toUserText(): String = buildString {
            if (sources.isEmpty()) { append(error ?: "No web sources could be retrieved."); return@buildString }
            appendLine("Web research: $query")
            sources.forEachIndexed { index, source ->
                appendLine()
                appendLine("${index + 1}. ${source.title}")
                appendLine(source.excerpt.take(900))
                appendLine("Source: ${source.url}")
            }
            append("Retrieved excerpts; not an independently verified consensus.")
        }
    }

    private val safeDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isEmpty() || addresses.any { !isPublicAddress(it) })
                throw UnknownHostException("Blocked non-public destination")
            return addresses
        }
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().dns(safeDns)
            .connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS).callTimeout(18, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).build()
    }

    suspend fun searchAndCrawl(query: String): ResearchResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val cleanQuery = query.trim().take(240)
            if (cleanQuery.isBlank()) return@withContext ResearchResult(cleanQuery, emptyList(), "Enter a search query.")
            try {
                val searchUrl = HttpUrl.parse(SEARCH_URL)?.newBuilder()
                    ?.addQueryParameter("q", cleanQuery)?.build()
                    ?: return@withContext ResearchResult(cleanQuery, emptyList(), "Could not construct search URL.")
                val html = fetchText(searchUrl.toString(), MAX_BODY_BYTES)
                    ?: return@withContext ResearchResult(cleanQuery, emptyList(), "Search provider returned no accessible results.")
                val doc = Jsoup.parse(html, SEARCH_URL)
                val results = doc.select("a.result__a, a[data-testid=result-title-a]")
                    .mapNotNull { element ->
                        val raw = element.absUrl("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val parsedLink = HttpUrl.parse(raw)
                        val destination = if (parsedLink?.host?.endsWith("duckduckgo.com") == true) parsedLink.queryParameter("uddg") ?: raw else raw
                        val safe = normalizePublicHttpsUrl(destination) ?: return@mapNotNull null
                        val title = element.text().trim().take(220).ifBlank { safe }
                        val snippet = element.parent()?.parent()?.selectFirst(".result__snippet")?.text()
                            ?: element.parent()?.selectFirst(".result__snippet")?.text().orEmpty()
                        Source(title, safe, snippet.trim().take(700), false)
                    }.distinctBy { it.url }.take(MAX_SEARCH_RESULTS)
                if (results.isEmpty()) return@withContext ResearchResult(cleanQuery, emptyList(),
                    "No parseable results. Search provider may have changed its markup or rate-limited this device.")
                val fetched = ArrayList<Source>()
                for (result in results) {
                    if (fetched.size >= MAX_CRAWL_PAGES) break
                    val content = fetchArticle(result.url)
                    fetched += if (content.isNullOrBlank()) result else result.copy(excerpt = content, fetched = true)
                }
                ResearchResult(cleanQuery, fetched)
            } catch (error: Exception) {
                Log.w(TAG, "Web research failed", error)
                ResearchResult(cleanQuery, emptyList(), "Web research failed: ${error.javaClass.simpleName}. Check connectivity and retry.")
            }
        }

    suspend fun crawlUrl(url: String): ResearchResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val safeUrl = normalizePublicHttpsUrl(url)
                ?: return@withContext ResearchResult(url, emptyList(), "Only public HTTPS URLs are supported.")
            val content = fetchArticle(safeUrl)
                ?: return@withContext ResearchResult(safeUrl, emptyList(), "Page could not be retrieved. It may block crawlers, disallow access in robots.txt, or contain no readable article text.")
            val title = runCatching {
                val html = fetchText(safeUrl, MAX_BODY_BYTES) ?: ""
                Jsoup.parse(html, safeUrl).title().take(220)
            }.getOrDefault(safeUrl)
            ResearchResult(safeUrl, listOf(Source(title.ifBlank { safeUrl }, safeUrl, content, true)))
        }

    private fun fetchArticle(url: String): String? {
        val normalized = normalizePublicHttpsUrl(url) ?: return null
        val uri = URI(normalized)
        val origin = "${uri.scheme}://${uri.host.lowercase()}"
        val robots = robotsCache[origin] ?: fetchText("$origin/robots.txt", MAX_ROBOTS_BYTES)
            .orEmpty().also { robotsCache[origin] = it }
        if (!isAllowedByRobots(robots, uri.rawPath.ifBlank { "/" })) return null
        val html = fetchText(normalized, MAX_BODY_BYTES) ?: return null
        val document = Jsoup.parse(html, normalized)
        document.select("script, style, noscript, svg, nav, footer, header, aside, form, iframe, canvas").remove()
        val article = document.selectFirst("article, main, [role=main]") ?: document.body()
        val text = article?.text()?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        return text.take(MAX_TEXT_CHARS).takeIf { it.length >= 120 }
    }

    private fun fetchText(initialUrl: String, maxBytes: Long): String? {
        var current = normalizePublicHttpsUrl(initialUrl) ?: return null
        for (redirect in 0..MAX_REDIRECTS) {
            var redirectTarget: String? = null
            val request = Request.Builder().url(current)
                .header("User-Agent", "KaspaBrowser-SIAgent/1.0 (+https://kaspabrowser.xyz)")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.5")
                .header("Accept-Language", "en-US,en;q=0.8").get().build()
            client.newCall(request).execute().use { response ->
                if (response.code in 300..399) {
                    if (redirect == MAX_REDIRECTS) return null
                    val location = response.header("Location") ?: return null
                    val next = response.request.url.resolve(location)?.toString() ?: return null
                    redirectTarget = normalizePublicHttpsUrl(next) ?: return null
                } else {
                    if (!response.isSuccessful) return null
                    val type = response.header("Content-Type").orEmpty().lowercase()
                    if (type.isNotBlank() && !type.contains("text/html") && !type.contains("text/plain") &&
                        !type.contains("application/xhtml+xml") && !type.contains("application/xml")) return null
                    val body = response.body ?: return null
                    if (body.contentLength() > maxBytes) return null
                    val source = body.source()
                    val buffer = okio.Buffer()
                    var totalBytes = 0L
                    while (totalBytes <= maxBytes) {
                        val read = source.read(buffer, minOf(8_192L, maxBytes + 1 - totalBytes))
                        if (read == -1L) break
                        totalBytes += read
                        if (totalBytes > maxBytes) return null
                    }
                    return buffer.readByteArray().toString(Charsets.UTF_8)
                }
            }
            current = redirectTarget ?: return null
        }
        return null
    }

    private fun normalizePublicHttpsUrl(raw: String): String? {
        return try {
            val uri = URI(raw.trim())
            if (!uri.scheme.equals("https", true) || uri.userInfo != null || uri.host.isNullOrBlank()) return null
            val host = uri.host.lowercase()
            if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return null
            if (uri.port != -1 && uri.port != 443) return null
            val resolved = Dns.SYSTEM.lookup(host)
            if (resolved.isEmpty() || resolved.any { !isPublicAddress(it) }) return null
            URI("https", null, host, -1, uri.path.ifBlank { "/" }, uri.query, null).toASCIIString()
        } catch (_: Exception) {
            null
        }
    }

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address
        if (address is Inet4Address) {
            val a = bytes[0].toInt() and 0xff; val b = bytes[1].toInt() and 0xff
            if (a == 0 || a == 10 || a == 127 || a >= 224) return false
            if (a == 100 && b in 64..127) return false
            if (a == 169 && b == 254) return false
            if (a == 172 && b in 16..31) return false
            if (a == 192 && b == 168) return false
            if (a == 192 && b == 0) return false
            if (a == 198 && b in 18..19) return false
            return true
        }
        if (address is Inet6Address) {
            val first = bytes[0].toInt() and 0xff
            if ((first and 0xe0) != 0x20 || bytes.all { it == 0.toByte() }) return false
            return true
        }
        return false
    }

    private fun isAllowedByRobots(robots: String, path: String): Boolean {
        if (robots.isBlank()) return true
        var currentAgentSpecific = false
        var applies = false
        val disallowed = ArrayList<String>()
        robots.lineSequence().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            val separator = line.indexOf(':')
            if (separator <= 0) return@forEach
            val name = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            when (name) {
                "user-agent" -> { currentAgentSpecific = value == "*" || value.equals("KaspaBrowser-SIAgent", true); if (currentAgentSpecific) applies = true }
                "disallow" -> if (currentAgentSpecific && value.isNotBlank()) disallowed += value
            }
        }
        return !applies || disallowed.none { path.startsWith(it) }
    }
}
