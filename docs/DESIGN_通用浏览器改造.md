# 设计文档：mtbrowser —— 通用 mTLS 浏览器

> 状态：设计定稿，随实现同步修订
> **状态说明（2026-10-07）**：文中 RenameFocusGuard/RenameFocusEnhancer（LibreChat 重命名键盘修复）
> 特性已整体移除，多标签、顶部地址栏、浮动全屏按钮为后续增量，本文其余设计仍然有效。
>
> 前置基线：`c0ebc06 增加重命名会话时键盘自动收起的修复开关`
> 作者：Felix & AI 助手，2026-10
> 设计原则：**正派（不藏魔法行为）、通用（能力不绑定特定站点）、可扩展（接口边界清晰）、抽象复用（单一事实来源，拒绝重复代码）**

## 一、背景与来龙去脉

本项目最初是一个 LibreChat 专用 WebView 客户端，解决的核心痛点是：
内网 Nginx 反向代理启用 mTLS 后，普通手机浏览器出示客户端证书的体验不好，
需要一个支持「选择证书 + 缓存 alias + 证书过期提醒」的专用壳。

随着使用深入，出现了新需求：**除了固定的 LibreChat 地址，还有其他同样启用
mTLS 的 URL 需要访问，并且同样需要证书过期提醒**。

最初的想法是「支持多个固定地址」，但多地址切换、地址管理、按地址区分证书
缓存……做到最后本质上就是一个带书签的浏览器。因此决策：

> **直接把它做成一个通用 mTLS 浏览器**，以书签为中心组织入口，
> mTLS 能力和证书提醒做成对所有站点通用的基础设施。

浏览器既然是通用工具，项目也随之正名：**应用名 `mtbrowser`，
包名 `cn.tobe.mtbrowser`**（脱离 LibreChat 语境，包前缀回归作者域名）。
`applicationId` 变更属于破坏性变更：旧版数据（书签、alias 缓存）不迁移，
需卸载重装一次——通用化的收益远大于一次性迁移成本。

### 已确认的产品决策

| 问题 | 决策 | 理由 |
|---|---|---|
| 书签以外的 URL 如何处理 | **应用内加载**（http/https 一律内开） | 让所有站点都享受 mTLS 与证书提醒能力；非 http scheme（`mailto:` 等）仍交系统 |
| 书签主页形式 | **原生布局** | 与现有纯 View 体系一致、实现简单、低配机流畅；WebView 渲染起始页需走 JS bridge，复杂度不值 |
| 地址栏位置 | **顶部**（桌面浏览器惯例） | 底部地址栏无先例；顶栏容纳 标签数 / 地址栏 / 刷新 / 收藏，底部只留纯导航 |
| 多标签实现 | **真 WebView 池**（每标签独立实例，LRU 上限 8 个），不用 save/restore 假切换 | 假切换有可感知延迟且丢失 JS/表单/滚动状态；8 个上限约束 WebView 内存（每个可达几十 MB） |
| 全屏切换交互 | **右上角浮动按钮**（扩散/收拢图标切换），真沉浸式（状态栏+导航栏+双栏全隐） | 滚动显隐方案在双栏布局下语义含糊（藏一半藏不干净），且用户需要**明确的**全屏入口/出口；浮动按钮状态自明、可发现性强 |
| 旧的「设置服务器地址」 | **删除** | 被地址栏（运行时入口）+ 书签（持久化入口）取代 |

## 二、现状分析（改造前基线）

```
app/src/main/java/cn/ptdocs/librechatapp/
├── MainActivity.kt            # 单 WebView + 设置对话框（服务器地址）—— God Activity
├── storage/Prefs.kt           # 全局 base_url、全局 keychain_alias、rename_focus_fix
└── web/
    ├── MtlsWebViewClient.kt   # mTLS 逻辑 + 外链策略 + 三处重复的证书提醒 + 400 处理
    ├── AppWebChromeClient.kt  # 文件上传
    ├── DownloadHandler.kt     # 下载
    ├── RenameFocusGuard.kt    # LibreChat 键盘 workaround
    └── WebViewConfigurator.kt
```

改造前的问题（即本设计要解决的点）：

1. **单一固定 URL**：`Prefs.base_url` 只有一个；想访问别的站点没有入口。
2. **alias 全局单值**：`keychain_alias` 全站共用一个，多 mTLS 站点证书不同时会串。
3. **证书提醒逻辑重复且易漏**：客户端证书、服务端旁路探测、SSL 错误回调三处各写一遍
   （计算天数、组装文案、弹窗、去重各复制了三次），且去重是实例级内存标志——
   app 重启就再弹。
