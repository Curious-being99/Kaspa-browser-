package com.example.network.siagent

import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidWebResearchTest {
    @Test
    fun researchResultIncludesExactSourceUrls() {
        val result = AndroidWebResearch.ResearchResult(
            query = "Kaspa GHOSTDAG",
            sources = listOf(
                AndroidWebResearch.Source(
                    title = "Kaspa documentation",
                    url = "https://kaspa.org/",
                    excerpt = "GHOSTDAG is a proof-of-work protocol.",
                    fetched = true
                )
            )
        )

        val rendered = result.toUserText()
        assertTrue(rendered.contains("Kaspa documentation"))
        assertTrue(rendered.contains("https://kaspa.org/"))
        assertTrue(rendered.contains("GHOSTDAG is a proof-of-work protocol."))
    }

    @Test
    fun emptyResearchNeverInventsASource() {
        val rendered = AndroidWebResearch.ResearchResult(
            query = "not found",
            sources = emptyList(),
            error = "No results were retrieved."
        ).toUserText()

        assertTrue(rendered.contains("No results were retrieved."))
        assertTrue(!rendered.contains("https://"))
    }
}
