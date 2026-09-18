package com.example.network

object KaspaReaderMode {
    /**
     * JavaScript to extract main content and format it for reader mode.
     * Encodes output using encodeURIComponent(JSON.stringify(...)) to avoid JSON parsing failures in Android evaluateJavascript.
     */
    const val JS_EXTRACT_CONTENT = """
        (function() {
            try {
                function getCleanText(el) {
                    return el ? (el.innerText || el.textContent || '').trim() : '';
                }

                // Identify main content element
                let mainEl = document.querySelector('article') || document.querySelector('main') || document.querySelector('[role="main"]');
                
                if (!mainEl) {
                    let bestScore = -1;
                    let bestEl = document.body;
                    const candidates = document.querySelectorAll('div, section, article, main');
                    candidates.forEach(el => {
                        const text = getCleanText(el);
                        if (text.length > 100) {
                            let score = text.length;
                            const tag = el.tagName.toLowerCase();
                            if (tag === 'article') score *= 3;
                            if (tag === 'main') score *= 2;
                            if (tag === 'section') score *= 1.5;
                            if (score > bestScore) {
                                bestScore = score;
                                bestEl = el;
                            }
                        }
                    });
                    mainEl = bestEl || document.body;
                }

                // Clone element to sanitize
                const clone = mainEl.cloneNode(true);
                const noiseSelectors = 'script, style, iframe, nav, footer, header, aside, .ad, .ads, .advertisement, .social-share, [role="banner"], [role="navigation"]';
                clone.querySelectorAll(noiseSelectors).forEach(n => n.remove());

                // Page Title
                let title = document.title || '';
                const h1 = document.querySelector('h1');
                if (h1 && h1.innerText.trim().length > 0) {
                    title = h1.innerText.trim();
                }

                // Lead Image
                let leadImg = '';
                const ogImg = document.querySelector('meta[property="og:image"]');
                if (ogImg && ogImg.getAttribute('content')) {
                    leadImg = ogImg.getAttribute('content');
                } else {
                    const firstImg = clone.querySelector('img');
                    if (firstImg && firstImg.src) {
                        leadImg = firstImg.src;
                    }
                }

                const contentHtml = clone.innerHTML || '';

                return encodeURIComponent(JSON.stringify({
                    title: title || 'Reader View',
                    leadImage: leadImg || '',
                    content: contentHtml
                }));
            } catch (err) {
                return encodeURIComponent(JSON.stringify({
                    title: document.title || 'Reader View',
                    leadImage: '',
                    content: document.body ? document.body.innerHTML : ''
                }));
            }
        })();
    """

    fun getReaderHtml(
        title: String,
        content: String,
        leadImage: String = "",
        theme: String = "dark",
        fontSizeSp: Int = 18
    ): String {
        val (bgColor, textColor, accentColor, cardBg, borderColor) = when (theme.lowercase()) {
            "sepia" -> listOf("#FBF0D9", "#3D2E1E", "#9A3412", "#F3E5AB", "#E2D1A6")
            "oled" -> listOf("#000000", "#F1F5F9", "#38BDF8", "#121212", "#27272A")
            "light" -> listOf("#FFFFFF", "#0F172A", "#0284C7", "#F1F5F9", "#E2E8F0")
            else -> listOf("#0F172A", "#E2E8F0", "#22D3EE", "#1E293B", "#334155") // "dark"
        }

        val leadImgHtml = if (leadImage.isNotBlank()) {
            """<img src="${leadImage}" class="lead-img" alt="Header Image" />"""
        } else ""

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>${title}</title>
                <style>
                    * { box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        line-height: 1.7;
                        font-size: ${fontSizeSp}px;
                        color: ${textColor};
                        background-color: ${bgColor};
                        padding: 24px 20px 80px 20px;
                        max-width: 820px;
                        margin: 0 auto;
                        word-break: break-word;
                    }
                    h1 {
                        color: ${accentColor};
                        font-size: 1.8em;
                        line-height: 1.3;
                        margin-bottom: 0.4em;
                        font-weight: 700;
                    }
                    .lead-img {
                        width: 100%;
                        max-height: 400px;
                        object-fit: cover;
                        border-radius: 12px;
                        margin: 1em 0;
                        border: 1px solid ${borderColor};
                    }
                    img {
                        max-width: 100%;
                        height: auto;
                        border-radius: 8px;
                        margin: 1.2em 0;
                    }
                    p { margin-bottom: 1.3em; }
                    a { color: ${accentColor}; text-decoration: underline; }
                    pre, code {
                        background: ${cardBg};
                        border: 1px solid ${borderColor};
                        padding: 12px;
                        border-radius: 8px;
                        overflow-x: auto;
                        font-family: monospace;
                        font-size: 0.9em;
                    }
                    blockquote {
                        border-left: 4px solid ${accentColor};
                        margin: 1.5em 0;
                        padding-left: 16px;
                        font-style: italic;
                    }
                    hr {
                        border: 0;
                        border-top: 1px solid ${borderColor};
                        margin: 24px 0;
                    }
                </style>
            </head>
            <body>
                <h1>${title}</h1>
                ${leadImgHtml}
                <hr>
                <div id="content">
                    ${content}
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}

