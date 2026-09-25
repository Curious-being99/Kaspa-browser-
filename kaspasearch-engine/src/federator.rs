use serde::{Deserialize, Serialize};
use std::collections::HashSet;
use tokio::task::JoinSet;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SearchResult {
    pub title: String,
    pub url: String,
    pub snippet: String,
    pub engine_source: String,
    pub is_verified_secure: bool,
}

pub struct FederatedSearchEngine {
    client: reqwest::Client,
}

impl FederatedSearchEngine {
    pub fn new() -> Self {
        let client = reqwest::Client::builder()
            .user_agent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
            .timeout(std::time::Duration::from_millis(3000))
            .build()
            .unwrap_or_default();

        Self { client }
    }

    /// Queries multiple web indexes asynchronously in parallel with zero tracking IP forwarding
    pub async fn search(&self, query: &str, _page: usize) -> Vec<SearchResult> {
        let mut set = JoinSet::new();
        let query_owned = query.to_string();

        let client_a = self.client.clone();
        let q_a = query_owned.clone();
        set.spawn(async move {
            Self::fetch_web_results(&client_a, &q_a).await
        });

        let client_b = self.client.clone();
        let q_b = query_owned.clone();
        set.spawn(async move {
            Self::fetch_bing_results(&client_b, &q_b).await
        });

        let client_c = self.client.clone();
        let q_c = query_owned.clone();
        set.spawn(async move {
            Self::fetch_wikipedia(&client_c, &q_c).await
        });

        let mut combined = Vec::new();
        let mut seen_urls = HashSet::new();

        while let Some(res) = set.join_next().await {
            if let Ok(results) = res {
                for item in results {
                    let sanitized = crate::privacy::PrivacyGuard::sanitize_url(&item.url);
                    if !seen_urls.contains(&sanitized) {
                        seen_urls.insert(sanitized.clone());
                        let mut clean_item = item;
                        clean_item.url = sanitized;
                        combined.push(clean_item);
                    }
                }
            }
        }

        combined
    }

    async fn fetch_web_results(client: &reqwest::Client, query: &str) -> Vec<SearchResult> {
        let url = format!("https://html.duckduckgo.com/html/?q={}", urlencoding::encode(query));
        let mut results = Vec::new();

        if let Ok(resp) = client.post(&url).header("User-Agent", "KaspaSearchBot/1.0").send().await {
            if let Ok(body) = resp.text().await {
                let document = scraper::Html::parse_document(&body);
                let result_selector = scraper::Selector::parse(".result").unwrap();
                let title_selector = scraper::Selector::parse(".result__title .result__a").unwrap();
                let snippet_selector = scraper::Selector::parse(".result__snippet").unwrap();

                for element in document.select(&result_selector) {
                    if let Some(title_el) = element.select(&title_selector).next() {
                        let title = title_el.text().collect::<Vec<_>>().join("");
                        let href = title_el.value().attr("href").unwrap_or("");
                        
                        let real_url = if let Some(idx) = href.find("uddg=") {
                            urlencoding::decode(&href[idx + 5..]).unwrap_or_default().to_string()
                        } else {
                            href.to_string()
                        };

                        let snippet = element
                            .select(&snippet_selector)
                            .next()
                            .map(|s| s.text().collect::<Vec<_>>().join(""))
                            .unwrap_or_default();

                        if !real_url.is_empty() && !title.is_empty() {
                            results.push(SearchResult {
                                title: title.trim().to_string(),
                                url: real_url.trim().to_string(),
                                snippet: snippet.trim().to_string(),
                                engine_source: "Web Index".to_string(),
                                is_verified_secure: real_url.starts_with("https://"),
                            });
                        }
                    }
                }
            }
        }
        results
    }

    async fn fetch_bing_results(client: &reqwest::Client, query: &str) -> Vec<SearchResult> {
        let url = format!("https://www.bing.com/search?q={}", urlencoding::encode(query));
        let mut results = Vec::new();

        if let Ok(resp) = client
            .get(&url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .send()
            .await
        {
            if let Ok(body) = resp.text().await {
                let document = scraper::Html::parse_document(&body);
                let result_selector = scraper::Selector::parse("li.b_algo").unwrap();
                let title_selector = scraper::Selector::parse("h2 a").unwrap();
                let snippet_selector = scraper::Selector::parse(".b_caption p, .b_lineclamp, p").unwrap();

                for element in document.select(&result_selector) {
                    if let Some(title_el) = element.select(&title_selector).next() {
                        let title = title_el.text().collect::<Vec<_>>().join("");
                        let href = title_el.value().attr("href").unwrap_or("");

                        let snippet = element
                            .select(&snippet_selector)
                            .next()
                            .map(|s| s.text().collect::<Vec<_>>().join(""))
                            .unwrap_or_default();

                        if !href.is_empty() && !title.is_empty() && href.starts_with("http") {
                            results.push(SearchResult {
                                title: title.trim().to_string(),
                                url: href.trim().to_string(),
                                snippet: snippet.trim().to_string(),
                                engine_source: "Bing Index".to_string(),
                                is_verified_secure: href.starts_with("https://"),
                            });
                        }
                    }
                }
            }
        }
        results
    }

    async fn fetch_wikipedia(client: &reqwest::Client, query: &str) -> Vec<SearchResult> {
        let url = format!(
            "https://en.wikipedia.org/w/api.php?action=opensearch&search={}&limit=3&namespace=0&format=json",
            urlencoding::encode(query)
        );
        let mut results = Vec::new();

        if let Ok(resp) = client.get(&url).send().await {
            if let Ok(json) = resp.json::<serde_json::Value>().await {
                if let (Some(titles), Some(snippets), Some(urls)) = (
                    json.get(1).and_then(|v| v.as_array()),
                    json.get(2).and_then(|v| v.as_array()),
                    json.get(3).and_then(|v| v.as_array()),
                ) {
                    for i in 0..titles.len() {
                        if let (Some(t), Some(s), Some(u)) = (
                            titles.get(i).and_then(|v| v.as_str()),
                            snippets.get(i).and_then(|v| v.as_str()),
                            urls.get(i).and_then(|v| v.as_str()),
                        ) {
                            if !s.is_empty() {
                                results.push(SearchResult {
                                    title: format!("{} — Wikipedia", t),
                                    url: u.to_string(),
                                    snippet: s.to_string(),
                                    engine_source: "Wikipedia Knowledge".to_string(),
                                    is_verified_secure: true,
                                });
                            }
                        }
                    }
                }
            }
        }
        results
    }
}
