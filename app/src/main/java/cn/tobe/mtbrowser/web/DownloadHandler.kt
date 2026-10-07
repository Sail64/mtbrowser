package cn.tobe.mtbrowser.web

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import java.io.IOException

class DownloadHandler(private val context: Context) : DownloadListener {

    companion object {
        private const val TAG = "DownloadHandler"
        const val JS_INTERFACE_NAME = "AndroidDownload"
    }

    private var webView: WebView? = null

    fun setup(webView: WebView) {
        this.webView = webView
        webView.setDownloadListener(this)
        webView.addJavascriptInterface(this, JS_INTERFACE_NAME)
    }

    override fun onDownloadStart(
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?,
        contentLength: Long
    ) {
        val safeUrl = url ?: return
        val safeMimeType = mimetype ?: "application/octet-stream"
        val safeUserAgent = userAgent ?: ""

        Log.d(TAG, "onDownloadStart: url=$safeUrl, mimetype=$safeMimeType")

        if (URLUtil.isNetworkUrl(safeUrl)) {
            handleHttpUrl(safeUrl, safeUserAgent, safeMimeType)
        } else {
            handleLocalUrl(safeUrl, safeMimeType)
        }
    }

    private fun handleLocalUrl(url: String, mimetype: String) {
        when {
            url.startsWith("data:") -> handleDataUrl(url, mimetype)
            url.startsWith("blob:") -> handleBlobUrl(url, mimetype)
            else -> {
                Log.w(TAG, "Unsupported download url: $url")
                showToast("不支持的下载链接")
            }
        }
    }

    private fun handleHttpUrl(
        url: String,
        userAgent: String,
        mimetype: String
    ) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(mimetype)
            request.addRequestHeader("User-Agent", userAgent)
            val cookies = CookieManager.getInstance().getCookie(url)
            if (cookies != null) {
                request.addRequestHeader("Cookie", cookies)
            }
            request.setDescription("Downloading file...")
            
            val fileName = buildTitleFileName(webView?.title, mimetype, url)
            request.setTitle(fileName)
            
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            
            showToast("开始下载: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            showToast("下载失败: ${e.message}")
        }
    }

    private fun handleDataUrl(url: String, mimetype: String) {
        try {
            val commaIndex = url.indexOf(',')
            if (commaIndex == -1) {
                Log.e(TAG, "Invalid data URL")
                return
            }
            
            val header = url.substring(0, commaIndex)
            val data = url.substring(commaIndex + 1)
            
            val isBase64 = header.contains(";base64")
            val bytes = if (isBase64) {
                Base64.decode(data, Base64.DEFAULT)
            } else {
                Uri.decode(data).toByteArray()
            }

            val fileName = buildTitleFileName(webView?.title, mimetype, null)
            saveFile(bytes, fileName, mimetype)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle data URL", e)
            showToast("下载失败: ${e.message}")
        }
    }

    private fun handleBlobUrl(url: String, mimetype: String) {
        val js = """
            (function() {
                var url = '$url';
                var pageTitle = document.title || 'librechat';
                
                var xhr = new XMLHttpRequest();
                xhr.open('GET', url, true);
                xhr.responseType = 'blob';
                xhr.onload = function(e) {
                    if (this.status == 200) {
                        var blob = this.response;
                        var reader = new FileReader();
                        reader.readAsDataURL(blob);
                        reader.onloadend = function() {
                            var base64data = reader.result;
                            $JS_INTERFACE_NAME.processBlob(base64data, '$mimetype', pageTitle);
                        }
                    }
                };
                xhr.send();
            })();
        """.trimIndent()
        
        webView?.post {
            webView?.evaluateJavascript(js, null)
        }
    }

    @JavascriptInterface
    fun processBlob(base64Data: String, mimetype: String, pageTitle: String) {
        try {
            val commaIndex = base64Data.indexOf(',')
            if (commaIndex == -1) return
            
            val data = base64Data.substring(commaIndex + 1)
            val bytes = Base64.decode(data, Base64.DEFAULT)
            val fileName = buildTitleFileName(pageTitle, mimetype, null)
            saveFile(bytes, fileName, mimetype)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process blob", e)
            showToast("处理文件失败: ${e.message}")
        }
    }

    private fun buildTitleFileName(rawTitle: String?, mimeType: String, url: String?): String {
        val title = rawTitle?.trim().orEmpty()
        val baseName = if (title.isBlank()) "librechat" else title
        val sanitized = baseName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val safeBaseName = if (sanitized.isBlank()) "librechat" else sanitized

        val extensionFromMime = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?.takeIf { it.isNotBlank() }
        val extensionFromUrl = url
            ?.let { MimeTypeMap.getFileExtensionFromUrl(it) }
            ?.takeIf { it.isNotBlank() }
        val extension = extensionFromMime ?: extensionFromUrl ?: "bin"

        return if (safeBaseName.endsWith(".$extension", ignoreCase = true)) {
            safeBaseName
        } else {
            "$safeBaseName.$extension"
        }
    }

    private fun saveFile(bytes: ByteArray, fileName: String, mimetype: String) {
        var finalMimeType = mimetype
        if (fileName.endsWith(".md", ignoreCase = true)) {
            finalMimeType = "text/markdown"
        } else if (fileName.endsWith(".json", ignoreCase = true)) {
            finalMimeType = "application/json"
        } else if (fileName.endsWith(".csv", ignoreCase = true)) {
            finalMimeType = "text/csv"
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, finalMimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(bytes)
                }
                showToast("文件已保存到: $fileName")
            } catch (e: IOException) {
                Log.e(TAG, "Failed to save file", e)
                showToast("保存文件失败: ${e.message}")
            }
        } else {
            showToast("无法创建文件")
        }
    }
    
    private fun showToast(message: String) {
        if (context is android.app.Activity) {
            context.runOnUiThread {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
