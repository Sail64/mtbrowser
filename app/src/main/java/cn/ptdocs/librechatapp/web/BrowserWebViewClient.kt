package cn.ptdocs.librechatapp.web

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.http.SslError
import android.util.Log
import android.webkit.ClientCertRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import cn.ptdocs.librechatapp.data.PrefSiteRepository
import cn.ptdocs.librechatapp.domain.CertReminderScheduler
import cn.ptdocs.librechatapp.domain.CertificateAdvisor
import cn.ptdocs.librechatapp.domain.ClientCertSelector
import cn.ptdocs.librechatapp.domain.NavigationPolicy
import cn.ptdocs.librechatapp.domain.SslTrustPolicy
import cn.ptdocs.librechatapp.domain.model.CertInfo
import cn.ptdocs.librechatapp.domain.model.CertKind
import cn.ptdocs.librechatapp.domain.model.CertVerdict
import cn.ptdocs.librechatapp.domain.model.NavigationDecision
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap

/**
 * 薄胶水层：把 WebView 回调翻译成 domain 组件调用，自身不含业务策略。
 * 所有决策来自注入的 Advisor / Selector / Policy / Enhancer。
 */
class BrowserWebViewClient(
    private val activity: Activity,
    private val selector: ClientCertSelector,
    private val advisor: CertificateAdvisor,
    private val reminders: CertReminderScheduler,
    private val sslTrustPolicy: SslTrustPolicy,
    private val navigationPolicy: NavigationPolicy,
    private val sites: cn.ptdocs.librechatapp.domain.SiteRepository,
    private val enhancers: List<PageEnhancer>,
    private val onUrlChanged: (String) -> Unit
) : WebViewClient() {

    companion object {
        private const val TAG = "BrowserWebViewClient"
        private const val CERT_CLEAR_COOLDOWN_MS = 5000L
    }

    private val lastCertClearTime = ConcurrentHashMap<String, Long>()

    // ---------- 导航 ----------

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        val currentSite = view.url?.let { PrefSiteRepository.hostOf(it) }?.let { sites.byHost(it) }
        return when (navigationPolicy.decide(url, currentSite?.url)) {
            NavigationDecision.OPEN_IN_APP -> false
            NavigationDecision.OPEN_EXTERNAL -> {
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open external URL: $url", e)
                    Toast.makeText(activity, "无法打开此链接", Toast.LENGTH_SHORT).show()
                }
                true
            }
        }
    }

    // ---------- mTLS 客户端证书 ----------

    override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
        val host = request.host
        Log.d(TAG, "Client cert request: host=$host port=${request.port}")
        val cached = selector.cachedAlias(host)
        if (cached != null) {
            proceedWithCert(request, cached)
        } else {
            promptAlias(request)
        }
    }

    private fun proceedWithCert(request: ClientCertRequest, alias: String) {
        Thread {
            try {
                val privateKey: PrivateKey? = loadPrivateKey(activity, alias)
                val chain: Array<X509Certificate>? = loadCertificateChain(activity, alias)
                if (privateKey != null && chain != null && chain.isNotEmpty()) {
                    maybeRemindCertExpiry(request.host, chain[0], CertKind.CLIENT)
                    request.proceed(privateKey, chain)
                } else {
                    Log.w(TAG, "Missing key/chain for alias: $alias")
                    activity.runOnUiThread { promptAlias(request) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load key/chain for alias: $alias", e)
                activity.runOnUiThread { promptAlias(request) }
            }
        }.start()
    }

    private fun promptAlias(request: ClientCertRequest) {
        val keyTypes = request.keyTypes ?: arrayOf()
        val principals = request.principals ?: arrayOfNulls<java.security.Principal>(0)
        selector.chooseAlias(request.host, request.port, keyTypes, principals) { alias ->
            if (alias != null) proceedWithCert(request, alias) else request.ignore()
        }
    }

    private fun maybeRemindCertExpiry(host: String, x509: X509Certificate, kind: CertKind) {
        val cert = CertInfo(
            subject = x509.subjectX500Principal?.name,
            notAfter = x509.notAfter
        )
        val verdict = advisor.evaluate(cert, kind)
        if (verdict == CertVerdict.VALID) return
        if (!reminders.shouldRemind(host, kind)) return

        reminders.markReminded(host, kind)
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle("证书到期提醒")
                .setMessage(advisor.message(verdict, kind, advisor.daysLeft(cert)))
                .setPositiveButton("确定", null)
                .show()
        }
    }

    // ---------- SSL 错误 ----------

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        val url = error.url
        val host = try { java.net.URI(url).host } catch (e: Exception) { null }
            ?: run { handler.cancel(); return }
        val cert = error.certificate
        val certInfo = cert?.let {
            CertInfo(subject = null, notAfter = it.validNotAfterDate)
        }
        val errorKind = when (error.primaryError) {
            SslError.SSL_EXPIRED -> "证书已过期"
            SslError.SSL_NOTYETVALID -> "证书尚未生效"
            SslError.SSL_DATE_INVALID -> "证书日期无效"
            SslError.SSL_IDMISMATCH -> "证书与域名不匹配"
            SslError.SSL_UNTRUSTED -> "证书不受信任"
            else -> null
        }

        when (val decision = sslTrustPolicy.onError(host, certInfo, errorKind)) {
            is SslTrustPolicy.Decision.Proceed -> handler.proceed()
            is SslTrustPolicy.Decision.AskUser -> askUserAboutSslError(host, handler, decision.reason)
            is SslTrustPolicy.Decision.Deny -> handler.cancel()
        }
    }

    private fun askUserAboutSslError(host: String, handler: SslErrorHandler, reason: String) {
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle("SSL 证书警告")
                .setMessage("$reason\n\n是否仍要继续访问 $host？\n（本次运行内不再询问该站点）")
                .setNegativeButton("返回主页") { _, _ -> handler.cancel() }
                .setPositiveButton("仍要继续") { _, _ ->
                    sslTrustPolicy.grantSessionExemption(host)
                    handler.proceed()
                }
                .setOnCancelListener { handler.cancel() }
                .show()
        }
    }

    // ---------- HTTP 错误：per-host 证书失效清理 ----------

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        Log.e(TAG, "onReceivedHttpError: statusCode=${errorResponse.statusCode}, url=${request.url}")

        if (request.isForMainFrame && errorResponse.statusCode == 400) {
            val host = request.url.host
            val cached = host?.let { selector.cachedAlias(it) }
            if (host != null && cached != null) {
                val now = System.currentTimeMillis()
                val last = lastCertClearTime[host] ?: 0L
                if (now - last > CERT_CLEAR_COOLDOWN_MS) {
                    Log.d(TAG, "Clearing client cert for host=$host due to 400 error")
                    lastCertClearTime[host] = now
                    selector.clearAlias(host)
                    WebView.clearClientCertPreferences {
                        activity.runOnUiThread {
                            Toast.makeText(activity, "客户端证书已失效，请重新选择", Toast.LENGTH_LONG).show()
                            view.reload()
                        }
                    }
                }
            }
        }
        super.onReceivedHttpError(view, request, errorResponse)
    }

    // ---------- 错误日志 ----------

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        Log.e(TAG, "onReceivedError: errorCode=${error.errorCode}, description=${error.description}, url=${request.url}")
        super.onReceivedError(view, request, error)
    }

    // ---------- 页面完成：增强器注册表 ----------

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        val host = PrefSiteRepository.hostOf(url)
        val site = host?.let { sites.byHost(it) }
        enhancers.forEach { enhancer ->
            try {
                if (enhancer.appliesTo(url, site)) {
                    enhancer.onPageFinished(activity, view, url, site)
                }
            } catch (e: Exception) {
                Log.e(TAG, "PageEnhancer '${enhancer.id}' failed", e)
            }
        }
        onUrlChanged(url)
    }

    // KeyChain 访问禁止在主线程进行，集中在此供后台线程调用
    private fun loadPrivateKey(activity: Activity, alias: String) =
        android.security.KeyChain.getPrivateKey(activity, alias)

    private fun loadCertificateChain(activity: Activity, alias: String) =
        android.security.KeyChain.getCertificateChain(activity, alias)
}
