package com.example.security

import android.util.Base64
import androidx.fragment.app.FragmentActivity
import com.example.data.AppDatabase
import com.example.data.WebAuthnCredentialEntity
import java.net.IDN
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import org.json.JSONObject
import org.json.JSONArray

class WebAuthnAuthenticator(
    private val activity: FragmentActivity,
    private val database: AppDatabase
) {
    private val dao = database.webAuthnCredentialDao()

    suspend fun create(origin: String, request: JSONObject): JSONObject {
        val rp = request.getJSONObject("rp")
        val rpId = canonicalHost(rp.optString("id").ifBlank { hostOf(origin) })
        validateRp(origin, rpId)
        val user = request.getJSONObject("user")
        val challenge = decode(request.getString("challenge"))
        require(challenge.isNotEmpty()) { "WebAuthn challenge is empty" }
        val userHandleBytes = decode(user.getString("id"))
        require(userHandleBytes.size in 1..64) { "Invalid WebAuthn user handle" }
        val userHandle = encode(userHandleBytes)
        val existing = dao.forRp(rpId)
        val excluded = request.optJSONArray("excludeCredentials")
        if (excluded != null) {
            for (i in 0 until excluded.length()) {
                val id = excluded.optJSONObject(i)?.optString("id").orEmpty()
                if (existing.any { it.credentialId == id }) {
                    throw IllegalStateException("Credential already registered")
                }
            }
        }
        val id = UUID.randomUUID().toString()
        val credentialId = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val alias = "kaspa-webauthn-${UUID.randomUUID()}"
        val publicKeyDer = WebAuthnKeystore.createCredentialKey(alias)
        val publicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(publicKeyDer)) as ECPublicKey
        val cose = coseKey(publicKey)
        val credId = encode(credentialId)
        val entity = WebAuthnCredentialEntity(
            id = id,
            rpId = rpId,
            origin = origin,
            userHandle = userHandle,
            userName = user.optString("name"),
            displayName = user.optString("displayName"),
            credentialId = credId,
            publicKey = encode(cose),
            keyAlias = alias
        )
        try {
            val clientData = clientData("webauthn.create", challenge, origin)
            val authData = authenticatorData(
                rpId, flags = 0x45, signCount = 0,
                attestedCredential = credentialId + cose
            )
            val attestation = Cbor.map(
                "fmt" to Cbor.text("none"),
                "attStmt" to Cbor.map(),
                "authData" to Cbor.bytes(authData)
            )
            dao.insert(entity)
            return JSONObject()
                .put("id", credId)
                .put("rawId", credId)
                .put("type", "public-key")
                .put("authenticatorAttachment", "platform")
                .put("clientExtensionResults", JSONObject())
                .put("response", JSONObject()
                    .put("clientDataJSON", encode(clientData))
                    .put("attestationObject", encode(attestation))
                    .put("transports", JSONArray().put("internal")))
        } catch (error: Throwable) {
            WebAuthnKeystore.deleteCredentialKey(alias)
            throw error
        }
    }

    suspend fun get(origin: String, request: JSONObject): JSONObject {
        val rpId = canonicalHost(request.optString("rpId").ifBlank { hostOf(origin) })
        validateRp(origin, rpId)
        val challenge = decode(request.getString("challenge"))
        require(challenge.isNotEmpty()) { "WebAuthn challenge is empty" }
        val allow = request.optJSONArray("allowCredentials")
        val candidates = dao.forRp(rpId).filter { credential ->
            allow == null || allow.length() == 0 || (0 until allow.length()).any { i ->
                allow.optJSONObject(i)?.optString("id") == credential.credentialId
            }
        }
        val credential = candidates.singleOrNull()
            ?: throw IllegalStateException(if (candidates.isEmpty()) "No matching passkey is stored for this website" else "More than one passkey matches; account selection is required")
        val clientData = clientData("webauthn.get", challenge, origin)
        val authData = authenticatorData(rpId, flags = 0x05, signCount = credential.signCount + 1)
        val signaturePayload = authData + MessageDigest.getInstance("SHA-256").digest(clientData)
        val signature = WebAuthnKeystore.sign(
            activity, credential.keyAlias, signaturePayload,
            "Verify passkey", "Sign in to $rpId"
        )
        dao.updateUse(credential.id, credential.signCount + 1, System.currentTimeMillis())
        return JSONObject()
            .put("id", credential.credentialId)
            .put("rawId", credential.credentialId)
            .put("type", "public-key")
            .put("authenticatorAttachment", "platform")
            .put("clientExtensionResults", JSONObject())
            .put("response", JSONObject()
                .put("clientDataJSON", encode(clientData))
                .put("authenticatorData", encode(authData))
                .put("signature", encode(signature))
                .put("userHandle", credential.userHandle))
    }

    private fun clientData(type: String, challenge: ByteArray, origin: String): ByteArray =
        JSONObject()
            .put("type", type)
            .put("challenge", encode(challenge))
            .put("origin", origin)
            .put("crossOrigin", false)
            .toString().toByteArray(Charsets.UTF_8)

    private fun authenticatorData(
        rpId: String,
        flags: Int,
        signCount: Long,
        attestedCredential: ByteArray? = null
    ): ByteArray {
        val rpHash = MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray(Charsets.UTF_8))
        val buffer = java.io.ByteArrayOutputStream()
        buffer.write(rpHash)
        buffer.write(flags)
        buffer.write(byteArrayOf(
            (signCount ushr 24).toByte(), (signCount ushr 16).toByte(),
            (signCount ushr 8).toByte(), signCount.toByte()
        ))
        if (attestedCredential != null) {
            buffer.write(ByteArray(16))
            buffer.write(byteArrayOf(
                (attestedCredential.size ushr 8).toByte(),
                attestedCredential.size.toByte()
            ))
            buffer.write(attestedCredential)
        }
        return buffer.toByteArray()
    }

    private fun coseKey(key: ECPublicKey): ByteArray {
        val x = unsigned32(key.w.affineX.toByteArray())
        val y = unsigned32(key.w.affineY.toByteArray())
        return Cbor.map(
            1L to Cbor.integer(2),
            3L to Cbor.integer(-7),
            -1L to Cbor.integer(1),
            -2L to Cbor.bytes(x),
            -3L to Cbor.bytes(y)
        )
    }

    private fun unsigned32(value: ByteArray): ByteArray {
        val trimmed = if (value.size > 32 && value[0] == 0.toByte()) value.copyOfRange(1, value.size) else value
        require(trimmed.size <= 32)
        return ByteArray(32 - trimmed.size) + trimmed
    }

    private fun validateRp(origin: String, rpId: String) {
        val host = canonicalHost(hostOf(origin))
        require(host == rpId || host.endsWith(".$rpId")) { "RP ID is not a registrable suffix of the calling origin" }
        require(!rpId.contains(':') && !rpId.matches(Regex("[0-9.]+"))) { "IP address RP IDs are not supported" }
        require(!setOf("com", "net", "org", "edu", "gov", "co.uk", "com.au", "co.jp").contains(rpId)) {
            "RP ID cannot be a public suffix"
        }
    }

    private fun hostOf(origin: String): String {
        val uri = java.net.URI(origin)
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.port in listOf(-1, 443)) {
            "WebAuthn requires a secure HTTPS origin"
        }
        return uri.host
    }

    private fun canonicalHost(value: String): String =
        IDN.toASCII(value.trim().trimEnd('.'), IDN.USE_STD3_ASCII_RULES).lowercase()

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun encode(value: ByteArray): String =
        Base64.encodeToString(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private object Cbor {
        fun integer(value: Long): ByteArray = head(if (value >= 0) 0 else 1, if (value >= 0) value else -1 - value)
        fun text(value: String): ByteArray = head(3, value.toByteArray(Charsets.UTF_8).size.toLong()) + value.toByteArray(Charsets.UTF_8)
        fun bytes(value: ByteArray): ByteArray = head(2, value.size.toLong()) + value
        fun map(vararg entries: Pair<Any, ByteArray>): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            out.write(head(5, entries.size.toLong()))
            entries.forEach { (key, value) ->
                out.write(when (key) {
                    is Long -> integer(key)
                    is String -> text(key)
                    else -> error("Unsupported CBOR key")
                })
                out.write(value)
            }
            return out.toByteArray()
        }
        private fun head(major: Int, value: Long): ByteArray {
            require(value >= 0)
            val prefix = major shl 5
            return when {
                value < 24 -> byteArrayOf((prefix or value.toInt()).toByte())
                value <= 0xff -> byteArrayOf((prefix or 24).toByte(), value.toByte())
                value <= 0xffff -> byteArrayOf((prefix or 25).toByte(), (value ushr 8).toByte(), value.toByte())
                value <= 0xffffffffL -> byteArrayOf((prefix or 26).toByte(), (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())
                else -> byteArrayOf((prefix or 27).toByte()) + ByteArray(8) { i -> (value ushr (56 - 8 * i)).toByte() }
            }
        }
    }
}
