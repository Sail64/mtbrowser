package cn.tobe.mtbrowser.web

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object WebViewConfigurator {
    fun configure(webView: WebView) {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowContentAccess = false
            saveFormData = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                forceDark = WebSettings.FORCE_DARK_AUTO
            }
        }
    }
}
