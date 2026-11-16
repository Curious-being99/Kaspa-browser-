package com.example.security

import android.net.Uri
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class OtpAuthType(val wireValue: String) {
    TOTP("totp"),
    HOTP("hotp")
}

enum class OtpHashAlgorithm(val jcaName: String, val uriName: String) {
    SHA1("HmacSHA1", "SHA1"),
    SHA256("HmacSHA256", "SHA256"),
    SHA512("HmacSHA512", "SHA512");

    companion object {
        fun fromString(raw: String?): OtpHashAlgorithm = when (raw?.trim()?.uppercase(Locale.ROOT)) {
            "SHA256", "HMACSHA256", "SHA-256" -> SHA256
            "SHA512", "HMACSHA512", "SHA-512" -> SHA512
            else -> SHA1
        }
    }
}

data class ParsedOtpAuthUri(
    val type: OtpAuthType,
    val issuer: String,
    val accountName: String,
    val base32Secret: String,
    val algorithm: OtpHashAlgorithm,
    val digits: Int,
    val periodSeconds: Int,
    val counter: Long
)

data class TotpCodeSnapshot(
    val code: String,
    val formattedCode: String,
    val nextCode: String,
    val formattedNextCode: String,
    val remainingSeconds: Int,
    val periodSeconds: Int,
    val progress: Float
)

/**
 * Standards-compliant RFC 6238 (TOTP), RFC 4226 (HOTP), and RFC 4648 (Base32)
 * two-factor authenticator generator.
 */
object TotpAuthenticator {
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val secureRandom = SecureRandom()

    fun normalizeBase32Secret(raw: String): String =
        raw.uppercase(Locale.ROOT)
            .replace(Regex("[\\s\\-_=\\u00A0]+"), "")
            .trim()

    fun isValidBase32Secret(raw: String): Boolean {
        val clean = normalizeBase32Secret(raw)
        if (clean.length < 8) return false
        return clean.all { it in BASE32_ALPHABET } && decodeBase32(clean).isNotEmpty()
    }

    fun decodeBase32(raw: String): ByteArray {
        val clean = normalizeBase32Secret(raw)
        if (clean.isEmpty()) return ByteArray(0)
        val out = ByteArray((clean.length * 5) / 8)
        var buffer = 0
        var bitsLeft = 0
        var index = 0
        for (c in clean) {
            val valIdx = BASE32_ALPHABET.indexOf(c)
            require(valIdx >= 0) { "Invalid Base32 character '$c' in authenticator secret" }
            buffer = (buffer shl 5) or valIdx
            bitsLeft += 5
            if (bitsLeft >= 8) {
                if (index < out.size) {
                    out[index++] = ((buffer shr (bitsLeft - 8)) and 0xFF).toByte()
                }
                bitsLeft -= 8
            }
        }
        return if (index == out.size) out else out.copyOf(index)
    }

