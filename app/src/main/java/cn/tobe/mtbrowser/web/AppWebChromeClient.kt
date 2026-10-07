package cn.tobe.mtbrowser.web

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import cn.tobe.mtbrowser.MainActivity

class AppWebChromeClient(
    private val activity: MainActivity
) : WebChromeClient() {

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        return activity.showFileChooser(filePathCallback, fileChooserParams)
    }
}
