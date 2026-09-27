package com.example.network

import android.view.MotionEvent
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native Overscroll & Pull-to-Refresh Physics Engine.
 *
 * Implements the gesture state machine research:
 * 1. INITIAL_TOUCH_LOCK: The touch gesture (ACTION_DOWN) MUST originate when
 *    the WebView is ALREADY at the absolute top (scrollY == 0 && !canScrollVertically(-1)).
 *    If the user touched the screen while scrolled down, this gesture is PERMANENTLY LOCKED
 *    to ChildScrolling for the entire touch stream. Even if the page reaches scrollY == 0
 *    mid-scroll, pull-to-refresh is STRICTLY FORBIDDEN until the user lifts their finger
 *    and begins a NEW intentional downward pull from the top.
 * 2. DELIBERATE_THRESHOLD: Requires an intentional downward drag of >= 100dp.
 * 3. FLING_MOMENTUM_REJECTION: Rapid momentum flings reaching scrollY == 0 cannot trigger refresh.
 * 4. ANGLE_CONSTRAINTS: Horizontal swipes (carousels, tabs) are completely rejected.
 */
object NativeOverscrollEngine {

    enum class GestureState {
        IDLE,
        CHILD_SCROLLING,
        EVALUATING_DEADBAND,
        ENGAGED_PULL,
        TRIGGER_READY
    }

    class GestureSession(private val density: Float, private val isRefreshEnabled: Boolean) {
        private var downX = 0f
        private var downY = 0f
        private var downScrollY = 0
        private var startedAtTrueTop = false
        private var currentState = GestureState.IDLE
        private val deadbandPx = 28f * density.coerceAtLeast(1f)
        private val triggerThresholdPx = 110f * density.coerceAtLeast(1f)
        private val maxHorizontalRatio = 0.55f

        fun onTouchDown(ev: MotionEvent, webView: WebView?) {
            downX = ev.rawX
            downY = ev.rawY
            val scrollY = webView?.scrollY ?: 0
            val canScrollUp = webView?.canScrollVertically(-1) ?: false
            downScrollY = scrollY

            // Strict condition: Both scrollY and canScrollVertically must confirm top of page
            startedAtTrueTop = (scrollY <= 0 && !canScrollUp)

            currentState = if (startedAtTrueTop && isRefreshEnabled) {
                GestureState.EVALUATING_DEADBAND
            } else {
                // Hard-lock: any touch starting while scrolled down is strictly locked to child scrolling
                GestureState.CHILD_SCROLLING
            }
        }

        fun onTouchMove(ev: MotionEvent, webView: WebView?): GestureState {
            if (!isRefreshEnabled || !startedAtTrueTop || currentState == GestureState.CHILD_SCROLLING) {
                currentState = GestureState.CHILD_SCROLLING
                return currentState
            }

            val scrollY = webView?.scrollY ?: 0
            val canScrollUp = webView?.canScrollVertically(-1) ?: false

            // If the WebView is scrolled down at all, cancel pull-to-refresh immediately
            if (scrollY > 0 || canScrollUp) {
                startedAtTrueTop = false
                currentState = GestureState.CHILD_SCROLLING
                return currentState
            }

            val deltaX = ev.rawX - downX
            val deltaY = ev.rawY - downY

            // User is scrolling upward (content moves up) -> NEVER pull-to-refresh
            if (deltaY <= 0f) {
                currentState = GestureState.CHILD_SCROLLING
                return currentState
            }

            // Horizontal drag -> cancel pull-to-refresh
            if (Math.abs(deltaX) > Math.abs(deltaY) * maxHorizontalRatio) {
                currentState = GestureState.CHILD_SCROLLING
                return currentState
            }

            currentState = when {
                deltaY < deadbandPx -> GestureState.EVALUATING_DEADBAND
                deltaY < triggerThresholdPx -> GestureState.ENGAGED_PULL
                else -> GestureState.TRIGGER_READY
            }

            return currentState
        }

        fun onTouchUp(): Boolean {
            val shouldRefresh = (currentState == GestureState.TRIGGER_READY)
            startedAtTrueTop = false
            currentState = GestureState.IDLE
            return shouldRefresh
        }

        fun shouldIntercept(): Boolean {
            return currentState == GestureState.ENGAGED_PULL || currentState == GestureState.TRIGGER_READY
        }

        fun isLockedToChild(): Boolean {
            return currentState == GestureState.CHILD_SCROLLING
        }
    }
}
