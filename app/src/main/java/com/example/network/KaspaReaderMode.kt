package com.example.network

import org.json.JSONArray
import org.json.JSONObject

object KaspaReaderMode {

    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("kaspasearch")
            isNativeLoaded = true
        } catch (_: Throwable) {
            isNativeLoaded = false
        }
    }

    @JvmStatic
    private external fun nativeExtractArticle(rawHtml: String, url: String): String

    @JvmStatic
    private external fun nativeGenerateReaderHtml(rawHtml: String, theme: String, fontSize: Int): String

    data class ExtractedArticle(
        val title: String,
        val byline: String? = null,
        val leadImage: String? = null,
        val contentHtml: String,
        val textContent: String = "",
        val wordCount: Int = 0,
        val readingTimeMinutes: Int = 1,
        val hasVideo: Boolean = false,
        val videoEmbeds: List<String> = emptyList()
    )

    /**
     * Extracts article metadata, semantic text, and video embeds from raw HTML.
     * Uses the on-device Rust engine for zero-jank sub-3ms parsing.
     */
    fun extractArticle(rawHtml: String, url: String = ""): ExtractedArticle {
        if (isNativeLoaded) {
            try {
                val jsonStr = nativeExtractArticle(rawHtml, url)
                if (jsonStr.isNotBlank() && jsonStr != "{}") {
                    val obj = JSONObject(jsonStr)
                    val embedsArr = obj.optJSONArray("video_embeds")
                    val embeds = mutableListOf<String>()
                    if (embedsArr != null) {
                        for (i in 0 until embedsArr.length()) {
                            embeds.add(embedsArr.optString(i))
                        }
                    }
                    return ExtractedArticle(
                        title = obj.optString("title", "Reader View"),
                        byline = if (obj.has("byline") && !obj.isNull("byline")) obj.optString("byline") else null,
                        leadImage = if (obj.has("lead_image") && !obj.isNull("lead_image")) obj.optString("lead_image") else null,
                        contentHtml = obj.optString("content_html", "<p>No content available.</p>"),
                        textContent = obj.optString("text_content", ""),
                        wordCount = obj.optInt("word_count", 0),
                        readingTimeMinutes = obj.optInt("reading_time_minutes", 1),
                        hasVideo = obj.optBoolean("has_video", embeds.isNotEmpty()),
                        videoEmbeds = embeds
                    )
                }
            } catch (_: Throwable) {}
        }

        // Pure Kotlin Fallback Extractor
        return fallbackExtractArticle(rawHtml)
    }

    /**
     * Directly transforms raw page HTML into a standalone Reader Mode document using native Rust.
     */
    fun convertRawHtmlToReaderHtml(rawHtml: String, theme: String = "dark", fontSizeSp: Int = 18): String {
        if (isNativeLoaded) {
            try {
                val nativeHtml = nativeGenerateReaderHtml(rawHtml, theme, fontSizeSp)
                if (nativeHtml.isNotBlank()) {
                    return nativeHtml
                }
            } catch (_: Throwable) {}
        }

        val article = extractArticle(rawHtml)
        return getReaderHtml(
            title = article.title,
            content = article.contentHtml,
            leadImage = article.leadImage ?: "",
            theme = theme,
            fontSizeSp = fontSizeSp,
            byline = article.byline,
            wordCount = article.wordCount,
            readingTimeMinutes = article.readingTimeMinutes
        )
    }

    private fun fallbackExtractArticle(rawHtml: String): ExtractedArticle {
        var title = "Reader View"
        val titleMatch = Regex("(?i)<title[^>]*>(.*?)</title>").find(rawHtml)
        if (titleMatch != null) {
            title = titleMatch.groupValues[1].trim()
        }

        val ogTitle = Regex("(?i)<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']+)[\"']").find(rawHtml)
        if (ogTitle != null) {
            title = ogTitle.groupValues[1].trim()
        }

        var leadImage: String? = null
        val ogImage = Regex("(?i)<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']").find(rawHtml)
        if (ogImage != null) {
            leadImage = ogImage.groupValues[1].trim()
        }

        val articleMatch = Regex("(?is)<article[^>]*>(.*?)</article>").find(rawHtml)
            ?: Regex("(?is)<main[^>]*>(.*?)</main>").find(rawHtml)

        var content = articleMatch?.groupValues?.get(1) ?: rawHtml

        // Strip scripts and styles
        content = content.replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), "")
            .replace(Regex("(?is)<nav[^>]*>.*?</nav>"), "")
            .replace(Regex("(?is)<header[^>]*>.*?</header>"), "")
            .replace(Regex("(?is)<footer[^>]*>.*?</footer>"), "")

        // Preserve YouTube / Vimeo / Dailymotion iframes and strip ad iframes
        val embeds = mutableListOf<String>()
        val iframeRegex = Regex("(?is)<iframe[^>]*>.*?</iframe>")
        content = iframeRegex.replace(content) { matchResult ->
            val matched = matchResult.value
            if (matched.contains("youtube.com", ignoreCase = true) ||
                matched.contains("youtube-nocookie.com", ignoreCase = true) ||
                matched.contains("youtu.be", ignoreCase = true) ||
                matched.contains("vimeo.com", ignoreCase = true) ||
                matched.contains("dailymotion.com", ignoreCase = true)
            ) {
                embeds.add(matched)
                """<div class="video-container">$matched</div>"""
            } else {
                ""
            }
        }

        val plainText = content.replace(Regex("<[^>]+>"), " ").trim()
        val words = plainText.split("\\s+".toRegex()).filter { it.isNotBlank() }.size

        return ExtractedArticle(
            title = title,
            byline = null,
            leadImage = leadImage,
            contentHtml = content,
            textContent = plainText,
            wordCount = words,
            readingTimeMinutes = maxOf(1, (words + 199) / 200),
            hasVideo = embeds.isNotEmpty(),
            videoEmbeds = embeds
        )
    }

    /**
     * JavaScript to extract main content and format it for reader mode.
     * Encodes output using encodeURIComponent(JSON.stringify(...)) to avoid JSON parsing failures in Android evaluateJavascript.
     */
    const val JS_EXTRACT_CONTENT = """
        (function() {
            try {
                function getCleanText(el) {
                    return el ? (el.innerText || el.textContent || '').trim() : '';
                }

                // Identify main content element
                let mainEl = document.querySelector('article') || document.querySelector('main') || document.querySelector('[role="main"]');
                
                if (!mainEl) {
                    let bestScore = -1;
                    let bestEl = document.body;
                    const candidates = document.querySelectorAll('div, section, article, main');
                    candidates.forEach(el => {
                        const text = getCleanText(el);
                        if (text.length > 100) {
                            let score = text.length;
                            const tag = el.tagName.toLowerCase();
                            if (tag === 'article') score *= 3;
                            if (tag === 'main') score *= 2;
                            if (tag === 'section') score *= 1.5;
                            if (score > bestScore) {
                                bestScore = score;
                                bestEl = el;
                            }
                        }
                    });
                    mainEl = bestEl || document.body;
                }

                // Clone element to sanitize
                const clone = mainEl.cloneNode(true);
                const noiseSelectors = 'script, style, iframe, nav, footer, header, aside, .ad, .ads, .advertisement, .social-share, [role="banner"], [role="navigation"]';
                clone.querySelectorAll(noiseSelectors).forEach(n => n.remove());

                // Page Title
                let title = document.title || '';
                const h1 = document.querySelector('h1');
                if (h1 && h1.innerText.trim().length > 0) {
                    title = h1.innerText.trim();
                }

                // Lead Image
                let leadImg = '';
                const ogImg = document.querySelector('meta[property="og:image"]');
                if (ogImg && ogImg.getAttribute('content')) {
                    leadImg = ogImg.getAttribute('content');
                } else {
                    const firstImg = clone.querySelector('img');
                    if (firstImg && firstImg.src) {
                        leadImg = firstImg.src;
                    }
                }

                const contentHtml = clone.innerHTML || '';

                return encodeURIComponent(JSON.stringify({
                    title: title || 'Reader View',
                    leadImage: leadImg || '',
                    content: contentHtml
                }));
            } catch (err) {
                return encodeURIComponent(JSON.stringify({
                    title: document.title || 'Reader View',
                    leadImage: '',
                    content: document.body ? document.body.innerHTML : ''
                }));
            }
        })();
    """

    fun getReaderHtml(
        title: String,
        content: String,
        leadImage: String = "",
        theme: String = "dark",
        fontSizeSp: Int = 18,
        byline: String? = null,
        wordCount: Int = 0,
        readingTimeMinutes: Int = 1
    ): String {
        val (bgColor, textColor, accentColor, cardBg, borderColor) = when (theme.lowercase()) {
            "sepia" -> listOf("#FBF0D9", "#3D2E1E", "#9A3412", "#F3E5AB", "#E2D1A6")
            "oled" -> listOf("#000000", "#F1F5F9", "#38BDF8", "#121212", "#27272A")
            "light" -> listOf("#FFFFFF", "#0F172A", "#0284C7", "#F1F5F9", "#E2E8F0")
            else -> listOf("#0F172A", "#E2E8F0", "#22D3EE", "#1E293B", "#334155") // "dark"
        }

        val leadImgHtml = if (leadImage.isNotBlank()) {
            """<img src="${leadImage}" class="lead-img" alt="Header Image" />"""
        } else ""

        val bylineHtml = if (!byline.isNullOrBlank()) {
            """<div class="byline">By ${byline}</div>"""
        } else ""

        val metaHtml = if (readingTimeMinutes > 0 || wordCount > 0) {
            """<div class="meta-row"><span>⏱ ${readingTimeMinutes} min read</span> • <span>${wordCount} words</span></div>"""
        } else ""

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>${title}</title>
                <style>
                    * { box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        line-height: 1.7;
                        font-size: ${fontSizeSp}px;
                        color: ${textColor};
                        background-color: ${bgColor};
                        padding: 24px 20px 80px 20px;
                        max-width: 820px;
                        margin: 0 auto;
                        word-break: break-word;
                    }
                    h1 {
                        color: ${accentColor};
                        font-size: 1.8em;
                        line-height: 1.3;
                        margin-bottom: 0.4em;
                        font-weight: 700;
                    }
                    .byline {
                        font-size: 0.9em;
                        opacity: 0.85;
                        margin-bottom: 0.4em;
                        font-weight: 500;
                    }
                    .meta-row {
                        font-size: 0.8em;
                        opacity: 0.65;
                        margin-bottom: 1.2em;
                        padding-bottom: 0.8em;
                        border-bottom: 1px solid ${borderColor};
                    }
                    .lead-img {
                        width: 100%;
                        max-height: 400px;
                        object-fit: cover;
                        border-radius: 12px;
                        margin: 1em 0;
                        border: 1px solid ${borderColor};
                    }
                    img {
                        max-width: 100%;
                        height: auto;
                        border-radius: 8px;
                        margin: 1.2em 0;
                    }
                    p { margin-bottom: 1.3em; }
                    a { color: ${accentColor}; text-decoration: underline; }
                    pre, code {
                        background: ${cardBg};
                        border: 1px solid ${borderColor};
                        padding: 12px;
                        border-radius: 8px;
                        overflow-x: auto;
                        font-family: monospace;
                        font-size: 0.9em;
                    }
                    blockquote {
                        border-left: 4px solid ${accentColor};
                        margin: 1.5em 0;
                        padding-left: 16px;
                        font-style: italic;
                    }
                    .video-container {
                        position: relative;
                        padding-bottom: 56.25%;
                        height: 0;
                        overflow: hidden;
                        max-width: 100%;
                        margin: 1.5em 0;
                        border-radius: 12px;
                        border: 1px solid ${borderColor};
                    }
                    .video-container iframe {
                        position: absolute;
                        top: 0;
                        left: 0;
                        width: 100%;
                        height: 100%;
                        border: 0;
                    }
                    hr {
                        border: 0;
                        border-top: 1px solid ${borderColor};
                        margin: 24px 0;
                    }
                </style>
            </head>
            <body>
                <h1>${title}</h1>
                ${bylineHtml}
                ${metaHtml}
                ${leadImgHtml}
                <hr>
                <div id="content">
                    ${content}
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}

