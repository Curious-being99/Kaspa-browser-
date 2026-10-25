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
    private val handler = Handler(Looper.getMainLooper())
    private var webViewRef: WeakReference<WebView>? = null
    private var appContext: Context? = null
    private var enabled = false
    private var lastPlaying = false
    private var lastTitle = ""
    private var lastUrl = ""

    private val poll = object : Runnable {
        override fun run() {
            val webView = webViewRef?.get()
            if (!enabled || webView == null) return
            runCatching {
                    webView.evaluateJavascript(
                        "(function(){var e=Array.from(document.querySelectorAll('audio,video')).find(function(m){return !m.paused&&!m.ended;});return JSON.stringify({playing:!!e,title:(e&&((e.getAttribute('title'))||(document.title)))||document.title||'',url:location.href});})()"
                    ) { raw ->
                        if (!enabled) return@evaluateJavascript
                        runCatching {
                            val decoded = JSONArray("[$raw]").getString(0)
                            val state = JSONObject(decoded)
                            val playing = state.optBoolean("playing", false)
                            val title = state.optString("title").take(180)
                            val url = state.optString("url").take(2048)
                            val changed = playing != lastPlaying || title != lastTitle || url != lastUrl
                            val wasPlaying = lastPlaying
                            lastPlaying = playing
                            lastTitle = title.ifBlank { url }
                            lastUrl = url
                            if (changed && (playing || wasPlaying)) {
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
            handler.postDelayed(this, 1000L)
        }
    }

    fun configure(context: Context, webView: WebView?, isEnabled: Boolean) {
        appContext = context.applicationContext
        webViewRef = webView?.let(::WeakReference)
        enabled = isEnabled
        handler.removeCallbacks(poll)
        if (isEnabled && webView != null) {
            handler.post(poll)
        } else {
            lastPlaying = false
            context.stopService(Intent(context, BrowserMediaPlaybackService::class.java))
        }
    }

    fun control(play: Boolean) {
        val webView = webViewRef?.get() ?: return
        val js = if (play) {
            "(function(){var e=Array.from(document.querySelectorAll('audio,video')).find(function(m){return !m.ended;});if(e){e.play().catch(function(){});}})()"
        } else {
            "(function(){document.querySelectorAll('audio,video').forEach(function(e){if(!e.paused)e.pause();});})()"
        }
        webView.post { webView.evaluateJavascript(js, null) }
    }
}
