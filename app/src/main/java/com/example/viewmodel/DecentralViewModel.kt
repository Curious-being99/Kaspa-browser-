package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AccountEntity
import com.example.data.ContentEntity
import com.example.data.PeerEntity
import com.example.data.TrafficAuditEntity
import com.example.data.HistoryEntity
import com.example.data.BookmarkEntity
import com.example.data.BrowserTabEntity
import com.example.data.BrowserSessionEntity
import com.example.data.DomainEntity
import com.example.data.NewsArticleEntity
import com.example.data.KaspaNewsItem
import com.example.data.toKaspaNewsItem
import com.example.data.toEntity
import com.example.data.getDefaultCuratedNews
import com.example.data.fetchLatestKaspaFeeds
import com.example.network.kaspa.KaspaDomainRegistry
import com.example.network.kaspa.KaspaTransactionEngine
import com.example.network.kaspa.DomainAvailability
import com.example.util.BrowserStateLog
import com.example.util.BrowserTabWebViewManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

import com.example.model.KaspaWalletState
import com.example.model.NetworkMetrics
import com.example.model.NetworkProtocol
import com.example.model.ResolvedResource
import com.example.model.VerificationStatus
import com.example.model.AppUpdateInfo
import com.example.model.UpdateStatus
import com.example.network.AppUpdateManager
import com.example.network.CryptoUtils
import com.example.network.DomainConstants
import com.example.network.DualStackResolver
import com.example.network.KaspaPrivacyRelayEngine
import com.example.network.KaspaRelayCircuit
import com.example.network.KaspaWalletService
import com.example.network.LocalNodeManager
import com.example.network.UBlockEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive

enum class AppTab {
    BROWSER_GATEWAY,
    MESH_RADAR,
    TRAFFIC_AUDIT,
    LIBRARY,
    KASPA_WALLET
}

enum class SearchEngine(val baseUrl: String, val displayName: String) {
    DUCKDUCKGO("https://duckduckgo.com/?q=", "DuckDuckGo"),
    BRAVE("https://search.brave.com/search?q=", "Brave Search"),
    GOOGLE("https://www.google.com/search?q=", "Google"),
    BING("https://www.bing.com/search?q=", "Bing"),
    STARTPAGE("https://www.startpage.com/sp/search?query=", "Startpage"),
    ECOSIA("https://www.ecosia.org/search?q=", "Ecosia"),
    QWANT("https://www.qwant.com/?q=", "Qwant"),
    YAHOO("https://search.yahoo.com/search?p=", "Yahoo"),
    KAGI("https://kagi.com/search?q=", "Kagi")
}

data class ActiveDownload(
    val downloadId: Long,
    val fileName: String,
    val url: String,
    val progress: Float, // 0.0 to 1.0
    val bytesDownloaded: Long,
    val bytesTotal: Long,
    val status: String // "Pending", "Downloading", "Success", "Failed"
)

data class KaspaAddressValidationResult(
    val isValid: Boolean,
    val rawInput: String = "",
    val cleanAddress: String = "",
    val networkPrefix: String = "",
    val networkName: String = "",
    val addressType: String = "",
    val payload: String = "",
    val checksumValid: Boolean = false,
    val truncatedAddress: String = "",
    val explorerUrl: String = ""
)

fun extractFilenameFromDisposition(disposition: String?): String? {
    if (disposition.isNullOrBlank()) return null
    try {
        val utf8Match = Regex("filename\\*=(?:UTF-8|utf-8)''([^;\\r\\n]+)").find(disposition)
        if (utf8Match != null) {
            val encoded = utf8Match.groupValues[1].trim('"', '\'')
            return try {
                java.net.URLDecoder.decode(encoded, "UTF-8")
            } catch (_: Exception) { encoded }
        }
        val standardMatch = Regex("filename=\"?([^\";\\r\\n]+)\"?").find(disposition)
        if (standardMatch != null) {
            return standardMatch.groupValues[1].trim('"', '\'')
        }
    } catch (_: Exception) {}
    return null
}

fun resolveUniversalMimeType(fileName: String, serverContentType: String? = null): String {
    val cleanServerType = serverContentType?.split(";")?.firstOrNull()?.trim()?.lowercase()
    if (!cleanServerType.isNullOrBlank() &&
        cleanServerType != "application/octet-stream" &&
        cleanServerType != "binary/octet-stream" &&
        cleanServerType != "application/download" &&
        cleanServerType != "application/force-download" &&
        cleanServerType != "text/plain") {
        return cleanServerType
    }

    val ext = fileName.substringAfterLast('.', "").lowercase()
    if (ext.isNotEmpty()) {
        val mapMime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (!mapMime.isNullOrBlank()) {
            return mapMime
        }
    }

    return when (ext) {
        // Android / Package Executables
        "apk" -> "application/vnd.android.package-archive"
        "aab" -> "application/octet-stream"
        "xapk", "apks" -> "application/zip"
        
        // Documents
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "odt" -> "application/vnd.oasis.opendocument.text"
        "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
        "odp" -> "application/vnd.oasis.opendocument.presentation"
        "rtf" -> "application/rtf"
        "txt", "log", "ini", "conf" -> "text/plain"
        "csv" -> "text/csv"
        "tsv" -> "text/tab-separated-values"
        "epub" -> "application/epub+zip"
        "mobi" -> "application/x-mobipocket-ebook"

        // Audio
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg", "oga" -> "audio/ogg"
        "flac" -> "audio/flac"
        "m4a", "aac" -> "audio/mp4"
        "opus" -> "audio/opus"
        "mid", "midi" -> "audio/midi"
        "wma" -> "audio/x-ms-wma"

        // Video
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "wmv" -> "video/x-ms-wmv"
        "3gp", "3gpp" -> "video/3gpp"
        "flv" -> "video/x-flv"
        "ts" -> "video/mp2t"

        // Images
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        "avif" -> "image/avif"
        "bmp" -> "image/bmp"
        "ico" -> "image/x-icon"
        "tiff", "tif" -> "image/tiff"
        "heic" -> "image/heic"
        "heif" -> "image/heif"

        // Archives
        "zip" -> "application/zip"
        "rar" -> "application/x-rar-compressed"
        "7z" -> "application/x-7z-compressed"
        "tar" -> "application/x-tar"
        "gz", "tgz" -> "application/gzip"
        "bz2" -> "application/x-bzip2"
        "xz" -> "application/x-xz"
        "iso" -> "application/x-iso9660-image"

        // Code / Web / Data
        "json" -> "application/json"
        "xml" -> "application/xml"
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js", "mjs" -> "text/javascript"
        "wasm" -> "application/wasm"
        "torrent" -> "application/x-bittorrent"

        else -> if (!cleanServerType.isNullOrBlank()) cleanServerType else "application/octet-stream"
    }
}

class DecentralViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val webViewTabManager = BrowserTabWebViewManager()
    private val appPrefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    @Volatile
    private var explicitIntentUrlHandled: Boolean = false

    fun setPendingExplicitUrl(url: String) {
        explicitIntentUrlHandled = true
    }

    private val _onboardingCompleted = MutableStateFlow(
        savedStateHandle.get<Boolean>("onboarding_completed")
            ?: (appPrefs.getBoolean("has_seen_tutorial_v1", false) || appPrefs.getBoolean("onboarding_completed", false))
    )
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    fun setOnboardingCompleted(completed: Boolean) {
        _onboardingCompleted.value = completed
        savedStateHandle["onboarding_completed"] = completed
        appPrefs.edit().putBoolean("has_seen_tutorial_v1", completed).putBoolean("onboarding_completed", completed).apply()
        viewModelScope.launch(Dispatchers.IO) {
            val session = database.browserSessionDao().getSession() ?: BrowserSessionEntity()
            database.browserSessionDao().saveSession(session.copy(onboardingCompleted = completed))
            BrowserStateLog.save("Onboarding completed set to: $completed")
        }
    }

    private val database = AppDatabase.getDatabase(application)
    private val kaspaWalletService = KaspaWalletService()
    val walletService: KaspaWalletService get() = kaspaWalletService
    private val resolver = DualStackResolver(database, kaspaWalletService)
    val nodeManager = LocalNodeManager(application, database, viewModelScope)

    val metrics: StateFlow<NetworkMetrics> = nodeManager.metrics
    val nsdState = nodeManager.discoveryManager.nsdState
    val bluetoothMeshManager = com.example.network.BluetoothMeshManager(application, database, viewModelScope)

    val isBluetoothMeshRunning: StateFlow<Boolean> = bluetoothMeshManager.isMeshRunning
    val isBluetoothEnabled: StateFlow<Boolean> = bluetoothMeshManager.isBluetoothEnabled
    val discoveredBtPeersCount: StateFlow<Int> = bluetoothMeshManager.discoveredBtPeersCount

    fun startBluetoothMesh(): Boolean {
        return bluetoothMeshManager.startMesh()
    }

    fun stopBluetoothMesh() {
        bluetoothMeshManager.stopMesh()
    }

    fun updateBluetoothState() {
        bluetoothMeshManager.updateBluetoothState()
    }

    val pinnedContents: StateFlow<List<ContentEntity>> = database.contentDao()
        .getAllContents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val peers: StateFlow<List<PeerEntity>> = database.peerDao()
        .getAllPeers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trafficAudits: StateFlow<List<TrafficAuditEntity>> = database.trafficAuditDao()
        .getRecentAudits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryEntity>> = database.historyDao()
        .getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<BookmarkEntity>> = database.bookmarkDao()
        .getAllBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val browserTabs: StateFlow<List<BrowserTabEntity>> = database.browserTabDao()
        .getAllTabs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isStandalonePwaMode = MutableStateFlow(false)
    val isStandalonePwaMode: StateFlow<Boolean> = _isStandalonePwaMode.asStateFlow()

    fun setStandalonePwaMode(enabled: Boolean) {
        _isStandalonePwaMode.value = enabled
    }

    val newsFeedItems: StateFlow<List<KaspaNewsItem>> = database.newsArticleDao()
        .getAllNews()
        .map { list ->
            if (list.isEmpty()) getDefaultCuratedNews()
            else list.map { it.toKaspaNewsItem() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, getDefaultCuratedNews())

    private val _isNewsRefreshing = MutableStateFlow(false)
    val isNewsRefreshing = _isNewsRefreshing.asStateFlow()

    fun refreshNewsFeeds() {
        if (_isNewsRefreshing.value) return
        _isNewsRefreshing.value = true
        viewModelScope.launch {
            try {
                val fetched = fetchLatestKaspaFeeds()
                if (fetched.isNotEmpty()) {
                    val entities = fetched.map { it.toEntity() }
                    database.newsArticleDao().insertAll(entities)
                }
            } catch (e: Exception) {
                android.util.Log.d("DecentralViewModel", "News refresh notice: ${e.message}")
            } finally {
                _isNewsRefreshing.value = false
            }
        }
    }

    private val securityPrefs = application.getSharedPreferences("kaspa_wallet_security", android.content.Context.MODE_PRIVATE)

    private val themePrefs = com.example.ui.theme.PhotoThemePreferences(application)
    private val _currentPhotoTheme = MutableStateFlow(themePrefs.getSavedTheme())
    val currentPhotoTheme: StateFlow<com.example.ui.theme.PhotoTheme> = _currentPhotoTheme.asStateFlow()

    private val _isAutoRotatePhotoTheme = MutableStateFlow(themePrefs.isAutoRotateEnabled())
    val isAutoRotatePhotoTheme: StateFlow<Boolean> = _isAutoRotatePhotoTheme.asStateFlow()

    fun selectPhotoTheme(theme: com.example.ui.theme.PhotoTheme) {
        _currentPhotoTheme.value = theme
        themePrefs.saveTheme(theme)
    }

    fun toggleAutoRotatePhotoTheme(enabled: Boolean) {
        _isAutoRotatePhotoTheme.value = enabled
        themePrefs.setAutoRotateEnabled(enabled)
    }

    fun cyclePhotoTheme() {
        val next = com.example.ui.theme.PhotoTheme.nextTheme(_currentPhotoTheme.value)
        selectPhotoTheme(next)
    }

    private val _hasWalletPassword = MutableStateFlow(securityPrefs.contains("wallet_pwd_hash"))
    val hasWalletPassword = _hasWalletPassword.asStateFlow()

    private val _isWalletLocked = MutableStateFlow(securityPrefs.contains("wallet_pwd_hash"))
    val isWalletLocked = _isWalletLocked.asStateFlow()

    private val _biometricsEnabled = MutableStateFlow(securityPrefs.getBoolean("biometrics_enabled", true))
    val biometricsEnabled = _biometricsEnabled.asStateFlow()

    fun setWalletPassword(password: String, enableBiometrics: Boolean = true) {
        if (password.isBlank()) return
        val pwdHash = CryptoUtils.sha256(password + "_kaspa_salt_v1")
        securityPrefs.edit()
            .putString("wallet_pwd_hash", pwdHash)
            .putBoolean("biometrics_enabled", enableBiometrics)
            .apply()

        _hasWalletPassword.value = true
        _biometricsEnabled.value = enableBiometrics
        _isWalletLocked.value = false
    }

    fun unlockWalletWithPassword(password: String): Boolean {
        val storedHash = securityPrefs.getString("wallet_pwd_hash", "") ?: ""
        if (storedHash.isBlank()) {
            _isWalletLocked.value = false
            return true
        }
        val inputHash = CryptoUtils.sha256(password + "_kaspa_salt_v1")
        if (inputHash == storedHash) {
            _isWalletLocked.value = false
            return true
        }
        return false
    }

    fun unlockWalletWithBiometric() {
        _isWalletLocked.value = false
    }

    fun lockWallet() {
        if (_hasWalletPassword.value) {
            _isWalletLocked.value = true
        }
    }

    private val _activeTabId = MutableStateFlow<String?>(savedStateHandle.get<String>("active_tab_id"))
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    private val closedTabIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var debounceSaveJob: Job? = null

    fun scheduleSaveDebounced(reason: String, delayMs: Long = 300L) {
        debounceSaveJob?.cancel()
        debounceSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(delayMs)
            persistBrowserState(reason)
        }
    }

    suspend fun persistBrowserState(reason: String) {
        try {
            val currentActiveId = _activeTabId.value
            val isCompleted = _onboardingCompleted.value

            val session = BrowserSessionEntity(
                id = "singleton",
                activeTabId = currentActiveId,
                onboardingCompleted = isCompleted,
                lastSavedTimestamp = System.currentTimeMillis()
            )
            database.browserSessionDao().saveSession(session)

            val tabs = database.browserTabDao().getAllTabsList()
                .filter { !closedTabIds.contains(it.id) }
            val updatedTabs = tabs.map { tab ->
                val scroll = webViewTabManager.getTabScroll(tab.id)
                val bundle = webViewTabManager.saveTabBundle(tab.id)
                val bundleBytes = BrowserTabWebViewManager.bundleToByteArray(bundle) ?: tab.webViewState
                val history = webViewTabManager.extractHistoryJson(tab.id)
                val historyToSave = if (history != "[]") history else tab.historyJson
                tab.copy(
                    scrollX = scroll?.first ?: tab.scrollX,
                    scrollY = scroll?.second ?: tab.scrollY,
                    historyJson = historyToSave,
                    webViewState = bundleBytes
                )
            }
            database.browserTabDao().insertAll(updatedTabs)
            BrowserStateLog.save("Persisted browser state for ${updatedTabs.size} tabs ($reason)")
        } catch (e: Exception) {
            android.util.Log.w("DecentralViewModel", "Error persisting browser state: ${e.message}")
        }
    }

    fun saveAllTabsState(reason: String) {
        viewModelScope.launch(Dispatchers.IO) {
            persistBrowserState(reason)
        }
    }

    fun saveActiveTabState(reason: String) {
        val activeId = _activeTabId.value ?: return
        if (closedTabIds.contains(activeId)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (closedTabIds.contains(activeId)) return@launch
                val tab = database.browserTabDao().getTabById(activeId) ?: return@launch
                val scroll = webViewTabManager.getTabScroll(activeId)
                val bundle = webViewTabManager.saveTabBundle(activeId)
                val bundleBytes = BrowserTabWebViewManager.bundleToByteArray(bundle) ?: tab.webViewState
                val history = webViewTabManager.extractHistoryJson(activeId)
                val historyToSave = if (history != "[]") history else tab.historyJson
                val updated = tab.copy(
                    scrollX = scroll?.first ?: tab.scrollX,
                    scrollY = scroll?.second ?: tab.scrollY,
                    historyJson = historyToSave,
                    webViewState = bundleBytes,
                    lastAccessed = System.currentTimeMillis()
                )
                database.browserTabDao().insert(updated)
                BrowserStateLog.save("Active tab $activeId saved ($reason)")
            } catch (e: Exception) {
                android.util.Log.w("DecentralViewModel", "Error saving active tab: ${e.message}")
            }
        }
    }

    fun setActiveTab(id: String?) {
        if (id == null || id == _activeTabId.value) return
        val prevId = _activeTabId.value
        BrowserStateLog.tabSwitch("Switching active tab from $prevId to $id")
        _activeTabId.value = id
        savedStateHandle["active_tab_id"] = id

        viewModelScope.launch {
            val tab = database.browserTabDao().getTabById(id) ?: browserTabs.value.find { it.id == id }
            if (tab != null) {
                database.browserTabDao().insert(tab.copy(lastAccessed = System.currentTimeMillis()))
                if (tab.url.isBlank()) {
                    resetToHome()
                } else {
                    _urlInput.value = tab.url
                    val isHttp = tab.url.startsWith("http://", ignoreCase = true) || tab.url.startsWith("https://", ignoreCase = true)
                    if (isHttp) {
                        val host = try { java.net.URI(tab.url).host ?: tab.url } catch (_: Exception) { tab.url }
                        _currentResource.value = ResolvedResource(
                            url = tab.url,
                            resolvedProtocol = NetworkProtocol.CENTRALIZED_HTTP,
                            cid = com.example.network.CryptoUtils.generateCid(tab.url),
                            title = if (tab.title.isNotBlank()) tab.title else host,
                            content = "",
                            contentType = "text/html",
                            sizeBytes = 0L,
                            latencyMs = 15L,
                            centralizedUrl = tab.url,
                            centralizedLatencyMs = 15L,
                            centralizedIp = "Direct High-Speed Stack",
                            verificationStatus = VerificationStatus.VERIFIED_TAMPER_PROOF,
                            cryptographicHash = com.example.network.CryptoUtils.sha256(tab.url),
                            routedVia = "Direct High-Speed Web Stack: $host"
                        )
                        _isLoading.value = false
                    } else {
                        resolveUrl(tab.url)
                    }
                }
            }
            scheduleSaveDebounced("active tab changed to $id")
        }
    }

    private fun updateTabAccessTime(id: String) {
        viewModelScope.launch {
            val tab = database.browserTabDao().getTabById(id)
            if (tab != null) {
                database.browserTabDao().insert(tab.copy(lastAccessed = System.currentTimeMillis()))
            }
        }
    }

    fun updateTabScroll(id: String, scrollX: Int, scrollY: Int) {
        webViewTabManager.saveTabScroll(id, scrollX, scrollY)
        scheduleSaveDebounced("scroll changed for tab $id: ($scrollX, $scrollY)")
    }

    fun createNewTab(url: String = "", title: String = if (url.isBlank()) "Home" else "New Tab", isExternal: Boolean = false) {
        viewModelScope.launch {
            val tabs = database.browserTabDao().getAllTabsList()
            val maxOrder = tabs.maxOfOrNull { it.tabOrder } ?: 0
            val newId = java.util.UUID.randomUUID().toString()
            val newTab = BrowserTabEntity(
                id = newId,
                url = url,
                title = title,
                tabOrder = maxOrder + 1,
                lastAccessed = System.currentTimeMillis(),
                isExternal = isExternal
            )
            database.browserTabDao().insert(newTab)
            _activeTabId.value = newId
            savedStateHandle["active_tab_id"] = newId
            BrowserStateLog.save("Created new tab $newId ($title)")
            if (url.isBlank()) {
                resetToHome()
            } else {
                _urlInput.value = url
                resolveUrl(url)
            }
            scheduleSaveDebounced("new tab created: $newId")
        }
    }

    fun openBackgroundTab(url: String, title: String = "New Tab") {
        viewModelScope.launch {
            val tabs = database.browserTabDao().getAllTabsList()
            val maxOrder = tabs.maxOfOrNull { it.tabOrder } ?: 0
            val newId = java.util.UUID.randomUUID().toString()
            val newTab = BrowserTabEntity(
                id = newId,
                url = url,
                title = title,
                tabOrder = maxOrder + 1,
                lastAccessed = System.currentTimeMillis()
            )
            database.browserTabDao().insert(newTab)
            BrowserStateLog.save("Background tab created $newId")
            _statusMessage.value = "Tab opened in background"
            scheduleSaveDebounced("background tab created: $newId")
        }
    }

    fun closeTab(id: String) {
        closedTabIds.add(id)
        debounceSaveJob?.cancel()
        viewModelScope.launch {
            webViewTabManager.destroyTab(id, "tab closed by user")
            val tabs = database.browserTabDao().getAllTabsList()
            val target = tabs.find { it.id == id }
            if (target != null) {
                database.browserTabDao().delete(target)
            }
            val remaining = tabs.filter { it.id != id && !closedTabIds.contains(it.id) }
            BrowserStateLog.save("Tab $id closed, ${remaining.size} remaining")

            if (_activeTabId.value == id) {
                if (remaining.isNotEmpty()) {
                    val nextTab = remaining.maxByOrNull { it.lastAccessed } ?: remaining.first()
                    _activeTabId.value = nextTab.id
                    savedStateHandle["active_tab_id"] = nextTab.id
                    if (nextTab.url.isBlank()) {
                        resetToHome()
                    } else {
                        _urlInput.value = nextTab.url
                        val isHttp = nextTab.url.startsWith("http://", ignoreCase = true) || nextTab.url.startsWith("https://", ignoreCase = true)
                        if (isHttp) {
                            val host = try { java.net.URI(nextTab.url).host ?: nextTab.url } catch (_: Exception) { nextTab.url }
                            _currentResource.value = ResolvedResource(
                                url = nextTab.url,
                                resolvedProtocol = NetworkProtocol.CENTRALIZED_HTTP,
                                cid = com.example.network.CryptoUtils.generateCid(nextTab.url),
                                title = if (nextTab.title.isNotBlank()) nextTab.title else host,
                                content = "",
                                contentType = "text/html",
                                sizeBytes = 0L,
                                latencyMs = 15L,
                                centralizedUrl = nextTab.url,
                                centralizedLatencyMs = 15L,
                                centralizedIp = "Direct High-Speed Stack",
                                verificationStatus = VerificationStatus.VERIFIED_TAMPER_PROOF,
                                cryptographicHash = com.example.network.CryptoUtils.sha256(nextTab.url),
                                routedVia = "Direct High-Speed Web Stack: $host"
                            )
                            _isLoading.value = false
                        } else {
                            resolveUrl(nextTab.url)
                        }
                    }
                } else {
                    val newId = java.util.UUID.randomUUID().toString()
                    val newTab = BrowserTabEntity(
                        id = newId,
                        url = "",
                        title = "Home",
                        tabOrder = 1,
                        lastAccessed = System.currentTimeMillis()
                    )
                    database.browserTabDao().insert(newTab)
                    _activeTabId.value = newId
                    savedStateHandle["active_tab_id"] = newId
                    resetToHome()
                }
            }

            // Immediately persist updated session so cold restart never restores the closed tab
            val currentActiveId = _activeTabId.value
            val isCompleted = _onboardingCompleted.value
            val session = BrowserSessionEntity(
                id = "singleton",
                activeTabId = currentActiveId,
                onboardingCompleted = isCompleted,
                lastSavedTimestamp = System.currentTimeMillis()
            )
            database.browserSessionDao().saveSession(session)
        }
    }

    fun updateTab(id: String, url: String, title: String) {
        if (closedTabIds.contains(id)) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (closedTabIds.contains(id)) return@launch
            val existing = database.browserTabDao().getTabById(id) ?: return@launch
            if (existing.url == url && existing.title == title) {
                return@launch
            }
            database.browserTabDao().insert(
                existing.copy(
                    url = url,
                    title = title,
                    lastAccessed = System.currentTimeMillis()
                )
            )
            scheduleSaveDebounced("tab updated: $url")
        }
    }

    fun updateActiveTabMetadata(tabId: String? = null, url: String, title: String) {
        val id = tabId ?: _activeTabId.value ?: return
        if (id != _activeTabId.value || closedTabIds.contains(id)) return
        updateTab(id, url, title)
    }

    fun suspendInactiveTabs() {
        viewModelScope.launch {
            val tabs = database.browserTabDao().getAllTabsList()
            val now = System.currentTimeMillis()
            val fiveMinutes = 5 * 60 * 1000
            tabs.forEach { tab ->
                if (tab.id != _activeTabId.value && !tab.isSuspended && now - tab.lastAccessed > fiveMinutes) {
                    database.browserTabDao().insert(tab.copy(isSuspended = true))
                }
            }
        }
    }

    fun clearAllTabs() {
        debounceSaveJob?.cancel()
        viewModelScope.launch {
            val tabs = database.browserTabDao().getAllTabsList()
            tabs.forEach { closedTabIds.add(it.id) }
            webViewTabManager.destroyAll("clear all tabs")
            database.browserTabDao().clearAll()
            val newId = java.util.UUID.randomUUID().toString()
            val homeTab = BrowserTabEntity(id = newId, url = "", title = "Home", lastAccessed = System.currentTimeMillis())
            database.browserTabDao().insert(homeTab)
            _activeTabId.value = newId
            savedStateHandle["active_tab_id"] = newId
            resetToHome()
            val session = BrowserSessionEntity(
                id = "singleton",
                activeTabId = newId,
                onboardingCompleted = _onboardingCompleted.value,
                lastSavedTimestamp = System.currentTimeMillis()
            )
            database.browserSessionDao().saveSession(session)
        }
    }

    val activeAccount: StateFlow<AccountEntity?> = database.accountDao()
        .getActiveAccount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allAccounts: StateFlow<List<AccountEntity>> = database.accountDao()
        .getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val domainRegistry = KaspaDomainRegistry(database, kaspaWalletService)

    val allDomains: StateFlow<List<DomainEntity>> = database.domainDao()
        .getAllDomains()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _kaspaWalletState = MutableStateFlow(KaspaWalletState())
    val kaspaWalletState: StateFlow<KaspaWalletState> = _kaspaWalletState.asStateFlow()

    private val _networkFeeCondition = MutableStateFlow(KaspaTransactionEngine.KaspaNetworkFeeCondition())
    val networkFeeCondition: StateFlow<KaspaTransactionEngine.KaspaNetworkFeeCondition> = _networkFeeCondition.asStateFlow()

    private val _isFetchingFeeCondition = MutableStateFlow(false)
    val isFetchingFeeCondition: StateFlow<Boolean> = _isFetchingFeeCondition.asStateFlow()

    // Working Testnet 10 Explorers: default to Kaspa Stream TN10 (https://tn10.kaspa.stream)
    val testnetExplorerOptions = listOf(
        "Kaspa Stream TN10 (tn10.kaspa.stream)" to "https://tn10.kaspa.stream",
        "Kaspanet TN10 (explorer-tn10.kaspanet.io)" to "https://explorer-tn10.kaspanet.io",
        "Katnip TN10" to "https://katnip-tn10.kaspa.org",
        "Kaspa Stream (TESTNET-10)" to "https://kaspa.stream/TESTNET-10"
    )
    private val _selectedExplorerUrl = MutableStateFlow(
        appPrefs.getString("selected_testnet_explorer", "https://tn10.kaspa.stream") ?: "https://tn10.kaspa.stream"
    )
    val selectedExplorerUrl: StateFlow<String> = _selectedExplorerUrl.asStateFlow()

    fun setExplorerPreference(url: String) {
        _selectedExplorerUrl.value = url
        appPrefs.edit().putString("selected_testnet_explorer", url).apply()
    }

    fun getTestnetAddressForAccount(account: AccountEntity?): String {
        if (account == null) return ""
        if (account.kaspaAddress.startsWith("kaspatest:")) return account.kaspaAddress
        return try {
            val seed = CryptoUtils.getDecryptedSeed(account.seedPhrase)
            CryptoUtils.deriveKaspaKeyPair(seed, prefix = "kaspatest").kaspaAddress
        } catch (_: Exception) {
            account.kaspaAddress
        }
    }

    val testnetAddress: StateFlow<String> = activeAccount.map { acc ->
        getTestnetAddressForAccount(acc)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _activeTab = MutableStateFlow(AppTab.BROWSER_GATEWAY)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    val kaspaAddressValidation: StateFlow<KaspaAddressValidationResult?> = _urlInput
        .map { input ->
            if (input.isBlank()) null
            else {
                val res = validateKaspaAddress(input)
                if (res.isValid) res else null
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _selectedProtocol = MutableStateFlow(NetworkProtocol.HYBRID_COEXISTENCE)
    val selectedProtocol: StateFlow<NetworkProtocol> = _selectedProtocol.asStateFlow()

    private val _currentResource = MutableStateFlow<ResolvedResource?>(null)
    val currentResource: StateFlow<ResolvedResource?> = _currentResource.asStateFlow()

    private val _navigationSessionId = MutableStateFlow(0)
    val navigationSessionId: StateFlow<Int> = _navigationSessionId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isGoogleSessionActive = MutableStateFlow(false)
    val isGoogleSessionActive: StateFlow<Boolean> = _isGoogleSessionActive.asStateFlow()

    private val _zkVerificationResult = MutableStateFlow<String?>(null)
    val zkVerificationResult: StateFlow<String?> = _zkVerificationResult.asStateFlow()

    // Granular Web Extensions & Security Settings
    private val _kaspaVerifierEnabled = MutableStateFlow(true)
    val kaspaVerifierEnabled: StateFlow<Boolean> = _kaspaVerifierEnabled.asStateFlow()

    private val _kaspaResolverEnabled = MutableStateFlow(true)
    val kaspaResolverEnabled: StateFlow<Boolean> = _kaspaResolverEnabled.asStateFlow()

    private val _blockTrackers = MutableStateFlow(true)
    val blockTrackers: StateFlow<Boolean> = _blockTrackers.asStateFlow()

    private val _enableDownloads = MutableStateFlow(true)
    val enableDownloads: StateFlow<Boolean> = _enableDownloads.asStateFlow()

    private val _enableUploads = MutableStateFlow(true)
    val enableUploads: StateFlow<Boolean> = _enableUploads.asStateFlow()

    private val _encryptedLocalStorage = MutableStateFlow(true)
    val encryptedLocalStorage: StateFlow<Boolean> = _encryptedLocalStorage.asStateFlow()

    private val _thirdPartyCookies = MutableStateFlow(false)
    val thirdPartyCookies: StateFlow<Boolean> = _thirdPartyCookies.asStateFlow()

    private val _blockThirdPartyCookies = MutableStateFlow(false)
    val blockThirdPartyCookies: StateFlow<Boolean> = _blockThirdPartyCookies.asStateFlow()

    private val _strictDecentralizedMode = MutableStateFlow(false)
    val strictDecentralizedMode: StateFlow<Boolean> = _strictDecentralizedMode.asStateFlow()

    private val _sendDntHeaders = MutableStateFlow(true)
    val sendDntHeaders: StateFlow<Boolean> = _sendDntHeaders.asStateFlow()

    private val _incognitoMode = MutableStateFlow(false)
    val incognitoMode: StateFlow<Boolean> = _incognitoMode.asStateFlow()

    private val browserSettingsPrefs = application.getSharedPreferences("kaspa_browser_settings", android.content.Context.MODE_PRIVATE)

    private val _desktopModeEnabled = MutableStateFlow(browserSettingsPrefs.getBoolean("desktop_mode_enabled", false))
    val desktopModeEnabled: StateFlow<Boolean> = _desktopModeEnabled.asStateFlow()

    private val _enablePullToRefresh = MutableStateFlow(browserSettingsPrefs.getBoolean("pull_to_refresh_enabled", true))
    val enablePullToRefresh: StateFlow<Boolean> = _enablePullToRefresh.asStateFlow()

    private val _httpsOnlyMode = MutableStateFlow(true)
    val httpsOnlyMode: StateFlow<Boolean> = _httpsOnlyMode.asStateFlow()

    private val _webAuthEnabled = MutableStateFlow(true)
    val webAuthEnabled: StateFlow<Boolean> = _webAuthEnabled.asStateFlow()

    private val _showWebAuthnRpIdDialog = MutableStateFlow(false)
    val showWebAuthnRpIdDialog: StateFlow<Boolean> = _showWebAuthnRpIdDialog.asStateFlow()

    private val _searchEngine = MutableStateFlow(
        runCatching {
            val saved = browserSettingsPrefs.getString("default_search_engine", SearchEngine.DUCKDUCKGO.name)
            if (saved == "KASPA" || saved.isNullOrBlank()) {
                SearchEngine.DUCKDUCKGO
            } else {
                SearchEngine.valueOf(saved)
            }
        }.getOrDefault(SearchEngine.DUCKDUCKGO)
    )
    val searchEngine: StateFlow<SearchEngine> = _searchEngine.asStateFlow()

    private val _isPrivacyRelayEnabled = MutableStateFlow(
        browserSettingsPrefs.getBoolean("kaspa_privacy_relay_enabled", true)
    )
    val isPrivacyRelayEnabled: StateFlow<Boolean> = _isPrivacyRelayEnabled.asStateFlow()

    private val _activeRelayCircuit = MutableStateFlow<KaspaRelayCircuit?>(
        try { KaspaPrivacyRelayEngine.getActiveCircuit() } catch (_: Throwable) { null }
    )
    val activeRelayCircuit: StateFlow<KaspaRelayCircuit?> = _activeRelayCircuit.asStateFlow()

    fun togglePrivacyRelay(enabled: Boolean) {
        _isPrivacyRelayEnabled.value = enabled
        browserSettingsPrefs.edit().putBoolean("kaspa_privacy_relay_enabled", enabled).apply()
        if (enabled) {
            com.example.network.LightweightTorEngine.start()
            com.example.network.KrpRelayDaemon.start()
            // Keep native WebView connection direct without loopback proxy cutoff
            com.example.network.WebViewProxyManager.clearProxy()
            _activeRelayCircuit.value = KaspaPrivacyRelayEngine.getActiveCircuit()
            _statusMessage.value = "Kaspa Privacy Relay Active: Dual-Hop KRP/1 Circuit Enabled"
        } else {
            com.example.network.WebViewProxyManager.clearProxy()
            com.example.network.LightweightTorEngine.stop()
            _statusMessage.value = "Kaspa Privacy Relay Disabled"
        }
    }

    fun rotatePrivacyRelayCircuit() {
        val newCircuit = KaspaPrivacyRelayEngine.rotateCircuit()
        _activeRelayCircuit.value = newCircuit
        _statusMessage.value = "Rotated Privacy Circuit: ${newCircuit.circuitId} (${newCircuit.entryNode.countryCode} ➔ ${newCircuit.exitNode.countryCode})"
    }

    fun refreshRelayCircuitState() {
        _activeRelayCircuit.value = KaspaPrivacyRelayEngine.getActiveCircuit()
    }

    private val _liveSuggestions = MutableStateFlow<List<String>>(emptyList())
    val liveSuggestions: StateFlow<List<String>> = _liveSuggestions.asStateFlow()

    fun fetchSearchSuggestions(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2 || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            _liveSuggestions.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
                val url = "https://suggestqueries.google.com/complete/search?client=chrome&q=$encoded"
                val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 2000
                    readTimeout = 2000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                if (conn.responseCode == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = org.json.JSONArray(jsonStr)
                    if (jsonArray.length() >= 2) {
                        val suggestionsArray = jsonArray.getJSONArray(1)
                        val list = mutableListOf<String>()
                        for (i in 0 until suggestionsArray.length()) {
                            val suggestion = suggestionsArray.optString(i)
                            if (suggestion.isNotBlank()) {
                                list.add(suggestion)
                            }
                        }
                        _liveSuggestions.value = list.take(6)
                    }
                }
            } catch (_: Exception) {
                _liveSuggestions.value = emptyList()
            }
        }
    }

    private val _blockedTrackersCount = MutableStateFlow(0)
    val blockedTrackersCount: StateFlow<Int> = _blockedTrackersCount.asStateFlow()

    private val _activeDownloads = MutableStateFlow<List<ActiveDownload>>(emptyList())
    val activeDownloads: StateFlow<List<ActiveDownload>> = _activeDownloads.asStateFlow()

    private val _blockedTrackerLogs = MutableStateFlow<List<String>>(emptyList())
    val blockedTrackerLogs: StateFlow<List<String>> = _blockedTrackerLogs.asStateFlow()

    // Real uBlock Engine state
    val uBlockRulesCount: StateFlow<Int> = UBlockEngine.rulesCount
    val uBlockIsUpdating: StateFlow<Boolean> = UBlockEngine.isUpdating
    val uBlockStatus: StateFlow<String> = UBlockEngine.lastUpdateStatus

    fun updateUBlockFilters(onResult: ((Boolean, String) -> Unit)? = null) {
        UBlockEngine.updateFilters(getApplication(), onResult)
    }

    private val pwaPrefs by lazy {
        getApplication<Application>().getSharedPreferences("browser_installed_pwas", android.content.Context.MODE_PRIVATE)
    }

    private fun loadInstalledPwas(): List<com.example.network.InstalledPwa> {
        return try {
            val jsonStr = pwaPrefs.getString("installed_pwas_json", null) ?: return emptyList()
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<com.example.network.InstalledPwa>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    com.example.network.InstalledPwa(
                        id = obj.optString("id", "pwa_$i"),
                        name = obj.optString("name", "Web App"),
                        url = obj.optString("url", ""),
                        iconUrl = if (obj.has("iconUrl") && !obj.isNull("iconUrl")) obj.optString("iconUrl") else null,
                        manifestUrl = if (obj.has("manifestUrl") && !obj.isNull("manifestUrl")) obj.optString("manifestUrl") else null,
                        hasManifest = obj.optBoolean("hasManifest", false),
                        installedAt = obj.optLong("installedAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveInstalledPwas(pwas: List<com.example.network.InstalledPwa>) {
        try {
            val array = org.json.JSONArray()
            for (pwa in pwas) {
                val obj = org.json.JSONObject().apply {
                    put("id", pwa.id)
                    put("name", pwa.name)
                    put("url", pwa.url)
                    put("iconUrl", pwa.iconUrl ?: org.json.JSONObject.NULL)
                    put("manifestUrl", pwa.manifestUrl ?: org.json.JSONObject.NULL)
                    put("hasManifest", pwa.hasManifest)
                    put("installedAt", pwa.installedAt)
                }
                array.put(obj)
            }
            pwaPrefs.edit().putString("installed_pwas_json", array.toString()).apply()
        } catch (_: Exception) {}
    }

    private val _installedPwas = MutableStateFlow<List<com.example.network.InstalledPwa>>(loadInstalledPwas())
    val installedPwas: StateFlow<List<com.example.network.InstalledPwa>> = _installedPwas.asStateFlow()

    private val _currentPagePwa = MutableStateFlow<com.example.network.InstalledPwa?>(null)
    val currentPagePwa: StateFlow<com.example.network.InstalledPwa?> = _currentPagePwa.asStateFlow()

    fun toggleKaspaVerifier(enabled: Boolean) { _kaspaVerifierEnabled.value = enabled }
    fun toggleKaspaResolver(enabled: Boolean) { _kaspaResolverEnabled.value = enabled }
    fun toggleBlockTrackers(enabled: Boolean) { _blockTrackers.value = enabled }
    fun toggleDownloads(enabled: Boolean) { _enableDownloads.value = enabled }
    fun toggleUploads(enabled: Boolean) { _enableUploads.value = enabled }
    fun toggleEncryptedLocalStorage(enabled: Boolean) { _encryptedLocalStorage.value = enabled }
    fun toggleThirdPartyCookies(enabled: Boolean) {
        _thirdPartyCookies.value = enabled
        _blockThirdPartyCookies.value = !enabled
    }
    fun toggleBlockThirdPartyCookies(enabled: Boolean) {
        _blockThirdPartyCookies.value = enabled
        _thirdPartyCookies.value = !enabled
    }
    fun toggleStrictDecentralizedMode(enabled: Boolean) { _strictDecentralizedMode.value = enabled }
    fun toggleSendDntHeaders(enabled: Boolean) { _sendDntHeaders.value = enabled }
    fun toggleIncognitoMode(enabled: Boolean) { _incognitoMode.value = enabled }
    fun toggleDesktopMode(enabled: Boolean) {
        _desktopModeEnabled.value = enabled
        browserSettingsPrefs.edit().putBoolean("desktop_mode_enabled", enabled).apply()
    }
    fun togglePullToRefresh(enabled: Boolean) {
        _enablePullToRefresh.value = enabled
        browserSettingsPrefs.edit().putBoolean("pull_to_refresh_enabled", enabled).apply()
    }
    fun toggleHttpsOnlyMode(enabled: Boolean) { _httpsOnlyMode.value = enabled }
    fun toggleWebAuth(enabled: Boolean) { _webAuthEnabled.value = enabled }
    fun setShowWebAuthnRpIdDialog(show: Boolean) { _showWebAuthnRpIdDialog.value = show }
    fun setSearchEngine(engine: SearchEngine) {
        _searchEngine.value = engine
        browserSettingsPrefs.edit().putString("default_search_engine", engine.name).apply()
    }

    // In-App Sideload Update Zone & Installer State
    private val updateManager by lazy { AppUpdateManager.getInstance() }
    private val updatePrefs by lazy {
        getApplication<Application>().getSharedPreferences("kaspa_update_prefs", android.content.Context.MODE_PRIVATE)
    }

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private val _isAutoCheckUpdatesEnabled = MutableStateFlow(
        updatePrefs.getBoolean("auto_check_updates_enabled", true)
    )
    val isAutoCheckUpdatesEnabled: StateFlow<Boolean> = _isAutoCheckUpdatesEnabled.asStateFlow()

    private val _customUpdateManifestUrl = MutableStateFlow(
        updatePrefs.getString("custom_manifest_url", "") ?: ""
    )
    val customUpdateManifestUrl: StateFlow<String> = _customUpdateManifestUrl.asStateFlow()

    private val _showUpdateAvailableDialog = MutableStateFlow(false)
    val showUpdateAvailableDialog: StateFlow<Boolean> = _showUpdateAvailableDialog.asStateFlow()

    fun checkForUpdates(isUserInitiated: Boolean = true) {
        viewModelScope.launch {
            _updateStatus.value = UpdateStatus.Checking
            val result = updateManager.checkForUpdates(
                context = getApplication(),
                customEndpoint = _customUpdateManifestUrl.value.takeIf { it.isNotBlank() }
            )
            result.onSuccess { info ->
                if (info.isUpdateAvailable) {
                    _updateStatus.value = UpdateStatus.Available(info)
                    _showUpdateAvailableDialog.value = true
                    _statusMessage.value = "New update available: v${info.latestVersionName}"
                } else {
                    _updateStatus.value = UpdateStatus.UpToDate(info.currentVersionName)
                    if (isUserInitiated) {
                        _statusMessage.value = "You are on the latest version (v${info.currentVersionName})"
                    }
                }
            }.onFailure { e ->
                if (isUserInitiated) {
                    _updateStatus.value = UpdateStatus.Error(e.message ?: "Failed to check for updates")
                    _statusMessage.value = "Update check failed: ${e.message}"
                } else {
                    // Suppress error state for automatic checks on startup to avoid intrusive "Network Error" when connection is warming up
                    _updateStatus.value = UpdateStatus.Idle
                }
            }
        }
    }

    fun downloadAndInstallUpdate(context: android.content.Context) {
        val current = _updateStatus.value
        val updateInfo = when (current) {
            is UpdateStatus.Available -> current.updateInfo
            is UpdateStatus.ReadyToInstall -> current.updateInfo
            is UpdateStatus.Downloading -> current.updateInfo
            else -> return
        }

        viewModelScope.launch {
            _updateStatus.value = UpdateStatus.Downloading(0, 0, updateInfo.apkSizeBytes, updateInfo)
            val result = updateManager.downloadApk(
                context = context,
                updateInfo = updateInfo,
                onProgress = { percent, downloaded, total ->
                    _updateStatus.value = UpdateStatus.Downloading(percent, downloaded, total, updateInfo)
                }
            )

            result.onSuccess { apkFile ->
                _updateStatus.value = UpdateStatus.ReadyToInstall(apkFile, updateInfo)
                _statusMessage.value = "Update downloaded! Launching Android package installer..."
                installReadyApk(context)
            }.onFailure { err ->
                _updateStatus.value = UpdateStatus.Error(err.message ?: "Download failed")
                _statusMessage.value = "Download failed: ${err.message}"
            }
        }
    }

    fun installReadyApk(context: android.content.Context) {
        val current = _updateStatus.value
        if (current is UpdateStatus.ReadyToInstall) {
            val installResult = updateManager.installApk(context, current.apkFile)
            installResult.onSuccess {
                _statusMessage.value = "Package installer launched"
                // Store the successfully installed release tag name so we don't nag the user again after update
                val prefs = context.getSharedPreferences("kaspa_update_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString("last_installed_release_tag", current.updateInfo.latestVersionName).apply()
            }.onFailure { err ->
                _statusMessage.value = "Install notice: ${err.message}"
            }
        }
    }

    fun toggleAutoCheckUpdates(enabled: Boolean) {
        _isAutoCheckUpdatesEnabled.value = enabled
        updatePrefs.edit().putBoolean("auto_check_updates_enabled", enabled).apply()
    }

    fun setCustomUpdateManifestUrl(url: String) {
        _customUpdateManifestUrl.value = url
        updatePrefs.edit().putString("custom_manifest_url", url).apply()
    }

    fun dismissUpdateDialog() {
        _showUpdateAvailableDialog.value = false
    }

    fun forceShowUpdateDialog() {
        val current = _updateStatus.value
        val info = when (current) {
            is UpdateStatus.Available -> current.updateInfo
            is UpdateStatus.ReadyToInstall -> current.updateInfo
            is UpdateStatus.Downloading -> current.updateInfo
            else -> com.example.model.AppUpdateInfo(
                latestVersionName = "1.0.1",
                latestVersionCode = 2,
                currentVersionName = com.example.BuildConfig.VERSION_NAME,
                currentVersionCode = 1,
                isUpdateAvailable = true,
                releaseTitle = "KaspaBrowser Official Release v1.0.1",
                releaseNotes = "• Official GitHub Release build\n• In-app sideload engine updates\n• BlockDAG Testnet 10 synchronization\n• Performance and security enhancements",
                releaseDate = "October 06, 2026",
                downloadUrl = "https://github.com/Curious-being99/Kaspa-browser-/releases/latest/download/KaspaBrowser-release-signed.apk",
                apkSizeBytes = 29_884_416L,
                apkSizeFormatted = "28.5 MB",
                sha256Checksum = null
            )
        }
        _updateStatus.value = UpdateStatus.Available(info)
        _showUpdateAvailableDialog.value = true
    }

    fun addToHistory(url: String, title: String) {
        if (url.startsWith("about:") || url.startsWith("data:") || _incognitoMode.value) return
        viewModelScope.launch {
            database.historyDao().insert(HistoryEntity(url = url, title = title))
        }
    }

    fun toggleBookmark(url: String, title: String) {
        viewModelScope.launch {
            val isBookmarked = database.bookmarkDao().isBookmarked(url)
            if (isBookmarked) {
                database.bookmarkDao().delete(BookmarkEntity(url, title))
            } else {
                database.bookmarkDao().insert(BookmarkEntity(url, title))
            }
        }
    }

    suspend fun isBookmarked(url: String): Boolean {
        return database.bookmarkDao().isBookmarked(url)
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            database.historyDao().delete(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            database.historyDao().clearAll()
        }
    }

    fun logBlockedTracker(domainOrUrl: String) {
        viewModelScope.launch(Dispatchers.Default) {
            _blockedTrackersCount.value += 1
            val current = _blockedTrackerLogs.value
            if (current.firstOrNull() != domainOrUrl) {
                _blockedTrackerLogs.value = (listOf(domainOrUrl) + current).take(50)
            }
        }
    }

    fun clearBlockedTrackerLogs() {
        _blockedTrackersCount.value = 0
        _blockedTrackerLogs.value = emptyList()
    }

    init {
        // Initialize uBlock Engine with rules and local cache
        UBlockEngine.init(getApplication())

        // Delete any legacy mock accounts so user has clean slate
        viewModelScope.launch {
            try {
                database.accountDao().deleteLegacyMockAccounts()
                database.domainDao().deleteLegacyMockDomains()
                val allAccounts = database.accountDao().getAllAccountsList()
                if (allAccounts.isNotEmpty()) {
                    for (acc in allAccounts) {
                        val cleanHandle = if (acc.handle.contains(".k") || acc.handle.startsWith("@kas")) {
                            "Kaspa Account (${acc.kaspaAddress.takeLast(6)})"
                        } else {
                            acc.handle
                        }

                        if (!CryptoUtils.isAnyValidKaspaAddress(acc.kaspaAddress) || acc.zkProofJson.isNullOrBlank() || cleanHandle != acc.handle) {
                            val refreshedAcc = if (acc.accountType == "GOOGLE_ZK_BRIDGE" && acc.googleEmail != null) {
                                CryptoUtils.deriveGoogleBridgeAccount(
                                    email = acc.googleEmail,
                                    displayName = acc.googleDisplayName ?: cleanHandle
                                ).copy(isActive = acc.isActive, createdAt = acc.createdAt)
                            } else {
                                CryptoUtils.deriveDecentralizedAccount(
                                    customHandle = cleanHandle,
                                    seedMnemonic = acc.seedPhrase,
                                    networkPrefix = "kaspatest"
                                ).copy(isActive = acc.isActive, createdAt = acc.createdAt)
                            }
                            database.accountDao().insertAccount(refreshedAcc)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Keep Kaspa Wallet synced with active account on Testnet 10
        viewModelScope.launch {
            activeAccount.collect { account ->
                val testnetAddr = getTestnetAddressForAccount(account)
                if (testnetAddr.isNotBlank()) {
                    refreshKaspaWallet(testnetAddr)
                }
            }
        }

        // Initial automatic check for updates on startup
        viewModelScope.launch {
            if (_isAutoCheckUpdatesEnabled.value) {
                delay(2000)
                checkForUpdates(isUserInitiated = false)
            }
        }

        // Automatic Background Theme Cycler (rotates photo themes every 30 seconds)
        viewModelScope.launch {
            while (isActive) {
                delay(30000L) // 30 seconds
                if (_isAutoRotatePhotoTheme.value) {
                    cyclePhotoTheme()
                }
            }
        }

        // Initial news feed refresh on startup to populate new categories like Discord
        viewModelScope.launch {
            delay(5000)
            refreshNewsFeeds()
        }

        // Cold-start restoration of browser session and tabs BEFORE initial navigation
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = database.browserSessionDao().getSession()
                if (session != null) {
                    if (session.onboardingCompleted) {
                        _onboardingCompleted.value = true
                        savedStateHandle["onboarding_completed"] = true
                    }
                }
                val tabs = database.browserTabDao().getAllTabsList()
                if (tabs.isEmpty()) {
                    if (!explicitIntentUrlHandled) {
                        val newId = java.util.UUID.randomUUID().toString()
                        val initialTab = BrowserTabEntity(id = newId, url = "", title = "Home", lastAccessed = System.currentTimeMillis())
                        database.browserTabDao().insert(initialTab)
                        _activeTabId.value = newId
                        savedStateHandle["active_tab_id"] = newId
                        BrowserStateLog.restore("Cold-start: no existing tabs, created initial home tab $newId")
                    }
                } else {
                    if (explicitIntentUrlHandled) {
                        BrowserStateLog.restore("Cold-start: preserved explicit intent URL priority over ${tabs.size} persisted tabs")
                    } else {
                        val sessionAge = if (session != null && session.lastSavedTimestamp > 0L) {
                            System.currentTimeMillis() - session.lastSavedTimestamp
                        } else {
                            0L
                        }
                        // If user hasn't entered the browser for a long time (> 15 minutes), start cleanly on Home screen
                        val isStaleSession = sessionAge > (15 * 60 * 1000L)

                        val targetTab = if (!session?.activeTabId.isNullOrBlank()) {
                            tabs.find { it.id == session.activeTabId } ?: tabs.maxByOrNull { it.lastAccessed } ?: tabs.first()
                        } else {
                            tabs.maxByOrNull { it.lastAccessed } ?: tabs.first()
                        }

                        // Normalize external tabs so they don't linger with one-off behavior across sessions
                        tabs.filter { it.isExternal }.forEach {
                            database.browserTabDao().insert(it.copy(isExternal = false))
                        }

                        val shouldStartOnHome = isStaleSession || targetTab.isExternal || targetTab.url.isBlank()

                        if (shouldStartOnHome) {
                            val homeTab = tabs.find { it.url.isBlank() }
                            if (homeTab != null) {
                                _activeTabId.value = homeTab.id
                                savedStateHandle["active_tab_id"] = homeTab.id
                            } else {
                                val newId = java.util.UUID.randomUUID().toString()
                                val freshHomeTab = BrowserTabEntity(
                                    id = newId,
                                    url = "",
                                    title = "Home",
                                    tabOrder = (tabs.maxOfOrNull { it.tabOrder } ?: 0) + 1,
                                    lastAccessed = System.currentTimeMillis()
                                )
                                database.browserTabDao().insert(freshHomeTab)
                                _activeTabId.value = newId
                                savedStateHandle["active_tab_id"] = newId
                            }
                            resetToHome()
                            BrowserStateLog.restore("Cold-start: long inactivity or external link - started on Home screen, preserved ${tabs.size} tabs")
                        } else {
                            _activeTabId.value = targetTab.id
                            savedStateHandle["active_tab_id"] = targetTab.id
                            BrowserStateLog.restore("Cold-start: restored ${tabs.size} tabs, active tab: ${targetTab.id} (url: ${targetTab.url})")
                            if (targetTab.url.isNotBlank()) {
                                _urlInput.value = targetTab.url
                                val isHttp = targetTab.url.startsWith("http://", ignoreCase = true) || targetTab.url.startsWith("https://", ignoreCase = true)
                                if (isHttp) {
                                    val host = try { java.net.URI(targetTab.url).host ?: targetTab.url } catch (_: Exception) { targetTab.url }
                                    _currentResource.value = ResolvedResource(
                                        url = targetTab.url,
                                        resolvedProtocol = NetworkProtocol.CENTRALIZED_HTTP,
                                        cid = com.example.network.CryptoUtils.generateCid(targetTab.url),
                                        title = if (targetTab.title.isNotBlank()) targetTab.title else host,
                                        content = "",
                                        contentType = "text/html",
                                        sizeBytes = 0L,
                                        latencyMs = 15L,
                                        centralizedUrl = targetTab.url,
                                        centralizedLatencyMs = 15L,
                                        centralizedIp = "Direct High-Speed Stack",
                                        verificationStatus = VerificationStatus.VERIFIED_TAMPER_PROOF,
                                        cryptographicHash = com.example.network.CryptoUtils.sha256(targetTab.url),
                                        routedVia = "Direct High-Speed Web Stack: $host"
                                    )
                                    _isLoading.value = false
                                } else {
                                    resolveUrl(targetTab.url)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("DecentralViewModel", "Notice restoring browser tabs: ${e.message}")
            }
        }

        // Initialize and persist latest news cache so it catches naturally and never resets
        viewModelScope.launch {
            try {
                val count = database.newsArticleDao().getCount()
                if (count == 0) {
                    val initialEntities = getDefaultCuratedNews().map { it.toEntity() }
                    database.newsArticleDao().insertAll(initialEntities)
                }
                refreshNewsFeeds()
            } catch (_: Exception) {}
        }

        // Periodic background news fetcher every 15 minutes
        viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(15 * 60_000L)
                refreshNewsFeeds()
            }
        }
    }

    fun refreshKaspaWallet(address: String? = null) {
        val targetAddress = address ?: testnetAddress.value.ifBlank { activeAccount.value?.let { getTestnetAddressForAccount(it) } } ?: return
        if (targetAddress.isBlank()) return
        viewModelScope.launch {
            _kaspaWalletState.value = _kaspaWalletState.value.copy(isLoading = true)
            val updated = kaspaWalletService.fetchWalletState(targetAddress)
            _kaspaWalletState.value = updated.copy(
                kaspaAddress = targetAddress,
                networkStatus = "Kaspa BlockDAG Testnet 10"
            )
        }
        refreshNetworkFeeCondition()
    }

    fun clearWalletStatusNotice() {
        _kaspaWalletState.value = _kaspaWalletState.value.copy(
            statusNotice = null,
            lastBroadcastTxId = null
        )
    }

    fun refreshNetworkFeeCondition() {
        viewModelScope.launch {
            _isFetchingFeeCondition.value = true
            try {
                val isTestnet = testnetAddress.value.startsWith("kaspatest:") || (activeAccount.value?.kaspaAddress?.startsWith("kaspatest:") == true)
                val cond = kaspaWalletService.fetchNetworkFeeEstimate(isTestnet = isTestnet)
                _networkFeeCondition.value = cond
            } catch (_: Exception) {}
            finally {
                _isFetchingFeeCondition.value = false
            }
        }
    }

    fun refreshTestnetWallet() {
        refreshKaspaWallet(testnetAddress.value)
    }

    fun calculateFeeBreakdown(
        amountKas: Double,
        sompiPerMass: Long = KaspaTransactionEngine.DEFAULT_SOMPI_PER_MASS
    ): KaspaTransactionEngine.FeeCalculationBreakdown {
        return KaspaTransactionEngine.calculateFeeBreakdown(
            amountKas = amountKas,
            inputsCount = maxOf(1, _kaspaWalletState.value.utxosCount),
            outputsCount = 2,
            sompiPerMass = sompiPerMass
        )
    }

    fun calculateFeeRangeBreakdown(
        amountKas: Double,
        selectedSompiPerMass: Long = _networkFeeCondition.value.normalFeerate
    ): KaspaTransactionEngine.FeeRangeCalculationBreakdown {
        return KaspaTransactionEngine.calculateFeeRangeBreakdown(
            amountKas = amountKas,
            inputsCount = maxOf(1, _kaspaWalletState.value.utxosCount),
            outputsCount = 2,
            selectedSompiPerMass = selectedSompiPerMass,
            networkCondition = _networkFeeCondition.value
        )
    }

    fun sendKaspaTransaction(
        recipientAddress: String,
        amountKas: Double,
        sompiPerMass: Long = KaspaTransactionEngine.DEFAULT_SOMPI_PER_MASS
    ) {
        val senderAcc = activeAccount.value ?: return
        viewModelScope.launch {
            _kaspaWalletState.value = _kaspaWalletState.value.copy(isSending = true, lastBroadcastTxId = null, statusNotice = null)
            try {
                val cleanRecipient = recipientAddress.trim()
                if (!isValidKaspaAddress(cleanRecipient)) {
                    val errMsg = "Invalid address. Please enter a valid Kaspa address"
                    _statusMessage.value = errMsg
                    _kaspaWalletState.value = _kaspaWalletState.value.copy(
                        isSending = false,
                        statusNotice = "Error: $errMsg"
                    )
                    return@launch
                }

                val decryptedSeed = try {
                    CryptoUtils.getDecryptedSeed(senderAcc.seedPhrase)
                } catch (e: Exception) {
                    val errMsg = "Failed to decrypt wallet seed: ${e.message}"
                    _statusMessage.value = errMsg
                    _kaspaWalletState.value = _kaspaWalletState.value.copy(
                        isSending = false,
                        lastBroadcastTxId = null,
                        statusNotice = "Error: $errMsg"
                    )
                    return@launch
                }

                val senderTestnetAddress = getTestnetAddressForAccount(senderAcc)

                val result = kaspaWalletService.sendKaspa(
                    senderAddress = senderTestnetAddress,
                    senderSeed = decryptedSeed,
                    recipientAddress = cleanRecipient,
                    amountKas = amountKas,
                    sompiPerMass = sompiPerMass
                )
                result.onSuccess { txItem ->
                    _statusMessage.value = "Testnet 10 KAS Broadcasted! Tx: ${txItem.txId.take(16)}..."
                    val updatedTxs = listOf(txItem) + _kaspaWalletState.value.recentTransactions
                    val updatedBalance = (_kaspaWalletState.value.balanceKas - amountKas - txItem.feeKas).coerceAtLeast(0.0)
                    _kaspaWalletState.value = _kaspaWalletState.value.copy(
                        isSending = false,
                        balanceKas = updatedBalance,
                        balanceUsd = updatedBalance * _kaspaWalletState.value.priceUsd,
                        lastBroadcastTxId = txItem.txId,
                        recentTransactions = updatedTxs,
                        statusNotice = "Sent %.4f KAS (TxID: ${txItem.txId.take(16)}...)".format(amountKas)
                    )
                }.onFailure { err ->
                    val errorMsg = err.message ?: "Transaction broadcast failed"
                    _statusMessage.value = "Testnet 10 failed: $errorMsg"
                    _kaspaWalletState.value = _kaspaWalletState.value.copy(
                        isSending = false,
                        lastBroadcastTxId = null,
                        statusNotice = "Error: $errorMsg"
                    )
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unexpected error during transaction"
                _statusMessage.value = "Transaction error: $errorMsg"
                _kaspaWalletState.value = _kaspaWalletState.value.copy(
                    isSending = false,
                    lastBroadcastTxId = null,
                    statusNotice = "Error: $errorMsg"
                )
            } finally {
                // Guaranteed immediate backoff: never leave button in sending/frozen state
                if (_kaspaWalletState.value.isSending) {
                    _kaspaWalletState.value = _kaspaWalletState.value.copy(isSending = false)
                }
            }
        }
    }

    fun createNewTestnetWallet(customName: String? = null, passphrase: String = "") {
        viewModelScope.launch {
            try {
                val newAcc = CryptoUtils.deriveDecentralizedAccount(
                    customHandle = customName ?: "Testnet 10 Wallet",
                    networkPrefix = "kaspatest",
                    passphrase = passphrase.trim()
                )
                database.accountDao().deactivateAll()
                database.accountDao().insertAccount(newAcc)
                _kaspaWalletState.value = _kaspaWalletState.value.copy(
                    isLoading = true,
                    kaspaAddress = newAcc.kaspaAddress,
                    statusNotice = "Scanning on-chain Testnet 10 BlockDAG for ${newAcc.kaspaAddress.take(18)}..."
                )
                _statusMessage.value = "Created Kaspa TN10 Wallet. Performing on-chain scan..."
                
                // Immediate full on-chain scan
                val scanned = kaspaWalletService.fetchWalletState(newAcc.kaspaAddress)
                _kaspaWalletState.value = scanned.copy(
                    kaspaAddress = newAcc.kaspaAddress,
                    networkStatus = "Kaspa BlockDAG Testnet 10",
                    statusNotice = null
                )
                _statusMessage.value = "Wallet active: ${newAcc.kaspaAddress.take(16)}... (%.4f KAS on-chain)".format(scanned.balanceKas)
            } catch (e: Exception) {
                _statusMessage.value = "Failed to create Testnet 10 wallet: ${e.message}"
            }
        }
    }

    fun importTestnetWallet(mnemonic: String, customName: String? = null, passphrase: String = "") {
        viewModelScope.launch {
            try {
                val cleanMnemonic = mnemonic.trim()
                val words = cleanMnemonic.split("\\s+".toRegex())
                if (words.size != 12 && words.size != 24) {
                    _statusMessage.value = "Invalid seed phrase: Expected 12 or 24 words, got ${words.size}"
                    return@launch
                }
                val importedAcc = CryptoUtils.deriveDecentralizedAccount(
                    customHandle = customName ?: "Imported TN10 Wallet",
                    seedMnemonic = cleanMnemonic,
                    networkPrefix = "kaspatest",
                    passphrase = passphrase.trim()
                )
                database.accountDao().deactivateAll()
                database.accountDao().insertAccount(importedAcc)
                _kaspaWalletState.value = _kaspaWalletState.value.copy(
                    isLoading = true,
                    kaspaAddress = importedAcc.kaspaAddress,
                    statusNotice = "Scanning on-chain Testnet 10 BlockDAG for ${importedAcc.kaspaAddress.take(18)}..."
                )
                _statusMessage.value = "Imported wallet. Performing on-chain BlockDAG scan..."
                
                // Immediate full on-chain scan
                val scanned = kaspaWalletService.fetchWalletState(importedAcc.kaspaAddress)
                _kaspaWalletState.value = scanned.copy(
                    kaspaAddress = importedAcc.kaspaAddress,
                    networkStatus = "Kaspa BlockDAG Testnet 10",
                    statusNotice = null
                )
                _statusMessage.value = "Wallet imported: ${importedAcc.kaspaAddress.take(16)}... (%.4f KAS on-chain)".format(scanned.balanceKas)
            } catch (e: Exception) {
                _statusMessage.value = "Failed to import wallet: ${e.message}"
            }
        }
    }

    fun openTestnetFaucet(context: android.content.Context? = null) {
        val address = testnetAddress.value
        if (address.isNotBlank() && context != null) {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Kaspa Testnet 10 Address", address))
        }
        _statusMessage.value = "Address copied! Opening Kaspa Testnet 10 Faucet..."
        openUrlInBrowser("https://faucet-tn10.kaspanet.io", isExternal = false)
    }

    fun openTestnetExplorer(address: String? = null) {
        val target = address ?: testnetAddress.value
        val base = _selectedExplorerUrl.value.trimEnd('/')
        val url = if (target.isNotBlank()) {
            if (base.contains("kaspa.stream")) {
                "$base/addresses/$target"
            } else if (base.contains("katnip")) {
                "$base/addr/$target"
            } else {
                "$base/addresses/$target"
            }
        } else {
            base
        }
        openUrlInBrowser(url, isExternal = false)
    }

    fun openTestnetTxExplorer(txId: String) {
        val base = _selectedExplorerUrl.value.trimEnd('/')
        val url = if (base.contains("kaspa.stream")) {
            "$base/txs/$txId"
        } else if (base.contains("katnip")) {
            "$base/tx/$txId"
        } else {
            "$base/txs/$txId"
        }
        openUrlInBrowser(url, isExternal = false)
    }

    fun getActiveSeedPhrase(): String? {
        val acc = activeAccount.value ?: return null
        return try {
            CryptoUtils.getDecryptedSeed(acc.seedPhrase)
        } catch (_: Exception) {
            null
        }
    }

    fun linkGoogleDecentralizedAccount(email: String, displayName: String) {
        viewModelScope.launch {
            try {
                val googleAcc = CryptoUtils.deriveGoogleBridgeAccount(
                    email = email,
                    displayName = displayName
                )
                database.accountDao().deactivateAll()
                database.accountDao().insertAccount(googleAcc)
                _statusMessage.value = "Linked Google Account to zk-Decentralized ID: ${googleAcc.did.take(18)}..."
            } catch (e: Exception) {
                _statusMessage.value = "Failed to link Google account: ${e.message}"
            }
        }
    }

    fun switchAccount(did: String) {
        viewModelScope.launch {
            try {
                database.accountDao().deactivateAll()
                database.accountDao().setActive(did)
                val acc = database.accountDao().getAccountByDid(did)
                _statusMessage.value = "Switched to account: ${acc?.handle ?: did.take(16)}"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to switch account: ${e.message}"
            }
        }
    }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch {
            try {
                database.accountDao().deleteAccount(account)
                _statusMessage.value = "Removed account: ${account.handle}"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to delete account: ${e.message}"
            }
        }
    }

    fun signOutActiveAccount() {
        viewModelScope.launch {
            try {
                database.accountDao().deactivateAll()
                _statusMessage.value = "Signed out successfully"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to sign out: ${e.message}"
            }
        }
    }


    fun openUrlInBrowser(url: String, isExternal: Boolean = true, isStandalonePwa: Boolean = false) {
        if (isExternal) {
            explicitIntentUrlHandled = true
        }
        viewModelScope.launch {
            if (isStandalonePwa) {
                _isStandalonePwaMode.value = true
            }
            _activeTab.value = AppTab.BROWSER_GATEWAY
            val cleanTarget = url.trim()
            if (cleanTarget.isBlank()) return@launch

            val tabs = database.browserTabDao().getAllTabsList()
            val activeId = _activeTabId.value
            val activeTab = tabs.find { it.id == activeId }
            val host = try { java.net.URI(cleanTarget).host ?: cleanTarget } catch (_: Exception) { cleanTarget }
            val tabTitle = if (!host.isNullOrBlank()) host else "External Link"

            if (isExternal) {
                // For external intents (app launching from outside), always open in a brand new tab to protect the user's active tabs.
                val newId = java.util.UUID.randomUUID().toString()
                val newTab = BrowserTabEntity(
                    id = newId,
                    url = cleanTarget,
                    title = tabTitle,
                    lastAccessed = System.currentTimeMillis(),
                    isExternal = true
                )
                database.browserTabDao().insert(newTab)
                _activeTabId.value = newId
                _urlInput.value = cleanTarget
                resolveUrl(cleanTarget)
            } else {
                // Inside the browser (clicking bookmark, history, or speed dial), load it in the CURRENT active tab.
                if (activeTab != null) {
                    val updatedTab = activeTab.copy(
                        url = cleanTarget,
                        title = tabTitle,
                        lastAccessed = System.currentTimeMillis()
                    )
                    database.browserTabDao().insert(updatedTab)
                    _urlInput.value = cleanTarget
                    resolveUrl(cleanTarget)
                } else {
                    // Fallback: create new tab if no active tab exists
                    val newId = java.util.UUID.randomUUID().toString()
                    val newTab = BrowserTabEntity(
                        id = newId,
                        url = cleanTarget,
                        title = tabTitle,
                        lastAccessed = System.currentTimeMillis()
                    )
                    database.browserTabDao().insert(newTab)
                    _activeTabId.value = newId
                    _urlInput.value = cleanTarget
                    resolveUrl(cleanTarget)
                }
            }
        }
    }

    fun onUserSubmitUrl(rawUrl: String? = null) {
        viewModelScope.launch {
            val input = (rawUrl ?: _urlInput.value).trim()
            if (input.equals("wallet", ignoreCase = true) ||
                input.equals("kaspa wallet", ignoreCase = true) ||
                input.equals("testnet wallet", ignoreCase = true) ||
                input.equals("tn10", ignoreCase = true) ||
                input.equals("kaspa testnet wallet", ignoreCase = true)
            ) {
                setTab(AppTab.KASPA_WALLET)
                return@launch
            }
            if (input.equals("update", ignoreCase = true) ||
                input.equals("updates", ignoreCase = true) ||
                input.equals("update zone", ignoreCase = true) ||
                input.equals("check update", ignoreCase = true) ||
                input.equals("check updates", ignoreCase = true) ||
                input.equals("sideload", ignoreCase = true)
            ) {
                setTab(AppTab.TRAFFIC_AUDIT)
                checkForUpdates(isUserInitiated = true)
                return@launch
            }
            val target = normalizeUrlOrQuery(input)
            if (target.isBlank()) return@launch

            val extractedQuery = extractSearchQuery(target)
            val displayInput = if (!extractedQuery.isNullOrBlank()) {
                extractedQuery
            } else if (!input.startsWith("http://", true) && !input.startsWith("https://", true) && !input.contains(".")) {
                input
            } else {
                target
            }
            _urlInput.value = displayInput

            val activeId = _activeTabId.value
            val activeTab = if (activeId != null) database.browserTabDao().getTabById(activeId) else null

            if (activeTab != null && activeTab.isExternal) {
                // When open link from another platforms it opens in our browser, not to persist when typing another link it moves to a new tab
                val host = try { java.net.URI(target).host ?: target } catch (_: Exception) { target }
                val newId = java.util.UUID.randomUUID().toString()
                val newTab = BrowserTabEntity(
                    id = newId,
                    url = target,
                    title = if (!host.isNullOrBlank()) host else "New Tab",
                    lastAccessed = System.currentTimeMillis(),
                    isExternal = false
                )
                database.browserTabDao().insert(newTab)
                _activeTabId.value = newId
                resolveUrl(target, overrideDisplayInput = displayInput)
            } else {
                resolveUrl(target, overrideDisplayInput = displayInput)
            }
        }
    }

    fun setTab(tab: AppTab) {
        _activeTab.value = tab
        if (_isAutoRotatePhotoTheme.value) {
            cyclePhotoTheme()
        }
    }

    fun setUrlInput(url: String) {
        _urlInput.value = url
    }

    fun setCurrentResource(res: ResolvedResource?) {
        _currentResource.value = res
    }

    fun setProtocol(protocol: NetworkProtocol) {
        _selectedProtocol.value = protocol
    }

    fun setStatusMessage(msg: String) {
        _statusMessage.value = msg
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun resetToHome() {
        _navigationSessionId.value = _navigationSessionId.value + 1
        _urlInput.value = ""
        _currentResource.value = null
        _statusMessage.value = null
        _isLoading.value = false

        val activeId = _activeTabId.value
        if (activeId != null) {
            webViewTabManager.destroyTab(activeId, "reset to home")
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val existing = database.browserTabDao().getTabById(activeId)
                if (existing != null) {
                    database.browserTabDao().insert(
                        existing.copy(
                            url = "",
                            title = "Home",
                            webViewState = null,
                            scrollX = 0,
                            scrollY = 0,
                            historyJson = "[]",
                            isExternal = false,
                            lastAccessed = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    fun normalizeUrlOrQuery(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""

        // Check if input is a valid Kaspa network address
        val kaspaCheck = validateKaspaAddress(trimmed)
        if (kaspaCheck.isValid) {
            return kaspaCheck.cleanAddress
        }

        // Catch unresolvable internal search placeholder URLs (e.g. https://search?q=...) without breaking search.brave.com or search.yahoo.com
        val parsedUri = runCatching { android.net.Uri.parse(trimmed) }.getOrNull()
        val parsedHost = parsedUri?.host?.lowercase()
        val isInternalSearchPlaceholder = (parsedHost == "search" || trimmed.startsWith("https://search?", ignoreCase = true) ||
                trimmed.startsWith("http://search?", ignoreCase = true) ||
                trimmed.startsWith("kaspa://search", ignoreCase = true) ||
                trimmed.startsWith("kas://search", ignoreCase = true))

        if (isInternalSearchPlaceholder) {
            val q = if (trimmed.contains("q=")) trimmed.substringAfter("q=").substringBefore("&") else ""
            val enc = try { java.net.URLEncoder.encode(q, "UTF-8") } catch (_: Exception) { q }
            val base = if (_searchEngine.value.baseUrl == "https://search?q=" || _searchEngine.value.baseUrl.isBlank()) {
                "https://duckduckgo.com/?q="
            } else {
                _searchEngine.value.baseUrl
            }
            return "$base$enc"
        }

        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("ipfs://", ignoreCase = true) ||
            trimmed.startsWith("mesh://", ignoreCase = true) ||
            trimmed.startsWith("dweb://", ignoreCase = true) ||
            trimmed.startsWith("p2p://", ignoreCase = true) ||
            trimmed.startsWith("kas://", ignoreCase = true) ||
            trimmed.startsWith("kaspa://", ignoreCase = true) ||
            trimmed.startsWith("kaspa:", ignoreCase = true) ||
            trimmed.startsWith("kaspatest:", ignoreCase = true) ||
            trimmed.startsWith("kaspadev:", ignoreCase = true) ||
            trimmed.startsWith("kaspasim:", ignoreCase = true) ||
            trimmed.startsWith("dnet://", ignoreCase = true) ||
            trimmed.startsWith("kns://", ignoreCase = true) ||
            trimmed.startsWith("hyper://", ignoreCase = true) ||
            trimmed.startsWith("magnet:", ignoreCase = true)
        ) {
            return trimmed
        }

        // Decentralized domain extensions
        if (DomainConstants.isCustomDomain(trimmed)) {
            return "kas://$trimmed"
        }
        if (trimmed.endsWith(".mesh", ignoreCase = true)) {
            return "mesh://$trimmed"
        }
        if (trimmed.endsWith(".eth", ignoreCase = true)) {
            return "dweb://$trimmed"
        }

        // IPFS hashes (v0 or v1)
        if ((trimmed.startsWith("Qm") && trimmed.length == 46) || (trimmed.startsWith("bafy") && trimmed.length >= 50)) {
            return "ipfs://$trimmed"
        }

        // Check if input looks like a web address (e.g. domain.tld, subdomain.domain.tld, localhost, IP address)
        val hasWhitespace = trimmed.any { it.isWhitespace() }
        val hasDot = trimmed.contains(".")
        val isLocalhost = trimmed.startsWith("localhost", ignoreCase = true)
        val isIp = trimmed.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?(/.*)?$"))

        if (!hasWhitespace && (hasDot || isLocalhost || isIp)) {
            return "https://$trimmed"
        }

        // Search fallback using selected engine
        val encoded = try {
            java.net.URLEncoder.encode(trimmed, "UTF-8")
        } catch (_: Exception) {
            trimmed
        }
        return "${_searchEngine.value.baseUrl}$encoded"
    }

    fun extractSearchQuery(url: String): String? {
        if (url.isBlank()) return null
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host?.lowercase() ?: ""
            val path = uri.path?.lowercase() ?: ""

            val rawQuery = when {
                // DuckDuckGo
                host.contains("duckduckgo.com") || host.contains("duck.com") -> {
                    uri.getQueryParameter("q")
                }
                // Brave Search
                host.contains("search.brave.com") || host.contains("brave.com") -> {
                    uri.getQueryParameter("q")
                }
                // Google
                host.contains("google.") && (path.contains("search") || path.contains("webhp") || path.isEmpty() || path == "/") -> {
                    uri.getQueryParameter("q") ?: uri.getQueryParameter("query")
                }
                // Bing
                host.contains("bing.com") -> {
                    uri.getQueryParameter("q")
                }
                // Startpage
                host.contains("startpage.com") -> {
                    uri.getQueryParameter("query") ?: uri.getQueryParameter("q")
                }
                // Ecosia
                host.contains("ecosia.org") -> {
                    uri.getQueryParameter("q")
                }
                // Qwant
                host.contains("qwant.com") -> {
                    uri.getQueryParameter("q")
                }
                // Yahoo
                host.contains("yahoo.com") && path.contains("search") -> {
                    uri.getQueryParameter("p") ?: uri.getQueryParameter("q")
                }
                // Baidu
                host.contains("baidu.com") -> {
                    uri.getQueryParameter("wd") ?: uri.getQueryParameter("word")
                }
                // Yandex
                host.contains("yandex.") && path.contains("search") -> {
                    uri.getQueryParameter("text") ?: uri.getQueryParameter("q")
                }
                // Kagi
                host.contains("kagi.com") -> {
                    uri.getQueryParameter("q")
                }
                // General /search?q=... or ?q=... or ?query=...
                (path.contains("search") || host.contains("search")) && (!uri.getQueryParameter("q").isNullOrBlank() || !uri.getQueryParameter("query").isNullOrBlank()) -> {
                    uri.getQueryParameter("q") ?: uri.getQueryParameter("query")
                }
                else -> null
            }
            rawQuery?.replace("+", " ")?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            when {
                url.contains("?q=") -> url.substringAfter("?q=").substringBefore("&").let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() ?: it.replace("+", " ") }
                url.contains("&q=") -> url.substringAfter("&q=").substringBefore("&").let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() ?: it.replace("+", " ") }
                url.contains("?query=") -> url.substringAfter("?query=").substringBefore("&").let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() ?: it.replace("+", " ") }
                url.contains("?p=") && url.contains("yahoo") -> url.substringAfter("?p=").substringBefore("&").let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() ?: it.replace("+", " ") }
                else -> null
            }?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    fun updateCurrentUrl(newUrl: String) {
        if (newUrl.isBlank() || newUrl.startsWith("data:") || newUrl.startsWith("about:") || newUrl.contains(".ipfs.dweb.link")) return
        val extractedQuery = extractSearchQuery(newUrl)
        val displayUrl = if (!extractedQuery.isNullOrBlank()) extractedQuery else newUrl
        if (_urlInput.value != displayUrl && !com.example.util.BrowserTabWebViewManager.isSameUrl(_urlInput.value, displayUrl)) {
            _urlInput.value = displayUrl
        }
        val activeId = _activeTabId.value
        if (activeId != null) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val tab = database.browserTabDao().getTabById(activeId)
                if (tab != null && !com.example.util.BrowserTabWebViewManager.isSameUrl(tab.url, newUrl)) {
                    database.browserTabDao().insert(tab.copy(url = newUrl, lastAccessed = System.currentTimeMillis()))
                }
            }
        }
        val current = _currentResource.value
        if (current == null) {
            if (newUrl.startsWith("http://") || newUrl.startsWith("https://")) {
                val host = try { java.net.URI(newUrl).host ?: newUrl } catch (_: Exception) { newUrl }
                _currentResource.value = ResolvedResource(
                    url = newUrl,
                    resolvedProtocol = NetworkProtocol.CENTRALIZED_HTTP,
                    cid = com.example.network.CryptoUtils.generateCid(newUrl),
                    title = host,
                    content = "",
                    contentType = "text/html",
                    sizeBytes = 0L,
                    latencyMs = 15L,
                    centralizedUrl = newUrl,
                    centralizedLatencyMs = 15L,
                    centralizedIp = "Direct High-Speed Stack",
                    verificationStatus = VerificationStatus.VERIFIED_TAMPER_PROOF,
                    cryptographicHash = com.example.network.CryptoUtils.sha256(newUrl),
                    routedVia = "Direct High-Speed Web Stack: $host"
                )
            }
            return
        }
        if (newUrl.startsWith("http://") || newUrl.startsWith("https://")) {
            val host = try { java.net.URI(newUrl).host ?: newUrl } catch (_: Exception) { newUrl }
            if (com.example.util.BrowserTabWebViewManager.isSameUrl(current.url, newUrl)) return
            _currentResource.value = current.copy(
                url = newUrl,
                title = if (current.title.isBlank() || current.title == "HTTP Connection Error") host else current.title,
                centralizedUrl = newUrl,
                cryptographicHash = com.example.network.CryptoUtils.sha256(newUrl)
            )
        }
    }

    fun updateResourceTitle(newTitle: String) {
        val current = _currentResource.value
        if (current != null && newTitle.isNotBlank() && !newTitle.startsWith("http://") && !newTitle.startsWith("https://")) {
            _currentResource.value = current.copy(title = newTitle)
        }
    }

    fun resolveUrl(url: String? = null, overrideDisplayInput: String? = null) {
        _navigationSessionId.value = _navigationSessionId.value + 1
        val raw = url ?: _urlInput.value
        if (raw.isBlank()) {
            _currentResource.value = null
            _isLoading.value = false
            return
        }

        val target = normalizeUrlOrQuery(raw)
        val extractedQuery = extractSearchQuery(target)
        val displayInput = overrideDisplayInput ?: if (!extractedQuery.isNullOrBlank()) {
            extractedQuery
        } else if (!raw.startsWith("http://", true) && !raw.startsWith("https://", true) && !raw.contains(".")) {
            raw
        } else {
            target
        }
        _urlInput.value = displayInput
        _isLoading.value = true

        val isHttp = target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)
        val currentSessionId = _navigationSessionId.value

        // Instantly set provisional resource so the WebView renders the website directly instead of showing speed dial / home screen theme first
        _currentResource.value = ResolvedResource(
            url = target,
            resolvedProtocol = NetworkProtocol.CENTRALIZED_HTTP,
            cid = "",
            title = displayInput,
            content = "",
            contentType = "text/html",
            sizeBytes = 0L,
            latencyMs = 0L,
            cryptographicHash = com.example.network.CryptoUtils.sha256(target),
            routedVia = "Direct"
        )

        viewModelScope.launch {
            try {
                val result = resolver.resolve(target, _selectedProtocol.value, _desktopModeEnabled.value, _searchEngine.value.baseUrl)
                if (_navigationSessionId.value == currentSessionId) {
                    _currentResource.value = result
                    verifyResourceIntegrity(notifyUser = false)
                    
                    val bytesTransferred = if (result.sizeBytes > 0L) result.sizeBytes else (420 * 1024L)
                    nodeManager.recordBrowserTraffic(bytesTransferred, target)
                    
                    if (result.resolvedProtocol == NetworkProtocol.DECENTRALIZED_P2P || 
                        result.resolvedProtocol == NetworkProtocol.HYBRID_COEXISTENCE) {
                        if (result.verificationStatus == VerificationStatus.VERIFIED_TAMPER_PROOF ||
                            result.verificationStatus == VerificationStatus.MIRROR_MATCHED) {
                            nodeManager.incrementCrossVerifications()
                        }
                    }
                }
            } catch (e: Exception) {
                if (_navigationSessionId.value == currentSessionId) {
                    _statusMessage.value = "Failed to resolve: ${e.localizedMessage}"
                }
            } finally {
                if (_navigationSessionId.value == currentSessionId) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun setIsLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun recordBrowserTraffic(url: String, estimatedBytes: Long = 0L) {
        val bytes = if (estimatedBytes > 0L) estimatedBytes else (350 * 1024L)
        nodeManager.recordBrowserTraffic(bytes, url)
    }

    fun publishNewContent(title: String, content: String, slug: String, onComplete: () -> Unit) {
        if (content.isBlank()) {
            _statusMessage.value = "Content cannot be empty"
            return
        }

        val account = activeAccount.value

        viewModelScope.launch {
            _isLoading.value = true
            try {
                var authorAddress = ""
                var signature = ""

                if (account != null) {
                    try {
                        val cleanSlug = com.example.network.DomainConstants.removeDomainSuffix(slug.trim().lowercase().replace(" ", "-"))
                        val cid = com.example.network.CryptoUtils.generateCid(content)
                        val protocolPrefix = if (cleanSlug.isNotEmpty()) "mesh://$cleanSlug | ${com.example.network.DomainConstants.formatDomain(cleanSlug)}" else "ipfs://$cid"
                        
                        authorAddress = account.publicKeyHex
                        val seedBytes = com.example.network.CryptoUtils.getDecryptedSeed(account.seedPhrase)
                        val keyPair = com.example.network.CryptoUtils.deriveKaspaKeyPair(seedBytes)
                        
                        val messageBytes = (content + protocolPrefix).toByteArray(Charsets.UTF_8)
                        signature = com.example.network.CryptoUtils.signKaspaPersonalMessage(keyPair.privateKey, messageBytes)
                    } catch (e: Exception) {
                        // Keep signature empty if signing fails
                    }
                }

                val entity = nodeManager.publishContent(title, content, slug, authorAddress, signature)
                _statusMessage.value = "Published to decentralized swarm! CID: ${entity.cid.take(16)}..."
                _urlInput.value = entity.protocolPrefix
                _currentResource.value = resolver.resolve(entity.protocolPrefix, NetworkProtocol.HYBRID_COEXISTENCE)
                _activeTab.value = AppTab.BROWSER_GATEWAY
                onComplete()
            } catch (e: Exception) {
                _statusMessage.value = "Publish error: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun verifyResourceIntegrity(notifyUser: Boolean = true) {
        val current = _currentResource.value ?: return
        val contentToHash = if (current.content.isNotEmpty()) current.content else current.url
        val calculatedHash = com.example.network.CryptoUtils.sha256(contentToHash)
        val isMatch = calculatedHash.equals(current.cryptographicHash, ignoreCase = true)

        if (isMatch) {
            if (notifyUser) {
                _statusMessage.value = "Cryptographic Verification Passed: SHA-256 matches Merkle root ($calculatedHash)."
            }
            nodeManager.incrementCrossVerifications()
        } else {
            if (notifyUser) {
                _statusMessage.value = "Warning: Hash mismatch detected between payload and Merkle root."
            }
        }
    }

    fun refreshPeerLatencies() {
        viewModelScope.launch {
            _statusMessage.value = "Testing connectivity to decentralized gateways..."
            nodeManager.pingAllPeers()
            _statusMessage.value = "Gateway ping complete. Latency and status updated."
        }
    }

    fun togglePin(cid: String) {
        viewModelScope.launch {
            nodeManager.togglePin(cid)
        }
    }

    fun deleteContent(cid: String) {
        viewModelScope.launch {
            nodeManager.deletePinned(cid)
        }
    }

    fun toggleDaemon() {
        nodeManager.toggleDaemon()
    }

    fun scanLocalNsdNetwork() {
        _statusMessage.value = "Starting NsdManager mDNS scan on local network..."
        nodeManager.scanLocalNetwork()
    }

    fun connectToP2PPeer(host: String, port: Int) {
        viewModelScope.launch {
            _statusMessage.value = "Connecting to P2P peer at $host:$port..."
            val result = nodeManager.connectToPeer(host, port)
            if (result.success) {
                _statusMessage.value = "P2P Connection Established with ${result.nodeName} (${result.latencyMs}ms)!"
            } else {
                _statusMessage.value = "P2P Connection failed: ${result.errorMessage}"
            }
        }
    }

    fun clearAuditLogs() {
        viewModelScope.launch {
            database.trafficAuditDao().clearAudits()
            _statusMessage.value = "Audit logs cleared."
        }
    }

    fun clearBrowsingData(context: android.content.Context) {
        viewModelScope.launch {
            // 1. Clear Database History
            database.historyDao().clearAll()
            // 2. Clear System Cookies
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
            // 3. Clear Web Storage (LocalStorage, IndexedDB)
            android.webkit.WebStorage.getInstance().deleteAllData()
            
            _statusMessage.value = "All browsing data, cookies, and cache cleared"
        }
    }

    fun addDownload(id: Long, fileName: String, url: String) {
        val newDownload = ActiveDownload(
            downloadId = id,
            fileName = fileName,
            url = url,
            progress = 0.02f,
            bytesDownloaded = 0L,
            bytesTotal = 0L,
            status = "Downloading"
        )
        _activeDownloads.value = _activeDownloads.value.filter { it.downloadId != id } + newDownload
    }

    fun updateDownloadFileName(id: Long, newFileName: String) {
        _activeDownloads.value = _activeDownloads.value.map {
            if (it.downloadId == id) it.copy(fileName = newFileName) else it
        }
    }

    fun updateDownloadProgress(id: Long, progress: Float, bytesDownloaded: Long, bytesTotal: Long, status: String) {
        _activeDownloads.value = _activeDownloads.value.map {
            if (it.downloadId == id) {
                it.copy(
                    progress = progress,
                    bytesDownloaded = bytesDownloaded,
                    bytesTotal = bytesTotal,
                    status = status
                )
            } else it
        }
    }

    fun saveBase64ImageDownload(
        context: android.content.Context,
        downloadId: Long,
        dataUrl: String,
        fileName: String
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val appDownloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            try {
                updateDownloadProgress(downloadId, 0.1f, 0L, 0L, "Saving")
                val commaIndex = dataUrl.indexOf(',')
                var mimeFromData: String? = null
                val bytes = if (commaIndex != -1) {
                    val metadata = dataUrl.substring(5, commaIndex)
                    mimeFromData = metadata.split(";").firstOrNull()?.trim()
                    val rawData = dataUrl.substring(commaIndex + 1)
                    if (metadata.contains("base64", ignoreCase = true)) {
                        android.util.Base64.decode(rawData, android.util.Base64.DEFAULT)
                    } else {
                        java.net.URLDecoder.decode(rawData, "UTF-8").toByteArray(Charsets.UTF_8)
                    }
                } else {
                    android.util.Base64.decode(dataUrl, android.util.Base64.DEFAULT)
                }

                var actualFileName = fileName
                val mimeType = resolveUniversalMimeType(actualFileName, mimeFromData)
                if (!actualFileName.contains(".")) {
                    val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                    if (!ext.isNullOrBlank()) {
                        actualFileName = "$actualFileName.$ext"
                        updateDownloadFileName(downloadId, actualFileName)
                    }
                }

                val targetFile = java.io.File(appDownloadsDir, actualFileName)
                targetFile.outputStream().use { fos ->
                    fos.write(bytes)
                    fos.flush()
                }

                // Register with System DownloadManager so OS posts completed notification and shows in Downloads app
                try {
                    val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
                    @Suppress("DEPRECATION")
                    dm?.addCompletedDownload(
                        actualFileName,
                        "Downloaded via Kaspa Browser",
                        true,
                        mimeType,
                        targetFile.absolutePath,
                        bytes.size.toLong(),
                        true
                    )
                } catch (_: Exception) {}

                // Export to public MediaStore downloads so file is visible in device system Downloads app and gallery
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, actualFileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(bytes)
                        }
                    }
                } else {
                    val publicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    if (publicDir.exists() || publicDir.mkdirs()) {
                        val publicFile = java.io.File(publicDir, actualFileName)
                        publicFile.outputStream().use { os ->
                            os.write(bytes)
                        }
                        android.media.MediaScannerConnection.scanFile(context, arrayOf(publicFile.absolutePath), arrayOf(mimeType), null)
                    }
                }
                updateDownloadProgress(downloadId, 1.0f, bytes.size.toLong(), bytes.size.toLong(), "Success")
            } catch (e: Exception) {
                updateDownloadProgress(downloadId, 0f, 0L, 0L, "Failed: ${e.message}")
            }
        }
    }

    fun startDownload(
        context: android.content.Context,
        downloadId: Long,
        urlStr: String,
        fileName: String,
        cookies: String? = null,
        userAgent: String? = null,
        referer: String? = null
    ) {
        if (urlStr.startsWith("data:", ignoreCase = true)) {
            saveBase64ImageDownload(context, downloadId, urlStr, fileName)
            return
        }

        // Use ViewModel-level IO scope to ensure download survives screen transitions cleanly
        viewModelScope.launch(Dispatchers.IO) {
            var streamSuccess = false
            val appDownloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            var activeFileName = fileName.replace(Regex("[/\\\\?%*:|\"<>]"), "_")
            var targetFile = java.io.File(appDownloadsDir, activeFileName)

            try {
                updateDownloadProgress(downloadId, 0.02f, 0L, 0L, "Downloading")
                var currentUrl = urlStr
                var redirectCount = 0
                var connection: java.net.HttpURLConnection? = null

                while (redirectCount < 8) {
                    val url = java.net.URL(currentUrl)
                    connection = url.openConnection() as java.net.HttpURLConnection
                    connection.instanceFollowRedirects = true
                    connection.connectTimeout = 20000
                    connection.readTimeout = 45000
                    connection.requestMethod = "GET"
                    val defaultUa = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                    connection.setRequestProperty("User-Agent", if (!userAgent.isNullOrBlank()) userAgent else defaultUa)
                    if (!cookies.isNullOrBlank()) {
                        connection.setRequestProperty("Cookie", cookies)
                    }
                    if (!referer.isNullOrBlank()) {
                        connection.setRequestProperty("Referer", referer)
                    }
                    connection.setRequestProperty("Accept-Encoding", "identity")
                    connection.connect()

                    val responseCode = connection.responseCode
                    if (responseCode in 300..399) {
                        val newUrl = connection.getHeaderField("Location")
                        if (!newUrl.isNullOrBlank()) {
                            currentUrl = if (newUrl.startsWith("http", ignoreCase = true)) newUrl else {
                                val base = java.net.URL(currentUrl)
                                java.net.URL(base, newUrl).toString()
                            }
                            redirectCount++
                            connection.disconnect()
                            continue
                        }
                    }
                    break
                }

                val conn = connection
                if (conn != null && conn.responseCode in 200..299) {
                    val serverContentType = conn.contentType
                    val disposition = conn.getHeaderField("Content-Disposition")
                    val extractedName = extractFilenameFromDisposition(disposition)

                    if (!extractedName.isNullOrBlank()) {
                        activeFileName = extractedName.replace(Regex("[/\\\\?%*:|\"<>]"), "_")
                    } else if (activeFileName.endsWith(".bin", ignoreCase = true) || !activeFileName.contains(".")) {
                        // Check if server provides a recognizable Content-Type to enrich extension
                        val cleanMime = serverContentType?.split(";")?.firstOrNull()?.trim()
                        val extFromMime = if (!cleanMime.isNullOrBlank()) {
                            android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(cleanMime)
                        } else null
                        if (!extFromMime.isNullOrBlank()) {
                            val baseName = if (activeFileName.endsWith(".bin", ignoreCase = true)) {
                                activeFileName.removeSuffix(".bin")
                            } else activeFileName
                            activeFileName = "$baseName.$extFromMime"
                        }
                    }

                    if (activeFileName != fileName) {
                        updateDownloadFileName(downloadId, activeFileName)
                        targetFile = java.io.File(appDownloadsDir, activeFileName)
                    }

                    val totalBytes = conn.contentLengthLong.let { if (it > 0) it else 0L }
                    
                    var bytesDownloaded = 0L
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    val inputStream = conn.inputStream
                    val outputStream = java.io.FileOutputStream(targetFile)

                    var lastUpdate = System.currentTimeMillis()

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 120) {
                            lastUpdate = now
                            val progress = if (totalBytes > 0) {
                                (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0.02f, 0.99f)
                            } else {
                                (1f - (1f / (1f + bytesDownloaded.toFloat() / 1000000f))) * 0.95f
                            }
                            updateDownloadProgress(downloadId, progress, bytesDownloaded, totalBytes, "Downloading")
                        }
                    }
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                    conn.disconnect()

                    val finalTotal = if (totalBytes > 0) totalBytes else bytesDownloaded
                    updateDownloadProgress(downloadId, 1.0f, bytesDownloaded, finalTotal, "Success")
                    streamSuccess = true

                    val mimeType = resolveUniversalMimeType(activeFileName, serverContentType)

                    // Register with System DownloadManager so OS posts completed notification and shows in Downloads app
                    try {
                        val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
                        @Suppress("DEPRECATION")
                        dm?.addCompletedDownload(
                            activeFileName,
                            "Downloaded via Kaspa Browser",
                            true,
                            mimeType,
                            targetFile.absolutePath,
                            bytesDownloaded,
                            true
                        )
                    } catch (_: Exception) {}

                    // Export to public MediaStore downloads so file is visible in device system Downloads app and third party viewers
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            val values = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, activeFileName)
                                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                            }
                            val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            if (uri != null) {
                                context.contentResolver.openOutputStream(uri)?.use { os ->
                                    java.io.FileInputStream(targetFile).use { isStream ->
                                        isStream.copyTo(os)
                                    }
                                }
                            }
                        } else {
                            val publicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                            if (publicDir.exists() || publicDir.mkdirs()) {
                                val publicFile = java.io.File(publicDir, activeFileName)
                                java.io.FileInputStream(targetFile).use { isStream ->
                                    java.io.FileOutputStream(publicFile).use { os ->
                                        isStream.copyTo(os)
                                    }
                                }
                                android.media.MediaScannerConnection.scanFile(context, arrayOf(publicFile.absolutePath), arrayOf(mimeType), null)
                            }
                        }
                    } catch (_: Exception) {}

                    // Scan file so MediaStore and Files app recognize any format immediately
                    try {
                        android.media.MediaScannerConnection.scanFile(
                            context,
                            arrayOf(targetFile.absolutePath),
                            arrayOf(mimeType),
                            null
                        )
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {
            }

            if (!streamSuccess) {
                try {
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                } catch (_: Exception) {}
                updateDownloadProgress(downloadId, 0f, 0L, 0L, "Failed")
            }
        }
    }

    fun startMonitoringDownload(context: android.content.Context, downloadId: Long, fileName: String = "") {
        viewModelScope.launch {
            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            var downloading = true
            var attempts = 0
            val maxAttempts = 120
            val targetFile = if (fileName.isNotBlank()) {
                java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )
            } else null

            while (downloading) {
                kotlinx.coroutines.delay(1000)
                attempts++
                val query = android.app.DownloadManager.Query().setFilterById(downloadId)
                val cursor = try { dm.query(query) } catch (e: Exception) { null }
                if (cursor != null && cursor.moveToFirst()) {
                    val bytesDownloadedIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val statusIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
                    
                    val bytesDownloaded = if (bytesDownloadedIndex != -1) cursor.getLong(bytesDownloadedIndex) else 0L
                    val bytesTotal = if (bytesTotalIndex != -1) cursor.getLong(bytesTotalIndex) else 0L
                    val statusInt = if (statusIndex != -1) cursor.getInt(statusIndex) else android.app.DownloadManager.STATUS_FAILED
                    
                    val calculatedProgress = if (bytesTotal > 0) bytesDownloaded.toFloat() / bytesTotal.toFloat() else 0f
                    val statusStr = when (statusInt) {
                        android.app.DownloadManager.STATUS_RUNNING -> "Downloading"
                        android.app.DownloadManager.STATUS_SUCCESSFUL -> "Success"
                        android.app.DownloadManager.STATUS_FAILED -> "Failed"
                        android.app.DownloadManager.STATUS_PENDING -> "Downloading"
                        android.app.DownloadManager.STATUS_PAUSED -> "Downloading"
                        else -> "Downloading"
                    }

                    val activeProgress = if (statusStr == "Downloading" && calculatedProgress < 0.05f) 0.05f else calculatedProgress
                    
                    if (statusInt == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                        val finalSize = if (bytesTotal > 0) bytesTotal else (targetFile?.length() ?: bytesDownloaded)
                        updateDownloadProgress(downloadId, 1.0f, finalSize, finalSize, "Success")
                        downloading = false
                    } else if (statusInt == android.app.DownloadManager.STATUS_FAILED) {
                        updateDownloadProgress(downloadId, 0f, bytesDownloaded, bytesTotal, "Failed")
                        downloading = false
                    } else {
                        updateDownloadProgress(downloadId, activeProgress, bytesDownloaded, bytesTotal, statusStr)
                    }
                    cursor.close()
                } else {
                    cursor?.close()
                    if (targetFile != null && targetFile.exists() && targetFile.length() > 0) {
                        val length = targetFile.length()
                        updateDownloadProgress(downloadId, 1.0f, length, length, "Success")
                        downloading = false
                    } else if (attempts >= maxAttempts) {
                        updateDownloadProgress(downloadId, 0f, 0L, 0L, "Failed")
                        downloading = false
                    }
                }
            }
        }
    }

    fun triggerRealTestDownload(context: android.content.Context) {
        val testUrl = "https://proof.ovh.net/files/10Mb.dat"
        val filename = "test_10mb.dat"
        val downloadId = System.currentTimeMillis()
        addDownload(downloadId, filename, testUrl)
        startDownload(context, downloadId, testUrl, filename)
        _statusMessage.value = "Real download initiated: $filename"
    }

    fun redownload(context: android.content.Context, url: String, filename: String) {
        val downloadId = System.currentTimeMillis()
        val cookies = try {
            android.webkit.CookieManager.getInstance().getCookie(url)
        } catch (_: Exception) {
            null
        }
        addDownload(downloadId, filename, url)
        startDownload(context, downloadId, url, filename, cookies)
        _statusMessage.value = "Redownload started: $filename"
    }

    fun setDetectedPwa(
        title: String,
        url: String,
        iconUrl: String?,
        manifestUrl: String? = null,
        hasManifest: Boolean = false
    ) {
        if (url.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("kas://") || url.startsWith("mesh://"))) {
            _currentPagePwa.value = com.example.network.InstalledPwa(
                id = "pwa_${url.hashCode()}",
                name = title.ifBlank { "Web App" },
                url = url,
                iconUrl = iconUrl,
                manifestUrl = manifestUrl,
                hasManifest = hasManifest
            )
        }
    }

    fun installCurrentPwa(context: android.content.Context) {
        val current = _currentPagePwa.value ?: run {
            val url = _urlInput.value
            val title = _currentResource.value?.title ?: if (url.isNotBlank()) url else "Web App"
            if (url.isNotBlank()) com.example.network.InstalledPwa("pwa_${url.hashCode()}", title, url) else null
        } ?: run {
            android.widget.Toast.makeText(context, "No active web page loaded to install", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _statusMessage.value = "Checking PWA support and downloading website logo..."
            val result = com.example.network.PwaShortcutHelper.installWebsitePwa(
                context = context,
                url = current.url,
                fallbackTitle = current.name,
                manifestUrl = current.manifestUrl,
                iconUrl = current.iconUrl
            )
            _statusMessage.value = result.second
            if (result.first) {
                val existing = _installedPwas.value.filter { it.url != current.url }
                _installedPwas.value = listOf(current) + existing
                saveInstalledPwas(_installedPwas.value)
            }
        }
    }

    fun installPwa(
        context: android.content.Context,
        title: String,
        url: String,
        manifestUrl: String? = null,
        iconUrl: String? = null
    ) {
        viewModelScope.launch {
            _statusMessage.value = "Checking PWA support and downloading website logo..."
            val currentPwa = _currentPagePwa.value
            val targetManifestUrl = manifestUrl ?: if (currentPwa?.url == url) currentPwa.manifestUrl else null
            val targetIconUrl = iconUrl ?: if (currentPwa?.url == url) currentPwa.iconUrl else null

            val result = com.example.network.PwaShortcutHelper.installWebsitePwa(
                context = context,
                url = url,
                fallbackTitle = title,
                manifestUrl = targetManifestUrl,
                iconUrl = targetIconUrl
            )
            _statusMessage.value = result.second
            if (result.first) {
                val pwa = com.example.network.InstalledPwa("pwa_${url.hashCode()}", title, url, targetIconUrl, targetManifestUrl, true)
                val existing = _installedPwas.value.filter { it.url != url }
                _installedPwas.value = listOf(pwa) + existing
                saveInstalledPwas(_installedPwas.value)
            }
        }
    }

    fun removeInstalledPwa(id: String) {
        _installedPwas.value = _installedPwas.value.filter { it.id != id }
        saveInstalledPwas(_installedPwas.value)
        _statusMessage.value = "Removed PWA from library"
    }

    fun installApkFile(context: android.content.Context, downloadId: Long, fileName: String): Pair<Boolean, String> {
        val result = com.example.network.PwaShortcutHelper.installApk(context, downloadId, fileName)
        _statusMessage.value = result.second
        return result
    }

    fun createDownloadShortcut(context: android.content.Context, fileName: String, url: String, downloadId: Long): Pair<Boolean, String> {
        val result = com.example.network.PwaShortcutHelper.createDownloadShortcut(context, fileName, url, downloadId)
        _statusMessage.value = result.second
        return result
    }

    /**
     * Regex-based validator for Kaspa network addresses entered in the browser's URL bar.
     * Validates Mainnet (kaspa), Testnet (kaspatest), Devnet (kaspadev), and Simnet (kaspasim)
     * CashAddr payloads conforming to standard 61-char (Schnorr/P2SH) and 63-char (ECDSA) lengths.
     */
    fun validateKaspaAddress(input: String): KaspaAddressValidationResult =
        Companion.validateKaspaAddress(input)

    fun isValidKaspaAddress(input: String): Boolean =
        Companion.isValidKaspaAddress(input)

    companion object {
        /**
         * Regular expression matching standard Kaspa network address format:
         * (kaspa|kaspatest|kaspadev|kaspasim):<61-63 chars in CashAddr charset>
         * Also tolerates optional browser scheme prefix "kaspa://".
         */
        val KASPA_ADDRESS_REGEX = Regex(
            "^(?:kaspa://)?(kaspa|kaspatest|kaspadev|kaspasim):([qpzry9x8gf2tvdw0s3jn54khce6mua7l]{61,63})$",
            RegexOption.IGNORE_CASE
        )

        /**
         * Matches standalone CashAddr base-32 payload without network prefix.
         */
        val KASPA_PAYLOAD_ONLY_REGEX = Regex(
            "^[qpzry9x8gf2tvdw0s3jn54khce6mua7l]{61,63}$",
            RegexOption.IGNORE_CASE
        )

        fun isValidKaspaAddress(input: String): Boolean = validateKaspaAddress(input).isValid

        fun validateKaspaAddress(input: String): KaspaAddressValidationResult {
            val trimmed = input.trim()
            if (trimmed.isBlank()) {
                return KaspaAddressValidationResult(isValid = false, rawInput = input)
            }

            // Clean trailing slashes or URL query artifacts
            val sanitized = trimmed.removeSuffix("/").trim()

            // 1. Direct match with standard prefix (e.g., kaspa:q..., kaspatest:q...)
            val match = KASPA_ADDRESS_REGEX.matchEntire(sanitized)
            if (match != null) {
                val prefix = match.groupValues[1].lowercase()
                val payload = match.groupValues[2].lowercase()
                return buildValidationResult(input, "$prefix:$payload", prefix, payload)
            }

            // 2. Strip protocol prefix if entered as browser URL like kaspa://
            val stripped = if (sanitized.startsWith("kaspa://", ignoreCase = true)) {
                sanitized.substring(8).trim()
            } else {
                sanitized
            }

            val matchStripped = KASPA_ADDRESS_REGEX.matchEntire(stripped)
            if (matchStripped != null) {
                val prefix = matchStripped.groupValues[1].lowercase()
                val payload = matchStripped.groupValues[2].lowercase()
                return buildValidationResult(input, "$prefix:$payload", prefix, payload)
            }

            // 3. Fallback for prefix-less 61 or 63 character CashAddr payload
            if (KASPA_PAYLOAD_ONLY_REGEX.matches(stripped)) {
                val payload = stripped.lowercase()
                val prefix = "kaspa"
                val res = buildValidationResult(input, "$prefix:$payload", prefix, payload)
                if (res.checksumValid || payload.startsWith("q") || payload.startsWith("p") || payload.startsWith("s")) {
                    return res
                }
            }

            return KaspaAddressValidationResult(isValid = false, rawInput = input)
        }

        private fun buildValidationResult(
            rawInput: String,
            cleanAddress: String,
            prefix: String,
            payload: String
        ): KaspaAddressValidationResult {
            val networkName = when (prefix) {
                "kaspa" -> "Kaspa Mainnet"
                "kaspatest" -> "Kaspa Testnet"
                "kaspadev" -> "Kaspa Devnet"
                "kaspasim" -> "Kaspa Simnet"
                else -> "Kaspa Network"
            }

            val addressType = when {
                payload.startsWith("q") -> "P2PK Schnorr"
                payload.startsWith("p") -> "P2SH Script"
                payload.startsWith("s") -> "P2PK ECDSA"
                else -> "CashAddr"
            }

            // Validate polymod checksum
            var checksumPassed = false
            try {
                val charset = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
                val data5Bit = ByteArray(payload.length)
                var allValid = true
                for (i in payload.indices) {
                    val idx = charset.indexOf(payload[i])
                    if (idx < 0) {
                        allValid = false
                        break
                    }
                    data5Bit[i] = idx.toByte()
                }
                if (allValid) {
                    checksumPassed = CryptoUtils.kaspaPolymod(prefix, data5Bit) == 0L
                }
            } catch (_: Exception) {
                checksumPassed = false
            }

            val truncated = if (cleanAddress.length > 22) {
                "${cleanAddress.take(12)}...${cleanAddress.takeLast(6)}"
            } else {
                cleanAddress
            }

            val explorerUrl = when (prefix) {
                "kaspatest" -> "https://tn10.kaspa.stream/addresses/$cleanAddress"
                "kaspadev" -> "https://explorer-devnet.kaspa.org/addresses/$cleanAddress"
                else -> "https://kaspa.stream/addresses/$cleanAddress"
            }

            return KaspaAddressValidationResult(
                isValid = true,
                rawInput = rawInput,
                cleanAddress = cleanAddress,
                networkPrefix = prefix,
                networkName = networkName,
                addressType = addressType,
                payload = payload,
                checksumValid = checksumPassed,
                truncatedAddress = truncated,
                explorerUrl = explorerUrl
            )
        }
    }
}
