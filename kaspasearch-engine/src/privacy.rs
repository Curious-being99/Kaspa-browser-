use url::Url;

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
}
