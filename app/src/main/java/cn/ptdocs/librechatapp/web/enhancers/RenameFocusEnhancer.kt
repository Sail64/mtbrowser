package cn.ptdocs.librechatapp.web.enhancers

import android.app.Activity
import android.webkit.WebView
import cn.ptdocs.librechatapp.domain.model.Site
import cn.ptdocs.librechatapp.web.PageEnhancer
import cn.ptdocs.librechatapp.web.RenameFocusGuard

/** 仅对 renameFocusFix = true 的站点生效的 LibreChat 键盘 workaround。 */
class RenameFocusEnhancer : PageEnhancer {
    override val id = "rename_focus_fix"
    override fun appliesTo(url: String, site: Site?) = site?.renameFocusFix == true
    override fun onPageFinished(activity: Activity, view: WebView, url: String, site: Site?) {
        RenameFocusGuard.inject(view)
    }
}
