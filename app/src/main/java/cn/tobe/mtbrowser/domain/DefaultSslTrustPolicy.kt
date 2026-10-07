package cn.tobe.mtbrowser.domain

import cn.tobe.mtbrowser.domain.model.CertInfo
import cn.tobe.mtbrowser.domain.model.CertKind
import cn.tobe.mtbrowser.domain.model.CertVerdict
import java.util.concurrent.ConcurrentHashMap

/**
 * 默认 SSL 放行策略：
 * - 已有会话豁免的 host 直接放行；
 * - 其余一律交用户决策（自签/过期内网站点不再无路可走，但绝不静默放行）。
 */
class DefaultSslTrustPolicy(private val advisor: CertificateAdvisor) : SslTrustPolicy {

    private val exemptions = ConcurrentHashMap.newKeySet<String>()

    override fun onError(host: String, cert: CertInfo?, errorKind: String?): SslTrustPolicy.Decision {
        if (hasSessionExemption(host)) return SslTrustPolicy.Decision.Proceed
        val reason = when {
            cert == null -> "SSL 证书无效"
            else -> when (advisor.evaluate(cert, CertKind.SERVER)) {
                CertVerdict.EXPIRED -> "服务端证书已过期（${cert.notAfter}）"
                else -> "SSL 证书校验失败${errorKind?.let { "（$it）" } ?: ""}"
            }
        }
        return SslTrustPolicy.Decision.AskUser(reason)
    }

    override fun grantSessionExemption(host: String) {
        exemptions.add(host)
    }

    override fun hasSessionExemption(host: String): Boolean = exemptions.contains(host)
}
