import SwiftUI

struct ContentView: View {
    @StateObject private var tabManager = TabManager()
    @StateObject private var biometricAuth = BiometricAuthManager()
    @State private var urlInput: String = "https://kaspa.org"

    var body: some View {
        ZStack {
            Color.black.edgesIgnoringSafeArea(.all)

            VStack(spacing: 0) {
                // Top Address Bar
                HStack(spacing: 8) {
                    TextField("Search or enter Kaspa / Web URL...", text: $urlInput, onCommit: {
                        var formatted = urlInput.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !formatted.hasPrefix("http://") && !formatted.hasPrefix("https://") && !formatted.hasPrefix("kaspa:") {
                            formatted = "https://" + formatted
                        }
                        tabManager.activeTabBinding.wrappedValue.urlString = formatted
                    })
                    .textFieldStyle(PlainTextFieldStyle())
                    .padding(8)
                    .background(Color(red: 0.08, green: 0.09, blue: 0.13))
                    .cornerRadius(8)
                    .foregroundColor(.white)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)

                    Button(action: {
                        tabManager.activeTabBinding.wrappedValue.isDesktopMode.toggle()
                    }) {
                        Image(systemName: tabManager.activeTabBinding.wrappedValue.isDesktopMode ? "desktopcomputer" : "iphone")
                            .foregroundColor(tabManager.activeTabBinding.wrappedValue.isDesktopMode ? .cyan : .gray)
                            .padding(6)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color(red: 0.05, green: 0.06, blue: 0.08))

                if tabManager.activeTabBinding.wrappedValue.isLoading {
                    ProgressView()
                        .progressViewStyle(LinearProgressViewStyle(tint: .cyan))
                }

                // Active WebKit WebView Tab
                KazviewWebView(
                    urlString: tabManager.activeTabBinding.urlString,
                    isDesktopMode: tabManager.activeTabBinding.isDesktopMode,
                    canGoBack: tabManager.activeTabBinding.canGoBack,
                    canGoForward: tabManager.activeTabBinding.canGoForward,
                    isLoading: tabManager.activeTabBinding.isLoading
                )
                .id(tabManager.activeTabId)
                .edgesIgnoringSafeArea(.bottom)

                // Bottom Multi-Tab Navigation Bar
                HStack {
                    Button(action: {
                        // Go back action
                    }) {
                        Image(systemName: "chevron.backward")
                            .foregroundColor(tabManager.activeTabBinding.wrappedValue.canGoBack ? .cyan : .gray)
                    }
                    .disabled(!tabManager.activeTabBinding.wrappedValue.canGoBack)
                    .frame(maxWidth: .infinity)

                    Button(action: {
                        // Go forward action
                    }) {
                        Image(systemName: "chevron.forward")
                            .foregroundColor(tabManager.activeTabBinding.wrappedValue.canGoForward ? .cyan : .gray)
                    }
                    .disabled(!tabManager.activeTabBinding.wrappedValue.canGoForward)
                    .frame(maxWidth: .infinity)

                    Button(action: {
                        tabManager.addNewTab()
                    }) {
                        Image(systemName: "plus")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.cyan)
                    }
                    .frame(maxWidth: .infinity)

                    Button(action: {
                        tabManager.isTabSwitcherPresented = true
                    }) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(Color.cyan, lineWidth: 1.5)
                                .frame(width: 22, height: 22)
                            Text("\(tabManager.tabs.count)")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.cyan)
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
                .padding(.vertical, 10)
                .background(Color(red: 0.05, green: 0.06, blue: 0.08))
            }
        }
        .sheet(isPresented: $tabManager.isTabSwitcherPresented) {
            TabSwitcherView(tabManager: tabManager)
        }
        .onChange(of: tabManager.activeTabBinding.wrappedValue.urlString) { newUrl in
            urlInput = newUrl
        }
    }
}

/// Tab Switcher Grid View for iOS
struct TabSwitcherView: View {
    @ObservedObject var tabManager: TabManager
    @Environment(\.presentationMode) var presentationMode

    let columns = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        NavigationView {
            ZStack {
                Color(red: 0.04, green: 0.04, blue: 0.06).edgesIgnoringSafeArea(.all)

                ScrollView {
                    LazyVGrid(columns: columns, spacing: 14) {
                        ForEach(tabManager.tabs) { tab in
                            ZStack(alignment: .topTrailing) {
                                Button(action: {
                                    tabManager.selectTab(id: tab.id)
                                }) {
                                    VStack(alignment: .leading, spacing: 8) {
                                        Text(tab.title)
                                            .font(.system(size: 13, weight: .semibold))
                                            .foregroundColor(.white)
                                            .lineLimit(1)

                                        Text(tab.urlString)
                                            .font(.system(size: 11))
                                            .foregroundColor(.gray)
                                            .lineLimit(2)

                                        Spacer()

                                        HStack {
                                            if tab.isDesktopMode {
                                                Image(systemName: "desktopcomputer")
                                                    .font(.system(size: 10))
                                                    .foregroundColor(.cyan)
                                            }
                                            Spacer()
                                            if tab.id == tabManager.activeTabId {
                                                Circle()
                                                    .fill(Color.cyan)
                                                    .frame(width: 8, height: 8)
                                            }
                                        }
                                    }
                                    .padding(12)
                                    .frame(height: 140)
                                    .frame(maxWidth: .infinity)
                                    .background(Color(red: 0.09, green: 0.10, blue: 0.14))
                                    .cornerRadius(12)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .stroke(tab.id == tabManager.activeTabId ? Color.cyan : Color.clear, lineWidth: 2)
                                    )
                                }

                                Button(action: {
                                    tabManager.closeTab(id: tab.id)
                                }) {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundColor(.gray)
                                        .padding(8)
                                }
                            }
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Tabs (\(tabManager.tabs.count))")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Done") {
                        presentationMode.wrappedValue.dismiss()
                    }
                    .foregroundColor(.cyan)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        tabManager.addNewTab()
                    }) {
                        Image(systemName: "plus")
                            .foregroundColor(.cyan)
                    }
                }
            }
        }
    }
}
