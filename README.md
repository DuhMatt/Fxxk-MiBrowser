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

## 主要修复场景 / Main Fixed Scenarios

1. 小米互传分享网页链接时，系统强制调用小米浏览器。
2. 系统设置里连接小米路由器后，“管理小米路由”入口强制跳转小米浏览器。
3. 小爱识屏 / 复制直达识别到网页链接后，点击链接仍然调用小米浏览器。

启用模块后，上述网页链接会交给系统当前设置的默认浏览器。

1. Mi Share forces shared web links into Xiaomi Browser.
2. The “Manage Xiaomi router” entry in Settings forces web links into Xiaomi Browser.
3. XiaoAi screen recognition / Copy Direct opens recognized web links in Xiaomi Browser.

With the module enabled, these web links are handed to the browser currently selected as the system default.

### 效果截图

![小米互传链接通知显示默认浏览器图标](assets/mishare-browser-icon-v1.2.6.png)

小米超级岛浏览器图标替换功能来自 [@189521394](https://github.com/189521394) 在 [#2](https://github.com/DuhMatt/Fxxk-MiBrowser/issues/2) 提出的建议，并参考了原项目 [com.fuckXiaomi.hookBrowser](https://github.com/Xposed-Modules-Repo/com.fuckXiaomi.hookBrowser) 的思路，在此感谢。

## License

MIT
