package cn.tobe.mtbrowser.domain

import android.content.Context

/**
 * 主框架域名白名单：名单数据只存在于本地配置文件
 * `assets/domain_whitelist.txt`（已被 .gitignore 排除，不入库、不随源码分发，
 * 但会打进本机构建的 APK）。文件缺失时白名单为空，所有 http/https 主框架
 * 导航均被拦截。
 *
 * 文件规则：
 * - 每行一条域名，`#` 开头为注释，空行忽略
 * - `*.a.com` 匹配 a.com 及其任意子域
 * - `a.com` 仅精确匹配该域
 * - 仅约束 http/https 主框架导航；about:、data: 等内部地址不拦
 */
class DomainWhitelist(private val context: Context) {

    private val entries: List<String> by lazy { loadEntries() }

    /** @return 是否允许访问该 URL 的主框架。 */
    fun allows(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val uri = try {
            java.net.URI(url)
        } catch (e: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return true
        // 去掉尾部根点：chromium 会把 "a.com." 规范化为 "a.com"，此处保持一致避免误拦
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        return entries.any { matches(host, it) }
    }

    private fun loadEntries(): List<String> = try {
        context.assets.open(FILE).bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.lowercase() }
    } catch (e: Exception) {
        emptyList()
    }

    private fun matches(host: String, entry: String): Boolean {
        val e = entry.lowercase()
        return if (e.startsWith("*.")) {
            val base = e.substring(2)
            host == base || host.endsWith(".$base")
        } else {
            host == e
        }
    }

    companion object {
        private const val FILE = "domain_whitelist.txt"
    }
}
