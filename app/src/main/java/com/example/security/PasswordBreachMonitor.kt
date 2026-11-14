package com.example.security

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.VaultCredentialEntity
import com.example.network.CronetClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

data class BreachedCredentialNotice(
    val credentialId: String,
    val siteName: String,
    val host: String,
    val username: String,
    val breachCount: Int
)

data class BreachAuditSummary(
    val checkedCount: Int,
    val breachedMap: Map<String, Int>,
    val newlyNotifiedCount: Int,
    val networkErrors: Int
)

/**
 * Privacy-preserving Password Breach Monitor using Have I Been Pwned k-Anonymity Range API.
 * Only the first 5 hex characters of the password's SHA-1 digest ever leave the device,
 * with Add-Padding enabled to prevent response-size fingerprinting.
 */
object PasswordBreachMonitor {
    private const val TAG = "PasswordBreachMonitor"
    private const val HIBP_RANGE_BASE = "https://api.pwnedpasswords.com/range/"
    const val CHANNEL_ID = "kaspa_password_alerts"
    const val EXTRA_OPEN_PASSWORD_VAULT = "OPEN_PASSWORD_VAULT"
    private const val NOTIFICATION_ID = 74082

    private val httpClient: OkHttpClient by lazy {
        CronetClientFactory.buildClient(
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
        )
    }

