# Fxxk-MiBrowser

<p align="center">
  <a href="https://github.com/DuhMatt/Fxxk-MiBrowser/releases/latest">
    <img src="https://img.shields.io/github/v/release/DuhMatt/Fxxk-MiBrowser?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC%20%2F%20LATEST&style=for-the-badge&color=ffffff&labelColor=000000&cacheSeconds=300&ts=202607051050" alt="最新版本 / LATEST">
  </a>
  <a href="https://github.com/DuhMatt/Fxxk-MiBrowser/releases/latest">
    <img src="https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/DuhMatt/Fxxk-MiBrowser/badge-data/downloads.json&style=for-the-badge&labelColor=000000&color=ffffff&cacheSeconds=300&ts=202607040640" alt="下载量 / DOWNLOADS">
  </a>
</p>
<p align="center">
  <a href="https://github.com/DuhMatt/Fxxk-MiBrowser/issues/new/choose">
    <img src="https://img.shields.io/static/v1?label=%E6%8F%90%E4%BA%A4%20ISSUE%20%2F%20OPEN%20ISSUE&message=%E5%8F%8D%E9%A6%88%20%2F%20REPORT&style=for-the-badge&color=ffffff&labelColor=000000" alt="提交 ISSUE / OPEN ISSUE">
  </a>
</p>

防止 HyperOS 强制使用小米浏览器打开链接，改为调用系统默认浏览器。
Prevent HyperOS from forcing links into Xiaomi Browser; redirect to the system default browser.

本项目xposed仓库地址：https://github.com/Xposed-Modules-Repo/com.hyperosfix.browser

## 中文说明

这是一个用于 HyperOS 的 LSPosed 模块。它只解决一个核心问题：当小米系统或系统应用拿到网页链接时，不应该强制调用小米浏览器，也不应该在小米浏览器已卸载或禁用时跳到小米应用商店的浏览器下载页，而应该交给用户在系统设置里选择的默认浏览器。

模块不会把链接硬编码到 Chrome、Edge、Firefox、Via 或任何固定浏览器。它会尽量恢复原始网页链接，清掉指向小米浏览器 / 小米应用商店的强制目标，然后让 Android 按当前默认浏览器设置继续处理。如果系统没有默认浏览器，则回到系统自己的浏览器选择器。

### 主要修复场景

1. 小米互传分享网页链接时，系统强制调用小米浏览器。
2. 系统设置里连接小米路由器后，“管理小米路由”入口强制跳转小米浏览器。
3. 小爱识屏 / 复制直达识别到网页链接后，点击链接仍然调用小米浏览器。

### 兼容性说明

v1.3.0 已完成以下设备与组件版本组合的兼容性验证。小米系统组件和系统版本可以独立更新，使用时请以设备实际版本为准。

小爱识屏适配超级小爱 `8.2.3.1616` 的 `com.xiaomi.voiceassistant.utils.t2.isIntentAvailable()` 链路；复制直达适配小米澎湃 AI 引擎 `4.12.16` 的网页卡片渲染链路。识别到网页链接后，模块会恢复真实 HTTP(S) 地址并交给系统默认浏览器，同时同步复制直达卡片的动作、名称和图标。

同理，其他作用域 app 目前虽然还没有收到明确的“旧版本导致 bug”反馈，但如果遇到相关问题，也建议先去小米应用商店把各个作用域 app 更新到最新版本后再测试。提交反馈时请附上系统、应用和 LSPosed 版本，以及相关日志。

### 计划加入的功能
  - 修复传送门搜索功能的浏览器跳转逻辑
    
  *由于本项目一开始的目的是自用，部分功能在其他模块已经支持了的就暂时没有做，如果有什么别的和小米浏览器相关的跳转功能想要增加支持的，欢迎提issue*

### 效果截图

![小米互传链接通知显示默认浏览器图标](assets/mishare-browser-icon-v1.2.6.png)

