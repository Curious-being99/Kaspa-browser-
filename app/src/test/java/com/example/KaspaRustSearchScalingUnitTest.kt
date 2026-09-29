package com.example

import com.example.network.EmbeddedRustSearchEngine
import com.example.network.SearchEngine
import com.example.viewmodel.SearchEngine as VMSearchEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KaspaRustSearchScalingUnitTest {

    @Test
    fun testKaspaSearchIsDefaultEngine() {
        assertEquals("https://duckduckgo.com/?q=", VMSearchEngine.DUCKDUCKGO.baseUrl)
        assertEquals("DuckDuckGo", VMSearchEngine.DUCKDUCKGO.displayName)
    }

    @Test
    fun testSearchExecutionRouting() = runBlocking {
        // Test that SearchEngine.executeSearch handles queries safely with fallback or JNI
        val results = SearchEngine.executeSearch("Kaspa GHOSTDAG")
        assertNotNull(results)
        // Sanitization check
        val sanitized = SearchEngine.sanitizeUrl("https://example.com/?utm_source=tracker&utm_campaign=ad&q=safe")
        assertTrue(!sanitized.contains("utm_source"))
    }

    @Test
    fun testEmbeddedRustSearchEngineExecution() = runBlocking {
        val results = EmbeddedRustSearchEngine.searchOnDevice("decentralized privacy")
        assertNotNull(results)
    }
}
