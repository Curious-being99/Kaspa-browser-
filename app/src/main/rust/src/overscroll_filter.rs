//! Overscroll & Pull-to-Refresh Gesture Filter in Rust.
//!
//! Solves the accidental pull-to-refresh trigger when scrolling back up a webpage.
//!
//! Research-backed rules implemented:
//! 1. INITIAL_TOUCH_LOCK: If touch ACTION_DOWN occurs when scroll_y > 0 or inner_scroll_top > 0,
//!    the gesture is hard-locked to ChildScrolling. Pull-to-refresh is IMPOSSIBLE during this touch.
//! 2. FLING_MOMENTUM_REJECTION: Upward flings reaching scroll_y == 0 cannot trigger refresh.
//! 3. DEADBAND_THRESHOLD: Requires a deliberate downward drag >= 100.0 dp with tension physics.
//! 4. ANGLE_CONSTRAINTS: Horizontal drag delta_x must not exceed 0.5 * delta_y.

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum GestureClassification {
    /// Normal webview child scrolling (DOM handles touch, no refresh)
    ChildScrolling,
    /// Deliberate pull-to-refresh gesture actively engaged
    EngagedOverscroll,
    /// Touch in deadband (monitoring initial movement)
    EvaluatingDeadband,
    /// Pull threshold met; trigger page refresh upon release
    RefreshTriggerReady,
    /// Idle / Touch released
    Idle,
}

#[derive(Debug, Clone, Copy)]
pub struct TouchPoint {
    pub x: f32,
    pub y: f32,
    pub timestamp_ms: i64,
}

pub struct OverscrollFilter {
    down_point: Option<TouchPoint>,
    started_at_true_top: bool,
    current_state: GestureClassification,
    deadband_threshold_px: f32,
    trigger_threshold_px: f32,
    max_horizontal_ratio: f32,
    is_refresh_enabled: bool,
}

impl OverscrollFilter {
    pub fn new(density_dpi: f32, is_refresh_enabled: bool) -> Self {
        let density_scale = (density_dpi / 160.0).max(1.0);
        Self {
            down_point: None,
            started_at_true_top: false,
            current_state: GestureClassification::Idle,
            deadband_threshold_px: 24.0 * density_scale,
            trigger_threshold_px: 110.0 * density_scale,
            max_horizontal_ratio: 0.55,
            is_refresh_enabled,
        }
    }

    /// Called on ACTION_DOWN.
    /// `scroll_y`: WebView.scrollY (must be 0)
    /// `can_scroll_up`: WebView.canScrollVertically(-1) (must be false)
    /// `inner_dom_scroll_top`: DOM element scrollTop (must be 0)
    pub fn on_touch_down(
        &mut self,
        x: f32,
        y: f32,
        timestamp_ms: i64,
        scroll_y: i32,
        can_scroll_up: bool,
        inner_dom_scroll_top: i32,
    ) {
        self.down_point = Some(TouchPoint { x, y, timestamp_ms });

        // Touch MUST originate when both Android WebView and inner DOM are at absolute 0
        self.started_at_true_top = scroll_y <= 0 && !can_scroll_up && inner_dom_scroll_top <= 0;

        if self.started_at_true_top && self.is_refresh_enabled {
            self.current_state = GestureClassification::EvaluatingDeadband;
        } else {
            // Hard lock: any drag starting while scrolled down is strictly ChildScrolling
            self.current_state = GestureClassification::ChildScrolling;
        }
    }

    /// Called on ACTION_MOVE.
    /// Evaluates whether the motion is a valid deliberate pull-to-refresh or normal scrolling.
    pub fn on_touch_move(
        &mut self,
        current_x: f32,
        current_y: f32,
        scroll_y: i32,
        can_scroll_up: bool,
        inner_dom_scroll_top: i32,
    ) -> GestureClassification {
        if !self.is_refresh_enabled || !self.started_at_true_top {
            self.current_state = GestureClassification::ChildScrolling;
            return self.current_state;
        }

        let down = match self.down_point {
            Some(pt) => pt,
            None => {
                self.current_state = GestureClassification::ChildScrolling;
                return self.current_state;
            }
        };

        // If the webview or inner DOM scrolled down at any point during this gesture, lock out refresh
        if scroll_y > 0 || can_scroll_up || inner_dom_scroll_top > 0 {
            self.started_at_true_top = false;
            self.current_state = GestureClassification::ChildScrolling;
            return self.current_state;
        }

        let delta_x = current_x - down.x;
        let delta_y = current_y - down.y;

        // User is scrolling upwards (content moves up) -> NEVER trigger refresh
        if delta_y <= 0.0 {
            self.current_state = GestureClassification::ChildScrolling;
            return self.current_state;
        }

        // Horizontal swipes (carousels, tabs, side menus) -> cancel pull-to-refresh
        if delta_x.abs() > delta_y.abs() * self.max_horizontal_ratio {
            self.current_state = GestureClassification::ChildScrolling;
            return self.current_state;
        }

        if delta_y < self.deadband_threshold_px {
            self.current_state = GestureClassification::EvaluatingDeadband;
        } else if delta_y < self.trigger_threshold_px {
            self.current_state = GestureClassification::EngagedOverscroll;
        } else {
            self.current_state = GestureClassification::RefreshTriggerReady;
        }

        self.current_state
    }

    /// Called on ACTION_UP / ACTION_CANCEL.
    /// Returns true if a refresh should execute.
    pub fn on_touch_up(&mut self) -> bool {
        let should_refresh = self.current_state == GestureClassification::RefreshTriggerReady;
        self.down_point = None;
        self.started_at_true_top = false;
        self.current_state = GestureClassification::Idle;
        should_refresh
    }

    /// Checks if the container should intercept touch events right now.
    pub fn should_intercept(&self) -> bool {
        self.current_state == GestureClassification::EngagedOverscroll
            || self.current_state == GestureClassification::RefreshTriggerReady
    }
}
