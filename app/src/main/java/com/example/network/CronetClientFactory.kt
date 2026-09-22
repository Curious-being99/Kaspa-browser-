package com.example.network

import android.content.Context
import android.util.Log
import com.google.android.gms.net.CronetProviderInstaller
import com.google.net.cronet.okhttptransport.CronetInterceptor
import okhttp3.OkHttpClient
import org.chromium.net.CronetEngine

object CronetClientFactory {
    private const val TAG = "CronetClientFactory"
    private var cronetEngine: CronetEngine? = null

    fun initialize(context: Context) {
        try {
            // Install the Cronet provider from Google Play Services
            CronetProviderInstaller.installProvider(context)
            
            // Build the CronetEngine with QUIC (HTTP/3) enabled
            cronetEngine = CronetEngine.Builder(context)
                .enableQuic(true)
                .enableHttp2(true)
                .enableBrotli(true)
                .build()
                
            Log.d(TAG, "Cronet engine initialized successfully with QUIC (HTTP/3) enabled!")
        } catch (e: Exception) {
            Log.w(TAG, "Cronet provider installation failed. Falling back to default HTTP/2 transport: ${e.message}")
        }
    }

    fun buildClient(baseBuilder: OkHttpClient.Builder): OkHttpClient {
        val engine = cronetEngine
        if (engine != null) {
            try {
                // Build the CronetInterceptor to intercept standard OkHttp traffic
                val cronetInterceptor = CronetInterceptor.newBuilder(engine).build()
                Log.d(TAG, "Applied CronetInterceptor to OkHttp. True QUIC transport is active!")
                return baseBuilder
                    .addInterceptor(cronetInterceptor)
                    .build()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply CronetInterceptor, using standard transport: ${e.message}")
            }
        }
        return baseBuilder.build()
    }
}