    fun encodeBase32(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val idx = (buffer shr (bitsLeft - 5)) and 0x1F
                sb.append(BASE32_ALPHABET[idx])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            val idx = (buffer shl (5 - bitsLeft)) and 0x1F
            sb.append(BASE32_ALPHABET[idx])
        }
        return sb.toString()
    }

    /** Generates a fresh 160-bit cryptographically random Base32 TOTP secret. */
    fun generateSecret(numBytes: Int = 20): String {
        val bytes = ByteArray(numBytes.coerceIn(10, 64))
        secureRandom.nextBytes(bytes)
        return encodeBase32(bytes)
    }

    /** Computes RFC 4226 HOTP code for a specific 64-bit counter. */
    fun generateHotpCode(
        base32Secret: String,
        counter: Long,
        digits: Int = 6,
        algorithm: OtpHashAlgorithm = OtpHashAlgorithm.SHA1
    ): String {
        val keyBytes = decodeBase32(base32Secret)
        require(keyBytes.isNotEmpty()) { "Authenticator secret cannot be empty" }
        val safeDigits = digits.coerceIn(6, 8)
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()

        val mac = Mac.getInstance(algorithm.jcaName)
        mac.init(SecretKeySpec(keyBytes, algorithm.jcaName))
        val hmac = mac.doFinal(counterBytes)

        val offset = hmac.last().toInt() and 0x0F
        val binary = ((hmac[offset].toInt() and 0x7F) shl 24) or
            ((hmac[offset + 1].toInt() and 0xFF) shl 16) or
            ((hmac[offset + 2].toInt() and 0xFF) shl 8) or
            (hmac[offset + 3].toInt() and 0xFF)

        var mod = 1
        repeat(safeDigits) { mod *= 10 }
        val otp = binary % mod
        return otp.toString().padStart(safeDigits, '0')
    }

    /** Computes RFC 6238 TOTP code at [timeSeconds] (Unix epoch seconds). */
    fun generateTotpCode(
        base32Secret: String,
        timeSeconds: Long = System.currentTimeMillis() / 1000L,
        periodSeconds: Int = 30,
        digits: Int = 6,
        algorithm: OtpHashAlgorithm = OtpHashAlgorithm.SHA1
    ): String {
        val safePeriod = periodSeconds.coerceIn(10, 300)
        val timeStep = Math.floorDiv(timeSeconds, safePeriod.toLong())
        return generateHotpCode(
            base32Secret = base32Secret,
            counter = timeStep,
            digits = digits,
            algorithm = algorithm
        )
    }

    /** Returns live code, next code, remaining seconds, and normalized progress for UI rendering. */
    fun computeTotpSnapshot(
        base32Secret: String,
        nowMillis: Long = System.currentTimeMillis(),
        periodSeconds: Int = 30,
        digits: Int = 6,
        algorithm: OtpHashAlgorithm = OtpHashAlgorithm.SHA1
    ): TotpCodeSnapshot {
        val safePeriod = periodSeconds.coerceIn(10, 300)
        val epochSeconds = Math.floorDiv(nowMillis, 1000L)
        val step = Math.floorDiv(epochSeconds, safePeriod.toLong())
        val elapsedInStep = Math.floorMod(epochSeconds, safePeriod.toLong()).toInt()
        val remaining = (safePeriod - elapsedInStep).coerceIn(1, safePeriod)
        val elapsedExactMs = Math.floorMod(nowMillis, safePeriod * 1000L)
        val progress = (1f - (elapsedExactMs.toFloat() / (safePeriod * 1000f))).coerceIn(0f, 1f)

        val current = generateHotpCode(base32Secret, step, digits, algorithm)
        val next = generateHotpCode(base32Secret, step + 1L, digits, algorithm)
        return TotpCodeSnapshot(
            code = current,
            formattedCode = formatCodeForDisplay(current),
            nextCode = next,
            formattedNextCode = formatCodeForDisplay(next),
            remainingSeconds = remaining,
            periodSeconds = safePeriod,
            progress = progress
        )
    }

    fun formatCodeForDisplay(code: String): String = when (code.length) {
        6 -> "${code.substring(0, 3)} ${code.substring(3)}"
        8 -> "${code.substring(0, 4)} ${code.substring(4)}"
        else -> code
    }

    fun parseOtpAuthUri(rawUri: String): ParsedOtpAuthUri? {
        val trimmed = rawUri.trim()
        if (!trimmed.startsWith("otpauth://", ignoreCase = true)) return null
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val type = when (uri.host?.lowercase(Locale.ROOT)) {
            "hotp" -> OtpAuthType.HOTP
            "totp" -> OtpAuthType.TOTP
            else -> return null
        }

        val secretParam = uri.getQueryParameter("secret") ?: return null
        val cleanSecret = normalizeBase32Secret(secretParam)
        if (!isValidBase32Secret(cleanSecret)) return null

        val rawLabel = uri.path.orEmpty().removePrefix("/").trim()
        val labelIssuer = if (rawLabel.contains(':')) rawLabel.substringBefore(':').trim() else ""
        val account = if (rawLabel.contains(':')) rawLabel.substringAfter(':').trim() else rawLabel
        val issuer = uri.getQueryParameter("issuer")?.trim()?.ifBlank { labelIssuer } ?: labelIssuer

        val algorithm = OtpHashAlgorithm.fromString(uri.getQueryParameter("algorithm"))
        val digits = uri.getQueryParameter("digits")?.toIntOrNull()?.coerceIn(6, 8) ?: 6
        val period = uri.getQueryParameter("period")?.toIntOrNull()?.coerceIn(10, 300) ?: 30
        val counter = uri.getQueryParameter("counter")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L

        return ParsedOtpAuthUri(
            type = type,
            issuer = issuer.ifBlank { account.substringAfter('@', "Authenticator") },
            accountName = account.ifBlank { "Account" },
            base32Secret = cleanSecret,
            algorithm = algorithm,
            digits = digits,
            periodSeconds = period,
            counter = counter
        )
    }

    fun buildOtpAuthUri(
        type: OtpAuthType,
        issuer: String,
        accountName: String,
        base32Secret: String,
        algorithm: OtpHashAlgorithm = OtpHashAlgorithm.SHA1,
        digits: Int = 6,
        periodSeconds: Int = 30,
        counter: Long = 0L
    ): String {
        val cleanIssuer = issuer.trim().ifBlank { "Kazium" }
        val cleanAccount = accountName.trim().ifBlank { "user" }
        val label = "${Uri.encode(cleanIssuer)}:${Uri.encode(cleanAccount)}"
        val builder = Uri.Builder()
            .scheme("otpauth")
            .authority(type.wireValue)
            .appendEncodedPath(label)
            .appendQueryParameter("secret", normalizeBase32Secret(base32Secret))
            .appendQueryParameter("issuer", cleanIssuer)
            .appendQueryParameter("algorithm", algorithm.uriName)
            .appendQueryParameter("digits", digits.coerceIn(6, 8).toString())
        if (type == OtpAuthType.TOTP) {
            builder.appendQueryParameter("period", periodSeconds.coerceIn(10, 300).toString())
        } else {
            builder.appendQueryParameter("counter", counter.coerceAtLeast(0L).toString())
        }
        return builder.build().toString()
    }
}
