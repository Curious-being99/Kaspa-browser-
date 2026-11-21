pub use kaspasearch_core::api::*;

pub fn sanitize_url(raw: &str) -> String {
    kaspasearch_core::privacy::PrivacyGuard::sanitize_url(raw)
}

pub fn filter_html_ast(raw: &str) -> String {
    kaspasearch_core::privacy::PrivacyGuard::filter_html_ast(raw)
}

pub fn is_tracker_or_ad(url: &str) -> bool {
    kaspasearch_core::privacy::PrivacyGuard::is_tracker_or_ad(url)
}

pub fn extract_host(url: &str) -> Option<String> {
    kaspasearch_core::privacy::PrivacyGuard::extract_host_fast(url)
}

pub fn is_google_account_domain(host: &str) -> bool {
    kaspasearch_core::privacy::PrivacyGuard::is_google_account_domain(host)
}

pub fn is_google_account_or_auth_url(url: &str) -> bool {
    kaspasearch_core::privacy::PrivacyGuard::is_google_account_or_auth_url(url)
}

pub fn should_block(url: &str) -> bool {
    kaspasearch_core::privacy::PrivacyGuard::should_block(url)
}
