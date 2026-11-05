package com.example.news

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.example.data.fetchLatestKaspaFeeds
import com.example.data.getKaspaNewsDeduplicationKey
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KaspaNewsNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            if (!canPostNotifications()) return Result.success()
            val articles = fetchLatestKaspaFeeds()
            if (articles.isEmpty()) return Result.success()

            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val seen = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty().toMutableSet()
            val initialized = prefs.getBoolean(KEY_INITIALIZED, false)
            val currentKeys = articles.map(::getKaspaNewsDeduplicationKey).distinct()

            if (!initialized) {
                prefs.edit()
                    .putStringSet(KEY_SEEN, currentKeys.takeLast(MAX_SEEN).toSet())
                    .putBoolean(KEY_INITIALIZED, true)
                    .apply()
                return Result.success()
            }

            val fresh = articles
                .filter { getKaspaNewsDeduplicationKey(it) !in seen }
                .sortedBy { it.epochMillis }

            fresh.forEach { article ->
                postArticleNotification(
                    article.title,
                    article.desc,
                    article.author.ifBlank { article.category },
                    article.url,
                    classify(article.title + " " + article.desc),
                    article.imageUrl
                )
            }

            prefs.edit()
                .putStringSet(KEY_SEEN, (seen + currentKeys).toList().takeLast(MAX_SEEN).toSet())
                .apply()

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
    }

    private fun classify(text: String): String {
        val value = text.lowercase()
        val breakingTerms = listOf("breaking", "urgent", "critical vulnerability", "mainnet outage", "consensus bug")
        val ecosystemTerms = listOf("ecosystem", "integration", "launch", "release", "upgrade", "mainnet", "wallet", "exchange", "protocol")
        return when {
            breakingTerms.any(value::contains) -> "Breaking Kaspa News"
            ecosystemTerms.any(value::contains) -> "Kaspa Ecosystem"
            else -> "Kaspa News"
        }
    }

    private suspend fun postArticleNotification(
        title: String,
        description: String,
        source: String,
        url: String,
        category: String,
        imageUrl: String?
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return

        val channelId = when (category) {
            "Breaking Kaspa News" -> BREAKING_CHANNEL
            "Kaspa Ecosystem" -> ECOSYSTEM_CHANNEL
            else -> NEWS_CHANNEL
        }
        createChannels()

        val openArticle = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(url)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val requestCode = url.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            requestCode,
            openArticle,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = description.ifBlank { "Open to read the full Kaspa update." }
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
        val articleImage = imageUrl
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
                            if (!response.isSuccessful || responseBody.contentLength() > 4L * 1024L * 1024L) return@use null
                            val bytes = responseBody.bytes()
                            if (bytes.isEmpty() || bytes.size > 4 * 1024 * 1024) return@use null
                            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@use null
                            val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 1024)
                            android.graphics.BitmapFactory.decodeByteArray(
                                bytes, 0, bytes.size,
                                android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                            )
                        }
                    }.getOrNull()
                }
            }
        val style = if (articleImage != null) {
            NotificationCompat.BigPictureStyle()
                .bigPicture(articleImage)
                .setBigContentTitle(title)
                .setSummaryText("Source: $source")
        } else {
            NotificationCompat.BigTextStyle().bigText("$body\n\nSource: $source")
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
            .setLargeIcon(appIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(style)
            .setSubText(category)
            .setContentIntent(pendingIntent)
            .setPriority(if (category == "Breaking Kaspa News") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup("kaspa_news_articles")
            .build()

        NotificationManagerCompat.from(applicationContext).notify(requestCode, notification)
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(NEWS_CHANNEL, "All Kaspa Articles", NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel(ECOSYSTEM_CHANNEL, "Kaspa Ecosystem Developments", NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel(BREAKING_CHANNEL, "Breaking Kaspa News", NotificationManager.IMPORTANCE_HIGH))
    }

    companion object {
        private const val PREFS = "kaspa_news_notification_state"
        private const val KEY_SEEN = "seen_article_keys"
        private const val KEY_INITIALIZED = "initialized"
        private const val MAX_SEEN = 1000
        private const val NEWS_CHANNEL = "kaspa_news_articles"
        private const val ECOSYSTEM_CHANNEL = "kaspa_ecosystem_news"
        private const val BREAKING_CHANNEL = "kaspa_breaking_news"
        private const val WORK_NAME = "kaspa_news_background_sync"

        fun schedule(context: Context) {
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
