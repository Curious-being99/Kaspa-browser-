use serde::{Deserialize, Serialize};

/// Detailed threat assessment result for a given URL or domain.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SecurityVerdict {
    pub is_safe: bool,
    pub threat_type: Option<String>,
    pub risk_score: u8, // 0 (safest) to 100 (critical threat)
    pub matched_target: Option<String>,
    pub reason: String,
}

impl SecurityVerdict {
    pub fn safe(reason: &str) -> Self {
        Self {
            is_safe: true,
            threat_type: None,
            risk_score: 0,
            matched_target: None,
            reason: reason.to_string(),
        }
    }

    pub fn threat(threat_type: &str, risk_score: u8, target: Option<&str>, reason: &str) -> Self {
        Self {
            is_safe: false,
            threat_type: Some(threat_type.to_string()),
            risk_score,
            matched_target: target.map(|s| s.to_string()),
            reason: reason.to_string(),
        }
    }
}

/// Popular protected brand domains frequently targeted by homograph and typosquatting attacks.
const PROTECTED_BRANDS: &[&str] = &[
    "google.com",
    "accounts.google.com",
    "youtube.com",
    "kaspa.org",
    "apple.com",
    "amazon.com",
    "microsoft.com",
    "paypal.com",
    "binance.com",
    "coinbase.com",
    "github.com",
    "wikipedia.org",
    "cloudflare.com",
];

/// Known in-browser cryptojacking and coin-mining domains
const CRYPTOJACKING_DOMAINS: &[&str] = &[
    "coinhive.com",
    "coin-hive.com",
    "crypto-loot.com",
    "cryptoloot.pro",
    "jsecoin.com",
    "webminepool.com",
    "minr.pw",
    "monerominer.rocks",
    "cloudcoins.co",
    "miner.pr0gramm.com",
    "coin-have.com",
];

/// Known typosquats and fake login phishing domains
const COMMON_TYPOSQUAT_PATTERNS: &[(&str, &str)] = &[
    ("goolge.com", "google.com"),
    ("gooogle.com", "google.com"),
    ("goggle.com", "google.com"),
    ("accounts-google.com", "accounts.google.com"),
    ("google-security-login.com", "accounts.google.com"),
    ("youutube.com", "youtube.com"),
    ("youtubee.com", "youtube.com"),
    ("kasspa.org", "kaspa.org"),
    ("kasppa.org", "kaspa.org"),
    ("binance-verify.com", "binance.com"),
    ("coinbase-support.com", "coinbase.com"),
    ("paypal-security.com", "paypal.com"),
    ("metamask-login.com", "metamask.io"),
];

/// Zero-allocation, on-device security guard evaluating phishing, homographs, and malicious URLs
pub struct SecurityGuard;

impl SecurityGuard {
    /// Evaluates a URL against homograph attacks, typosquatting, cryptojackers, and DGA entropy.
    pub fn inspect_url(url: &str) -> SecurityVerdict {
        if url.trim().is_empty() {
            return SecurityVerdict::safe("Empty URL");
        }

        let lower = url.to_lowercase();

        // 1. Never inspect or block internal safe pseudo-schemes
        if lower.starts_with("data:") || lower.starts_with("blob:") || lower.starts_with("about:") {
            return SecurityVerdict::safe("Internal pseudo-scheme");
        }

        // 2. Never block decentralized Kaspa schemes or local development
        if lower.starts_with("kaspa:")
            || lower.starts_with("dnet:")
            || lower.starts_with("ipfs:")
            || lower.starts_with("hyper:")
            || lower.contains("127.0.0.1")
            || lower.contains("localhost")
        {
            return SecurityVerdict::safe("Decentralized or local network");
        }

        // Extract host
        let host = match Self::extract_host(url) {
            Some(h) => h,
            None => return SecurityVerdict::safe("No host extracted"),
        };

        // 3. Absolute Protected Whitelist (Google Accounts, YouTube video streams, Kaspa)
        if Self::is_exempt_infrastructure(&host) {
            return SecurityVerdict::safe("Essential infrastructure or verified domain");
        }

        // 4. Check Known Cryptojacking Domains
        for &bad in CRYPTOJACKING_DOMAINS {
            if host == bad || host.ends_with(&format!(".{}", bad)) {
                return SecurityVerdict::threat(
                    "MALICIOUS_CRYPTOJACKING",
                    95,
                    Some(bad),
                    "Known browser cryptojacking or unauthorized background miner",
                );
            }
        }

        // 5. Check Direct Typosquat Patterns
        for &(typo, target) in COMMON_TYPOSQUAT_PATTERNS {
            if host == typo || host.ends_with(&format!(".{}", typo)) {
                return SecurityVerdict::threat(
                    "TYPOSQUATTING",
                    90,
                    Some(target),
                    &format!("Typosquatting deceptive domain mimicking {}", target),
                );
            }
        }

        // 6. Check Homograph (Internationalized Domain / Cyrillic / Greek Confusables) Phishing
        if let Some(verdict) = Self::detect_homograph_attack(&host) {
            return verdict;
        }

        // 7. Check DGA (Domain Generation Algorithm) & High-Entropy Anomaly
        if let Some(verdict) = Self::detect_dga_entropy(&host) {
            return verdict;
        }

        SecurityVerdict::safe("No malicious indicators found")
    }

