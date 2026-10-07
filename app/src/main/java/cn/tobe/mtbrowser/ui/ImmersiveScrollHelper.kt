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

    /** 工具栏隐藏/恢复导致 WebView 区域尺寸变化后通知（如浮动按钮重入界）。 */
    var onBarsVisibilityChanged: (() -> Unit)? = null

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
        val target = if (hide) View.GONE else View.VISIBLE
        var changed = false
        bars.forEach {
            if (it.visibility != target) {
                it.visibility = target
                changed = true
            }
        }
        if (changed) onBarsVisibilityChanged?.invoke()
    }

    private fun applySystemBars() {
        val window = activity.window ?: return
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val hide = immersive || fullscreen
        if (hide) {
            // 先放开 fits，再隐藏；behavior 允许全屏中下滑临时呼出系统栏
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            // 兼容回退：部分系统上 WindowInsetsControllerCompat.hide 不生效，直接置经典 flag
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }
}
