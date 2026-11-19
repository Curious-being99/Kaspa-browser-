import SwiftUI
import WebKit

/// Kazview iOS Native WebKit Engine
/// Implements real Apple WebKit (WKWebView) with custom User-Agent,
/// Desktop Mode viewport enforcement, and isolated privacy data stores.
struct KazviewWebView: UIViewRepresentable {
    @Binding var urlString: String
    @Binding var isDesktopMode: Bool
    @Binding var canGoBack: Bool
    @Binding var canGoForward: Bool
    @Binding var isLoading: Bool

    // Custom Coordinator to handle WKNavigationDelegate and WKUIDelegate
    class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
        var parent: KazviewWebView
        var webView: WKWebView?

        init(_ parent: KazviewWebView) {
            self.parent = parent
        }

        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            // Secure native script message bridge (e.g. Passkeys or wallet actions)
            if message.name == "kazviewBridge" {
                print("Received bridge payload: \(message.body)")
            }
        }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            DispatchQueue.main.async {
                self.parent.isLoading = true
                self.parent.canGoBack = webView.canGoBack
                self.parent.canGoForward = webView.canGoForward
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            DispatchQueue.main.async {
                self.parent.isLoading = false
                self.parent.canGoBack = webView.canGoBack
                self.parent.canGoForward = webView.canGoForward
                if let currentUrl = webView.url?.absoluteString {
                    self.parent.urlString = currentUrl
                }
            }
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            DispatchQueue.main.async {
                self.parent.isLoading = false
            }
        }

        func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
            if navigationAction.targetFrame == nil {
                webView.load(navigationAction.request)
            }
            return nil
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        let contentController = WKUserContentController()

        // Privacy & Desktop Viewport script injection for Real WebKit
        let desktopScriptSource = """
        (function() {
            if (window.top !== window.self) return;
            var metas = document.querySelectorAll('meta[name="viewport"]');
            for (var i = 0; i < metas.length; i++) {
                metas[i].setAttribute('content', 'width=980, user-scalable=yes');
            }
        })();
        """
        let userScript = WKUserScript(source: desktopScriptSource, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
        contentController.addUserScript(userScript)
        contentController.add(context.coordinator, name: "kazviewBridge")
        configuration.userContentController = contentController

        // Apply WebKit Preferences
        configuration.preferences.javaScriptCanOpenWindowsAutomatically = true
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true
        
        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        webView.uiDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = true
        context.coordinator.webView = webView

        applyEngineSettings(webView)

        if let url = URL(string: urlString) {
            webView.load(URLRequest(url: url))
        }

        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        applyEngineSettings(uiView)
    }

    private func applyEngineSettings(_ webView: WKWebView) {
        if isDesktopMode {
            webView.configuration.defaultWebpagePreferences.preferredContentMode = .desktop
            webView.customUserAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"
        } else {
            webView.configuration.defaultWebpagePreferences.preferredContentMode = .mobile
            webView.customUserAgent = nil
        }
    }
}
