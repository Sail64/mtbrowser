# mtbrowser

> Lightweight general-purpose mTLS browser for Android. Bookmark-centric: any self-hosted
> service behind mutual TLS (LibreChat, internal admin panels, router consoles, …) can be
> bookmarked and visited.

## Features

- **Multi-tab browsing** (independent WebView per tab, LRU cap of 8, tab overview to switch/close,
  long-press to close others)
- **Bookmark home** (native layout, add/edit/delete, empty-state guide)
- **Top bar** (tab count, auto-prefixes `https://`, reload, one-tap bookmark, home; http/https
  always open in-app)
- **Floating fullscreen button** (draggable with remembered position; fullscreen hides the app
  top bar, and the status bar is tinted to the page background so it blends in — icons kept,
  light/dark adapts to background; no bottom bar at all — system back gesture handles page back)
- **mTLS client certificate support** (per-site alias cache, expiry warning — at most once per
  site per day)
- **Controllable SSL error handling** (self-signed/expired certs prompt the user; "proceed anyway"
  grants a session-only exemption)
- **Invalid cert handling** (clears the cached cert on main-frame 400 and re-prompts)
- **Secure WebView settings** (disallow mixed content, disable third-party cookies)
- **File upload & download support** (HTTP, data URL, blob URL; saved to system Downloads)

## Usage

### 1. Add a Site
Tap **"＋ 添加"** on the bookmark home. Tap ☆ in the address bar while browsing to bookmark the
current page. Long-press a bookmark to edit or delete it.

### 2. Tabs
Tap ▣ in the top bar to open the tab overview: tap a card to switch, ✕ to close, **"＋ 新建标签"**
for a new tab, long-press to close the others. Bookmarks and address-bar input load in the
current tab.

### 3. Client Certificate (mTLS)
If a site uses mutual TLS, the app prompts you to select a client certificate. The alias is cached
per site (no cross-site interference), and you'll get expiry warnings (at most once per site daily).

### 4. File Upload & Downloads
Upload files in web pages; downloads (HTTP / data URL / blob URL) are saved to system Downloads.

### 5. Fullscreen
Tap the floating button (top-right) for immersive fullscreen (hides address bar, toolbar, status
and navigation bars); tap again or press back to restore. The button can be dragged anywhere and
its position is remembered.

## Build

```
./gradlew assembleDebug
```

Signing & release build: see `build_release.sh`.

## Code Structure

```
app/src/main/java/cn/tobe/mtbrowser/
├── MainActivity.kt                 # Thin controller: home/browser switch, back key, state
├── domain/                         # Pure business interfaces & models (no Android deps)
│   ├── CertificateAdvisor.kt       # Single source of truth for cert evaluation
│   ├── CertReminderScheduler.kt    # Reminder dedup (once per site per day)
│   ├── ClientCertSelector.kt       # Client cert selection & caching
│   ├── ProbeThrottle.kt            # Background probe throttle (once per site per day)
│   ├── SslTrustPolicy.kt           # SSL trust policy (session exemptions)
│   ├── NavigationPolicy.kt         # Navigation policy (in-app vs external)
│   ├── SiteRepository.kt           # Site (bookmark) repository
│   └── model/                      # Site / CertInfo / CertVerdict etc.
├── data/                           # SharedPreferences+JSON implementations
├── platform/                       # KeyChain selector, server cert prober
├── di/AppGraph.kt                  # Lightweight DI container (composition root)
├── storage/Prefs.kt                # Global metadata (last_url, FAB position)
├── ui/
│   ├── home/                       # Bookmark home, editor dialog
│   ├── TabSwitcher.kt              # Tab overview (switch/close/new)
│   └── ImmersiveController.kt      # Immersive fullscreen (bars + system bars)
└── web/
    ├── BrowserWebViewClient.kt     # Thin glue: WebView callbacks → domain components
    ├── TabManager.kt               # Tab lifecycle & LRU eviction (one WebView per tab)
    ├── PageEnhancer.kt             # Page enhancer registry (onPageFinished hooks)
    ├── enhancers/                  # Cookie flush / server cert check
    ├── AppWebChromeClient.kt       # File chooser callbacks, tab title sync
    ├── DownloadHandler.kt          # Download handling (HTTP / data / blob)
    └── WebViewConfigurator.kt      # WebView security config
```

Design & implementation details: [docs/DESIGN.md](docs/DESIGN.md).

## License

See [LICENSE](LICENSE).
