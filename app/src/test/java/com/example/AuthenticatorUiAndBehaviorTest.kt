package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.data.AppDatabase
import com.example.data.AuthenticatorSecretEntity
import com.example.network.CryptoUtils
import com.example.security.AuthenticatorAccount
import com.example.security.AuthenticatorRepository
import com.example.security.OtpAuthType
import com.example.security.OtpHashAlgorithm
import com.example.security.TotpAuthenticator
import com.example.ui.AddAuthenticatorDialog
import com.example.ui.AuthenticatorAccountCard
import com.example.ui.ScrollableThreeDotsMenu
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthenticatorUiAndBehaviorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addAuthenticatorDialogHasNameKeyAndTimeCounterSelectorWithoutUrlGoogleOrGenerate() {
        var savedTitle: String? = null
        var savedSecret: String? = null
        var savedType: OtpAuthType? = null

        composeRule.setContent {
            MyApplicationTheme {
                AddAuthenticatorDialog(
                    onDismiss = {},
                    onSaveManual = { title, secret, type ->
                        savedTitle = title
                        savedSecret = secret
                        savedType = type
                        null
                    }
                )
            }
        }
        composeRule.waitForIdle()

        // Required UI elements
        composeRule.onNodeWithText("Enter setup key").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_name_input").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_key_input").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_type_selector").assertIsDisplayed()
        composeRule.onNodeWithText("Time based").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_save_button").assertIsNotEnabled()

        // Must NOT contain URL input, Google text, or key generation button
        assertEquals(0, composeRule.onAllNodesWithText("otpauth://", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Google", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Generate", substring = true, ignoreCase = true).fetchSemanticsNodes().size)

        // Enter site name and real Base32 key with spaces, dashes, underscores, and lowercase
        composeRule.onNodeWithTag("auth_name_input").performTextInput("GitHub Work")
        composeRule.onNodeWithTag("auth_key_input").performTextInput("jbsw y3dp-ehpk_3pxp")
        composeRule.waitForIdle()

        // Live 2FA verified preview appears for valid Base32 secret
        composeRule.onNodeWithTag("auth_live_preview").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Verified 2FA Code:").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_save_button").assertIsEnabled()

        // Open Type of key selector and pick Counter based
        composeRule.onNodeWithTag("auth_type_selector").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth_type_time_based").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_type_counter_based").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_type_counter_based").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Counter based").assertIsDisplayed()

        // Switch back to Time based and save
        composeRule.onNodeWithTag("auth_type_selector").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth_type_time_based").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("auth_save_button").performClick()
        composeRule.waitForIdle()

        assertEquals("GitHub Work", savedTitle)
        assertEquals("JBSWY3DPEHPK3PXP", savedSecret)
        assertEquals(OtpAuthType.TOTP, savedType)
    }

    @Test
    fun addAuthenticatorDialogRejectsInvalidKeyAndAllowsCounterBasedSave() {
        var savedType: OtpAuthType? = null

        composeRule.setContent {
            MyApplicationTheme {
                AddAuthenticatorDialog(
                    onDismiss = {},
                    onSaveManual = { _, _, type ->
                        savedType = type
                        null
                    }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("auth_name_input").performTextInput("Bitwarden")
        composeRule.onNodeWithTag("auth_key_input").performTextInput("invalid-1890")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth_save_button").performClick()
        composeRule.waitForIdle()

        assertNull(savedType)
        composeRule.onNodeWithText("Invalid 2FA key", substring = true).performScrollTo().assertIsDisplayed()

        // Fix key using performTextReplacement and verify Counter based save works cleanly
        composeRule.onNodeWithTag("auth_key_input").performTextReplacement("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
        composeRule.onNodeWithTag("auth_type_selector").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth_type_counter_based").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth_save_button").performClick()
        composeRule.waitForIdle()

        assertEquals(OtpAuthType.HOTP, savedType)
    }

    @Test
    fun authenticatorCardsComputeRealTotpAndHotpCodes() {
        // RFC 6238 SHA1 secret ("12345678901234567890") -> at T=59s expected 287082 -> "287 082"
        val rfcSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val totpAccount = AuthenticatorAccount(
            id = "totp-1",
            issuer = "GitHub",
            accountName = "",
            encryptedSecret = "unused",
            type = OtpAuthType.TOTP,
            algorithm = OtpHashAlgorithm.SHA1,
            digits = 6,
            periodSeconds = 30
        )
        val hotpAccount = AuthenticatorAccount(
            id = "hotp-1",
            issuer = "Hardware Token",
            accountName = "",
            encryptedSecret = "unused",
            type = OtpAuthType.HOTP,
            algorithm = OtpHashAlgorithm.SHA1,
            digits = 6,
            counter = 0L
        )
        var hotpIncremented = false

        composeRule.setContent {
            MyApplicationTheme {
                androidx.compose.foundation.layout.Column {
                    AuthenticatorAccountCard(
                        account = totpAccount,
                        decryptedSecret = rfcSecret,
                        nowMillis = 59_000L,
                        onCopyCode = {},
                        onNextHotp = {},
                        onDelete = {}
                    )
                    AuthenticatorAccountCard(
                        account = hotpAccount,
                        decryptedSecret = rfcSecret,
                        nowMillis = 59_000L,
                        onCopyCode = {},
                        onNextHotp = { hotpIncremented = true },
                        onDelete = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("GitHub").assertIsDisplayed()
        composeRule.onNodeWithText("Time based · 30s").assertIsDisplayed()
        composeRule.onNodeWithText("287 082").assertIsDisplayed()

        composeRule.onNodeWithText("Hardware Token").assertIsDisplayed()
        composeRule.onNodeWithText("Counter based · #0").assertIsDisplayed()
        // RFC 4226 counter=0 is 755224 -> "755 224"
        composeRule.onNodeWithText("755 224").assertIsDisplayed()

        composeRule.onNodeWithText("Next Code").performClick()
        assertTrue(hotpIncremented)
    }

    @Test
    fun repositoryAndNormalizerHandleSingleNameAndFormattedKeys() {
        assertEquals(
            "JBSWY3DPEHPK3PXP",
            TotpAuthenticator.normalizeBase32Secret("  jbsw\u00A0y3dp-ehpk_3pxp=== ")
        )

        val context = RuntimeEnvironment.getApplication()
        val repo = AuthenticatorRepository(context, keyPhrase = "unit-test-key")
        repo.accounts.value.forEach { repo.deleteAccount(it.id) }

        val saved = repo.addManual(
            issuer = "Cloudflare",
            accountName = "",
            rawBase32Secret = "jbsw y3dp-ehpk_3pxp",
            type = OtpAuthType.TOTP
        )
        assertEquals("Cloudflare", saved.issuer)
        assertEquals("", saved.accountName)
        assertEquals(OtpAuthType.TOTP, saved.type)
        assertEquals("JBSWY3DPEHPK3PXP", repo.decryptSecret(saved))

        val reloaded = AuthenticatorRepository(context, keyPhrase = "unit-test-key")
        reloaded.reload()
        assertEquals(1, reloaded.accounts.value.size)
        assertEquals("", reloaded.accounts.value.first().accountName)
        reloaded.deleteAccount(saved.id)
    }

    @Test
    fun roomEncrypted2FaSchemaIsolatesSecretsBySiteAndSupportsTotpAndHotp() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.authenticatorSecretDao()
            val repo = AuthenticatorRepository(
                dao = dao,
                context = null,
                keyPhrase = "room-isolated-2fa-master-key-2026"
            )
            val sharedPlainSecret = "JBSWY3DPEHPK3PXP"

            val githubTotp = repo.addManualSuspend(
                issuer = "GitHub",
                accountName = "alice@github",
                rawBase32Secret = sharedPlainSecret,
                type = OtpAuthType.TOTP,
                siteUrl = "https://github.com/settings/two_factor_authentication"
            )
            val gitlabHotp = repo.addManualSuspend(
                issuer = "GitLab",
                accountName = "alice@gitlab",
                rawBase32Secret = sharedPlainSecret,
                type = OtpAuthType.HOTP,
                counter = 3L,
                siteUrl = "https://gitlab.com/-/profile/two_factor_auth"
            )

            assertEquals("github.com", githubTotp.siteHost)
            assertEquals("gitlab.com", gitlabHotp.siteHost)
            assertNotEquals(sharedPlainSecret, githubTotp.encryptedSecret)
            assertNotEquals(
                "Identical secrets across different sites must produce distinct encrypted ciphertexts",
                githubTotp.encryptedSecret,
                gitlabHotp.encryptedSecret
            )

            // Verify persisted Room rows are encrypted at rest
            val rawRows = dao.getAllSecretsList()
            assertEquals(2, rawRows.size)
            assertTrue(rawRows.none { it.encryptedSecret.contains(sharedPlainSecret) })

            // Verify strict per-site credential isolation
            val githubEntries = repo.getAccountsForSiteSuspend("https://github.com/login")
            assertEquals(1, githubEntries.size)
            assertEquals(githubTotp.id, githubEntries.first().id)

            assertTrue(repo.getAccountsForSiteSuspend("https://evilgithub.com").isEmpty())
            assertTrue(repo.getAccountsForSiteSuspend("https://gist.github.com").isEmpty())

            // Verify cryptographic site-isolation: moving GitHub ciphertext onto GitLab site fails decryption
            val tamperedCrossSite = githubTotp.copy(siteHost = "gitlab.com")
            assertNull(
                "Moving ciphertext to another site must fail GCM AAD authentication",
                repo.decryptSecret(tamperedCrossSite)
            )
            assertEquals(sharedPlainSecret, repo.decryptSecret(githubTotp))
            assertEquals(sharedPlainSecret, repo.decryptSecret(gitlabHotp))

            // Verify HOTP counter increment and reactive Flow updates
            val advanced = repo.incrementHotpCounterSuspend(gitlabHotp.id)
            assertEquals(4L, advanced?.counter)
            val flowSnapshot = repo.accountsFlow.first()
            assertEquals(4L, flowSnapshot.first { it.id == gitlabHotp.id }.counter)
            assertEquals(OtpAuthType.TOTP, flowSnapshot.first { it.id == githubTotp.id }.type)

            // Cross-check site-isolated CryptoUtils directly
            val directCipher = CryptoUtils.encryptSiteIsolatedAes256(
                plainText = "SECRETKEYDATA",
                siteScope = "bank.example",
                recordId = "rec-1",
                keyPhrase = "direct-phrase"
            )
            assertEquals(
                "SECRETKEYDATA",
                CryptoUtils.decryptSiteIsolatedAes256(
                    cipherText = directCipher,
                    siteScope = "bank.example",
                    recordId = "rec-1",
                    keyPhrase = "direct-phrase"
                )
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun migration15To16CreatesAuthenticatorSecretsTableAndIndicesCleanly() {
        val context = RuntimeEnvironment.getApplication()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        try {
            val sqlDb = helper.writableDatabase
            AppDatabase.MIGRATION_15_16.migrate(sqlDb)
            sqlDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='authenticator_secrets'").use { cursor ->
                assertTrue("authenticator_secrets table must exist after MIGRATION_15_16", cursor.moveToFirst())
            }
            sqlDb.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='authenticator_secrets'").use { cursor ->
                val indexNames = buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
                assertTrue(indexNames.contains("index_authenticator_secrets_siteHost"))
                assertTrue(indexNames.contains("index_authenticator_secrets_otpType"))
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun threeDotsMenuScrollsNormallyToBottomItems() {
        val labels = listOf(
            "New Tab", "Find in Page", "Dev Console", "Desktop site", "Share Page",
            "Add to device", "Reader Mode", "Translate Page", "Si Agent",
            "Customize Background", "Offline Arcade", "Kaspa Wallet", "Network (Mesh Radar)",
            "Passwords & Passkeys", "Passkey Diagnostics", "Settings & Traffic Audit",
            "Update Zone", "Library (History & Bookmarks)"
        )
        var lastItemClicked = false
        val menuScrollState = androidx.compose.foundation.ScrollState(0)

        composeRule.setContent {
            MyApplicationTheme {
                Box {
                    ScrollableThreeDotsMenu(
                        expanded = true,
                        onDismissRequest = {},
                        scrollState = menuScrollState,
                        modifier = Modifier.testTag("three_dots_menu_popup")
                    ) {
                        labels.forEach { title ->
                            DropdownMenuItem(
                                text = { Text(title) },
                                onClick = {
                                    if (title == "Library (History & Bookmarks)") {
                                        lastItemClicked = true
                                    }
                                },
                                modifier = Modifier.testTag("menu_item_${title.lowercase().replace(Regex("[^a-z0-9]+"), "_")}")
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("New Tab").assertIsDisplayed()
        assertTrue("Overflow menu must have scrollable overflow content", menuScrollState.maxValue > 0)

        // Verify Compose scroll action scrolls cleanly down to the bottom item and makes it clickable
        composeRule.onNodeWithText("Library (History & Bookmarks)")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertTrue("Menu scrollState must advance when scrolling to bottom item", menuScrollState.value > 0)
        assertTrue(lastItemClicked)

        // Verify touch swipe gesture also scrolls normally inside the menu viewport
        val scrolledBeforeSwipe = menuScrollState.value
        composeRule.onNodeWithTag("three_dots_menu_popup").performTouchInput {
            swipeDown(
                startY = height * 0.2f,
                endY = height * 0.85f,
                durationMillis = 250L
            )
        }
        composeRule.waitForIdle()
        assertTrue(
            "Touch swipe must scroll menu cleanly (before=$scrolledBeforeSwipe, after=${menuScrollState.value})",
            menuScrollState.value < scrolledBeforeSwipe
        )
    }
}
