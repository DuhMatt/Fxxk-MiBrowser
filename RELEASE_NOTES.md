# Release Notes

## v1.2.12 — Super XiaoAi 8.2.3.1616

### 中文

- 修复超级小爱识屏后点击网页链接显示“无法打开网址”的问题。
- 适配超级小爱 `8.2.3.1616`（`com.miui.voiceassist`，`versionCode 508002003`）新的 `com.xiaomi.voiceassistant.utils.t2.isIntentAvailable(Intent, Context)` 链路。
- 在可用性检查阶段恢复识别到的 HTTP(S) 网页地址，并交给用户设置的系统默认浏览器；不再跳转到小米应用商店的浏览器下载页。
- 本版本只保留当前超级小爱版本的精确适配，不再包含旧版 `b2/f2/s2` 兼容路径或旧版本门控。
- 已在超级小爱 `8.2.3.1616`、默认浏览器 Via (`mark.via`) 的真机环境验证通过。
- 尚未在 HyperOS 3 上进行回归测试；由于小米系统组件与系统版本可独立更新，本文以超级小爱版本作为适配依据，不以 HyperOS/Android 版本作为硬性限制。
- 主仓库同时提供普通版与 Log 版；Xposed Modules 仓库仅提供普通版。

### English

- Fix the “unable to open URL” toast when opening a web link from Super XiaoAi screen recognition.
- Adapt to Super XiaoAi `8.2.3.1616` (`com.miui.voiceassist`, `versionCode 508002003`) and its new `com.xiaomi.voiceassistant.utils.t2.isIntentAvailable(Intent, Context)` path.
- Recover the recognized HTTP(S) URL during the availability check and hand it to the user's Android default browser instead of falling through to Xiaomi Market's browser download page.
- This release keeps only the exact current Super XiaoAi compatibility path; the old `b2/f2/s2` compatibility paths and version gate are removed.
- Verified on-device with Super XiaoAi `8.2.3.1616` and Via (`mark.via`) as the default browser.
- HyperOS 3 regression testing has not been performed. Xiaomi system components and system versions can update independently, so compatibility is described by the Super XiaoAi version rather than a hard HyperOS or Android-version restriction.
- The personal repository ships both Release and Log APKs; the Xposed Modules repository ships only the Release APK.

## v1.2.11 — HyperOS 4 Copy Direct (Pre-release)

> **警告：这是 Pre-release，不是正式稳定版本。仅推荐 HyperOS 4 / Android 17 开发版用户更新。该版本尚未在 HyperOS 3 真机回归；HyperOS 3 用户请继续使用 v1.2.9。如遇异常，请降级至 v1.2.9。**

### 中文

- 修复 HyperOS 4“复制直达”识别网页链接后，仍固定显示并调用小米浏览器的问题。
- 将 `get_copy_direct_data` 返回的网页 Intent 改写为用户设置的系统默认浏览器，同时保持非 HTTP(S) 和非小米浏览器目标不变。
- 在最终气泡 `CueData` 中同步替换目标包名和“在浏览器中打开”文案。
- 参考小米互传通知图标的处理方式，直接读取默认浏览器的真实应用图标并注入复制直达气泡，避免依赖 HyperAI 仅支持小米内置图标的标识表。
- 已在 HyperOS `OS4.0.0.17.XPACNXM`、Android 17、默认浏览器 Via (`mark.via`) 上实测：名称、图标和点击跳转均正确，且不再提示“执行失败”。
- 保留 v1.2.10-beta 的超级小爱 `s2.isIntentAvailable()` 修复和版本门控。

> **Warning: this is a pre-release, not a stable release. It is recommended only for HyperOS 4 / Android 17 developer-build users. It has not been regression-tested on HyperOS 3; HyperOS 3 users should remain on v1.2.9. Downgrade to v1.2.9 if problems occur.**

### English

- Fix HyperOS 4 Clipboard shortcut web cards still displaying and invoking Xiaomi Browser.
- Rewrite HTTP(S) `get_copy_direct_data` intents that explicitly target Xiaomi Browser to the user's system default browser, while leaving unrelated targets unchanged.
- Synchronize the final bubble `CueData` target package and “open in browser” label with the default browser.
- Reuse the proven Mi Share icon strategy: load the real default-browser app icon and inject it into the Clipboard shortcut bubble instead of relying on HyperAI's Xiaomi-only builtin icon registry.
- Tested on HyperOS `OS4.0.0.17.XPACNXM`, Android 17, with Via (`mark.via`) as default: label, icon, and click routing all worked without the “execution failed” toast.
- Retain the v1.2.10-beta Super XiaoAi `s2.isIntentAvailable()` fix and version gate.

## v1.2.10-beta — HyperOS 4 only (Pre-release)

