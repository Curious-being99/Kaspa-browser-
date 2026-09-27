//! PWA Manifest & Web App Architecture Engine in Rust.
//!
//! Provides strict W3C Web App Manifest compliance checking, display mode resolution,
//! and asset icon selection matching Chrome / Edge standards.

#[derive(Debug, Clone)]
pub struct PwaManifest {
    pub name: String,
    pub short_name: String,
    pub start_url: String,
    pub display_mode: String,
    pub theme_color: Option<String>,
    pub background_color: Option<String>,
    pub best_icon_url: Option<String>,
    pub is_installable: bool,
}

#[derive(Debug, Clone)]
pub struct IconCandidate {
    pub src: String,
    pub sizes: Option<String>,
    pub mime_type: Option<String>,
    pub purpose: Option<String>,
}

/// Evaluates website installability based on W3C PWA standards
pub fn evaluate_pwa_compatibility(
    name: &str,
    start_url: &str,
    has_service_worker: bool,
    icons: &[IconCandidate],
) -> PwaManifest {
    let best_icon = select_best_icon(icons);
    let is_valid_name = !name.trim().is_empty();
    let is_valid_url = !start_url.trim().is_empty();
    let has_icon = best_icon.is_some();

    // Standard Chromium installability heuristics
    let is_installable = is_valid_name && is_valid_url && (has_service_worker || has_icon);

    PwaManifest {
        name: name.trim().to_string(),
        short_name: if name.len() > 12 { name[..12].trim().to_string() } else { name.trim().to_string() },
        start_url: start_url.trim().to_string(),
        display_mode: "standalone".to_string(),
        theme_color: None,
        background_color: None,
        best_icon_url: best_icon,
        is_installable,
    }
}

/// Selects the highest resolution maskable or any icon candidate for Android home screen
fn select_best_icon(icons: &[IconCandidate]) -> Option<String> {
    if icons.is_empty() {
        return None;
    }

    // Prefer maskable or >= 192x192 icons
    let mut best_score = -1;
    let mut best_url: Option<String> = None;

    for icon in icons {
        let mut score = 10;
        if let Some(ref purpose) = icon.purpose {
            if purpose.contains("maskable") {
                score += 50;
            }
        }
        if let Some(ref sizes) = icon.sizes {
            if sizes.contains("512x512") {
                score += 40;
            } else if sizes.contains("192x192") {
                score += 30;
            } else if sizes.contains("any") {
                score += 20;
            }
        }
        if let Some(ref mime) = icon.mime_type {
            if mime == "image/png" || mime == "image/webp" {
                score += 10;
            }
        }

        if score > best_score {
            best_score = score;
            best_url = Some(icon.src.clone());
        }
    }

    best_url
}