4. **外链一律甩给系统浏览器**：其他 mTLS 站点根本无法在应用内访问。
5. **职责耦合**：`MtlsWebViewClient` 同时管证书、导航策略、页面注入、错误处理；
   `MainActivity` 同时管 UI、存储读写、业务判断。加新能力只会继续膨胀。

## 三、架构与抽象设计（本设计的核心）

### 3.0 总原则

1. **单一事实来源（SSOT）**：证书天数计算、文案、阈值只写一份（`CertificateAdvisor`）；
   站点数据只存一份（`SiteRepository`）；导航决策只做一处（`NavigationPolicy`）；
   后台任务限频只做一处（`ProbeThrottle`）。
2. **依赖接口而非实现**：UI 层只依赖 domain 接口；SharedPreferences/KeyChain 等
   平台细节全部藏在 data/impl 后面。将来换 DataStore、Room、内置 PKCS#12 证书库
   都不动业务代码。
3. **组合而非继承**：`BrowserWebViewClient` 是薄胶水层（把 WebView 回调翻译成
   domain 调用），所有行为来自注入的组件；新增能力 = 新增一个组件，不改旧类。
4. **正派**：没有隐式行为——每个 URL 为什么内开/外开、证书为什么放行/拦截、
   提醒为什么弹/不弹、标签为什么被淘汰，都有唯一可查的策略/管理对象，行为可测试。
5. **标签是 UI/平台概念**：标签管理不进 domain 层，`TabManager` 只管 WebView
   生命周期与 LRU 索引，WebView 装配通过工厂回调注入，不掺业务逻辑。

### 3.1 分层与包结构（目标态）

```
cn/tobe/mtbrowser/
├── domain/                      # 纯 Kotlin，无 Android UI 依赖（可单测）
│   ├── model/
│   │   ├── Site.kt                  # 站点（书签）模型
│   │   ├── CertModels.kt            # CertInfo / CertKind / CertVerdict
│   │   └── NavigationDecision.kt    # OPEN_IN_APP / OPEN_EXTERNAL
│   ├── CertificateAdvisor.kt        # 接口：cert → verdict（天数/阈值/文案 SSOT）
│   ├── CertReminderScheduler.kt     # 接口：提醒去重（每 host+类型 24h 一次）
│   ├── ProbeThrottle.kt             # 接口：后台任务限频（每 host 24h 一次）
│   ├── ClientCertSelector.kt        # 接口：为 host 选客户端证书（alias）
│   ├── SslTrustPolicy.kt            # 接口：SSL 错误 → 放行/拦截决策
│   ├── NavigationPolicy.kt          # 接口：url → 在哪打开
│   └── SiteRepository.kt            # 接口：站点 CRUD + 按 host 查询
├── data/
│   ├── PrefStores.kt                # PrefSiteRepository / PrefClientCertStore /
│   │                                #   PrefCertReminderStore / PrefProbeThrottleStore
│   └── LegacyMigrator.kt            # base_url → 首条书签的一次性迁移
├── platform/
│   ├── KeyChainCertSelector.kt      # ClientCertSelector 的 KeyChain 实现
│   └── HttpsCertProber.kt           # 服务端证书旁路探测
├── di/AppGraph.kt                   # 轻量组合根：全部单例的装配点
├── storage/Prefs.kt                 # 全局元数据（last_url）
├── ui/
│   ├── MainActivity.kt              # 薄控制器：视图路由、标签委托、生命周期
│   ├── home/
│   │   ├── HomeView.kt              # 书签列表 + 空态引导
│   │   └── SiteEditorDialog.kt      # 添加/编辑书签（URL 规范化集中处）
│   ├── TabSwitcher.kt               # 标签概览层（原生列表：标题+URL+关闭）
│   └── ImmersiveScrollHelper.kt     # 沉浸式控制：滚动联动 + 全屏切换
└── web/
    ├── TabManager.kt                # 标签生命周期、LRU 淘汰、当前标签索引
    ├── BrowserWebViewClient.kt      # 薄胶水：WebView 回调 → domain 组件
    ├── AppWebChromeClient.kt        # 文件选择 + onReceivedTitle → 标签标题
    ├── DownloadHandler.kt           # 下载（HTTP / data / blob）
    ├── WebViewConfigurator.kt       # WebView 安全配置
    ├── PageEnhancer.kt              # 接口：onPageFinished 钩子
    └── enhancers/
        ├── RenameFocusEnhancer.kt
        ├── CookieFlushEnhancer.kt
        └── ServerCertCheckEnhancer.kt
```

