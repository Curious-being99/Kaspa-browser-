pub const API_VERSION: u32 = 1;

pub fn sanitize_url(raw_url: &str) -> String {
    crate::privacy::PrivacyGuard::sanitize_url(raw_url)
}

pub fn filter_html_ast(raw_html: &str) -> String {
    crate::privacy::PrivacyGuard::filter_html_ast(raw_html)
}

pub fn is_tracker_or_ad(url: &str) -> bool {
    crate::privacy::PrivacyGuard::is_tracker_or_ad(url)
}

pub fn extract_host(url: &str) -> Option<String> {
    crate::privacy::PrivacyGuard::extract_host_fast(url)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn api_version_is_stable() {
        assert_eq!(API_VERSION, 1);
    }

    #[test]
    fn sanitize_url_preserves_non_tracking_query_parameters() {
        let result = sanitize_url("https://example.com/page?keep=yes&utm_source=campaign");
        assert_eq!(result, "https://example.com/page?keep=yes");
    }

    #[test]
    fn sanitize_url_keeps_unparseable_input_unchanged() {
        let input = "not a valid url?utm_source=campaign";
        assert_eq!(sanitize_url(input), input);
    }

    #[test]
    fn tracker_detection_uses_existing_privacy_policy() {
        assert!(is_tracker_or_ad("https://doubleclick.net/ad.js"));
        assert!(!is_tracker_or_ad("https://kaspa.org/"));
    }

    #[test]
    fn host_extraction_returns_the_host() {
        assert_eq!(
            extract_host("https://kaspa.org/path"),
            Some("kaspa.org".to_string())
        );
    }
}
