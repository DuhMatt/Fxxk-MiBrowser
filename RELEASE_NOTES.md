# Release Notes

## v1.2.1 (current)

### 中文

v1.2.1 的新增内容：

- 修复小爱识屏 / 超级小爱中 `mibrowser://...web_url=...` 和 `intent://...web_url=...#Intent` 链路无法恢复真实网页的问题。
- 修复 URL 恢复时误扫 Android 框架对象，导致 `base.apk`、主题资源 ID 或包名片段被误当作网页打开的问题。
- 修复 `www.baidu.com` 这类三段式域名被误判为 Android 包名而被过滤的问题。
- 当无法恢复真实 URL 时，不再用空白 `https://` 打开默认浏览器，避免出现无意义空白页。

### English

New in v1.2.1:

- Fix XiaoAi / Super XiaoAi links wrapped as `mibrowser://...web_url=...` or `intent://...web_url=...#Intent` not being recovered correctly.
- Prevent URL recovery from scanning Android framework objects and accidentally opening values such as `base.apk`, theme resource IDs, or package-name fragments.
- Fix `www.baidu.com`-style three-part domains being mistaken for Android package names and filtered out.
- Stop opening a blank `https://` page when the original URL cannot be recovered.

## v1.2

### 中文

v1.2 的新增内容：

- 修复小爱识屏 / 超级小爱识别到网页链接后强制调用小米浏览器的问题。模块会从识屏结果对象里恢复原始 `http(s)` 链接，识别小米应用商店的浏览器下载页跳转，例如 `market://details?id=com.android.browser` 或 `mimarket://details?id=com.android.browser`，并交给系统默认浏览器。
- 当小爱识屏链路已经变成 `mimarket://details?id=com.android.browser` 时，从识屏结果对象里恢复原始 URL。
- 增加小爱识屏 / 超级小爱相关作用域：`com.miui.voiceassist`、`com.xiaomi.aicr`、`com.xiaomi.aiasst.vision`。
- 把原来的 MiShare 专用 URL 缓存扩展为 Xiaomi 系统组件通用缓存，方便从小爱、AI Engine 等链路里找回原始网页链接。
- 过滤小爱自身图标资源、代码常量和 Android 包名，避免误把 `https://` 当成 `https://com.android.browser`。
- 顺便修了一下 `assembleRelease` 没有接 `signingConfigs` 的问题，现在会用本地 release keystore 签名 release APK。

模块不会硬编码 Chrome、Edge、Firefox、Via 或任何固定浏览器；不设置默认浏览器时回退到系统浏览器选择器。只处理网页 Intent，不影响文件、电话、短信、地图、应用私有 scheme。

### English

New in v1.2:

- Fix XiaoAi / Super XiaoAi screen recognition forcing recognised web links into Xiaomi Browser. The original `http(s)` URL is recovered from the screen-recognition payload, Xiaomi Market browser download-page redirects (`market://details?id=com.android.browser` / `mimarket://details?id=com.android.browser`) are detected, and the link is handed to the system default browser.
- When the XiaoAi screen-recognition flow has already been rewritten into `mimarket://details?id=com.android.browser`, the original URL is recovered from the recognition result object.
- Add scope for `com.miui.voiceassist`, `com.xiaomi.aicr`, and `com.xiaomi.aiasst.vision`.
- Expand the Mi Share-only URL cache into a generic Xiaomi system-component URL cache, so the original web URL can be recovered from XiaoAi, AI Engine, and related flows.
- Filter XiaoAi's own icon assets, code constants, and Android package names, so a recovered URL is never something like `https://com.android.browser`.
- Wire up the missing `signingConfigs.release` so `assembleRelease` now produces a properly signed APK with the local release keystore.

The module does not hard-code any third-party browser and does not choose Chrome, Edge, Firefox, Via, or any other browser for the user. If no default browser is set, it falls back to the Android system chooser. Only web Intents are affected; files, phone, SMS, maps, and app-private schemes pass through untouched.
