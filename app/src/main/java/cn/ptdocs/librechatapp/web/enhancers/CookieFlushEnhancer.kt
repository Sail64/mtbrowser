package cn.ptdocs.librechatapp.web.enhancers

import android.app.Activity
import android.webkit.CookieManager
import android.webkit.WebView
import cn.ptdocs.librechatapp.domain.model.Site
import cn.ptdocs.librechatapp.web.PageEnhancer

/** 所有站点：页面加载完成后落盘 Cookie。 */
class CookieFlushEnhancer : PageEnhancer {
    override val id = "cookie_flush"
    override fun appliesTo(url: String, site: Site?) = true
    override fun onPageFinished(activity: Activity, view: WebView, url: String, site: Site?) {
        CookieManager.getInstance().flush()
    }
}
