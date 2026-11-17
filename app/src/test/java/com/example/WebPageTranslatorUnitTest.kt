package com.example

import com.example.util.WebPageTranslator
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WebPageTranslatorUnitTest {

    @Test
    fun normalizeLanguageCodeAcceptsSupportedCodesAndRejectsInvalid() {
        assertEquals("es", WebPageTranslator.normalizeLanguageCode("ES"))
        assertEquals("zh-CN", WebPageTranslator.normalizeLanguageCode("zh-cn"))
        assertEquals("zh-TW", WebPageTranslator.normalizeLanguageCode("zh-tw"))
        assertEquals("ja", WebPageTranslator.normalizeLanguageCode(" ja "))
        assertEquals("he", WebPageTranslator.normalizeLanguageCode("he"))
        assertEquals("he", WebPageTranslator.normalizeLanguageCode("iw"))
        assertEquals("kri", WebPageTranslator.normalizeLanguageCode("kri"))
        assertEquals("tpi", WebPageTranslator.normalizeLanguageCode("tpi"))
        assertEquals("jam", WebPageTranslator.normalizeLanguageCode("jam"))
        assertEquals("sw", WebPageTranslator.normalizeLanguageCode("sw"))
        assertEquals("bn", WebPageTranslator.normalizeLanguageCode("bn"))
        assertEquals("fa", WebPageTranslator.normalizeLanguageCode("fa"))
        assertNull(WebPageTranslator.normalizeLanguageCode("pcm"))
        assertNull(WebPageTranslator.normalizeLanguageCode("xx-invalid';alert(1)"))
        assertNull(WebPageTranslator.normalizeLanguageCode(""))
    }

    @Test
    fun gatewayUrlBuildDetectAndUnwrapRoundTrip() {
        val original = "https://kaspa.org/resources/?tab=overview"
        val gateway = WebPageTranslator.buildGatewayTranslateUrl(original, "fr")
        assertNotNull(gateway)
        assertTrue(WebPageTranslator.isGatewayTranslateUrl(gateway))
        assertEquals(original, WebPageTranslator.extractOriginalFromGatewayUrl(gateway))

        // Switching language while already on a gateway URL preserves original URL
        val switched = WebPageTranslator.buildGatewayTranslateUrl(gateway!!, "de")
        assertNotNull(switched)
        assertTrue(switched!!.contains("tl=de"))
        assertEquals(original, WebPageTranslator.extractOriginalFromGatewayUrl(switched))

        assertNull(WebPageTranslator.buildGatewayTranslateUrl("javascript:alert(1)", "es"))
        assertFalse(WebPageTranslator.isGatewayTranslateUrl("https://kaspa.org"))
    }

    @Test
    fun parseExtractedPageTextHandlesQuotedJsResultAndRawJson() {
        val quotedJsResult = "\"{\\\"ok\\\":true,\\\"title\\\":\\\"Kaspa Home\\\",\\\"texts\\\":[\\\"Hello\\\",\\\"Decentralized\\\"]}\""
        val parsed = WebPageTranslator.parseExtractedPageText(quotedJsResult)
        assertTrue(parsed.ok)
        assertEquals("Kaspa Home", parsed.title)
        assertEquals(listOf("Hello", "Decentralized"), parsed.texts)

        val empty = WebPageTranslator.parseExtractedPageText("null")
        assertFalse(empty.ok)
        assertTrue(empty.texts.isEmpty())
    }

    @Test
    fun parseGoogleTranslateSingleResponseCombinesAllSentences() {
        val body = """
            [
              [
                ["Hola ", "Hello ", null, null, 10],
                ["mundo.", "world.", null, null, 10]
              ],
              null,
              "en"
            ]
        """.trimIndent()
        val parsed = WebPageTranslator.parseGoogleTranslateSingleResponse(body)
        assertEquals("Hola mundo.", parsed)
    }

    @Test
    fun translateStringsBatchesAndFallsBackWhenDelimiterMangled() = runBlocking {
        var callCount = 0
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                callCount++
                val req = chain.request()
                val q = req.url.queryParameter("q").orEmpty()
                val translated = if (q.contains("[[KSP]]") && q.contains("First")) {
                    // Simulate translator altering/dropping separator in batch 1 -> triggers per-item fallback
                    "Mangled batch output without marker"
                } else {
                    when (q) {
                        "First" -> "Primero"
                        "Second" -> "Segundo"
                        else -> "T-$q"
                    }
                }
                val json = JSONArray().apply {
                    put(JSONArray().apply {
                        put(JSONArray().apply {
                            put(translated)
                            put(q)
                        })
                    })
                }.toString()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val result = WebPageTranslator.translateStrings(
            strings = listOf("First", "Second", "First"),
            targetLang = "es",
            httpClient = mockClient,
            endpoints = listOf("https://mock.translate.local/translate_a/single")
        )

        assertEquals(2, result.totalRequested)
        assertEquals(2, result.translatedCount)
        assertEquals("Primero", result.translations["First"])
        assertEquals("Segundo", result.translations["Second"])
        assertTrue("Expected batch attempt + fallback calls", callCount >= 3)
    }

    @Test
    fun translateStringsFailsOverToBackupEndpointOnPrimaryFailure() = runBlocking {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                if (req.url.host == "primary.fail.local") {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("Service Unavailable")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                } else {
                    val q = req.url.queryParameter("q").orEmpty()
                    val mapped = q.replace("Welcome", "Bienvenido").replace("Home", "Inicio")
                    val json = JSONArray().apply {
                        put(JSONArray().apply {
                            put(JSONArray().apply {
                                put(mapped)
                                put(q)
                            })
                        })
                    }.toString()
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(json.toResponseBody("application/json".toMediaType()))
                        .build()
                }
            })
            .build()

        val result = WebPageTranslator.translateStrings(
            strings = listOf("Welcome", "Home"),
            targetLang = "es",
            httpClient = mockClient,
            endpoints = listOf(
                "https://primary.fail.local/translate_a/single",
                "https://backup.ok.local/translate_a/single"
            )
        )

        assertEquals("Bienvenido", result.translations["Welcome"])
        assertEquals("Inicio", result.translations["Home"])
    }

    @Test
    fun buildApplyTranslationsScriptEscapesSafely() {
        val script = WebPageTranslator.buildApplyTranslationsScript(
            mapOf("It's \"live\"\u2028" to "C'est \"en direct\"")
        )
        assertTrue(script.contains("__kaspaTransRecords"))
        assertFalse(script.contains("\u2028"))
        assertTrue(script.contains("C'est"))
    }

    @Test
    fun translateStringsFallsBackToTranslateATAndMobileHtmlAndReports400() = runBlocking {
        val clientWithFallback = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                when {
                    req.url.encodedPath.contains("/translate_a/single") -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(400)
                            .message("Bad Request")
                            .body("".toResponseBody("text/plain".toMediaType()))
                            .build()
                    }
                    req.url.encodedPath.contains("/translate_a/t") -> {
                        val q = req.url.queryParameter("q").orEmpty()
                        val json = if (q == "Hello") """["Aw yu du"]""" else """[["Welkom","en"]]"""
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(json.toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    else -> {
                        val html = "<html><body><div class=\"result-container\">Barka da zuwa</div></body></html>"
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(html.toResponseBody("text/html".toMediaType()))
                            .build()
                    }
                }
            })
            .build()

        val fallbackRes = WebPageTranslator.translateStrings(
            strings = listOf("Hello"),
            targetLang = "tpi",
            httpClient = clientWithFallback,
            endpoints = listOf(
                "https://mock.translate.local/translate_a/single",
                "https://mock.translate.local/translate_a/t"
            )
        )
        assertEquals("Aw yu du", fallbackRes.translations["Hello"])
        assertFalse(fallbackRes.unsupportedLanguage)

        val mobileRes = WebPageTranslator.translateStrings(
            strings = listOf("Welcome"),
            targetLang = "kri",
            httpClient = clientWithFallback,
            endpoints = listOf("https://mock.translate.local/m")
        )
        assertEquals("Barka da zuwa", mobileRes.translations["Welcome"])

        val always400Client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(400)
                    .message("Bad Request")
                    .body("".toResponseBody("text/plain".toMediaType()))
                    .build()
            })
            .build()
        val failed400 = WebPageTranslator.translateStrings(
            strings = listOf("Hello"),
            targetLang = "kri",
            httpClient = always400Client,
            endpoints = listOf("https://mock.translate.local/translate_a/single")
        )
        assertTrue(failed400.translations.isEmpty())
        assertTrue(failed400.unsupportedLanguage)
    }
}
