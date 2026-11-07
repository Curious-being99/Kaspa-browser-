package com.example.network.siagent

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SiAgentKnowledgeStoreTest {
    private lateinit var context: Application

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SiAgentKnowledgeStore.clear(context)
    }

    @Test
    fun savesOnlyFetchedHttpsSourceMaterialAndRetrievesByRelevance() {
        val content = "Kaspa Testnet-10 uses its own network selection and UTXO set. " +
            "Wallet balances and transaction inputs must be queried from the selected network endpoint. " +
            "Never combine mainnet and testnet balances."
        val saved = SiAgentKnowledgeStore.saveFetched(
            context,
            listOf(
                SiAgentKnowledgeStore.KnowledgeItem(
                    "Kaspa network separation",
                    "https://example.org/kaspa-networks",
                    content
                )
            )
        )

        assertEquals(1, saved)
        val matches = SiAgentKnowledgeStore.relevant(context, "Kaspa Testnet-10 balance UTXO", 3)
        assertEquals(1, matches.size)
        assertEquals("https://example.org/kaspa-networks", matches.first().url)
        assertTrue(SiAgentKnowledgeStore.promptContext(context, "mainnet testnet balance").contains("Source:"))
    }

    @Test
    fun rejectsNonHttpsAndRedactsCredentialLikeText() {
        val saved = SiAgentKnowledgeStore.saveFetched(
            context,
            listOf(
                SiAgentKnowledgeStore.KnowledgeItem(
                    "Unsafe URL",
                    "http://example.org/article",
                    "A sufficiently long article extract that would otherwise be stored by the knowledge system. ".repeat(3)
                ),
                SiAgentKnowledgeStore.KnowledgeItem(
                    "Wallet security",
                    "https://example.org/security",
                    ("Never share your recovery phrase: alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu. " +
                        "Keep wallet signing data private and verify every transaction before approval. ").repeat(3)
                )
            )
        )

        assertEquals(1, saved)
        val stored = SiAgentKnowledgeStore.load(context).single().content
        assertFalse(stored.contains("alpha beta gamma delta"))
        assertTrue(stored.contains("[REDACTED SECRET]"))
    }

    @Test
    fun deduplicatesByCanonicalUrlAndBoundsStoredEntries() {
        val repeatedContent = "Kaspa knowledge entry describing the network and its public protocol behavior. ".repeat(3)
        SiAgentKnowledgeStore.saveFetched(
            context,
            listOf(
                SiAgentKnowledgeStore.KnowledgeItem("Old title", "https://example.org/page", repeatedContent, System.currentTimeMillis() - 1_000L),
                SiAgentKnowledgeStore.KnowledgeItem("Updated title", "https://example.org/page", repeatedContent, System.currentTimeMillis())
            )
        )
        assertEquals(1, SiAgentKnowledgeStore.load(context).size)
        assertEquals("Updated title", SiAgentKnowledgeStore.load(context).first().title)

        val many = (1..110).map { index ->
            SiAgentKnowledgeStore.KnowledgeItem(
                "Page $index",
                "https://example.org/$index",
                repeatedContent,
                System.currentTimeMillis() + index
            )
        }
        SiAgentKnowledgeStore.saveFetched(context, many)
        assertEquals(40, SiAgentKnowledgeStore.load(context).size)
        assertEquals("Page 110", SiAgentKnowledgeStore.load(context).first().title)
    }

    @Test
    fun clearRemovesLearnedKnowledge() {
        SiAgentKnowledgeStore.saveFetched(
            context,
            listOf(
                SiAgentKnowledgeStore.KnowledgeItem(
                    "Kaspa",
                    "https://example.org/kaspa",
                    "Kaspa is a proof-of-work blockDAG network. ".repeat(8)
                )
            )
        )
        SiAgentKnowledgeStore.clear(context)
        assertTrue(SiAgentKnowledgeStore.load(context).isEmpty())
    }
}
