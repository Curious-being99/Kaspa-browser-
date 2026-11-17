package com.example.util

import android.net.Uri
import android.util.Log
import com.example.network.CronetClientFactory
import com.example.ui.SupportedLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CSP-immune native + DOM webpage translator.
 *
 * Extracts visible text nodes and UI attributes directly via WebView DOM traversal,
 * translates deduplicated segments over native HTTPS (bypassing strict website CSP headers
 * that block external <script src="translate.google.com/..."> injection), and applies
 * translations cleanly in-place with instant non-destructive Original text restoration.
 */
object WebPageTranslator {
    private const val TAG = "WebPageTranslator"
    private const val BATCH_DELIMITER = "\n\n[[KSP]]\n\n"
    private val BATCH_SPLIT_REGEX = Regex("""\s*\[\s*\[\s*KSP\s*\]\s*\]\s*""", RegexOption.IGNORE_CASE)

    private val ENDPOINTS = listOf(
        "https://translate.googleapis.com/translate_a/single",
        "https://clients5.google.com/translate_a/single",
        "https://translate.googleapis.com/translate_a/t",
        "https://translate.google.com/m"
    )

    private val defaultHttpClient: OkHttpClient by lazy {
        CronetClientFactory.buildClient(
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
        )
    }

    data class ExtractedPageText(
        val ok: Boolean,
        val title: String,
        val texts: List<String>,
        val error: String? = null
    )

    data class TranslationResult(
        val translations: Map<String, String>,
        val translatedCount: Int,
        val totalRequested: Int,
        val unsupportedLanguage: Boolean = false
    )

