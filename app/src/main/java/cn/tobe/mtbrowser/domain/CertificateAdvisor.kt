package cn.tobe.mtbrowser.domain

import cn.tobe.mtbrowser.domain.model.CertInfo
import cn.tobe.mtbrowser.domain.model.CertKind
import cn.tobe.mtbrowser.domain.model.CertVerdict
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * 证书评估的唯一事实来源（SSOT）。
 * 所有需要判断证书健康度/组装文案的地方都必须问它，不允许各自计算天数。
 */
interface CertificateAdvisor {
    fun evaluate(cert: CertInfo, kind: CertKind): CertVerdict
    fun daysLeft(cert: CertInfo): Long
    fun message(verdict: CertVerdict, kind: CertKind, daysLeft: Long): String
}

/** 默认实现：客户端证书 30 天、服务端证书 14 天阈值（与历史版本行为一致）。 */
class DefaultCertificateAdvisor(
    private val clientWarnDays: Long = 30L,
    private val serverWarnDays: Long = 14L
) : CertificateAdvisor {

    override fun daysLeft(cert: CertInfo): Long {
        val diff = cert.notAfter.time - Date().time
        return TimeUnit.MILLISECONDS.toDays(diff)
    }

    override fun evaluate(cert: CertInfo, kind: CertKind): CertVerdict {
        val days = daysLeft(cert)
        val threshold = if (kind == CertKind.CLIENT) clientWarnDays else serverWarnDays
        return when {
            days < 0 -> CertVerdict.EXPIRED
            days < threshold -> CertVerdict.EXPIRING
            else -> CertVerdict.VALID
        }
    }

    override fun message(verdict: CertVerdict, kind: CertKind, daysLeft: Long): String {
        val label = when (kind) {
            CertKind.CLIENT -> "客户端证书（注意：非服务端证书）"
            CertKind.SERVER -> "服务端证书（注意：非客户端证书）"
        }
        return when (verdict) {
            CertVerdict.EXPIRED -> "${label}已过期，请立即更新。"
            CertVerdict.EXPIRING -> "${label}将在 $daysLeft 天后过期，请及时联系管理员更新。"
            CertVerdict.VALID -> "${label}状态正常。"
        }
    }
}
