package cn.tobe.mtbrowser.ui

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import cn.tobe.mtbrowser.web.Tab
import cn.tobe.mtbrowser.web.TabManager

/**
 * 标签概览层：原生列表（标题 + URL + 关闭），底部「＋ 新建标签」。
 * 长按标签可关闭其他。只负责渲染与交互，标签数据全部来自 TabManager。
 */
class TabSwitcher(
    private val activity: Activity,
    private val root: LinearLayout,
    private val listContainer: LinearLayout,
    private val manager: TabManager,
    private val onSelect: (Tab) -> Unit,
    private val onNewTab: () -> Unit,
    private val onAllClosed: () -> Unit
) {

    private var visible = false

    fun isVisible(): Boolean = visible

    fun toggle() {
        if (visible) hide() else show()
    }

    fun show() {
        visible = true
        render()
        root.visibility = View.VISIBLE
    }

    fun hide() {
        visible = false
        root.visibility = View.GONE
    }

    fun render() {
        listContainer.removeAllViews()
        val currentId = manager.current?.id
        val tabs = manager.tabs()
        if (tabs.isEmpty()) {
            hide()
            onAllClosed()
            return
        }
        tabs.forEach { tab -> listContainer.addView(buildItem(tab, tab.id == currentId)) }
    }

    private fun buildItem(tab: Tab, isCurrent: Boolean): View {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val title = TextView(activity).apply {
            text = tab.title.ifBlank { tab.url.ifBlank { "新标签页" } }
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 1
        }
        val url = TextView(activity).apply {
            text = tab.url.ifBlank { "空白页" }
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 12f
            maxLines = 1
        }
        val textColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title)
            addView(url)
        }

        val close = TextView(activity).apply {
            text = "✕"
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(4), dp(8))
            setOnClickListener { closeTab(tab) }
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(4), dp(12))
            setBackgroundColor(if (isCurrent) Color.parseColor("#3A4A5A") else Color.parseColor("#333333"))
            isClickable = true
            isFocusable = true
            addView(textColumn, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ))
            addView(close)

            setOnClickListener { selectTab(tab) }
            setOnLongClickListener {
                manager.closeOthers(tab)
                Toast.makeText(activity, "已关闭其他标签", Toast.LENGTH_SHORT).show()
                render()
                true
            }
        }
    }

    private fun selectTab(tab: Tab) {
        manager.select(tab)
        hide()
        onSelect(tab)
    }

    private fun closeTab(tab: Tab) {
        val next = manager.close(tab)
        if (next == null) {
            hide()
            onAllClosed()
        } else {
            render()
            onSelect(next)
        }
    }
}
