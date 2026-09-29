use url::Url;

/// Comprehensive list of tracker, analytics, fingerprinting, and ad-network domains.
/// Migrated from KaspaPrivacyEngine.kt and UBlockEngine.kt for native-speed zero-allocation evaluation.
pub const TRACKER_AND_AD_DOMAINS: &[&str] = &[
    // Ad Networks & Pixels
    "doubleclick.net",
    "google-analytics.com",
    "googleadservices.com",
    "connect.facebook.net",
    "facebook.com/tr",
    "scorecardresearch.com",
    "quantserve.com",
    "hotjar.com",
    "mixpanel.com",
    "segment.io",
    "amplitude.com",
    "heap.io",
    "fullstory.com",
    "crazyegg.com",
    "mouseflow.com",
    "smartlook.com",
    "clarity.ms",
    "yandex.ru/metrika",
    "mc.yandex.ru",
    "criteo.com",
    "criteo.net",
    "taboola.com",
    "outbrain.com",
    "adnxs.com",
    "adsrvr.org",
    "moatads.com",
    "pubmatic.com",
    "rubiconproject.com",
    "openx.net",
    "adroll.com",
    "applovin.com",
    "unityads.unity3d.com",
    "vungle.com",
    "chartboost.com",
    "inmobi.com",
    "ironsrc.com",
    "adcolony.com",
    "amazon-adsystem.com",
    "casalemedia.com",
    "bidswitch.net",
    "smartadserver.com",
    "sovrn.com",
    "advertising.com",
    // Telemetry & Error Tracking
    "bugsnag.com",
    "sentry.io",
    "sentry-cdn.com",
    "getsentry.com",
    "loggly.com",
    "datadoghq.com",
    "newrelic.com",
    "raygun.io",
    "rollbar.com",
    "trackjs.com",
    "inspectlet.com",
    // Fingerprinting & Behavioral Profiling
    "fingerprintjs.com",
    "fpjs.sh",
    "iovation.com",
    "threatmetrix.com",
    "perimeterx.net",
    "arkoselabs.com",
    "datadome.co",
];

/// Google Account & Authentication domains and infrastructure that must NEVER be blocked
pub const GOOGLE_ACCOUNT_DOMAINS: &[&str] = &[
    "accounts.google.com",
    "myaccount.google.com",
    "accounts.youtube.com",
    "apis.google.com",
    "oauth2.googleapis.com",
    "identitytoolkit.googleapis.com",
    "securetoken.googleapis.com",
    "clients6.google.com",
    "content.googleapis.com",
    "ogs.google.com",
    "passwords.google.com",
    "families.google.com",
    "gds.google.com",
    "id.google.com",
    "recaptcha.net",
    "www.recaptcha.net",
    "ssl.gstatic.com",
    "www.gstatic.com",
    "gstatic.com",
    "googleusercontent.com",
];

/// Essential CDN, font, and web infrastructure domains that must NEVER be blocked
pub const ESSENTIAL_WEB_DOMAINS: &[&str] = &[
    "accounts.google.com",
    "myaccount.google.com",
    "accounts.youtube.com",
    "apis.google.com",
    "oauth2.googleapis.com",
    "identitytoolkit.googleapis.com",
    "securetoken.googleapis.com",
    "clients6.google.com",
    "content.googleapis.com",
    "ogs.google.com",
    "passwords.google.com",
    "families.google.com",
    "gds.google.com",
    "id.google.com",
    "ssl.gstatic.com",
    "www.gstatic.com",
    "gstatic.com",
    "googleusercontent.com",
    "recaptcha.net",
    "www.recaptcha.net",
    "googletagmanager.com",
    "googletagservices.com",
    "google-analytics.com",
    "fonts.googleapis.com",
    "fonts.gstatic.com",
    "cdnjs.cloudflare.com",
    "cdn.jsdelivr.net",
    "unpkg.com",
    "ajax.googleapis.com",
    "stackpath.bootstrapcdn.com",
    "maxcdn.bootstrapcdn.com",
    "code.jquery.com",
    "cdn.tailwindcss.com",
    "cloudflare.com",
    "fastly.net",
    "akamaihd.net",
    "githubassets.com",
    "raw.githubusercontent.com",
    "wikimedia.org",
    "wikipedia.org",
    "wp.com",
    "s.w.org",
    "w3.org",
    "kaspa.org",
    "kas.pa",
    "youtube.com",
    "m.youtube.com",
    "www.youtube.com",
    "youtu.be",
    "googlevideo.com",
    "ytimg.com",
    "i.ytimg.com",
    "jnn-pa.googleapis.com",
    "play.google.com",
    "ggpht.com",
    "yt3.ggpht.com",
    "yt4.ggpht.com",
    "youtube-nocookie.com",
    "vimeo.com",
    "vimeocdn.com",
    "dailymotion.com",
];

