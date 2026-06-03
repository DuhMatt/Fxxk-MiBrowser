# Fxxk-MiBrowser

当前版本：`1.3`

## 中文说明

这是一个给 HyperOS / MIUI 用的 LSPosed 小模块，主要解决一个很烦人的问题：系统明明有默认浏览器，小米互传、小爱识屏等系统功能收到网页链接时却还是想把链接交给小米浏览器；如果小米浏览器被卸载或禁用，还可能跳到小米应用商店的下载页。

常见的跳转长这样：

```text
market://details?id=com.android.browser
```

这个模块会在跳转发生前把原始网页链接找回来，再交给系统里已经设置好的默认浏览器。

从 `1.1` 开始，它也会处理 Wi-Fi 详情页里的“管理小米路由”。如果设置页把路由器后台地址强制丢给小米浏览器，比如 `http://192.168.1.1`，模块会改用默认浏览器打开。

从 `1.3` 开始，它会处理小爱识屏 / 超级小爱识别屏幕链接后的跳转。如果小爱把链接改成小米浏览器或小米应用商店下载页，模块会从识屏结果对象里恢复刚识别到的原始网页链接，再交给默认浏览器。

### 功能

- 拦截 MiShare (`com.miui.mishare.connectivity`) 打开网页链接的流程。
- 清理指向小米浏览器或小米应用商店的强制跳转。
- 从 MiShare 的 `TapRecvData` / `TapData` 对象里恢复原始 URL。
- 修复 Wi-Fi 详情页“管理小米路由”强制使用小米浏览器的问题。
- 尝试修复小爱识屏 / 超级小爱识别屏幕链接后强制使用小米浏览器的问题。
- 尽量只处理网页链接，不动文件、电话、短信、地图和应用自己的私有 scheme。

### 版本更新

#### 1.3

- 增加小爱识屏 / 超级小爱相关作用域：`com.miui.voiceassist`、`com.xiaomi.aicr`、`com.xiaomi.aiasst.vision`。
- 把原来的 MiShare 专用 URL 缓存扩展为 Xiaomi 系统组件通用缓存。
- 当小爱识屏链路已经变成 `mimarket://details?id=com.android.browser` 时，从识屏结果对象里恢复原始 URL 并打开默认浏览器。
- 过滤小爱自身图标资源、代码常量和 Android 包名，避免误打开 `https://` 或 `https://com.android.browser`。

#### 1.1

- 增加 Wi-Fi 详情页“管理小米路由”的处理。
- 当系统试图用小米浏览器打开 `miwifi.com` 或内网网关地址时，改交给默认浏览器。
- 已用 Via (`mark.via`) 作为默认浏览器测试过 `http://192.168.1.1`。

### 已测试环境

以下信息来自实机和 LSPosed 管理器：

| 项目 | 值 |
| --- | --- |
| 厂商 | Xiaomi |
| 设备型号 | `25128PNA1C` |
| 设备代号 | `nezha` |
| Android 版本 | `16` |
| Android SDK | `36` |
| HyperOS 版本名 | `OS3.0` |
| HyperOS 增量版本 | `OS3.0.307.0.WPACNXM` |
| 系统构建版本 | `16OS3.1.260514.221906302.QCPECN.S` |
| LSPosed | `2.0.2 (7668)` |
| Xposed API | `101` |
| LSPosed 管理器包名 | `org.lsposed.manager` |
| Xposed API 调用保护 | 已启用 |
| Dex 优化器包装 | 支持 |
| 测试浏览器 | Via (`mark.via`) |

MiShare 场景里，实测能恢复到这条路径：

```text
点击 MiShare 接收通知
-> tap_recv_data
-> com.miui.mishare.tap.TapData.h
-> 原始 https 链接
-> 默认浏览器
```

“管理小米路由”场景里，实测跳转会从：

```text
http://192.168.1.1
-> com.android.browser
```

改成：

```text
http://192.168.1.1
-> mark.via
```

### 使用要求

- 已 root 的 Android 设备
- LSPosed
- 带 MiShare / 小米互传的 HyperOS 或 MIUI 设备

推荐 LSPosed 作用域：

- 系统框架 (`android`)
- 小米互传 / MiShare (`com.miui.mishare.connectivity`)
- 小米应用商店 (`com.xiaomi.market`)
- 小米浏览器 (`com.android.browser`)，如果设备上存在
- 设置 (`com.android.settings`)，用于 Wi-Fi 详情页的“小米路由”入口
- HyperAI Engine (`com.xiaomi.aicr`)，用于剪贴板识别和部分识屏链路
- 超级小爱 / 小爱同学 (`com.miui.voiceassist`)，用于小爱识屏
- AI 视觉助手 (`com.xiaomi.aiasst.vision`)，部分 HyperOS 版本可能使用

### 安装

