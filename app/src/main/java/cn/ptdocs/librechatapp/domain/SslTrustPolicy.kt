package cn.ptdocs.librechatapp.domain

import cn.ptdocs.librechatapp.domain.model.CertInfo

/**
 * SSL 放行策略：谁放行、放行多久，只有这一个地方说了算。
 * 会话级豁免仅保存在内存中，进程结束即失效（正派：不给永久跳过校验的机会）。
 */
interface SslTrustPolicy {
    sealed interface Decision {
        /** 无条件放行（如该 host 已有会话豁免）。 */
        data object Proceed : Decision

        /** 交给用户决策。 */
        data class AskUser(val reason: String) : Decision

        /** 直接拦截。 */
        data object Deny : Decision
    }

    fun onError(host: String, cert: CertInfo?, errorKind: String?): Decision
    fun grantSessionExemption(host: String)
    fun hasSessionExemption(host: String): Boolean
}
