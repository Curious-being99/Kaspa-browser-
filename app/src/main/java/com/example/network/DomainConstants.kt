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
}
