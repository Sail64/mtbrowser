package cn.ptdocs.librechatapp

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.net.Uri
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import cn.ptdocs.librechatapp.data.PrefSiteRepository
import cn.ptdocs.librechatapp.di.AppGraph
import cn.ptdocs.librechatapp.domain.model.Site
import cn.ptdocs.librechatapp.platform.KeyChainCertSelector
import cn.ptdocs.librechatapp.storage.Prefs
import cn.ptdocs.librechatapp.ui.ImmersiveScrollHelper
import cn.ptdocs.librechatapp.ui.home.HomeView
import cn.ptdocs.librechatapp.ui.home.SiteEditorDialog
import cn.ptdocs.librechatapp.web.AppWebChromeClient
import cn.ptdocs.librechatapp.web.BrowserWebViewClient
import cn.ptdocs.librechatapp.web.DownloadHandler
import cn.ptdocs.librechatapp.web.WebViewConfigurator
import cn.ptdocs.librechatapp.web.enhancers.CookieFlushEnhancer
import cn.ptdocs.librechatapp.web.enhancers.RenameFocusEnhancer
import cn.ptdocs.librechatapp.web.enhancers.ServerCertCheckEnhancer

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var homeContainer: View
    private lateinit var browserContainer: LinearLayout
    private lateinit var addressBar: TextView
    private lateinit var btnStar: TextView
    private lateinit var homeView: HomeView
    private lateinit var immersive: ImmersiveScrollHelper
    private lateinit var certSelector: KeyChainCertSelector

    private var inBrowser = false
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
        addressBar = findViewById(R.id.address_bar)
        btnStar = findViewById(R.id.btn_star)
        webView = findViewById(R.id.webview)

        setupHome()
        setupBrowser()

        // 恢复：优先 restoreState（转屏/进程回收），否则打开上次访问的站点
        val restored = savedInstanceState?.getBundle(KEY_WEBVIEW_STATE)?.let {
            webView.restoreState(it)
        } != null
        if (restored) {
            showBrowser(updateHomeList = false)
        } else {
            Prefs.getLastUrl(this)
                ?.let { PrefSiteRepository.hostOf(it) }
                ?.let { AppGraph.siteRepository.byHost(it) }
                ?.let { openSite(it) }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (inBrowser && webView.canGoBack()) {
                    webView.goBack()
                } else if (inBrowser) {
                    showHome()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
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

    private fun showHome() {
        inBrowser = false
        immersive.setEnabled(false)
        homeContainer.visibility = View.VISIBLE
        browserContainer.visibility = View.GONE
        homeView.render()
    }

    private fun showBrowser(updateHomeList: Boolean = true) {
        inBrowser = true
        immersive.setEnabled(true)
        homeContainer.visibility = View.GONE
        browserContainer.visibility = View.VISIBLE
        if (updateHomeList) homeView.render()
    }

    fun openSite(site: Site) {
        Prefs.setLastUrl(this, site.url)
        showBrowser()
        webView.loadUrl(site.url)
    }

    // ---------- 浏览视图 ----------

    private fun setupBrowser() {
        WebViewConfigurator.configure(webView)

        immersive = ImmersiveScrollHelper(this, findViewById(R.id.bottom_toolbar), thresholdPx = 24)
        immersive.attach(webView)

        webView.webViewClient = BrowserWebViewClient(
            activity = this,
            selector = certSelector,
            advisor = AppGraph.certAdvisor,
            reminders = AppGraph.reminderScheduler,
            sslTrustPolicy = AppGraph.sslTrustPolicy,
            navigationPolicy = AppGraph.navigationPolicy,
            sites = AppGraph.siteRepository,
            enhancers = listOf(
                CookieFlushEnhancer(),
                RenameFocusEnhancer(),
                ServerCertCheckEnhancer(AppGraph.certAdvisor, AppGraph.reminderScheduler)
            ),
            onUrlChanged = { url -> runOnUiThread { onUrlChanged(url) } }
        )
        webView.webChromeClient = AppWebChromeClient(this)
        DownloadHandler(this).setup(webView)

        findViewById<TextView>(R.id.btn_nav_back).setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        findViewById<TextView>(R.id.btn_nav_forward).setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        findViewById<TextView>(R.id.btn_nav_home).setOnClickListener { showHome() }
        addressBar.setOnClickListener { showAddressInputDialog() }
        btnStar.setOnClickListener { bookmarkCurrentPage() }
    }

    private fun onUrlChanged(url: String) {
        Prefs.setLastUrl(this, url)
        addressBar.text = url
        val bookmarked = AppGraph.siteRepository.findByUrl(url) != null
        btnStar.text = if (bookmarked) "★" else "☆"
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
                    webView.loadUrl(url)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun bookmarkCurrentPage() {
        val url = webView.url ?: return
        val existing = AppGraph.siteRepository.findByUrl(url)
        if (existing != null) {
            SiteEditorDialog.show(this, AppGraph.siteRepository, existing) { onUrlChanged(url) }
        } else {
            SiteEditorDialog.show(
                this, AppGraph.siteRepository, null,
                onSaved = { onUrlChanged(url) },
                prefillName = webView.title.orEmpty(),
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

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.isAppearanceLightStatusBars = !isDarkTheme
            controller.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    // ---------- 生命周期 ----------

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (inBrowser) {
            val bundle = Bundle()
            webView.saveState(bundle)
            outState.putBundle(KEY_WEBVIEW_STATE, bundle)
        }
    }

    override fun onDestroy() {
        if (this::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }

    companion object {
        private const val KEY_WEBVIEW_STATE = "webview_state"
    }
}