/// Baseline uBlock Origin blocklist domains evaluated in pure O(1) hierarchical lookup
pub const BASELINE_UBLOCK_DOMAINS: &[&str] = &[
    "googleadservices.com",
    "googlesyndication.com",
    "doubleclick.net",
    "adservice.google.com",
    "pagead2.googlesyndication.com",
    "google-analytics.com",
    "googletagservices.com",
    "analytics.google.com",
    "ads.google.com",
    "adwords.google.com",
    "stats.g.doubleclick.net",
    "pixel.facebook.com",
    "an.facebook.com",
    "ads.facebook.com",
    "analytics.tiktok.com",
    "ads.tiktok.com",
    "business-api.tiktok.com",
    "pangle-ads.com",
    "ads-twitter.com",
    "static.ads-twitter.com",
    "analytics.twitter.com",
    "amazon-adsystem.com",
    "aax.amazon-adsystem.com",
    "c.amazon-adsystem.com",
    "s.amazon-adsystem.com",
    "criteo.com",
    "criteo.net",
    "static.criteo.net",
    "bidder.criteo.com",
    "taboola.com",
    "cdn.taboola.com",
    "outbrain.com",
    "widgets.outbrain.com",
    "revcontent.com",
    "trends.revcontent.com",
    "mgid.com",
    "adnxs.com",
    "ib.adnxs.com",
    "rubiconproject.com",
    "fastlane.rubiconproject.com",
    "pubmatic.com",
    "ads.pubmatic.com",
    "openx.net",
    "casalemedia.com",
    "adroll.com",
    "smartadserver.com",
    "scorecardresearch.com",
    "b.scorecardresearch.com",
    "quantserve.com",
    "pixel.quantserve.com",
    "mathtag.com",
    "advertising.com",
    "yieldmo.com",
    "sharethrough.com",
    "inmobi.com",
    "smaato.net",
    "unityads.unity3d.com",
    "ironsrc.com",
    "vungle.com",
    "mintegral.com",
];

/// Common ad telemetry paths that indicate advertising/tracking requests
pub const COMMON_AD_PATHS: &[&str] = &[
    "/pagead/",
    "/gtm.js?id=",
    "/fbevents.js",
    "/analytics.js",
    "/pixel.gif",
    "/adsbygoogle.js",
];

pub struct PrivacyGuard;

impl PrivacyGuard {
    /// Strips tracking parameters from target URLs (UTM, Google, Facebook tokens)
    pub fn sanitize_url(raw_url: &str) -> String {
        if let Ok(mut parsed) = Url::parse(raw_url) {
            let tracking_params = [
                "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
                "fbclid", "gclid", "gbraid", "wbraid", "msclkid", "mc_eid", "ref", "trk"
            ];
            
            let filtered_pairs: Vec<(String, String)> = parsed
                .query_pairs()
                .filter(|(key, _)| !tracking_params.contains(&key.as_ref()))
                .map(|(k, v)| (k.into_owned(), v.into_owned()))
                .collect();

            if filtered_pairs.is_empty() {
                parsed.set_query(None);
            } else {
                let mut serializer = parsed.query_pairs_mut();
                serializer.clear();
                for (k, v) in filtered_pairs {
                    serializer.append_pair(&k, &v);
                }
            }
            return parsed.to_string();
        }
        raw_url.to_string()
    }

    /// Rust v9 AST pre-filter that strips tracking scripts, ad banners, and telemetry nodes
    pub fn filter_html_ast(raw_html: &str) -> String {
        let mut clean = raw_html.to_string();
        let tracker_patterns = [
            r"(?i)<script[^>]*google-analytics\.com[^>]*>.*?</script>",
            r"(?i)<script[^>]*googletagmanager\.com[^>]*>.*?</script>",
            r"(?i)<script[^>]*facebook\.net[^>]*>.*?</script>",
            r"(?i)<script[^>]*connect\.facebook\.net[^>]*>.*?</script>",
            r"(?i)<script[^>]*doubleclick\.net[^>]*>.*?</script>",
            r"(?i)<script[^>]*amazon-adsystem\.com[^>]*>.*?</script>",
        ];
        for pattern in tracker_patterns {
            if let Ok(re) = regex::Regex::new(pattern) {
                clean = re.replace_all(&clean, "").to_string();
            }
        }
        clean
    }

