package com.example.browser.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.cefrium.compose.CefriumView
import com.cefrium.compose.rememberCefriumNavigator
import com.cefrium.compose.rememberCefriumViewState

/**
 * Renderer-only surface for an existing browser tab.
 *
 * Tab identity, address bar, history persistence, downloads, permissions,
 * privacy policy and native wallet authorization remain owned by KaspaBrowser.
 * No JavaScript object or signing capability is exposed by this surface.
 */
@Composable
fun CefriumRendererSurface(
    initialUrl: String,
    navigationSessionId: Int,
    modifier: Modifier = Modifier,
) {
    val state = rememberCefriumViewState(initialUrl)
    val navigator = rememberCefriumNavigator()

    LaunchedEffect(navigationSessionId, initialUrl) {
        if (initialUrl.isNotBlank()) {
            navigator.loadUrl(initialUrl)
        }
    }

    CefriumView(
        state = state,
        modifier = modifier,
        navigator = navigator,
    )
}