    /**
     * JavaScript to snapshot original DOM text nodes & key UI attributes (restoring any prior
     * translation pass first so switching target languages never double-translates) and return
     * deduplicated visible segments as JSON.
     */
    val JS_EXTRACT_TEXT_NODES: String = """
        (function() {
            try {
                if (Array.isArray(window.__kaspaTransRecords) && window.__kaspaTransRecords.length > 0) {
                    for (var r = 0; r < window.__kaspaTransRecords.length; r++) {
                        var prev = window.__kaspaTransRecords[r];
                        try {
                            if (prev.type === 'text' && prev.node && prev.node.nodeValue !== prev.rawOrig) {
                                prev.node.nodeValue = prev.rawOrig;
                            } else if (prev.type === 'attr' && prev.el && prev.el.getAttribute(prev.attr) !== prev.rawOrig) {
                                prev.el.setAttribute(prev.attr, prev.rawOrig);
                            }
                        } catch (_) {}
                    }
                }
                if (typeof window.__kaspaOrigTitle === 'string' && document.title !== window.__kaspaOrigTitle) {
                    document.title = window.__kaspaOrigTitle;
                }
                window.__kaspaOrigTitle = document.title || '';

                var skipTags = {
                    'SCRIPT': 1, 'STYLE': 1, 'NOSCRIPT': 1, 'CODE': 1, 'PRE': 1,
                    'KBD': 1, 'SAMP': 1, 'VAR': 1, 'SVG': 1, 'MATH': 1,
                    'TEXTAREA': 1, 'TEMPLATE': 1, 'CANVAS': 1, 'IFRAME': 1
                };
                var hasLetters = /[a-zA-Z\u00C0-\u024F\u0370-\u03FF\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u0DFF\u0E00-\u0E7F\u1200-\u137F\u3040-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]/;
                var records = [];
                var uniqueMap = Object.create(null);
                var uniqueList = [];
                var maxUnique = 500;

                function registerSegment(trimmed) {
                    if (!trimmed || trimmed.length > 4500) return false;
                    if (!hasLetters.test(trimmed)) return false;
                    if (Object.prototype.hasOwnProperty.call(uniqueMap, trimmed)) return true;
                    if (uniqueList.length >= maxUnique) return false;
                    uniqueMap[trimmed] = uniqueList.length;
                    uniqueList.push(trimmed);
                    return true;
                }

                if (window.__kaspaOrigTitle) {
                    registerSegment(window.__kaspaOrigTitle.trim());
                }

                if (document.body) {
                    var walker = document.createTreeWalker(
                        document.body,
                        NodeFilter.SHOW_TEXT,
                        {
                            acceptNode: function(node) {
                                var parent = node.parentElement;
                                if (!parent) return NodeFilter.FILTER_REJECT;
                                var cur = parent;
                                while (cur && cur !== document.body) {
                                    if (skipTags[cur.tagName] || cur.isContentEditable) {
                                        return NodeFilter.FILTER_REJECT;
                                    }
                                    if (cur.getAttribute && cur.getAttribute('translate') === 'no') {
                                        return NodeFilter.FILTER_REJECT;
                                    }
                                    if (cur.classList && cur.classList.contains('notranslate')) {
                                        return NodeFilter.FILTER_REJECT;
                                    }
                                    cur = cur.parentElement;
                                }
                                return NodeFilter.FILTER_ACCEPT;
                            }
                        },
                        false
                    );
                    var n;
                    while ((n = walker.nextNode())) {
                        var raw = n.nodeValue;
                        if (!raw) continue;
                        var trimmed = raw.trim();
                        if (!trimmed) continue;
                        if (!registerSegment(trimmed)) continue;
                        var leadMatch = raw.match(/^\s*/);
                        var trailMatch = raw.match(/\s*$/);
                        records.push({
                            type: 'text',
                            node: n,
                            key: trimmed,
                            rawOrig: raw,
                            lead: leadMatch ? leadMatch[0] : '',
                            trail: trailMatch ? trailMatch[0] : ''
                        });
                    }

                    var attrEls = document.body.querySelectorAll(
                        '[placeholder], [title], [alt], input[type="submit"], input[type="button"], input[type="reset"]'
                    );
                    for (var i = 0; i < attrEls.length && uniqueList.length < maxUnique; i++) {
                        var el = attrEls[i];
                        if (el.closest && el.closest('[translate="no"], .notranslate')) continue;
                        var attrs = ['placeholder', 'title', 'alt'];
                        if (el.tagName === 'INPUT') attrs.push('value');
                        for (var a = 0; a < attrs.length; a++) {
                            var attrName = attrs[a];
                            var val = el.getAttribute(attrName);
                            if (!val) continue;
                            var tVal = val.trim();
                            if (!registerSegment(tVal)) continue;
                            var lm = val.match(/^\s*/);
                            var tm = val.match(/\s*$/);
                            records.push({
                                type: 'attr',
                                el: el,
                                attr: attrName,
                                key: tVal,
                                rawOrig: val,
                                lead: lm ? lm[0] : '',
                                trail: tm ? tm[0] : ''
                            });
                        }
                    }
                }

                window.__kaspaTransRecords = records;
                return JSON.stringify({
                    ok: true,
                    title: window.__kaspaOrigTitle ? window.__kaspaOrigTitle.trim() : '',
                    texts: uniqueList
                });
            } catch (e) {
                return JSON.stringify({ ok: false, error: String(e), texts: [] });
            }
        })();
    """.trimIndent()

    /**
     * JavaScript to restore original text nodes, attributes, and document title immediately.
     */
    val JS_RESTORE_ORIGINALS: String = """
        (function() {
            try {
                var restored = 0;
                if (Array.isArray(window.__kaspaTransRecords)) {
                    for (var i = 0; i < window.__kaspaTransRecords.length; i++) {
                        var rec = window.__kaspaTransRecords[i];
                        try {
                            if (rec.type === 'text' && rec.node) {
                                if (rec.node.nodeValue !== rec.rawOrig) {
                                    rec.node.nodeValue = rec.rawOrig;
                                }
                                restored++;
                            } else if (rec.type === 'attr' && rec.el) {
                                if (rec.el.getAttribute(rec.attr) !== rec.rawOrig) {
                                    rec.el.setAttribute(rec.attr, rec.rawOrig);
                                }
                                restored++;
                            }
                        } catch (_) {}
                    }
                }
                if (typeof window.__kaspaOrigTitle === 'string' && window.__kaspaOrigTitle.length > 0) {
                    document.title = window.__kaspaOrigTitle;
                }
                window.__kaspaTranslatedActive = false;
                return restored > 0 ? 'restore-requested' : 'not-translated';
            } catch (e) {
                return 'restore-failed';
            }
        })();
    """.trimIndent()

