use std::fmt;

pub const API_VERSION: u32 = 2;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BrowserPlatform {
    Android,
    Linux,
    Windows,
    MacOs,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EngineKind {
    AndroidWebView,
    ChromiumEmbeddedFramework,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct EngineCapabilities {
    pub platform: BrowserPlatform,
    pub engine: EngineKind,
    pub supports_javascript: bool,
    pub supports_service_workers: bool,
    pub supports_web_messages: bool,
    pub supports_downloads: bool,
    pub supports_media_playback: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum NavigationKind {
    Navigate,
    Reload,
    Back,
    Forward,
    Stop,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct NavigationCommand {
    pub kind: NavigationKind,
    pub url: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum BrowserEvent {
    NavigationStarted { url: String },
    NavigationCommitted { url: String },
    LoadProgress { percent: u8 },
    LoadFinished { url: String },
    LoadFailed { url: String, code: i32 },
    TitleChanged { title: String },
    SecurityStateChanged { secure: bool },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum NavigationError {
    MissingUrl,
    UnsupportedScheme,
    InvalidUrl,
}

impl fmt::Display for NavigationError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::MissingUrl => write!(f, "navigation URL is required"),
            Self::UnsupportedScheme => write!(f, "navigation scheme is not supported"),
            Self::InvalidUrl => write!(f, "navigation URL is invalid"),
        }
    }
}

impl std::error::Error for NavigationError {}

pub fn validate_navigation_url(raw: &str) -> Result<String, NavigationError> {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return Err(NavigationError::MissingUrl);
    }
    let parsed = url::Url::parse(trimmed).map_err(|_| NavigationError::InvalidUrl)?;
    match parsed.scheme() {
        "http" | "https" => Ok(parsed.to_string()),
        _ => Err(NavigationError::UnsupportedScheme),
    }
}

pub fn sanitize_navigation_url(raw: &str) -> Result<String, NavigationError> {
    validate_navigation_url(raw).map(|url| crate::privacy::PrivacyGuard::sanitize_url(&url))
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(i32)]
pub enum SpeculativePreloadDecision {
    AllowPreconnectAndPrefetch = 1,
    SkipPrivacyOrRelayActive = -1,
    SkipLowRamDevice = -2,
    SkipNonHttps = -3,
    SkipAuthOrChallenge = -4,
    SkipTrackerDomain = -5,
    SkipInvalidUrl = -6,
}

pub fn evaluate_speculative_preload(
    raw_url: &str,
    privacy_relay_or_incognito: bool,
    low_ram_device: bool,
) -> SpeculativePreloadDecision {
    if privacy_relay_or_incognito {
        return SpeculativePreloadDecision::SkipPrivacyOrRelayActive;
    }
    if low_ram_device {
        return SpeculativePreloadDecision::SkipLowRamDevice;
    }
    let trimmed = raw_url.trim();
    if trimmed.is_empty() {
        return SpeculativePreloadDecision::SkipInvalidUrl;
    }
    let parsed = match url::Url::parse(trimmed) {
        Ok(u) => u,
        Err(_) => return SpeculativePreloadDecision::SkipInvalidUrl,
    };
    if !parsed.scheme().eq_ignore_ascii_case("https") {
        return SpeculativePreloadDecision::SkipNonHttps;
    }
    let host = match parsed.host_str() {
        Some(h) if !h.is_empty() => h.to_ascii_lowercase(),
        _ => return SpeculativePreloadDecision::SkipInvalidUrl,
    };
    if host == "localhost" || host == "127.0.0.1" {
        return SpeculativePreloadDecision::SkipInvalidUrl;
    }
    if crate::privacy::PrivacyGuard::is_google_account_or_auth_url(trimmed)
        || crate::privacy::PrivacyGuard::is_google_account_domain(&host)
    {
        return SpeculativePreloadDecision::SkipAuthOrChallenge;
    }
    let lower = trimmed.to_ascii_lowercase();
    if lower.contains("/cdn-cgi/")
        || lower.contains("turnstile")
        || lower.contains("hcaptcha")
        || lower.contains("recaptcha")
        || lower.contains("arkose")
        || lower.contains("datadome")
        || lower.contains("/login")
        || lower.contains("/oauth")
        || lower.contains("/signin")
        || lower.contains("/webauthn")
        || lower.contains("/passkey")
    {
        return SpeculativePreloadDecision::SkipAuthOrChallenge;
    }
    if crate::privacy::PrivacyGuard::is_tracker_or_ad(trimmed) {
        return SpeculativePreloadDecision::SkipTrackerDomain;
    }
    SpeculativePreloadDecision::AllowPreconnectAndPrefetch
}

/// C-ABI export usable by Desktop C++ (WebKitGTK) and iOS Swift/Obj-C++ layers.
#[no_mangle]
pub unsafe extern "C" fn kazview_eval_speculative_preload(
    url_ptr: *const std::os::raw::c_char,
    privacy_relay_or_incognito: bool,
    low_ram_device: bool,
) -> i32 {
    if url_ptr.is_null() {
        return SpeculativePreloadDecision::SkipInvalidUrl as i32;
    }
    let Ok(raw) = std::ffi::CStr::from_ptr(url_ptr).to_str() else {
        return SpeculativePreloadDecision::SkipInvalidUrl as i32;
    };
    evaluate_speculative_preload(raw, privacy_relay_or_incognito, low_ram_device) as i32
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn accepts_http_and_https_and_normalizes_url() {
        assert_eq!(
            validate_navigation_url(" https://example.com/path ").unwrap(),
            "https://example.com/path"
        );
        assert_eq!(validate_navigation_url("http://example.com").unwrap(), "http://example.com/");
    }

    #[test]
    fn rejects_missing_invalid_and_unsafe_schemes() {
        assert_eq!(validate_navigation_url("  "), Err(NavigationError::MissingUrl));
        assert_eq!(validate_navigation_url("not a url"), Err(NavigationError::InvalidUrl));
        assert_eq!(validate_navigation_url("javascript:alert(1)"), Err(NavigationError::UnsupportedScheme));
        assert_eq!(validate_navigation_url("file:///etc/passwd"), Err(NavigationError::UnsupportedScheme));
    }

    #[test]
    fn navigation_sanitization_preserves_functional_parameters() {
        let url = sanitize_navigation_url("https://example.com/?q=kaspa&utm_source=test").unwrap();
        assert!(url.contains("q=kaspa"));
        assert!(!url.contains("utm_source"));
    }

    #[test]
    fn speculative_preload_gating_respects_privacy_low_ram_auth_and_trackers() {
        assert_eq!(
            evaluate_speculative_preload("https://kaspa.org/", false, false),
            SpeculativePreloadDecision::AllowPreconnectAndPrefetch
        );
        assert_eq!(
            evaluate_speculative_preload("https://kaspa.org/", true, false),
            SpeculativePreloadDecision::SkipPrivacyOrRelayActive
        );
        assert_eq!(
            evaluate_speculative_preload("https://kaspa.org/", false, true),
            SpeculativePreloadDecision::SkipLowRamDevice
        );
        assert_eq!(
            evaluate_speculative_preload("http://example.com/", false, false),
            SpeculativePreloadDecision::SkipNonHttps
        );
        assert_eq!(
            evaluate_speculative_preload("https://accounts.google.com/signin", false, false),
            SpeculativePreloadDecision::SkipAuthOrChallenge
        );
        assert_eq!(
            evaluate_speculative_preload("https://doubleclick.net/pixel.js", false, false),
            SpeculativePreloadDecision::SkipTrackerDomain
        );
    }
}
