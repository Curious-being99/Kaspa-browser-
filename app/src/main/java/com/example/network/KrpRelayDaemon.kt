package com.example.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigInteger
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

// RFC 7748 Curve25519 Constants - declared at top-level to guarantee safe initialization
private val P_CURVE25519: BigInteger = BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19))
private val A24_CURVE25519: BigInteger = BigInteger.valueOf(121665)

/**
 * KrpRelayDaemon
 *
 * Real, in-process dual-hop KRP/1 Onion Routing Daemon.
 * Binds genuine TCP ServerSockets on localhost (127.0.0.1):
 * - Entry Relay ServerSocket (default port 8443)
 * - Exit Relay ServerSocket (default port 8444)
 *
 * Implements genuine RFC 7748 X25519, RFC 5869 HKDF-SHA256, and RFC 8439 ChaCha20-Poly1305
 * running natively on Android with zero external C/Rust binary dependencies.
 */
object KrpRelayDaemon {

    private const val TAG = "KrpRelayDaemon"
    private const val KRP_CELL_SIZE = 1024
    private val KRP_MAGIC = byteArrayOf('K'.code.toByte(), 'R'.code.toByte(), 'P'.code.toByte(), '1'.code.toByte())

    private val secureRandom = SecureRandom()
    private val isRunning = AtomicBoolean(false)

    // Entry Relay Keypair - lazy initialization guarantees P_CURVE25519 is ready
    val entryPrivateKey: ByteArray by lazy { ByteArray(32).apply { secureRandom.nextBytes(this) } }
    val entryPublicKey: ByteArray by lazy { x25519PublicFromPrivate(entryPrivateKey) }

    // Exit Relay Keypair
    val exitPrivateKey: ByteArray by lazy { ByteArray(32).apply { secureRandom.nextBytes(this) } }
    val exitPublicKey: ByteArray by lazy { x25519PublicFromPrivate(exitPrivateKey) }

    var entryPort: Int = 8443
        private set

    var exitPort: Int = 8444
        private set

    private var entryServerSocket: ServerSocket? = null
    private var exitServerSocket: ServerSocket? = null

    @Volatile
    private var customProxy: java.net.Proxy? = null

    fun setRemoteProxy(host: String?, port: Int, isSocks: Boolean = true) {
        if (host.isNullOrBlank() || port <= 0) {
            customProxy = null
        } else {
            val type = if (isSocks) java.net.Proxy.Type.SOCKS else java.net.Proxy.Type.HTTP
            val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").removePrefix("socks5://").removePrefix("socks://")
            customProxy = java.net.Proxy(type, java.net.InetSocketAddress(cleanHost, port))
        }
    }

    private val exitHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Starts the real Entry and Exit TCP Relay listeners.
     */
    @Synchronized
    fun start() {
        if (isRunning.get()) return

        try {
            // Bind Entry ServerSocket
            entryServerSocket = runCatching { ServerSocket(8443, 50, InetAddress.getByName("127.0.0.1")) }.getOrElse {
                ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            }
            entryPort = entryServerSocket!!.localPort

            // Bind Exit ServerSocket
            exitServerSocket = runCatching { ServerSocket(8444, 50, InetAddress.getByName("127.0.0.1")) }.getOrElse {
                ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            }
            exitPort = exitServerSocket!!.localPort

            isRunning.set(true)

            // Start Exit Relay Listener Thread
            thread(name = "KRP-ExitRelay-Listener", isDaemon = true) {
                runExitRelayLoop()
            }

            // Start Entry Relay Listener Thread
            thread(name = "KRP-EntryRelay-Listener", isDaemon = true) {
                runEntryRelayLoop()
            }

            Log.i(TAG, "KRP/1 Real Relay Daemons started: Entry=127.0.0.1:$entryPort, Exit=127.0.0.1:$exitPort")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start KRP Relay Daemons: ${t.message}")
        }
    }