    /**
     * Builds the JavaScript invocation that applies translated strings back onto the extracted
     * DOM records cleanly without touching HTML structure or event listeners.
     */
    fun buildApplyTranslationsScript(translations: Map<String, String>): String {
        val jsonPayload = JSONObject().apply {
            translations.forEach { (k, v) -> put(k, v) }
        }.toString()
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")

        return """
            (function(map) {
                try {
                    var applied = 0;
                    if (Array.isArray(window.__kaspaTransRecords)) {
                        for (var i = 0; i < window.__kaspaTransRecords.length; i++) {
                            var rec = window.__kaspaTransRecords[i];
                            var translated = map[rec.key];
                            if (typeof translated === 'string' && translated.length > 0) {
                                var fullVal = rec.lead + translated + rec.trail;
                                if (rec.type === 'text' && rec.node) {
                                    rec.node.nodeValue = fullVal;
                                    applied++;
                                } else if (rec.type === 'attr' && rec.el) {
                                    rec.el.setAttribute(rec.attr, fullVal);
                                    applied++;
                                }
                            }
                        }
                    }
                    if (typeof window.__kaspaOrigTitle === 'string') {
                        var titleKey = window.__kaspaOrigTitle.trim();
                        if (typeof map[titleKey] === 'string' && map[titleKey].length > 0) {
                            document.title = map[titleKey];
                            if (applied === 0) applied = 1;
                        }
                    }
                    window.__kaspaTranslatedActive = applied > 0;
                    return applied;
                } catch (e) {
                    return -1;
                }
            })($jsonPayload);
        """.trimIndent()
    }

    /**
     * Parses the raw string returned by WebView.evaluateJavascript(JS_EXTRACT_TEXT_NODES).
     */
    fun parseExtractedPageText(rawJsResult: String?): ExtractedPageText {
        if (rawJsResult.isNullOrBlank() || rawJsResult == "null") {
            return ExtractedPageText(ok = false, title = "", texts = emptyList(), error = "empty")
        }
        return try {
            val unwrapped = when (val parsed = JSONTokener(rawJsResult).nextValue()) {
                is String -> parsed
                is JSONObject -> parsed.toString()
                else -> rawJsResult
            }
            val obj = JSONObject(unwrapped)
            val ok = obj.optBoolean("ok", false)
            val title = obj.optString("title", "")
            val arr = obj.optJSONArray("texts") ?: JSONArray()
            val list = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val item = arr.optString(i, "").trim()
                if (item.isNotBlank()) list.add(item)
            }
            ExtractedPageText(
                ok = ok,
                title = title,
                texts = list,
                error = obj.optString("error", "").takeIf { it.isNotBlank() }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to parse extracted page text: ${t.message}")
            ExtractedPageText(ok = false, title = "", texts = emptyList(), error = t.message)
        }
    }

    /**
     * Validates and canonicalizes a target language code against SupportedLanguage.ALL.
     */
    fun normalizeLanguageCode(code: String): String? {
        val trimmed = code.trim()
        val canonicalInput = if (trimmed.equals("iw", ignoreCase = true)) "he" else trimmed
        val match = SupportedLanguage.ALL.firstOrNull { it.code.equals(canonicalInput, ignoreCase = true) }
            ?: return null
        val sanitized = match.code.filter { it.isLetter() || it == '-' }
        return sanitized.takeIf { it.isNotBlank() }
    }

