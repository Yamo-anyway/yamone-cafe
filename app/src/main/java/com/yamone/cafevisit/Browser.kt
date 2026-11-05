package com.yamone.cafevisit

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object Browser {
    const val HOME = "https://m.cafe.naver.com/"
    const val LOGIN = "https://nid.naver.com/nidlogin.login?url=https%3A%2F%2Fm.cafe.naver.com%2F"
    @SuppressLint("SetJavaScriptEnabled")
    fun create(c: Context, fresh: Boolean = false): WebView = WebView(c).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = if (fresh) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        // No JavaScript bridge; credentials are only entered into Naver's own HTTPS page.
    }
}
