# mtbrowser 设计文档

轻量级通用 mTLS 浏览器（Android WebView）。本文记录整体设计与关键决策，作为后续演进的基准。

## 1. 目标与原则

**目标**：在手机上以接近原生浏览器的体验访问自部署站点，并完整支持 mTLS 双向认证——
证书选择、按站点缓存、过期提醒、失效重选；同时保持体量轻、无追踪、无多余权限。

**原则**：

1. **接口驱动**：业务决策全部收在 `domain` 层接口后面，WebView 回调层只做翻译（薄胶水）。
2. **单一事实来源（SSOT）**：证书评估（天数/阈值/文案）只在 `CertificateAdvisor`；
   URL 规范化只在 `PrefSiteRepository.normalizeUrl`；书签数据只在 `SiteRepository`。
3. **无 Android 依赖的业务层**：`domain` 是纯 Kotlin，可单测；平台能力（KeyChain、探测）在
   `platform` 层实现接口。
4. **扩展走注册表**：页面完成时的增强逻辑通过 `PageEnhancer` 注册表接入，新增增强不改核心。

## 2. 分层

```
ui ──► web ──► platform ──► domain ◄── data
 ▲       │                          ▲
 └── di/AppGraph（组合根）────────────┘
```

- **domain**：接口与模型。`SiteRepository` / `ClientCertSelector` / `CertificateAdvisor` /
  `CertReminderScheduler` / `ProbeThrottle` / `SslTrustPolicy` / `NavigationPolicy`。
- **data**：`Pref*` 实现（SharedPreferences + JSON），`Prefs` 存全局元数据。
- **platform**：`KeyChainCertSelector`（系统证书选择器 + KeyChain 取私钥/链）、
  `HttpsCertProber`（旁路 TLS 探测服务端证书）。
- **web**：`BrowserWebViewClient`（薄胶水）、`TabManager`（标签生命周期）、
  `PageEnhancer` 注册表、下载/文件选择/WebView 配置。
- **ui**：书签主页、编辑对话框、`TabSwitcher`、`ImmersiveController`。
- **di**：`AppGraph` 手写依赖容器，`Application.onCreate` 首行初始化。

## 3. 多标签

- **每标签一个独立 WebView**（真池，非 saveState/restoreState 假切换）：切换瞬时完成，
  滚动位置、JS 状态、表单内容全部保留。
- `TabManager` 只管生命周期与索引：创建走工厂回调（MainActivity 装配 client 链），
  超过 8 个按 **LRU 淘汰**（`select` 触摸排序，current 永不被淘汰），淘汰即 `destroy()`。
- `MainActivity` 持 `web_slot`（FrameLayout），**slot 内至多一个已挂载 WebView**，
  切换 = 移除旧 + 插入新（插到 index 0，浮动全屏按钮保持置顶）。
- 书签与地址栏输入在**当前标签**加载，不自动开新标签；标签概览层 `TabSwitcher`
  是 root 下的覆盖层，只负责渲染与交互。
- 取舍：标签不持久化——进程被杀只恢复当前标签（WebView 状态 Bundle）。持久化全部标签
  需要 tab 快照存储，成本高，待真实需求出现再做。

## 4. mTLS 客户端证书

- `onReceivedClientCertRequest`：先查 per-host 缓存 alias → 命中则后台线程取私钥/链并
  `proceed`；未命中弹系统选择器。KeyChain 访问禁止在主线程。
- **缓存键是 host**（含端口语义由 KeyChain alias 语义决定），站点间互不干扰。
- **失效清理**：主框架 400 且该 host 存过 alias → 清 per-host 记录 +
  `WebView.clearClientCertPreferences` + 提示重选。带 5s 冷却防错误风暴；
  「存过才清」避免对无关站点误清全局偏好。

## 5. 证书提醒与探测

- **提醒**（客户端证书在握手时评估，服务端证书走旁路探测）：
  `CertificateAdvisor` 产出 verdict（有效/临期/已过期/未生效…），文案与阈值集中在此。
- **提醒去重**：`CertReminderScheduler`，按「host + kind」每 24h 最多一次（持久化）。
- **探测限频**：`ProbeThrottle` 与提醒去重**分离**——探测无论结果都要记时，
  否则证书正常的站点会退化成每次页面加载都发旁路连接。探测失败（如强校验网关
  拒绝无客户端证书的连接）静默忽略，不影响客户端证书路径的提醒。

## 6. SSL 错误策略

- `SslTrustPolicy.onError` 产出三态决策：`Proceed`（已豁免）/ `AskUser` / `Deny`。
- 用户选「仍要继续」→ 授予**会话级豁免**（仅本次运行有效，重启即失效）。
- 同一 host 的多个子资源同时报错只弹一个对话框，其余直接 cancel；
  弹窗前检查 `isFinishing/isDestroyed`。

## 7. 沉浸式与全屏

- **沉浸式全屏**：`ImmersiveController` 由右上角浮动按钮驱动，仅隐藏应用顶部操作栏，
  **不改动窗口布局与系统栏**——避免在部分 ROM 上 `decorFitsSystemWindows` 切换不可靠的问题
  （内容顶进状态栏与图标打架、退出恢复不全）。状态栏背景色在页面加载完成时通过 JS 探测
  `body/html` 的 `background-color` 并染色，图标明暗按背景亮度自适应；回主页恢复主题配色。
- **浮动全屏按钮**：右上角 34dp，四角扩散/收拢图标；点击切换全屏
  （隐藏顶部操作栏 + 状态栏 + 导航栏），可拖动（主指针跟踪、touchSlop 判别、
  边界钳制），位置持久化；操作栏显隐导致 WebView 区域尺寸变化时重钳入界并同步存档。
- 返回键次序：退出全屏 → 关标签概览 → 网页后退 → 回书签主页。

## 8. 已知取舍

- `usesCleartextTraffic=true`：通用浏览器需要允许 http；如需收紧可改网络安全配置白名单。
- ☆ 收藏按完整 URL 匹配：深层页面显示 ☆，收藏的是当前完整 URL 而非站点根。
- 标签不持久化（见 §3）。
- LRU 上限 8：每个 WebView 可占数十 MB 内存，兼顾体验与低端机 OOM 风险。
