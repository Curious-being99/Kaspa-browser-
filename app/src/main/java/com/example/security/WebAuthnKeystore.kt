package com.example.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Base64

object WebAuthnKeystore {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"

    fun createCredentialKey(alias: String): ByteArray {
        require(alias.matches(Regex("kaspa-webauthn-[a-f0-9-]{16,80}")))
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
        } else {
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        generator.initialize(builder.build())
        return generator.generateKeyPair().public.encoded
    }

    suspend fun sign(
        activity: FragmentActivity,
        alias: String,
        payload: ByteArray,
        title: String,
        subtitle: String
    ): ByteArray = suspendCancellableCoroutine { continuation ->
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            val privateKey = keyStore.getKey(alias, null) as? java.security.PrivateKey
                ?: throw IllegalStateException("Passkey private key is unavailable")
            val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(privateKey) }
            val executor: Executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (!continuation.isActive) return
                    try {
                        val authenticatedSignature = result.cryptoObject?.signature
                            ?: throw IllegalStateException("Authenticated signing operation unavailable")
                        authenticatedSignature.update(payload)
                        continuation.resume(authenticatedSignature.sign())
                    } catch (error: Throwable) {
                        continuation.resumeWithException(error)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            SecurityException("Passkey verification failed: $errString")
                        )
                    }
                }

                override fun onAuthenticationFailed() = Unit
            })
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setConfirmationRequired(true)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setAllowedAuthenticators(
                            BiometricPrompt.Authenticators.BIOMETRIC_STRONG or
                                BiometricPrompt.Authenticators.DEVICE_CREDENTIAL
                        )
                    } else {
                        setNegativeButtonText("Cancel")
                    }
                }
                .build()
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
            prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(signature))
        } catch (error: Throwable) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }

    fun deleteCredentialKey(alias: String) {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        keyStore.deleteEntry(alias)
    }

    fun encodePublicKey(publicKey: ByteArray): String =
        android.util.Base64.encodeToString(publicKey, android.util.Base64.NO_WRAP)

    fun decodePublicKey(encoded: String): ByteArray =
        android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
}
