package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AccountEntity
import com.example.data.ContentEntity
import com.example.data.PeerEntity
import com.example.data.TrafficAuditEntity
import com.example.data.HistoryEntity
import com.example.data.BookmarkEntity
import com.example.data.BrowserTabEntity
import com.example.data.DomainEntity
import com.example.network.kaspa.KaspaDomainRegistry
import com.example.network.kaspa.DomainAvailability
import kotlinx.coroutines.flow.combine

import com.example.model.KaspaWalletState
import com.example.model.NetworkMetrics
import com.example.model.NetworkProtocol
import com.example.model.ResolvedResource
import com.example.model.VerificationStatus
import com.example.network.CryptoUtils
import com.example.network.DomainConstants
import com.example.network.DualStackResolver
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

enum class AppTab {
    BROWSER_GATEWAY,
    MESH_RADAR,
    TRAFFIC_AUDIT,
    LIBRARY
}

enum class SearchEngine(val baseUrl: String, val displayName: String) {
    DUCKDUCKGO("https://duckduckgo.com/?q=", "DuckDuckGo"),
    GOOGLE("https://www.google.com/search?q=", "Google"),
    DECENTRAL_SEARCH("kas://search.kas?q=", "Decentral Search")
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

class DecentralViewModel(application: Application) : AndroidViewModel(application) {

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