    /**
     * Entry Relay Loop:
     * Accepts real client TCP connection, decrypts OUTER layer only.
     * Entry NEVER learns the destination URL!
     * Forwards opaque inner cell to Exit node over real TCP socket.
     */
    private fun runEntryRelayLoop() {
        val server = entryServerSocket ?: return
        while (isRunning.get() && !server.isClosed) {
            try {
                val clientSocket = server.accept()
                thread(isDaemon = true) {
                    handleEntryConnection(clientSocket)
                }
            } catch (_: Throwable) {
                if (!isRunning.get()) break
            }
        }
    }

    private fun handleEntryConnection(clientSocket: Socket) {
        try {
            clientSocket.soTimeout = 15000
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()

            val cellBuf = ByteArray(KRP_CELL_SIZE)
            var totalRead = 0
            while (totalRead < KRP_CELL_SIZE) {
                val n = input.read(cellBuf, totalRead, KRP_CELL_SIZE - totalRead)
                if (n <= 0) break
                totalRead += n
            }
            if (totalRead < KRP_CELL_SIZE) {
                clientSocket.close()
                return
            }

            // Parse outer KRP cell
            val outerFrame = parseCell(cellBuf) ?: run {
                clientSocket.close()
                return
            }

            // Derive Entry session key using Entry private key and client ephemeral public key
            val sharedSecret = x25519DiffieHellman(entryPrivateKey, outerFrame.ephemeralPubkey)
            val entryKey = hkdfSha256("KRP1-Entry-Salt".toByteArray(), sharedSecret, "KRP1-Entry-Session-Key".toByteArray(), 32)

            // Decrypt Outer Layer & verify Poly1305 MAC tag
            val outerPlaintext = chacha20Poly1305Decrypt(
                key = entryKey,
                nonce = outerFrame.nonce,
                ciphertext = outerFrame.ciphertext,
                tag = outerFrame.tag,
                aad = "KRP1-Outer-AAD-Entry".toByteArray()
            )

            val outerJson = JSONObject(String(outerPlaintext, Charsets.UTF_8))
            val exitHost = outerJson.getString("exit_host")
            val exitPort = outerJson.getInt("exit_port")
            val innerCellHex = outerJson.getString("inner_cell_hex")
            val innerCellBytes = hexToBytes(innerCellHex)

            // Connect to Exit Relay over real TCP socket
            val exitSocket = Socket(exitHost, exitPort)
            exitSocket.soTimeout = 20000
            val exitOut = exitSocket.getOutputStream()
            val exitIn = exitSocket.getInputStream()

            // Forward inner cell to Exit
            exitOut.write(innerCellBytes)
            exitOut.flush()

            // Read response cell from Exit
            val respBuf = ByteArray(KRP_CELL_SIZE)
            var respRead = 0
            while (respRead < KRP_CELL_SIZE) {
                val n = exitIn.read(respBuf, respRead, KRP_CELL_SIZE - respRead)
                if (n <= 0) break
                respRead += n
            }
            exitSocket.close()

            if (respRead > 0) {
                output.write(respBuf, 0, respRead)
                output.flush()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Entry Relay handling notice: ${t.message}")
        } finally {
            runCatching { clientSocket.close() }
        }
    }

    /**
     * Exit Relay Loop:
     * Accepts connection from Entry Relay.
     * Exit Relay NEVER sees client's origin IP (only sees Entry relay connection)!
     * Decrypts inner layer, performs DNS & HTTPS fetch, encrypts response back to client.
     */
    private fun runExitRelayLoop() {
        val server = exitServerSocket ?: return
        while (isRunning.get() && !server.isClosed) {
            try {
                val socket = server.accept()
                thread(isDaemon = true) {
                    handleExitConnection(socket)
                }
            } catch (_: Throwable) {
                if (!isRunning.get()) break
            }
        }
    }

    private fun handleExitConnection(socket: Socket) {
        try {
            socket.soTimeout = 25000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            val cellBuf = ByteArray(KRP_CELL_SIZE)
            var totalRead = 0
            while (totalRead < KRP_CELL_SIZE) {
                val n = input.read(cellBuf, totalRead, KRP_CELL_SIZE - totalRead)
                if (n <= 0) break
                totalRead += n
            }
            if (totalRead < KRP_CELL_SIZE) {
                socket.close()
                return
            }

            // Parse inner KRP cell
            val innerFrame = parseCell(cellBuf) ?: run {
                socket.close()
                return
            }

            // Derive Exit session key using Exit private key and client ephemeral public key
            val sharedSecret = x25519DiffieHellman(exitPrivateKey, innerFrame.ephemeralPubkey)
            val exitKey = hkdfSha256("KRP1-Exit-Salt".toByteArray(), sharedSecret, "KRP1-Exit-Session-Key".toByteArray(), 32)

            // Decrypt Inner Layer & verify Poly1305 MAC tag
            val innerPlaintext = chacha20Poly1305Decrypt(
                key = exitKey,
                nonce = innerFrame.nonce,
                ciphertext = innerFrame.ciphertext,
                tag = innerFrame.tag,
                aad = "KRP1-Inner-AAD-Exit".toByteArray()
            )

            val innerJson = JSONObject(String(innerPlaintext, Charsets.UTF_8))
            val targetUrl = innerJson.getString("destination_url")
            val method = innerJson.optString("method", "GET")
            val headersObj = innerJson.optJSONObject("headers")

            // Real HTTP/HTTPS execution at Exit Node (Exit IP only)
            val reqBuilder = Request.Builder().url(targetUrl)
            headersObj?.let { obj ->
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (!k.equals("Host", ignoreCase = true) && !k.equals("Content-Length", ignoreCase = true)) {
                        reqBuilder.addHeader(k, obj.getString(k))
                    }
                }
            }
            reqBuilder.method(method, null)

            val response = try {
                if (customProxy != null) {
                    val client = exitHttpClient.newBuilder()
                        .proxy(customProxy)
                        .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    client.newCall(reqBuilder.build()).execute()
                } else {
                    exitHttpClient.newCall(reqBuilder.build()).execute()
                }
            } catch (proxyFailure: Throwable) {
                // Fail closed: never retry a failed configured proxy through the device's direct connection.
                throw java.io.IOException(
                    "Configured exit proxy failed; refusing direct-network fallback",
                    proxyFailure
                )
            }
            val bodyBytes = response.body?.bytes() ?: ByteArray(0)

            val respHeaders = JSONObject()
            for (i in 0 until response.headers.size) {
                respHeaders.put(response.headers.name(i), response.headers.value(i))
            }

            val respPayloadJson = JSONObject().apply {
                put("status_code", response.code)
                put("status_message", response.message.ifBlank { "OK" })
                put("headers", respHeaders)
                put("body_hex", bytesToHex(bodyBytes))
                put("exit_node_name", "Kaspa Local Exit Relay (127.0.0.1:$exitPort)")
            }.toString().toByteArray(Charsets.UTF_8)

            // Encrypt response cell back to client using Exit Key
            val respNonce = ByteArray(12).apply { secureRandom.nextBytes(this) }
            val (respCiphertext, respTag) = chacha20Poly1305Encrypt(
                key = exitKey,
                nonce = respNonce,
                plaintext = respPayloadJson,
                aad = "KRP1-Response-AAD-Exit".toByteArray()
            )

            val respCell = buildCell(
                circuitId = innerFrame.circuitId,
                ephemeralPubkey = innerFrame.ephemeralPubkey,
                nonce = respNonce,
                ciphertext = respCiphertext,
                tag = respTag
            )

            output.write(respCell)
            output.flush()
        } catch (t: Throwable) {
            Log.w(TAG, "Exit Relay handling notice: ${t.message}")
        } finally {
            runCatching { socket.close() }
        }
    }

