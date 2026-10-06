package cn.ptdocs.librechatapp.ui

import android.view.View
import android.webkit.WebView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 沉浸式滚动：向下滚动隐藏底部工具栏与状态栏，向上滚动或回到页顶恢复。
 * 独立类封装，工具栏/状态栏联动策略要改只动这里。
 */
class ImmersiveScrollHelper(
    private val activity: android.app.Activity,
    private val chromeView: View,
    private val thresholdPx: Int
) {

    private var lastScrollY = 0
    private var lastToggleAt = 0L
    private var immersive = false
    private var enabled = true

    private val throttleMs = 100L

    fun attach(webView: WebView) {
        webView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (!enabled) return@setOnScrollChangeListener
            val now = System.currentTimeMillis()
            if (now - lastToggleAt < throttleMs) {
                lastScrollY = scrollY
                return@setOnScrollChangeListener
            }
            val delta = scrollY - lastScrollY
            lastScrollY = scrollY

            when {
                scrollY <= thresholdPx -> setImmersive(false)
                delta > 0 -> setImmersive(true)
                delta < 0 -> setImmersive(false)
            }
            lastToggleAt = now
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) setImmersive(false)
    }

    fun setImmersive(value: Boolean) {
        if (immersive == value) return
        immersive = value
        chromeView.visibility = if (value) View.GONE else View.VISIBLE

        val window = (activity as? android.app.Activity)?.window ?: return
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (value) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            WindowCompat.setDecorFitsSystemWindows(window, false)
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }
}
