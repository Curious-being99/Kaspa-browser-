use crate::api::{sanitize_navigation_url, NavigationError};
use std::collections::{BTreeMap, BTreeSet};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct HistoryEntry {
    pub url: String,
    pub title: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BrowserTab {
    pub id: String,
    pub title: String,
    pub url: Option<String>,
    pub history: Vec<HistoryEntry>,
    pub history_index: Option<usize>,
}

impl BrowserTab {
    fn new(id: String) -> Self {
        Self { id, title: "New tab".into(), url: None, history: Vec::new(), history_index: None }
    }

    pub fn can_go_back(&self) -> bool {
        self.history_index.is_some_and(|index| index > 0)
    }

    pub fn can_go_forward(&self) -> bool {
        self.history_index.is_some_and(|index| index + 1 < self.history.len())
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Bookmark {
    pub url: String,
    pub title: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BrowserSettings {
    pub homepage: String,
    pub restore_session: bool,
    pub javascript_enabled: bool,
    pub block_third_party_cookies: bool,
}

impl Default for BrowserSettings {
    fn default() -> Self {
        Self {
            homepage: "https://kaspabrowser.xyz".into(),
            restore_session: true,
            javascript_enabled: true,
            block_third_party_cookies: true,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WalletMethod {
    Connect,
    ReadAccount,
    ReadPublicKey,
    SignMessage,
    SignTransaction,
    SubmitTransaction,
}

impl WalletMethod {
    pub fn requires_native_approval(self) -> bool {
        matches!(self, Self::Connect | Self::SignMessage | Self::SignTransaction | Self::SubmitTransaction)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PermissionDecision {
    Ask,
    Allow,
    Block,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct WalletOriginPermission {
    pub origin: String,
    pub decision: PermissionDecision,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum BrowserStateError {
    InvalidTabId,
    UnknownTab,
    InvalidUrl(NavigationError),
    NoActiveTab,
}

impl From<NavigationError> for BrowserStateError {
    fn from(value: NavigationError) -> Self { Self::InvalidUrl(value) }
}

/// Platform-neutral browser state. Rendering engines remain owned by their
/// platform adapters; this type contains no WebView/CEF objects or wallet keys.
#[derive(Debug, Clone)]
pub struct BrowserState {
    tabs: BTreeMap<String, BrowserTab>,
    active_tab: Option<String>,
    recently_closed: Vec<BrowserTab>,
    bookmarks: BTreeMap<String, Bookmark>,
    settings: BrowserSettings,
    wallet_permissions: BTreeMap<String, PermissionDecision>,
    granted_site_permissions: BTreeSet<(String, String)>,
}

impl Default for BrowserState {
    fn default() -> Self { Self::new(BrowserSettings::default()) }
}

impl BrowserState {
    pub fn new(settings: BrowserSettings) -> Self {
        Self {
            tabs: BTreeMap::new(),
            active_tab: None,
            recently_closed: Vec::new(),
            bookmarks: BTreeMap::new(),
            settings,
            wallet_permissions: BTreeMap::new(),
            granted_site_permissions: BTreeSet::new(),
        }
    }

    pub fn settings(&self) -> &BrowserSettings { &self.settings }

    pub fn update_settings(&mut self, settings: BrowserSettings) -> Result<(), BrowserStateError> {
        self.settings = BrowserSettings {
            homepage: sanitize_navigation_url(&settings.homepage)?,
            ..settings
        };
        Ok(())
    }

    pub fn open_tab(&mut self, id: impl Into<String>) -> Result<&BrowserTab, BrowserStateError> {
        let id = id.into();
        if id.trim().is_empty() { return Err(BrowserStateError::InvalidTabId); }
        self.tabs.entry(id.clone()).or_insert_with(|| BrowserTab::new(id.clone()));
        self.active_tab = Some(id.clone());
        Ok(self.tabs.get(&id).expect("tab inserted"))
    }

    pub fn close_tab(&mut self, id: &str) -> Result<Option<BrowserTab>, BrowserStateError> {
        let tab = self.tabs.remove(id).ok_or(BrowserStateError::UnknownTab)?;
        if self.active_tab.as_deref() == Some(id) {
            self.active_tab = self.tabs.keys().next().cloned();
        }
        self.recently_closed.push(tab.clone());
        Ok(Some(tab))
    }

    pub fn reopen_last_closed(&mut self) -> Option<&BrowserTab> {
        let mut tab = self.recently_closed.pop()?;
        let id = tab.id.clone();
        if self.tabs.contains_key(&id) { return None; }
        self.tabs.insert(id.clone(), tab);
        self.active_tab = Some(id.clone());
        self.tabs.get(&id)
    }

    pub fn activate_tab(&mut self, id: &str) -> Result<(), BrowserStateError> {
        if !self.tabs.contains_key(id) { return Err(BrowserStateError::UnknownTab); }
        self.active_tab = Some(id.to_owned());
        Ok(())
    }

    pub fn active_tab(&self) -> Option<&BrowserTab> {
        self.active_tab.as_ref().and_then(|id| self.tabs.get(id))
    }

    pub fn tabs(&self) -> impl Iterator<Item = &BrowserTab> { self.tabs.values() }

    pub fn commit_navigation(&mut self, tab_id: &str, raw_url: &str, title: Option<String>) -> Result<(), BrowserStateError> {
        let url = sanitize_navigation_url(raw_url)?;
        let tab = self.tabs.get_mut(tab_id).ok_or(BrowserStateError::UnknownTab)?;
        if let Some(index) = tab.history_index {
            tab.history.truncate(index + 1);
        }
        tab.history.push(HistoryEntry { url: url.clone(), title: title.clone() });
        tab.history_index = Some(tab.history.len() - 1);
        tab.url = Some(url);
        if let Some(title) = title { tab.title = title; }
        Ok(())
    }

    pub fn set_tab_title(&mut self, tab_id: &str, title: String) -> Result<(), BrowserStateError> {
        let tab = self.tabs.get_mut(tab_id).ok_or(BrowserStateError::UnknownTab)?;
        tab.title = title;
        Ok(())
    }

    pub fn go_back(&mut self, tab_id: &str) -> Result<Option<String>, BrowserStateError> {
        let tab = self.tabs.get_mut(tab_id).ok_or(BrowserStateError::UnknownTab)?;
        let Some(index) = tab.history_index else { return Ok(None); };
        if index == 0 { return Ok(None); }
        tab.history_index = Some(index - 1);
        let entry = &tab.history[index - 1];
        tab.url = Some(entry.url.clone());
        Ok(Some(entry.url.clone()))
    }

    pub fn go_forward(&mut self, tab_id: &str) -> Result<Option<String>, BrowserStateError> {
        let tab = self.tabs.get_mut(tab_id).ok_or(BrowserStateError::UnknownTab)?;
        let Some(index) = tab.history_index else { return Ok(None); };
        if index + 1 >= tab.history.len() { return Ok(None); }
        tab.history_index = Some(index + 1);
        let entry = &tab.history[index + 1];
        tab.url = Some(entry.url.clone());
        Ok(Some(entry.url.clone()))
    }

    pub fn add_bookmark(&mut self, raw_url: &str, title: impl Into<String>) -> Result<(), BrowserStateError> {
        let url = sanitize_navigation_url(raw_url)?;
        self.bookmarks.insert(url.clone(), Bookmark { url, title: title.into() });
        Ok(())
    }

    pub fn remove_bookmark(&mut self, raw_url: &str) -> Result<bool, BrowserStateError> {
        let url = sanitize_navigation_url(raw_url)?;
        Ok(self.bookmarks.remove(&url).is_some())
    }

    pub fn bookmarks(&self) -> impl Iterator<Item = &Bookmark> { self.bookmarks.values() }

    pub fn set_wallet_origin_permission(&mut self, origin: &str, decision: PermissionDecision) -> Result<(), BrowserStateError> {
        let normalized = normalize_https_origin(origin)?;
        self.wallet_permissions.insert(normalized, decision);
        Ok(())
    }

    pub fn wallet_origin_permission(&self, origin: &str) -> Result<PermissionDecision, BrowserStateError> {
        let normalized = normalize_https_origin(origin)?;
        Ok(self.wallet_permissions.get(&normalized).copied().unwrap_or(PermissionDecision::Ask))
    }

    pub fn wallet_request_requires_approval(&self, origin: &str, method: WalletMethod) -> Result<bool, BrowserStateError> {
        let permission = self.wallet_origin_permission(origin)?;
        Ok(method.requires_native_approval() || permission != PermissionDecision::Allow)
    }

    pub fn set_site_permission(&mut self, origin: &str, permission: &str, granted: bool) -> Result<(), BrowserStateError> {
        let origin = normalize_https_origin(origin)?;
        let key = (origin, permission.trim().to_ascii_lowercase());
        if granted { self.granted_site_permissions.insert(key); }
        else { self.granted_site_permissions.remove(&key); }
        Ok(())
    }

    pub fn site_permission_granted(&self, origin: &str, permission: &str) -> Result<bool, BrowserStateError> {
        let origin = normalize_https_origin(origin)?;
        Ok(self.granted_site_permissions.contains(&(origin, permission.trim().to_ascii_lowercase())))
    }
}

fn normalize_https_origin(raw: &str) -> Result<String, BrowserStateError> {
    let url = url::Url::parse(raw.trim()).map_err(|_| BrowserStateError::InvalidUrl(NavigationError::InvalidUrl))?;
    if url.scheme() != "https" || url.host_str().is_none() {
        return Err(BrowserStateError::InvalidUrl(NavigationError::UnsupportedScheme));
    }
    Ok(url.origin().ascii_serialization())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn tabs_keep_independent_navigation_histories_and_truncate_forward_entries() {
        let mut state = BrowserState::default();
        state.open_tab("a").unwrap();
        state.commit_navigation("a", "https://one.example", Some("One".into())).unwrap();
        state.commit_navigation("a", "https://two.example", Some("Two".into())).unwrap();
        assert_eq!(state.go_back("a").unwrap().as_deref(), Some("https://one.example/"));
        state.commit_navigation("a", "https://three.example", None).unwrap();
        assert!(!state.tabs().find(|tab| tab.id == "a").unwrap().can_go_forward());
    }

    #[test]
    fn tabs_can_be_closed_and_reopened() {
        let mut state = BrowserState::default();
        state.open_tab("a").unwrap();
        state.open_tab("b").unwrap();
        state.close_tab("b").unwrap();
        assert_eq!(state.active_tab().unwrap().id, "a");
        assert_eq!(state.reopen_last_closed().unwrap().id, "b");
    }

    #[test]
    fn bookmarks_only_accept_http_and_https_urls() {
        let mut state = BrowserState::default();
        state.add_bookmark("https://example.com/path", "Example").unwrap();
        assert_eq!(state.bookmarks().count(), 1);
        assert!(state.add_bookmark("file:///etc/passwd", "local").is_err());
    }

    #[test]
    fn wallet_signing_and_submission_always_require_native_approval() {
        let mut state = BrowserState::default();
        state.set_wallet_origin_permission("https://dapp.example/path", PermissionDecision::Allow).unwrap();
        assert!(!state.wallet_request_requires_approval("https://dapp.example", WalletMethod::ReadAccount).unwrap());
        assert!(state.wallet_request_requires_approval("https://dapp.example", WalletMethod::SignTransaction).unwrap());
        assert!(state.wallet_request_requires_approval("http://dapp.example", WalletMethod::ReadAccount).is_err());
    }

    #[test]
    fn settings_homepage_is_validated() {
        let mut state = BrowserState::default();
        let invalid = BrowserSettings { homepage: "file:///tmp".into(), ..BrowserSettings::default() };
        assert!(state.update_settings(invalid).is_err());
    }
}
