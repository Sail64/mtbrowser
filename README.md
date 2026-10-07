# LibreChatApp

> **Unofficial Client Notice**
>
> This project started as an **unofficial Android client** for **LibreChat** and has evolved into a
> **general-purpose mTLS browser**: bookmark-centric, any self-hosted mTLS service (LibreChat,
> internal admin panels, etc.) can be added and visited. It is **not affiliated with the official
> LibreChat team**.

## Screenshots

> The screenshots show the **LibreChat web interface**. This app is just a full-screen WebView wrapper
> with passwordless authentication via mTLS.

![Screenshot 1](screenshots/Screenshot_20260219_182457_cn.ptdocs.librechatapp.jpg)
![Screenshot 2](screenshots/Screenshot_20260219_182528_cn.ptdocs.librechatapp.jpg)

---

## Overview

Why I built this:
I use LibreChat on my phone often, but there’s no official app. Using a mobile browser comes with a few
frictions:
1) You still have to go through a login step, even if the browser remembers your password.
2) The LibreChat UI looks great, but the browser’s address bar and menus eat up screen space and get in
   the way of the experience.
3) My LibreChat instance lives on a private network behind an Nginx reverse proxy with mTLS for extra
   security. That setup requires the browser to present and cache client certificates (aliases). Only
   heavyweight browsers like Chrome or Firefox handle this well, and they feel overkill on mobile.

That’s why I built LibreChatApp: a lightweight Android WebView client for a self-hosted LibreChat
server. As usage grew, I needed to reach other mTLS-protected sites too, so it has evolved into a
**general-purpose mTLS browser**: bookmark any site or type a URL directly, and every site shares
mTLS client certificate selection & caching, expiry warnings, and file upload/download.

Design & implementation details: [docs/DESIGN_通用浏览器改造.md](docs/DESIGN_通用浏览器改造.md).

---

## Features

- **Bookmark home** (native layout, add/edit/delete, empty-state guide; legacy server URL is
  auto-migrated on first launch)
- **Address bar** (auto-prefixes `https://`; http/https always open in-app)
- **Bottom toolbar** (back/forward/home/reload/one-tap bookmark) and **immersive scrolling**
  (scroll down hides toolbar & status bar, scroll up restores)
- **mTLS client certificate support** (per-site alias cache, expiry warning — at most once per
  site per day)
- **Controllable SSL error handling** (self-signed/expired certs prompt the user; "proceed anyway"
  grants a session-only exemption)
- **Invalid cert handling** (clears the cached cert on main-frame 400 and re-prompts)
- **Secure WebView settings** (disallow mixed content, disable third-party cookies)
- **File upload & download support** (HTTP, data URL, blob URL; saved to system Downloads)

---

## Usage

### 1. Add a Site
On first launch, tap **"＋ 添加"** on the bookmark home. Tap ☆ while browsing to bookmark the
current page. Long-press a bookmark to edit or delete it. A legacy server URL is migrated
automatically into the first bookmark "LibreChat".

### 2. Client Certificate (mTLS)
If a site uses mutual TLS, the app prompts you to select a client certificate. The alias is cached
per site (no cross-site interference), and you'll get expiry warnings (at most once per site daily).

### 3. File Upload & Downloads
Supports uploading files in chats and downloading exported conversation records.

### 4. Immersive Browsing
Scrolling down hides the toolbar and status bar; scrolling up or reaching the top restores them.

---

## Build & Release

### Debug Build
```bash
./gradlew assembleDebug
```

### Generate Keystore
```bash
./generate_keystore.sh
```

### Build Release
```bash
./build_release.sh
```
On success, `release.apk` will be generated in the project root.

---

## Project Structure

```
app/src/main/java/cn/ptdocs/librechatapp/
├── MainActivity.kt                 # Thin controller: home/browser view switch, back key, state
├── domain/                         # Pure business interfaces & models (no Android deps)
│   ├── CertificateAdvisor.kt       # Single source of truth for cert evaluation
│   ├── CertReminderScheduler.kt    # Reminder dedup (once per site per day)
│   ├── ClientCertSelector.kt       # Client cert selection & caching
│   ├── SslTrustPolicy.kt           # SSL trust policy (session exemptions)
│   ├── NavigationPolicy.kt         # Navigation policy (in-app vs external)
│   ├── SiteRepository.kt           # Site (bookmark) repository
│   └── model/                      # Site / CertInfo / CertVerdict etc.
├── data/                           # SharedPreferences+JSON impl, legacy migration
├── platform/                       # KeyChain selector, server cert prober
├── di/AppGraph.kt                  # Lightweight DI container (composition root)
├── storage/Prefs.kt                # Global metadata (last_url)
├── ui/
│   ├── home/                       # Bookmark home, editor dialog
│   └── ImmersiveScrollHelper.kt    # Immersive scrolling (toolbar/status bar)
└── web/
    ├── BrowserWebViewClient.kt     # Thin glue: WebView callbacks → domain components
    ├── PageEnhancer.kt             # Page enhancer registry (onPageFinished hooks)
    ├── enhancers/                  # Cookie flush / server cert check
    ├── AppWebChromeClient.kt       # File chooser callbacks
    ├── DownloadHandler.kt          # Download handling (HTTP / data / blob)
    └── WebViewConfigurator.kt      # WebView security config
```

---

## Permissions

- `android.permission.INTERNET`

---

## License

MIT License. See [LICENSE](LICENSE).
