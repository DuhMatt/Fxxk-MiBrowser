# Release Notes

## v1.2.4 (current)

### 中文

v1.2.4 是一次代码整理版。功能没有换方向，只把已经测试正常的跳转流程收短一点。

- 合并 `startActivity` 和 `Instrumentation.execStartActivity` 里重复的浏览器重定向逻辑，两个入口现在走同一段处理。
- 精简默认浏览器候选选择逻辑，仍然优先避开小米浏览器，再交给系统默认浏览器或系统选择器。
- 简化小米浏览器 / 应用商店包名判断，行为不变。
- 清掉几处 Kotlin 编译器提示里的多余安全调用和不必要类型转换。
- 已在手机上测试，现有功能正常。

这一版不改 LSPosed 推荐作用域，不改默认浏览器选择策略，也不新增任何浏览器包名。

### English

v1.2.4 is a cleanup release. The behavior stays the same; the redirect path is just shorter now.

- Merge the duplicated redirect logic used by `startActivity` and `Instrumentation.execStartActivity`.
- Trim the default-browser candidate selection while still avoiding Xiaomi Browser first, then using the system default browser or Android chooser.
- Simplify Xiaomi Browser / app-store package checks without changing the result.
- Remove a few unnecessary safe calls and casts flagged by the Kotlin compiler.
- Tested on-device after the change. Existing behavior still works.

This release does not change the recommended LSPosed scope, browser selection behavior, or add any hard-coded browser package.

## v1.2.2

### 中文

v1.2.2 主要是把默认作用域收回来一点，同时保留已经验证过的三条链路。

- 推荐作用域从 13 个缩到 9 个，去掉 `com.xiaomi.mirror`、`com.miui.video`、`com.miui.securitycenter` 和 `com.android.systemui`。
- 保留小米互传、小爱识屏 / 超级小爱、AI Engine、contentcatcher、AI 视觉助手，以及设置里的“小米路由管理”入口所需作用域。
- 移除一个只用于记录日志的 `PackageManager.resolveActivity` hook。它不改结果，release 版里继续挂着意义不大。
- 回归测试小爱识屏、小米互传 URL、设置里的“管理小米路由”，三项都能继续交给系统默认浏览器。

这一版没有改变默认浏览器选择逻辑，也没有硬编码任何浏览器包名。

### English

v1.2.2 trims the default LSPosed scope without dropping the paths that were tested on-device.

- Reduce the recommended scope from 13 packages to 9 by removing `com.xiaomi.mirror`, `com.miui.video`, `com.miui.securitycenter`, and `com.android.systemui`.
- Keep the scopes needed by Mi Share, XiaoAi / Super XiaoAi screen recognition, AI Engine, contentcatcher, AI vision, and the Settings entry for Xiaomi router management.
- Remove the `PackageManager.resolveActivity` hook that only logged resolver results. It did not change behavior, so it should not stay in the release build.
- Retested XiaoAi screen recognition, Mi Share URL handling, and Settings' "Manage Xiaomi router" entry. All three still hand links to the system default browser.

This release does not change browser selection and still does not hard-code a browser package.

## v1.2.1

### 中文

v1.2.1 是一次小修复，主要补齐小爱识屏 / 超级小爱在新版 HyperOS 上的链接格式。

- 支持从小爱识屏的 `mibrowser://...web_url=...` 和 `intent://...web_url=...#Intent` 中恢复真实网页链接，例如 `web_url=www.baidu.com`。
- 修复 URL 恢复时误扫 Android 框架对象的问题。之前在部分设备上可能会把 `base.apk`、主题资源 ID 或包名片段误当成网页打开。
- 修复 `www.baidu.com` 这类三段式域名被误判为 Android 包名，导致真实链接被过滤的问题。
- 如果无法恢复真实 URL，模块不再打开空白 `https://` 页面，也不会继续跳到小米浏览器下载页。

已回归测试小爱识屏、互传 URL 和默认浏览器跳转。模块仍然只处理网页 Intent，不会硬编码某一个浏览器。

### English

v1.2.1 is a small bugfix release for the link format used by XiaoAi / Super XiaoAi on newer HyperOS builds.

- Recover real web links from XiaoAi payloads such as `mibrowser://...web_url=...` and `intent://...web_url=...#Intent`, including cases like `web_url=www.baidu.com`.
- Stop URL recovery from walking through Android framework objects. On some devices this could turn `base.apk`, theme resource IDs, or package-name fragments into bogus browser URLs.
- Fix three-part domains such as `www.baidu.com` being mistaken for Android package names and filtered out.
- If the original URL cannot be recovered, the module no longer opens a blank `https://` page or falls through to Xiaomi Browser's app-store download page.

XiaoAi screen recognition, Mi Share URL handling, and default-browser dispatch were retested. The module still only handles web Intents and does not hard-code a browser package.

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
