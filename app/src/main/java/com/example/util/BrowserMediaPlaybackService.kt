package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class BrowserMediaPlaybackService : Service() {
    private var mediaSession: MediaSession? = null
    private var playing = false
    private var title = "Web media"
    private var pageUrl = ""

    override fun onCreate() {
        super.onCreate()
        createChannel()
        mediaSession = MediaSession(this, "KaspaBrowserWebMedia").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = BrowserBackgroundPlayback.control(true)
                override fun onPause() = BrowserBackgroundPlayback.control(false)
                override fun onStop() = BrowserBackgroundPlayback.control(false)
            })
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE -> {
                playing = intent.getBooleanExtra(EXTRA_PLAYING, false)
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Web media" }
                pageUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
                if (playing) {
                    updateSession()
                    startForeground(NOTIFICATION_ID, buildNotification())
                } else {
                    mediaSession?.setPlaybackState(
                        PlaybackState.Builder()
                            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)
                            .setState(PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f)
                            .build()
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_PLAY -> BrowserBackgroundPlayback.control(true)
            ACTION_PAUSE -> BrowserBackgroundPlayback.control(false)
        }
        return START_NOT_STICKY
    }

    private fun updateSession() {
        mediaSession?.apply {
            setMetadata(
                android.media.MediaMetadata.Builder()
                    .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
                    .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, runCatching {
                        android.net.Uri.parse(pageUrl).host.orEmpty()
                    }.getOrDefault(""))
                    .build()
            )
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)
                    .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                    .build()
            )
            isActive = true
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = PendingIntent.getService(
            this, 1, Intent(this, BrowserMediaPlaybackService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playIntent = PendingIntent.getService(
            this, 2, Intent(this, BrowserMediaPlaybackService::class.java).setAction(ACTION_PLAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(runCatching { android.net.Uri.parse(pageUrl).host }.getOrNull().orEmpty())
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (playing) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "Play", playIntent)
        }
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Browser media playback", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            isActive = false
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_UPDATE = "com.example.browser.media.UPDATE"
        const val ACTION_PLAY = "com.example.browser.media.PLAY"
        const val ACTION_PAUSE = "com.example.browser.media.PAUSE"
        const val EXTRA_PLAYING = "playing"
        const val EXTRA_TITLE = "title"
        const val EXTRA_URL = "url"
        private const val CHANNEL_ID = "browser_media_playback"
        private const val NOTIFICATION_ID = 7301
    }
}