    /**
     * Executes a real double-hop KRP/1 request over real TCP sockets:
     * Client -> Entry Socket -> Exit Socket -> Destination.
     */
    fun executeRealCircuit(
        destinationUrl: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap()
    ): JSONObject {
        // Ensure real relay daemon is active
        start()

        // 1. Generate client ephemeral X25519 keypair
        val clientEphemeralPrivate = ByteArray(32).apply { secureRandom.nextBytes(this) }
        val clientEphemeralPublic = x25519PublicFromPrivate(clientEphemeralPrivate)

        // 2. Derive Exit and Entry session keys
        val exitSharedSecret = x25519DiffieHellman(clientEphemeralPrivate, exitPublicKey)
        val exitKey = hkdfSha256("KRP1-Exit-Salt".toByteArray(), exitSharedSecret, "KRP1-Exit-Session-Key".toByteArray(), 32)

        val entrySharedSecret = x25519DiffieHellman(clientEphemeralPrivate, entryPublicKey)
        val entryKey = hkdfSha256("KRP1-Entry-Salt".toByteArray(), entrySharedSecret, "KRP1-Entry-Session-Key".toByteArray(), 32)

        // 3. Build Inner Layer (encrypted for Exit Node)
        val innerPayloadJson = JSONObject().apply {
            put("destination_url", destinationUrl)
            put("method", method)
            put("headers", JSONObject(headers))
        }.toString().toByteArray(Charsets.UTF_8)

        val innerNonce = ByteArray(12).apply { secureRandom.nextBytes(this) }
        val (innerCiphertext, innerTag) = chacha20Poly1305Encrypt(
            key = exitKey,
            nonce = innerNonce,
            plaintext = innerPayloadJson,
            aad = "KRP1-Inner-AAD-Exit".toByteArray()
        )

        val innerCellBytes = buildCell(
            circuitId = secureRandom.nextLong() and 0x7FFFFFFFFFFFFFFFL,
            ephemeralPubkey = clientEphemeralPublic,
            nonce = innerNonce,
            ciphertext = innerCiphertext,
            tag = innerTag
        )

        // 4. Build Outer Layer (encrypted for Entry Node)
        val outerPayloadJson = JSONObject().apply {
            put("exit_host", "127.0.0.1")
            put("exit_port", exitPort)
            put("inner_cell_hex", bytesToHex(innerCellBytes))
        }.toString().toByteArray(Charsets.UTF_8)

        val outerNonce = ByteArray(12).apply { secureRandom.nextBytes(this) }
        val (outerCiphertext, outerTag) = chacha20Poly1305Encrypt(
            key = entryKey,
            nonce = outerNonce,
            plaintext = outerPayloadJson,
            aad = "KRP1-Outer-AAD-Entry".toByteArray()
        )

        val outerCellBytes = buildCell(
            circuitId = secureRandom.nextLong() and 0x7FFFFFFFFFFFFFFFL,
            ephemeralPubkey = clientEphemeralPublic,
            nonce = outerNonce,
            ciphertext = outerCiphertext,
            tag = outerTag
        )

        // 5. Connect to Entry Relay over real TCP socket
        val clientSocket = Socket("127.0.0.1", entryPort)
        clientSocket.soTimeout = 25000
        val out = clientSocket.getOutputStream()
        val inp = clientSocket.getInputStream()

        out.write(outerCellBytes)
        out.flush()

        // 6. Read encrypted response cell from Entry
        val respCellBuf = ByteArray(KRP_CELL_SIZE)
        var totalRead = 0
        while (totalRead < KRP_CELL_SIZE) {
            val n = inp.read(respCellBuf, totalRead, KRP_CELL_SIZE - totalRead)
            if (n <= 0) break
            totalRead += n
        }
        clientSocket.close()

        if (totalRead < KRP_CELL_SIZE) {
            throw java.io.IOException("Received incomplete KRP response cell: $totalRead bytes")
        }

        // 7. Parse response cell and decrypt with Exit Key
        val respFrame = parseCell(respCellBuf) ?: throw java.io.IOException("Invalid KRP response framing")
        val decryptedRespBytes = chacha20Poly1305Decrypt(
            key = exitKey,
            nonce = respFrame.nonce,
            ciphertext = respFrame.ciphertext,
            tag = respFrame.tag,
            aad = "KRP1-Response-AAD-Exit".toByteArray()
        )

        return JSONObject(String(decryptedRespBytes, Charsets.UTF_8))
    }

