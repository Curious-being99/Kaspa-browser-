package com.example

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.security.BrowserLockStore
import com.example.ui.BiometricUnlockView
import com.example.ui.buildBrowserUnlockPromptInfo
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BrowserLockAndBiometricUnlockTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun browserLockStoreIsIsolatedFromWalletAndPreservesSeedsOnForgotRecovery() {
        val context = RuntimeEnvironment.getApplication()

        // Simulate existing wallet security preferences that must NEVER be touched by browser lock operations
        val walletPrefs = context.getSharedPreferences("kaspa_wallet_security", Context.MODE_PRIVATE)
        walletPrefs.edit().putString("wallet_pwd_hash", "simulated_wallet_hash_preserved").commit()

        BrowserLockStore.enableBrowserLock(context, optionalPassword = "MyPrivacyPass99!")
        BrowserLockStore.reloadFromPrefs(context)

        assertTrue(BrowserLockStore.isBrowserLockEnabled.value)
        assertTrue(BrowserLockStore.hasCustomLockPassword.value)
        assertFalse("Cold reload with lock enabled must start locked", BrowserLockStore.isBrowserUnlocked.value)

        // Verify password verification
        assertFalse(BrowserLockStore.verifyLockPassword(context, "wrong_password"))
        assertTrue(BrowserLockStore.verifyLockPassword(context, "MyPrivacyPass99!"))

        // Browser unlock marks ONLY browser unlocked — never modifies wallet preferences
        BrowserLockStore.markBrowserUnlocked()
        assertTrue(BrowserLockStore.isBrowserUnlocked.value)
        assertEquals("simulated_wallet_hash_preserved", walletPrefs.getString("wallet_pwd_hash", null))

        // Backgrounding re-locks browser if enabled
        BrowserLockStore.lockBrowser()
        assertFalse(BrowserLockStore.isBrowserUnlocked.value)

        // Forgotten lock password recovery clears ONLY browser lock password, NEVER the wallet
        BrowserLockStore.clearCustomLockPasswordAfterDeviceUnlock(context)
        assertFalse(BrowserLockStore.hasCustomLockPassword.value)
        assertEquals("simulated_wallet_hash_preserved", walletPrefs.getString("wallet_pwd_hash", null))

        // Disabling lock cleans up
        BrowserLockStore.disableBrowserLock(context)
        assertFalse(BrowserLockStore.isBrowserLockEnabled.value)
        assertTrue(BrowserLockStore.isBrowserUnlocked.value)
        assertEquals("simulated_wallet_hash_preserved", walletPrefs.getString("wallet_pwd_hash", null))
    }

    @Test
    fun biometricUnlockPromptInfoAllowsDeviceCredentialWithoutNegativeButton() {
        val promptInfo = buildBrowserUnlockPromptInfo()
        val expectedFlags = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        assertEquals(expectedFlags, promptInfo.allowedAuthenticators)
        assertTrue(
            promptInfo.subtitle?.toString()?.contains("separate", ignoreCase = true) == true
        )
    }

    @Test
    fun biometricUnlockViewHandlesPasswordForgotDeviceFallbackAndBiometricTrigger() {
        var unlockedCount = 0
        var lastPromptRecoveryFlag: Boolean? = null
        var promptTriggerCount = 0

        composeRule.setContent {
            MyApplicationTheme {
                BiometricUnlockView(
                    hasCustomPassword = true,
                    onVerifyPassword = { it == "unlock-browser-secret" },
                    onUnlockSucceeded = { unlockedCount++ },
                    autoPromptBiometric = false,
                    onLaunchBiometricOverride = { isRecovery ->
                        promptTriggerCount++
                        lastPromptRecoveryFlag = isRecovery
                    }
                )
            }
        }

        // Separation notice must be visible
        composeRule.onNodeWithTag("biometric_unlock_separation_notice").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Unlock with Password").performScrollTo().assertIsDisplayed()

        // Wrong password shows error and does NOT unlock
        composeRule.onNodeWithTag("browser_lock_password_input").performScrollTo().performTextInput("bad-pass")
        composeRule.onNodeWithTag("browser_lock_password_submit").performScrollTo().performClick()
        assertEquals(0, unlockedCount)
        composeRule.onNodeWithTag("browser_lock_error_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Incorrect browser lock password.").assertIsDisplayed()

        // Forgot password triggers phone lock recovery mode (device credential)
        composeRule.onNodeWithTag("browser_lock_forgot_password_button").performScrollTo().performClick()
        assertEquals(1, promptTriggerCount)
        assertEquals(true, lastPromptRecoveryFlag)

        // Biometric button triggers standard biometric/PIN prompt (recovery = false)
        composeRule.onNodeWithTag("browser_lock_biometric_button").performScrollTo().performClick()
        assertEquals(2, promptTriggerCount)
        assertEquals(false, lastPromptRecoveryFlag)
    }

    @Test
    fun biometricUnlockViewUnlocksOnValidPasswordWithoutTouchingWallet() {
        var unlocked = false

        composeRule.setContent {
            MyApplicationTheme {
                BiometricUnlockView(
                    hasCustomPassword = true,
                    onVerifyPassword = { it == "kazium-privacy-key" },
                    onUnlockSucceeded = { unlocked = true },
                    autoPromptBiometric = false,
                    onLaunchBiometricOverride = {}
                )
            }
        }

        composeRule.onNodeWithTag("browser_lock_password_input").performTextInput("kazium-privacy-key")
        composeRule.onNodeWithTag("browser_lock_password_submit").performClick()
        assertTrue(unlocked)
    }
}
