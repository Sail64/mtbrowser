package cn.tobe.mtbrowser.web

import android.app.Activity
import android.webkit.WebView
import cn.tobe.mtbrowser.domain.model.Site

/**
 * 页面增强器：onPageFinished 的所有行为都走注册表。
 * 新增页面行为 = 实现本接口并注册，BrowserWebViewClient 永不膨胀。
 */
interface PageEnhancer {
    val id: String

    /** 是否对该页面生效。 */
    fun appliesTo(url: String, site: Site?): Boolean

    fun onPageFinished(activity: Activity, view: WebView, url: String, site: Site?)
}