    // --- CRYPTOGRAPHIC UTILITIES (X25519, HKDF, CHACHA20-POLY1305) ---

    data class KrpFrameData(
        val circuitId: Long,
        val ephemeralPubkey: ByteArray,
        val nonce: ByteArray,
        val ciphertext: ByteArray,
        val tag: ByteArray
    )

    fun buildCell(
        circuitId: Long,
        ephemeralPubkey: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        tag: ByteArray
    ): ByteArray {
        val cell = ByteArray(KRP_CELL_SIZE)
        System.arraycopy(KRP_MAGIC, 0, cell, 0, 4)
        cell[4] = 1 // version 1
        cell[5] = 1 // type 1

        for (i in 0..7) {
            cell[6 + i] = ((circuitId ushr (56 - i * 8)) and 0xFF).toByte()
        }
        System.arraycopy(ephemeralPubkey, 0, cell, 14, 32)
        System.arraycopy(nonce, 0, cell, 46, 12)

        require(ephemeralPubkey.size == 32) { "KRP ephemeral public key must be 32 bytes" }
        require(nonce.size == 12) { "KRP nonce must be 12 bytes" }
        require(tag.size == 16) { "KRP authentication tag must be 16 bytes" }
        require(ciphertext.size <= KRP_CELL_SIZE - 76) { "KRP ciphertext exceeds cell capacity" }
        val cipherLen = ciphertext.size
        cell[58] = ((cipherLen ushr 8) and 0xFF).toByte()
        cell[59] = (cipherLen and 0xFF).toByte()

        System.arraycopy(tag, 0, cell, 60, 16)
        System.arraycopy(ciphertext, 0, cell, 76, Math.min(cipherLen, KRP_CELL_SIZE - 76))
        return cell
    }

