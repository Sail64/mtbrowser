package cn.tobe.mtbrowser.domain

import java.security.Principal

/**
 * 客户端证书选择：按 host 隔离的证书缓存与选择入口。
 * KeyChain 等平台细节由实现隐藏，业务层只面向该接口。
 */
interface ClientCertSelector {
    fun cachedAlias(host: String): String?
    fun saveAlias(host: String, alias: String)
    fun clearAlias(host: String)
    fun chooseAlias(
        host: String,
        port: Int,
        keyTypes: Array<String>,
        principals: Array<Principal?>,
        callback: (String?) -> Unit
    )
}
