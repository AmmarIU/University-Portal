package com.example.odoo

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient

class AuthWebViewClient(
    private val loginUrl: String,
    private val onSessionExtracted: (String) -> Unit
) : WebViewClient() {

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        checkUrlForAuth(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        checkUrlForAuth(url)
    }

    private fun checkUrlForAuth(url: String) {
        Log.d("AuthWebView", "Current WebView URL: $url")
        
        // If we are no longer on the login page, assume authentication was successful!
        if (!url.contains("/web/login")) {
            val cookieManager = CookieManager.getInstance()
            val cookies = cookieManager.getCookie(url)
            
            // Extract the session_id from the cookie string
            val sessionId = extractSessionId(cookies)
            if (sessionId != null) {
                Log.d("AuthWebView", "Successfully captured session_id!")
                onSessionExtracted(sessionId)
            } else {
                Log.d("AuthWebView", "Navigated away from login, but no session_id cookie found yet.")
            }
        }
    }

    private fun extractSessionId(cookieString: String?): String? {
        if (cookieString == null) return null
        
        // Cookies are returned as a single string separated by semicolons
        // Example: "frontend_lang=en_US; session_id=123abc456def..."
        val cookieList = cookieString.split(";")
        for (cookie in cookieList) {
            val trimmedCookie = cookie.trim()
            if (trimmedCookie.startsWith("session_id=")) {
                return trimmedCookie.substringAfter("session_id=")
            }
        }
        return null
    }
}