普通用户建议直接到 [Releases](https://github.com/DuhMatt/Fxxk-MiBrowser/releases) 下载已签名的 APK。

安装后，在 LSPosed 里启用模块并选择上面的作用域。改完作用域后最好重启手机；只强制停止相关应用有时也能生效，但不如重启稳。

### 构建

Debug 构建：

```bash
./gradlew assembleDebug
```

Release 构建：

```bash
./gradlew assembleRelease
```

默认生成的 release APK 没有签名。如果要分发，需要自己签名。

### 调试

常用日志 tag：

```text
HyperOSBrowserFix_Main
HyperOSBrowserFix_Intent
HyperOSBrowserFix_Resolver
```

比较有用的日志：

```text
Cached Xiaomi source URL
Recovered URL from object field
Recovered original URL from Xiaomi source cache
Default browser found
Redirecting to
```

### 注意事项

HyperOS / MIUI 的内部实现经常变。这个模块只保证在上面列出的设备和系统版本上实测可用；如果换系统版本后失效，通常要重新看 LSPosed 和 logcat 日志，找到新的跳转链路再补 hook。

## English

Current version: `1.3`

This is a small LSPosed module for HyperOS / MIUI. It fixes one specific annoyance: Mi Share, XiaoAi Screen Recognition, and other Xiaomi system features may force received web links through Xiaomi Browser, even when the user has already chosen another default browser. If Xiaomi Browser is removed or disabled, the same flow may end up on Xiaomi Market's browser download page instead.

The fallback often looks like this:

```text
market://details?id=com.android.browser
```

The module catches that path, recovers the original web URL, and opens it with the system default browser.

Starting with `1.1`, it also handles the "Manage Xiaomi router" entry in Wi-Fi details. If Settings tries to send the router admin page to Xiaomi Browser, such as `http://192.168.1.1`, the module sends it to the default browser instead.

Starting with `1.3`, it also handles XiaoAi / Super XiaoAi screen-recognition links. If the recognized URL is rewritten to Xiaomi Browser or Xiaomi Market, the module restores the original URL from the screen-recognition result object and opens it with the default browser.

### Features

- Intercepts Mi Share (`com.miui.mishare.connectivity`) web-link launches.
- Removes forced Xiaomi Browser or Xiaomi Market targets.
- Recovers the original URL from Mi Share's `TapRecvData` / `TapData` objects.
- Fixes the Wi-Fi details "Manage Xiaomi router" entry when it is forced to Xiaomi Browser.
- Tries to fix XiaoAi / Super XiaoAi screen-recognition links forced to Xiaomi Browser.
- Leaves non-web intents alone as much as possible, including files, phone links, SMS, maps, and app-specific schemes.

### Changelog

#### 1.3

- Added XiaoAi / Super XiaoAi related scopes: `com.miui.voiceassist`, `com.xiaomi.aicr`, and `com.xiaomi.aiasst.vision`.
- Expanded the Mi Share-only URL cache into a generic Xiaomi system-component URL cache.
- When the XiaoAi path has already become `mimarket://details?id=com.android.browser`, restores the original URL from the screen-recognition result object and opens the default browser.
- Filters XiaoAi icon resources, code constants, and Android package names to avoid opening `https://` or `https://com.android.browser`.

#### 1.1

- Added support for the Wi-Fi details "Manage Xiaomi router" entry.
- Redirects `miwifi.com` and private gateway admin URLs to the default browser when Xiaomi Browser is forced.
- Tested with Via (`mark.via`) as the default browser and `http://192.168.1.1` as the router admin entry.

### Tested Environment

The values below were checked on a real device and in LSPosed Manager:

| Item | Value |
| --- | --- |
| Manufacturer | Xiaomi |
| Device model | `25128PNA1C` |
| Device codename | `nezha` |
| Android version | `16` |
| Android SDK | `36` |
| HyperOS version name | `OS3.0` |
| HyperOS incremental version | `OS3.0.307.0.WPACNXM` |
| System build version | `16OS3.1.260514.221906302.QCPECN.S` |
| LSPosed | `2.0.2 (7668)` |
| Xposed API | `101` |
| LSPosed manager package | `org.lsposed.manager` |
| Xposed API call protection | Enabled |
| Dex optimizer wrapper | Supported |
| Browser tested | Via (`mark.via`) |

Observed Mi Share recovery path:

```text
Mi Share notification click
-> tap_recv_data
-> com.miui.mishare.tap.TapData.h
-> original https URL
-> default browser
```

Observed Xiaomi router path:

```text
http://192.168.1.1
-> com.android.browser
```

becomes:

```text
http://192.168.1.1
-> mark.via
```

### Requirements

- Rooted Android device
- LSPosed
- HyperOS or MIUI device with Mi Share

Recommended LSPosed scope:

- System Framework (`android`)
- Mi Share (`com.miui.mishare.connectivity`)
- Xiaomi Market (`com.xiaomi.market`)
- Xiaomi Browser (`com.android.browser`), if present on the device
- Settings (`com.android.settings`), for the Wi-Fi details Xiaomi router entry
- HyperAI Engine (`com.xiaomi.aicr`), for clipboard recognition and some screen-recognition flows
- Super XiaoAi / Mi AI (`com.miui.voiceassist`), for XiaoAi screen recognition
- AI vision assistant (`com.xiaomi.aiasst.vision`), used by some HyperOS builds

### Install

For normal use, download the signed APK from [Releases](https://github.com/DuhMatt/Fxxk-MiBrowser/releases).

After installing it, enable the module in LSPosed and select the scopes above. A full reboot is the cleanest way to apply scope changes; force-stopping the scoped apps may work, but rebooting is less fiddly.

### Build

Debug build:

```bash
./gradlew assembleDebug
```

Release build:

```bash
./gradlew assembleRelease
```

The default release APK is unsigned. Sign it yourself before distributing it.

### Debugging

Useful log tags:

```text
HyperOSBrowserFix_Main
HyperOSBrowserFix_Intent
HyperOSBrowserFix_Resolver
```

Useful log lines:

```text
Cached Mi Share URL
Recovered URL from object field
Recovered original URL from Mi Share cache
Default browser found
Redirecting to
```

### Notes

HyperOS and MIUI internals change often. This module is tested on the device and build listed above. If it stops working on another build, the next step is to inspect LSPosed and logcat output and update the hook for the new launch path.

## License

MIT