小米超级岛浏览器图标替换功能来自 [@189521394](https://github.com/189521394) 在 [#2](https://github.com/DuhMatt/Fxxk-MiBrowser/issues/2) 提出的建议，并参考了原项目 [com.fuckXiaomi.hookBrowser](https://github.com/Xposed-Modules-Repo/com.fuckXiaomi.hookBrowser) 的思路，在此感谢。

### 处理方式

- 只处理网页链接，例如 `http://` 和 `https://`。
- 移除 Intent 中指向小米浏览器的固定包名或组件。
- 识别小米应用商店的浏览器下载页跳转，例如：

```text
market://details?id=com.android.browser
mimarket://details?id=com.android.browser
```

- 尝试从小米互传、小米路由入口或小爱识屏链路里恢复原始 URL。
- 将恢复后的网页链接交给用户设置的系统默认浏览器。
- 避免影响文件、电话、短信、地图、应用私有 scheme 等非网页 Intent。

### 已测试环境

以下信息来自实机和 LSPosed 管理器：

| 设备 / Device | 系统版本 / System build | Android | 超级小爱 / Super XiaoAi | 小米澎湃 AI 引擎 / Xiaomi HyperAI |
| --- | --- | --- | --- | --- |
| `nezha` | `OS4.0.0.21.XPACNXM` | 17 | `8.2.3.1616` | `4.12.16`（versionCode `2030041216`） |
| `ishtar` | `OS3.0.307.0.WMACNXM` | 16 | `7.13.33.0017` | `3.63.1`（versionCode `2030036301`） |

v1.3.0 的复制直达及相关网页跳转路径已在上述环境完成验证。

小米互传场景，实测能从接收数据里恢复原始网页链接：

```text
点击小米互传接收通知
-> tap_recv_data
-> com.miui.mishare.tap.TapData.h
-> 原始 https 链接
-> 用户设置的默认浏览器
```

“管理小米路由”场景，系统原本会把路由器后台地址交给小米浏览器：

```text
http://192.168.1.1
-> com.android.browser
```

模块启用后会改为：

```text
http://192.168.1.1
-> 用户设置的默认浏览器
```

小爱识屏场景，实测能从小米浏览器下载页链路恢复识别到的网页链接：

```text
mimarket://details?id=com.android.browser
-> https://baidu.com
-> 用户设置的默认浏览器
```

### 使用要求

- 已 root 的 Android 设备
- LSPosed
- HyperOS 或 MIUI
- 系统里已经设置好你想使用的默认浏览器

推荐 LSPosed 作用域：

- 系统框架 (`android`)
- 小米互传 / MiShare (`com.miui.mishare.connectivity`)
- 小米应用商店 (`com.xiaomi.market`)
- 小米浏览器 (`com.android.browser`)，如果设备上存在
- 设置 (`com.android.settings`)，用于 Wi-Fi 详情页的“小米路由”入口
- HyperAI Engine (`com.xiaomi.aicr`)，用于剪贴板识别和部分识屏链路
- 超级小爱 / 小爱同学 (`com.miui.voiceassist`)，用于小爱识屏
- 翻译 (`com.xiaomi.aiasst.vision`)，部分 HyperOS 版本可能使用

### 安装

普通用户建议直接到 [Releases](https://github.com/DuhMatt/Fxxk-MiBrowser/releases/latest) 下载已签名的 APK。

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

如果存在本地 `signing/release-keystore.properties`，`assembleRelease` 会使用项目 Release keystore 签名；分发前请确认签名配置和产物来源。

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

看到 `Default browser found` 和 `Redirecting to`，通常说明模块已经把链接交回给系统默认浏览器，而不是继续走小米浏览器。

### 注意事项

HyperOS / MIUI 的内部实现经常变。这个模块只保证在上面列出的设备和系统版本上实测可用；如果换系统版本后失效，通常要重新看 LSPosed 和 logcat 日志，找到新的跳转链路再补 hook。

## English

Current version: `1.3.0`

This is an LSPosed module for HyperOS. It fixes one core problem: when Xiaomi system components receive a web link, they should not force it into Xiaomi Browser, and they should not open Xiaomi Market's browser download page when Xiaomi Browser is removed or disabled. The link should go to the browser the user selected as the Android default browser.

The module does not hard-code Chrome, Edge, Firefox, Via, or any other browser. It tries to recover the original web URL, removes forced Xiaomi Browser / Xiaomi Market targets, and lets Android continue with the current default-browser setting. If no default browser is set, Android's normal browser chooser is used.

### Main Fixed Scenarios

1. Mi Share opens shared web links with Xiaomi Browser.
2. The "Manage Xiaomi router" entry in system Wi-Fi settings opens Xiaomi Browser.
3. Super XiaoAi screen recognition / Clipboard shortcut opens recognized web links with Xiaomi Browser.

### Compatibility

v1.3.0 compatibility was verified on the two physical-device environments listed in the compatibility matrix above. Xiaomi system components and system versions can update independently, so users should verify the actual component versions on their devices.

The screen-recognition hook targets Super XiaoAi `8.2.3.1616` and its `com.xiaomi.voiceassistant.utils.t2.isIntentAvailable()` path. The Copy Direct path covers Xiaomi HyperAI `4.12.16` and its web-card rendering boundary. Recognized HTTP(S) links are recovered and handed to the system default browser; Copy Direct web cards also receive synchronized action, label, and icon metadata.

The same advice applies to other scoped Xiaomi apps as well. There are no confirmed old-version bugs for those apps yet, but if you run into related problems, it is still worth updating the scoped apps from Xiaomi Market before reporting the issue. Please include the system, app, and LSPosed versions plus relevant logs in a report.

### Planned Features
  - Fix the browser redirection logic for the “Portal Search” feature


### What It Does

- Handles only web links, such as `http://` and `https://`.
- Removes fixed Intent packages or components that point to Xiaomi Browser.
- Detects Xiaomi Market browser download-page redirects, for example:

```text
market://details?id=com.android.browser
mimarket://details?id=com.android.browser
```

- Tries to recover the original URL from Mi Share, Xiaomi router settings, or XiaoAi screen-recognition flows.
- Sends the recovered web link to the user's system default browser.
- Avoids touching files, phone links, SMS links, maps, app-private schemes, and other non-web Intents.

### Tested Environment

The bilingual compatibility matrix above records the two physical-device environments used for v1.3.0 verification.

Observed Mi Share recovery path:

```text
Mi Share notification click
-> tap_recv_data
-> com.miui.mishare.tap.TapData.h
-> original https URL
-> user's default browser
```

Observed Xiaomi router path:

```text
http://192.168.1.1
-> com.android.browser
```

With the module enabled:

```text
http://192.168.1.1
-> user's default browser
```

Observed XiaoAi screen-recognition recovery path:

```text
mimarket://details?id=com.android.browser
-> https://baidu.com
-> user's default browser
```

### Requirements

- Rooted Android device
- LSPosed
- HyperOS or MIUI
- A browser already selected as the system default browser

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

For normal use, download the signed APK from [Releases](https://github.com/DuhMatt/Fxxk-MiBrowser/releases/latest).

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

When `signing/release-keystore.properties` is present, `assembleRelease` signs the APK with the project's Release keystore. Verify the signing configuration and artifact source before distribution.

### Debugging

Useful log tags:

```text
HyperOSBrowserFix_Main
HyperOSBrowserFix_Intent
HyperOSBrowserFix_Resolver
```

Useful log lines:

```text
Cached Xiaomi source URL
Recovered URL from object field
Recovered original URL from Xiaomi source cache
Default browser found
Redirecting to
```

When `Default browser found` and `Redirecting to` appear, the module has usually handed the link back to the system default browser instead of continuing through Xiaomi Browser.

### Notes

HyperOS and MIUI internals change often. This module is tested on the device and build listed above. If it stops working on another build, the next step is to inspect LSPosed and logcat output and update the hook for the new launch path.

## License

MIT
