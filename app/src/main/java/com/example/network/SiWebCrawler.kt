package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Dns
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.InetAddress
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

data class SiCrawledPage(
    val title: String,
    val url: String,
    val text: String
)

data class SiCrawlResult(
    val pages: List<SiCrawledPage>,
    val blockedByRobots: Int,
    val skipped: Int
)

/**
 * Small, bounded, same-origin crawler for SI Agent.
 * Uses OkHttp for transport and the MIT-licensed jsoup parser for HTML extraction.
 */
class SiWebCrawler(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .dns { hostname ->
            Dns.SYSTEM.lookup(hostname).also { addresses ->
                require(addresses.isNotEmpty() && addresses.all { address ->
                    !address.isAnyLocalAddress && !address.isLoopbackAddress &&
                        !address.isLinkLocalAddress && !address.isSiteLocalAddress
                }) { "Non-public DNS target blocked" }
            }
        }
        .build(),
    private val maxPages: Int = 8,
    private val maxDepth: Int = 2,
    private val maxBytes: Long = 1_500_000L
) {
    suspend fun crawl(startUrl: String): SiCrawlResult = withContext(Dispatchers.IO) {
        val start = normalizePublicUrl(startUrl)
            ?: throw IllegalArgumentException("Enter a public http or https website URL.")
        val originHost = start.host
        val robotsRules = loadRobotsRules(start)
        val queue = ArrayDeque<Pair<HttpUrl, Int>>()
        val visited = linkedSetOf<String>()
        val pages = mutableListOf<SiCrawledPage>()
        var blocked = 0
        var skipped = 0
        queue.add(start to 0)

        while (queue.isNotEmpty() && pages.size < maxPages) {
            val (url, depth) = queue.removeFirst()
            val canonical = url.newBuilder().fragment(null).build().toString()
            if (!visited.add(canonical)) continue
            if (url.host != originHost || !isPublicHost(url.host)) {
                skipped++
                continue
            }
            if (isDisallowed(url.encodedPath, robotsRules)) {
                blocked++
                continue
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "KaspaBrowser-SIAgent/1.0 (+https://kaspabrowser.xyz)")
                .header("Accept", "text/html,application/xhtml+xml")
                .get()
                .build()

            val parsed = runCatching {
                client.newCall(request).execute().use { response ->
                    val body = response.body ?: return@use null
                    if (!response.isSuccessful ||
                        !(response.header("Content-Type").orEmpty().contains("text/html", true) ||
                          response.header("Content-Type").orEmpty().contains("application/xhtml+xml", true)) ||
                        body.contentLength() > maxBytes
                    ) return@use null
                    val bytes = body.bytes()
                    if (bytes.isEmpty() || bytes.size.toLong() > maxBytes) return@use null
                    val doc = Jsoup.parse(bytes.inputStream(), null, response.request.url.toString())
                    doc.select("script,style,noscript,svg,canvas,nav,footer,header,form,iframe").remove()
                    val title = doc.title().ifBlank { response.request.url.host }
                    val text = doc.body()?.text().orEmpty().replace(Regex("\\s+"), " ").take(8_000)
                    val links = if (depth < maxDepth) {
                        doc.select("a[href]").mapNotNull { anchor ->
                            val candidate = response.request.url.resolve(anchor.attr("href")) ?: return@mapNotNull null
                            candidate.newBuilder().fragment(null).build()
                                .takeIf { it.host == originHost && it.scheme in setOf("http", "https") }
                        }
                    } else emptyList()
                    Triple(SiCrawledPage(title, response.request.url.toString(), text), links, null)
                }
            }.getOrNull()

            if (parsed == null) {
                skipped++
                continue
            }
            val page = parsed.first
            if (page.text.isNotBlank()) pages.add(page)
            if (depth < maxDepth) {
                parsed.second.forEach { link ->
                    if (queue.size + visited.size < maxPages * 5) queue.add(link to depth + 1)
                }
            }
            // Keep requests polite and avoid hammering a single host.
            if (queue.isNotEmpty() && pages.size < maxPages) delay(350)
        }
        SiCrawlResult(pages, blocked, skipped)
    }

    private fun loadRobotsRules(start: HttpUrl): List<Pair<String, Boolean>> {
        val robotsUrl = start.newBuilder().encodedPath("/robots.txt").query(null).build()
        return runCatching {
            val request = Request.Builder()
                .url(robotsUrl)
                .header("User-Agent", "KaspaBrowser-SIAgent/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                (response.body?.string() ?: "").lineSequence()
                    .map { it.substringBefore("#").trim() }
                    .filter { it.startsWith("Disallow:", true) }
                    .mapNotNull { line ->
                        val path = line.substringAfter(":").trim()
                        if (path.isBlank()) null else path to true
                    }.toList()
            }
        }.getOrDefault(emptyList())
    }

    private fun isDisallowed(path: String, rules: List<Pair<String, Boolean>>): Boolean =
        rules.any { (prefix, _) -> prefix != "/" && path.startsWith(prefix) } ||
            rules.any { (prefix, _) -> prefix == "/" }

    private fun normalizePublicUrl(raw: String): HttpUrl? {
        val candidate = raw.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        val url = candidate.toHttpUrlOrNull() ?: return null
        if (url.scheme !in setOf("http", "https") || !isPublicHost(url.host)) return null
        return url
    }

    private fun isPublicHost(host: String): Boolean {
        val normalized = host.lowercase()
        if (normalized == "localhost" || normalized.endsWith(".localhost") ||
            normalized.endsWith(".local") || normalized.endsWith(".internal")
        ) return false
        return runCatching {
            val address = InetAddress.getByName(normalized)
            !address.isAnyLocalAddress && !address.isLoopbackAddress &&
                !address.isLinkLocalAddress && !address.isSiteLocalAddress
        }.getOrDefault(false)
    }
}
