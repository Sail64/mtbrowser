package cn.tobe.mtbrowser.ui

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import cn.tobe.mtbrowser.R
import cn.tobe.mtbrowser.web.Tab
import cn.tobe.mtbrowser.web.TabManager

/**
 * 标签切换器：半透明遮罩上的浅色圆角浮动面板（标题 + URL + 关闭），
 * 底部「＋ 新建标签」，点击遮罩空白处收起。长按标签可关闭其他。
 * 只负责渲染与交互，标签数据全部来自 TabManager。
 */
class TabSwitcher(
    private val activity: Activity,
    private val root: View,
    private val listContainer: LinearLayout,
    private val manager: TabManager,
    private val onSelect: (Tab) -> Unit,
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
        // render 发现列表为空时会收起并回调 onAllClosed，此处不能再亮出空面板
        if (!visible) return
        root.visibility = View.VISIBLE
        // 点击遮罩空白处收起；面板本身消费点击，避免透传
        root.setOnClickListener { hide() }
        root.findViewById<View>(R.id.tab_panel).isClickable = true
    }

    fun hide() {
        visible = false
        root.visibility = View.GONE
    }

    /** 面板高度校准：列表变化后（打开、关闭、长按关其他）重新约束到约 55% 屏高。 */
    private fun capPanelHeight() {
        root.post {
            val scroll = root.findViewById<View>(R.id.tab_list_scroll) ?: return@post
            if (root.width == 0 || root.height == 0) return@post
            // 用 ScrollView 自身已布局宽度测量（root 宽度含面板左右 24dp 边距）
            val measureWidth = if (scroll.width > 0) scroll.width else root.width
            scroll.measure(
                View.MeasureSpec.makeMeasureSpec(measureWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val maxH = (root.height * 0.55f).toInt()
            scroll.layoutParams.height = minOf(scroll.measuredHeight, maxH)
            scroll.requestLayout()
        }
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
        capPanelHeight()
    }

    private fun buildItem(tab: Tab, isCurrent: Boolean): View {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val title = TextView(activity).apply {
            text = tab.title.ifBlank { tab.url.ifBlank { "新标签页" } }
            setTextColor(Color.parseColor("#222222"))
            textSize = 15f
            maxLines = 1
        }
        val url = TextView(activity).apply {
            text = tab.url.ifBlank { "空白页" }
            setTextColor(Color.parseColor("#888888"))
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
            setTextColor(Color.parseColor("#999999"))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(4), dp(8))
            setOnClickListener { closeTab(tab) }
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(4), dp(12))
            background = if (isCurrent) {
                ContextCompat.getDrawable(activity, R.drawable.bg_tab_item_current)
            } else {
                // 未选中不加任何高亮背景
                null
            }
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
                // 幸存者（含被长按的标签）必须挂载：原当前标签的 WebView 已被销毁
                manager.current?.let { onSelect(it) }
                true
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) }
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
