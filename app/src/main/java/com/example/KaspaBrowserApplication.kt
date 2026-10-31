package com.example

import android.app.Application
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.android.relay.ConnectionType
import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit

class KaspaBrowserApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val projectId = BuildConfig.REOWN_PROJECT_ID
        check(projectId.isNotBlank()) { "Reown Project ID is not configured" }

        val metadata = Core.Model.AppMetaData(
            name = "Kaspa Browser",
            description = "Kaspa Browser wallet",
            url = "https://kaspabrowser.xyz",
            icons = listOf("https://kaspabrowser.xyz/favicon.ico"),
            redirect = "kaspabrowser://open",
            appLink = "",
            linkMode = false
        )

        CoreClient.initialize(
            application = this,
            projectId = projectId,
            metaData = metadata,
            connectionType = ConnectionType.AUTOMATIC,
            onError = { error ->
                android.util.Log.e(
                    "ReownWalletKit",
                    "Core initialization failed",
                    error.throwable
                )
            }
        )

        WalletKit.initialize(
            Wallet.Params.Init(core = CoreClient),
            onSuccess = {
                WalletKit.setWalletDelegate(com.example.dapp.KaspaWalletConnectController)
                com.example.dapp.KaspaWalletConnectController.onWalletKitInitialized()
                android.util.Log.i("ReownWalletKit", "WalletKit initialized")
            },
            onError = { error ->
                com.example.dapp.KaspaWalletConnectController.onWalletKitInitializationFailed(
                    error.throwable.message ?: "WalletKit initialization failed"
                )
                android.util.Log.e(
                    "ReownWalletKit",
                    "WalletKit initialization failed",
                    error.throwable
                )
            }
        )
    }
}
