package com.example.extensions

/**
 * Describes a wallet extension that has been tested with a specific engine build.
 *
 * This is compatibility metadata only; it does not load extensions or expose a
 * wallet provider. Never infer compatibility from a wallet's name or website.
 */
data class WalletExtensionCompatibility(
    val displayName: String,
    val engine: ExtensionEngine,
    val packageFormat: ExtensionPackageFormat,
    val testedVersion: String?,
    val installVerified: Boolean,
    val dappConnectionVerified: Boolean,
    val transactionApprovalVerified: Boolean,
    val privacyRoutingVerified: Boolean
) {
    val isSupported: Boolean
        get() = testedVersion != null &&
            installVerified &&
            dappConnectionVerified &&
            transactionApprovalVerified &&
            privacyRoutingVerified
}

enum class ExtensionEngine {
    GECKOVIEW,
    CHROMIUM
}

enum class ExtensionPackageFormat {
    SIGNED_XPI,
    CRX,
    UNVERIFIED
}

/**
 * Only report support when the exact extension version and all security-critical
 * tests pass. A missing entry means "not verified", not "compatible".
 */
object KaspaWalletExtensionSupport {
    fun status(entry: WalletExtensionCompatibility?): SupportStatus {
        if (entry == null) return SupportStatus.NOT_TESTED
        if (entry.packageFormat == ExtensionPackageFormat.UNVERIFIED) {
            return SupportStatus.UNSUPPORTED_PACKAGE
        }
        return if (entry.isSupported) SupportStatus.VERIFIED else SupportStatus.INCOMPLETE
    }
}

enum class SupportStatus {
    NOT_TESTED,
    UNSUPPORTED_PACKAGE,
    INCOMPLETE,
    VERIFIED
}
