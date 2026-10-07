package cn.tobe.mtbrowser.ui

import android.app.Activity
import android.view.View

/**
 * 沉浸式全屏控制：全屏时仅隐藏应用顶部操作栏（进度条随行隐藏），
 * 不改动窗口布局与系统栏——状态栏/导航栏保持可见，图标保留；
 * 状态栏背景色由外部按页面背景色渲染，实现与页面视觉一致。
 */
class ImmersiveController(
    private val bars: List<View>
) {

    private var fullscreen = false

    /** 操作栏显隐变化后通知（用于浮动按钮重钳入界、进度条显隐重设等）。 */
    var onBarsVisibilityChanged: (() -> Unit)? = null

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
    }

    private fun applyBars() {
        val target = if (fullscreen) View.GONE else View.VISIBLE
        var changed = false
        bars.forEach {
            if (it.visibility != target) {
                it.visibility = target
                changed = true
            }
        }
        if (changed) onBarsVisibilityChanged?.invoke()
    }
}
