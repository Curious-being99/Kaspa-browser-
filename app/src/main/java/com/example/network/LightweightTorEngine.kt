package com.example.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Local Loopback SOCKS5 Relay Forwarder.
 *
 * Provides a local RFC 1928 SOCKS5 listener on 127.0.0.1 (port 9155 or dynamic fallback)
 * that resolves domain targets through KaspaPrivacyRelayEngine's encrypted DoH resolver
 * before opening outbound sockets, avoiding plaintext system DNS lookups on relayed streams.
 *
 * Note: Running only in-process on loopback does not hide the device's egress IP from
 * destinations. External IP masking requires routing through an external SOCKS5/HTTP proxy
 * (such as Orbot/Tor) configured in Proxy Settings.
 */
object LightweightTorEngine {
    private const val TAG = "LightweightTorEngine"
    private const val DEFAULT_SOCKS_PORT = 9155
    private const val FALLBACK_SOCKS_PORT = 9156

    private val secureRandom = SecureRandom()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var serverSocket: ServerSocket? = null
    private var listenJob: Job? = null

    val totalBytesRelayed = AtomicLong(0L)

    data class OnionHop(
        val id: String,
        val role: String,
        val name: String,
        val countryCode: String,
        val countryName: String,
        val publicKeyHex: String,
        val latencyMs: Long
    )

    data class OnionCircuit(
        val circuitId: String,
        val guardHop: OnionHop,
        val middleHop: OnionHop,
        val exitHop: OnionHop,
        val createdAtEpochSec: Long = System.currentTimeMillis() / 1000L,
        val isEncrypted: Boolean = true,
        val streamIsolationDomain: String = "Local Loopback DoH"
    )

    private val _isTorRunning = MutableStateFlow(false)
    val isTorRunning: StateFlow<Boolean> = _isTorRunning.asStateFlow()

    private val _activeOnionCircuit = MutableStateFlow(generateNewCircuit())
    val activeOnionCircuit: StateFlow<OnionCircuit> = _activeOnionCircuit.asStateFlow()

    private val _activePort = MutableStateFlow(DEFAULT_SOCKS_PORT)
    val activePort: StateFlow<Int> = _activePort.asStateFlow()

    @Volatile
    var isEnabled: Boolean = false
        private set

    /**
     * Start the local loopback SOCKS5 listener. Does not override KaspaPrivacyRelayEngine's
     * upstream external proxy setting, ensuring proxy telemetry remains truthful.
     */
    @Synchronized
    fun start() {
        if (_isTorRunning.value) return
        isEnabled = true

        var boundPort = DEFAULT_SOCKS_PORT
        var server: ServerSocket? = null

        try {
            server = ServerSocket(DEFAULT_SOCKS_PORT, 50, InetAddress.getByName("127.0.0.1"))
            boundPort = DEFAULT_SOCKS_PORT
        } catch (_: Throwable) {
            try {
                server = ServerSocket(FALLBACK_SOCKS_PORT, 50, InetAddress.getByName("127.0.0.1"))
                boundPort = FALLBACK_SOCKS_PORT
            } catch (_: Throwable) {
                try {
                    server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
                    boundPort = server.localPort
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to bind local SOCKS5 socket: ${e.message}")
                    isEnabled = false
                    return
                }
            }
        }

        serverSocket = server
        _activePort.value = boundPort
        _isTorRunning.value = true
        Log.i(TAG, "Local DoH SOCKS5 listener bound on 127.0.0.1:$boundPort")

        listenJob = scope.launch {
            while (isActive && _isTorRunning.value) {
                try {
                    val clientSocket = server.accept()
                    launch { handleSocks5Connection(clientSocket) }
                } catch (_: Throwable) {
                    if (!isActive) break
                }
            }
        }
    }

    /**
     * Stop the local loopback SOCKS5 listener.
     */
    @Synchronized
    fun stop() {
        _isTorRunning.value = false
        isEnabled = false
        listenJob?.cancel()
        listenJob = null
        try {
            serverSocket?.close()
        } catch (_: Throwable) {}
        serverSocket = null
        Log.i(TAG, "Local DoH SOCKS5 listener stopped")
    }

    /**
     * Refreshes local session metadata. Note: External IP identity only changes when
     * an external upstream proxy (e.g., Orbot) rotates its circuit.
     */
    fun rotateCircuit(): OnionCircuit {
        val newCircuit = generateNewCircuit()
        _activeOnionCircuit.value = newCircuit
        KaspaPrivacyRelayEngine.createCircuit()
        return newCircuit
    }

    private fun generateNewCircuit(): OnionCircuit {
        val cid = "loc-sess-${CryptoUtils.bytesToHex(ByteArray(4).apply { secureRandom.nextBytes(this) })}"
        val entryPub = CryptoUtils.bytesToHex(KrpRelayDaemon.entryPublicKey)
        val exitPub = CryptoUtils.bytesToHex(KrpRelayDaemon.exitPublicKey)
        val hasUpstreamProxy = KaspaPrivacyRelayEngine.customProxyDescription.isNotBlank()

        val guard = OnionHop(
            id = "ingress-$cid",
            role = "Local Ingress",
            name = "Loopback SOCKS5 Socket (127.0.0.1)",
            countryCode = "LOC",
            countryName = "On-Device Loopback",
            publicKeyHex = entryPub,
            latencyMs = 1L
        )

        val middle = OnionHop(
            id = "doh-$cid",
            role = "DNS Resolver",
            name = "Fail-Closed Encrypted DoH",
            countryCode = "DoH",
            countryName = "Cloudflare / Quad9 / Google DoH",
            publicKeyHex = exitPub,
            latencyMs = 1L
        )

        val exit = OnionHop(
            id = "egress-$cid",
            role = "Egress Mode",
            name = if (hasUpstreamProxy) "Upstream ${KaspaPrivacyRelayEngine.customProxyDescription}" else "Direct Device Egress",
            countryCode = if (hasUpstreamProxy) "PRX" else "DEV",
            countryName = if (hasUpstreamProxy) KaspaPrivacyRelayEngine.customProxyDescription else "No External Tor/Proxy Configured",
            publicKeyHex = exitPub,
            latencyMs = 0L
        )

        return OnionCircuit(
            circuitId = cid,
            guardHop = guard,
            middleHop = middle,
            exitHop = exit,
            streamIsolationDomain = if (hasUpstreamProxy) "Upstream Proxy Active" else "Local Loopback DoH Only"
        )
    }

