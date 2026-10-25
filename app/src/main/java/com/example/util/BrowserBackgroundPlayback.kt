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
    var onMediaStateChanged: ((playing: Boolean, title: String, url: String) -> Unit)? = null
    private var lastUrl = ""

    private var lastDurationMs = 0L
    private var lastServiceUpdateMs = 0L

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
                        val artist = state.optString("artist").take(120)
                        val album = state.optString("album").take(120)
                        val url = state.optString("url").take(2048)
                        val positionMs = state.optLong("position", 0L)
                        val durationMs = state.optLong("duration", 0L)
                        val normalizedTitle = title.ifBlank { url }

                        val now = System.currentTimeMillis()
                        val durationDiscovered = playing && lastDurationMs == 0L && durationMs > 0L
                        val periodicSync = playing && (now - lastServiceUpdateMs >= 3000L)
                        val stateChanged = playing != lastPlaying ||
                            normalizedTitle != lastTitle ||
                            url != lastUrl
                        val changed = stateChanged || durationDiscovered || periodicSync
                        val wasPlaying = lastPlaying

                        lastPlaying = playing
                        lastTitle = normalizedTitle
                        lastUrl = url
                        if (durationMs > 0L) lastDurationMs = durationMs
                        if (playing) hasObservedPlayback = true

                        onMediaStateChanged?.invoke(playing, lastTitle, lastUrl)

                        if (changed && (hasObservedPlayback || playing || wasPlaying)) {
                            lastServiceUpdateMs = now
                            val context = appContext ?: return@runCatching
                            val intent = Intent(context, BrowserMediaPlaybackService::class.java)
                                .setAction(BrowserMediaPlaybackService.ACTION_UPDATE)
                                .putExtra(BrowserMediaPlaybackService.EXTRA_PLAYING, playing)
                                .putExtra(BrowserMediaPlaybackService.EXTRA_TITLE, lastTitle)
                                .putExtra(BrowserMediaPlaybackService.EXTRA_ARTIST, artist)
                                .putExtra(BrowserMediaPlaybackService.EXTRA_ALBUM, album)
                                .putExtra(BrowserMediaPlaybackService.EXTRA_URL, lastUrl)
                                .putExtra(BrowserMediaPlaybackService.EXTRA_POSITION, positionMs)
                                .putExtra(BrowserMediaPlaybackService.EXTRA_DURATION, durationMs)
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

    fun getBackgroundPlaybackScript(): String = BRAVE_BG_PLAYBACK_SCRIPT

    fun configure(context: Context, webView: WebView?, isEnabled: Boolean) {
        appContext = context.applicationContext
        webViewRef = webView?.let(::WeakReference)
        enabled = isEnabled
        handler.removeCallbacks(poll)
        if (isEnabled) {
            webView?.post {
                webView.evaluateJavascript(BRAVE_BG_PLAYBACK_SCRIPT, null)
            }
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
        val js = if (play) {
            "window.__kaspa_user_pause_allowed = false;\n$MEDIA_PLAY_SCRIPT"
        } else {
            "window.__kaspa_user_pause_allowed = true;\n$MEDIA_PAUSE_SCRIPT;\nsetTimeout(function() { window.__kaspa_user_pause_allowed = false; }, 1000);"
        }
        webView.post {
            if (enabled && webViewRef?.get() === webView) {
                webView.evaluateJavascript(js, null)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val webView = webViewRef?.get() ?: return
        val posSeconds = positionMs / 1000.0
        val js = """
            (function() {
                var visited = [];
                function scan(win) {
                    try {
                        if (!win || visited.indexOf(win) !== -1) return;
                        visited.push(win);
                        win.document.querySelectorAll('audio,video').forEach(function(el) {
                            try { el.currentTime = $posSeconds; } catch(_) {}
                        });
                        var frames = win.document.querySelectorAll('iframe,frame');
                        for (var i = 0; i < frames.length; i++) {
                            try { scan(frames[i].contentWindow); } catch (_) {}
                        }
                    } catch (_) {}
                }
                scan(window);
            })()
        """.trimIndent()
        webView.post {
            if (enabled && webViewRef?.get() === webView) {
                webView.evaluateJavascript(js, null)
            }
        }
    }

    private const val BRAVE_BG_PLAYBACK_SCRIPT = """
        (function() {
            if (window.__brave_bg_playback_installed__) return;
            window.__brave_bg_playback_installed__ = true;

            try {
                Object.defineProperty(document, 'hidden', {
                    get: function() { return false; },
                    configurable: true
                });
                Object.defineProperty(document, 'visibilityState', {
                    get: function() { return 'visible'; },
                    configurable: true
                });
                Object.defineProperty(document, 'webkitVisibilityState', {
                    get: function() { return 'visible'; },
                    configurable: true
                });
                document.hasFocus = function() { return true; };
            } catch (e) {}

            try {
                var origAEL = EventTarget.prototype.addEventListener;
                EventTarget.prototype.addEventListener = function(type, listener, options) {
                    if (type === 'visibilitychange' || type === 'webkitvisibilitychange') {
                        var wrapped = function(event) {
                            try {
                                listener.call(this, event);
                            } catch (_) {}
                        };
                        return origAEL.call(this, type, wrapped, options);
                    }
                    return origAEL.call(this, type, listener, options);
                };
            } catch (e) {}

            try {
                window.addEventListener('pagehide', function(e) {
                    if (e && e.stopImmediatePropagation) e.stopImmediatePropagation();
                }, true);
            } catch (e) {}

            try {
                var lastUserInteractionTime = 0;
                ['click', 'pointerdown', 'touchstart', 'keydown'].forEach(function(evtName) {
                    window.addEventListener(evtName, function() {
                        lastUserInteractionTime = Date.now();
                    }, true);
                });

                var origPause = HTMLMediaElement.prototype.pause;
                HTMLMediaElement.prototype.pause = function() {
                    var isUserTriggered = window.__kaspa_user_pause_allowed || ((Date.now() - lastUserInteractionTime) < 600);
                    if (isUserTriggered) {
                        return origPause.apply(this, arguments);
                    }
                    // Auto-pause calls from page scripts when losing focus or changing app are blocked.
                    return;
                };
            } catch (e) {}

            try {
                var AudioCtx = window.AudioContext || window.webkitAudioContext;
                if (AudioCtx && AudioCtx.prototype) {
                    var origSuspend = AudioCtx.prototype.suspend;
                    if (origSuspend) {
                        AudioCtx.prototype.suspend = function() {
                            var isUserTriggered = window.__kaspa_user_pause_allowed || ((Date.now() - lastUserInteractionTime) < 600);
                            if (isUserTriggered) {
                                return origSuspend.apply(this, arguments);
                            }
                            return Promise.resolve();
                        };
                    }
                }
            } catch (e) {}
        })();
    """

    private const val MEDIA_STATE_SCRIPT = """
        (function() {
            var result = {playing:false, title:document.title || '', artist:'', album:'', url:location.href, position:0, duration:0};
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
                    
                    if (current) {
                        result.position = Math.floor((current.currentTime || 0) * 1000);
                        result.duration = Math.floor((current.duration || 0) * 1000);
                    }
                    if (session && session.metadata) {
                        if (session.metadata.title) result.title = session.metadata.title;
                        if (session.metadata.artist) result.artist = session.metadata.artist;
                        if (session.metadata.album) result.album = session.metadata.album;
                    }
                    if (session && session.positionState) {
                        try {
                            if (session.positionState.duration) result.duration = Math.floor(session.positionState.duration * 1000);
                            if (session.positionState.position) result.position = Math.floor(session.positionState.position * 1000);
                        } catch (_) {}
                    }
                    if (active || sessionPlaying) {
                        result.playing = true;
                        if (current && current.getAttribute('title')) {
                            result.title = result.title || current.getAttribute('title');
                        } else if (doc.title) {
                            result.title = result.title || doc.title;
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
