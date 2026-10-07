package cn.tobe.mtbrowser

import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.webkit.WebView
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.net.Uri
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import cn.tobe.mtbrowser.data.PrefSiteRepository
import cn.tobe.mtbrowser.di.AppGraph
import cn.tobe.mtbrowser.domain.model.Site
import cn.tobe.mtbrowser.platform.KeyChainCertSelector
import cn.tobe.mtbrowser.storage.Prefs
import kotlin.math.abs
import cn.tobe.mtbrowser.ui.ImmersiveController
import cn.tobe.mtbrowser.ui.TabSwitcher
import cn.tobe.mtbrowser.ui.home.HomeView
import cn.tobe.mtbrowser.ui.home.SiteEditorDialog
import cn.tobe.mtbrowser.web.AppWebChromeClient
import cn.tobe.mtbrowser.web.BrowserWebViewClient
import cn.tobe.mtbrowser.web.DownloadHandler
import cn.tobe.mtbrowser.web.TabManager
import cn.tobe.mtbrowser.web.Tab
import cn.tobe.mtbrowser.web.WebViewConfigurator
import cn.tobe.mtbrowser.web.enhancers.CookieFlushEnhancer
import cn.tobe.mtbrowser.web.enhancers.ServerCertCheckEnhancer

class MainActivity : AppCompatActivity() {

    private lateinit var homeContainer: View
    private lateinit var browserContainer: LinearLayout
    private lateinit var webSlot: ViewGroup
    private lateinit var topBar: View
    private lateinit var addressBar: TextView
    private lateinit var btnStar: TextView
    private lateinit var btnTabs: TextView
    private lateinit var btnFullscreen: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var btnCloseHome: TextView
    private lateinit var homeView: HomeView
    private lateinit var immersive: ImmersiveController
    private lateinit var certSelector: KeyChainCertSelector
    private lateinit var tabManager: TabManager
    private lateinit var tabSwitcher: TabSwitcher

    private var inBrowser = false
    private var homeOverlay = false
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (fileUploadCallback == null) return@registerForActivityResult
        val results: Array<Uri>? = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        fileUploadCallback?.onReceiveValue(results)
        fileUploadCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(this)
        certSelector = KeyChainCertSelector(this, AppGraph.clientCertStore)

        configureStatusBar()
        setContentView(R.layout.activity_main)

        homeContainer = findViewById(R.id.home_container)
        browserContainer = findViewById(R.id.browser_container)
        webSlot = findViewById(R.id.web_slot)
        topBar = findViewById(R.id.top_bar)
        addressBar = findViewById(R.id.address_bar)
        btnStar = findViewById(R.id.btn_star)
        btnTabs = findViewById(R.id.btn_tabs)
        btnFullscreen = findViewById(R.id.btn_fullscreen)
        progressBar = findViewById(R.id.progress_bar)
        btnCloseHome = findViewById(R.id.btn_close_home)
        btnCloseHome.setOnClickListener { closeHomeOverlay() }

        immersive = ImmersiveController(listOf(topBar, progressBar))

        setupTabs()
        setupHome()
        setupBrowser()

