package com.example.news

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.data.KaspaNewsItem
import com.example.data.fetchLatestKaspaFeeds
import com.example.data.getKaspaNewsDeduplicationKey
import com.example.network.EmbeddedRustSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale
import java.util.concurrent.TimeUnit

class KaspaNewsNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            syncChannels(applicationContext)
            if (!canPostNotifications()) return Result.success()

            val items = fetchLatestKaspaFeeds()
            if (items.isEmpty()) return Result.success()

            val toNotify = dedupMutex.withLock {
                selectFreshNotificationsAndMarkSeen(applicationContext, items)
            }

            for (entry in toNotify) {
                postEcosystemNotification(
                    item = entry.item,
                    dedupKey = entry.dedupKey,
                    category = entry.category
                )
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
    }

    private suspend fun postEcosystemNotification(
        item: KaspaNewsItem,
        dedupKey: String,
        category: String
    ) {
        if (!canPostNotifications()) return
        val parsedUri = runCatching { Uri.parse(item.url.trim()) }.getOrNull() ?: return
        val scheme = parsedUri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "https" && scheme != "http") return

        val channelId = when (category) {
            CATEGORY_BREAKING -> BREAKING_CHANNEL
            CATEGORY_ECOSYSTEM -> ECOSYSTEM_CHANNEL
            else -> return
        }
        syncChannels(applicationContext)

        val notificationId = notificationIdForKey(dedupKey)
        val openItemIntent = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = parsedUri
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            openItemIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val source = item.author.ifBlank { item.category }.ifBlank { "Kaspa Ecosystem" }
        val body = item.desc.ifBlank { "Open to view the latest Kaspa update." }
        val appIcon = runCatching {
            val drawable = applicationContext.packageManager.getApplicationIcon(applicationContext.applicationInfo)
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
            android.graphics.Bitmap.createBitmap(
                width.coerceAtMost(512),
                height.coerceAtMost(512),
                android.graphics.Bitmap.Config.ARGB_8888
            ).also { bitmap ->
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
            }
        }.getOrNull()

        val previewImage = item.imageUrl
            ?.takeIf { it.startsWith("https://", ignoreCase = true) }
            ?.let { target ->
                withContext(Dispatchers.IO) {
                    runCatching {
                        val client = okhttp3.OkHttpClient.Builder()
                            .connectTimeout(4, TimeUnit.SECONDS)
                            .readTimeout(4, TimeUnit.SECONDS)
                            .callTimeout(6, TimeUnit.SECONDS)
                            .build()
                        val request = okhttp3.Request.Builder()
                            .url(target)
                            .header("User-Agent", "KaspaBrowser/1.0")
                            .build()
                        client.newCall(request).execute().use { response ->
                            val responseBody = response.body ?: return@use null
                            if (!response.isSuccessful || responseBody.contentLength() > 4L * 1024L * 1024L) {
                                return@use null
                            }
                            val bytes = responseBody.bytes()
                            if (bytes.isEmpty() || bytes.size > 4 * 1024 * 1024) return@use null
                            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@use null
                            val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 1024)
                            android.graphics.BitmapFactory.decodeByteArray(
                                bytes,
                                0,
                                bytes.size,
                                android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                            )
                        }
                    }.getOrNull()
                }
            }

        val style = if (previewImage != null) {
            NotificationCompat.BigPictureStyle()
                .bigPicture(previewImage)
                .setBigContentTitle(item.title)
                .setSummaryText("Source: $source")
        } else {
            NotificationCompat.BigTextStyle().bigText("$body\n\nSource: $source")
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
            .setLargeIcon(appIcon)
            .setContentTitle(item.title)
            .setContentText(body)
            .setStyle(style)
            .setSubText(category)
            .setContentIntent(pendingIntent)
            .setPriority(
                if (category == CATEGORY_BREAKING) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setGroup(NOTIFICATION_GROUP)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
    }

    internal data class NotificationCandidate(
        val item: KaspaNewsItem,
        val dedupKey: String,
        val normalizedUrl: String,
        val category: String
    )

    companion object {
        private const val PREFS = "kaspa_news_notification_state"
        private const val KEY_SEEN_LEGACY_SET = "seen_article_keys"
        private const val KEY_SEEN_ORDERED = "seen_ordered_keys_json"
        private const val KEY_INITIALIZED = "initialized"
        private const val MAX_SEEN = 1500
        private const val MAX_NOTIFICATIONS_PER_RUN = 2

        const val LEGACY_ARTICLES_CHANNEL = "kaspa_news_articles"
        const val LEGACY_VIEWMODEL_CHANNEL = "kaspa_news_channel"
        const val LEGACY_VIEWMODEL_NOTIFICATION_ID = 1

        const val ECOSYSTEM_CHANNEL = "kaspa_ecosystem_news"
        const val BREAKING_CHANNEL = "kaspa_breaking_news"
        const val NOTIFICATION_GROUP = "kaspa_ecosystem_alerts"

        const val CATEGORY_ECOSYSTEM = "Kaspa Ecosystem"
        const val CATEGORY_BREAKING = "Breaking Kaspa News"

        private const val WORK_NAME = "kaspa_news_background_sync"
        private val dedupMutex = Mutex()
        private val prefsLock = Any()

        /**
         * Classifies feed items for notifications.
         * General Kaspa articles return null so "All Kaspa Articles" notifications are never sent.
         */
        internal fun classifyForNotification(item: KaspaNewsItem): String? {
            val value = "${item.title} ${item.desc}".lowercase(Locale.ROOT)
            val breakingTerms = listOf(
                "breaking",
                "urgent",
                "critical vulnerability",
                "mainnet outage",
                "consensus bug",
                "security alert",
                "zero-day",
                "exploit"
            )
            if (breakingTerms.any(value::contains)) {
                return CATEGORY_BREAKING
            }

            if (item.category.equals("GitHub", ignoreCase = true)) {
                return CATEGORY_ECOSYSTEM
            }

            val ecosystemTerms = listOf(
                "ecosystem",
                "integration",
                "launch",
                "release",
                "upgrade",
                "mainnet",
                "testnet",
                "wallet",
                "exchange",
                "protocol",
                "ghostdag",
                "krc20",
                "krc-20",
                "krc721",
                "kasplex",
                "kaspad",
                "rusty-kaspa",
                "hard fork"
            )
            if (ecosystemTerms.any(value::contains)) {
                return CATEGORY_ECOSYSTEM
            }

            return null
        }

        internal fun normalizeUrlKey(rawUrl: String): String {
            return EmbeddedRustSearchEngine.sanitizeUrl(rawUrl)
                .trim()
                .substringBefore('#')
                .trimEnd('/')
                .lowercase(Locale.ROOT)
        }

        internal fun notificationIdForKey(dedupKey: String): Int {
            // Keep IDs >= 10,000 so they never collide with foreground media service (7301) or legacy ID (1)
            return 10_000 + ((dedupKey.hashCode() and 0x7FFFFFFF) % 1_000_000)
        }

        /**
         * Creates active ecosystem/breaking channels and deletes deprecated article/duplicate channels.
         */
        fun syncChannels(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { manager.deleteNotificationChannel(LEGACY_ARTICLES_CHANNEL) }
                runCatching { manager.deleteNotificationChannel(LEGACY_VIEWMODEL_CHANNEL) }
                manager.createNotificationChannel(
                    NotificationChannel(
                        ECOSYSTEM_CHANNEL,
                        "Kaspa Ecosystem Developments",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
                manager.createNotificationChannel(
                    NotificationChannel(
                        BREAKING_CHANNEL,
                        "Breaking Kaspa News",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                )
            }
            runCatching { manager.cancel(LEGACY_VIEWMODEL_NOTIFICATION_ID) }
        }

        /**
         * Marks items fetched in the foreground UI as already seen so the periodic background
         * worker never posts duplicate notifications for items the user already viewed in-app.
         */
        fun markItemsSeen(context: Context, items: List<KaspaNewsItem>) {
            if (items.isEmpty()) return
            synchronized(prefsLock) {
                val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val ordered = loadOrderedSeenKeys(prefs)
                val seenSet = ordered.toHashSet()
                val newKeys = mutableListOf<String>()
                for (item in items) {
                    val dk = getKaspaNewsDeduplicationKey(item)
                    val uk = "u:${normalizeUrlKey(item.url)}"
                    if (seenSet.add(dk)) newKeys.add(dk)
                    if (uk != "u:" && seenSet.add(uk)) newKeys.add(uk)
                }
                if (newKeys.isNotEmpty() || !prefs.getBoolean(KEY_INITIALIZED, false)) {
                    persistOrderedSeenKeys(prefs, (ordered + newKeys).takeLast(MAX_SEEN))
                }
            }
        }

        internal fun selectFreshNotificationsAndMarkSeen(
            context: Context,
            items: List<KaspaNewsItem>
        ): List<NotificationCandidate> {
            synchronized(prefsLock) {
                val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val initialized = prefs.getBoolean(KEY_INITIALIZED, false)
                val ordered = loadOrderedSeenKeys(prefs)
                val seenSet = ordered.toHashSet()

                val allKeysToAppend = mutableListOf<String>()
                val batchDedupKeys = HashSet<String>()
                val batchUrlKeys = HashSet<String>()
                val candidates = mutableListOf<NotificationCandidate>()

                for (item in items) {
                    val dedupKey = getKaspaNewsDeduplicationKey(item)
                    val urlKey = "u:${normalizeUrlKey(item.url)}"

                    val alreadySeen = dedupKey in seenSet ||
                        (urlKey != "u:" && urlKey in seenSet) ||
                        dedupKey in batchDedupKeys ||
                        (urlKey != "u:" && urlKey in batchUrlKeys)

                    batchDedupKeys.add(dedupKey)
                    if (urlKey != "u:") batchUrlKeys.add(urlKey)

                    if (seenSet.add(dedupKey)) allKeysToAppend.add(dedupKey)
                    if (urlKey != "u:" && seenSet.add(urlKey)) allKeysToAppend.add(urlKey)

                    if (initialized && !alreadySeen) {
                        val category = classifyForNotification(item)
                        if (category != null) {
                            candidates.add(
                                NotificationCandidate(
                                    item = item,
                                    dedupKey = dedupKey,
                                    normalizedUrl = urlKey,
                                    category = category
                                )
                            )
                        }
                    }
                }

                persistOrderedSeenKeys(prefs, (ordered + allKeysToAppend).takeLast(MAX_SEEN))

                if (!initialized) return emptyList()
                return candidates
                    .sortedWith(
                        compareByDescending<NotificationCandidate> { it.category == CATEGORY_BREAKING }
                            .thenByDescending { it.item.epochMillis }
                    )
                    .take(MAX_NOTIFICATIONS_PER_RUN)
            }
        }

        private fun loadOrderedSeenKeys(prefs: android.content.SharedPreferences): List<String> {
            val json = prefs.getString(KEY_SEEN_ORDERED, null)
            if (!json.isNullOrBlank()) {
                val parsed = runCatching {
                    val arr = JSONArray(json)
                    List(arr.length()) { idx -> arr.optString(idx) }.filter { it.isNotBlank() }
                }.getOrNull()
                if (parsed != null) return parsed
            }
            return prefs.getStringSet(KEY_SEEN_LEGACY_SET, emptySet()).orEmpty()
                .filter { it.isNotBlank() }
                .toList()
        }

        private fun persistOrderedSeenKeys(
            prefs: android.content.SharedPreferences,
            orderedKeys: List<String>
        ) {
            val arr = JSONArray()
            orderedKeys.forEach { arr.put(it) }
            prefs.edit()
                .putString(KEY_SEEN_ORDERED, arr.toString())
                .remove(KEY_SEEN_LEGACY_SET)
                .putBoolean(KEY_INITIALIZED, true)
                .apply()
        }

        fun schedule(context: Context) {
            syncChannels(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<KaspaNewsNotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
