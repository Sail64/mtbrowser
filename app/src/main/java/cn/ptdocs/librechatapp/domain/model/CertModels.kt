package cn.ptdocs.librechatapp.domain.model

import java.util.Date

/** 证书类别：客户端证书（mTLS 出示的）或服务端证书。 */
enum class CertKind { CLIENT, SERVER }

/** 证书健康度结论。 */
enum class CertVerdict { VALID, EXPIRING, EXPIRED }

/** 证书快照：屏蔽具体证书 API 差异（X509Certificate / SslCertificate）。 */
data class CertInfo(
    val subject: String?,
    val notAfter: Date
)

/** 导航决策：一个 URL 应该在哪里打开。 */
enum class NavigationDecision { OPEN_IN_APP, OPEN_EXTERNAL }
