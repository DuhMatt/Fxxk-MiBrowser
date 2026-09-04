# Fxxk-MiBrowser

一个用于 HyperOS / MIUI 的 LSPosed 模块，用来阻止小米系统组件强制使用小米浏览器打开网页，并将网页链接交给系统当前设置的默认浏览器。

An LSPosed module for HyperOS / MIUI. It prevents Xiaomi system components from forcing web links into Xiaomi Browser and lets Android open them with the current default browser.

[个人仓库 / Source Repository](https://github.com/DuhMatt/Fxxk-MiBrowser) · [Xposed 仓库 / Xposed Repository](https://github.com/Xposed-Modules-Repo/com.hyperosfix.browser) · [Releases](https://github.com/DuhMatt/Fxxk-MiBrowser/releases/latest)

## 支持范围 / Supported Versions

`v1.3.0` 支持 HyperOS 3 和 HyperOS 4。下表列出已经在真机上验证过的版本组合。超级小爱或小米澎湃 AI 引擎更新后，兼容性需要重新确认。

`v1.3.0` supports HyperOS 3 and HyperOS 4. The matrix below lists the combinations verified on physical devices. Compatibility should be checked again after Super XiaoAi or Xiaomi HyperAI is updated.

| HyperOS | 设备 / Device | 系统版本 / System build | Android | 超级小爱 / Super XiaoAi | 小米澎湃 AI 引擎 / Xiaomi HyperAI |
| --- | --- | --- | --- | --- | --- |
| HyperOS 4 | `nezha` | `OS4.0.0.21.XPACNXM` | 17 | `8.2.3.1616` | `4.12.16`（versionCode `2030041216`） |
| HyperOS 3 | `ishtar` | `OS3.0.307.0.WMACNXM` | 16 | `7.13.33.0017` | `3.63.1`（versionCode `2030036301`） |

## 功能与效果 / What It Does

- 小米互传、设置中的小米路由入口、小爱识屏和复制直达识别到的网页链接，不再强制跳转小米浏览器。
- 点击网页链接时，会打开系统当前设置的默认浏览器。
- 小米浏览器被禁用或卸载时，网页链接不会被转到小米应用商店的浏览器下载页。
- 复制直达识别到的网页链接，点击后也会使用系统默认浏览器打开；卡片上的浏览器名称和图标会随之变化。

- Web links from Mi Share, the Xiaomi router entry in Settings, XiaoAi screen recognition, and Copy Direct are no longer forced into Xiaomi Browser.
- Tapping a web link opens the browser currently selected as the Android default.
- If Xiaomi Browser is disabled or uninstalled, the link is not redirected to Xiaomi Market's browser download page.
- Copy Direct web cards open links in the current default browser, and their browser name and icon follow that choice.

## License

MIT
