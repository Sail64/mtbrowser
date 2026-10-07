package cn.tobe.mtbrowser.domain

import cn.tobe.mtbrowser.domain.model.NavigationDecision
import java.net.URI

/**
 * 导航策略：一个 URL 应该在哪里打开，只有这一处决策。
 * http/https（含无法解析的兜底）一律应用内加载，享受 mTLS 与证书提醒能力；
 * 其他 scheme（mailto:、intent: 等）交给系统。
 */
interface NavigationPolicy {
    fun decide(url: String, currentSiteUrl: String?): NavigationDecision
}

class DefaultNavigationPolicy : NavigationPolicy {
    override fun decide(url: String, currentSiteUrl: String?): NavigationDecision {
        val scheme = try {
            URI(url).scheme?.lowercase()
        } catch (e: Exception) {
            null
        }
        return when (scheme) {
            "http", "https", null -> NavigationDecision.OPEN_IN_APP
            else -> NavigationDecision.OPEN_EXTERNAL
        }
    }
}
