import Foundation
import SwiftUI
import Combine

/// Model representing a browser tab in Kazium iOS
struct BrowserTab: Identifiable, Equatable {
    let id: UUID
    var urlString: String
    var title: String
    var isDesktopMode: Bool
    var canGoBack: Bool
    var canGoForward: Bool
    var isLoading: Bool

    init(
        id: UUID = UUID(),
        urlString: String = "https://kaspa.org",
        title: String = "Kaspa — Real Time Decentralized",
        isDesktopMode: Bool = false
    ) {
        self.id = id
        self.urlString = urlString
        self.title = title
        self.isDesktopMode = isDesktopMode
        self.canGoBack = false
        self.canGoForward = false
        self.isLoading = false
    }

    static func == (lhs: BrowserTab, rhs: BrowserTab) -> Bool {
        lhs.id == rhs.id &&
        lhs.urlString == rhs.urlString &&
        lhs.title == rhs.title &&
        lhs.isDesktopMode == rhs.isDesktopMode
    }
}

/// Tab Manager for Kazium iOS Multi-Tab Browsing
class TabManager: ObservableObject {
    @Published var tabs: [BrowserTab] = []
    @Published var activeTabId: UUID = UUID()
    @Published var isTabSwitcherPresented: Bool = false

    init() {
        let initialTab = BrowserTab()
        self.tabs = [initialTab]
        self.activeTabId = initialTab.id
    }

    var activeTabBinding: Binding<BrowserTab> {
        Binding(
            get: {
                if let tab = self.tabs.first(where: { $0.id == self.activeTabId }) {
                    return tab
                }
                return self.tabs.first ?? BrowserTab()
            },
            set: { updatedTab in
                if let index = self.tabs.firstIndex(where: { $0.id == updatedTab.id }) {
                    self.tabs[index] = updatedTab
                }
            }
        )
    }

    func addNewTab(url: String = "https://kaspa.org", isDesktop: Bool = false) {
        let newTab = BrowserTab(urlString: url, title: "New Tab", isDesktopMode: isDesktop)
        tabs.append(newTab)
        activeTabId = newTab.id
        isTabSwitcherPresented = false
    }

    func closeTab(id: UUID) {
        guard let index = tabs.firstIndex(where: { $0.id == id }) else { return }
        tabs.remove(at: index)

        if tabs.isEmpty {
            addNewTab()
        } else if activeTabId == id {
            let nextIndex = min(index, tabs.count - 1)
            activeTabId = tabs[nextIndex].id
        }
    }

    func selectTab(id: UUID) {
        activeTabId = id
        isTabSwitcherPresented = false
    }
}
