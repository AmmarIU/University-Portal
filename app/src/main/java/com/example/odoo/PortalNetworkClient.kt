package com.example.odoo

import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class PortalNetworkClient(
    cacheDir: File,
    private val getSessionId: () -> String?
) {
    // 10 MB cache to avoid hammering the Odoo server
    private val cacheSize = 10L * 1024 * 1024
    private val cache = Cache(cacheDir, cacheSize)

    // Interceptor to inject the session cookie
    private val sessionInterceptor = Interceptor { chain ->
        val requestBuilder = chain.request().newBuilder()
        
        getSessionId()?.let { sessionId ->
            // Odoo relies heavily on the session_id cookie
            requestBuilder.addHeader("Cookie", "session_id=$sessionId")
        }
        
        // Act like a standard browser to avoid triggering basic bot protections
        requestBuilder.addHeader("User-Agent", "Mozilla/5.0 (Android 14; Mobile) AppleWebKit/537.36")
        
        chain.proceed(requestBuilder.build())
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cache(cache)
        .addInterceptor(sessionInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}