        // 恢复：优先 restoreState（转屏/进程回收），否则打开上次访问的站点
        val restoredView = savedInstanceState?.getBoolean(KEY_IN_BROWSER, false) ?: false
        val restored = (savedInstanceState?.getBundle(KEY_WEBVIEW_STATE)?.let {
            val tab = tabManager.newTab()
            tab.webView.restoreState(it)
        } != null) && restoredView
        if (restored) {
            tabManager.current?.let { attachTab(it) }
            showBrowser(updateHomeList = false)
        } else {
            Prefs.getLastUrl(this)
                ?.let { PrefSiteRepository.hostOf(it) }
                ?.let { AppGraph.siteRepository.byHost(it) }
                ?.let { openSite(it) }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    immersive.isFullscreen() -> exitFullscreen()
                    inBrowser && tabSwitcher.isVisible() -> tabSwitcher.hide()
                    inBrowser && homeContainer.visibility == View.VISIBLE -> closeHomeOverlay()
                    inBrowser && currentWebView()?.canGoBack() == true -> currentWebView()?.goBack()
                    inBrowser -> showHome()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    // ---------- 主页 ----------

    private fun setupHome() {
        homeView = HomeView(
            activity = this,
            repository = AppGraph.siteRepository,
            container = findViewById(R.id.site_list),
            emptyHint = findViewById(R.id.empty_hint)
        ) { site -> openSite(site) }

        findViewById<TextView>(R.id.btn_add_site).setOnClickListener {
            SiteEditorDialog.show(this, AppGraph.siteRepository, null) { homeView.render() }
        }
        homeView.render()
    }

    private fun showHome(overlay: Boolean = false) {
        exitFullscreen()
        // 离开网页，状态栏恢复主题配色
        configureStatusBar()
        if (overlay) {
            // 覆盖层模式：从浏览会话进入，会话保持，✕/返回键回原页面
            inBrowser = true
            homeOverlay = true
            btnCloseHome.visibility = View.VISIBLE
        } else {
            // 独立模式：应用启动入口，返回键退出应用
            inBrowser = false
            homeOverlay = false
            btnCloseHome.visibility = View.GONE
        }
        homeContainer.visibility = View.VISIBLE
        browserContainer.visibility = View.GONE
        homeView.render()
    }

    private fun closeHomeOverlay() {
        homeOverlay = false
        btnCloseHome.visibility = View.GONE
        showBrowser(updateHomeList = false)
    }

    private fun showBrowser(updateHomeList: Boolean = true) {
        inBrowser = true
        homeOverlay = false
        homeContainer.visibility = View.GONE
        browserContainer.visibility = View.VISIBLE
        if (updateHomeList) homeView.render()
        btnFullscreen.post { restoreFabPosition() }
    }

    fun openSite(site: Site) {
        Prefs.setLastUrl(this, site.url)
        showBrowser()
        ensureCurrentTab().loadUrl(site.url)
    }

    // ---------- 标签 ----------

    private fun setupTabs() {
        tabManager = TabManager(createWebView = { createTabWebView() })
        tabSwitcher = TabSwitcher(
            activity = this,
            root = findViewById(R.id.tab_switcher),
            listContainer = findViewById(R.id.tab_list),
            manager = tabManager,
            onSelect = { attachTab(it) },
            onAllClosed = { showHome() }
        )
        findViewById<View>(R.id.btn_new_tab).setOnClickListener {
            // 新标签 → 引导到书签选择：挂载空白页后切主页覆盖层，挑书签或按 ✕ 留在空白页
            attachTab(tabManager.newTab())
            tabSwitcher.hide()
            showHome(overlay = true)
        }
        btnTabs.setOnClickListener { tabSwitcher.toggle() }
        updateTabsButton()
    }

    private fun createTabWebView(): WebView {
        val wv = WebView(this)
        WebViewConfigurator.configure(wv)
        wv.webViewClient = BrowserWebViewClient(
            activity = this,
            selector = certSelector,
            advisor = AppGraph.certAdvisor,
            reminders = AppGraph.reminderScheduler,
            sslTrustPolicy = AppGraph.sslTrustPolicy,
            navigationPolicy = AppGraph.navigationPolicy,
            sites = AppGraph.siteRepository,
            enhancers = listOf(
                CookieFlushEnhancer(),
                ServerCertCheckEnhancer(AppGraph.certAdvisor, AppGraph.reminderScheduler, AppGraph.probeThrottle)
            ),
            onUrlChanged = { url -> runOnUiThread { onTabUrlChanged(wv, url) } },
            onPageBackground = { css -> onTabBackground(wv, css) }
        )
        wv.webChromeClient = AppWebChromeClient(this, onTitle = { title ->
            onTabTitleChanged(wv, title)
        }, onProgress = { progress ->
            onTabProgress(wv, progress)
        })
        DownloadHandler(this).setup(wv)
        return wv
    }

    /** 把标签的 WebView 挂到 web_slot（插到 FAB 之下），保证 slot 内只有当前标签一个 WebView。 */
    private fun attachTab(tab: Tab) {
        tabManager.select(tab)
        if (tab.webView.parent === webSlot) {
            // 已挂载且是当前标签，无需重挂（避免切标签时闪烁）
            updateTabsButton()
            onCurrentTabUiChanged()
            return
        }
        for (i in webSlot.childCount - 1 downTo 0) {
            if (webSlot.getChildAt(i) is WebView) webSlot.removeViewAt(i)
        }
        webSlot.addView(
            tab.webView, 0,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        updateTabsButton()
        onCurrentTabUiChanged()
        updateProgressBar(tab.webView.progress)
    }

    private fun ensureCurrentTab(): WebView {
        val tab = tabManager.current ?: tabManager.newTab().also { attachTab(it) }
        if (tab.webView.parent !== webSlot) attachTab(tab)
        return tab.webView
    }

    private fun currentWebView(): WebView? = tabManager.currentWebView()

    private fun onTabUrlChanged(webView: WebView, url: String) {
        tabManager.tabs().firstOrNull { it.webView === webView }?.let { tab ->
            tab.url = url
            tab.title = webView.title.orEmpty()
        }
        if (webView === currentWebView()) onCurrentTabUiChanged(url)
    }

    private fun onTabTitleChanged(webView: WebView, title: String) {
        tabManager.tabs().firstOrNull { it.webView === webView }?.title = title
    }

    /** 进度回调只对当前标签生效，切换标签时按该标签自己的进度刷新。 */
    private fun onTabProgress(webView: WebView, progress: Int) {
        if (webView === currentWebView()) updateProgressBar(progress)
    }

    private fun updateProgressBar(progress: Int) {
        progressBar.progress = progress
        // 全屏期间一律不显示；退出全屏时 onBarsVisibilityChanged 会按真实进度重设
        progressBar.visibility =
            if (progress in 1..99 && !immersive.isFullscreen()) View.VISIBLE else View.GONE
    }

    private fun onTabBackground(webView: WebView, cssColor: String?) {
        // 仅当前标签、且不在主页覆盖层（主页应保持主题配色）
        if (webView !== currentWebView() || homeOverlay) return
        val color = cssColor?.let { parseCssColor(it) } ?: return
        // 状态栏/导航栏染成页面背景色，图标明暗按背景亮度自适应
        window.statusBarColor = color
        window.navigationBarColor = color
        val lum = (0.299 * android.graphics.Color.red(color) +
            0.587 * android.graphics.Color.green(color) +
            0.114 * android.graphics.Color.blue(color)) / 255.0
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = lum > 0.6
            isAppearanceLightNavigationBars = lum > 0.6
        }
    }

    /** 解析 "rgb(255, 255, 255)" / "rgba(...)" 形式的 CSS 颜色。 */
    private fun parseCssColor(css: String): Int? {
        val m = Regex("rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)").find(css) ?: return null
        val (r, g, b) = m.destructured
        return try {
            android.graphics.Color.rgb(r.toInt(), g.toInt(), b.toInt())
        } catch (e: Exception) {
            null
        }
    }

    /** 当前标签变化后刷新地址栏 / 收藏角标 / 最近 URL。 */
    private fun onCurrentTabUiChanged(url: String? = null) {
        val webView = currentWebView() ?: return
        val currentUrl = url ?: webView.url.orEmpty()
        if (currentUrl.isNotEmpty()) Prefs.setLastUrl(this, currentUrl)
        addressBar.text = currentUrl.ifEmpty { "输入网址或回到主页" }
        val bookmarked = AppGraph.siteRepository.findByUrl(currentUrl) != null
        btnStar.text = if (bookmarked) "★" else "☆"
        updateTabsButton()
    }

    private fun updateTabsButton() {
        btnTabs.text = "▣ ${tabManager.tabs().size}"
    }

    // ---------- 浏览视图控件 ----------

    private fun setupBrowser() {
        findViewById<TextView>(R.id.btn_reload).setOnClickListener { currentWebView()?.reload() }
        findViewById<TextView>(R.id.btn_nav_home).setOnClickListener { showHome(overlay = true) }
        addressBar.setOnClickListener { showAddressInputDialog() }
        btnStar.setOnClickListener { bookmarkCurrentPage() }
        btnFullscreen.setOnClickListener {
            val fullscreen = immersive.toggleFullscreen()
            btnFullscreen.setImageResource(if (fullscreen) R.drawable.ic_collapse else R.drawable.ic_expand)
        }
        setupFabDrag()
    }

    // ---------- 浮动全屏按钮：可拖动并记忆位置 ----------

    private var fabDragged = false
    private var fabLastRawX = 0f
    private var fabLastRawY = 0f
    private val fabTouchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    private fun setupFabDrag() {
        btnFullscreen.setOnTouchListener { v, event -> onFabTouch(v, event) }
        // 布局完成后恢复上次位置（webSlot 尚未布局/不可见时跳过，showBrowser 会再试）
        btnFullscreen.post { restoreFabPosition() }
        // 工具栏显隐会改变 webSlot 尺寸：FAB 重钳入界；进度条按真实加载进度重设显隐
        immersive.onBarsVisibilityChanged = {
            btnFullscreen.post { snapFabIntoBounds() }
            progressBar.post { updateProgressBar(currentWebView()?.progress ?: 0) }
        }
    }

    private fun onFabTouch(v: View, event: MotionEvent): Boolean {
        val lp = v.layoutParams as FrameLayout.LayoutParams
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fabLastRawX = event.rawX
                fabLastRawY = event.rawY
                fabDragged = false
                v.isPressed = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // 只跟随主指针，其余手指参与时坐标会突变导致按钮瞬移
                if (event.pointerCount > 1) return true
                val dx = event.rawX - fabLastRawX
                val dy = event.rawY - fabLastRawY
                if (!fabDragged && (abs(dx) > fabTouchSlop || abs(dy) > fabTouchSlop)) fabDragged = true
                if (fabDragged) {
                    lp.leftMargin += dx.toInt()
                    lp.topMargin += dy.toInt()
                    clampFab(lp)
                    v.layoutParams = lp
                    fabLastRawX = event.rawX
                    fabLastRawY = event.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.isPressed = false
                if (fabDragged) {
                    Prefs.setFabPos(this, lp.leftMargin, lp.topMargin)
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    v.performClick()
                }
                return true
            }
        }
        return false
    }

