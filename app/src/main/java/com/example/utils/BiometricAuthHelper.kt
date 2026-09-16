package com.example.utils

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat

/**
 * Expert Biometric Authentication Helper.
 * Utilizes standard Android BiometricPrompt (via AndroidX) to interact directly with hardware 
 * fingerprint, face, or iris sensors, or fallback to device PIN/Pattern/Password.
 */
object BiometricAuthHelper {

    fun Context.findFragmentActivity(): FragmentActivity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is FragmentActivity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * Checks if any secure hardware biometric or device lock is configured on the device.
     */
    fun isDeviceLockAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val result = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or 
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Triggers the official Android Biometric / Hardware authentication prompt.
     * This is the real hardware-backed security flow.
     */
    fun authenticateWithBiometricOrDeviceLock(
        context: Context,
        title: String = "Unlock Kaspa Wallet",
        subtitle: String = "Authenticate using fingerprint, face, or device PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val activity = context.findFragmentActivity()
        if (activity == null) {
            onError("Hardware biometric prompt requires an active Activity context.")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                // If user cancels or if hardware isn't available, report the error
                onError(errString.toString())
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                // Hardware verification successful!
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Fingerprint/Face scanned but not recognized
                onError("Biometric verification failed. Please try again.")
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            // Note: If DEVICE_CREDENTIAL is included, setNegativeButtonText MUST NOT be called.
            .build()

        try {
            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError("Biometric engine error: ${e.message}")
        }
    }
}