    fun sha1HexUppercase(password: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(password.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format(Locale.ROOT, "%02X", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    /**
     * Parses HIBP range response lines (`SUFFIX35:COUNT`) and returns occurrence count for [sha1Suffix35].
     * Skips zero-count padded dummy entries automatically.
     */
    fun parseRangeMatchCount(rangeResponseBody: String, sha1Suffix35: String): Int {
        val target = sha1Suffix35.trim().uppercase(Locale.ROOT)
        if (target.length != 35) return 0
        val lines = rangeResponseBody.lineSequence()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val colonIdx = line.indexOf(':')
            if (colonIdx <= 0) continue
            val suffix = line.substring(0, colonIdx).trim().uppercase(Locale.ROOT)
            if (suffix == target) {
                val count = line.substring(colonIdx + 1).trim().toIntOrNull() ?: 0
                if (count > 0) return count
            }
        }
        return 0
    }

    fun queryPrefixRange(prefix5: String): String? {
        val clean = prefix5.trim().uppercase(Locale.ROOT)
        if (!clean.matches(Regex("^[0-9A-F]{5}$"))) return null
        return try {
            val request = Request.Builder()
                .url("$HIBP_RANGE_BASE$clean")
                .header("Add-Padding", "true")
                .header("User-Agent", "KaspaBrowser-Android/1.0")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HIBP prefix query HTTP ${response.code}")
                    null
                } else {
                    response.body?.string()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "HIBP prefix query failed: ${t.message}")
            null
        }
    }

    fun checkPasswordBreachCount(
        password: String,
        prefixFetcher: (String) -> String? = ::queryPrefixRange
    ): Int? {
        if (password.isEmpty()) return 0
        val sha1 = sha1HexUppercase(password)
        val prefix = sha1.substring(0, 5)
        val suffix = sha1.substring(5)
        val body = prefixFetcher(prefix) ?: return null
        return parseRangeMatchCount(body, suffix)
    }

    suspend fun auditVaultCredentials(
        context: Context,
        credentials: List<VaultCredentialEntity>,
        repository: PasswordVaultRepository,
        notifyOnNewBreach: Boolean = true,
        keyPhrase: String = "",
        prefixFetcher: (String) -> String? = ::queryPrefixRange
    ): BreachAuditSummary = withContext(Dispatchers.IO) {
        CredentialSettingsStore.init(context)
        if (!CredentialSettingsStore.passwordAlertsEnabled.value) {
            return@withContext BreachAuditSummary(
                checkedCount = 0,
                breachedMap = CredentialSettingsStore.breachedCredentials.value,
                newlyNotifiedCount = 0,
                networkErrors = 0
            )
        }

        CredentialSettingsStore.setCheckingBreaches(true)
        try {
            val existingBreaches = CredentialSettingsStore.breachedCredentials.value
            val updatedBreaches = linkedMapOf<String, Int>()
            val prefixBodyCache = hashMapOf<String, String?>()
            val candidateSignatures = mutableListOf<Pair<String, String>>()
            val breachedNoticeById = linkedMapOf<String, BreachedCredentialNotice>()
            var checkedCount = 0
            var networkErrors = 0

            val passwordCreds = credentials.filter { it.hasPassword }
            for (cred in passwordCreds) {
                val rawPassword = repository.decryptPassword(cred, keyPhrase = keyPhrase) ?: continue
                if (rawPassword.isEmpty()) continue
                checkedCount++

                val sha1 = sha1HexUppercase(rawPassword)
                val prefix = sha1.substring(0, 5)
                val suffix = sha1.substring(5)

                val rangeBody = if (prefixBodyCache.containsKey(prefix)) {
                    prefixBodyCache[prefix]
                } else {
                    val fetched = prefixFetcher(prefix)
                    if (fetched != null) {
                        prefixBodyCache[prefix] = fetched
                    }
                    fetched
                }

                if (rangeBody == null) {
                    networkErrors++
                    existingBreaches[cred.id]?.let { prevCount ->
                        if (prevCount > 0) updatedBreaches[cred.id] = prevCount
                    }
                    continue
                }

                val matchCount = parseRangeMatchCount(rangeBody, suffix)
                if (matchCount > 0) {
                    updatedBreaches[cred.id] = matchCount
                    // Store only truncated hash prefix + count for dedup so plaintext never touches alert prefs
                    val signature = "${prefix}_${suffix.take(6)}:$matchCount"
                    candidateSignatures.add(cred.id to signature)
                    breachedNoticeById[cred.id] = BreachedCredentialNotice(
                        credentialId = cred.id,
                        siteName = cred.siteName.ifBlank { cred.host },
                        host = cred.host,
                        username = cred.username,
                        breachCount = matchCount
                    )
                }
            }

            CredentialSettingsStore.updateBreachResults(
                context = context,
                breachedMap = updatedBreaches,
                checkedAtMs = System.currentTimeMillis()
            )

            val newlyUnnotifiedIds = if (notifyOnNewBreach && candidateSignatures.isNotEmpty()) {
                CredentialSettingsStore.markAndFilterUnnotifiedBreaches(context, candidateSignatures)
            } else {
                emptySet()
            }

            val toNotify = newlyUnnotifiedIds.mapNotNull { breachedNoticeById[it] }
            if (notifyOnNewBreach && toNotify.isNotEmpty()) {
                postPasswordAlertNotification(context, toNotify)
            }

            BreachAuditSummary(
                checkedCount = checkedCount,
                breachedMap = updatedBreaches,
                newlyNotifiedCount = toNotify.size,
                networkErrors = networkErrors
            )
        } finally {
            CredentialSettingsStore.setCheckingBreaches(false)
        }
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Password Security Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when saved passwords appear in known online data breaches."
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun postPasswordAlertNotification(
        context: Context,
        breached: List<BreachedCredentialNotice>
    ): Boolean {
        if (breached.isEmpty()) return false
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted; password breach tracked in-app")
                return false
            }
        }

        ensureNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_OPEN_PASSWORD_VAULT, true)
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, launchIntent, pendingFlags)

        val first = breached.first()
        val title = if (breached.size == 1) {
            "Password alert for ${first.siteName}"
        } else {
            "Password alert: ${breached.size} saved passwords found online"
        }
        val sitesPreview = breached.take(3).joinToString(", ") { it.host }
        val extraSuffix = if (breached.size > 3) " +${breached.size - 3} more" else ""
        val body = if (breached.size == 1) {
            val accountLabel = first.username.ifBlank { first.host }
            "Your saved password for $accountLabel appeared in ${first.breachCount} online breach exposure(s). Change it now in Vault."
        } else {
            "Saved passwords for $sitesPreview$extraSuffix were found in online data breaches. Open Vault to update them."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to post password alert notification: ${t.message}")
            false
        }
    }
}
