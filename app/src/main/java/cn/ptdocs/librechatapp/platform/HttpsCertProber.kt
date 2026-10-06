package cn.ptdocs.librechatapp.platform

import android.os.Handler
import android.os.Looper
import android.util.Log
import cn.ptdocs.librechatapp.domain.model.CertInfo
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * 服务端证书旁路探测。
 * 已知局限：该连接不携带客户端证书，对 mTLS 站点可能 400/403 甚至被 WAF 拦截，
 * 因此失败一律静默返回 null；调用方必须自行限频（本设计由 ServerCertCheckEnhancer
 * 通过 CertReminderScheduler 门控，每 host 24h 最多探测一次）。
 */
object HttpsCertProber {

    private const val TAG = "HttpsCertProber"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun probeAsync(urlString: String, callback: (CertInfo?) -> Unit) {
        Thread {
            val cert = try {
                probe(urlString)
            } catch (e: Exception) {
                Log.w(TAG, "Server cert probe failed (silently ignored): ${e.message}")
                null
            }
            mainHandler.post { callback(cert) }
        }.start()
    }

    private fun probe(urlString: String): CertInfo? {
        val url = URL(urlString)
        if (url.protocol != "https") return null

        val conn = url.openConnection() as HttpsURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.connect()
        val certs = conn.serverCertificates
        conn.disconnect()

        val first = certs.firstOrNull() as? java.security.cert.X509Certificate ?: return null
        return CertInfo(subject = first.subjectX500Principal?.name, notAfter = first.notAfter)
    }
}
