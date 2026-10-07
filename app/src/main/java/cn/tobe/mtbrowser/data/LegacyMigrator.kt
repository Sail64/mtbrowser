package cn.tobe.mtbrowser.data

import android.content.Context
import cn.tobe.mtbrowser.domain.model.Site

/**
 * 一次性迁移：把旧版「全局 base_url + 全局 alias + 全局 rename_focus_fix」
 * 转成首条 Site + per-host alias。旧 key 只读一次，之后不再读写。
 */
object LegacyMigrator {

    private const val FILE = "mtls_webview"
    private const val OLD_BASE_URL = "base_url"
    private const val OLD_ALIAS = "keychain_alias"
    private const val OLD_RENAME_FIX = "rename_focus_fix"
    private const val FLAG_MIGRATED = "legacy_migrated_v1"

    fun migrateIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (prefs.getBoolean(FLAG_MIGRATED, false)) return

        val baseUrl = prefs.getString(OLD_BASE_URL, null)?.trim()
        val sites = PrefSiteRepository(context)

        if (!baseUrl.isNullOrEmpty() && sites.findByUrl(baseUrl) == null) {
            val site = sites.add(
                name = "LibreChat",
                url = baseUrl,
                renameFocusFix = prefs.getBoolean(OLD_RENAME_FIX, true)
            )
            // 全局 alias 迁移到 per-host 存储，老用户的证书选择不丢
            val oldAlias = prefs.getString(OLD_ALIAS, null)
            val host = PrefSiteRepository.hostOf(baseUrl)
            if (!oldAlias.isNullOrEmpty() && host != null) {
                PrefClientCertStore(context).saveAlias(host, oldAlias)
            }
        }

        prefs.edit().putBoolean(FLAG_MIGRATED, true).apply()
    }
}
