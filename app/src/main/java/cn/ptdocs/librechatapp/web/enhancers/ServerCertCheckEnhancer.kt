package cn.ptdocs.librechatapp.web.enhancers

import android.app.Activity
import android.app.AlertDialog
import android.util.Log
import android.webkit.WebView
import cn.ptdocs.librechatapp.data.PrefSiteRepository
import cn.ptdocs.librechatapp.domain.CertReminderScheduler
import cn.ptdocs.librechatapp.domain.CertificateAdvisor
import cn.ptdocs.librechatapp.domain.model.CertKind
import cn.ptdocs.librechatapp.domain.model.CertVerdict
import cn.ptdocs.librechatapp.domain.model.Site
import cn.ptdocs.librechatapp.platform.HttpsCertProber
import cn.ptdocs.librechatapp.web.PageEnhancer

/**
 * 服务端证书过期检查（https 站点）。
 * 旁路探测由 CertReminderScheduler 门控（每 host 24h 一次），失败静默。
 */
class ServerCertCheckEnhancer(
    private val advisor: CertificateAdvisor,
    private val reminders: CertReminderScheduler
) : PageEnhancer {

    companion object {
        private const val TAG = "ServerCertCheckEnhancer"
    }

    override val id = "server_cert_check"

    override fun appliesTo(url: String, site: Site?): Boolean =
        url.startsWith("https://")

    override fun onPageFinished(activity: Activity, view: WebView, url: String, site: Site?) {
        val host = PrefSiteRepository.hostOf(url) ?: return
        if (!reminders.shouldRemind(host, CertKind.SERVER)) return

        HttpsCertProber.probeAsync(url) { cert ->
            if (cert == null) return@probeAsync
            val verdict = advisor.evaluate(cert, CertKind.SERVER)
            if (verdict == CertVerdict.VALID) return@probeAsync

            Log.d(TAG, "Server certificate $verdict for $host")
            reminders.markReminded(host, CertKind.SERVER)
            AlertDialog.Builder(activity)
                .setTitle("证书到期提醒")
                .setMessage(advisor.message(verdict, CertKind.SERVER, advisor.daysLeft(cert)))
                .setPositiveButton("确定", null)
                .show()
        }
    }
}
