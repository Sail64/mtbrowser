package cn.ptdocs.librechatapp.platform

import android.app.Activity
import android.security.KeyChain
import android.util.Log
import cn.ptdocs.librechatapp.data.PrefClientCertStore
import cn.ptdocs.librechatapp.domain.ClientCertSelector
import java.security.Principal

/**
 * ClientCertSelector 的 KeyChain 实现：缓存职责交给 PrefClientCertStore，
 * 系统证书选择器交互在这里被隔离。
 */
class KeyChainCertSelector(
    private val activity: Activity,
    private val store: PrefClientCertStore
) : ClientCertSelector {

    companion object {
        private const val TAG = "KeyChainCertSelector"
    }

    override fun cachedAlias(host: String): String? = store.cachedAlias(host)

    override fun saveAlias(host: String, alias: String) = store.saveAlias(host, alias)

    override fun clearAlias(host: String) = store.clearAlias(host)

    override fun chooseAlias(
        host: String,
        port: Int,
        keyTypes: Array<String>,
        principals: Array<Principal?>,
        callback: (String?) -> Unit
    ) {
        Log.d(TAG, "Prompting for client cert alias: host=$host port=$port")
        KeyChain.choosePrivateKeyAlias(
            activity,
            { alias ->
                Log.d(TAG, "Alias selection result: ${alias ?: "null"}")
                if (alias != null) {
                    store.saveAlias(host, alias)
                }
                callback(alias)
            },
            keyTypes,
            principals,
            host,
            port,
            null
        )
    }
}