    /** 把 margin 夹在 webSlot 可用范围内。 */
    private fun clampFab(lp: FrameLayout.LayoutParams) {
        if (webSlot.width <= 0 || webSlot.height <= 0) return
        lp.leftMargin = lp.leftMargin.coerceIn(0, (webSlot.width - btnFullscreen.width).coerceAtLeast(0))
        lp.topMargin = lp.topMargin.coerceIn(0, (webSlot.height - btnFullscreen.height).coerceAtLeast(0))
    }

    private fun restoreFabPosition() {
        if (webSlot.width <= 0 || webSlot.height <= 0) return
        val pos = Prefs.getFabPos(this) ?: return
        val lp = btnFullscreen.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = pos.first
        lp.topMargin = pos.second
        clampFab(lp)
        btnFullscreen.layoutParams = lp
    }

    /** bars 显隐导致 webSlot 尺寸变化后，把 FAB 钳回可视范围并同步存档。 */
    private fun snapFabIntoBounds() {
        if (btnFullscreen.width <= 0) return
        val lp = btnFullscreen.layoutParams as FrameLayout.LayoutParams
        val before = lp.leftMargin to lp.topMargin
        clampFab(lp)
        if (before != lp.leftMargin to lp.topMargin) {
            btnFullscreen.layoutParams = lp
            Prefs.setFabPos(this, lp.leftMargin, lp.topMargin)
        }
    }

