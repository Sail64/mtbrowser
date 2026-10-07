package cn.tobe.mtbrowser.ui

import android.app.Activity
import android.view.View
import android.webkit.WebView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 沉浸式控制：
 * - 滚动联动：向下滚动隐藏工具栏与状态栏，向上滚动或回到页顶恢复（每标签 WebView 需各自 attach）；
 * - 全屏切换：隐藏全部工具栏 + 状态栏 + 导航栏（沉浸式），浮动按钮调用 [toggleFullscreen]。
 * 独立类封装，联动策略要改只动这里。
 */
class ImmersiveScrollHelper(
    private val activity: Activity,
    private val bars: List<View>,
    private val thresholdPx: Int
) {

    private var lastScrollY = 0
    private var lastToggleAt = 0L
    private var immersive = false
    private var fullscreen = false
    private var enabled = true

    private val throttleMs = 100L

    /** 每个标签的 WebView 创建后调用一次。 */
    fun attach(webView: WebView) {
        webView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (!enabled || fullscreen) return@setOnScrollChangeListener
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
        if (!value) setFullscreen(false)
    }

    fun isFullscreen(): Boolean = fullscreen

    /** @return 切换后的全屏状态。 */
    fun toggleFullscreen(): Boolean {
        setFullscreen(!fullscreen)
        return fullscreen
    }

    fun setFullscreen(value: Boolean) {
        if (fullscreen == value) return
        fullscreen = value
        applyBars()
        applySystemBars()
        if (!value) immersive = false
    }

    private fun setImmersive(value: Boolean) {
        if (immersive == value || fullscreen) return
        immersive = value
        applyBars()
        applySystemBars()
    }

    private fun applyBars() {
        val hide = immersive || fullscreen
        bars.forEach { it.visibility = if (hide) View.GONE else View.VISIBLE }
    }

    private fun applySystemBars() {
        val window = activity.window ?: return
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val hide = immersive || fullscreen
        if (hide) {
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            WindowCompat.setDecorFitsSystemWindows(window, false)
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }
}
