//! Kaspa Browser Native Rust Core Engine
//!
//! Provides ultra-low latency, zero-allocation overscroll physics filtering,
//! gesture state classification, PWA manifest evaluation, and external app intent routing.

pub mod overscroll_filter;
pub mod pwa_engine;
pub mod intent_router;

pub use overscroll_filter::{OverscrollFilter, GestureClassification, TouchPoint};
pub use pwa_engine::{PwaManifest, evaluate_pwa_compatibility};
pub use intent_router::{route_target_url, IntentRouteAction};
