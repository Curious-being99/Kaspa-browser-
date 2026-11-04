#include "browser_host.h"
#include <algorithm>
#include <iostream>
#include <mutex>
#include <utility>
#include "include/cef_parser.h"
#include "include/cef_task.h"
#include "include/views/cef_browser_view_delegate.h"
#include "include/views/cef_window_delegate.h"

namespace {
std::mutex output_mutex;
CefRefPtr<CefBrowser> active_browser;
std::string EscapeJson(const std::string& value) {
  std::string out;
  for (unsigned char c : value) {
    switch (c) {
      case '"': out += "\\\""; break;
      case '\\': out += "\\\\"; break;
      case '\n': out += "\\n"; break;
      case '\r': out += "\\r"; break;
      case '\t': out += "\\t"; break;
      default: if (c < 0x20) out += '?'; else out += static_cast<char>(c);
    }
  }
  return out;
}
void Emit(const std::string& json) {
  std::lock_guard<std::mutex> lock(output_mutex);
  std::cout << json << std::endl;
}
void EmitUrl(const char* event, const CefString& url) {
  Emit(std::string("{\"event\":\"") + event + "\",\"url\":\"" + EscapeJson(url.ToString()) + "\"}");
}
bool IsHttpUrl(const std::string& value) {
  CefURLParts parts;
  if (!CefParseURL(value, parts)) return false;
  const auto scheme = CefString(&parts.scheme).ToString();
  return scheme == "http" || scheme == "https";
}
class BrowserViewDelegate final : public CefBrowserViewDelegate {
 public: IMPLEMENT_REFCOUNTING(BrowserViewDelegate);
};
class WindowDelegate final : public CefWindowDelegate {
 public:
  explicit WindowDelegate(CefRefPtr<CefBrowserView> view) : view_(view) {}
  void OnWindowCreated(CefRefPtr<CefWindow> window) override {
    window->AddChildView(view_); window->Show(); view_->RequestFocus();
  }
  CefSize GetPreferredSize(CefRefPtr<CefView> view) override { return CefSize(1280, 800); }
  void OnWindowDestroyed(CefRefPtr<CefWindow> window) override { view_ = nullptr; }
 private:
  CefRefPtr<CefBrowserView> view_;
  IMPLEMENT_REFCOUNTING(WindowDelegate);
};
class BrowserCommandTask final : public CefTask {
 public:
  BrowserCommandTask(std::string command, std::string value) : command_(std::move(command)), value_(std::move(value)) {}
  void Execute() override {
    if (command_ == "QUIT") { if (active_browser) active_browser->GetHost()->CloseBrowser(true); return; }
    if (!active_browser) return;
    if (command_ == "NAVIGATE" && IsHttpUrl(value_)) active_browser->GetMainFrame()->LoadURL(value_);
    else if (command_ == "RELOAD") active_browser->Reload();
    else if (command_ == "BACK") active_browser->GoBack();
    else if (command_ == "FORWARD") active_browser->GoForward();
    else if (command_ == "STOP") active_browser->StopLoad();
  }
 private:
  std::string command_, value_;
  IMPLEMENT_REFCOUNTING(BrowserCommandTask);
};
} // namespace

void KaspaBrowserHandler::OnAfterCreated(CefRefPtr<CefBrowser> browser) { active_browser = browser; }
void KaspaBrowserHandler::OnBeforeClose(CefRefPtr<CefBrowser> browser) {
  if (active_browser && active_browser->GetIdentifier() == browser->GetIdentifier()) {
    active_browser = nullptr;
    CefQuitMessageLoop();
  }
}

bool KaspaBrowserHandler::OnBeforePopup(
    CefRefPtr<CefBrowser>, CefRefPtr<CefFrame>, int, const CefString&,
    const CefString&, CefLifeSpanHandler::WindowOpenDisposition, bool, const CefPopupFeatures&,
    CefWindowInfo&, CefRefPtr<CefClient>&, CefBrowserSettings&,
    CefRefPtr<CefDictionaryValue>&, bool*) {
  return true;
}

bool KaspaBrowserHandler::OnBeforeBrowse(CefRefPtr<CefBrowser>,
                                         CefRefPtr<CefFrame> frame,
                                         CefRefPtr<CefRequest> request,
                                         bool, bool) {
  if (!IsHttpUrl(request->GetURL().ToString())) return true;
  if (frame->IsMain()) EmitUrl("navigation_started", request->GetURL());
  return false;
}
void KaspaBrowserHandler::OnLoadStart(CefRefPtr<CefBrowser>, CefRefPtr<CefFrame> frame, TransitionType) {
  if (frame->IsMain()) EmitUrl("navigation_committed", frame->GetURL());
}
void KaspaBrowserHandler::OnLoadingProgressChange(CefRefPtr<CefBrowser>, double progress) {
  const int percent = std::clamp(static_cast<int>(progress * 100.0), 0, 100);
  Emit("{\"event\":\"load_progress\",\"percent\":" + std::to_string(percent) + "}");
}
void KaspaBrowserHandler::OnLoadEnd(CefRefPtr<CefBrowser>, CefRefPtr<CefFrame> frame, int) {
  if (frame->IsMain()) EmitUrl("load_finished", frame->GetURL());
}
void KaspaBrowserHandler::OnLoadError(CefRefPtr<CefBrowser>, CefRefPtr<CefFrame> frame,
 ErrorCode code, const CefString&, const CefString& failed_url) {
  if (!frame->IsMain() || code == ERR_ABORTED) return;
  Emit("{\"event\":\"load_failed\",\"url\":\"" + EscapeJson(failed_url.ToString()) +
       "\",\"code\":" + std::to_string(static_cast<int>(code)) + "}");
}
void KaspaBrowserHandler::OnTitleChange(CefRefPtr<CefBrowser>, const CefString& title) {
  Emit("{\"event\":\"title_changed\",\"title\":\"" + EscapeJson(title.ToString()) + "\"}");
}
KaspaBrowserApp::KaspaBrowserApp(std::string initial_url) : initial_url_(std::move(initial_url)) {}
void KaspaBrowserApp::OnContextInitialized() {
  if (!IsHttpUrl(initial_url_)) initial_url_ = "https://kaspabrowser.xyz";
  CefBrowserSettings settings;
  CefRefPtr<KaspaBrowserHandler> handler(new KaspaBrowserHandler());
  auto view = CefBrowserView::CreateBrowserView(handler, initial_url_, settings, nullptr, nullptr, new BrowserViewDelegate());
  CefWindow::CreateTopLevelWindow(new WindowDelegate(view));
}
void PostBrowserCommand(const std::string& command, const std::string& value) {
  CefPostTask(TID_UI, new BrowserCommandTask(command, value));
}