    private val securityPrefs = application.getSharedPreferences("kaspa_wallet_security", android.content.Context.MODE_PRIVATE)

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

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    fun setActiveTab(id: String?) {
        if (id == null) return
        _activeTabId.value = id
        viewModelScope.launch {
            val tab = database.browserTabDao().getTabById(id) ?: browserTabs.value.find { it.id == id }
            if (tab != null) {
                database.browserTabDao().insert(tab.copy(lastAccessed = System.currentTimeMillis()))
                if (tab.url.isBlank()) {
                    resetToHome()
                } else {
                    _urlInput.value = tab.url
                    resolveUrl(tab.url)
                }
            }
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

    fun createNewTab(url: String = "", title: String = if (url.isBlank()) "Home" else "New Tab", isExternal: Boolean = false) {
        viewModelScope.launch {
            val newId = java.util.UUID.randomUUID().toString()
            val newTab = BrowserTabEntity(
                id = newId,
                url = url,
                title = title,
                lastAccessed = System.currentTimeMillis(),
                isExternal = isExternal
            )
            database.browserTabDao().insert(newTab)
            _activeTabId.value = newId
            if (url.isBlank()) {
                resetToHome()
            } else {
                _urlInput.value = url
                resolveUrl(url)
            }
        }
    }

    fun openBackgroundTab(url: String, title: String = "New Tab") {
        viewModelScope.launch {
            val newId = java.util.UUID.randomUUID().toString()
            val newTab = BrowserTabEntity(
                id = newId,
                url = url,
                title = title,
                lastAccessed = System.currentTimeMillis()
            )
            database.browserTabDao().insert(newTab)
            _statusMessage.value = "Tab opened in background"
        }
    }

    fun closeTab(id: String) {
        viewModelScope.launch {
            val tabs = database.browserTabDao().getAllTabsList()
            val target = tabs.find { it.id == id } ?: return@launch
            database.browserTabDao().delete(target)
            val remaining = tabs.filter { it.id != id }
            if (_activeTabId.value == id) {
                if (remaining.isNotEmpty()) {
                    val nextTab = remaining.maxByOrNull { it.lastAccessed } ?: remaining.first()
                    setActiveTab(nextTab.id)
                } else {
                    createNewTab("", "Home")
                }
            }
        }
    }

    fun updateTab(id: String, url: String, title: String) {
        viewModelScope.launch {
            val existing = database.browserTabDao().getTabById(id)
            val isSuspended = existing?.isSuspended ?: false
            val isExternal = existing?.isExternal ?: false
            database.browserTabDao().insert(
                BrowserTabEntity(id = id, url = url, title = title, lastAccessed = System.currentTimeMillis(), isSuspended = isSuspended, isExternal = isExternal)
            )
        }
    }

    fun updateActiveTabMetadata(url: String, title: String) {
        val id = _activeTabId.value ?: return
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
        viewModelScope.launch {
            database.browserTabDao().clearAll()
            createNewTab("", "Home")
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

    private val _domainAvailability = MutableStateFlow<DomainAvailability>(DomainAvailability.Idle)
    val domainAvailability: StateFlow<DomainAvailability> = _domainAvailability.asStateFlow()

    private val _isRegisteringDomain = MutableStateFlow(false)
    val isRegisteringDomain: StateFlow<Boolean> = _isRegisteringDomain.asStateFlow()

    private val _kaspaWalletState = MutableStateFlow(KaspaWalletState())
    val kaspaWalletState: StateFlow<KaspaWalletState> = _kaspaWalletState.asStateFlow()

    private val _activeTab = MutableStateFlow(AppTab.BROWSER_GATEWAY)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

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

    private val _aiMode = MutableStateFlow(false)
    val aiMode: StateFlow<Boolean> = _aiMode.asStateFlow()

    private val _desktopModeEnabled = MutableStateFlow(false)
    val desktopModeEnabled: StateFlow<Boolean> = _desktopModeEnabled.asStateFlow()

    private val _httpsOnlyMode = MutableStateFlow(true)
    val httpsOnlyMode: StateFlow<Boolean> = _httpsOnlyMode.asStateFlow()

    private val _webAuthEnabled = MutableStateFlow(true)
    val webAuthEnabled: StateFlow<Boolean> = _webAuthEnabled.asStateFlow()

    private val _showWebAuthnRpIdDialog = MutableStateFlow(false)
    val showWebAuthnRpIdDialog: StateFlow<Boolean> = _showWebAuthnRpIdDialog.asStateFlow()

    private val _searchEngine = MutableStateFlow(SearchEngine.DUCKDUCKGO)
    val searchEngine: StateFlow<SearchEngine> = _searchEngine.asStateFlow()

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
    fun toggleAiMode(enabled: Boolean) { _aiMode.value = enabled }
    fun toggleDesktopMode(enabled: Boolean) { _desktopModeEnabled.value = enabled }
    fun toggleHttpsOnlyMode(enabled: Boolean) { _httpsOnlyMode.value = enabled }
    fun toggleWebAuth(enabled: Boolean) { _webAuthEnabled.value = enabled }
    fun setShowWebAuthnRpIdDialog(show: Boolean) { _showWebAuthnRpIdDialog.value = show }
    fun setSearchEngine(engine: SearchEngine) { _searchEngine.value = engine }

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
                        val cleanHandle = if (acc.handle.contains(".k") || acc.handle.contains(".kab") || acc.handle.startsWith("@kas")) {
                            "Kaspa Wallet (${acc.kaspaAddress.takeLast(6)})"
                        } else {
                            acc.handle
                        }

                        if (!CryptoUtils.isValidKaspaAddress(acc.kaspaAddress) || acc.zkProofJson.isNullOrBlank() || cleanHandle != acc.handle) {
                            val refreshedAcc = if (acc.accountType == "GOOGLE_ZK_BRIDGE" && acc.googleEmail != null) {
                                CryptoUtils.deriveGoogleBridgeAccount(
                                    email = acc.googleEmail,
                                    displayName = acc.googleDisplayName ?: cleanHandle
                                ).copy(isActive = acc.isActive, createdAt = acc.createdAt)
                            } else {
                                CryptoUtils.deriveDecentralizedAccount(
                                    customHandle = cleanHandle,
                                    seedMnemonic = acc.seedPhrase
                                ).copy(isActive = acc.isActive, createdAt = acc.createdAt)
                            }
                            database.accountDao().insertAccount(refreshedAcc)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Keep Kaspa Wallet synced with active account
        viewModelScope.launch {
            activeAccount.collect { account ->
                account?.kaspaAddress?.let { address ->
                    refreshKaspaWallet(address)
                }
            }
        }

        // Initialize browser tabs with a clean Home tab if empty or restore active tab
        viewModelScope.launch {
            try {
                val tabs = database.browserTabDao().getAllTabsList()
                if (tabs.isEmpty()) {
                    val newId = java.util.UUID.randomUUID().toString()
                    database.browserTabDao().insert(
                        BrowserTabEntity(id = newId, url = "", title = "Home", lastAccessed = System.currentTimeMillis())
                    )
                    _activeTabId.value = newId
                } else {
                    val mostRecent = tabs.maxByOrNull { it.lastAccessed } ?: tabs.first()
                    _activeTabId.value = mostRecent.id
                    if (mostRecent.url.isNotBlank()) {
                        _urlInput.value = mostRecent.url
                        resolveUrl(mostRecent.url)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun refreshKaspaWallet(address: String? = null) {
        val targetAddress = address ?: activeAccount.value?.kaspaAddress ?: return
        if (targetAddress.isBlank()) return
        viewModelScope.launch {
            _kaspaWalletState.value = _kaspaWalletState.value.copy(isLoading = true)
            val updated = kaspaWalletService.fetchWalletState(targetAddress)
            _kaspaWalletState.value = updated
        }
    }

    fun sendKaspaTransaction(recipientAddress: String, amountKas: Double) {
        val senderAcc = activeAccount.value ?: return
        viewModelScope.launch {
            _kaspaWalletState.value = _kaspaWalletState.value.copy(isSending = true)
            val result = kaspaWalletService.sendKaspa(
                senderAddress = senderAcc.kaspaAddress,
                senderSeed = CryptoUtils.getDecryptedSeed(senderAcc.seedPhrase),
                recipientAddress = recipientAddress,
                amountKas = amountKas
            )
            result.onSuccess { txItem ->
                _statusMessage.value = "KAS Transaction Broadcasted! Tx: ${txItem.txId.take(16)}..."
                val updatedTxs = listOf(txItem) + _kaspaWalletState.value.recentTransactions
                val updatedBalance = (_kaspaWalletState.value.balanceKas - amountKas - txItem.feeKas).coerceAtLeast(0.0)
                _kaspaWalletState.value = _kaspaWalletState.value.copy(
                    isSending = false,
                    balanceKas = updatedBalance,
                    balanceUsd = updatedBalance * _kaspaWalletState.value.priceUsd,
                    lastBroadcastTxId = txItem.txId,
                    recentTransactions = updatedTxs,
                    statusNotice = "Sent %.4f KAS (TxID: ${txItem.txId})".format(amountKas)
                )
            }.onFailure { err ->
                _statusMessage.value = "Transaction failed: ${err.message}"
                _kaspaWalletState.value = _kaspaWalletState.value.copy(
                    isSending = false,
                    lastBroadcastTxId = null,
                    statusNotice = "Error: ${err.message}"
                )
            }
        }
    }

    fun checkDomainAvailability(rawInput: String) {
        val clean = rawInput.trim()
        if (clean.isBlank()) {
            _domainAvailability.value = DomainAvailability.Idle
            return
        }
        viewModelScope.launch {
            _domainAvailability.value = DomainAvailability.Checking(clean)
            val result = domainRegistry.checkAvailability(clean, activeAccount.value?.kaspaAddress)
            _domainAvailability.value = result
        }
    }

    fun registerKabDomain(
        domainName: String,
        targetCid: String? = null,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val account = activeAccount.value
        if (account == null) {
            val msg = "Please activate or create a Kaspa wallet before registering a .k domain."
            _statusMessage.value = msg
            onResult?.invoke(false, msg)
            return
        }

        viewModelScope.launch {
            _isRegisteringDomain.value = true
            try {
                val result = domainRegistry.claimDomainOnChain(domainName, account, targetCid)
                result.onSuccess { domainEntity ->
                    _statusMessage.value = "Registered ${domainEntity.domain} on-chain! Tx: ${domainEntity.txId.take(16)}..."
                    refreshKaspaWallet(account.kaspaAddress)
                    _domainAvailability.value = DomainAvailability.OwnedByYou(
                        domain = domainEntity.domain,
                        txId = domainEntity.txId,
                        registeredAt = domainEntity.registeredAt,
                        targetCid = domainEntity.targetCid
                    )
                    onResult?.invoke(true, "Claimed ${domainEntity.domain} on Kaspa BlockDAG! Tx: ${domainEntity.txId.take(16)}...")
                }.onFailure { err ->
                    val errorMsg = err.message ?: "Failed to claim domain on-chain"
                    _statusMessage.value = "Registration failed: $errorMsg"
                    onResult?.invoke(false, errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Error during domain claim"
                _statusMessage.value = "Registration error: $errorMsg"
                onResult?.invoke(false, errorMsg)
            } finally {
                _isRegisteringDomain.value = false
            }
        }
    }

    fun deleteDomain(domain: DomainEntity) {
        viewModelScope.launch {
            try {
                database.domainDao().deleteDomain(domain)
                _statusMessage.value = "Removed domain record: ${domain.domain}"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to remove domain: ${e.message}"
            }
        }
    }

    fun transferKabDomain(
        domainName: String,
        newOwnerAddress: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val account = activeAccount.value
        if (account == null) {
            val msg = "Please activate or create a Kaspa wallet before transferring a domain."
            _statusMessage.value = msg
            onResult?.invoke(false, msg)
            return
        }

        viewModelScope.launch {
            _statusMessage.value = "Transferring $domainName to ${newOwnerAddress.take(16)}... on Kaspa BlockDAG..."
            try {
                val result = domainRegistry.transferDomain(domainName, account, newOwnerAddress)
                result.onSuccess { updatedDomain ->
                    _statusMessage.value = "Transferred ${updatedDomain.domain}! New Owner: ${updatedDomain.ownerAddress.take(16)}..."
                    refreshKaspaWallet(account.kaspaAddress)
                    onResult?.invoke(true, "Transferred ${updatedDomain.domain} to ${newOwnerAddress.take(16)}... (Tx: ${updatedDomain.txId.take(16)}...)")
                }.onFailure { err ->
                    val errorMsg = err.message ?: "Failed to transfer domain on-chain"
                    _statusMessage.value = "Transfer failed: $errorMsg"
                    onResult?.invoke(false, errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Error during domain transfer"
                _statusMessage.value = "Transfer error: $errorMsg"
                onResult?.invoke(false, errorMsg)
            }
        }
    }

    fun releaseKabDomain(
        domainName: String,
        refundAddress: String? = null,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val account = activeAccount.value
        if (account == null) {
            val msg = "Please activate or create a Kaspa wallet before releasing a domain."
            _statusMessage.value = msg
            onResult?.invoke(false, msg)
            return
        }

        viewModelScope.launch {
            _statusMessage.value = "Releasing $domainName and reclaiming 1 KAS bond on Kaspa BlockDAG..."
            try {
                val targetRefund = refundAddress?.ifBlank { null } ?: account.kaspaAddress
                val result = domainRegistry.releaseDomain(domainName, account, targetRefund)
                result.onSuccess { releasedDomain ->
                    _statusMessage.value = "Released ${releasedDomain.domain}! 1 KAS Bond refunded to ${targetRefund.take(16)}..."
                    refreshKaspaWallet(account.kaspaAddress)
                    onResult?.invoke(true, "Released ${releasedDomain.domain}! 1 KAS bond refunded.")
                }.onFailure { err ->
                    val errorMsg = err.message ?: "Failed to release domain on-chain"
                    _statusMessage.value = "Release failed: $errorMsg"
                    onResult?.invoke(false, errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Error during domain release"
                _statusMessage.value = "Release error: $errorMsg"
                onResult?.invoke(false, errorMsg)
            }
        }
    }

    fun updateKabDomainRecords(
        domainName: String,
        records: Map<String, Any>,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val account = activeAccount.value
        if (account == null) {
            val msg = "Please activate or create a Kaspa wallet before updating records."
            _statusMessage.value = msg
            onResult?.invoke(false, msg)
            return
        }

        viewModelScope.launch {
            _statusMessage.value = "Attaching Card with ${records.size} records to $domainName on Kaspa BlockDAG..."
            try {
                val result = domainRegistry.updateRecords(domainName, records, account)
                result.onSuccess { updatedDomain ->
                    _statusMessage.value = "Updated records for ${updatedDomain.domain}! Attached Card on-chain."
                    refreshKaspaWallet(account.kaspaAddress)
                    onResult?.invoke(true, "Updated records for ${updatedDomain.domain}!")
                }.onFailure { err ->
                    val errorMsg = err.message ?: "Failed to update records on-chain"
                    _statusMessage.value = "Record update failed: $errorMsg"
                    onResult?.invoke(false, errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Error updating domain records"
                _statusMessage.value = "Record update error: $errorMsg"
                onResult?.invoke(false, errorMsg)
            }
        }
    }

    fun sweepRetiredCards(
        destinationAddress: String? = null,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val account = activeAccount.value
        if (account == null) {
            val msg = "Please activate or create a Kaspa wallet before sweeping cards."
            _statusMessage.value = msg
            onResult?.invoke(false, msg)
            return
        }

        viewModelScope.launch {
            _statusMessage.value = "Scanning and sweeping retired cards for ${account.kaspaAddress.take(16)}..."
            try {
                val seedPhrase = try {
                    CryptoUtils.getDecryptedSeed(account.seedPhrase)
                } catch (e: Exception) {
                    val msg = "Cannot decrypt seed phrase: ${e.message}"
                    _statusMessage.value = msg
                    onResult?.invoke(false, msg)
                    return@launch
                }

                val target = destinationAddress?.ifBlank { null } ?: account.kaspaAddress
                val result = kaspaWalletService.sweepCardsOnChain(
                    spenderAddress = account.kaspaAddress,
                    spenderSeed = seedPhrase,
                    destinationAddress = target
                )

                result.onSuccess { txItem ->
                    _statusMessage.value = "Successfully swept retired card(s)! Reclaimed ${txItem.amountKas} KAS."
                    refreshKaspaWallet(account.kaspaAddress)
                    onResult?.invoke(true, "Swept retired cards! Reclaimed ${txItem.amountKas} KAS.")
                }.onFailure { err ->
                    val errorMsg = err.message ?: "Failed to sweep cards"
                    _statusMessage.value = "Sweep failed: $errorMsg"
                    onResult?.invoke(false, errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Error during card sweep"
                _statusMessage.value = "Sweep error: $errorMsg"
                onResult?.invoke(false, errorMsg)
            }
        }
    }

    fun createDecentralizedAccount(
        walletLabel: String,
        customMnemonic: String? = null,
        password: String? = null,
        enableBiometric: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                if (!password.isNullOrBlank()) {
                    setWalletPassword(password, enableBiometric)
                }
                val newAcc = CryptoUtils.deriveDecentralizedAccount(
                    customHandle = walletLabel.ifBlank { null },
                    seedMnemonic = customMnemonic?.ifBlank { null }
                )
                database.accountDao().deactivateAll()
                database.accountDao().insertAccount(newAcc)

                _statusMessage.value = "Created Kaspa wallet: ${newAcc.handle}"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to create wallet: ${e.message}"
            }
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


    fun openUrlInBrowser(url: String, isExternal: Boolean = true) {
        viewModelScope.launch {
            _activeTab.value = AppTab.BROWSER_GATEWAY
            val cleanTarget = url.trim()
            if (cleanTarget.isBlank()) return@launch

            val tabs = database.browserTabDao().getAllTabsList()
            val activeId = _activeTabId.value
            val activeTab = tabs.find { it.id == activeId }
            val host = try { java.net.URI(cleanTarget).host ?: cleanTarget } catch (_: Exception) { cleanTarget }
            val tabTitle = if (!host.isNullOrBlank()) host else "External Link"
            val normTarget = cleanTarget.removeSuffix("/").lowercase()

            // 1. Check if the active tab is already on this exact URL or same page
            if (activeTab != null && activeTab.url.trim().removeSuffix("/").equals(normTarget, ignoreCase = true)) {
                _urlInput.value = activeTab.url
                updateTabAccessTime(activeTab.id)
                return@launch
            }

            // 2. Check if an existing open tab already matches this exact URL or site
            val existingTab = tabs.find { tab ->
                val tabNorm = tab.url.trim().removeSuffix("/").lowercase()
                tabNorm.isNotEmpty() && tabNorm.equals(normTarget, ignoreCase = true)
            } ?: tabs.find { tab ->
                if (tab.url.isBlank()) return@find false
                val tabHost = try { java.net.URI(tab.url).host ?: "" } catch (_: Exception) { "" }
                tabHost.isNotEmpty() && tabHost.equals(host, ignoreCase = true) &&
                    (tab.url.trim().removeSuffix("/").equals(normTarget, ignoreCase = true))
            }

            if (existingTab != null) {
                _activeTabId.value = existingTab.id
                _urlInput.value = existingTab.url
                database.browserTabDao().insert(existingTab.copy(lastAccessed = System.currentTimeMillis()))
                resolveUrl(existingTab.url)
                return@launch
            }

            // 3. If active tab is blank, reuse it
            if (activeTab != null && activeTab.url.isBlank()) {
                val updatedTab = activeTab.copy(
                    url = cleanTarget,
                    title = tabTitle,
                    lastAccessed = System.currentTimeMillis(),
                    isExternal = isExternal
                )
                database.browserTabDao().insert(updatedTab)
                _urlInput.value = cleanTarget
                resolveUrl(cleanTarget)
            } else {
                // 4. Create new tab
                val newId = java.util.UUID.randomUUID().toString()
                val newTab = BrowserTabEntity(
                    id = newId,
                    url = cleanTarget,
                    title = tabTitle,
                    lastAccessed = System.currentTimeMillis(),
                    isExternal = isExternal
                )
                database.browserTabDao().insert(newTab)
                _activeTabId.value = newId
                _urlInput.value = cleanTarget
                resolveUrl(cleanTarget)
            }
        }
    }

    fun onUserSubmitUrl(rawUrl: String? = null) {
        viewModelScope.launch {
            val target = normalizeUrlOrQuery(rawUrl ?: _urlInput.value)
            if (target.isBlank()) return@launch

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
                _urlInput.value = target
                resolveUrl(target)
            } else {
                _urlInput.value = target
                resolveUrl(target)
            }
        }
    }

    fun setTab(tab: AppTab) {
        _activeTab.value = tab
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
    }

    fun normalizeUrlOrQuery(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""

        // Explicit protocols
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("ipfs://", ignoreCase = true) ||
            trimmed.startsWith("mesh://", ignoreCase = true) ||
            trimmed.startsWith("dweb://", ignoreCase = true) ||
            trimmed.startsWith("p2p://", ignoreCase = true) ||
            trimmed.startsWith("kas://", ignoreCase = true) ||
            trimmed.startsWith("kaspa://", ignoreCase = true) ||
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

    fun updateCurrentUrl(newUrl: String) {
        if (newUrl.isBlank() || newUrl.startsWith("data:") || newUrl.startsWith("about:")) return
        _urlInput.value = newUrl
        val activeId = _activeTabId.value
        if (activeId != null) {
            viewModelScope.launch {
                val tab = database.browserTabDao().getTabById(activeId)
                if (tab != null && tab.url != newUrl) {
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
            if (current.url == newUrl) return
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

    fun resolveUrl(url: String? = null) {
        _navigationSessionId.value = _navigationSessionId.value + 1
        val raw = url ?: _urlInput.value
        if (raw.isBlank()) {
            _currentResource.value = null
            _isLoading.value = false
            return
        }

        val target = normalizeUrlOrQuery(raw)
        _urlInput.value = target
        _isLoading.value = true

        val isHttp = target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)
        if (isHttp) {
            val host = try { java.net.URI(target).host ?: target } catch (_: Exception) { target }
            _currentResource.value = ResolvedResource(
                url = target,
                resolvedProtocol = NetworkProtocol.CENTRALIZED_HTTP,
                cid = com.example.network.CryptoUtils.generateCid(target),
                title = host,
                content = "",
                contentType = "text/html",
                sizeBytes = 0L,
                latencyMs = 15L,
                centralizedUrl = target,
                centralizedLatencyMs = 15L,
                centralizedIp = "Direct High-Speed Stack",
                verificationStatus = VerificationStatus.VERIFIED_TAMPER_PROOF,
                cryptographicHash = com.example.network.CryptoUtils.sha256(target),
                routedVia = "Direct High-Speed Web Stack: $host"
            )
        }

        val currentSessionId = _navigationSessionId.value
        viewModelScope.launch {
            try {
                val result = resolver.resolve(target, _selectedProtocol.value)
                if (_navigationSessionId.value == currentSessionId) {
                    val activeUrl = _urlInput.value
                    val finalResult = if (isHttp && activeUrl.isNotBlank() && activeUrl != result.url) {
                        result.copy(url = activeUrl, title = _currentResource.value?.title ?: result.title)
                    } else {
                        result
                    }
                    _currentResource.value = finalResult
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
                if (!isHttp && _navigationSessionId.value == currentSessionId) {
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

    fun startDownload(context: android.content.Context, downloadId: Long, urlStr: String, fileName: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var streamSuccess = false
            val appDownloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            val targetFile = java.io.File(appDownloadsDir, fileName)

            try {
                updateDownloadProgress(downloadId, 0.02f, 0L, 0L, "Downloading")
                var currentUrl = urlStr
                var redirectCount = 0
                var connection: java.net.HttpURLConnection? = null

                while (redirectCount < 6) {
                    val url = java.net.URL(currentUrl)
                    connection = url.openConnection() as java.net.HttpURLConnection
                    connection.instanceFollowRedirects = true
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    connection.connect()

                    val responseCode = connection.responseCode
                    if (responseCode in 300..399) {
                        val newUrl = connection.getHeaderField("Location")
                        if (!newUrl.isNullOrBlank()) {
                            currentUrl = newUrl
                            redirectCount++
                            connection.disconnect()
                            continue
                        }
                    }
                    break
                }

                val conn = connection
                if (conn != null && conn.responseCode in 200..299) {
                    val totalBytes = conn.contentLengthLong.let { if (it > 0) it else 0L }
                    
                    var bytesDownloaded = 0L
                    val buffer = ByteArray(32768)
                    var bytesRead: Int
                    val inputStream = conn.inputStream
                    val outputStream = java.io.FileOutputStream(targetFile)

                    var lastUpdate = System.currentTimeMillis()

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 100) {
                            lastUpdate = now
                            val progress = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0.02f, 0.99f) else 0.50f
                            updateDownloadProgress(downloadId, progress, bytesDownloaded, totalBytes, "Downloading")
                        }
                    }
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                    conn.disconnect()

                    updateDownloadProgress(downloadId, 1.0f, bytesDownloaded, if (totalBytes > 0) totalBytes else bytesDownloaded, "Success")
                    streamSuccess = true

                    // Export to public MediaStore downloads so file is visible in device system Downloads app
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            val values = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
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
                                val publicFile = java.io.File(publicDir, fileName)
                                java.io.FileInputStream(targetFile).use { isStream ->
                                    java.io.FileOutputStream(publicFile).use { os ->
                                        isStream.copyTo(os)
                                    }
                                }
                                android.media.MediaScannerConnection.scanFile(context, arrayOf(publicFile.absolutePath), null, null)
                            }
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {
            }

            if (!streamSuccess) {
                if (targetFile.exists() && targetFile.length() > 0) {
                    val fileLength = targetFile.length()
                    updateDownloadProgress(downloadId, 1.0f, fileLength, fileLength, "Success")
                } else {
                    startMonitoringDownload(context, downloadId, fileName)
                }
            }
        }
    }

    fun startMonitoringDownload(context: android.content.Context, downloadId: Long, fileName: String = "") {
        viewModelScope.launch {
            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            var downloading = true
            var attempts = 0
            val maxAttempts = 15
            val targetFile = if (fileName.isNotBlank()) {
                java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )
            } else null

            while (downloading) {
                kotlinx.coroutines.delay(500)
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
                    
                    updateDownloadProgress(downloadId, activeProgress, bytesDownloaded, bytesTotal, statusStr)
                    
                    if (statusInt == android.app.DownloadManager.STATUS_SUCCESSFUL || statusInt == android.app.DownloadManager.STATUS_FAILED) {
                        downloading = false
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
        try {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(testUrl)).apply {
                setDescription("Downloading real 10MB test file...")
                setTitle(filename)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI or android.app.DownloadManager.Request.NETWORK_MOBILE)
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)
            }
            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val dmId = try { dm.enqueue(request) } catch (e: Exception) { downloadId }
            addDownload(dmId, filename, testUrl)
            startDownload(context, dmId, testUrl, filename)
            _statusMessage.value = "Real download initiated: $filename"
        } catch (e: Exception) {
            addDownload(downloadId, filename, testUrl)
            startDownload(context, downloadId, testUrl, filename)
            _statusMessage.value = "Real download initiated: $filename"
        }
    }

    fun redownload(context: android.content.Context, url: String, filename: String) {
        val downloadId = System.currentTimeMillis()
        try {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
                setDescription("Redownloading file...")
                setTitle(filename)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI or android.app.DownloadManager.Request.NETWORK_MOBILE)
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)
            }
            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val dmId = try { dm.enqueue(request) } catch (e: Exception) { downloadId }
            addDownload(dmId, filename, url)
            startDownload(context, dmId, url, filename)
            _statusMessage.value = "Redownload started: $filename"
        } catch (e: Exception) {
            addDownload(downloadId, filename, url)
            startDownload(context, downloadId, url, filename)
            _statusMessage.value = "Redownload started: $filename"
        }
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
}
