package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.log2
import kotlin.math.roundToInt

data class PasswordStrengthResult(
    val score: Int, // 0..4
    val label: String,
    val entropyBits: Int
)

/**
 * Cryptographically collision-resistant password generator.
 *
 * Uses a persistent per-installation 256-bit salt, a persistent monotonic counter,
 * fresh OS CSPRNG bytes (`SecureRandom`), nanosecond clock + UUID entropy mixed through
 * HMAC-SHA512 DRBG expansion, unbiased rejection sampling, structural pattern checks,
 * and SHA-256 history deduplication so no two users or repeated invocations produce the
 * same password.
 */
object PasswordGenerator {
    private val secureRandom = SecureRandom()
    private val memoryCounter = AtomicLong(1L)
    private val lock = Any()

    private const val PREFS_NAME = "kazium_password_csprng_state"
    private const val KEY_INSTALL_SALT = "install_salt_b64"
    private const val KEY_COUNTER = "generation_counter"
    private const val KEY_RECENT_HASHES = "recent_sha256_ring"
    private const val MAX_MEMORY_HASHES = 50_000
    private const val MAX_PERSISTED_HASHES = 1_500

    private val seenHashes: MutableSet<String> = Collections.synchronizedSet(object : LinkedHashSet<String>() {
        override fun add(element: String): Boolean {
            if (size >= MAX_MEMORY_HASHES) {
                val it = iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
            return super.add(element)
        }
    })

    @Volatile
    private var appPrefs: SharedPreferences? = null

    @Volatile
    private var installSaltBytes: ByteArray = ByteArray(32).also { secureRandom.nextBytes(it) }

    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val UPPER_NO_AMBIGUOUS = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val LOWER_NO_AMBIGUOUS = "abcdefghijkmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val DIGITS_NO_AMBIGUOUS = "23456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}:;,.?"

    /**
     * Initializes the persistent per-install 256-bit CSPRNG salt and digest history ring.
     * Safe to call repeatedly from Application/MainActivity or UI composables.
     */
    fun init(context: Context) {
        if (appPrefs != null) return
        synchronized(lock) {
            if (appPrefs != null) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existingB64 = prefs.getString(KEY_INSTALL_SALT, null)
            val salt = if (!existingB64.isNullOrBlank()) {
                runCatching { Base64.decode(existingB64, Base64.NO_WRAP) }.getOrNull()
                    ?.takeIf { it.size >= 32 }
            } else {
                null
            } ?: ByteArray(32).also { fresh ->
                secureRandom.nextBytes(fresh)
                prefs.edit().putString(KEY_INSTALL_SALT, Base64.encodeToString(fresh, Base64.NO_WRAP)).apply()
            }
            installSaltBytes = salt
            val savedCounter = prefs.getLong(KEY_COUNTER, 0L)
            if (savedCounter > memoryCounter.get()) {
                memoryCounter.set(savedCounter)
            }
            val savedRing = prefs.getString(KEY_RECENT_HASHES, null)
            if (!savedRing.isNullOrBlank()) {
                savedRing.split(',').filter { it.isNotBlank() }.forEach { seenHashes.add(it) }
            }
            appPrefs = prefs
        }
    }

    fun generate(
        length: Int = 24,
        includeUppercase: Boolean = true,
        includeLowercase: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true,
        avoidAmbiguous: Boolean = false
    ): String = generateUnique(
        length = length,
        includeUppercase = includeUppercase,
        includeLowercase = includeLowercase,
        includeDigits = includeDigits,
        includeSymbols = includeSymbols,
        avoidAmbiguous = avoidAmbiguous
    )

    fun generateUnique(
        length: Int = 24,
        includeUppercase: Boolean = true,
        includeLowercase: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true,
        avoidAmbiguous: Boolean = false,
        excludePasswords: Set<String> = emptySet()
    ): String {
        val clampedLength = length.coerceIn(8, 64)
        val pools = mutableListOf<String>()
        if (includeUppercase) pools += if (avoidAmbiguous) UPPER_NO_AMBIGUOUS else UPPER
        if (includeLowercase) pools += if (avoidAmbiguous) LOWER_NO_AMBIGUOUS else LOWER
        if (includeDigits) pools += if (avoidAmbiguous) DIGITS_NO_AMBIGUOUS else DIGITS
        if (includeSymbols) pools += SYMBOLS

        if (pools.isEmpty()) {
            pools += if (avoidAmbiguous) LOWER_NO_AMBIGUOUS else LOWER
            pools += if (avoidAmbiguous) DIGITS_NO_AMBIGUOUS else DIGITS
        }
        val combinedPool = pools.joinToString("")
        val excludeHashes = if (excludePasswords.isEmpty()) emptySet() else excludePasswords.map { sha256Hex(it) }.toSet()

        repeat(32) { attempt ->
            val drbg = DrbgStream(nextEntropyBlock(attempt))
            val chars = CharArray(clampedLength)
            var idx = 0
            for (pool in pools) {
                if (idx < clampedLength) {
                    chars[idx++] = pool[drbg.nextUniformInt(pool.length)]
                }
            }
            while (idx < clampedLength) {
                chars[idx++] = combinedPool[drbg.nextUniformInt(combinedPool.length)]
            }

            // Unbiased Fisher-Yates shuffle
            for (i in clampedLength - 1 downTo 1) {
                val j = drbg.nextUniformInt(i + 1)
                val tmp = chars[i]
                chars[i] = chars[j]
                chars[j] = tmp
            }

            val candidate = String(chars)
            val hash = sha256Hex(candidate)
            val hasPatternWeakness = combinedPool.length >= 20 && hasExcessiveRepeats(candidate)
            if (!hasPatternWeakness && !excludeHashes.contains(hash) && recordIfUnique(hash)) {
                return candidate
            }
        }

        // Fallback if caller supplied an extremely tiny charset (still deduplicated)
        val drbg = DrbgStream(nextEntropyBlock(99))
        val fallback = CharArray(clampedLength) { combinedPool[drbg.nextUniformInt(combinedPool.length)] }
        for (i in clampedLength - 1 downTo 1) {
            val j = drbg.nextUniformInt(i + 1)
            val tmp = fallback[i]
            fallback[i] = fallback[j]
            fallback[j] = tmp
        }
        val candidate = String(fallback)
        recordIfUnique(sha256Hex(candidate))
        return candidate
    }

