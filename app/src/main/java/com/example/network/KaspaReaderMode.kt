package com.example.network

object KaspaReaderMode {
    /**
     * JavaScript to extract main content and format it for reader mode.
     * Based on a simplified version of Readability.js logic.
     */
    const val JS_EXTRACT_CONTENT = """
        (function() {
            function getCleanText(el) {
                return el.innerText.trim();
            }

            function scoreElement(el) {
                let score = 0;
                const tagName = el.tagName.toLowerCase();
                if (tagName === 'div') score += 5;
                if (tagName === 'article') score += 20;
                if (tagName === 'section') score += 10;
                
                const text = getCleanText(el);
                if (text.length > 200) score += Math.floor(text.length / 100);
                
                return score;
            }

            let bestElement = document.body;
            let maxScore = 0;

            const candidates = document.querySelectorAll('div, article, section, main');
            candidates.forEach(el => {
                const score = scoreElement(el);
                if (score > maxScore) {
                    maxScore = score;
                    bestElement = el;
                }
            });

            const title = document.title || "";
            const content = bestElement.innerHTML;
            
            return JSON.stringify({
                title: title,
                content: content
            });
        })();
    """

    fun getReaderHtml(title: String, content: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>${title}</title>
                <style>
                    body {
                        font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        line-height: 1.6;
                        color: #E2E8F0;
                        background-color: #0F172A;
                        padding: 20px;
                        max-width: 800px;
                        margin: 0 auto;
                    }
                    h1 { color: #22D3EE; font-size: 1.8em; margin-bottom: 0.5em; }
                    img { max-width: 100%; height: auto; border-radius: 8px; margin: 1em 0; }
                    p { margin-bottom: 1.2em; }
                    a { color: #22D3EE; text-decoration: none; }
                    pre { background: #1E293B; padding: 15px; border-radius: 6px; overflow-x: auto; }
                    code { font-family: monospace; }
                </style>
            </head>
            <body>
                <h1>${title}</h1>
                <hr style="border: 0; border-top: 1px solid #334155; margin: 20px 0;">
                <div id="content">
                    ${content}
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
