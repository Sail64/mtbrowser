# LibreChatApp

> **非官方客户端声明**
>
> 本项目最初是 **LibreChat** 的一个 **非官方 Android 客户端**，现已演进为一个
> **通用 mTLS 浏览器**：以书签为中心组织站点，任何启用 mTLS 的自部署服务
> （LibreChat、内网管理系统等）都可以添加收藏访问。它不隶属于 LibreChat 官方团队。

## 截图

> 截图展示的是 **LibreChat 的网页界面**，本应用仅做全屏 WebView 封装和免输密码认证。

![Screenshot 1](screenshots/Screenshot_20260219_182457_cn.ptdocs.librechatapp.jpg)
![Screenshot 2](screenshots/Screenshot_20260219_182528_cn.ptdocs.librechatapp.jpg)

---

## 简介

我想要说明一下我做这个项目的背景：
我经常要在手机上使用，而官方没有APP客户端，在手机上使用有以下几点使用不便：
1、要输入密码，哪怕是浏览器记录密码，仍然需要有一次登录过程。
2、LibreChat的网页非常优雅美观，但是放在手机浏览器里，浏览器的地址栏和菜单会占用过多屏幕空间，同时会
对LibreChat的网页产生干扰。
3、我把LibreChat部署在我的内网，我部署了一个nginx反向代理服务器，采用mTLS认证的方式，以便加强安全性，
这就要求手机浏览器能够做到出示客户端证书和缓存证书（别名），但目前只有Chrome/firefox等大型浏览器能够实现
这一点，但是他们太重了。

基于这些背景我决定开发一款自己的轻量级APP，LibreChatApp 就是这样一款轻量的 Android WebView 客户端，
访问自部署的 LibreChat 服务端。随着使用深入，还需要访问其他启用 mTLS 的站点，因此已演进为
一个**通用 mTLS 浏览器**：支持书签收藏任意站点、地址栏直接输入访问，所有站点共享
mTLS 客户端证书选择与缓存、证书过期提醒、文件上传下载等能力。

设计与实现细节见 [docs/DESIGN_通用浏览器改造.md](docs/DESIGN_通用浏览器改造.md)。

---

## 功能特性

- **书签主页**（原生布局，添加/编辑/删除，空态引导；首次启动自动迁移旧版服务器地址）
- **多标签浏览**（每标签独立 WebView，LRU 上限 8 个，标签概览层切换/关闭，长按关闭其他）
- **顶部地址栏**（标签数、输入 URL 自动补 `https://`、刷新、一键收藏；http/https 一律应用内加载）
- **底部工具栏**（返回/前进/主页）与**浮动全屏按钮**（右上角可拖动并记忆位置，真沉浸式全屏）
- **沉浸式滚动**（下滚隐藏工具栏与状态栏，上滚恢复）
- **mTLS 客户端证书支持**（按站点缓存 alias、证书过期提醒——同一站点每天最多提醒一次）
- **SSL 错误可控放行**（自签/过期证书弹窗提示，可选择「仍要继续」，豁免仅本次运行内有效）
- **证书失效自动清理**（主框架 400 且该站点缓存过证书时，自动清理并重新选择）
- **WebView 安全配置**（禁止混合内容、关闭第三方 Cookie）
- **文件上传、下载支持**（HTTP、data URL、blob URL，保存到系统下载目录）

---

## 使用说明

### 1. 添加站点
首次启动在书签主页点击右上角「＋ 添加」，输入名称与地址即可；在浏览页面点击 ☆ 可一键收藏当前页。
长按书签可编辑或删除。旧版本配置的服务器地址会自动迁移为第一条书签「LibreChat」。

### 2. mTLS 客户端证书
如果站点启用了双向 TLS，应用会弹出系统证书选择器。选择后按站点缓存证书 alias（各站点互不干扰），
且会在证书即将过期时提示（同一站点每天最多一次）。

### 3. 文件上传和下载
支持对话时上传文件，支持导出记录时的文件下载。

### 4. 沉浸式浏览
向下滚动自动隐藏工具栏与状态栏，向上滚动或回到页面顶部自动恢复。

---

## 构建与发布

### 开发构建
```bash
./gradlew assembleDebug
```

### 生成签名文件
```bash
./generate_keystore.sh
```

### 构建 Release
```bash
./build_release.sh
```
构建成功后会生成 `release.apk`。

---

## 项目结构

```
app/src/main/java/cn/ptdocs/librechatapp/
├── MainActivity.kt                 # 薄控制器：主页/浏览视图切换、返回键、状态保存
├── domain/                         # 纯业务接口与模型（无 Android 依赖，可单测）
│   ├── CertificateAdvisor.kt       # 证书评估唯一事实来源（天数/阈值/文案）
│   ├── CertReminderScheduler.kt    # 提醒去重调度（每站点每天一次）
│   ├── ClientCertSelector.kt       # 客户端证书选择与缓存接口
│   ├── SslTrustPolicy.kt           # SSL 放行策略（含会话级豁免）
│   ├── NavigationPolicy.kt         # 导航策略（应用内/系统打开）
│   ├── SiteRepository.kt           # 站点（书签）仓库接口
│   └── model/                      # Site / CertInfo / CertVerdict 等
├── data/                           # SharedPreferences+JSON 实现、旧配置迁移
├── platform/                       # KeyChain 证书选择、服务端证书探测
├── di/AppGraph.kt                  # 轻量依赖容器（组合根）
├── storage/Prefs.kt                # 全局元数据（last_url）
├── ui/
│   ├── home/                       # 书签主页、编辑对话框
│   ├── ImmersiveScrollHelper.kt    # 沉浸式滚动（工具栏/状态栏联动）
│   └── TabSwitcher.kt              # 标签概览层（切换/关闭/新建）
└── web/
    ├── BrowserWebViewClient.kt     # 薄胶水：WebView 回调 → domain 组件
    ├── PageEnhancer.kt             # 页面增强器注册表（onPageFinished 钩子）
    ├── enhancers/                  # Cookie flush / 键盘修复 / 服务端证书检查
    ├── AppWebChromeClient.kt       # 文件选择回调、标签标题同步
    ├── TabManager.kt               # 标签生命周期与 LRU 淘汰（每标签独立 WebView）
    ├── DownloadHandler.kt          # 下载处理（HTTP / data / blob）
    └── WebViewConfigurator.kt      # WebView 安全配置
```

---

## 权限

- `android.permission.INTERNET`

---

## License

MIT License. 详见 [LICENSE](LICENSE)。
