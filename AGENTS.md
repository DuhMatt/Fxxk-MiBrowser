请帮我设计并实现一个 Android 模块，用于修复小米 / MIUI / HyperOS 系统中“链接强制使用小米浏览器打开”的问题。

我的目标是：当系统、系统应用或小米互传等功能接收到一个网页链接并尝试打开时，不要强制跳转到小米浏览器，也不要在小米浏览器已卸载时跳转到应用商店的小米浏览器下载页，而是应该调用系统设置中用户已经设置好的默认浏览器。

设备环境：

- 手机品牌：小米
- 系统：MIUI / HyperOS
- 已 Root
- 使用 LSPosed 模块方案
- 当前问题场景：我从其他设备通过小米互传发送一个 URL 到小米手机，点击打开后系统只尝试调用小米浏览器。如果小米浏览器已卸载，就会跳转到应用商店的小米浏览器下载页面，而不是调用我安装并设置为默认浏览器的第三方浏览器。

功能需求：

1. 拦截系统中打开网页链接的行为。
2. 判断 Intent 是否是网页链接，例如 ACTION_VIEW + http / https URI。
3. 如果目标包名被强制指定为小米浏览器，例如 com.android.browser 或其他小米浏览器相关包名，则移除这个强制包名。
4. 查询系统当前默认浏览器。
5. 将链接重新交给系统默认浏览器处理。
6. 如果系统没有设置默认浏览器，则弹出系统浏览器选择器，而不是跳转到小米浏览器下载页。
7. 需要避免影响非网页链接，例如 app scheme、文件、电话、短信、地图等 Intent。
8. 需要避免无限循环跳转。
9. 需要尽量兼容 MIUI 和 HyperOS。
10. 请写出完整项目结构、核心代码、AndroidManifest、Gradle 配置和必要的说明。

技术实现方向：

请尝试从以下方向分析并选择最合理的实现方式：

- Hook Android Framework 中的 Intent 启动流程，例如 ActivityTaskManager、ActivityManagerService、Instrumentation、ContextImpl.startActivity 等相关方法。
- Hook 小米互传或系统相关组件中处理 URL 的方法。
- Hook PackageManager / ResolveInfo 相关逻辑，让系统不要把小米浏览器作为固定处理对象。
- Hook Intent，在启动前检查是否存在 setPackage("com.android.browser")、setClassName("com.android.browser", ...)、component 指向小米浏览器等情况，并将其清除。
- 对于跳转到应用商店的小米浏览器下载页的情况，识别对应 Intent，并改为使用默认浏览器打开原始 URL。

请输出：

1. 推荐方案说明。
2. 为什么选择 LSPosed
3. 完整代码。
4. 项目目录结构。
5. 编译和安装步骤。
6. 需要 hook 的包名建议，例如 Android 系统框架、系统桌面、小米互传、小米应用商店、小米浏览器相关包。
7. 如何通过 logcat 调试。
8. 如何判断模块是否生效。
9. 如果某些类名或方法名因 MIUI / HyperOS 版本不同无法确定，请提供一种通过 jadx、logcat 或 LSPosed 日志定位 hook 点的方法。

实现时请注意：

- 不要写成只能打开某一个固定浏览器。
- 应该读取系统默认浏览器，而不是硬编码 Chrome、Edge、Firefox 等包名。
- 不要破坏系统正常的 Intent 分发机制。
- 尽量保持模块轻量。
- 代码需要有注释。
- 如果需要权限或系统 API，请解释原因。
- 如果涉及不同 Android 版本 API 差异，请做兼容处理。

请直接给出可以实际创建项目并尝试编译运行的版本。