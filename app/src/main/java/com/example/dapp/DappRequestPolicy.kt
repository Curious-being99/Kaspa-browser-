package com.example.dapp

import java.net.URI
import java.util.Locale

/**
 * Validates the web origin and request method before any native wallet UI is opened.
 * This is policy scaffolding only; it does not sign or broadcast transactions.
 */
object DappRequestPolicy {
    private val readMethods = setOf("kaspa_getNetwork", "kaspa_getAccounts")
    private val permissionMethods = setOf("kaspa_connect", "kaspa_disconnect")
    private val approvalMethods = setOf(
        "kaspa_signMessage",
        "kaspa_signTransaction",
        "kaspa_sendTransaction"
    )

    enum class RequestClass { READ_ONLY, PERMISSION, USER_APPROVAL }

    data class ValidatedRequest(
        val origin: String,
        val method: String,
        val requestClass: RequestClass
    )

    fun validate(webUrl: String, method: String): Result<ValidatedRequest> = runCatching {
        val origin = normalizeSecureOrigin(webUrl)
            ?: throw IllegalArgumentException("Wallet requests require a secure HTTPS origin")
        val normalizedMethod = method.trim()
        val requestClass = when (normalizedMethod) {
            in readMethods -> RequestClass.READ_ONLY
            in permissionMethods -> RequestClass.PERMISSION
            in approvalMethods -> RequestClass.USER_APPROVAL
            else -> throw IllegalArgumentException("Unsupported dApp wallet method")
        }
        ValidatedRequest(origin, normalizedMethod, requestClass)
    }

    fun normalizeSecureOrigin(webUrl: String): String? {
        val uri = try {
            URI(webUrl)
        } catch (_: Exception) {
            return null
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.rawUserInfo != null || uri.host.isNullOrBlank()) return null
        if (uri.port != -1 && uri.port !in 1..65535) return null
        if (uri.host.any { it.isWhitespace() }) return null

        val host = uri.host.lowercase(Locale.ROOT).removeSuffix(".")
        val port = when (uri.port) {
            -1, 443 -> ""
            else -> ":\u0024{uri.port}"
        }
        return "https://\u0024host\u0024port"
    }
}