> **警告：这是 Beta / Pre-release，不是正式稳定版本。仅推荐 HyperOS 4 / Android 17 开发版用户更新。该版本尚未在 HyperOS 3 真机回归，可能影响 HyperOS 3 上原本正常的功能；HyperOS 3 用户请继续使用 v1.2.9。如遇异常，请降级至 v1.2.9。**

### 中文

- 修复 HyperOS 4 / Android 17 开发版中，小爱识屏点击网页链接时提示“未安装该应用，请先安装”的问题。
- 新版超级小爱会在调用 `startActivity` 前，通过 `com.xiaomi.voiceassistant.utils.s2.isIntentAvailable()` 检查指向小米浏览器的 Intent；模块现在会在该检查阶段恢复 HTTP(S) 链接并交给系统默认浏览器。
- 模块读取 `com.miui.voiceassist` 的真实 `versionCode`，仅在 `versionCode >= 508000030` 时安装 HyperOS 4 专用 `s2` Hook；更低版本继续使用旧的 `b2/f2` 路径。
- 已在 HyperOS `OS4.0.0.17.XPACNXM`、Android 17、超级小爱 `8.0.30.4121` 上实测通过。
- 普通版与 Log 版使用和 v1.2.9 相同的包名及签名，可直接覆盖安装；Log 版提供本地日志开关、查看和分享面板。

> **Warning: this is a Beta / Pre-release, not a stable release. It is recommended only for HyperOS 4 / Android 17 developer-build users. It has not been regression-tested on HyperOS 3 and may affect previously working behavior. HyperOS 3 users should remain on v1.2.9. Downgrade to v1.2.9 if problems occur.**

### English

- Fix the “app not installed” toast shown when opening a screen-recognition web link on HyperOS 4 / Android 17 developer builds.
- New Super XiaoAi builds check Xiaomi Browser intents through `com.xiaomi.voiceassistant.utils.s2.isIntentAvailable()` before `startActivity`; the module now restores the HTTP(S) URL during that check and routes it to the system default browser.
- The module reads the real `com.miui.voiceassist` `versionCode`. The HyperOS 4-specific `s2` hook is installed only for `versionCode >= 508000030`; lower versions retain the legacy `b2/f2` path.
- Tested on HyperOS `OS4.0.0.17.XPACNXM`, Android 17, and Super XiaoAi `8.0.30.4121`.
- Release and Log APKs use the same package name and signing certificate as v1.2.9 and can be installed over it directly. The Log build includes a local logging toggle, viewer, and share panel.

## v1.2.9

### 中文

**修复：** 启用模块后小米浏览器启动崩溃的问题。

