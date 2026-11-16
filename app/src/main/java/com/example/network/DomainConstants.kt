package com.example.network

object DomainConstants {
    /** Primary web domain extension for decentralized nodes & handles (.k) */
    const val PRIMARY_DOMAIN_SUFFIX = ".k"

    /** Legacy domain extensions supported for backwards compatibility */
    const val KASBROWSER_DOMAIN_SUFFIX = ".kasbrowser"
    const val KAS_DOMAIN_SUFFIX = ".kas"

    val ALL_DOMAIN_SUFFIXES = listOf(
        PRIMARY_DOMAIN_SUFFIX,
        KASBROWSER_DOMAIN_SUFFIX,
        KAS_DOMAIN_SUFFIX
    )

    /**
     * Checks if a domain/URL string ends with any supported decentralized web extension (.k, .kasbrowser, .kas).
     */
    fun isCustomDomain(url: String): Boolean {
        val clean = url.trim().lowercase()
        return ALL_DOMAIN_SUFFIXES.any { clean.endsWith(it) }
    }

    /**
     * Removes all supported domain suffixes from a handle or slug.
     */
    fun removeDomainSuffix(domainOrSlug: String): String {
        var clean = domainOrSlug.trim()
        ALL_DOMAIN_SUFFIXES.forEach { suffix ->
            if (clean.endsWith(suffix, ignoreCase = true)) {
                clean = clean.substring(0, clean.length - suffix.length)
            }
        }
        return clean
    }

    /**
     * Formats a clean handle or slug into the standard .k domain name.
     */
    fun formatDomain(handleOrSlug: String): String {
        val clean = removeDomainSuffix(handleOrSlug.trim())
        return "$clean$PRIMARY_DOMAIN_SUFFIX"
    }

    private val KASPA_SCHEME_TYPO_REGEX = Regex(
        "^kaspa\\s*(?:[.;:]\\s*[/\\\\]+|:[/\\\\]{1,2})\\s*(.*)$",
        RegexOption.IGNORE_CASE
    )
    private val KAS_SCHEME_TYPO_REGEX = Regex(
        "^kas\\s*(?:[.;:]\\s*[/\\\\]+|:[/\\\\]{1,2})\\s*(.*)$",
        RegexOption.IGNORE_CASE
    )
    private val KNS_SCHEME_TYPO_REGEX = Regex(
        "^kns\\s*(?:[.;:]\\s*[/\\\\]+|:[/\\\\]{1,2})\\s*(.*)$",
        RegexOption.IGNORE_CASE
    )

    /**
     * Normalizes malformed Kaspa scheme inputs (e.g. "Kaspa.//", "kaspa.//offline-game",
     * "kaspa;//", "kas.//alice.k") into canonical schemes without altering normal hosts like
     * "kaspa.org", currency queries like "kas/usd", or CashAddr addresses ("kaspa:qp...").
     */
    fun normalizeKaspaScheme(raw: String): String {
        val trimmed = raw.trim().replace("\r", "").replace("\n", "")
        if (trimmed.isEmpty()) return ""

        KASPA_SCHEME_TYPO_REGEX.matchEntire(trimmed)?.let { match ->
            val rest = match.groupValues[1].trimStart('/', '\\')
            return "kaspa://$rest"
        }
        KAS_SCHEME_TYPO_REGEX.matchEntire(trimmed)?.let { match ->
            val rest = match.groupValues[1].trimStart('/', '\\')
            return "kas://$rest"
        }
        KNS_SCHEME_TYPO_REGEX.matchEntire(trimmed)?.let { match ->
            val rest = match.groupValues[1].trimStart('/', '\\')
            return "kns://$rest"
        }
        return trimmed
    }

    /**
     * Returns true when a URL or resource target points to the built-in Offline Arcade.
     */
    fun isOfflineArcadeUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val canonical = normalizeKaspaScheme(rawUrl)
        val cleanPath = canonical
            .substringBefore('?')
            .substringBefore('#')
            .lowercase()
            .trimEnd('/')
        return cleanPath == "kaspa://offline-game" ||
            cleanPath == "kaspa://offline-games" ||
            cleanPath == "kaspa://arcade" ||
            cleanPath == "kaspa://games" ||
            cleanPath == "kas://offline-game" ||
            cleanPath == "kas://offline-games" ||
            cleanPath == "kas://arcade" ||
            cleanPath == "kas://games" ||
            cleanPath == "kaspa-action://arcade" ||
            cleanPath == "kaspa-action://play-offline"
    }
}