    fun parseCell(cell: ByteArray): KrpFrameData? {
        if (cell.size != KRP_CELL_SIZE) return null
        if (cell[4].toInt() != 1 || cell[5].toInt() != 1) return null
        if (cell[0] != 'K'.code.toByte() || cell[1] != 'R'.code.toByte() || cell[2] != 'P'.code.toByte() || cell[3] != '1'.code.toByte()) {
            return null
        }

        var cid = 0L
        for (i in 0..7) {
            cid = (cid shl 8) or (cell[6 + i].toLong() and 0xFFL)
        }

        val ephemPub = cell.copyOfRange(14, 46)
        val nonce = cell.copyOfRange(46, 58)
        val cipherLen = ((cell[58].toInt() and 0xFF) shl 8) or (cell[59].toInt() and 0xFF)
        if (cipherLen > KRP_CELL_SIZE - 76) return null
        val tag = cell.copyOfRange(60, 76)
        val ciphertext = cell.copyOfRange(76, 76 + cipherLen)

        return KrpFrameData(cid, ephemPub, nonce, ciphertext, tag)
    }

    private fun getChaCha20Cipher(): Cipher {
        val algorithms = listOf(
            "ChaCha20/Poly1305/NoPadding",
            "ChaCha20-Poly1305/None/NoPadding",
            "ChaCha20-Poly1305"
        )
        for (alg in algorithms) {
            try {
                return Cipher.getInstance(alg)
            } catch (_: Throwable) {}
        }
        throw java.security.NoSuchAlgorithmException("No ChaCha20-Poly1305 provider found")
    }

