package com.example.dapp

import java.net.URI
import java.util.Locale

enum class DappWalletMethod {
    CONNECT,
    GET_ACCOUNTS,
    SIGN_TRANSACTION,
    SUBMIT_TRANSACTION,
    SWITCH_NETWORK,
    UNKNOWN
}

enum class DappPolicyDecision {
    DENY,
    PROMPT_FOR_CONNECTION,
    ALLOW_READ_ONLY,
    REQUIRE_USER_APPROVAL
}

data class DappWalletRequest(
    val pageUrl: String,
    val frameUrl: String,
    val method: DappWalletMethod,
    val connectionGranted: Boolean,
    val activeNetwork: String,
    val requestedNetwork: String? = null,
    val hasTransactionPayload: Boolean = false
)

object DappRequestPolicy {
    fun evaluate(request: DappWalletRequest): DappPolicyDecision {
        val pageOrigin = secureOrigin(request.pageUrl) ?: return DappPolicyDecision.DENY
        val frameOrigin = secureOrigin(request.frameUrl) ?: return DappPolicyDecision.DENY
        if (pageOrigin != frameOrigin) return DappPolicyDecision.DENY

        if (request.method == DappWalletMethod.UNKNOWN) return DappPolicyDecision.DENY

        if (!request.connectionGranted) {
            return when (request.method) {
                DappWalletMethod.CONNECT,
                DappWalletMethod.GET_ACCOUNTS,
                DappWalletMethod.SIGN_TRANSACTION,
                DappWalletMethod.SUBMIT_TRANSACTION,
                DappWalletMethod.SWITCH_NETWORK -> DappPolicyDecision.PROMPT_FOR_CONNECTION
                DappWalletMethod.UNKNOWN -> DappPolicyDecision.DENY
            }
        }

        return when (request.method) {
            DappWalletMethod.CONNECT,
            DappWalletMethod.GET_ACCOUNTS -> DappPolicyDecision.ALLOW_READ_ONLY

            DappWalletMethod.SIGN_TRANSACTION,
            DappWalletMethod.SUBMIT_TRANSACTION -> {
                if (!request.hasTransactionPayload) {
                    DappPolicyDecision.DENY
                } else if (request.requestedNetwork != null &&
                    request.requestedNetwork != request.activeNetwork
                ) {
                    DappPolicyDecision.DENY
                } else {
                    DappPolicyDecision.REQUIRE_USER_APPROVAL
                }
            }

            DappWalletMethod.SWITCH_NETWORK -> {
                val target = request.requestedNetwork
                if (target.isNullOrBlank()) {
                    DappPolicyDecision.DENY
                } else if (target == request.activeNetwork) {
                    DappPolicyDecision.ALLOW_READ_ONLY
                } else {
                    DappPolicyDecision.REQUIRE_USER_APPROVAL
                }
            }

            DappWalletMethod.UNKNOWN -> DappPolicyDecision.DENY
        }
    }

    private fun secureOrigin(url: String): String? {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return null
        }

        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.rawUserInfo != null) return null

        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port
        if (port == 0 || port > 65535 || port < -1) return null

        val normalizedHost = host.lowercase(Locale.ROOT)
        return if (port == -1 || port == 443) {
            "https://$normalizedHost"
        } else {
            "https://$normalizedHost:$port"
        }
    }
}