    /**
     * Handle incoming RFC 1928 SOCKS5 Handshake & Relay Stream
     */
    private suspend fun handleSocks5Connection(socket: Socket) {
        var targetSocket: Socket? = null
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 15000

            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // 1. Version & Method Negotiation
            val ver = input.read()
            if (ver != 0x05) {
                return
            }

            val nMethods = input.read()
            if (nMethods !in 1..255) {
                return
            }
            val methods = ByteArray(nMethods)
            input.readFullyStrict(methods)

            // Reply: Version 5, Method 0 (No Auth Required)
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            // 2. Request Handling
            val reqVer = input.read()
            val cmd = input.read()
            @Suppress("UNUSED_VARIABLE")
            val rsv = input.read()
            val atyp = input.read()

            if (reqVer != 0x05 || cmd != 0x01 || atyp < 0) { // 0x01 = CONNECT
                output.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0)) // Command not supported
                output.flush()
                return
            }

            val targetHost: String = when (atyp) {
                0x01 -> { // IPv4
                    val ipBytes = ByteArray(4)
                    input.readFullyStrict(ipBytes)
                    InetAddress.getByAddress(ipBytes).hostAddress ?: "127.0.0.1"
                }
                0x03 -> { // Domain name
                    val len = input.read()
                    if (len !in 1..255) return
                    val domainBytes = ByteArray(len)
                    input.readFullyStrict(domainBytes)
                    String(domainBytes, Charsets.UTF_8)
                }
                0x04 -> { // IPv6
                    val ipBytes = ByteArray(16)
                    input.readFullyStrict(ipBytes)
                    InetAddress.getByAddress(ipBytes).hostAddress ?: "::1"
                }
                else -> return
            }

            val portHigh = input.read()
            val portLow = input.read()
            if (portHigh < 0 || portLow < 0) return
            val targetPort = ((portHigh and 0xFF) shl 8) or (portLow and 0xFF)
            if (targetPort !in 1..65535) {
                output.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                output.flush()
                return
            }

            // Resolve domains via fail-closed encrypted DoH so SOCKS5 never falls back to plaintext system DNS
            val resolvedAddress: InetAddress = try {
                if (atyp == 0x03) {
                    val candidates = KaspaPrivacyRelayEngine.lookupEncryptedDns(targetHost)
                    candidates.firstOrNull() ?: throw java.io.IOException("No DoH records for $targetHost")
                } else {
                    InetAddress.getByName(targetHost)
                }
            } catch (_: Throwable) {
                output.write(byteArrayOf(0x05, 0x04, 0x00, 0x01, 0, 0, 0, 0, 0, 0)) // Host unreachable
                output.flush()
                return
            }

            // Guard against recursive self-looping into local proxy/daemon ports
            val localDaemonPorts = setOf(_activePort.value, KrpRelayDaemon.entryPort, KrpRelayDaemon.exitPort)
            if (resolvedAddress.isLoopbackAddress && targetPort in localDaemonPorts) {
                output.write(byteArrayOf(0x05, 0x02, 0x00, 0x01, 0, 0, 0, 0, 0, 0)) // Connection not allowed
                output.flush()
                return
            }

            // Connect to Target Egress via Fast Multiplexed Socket using resolved IP
            targetSocket = try {
                val s = Socket()
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(resolvedAddress, targetPort), 8000)
                s
            } catch (_: Throwable) {
                // Reply Connection Refused / Host Unreachable
                output.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                output.flush()
                return
            }

            // Reply SOCKS5 Success (0x00)
            val localAddr = targetSocket.localAddress.address
            val localPort = targetSocket.localPort
            output.write(byteArrayOf(0x05, 0x00, 0x00, if (localAddr.size == 16) 0x04 else 0x01))
            output.write(localAddr)
            output.write(byteArrayOf((localPort shr 8).toByte(), localPort.toByte()))
            output.flush()

            // 3. Bi-directional Stream Forwarding with Onion Byte Accounting
            val targetIn = targetSocket.getInputStream()
            val targetOut = targetSocket.getOutputStream()

            val forwardJob = scope.launch {
                pipeStreams(input, targetOut)
            }
            val backwardJob = scope.launch {
                pipeStreams(targetIn, output)
            }

            forwardJob.join()
            backwardJob.join()
        } catch (_: Throwable) {
        } finally {
            try { targetSocket?.close() } catch (_: Throwable) {}
            try { socket.close() } catch (_: Throwable) {}
        }
    }

    private fun pipeStreams(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                output.flush()
                totalBytesRelayed.addAndGet(read.toLong())
            }
        } catch (_: Throwable) {}
    }

    private fun InputStream.readFullyStrict(b: ByteArray) {
        var offset = 0
        while (offset < b.size) {
            val count = read(b, offset, b.size - offset)
            if (count < 0) throw java.io.EOFException("Unexpected EOF during SOCKS5 handshake")
            offset += count
        }
    }
}
