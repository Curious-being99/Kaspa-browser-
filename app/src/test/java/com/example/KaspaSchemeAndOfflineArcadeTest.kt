package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.TrafficAuditEntity
import com.example.model.VerificationStatus
import com.example.network.DomainConstants
import com.example.network.DualStackResolver
import com.example.network.KaspaPrivacyEngine
import com.example.network.KaspaWalletService
import com.example.network.SmartOmniboxEngine
import com.example.omnibar.SmartOmnibarEngine
import com.example.ui.BrickBreakerPhysics
import com.example.ui.Kaspa2048Engine
import com.example.ui.MoveDirection
import com.example.ui.OfflineArcadeScreen
import com.example.ui.RealTrafficAuditItemCard
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KaspaSchemeAndOfflineArcadeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var resolver: DualStackResolver

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        resolver = DualStackResolver(
            database = database,
            walletService = KaspaWalletService(OkHttpClient())
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun kaspaSchemeTyposNormalizeWithoutBreakingHostsOrPrices() {
        assertEquals("kaspa://", DomainConstants.normalizeKaspaScheme("Kaspa.//"))
        assertEquals("kaspa://", DomainConstants.normalizeKaspaScheme("  kaspa./  "))
        assertEquals("kaspa://offline-game", DomainConstants.normalizeKaspaScheme("Kaspa.//offline-game"))
        assertEquals("kaspa://portal", DomainConstants.normalizeKaspaScheme("kaspa;//portal"))
        assertEquals("kaspa://arcade", DomainConstants.normalizeKaspaScheme("kaspa: //arcade"))
        assertEquals("kas://alice.k", DomainConstants.normalizeKaspaScheme("kas.//alice.k"))
        assertEquals("kns://node", DomainConstants.normalizeKaspaScheme("kns.//node"))

        // Preserve regular domains, currency queries, and CashAddr
        assertEquals("kaspa.org", DomainConstants.normalizeKaspaScheme("kaspa.org"))
        assertEquals("kaspa.org/blocks", DomainConstants.normalizeKaspaScheme("kaspa.org/blocks"))
        assertEquals("kas/usd", DomainConstants.normalizeKaspaScheme("kas/usd"))
        assertEquals(
            "kaspa:qpzpfwcsqsxhxwup26r55fd0gh025r53f9d2u5x260z98k20h7s8x6s4z6f0c",
            DomainConstants.normalizeKaspaScheme("kaspa:qpzpfwcsqsxhxwup26r55fd0gh025r53f9d2u5x260z98k20h7s8x6s4z6f0c")
        )

        assertTrue(DomainConstants.isOfflineArcadeUrl("Kaspa.//offline-game"))
        assertTrue(DomainConstants.isOfflineArcadeUrl("kaspa://offline-game/"))
        assertTrue(DomainConstants.isOfflineArcadeUrl("kaspa://arcade"))
        assertTrue(DomainConstants.isOfflineArcadeUrl("kaspa-action://arcade"))
        assertFalse(DomainConstants.isOfflineArcadeUrl("kaspa.org"))
    }

    @Test
    fun omnibarPreviewsDoNotRewriteKaspaDotSlashIntoHttps() {
        val omniboxPreview = SmartOmniboxEngine.generateWebsitePreview("Kaspa.//")
        assertNotNull(omniboxPreview)
        assertEquals("kaspa://", omniboxPreview!!.fullUrl)
        assertTrue(omniboxPreview.isSecure)
        assertTrue(omniboxPreview.isKaspaNative)

        val omnibarPreview = SmartOmnibarEngine.getWebsitePreview("Kaspa.//")
        assertNotNull(omnibarPreview)
        assertEquals("kaspa://", omnibarPreview!!.fullUrl)
        assertTrue(omnibarPreview.isSecure)
        assertTrue(omnibarPreview.isDecentralized)

        val arcadePreview = SmartOmnibarEngine.getWebsitePreview("Kaspa.//offline-game")
        assertNotNull(arcadePreview)
        assertEquals("Kaspa Offline Arcade", arcadePreview!!.title)

        assertFalse(KaspaPrivacyEngine.isTrackerOrAd("kas://alice.k"))
        assertFalse(KaspaPrivacyEngine.isTrackerOrAd("kaspa-action://arcade"))
    }

    @Test
    fun resolvingKaspaSlashDotAndOfflineArcadeNeverTriggersTamperWarning() = runBlocking {
        val portalRes = resolver.resolve("Kaspa.//")
        assertEquals(VerificationStatus.VERIFIED_TAMPER_PROOF, portalRes.verificationStatus)
        assertEquals("kaspa://portal", portalRes.url)

        val arcadeRes = resolver.resolve("kaspa.//offline-game/")
        assertEquals(VerificationStatus.VERIFIED_TAMPER_PROOF, arcadeRes.verificationStatus)
        assertEquals("kaspa://offline-game", arcadeRes.url)

        val audits = database.trafficAuditDao().getRecentAudits().first()
        assertTrue(audits.size >= 2)
        assertTrue(
            "All resolved Kaspa scheme requests must be tamper-proof without false Tamper Warning",
            audits.all { it.isTamperProof }
        )
    }

    @Test
    fun trafficAuditCardShowsTamperWarningOnlyForGenuineHashMismatch() {
        val unverifiedStandard = TrafficAuditEntity(
            id = 1L,
            timestamp = 1_700_000_000_000L,
            url = "https://example.com",
            protocol = "CENTRALIZED_HTTP",
            routeTaken = "Direct High-Speed Web Stack",
            latencyMs = 12L,
            isTamperProof = false,
            verificationSummary = "Status: UNVERIFIED | Hash: abc123..."
        )
        val realTamper = TrafficAuditEntity(
            id = 2L,
            timestamp = 1_700_000_000_001L,
            url = "ipfs://tampered-cid",
            protocol = "DECENTRALIZED_P2P",
            routeTaken = "IPFS Gateway",
            latencyMs = 35L,
            isTamperProof = false,
            verificationSummary = "Status: TAMPERED_HASH_MISMATCH | Hash: deadbeef..."
        )

        composeRule.setContent {
            MyApplicationTheme {
                androidx.compose.foundation.layout.Column {
                    RealTrafficAuditItemCard(audit = unverifiedStandard)
                    RealTrafficAuditItemCard(audit = realTamper)
                }
            }
        }

        composeRule.onNodeWithText("Standard Route").assertIsDisplayed()
        composeRule.onNodeWithText("Tamper Warning").assertIsDisplayed()
    }

    @Test
    fun kaspa2048EngineMergesTilesSupportsUndoDetectsWinAndGameOver() {
        val engine = Kaspa2048Engine(Random(42))
        engine.loadGridForTest(
            intArrayOf(
                2, 2, 4, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0
            )
        )
        val moved = engine.move(MoveDirection.LEFT)
        assertTrue(moved)
        assertEquals(4, engine.grid[0])
        assertEquals(4, engine.grid[1])
        assertEquals(4, engine.score)
        assertTrue(engine.canUndo)

        assertTrue(engine.undo())
        assertEquals(2, engine.grid[0])
        assertEquals(2, engine.grid[1])
        assertEquals(0, engine.score)

        // Win threshold check
        engine.loadGridForTest(
            intArrayOf(
                1024, 1024, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0
            )
        )
        assertTrue(engine.move(MoveDirection.LEFT))
        assertTrue(engine.hasWon2048)

        // Deadlock detection check
        engine.loadGridForTest(
            intArrayOf(
                2, 4, 2, 4,
                4, 2, 4, 2,
                2, 4, 2, 4,
                4, 2, 4, 2
            )
        )
        assertTrue(engine.isGameOver)
        assertFalse(engine.hasAvailableMoves())
    }

    @Test
    fun brickBreakerPhysicsBouncesBallCleanlyOnBrickHit() {
        val ball = BrickBreakerPhysics.Ball(
            x = 100f,
            y = 112f,
            vx = 40f,
            vy = -120f,
            radius = 14f
        )
        val hit = BrickBreakerPhysics.resolveCircleRectCollision(
            ball = ball,
            left = 60f,
            top = 70f,
            right = 140f,
            bottom = 100f
        )
        assertTrue(hit)
        assertTrue("Vertical velocity must invert after bottom face impact", ball.vy > 0f)
        assertNotEquals(112f, ball.y)
    }

    @Test
    fun nativeOfflineArcadeScreenSwitchesBetweenAllThreeRealGames() {
        var backClicked = false
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MyApplicationTheme {
                OfflineArcadeScreen(onBackHome = { backClicked = true })
            }
        }
        composeRule.mainClock.advanceTimeBy(64L)

        composeRule.onNodeWithTag("offline_arcade_root").assertIsDisplayed()
        composeRule.onNodeWithTag("arcade_tab_dag_dash").assertIsDisplayed()
        composeRule.onNodeWithTag("arcade_tab_2048").assertIsDisplayed()
        composeRule.onNodeWithTag("arcade_tab_breaker").assertIsDisplayed()

        // Switch to 2048+ (no continuous frame loop)
        composeRule.onNodeWithTag("arcade_tab_2048").performClick()
        composeRule.mainClock.advanceTimeBy(64L)
        composeRule.onNodeWithTag("arcade_2048_board").assertIsDisplayed()

        // Switch to Prism Breaker
        composeRule.onNodeWithTag("arcade_tab_breaker").performClick()
        composeRule.mainClock.advanceTimeBy(64L)
        composeRule.onNodeWithText("Drag Paddle to Deflect & Break Bricks").assertIsDisplayed()

        // Back button returns cleanly
        composeRule.onNodeWithTag("arcade_back_home").performClick()
        composeRule.mainClock.advanceTimeBy(64L)
        assertTrue(backClicked)
    }
}
