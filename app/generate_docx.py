import os
import zipfile

content_types_xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

rels_xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

document_rels_xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

styles_xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/>
        <w:sz w:val="22"/>
        <w:color w:val="222222"/>
      </w:rPr>
    </w:rPrDefault>
  </w:docDefaults>
</w:styles>"""

document_xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p>
      <w:pPr>
        <w:jc w:val="center"/>
        <w:spacing w:before="240" w:after="120"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:b/>
          <w:sz w:val="36"/>
          <w:color w:val="1A73E8"/>
        </w:rPr>
        <w:t>Kaspa Browser: Desktop Site Architecture Specification</w:t>
      </w:r>
    </w:p>
    
    <w:p>
      <w:pPr>
        <w:spacing w:before="120" w:after="240"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:i/>
          <w:color w:val="555555"/>
        </w:rPr>
        <w:t>Technical Architecture, Request Handling &amp; Hardware Compatibility Model</w:t>
      </w:r>
    </w:p>

    <w:p>
      <w:pPr>
        <w:spacing w:before="200" w:after="100"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:b/>
          <w:sz w:val="26"/>
          <w:color w:val="0D47A1"/>
        </w:rPr>
        <w:t>1. Executive Summary &amp; Core Philosophy</w:t>
      </w:r>
    </w:p>

    <w:p>
      <w:pPr>
        <w:spacing w:after="120"/>
      </w:pPr>
      <w:r>
        <w:t>On Android, "Desktop Site" is an identification, presentation, and viewport-scaling switch rather than a simulated hardware emulator. The browser requests and displays the desktop-oriented layout while preserving the Android device's native touch gestures, GPU acceleration, screen metrics, and Chromium rendering pipeline intact.</w:t>
      </w:r>
    </w:p>

    <w:p>
      <w:pPr>
        <w:spacing w:before="200" w:after="100"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:b/>
          <w:sz w:val="26"/>
          <w:color w:val="0D47A1"/>
        </w:rPr>
        <w:t>2. Architectural Flow Diagram</w:t>
      </w:r>
    </w:p>

    <w:p>
      <w:pPr>
        <w:pBdr>
          <w:top w:val="single" w:sz="4" w:space="4" w:color="CCCCCC"/>
          <w:left w:val="single" w:sz="4" w:space="4" w:color="CCCCCC"/>
          <w:bottom w:val="single" w:sz="4" w:space="4" w:color="CCCCCC"/>
          <w:right w:val="single" w:sz="4" w:space="4" w:color="CCCCCC"/>
        </w:pBdr>
        <w:shd w:val="clear" w:color="auto" w:fill="F8F9FA"/>
        <w:spacing w:before="120" w:after="120"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:rFonts w:ascii="Courier New" w:hAnsi="Courier New" w:cs="Courier New"/>
          <w:sz w:val="18"/>
        </w:rPr>
        <w:t xml:space="preserve">
User Toggles Desktop Site
          │
          ▼
┌─────────────────────────────────────────────────────────────┐
│                  WebView Configuration                      │
│                                                             │
│ • userAgentString = Dynamic Desktop Chromium UA             │
│ • useWideViewPort = true                                    │
│ • loadWithOverviewMode = true                               │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
                       webView.reload()
                               │
             ┌─────────────────┴─────────────────┐
             ▼                                   ▼
┌─────────────────────────────┐   ┌─────────────────────────────┐
│   Top-Level Navigation      │   │   Chromium/WebView Engine   │
├─────────────────────────────┤   ├─────────────────────────────┤
│ • Desktop UA                │   │ • Subresources, CSS,        │
│ • Initial desktop hint      │   │   XHR/fetch, images, etc.   │
│   headers where supplied    │   │   handled by Chromium       │
│ • Server can select         │   │ • Configured UA is used     │
│   desktop-oriented HTML     │   │   according to WebView's    │
│                             │   │   normal request handling   │
└──────────────┬──────────────┘   └──────────────┬──────────────┘
               │                                 │
               └────────────────┬────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────┐
│              Compatibility &amp; Hardware Layer                 │
├─────────────────────────────────────────────────────────────┤
│ • Document-start compatibility layer exposes the configured │
│   desktop presentation state to page JavaScript             │
│ • Touch gestures remain native                              │
│ • Pinch-to-zoom / charts / drag interactions preserved      │
│ • Native GPU/WebGL rendering remains available               │
│ • Screen density and device capabilities remain native       │
│ • Back/forward navigation and session state preserved         │
└─────────────────────────────────────────────────────────────┘
        </w:t>
      </w:r>
    </w:p>

    <w:p>
      <w:pPr>
        <w:spacing w:before="200" w:after="100"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:b/>
          <w:sz w:val="26"/>
          <w:color w:val="0D47A1"/>
        </w:rPr>
        <w:t>3. Technical Implementation Details</w:t>
      </w:r>
    </w:p>

    <w:p>
      <w:pPr>
        <w:spacing w:after="80"/>
      </w:pPr>
      <w:r>
        <w:rPr><w:b/></w:rPr>
        <w:t>• Dynamic User-Agent Generation: </w:t>
      </w:r>
      <w:r>
        <w:t>Derives the Chromium version dynamically via WebSettings.getDefaultUserAgent(context), matching the real device Chromium engine (e.g. Chrome/130.x.x.x) and avoiding stale hardcoded strings.</w:t>
      </w:r>
    </w:p>

    <w:p>
      <w:pPr>
        <w:spacing w:after="80"/>
      </w:pPr>
      <w:r>
        <w:rPr><w:b/></w:rPr>
        <w:t>• Viewport &amp; Scaling: </w:t>
      </w:r>
      <w:r>
        <w:t>useWideViewPort = true and loadWithOverviewMode = true enable standard 980px+ desktop layout grids without clipping.</w:t>
      </w:r>
    </w:p>

    <w:p>
      <w:pPr>
        <w:spacing w:after="80"/>
      </w:pPr>
      <w:r>
        <w:rPr><w:b/></w:rPr>
        <w:t>• Request &amp; Subresource Handling: </w:t>
      </w:r>
      <w:r>
        <w:t>Top-level navigation uses Desktop UA with initial desktop hint headers; all subresources (CSS, scripts, fetch/XHR, images) are handled by Chromium using the configured UA according to WebView's normal request handling.</w:t>
      </w:r>
    </w:p>

    <w:p>
      <w:pPr>
        <w:spacing w:after="80"/>
      </w:pPr>
      <w:r>
        <w:rPr><w:b/></w:rPr>
        <w:t>• Document-Start Compatibility: </w:t>
      </w:r>
      <w:r>
        <w:t>WebViewCompat.addDocumentStartJavaScript exposes the configured desktop presentation state (mobile: false) to page scripts before DOM parsing.</w:t>
      </w:r>
    </w:p>

    <w:p>
      <w:pPr>
        <w:spacing w:after="80"/>
      </w:pPr>
      <w:r>
        <w:rPr><w:b/></w:rPr>
        <w:t>• Preserved Native Hardware: </w:t>
      </w:r>
      <w:r>
        <w:t>maxTouchPoints and touch listeners remain untouched to maintain full functionality for TradingView charts, pinch-to-zoom, DEX interfaces, and drag gestures.</w:t>
      </w:r>
    </w:p>

    <w:p>
      <w:pPr>
        <w:spacing w:after="80"/>
      </w:pPr>
      <w:r>
        <w:rPr><w:b/></w:rPr>
        <w:t>• Persistence &amp; Navigation: </w:t>
      </w:r>
      <w:r>
        <w:t>Preferences are saved in SharedPreferences per-tab and globally. Toggling reloads the page while preserving back/forward history stacks.</w:t>
      </w:r>
    </w:p>

  </w:body>
</w:document>"""

def create_docx(filename):
    with zipfile.ZipFile(filename, 'w', zipfile.ZIP_DEFLATED) as docx:
        docx.writestr('[Content_Types].xml', content_types_xml)
        docx.writestr('_rels/.rels', rels_xml)
        docx.writestr('word/_rels/document.xml.rels', document_rels_xml)
        docx.writestr('word/styles.xml', styles_xml)
        docx.writestr('word/document.xml', document_xml)
    print(f"Created {filename} successfully ({os.path.getsize(filename)} bytes)")

if __name__ == '__main__':
    create_docx('Desktop_Site_Architecture_Specification.docx')