依赖方向：`ui → domain ← data/platform`。domain 不 import 任何 android.widget/webkit。

### 3.2 核心接口定义

```kotlin
// 证书评估的唯一事实来源：所有地方问它，不许自己算天数
interface CertificateAdvisor {
    fun evaluate(cert: CertInfo, kind: CertKind): CertVerdict   // kind: CLIENT / SERVER
    fun message(verdict: CertVerdict, kind: CertKind, daysLeft: Long): String
    // 阈值：CLIENT=30d, SERVER=14d，实现内可配置
}

// 提醒去重：弹不弹由它决定，弹过即记录（持久化）
interface CertReminderScheduler {
    fun shouldRemind(host: String, kind: CertKind, now: Long = System.currentTimeMillis()): Boolean
    fun markReminded(host: String, kind: CertKind, now: Long = System.currentTimeMillis())
}

// 后台任务限频：与提醒去重分离——探测无论结果如何都要记录时间，
// 否则「证书正常」会退化成每次页面加载都发一次探测连接
interface ProbeThrottle {
    fun shouldRun(host: String, now: Long = System.currentTimeMillis()): Boolean
    fun markRun(host: String, now: Long = System.currentTimeMillis())
}

// 客户端证书选择：WebView 只调它，KeyChain 细节被隔离
interface ClientCertSelector {
    fun cachedAlias(host: String): String?
    fun saveAlias(host: String, alias: String)
    fun clearAlias(host: String)          // 400 失效清理也按 host
    fun chooseAlias(host: String, port: Int, keyTypes: Array<String>,
                    principals: Array<Principal?>, callback: (String?) -> Unit)
}

// SSL 放行策略：谁放行、放行多久，只有一个地方说了算
interface SslTrustPolicy {
    sealed interface Decision {
        object Proceed : Decision                       // 无条件放行（如用户豁免过）
        data class AskUser(val reason: String) : Decision  // 弹「返回主页/仍要继续」
        object Deny : Decision
    }
    fun onError(host: String, cert: CertInfo, errorKind: String?): Decision
    fun grantSessionExemption(host: String)   // 会话级豁免（进程内存，不落盘）
}

// 导航策略：外链去哪，一处决策
interface NavigationPolicy {
    fun decide(url: String, currentSiteUrl: String?): NavigationDecision
    // http/https → OPEN_IN_APP；mailto/intent 等 → OPEN_EXTERNAL；其余默认 OPEN_IN_APP 兜底
}

// 站点仓库：主页、收藏、per-host 查询的唯一数据源
interface SiteRepository {
    fun all(): List<Site>
    fun byHost(host: String): Site?
    fun findByUrl(url: String): Site?
    fun add(name: String, url: String, renameFocusFix: Boolean): Site
    fun update(site: Site)
    fun remove(id: String)
}

// 页面增强器：onPageFinished 的所有行为走注册表，WebViewClient 永不膨胀
interface PageEnhancer {
    val id: String
    fun appliesTo(url: String, site: Site?): Boolean
    fun onPageFinished(activity: Activity, view: WebView, url: String, site: Site?)
}
```

### 3.3 组件协作（关键流程走读）

**流程 A：加载 mTLS 站点**
```
BrowserWebViewClient.onReceivedClientCertRequest(request)
  → ClientCertSelector.cachedAlias(request.host)
      ├─ 有 → 后台线程 KeyChain 取私钥/链 → CertificateAdvisor.evaluate(chain[0], CLIENT)
      │        └─ EXPIRING/EXPIRED 且 CertReminderScheduler.shouldRemind → 弹提醒
      │        └─ 取钥失败 → 重新 promptAlias()
      └─ 无 → chooseAlias() 系统选择器 → saveAlias(host, alias) → 同上
```

**流程 B：SSL 错误**
```
onReceivedSslError → SslTrustPolicy.onError(host, cert, primaryError)
  ├─ Proceed → handler.proceed()
  ├─ AskUser → 弹「返回主页 / 仍要继续」（同 host 防叠加，只弹一个）
  │            → 继续 = grantSessionExemption + proceed
  └─ Deny   → handler.cancel()
证书提醒同样走 Advisor + Scheduler（与流程 A 共用，零重复代码）
```

