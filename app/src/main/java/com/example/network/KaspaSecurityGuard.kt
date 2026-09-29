package com.example.network

import org.json.JSONObject

/**
 * On-device Security Guard powered by Rust.
 * Evaluates domain entropy, homograph phishing attacks (e.g. Cyrillic lookalikes),
 * typosquatting deceptive domains, and browser cryptojacking scripts locally with zero latency.
 */
object KaspaSecurityGuard {

    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("kaspasearch")
            isNativeLoaded = true
        } catch (_: Throwable) {
            isNativeLoaded = false
        }
    }

    @JvmStatic
    private external fun nativeInspectUrl(url: String): String

    data class SecurityVerdict(
        val isSafe: Boolean,
        val threatType: String? = null,
        val riskScore: Int = 0,
        val matchedTarget: String? = null,
        val reason: String
    ) {
        companion object {
            fun safe(reason: String = "URL is safe") = SecurityVerdict(
                isSafe = true,
                threatType = null,
                riskScore = 0,
                matchedTarget = null,
                reason = reason
            )

            fun threat(threatType: String, riskScore: Int, target: String?, reason: String) = SecurityVerdict(
                isSafe = false,
                threatType = threatType,
                riskScore = riskScore,
                matchedTarget = target,
                reason = reason
            )
        }
    }

    private val CRYPTOJACKING_DOMAINS = setOf(
        "coinhive.com", "coin-hive.com", "crypto-loot.com", "cryptoloot.pro",
        "jsecoin.com", "webminepool.com", "minr.pw", "monerominer.rocks",
        "cloudcoins.co", "miner.pr0gramm.com", "coin-have.com"
    )

    private val TYPOSQUATS = mapOf(
        "goolge.com" to "google.com",
        "gooogle.com" to "google.com",
        "goggle.com" to "google.com",
        "accounts-google.com" to "accounts.google.com",
        "google-security-login.com" to "accounts.google.com",
        "youutube.com" to "youtube.com",
        "youtubee.com" to "youtube.com",
        "kasspa.org" to "kaspa.org",
        "kasppa.org" to "kaspa.org",
        "binance-verify.com" to "binance.com",
        "coinbase-support.com" to "coinbase.com",
        "paypal-security.com" to "paypal.com",
        "metamask-login.com" to "metamask.io"
    )

    private val PROTECTED_TARGETS = listOf(
        "google.com", "accounts.google.com", "youtube.com", "kaspa.org",
        "apple.com", "amazon.com", "microsoft.com", "paypal.com",
        "binance.com", "coinbase.com", "github.com", "wikipedia.org", "cloudflare.com"
    )

    /**
     * Inspects a target URL for phishing homographs, typosquatting, cryptojackers, and malicious entropy.
     */
    fun inspectUrl(url: String): SecurityVerdict {
        if (url.isBlank()) return SecurityVerdict.safe("Empty URL")

        if (isNativeLoaded) {
            try {
                val jsonStr = nativeInspectUrl(url)
                if (jsonStr.isNotBlank() && jsonStr != "{}") {
                    val obj = JSONObject(jsonStr)
                    return SecurityVerdict(
                        isSafe = obj.optBoolean("is_safe", true),
                        threatType = if (obj.has("threat_type") && !obj.isNull("threat_type")) obj.optString("threat_type") else null,
                        riskScore = obj.optInt("risk_score", 0),
                        matchedTarget = if (obj.has("matched_target") && !obj.isNull("matched_target")) obj.optString("matched_target") else null,
                        reason = obj.optString("reason", "Evaluated by Rust Security Engine")
                    )
                }
            } catch (_: Throwable) {}
        }

        // Pure Kotlin Fallback
        return fallbackInspectUrl(url)
    }

    private fun fallbackInspectUrl(url: String): SecurityVerdict {
        val lower = url.lowercase().trim()

        if (lower.startsWith("data:") || lower.startsWith("blob:") || lower.startsWith("about:")) {
            return SecurityVerdict.safe("Internal pseudo-scheme")
        }

        if (lower.startsWith("kaspa:") || lower.startsWith("dnet:") || lower.startsWith("ipfs:") ||
            lower.contains("127.0.0.1") || lower.contains("localhost")) {
            return SecurityVerdict.safe("Decentralized or local network")
        }

        val host = extractHost(url) ?: return SecurityVerdict.safe("No host")

        // Exemption check: Google Accounts, YouTube, Kaspa
        if (isExempt(host)) {
            return SecurityVerdict.safe("Essential infrastructure or verified domain")
        }

        // Cryptojacking check
        for (cj in CRYPTOJACKING_DOMAINS) {
            if (host == cj || host.endsWith(".$cj")) {
                return SecurityVerdict.threat(
                    "MALICIOUS_CRYPTOJACKING",
                    95,
                    cj,
                    "Known browser cryptojacking or unauthorized background miner"
                )
            }
        }

        // Direct Typosquat check
        for ((typo, target) in TYPOSQUATS) {
            if (host == typo || host.endsWith(".$typo")) {
                return SecurityVerdict.threat(
                    "TYPOSQUATTING",
                    90,
                    target,
                    "Typosquatting deceptive domain mimicking $target"
                )
            }
        }

        // Homograph visual spoofing check
        val skeleton = StringBuilder()
        var hasConfusables = false
        for (ch in host) {
            when (ch) {
                '\u0430' -> { hasConfusables = true; skeleton.append('a') } // Cyrillic 'a'
                '\u0441' -> { hasConfusables = true; skeleton.append('c') } // Cyrillic 'c'
                '\u0435' -> { hasConfusables = true; skeleton.append('e') } // Cyrillic 'e'
                '\u0456' -> { hasConfusables = true; skeleton.append('i') } // Cyrillic 'i'
                '\u043E' -> { hasConfusables = true; skeleton.append('o') } // Cyrillic 'o'
                '\u0440' -> { hasConfusables = true; skeleton.append('p') } // Cyrillic 'p'
                '\u0455' -> { hasConfusables = true; skeleton.append('s') } // Cyrillic 's'
                '\u0443' -> { hasConfusables = true; skeleton.append('y') } // Cyrillic 'y'
                '\u0445' -> { hasConfusables = true; skeleton.append('x') } // Cyrillic 'x'
                '\u03BF' -> { hasConfusables = true; skeleton.append('o') } // Greek 'o'
                else -> skeleton.append(ch)
            }
        }

        val skeletonStr = skeleton.toString().lowercase()
        if (hasConfusables || host.contains("xn--")) {
            for (target in PROTECTED_TARGETS) {
                if (skeletonStr == target || skeletonStr.endsWith(".$target")) {
                    return SecurityVerdict.threat(
                        "PHISHING_HOMOGRAPH",
                        98,
                        target,
                        "Homograph visual spoofing attack targeting $target"
                    )
                }
            }
        }

        return SecurityVerdict.safe("Verified safe")
    }

    private fun isExempt(host: String): Boolean {
        val h = host.lowercase()
        return h == "accounts.google.com" || h == "myaccount.google.com" ||
                h == "google.com" || h.endsWith(".google.com") ||
                h == "youtube.com" || h == "m.youtube.com" || h.endsWith(".youtube.com") ||
                h == "googlevideo.com" || h.endsWith(".googlevideo.com") ||
                h.endsWith(".ytimg.com") || h.endsWith(".googleapis.com") ||
                h.endsWith(".gstatic.com") || h == "kaspa.org" || h.endsWith(".kaspa.org") ||
                h == "kas.pa" || h == "wikipedia.org" || h.endsWith(".wikipedia.org") ||
                h == "cloudflare.com" || h.endsWith(".cloudflare.com") ||
                h == "github.com" || h.endsWith(".github.com")
    }

    private fun extractHost(url: String): String? {
        val schemeEnd = url.indexOf("://")
        val start = if (schemeEnd != -1) schemeEnd + 3 else 0
        if (start >= url.length) return null

        var end = url.indexOf('/', start)
        if (end == -1) end = url.indexOf('?', start)
        if (end == -1) end = url.indexOf('#', start)
        if (end == -1) end = url.length

        val colonInHost = url.indexOf(':', start)
        if (colonInHost != -1 && colonInHost < end) {
            end = colonInHost
        }

        if (start >= end) return null
        return url.substring(start, end).lowercase()
    }
}
