package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

object BrowserBackgroundPlayback {
    private const val POLL_INTERVAL_MS = 1000L
    private val handler = Handler(Looper.getMainLooper())
    private var webViewRef: WeakReference<WebView>? = null
    private var appContext: Context? = null
    private var enabled = false
    private var hasObservedPlayback = false
    private var lastPlaying = false
    private var lastTitle = ""
    private var lastUrl = ""

    private val poll = object : Runnable {
        override fun run() {
            if (!enabled) return
            val webView = webViewRef?.get()
            if (webView == null) {
                handler.postDelayed(this, POLL_INTERVAL_MS)
                return
            }

            runCatching {
                webView.evaluateJavascript(MEDIA_STATE_SCRIPT) { raw ->
                    if (!enabled || webViewRef?.get() !== webView) return@evaluateJavascript
                    runCatching {
                        val decoded = JSONArray("[$raw]").optString(0)
                        val state = JSONObject(decoded)
                        val playing = state.optBoolean("playing", false)
                        val title = state.optString("title").take(180)
                        val url = state.optString("url").take(2048)
                        val normalizedTitle = title.ifBlank { url }
                        val changed = playing != lastPlaying ||
                            normalizedTitle != lastTitle ||
                            url != lastUrl
                        val wasPlaying = lastPlaying

                        lastPlaying = playing
                        lastTitle = normalizedTitle
                        lastUrl = url
                        if (playing) hasObservedPlayback = true

                        if (changed && (hasObservedPlayback || playing || wasPlaying)) {
                            val context = appContext ?: return@runCatching
                            val intent = Intent(context, BrowserMediaPlaybackService::class.java)
                                .setAction(BrowserMediaPlaybackService.ACTION_UPDATE)
                                .putExtra(BrowserMediaPlaybackService.EXTRA_PLAYING, playing)
                                .putExtra(BrowserMediaPlaybackService.EXTRA_TITLE, lastTitle)
                                .putExtra(BrowserMediaPlaybackService.EXTRA_URL, lastUrl)
                            if (playing) {
                                androidx.core.content.ContextCompat.startForegroundService(context, intent)
                            } else {
                                context.startService(intent)
                            }
                        }
                    }
                }
            }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    fun configure(context: Context, webView: WebView?, isEnabled: Boolean) {
        appContext = context.applicationContext
        webViewRef = webView?.let(::WeakReference)
        enabled = isEnabled
        handler.removeCallbacks(poll)
        if (isEnabled) {
            handler.post(poll)
        } else {
            hasObservedPlayback = false
            lastPlaying = false
            lastTitle = ""
            lastUrl = ""
            context.stopService(Intent(context, BrowserMediaPlaybackService::class.java))
        }
    }

    fun control(play: Boolean) {
        val webView = webViewRef?.get() ?: return
        val js = if (play) MEDIA_PLAY_SCRIPT else MEDIA_PAUSE_SCRIPT
        webView.post {
            if (enabled && webViewRef?.get() === webView) {
                webView.evaluateJavascript(js, null)
            }
        }
    }

    private const val MEDIA_STATE_SCRIPT = """
        (function() {
            var result = {playing:false, title:document.title || '', url:location.href};
            var visited = [];
            function scan(win) {
                try {
                    if (!win || visited.indexOf(win) !== -1) return;
                    visited.push(win);
                    var doc = win.document;
                    var media = Array.from(doc.querySelectorAll('audio,video'));
                    var active = media.find(function(el) { return !el.paused && !el.ended; });
                    var current = active || media.find(function(el) { return !el.ended; });
                    var session = win.navigator && win.navigator.mediaSession;
                    var sessionPlaying = !!(session && session.playbackState === 'playing');
                    if (active || sessionPlaying) {
                        result.playing = true;
                        if (session && session.metadata && session.metadata.title) {
                            result.title = session.metadata.title;
                        } else if (current && current.getAttribute('title')) {
                            result.title = current.getAttribute('title');
                        } else if (doc.title) {
                            result.title = doc.title;
                        }
                        result.url = win.location.href || result.url;
                        return;
                    }
                    var frames = doc.querySelectorAll('iframe,frame');
                    for (var i = 0; i < frames.length; i++) {
                        try {
                            scan(frames[i].contentWindow);
                            if (result.playing) return;
                        } catch (_) {}
                    }
                } catch (_) {}
            }
            scan(window);
            return JSON.stringify(result);
        })()
    """

    private const val MEDIA_PLAY_SCRIPT = """
        (function() {
            var visited = [];
            function scan(win) {
                try {
                    if (!win || visited.indexOf(win) !== -1) return;
                    visited.push(win);
                    var doc = win.document;
                    var media = Array.from(doc.querySelectorAll('audio,video'));
                    var target = media.find(function(el) { return !el.ended; });
                    if (target) {
                        var promise = target.play();
                        if (promise && promise.catch) promise.catch(function() {});
                    }
                    var frames = doc.querySelectorAll('iframe,frame');
                    for (var i = 0; i < frames.length; i++) {
                        try { scan(frames[i].contentWindow); } catch (_) {}
                    }
                } catch (_) {}
            }
            scan(window);
        })()
    """

    private const val MEDIA_PAUSE_SCRIPT = """
        (function() {
            var visited = [];
            function scan(win) {
                try {
                    if (!win || visited.indexOf(win) !== -1) return;
                    visited.push(win);
                    win.document.querySelectorAll('audio,video').forEach(function(el) {
                        if (!el.paused) el.pause();
                    });
                    var frames = win.document.querySelectorAll('iframe,frame');
                    for (var i = 0; i < frames.length; i++) {
                        try { scan(frames[i].contentWindow); } catch (_) {}
                    }
                } catch (_) {}
            }
            scan(window);
        })()
    """
}
