package com.example

import com.example.network.KaspaReaderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeReaderModeUnitTest {

    @Test
    fun testArticleExtractionWithVideosAndNoiseRemoval() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Original HTML Title</title>
                <meta property="og:title" content="Kaspa Decentralized Web Innovations" />
                <meta name="author" content="Satoshi Nakamoto" />
                <meta property="og:image" content="https://kaspa.org/images/hero.jpg" />
            </head>
            <body>
                <header>
                    <nav><a href="/">Home</a><a href="/news">News</a></nav>
                </header>
                <div class="banner-ad">Buy crypto now banner ad</div>
                <article>
                    <h1>Kaspa Decentralized Web Innovations</h1>
                    <p>Kaspa is an innovative proof-of-work cryptocurrency implementing the GHOSTDAG protocol.</p>
                    <p>Unlike traditional blockchains, GHOSTDAG does not orphan blocks formed in parallel.</p>
                    <!-- Embedded YouTube video that MUST be preserved for safe media playback -->
                    <iframe src="https://www.youtube.com/embed/dQw4w9WgXcQ" width="560" height="315"></iframe>
                    <!-- Embedded ad tracker iframe that MUST be stripped -->
                    <iframe src="https://doubleclick.net/ad/track.html"></iframe>
                    <p>It allows blocks to coexist and orders them in consensus with sub-second confirmation times.</p>
                </article>
                <aside class="sidebar">Recent articles sidebar</aside>
                <footer>
                    <p>Copyright 2026 Kaspa Network. All rights reserved.</p>
                </footer>
            </body>
            </html>
        """.trimIndent()

        val article = KaspaReaderMode.extractArticle(sampleHtml)

        // 1. Verify Title & Image Extraction
        assertEquals("Kaspa Decentralized Web Innovations", article.title)
        assertEquals("https://kaspa.org/images/hero.jpg", article.leadImage)

        // 2. Verify Noise Removal
        assertFalse(article.contentHtml.contains("Buy crypto now banner ad"))
        assertFalse(article.contentHtml.contains("Home</a>"))
        assertFalse(article.contentHtml.contains("Recent articles sidebar"))
        assertFalse(article.contentHtml.contains("Copyright 2026 Kaspa Network"))

        // 3. Verify Video Preservation (CRITICAL)
        assertTrue("YouTube embed must be preserved", article.hasVideo)
        assertTrue("YouTube iframe must be preserved in content", article.contentHtml.contains("youtube.com/embed/dQw4w9WgXcQ"))
        assertFalse("Ad iframe must be removed", article.contentHtml.contains("doubleclick.net"))

        // 4. Verify Content & Metrics
        assertTrue(article.wordCount > 10)
        assertEquals(1, article.readingTimeMinutes)
    }

    @Test
    fun testGenerateReaderHtmlFormatting() {
        val sampleHtml = """
            <html>
            <head><title>Clean News</title></head>
            <body>
                <main>
                    <h1>Clean News Article</h1>
                    <p>Reading in peace without tracking or layout shifts.</p>
                </main>
            </body>
            </html>
        """.trimIndent()

        val readerHtml = KaspaReaderMode.convertRawHtmlToReaderHtml(
            rawHtml = sampleHtml,
            theme = "sepia",
            fontSizeSp = 20
        )

        assertNotNull(readerHtml)
        assertTrue(readerHtml.contains("Clean News Article"))
        assertTrue(readerHtml.contains("Reading in peace without tracking"))
        // Sepia theme background check
        assertTrue(readerHtml.contains("#FBF0D9"))
        // Font size check
        assertTrue(readerHtml.contains("20px"))
    }
}
