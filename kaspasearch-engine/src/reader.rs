use regex::Regex;
use scraper::{Html, Selector};
use serde::{Deserialize, Serialize};

/// Cleaned and extracted article representation for zero-jank native Reader Mode.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExtractedArticle {
    pub title: String,
    pub byline: Option<String>,
    pub lead_image: Option<String>,
    pub content_html: String,
    pub text_content: String,
    pub word_count: usize,
    pub reading_time_minutes: usize,
    pub has_video: bool,
    pub video_embeds: Vec<String>,
}

pub struct NativeReaderEngine;

impl NativeReaderEngine {
    /// Extracts clean article content, title, lead image, and media from raw HTML.
    /// Operates in sub-5ms time without injecting JavaScript into the WebView DOM.
    pub fn extract_article(raw_html: &str, base_url: Option<&str>) -> ExtractedArticle {
        if raw_html.trim().is_empty() {
            return ExtractedArticle {
                title: "Reader View".to_string(),
                byline: None,
                lead_image: None,
                content_html: "<p>No content available.</p>".to_string(),
                text_content: "No content available.".to_string(),
                word_count: 0,
                reading_time_minutes: 1,
                has_video: false,
                video_embeds: Vec::new(),
            };
        }

        let document = Html::parse_document(raw_html);

        // 1. Extract Page Title
        let title = Self::extract_title(&document);

        // 2. Extract Byline / Author
        let byline = Self::extract_byline(&document);

        // 3. Extract Lead Image
        let lead_image = Self::extract_lead_image(&document);

        // 4. Extract & Preserve Video Embeds (YouTube, Vimeo, Dailymotion)
        let video_embeds = Self::extract_video_embeds(raw_html);
        let has_video = !video_embeds.is_empty();

        // 5. Extract Core Article Content & Sanitize
        let (content_html, text_content) = Self::extract_and_sanitize_content(&document, raw_html, &video_embeds);

        // 6. Compute Word Count & Reading Time
        let word_count = text_content
            .split_whitespace()
            .filter(|w| !w.is_empty())
            .count();
        let reading_time_minutes = std::cmp::max(1, (word_count + 199) / 200);

        ExtractedArticle {
            title,
            byline,
            lead_image,
            content_html,
            text_content,
            word_count,
            reading_time_minutes,
            has_video,
            video_embeds,
        }
    }

