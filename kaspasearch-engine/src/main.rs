use axum::{
    extract::{Query, State},
    response::{Html, IntoResponse, Json},
    routing::get,
    Router,
};
use serde::{Deserialize, Serialize};
use std::sync::Arc;

mod federator;
mod privacy;

#[derive(Deserialize)]
struct SearchParams {
    q: Option<String>,
    page: Option<usize>,
}

#[derive(Serialize)]
struct SearchApiResponse {
    query: String,
    took_ms: u128,
    total: usize,
    results: Vec<federator::SearchResult>,
}

struct AppState {
    engine: federator::FederatedSearchEngine,
}

#[tokio::main]
async fn main() {
    let state = Arc::new(AppState {
        engine: federator::FederatedSearchEngine::new(),
    });

    let app = Router::new()
        .route("/", get(search_ui_handler))
        .route("/search", get(search_ui_handler))
        .route("/api/v1/search", get(search_api_handler))
        .route("/opensearch.xml", get(opensearch_handler))
        .with_state(state);

    let addr = "0.0.0.0:8080";
    println!("🚀 KaspaSearch Rust Privacy Engine listening on http://{}", addr);
    let listener = tokio::net::TcpListener::bind(addr).await.unwrap();
    axum::serve(listener, app).await.unwrap();
}

async fn search_api_handler(
    State(state): State<Arc<AppState>>,
    Query(params): Query<SearchParams>,
) -> impl IntoResponse {
    let start = std::time::Instant::now();
    let query = params.q.unwrap_or_default();
    let page = params.page.unwrap_or(1);

    if query.trim().is_empty() {
        return Json(SearchApiResponse {
            query,
            took_ms: 0,
            total: 0,
            results: vec![],
        });
    }

    let results = state.engine.search(&query, page).await;
    let took_ms = start.elapsed().as_millis();

    Json(SearchApiResponse {
        total: results.len(),
        took_ms,
        query,
        results,
    })
}

async fn search_ui_handler(
    State(state): State<Arc<AppState>>,
    Query(params): Query<SearchParams>,
) -> impl IntoResponse {
    let query = params.q.unwrap_or_default();
    let mut results_html = String::new();

    if !query.trim().is_empty() {
        let results = state.engine.search(&query, 1).await;
        for r in results {
            results_html.push_str(&format!(
                r#"<article class="result">
                    <span class="url">{}</span>
                    <h2><a href="{}" rel="noreferrer noopener">{}</a></h2>
                    <p>{}</p>
                </article>"#,
                html_escape(&r.url),
                html_escape(&r.url),
                html_escape(&r.title),
                html_escape(&r.snippet)
            ));
        }
    }

    Html(format!(
        r#"<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>KaspaSearch — Privacy First Rust Search Engine</title>
    <style>
        :root {{ --bg: #0C0D10; --surface: #14161C; --text: #ECEFF1; --accent: #70C7BA; --cyan: #00F5D4; }}
        body {{ background: var(--bg); color: var(--text); font-family: system-ui, -apple-system, sans-serif; margin: 0; padding: 20px; }}
        .header {{ max-width: 800px; margin: 20px auto; text-align: center; }}
        .logo {{ color: var(--accent); font-size: 28px; font-weight: bold; text-decoration: none; }}
        .search-box {{ display: flex; max-width: 700px; margin: 20px auto; background: var(--surface); border-radius: 24px; border: 1px solid #282C37; padding: 8px 16px; }}
        .search-box input {{ flex: 1; background: transparent; border: none; outline: none; color: #fff; font-size: 16px; }}
        .search-box button {{ background: var(--accent); color: #0C0D10; border: none; border-radius: 16px; padding: 8px 20px; font-weight: bold; cursor: pointer; }}
        .results {{ max-width: 700px; margin: 30px auto; }}
        .result {{ background: var(--surface); border: 1px solid #282C37; border-radius: 12px; padding: 16px; margin-bottom: 16px; }}
        .result .url {{ font-size: 12px; color: var(--cyan); word-break: break-all; }}
        .result h2 {{ margin: 6px 0; font-size: 18px; }}
        .result h2 a {{ color: #70C7BA; text-decoration: none; }}
        .result h2 a:hover {{ text-decoration: underline; }}
        .result p {{ margin: 0; font-size: 14px; color: #94A3B8; line-height: 1.5; }}
    </style>
</head>
<body>
    <div class="header">
        <a href="/" class="logo">⚡ KASPA SEARCH (RUST)</a>
        <p style="color: #64748B; font-size: 13px;">Zero Logs | Zero IP Tracking | High-Speed Async Tokio Engine</p>
    </div>
    <form class="search-box" action="/search" method="GET">
        <input type="text" name="q" value="{}" placeholder="Search the web with privacy..." autofocus>
        <button type="submit">Search</button>
    </form>
    <div class="results">
        {}
    </div>
</body>
</html>"#,
        html_escape(&query),
        results_html
    ))
}

async fn opensearch_handler() -> impl IntoResponse {
    let xml = r#"<?xml version="1.0" encoding="UTF-8"?>
<OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
    <ShortName>KaspaSearch</ShortName>
    <Description>Privacy First Decentralized Search</Description>
    <InputEncoding>UTF-8</InputEncoding>
    <Url type="text/html" template="http://localhost:8080/search?q={searchTerms}"/>
</OpenSearchDescription>"#;
    ([(axum::http::header::CONTENT_TYPE, "application/opensearchdescription+xml")], xml)
}

fn html_escape(s: &str) -> String {
    s.replace('&', "&amp;").replace('<', "&lt;").replace('>', "&gt;").replace('"', "&quot;")
}
