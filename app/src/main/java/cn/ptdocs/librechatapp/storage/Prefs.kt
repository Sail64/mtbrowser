package cn.ptdocs.librechatapp.storage

import android.content.Context

/** 轻量元数据（不属于任何 Site 的全局状态）。 */
object Prefs {
    private const val FILE = "mtls_webview"
    private const val KEY_LAST_URL = "last_url"

    fun getLastUrl(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LAST_URL, null)

    fun setLastUrl(ctx: Context, url: String?) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_URL, url)
            .apply()
    }
}
