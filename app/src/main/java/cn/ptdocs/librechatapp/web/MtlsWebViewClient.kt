package cn.ptdocs.librechatapp.web

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.http.SslError
import android.security.KeyChain
import android.util.Log
import android.webkit.ClientCertRequest
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import cn.ptdocs.librechatapp.storage.Prefs
import java.net.URL
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

import android.widget.Toast

class MtlsWebViewClient(
    private val activity: Activity,
    private val onSettingsVisibilityChange: (Boolean) -> Unit
) : WebViewClient() {

    companion object {
        private const val TAG = "MtlsWebViewClient"
        private const val CERT_CLEAR_COOLDOWN_MS = 5000L

        private const val CLIENT_CERT_WARN_DAYS = 30L
        private const val SERVER_CERT_WARN_DAYS = 14L

        private const val CLIENT_CERT_LABEL = "客户端证书（注意：非服务端证书）"
        private const val SERVER_CERT_LABEL = "服务端证书（注意：非客户端证书）"

        private fun certExpiredMsg(label: String) = "${label}已过期，请立即更新。"
        private fun certExpiringMsg(label: String, days: Long) = "${label}将在 $days 天后过期，请及时联系管理员更新。"
    }

    private var hasShownExpiryWarning = false
    private var hasShownServerExpiryWarning = false
    private var lastCertClearTime = 0L

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        val currentHost = Prefs.getHost(activity)
        if (currentHost != null && url.host == currentHost) {
            return false
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, url)
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open external URL: $url", e)
        }
        return true
    }

    private fun checkCertificateExpiry(cert: X509Certificate) {
        if (hasShownExpiryWarning) return

        val expiryDate = cert.notAfter
        val now = Date()
        val diff = expiryDate.time - now.time
        
        // 如果证书已经过期，diff 会是负数，这里我们只关心即将过期的（比如还有 30 天）
        // 如果已经过期，通常 TLS 握手会失败，或者浏览器会提示证书错误，但这里我们也提示一下
        val daysLeft = TimeUnit.MILLISECONDS.toDays(diff)

        Log.d(TAG, "Certificate expires in $daysLeft days")

        if (daysLeft < CLIENT_CERT_WARN_DAYS) {
            hasShownExpiryWarning = true
            activity.runOnUiThread {
                val message = if (daysLeft < 0) {
                    certExpiredMsg(CLIENT_CERT_LABEL)
                } else {
                    certExpiringMsg(CLIENT_CERT_LABEL, daysLeft)
                }
                
                AlertDialog.Builder(activity)
                    .setTitle("证书到期提醒")
                    .setMessage(message)
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
    }

    private fun checkServerCertificateExpiry(urlString: String) {
        if (hasShownServerExpiryWarning) return

        Thread {
            try {
                val url = URL(urlString)
                if (url.protocol != "https") return@Thread

                val conn = url.openConnection() as HttpsURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.connect()

                val certs = conn.serverCertificates
                conn.disconnect()

                if (certs.isNotEmpty() && certs[0] is X509Certificate) {
                    val serverCert = certs[0] as X509Certificate
                    val expiryDate = serverCert.notAfter
                    val now = Date()
                    val diff = expiryDate.time - now.time
                    val daysLeft = TimeUnit.MILLISECONDS.toDays(diff)

                    Log.d(TAG, "Server certificate expires in $daysLeft days")

                    if (daysLeft < SERVER_CERT_WARN_DAYS) {
                        hasShownServerExpiryWarning = true
                        activity.runOnUiThread {
                            val message = if (daysLeft < 0) {
                                certExpiredMsg(SERVER_CERT_LABEL)
                            } else {
                                certExpiringMsg(SERVER_CERT_LABEL, daysLeft)
                            }

                            AlertDialog.Builder(activity)
                                .setTitle("证书到期提醒")
                                .setMessage(message)
                                .setPositiveButton("确定", null)
                                .show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check server certificate expiry: ${e.message}")
            }
        }.start()
    }

    override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
        val savedAlias = Prefs.getAlias(activity)

        Log.d(TAG, "Client cert request: host=${request.host} port=${request.port} savedAlias=${savedAlias != null}")

        lateinit var proceedWithAlias: (String) -> Unit

        fun chooseAlias() {
            Log.d(TAG, "Prompting for client cert alias")
            KeyChain.choosePrivateKeyAlias(
                activity,
                { alias ->
                    Log.d(TAG, "Alias selection result: ${alias ?: "null"}")
                    if (alias != null) {
                        Prefs.setAlias(activity, alias)
                        proceedWithAlias(alias)
                    } else {
                        request.ignore()
                    }
                },
                request.keyTypes,
                request.principals,
                request.host,
                request.port,
                null
            )
        }

        proceedWithAlias = { alias ->
            Thread {
                try {
                    val privateKey = KeyChain.getPrivateKey(activity, alias)
                    val chain = KeyChain.getCertificateChain(activity, alias)
                    if (privateKey != null && chain != null) {
                        Log.d(TAG, "Proceeding with alias: $alias, chainLen=${chain.size}")

                        if (chain.isNotEmpty()) {
                            checkCertificateExpiry(chain[0])
                        }

                        request.proceed(privateKey, chain)
                    } else {
                        Log.w(TAG, "Missing key/chain for alias: $alias")
                        activity.runOnUiThread { chooseAlias() }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load key/chain for alias: $alias", e)
                    activity.runOnUiThread { chooseAlias() }
                }
            }.start()
        }

        if (savedAlias != null) {
            Log.d(TAG, "Using cached alias")
            proceedWithAlias(savedAlias)
            return
        }

        chooseAlias()
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        Log.e(TAG, "onReceivedError: errorCode=${error.errorCode}, description=${error.description}, url=${request.url}")
        super.onReceivedError(view, request, error)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        Log.e(TAG, "onReceivedSslError: primaryError=${error.primaryError}, url=${error.url}")

        val sslCert = error.certificate
        if (sslCert != null) {
            try {
                val expiryDate = sslCert.validNotAfterDate
                val now = Date()
                val diff = expiryDate.time - now.time
                val daysLeft = TimeUnit.MILLISECONDS.toDays(diff)

                Log.d(TAG, "SSL error - server certificate expires in $daysLeft days, primaryError=${error.primaryError}")

                if (daysLeft < SERVER_CERT_WARN_DAYS || error.primaryError == SslError.SSL_EXPIRED) {
                    if (!hasShownServerExpiryWarning) {
                        hasShownServerExpiryWarning = true
                        activity.runOnUiThread {
                            val message = if (daysLeft < 0 || error.primaryError == SslError.SSL_EXPIRED) {
                                certExpiredMsg(SERVER_CERT_LABEL)
                            } else {
                                certExpiringMsg(SERVER_CERT_LABEL, daysLeft)
                            }

                            AlertDialog.Builder(activity)
                                .setTitle("证书到期提醒")
                                .setMessage(message)
                                .setPositiveButton("确定", null)
                                .show()

                            onSettingsVisibilityChange(true)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse SSL certificate: ${e.message}")
            }
        }

        if (!hasShownServerExpiryWarning) {
            hasShownServerExpiryWarning = true
            activity.runOnUiThread {
                AlertDialog.Builder(activity)
                    .setTitle("证书到期提醒")
                    .setMessage("${SERVER_CERT_LABEL}无效，请检查服务器配置。")
                    .setPositiveButton("确定", null)
                    .show()

                onSettingsVisibilityChange(true)
            }
        }

        handler.cancel()
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        Log.e(TAG, "onReceivedHttpError: statusCode=${errorResponse.statusCode}, reasonPhrase=${errorResponse.reasonPhrase}, url=${request.url}")

        if (request.isForMainFrame && errorResponse.statusCode == 400) {
            val savedAlias = Prefs.getAlias(activity)
            val currentTime = System.currentTimeMillis()

            if (savedAlias != null && (currentTime - lastCertClearTime > CERT_CLEAR_COOLDOWN_MS)) {
                Log.d(TAG, "Clearing client cert preferences due to 400 error on main frame")
                lastCertClearTime = currentTime

                // Clear alias
                Prefs.clearAlias(activity)

                // Clear WebView cache
                WebView.clearClientCertPreferences {
                    Log.d(TAG, "Client cert preferences cleared")
                    activity.runOnUiThread {
                        Toast.makeText(activity, "${CLIENT_CERT_LABEL}已失效，请重新选择", Toast.LENGTH_LONG).show()
                        view.reload()
                    }
                }
            }
        }

        super.onReceivedHttpError(view, request, errorResponse)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        CookieManager.getInstance().flush()
        Log.d(TAG, "Page finished, cookies flushed: $url")

        if (Prefs.getRenameFocusFix(activity)) {
            RenameFocusGuard.inject(view)
        }

        checkServerCertificateExpiry(url)

        val cookies = CookieManager.getInstance().getCookie(url)
        val isLoginPage = url.contains("/login")
        val hasCookies = !cookies.isNullOrEmpty()
        
        // Show settings if on login page OR no cookies
        val shouldShowSettings = isLoginPage || !hasCookies
        
        activity.runOnUiThread {
            onSettingsVisibilityChange(shouldShowSettings)
        }
    }
}
