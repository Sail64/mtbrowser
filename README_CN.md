# mtbrowser

> 轻量级通用 mTLS 浏览器（Android）。以书签为中心组织站点，任何启用双向 TLS 的
> 自部署服务（LibreChat、内网管理系统、路由器后台等）都可以收藏访问。

## 特性

- **多标签浏览**（每标签独立 WebView，LRU 上限 8 个，标签概览层切换/关闭，长按关闭其他）
- **书签主页**（原生布局，添加/编辑/删除，空态引导）
- **顶部操作栏**（标签数、输入 URL 自动补 `https://`、刷新、一键收藏、回主页；http/https 一律应用内加载）
- **浮动全屏按钮**（右上角可拖动并记忆位置；全屏隐藏顶部操作栏，状态栏/导航栏不动、
  背景色自动染成页面背景色以保持视觉一致，图标明暗自适应；底部无任何按钮，返回手势负责后退）
- **mTLS 客户端证书支持**（按站点缓存 alias、证书过期提醒——同一站点每天最多提醒一次）
- **SSL 错误可控放行**（自签/过期证书弹窗提示，可选择「仍要继续」，豁免仅本次运行内有效）
- **证书失效自动清理**（主框架 400 且该站点缓存过证书时，自动清理并重新选择）
- **WebView 安全配置**（禁止混合内容、关闭第三方 Cookie）
- **文件上传、下载支持**（HTTP、data URL、blob URL，保存到系统下载目录）

## 使用说明

### 1. 添加站点
在书签主页点击右上角「＋ 添加」，输入名称与地址即可；浏览时点击地址栏右侧 ☆ 可一键收藏当前页。
长按书签可编辑或删除。

### 2. 多标签
点击顶栏 ▣ 打开标签概览，点卡片切换、点 ✕ 关闭、底部「＋ 新建标签」开新页，长按卡片可关闭其他。
书签与地址栏输入均在当前标签内加载。

### 3. mTLS 客户端证书
如果站点启用了双向 TLS，应用会弹出系统证书选择器。选择后按站点缓存证书 alias（各站点互不干扰），
且会在证书即将过期时提示（同一站点每天最多一次）。

### 4. 文件上传和下载
支持网页内上传文件；下载（HTTP / data URL / blob URL）保存到系统下载目录。

### 5. 全屏浏览
点击右上角浮动按钮进入全屏：顶部操作栏隐藏，状态栏背景自动染成页面背景色（信号、电量图标
保留，明暗随背景自适应），与页面视觉连成一体。再点一次或按返回键恢复。
按钮可以拖到任意位置，位置会被记住。

## 构建

```
./gradlew assembleDebug
```

签名与 Release 构建见 `build_release.sh`。

## 代码结构

```
app/src/main/java/cn/tobe/mtbrowser/
├── MainActivity.kt                 # 薄控制器：主页/浏览视图切换、返回键、状态保存
├── domain/                         # 纯业务接口与模型（无 Android 依赖，可单测）
│   ├── CertificateAdvisor.kt       # 证书评估唯一事实来源（天数/阈值/文案）
│   ├── CertReminderScheduler.kt    # 提醒去重调度（每站点每天一次）
│   ├── ClientCertSelector.kt       # 客户端证书选择与缓存接口
│   ├── ProbeThrottle.kt            # 后台探测限频（每站点每天一次）
│   ├── SslTrustPolicy.kt           # SSL 放行策略（含会话级豁免）
│   ├── NavigationPolicy.kt         # 导航策略（应用内/系统打开）
│   ├── SiteRepository.kt           # 站点（书签）仓库接口
│   └── model/                      # Site / CertInfo / CertVerdict 等
├── data/                           # SharedPreferences+JSON 实现
├── platform/                       # KeyChain 证书选择、服务端证书探测
├── di/AppGraph.kt                  # 轻量依赖容器（组合根）
├── storage/Prefs.kt                # 全局元数据（last_url、浮窗按钮位置）
├── ui/
│   ├── home/                       # 书签主页、编辑对话框
│   ├── TabSwitcher.kt              # 标签概览层（切换/关闭/新建）
│   └── ImmersiveController.kt      # 沉浸式全屏（操作栏/系统栏显隐）
└── web/
    ├── BrowserWebViewClient.kt     # 薄胶水：WebView 回调 → domain 组件
    ├── TabManager.kt               # 标签生命周期与 LRU 淘汰（每标签独立 WebView）
    ├── PageEnhancer.kt             # 页面增强器注册表（onPageFinished 钩子）
    ├── enhancers/                  # Cookie flush / 服务端证书检查
    ├── AppWebChromeClient.kt       # 文件选择回调、标签标题同步
    ├── DownloadHandler.kt          # 下载处理（HTTP / data / blob）
    └── WebViewConfigurator.kt      # WebView 安全配置
```

设计与实现细节见 [docs/DESIGN.md](docs/DESIGN.md)。

## 协议

见 [LICENSE](LICENSE)。
