# Release Notes

## 中文

这个版本的重点是：拦截小米系统对网页链接的强制接管，让链接回到用户自己设置的系统默认浏览器，而不是继续调用小米浏览器。

主要修复三个场景：

1. 小米互传接收到网页链接后强制打开小米浏览器。
2. 系统设置里连接小米路由器时，“管理小米路由”入口强制跳转小米浏览器。
3. 小爱识屏 / 超级小爱识别到网页链接后，点击链接仍然调用小米浏览器。

模块会尽量恢复原始 `http` / `https` 链接，移除指向 `com.android.browser` 的固定目标，并识别小米应用商店的浏览器下载页跳转，例如 `market://details?id=com.android.browser` 或 `mimarket://details?id=com.android.browser`。恢复后的链接会交给 Android 当前的默认浏览器处理；如果系统没有默认浏览器，则使用系统浏览器选择器。

它不会硬编码某个第三方浏览器，也不会替用户决定用 Chrome、Edge、Firefox、Via 或其他浏览器。

## English

This release focuses on one thing: stopping Xiaomi system components from forcing web links into Xiaomi Browser, and sending those links back to the browser the user selected as the Android default browser.

Main fixed scenarios:

1. Mi Share opens received web links with Xiaomi Browser.
2. The "Manage Xiaomi router" entry in system Wi-Fi settings opens Xiaomi Browser.
3. XiaoAi / Super XiaoAi screen recognition opens recognized web links with Xiaomi Browser.

The module tries to recover the original `http` / `https` URL, removes fixed targets pointing to `com.android.browser`, and detects Xiaomi Market browser download-page redirects such as `market://details?id=com.android.browser` or `mimarket://details?id=com.android.browser`. The recovered link is then handed to Android's current default browser; if no default browser is set, Android's normal browser chooser is used.

It does not hard-code any third-party browser and does not choose Chrome, Edge, Firefox, Via, or any other browser for the user.