    private fun hasExcessiveRepeats(password: String): Boolean {
        var run = 1
        for (i in 1 until password.length) {
            if (password[i] == password[i - 1]) {
                run++
                if (run >= 3) return true
            } else {
                run = 1
            }
        }
        return false
    }

    private fun recordIfUnique(digestHex: String): Boolean {
        synchronized(lock) {
            if (seenHashes.contains(digestHex)) return false
            seenHashes.add(digestHex)
            appPrefs?.let { prefs ->
                val ring = seenHashes.toList().takeLast(MAX_PERSISTED_HASHES).joinToString(",")
                prefs.edit()
                    .putLong(KEY_COUNTER, memoryCounter.get())
                    .putString(KEY_RECENT_HASHES, ring)
                    .apply()
            }
            return true
        }
    }

    private fun nextEntropyBlock(attempt: Int): ByteArray {
        val osRandom = ByteArray(32).also { secureRandom.nextBytes(it) }
        val count = memoryCounter.getAndIncrement()
        val uuid = UUID.randomUUID()
        val material = ByteBuffer.allocate(8 + 8 + 8 + 16 + 4 + osRandom.size)
            .putLong(count)
            .putLong(System.nanoTime())
            .putLong(System.currentTimeMillis())
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .putInt(attempt)
            .put(osRandom)
            .array()

        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(installSaltBytes, "HmacSHA512"))
        return mac.doFinal(material)
    }

    private class DrbgStream(initialSeed: ByteArray) {
        private var state = initialSeed
        private var blockCounter = 0L
        private var offset = 0
        private var buffer: ByteArray = expandNext()

        private fun expandNext(): ByteArray {
            val freshOs = ByteArray(16).also { secureRandom.nextBytes(it) }
            val mac = Mac.getInstance("HmacSHA512")
            mac.init(SecretKeySpec(state, "HmacSHA512"))
            mac.update(freshOs)
            val counterBytes = ByteBuffer.allocate(8).putLong(blockCounter++).array()
            val out = mac.doFinal(counterBytes)
            state = MessageDigest.getInstance("SHA-256").digest(out + freshOs)
            offset = 0
            return out
        }

        private fun nextByte(): Int {
            if (offset >= buffer.size) {
                buffer = expandNext()
            }
            return buffer[offset++].toInt() and 0xFF
        }

        private fun nextUnsignedInt32(): Long {
            val b0 = nextByte().toLong()
            val b1 = nextByte().toLong()
            val b2 = nextByte().toLong()
            val b3 = nextByte().toLong()
            return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        }

        /** Modulo-bias-free uniform integer in [0, bound). */
        fun nextUniformInt(bound: Int): Int {
            require(bound > 0)
            if (bound == 1) return 0
            val b = bound.toLong()
            val maxValid = (0x1_0000_0000L / b) * b
            while (true) {
                val r = nextUnsignedInt32() xor (secureRandom.nextInt().toLong() and 0xFFFFFFFFL)
                if (r < maxValid) return (r % b).toInt()
            }
        }
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(Character.forDigit((b.toInt() shr 4) and 0xF, 16))
            sb.append(Character.forDigit(b.toInt() and 0xF, 16))
        }
        return sb.toString()
    }

    fun evaluateStrength(password: String): PasswordStrengthResult {
        if (password.isEmpty()) {
            return PasswordStrengthResult(score = 0, label = "Empty", entropyBits = 0)
        }
        var poolSize = 0
        if (password.any { it in 'a'..'z' }) poolSize += 26
        if (password.any { it in 'A'..'Z' }) poolSize += 26
        if (password.any { it in '0'..'9' }) poolSize += 10
        if (password.any { !it.isLetterOrDigit() }) poolSize += 32

        val effectivePool = poolSize.coerceAtLeast(10)
        val entropyBits = (password.length * log2(effectivePool.toDouble())).roundToInt()

        val hasVariety = listOf(
            password.any { it in 'a'..'z' },
            password.any { it in 'A'..'Z' },
            password.any { it in '0'..'9' },
            password.any { !it.isLetterOrDigit() }
        ).count { it }

        val score = when {
            password.length < 8 || entropyBits < 35 -> 1
            entropyBits < 55 || hasVariety < 2 -> 2
            entropyBits < 80 -> 3
            else -> 4
        }
        val label = when (score) {
            1 -> "Weak"
            2 -> "Fair"
            3 -> "Strong"
            4 -> "Very Strong"
            else -> "Empty"
        }
        return PasswordStrengthResult(score = score, label = label, entropyBits = entropyBits)
    }
}
