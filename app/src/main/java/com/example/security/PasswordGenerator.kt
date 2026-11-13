package com.example.security

import java.security.SecureRandom
import kotlin.math.log2
import kotlin.math.roundToInt

data class PasswordStrengthResult(
    val score: Int, // 0..4
    val label: String,
    val entropyBits: Int
)

object PasswordGenerator {
    private val secureRandom = SecureRandom()

    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val UPPER_NO_AMBIGUOUS = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val LOWER_NO_AMBIGUOUS = "abcdefghijkmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val DIGITS_NO_AMBIGUOUS = "23456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}:;,.?"

    fun generate(
        length: Int = 20,
        includeUppercase: Boolean = true,
        includeLowercase: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true,
        avoidAmbiguous: Boolean = false
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

        val chars = CharArray(clampedLength)
        var idx = 0
        for (pool in pools) {
            if (idx < clampedLength) {
                chars[idx++] = pool[secureRandom.nextInt(pool.length)]
            }
        }
        val combinedPool = pools.joinToString("")
        while (idx < clampedLength) {
            chars[idx++] = combinedPool[secureRandom.nextInt(combinedPool.length)]
        }

        // Fisher-Yates shuffle using SecureRandom
        for (i in clampedLength - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }

        return String(chars)
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