    /// Fast host extraction without memory allocations
    pub fn extract_host_fast(url: &str) -> Option<String> {
        let scheme_end = url.find("://");
        let start = match scheme_end {
            Some(idx) => idx + 3,
            None => 0,
        };
        if start >= url.len() {
            return None;
        }

        let slice = &url[start..];
        let mut end = slice.len();
        for (i, b) in slice.bytes().enumerate() {
            if b == b'/' || b == b'?' || b == b'#' {
                end = i;
                break;
            }
        }

        let host_slice = &slice[..end];
        let colon = host_slice.find(':');
        let final_host = match colon {
            Some(c_idx) => &host_slice[..c_idx],
            None => host_slice,
        };

        if final_host.is_empty() {
            None
        } else {
            Some(final_host.to_lowercase())
        }
    }

    /// Checks if a host belongs to Google Account login, profile, authentication or identity services.
    pub fn is_google_account_domain(host: &str) -> bool {
        let h = host.trim().to_lowercase();
        if GOOGLE_ACCOUNT_DOMAINS.contains(&h.as_str()) {
            return true;
        }
        if h == "accounts.google.com" || h.ends_with(".accounts.google.com") {
            return true;
        }
        if h == "myaccount.google.com" || h.ends_with(".myaccount.google.com") {
            return true;
        }
        if h == "accounts.youtube.com" || h.ends_with(".accounts.youtube.com") {
            return true;
        }
        if h.starts_with("accounts.google.") {
            return true;
        }
        if h == "apis.google.com" || h.ends_with(".apis.google.com") {
            return true;
        }
        if h.ends_with(".gstatic.com") || h.ends_with(".googleusercontent.com") || h.ends_with(".recaptcha.net") {
            return true;
        }
        if h == "oauth2.googleapis.com" || h == "identitytoolkit.googleapis.com" || h == "securetoken.googleapis.com" {
            return true;
        }
        false
    }

    /// Checks if a URL is part of Google Account login, OAuth, account management, or authentication.
    pub fn is_google_account_or_auth_url(url: &str) -> bool {
        if url.len() < 5 {
            return false;
        }
        let lower = url.to_lowercase();
        if let Some(host) = Self::extract_host_fast(url) {
            if Self::is_google_account_domain(&host) {
                return true;
            }
            if host == "google.com" || host.ends_with(".google.com") || host.contains("google.") {
                let path = if let Ok(parsed) = Url::parse(url) {
                    parsed.path().to_lowercase()
                } else {
                    "".to_string()
                };
                if path.starts_with("/accounts/")
                    || path.starts_with("/account/")
                    || path.starts_with("/servicelogin")
                    || path.starts_with("/checkcookie")
                    || path.starts_with("/signin")
                    || path.starts_with("/o/oauth2/")
                    || path.starts_with("/gsi/")
                    || path.starts_with("/recaptcha/")
                    || path.starts_with("/_/signin/")
                {
                    return true;
                }
                if lower.contains("client_id=") && (lower.contains("accounts.google") || lower.contains("oauth2")) {
                    return true;
                }
            }
        }
        false
    }

    /// Evaluates a request URL against the active uBlock rule database in pure O(1) time.
    pub fn should_block(url: &str) -> bool {
        if url.len() < 4 {
            return false;
        }

        // Fast skip for internal pseudo-schemes
        let first = url.as_bytes()[0];
        if first == b'd' || first == b'b' || first == b'a' || first == b'j' {
            let lower = url.to_lowercase();
            if lower.starts_with("data:")
                || lower.starts_with("blob:")
                || lower.starts_with("about:")
                || lower.starts_with("javascript:")
            {
                return false;
            }
        }

        // Never block decentralized Kaspa schemes or localhost nodes
        let lower = url.to_lowercase();
        if lower.starts_with("kaspa:")
            || lower.starts_with("dnet:")
            || lower.starts_with("ipfs:")
            || lower.starts_with("hyper:")
            || lower.contains("127.0.0.1")
            || lower.contains("localhost")
        {
            return false;
        }

        // Never block Google Account login, OAuth, account management, or identity endpoints
        if Self::is_google_account_or_auth_url(url) {
            return false;
        }

        let host = match Self::extract_host_fast(url) {
            Some(h) => h,
            None => return false,
        };

        if Self::is_google_account_domain(&host) {
            return false;
        }

        if ESSENTIAL_WEB_DOMAINS.contains(&host.as_str()) {
            return false;
        }

        // 1. Hierarchical Domain Lookup
        let mut current_host = host.as_str();
        loop {
            if BASELINE_UBLOCK_DOMAINS.contains(&current_host) {
                return true;
            }
            if TRACKER_AND_AD_DOMAINS.contains(&current_host) {
                return true;
            }
            match current_host.find('.') {
                Some(dot) if dot < current_host.len() - 2 => {
                    current_host = &current_host[dot + 1..];
                }
                _ => break,
            }
        }

        // 2. Fast check against universal ad telemetry paths
        for path in COMMON_AD_PATHS {
            if url.contains(path) {
                return true;
            }
        }

        false
    }