    /**
     * Builds chunks of strings keeping each batch small enough for fast, reliable translation.
     */
    internal fun chunkStrings(
        strings: List<String>,
        maxBatchChars: Int = 950,
        maxBatchItems: Int = 12
    ): List<List<String>> {
        if (strings.isEmpty()) return emptyList()
        val batches = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var currentLen = 0
        for (item in strings) {
            if (item.length >= maxBatchChars) {
                if (current.isNotEmpty()) {
                    batches.add(current)
                    current = mutableListOf()
                    currentLen = 0
                }
                batches.add(listOf(item))
                continue
            }
            val separatorLen = if (current.isEmpty()) 0 else BATCH_DELIMITER.length
            if (current.size >= maxBatchItems || (currentLen + separatorLen + item.length) > maxBatchChars) {
                if (current.isNotEmpty()) {
                    batches.add(current)
                    current = mutableListOf()
                    currentLen = 0
                }
            }
            current.add(item)
            currentLen += (if (current.size == 1) 0 else BATCH_DELIMITER.length) + item.length
        }
        if (current.isNotEmpty()) {
            batches.add(current)
        }
        return batches
    }

    /**
     * Translates a list of deduplicated strings to [targetLang] using native HTTP calls.
     * Batches strings with a verifiable delimiter and falls back per-item only if a batch
     * delimiter is altered by the translation engine.
     */
    suspend fun translateStrings(
        strings: List<String>,
        targetLang: String,
        httpClient: OkHttpClient = defaultHttpClient,
        endpoints: List<String> = ENDPOINTS
    ): TranslationResult = withContext(Dispatchers.IO) {
        val safeLang = normalizeLanguageCode(targetLang)
            ?: return@withContext TranslationResult(emptyMap(), 0, strings.size)
        val unique = strings.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (unique.isEmpty()) {
            return@withContext TranslationResult(emptyMap(), 0, 0)
        }

        val chunks = chunkStrings(unique)
        val semaphore = Semaphore(permits = 4)
        val outMap = LinkedHashMap<String, String>(unique.size)
        val sawHttp400 = AtomicBoolean(false)

        val chunkResults: List<Map<String, String>> = coroutineScope {
            chunks.map { chunk ->
                async {
                    semaphore.withPermit {
                        translateChunk(chunk, safeLang, httpClient, endpoints, sawHttp400)
                    }
                }
            }.awaitAll()
        }

        chunkResults.forEach { partial ->
            outMap.putAll(partial)
        }

        TranslationResult(
            translations = outMap,
            translatedCount = outMap.size,
            totalRequested = unique.size,
            unsupportedLanguage = outMap.isEmpty() && sawHttp400.get()
        )
    }

    private suspend fun translateChunk(
        chunk: List<String>,
        targetLang: String,
        httpClient: OkHttpClient,
        endpoints: List<String>,
        sawHttp400: AtomicBoolean
    ): Map<String, String> {
        if (chunk.isEmpty()) return emptyMap()
        if (chunk.size == 1) {
            val orig = chunk[0]
            val translated = translateSingleText(orig, targetLang, httpClient, endpoints, sawHttp400)
            return if (!translated.isNullOrBlank()) mapOf(orig to translated) else emptyMap()
        }

        val joined = chunk.joinToString(BATCH_DELIMITER)
        val translatedJoined = translateRawPayload(joined, targetLang, httpClient, endpoints, sawHttp400)
        if (!translatedJoined.isNullOrBlank()) {
            val parts = translatedJoined.split(BATCH_SPLIT_REGEX)
            if (parts.size == chunk.size) {
                val result = LinkedHashMap<String, String>(chunk.size)
                for (i in chunk.indices) {
                    val piece = parts[i].trim()
                    if (piece.isNotBlank()) {
                        result[chunk[i]] = piece
                    }
                }
                if (result.size == chunk.size) {
                    return result
                }
            }
        }

        // Delimiter was altered or dropped by translation grammar; fall back to individual items for this small chunk
        val fallback = LinkedHashMap<String, String>(chunk.size)
        for (orig in chunk) {
            val single = translateSingleText(orig, targetLang, httpClient, endpoints, sawHttp400)
            if (!single.isNullOrBlank()) {
                fallback[orig] = single
            }
        }
        return fallback
    }

