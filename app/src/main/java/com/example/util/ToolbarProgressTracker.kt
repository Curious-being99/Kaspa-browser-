package com.example.util

import android.webkit.WebView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.KaspaTea
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import java.util.Collections
import java.util.WeakHashMap

enum class ToolbarProgressPhase {
    IDLE,
    LOADING,
    FINISHING
}

@Immutable
data class ToolbarProgressState(
    val phase: ToolbarProgressPhase = ToolbarProgressPhase.IDLE,
    val progress: Float = 0f,
    val isVisible: Boolean = false,
    val activeSessionId: Int = -1,
    val hasMainPageFinished: Boolean = false,
    val completionEpoch: Int = 0
)

/**
 * Chrome & Brave-style monotonic top-level navigation progress state machine:
 * - Appears once at the start of a real navigation (ignoring about:blank / data: / kaspa-error://).
 * - Never resets backward across HTTP 301/302 redirects or duplicate start callbacks in the same navigation.
 * - Sweeps smoothly to 1.0f on completion, holds progress at 1.0f throughout fade-out, and resets to 0f
 *   only after the bar is completely hidden.
 * - Ignores trailing subresource/iframe progress ticks after the main document has finished.
 */
class ToolbarProgressTracker {
    companion object {
        const val INITIAL_PROGRESS = 0.08f
        const val VISUAL_COMMIT_MIN_PROGRESS = 0.72f
        const val MAX_ACTIVE_PROGRESS = 0.96f
        const val TRICKLE_CAP_PROGRESS = 0.34f
        const val TRICKLE_STEP = 0.025f

        fun isTrackableUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val trimmed = url.trim()
            return !trimmed.startsWith("about:", ignoreCase = true) &&
                !trimmed.startsWith("data:", ignoreCase = true) &&
                !trimmed.startsWith("javascript:", ignoreCase = true) &&
                !trimmed.startsWith("kaspa-error://", ignoreCase = true)
        }
    }

    private val _state = MutableStateFlow(ToolbarProgressState())
    val state: StateFlow<ToolbarProgressState> = _state.asStateFlow()

    private val lock = Any()

    fun startNavigation(
        url: String?,
        sessionId: Int = -1,
        forceRestart: Boolean = false
    ) {
        if (!isTrackableUrl(url)) return
        synchronized(lock) {
            val cur = _state.value
            val isSameActiveLoad = !forceRestart &&
                cur.phase == ToolbarProgressPhase.LOADING &&
                !cur.hasMainPageFinished &&
                (sessionId == -1 || cur.activeSessionId == -1 || sessionId == cur.activeSessionId)

            if (isSameActiveLoad) {
                val resolvedSession = if (sessionId != -1) sessionId else cur.activeSessionId
                val nextProgress = maxOf(cur.progress, INITIAL_PROGRESS)
                if (resolvedSession != cur.activeSessionId || nextProgress != cur.progress || !cur.isVisible) {
                    _state.value = cur.copy(
                        isVisible = true,
                        progress = nextProgress,
                        activeSessionId = resolvedSession
                    )
                }
                return
            }

            _state.value = ToolbarProgressState(
                phase = ToolbarProgressPhase.LOADING,
                progress = INITIAL_PROGRESS,
                isVisible = true,
                activeSessionId = sessionId,
                hasMainPageFinished = false,
                completionEpoch = cur.completionEpoch
            )
        }
    }

    fun onProgressChanged(rawPercent: Int) {
        if (rawPercent >= 100) {
            finishNavigation()
            return
        }
        if (rawPercent <= 0) return
        synchronized(lock) {
            val cur = _state.value
            if (cur.phase != ToolbarProgressPhase.LOADING || cur.hasMainPageFinished) {
                return
            }
            val mapped = (rawPercent / 100f).coerceIn(INITIAL_PROGRESS, MAX_ACTIVE_PROGRESS)
            if (mapped > cur.progress) {
                _state.value = cur.copy(progress = mapped, isVisible = true)
            }
        }
    }

    fun onVisualCommit() {
        synchronized(lock) {
            val cur = _state.value
            if (cur.phase == ToolbarProgressPhase.LOADING && !cur.hasMainPageFinished) {
                val boosted = maxOf(cur.progress, VISUAL_COMMIT_MIN_PROGRESS)
                if (boosted > cur.progress) {
                    _state.value = cur.copy(progress = boosted)
                }
            }
        }
    }

    fun trickle() {
        synchronized(lock) {
            val cur = _state.value
            if (cur.phase == ToolbarProgressPhase.LOADING &&
                !cur.hasMainPageFinished &&
                cur.progress < TRICKLE_CAP_PROGRESS
            ) {
                val next = (cur.progress + TRICKLE_STEP).coerceAtMost(TRICKLE_CAP_PROGRESS)
                _state.value = cur.copy(progress = next)
            }
        }
    }

    fun finishNavigation() {
        synchronized(lock) {
            val cur = _state.value
            if (cur.phase == ToolbarProgressPhase.IDLE) return
            if (cur.phase == ToolbarProgressPhase.FINISHING && cur.hasMainPageFinished) return
            _state.value = cur.copy(
                phase = ToolbarProgressPhase.FINISHING,
                progress = 1.0f,
                isVisible = true,
                hasMainPageFinished = true,
                completionEpoch = cur.completionEpoch + 1
            )
        }
    }

    fun beginFadeOut(expectedEpoch: Int) {
        synchronized(lock) {
            val cur = _state.value
            if (cur.phase == ToolbarProgressPhase.FINISHING &&
                cur.completionEpoch == expectedEpoch &&
                cur.isVisible
            ) {
                // Keep progress locked at 1.0f while alpha fades out cleanly
                _state.value = cur.copy(isVisible = false, progress = 1.0f)
            }
        }
    }

    fun finishFadeOutReset(expectedEpoch: Int) {
        synchronized(lock) {
            val cur = _state.value
            if (cur.phase == ToolbarProgressPhase.FINISHING &&
                !cur.isVisible &&
                cur.completionEpoch == expectedEpoch
            ) {
                _state.value = ToolbarProgressState(
                    phase = ToolbarProgressPhase.IDLE,
                    progress = 0f,
                    isVisible = false,
                    activeSessionId = -1,
                    hasMainPageFinished = false,
                    completionEpoch = expectedEpoch
                )
            }
        }
    }

    fun resetImmediately() {
        synchronized(lock) {
            val cur = _state.value
            _state.value = ToolbarProgressState(
                phase = ToolbarProgressPhase.IDLE,
                progress = 0f,
                isVisible = false,
                activeSessionId = -1,
                hasMainPageFinished = false,
                completionEpoch = cur.completionEpoch + 1
            )
        }
    }
}

