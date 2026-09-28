package com.example.util

import android.util.Log

object BrowserStateLog {
    private const val TAG = "BrowserState"

    fun restore(reason: String) {
        Log.i(TAG, "[BrowserState] restore: $reason")
    }

    fun tabSwitch(reason: String) {
        Log.i(TAG, "[BrowserState] tab switch: $reason")
    }

    fun navigation(reason: String) {
        Log.i(TAG, "[BrowserState] navigation: $reason")
    }

    fun save(reason: String) {
        Log.i(TAG, "[BrowserState] save: $reason")
    }

    fun webViewCreated(reason: String) {
        Log.i(TAG, "[BrowserState] WebView created: $reason")
    }

    fun webViewDestroyed(reason: String) {
        Log.i(TAG, "[BrowserState] WebView destroyed: $reason")
    }

    fun loadUrl(reason: String) {
        Log.i(TAG, "[BrowserState] loadUrl: $reason")
    }

    fun reload(reason: String) {
        Log.i(TAG, "[BrowserState] reload: $reason")
    }
}