    private fun translateSingleText(
        text: String,
        targetLang: String,
        httpClient: OkHttpClient,
        endpoints: List<String>,
        sawHttp400: AtomicBoolean
    ): String? {
        if (text.length <= 2800) {
            return translateRawPayload(text, targetLang, httpClient, endpoints, sawHttp400)
        }
        // Split very long paragraphs on whitespace boundaries cleanly
        val parts = mutableListOf<String>()
        var idx = 0
        while (idx < text.length) {
            var end = (idx + 2400).coerceAtMost(text.length)
            if (end < text.length) {
                val breakAt = text.lastIndexOfAny(charArrayOf('.', '!', '?', '\n', ' '), startIndex = end)
                if (breakAt > idx + 400) end = breakAt + 1
            }
            val sub = text.substring(idx, end).trim()
            if (sub.isNotBlank()) {
                val t = translateRawPayload(sub, targetLang, httpClient, endpoints, sawHttp400) ?: sub
                parts.add(t)
            }
            idx = end
        }
        return parts.joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun translateRawPayload(
        payload: String,
        targetLang: String,
        httpClient: OkHttpClient,
        endpoints: List<String>,
        sawHttp400: AtomicBoolean
    ): String? {
        val usePost = payload.length > 900
        for (baseEndpoint in endpoints) {
            try {
                val encodedLang = URLEncoder.encode(targetLang, "UTF-8")
                val isMobileHtml = baseEndpoint.endsWith("/m") || baseEndpoint.contains("/m?")
                val isTranslateAT = baseEndpoint.contains("/translate_a/t")
                if (isMobileHtml && usePost) {
                    continue
                }

                val request = when {
                    isMobileHtml -> {
                        val encodedText = URLEncoder.encode(payload, "UTF-8")
                        val sep = if (baseEndpoint.contains("?")) "&" else "?"
                        val url = "$baseEndpoint${sep}sl=auto&tl=$encodedLang&q=$encodedText"
                        Request.Builder()
                            .url(url)
                            .get()
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                            .build()
                    }
                    isTranslateAT -> {
                        if (usePost) {
                            val url = "$baseEndpoint?client=dict-chrome-ex&sl=auto&tl=$encodedLang"
                            val body = FormBody.Builder()
                                .add("q", payload)
                                .build()
                            Request.Builder()
                                .url(url)
                                .post(body)
                                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                                .build()
                        } else {
                            val encodedText = URLEncoder.encode(payload, "UTF-8")
                            val url = "$baseEndpoint?client=dict-chrome-ex&sl=auto&tl=$encodedLang&q=$encodedText"
                            Request.Builder()
                                .url(url)
                                .get()
                                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                                .build()
                        }
                    }
                    usePost -> {
                        val url = "$baseEndpoint?client=gtx&sl=auto&tl=$encodedLang&dt=t"
                        val body = FormBody.Builder()
                            .add("q", payload)
                            .build()
                        Request.Builder()
                            .url(url)
                            .post(body)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                            .build()
                    }
                    else -> {
                        val encodedText = URLEncoder.encode(payload, "UTF-8")
                        val url = "$baseEndpoint?client=gtx&sl=auto&tl=$encodedLang&dt=t&q=$encodedText"
                        Request.Builder()
                            .url(url)
                            .get()
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                            .build()
                    }
                }

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 400) {
                            sawHttp400.set(true)
                        }
                        Log.w(TAG, "Translate endpoint $baseEndpoint returned HTTP ${response.code} for tl=$targetLang")
                        return@use
                    }
                    val bodyStr = response.body?.string() ?: return@use
                    val parsed = when {
                        isMobileHtml -> parseMobileTranslateHtml(bodyStr)
                        isTranslateAT -> parseTranslateATResponse(bodyStr)
                        else -> parseGoogleTranslateSingleResponse(bodyStr)
                    }
                    if (!parsed.isNullOrBlank()) return parsed
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Translate endpoint $baseEndpoint failed: ${t.message}")
            }
        }
        return null
    }

    /**
     * Parses `translate_a/single?client=gtx&dt=t` JSON array responses into translated text.
     */
    internal fun parseGoogleTranslateSingleResponse(jsonBody: String): String? {
        return try {
            val root = JSONArray(jsonBody)
            val sentences = root.optJSONArray(0) ?: return null
            val sb = StringBuilder()
            for (i in 0 until sentences.length()) {
                val seg = sentences.optJSONArray(i) ?: continue
                if (!seg.isNull(0)) {
                    val piece = seg.optString(0, "")
                    if (piece.isNotEmpty() && piece != "null") {
                        sb.append(piece)
                    }
                }
            }
            sb.toString().takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Parses `translate_a/t?client=dict-chrome-ex` JSON responses into translated text.
     */
    internal fun parseTranslateATResponse(jsonBody: String): String? {
        return try {
            when (val parsed = JSONTokener(jsonBody).nextValue()) {
                is JSONArray -> {
                    if (parsed.length() == 0) return null
                    val sb = StringBuilder()
                    for (i in 0 until parsed.length()) {
                        when (val item = parsed.opt(i)) {
                            is String -> if (item.isNotBlank() && item != "null") sb.append(item)
                            is JSONArray -> {
                                val text = item.optString(0, "")
                                if (text.isNotBlank() && text != "null") sb.append(text)
                            }
                        }
                    }
                    sb.toString().takeIf { it.isNotBlank() }
                }
                is JSONObject -> {
                    val sentences = parsed.optJSONArray("sentences") ?: return null
                    val sb = StringBuilder()
                    for (i in 0 until sentences.length()) {
                        val s = sentences.optJSONObject(i)?.optString("trans", "") ?: ""
                        if (s.isNotEmpty()) sb.append(s)
                    }
                    sb.toString().takeIf { it.isNotBlank() }
                }
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Parses `translate.google.com/m` lightweight mobile HTML (`div.result-container`).
     */
    internal fun parseMobileTranslateHtml(html: String): String? {
        return try {
            Jsoup.parse(html).selectFirst(".result-container")?.text()?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Builds the full-proxy Google Translate Gateway URL for an HTTP/HTTPS webpage.
     */
    fun buildGatewayTranslateUrl(pageUrl: String, targetLang: String): String? {
        val safeLang = normalizeLanguageCode(targetLang) ?: return null
        val original = extractOriginalFromGatewayUrl(pageUrl) ?: pageUrl.trim()
        if (!original.startsWith("http://", ignoreCase = true) &&
            !original.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        return "https://translate.google.com/translate?sl=auto&tl=${Uri.encode(safeLang)}&u=${Uri.encode(original)}"
    }

    /**
     * Detects whether a URL is a Google Translate Gateway proxy page.
     */
    fun isGatewayTranslateUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return lower.startsWith("https://translate.google.com/translate") ||
            lower.contains(".translate.goog/")
    }

    /**
     * Extracts the underlying original page URL from a `translate.google.com/translate?...&u=` URL.
     */
    fun extractOriginalFromGatewayUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return null
            if (host == "translate.google.com" && uri.path?.startsWith("/translate") == true) {
                val uParam = uri.getQueryParameter("u")?.trim()
                if (!uParam.isNullOrBlank() &&
                    (uParam.startsWith("http://", ignoreCase = true) || uParam.startsWith("https://", ignoreCase = true))
                ) {
                    return uParam
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }
}
