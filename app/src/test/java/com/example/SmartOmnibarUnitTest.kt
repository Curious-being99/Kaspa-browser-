package com.example

import com.example.omnibar.SmartOmnibarEngine
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class SmartOmnibarUnitTest {

    @Test
    fun testMathEvaluation_basicArithmetic() {
        val mult = SmartOmnibarEngine.evaluateMath("25 * 40")
        assertNotNull(mult)
        assertEquals(1000.0, mult!!.numericResult, 0.001)

        val multX = SmartOmnibarEngine.evaluateMath("25 x 40")
        assertNotNull(multX)
        assertEquals(1000.0, multX!!.numericResult, 0.001)

        val multUnicode = SmartOmnibarEngine.evaluateMath("25 × 40")
        assertNotNull(multUnicode)
        assertEquals(1000.0, multUnicode!!.numericResult, 0.001)

        val multTimes = SmartOmnibarEngine.evaluateMath("25 times 40")
        assertNotNull(multTimes)
        assertEquals(1000.0, multTimes!!.numericResult, 0.001)

        val multBy = SmartOmnibarEngine.evaluateMath("25 multiplied by 40")
        assertNotNull(multBy)
        assertEquals(1000.0, multBy!!.numericResult, 0.001)

        val add = SmartOmnibarEngine.evaluateMath("125 + 75")
        assertNotNull(add)
        assertEquals(200.0, add!!.numericResult, 0.001)

        val div = SmartOmnibarEngine.evaluateMath("1000 / 8")
        assertNotNull(div)
        assertEquals(125.0, div!!.numericResult, 0.001)

        val divUnicode = SmartOmnibarEngine.evaluateMath("1000 ÷ 8")
        assertNotNull(divUnicode)
        assertEquals(125.0, divUnicode!!.numericResult, 0.001)

        val divWords = SmartOmnibarEngine.evaluateMath("1000 divided by 8")
        assertNotNull(divWords)
        assertEquals(125.0, divWords!!.numericResult, 0.001)

        val divOver = SmartOmnibarEngine.evaluateMath("1000 over 8")
        assertNotNull(divOver)
        assertEquals(125.0, divOver!!.numericResult, 0.001)

        val mod = SmartOmnibarEngine.evaluateMath("29 mod 7")
        assertNotNull(mod)
        assertEquals(1.0, mod!!.numericResult, 0.001)
    }

    @Test
    fun testMathEvaluation_advancedTrigonometry() {
        // sin(pi / 2) == 1
        val sinPi = SmartOmnibarEngine.evaluateMath("sin(pi / 2)")
        assertNotNull(sinPi)
        assertEquals(1.0, sinPi!!.numericResult, 0.0001)

        // cos(0) == 1
        val cosZero = SmartOmnibarEngine.evaluateMath("cos(0)")
        assertNotNull(cosZero)
        assertEquals(1.0, cosZero!!.numericResult, 0.0001)

        // tan(pi / 4) == 1
        val tanPi4 = SmartOmnibarEngine.evaluateMath("tan(pi / 4)")
        assertNotNull(tanPi4)
        assertEquals(1.0, tanPi4!!.numericResult, 0.0001)

        // sin(90 deg) == 1
        val sinDeg = SmartOmnibarEngine.evaluateMath("sin(90 deg)")
        assertNotNull(sinDeg)
        assertEquals(1.0, sinDeg!!.numericResult, 0.0001)
    }

    @Test
    fun testMathEvaluation_advancedScientific() {
        // sqrt(144) == 12
        val sqrtVal = SmartOmnibarEngine.evaluateMath("sqrt(144)")
        assertNotNull(sqrtVal)
        assertEquals(12.0, sqrtVal!!.numericResult, 0.0001)

        // cbrt(27) == 3
        val cbrtVal = SmartOmnibarEngine.evaluateMath("cbrt(27)")
        assertNotNull(cbrtVal)
        assertEquals(3.0, cbrtVal!!.numericResult, 0.0001)

        // log(1000) == 3
        val logVal = SmartOmnibarEngine.evaluateMath("log(1000)")
        assertNotNull(logVal)
        assertEquals(3.0, logVal!!.numericResult, 0.0001)

        // ln(e) == 1
        val lnVal = SmartOmnibarEngine.evaluateMath("ln(e)")
        assertNotNull(lnVal)
        assertEquals(1.0, lnVal!!.numericResult, 0.0001)

        // 2^10 == 1024
        val powVal = SmartOmnibarEngine.evaluateMath("2^10")
        assertNotNull(powVal)
        assertEquals(1024.0, powVal!!.numericResult, 0.0001)

        // abs(-42.5) == 42.5
        val absVal = SmartOmnibarEngine.evaluateMath("abs(-42.5)")
        assertNotNull(absVal)
        assertEquals(42.5, absVal!!.numericResult, 0.0001)
    }

    @Test
    fun testMathEvaluation_factorials() {
        // 5! == 120
        val fact5 = SmartOmnibarEngine.evaluateMath("5!")
        assertNotNull(fact5)
        assertEquals(120.0, fact5!!.numericResult, 0.001)

        // 0! == 1
        val fact0 = SmartOmnibarEngine.evaluateMath("0!")
        assertNotNull(fact0)
        assertEquals(1.0, fact0!!.numericResult, 0.001)

        // 5! + sqrt(64) == 128
        val factComb = SmartOmnibarEngine.evaluateMath("5! + sqrt(64)")
        assertNotNull(factComb)
        assertEquals(128.0, factComb!!.numericResult, 0.001)
    }

    @Test
    fun testMathEvaluation_implicitMultiplication() {
        // 3(4 + 5) == 27
        val imp1 = SmartOmnibarEngine.evaluateMath("3(4 + 5)")
        assertNotNull(imp1)
        assertEquals(27.0, imp1!!.numericResult, 0.001)

        // (2)(3) == 6
        val imp2 = SmartOmnibarEngine.evaluateMath("(2)(3)")
        assertNotNull(imp2)
        assertEquals(6.0, imp2!!.numericResult, 0.001)

        // 2pi == 6.283185...
        val imp3 = SmartOmnibarEngine.evaluateMath("2pi")
        assertNotNull(imp3)
        assertEquals(2 * PI, imp3!!.numericResult, 0.0001)
    }

    @Test
    fun testMathEvaluation_percentages() {
        val pct = SmartOmnibarEngine.evaluateMath("15% of 200")
        assertNotNull(pct)
        assertEquals(30.0, pct!!.numericResult, 0.001)

        val pctAdd = SmartOmnibarEngine.evaluateMath("100 + 20%")
        assertNotNull(pctAdd)
        assertEquals(120.0, pctAdd!!.numericResult, 0.001)

        val pctSub = SmartOmnibarEngine.evaluateMath("100 - 15%")
        assertNotNull(pctSub)
        assertEquals(85.0, pctSub!!.numericResult, 0.001)
    }

    @Test
    fun testMathEvaluation_cryptoConversion() {
        val kasToUsd = SmartOmnibarEngine.evaluateMath("500 kas in usd")
        assertNotNull(kasToUsd)
        assertTrue(kasToUsd!!.isCryptoConversion)
        assertTrue(kasToUsd.formattedResult.contains("USD"))

        val usdToKas = SmartOmnibarEngine.evaluateMath("100 usd in kas")
        assertNotNull(usdToKas)
        assertTrue(usdToKas!!.isCryptoConversion)
        assertTrue(usdToKas.formattedResult.contains("KAS"))
    }

    @Test
    fun testMathEvaluation_excludesNonMath() {
        val regularUrl = SmartOmnibarEngine.evaluateMath("https://kaspa.org")
        assertNull(regularUrl)

        val regularQuery = SmartOmnibarEngine.evaluateMath("what is kaspa")
        assertNull(regularQuery)

        val incomplete = SmartOmnibarEngine.evaluateMath("5 +")
        assertNull(incomplete)
    }

    @Test
    fun testKaspaPriceQueryDetection() {
        assertTrue(SmartOmnibarEngine.isKaspaPriceQuery("kaspa price"))
        assertTrue(SmartOmnibarEngine.isKaspaPriceQuery("kas price"))
        assertTrue(SmartOmnibarEngine.isKaspaPriceQuery("\$kas"))
        assertTrue(SmartOmnibarEngine.isKaspaPriceQuery("kas/usd"))
        assertTrue(SmartOmnibarEngine.isKaspaPriceQuery("kaspa market"))

        assertFalse(SmartOmnibarEngine.isKaspaPriceQuery("kaspa explorer"))
        assertFalse(SmartOmnibarEngine.isKaspaPriceQuery("weather today"))
    }

    @Test
    fun testBuyKaspaQueryDetection() {
        assertTrue(SmartOmnibarEngine.isBuyKaspaQuery("buy kaspa"))
        assertTrue(SmartOmnibarEngine.isBuyKaspaQuery("buy kas"))
        assertTrue(SmartOmnibarEngine.isBuyKaspaQuery("how to buy kaspa"))
        assertTrue(SmartOmnibarEngine.isBuyKaspaQuery("where to buy kaspa"))

        assertFalse(SmartOmnibarEngine.isBuyKaspaQuery("kaspa price"))
        assertFalse(SmartOmnibarEngine.isBuyKaspaQuery("kaspa stream"))
    }

    @Test
    fun testWebsitePreview_realTimeTyping() {
        // Typing site alias "kaspa" immediately previews kaspa.org
        val previewKaspaAlias = SmartOmnibarEngine.getWebsitePreview("kaspa")
        assertNotNull(previewKaspaAlias)
        assertEquals("kaspa.org", previewKaspaAlias!!.domain)
        assertTrue(previewKaspaAlias.title.contains("Kaspa"))

        // Typing "google" immediately previews google.com
        val previewGoogle = SmartOmnibarEngine.getWebsitePreview("google")
        assertNotNull(previewGoogle)
        assertEquals("google.com", previewGoogle!!.domain)

        // Typing "dot.k" or "dot" immediately previews .k
        val previewDot = SmartOmnibarEngine.getWebsitePreview("dot")
        assertNotNull(previewDot)
        assertTrue(previewDot!!.isDecentralized)

        // Typing full URL
        val previewFull = SmartOmnibarEngine.getWebsitePreview("wikipedia.org")
        assertNotNull(previewFull)
        assertEquals("wikipedia.org", previewFull!!.domain)
    }

    @Test
    fun testTypoCorrection_generalWordsAndPhrases() {
        // Direct common typos
        assertEquals("the", SmartOmnibarEngine.findTypoCorrection("teh"))
        assertEquals("weather", SmartOmnibarEngine.findTypoCorrection("weatehr"))
        assertEquals("computer", SmartOmnibarEngine.findTypoCorrection("computr"))
        assertEquals("definitely", SmartOmnibarEngine.findTypoCorrection("definately"))
        assertEquals("restaurant", SmartOmnibarEngine.findTypoCorrection("restaraunt"))
        assertEquals("government", SmartOmnibarEngine.findTypoCorrection("goverment"))

        // Multi-word phrase corrections
        assertEquals("artificial intelligence", SmartOmnibarEngine.findTypoCorrection("artifical intellegence"))
        assertEquals("kaspa wallet download", SmartOmnibarEngine.findTypoCorrection("kapsa walet downlod"))
        assertEquals("weather today", SmartOmnibarEngine.findTypoCorrection("weatehr today"))

        // Damerau-Levenshtein transposition
        assertEquals("kaspa", SmartOmnibarEngine.findTypoCorrection("kapsa"))
        assertEquals("google", SmartOmnibarEngine.findTypoCorrection("googlr"))
    }
}
