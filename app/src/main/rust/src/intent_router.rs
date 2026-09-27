//! Native Rust Deep-Link & External App Intent Router.
//!
//! Evaluates whether an incoming URL or clicked link should:
//! 1. Open externally in an installed native Android application (e.g. YouTube, Spotify, Telegram, X/Twitter, WhatsApp, Maps)
//! 2. Execute via an Android `intent://` scheme or custom URI scheme (e.g. `tg://`, `kaspa://`, `mailto:`, `tel:`)
//! 3. Render internally inside the browser rendering pipeline.

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum IntentRouteAction {
    /// Launch installed native Android application outside the browser
    LaunchNativeApp {
        package_hint: Option<String>,
        action: String,
        uri: String,
    },
    /// Parse and execute Android `intent://` specification with fallback
    ParseIntentScheme {
        raw_intent_uri: String,
        fallback_url: Option<String>,
    },
    /// Custom OS protocol scheme (e.g., mailto, tel, sms, geo, crypto wallets)
    SystemProtocol {
        scheme: String,
        uri: String,
    },
    /// Render normally inside Chromium/WebView engine
    RenderInBrowser,
}

/// Known high-priority native app domains and scheme mappings
const KNOWN_NATIVE_DOMAINS: &[(&str, &str)] = &[
    ("youtube.com", "com.google.android.youtube"),
    ("youtu.be", "com.google.android.youtube"),
    ("open.spotify.com", "com.spotify.music"),
    ("t.me", "org.telegram.messenger"),
    ("telegram.me", "org.telegram.messenger"),
    ("twitter.com", "com.twitter.android"),
    ("x.com", "com.twitter.android"),
    ("instagram.com", "com.instagram.android"),
    ("maps.google.com", "com.google.android.apps.maps"),
    ("whatsapp.com", "com.whatsapp"),
];

const KNOWN_CUSTOM_SCHEMES: &[&str] = &[
    "tg", "telegram", "whatsapp", "twitter", "tweet", "spotify", "vnd.youtube",
    "mailto", "tel", "sms", "smsto", "mms", "geo", "google.navigation",
    "kaspa", "kas", "ethereum", "solana", "wc", "metamask", "phantom", "trust",
];

/// Classifies a target URL into an actionable routing decision
pub fn route_target_url(url: &str, has_user_gesture: bool) -> IntentRouteAction {
    let clean = url.trim();
    if clean.is_empty() {
        return IntentRouteAction::RenderInBrowser;
    }

    // 1. Android Intent URI (intent://...)
    if clean.starts_with("intent://") || clean.starts_with("INTENT://") {
        return IntentRouteAction::ParseIntentScheme {
            raw_intent_uri: clean.to_string(),
            fallback_url: extract_intent_fallback(clean),
        };
    }

    // 2. Custom non-HTTP schemes
    if let Some(colon_pos) = clean.find(':') {
        let scheme = &clean[..colon_pos].to_lowercase();
        if scheme != "http" && scheme != "https" && scheme != "about" && scheme != "data" && scheme != "javascript" && scheme != "blob" && scheme != "file" {
            if KNOWN_CUSTOM_SCHEMES.contains(&scheme.as_str()) || !scheme.is_empty() {
                return IntentRouteAction::SystemProtocol {
                    scheme: scheme.to_string(),
                    uri: clean.to_string(),
                };
            }
        }
    }

    // 3. HTTP / HTTPS with native app domain matching (only on deliberate user gesture)
    if has_user_gesture && (clean.starts_with("http://") || clean.starts_with("https://")) {
        let lower = clean.to_lowercase();
        for (domain, package) in KNOWN_NATIVE_DOMAINS {
            if lower.contains(domain) {
                return IntentRouteAction::LaunchNativeApp {
                    package_hint: Some(package.to_string()),
                    action: "android.intent.action.VIEW".to_string(),
                    uri: clean.to_string(),
                };
            }
        }
    }

    IntentRouteAction::RenderInBrowser
}

fn extract_intent_fallback(intent_uri: &str) -> Option<String> {
    let fallback_prefix = "browser_fallback_url=";
    if let Some(pos) = intent_uri.find(fallback_prefix) {
        let after = &intent_uri[pos + fallback_prefix.len()..];
        let end_pos = after.find(';').unwrap_or(after.len());
        let encoded = &after[..end_pos];
        Some(encoded.to_string())
    } else {
        None
    }
}
