package com.example.network

import android.content.Context
import android.util.Log
import com.google.android.gms.net.CronetProviderInstaller
import com.google.net.cronet.okhttptransport.CronetInterceptor
import okhttp3.OkHttpClient
import org.chromium.net.CronetEngine
import org.chromium.net.CronetProvider

object CronetClientFactory {
    private const val TAG = "CronetClientFactory"
    private var cronetEngine: CronetEngine? = null

    fun initialize(context: Context) {
        try {
            // Install the Cronet provider from Google Play Services
            try {
                CronetProviderInstaller.installProvider(context)
            } catch (e: Exception) {
                Log.w(TAG, "CronetProviderInstaller warning: ${e.message}")
            }

            // Find best available Cronet provider (Google Play Services, Native, or Fallback)
            val providers = CronetProvider.getAllProviders(context)
                .filter { it.isEnabled }
                .sortedByDescending { it.name.contains("Play-Services") || it.name.contains("Native") }

            val builder = if (providers.isNotEmpty()) {
                providers.first().createBuilder()
            } else {
                CronetEngine.Builder(context)
            }

            // Configure HTTP/3 (QUIC), HTTP/2, Brotli, and QUIC Hints
            cronetEngine = builder
                .enableQuic(true)
                .enableHttp2(true)
                .enableBrotli(true)
                .addQuicHint("api.kaspa.org", 443, 443)
                .addQuicHint("api.kasplex.org", 443, 443)
                .addQuicHint("tn10api.kasplex.org", 443, 443)
                .addQuicHint("rpc.igralabs.com", 443, 443)
                .addQuicHint("chainlist.org", 443, 443)
                .build()

            Log.d(TAG, "Cronet engine initialized successfully with HTTP/3 (QUIC) enabled!")
        } catch (e: Exception) {
            Log.w(TAG, "Cronet engine initialization failed: ${e.message}")
        }
    }

    fun buildClient(baseBuilder: OkHttpClient.Builder): OkHttpClient {
        val engine = cronetEngine
        if (engine != null) {
            try {
                val cronetInterceptor = CronetInterceptor.newBuilder(engine).build()
                Log.d(TAG, "Applied CronetInterceptor (HTTP/3 QUIC) to OkHttp client!")
                return baseBuilder
                    .addInterceptor(cronetInterceptor)
                    .build()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply CronetInterceptor: ${e.message}")
            }
        }
        return baseBuilder.build()
    }
}
