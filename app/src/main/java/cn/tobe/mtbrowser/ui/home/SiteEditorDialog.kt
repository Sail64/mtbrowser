package cn.tobe.mtbrowser.ui.home

import android.app.Activity
import android.app.AlertDialog
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import cn.tobe.mtbrowser.data.PrefSiteRepository
import cn.tobe.mtbrowser.domain.SiteRepository
import cn.tobe.mtbrowser.domain.model.Site

/** 添加 / 编辑书签对话框。URL 输入自动补 https:// 前缀（规范化集中于此一处）。 */
object SiteEditorDialog {

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    fun show(
        activity: Activity,
        repository: SiteRepository,
        existing: Site?,
        prefillName: String = "",
        prefillUrl: String = "",
        onSaved: () -> Unit
    ) {
        val margin = (20 * activity.resources.displayMetrics.density).toInt()

        val nameInput = EditText(activity).apply {
            hint = "名称（留空则使用域名）"
            setText(existing?.name ?: prefillName)
        }
        val urlInput = EditText(activity).apply {
            hint = "地址，例如 example.com:8443"
            setText(existing?.url ?: prefillUrl)
        }

        fun layoutParams(top: Int = 0) = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = margin
            rightMargin = margin
            topMargin = top
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(nameInput, layoutParams())
            addView(urlInput, layoutParams(12.dp(activity)))
        }

        AlertDialog.Builder(activity)
            .setTitle(if (existing == null) "添加书签" else "编辑书签")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val url = normalizeUrl(urlInput.text.toString())
                if (url.isEmpty()) return@setPositiveButton
                val name = nameInput.text.toString().trim()
                    .ifBlank { PrefSiteRepository.hostOf(url) ?: url }
                if (existing == null) {
                    repository.add(name, url)
                } else {
                    repository.update(existing.copy(name = name, url = url))
                }
                onSaved()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun Int.dp(activity: Activity): Int =
        (this * activity.resources.displayMetrics.density).toInt()
}
