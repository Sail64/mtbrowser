package cn.tobe.mtbrowser.web

import android.app.Activity
import android.webkit.WebView

/** 一个浏览器标签：持有独立的 WebView 实例与展示元数据。 */
class Tab(
    val id: Long,
    val webView: WebView,
    var title: String,
    var url: String
) {
    /** 最近使用时间戳：仅供 LRU 淘汰比较，不影响列表展示顺序。 */
    var lastUsedAt: Long = System.currentTimeMillis()
}

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

    /**
     * 当前标签；tabs 列表保持打开（创建）顺序——概览层展示顺序稳定，
     * LRU 淘汰依据 Tab.lastUsedAt 而非列表位置。
     */
    var current: Tab? = null
        private set

    fun tabs(): List<Tab> = tabs.toList()

    fun newTab(url: String? = null): Tab {
        // 超上限时淘汰最近最少使用的（current 排除）
        while (tabs.size >= maxTabs) {
            val victim = tabs.filter { it !== current }.minByOrNull { it.lastUsedAt } ?: break
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
        tab.lastUsedAt = System.currentTimeMillis()
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
        tab.lastUsedAt = System.currentTimeMillis()
    }

    fun currentWebView(): WebView? = current?.webView
}
