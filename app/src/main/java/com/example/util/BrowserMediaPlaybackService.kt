package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.MainActivity
import com.example.R

class BrowserMediaPlaybackService : Service() {
    private var mediaSession: MediaSessionCompat? = null
    private var playing = false
    private var title = "Web media"
    private var artist = ""
    private var album = ""
    private var pageUrl = ""
    private var positionMs = 0L
    private var durationMs = 0L
    private var audioFocusRequest: Any? = null
    private var playbackWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        mediaSession = MediaSessionCompat(this, "KaspaBrowserWebMedia").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = BrowserBackgroundPlayback.control(true)
                override fun onPause() = BrowserBackgroundPlayback.control(false)
                override fun onSeekTo(pos: Long) = BrowserBackgroundPlayback.seekTo(pos)
                override fun onStop() {
                    BrowserBackgroundPlayback.control(false)
                    stopForegroundInternal(removeNotification = true)
                    stopSelf()
                }
            })
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                @Suppress("DEPRECATION")
                setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                )
            }
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE -> {
                playing = intent.getBooleanExtra(EXTRA_PLAYING, false)
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Web media" }
                artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty()
                album = intent.getStringExtra(EXTRA_ALBUM).orEmpty()
                pageUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
                positionMs = intent.getLongExtra(EXTRA_POSITION, 0L)
                durationMs = intent.getLongExtra(EXTRA_DURATION, 0L)
                updateSession()
                if (playing) {
                    requestAudioFocus()
                    acquirePlaybackWakeLock()
                    startForegroundCompat()
                } else {
                    releasePlaybackWakeLock()
                    stopForegroundInternal(removeNotification = false)
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, buildNotification())
                }
            }
            ACTION_PLAY -> {
                requestAudioFocus()
                BrowserBackgroundPlayback.control(true)
            }
            ACTION_PAUSE -> {
                BrowserBackgroundPlayback.control(false)
            }
            ACTION_STOP -> {
                releasePlaybackWakeLock()
                abandonAudioFocus()
                BrowserBackgroundPlayback.control(false)
                stopForegroundInternal(removeNotification = true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun acquirePlaybackWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock = playbackWakeLock ?: powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "KaspaBrowser:WebMediaPlayback"
            ).apply { setReferenceCounted(false) }.also { playbackWakeLock = it }
            if (!lock.isHeld) lock.acquire(12 * 60 * 60 * 1000L)
        } catch (e: Exception) {
            android.util.Log.w("BrowserMediaPlayback", "Could not acquire playback wake lock: " + e.message)
        }
    }

    private fun releasePlaybackWakeLock() {
        try {
            playbackWakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundInternal(removeNotification: Boolean) {
        if (removeNotification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
        }
    }

    private fun requestAudioFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> BrowserBackgroundPlayback.control(false)
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> BrowserBackgroundPlayback.control(false)
                    }
                }
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        BrowserBackgroundPlayback.control(false)
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (audioFocusRequest as? AudioFocusRequest)?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    private fun updateSession() {
        val host = runCatching { android.net.Uri.parse(pageUrl).host.orEmpty() }.getOrDefault("")
        val displayArtist = if (artist.isNotBlank()) artist else host
        val displayAlbum = if (album.isNotBlank()) album else host
        val appIcon = runCatching {
            android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        }.getOrNull()

        mediaSession?.apply {
            setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, displayArtist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, displayAlbum)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, displayArtist)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, host)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, if (durationMs > 0L) durationMs else -1L)
                    .apply {
                        if (appIcon != null) {
                            putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, appIcon)
                            putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, appIcon)
                        }
                    }
                    .build()
            )
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE or
                            PlaybackStateCompat.ACTION_SEEK_TO or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_STOP
                    )
                    .setState(
                        if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                        if (positionMs > 0L) positionMs else PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        if (playing) 1.0f else 0.0f,
                        android.os.SystemClock.elapsedRealtime()
                    )
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
        val stopIntent = PendingIntent.getService(
            this, 3, Intent(this, BrowserMediaPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val host = runCatching { android.net.Uri.parse(pageUrl).host }.getOrNull().orEmpty()
        val displaySub = if (artist.isNotBlank()) artist else host.ifBlank { "Kaspa Browser Background Playback" }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(displaySub)
            .setSubText(host)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
        if (playing) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "Play", playIntent)
        }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopIntent)
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Browser media playback", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Media playback status and controls"
                    setShowBadge(false)
                }
            )
        }
    }

    override fun onDestroy() {
        releasePlaybackWakeLock()
        abandonAudioFocus()
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
        const val ACTION_STOP = "com.example.browser.media.STOP"
        const val EXTRA_PLAYING = "playing"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_ALBUM = "album"
        const val EXTRA_URL = "url"
        const val EXTRA_POSITION = "position"
        const val EXTRA_DURATION = "duration"
        private const val CHANNEL_ID = "browser_media_playback"
        private const val NOTIFICATION_ID = 7301
    }
}