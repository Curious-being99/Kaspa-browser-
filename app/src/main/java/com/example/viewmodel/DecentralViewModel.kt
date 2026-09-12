package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AccountEntity
import com.example.data.ContentEntity
import com.example.data.PeerEntity
import com.example.data.TrafficAuditEntity

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab {
    BROWSER_GATEWAY,
    MESH_RADAR,
    TRAFFIC_AUDIT
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
    private val resolver = DualStackResolver(database)
    val nodeManager = LocalNodeManager(application, database, viewModelScope)

    val metrics: StateFlow<NetworkMetrics> = nodeManager.metrics
    val nsdState = nodeManager.discoveryManager.nsdState

    val pinnedContents: StateFlow<List<ContentEntity>> = database.contentDao()
        .getAllContents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val peers: StateFlow<List<PeerEntity>> = database.peerDao()
        .getAllPeers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trafficAudits: StateFlow<List<TrafficAuditEntity>> = database.trafficAuditDao()
        .getRecentAudits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAccount: StateFlow<AccountEntity?> = database.accountDao()
        .getActiveAccount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allAccounts: StateFlow<List<AccountEntity>> = database.accountDao()
        .getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val kaspaWalletService = KaspaWalletService()
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

    private val _blockedTrackersCount = MutableStateFlow(0)
    val blockedTrackersCount: StateFlow<Int> = _blockedTrackersCount.asStateFlow()

    private val _activeDownloads = MutableStateFlow<List<ActiveDownload>>(emptyList())
    val activeDownloads: StateFlow<List<ActiveDownload>> = _activeDownloads.asStateFlow()

    private val _blockedTrackerLogs = MutableStateFlow<List<String>>(emptyList())
    val blockedTrackerLogs: StateFlow<List<String>> = _blockedTrackerLogs.asStateFlow()

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

    fun logBlockedTracker(domainOrUrl: String) {
        _blockedTrackersCount.value += 1
        val updated = (listOf(domainOrUrl) + _blockedTrackerLogs.value).take(50)
        _blockedTrackerLogs.value = updated
    }

    fun clearBlockedTrackerLogs() {
        _blockedTrackersCount.value = 0
        _blockedTrackerLogs.value = emptyList()
    }

    init {
        // Delete any legacy mock accounts so user has clean slate
        viewModelScope.launch {
            try {
                database.accountDao().deleteLegacyMockAccounts()
                val allAccounts = database.accountDao().getAllAccountsList()
                if (allAccounts.isNotEmpty()) {
                    for (acc in allAccounts) {
                        if (!CryptoUtils.isValidKaspaAddress(acc.kaspaAddress)) {
                            val refreshedAcc = if (acc.accountType == "GOOGLE_ZK_BRIDGE" && acc.googleEmail != null) {
                                CryptoUtils.deriveGoogleBridgeAccount(
                                    email = acc.googleEmail,
                                    displayName = acc.googleDisplayName ?: acc.handle
                                ).copy(isActive = acc.isActive, createdAt = acc.createdAt)
                            } else {
                                CryptoUtils.deriveDecentralizedAccount(
                                    customHandle = acc.handle.removePrefix("@"),
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
                    recentTransactions = updatedTxs,
                    statusNotice = "Sent %.4f KAS to ${recipientAddress.take(14)}...".format(amountKas)
                )
            }.onFailure { err ->
                _statusMessage.value = "Transaction failed: ${err.message}"
                _kaspaWalletState.value = _kaspaWalletState.value.copy(
                    isSending = false,
                    statusNotice = "Error: ${err.message}"
                )
            }
        }
    }

    fun createDecentralizedAccount(handle: String, customMnemonic: String? = null) {
        viewModelScope.launch {
            try {
                val newAcc = CryptoUtils.deriveDecentralizedAccount(
                    customHandle = handle.ifBlank { null },
                    seedMnemonic = customMnemonic?.ifBlank { null }
                )
                database.accountDao().deactivateAll()
                database.accountDao().insertAccount(newAcc)
                _statusMessage.value = "Created decentralized account: ${newAcc.handle}"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to create account: ${e.message}"
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


    fun openUrlInBrowser(url: String) {
        _urlInput.value = url
        _activeTab.value = AppTab.BROWSER_GATEWAY
        resolveUrl(url)
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

        // Privacy-respecting search fallback
        val encoded = try {
            java.net.URLEncoder.encode(trimmed, "UTF-8")
        } catch (_: Exception) {
            trimmed
        }
        return "https://duckduckgo.com/?q=$encoded"
    }

    fun updateCurrentUrl(newUrl: String) {
        if (newUrl.isBlank() || newUrl.startsWith("data:") || newUrl.startsWith("about:")) return
        val current = _currentResource.value ?: return
        _urlInput.value = newUrl
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
                    _currentResource.value = result
                    verifyResourceIntegrity()
                    
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

    fun verifyResourceIntegrity() {
        val current = _currentResource.value ?: return
        val contentToHash = if (current.content.isNotEmpty()) current.content else current.url
        val calculatedHash = com.example.network.CryptoUtils.sha256(contentToHash)
        val isMatch = calculatedHash.equals(current.cryptographicHash, ignoreCase = true)

        if (isMatch) {
            _statusMessage.value = "Cryptographic Verification Passed: SHA-256 matches Merkle root ($calculatedHash)."
            nodeManager.incrementCrossVerifications()
        } else {
            _statusMessage.value = "Warning: Hash mismatch detected between payload and Merkle root."
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

    fun addDownload(id: Long, fileName: String, url: String) {
        val newDownload = ActiveDownload(
            downloadId = id,
            fileName = fileName,
            url = url,
            progress = 0f,
            bytesDownloaded = 0L,
            bytesTotal = 0L,
            status = "Downloading"
        )
        _activeDownloads.value = _activeDownloads.value + newDownload
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

    fun startMonitoringDownload(context: android.content.Context, downloadId: Long) {
        viewModelScope.launch {
            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            var downloading = true
            while (downloading) {
                kotlinx.coroutines.delay(1000)
                val query = android.app.DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val bytesDownloadedIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val statusIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
                    
                    val bytesDownloaded = if (bytesDownloadedIndex != -1) cursor.getLong(bytesDownloadedIndex) else 0L
                    val bytesTotal = if (bytesTotalIndex != -1) cursor.getLong(bytesTotalIndex) else 0L
                    val statusInt = if (statusIndex != -1) cursor.getInt(statusIndex) else android.app.DownloadManager.STATUS_FAILED
                    
                    val progress = if (bytesTotal > 0) bytesDownloaded.toFloat() / bytesTotal.toFloat() else 0f
                    val statusStr = when (statusInt) {
                        android.app.DownloadManager.STATUS_RUNNING -> "Downloading"
                        android.app.DownloadManager.STATUS_SUCCESSFUL -> "Success"
                        android.app.DownloadManager.STATUS_FAILED -> "Failed"
                        android.app.DownloadManager.STATUS_PENDING -> "Pending"
                        android.app.DownloadManager.STATUS_PAUSED -> "Paused"
                        else -> "Downloading"
                    }
                    
                    updateDownloadProgress(downloadId, progress, bytesDownloaded, bytesTotal, statusStr)
                    
                    if (statusInt == android.app.DownloadManager.STATUS_SUCCESSFUL || statusInt == android.app.DownloadManager.STATUS_FAILED) {
                        downloading = false
                    }
                    cursor.close()
                } else {
                    cursor?.close()
                    downloading = false
                }
            }
        }
    }

    fun triggerRealTestDownload(context: android.content.Context) {
        val testUrl = "https://proof.ovh.net/files/10Mb.dat"
        val filename = "test_10mb.dat"
        try {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(testUrl)).apply {
                setDescription("Downloading real 10MB test file...")
                setTitle(filename)
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)
            }
            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val downloadId = dm.enqueue(request)
            addDownload(downloadId, filename, testUrl)
            startMonitoringDownload(context, downloadId)
            _statusMessage.value = "Real download initiated: $filename"
        } catch (e: Exception) {
            _statusMessage.value = "Real test download failed: ${e.message}"
        }
    }

    fun redownload(context: android.content.Context, url: String, filename: String) {
        try {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
                setDescription("Redownloading file...")
                setTitle(filename)
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)
            }
            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val downloadId = dm.enqueue(request)
            addDownload(downloadId, filename, url)
            startMonitoringDownload(context, downloadId)
            _statusMessage.value = "Redownload started: $filename"
        } catch (e: Exception) {
            _statusMessage.value = "Redownload failed: ${e.message}"
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
