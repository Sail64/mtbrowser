package cn.tobe.mtbrowser.web

import android.app.Activity
import android.webkit.WebView

/** 一个浏览器标签：持有独立的 WebView 实例与展示元数据。 */
class Tab(
    val id: Long,
    val webView: WebView,
    var title: String,
    var url: String
)

/**
 * 标签管理器：每个标签一个独立 WebView（真池，非 save/restore 假切换），
 * 超过 [maxTabs] 时按 LRU 淘汰最久未用的标签。
 * 只管生命周期与索引，不掺业务逻辑；WebView 的装配由 [createWebView] 工厂回调完成。
 */
class TabManager(
    private val createWebView: () -> WebView,
    private val maxTabs: Int = 8
) {

    private val tabs = mutableListOf<Tab>()
    private var nextId = 1L

    /** 切换/加载时更新最近使用顺序用：tabs[0] 最久未用，tabs.last 最新。 */
    var current: Tab? = null
        private set

    fun tabs(): List<Tab> = tabs.toList()

    fun newTab(url: String? = null): Tab {
        // 超 LRU 上限时先淘汰最久未用的（current 排除）
        while (tabs.size >= maxTabs) {
            val victim = tabs.firstOrNull { it !== current } ?: break
            closeTab(victim)
        }
        val tab = Tab(nextId++, createWebView(), "", url.orEmpty())
        tabs.add(tab)
        if (url != null) tab.webView.loadUrl(url)
        current = tab
        return tab
    }
    fun select(tab: Tab) {
        if (!tabs.contains(tab)) return
        current = tab
        touch(tab)
    }

    /** 最近使用排序：tabs.last 最新，tabs.first 最久未用（LRU 淘汰依据）。 */
    private fun touch(tab: Tab) {
        tabs.remove(tab)
        tabs.add(tab)
    }

    /**
     * 关闭标签；返回应成为当前标签的相邻标签（优先右侧），全部关完返回 null。
     * 会被 [newTab] 的 LRU 淘汰路径复用。
     */
    fun close(tab: Tab): Tab? = closeTab(tab)

    private fun closeTab(tab: Tab): Tab? {
        val idx = tabs.indexOf(tab)
        if (idx < 0) return current
        tabs.removeAt(idx)
        if (tab === current) {
            current = tabs.getOrNull(idx) ?: tabs.getOrNull(idx - 1)
        }
        tab.webView.destroy()
        return current
    }

    fun closeOthers(tab: Tab) {
        tabs.toList().forEach { if (it !== tab) closeTab(it) }
        current = tab
    }

    fun currentWebView(): WebView? = current?.webView
}
