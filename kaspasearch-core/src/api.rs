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
}
