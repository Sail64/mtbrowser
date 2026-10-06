package cn.ptdocs.librechatapp

import android.os.Bundle
import android.webkit.WebView
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.net.Uri
import android.widget.TextView
import android.widget.EditText
import android.app.AlertDialog
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import cn.ptdocs.librechatapp.storage.Prefs
import cn.ptdocs.librechatapp.web.AppWebChromeClient
import cn.ptdocs.librechatapp.web.DownloadHandler
import cn.ptdocs.librechatapp.web.MtlsWebViewClient
import cn.ptdocs.librechatapp.web.WebViewConfigurator

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (fileUploadCallback == null) return@registerForActivityResult

        val results: Array<Uri>? = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        fileUploadCallback?.onReceiveValue(results)
        fileUploadCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureStatusBar()

        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webview)

        val tvSettings = findViewById<TextView>(R.id.tv_settings)
        tvSettings.setOnClickListener {
            showSettingsDialog(cancelable = true)
        }

        WebViewConfigurator.configure(webView)
        webView.webViewClient = MtlsWebViewClient(this) { show ->
            tvSettings.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        }
        webView.webChromeClient = AppWebChromeClient(this)

        val downloadHandler = DownloadHandler(this)
        downloadHandler.setup(webView)
        
        val url = Prefs.getBaseUrl(this)
        
        // Initial visibility check to prevent flash
        if (!url.isNullOrEmpty()) {
            val cookies = CookieManager.getInstance().getCookie(url)
            val isLoginPage = url.contains("/login")
            val hasCookies = !cookies.isNullOrEmpty()
            val shouldShow = isLoginPage || !hasCookies
            tvSettings.visibility = if (shouldShow) android.view.View.VISIBLE else android.view.View.GONE
        } else {
            tvSettings.visibility = android.view.View.VISIBLE
        }

        if (url.isNullOrEmpty()) {
            showSettingsDialog(cancelable = false)
        } else {
            loadUrl()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    fun showFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?
    ): Boolean {
        if (fileUploadCallback != null) {
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = null
        }
        fileUploadCallback = filePathCallback

        val intent = fileChooserParams?.createIntent()
        try {
            if (intent != null) {
                fileChooserLauncher.launch(intent)
            } else {
                return false
            }
        } catch (e: Exception) {
            fileUploadCallback = null
            return false
        }
        return true
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

    private fun loadUrl() {
        val url = Prefs.getBaseUrl(this)
        if (!url.isNullOrEmpty()) {
            webView.loadUrl(url)
        }
    }

    private fun showSettingsDialog(cancelable: Boolean) {
        val editText = EditText(this)
        editText.setText(Prefs.getBaseUrl(this))

        val checkBox = android.widget.CheckBox(this)
        checkBox.text = "修复重命名会话时键盘自动收起"
        checkBox.isChecked = Prefs.getRenameFocusFix(this)

        val container = android.widget.LinearLayout(this)
        container.orientation = android.widget.LinearLayout.VERTICAL
        val margin = (20 * resources.displayMetrics.density).toInt()

        val editParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        editParams.leftMargin = margin
        editParams.rightMargin = margin
        editText.layoutParams = editParams

        val checkParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        checkParams.leftMargin = margin
        checkParams.rightMargin = margin
        checkParams.topMargin = (12 * resources.displayMetrics.density).toInt()
        checkBox.layoutParams = checkParams

        container.addView(editText)
        container.addView(checkBox)

        val builder = AlertDialog.Builder(this)
            .setTitle("设置")
            .setView(container)
            .setCancelable(cancelable)
            .setPositiveButton("保存") { _, _ ->
                Prefs.setRenameFocusFix(this, checkBox.isChecked)
                val newUrl = editText.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    Prefs.setBaseUrl(this, newUrl)
                    loadUrl()
                } else if (!cancelable) {
                    showSettingsDialog(false)
                }
            }
        
        if (cancelable) {
            builder.setNegativeButton("取消", null)
        }
            
        builder.show()
    }

    private fun configureStatusBar() {
        // 设置为 true，表示不由内容填充系统窗口区域（即内容会在状态栏下方）
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val isDarkTheme = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        if (isDarkTheme) {
            window.statusBarColor = android.graphics.Color.BLACK
            window.navigationBarColor = android.graphics.Color.BLACK
        } else {
            // 设置状态栏颜色为白色（如果不设置，可能为默认黑色或主题色）
            // 这里设置为白色是为了配合常见的浅色网页背景
            window.statusBarColor = android.graphics.Color.WHITE
            window.navigationBarColor = android.graphics.Color.WHITE
        }

        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            // 状态栏图标设为深色 (Light mode -> dark icons, Dark mode -> light icons)
            controller.isAppearanceLightStatusBars = !isDarkTheme
            controller.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
}