**流程 C：页面加载完成**
```
onPageFinished → for (enhancer in enhancers) if (enhancer.appliesTo(url, site)) enhancer.onPageFinished(...)
  - CookieFlushEnhancer: 所有站点
  - RenameFocusEnhancer: 仅 site.renameFocusFix == true
  - ServerCertCheckEnhancer: https 站点
      → ProbeThrottle.shouldRun(host) 门控（无论探测结果 markRun，24h 一次）
      → HttpsCertProber 旁路探测 → 异常时再过 CertReminderScheduler 去重后弹提醒
```
> 新页面行为（如「某站点自动暗色模式」）= 写一个 `PageEnhancer` 注册进去，
> **不改 `BrowserWebViewClient` 一行代码**。这是可扩展性的关键落点。

**流程 D：导航决策**
```
shouldOverrideUrlLoading → NavigationPolicy.decide(url, 当前 WebView 所在站点的 url)
  ├─ OPEN_IN_APP    → return false（应用内加载）
  └─ OPEN_EXTERNAL  → startActivity（mailto:/intent: 等，失败 Toast 兜底）
```

**流程 E：标签生命周期**
```
MainActivity（TabManager 工厂回调 createWebView）
  → WebViewConfigurator.configure + 各 Client 挂载 + ImmersiveScrollHelper.attach
newTab(url)  → 超上限先 LRU 淘汰（current 永不被逐）→ 创建并置为 current
select(tab)  → touch() 移到队尾（LRU 依据）→ attachTab 挂载到 web_slot
close(tab)   → destroy() + 相邻标签接棒（优先右侧）；最后一个关闭 → 回书签主页
closeOthers  → 仅幸存者；调用方必须把幸存者挂载上去（否则销毁实例滞留屏上）
```
> slot 不变量：`web_slot` 内任意时刻最多挂一个 WebView（插在 FAB 之下），
> 由 `attachTab` 统一维护。

## 四、数据层

### 4.1 模型

```kotlin
data class Site(
    val id: String,              // UUID
    val name: String,            // 显示名，默认取 host
    val url: String,
    val renameFocusFix: Boolean = false,   // per-site 键盘 workaround
    val createdAt: Long = System.currentTimeMillis()
)
```

### 4.2 存储

- `PrefSiteRepository`：JSON 数组存 SharedPreferences。站点量小（几十级），不上 Room；
  接口已隔离，将来要排序/分组/云同步时换实现即可。
- per-host 证书状态：`PrefClientCertStore`（alias 映射）、`PrefCertReminderStore`
  （`host+kind → lastWarnAt`）、`PrefProbeThrottleStore`（`host → lastProbeAt`），
  三者互相独立，语义不混用。
- **迁移**：`LegacyMigrator` 首次启动检测旧 `base_url` → 生成首条 Site
  「LibreChat」（`renameFocusFix = true`），旧全局 alias 迁入 per-host 存储，旧 key 不再写。

### 4.3 标签的持久化取舍

标签**不持久化**：进程被杀后仅通过 `saveState/restoreState` 恢复当前标签，
其余标签丢失。轻量浏览器的合理默认；若日后需要，引入 tab 快照存储即可，
`TabManager` 是唯一改动点。

## 五、UI 层

### 5.1 主页视图 `HomeView`（原生布局）

- 书签列表：字母图标 + 名称 + URL。
- 右上角「＋ 添加」→ `SiteEditorDialog`（名称 + URL，输入自动补 `https://`）。
- 长按：编辑 / 删除。
- **空态引导页**：无书签时引导添加（承接原「设置服务器地址」的冷启动职责）。

### 5.2 浏览视图（顶栏 + WebView + 底栏）

```
┌──────────────────────────────────┐
│ [▣ n] [ 地址栏........ ] [↻] [☆] │  ← 顶栏 top_bar（48dp）
│ [web_slot: 当前标签 WebView       │
│            ┌──────┐]             │
│            │ ⛶ FAB│             │  ← 右上角浮动全屏按钮（叠加层）
│            └──────┘              │
│ ‹  ›                      ⌂     │  ← 底栏 bottom_toolbar（52dp）
└──────────────────────────────────┘
```

- **标签按钮 `▣ n`**：显示标签数，点击开合 `TabSwitcher` 概览层
  （深色半透明覆盖：标题 + URL + ✕，长按关闭其他，底部「＋ 新建标签」；
  最后一个标签关闭 → 回书签主页）。
- **地址栏**：显示当前 URL，点击输入直接访问（运行时入口）。URL 规范化集中在
  `SiteEditorDialog.normalizeUrl` 一处：`192.168.1.10:3000` 自动补 `https://`。
- **刷新 ↻**：`reload()` 当前标签。
- **收藏 ☆**：一键收藏当前页（预填页面标题；已收藏则变为可编辑），顶栏内即达。
- **底部工具栏**：返回 / 前进 / 主页，纯导航不放功能键。
- 书签/地址输入一律加载到**当前标签**，不自动开新标签（防标签爆炸）。