    /// Generates a complete, beautiful, isolated Reader Mode HTML document
    pub fn generate_reader_html(
        article: &ExtractedArticle,
        theme: &str,
        font_size_sp: i32,
    ) -> String {
        let (bg_color, text_color, accent_color, card_bg, border_color) = match theme.to_lowercase().as_str() {
            "sepia" => ("#FBF0D9", "#3D2E1E", "#9A3412", "#F3E5AB", "#E2D1A6"),
            "oled" => ("#000000", "#F1F5F9", "#38BDF8", "#121212", "#27272A"),
            "light" => ("#FFFFFF", "#0F172A", "#0284C7", "#F1F5F9", "#E2E8F0"),
            _ => ("#0F172A", "#E2E8F0", "#22D3EE", "#1E293B", "#334155"), // "dark"
        };

        let lead_img_tag = if let Some(ref img_url) = article.lead_image {
            format!(r#"<img src="{}" class="lead-img" alt="Lead Image" />"#, img_url)
        } else {
            String::new()
        };

        let byline_tag = if let Some(ref author) = article.byline {
            format!(r#"<div class="byline">By {}</div>"#, author)
        } else {
            String::new()
        };

        let meta_stats = format!(
            r#"<div class="meta-row"><span>⏱ {} min read</span> • <span>{} words</span></div>"#,
            article.reading_time_minutes, article.word_count
        );

        format!(
            r#"<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{}</title>
    <style>
        * {{ box-sizing: border-box; }}
        body {{
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            line-height: 1.75;
            font-size: {}px;
            color: {};
            background-color: {};
            padding: 24px 20px 80px 20px;
            max-width: 820px;
            margin: 0 auto;
            word-break: break-word;
        }}
        h1 {{
            color: {};
            font-size: 1.85em;
            line-height: 1.3;
            margin-bottom: 0.3em;
            font-weight: 700;
        }}
        .byline {{
            font-size: 0.9em;
            opacity: 0.85;
            margin-bottom: 0.4em;
            font-weight: 500;
        }}
        .meta-row {{
            font-size: 0.8em;
            opacity: 0.65;
            margin-bottom: 1.2em;
            padding-bottom: 0.8em;
            border-bottom: 1px solid {};
        }}
        .lead-img {{
            width: 100%;
            max-height: 420px;
            object-fit: cover;
            border-radius: 12px;
            margin: 1em 0;
            border: 1px solid {};
        }}
        img {{
            max-width: 100%;
            height: auto;
            border-radius: 8px;
            margin: 1.2em 0;
        }}
        p {{ margin-bottom: 1.35em; }}
        a {{ color: {}; text-decoration: underline; }}
        pre, code {{
            background: {};
            border: 1px solid {};
            padding: 12px;
            border-radius: 8px;
            overflow-x: auto;
            font-family: monospace;
            font-size: 0.9em;
        }}
        blockquote {{
            border-left: 4px solid {};
            margin: 1.5em 0;
            padding: 8px 16px;
            background: {};
            border-radius: 0 8px 8px 0;
            font-style: italic;
        }}
        .video-container {{
            position: relative;
            padding-bottom: 56.25%;
            height: 0;
            overflow: hidden;
            max-width: 100%;
            margin: 1.5em 0;
            border-radius: 12px;
            border: 1px solid {};
        }}
        .video-container iframe {{
            position: absolute;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            border: 0;
        }}
    </style>
</head>
<body>
    <h1>{}</h1>
    {}
    {}
    {}
    <div class="article-content">
        {}
    </div>
</body>
</html>"#,
            article.title,
            font_size_sp,
            text_color,
            bg_color,
            accent_color,
            border_color,
            border_color,
            accent_color,
            card_bg,
            border_color,
            accent_color,
            card_bg,
            border_color,
            article.title,
            byline_tag,
            meta_stats,
            lead_img_tag,
            article.content_html
        )
    }

    fn extract_title(doc: &Html) -> String {
        // 1. Meta OpenGraph Title
        if let Ok(og_selector) = Selector::parse(r#"meta[property="og:title"]"#) {
            if let Some(el) = doc.select(&og_selector).next() {
                if let Some(content) = el.value().attr("content") {
                    let trimmed = content.trim();
                    if !trimmed.is_empty() {
                        return trimmed.to_string();
                    }
                }
            }
        }

        // 2. Main <h1>
        if let Ok(h1_selector) = Selector::parse("h1") {
            if let Some(el) = doc.select(&h1_selector).next() {
                let text = el.text().collect::<Vec<_>>().join(" ").trim().to_string();
                if !text.is_empty() {
                    return text;
                }
            }
        }

        // 3. Document <title>
        if let Ok(title_selector) = Selector::parse("title") {
            if let Some(el) = doc.select(&title_selector).next() {
                let text = el.text().collect::<Vec<_>>().join(" ").trim().to_string();
                if !text.is_empty() {
                    return text;
                }
            }
        }

        "Reader View".to_string()
    }

    fn extract_byline(doc: &Html) -> Option<String> {
        let author_selectors = [
            r#"meta[name="author"]"#,
            r#"meta[property="article:author"]"#,
            r#"[rel="author"]"#,
            r#".byline"#,
            r#".author"#,
            r#".post-author"#,
        ];

        for sel_str in author_selectors {
            if let Ok(sel) = Selector::parse(sel_str) {
                if let Some(el) = doc.select(&sel).next() {
                    if let Some(content) = el.value().attr("content") {
                        let trimmed = content.trim();
                        if !trimmed.is_empty() {
                            return Some(trimmed.to_string());
                        }
                    }
                    let text = el.text().collect::<Vec<_>>().join(" ").trim().to_string();
                    if !text.is_empty() && text.len() < 80 {
                        return Some(text);
                    }
                }
            }
        }
        None
    }

    fn extract_lead_image(doc: &Html) -> Option<String> {
        let img_selectors = [
            r#"meta[property="og:image"]"#,
            r#"meta[name="twitter:image"]"#,
            r#"article img"#,
            r#"main img"#,
        ];

        for sel_str in img_selectors {
            if let Ok(sel) = Selector::parse(sel_str) {
                if let Some(el) = doc.select(&sel).next() {
                    if let Some(content) = el.value().attr("content") {
                        let trimmed = content.trim();
                        if trimmed.starts_with("http") {
                            return Some(trimmed.to_string());
                        }
                    }
                    if let Some(src) = el.value().attr("src") {
                        let trimmed = src.trim();
                        if trimmed.starts_with("http") && !trimmed.contains("icon") && !trimmed.contains("avatar") {
                            return Some(trimmed.to_string());
                        }
                    }
                }
            }
        }
        None
    }

    /// Extracts valid YouTube, Vimeo, Dailymotion embed iframes or video players
    fn extract_video_embeds(raw_html: &str) -> Vec<String> {
        let mut embeds = Vec::new();
        let iframe_regex = Regex::new(r#"(?i)<iframe[^>]+src=["']([^"']+)["'][^>]*>.*?</iframe>"#).unwrap();

        for cap in iframe_regex.captures_iter(raw_html) {
            let src = &cap[1];
            if src.contains("youtube.com")
                || src.contains("youtube-nocookie.com")
                || src.contains("youtu.be")
                || src.contains("vimeo.com")
                || src.contains("dailymotion.com")
            {
                embeds.push(cap[0].to_string());
            }
        }
        embeds
    }

    /// Extracts clean article markup and strips ads, telemetry, and tracking scripts
    fn extract_and_sanitize_content(
        doc: &Html,
        raw_html: &str,
        video_embeds: &[String],
    ) -> (String, String) {
        // 1. Candidate selection: <article>, <main>, [role="main"]
        let candidate_selectors = ["article", "main", r#"[role="main"]"#, ".post-content", ".article-body"];
        let mut best_html = String::new();

        for sel_str in candidate_selectors {
            if let Ok(sel) = Selector::parse(sel_str) {
                if let Some(el) = doc.select(&sel).next() {
                    let inner = el.html();
                    if inner.len() > 200 {
                        best_html = inner;
                        break;
                    }
                }
            }
        }

        if best_html.is_empty() {
            // Fallback: extract body or raw html
            if let Ok(body_sel) = Selector::parse("body") {
                if let Some(body_el) = doc.select(&body_sel).next() {
                    best_html = body_el.html();
                }
            }
            if best_html.is_empty() {
                best_html = raw_html.to_string();
            }
        }

        // 2. Strip Noise Patterns (Scripts, Ads, Tracking, Navigation, Comments)
        let noise_patterns = [
            r"(?is)<script[^>]*>.*?</script>",
            r"(?is)<style[^>]*>.*?</style>",
            r"(?is)<noscript[^>]*>.*?</noscript>",
            r"(?is)<nav[^>]*>.*?</nav>",
            r"(?is)<footer[^>]*>.*?</footer>",
            r"(?is)<header[^>]*>.*?</header>",
            r"(?is)<aside[^>]*>.*?</aside>",
            r"(?is)<form[^>]*>.*?</form>",
            r#"(?is)<div[^>]*class=["'][^"']*\b(ad|ads|advertisement|banner|social-share|cookie-consent|sidebar|popup)\b[^"']*["'][^>]*>.*?</div>"#,
            r#"(?is)<div[^>]*id=["'][^"']*\b(ad|ads|banner|sidebar|popup)\b[^"']*["'][^>]*>.*?</div>"#,
        ];

        let mut sanitized = best_html;
        for pattern in noise_patterns {
            if let Ok(re) = Regex::new(pattern) {
                sanitized = re.replace_all(&sanitized, "").to_string();
            }
        }

        // 3. Remove all non-video iframes
        let non_video_iframe_re = Regex::new(r#"(?is)<iframe[^>]*>.*?</iframe>"#).unwrap();
        sanitized = non_video_iframe_re.replace_all(&sanitized, |caps: &regex::Captures| {
            let matched = &caps[0];
            if matched.contains("youtube.com")
                || matched.contains("youtube-nocookie.com")
                || matched.contains("youtu.be")
                || matched.contains("vimeo.com")
                || matched.contains("dailymotion.com")
            {
                format!(r#"<div class="video-container">{}</div>"#, matched)
            } else {
                String::new()
            }
        }).to_string();

        // 4. Inject preserved top-level videos if not already in content
        if !video_embeds.is_empty() {
            for v in video_embeds {
                if !sanitized.contains(v) {
                    sanitized.push_str(&format!(r#"<div class="video-container">{}</div>"#, v));
                }
            }
        }

        // 5. Extract plain text for word count
        let tag_strip_re = Regex::new(r"<[^>]+>").unwrap();
        let plain_text = tag_strip_re.replace_all(&sanitized, " ").to_string();
        let clean_text = plain_text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">");

        (sanitized, clean_text.trim().to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_article_basic() {
        let html = r#"
            <!DOCTYPE html>
            <html>
            <head>
                <title>Test Page Title</title>
                <meta property="og:title" content="OpenGraph Article Title" />
                <meta name="author" content="Alice Satoshi" />
                <meta property="og:image" content="https://example.com/lead.jpg" />
            </head>
            <body>
                <header>Navigation and Site Header</header>
                <article>
                    <h1>OpenGraph Article Title</h1>
                    <p>This is the first paragraph of a high performance reader mode article.</p>
                    <p>Here is another paragraph containing important information for the reader.</p>
                    <iframe src="https://www.youtube.com/embed/dQw4w9WgXcQ"></iframe>
                    <div class="ad-banner">Ad that should be removed</div>
                </article>
                <footer>Footer text</footer>
            </body>
            </html>
        "#;

        let article = NativeReaderEngine::extract_article(html, None);
        assert_eq!(article.title, "OpenGraph Article Title");
        assert_eq!(article.byline, Some("Alice Satoshi".to_string()));
        assert_eq!(article.lead_image, Some("https://example.com/lead.jpg".to_string()));
        assert!(article.has_video);
        assert!(article.content_html.contains("youtube.com/embed/dQw4w9WgXcQ"));
        assert!(!article.content_html.contains("Ad that should be removed"));
        assert!(!article.content_html.contains("Navigation and Site Header"));
        assert!(article.word_count > 10);
        assert_eq!(article.reading_time_minutes, 1);
    }

    #[test]
    fn test_generate_reader_html() {
        let article = ExtractedArticle {
            title: "Super Fast Browser Article".to_string(),
            byline: Some("Kaspa Team".to_string()),
            lead_image: Some("https://kaspa.org/banner.jpg".to_string()),
            content_html: "<p>Article text goes here.</p>".to_string(),
            text_content: "Article text goes here.".to_string(),
            word_count: 4,
            reading_time_minutes: 1,
            has_video: false,
            video_embeds: Vec::new(),
        };

        let html = NativeReaderEngine::generate_reader_html(&article, "dark", 18);
        assert!(html.contains("Super Fast Browser Article"));
        assert!(html.contains("Kaspa Team"));
        assert!(html.contains("https://kaspa.org/banner.jpg"));
        assert!(html.contains("18px"));
    }
}