/**
 * Isolated single-shot navigation dispatch guard per WebView so Compose recompositions
 * (including redirects, https upgrades, URL bar updates, and progress ticks) never call
 * webView.loadUrl() or loadDataWithBaseURL() twice for the same navigation session.
 */
object WebViewSingleLoadGuard {
    data class DispatchedLoadState(
        val sessionId: Int,
        val dispatchedUrl: String,
        val dataLoadKey: String? = null
    )

    private val dispatchedByWebView = Collections.synchronizedMap(WeakHashMap<WebView, DispatchedLoadState>())

    fun shouldDispatchHttpLoad(
        currentState: DispatchedLoadState?,
        sessionId: Int,
        targetUrl: String,
        webViewCurrentUrl: String?
    ): Boolean {
        if (currentState != null && currentState.sessionId == sessionId) {
            return false
        }
        if (currentState == null &&
            sessionId == 0 &&
            !webViewCurrentUrl.isNullOrBlank() &&
            BrowserTabWebViewManager.isSameUrl(webViewCurrentUrl, targetUrl)
        ) {
            return false
        }
        return true
    }

    fun shouldDispatchDataLoad(
        currentState: DispatchedLoadState?,
        sessionId: Int,
        dataLoadKey: String
    ): Boolean {
        if (currentState != null &&
            currentState.sessionId == sessionId &&
            currentState.dataLoadKey == dataLoadKey
        ) {
            return false
        }
        return true
    }

