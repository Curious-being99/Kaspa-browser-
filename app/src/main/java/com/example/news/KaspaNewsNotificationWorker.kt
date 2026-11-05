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

class KaspaNewsNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val articles = fetchLatestKaspaFeeds()
            if (articles.isEmpty()) return Result.success()

            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val seen = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty().toMutableSet()
            val initialized = prefs.getBoolean(KEY_INITIALIZED, false)
            val currentKeys = articles.map(::getKaspaNewsDeduplicationKey).toSet()

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
                    classify(article.title + " " + article.desc)
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

    private fun postArticleNotification(
        title: String,
        description: String,
        source: String,
        url: String,
        category: String
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
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n\nSource: $source"))
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
