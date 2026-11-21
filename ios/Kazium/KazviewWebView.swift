import SwiftUI
import UIKit
import WebKit

/// Coordinates lightweight HTTPS socket warming + tracker filtering and cancels on memory pressure.
final class KazviewPreloadCoordinator {
    static let shared = KazviewPreloadCoordinator()

    private let lock = NSLock()
    private var activeTask: URLSessionDataTask?
    private var lastOriginTimes: [String: Date] = [:]
    private var compiledRuleList: WKContentRuleList?

    private init() {
        compileDefaultContentBlockRules()
        NotificationCenter.default.addObserver(
            forName: UIApplication.didReceiveMemoryWarningNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.cancelAndTrimMemory()
        }
    }

    func warmOriginIfEligible(rawUrl: String, isPrivacyBlocked: Bool = false) {
        guard !isPrivacyBlocked,
              rawUrl.lowercased().hasPrefix("https://"),
              let url = URL(string: rawUrl),
              let host = url.host?.lowercased(),
              host.contains(".") else { return }

        if host.contains("challenges.cloudflare") || host.contains("doubleclick") {
            return
        }

        let origin = "https://\(host)"
        lock.lock()
        let last = lastOriginTimes[origin]
        if let last = last, Date().timeIntervalSince(last) < 60.0 {
            lock.unlock()
            return
        }
        lastOriginTimes[origin] = Date()
        activeTask?.cancel()
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 4.0)
        request.httpMethod = "HEAD"
        let task = URLSession.shared.dataTask(with: request) { _, _, _ in }
        activeTask = task
        lock.unlock()
        task.resume()
    }

    func cancelAndTrimMemory() {
        lock.lock()
        activeTask?.cancel()
        activeTask = nil
        lastOriginTimes.removeAll()
        lock.unlock()
        URLCache.shared.removeAllCachedResponses()
    }

    func applyRulesIfReady(to controller: WKUserContentController) {
        if let rules = compiledRuleList {
            controller.add(rules)
        }
    }

    private func compileDefaultContentBlockRules() {
        let rulesJson = """
        [
          { "trigger": { "url-filter": ".*doubleclick\\\\.net.*" }, "action": { "type": "block" } },
          { "trigger": { "url-filter": ".*googlesyndication\\\\.com.*" }, "action": { "type": "block" } },
          { "trigger": { "url-filter": ".*google-analytics\\\\.com.*" }, "action": { "type": "block" } }
        ]
        """
        WKContentRuleListStore.default().compileContentRuleList(
            forIdentifier: "kazview_tracker_rules_v1",
            encodedContentRuleList: rulesJson
        ) { [weak self] ruleList, _ in
            if let ruleList = ruleList {
                DispatchQueue.main.async {
                    self?.compiledRuleList = ruleList
                }
            }
        }
    }
}

/// Kazview iOS Native WebKit Engine
/// Shared WKProcessPool, dark pre-paint canvas, KVO estimatedProgress,
/// didCommit live paint transition, and memory-warning eviction.
struct KazviewWebView: UIViewRepresentable {
    static let sharedProcessPool = WKProcessPool()

    let tabId: UUID
    @Binding var urlString: String
    @Binding var isDesktopMode: Bool
    @Binding var canGoBack: Bool
    @Binding var canGoForward: Bool
    @Binding var isLoading: Bool
    @Binding var estimatedProgress: Double
    @Binding var pendingNavAction: WebNavigationAction?

    class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
        var parent: KazviewWebView
        weak var webView: WKWebView?
        var progressObservation: NSKeyValueObservation?
        var lastHandledActionNonce: UUID?
        var lastLoadedUrl: String?

        init(_ parent: KazviewWebView) {
            self.parent = parent
        }

        deinit {
            progressObservation?.invalidate()
        }

        func attachProgressObserver(to webView: WKWebView) {
            progressObservation?.invalidate()
            progressObservation = webView.observe(\.estimatedProgress, options: [.new]) { [weak self] view, _ in
                DispatchQueue.main.async {
                    self?.parent.estimatedProgress = view.estimatedProgress
                }
            }
        }

        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
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

        func webView(_ webView: WKWebView, didCommit navigation: WKNavigation!) {
            DispatchQueue.main.async {
                self.parent.canGoBack = webView.canGoBack
                self.parent.canGoForward = webView.canGoForward
                if let committedUrl = webView.url?.absoluteString {
                    self.lastLoadedUrl = committedUrl
                    self.parent.urlString = committedUrl
                }
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            DispatchQueue.main.async {
                self.parent.isLoading = false
                self.parent.estimatedProgress = 1.0
                self.parent.canGoBack = webView.canGoBack
                self.parent.canGoForward = webView.canGoForward
                if let currentUrl = webView.url?.absoluteString {
                    self.lastLoadedUrl = currentUrl
                    self.parent.urlString = currentUrl
                }
            }
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            DispatchQueue.main.async {
                self.parent.isLoading = false
            }
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
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
        configuration.processPool = Self.sharedProcessPool
        configuration.websiteDataStore = .default()
        let contentController = WKUserContentController()

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
        KazviewPreloadCoordinator.shared.applyRulesIfReady(to: contentController)
        configuration.userContentController = contentController

        configuration.preferences.javaScriptCanOpenWindowsAutomatically = true
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.isOpaque = false
        let obsidianBg = UIColor(red: 0.031, green: 0.035, blue: 0.047, alpha: 1.0)
        webView.backgroundColor = obsidianBg
        webView.scrollView.backgroundColor = obsidianBg
        webView.navigationDelegate = context.coordinator
        webView.uiDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = true
        context.coordinator.webView = webView
        context.coordinator.attachProgressObserver(to: webView)

        applyEngineSettings(webView)

        if let url = URL(string: urlString) {
            context.coordinator.lastLoadedUrl = urlString
            webView.load(URLRequest(url: url))
        }

        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        context.coordinator.parent = self
        applyEngineSettings(uiView)

        if let action = pendingNavAction {
            switch action {
            case .goBack(let targetTabId, let nonce) where targetTabId == tabId && context.coordinator.lastHandledActionNonce != nonce:
                context.coordinator.lastHandledActionNonce = nonce
                if uiView.canGoBack { uiView.goBack() }
            case .goForward(let targetTabId, let nonce) where targetTabId == tabId && context.coordinator.lastHandledActionNonce != nonce:
                context.coordinator.lastHandledActionNonce = nonce
                if uiView.canGoForward { uiView.goForward() }
            case .reload(let targetTabId, let nonce) where targetTabId == tabId && context.coordinator.lastHandledActionNonce != nonce:
                context.coordinator.lastHandledActionNonce = nonce
                uiView.reload()
            default:
                break
            }
        }

        if let targetUrl = URL(string: urlString),
           context.coordinator.lastLoadedUrl != urlString,
           uiView.url?.absoluteString != urlString {
            context.coordinator.lastLoadedUrl = urlString
            uiView.load(URLRequest(url: targetUrl))
        }
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