    fun getDispatchedState(webView: WebView): DispatchedLoadState? =
        synchronized(dispatchedByWebView) { dispatchedByWebView[webView] }

    fun markDispatched(
        webView: WebView,
        sessionId: Int,
        url: String,
        dataLoadKey: String? = null
    ) {
        synchronized(dispatchedByWebView) {
            dispatchedByWebView[webView] = DispatchedLoadState(
                sessionId = sessionId,
                dispatchedUrl = url,
                dataLoadKey = dataLoadKey
            )
        }
    }

    fun clear(webView: WebView?) {
        if (webView == null) return
        synchronized(dispatchedByWebView) {
            dispatchedByWebView.remove(webView)
        }
    }
}

/**
 * Lightweight, scoped Chrome/Brave-style 2.5dp top-edge loading indicator.
 * Observing progress inside its own composable prevents progress animations from
 * recomposing the parent BrowserGatewayScreen or AndroidView container.
 */
@Composable
fun ChromeStyleLoadingProgressBar(
    tracker: ToolbarProgressTracker,
    modifier: Modifier = Modifier
) {
    val progressState by tracker.state.collectAsState()

    // Smooth initial TTFB crawl (Chrome/Brave style) while waiting for response headers
    LaunchedEffect(progressState.phase, progressState.activeSessionId) {
        if (progressState.phase == ToolbarProgressPhase.LOADING) {
            delay(280L)
            while (isActive && tracker.state.value.phase == ToolbarProgressPhase.LOADING) {
                tracker.trickle()
                delay(240L)
            }
        }
    }

    // Single completion lifecycle: sweep to 1.0f -> hold briefly -> fade alpha -> reset only after invisible
    LaunchedEffect(progressState.phase, progressState.completionEpoch) {
        if (progressState.phase == ToolbarProgressPhase.FINISHING) {
            val epoch = progressState.completionEpoch
            delay(210L)
            tracker.beginFadeOut(epoch)
            delay(250L)
            tracker.finishFadeOutReset(epoch)
        }
    }

    val targetAlpha = when {
        progressState.phase == ToolbarProgressPhase.IDLE -> 0f
        progressState.isVisible -> 1f
        else -> 0f
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(
            durationMillis = if (progressState.isVisible) 110 else 230,
            easing = if (progressState.isVisible) FastOutSlowInEasing else LinearOutSlowInEasing
        ),
        label = "chromeProgressAlpha"
    )

    val targetFraction = when (progressState.phase) {
        ToolbarProgressPhase.IDLE -> 0f
        ToolbarProgressPhase.LOADING -> progressState.progress.coerceIn(0.06f, 0.98f)
        ToolbarProgressPhase.FINISHING -> 1.0f
    }

    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(
            durationMillis = if (progressState.phase == ToolbarProgressPhase.FINISHING) 170 else 150,
            easing = FastOutSlowInEasing
        ),
        label = "chromeProgressFraction"
    )

    if (progressState.phase != ToolbarProgressPhase.IDLE || animatedAlpha > 0.005f) {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .graphicsLayer { alpha = animatedAlpha }
                .testTag("browser_loading_progress_bar")
        ) {
            val fillWidth = (size.width * animatedFraction).coerceIn(0f, size.width)
            if (fillWidth > 0f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(KaspaTea, ElectricCyan),
                        startX = 0f,
                        endX = fillWidth.coerceAtLeast(1f)
                    ),
                    topLeft = Offset.Zero,
                    size = Size(fillWidth, size.height)
                )
                // Crisp Chrome-style leading edge accent highlight
                val tipWidth = minOf(18.dp.toPx(), fillWidth)
                drawRect(
                    color = Color.White.copy(alpha = 0.45f),
                    topLeft = Offset(fillWidth - tipWidth, 0f),
                    size = Size(tipWidth, size.height)
                )
            }
        }
    }
}