感谢 [@ChenXiaoming233](https://github.com/ChenXiaoming233) 在 [#11](https://github.com/DuhMatt/Fxxk-MiBrowser/issues/11) 提供崩溃日志协助定位问题。

#### Bug 修复

- **修复** 浏览器启动崩溃 — 模块的 `PackageManager` hook 在浏览器进程内拦截了自身对 `getPackageInfo` 的调用，返回的假 `PackageInfo` 缺少 `signatures` 字段，浏览器启动时 `AppSignatureUtil.getSignature()` 读取到 null 数组导致 NPE 崩溃。**根因：** `buildFakePackageInfo()` 未填充 `signatures` 字段。**修复：** `hookPackageManager` 中增加自查询放行判断——当调用进程包名等于查询目标包名时跳过拦截，放行真实数据。

### English

**Fix:** Browser crash on startup when the module is enabled.

Thanks to [@ChenXiaoming233](https://github.com/ChenXiaoming233) for providing crash logs in [#11](https://github.com/DuhMatt/Fxxk-MiBrowser/issues/11) that helped identify the root cause.

#### Bug Fixes

- **Fix** browser startup crash — the `PackageManager` hook intercepted the browser's own `getPackageInfo` call and returned a fake `PackageInfo` without `signatures`. On browser startup, `AppSignatureUtil.getSignature()` reads the null array → NPE crash. **Root cause:** `buildFakePackageInfo()` did not populate the `signatures` field. **Fix:** Added a self-query guard in `hookPackageManager` — when the calling package name matches the queried target, skip interception and let real data through.

### v1.2.8 (v1.2.9 replaces this release)

感谢 [@MrJonhShelby](https://github.com/MrJonhShelby) 在 [#8](https://github.com/DuhMatt/Fxxk-MiBrowser/issues/8) 报告此问题。

- 修复 `hookClipboardNotificationIcon` 无条件将所有复制直达通知（URL、地址、快递）的图标替换为浏览器图标的问题。
- 改为通过通知 `PendingIntent` 的目标、通知文本内容、以及图标来源综合判断，**仅在通知确实是打开浏览器时才替换图标**。
- 地址类复制触发地图 App、快递类复制触发物流 App 时，聚焦通知图标保持不变。
- 已真机测试：复制 URL → 替换为默认浏览器图标 ✓，复制地址 → 保持地图图标 ✓。

### English

v1.2.7 fixes the AI Engine clipboard notification icon being incorrectly replaced for all "Copy Direct" actions (addresses, express tracking).

Thanks to [@MrJonhShelby](https://github.com/MrJonhShelby) for reporting in [#8](https://github.com/DuhMatt/Fxxk-MiBrowser/issues/8).

- Fix `hookClipboardNotificationIcon` unconditionally replacing all clipboard notification icons (URLs, addresses, express) with the browser icon.
- Now inspects the notification's PendingIntent target, text content, and icon source to determine whether the action actually opens a browser. **Only replaces the icon when the notification is about opening a browser.**
- Address copies keep their map app icon; express copies keep their logistics app icon.
- Tested on-device: URL copy → replaced with default browser icon ✓, address copy → keeps map icon ✓.

## v1.2.6

![小米互传链接通知显示默认浏览器图标](assets/mishare-browser-icon-v1.2.6.png)

### 中文

v1.2.6 修复小米互传接收链接时仍显示小米浏览器图标的问题。

感谢 [@189521394](https://github.com/189521394) 在 [#2](https://github.com/DuhMatt/Fxxk-MiBrowser/issues/2) 提出功能建议并提供灵感来源；本功能参考了原项目 [com.fuckXiaomi.hookBrowser](https://github.com/Xposed-Modules-Repo/com.fuckXiaomi.hookBrowser) 的思路。

- 当小米互传接收链接弹窗读取小米浏览器图标时，动态替换为用户当前默认浏览器的图标。
- 同步处理小米互传相关通知里的浏览器图标，避免链接已经交给默认浏览器但界面仍显示小米浏览器。
- 修正 `NotificationManager.notify(String, int, Notification)` 参数判断，避免把 tag 误当成包名。
- 保留 v1.2.5 的设置搜索闪退修复，继续补齐假 `PackageInfo.applicationInfo` 和 `ApplicationInfo.sourceDir`。
- 已在真机上测试小米互传链接接收弹窗，图标替换正常。

这一版只替换显示图标，不改变默认浏览器选择策略，也不会把非网页文件强行当成链接打开。

### English

v1.2.6 fixes Xiaomi Browser icons still appearing in Mi Share link-receive UI.

Thanks to [@189521394](https://github.com/189521394) for suggesting this in [#2](https://github.com/DuhMatt/Fxxk-MiBrowser/issues/2) and sharing the inspiration; this feature also references the idea from the original [com.fuckXiaomi.hookBrowser](https://github.com/Xposed-Modules-Repo/com.fuckXiaomi.hookBrowser) project.

- Replace Xiaomi Browser icons with the user's current default browser icon when Mi Share reads the browser icon for a received link popup.
- Also cover Mi Share browser-icon notification paths so links handed to the default browser do not still show Xiaomi Browser branding.
- Fix the `NotificationManager.notify(String, int, Notification)` argument handling so the tag is not mistaken for a package name.
- Keep the v1.2.5 Settings-search crash fix by preserving the fake `PackageInfo.applicationInfo` and `ApplicationInfo.sourceDir` fields.
- Tested on-device with Mi Share link receive popup icon replacement.

This release only changes displayed icons. It does not change browser selection behavior or force non-web files to open as links.

## v1.2.5

### 中文

v1.2.5 修了一个会导致设置搜索闪退的问题。

感谢 [@Rakau](https://github.com/Rakau) 和 [@cow-your-sister](https://github.com/cow-your-sister) 在 [#1](https://github.com/DuhMatt/Fxxk-MiBrowser/issues/1) 报告此问题。

- 补齐假 `PackageInfo` 里缺的 `applicationInfo` 字段。之前模块假装小米浏览器已安装时返回的对象不完整，设置搜索遍历所有包名检查系统应用身份时读到 null 直接崩了。
- 顺便给假 `ApplicationInfo` 加了 `sourceDir`，避免其他可能读这个字段的代码再踩空。

在小米 17 Ultra 和小米 13 Ultra 上确认过，设置搜索不再闪退，路由管理跳转也正常。

### English

v1.2.5 fixes a crash when searching in Settings.

Thanks to [@Rakau](https://github.com/Rakau) and [@cow-your-sister](https://github.com/cow-your-sister) for reporting in [#1](https://github.com/DuhMatt/Fxxk-MiBrowser/issues/1).

- The fake `PackageInfo` returned by the module was missing its `applicationInfo` field. When Settings' search thread iterates all packages and reads `applicationInfo.flags`, the null field caused a `NullPointerException`.
- Added `sourceDir` to the fake `ApplicationInfo` as well, in case other code paths read it.

Confirmed on Xiaomi 17 Ultra and Xiaomi 13 Ultra. Settings search no longer crashes, and the Xiaomi router management redirect still works.

## v1.2.4

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
