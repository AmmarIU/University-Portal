package com.example.odoo

import android.webkit.CookieManager
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
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
        
        val urlString = originalRequest.url.toString()
        val cookies = CookieManager.getInstance().getCookie(urlString)
        
        if (cookies != null) {
            // Inject all cookies managed by the WebView for this URL (e.g. session_id, load balancer cookies)
            requestBuilder.addHeader("Cookie", cookies)
        } else {
            // Fallback just in case
            getSessionId()?.let { sessionId ->
                requestBuilder.addHeader("Cookie", "session_id=$sessionId")
            }
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
