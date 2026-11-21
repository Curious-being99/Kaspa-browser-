package com.example.engine

import android.net.Uri
import com.example.network.KaspaPrivacyEngine
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Capability-aware native accelerator bridge for Kazview.
 *
 * Prioritizes compiled C++17 (`native_mmap_cache`) and Rust (`kaspasearch`) JNI libraries
 * when bundled, and transparently falls back to deterministic Kotlin/POSIX execution in
 * NDK-free builds so the browser never crashes with UnsatisfiedLinkError.
 */
object KazviewNativeCore {
    private const val MAX_FALLBACK_ORIGINS = 64

    @Volatile
    var isCppCoreLoaded: Boolean = false
        private set

    @Volatile
    var isRustCoreLoaded: Boolean = false
        private set

    private val fallbackWarmupMap = ConcurrentHashMap<String, Long>()

    init {
        isCppCoreLoaded = try {
            System.loadLibrary("native_mmap_cache")
            true
        } catch (_: Throwable) {
            false
        }
        isRustCoreLoaded = try {
            System.loadLibrary("kaspasearch")
            true
        } catch (_: Throwable) {
            false
        }
    }

    @JvmStatic
    private external fun nativeIsSpeculativeEligible(
        url: String,
        privacyBlocked: Boolean,
        lowRamDevice: Boolean
    ): Boolean

    @JvmStatic
    private external fun nativeRecordOriginWarmup(
        origin: String,
        nowMs: Long,
        cooldownMs: Long
    ): Boolean

    @JvmStatic
    private external fun nativeResetWarmupCache()

    @JvmStatic
    private external fun rustEvalSpeculativePreload(
        url: String,
        privacyRelayOrIncognito: Boolean,
        lowRamDevice: Boolean
    ): Int

    fun isSpeculativeEligible(
        rawUrl: String?,
        privacyBlocked: Boolean,
        lowRamDevice: Boolean
    ): Boolean {
        if (privacyBlocked || lowRamDevice || rawUrl.isNullOrBlank()) return false
        val trimmed = rawUrl.trim()

        if (isRustCoreLoaded) {
            val rustResult = runCatching {
                rustEvalSpeculativePreload(trimmed, false, false) == 1
            }.getOrNull()
            if (rustResult != null) return rustResult
        }

        if (isCppCoreLoaded) {
            val cppResult = runCatching {
                nativeIsSpeculativeEligible(trimmed, false, false)
            }.getOrNull()
            if (cppResult != null) return cppResult
        }

        return evaluateEligibilityKotlin(trimmed)
    }

    fun tryAcquireOriginWarmupSlot(
        origin: String,
        nowMs: Long = System.currentTimeMillis(),
        cooldownMs: Long = 60_000L
    ): Boolean {
        if (origin.isBlank()) return false
        if (isCppCoreLoaded) {
            val cppResult = runCatching {
                nativeRecordOriginWarmup(origin, nowMs, cooldownMs)
            }.getOrNull()
            if (cppResult != null) return cppResult
        }

        val normalized = origin.lowercase(Locale.ROOT)
        val last = fallbackWarmupMap[normalized]
        if (last != null && (nowMs - last) < cooldownMs) {
            return false
        }
        fallbackWarmupMap[normalized] = nowMs
        if (fallbackWarmupMap.size > MAX_FALLBACK_ORIGINS) {
            val oldest = fallbackWarmupMap.entries.minByOrNull { it.value }?.key
            if (oldest != null) fallbackWarmupMap.remove(oldest)
        }
        return true
    }

    fun resetWarmupCache() {
        if (isCppCoreLoaded) {
            runCatching { nativeResetWarmupCache() }
        }
        fallbackWarmupMap.clear()
    }

    fun modeLabel(): String = when {
        isRustCoreLoaded && isCppCoreLoaded -> "Rust + C++17 Native"
        isCppCoreLoaded -> "C++17 mmap + Kotlin"
        isRustCoreLoaded -> "Rust + POSIX Fallback"
        else -> "Kotlin + POSIX Fallback"
    }

    internal fun evaluateEligibilityKotlin(trimmedUrl: String): Boolean {
        if (!trimmedUrl.startsWith("https://", ignoreCase = true)) return false
        val parsed = runCatching { Uri.parse(trimmedUrl) }.getOrNull() ?: return false
        val host = parsed.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() && it.contains('.') }
            ?: return false
        if (host == "localhost" || host == "127.0.0.1") return false
        if (KaspaPrivacyEngine.isEssentialAuthOrCaptchaUrl(trimmedUrl) ||
            KaspaPrivacyEngine.isGoogleAccountOrAuthUrl(trimmedUrl) ||
            KaspaPrivacyEngine.isGoogleAccountDomain(host) ||
            KaspaPrivacyEngine.isTrackerOrAd(trimmedUrl)
        ) {
            return false
        }
        val lower = trimmedUrl.lowercase(Locale.ROOT)
        if (lower.contains("/login") ||
            lower.contains("/signin") ||
            lower.contains("/oauth") ||
            lower.contains("/webauthn") ||
            lower.contains("/passkey")
        ) {
            return false
        }
        return true
    }
}
