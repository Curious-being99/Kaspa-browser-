package com.example.news

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.data.KaspaNewsItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KaspaNewsNotificationWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("kaspa_news_notification_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun syncChannels_removesLegacyArticleAndViewModelChannels_andCreatesActiveChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    KaspaNewsNotificationWorker.LEGACY_ARTICLES_CHANNEL,
                    "All Kaspa Articles",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    KaspaNewsNotificationWorker.LEGACY_VIEWMODEL_CHANNEL,
                    "Kaspa News",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        KaspaNewsNotificationWorker.syncChannels(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertNull(manager.getNotificationChannel(KaspaNewsNotificationWorker.LEGACY_ARTICLES_CHANNEL))
            assertNull(manager.getNotificationChannel(KaspaNewsNotificationWorker.LEGACY_VIEWMODEL_CHANNEL))
            assertNotNull(manager.getNotificationChannel(KaspaNewsNotificationWorker.ECOSYSTEM_CHANNEL))
            assertNotNull(manager.getNotificationChannel(KaspaNewsNotificationWorker.BREAKING_CHANNEL))
        }
    }

    @Test
    fun classifyForNotification_skipsGeneralKaspaArticles_andRoutesEcosystemAndBreaking() {
        val generalArticle = KaspaNewsItem(
            title = "Kaspa community hosts weekly meetup in Berlin",
            desc = "Local supporters discuss adoption and community projects.",
            url = "https://example.org/kaspa-meetup",
            category = "News",
            timestamp = "Just now",
            epochMillis = 1_700_000_000_000L
        )
        val ecosystemUpdate = KaspaNewsItem(
            title = "Rusty-Kaspa mainnet node release v1.0.2 shipped",
            desc = "Includes GhostDAG sync optimizations for protocol operators.",
            url = "https://github.com/kaspanet/rusty-kaspa/releases/tag/v1.0.2",
            category = "GitHub",
            timestamp = "Just now",
            epochMillis = 1_700_000_001_000L
        )
        val breakingAlert = KaspaNewsItem(
            title = "Breaking: Urgent consensus bug patched in node update",
            desc = "Operators should update immediately.",
            url = "https://example.org/kaspa-security-alert",
            category = "News",
            timestamp = "Just now",
            epochMillis = 1_700_000_002_000L
        )

        assertNull(KaspaNewsNotificationWorker.classifyForNotification(generalArticle))
        assertEquals(
            KaspaNewsNotificationWorker.CATEGORY_ECOSYSTEM,
            KaspaNewsNotificationWorker.classifyForNotification(ecosystemUpdate)
        )
        assertEquals(
            KaspaNewsNotificationWorker.CATEGORY_BREAKING,
            KaspaNewsNotificationWorker.classifyForNotification(breakingAlert)
        )
    }

    @Test
    fun selectFreshNotificationsAndMarkSeen_suppressesFirstRunAndDuplicatesAcrossPublishersAndTrackingParams() {
        val baselineItem = KaspaNewsItem(
            title = "Rusty-Kaspa v1.0 release notes",
            desc = "Mainnet release notes",
            url = "https://github.com/kaspanet/rusty-kaspa/releases",
            category = "GitHub",
            timestamp = "1h ago",
            epochMillis = 1_700_000_000_000L
        )

        // First run silently populates baseline without notifying.
        val firstRun = KaspaNewsNotificationWorker.selectFreshNotificationsAndMarkSeen(
            context,
            listOf(baselineItem)
        )
        assertTrue(firstRun.isEmpty())

        // Same baseline item + duplicate story reported with publisher suffix and utm tracking params.
        val newEcosystemA = KaspaNewsItem(
            title = "KRC20 indexer mainnet upgrade goes live - CoinDesk",
            desc = "Ecosystem infrastructure upgrade completes.",
            url = "https://example.com/krc20-upgrade/?utm_source=rss&utm_medium=feed#top",
            category = "News",
            timestamp = "5m ago",
            epochMillis = 1_700_000_100_000L
        )
        val duplicateEcosystemA = KaspaNewsItem(
            title = "KRC20 indexer mainnet upgrade goes live - Cointelegraph",
            desc = "Ecosystem infrastructure upgrade completes across nodes.",
            url = "https://mirror.example.org/krc20-indexer-upgrade",
            category = "News",
            timestamp = "4m ago",
            epochMillis = 1_700_000_101_000L
        )
        val sameUrlDifferentHeadline = KaspaNewsItem(
            title = "Major Kaspa wallet upgrade announced today",
            desc = "Wallet protocol improvements",
            url = "https://example.com/krc20-upgrade",
            category = "News",
            timestamp = "3m ago",
            epochMillis = 1_700_000_102_000L
        )
        val generalArticle = KaspaNewsItem(
            title = "Analyst shares opinion on Kaspa market sentiment",
            desc = "Commentary on price trends.",
            url = "https://example.com/kaspa-market-sentiment",
            category = "News",
            timestamp = "2m ago",
            epochMillis = 1_700_000_103_000L
        )

        val secondRun = KaspaNewsNotificationWorker.selectFreshNotificationsAndMarkSeen(
            context,
            listOf(
                baselineItem,
                newEcosystemA,
                duplicateEcosystemA,
                sameUrlDifferentHeadline,
                generalArticle
            )
        )
        assertEquals(1, secondRun.size)
        assertEquals(newEcosystemA.title, secondRun.single().item.title)

        // Subsequent run with the same items emits nothing.
        val thirdRun = KaspaNewsNotificationWorker.selectFreshNotificationsAndMarkSeen(
            context,
            listOf(baselineItem, newEcosystemA, duplicateEcosystemA, sameUrlDifferentHeadline)
        )
        assertTrue(thirdRun.isEmpty())
    }

    @Test
    fun markItemsSeen_preventsForegroundFetchedItemsFromReNotifyingInBackground() {
        val preViewed = KaspaNewsItem(
            title = "Kasplex ecosystem integration launched",
            desc = "New ecosystem launch",
            url = "https://example.com/kasplex-launch",
            category = "News",
            timestamp = "Just now",
            epochMillis = 1_700_000_200_000L
        )
        KaspaNewsNotificationWorker.markItemsSeen(context, listOf(preViewed))

        val workerResults = KaspaNewsNotificationWorker.selectFreshNotificationsAndMarkSeen(
            context,
            listOf(preViewed)
        )
        assertTrue(workerResults.isEmpty())
    }

    @Test
    fun selectFreshNotificationsAndMarkSeen_capsAtTwoPerRunAndPrioritizesBreaking() {
        // Initialize baseline
        KaspaNewsNotificationWorker.selectFreshNotificationsAndMarkSeen(context, emptyList())

        val eco1 = KaspaNewsItem(
            title = "Kaspa wallet release v2.1 arrives",
            desc = "Wallet release",
            url = "https://example.com/eco1",
            category = "News",
            timestamp = "1m ago",
            epochMillis = 1_700_000_010_000L
        )
        val eco2 = KaspaNewsItem(
            title = "Kaspa exchange integration opens deposits",
            desc = "Exchange integration",
            url = "https://example.com/eco2",
            category = "News",
            timestamp = "2m ago",
            epochMillis = 1_700_000_020_000L
        )
        val breaking = KaspaNewsItem(
            title = "Breaking security alert for Kaspa node operators",
            desc = "Critical vulnerability notice",
            url = "https://example.com/breaking",
            category = "News",
            timestamp = "3m ago",
            epochMillis = 1_700_000_005_000L
        )

        val selected = KaspaNewsNotificationWorker.selectFreshNotificationsAndMarkSeen(
            context,
            listOf(eco1, eco2, breaking)
        )
        assertEquals(2, selected.size)
        assertEquals(KaspaNewsNotificationWorker.CATEGORY_BREAKING, selected[0].category)
        assertEquals(eco2.title, selected[1].item.title)

        // Remaining item from the capped batch was also marked seen and won't burst later.
        val followUp = KaspaNewsNotificationWorker.selectFreshNotificationsAndMarkSeen(
            context,
            listOf(eco1, eco2, breaking)
        )
        assertTrue(followUp.isEmpty())
    }

    @Test
    fun notificationIdForKey_avoidsLegacyAndMediaServiceCollisions() {
        val sampleKeys = listOf("title_a", "title_b", "url_https://example.com/x", "")
        for (key in sampleKeys) {
            val id = KaspaNewsNotificationWorker.notificationIdForKey(key)
            assertTrue("Notification ID $id must be >= 10000", id >= 10_000)
            assertTrue(id != KaspaNewsNotificationWorker.LEGACY_VIEWMODEL_NOTIFICATION_ID)
            assertTrue(id != 7301)
        }
    }
}
