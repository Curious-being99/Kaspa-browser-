package com.example.network.kaspa

sealed interface TransferGate {
    data class Blocked(val reason: String) : TransferGate
    data object Ready : TransferGate
}

object KbKcc20TransferBuilder {
    private const val PUBLISHED_STATUS = "published"

    fun gateStatus(
        config: KbKcc20DeploymentConfig = KbKcc20Protocol.reviewedConfig
    ): TransferGate {
        if (config.artifactStatus != PUBLISHED_STATUS) {
            return TransferGate.Blocked(
                "KB transfer blocked: the covenant artifact is unpublished; no verified assembler is available."
            )
        }

        if (config.conformant != true) {
            return TransferGate.Blocked(
                "KB transfer blocked: KCC-20 conformance is not claimed with verified evidence."
            )
        }

        return TransferGate.Blocked(
            "KB transfer blocked: the on-device covenant transaction assembler is not integrated. Refusing to sign a plain KAS payment as a token transfer."
        )
    }

    fun isSigningAllowed(): Boolean = gateStatus() is TransferGate.Ready

    fun <T> buildSignableTransaction(assemble: () -> T): T {
        val gate = gateStatus()
        check(gate is TransferGate.Ready) {
            (gate as TransferGate.Blocked).reason
        }
        return assemble()
    }
}
