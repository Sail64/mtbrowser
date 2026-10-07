package cn.tobe.mtbrowser.storage

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

    private const val KEY_FAB_X = "fab_x"
    private const val KEY_FAB_Y = "fab_y"

    /** 全屏浮窗按钮上次的 left/top margin（像素），无记录返回 null。 */
    fun getFabPos(ctx: Context): Pair<Int, Int>? {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (!p.contains(KEY_FAB_X)) return null
        return p.getInt(KEY_FAB_X, 0) to p.getInt(KEY_FAB_Y, 0)
    }

    fun setFabPos(ctx: Context, x: Int, y: Int) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_FAB_X, x)
            .putInt(KEY_FAB_Y, y)
            .apply()
    }
}
