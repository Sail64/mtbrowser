package cn.tobe.mtbrowser.web.enhancers

import android.app.Activity
import android.webkit.WebView
import cn.tobe.mtbrowser.domain.model.Site
import cn.tobe.mtbrowser.web.PageEnhancer
import cn.tobe.mtbrowser.web.RenameFocusGuard

/** 仅对 renameFocusFix = true 的站点生效的 LibreChat 键盘 workaround。 */
class RenameFocusEnhancer : PageEnhancer {
    override val id = "rename_focus_fix"
    override fun appliesTo(url: String, site: Site?) = site?.renameFocusFix == true
    override fun onPageFinished(activity: Activity, view: WebView, url: String, site: Site?) {
        RenameFocusGuard.inject(view)
    }
}
