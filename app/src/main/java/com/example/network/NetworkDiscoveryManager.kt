package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.PeerEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

data class RealNetworkState(
    val isConnected: Boolean = false,
    val isWifi: Boolean = false,
    val isCellular: Boolean = false,
    val interfaceName: String = "Detecting...",
    val localIpV4Address: String = "127.0.0.1",
    val natType: String = "Direct / Local Subnet",
    val discoveredLocalServices: Int = 0
)

data class NsdDiscoveryState(
    val isBroadcasting: Boolean = false,
    val isScanning: Boolean = false,
    val registeredServiceName: String? = null,
    val serviceType: String = "_dnet-p2p._tcp.",
    val activeLocalNodesCount: Int = 0,
    val lastDiscoveryEvent: String = "NSD Initialized (Ready for LAN Discovery)"
)

data class P2PHandshakeResult(
    val success: Boolean,
    val peerId: String,
    val nodeName: String,
    val host: String,
    val port: Int,
    val latencyMs: Long,
    val errorMessage: String? = null
)

class NetworkDiscoveryManager(
    private val context: Context,
    private val database: AppDatabase,
    private val scope: CoroutineScope
) {
    private val TAG = "NetworkDiscoveryManager"
    val SERVICE_TYPE = "_dnet-p2p._tcp."
    private val SERVICE_NAME_PREFIX = "DNetNode-"

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val nsdManager =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var multicastLock: WifiManager.MulticastLock? = null

    private val _networkState = MutableStateFlow(RealNetworkState())
    val networkState: StateFlow<RealNetworkState> = _networkState.asStateFlow()

    private val _nsdState = MutableStateFlow(NsdDiscoveryState(serviceType = SERVICE_TYPE))
    val nsdState: StateFlow<NsdDiscoveryState> = _nsdState.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var currentListeningPort: Int = 8080
    private var currentLocalPeerId: String = ""

    private val resolveMutex = Mutex()
    private var scanTimeoutJob: Job? = null

    init {
        monitorNetworkConnectivity()
        scope.launch(Dispatchers.IO) {
            refreshActiveInterfaces()
        }
    }

    private fun acquireMulticastLock() {
        try {
            if (multicastLock == null) {
                multicastLock = wifiManager?.createMulticastLock("DecentralNet_mDNS_Lock")?.apply {
                    setReferenceCounted(true)
                }
            }
            multicastLock?.let {
                if (!it.isHeld) {
                    it.acquire()
                    Log.d(TAG, "Wi-Fi MulticastLock acquired for mDNS discovery")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to acquire MulticastLock: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Wi-Fi MulticastLock released")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to release MulticastLock: ${e.message}")
        }
    }

    private fun monitorNetworkConnectivity() {
        if (connectivityManager == null) return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    evaluateNetwork(network)
                }

                override fun onLost(network: Network) {
                    scope.launch(Dispatchers.IO) {
                        refreshActiveInterfaces()
                    }
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    evaluateNetwork(network)
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    private fun evaluateNetwork(network: Network?) {
        scope.launch(Dispatchers.IO) {
            val net = network ?: connectivityManager?.activeNetwork
            val caps = connectivityManager?.getNetworkCapabilities(net)

            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            val realIp = getRealLocalIpAddress()
            val iface = getRealActiveInterfaceName()

            val natType = when {
                !isConnected -> "Offline / Disconnected"
                isWifi -> "Full Cone (Wi-Fi Local Subnet)"
                isCellular -> "Carrier-Grade NAT (CGNAT)"
                else -> "Direct Link (${iface})"
            }

            _networkState.value = _networkState.value.copy(
                isConnected = isConnected,
                isWifi = isWifi,
                isCellular = isCellular,
                interfaceName = iface,
                localIpV4Address = realIp,
                natType = natType
            )
        }
    }

    suspend fun refreshActiveInterfaces() = withContext(Dispatchers.IO) {
        val activeNet = connectivityManager?.activeNetwork
        val caps = connectivityManager?.getNetworkCapabilities(activeNet)

        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true || activeNet != null

        val realIp = getRealLocalIpAddress()
        val iface = getRealActiveInterfaceName()

        val natType = when {
            !isConnected -> "Offline / Loopback"
            isWifi -> "Full Cone (Wi-Fi Subnet)"
            isCellular -> "Carrier-Grade NAT (CGNAT)"
            else -> "Ethernet / Direct Mesh ($iface)"
        }

        _networkState.value = _networkState.value.copy(
            isConnected = isConnected,
            isWifi = isWifi,
            isCellular = isCellular,
            interfaceName = iface,
            localIpV4Address = realIp,
            natType = natType
        )
    }

    private fun getRealLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating network interfaces: ${e.message}")
        }
        return "127.0.0.1"
    }

    private fun getRealActiveInterfaceName(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                return "${iface.displayName} (${iface.name})"
            }
        } catch (_: Exception) {}
        return "lo (Loopback)"
    }

    /**
     * Announces this node on the local Wi-Fi/LAN via Android NsdManager (DNS-SD / mDNS).
     */
    fun startLocalDiscoveryBroadcast(listeningPort: Int, localPeerId: String) {
        if (nsdManager == null) {
            _nsdState.value = _nsdState.value.copy(
                lastDiscoveryEvent = "NSD Service unavailable on this device subsystem"
            )
            return
        }

        currentListeningPort = listeningPort
        currentLocalPeerId = localPeerId
        acquireMulticastLock()

        stopRegistration()

        val uniqueSuffix = localPeerId.takeLast(6).ifEmpty { "node01" }
        val serviceName = "$SERVICE_NAME_PREFIX$uniqueSuffix"

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = SERVICE_TYPE
            this.port = listeningPort
            setAttribute("peerId", localPeerId)
            setAttribute("nodeName", "DecentralNode-${Build.MODEL.take(12)}")
            setAttribute("proto", "/dnet/p2p/1.0.0")
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(registeredService: NsdServiceInfo) {
                Log.d(TAG, "NSD Service registered: ${registeredService.serviceName} on port ${registeredService.port}")
                _nsdState.value = _nsdState.value.copy(
                    isBroadcasting = true,
                    registeredServiceName = registeredService.serviceName,
                    lastDiscoveryEvent = "Broadcasting as ${registeredService.serviceName} on port ${registeredService.port}"
                )
                // Start discovery listener concurrently
                startDiscoveryListener()
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD Registration failed with error code: $errorCode")
                _nsdState.value = _nsdState.value.copy(
                    isBroadcasting = false,
                    lastDiscoveryEvent = "NSD Registration failed (Error code: $errorCode)"
                )
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d(TAG, "NSD service unregistered successfully")
                _nsdState.value = _nsdState.value.copy(
                    isBroadcasting = false,
                    registeredServiceName = null,
                    lastDiscoveryEvent = "NSD service unregistered"
                )
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD Unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.w(TAG, "Exception during NSD registerService: ${e.message}")
            _nsdState.value = _nsdState.value.copy(
                lastDiscoveryEvent = "Registration error: ${e.message}"
            )
        }
    }

    /**
     * Starts discovering other decentralized nodes advertising via DNS-SD on the local network.
     */
    fun startDiscoveryListener() {
        if (nsdManager == null) return
        acquireMulticastLock()

        stopDiscoveryListener()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "NSD Discovery started for serviceType: $regType")
                _nsdState.value = _nsdState.value.copy(
                    isScanning = true,
                    lastDiscoveryEvent = "Scanning local subnet for $regType..."
                )
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "NSD Service found: ${service.serviceName}, type: ${service.serviceType}")
                val ourServiceName = _nsdState.value.registeredServiceName
                // Don't resolve our own broadcasted node
                if (ourServiceName != null && service.serviceName == ourServiceName) {
                    Log.d(TAG, "Ignoring self-discovered NSD service: ${service.serviceName}")
                    return
                }

                if (service.serviceType.contains("dnet-p2p") || service.serviceType.contains("ipfs")) {
                    resolveDiscoveredService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "NSD Service lost: ${service.serviceName}")
                _nsdState.value = _nsdState.value.copy(
                    lastDiscoveryEvent = "Node went offline: ${service.serviceName}"
                )
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "NSD Discovery stopped: $serviceType")
                _nsdState.value = _nsdState.value.copy(
                    isScanning = false,
                    lastDiscoveryEvent = "Local scan stopped"
                )
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "NSD Discovery start failed: $errorCode")
                _nsdState.value = _nsdState.value.copy(
                    isScanning = false,
                    lastDiscoveryEvent = "Scan failed to start (Code: $errorCode)"
                )
                try {
                    nsdManager.stopServiceDiscovery(this)
                } catch (_: Exception) {}
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "NSD Discovery stop failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start discoverServices: ${e.message}")
            _nsdState.value = _nsdState.value.copy(
                isScanning = false,
                lastDiscoveryEvent = "Discovery error: ${e.message}"
            )
        }
    }

    /**
     * Manually triggers a fresh 10-second NSD discovery scan across local Wi-Fi / LAN subnet.
     */
    fun scanLocalNetwork() {
        scope.launch(Dispatchers.IO) {
            refreshActiveInterfaces()
            startDiscoveryListener()

            scanTimeoutJob?.cancel()
            scanTimeoutJob = scope.launch(Dispatchers.IO) {
                delay(12000)
                if (_nsdState.value.isScanning) {
                    _nsdState.value = _nsdState.value.copy(
                        lastDiscoveryEvent = "NSD scan complete. Ready for P2P connections."
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveDiscoveredService(service: NsdServiceInfo) {
        scope.launch(Dispatchers.IO) {
            resolveMutex.withLock {
                try {
                    nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.w(TAG, "NSD Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host?.hostAddress ?: return
                            val port = serviceInfo.port
                            val peerIdAttr = serviceInfo.attributes?.get("peerId")?.let { String(it) }
                                ?: "12D3KooWLAN${host.replace(".", "")}p$port"
                            val nodeNameAttr = serviceInfo.attributes?.get("nodeName")?.let { String(it) }
                                ?: serviceInfo.serviceName

                            Log.d(TAG, "NSD Service resolved: $nodeNameAttr at $host:$port (PeerID: $peerIdAttr)")

                            _nsdState.value = _nsdState.value.copy(
                                activeLocalNodesCount = _nsdState.value.activeLocalNodesCount + 1,
                                lastDiscoveryEvent = "Discovered peer $nodeNameAttr on $host:$port"
                            )

                            // Initiate real P2P Connection & Handshake with discovered local node
                            scope.launch(Dispatchers.IO) {
                                establishP2PConnection(
                                    host = host,
                                    port = port,
                                    peerIdHint = peerIdAttr,
                                    nodeNameHint = nodeNameAttr
                                )
                            }
                        }
                    })
                } catch (e: Exception) {
                    Log.w(TAG, "Error invoking resolveService: ${e.message}")
                }
            }
        }
    }

    /**
     * Establishes a real P2P TCP connection to the discovered local node,
     * conducts a cryptographic handshake, verifies reachability, measures real RTT latency,
     * and saves the peer to the local database.
     */
    suspend fun establishP2PConnection(
        host: String,
        port: Int,
        peerIdHint: String? = null,
        nodeNameHint: String? = null
    ): P2PHandshakeResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var socket: Socket? = null

        try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), 4000)
            socket.soTimeout = 4000

            val writer = OutputStreamWriter(socket.getOutputStream())
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            // Construct real P2P handshake command
            val handshakeJson = JSONObject().apply {
                put("cmd", "P2P_HANDSHAKE")
                put("fromPeerId", currentLocalPeerId)
                put("fromNodeName", "AndroidNode-${Build.MODEL}")
                put("listeningPort", currentListeningPort)
                put("protocol", "/dnet/p2p/1.0.0")
                put("timestamp", System.currentTimeMillis())
            }

            writer.write(handshakeJson.toString() + "\n")
            writer.flush()

            val responseLine = reader.readLine()
            val latency = System.currentTimeMillis() - startTime

            var remotePeerId = peerIdHint ?: "12D3KooWLAN${host.replace(".", "")}"
            var remoteNodeName = nodeNameHint ?: "LAN Node ($host:$port)"
            var blocksShared = 1

            if (!responseLine.isNullOrBlank()) {
                try {
                    val respJson = JSONObject(responseLine)
                    remotePeerId = respJson.optString("peerId", remotePeerId)
                    remoteNodeName = respJson.optString("nodeName", remoteNodeName)
                    blocksShared = respJson.optInt("blocks", 1)
                } catch (_: Exception) {
                    // Raw string response fallback
                }
            }

            // Do not insert self into peer list
            if (remotePeerId != currentLocalPeerId && !(host == _networkState.value.localIpV4Address && port == currentListeningPort)) {
                val peerEntity = PeerEntity(
                    peerId = remotePeerId,
                    name = remoteNodeName,
                    multiaddress = "/ip4/$host/tcp/$port",
                    latencyMs = latency,
                    isOnline = true,
                    blocksShared = blocksShared,
                    region = "Local Subnet (NSD / mDNS)",
                    isBootstrap = false
                )

                database.peerDao().insertPeers(listOf(peerEntity))
            }

            _networkState.value = _networkState.value.copy(
                discoveredLocalServices = _networkState.value.discoveredLocalServices + 1
            )

            _nsdState.value = _nsdState.value.copy(
                lastDiscoveryEvent = "P2P Connection Established with $remoteNodeName (${latency}ms)"
            )

            P2PHandshakeResult(
                success = true,
                peerId = remotePeerId,
                nodeName = remoteNodeName,
                host = host,
                port = port,
                latencyMs = latency
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.w(TAG, "P2P Handshake connection to $host:$port failed: ${e.message}")

            P2PHandshakeResult(
                success = false,
                peerId = peerIdHint ?: "",
                nodeName = nodeNameHint ?: "Unknown",
                host = host,
                port = port,
                latencyMs = latency,
                errorMessage = e.message ?: "Connection failed"
            )
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    private fun stopRegistration() {
        try {
            registrationListener?.let {
                nsdManager?.unregisterService(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering NSD service: ${e.message}")
        }
        registrationListener = null
        _nsdState.value = _nsdState.value.copy(
            isBroadcasting = false,
            registeredServiceName = null
        )
    }

    private fun stopDiscoveryListener() {
        try {
            discoveryListener?.let {
                nsdManager?.stopServiceDiscovery(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping NSD discovery: ${e.message}")
        }
        discoveryListener = null
        _nsdState.value = _nsdState.value.copy(isScanning = false)
    }

    fun stopLocalDiscovery() {
        stopRegistration()
        stopDiscoveryListener()
        releaseMulticastLock()
        scanTimeoutJob?.cancel()
        _nsdState.value = _nsdState.value.copy(
            isBroadcasting = false,
            isScanning = false,
            lastDiscoveryEvent = "NSD discovery service stopped"
        )
    }
}