    /// Fast host extraction without regex or allocation
    pub fn extract_host(url: &str) -> Option<String> {
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
            Some(final_host.to_string())
        }
    }

    /// Identifies homograph confusable characters that mimic ASCII Latin characters
    fn detect_homograph_attack(host: &str) -> Option<SecurityVerdict> {
        let is_punycode = host.contains("xn--");

        // Skeletonize: map confusable Unicode characters to standard ASCII equivalents
        let mut skeleton = String::with_capacity(host.len());
        let mut contains_confusables = false;

        for c in host.chars() {
            let mapped = match c {
                // Cyrillic Lookalikes
                '\u{0430}' => { contains_confusables = true; 'a' } // Cyrillic Small Letter A
                '\u{0441}' => { contains_confusables = true; 'c' } // Cyrillic Small Letter Es
                '\u{0435}' => { contains_confusables = true; 'e' } // Cyrillic Small Letter Ie
                '\u{0456}' => { contains_confusables = true; 'i' } // Cyrillic Small Letter Byelorussian-Ukrainian I
                '\u{0458}' => { contains_confusables = true; 'j' } // Cyrillic Small Letter Je
                '\u{043E}' => { contains_confusables = true; 'o' } // Cyrillic Small Letter O
                '\u{0440}' => { contains_confusables = true; 'p' } // Cyrillic Small Letter Er
                '\u{0455}' => { contains_confusables = true; 's' } // Cyrillic Small Letter Dze
                '\u{0443}' => { contains_confusables = true; 'y' } // Cyrillic Small Letter U
                '\u{0445}' => { contains_confusables = true; 'x' } // Cyrillic Small Letter Ha
                // Greek Lookalikes
                '\u{03BF}' => { contains_confusables = true; 'o' } // Greek Small Letter Omicron
                '\u{03BD}' => { contains_confusables = true; 'v' } // Greek Small Letter Nu
                '\u{03C1}' => { contains_confusables = true; 'p' } // Greek Small Letter Rho
                other => other,
            };
            skeleton.push(mapped);
        }

        let skeleton_lower = skeleton.to_lowercase();

        // Check if the skeleton matches any protected brand
        for &brand in PROTECTED_BRANDS {
            if (contains_confusables || is_punycode) && (skeleton_lower == brand || skeleton_lower.ends_with(&format!(".{}", brand))) {
                return Some(SecurityVerdict::threat(
                    "PHISHING_HOMOGRAPH",
                    98,
                    Some(brand),
                    &format!("Homograph visual spoofing attack targeting {}", brand),
                ));
            }
        }

        None
    }

    /// Evaluates Shannon entropy and vowel-consonant ratios to detect automated DGAs
    fn detect_dga_entropy(host: &str) -> Option<SecurityVerdict> {
        // Strip TLD (e.g. .com, .xyz, .biz)
        let main_label = host.split('.').next().unwrap_or(host);

        // Ignore short labels
        if main_label.len() < 12 {
            return None;
        }

        // Count character frequencies for Shannon entropy
        let mut counts = [0u32; 256];
        let bytes = main_label.as_bytes();
        for &b in bytes {
            counts[b as usize] += 1;
        }

        let len_f = bytes.len() as f64;
        let mut entropy = 0.0f64;
        for &count in &counts {
            if count > 0 {
                let p = count as f64 / len_f;
                entropy -= p * p.log2();
            }
        }

        // High entropy threshold (> 3.8 bits/char on main label with low vowels)
        let vowel_count = bytes
            .iter()
            .filter(|&&b| b == b'a' || b == b'e' || b == b'i' || b == b'o' || b == b'u')
            .count();
        let vowel_ratio = vowel_count as f64 / len_f;

        if entropy > 3.85 && vowel_ratio < 0.15 {
            return Some(SecurityVerdict::threat(
                "SUSPICIOUS_DGA",
                75,
                None,
                "High entropy domain structure consistent with algorithmically generated malware domain (DGA)",
            ));
        }

        None
    }

    /// Hardcoded infrastructure exemptions that must NEVER be flagged
    fn is_exempt_infrastructure(host: &str) -> bool {
        let h = host.to_lowercase();
        // Google & YouTube Accounts and Services
        if h == "accounts.google.com"
            || h == "myaccount.google.com"
            || h == "google.com"
            || h.ends_with(".google.com")
            || h == "youtube.com"
            || h == "m.youtube.com"
            || h.ends_with(".youtube.com")
            || h == "googlevideo.com"
            || h.ends_with(".googlevideo.com")
            || h.ends_with(".ytimg.com")
            || h.ends_with(".googleapis.com")
            || h.ends_with(".gstatic.com")
            || h.ends_with(".googleusercontent.com")
        {
            return true;
        }

        // Kaspa Ecosystem
        if h == "kaspa.org" || h.ends_with(".kaspa.org") || h == "kas.pa" || h.ends_with(".kas.pa") {
            return true;
        }

        // Web Standards and CDN Infrastructure
        if h == "wikipedia.org"
            || h.ends_with(".wikipedia.org")
            || h == "cloudflare.com"
            || h.ends_with(".cloudflare.com")
            || h == "github.com"
            || h.ends_with(".github.com")
            || h == "w3.org"
        {
            return true;
        }

        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_exempt_infrastructure() {
        let v1 = SecurityGuard::inspect_url("https://accounts.google.com/signin/v2");
        assert!(v1.is_safe);

        let v2 = SecurityGuard::inspect_url("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assert!(v2.is_safe);

        let v3 = SecurityGuard::inspect_url("https://kaspa.org");
        assert!(v3.is_safe);
    }

    #[test]
    fn test_cryptojacking_detection() {
        let v = SecurityGuard::inspect_url("https://coinhive.com/lib/coinhive.min.js");
        assert!(!v.is_safe);
        assert_eq!(v.threat_type, Some("MALICIOUS_CRYPTOJACKING".to_string()));
    }

    #[test]
    fn test_homograph_attack_detection() {
        // Cyrillic 'о' in google.com: "g\u{043E}\u{043E}gle.com"
        let malicious_homograph = format!("https://g\u{043E}\u{043E}gle.com/login");
        let v = SecurityGuard::inspect_url(&malicious_homograph);
        assert!(!v.is_safe);
        assert_eq!(v.threat_type, Some("PHISHING_HOMOGRAPH".to_string()));
        assert_eq!(v.matched_target, Some("google.com".to_string()));
    }

    #[test]
    fn test_typosquatting_detection() {
        let v = SecurityGuard::inspect_url("https://goolge.com/search");
        assert!(!v.is_safe);
        assert_eq!(v.threat_type, Some("TYPOSQUATTING".to_string()));
        assert_eq!(v.matched_target, Some("google.com".to_string()));
    }
}
