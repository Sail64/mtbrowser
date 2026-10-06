package cn.ptdocs.librechatapp.ui.home

import android.app.Activity
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import cn.ptdocs.librechatapp.R
import cn.ptdocs.librechatapp.domain.SiteRepository
import cn.ptdocs.librechatapp.domain.model.Site
import java.net.URI

/**
 * 主页：原生书签列表 + 空态引导。
 * 只负责渲染与用户交互，数据全部来自 SiteRepository。
 */
class HomeView(
    private val activity: Activity,
    private val repository: SiteRepository,
    private val container: LinearLayout,
    private val emptyHint: TextView,
    private val onOpenSite: (Site) -> Unit
) {

    fun render() {
        container.removeAllViews()
        val sites = repository.all()
        emptyHint.visibility = if (sites.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        sites.forEach { site -> container.addView(buildItem(site)) }
    }

    private fun buildItem(site: Site): LinearLayout {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val icon = TextView(activity).apply {
            val host = try { URI(site.url).host } catch (e: Exception) { null } ?: site.name
            text = host.removePrefix("www.").take(1).uppercase()
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_site_icon)
        }
        val iconParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            marginEnd = dp(12)
        }

        val nameView = TextView(activity).apply {
            text = site.name
            setTextColor(Color.parseColor("#222222"))
            textSize = 16f
        }
        val urlView = TextView(activity).apply {
            text = site.url
            setTextColor(Color.parseColor("#888888"))
            textSize = 12f
            maxLines = 1
        }
        val textColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(nameView)
            addView(urlView)
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(10), dp(4), dp(10))
            setBackgroundResource(android.R.drawable.list_selector_background)
            isClickable = true
            isFocusable = true
            addView(icon, iconParams)
            addView(textColumn, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ))

            setOnClickListener { onOpenSite(site) }
            setOnLongClickListener {
                showItemMenu(site)
                true
            }
        }
    }

    private fun showItemMenu(site: Site) {
        val items = arrayOf("打开", "编辑", "删除")
        android.app.AlertDialog.Builder(activity)
            .setTitle(site.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> onOpenSite(site)
                    1 -> SiteEditorDialog.show(activity, repository, site) { render() }
                    2 -> confirmRemove(site)
                }
            }
            .show()
    }

    private fun confirmRemove(site: Site) {
        android.app.AlertDialog.Builder(activity)
            .setTitle("删除书签")
            .setMessage("确定删除「${site.name}」吗？")
            .setPositiveButton("删除") { _, _ ->
                repository.remove(site.id)
                Toast.makeText(activity, "已删除", Toast.LENGTH_SHORT).show()
                render()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
