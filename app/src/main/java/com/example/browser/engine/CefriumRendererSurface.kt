package com.example.browser.engine

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cefrium.compose.CefriumView
import com.cefrium.compose.rememberCefriumViewState

/**
 * Renderer-only Cefrium surface.
 *
 * The caller owns tab identity, URL state, navigation UI, persistence, downloads,
 * permissions and all native bridges. Keep this composable keyed by the existing
 * browser tab ID so the renderer lifetime follows that tab.
 *
 * This is an integration seam/smoke surface; it deliberately exposes no wallet
 * signing bridge and does not change shared browser state.
 */
@Composable
fun CefriumRendererSurface(
    initialUrl: String,
    modifier: Modifier = Modifier,
) {
    val state = rememberCefriumViewState(initialUrl)
    CefriumView(
        state = state,
        modifier = modifier,
    )
}