### 5.3 沉浸式控制 `ImmersiveScrollHelper`

独立类封装，两档语义分开：

- **全屏切换**（浮动按钮触发）：隐藏顶栏 + 底栏 + 状态栏 + 导航栏
  （`WindowInsetsController` + `setDecorFitsSystemWindows(false)` 真沉浸式），
  图标在扩散（expand）/收拢（collapse）间切换；返回键先退全屏。
- **滚动联动**（过渡特性）：下滚藏双栏、上滚/回顶恢复，100ms 节流；
  全屏状态下滚动联动静默。全屏按钮验证稳定后此档移除，
  `ImmersiveScrollHelper` 收敛为纯全屏控制器。

每个标签的 WebView 创建后都要 `attach()` 一次（滚动监听按实例挂载）。

### 5.4 视图切换与返回键（`MainActivity` 薄控制器）

- `HomeView` 与浏览器双容器切换，**不销毁重建 WebView**；标签随进程存活。
- 返回键逐级退出：全屏 → 标签概览 → 网页后退 → 主页 → 退出应用。
- 转屏/进程回收：`in_browser` 标志 + 当前标签 `saveState/restoreState`
  双条件恢复（只在「当时在浏览器」时恢复浏览器，避免主页转屏误开网页）。

## 六、风险与坑（通盘审视记录）

1. **SSL 一律 cancel 是通用化后的最大缺陷** → `SslTrustPolicy` 会话级豁免（见 3.3 流程 B）；
   同 host 子资源并发报错用防叠加集合去重，未弹的 handler 直接 cancel。
2. **旁路证书探测对 mTLS 站点不可靠**（不带客户端证书可能 400/被 WAF 拦）→
   `ProbeThrottle` 每 host 24h 限频（**无论结果**都记录，防止退化成每页一连接）
   + 静默失败。提醒去重（`CertReminderScheduler`）与其分离，各管各的。
3. **400 清证书误伤普通网站** → alias 已 per-host，且仅 `cachedAlias(host) != null` 才清理，
   保留 5s 冷却。注意 `WebView.clearClientCertPreferences` 是全局 API，
   会波及其他标签的进行中请求——证书确实失效，全局重选是正确语义。
4. **RenameFocusGuard 全局注入有副作用** → 变为 `PageEnhancer`，`appliesTo` 按 Site 开关。
5. **多标签内存** → 真 WebView 池上限 8 个，LRU 淘汰（current 永不被逐）。
6. **WebView 状态丢失** → 当前标签 saveState/restoreState（见 4.3 / 5.4）。
7. **销毁实例滞留屏上** → 任何「关闭/淘汰当前标签」的路径，调用方都必须
   把接棒标签挂载上去（`attachTab`），slot 不变量由它单点维护。
8. **KeyChain 禁止主线程访问** → 取私钥/链集中在后台线程执行，失败回落到重新选择。
9. **是否注册为系统浏览器（http/https intent-filter）** → 本期**不做**，
   避免与「外链应用内加载」测试互相干扰，后续版本再议。

## 七、实施顺序

1. `domain/` 全部接口与模型（纯 Kotlin，先立边界）
2. `data/` 实现（含 `ProbeThrottle`）+ `LegacyMigrator` 迁移
3. `platform/`（KeyChainCertSelector、HttpsCertProber）
4. `BrowserWebViewClient` 薄胶水 + 三个 `PageEnhancer`（替换旧 MtlsWebViewClient）
5. `HomeView` + `SiteRepository` 接入（含空态引导）
6. 顶栏（地址栏/刷新/收藏/标签按钮）+ 底栏导航
7. `TabManager` + `TabSwitcher` + MainActivity 标签委托改造
8. `ImmersiveScrollHelper`：全屏切换（浮动按钮）+ 滚动联动
9. WebView 状态保存
10. 更名 mtbrowser / `cn.tobe.mtbrowser`（namespace、applicationId、主题、资源）
11. 删除旧代码（`MtlsWebViewClient`、旧 `showSettingsDialog`、`Prefs` 旧入口）
12. 更新 README / README_CN

> 顺序刻意先立接口后写实现：每一步编译都可过，行为可逐流程验证（3.3 的 A–E）。

## 八、保持不变的能力

文件上传（`AppWebChromeClient`）、下载（`DownloadHandler`）、Cookie flush
（成为 `CookieFlushEnhancer`）、状态栏适配、`RenameFocusGuard` 本体算法。