    private fun exitFullscreen() {
        if (immersive.isFullscreen()) {
            immersive.toggleFullscreen()
        }
        btnFullscreen.setImageResource(R.drawable.ic_expand)
    }

    private fun showAddressInputDialog() {
        val input = EditText(this)
        input.setText(addressBar.text.toString())
        AlertDialog.Builder(this)
            .setTitle("打开网址")
            .setView(input)
            .setPositiveButton("打开") { _, _ ->
                val url = SiteEditorDialog.normalizeUrl(input.text.toString())
                if (url.isNotEmpty()) {
                    ensureCurrentTab().loadUrl(url)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun bookmarkCurrentPage() {
        val url = currentWebView()?.url ?: return
        val existing = AppGraph.siteRepository.findByUrl(url)
        if (existing != null) {
            SiteEditorDialog.show(this, AppGraph.siteRepository, existing) { onCurrentTabUiChanged(url) }
        } else {
            SiteEditorDialog.show(
                this, AppGraph.siteRepository, null,
                onSaved = { onCurrentTabUiChanged(url) },
                prefillName = currentWebView()?.title.orEmpty(),
                prefillUrl = url
            )
        }
    }

    // ---------- 文件上传 ----------

    fun showFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?
    ): Boolean {
        if (fileUploadCallback != null) {
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = null
        }
        fileUploadCallback = filePathCallback
        val intent = fileChooserParams?.createIntent() ?: return false
        return try {
            fileChooserLauncher.launch(intent)
            true
        } catch (e: Exception) {
            fileUploadCallback = null
            false
        }
    }

    // ---------- 状态栏 ----------

    private fun configureStatusBar() {
        val isDarkTheme = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        window.statusBarColor = if (isDarkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        window.navigationBarColor = if (isDarkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE

        // 挖孔/刘海屏：允许窗口延伸进 cutout 区域，否则全屏时状态栏位置会留一条黑带
        window.attributes = window.attributes.also {
            it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.isAppearanceLightStatusBars = !isDarkTheme
            controller.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    // ---------- 生命周期 ----------

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IN_BROWSER, inBrowser)
        if (inBrowser) {
            val bundle = Bundle()
            currentWebView()?.saveState(bundle)
            outState.putBundle(KEY_WEBVIEW_STATE, bundle)
        }
    }

    override fun onDestroy() {
        tabManager.tabs().forEach { it.webView.destroy() }
        super.onDestroy()
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }

    companion object {
        private const val KEY_WEBVIEW_STATE = "webview_state"
        private const val KEY_IN_BROWSER = "in_browser"
    }
}
