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
    if has_user_gesture {
        if let Some(host) = extract_hostname(clean) {
            for (domain, package) in KNOWN_NATIVE_DOMAINS {
                let domain_lower = domain.to_lowercase();
                let dot_domain = format!(".{}", domain_lower);
                if host == domain_lower || host.ends_with(&dot_domain) {
                    return IntentRouteAction::LaunchNativeApp {
                        package_hint: Some(package.to_string()),
                        action: "android.intent.action.VIEW".to_string(),
                        uri: clean.to_string(),
                    };
                }
            }
        }
    }

    IntentRouteAction::RenderInBrowser
}

/// Extracts the normalized lowercase hostname from an HTTP or HTTPS URL.
/// Strips out userinfo, port, path, query parameters, and fragments.
pub fn extract_hostname(url: &str) -> Option<String> {
    let trimmed = url.trim();
    let lower = trimmed.to_lowercase();
    let after_scheme = if let Some(rest) = lower.strip_prefix("https://") {
        rest
    } else if let Some(rest) = lower.strip_prefix("http://") {
        rest
    } else {
        return None;
    };

    // Host terminates at '/', '?', or '#'
    let authority = after_scheme
        .split(|c| c == '/' || c == '?' || c == '#')
        .next()?
        .trim();

    // Strip userinfo (e.g., username:password@host)
    let host_and_port = if let Some(at_idx) = authority.rfind('@') {
        &authority[at_idx + 1..]
    } else {
        authority
    };

    // Strip port, safely accounting for IPv6 bracketed hosts (e.g. [::1]:8080)
    let host = if host_and_port.starts_with('[') {
        if let Some(close_bracket) = host_and_port.find(']') {
            &host_and_port[1..close_bracket]
        } else {
            host_and_port
        }
    } else if let Some(colon_idx) = host_and_port.find(':') {
        &host_and_port[..colon_idx]
    } else {
        host_and_port
    };

    // Trim any trailing dots (e.g. "youtube.com.")
    let clean_host = host.trim_matches('.');
    if clean_host.is_empty() {
        None
    } else {
        Some(clean_host.to_string())
    }
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_hostname() {
        assert_eq!(extract_hostname("https://youtube.com/watch?v=123"), Some("youtube.com".to_string()));
        assert_eq!(extract_hostname("HTTPS://YOUTUBE.COM/WATCH?V=123"), Some("youtube.com".to_string()));
        assert_eq!(extract_hostname("https://m.youtube.com/watch?v=123"), Some("m.youtube.com".to_string()));
        assert_eq!(extract_hostname("http://music.youtube.com:8080/"), Some("music.youtube.com".to_string()));
        assert_eq!(extract_hostname("http://[::1]:8080/"), Some("::1".to_string()));
        assert_eq!(extract_hostname("https://user:pass@open.spotify.com/track/123"), Some("open.spotify.com".to_string()));
        assert_eq!(extract_hostname("https://youtube.com."), Some("youtube.com".to_string()));
        assert_eq!(extract_hostname("ftp://youtube.com"), None);
    }

    #[test]
    fn test_route_target_url_exact_and_subdomain() {
        let res = route_target_url("https://youtube.com/watch?v=test", true);
        assert_eq!(res, IntentRouteAction::LaunchNativeApp {
            package_hint: Some("com.google.android.youtube".to_string()),
            action: "android.intent.action.VIEW".to_string(),
            uri: "https://youtube.com/watch?v=test".to_string(),
        });

        let res_sub = route_target_url("https://music.youtube.com/test", true);
        assert_eq!(res_sub, IntentRouteAction::LaunchNativeApp {
            package_hint: Some("com.google.android.youtube".to_string()),
            action: "android.intent.action.VIEW".to_string(),
            uri: "https://music.youtube.com/test".to_string(),
        });

        let res_multi = route_target_url("https://embed.mobile.youtube.com/test", true);
        assert_eq!(res_multi, IntentRouteAction::LaunchNativeApp {
            package_hint: Some("com.google.android.youtube".to_string()),
            action: "android.intent.action.VIEW".to_string(),
            uri: "https://embed.mobile.youtube.com/test".to_string(),
        });
    }

    #[test]
    fn test_route_target_url_rejects_spoofed_or_substring_domains() {
        // Domain suffix spoofing (e.g. attacker domain)
        assert_eq!(
            route_target_url("https://youtube.com.attacker.com/watch", true),
            IntentRouteAction::RenderInBrowser
        );

        // Preceding characters in domain name
        assert_eq!(
            route_target_url("https://not-youtube.com/watch", true),
            IntentRouteAction::RenderInBrowser
        );

        // Query parameter injection
        assert_eq!(
            route_target_url("https://attacker.com/?redirect=youtube.com", true),
            IntentRouteAction::RenderInBrowser
        );

        // Path component injection
        assert_eq!(
            route_target_url("https://attacker.com/youtube.com/test", true),
            IntentRouteAction::RenderInBrowser
        );

        // Without user gesture, even valid domains render in browser
        assert_eq!(
            route_target_url("https://youtube.com/watch?v=test", false),
            IntentRouteAction::RenderInBrowser
        );
    }
}