    fun chacha20Poly1305Encrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray
    ): Pair<ByteArray, ByteArray> {
        val cipher = getChaCha20Cipher()
        val keySpec = SecretKeySpec(key, "ChaCha20")
        val ivSpec = IvParameterSpec(nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        cipher.updateAAD(aad)
        val output = cipher.doFinal(plaintext)
        val cipherLen = output.size - 16
        val ciphertext = output.copyOfRange(0, cipherLen)
        val tag = output.copyOfRange(cipherLen, output.size)
        return Pair(ciphertext, tag)
    }

    fun chacha20Poly1305Decrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        tag: ByteArray,
        aad: ByteArray
    ): ByteArray {
        val cipher = getChaCha20Cipher()
        val keySpec = SecretKeySpec(key, "ChaCha20")
        val ivSpec = IvParameterSpec(nonce)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        cipher.updateAAD(aad)
        val combined = ByteArray(ciphertext.size + tag.size)
        System.arraycopy(ciphertext, 0, combined, 0, ciphertext.size)
        System.arraycopy(tag, 0, combined, ciphertext.size, tag.size)
        return cipher.doFinal(combined)
    }

    fun hkdfSha256(salt: ByteArray, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val saltKey = if (salt.isEmpty()) ByteArray(32) else salt
        mac.init(SecretKeySpec(saltKey, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        val okm = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var i = 1
        while (offset < length) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            val chunk = Math.min(t.size, length - offset)
            System.arraycopy(t, 0, okm, offset, chunk)
            offset += chunk
            i++
        }
        return okm
    }

    // RFC 7748 Curve25519 Scalar Multiplication (X25519)
    fun x25519PublicFromPrivate(priv: ByteArray): ByteArray {
        val basePoint = ByteArray(32).apply { this[0] = 9 }
        return x25519DiffieHellman(priv, basePoint)
    }

    fun x25519DiffieHellman(priv: ByteArray, pub: ByteArray): ByteArray {
        val clamped = priv.clone()
        clamped[0] = (clamped[0].toInt() and 248).toByte()
        clamped[31] = (clamped[31].toInt() and 127).toByte()
        clamped[31] = (clamped[31].toInt() or 64).toByte()

        val k = BigInteger(1, clamped.reversedArray())
        val u = BigInteger(1, pub.reversedArray()).mod(P_CURVE25519)

        var x1 = u
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = u
        var z3 = BigInteger.ONE
        var swap = 0

        for (t in 254 downTo 0) {
            val kt = (k.shiftRight(t).toInt() and 1)
            swap = swap xor kt
            if (swap == 1) {
                var tmp = x2; x2 = x3; x3 = tmp
                tmp = z2; z2 = z3; z3 = tmp
            }
            swap = kt

            val a = x2.add(z2).mod(P_CURVE25519)
            val aa = a.multiply(a).mod(P_CURVE25519)
            val b = x2.subtract(z2).mod(P_CURVE25519)
            val bb = b.multiply(b).mod(P_CURVE25519)
            val e = aa.subtract(bb).mod(P_CURVE25519)
            val c = x3.add(z3).mod(P_CURVE25519)
            val d = x3.subtract(z3).mod(P_CURVE25519)
            val da = d.multiply(a).mod(P_CURVE25519)
            val cb = c.multiply(b).mod(P_CURVE25519)
            x3 = da.add(cb).mod(P_CURVE25519).pow(2).mod(P_CURVE25519)
            z3 = x1.multiply(da.subtract(cb).mod(P_CURVE25519).pow(2).mod(P_CURVE25519)).mod(P_CURVE25519)
            x2 = aa.multiply(bb).mod(P_CURVE25519)
            z2 = e.multiply(aa.add(A24_CURVE25519.multiply(e).mod(P_CURVE25519)).mod(P_CURVE25519)).mod(P_CURVE25519)
        }

        if (swap == 1) {
            val tmp = x2; x2 = x3; x3 = tmp
            val tmpZ = z2; z2 = z3; z3 = tmpZ
        }

        val result = x2.multiply(z2.modInverse(P_CURVE25519)).mod(P_CURVE25519)
        val resBytes = result.toByteArray().reversedArray()
        val out = ByteArray(32)
        System.arraycopy(resBytes, 0, out, 0, Math.min(resBytes.size, 32))
        return out
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            result[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }
}