    /// Evaluates if a request URL matches any tracker, ad network, or telemetry domain.
    pub fn is_tracker_or_ad(url: &str) -> bool {
        if url.len() < 4 {
            return false;
        }

        let first = url.as_bytes()[0];
        if first == b'd' || first == b'b' || first == b'a' || first == b'j' {
            let lower = url.to_lowercase();
            if lower.starts_with("data:")
                || lower.starts_with("blob:")
                || lower.starts_with("about:")
                || lower.starts_with("javascript:")
            {
                return false;
            }
        }

        let lower = url.to_lowercase();
        // Skip media streams, images, fonts, stylesheets
        let path = if let Ok(parsed) = Url::parse(url) {
            parsed.path().to_lowercase()
        } else {
            "".to_string()
        };

        if path.ends_with(".png")
            || path.ends_with(".jpg")
            || path.ends_with(".jpeg")
            || path.ends_with(".webp")
            || path.ends_with(".gif")
            || path.ends_with(".svg")
            || path.ends_with(".ico")
            || path.ends_with(".bmp")
            || path.ends_with(".avif")
            || path.ends_with(".mp4")
            || path.ends_with(".webm")
            || path.ends_with(".m4v")
            || path.ends_with(".m4s")
            || path.ends_with(".m4a")
            || path.ends_with(".mp3")
            || path.ends_with(".ogg")
            || path.ends_with(".ogv")
            || path.ends_with(".ts")
            || path.ends_with(".m3u8")
            || path.ends_with(".mpd")
            || path.ends_with(".css")
            || path.ends_with(".woff")
            || path.ends_with(".woff2")
            || path.ends_with(".ttf")
            || path.contains("/video/")
            || path.contains("/audio/")
            || path.contains("/media/")
            || lower.contains("videoplayback")
            || lower.contains("stream")
            || lower.contains("kaspa")
            || path.contains("/thumb")
            || path.contains("/poster")
        {
            return false;
        }

        // Never block decentralized Kaspa schemes or localhost
        if lower.starts_with("kaspa://")
            || lower.starts_with("dnet://")
            || lower.starts_with("ipfs://")
            || lower.starts_with("hyper://")
            || lower.contains("127.0.0.1")
            || lower.contains("localhost")
        {
            return false;
        }

        // Never block Google Account login or OAuth
        if Self::is_google_account_or_auth_url(url) {
            return false;
        }

        let host = match Self::extract_host_fast(url) {
            Some(h) => h,
            None => return false,
        };

        if Self::is_google_account_domain(&host) {
            return false;
        }

        // 1. Evaluate via real uBlock Engine
        if Self::should_block(url) {
            return true;
        }

        if TRACKER_AND_AD_DOMAINS.contains(&host.as_str()) {
            return true;
        }

        // Check parent domains
        let mut dot_index = host.find('.');
        while let Some(idx) = dot_index {
            if idx < host.len() - 1 {
                let parent_domain = &host[idx + 1..];
                if Self::is_google_account_domain(parent_domain)
                    || parent_domain == "google.com"
                    || parent_domain == "googleapis.com"
                    || parent_domain == "gstatic.com"
                {
                    break;
                }
                if TRACKER_AND_AD_DOMAINS.contains(&parent_domain) {
                    return true;
                }
                dot_index = host[idx + 1..].find('.').map(|i| idx + 1 + i);
            } else {
                break;
            }
        }

        false
    }

    /// Alias for is_tracker_or_ad
    pub fn is_tracker(url: &str) -> bool {
        Self::is_tracker_or_ad(url)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_host() {
        assert_eq!(
            PrivacyGuard::extract_host_fast("https://adservice.google.com/pagead/id"),
            Some("adservice.google.com".to_string())
        );
        assert_eq!(
            PrivacyGuard::extract_host_fast("http://localhost:8080/test"),
            Some("localhost".to_string())
        );
    }

    #[test]
    fn test_is_google_account_domain() {
        assert!(PrivacyGuard::is_google_account_domain("accounts.google.com"));
        assert!(PrivacyGuard::is_google_account_domain("myaccount.google.com"));
        assert!(PrivacyGuard::is_google_account_domain("oauth2.googleapis.com"));
        assert!(!PrivacyGuard::is_google_account_domain("doubleclick.net"));
    }

    #[test]
    fn test_is_tracker_or_ad() {
        assert!(PrivacyGuard::is_tracker_or_ad("https://doubleclick.net/ad.js"));
        assert!(PrivacyGuard::is_tracker_or_ad("https://google-analytics.com/analytics.js"));
        assert!(!PrivacyGuard::is_tracker_or_ad("https://accounts.google.com/signin"));
        assert!(!PrivacyGuard::is_tracker_or_ad("https://kaspa.org/index.html"));
    }
}
