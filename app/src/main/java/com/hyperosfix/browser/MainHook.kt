package com.hyperosfix.browser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.hyperosfix.browser.BuildConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed module entry point — diagnostic + interception version.
 *
 * Hooks every reachable startActivity variant and logs ALL calls
 * so we can see what's actually happening on HyperOS 3.
 */
class MainHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "HyperOSBrowserFix_Main"

        /** Set to true to log EVERY startActivity call (very verbose) */
        private val DIAGNOSTIC_LOG_ALL = BuildConfig.DEBUG
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName
        Log.i(TAG, "Module loaded in process: $pkg")
        XposedBridge.log("[$TAG] Loaded in: $pkg")

        // ── Per-app hooks for known Xiaomi apps ──────────────────────────
        // These hook specific MIUI classes that aren't accessible from the
        // system ClassLoader. Only relevant when the target app is in scope.

        if (pkg == XiaomiPackageList.AI_ENGINE) {
            hookXiaomiAiEngine(lpparam)
        }

        if (pkg == XiaomiPackageList.VOICE_ASSIST) {
            hookXiaomiVoiceAssist(lpparam)
        }

        if (pkg == XiaomiPackageList.AI_ENGINE ||
            pkg == XiaomiPackageList.VOICE_ASSIST ||
            pkg == XiaomiPackageList.AI_ASSIST_VISION) {
            hookXiaomiUrlSourceMethods(lpparam, pkg)
        }

        // ── Mi Share specific hooks ──────────────────────────────────────
        if (pkg == XiaomiPackageList.MI_SHARE) {
            hookMiShareService(lpparam)
        }

        // ── PackageManager hooks: fake Xiaomi Browser as "installed" ─────
        // This prevents Mi Share and other system apps from converting
        // HTTP URLs into market:// download-page links. If the system thinks
        // the browser is installed, it sends the original web URL instead.
        hookPackageManager(lpparam)

        // ── PendingIntent hooks: intercept URL conversion at creation time ─
        // Mi Share pre-converts HTTP URLs to market:// PendingIntent before
        // we can intercept them in startActivity. Hook PendingIntent.getActivity
        // to catch the original URL before it's lost.
        if (pkg == XiaomiPackageList.MI_SHARE || XiaomiPackageList.isXiaomiSystemApp(pkg)) {
            hookPendingIntentCreation()
        }

        // ── Framework-level hooks (catch-all for all processes) ──────────
        // Try BOTH classloaders — system for framework, lpparam for app-specific
        val classLoaders = listOfNotNull(
            ClassLoader.getSystemClassLoader(),
            lpparam.classLoader
        ).distinct()

        for (cl in classLoaders) {
            val loaderName = if (cl === lpparam.classLoader) "app" else "system"
            tryHookAll(cl, loaderName)
        }
    }

    private fun tryHookAll(classLoader: ClassLoader, loaderLabel: String) {
        // Hook 1: ContextImpl.startActivity(Intent, Bundle) — primary
        tryHook(
            classLoader, loaderLabel,
            "android.app.ContextImpl",
            "startActivity",
            arrayOf(Intent::class.java, Bundle::class.java)
        ) { param ->
            val intent = param.args[0] as? Intent
            val options = param.args[1] as? Bundle
            val ctx = param.thisObject as? Context
            diagnosticLog(ctx, intent, "ContextImpl.startActivity(I,B)")
            IntentInterceptor.onStartActivity(intent, ctx, options, param)
        }

        // Hook 2: ContextImpl.startActivity(Intent) — simpler overload
        tryHook(
            classLoader, loaderLabel,
            "android.app.ContextImpl",
            "startActivity",
            arrayOf(Intent::class.java)
        ) { param ->
            val intent = param.args[0] as? Intent
            val ctx = param.thisObject as? Context
            diagnosticLog(ctx, intent, "ContextImpl.startActivity(I)")
            IntentInterceptor.onStartActivity(intent, ctx, null, param)
        }

        // Hook 3: Activity.startActivity(Intent, Bundle)
        tryHook(
            classLoader, loaderLabel,
            "android.app.Activity",
            "startActivity",
            arrayOf(Intent::class.java, Bundle::class.java)
        ) { param ->
            val intent = param.args[0] as? Intent
            val ctx = param.thisObject as? Context
            diagnosticLog(ctx, intent, "Activity.startActivity(I,B)")
            IntentInterceptor.onStartActivity(intent, ctx, param.args[1] as? Bundle, param)
        }

        // Hook 4: Activity.startActivity(Intent)
        tryHook(
            classLoader, loaderLabel,
            "android.app.Activity",
            "startActivity",
            arrayOf(Intent::class.java)
        ) { param ->
            val intent = param.args[0] as? Intent
            val ctx = param.thisObject as? Context
            diagnosticLog(ctx, intent, "Activity.startActivity(I)")
            IntentInterceptor.onStartActivity(intent, ctx, null, param)
        }

        // Hook 5: Activity.startActivityForResult(Intent, int, Bundle)
        tryHook(
            classLoader, loaderLabel,
            "android.app.Activity",
            "startActivityForResult",
            arrayOf(Intent::class.java, Int::class.javaPrimitiveType!!, Bundle::class.java)
        ) { param ->
            val intent = param.args[0] as? Intent
            val ctx = param.thisObject as? Context
            diagnosticLog(ctx, intent, "Activity.startActivityForResult(I,i,B)")
            IntentInterceptor.onStartActivity(intent, ctx, param.args[2] as? Bundle, param)
        }

        // Hook 6: ContextWrapper.startActivity(Intent, Bundle)
        tryHook(
            classLoader, loaderLabel,
            "android.content.ContextWrapper",
            "startActivity",
            arrayOf(Intent::class.java, Bundle::class.java)
        ) { param ->
            val intent = param.args[0] as? Intent
            val ctx = param.thisObject as? Context
            diagnosticLog(ctx, intent, "ContextWrapper.startActivity(I,B)")
            IntentInterceptor.onStartActivity(intent, ctx, param.args[1] as? Bundle, param)
        }

        // Hook 7: Instrumentation.execStartActivity (7-param)
        tryHook(
            classLoader, loaderLabel,
            "android.app.Instrumentation",
            "execStartActivity",
            arrayOf(
                Context::class.java,
                "android.os.IBinder",
                "android.os.IBinder",
                Activity::class.java,
                Intent::class.java,
                Int::class.javaPrimitiveType!!,
                Bundle::class.java
            )
        ) { param ->
            val ctx = param.args[0] as? Context
            val intent = param.args[4] as? Intent
            diagnosticLog(ctx, intent, "Instrumentation.execStartActivity(7)")
            IntentInterceptor.onExecStartActivity(ctx, intent, param)
        }

        // Hook 8: Instrumentation.execStartActivity (6-param, older API)
        tryHook(
            classLoader, loaderLabel,
            "android.app.Instrumentation",
            "execStartActivity",
            arrayOf(
                Context::class.java,
                "android.os.IBinder",
                "android.os.IBinder",
                Activity::class.java,
                Intent::class.java,
                Int::class.javaPrimitiveType!!
            )
        ) { param ->
            val ctx = param.args[0] as? Context
            val intent = param.args[4] as? Intent
            diagnosticLog(ctx, intent, "Instrumentation.execStartActivity(6)")
            IntentInterceptor.onExecStartActivity(ctx, intent, param)
        }

        // Hook 9: Instrumentation.execStartActivity (5-param, even older)
        tryHook(
            classLoader, loaderLabel,
            "android.app.Instrumentation",
            "execStartActivity",
            arrayOf(
                Context::class.java,
                "android.os.IBinder",
                "android.os.IBinder",
                Intent::class.java,
                Int::class.javaPrimitiveType!!
            )
        ) { param ->
            val ctx = param.args[0] as? Context
            val intent = param.args[3] as? Intent
            diagnosticLog(ctx, intent, "Instrumentation.execStartActivity(5)")
            IntentInterceptor.onExecStartActivity(ctx, intent, param)
        }

        // Hook 10: Context.startActivities(Intent[], Bundle) — batch launch
        tryHook(
            classLoader, loaderLabel,
            "android.app.ContextImpl",
            "startActivities",
            arrayOf(Array<Intent>::class.java, Bundle::class.java)
        ) { param ->
            val intents = param.args[0] as? Array<*>
            val ctx = param.thisObject as? Context
            if (intents != null) {
                for (i in intents.indices) {
                    diagnosticLog(ctx, intents[i] as? Intent, "ContextImpl.startActivities[$i]")
                }
            }
        }

        // ── HyperOS-specific hooks ───────────────────────────────────────
        // Mi Share / Mi Mover may use custom notification-click handlers.

        // Hook 11: Try MIUI-specific context wrapper
        tryHook(
            classLoader, loaderLabel,
            "miui.util.ContextWrapper",
            "startActivity",
            arrayOf(Intent::class.java, Bundle::class.java)
        ) { param ->
            val intent = param.args[0] as? Intent
            val ctx = param.thisObject as? Context
            diagnosticLog(ctx, intent, "miui.ContextWrapper.startActivity")
            IntentInterceptor.onStartActivity(intent, ctx, param.args[1] as? Bundle, param)
        }

        // Hook 12: Try MIUI Activity starter
        tryHook(
            classLoader, loaderLabel,
            "android.miui.ActivityStarter",
            "startActivity",
            arrayOf(Intent::class.java)
        ) { param ->
            val intent = param.args[0] as? Intent
            diagnosticLog(null, intent, "miui.ActivityStarter.startActivity")
            IntentInterceptor.onStartActivity(intent, null, null, param)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun tryHook(
        classLoader: ClassLoader,
        loaderLabel: String,
        className: String,
        methodName: String,
        paramTypes: Array<Any>,
        callback: (XC_MethodHook.MethodHookParam) -> Unit
    ) {
        try {
            val clazz = XposedHelpers.findClass(className, classLoader)
            val method = XposedHelpers.findMethodExact(
                clazz, methodName, *paramTypes
            )
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    callback(param)
                }
            })
            Log.i(TAG, "[$loaderLabel] Hooked $className.$methodName")
            XposedBridge.log("[$TAG] Hooked $className.$methodName (loader=$loaderLabel)")
        } catch (e: ClassNotFoundException) {
            // Class doesn't exist in this loader — normal
        } catch (e: NoSuchMethodError) {
            Log.w(TAG, "[$loaderLabel] Method not found: $className.$methodName — " +
                "signature may differ in HyperOS 3. Expected: ${paramTypes.joinToString()}")
            XposedBridge.log("[$TAG] Method not found: $className.$methodName")
        } catch (e: Exception) {
            Log.e(TAG, "[$loaderLabel] Failed to hook $className.$methodName", e)
            XposedBridge.log("[$TAG] Hook failed: $className.$methodName — ${e.message}")
        } catch (t: Throwable) {
            // XposedHelpers wraps some errors (e.g. ClassNotFoundError) which extend Error,
            // not Exception. Catch-all for any unexpected Throwable from XposedHelpers.
            Log.w(TAG, "[$loaderLabel] Hook unavailable: $className.$methodName — ${t.javaClass.simpleName}")
            XposedBridge.log("[$TAG] Hook unavailable: $className.$methodName — ${t.javaClass.simpleName}")
        }
    }

    /** Log every startActivity call for diagnostic purposes. */
    private fun diagnosticLog(ctx: Context?, intent: Intent?, source: String) {
        if (!DIAGNOSTIC_LOG_ALL || intent == null) return

        val data: Uri? = intent.data
        val scheme: String? = data?.scheme
        val pkg: String? = intent.`package`
        val comp: String? = intent.component?.flattenToShortString()
        val action: String? = intent.action
        val caller: String? = ctx?.packageName

        // Always log http/https and market:// intents
        val isRelevant = scheme == "http" || scheme == "https" ||
            scheme == "market" || pkg == "com.android.browser" ||
            pkg == "com.xiaomi.market" ||
            comp?.contains("browser") == true ||
            comp?.contains("market") == true

        if (isRelevant || DIAGNOSTIC_LOG_ALL) {
            val flags = "0x${Integer.toHexString(intent.flags)}"
            Log.i(TAG, "[DIAG] $source | caller=$caller | action=$action | " +
                "data=$data | pkg=$pkg | comp=$comp | flags=$flags")

            // Log stack trace for Mi Share market:// calls to find URL conversion point
            if (caller == "com.miui.mishare.connectivity" && scheme == "market") {
                val stack = Throwable().stackTrace
                val relevantFrames = stack.take(15).joinToString("\n  ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
                Log.w(TAG, "[DIAG-STACK] Mi Share market:// call stack:\n  $relevantFrames")
            }

            // Also dump extras for relevant intents
            if (isRelevant && intent.extras != null && !intent.extras!!.isEmpty) {
                for (key in intent.extras!!.keySet()) {
                    val value = intent.extras!!.get(key)
                    Log.d(TAG, "[DIAG]   extra: $key = $value (${value?.javaClass?.simpleName})")
                }
            }

            XposedBridge.log("[$TAG] $source: data=$data pkg=$pkg comp=$comp caller=$caller")
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PackageManager hooks: fake Xiaomi Browser as "installed"
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Hook PackageManager methods to fake Xiaomi Browser as "installed".
     *
     * Mi Share and other system apps may use different APIs to check
     * browser availability: getPackageInfo, getApplicationInfo,
     * resolveActivity, queryIntentActivities, etc.
     * We hook all of them to ensure the browser appears installed.
     */
    private fun hookPackageManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pmClass = "android.app.ApplicationPackageManager"
        val appCl = lpparam.classLoader
        val sysCl = ClassLoader.getSystemClassLoader()

        val effectiveCl = try {
            XposedHelpers.findClass(pmClass, appCl)
            appCl
        } catch (_: Throwable) {
            Log.d(TAG, "[PackageManager] App CL can't find $pmClass, using system CL")
            sysCl
        }

        // 1. getPackageInfo(String, int)
        try {
            XposedHelpers.findAndHookMethod(
                pmClass, effectiveCl,
                "getPackageInfo",
                String::class.java, Int::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkgName = param.args[0] as? String ?: return
                        if (XiaomiPackageList.isXiaomiBrowser(pkgName)) {
                            param.result = buildFakePackageInfo(pkgName)
                            Log.d(TAG, "[PackageManager] Faked getPackageInfo: $pkgName")
                        }
                    }
                })
            Log.i(TAG, "[PackageManager] Hooked getPackageInfo(String, int)")
        } catch (t: Throwable) {
            Log.w(TAG, "[PackageManager] getPackageInfo(String,int): ${t.javaClass.simpleName}")
        }

        // 2. getPackageInfo(String, PackageInfoFlags)
        try {
            XposedHelpers.findAndHookMethod(
                pmClass, effectiveCl,
                "getPackageInfo",
                String::class.java,
                PackageManager.PackageInfoFlags::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkgName = param.args[0] as? String ?: return
                        if (XiaomiPackageList.isXiaomiBrowser(pkgName)) {
                            param.result = buildFakePackageInfo(pkgName)
                            Log.d(TAG, "[PackageManager] Faked getPackageInfo(flags): $pkgName")
                        }
                    }
                })
            Log.i(TAG, "[PackageManager] Hooked getPackageInfo(String, PackageInfoFlags)")
        } catch (t: Throwable) {
            Log.w(TAG, "[PackageManager] getPackageInfo(String,PackageInfoFlags): ${t.javaClass.simpleName}")
        }

        // 3. getApplicationInfo(String, int) — some apps use this instead of getPackageInfo
        try {
            XposedHelpers.findAndHookMethod(
                pmClass, effectiveCl,
                "getApplicationInfo",
                String::class.java, Int::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkgName = param.args[0] as? String ?: return
                        if (XiaomiPackageList.isXiaomiBrowser(pkgName)) {
                            param.result = buildFakeApplicationInfo(pkgName)
                            Log.d(TAG, "[PackageManager] Faked getApplicationInfo: $pkgName")
                        }
                    }
                })
            Log.i(TAG, "[PackageManager] Hooked getApplicationInfo(String, int)")
        } catch (t: Throwable) {
            Log.w(TAG, "[PackageManager] getApplicationInfo: ${t.javaClass.simpleName}")
        }

        // 4. getApplicationInfo(String, ApplicationInfoFlags)
        try {
            XposedHelpers.findAndHookMethod(
                pmClass, effectiveCl,
                "getApplicationInfo",
                String::class.java,
                PackageManager.ApplicationInfoFlags::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkgName = param.args[0] as? String ?: return
                        if (XiaomiPackageList.isXiaomiBrowser(pkgName)) {
                            param.result = buildFakeApplicationInfo(pkgName)
                            Log.d(TAG, "[PackageManager] Faked getApplicationInfo(flags): $pkgName")
                        }
                    }
                })
            Log.i(TAG, "[PackageManager] Hooked getApplicationInfo(String, ApplicationInfoFlags)")
        } catch (t: Throwable) {
            Log.w(TAG, "[PackageManager] getApplicationInfo(flags): ${t.javaClass.simpleName}")
        }

        // 5. getLaunchIntentForPackage — some apps use this to check if a package can be launched
        try {
            XposedHelpers.findAndHookMethod(
                pmClass, effectiveCl,
                "getLaunchIntentForPackage",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val pkgName = param.args[0] as? String ?: return
                        if (XiaomiPackageList.isXiaomiBrowser(pkgName) && param.result == null) {
                            // Return a fake launch intent so the caller thinks the app is launchable
                            val intent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_LAUNCHER)
                                setPackage(pkgName)
                            }
                            param.result = intent
                            Log.d(TAG, "[PackageManager] Faked getLaunchIntentForPackage: $pkgName")
                        }
                    }
                })
            Log.i(TAG, "[PackageManager] Hooked getLaunchIntentForPackage(String)")
        } catch (t: Throwable) {
            Log.w(TAG, "[PackageManager] getLaunchIntentForPackage: ${t.javaClass.simpleName}")
        }
    }

    /**
     * Build a minimal [PackageInfo] that satisfies "is the package installed?" checks.
     */
    private fun buildFakePackageInfo(packageName: String): PackageInfo {
        val pi = PackageInfo()
        pi.packageName = packageName
        pi.versionName = "1.0"
        @Suppress("DEPRECATION")
        pi.versionCode = 1
        return pi
    }

    /**
     * Build a minimal [ApplicationInfo] that satisfies "is the app installed?" checks.
     */
    private fun buildFakeApplicationInfo(packageName: String): android.content.pm.ApplicationInfo {
        val ai = android.content.pm.ApplicationInfo()
        ai.packageName = packageName
        ai.flags = android.content.pm.ApplicationInfo.FLAG_SYSTEM
        return ai
    }

    // ══════════════════════════════════════════════════════════════════════
    // PendingIntent hooks: intercept URL conversion at creation time
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Hook [PendingIntent.getActivity] to intercept the Intent before it's
     * wrapped into a PendingIntent.
     *
     * Mi Share converts HTTP URLs to market:// details links BEFORE calling
     * startActivity, so our startActivity hooks see only the market:// URL.
     * By hooking PendingIntent.getActivity, we can catch the original Intent
     * and redirect it to the default browser before the URL is lost.
     */
    private fun hookPendingIntentCreation() {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.PendingIntent",
                ClassLoader.getSystemClassLoader(),
                "getActivity",
                Context::class.java,
                Int::class.javaPrimitiveType!!,
                Intent::class.java,
                Int::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val intent = param.args[2] as? Intent ?: return
                        val data = intent.data ?: return
                        val scheme = data.scheme

                        // Check if this is a market:// intent targeting Xiaomi Browser
                        if (scheme == "market") {
                            val id = data.getQueryParameter("id")
                            if (XiaomiPackageList.isXiaomiBrowser(id)) {
                                Log.i(TAG, "[PendingIntent] Intercepted market:// for browser: $data")
                                XposedBridge.log("[$TAG] [PendingIntent] Intercepted market:// for browser: $data")

                                val ctx = param.args[0] as? Context ?: return
                                val browser = DefaultBrowserResolver.resolveDefaultBrowser(ctx)

                                val recoveredUrl = IntentInterceptor.recoverUrlForRedirect(intent)
                                if (browser != null && recoveredUrl != null) {
                                    val replacement = Intent(Intent.ACTION_VIEW, recoveredUrl).apply {
                                        addCategory(Intent.CATEGORY_BROWSABLE)
                                        addCategory(Intent.CATEGORY_DEFAULT)
                                        if (browser.isDefault) {
                                            setPackage(browser.packageName)
                                        }
                                    }
                                    // Copy original flags
                                    replacement.flags = intent.flags
                                    replacement.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                                    param.args[2] = replacement
                                    Log.i(TAG, "[PendingIntent] Replaced market:// intent with recovered URL: $recoveredUrl")
                                    XposedBridge.log("[$TAG] [PendingIntent] Replaced with recovered URL: $recoveredUrl")
                                } else if (browser != null) {
                                    Log.w(TAG, "[PendingIntent] No original URL found; keeping market intent instead of opening https://")
                                }
                            }
                        }
                    }
                })
            Log.i(TAG, "[PendingIntent] Hooked PendingIntent.getActivity(Context, int, Intent, int)")
        } catch (t: Throwable) {
            Log.w(TAG, "[PendingIntent] Failed to hook PendingIntent.getActivity: ${t.javaClass.simpleName}")
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Per-app hooks: com.miui.mishare.connectivity (Mi Share)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Hook Mi Share's LyraShareListenerService to intercept the intent
     * data before Mi Share processes it.
     *
     * When a URL is shared via Mi Share, the notification PendingIntent
     * triggers LyraShareListenerService.onStartCommand(). The original URL
     * may be in the service's intent extras before Mi Share converts it
     * to a market:// link.
     */
    private fun hookMiShareService(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cl = lpparam.classLoader
        val serviceName = "com.miui.mishare.connectivity.refactor.lyra.LyraShareListenerService"

        try {
            XposedHelpers.findAndHookMethod(
                serviceName, cl,
                "onStartCommand",
                android.content.Intent::class.java,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val intent = param.args[0] as? Intent ?: return
                        val data = intent.data
                        val action = intent.action
                        Log.i(TAG, "[MiShare] onStartCommand: action=$action, data=$data")
                        IntentInterceptor.rememberMiShareUrl(intent)

                        // Dump ALL extras to find the original URL
                        if (intent.extras != null) {
                            for (key in intent.extras!!.keySet()) {
                                val value = intent.extras!!.get(key)
                                val valueStr = value?.toString()?.take(200)
                                Log.d(TAG, "[MiShare]   extra: $key = $valueStr (${value?.javaClass?.simpleName})")
                            }
                        }
                    }
                })
            Log.i(TAG, "[MiShare] Hooked LyraShareListenerService.onStartCommand")
        } catch (t: Throwable) {
            Log.w(TAG, "[MiShare] Failed to hook onStartCommand: ${t.javaClass.simpleName} — ${t.message}")
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Per-app hooks: cache URLs seen by XiaoAi / AI Engine before rewrites
    // ══════════════════════════════════════════════════════════════════════

    /**
     * XiaoAi Screen Recognition and Xiaomi AI Engine sometimes inspect the
     * screen URL in app-layer objects before later launching a browser or
     * Market intent. Hook methods that receive Intent/String/Uri-like args and
     * cache only real http/https URLs; this does not change method behavior.
     */
    private fun hookXiaomiUrlSourceMethods(
        lpparam: XC_LoadPackage.LoadPackageParam,
        sourcePackage: String
    ) {
        val cl = lpparam.classLoader
        var hookedCount = 0
        val maxHooks = 80

        for (className in XiaomiPackageList.URL_SOURCE_CLASS_CANDIDATES) {
            if (hookedCount >= maxHooks) break

            val clazz = try {
                XposedHelpers.findClass(className, cl)
            } catch (_: Throwable) {
                continue
            }

            for (method in clazz.declaredMethods) {
                if (hookedCount >= maxHooks) break

                val params = method.parameterTypes
                val hasInterestingArg = params.any { type ->
                    type == Intent::class.java ||
                        type == String::class.java ||
                        type == Uri::class.java ||
                        type == Bundle::class.java ||
                        CharSequence::class.java.isAssignableFrom(type)
                }
                if (!hasInterestingArg) continue

                try {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            logUrlSourceArgsIfUseful(sourcePackage, clazz.name, method.name, param.args)
                            for (arg in param.args) {
                                if (!isUrlCarrierArg(arg)) continue
                                IntentInterceptor.rememberWebUrlFromValue(
                                    arg,
                                    "$sourcePackage:${clazz.name}.${method.name}"
                                )
                            }
                        }
                    })
                    hookedCount++
                    Log.i(TAG, "[URL-Source] Hooked ${clazz.name}.${method.name} for $sourcePackage")
                } catch (t: Throwable) {
                    Log.w(TAG, "[URL-Source] Failed ${clazz.name}.${method.name}: ${t.javaClass.simpleName}")
                }
            }
        }

        Log.i(TAG, "[URL-Source] Hooked $hookedCount URL cache methods for $sourcePackage")
        XposedBridge.log("[$TAG] URL-Source: hooked $hookedCount methods for $sourcePackage")
    }

    private fun isUrlCarrierArg(arg: Any?): Boolean {
        return arg is Intent ||
            arg is String ||
            arg is Uri ||
            arg is Bundle ||
            arg is CharSequence
    }

    private fun logUrlSourceArgsIfUseful(
        sourcePackage: String,
        className: String,
        methodName: String,
        args: Array<Any?>
    ) {
        if (!BuildConfig.DEBUG) return

        if (sourcePackage != XiaomiPackageList.VOICE_ASSIST &&
            sourcePackage != XiaomiPackageList.AI_ASSIST_VISION) {
            return
        }

        val interestingMethod = methodName in setOf(
            "openInBrowser",
            "convertUrlToIntent",
            "parseIntent",
            "sendUriOrAndroidIntent",
            "sendIntent",
            "sendIntentByClick",
            "startActivity",
            "startActivitySafely",
            "startActivityWithIntent",
            "setDeepLinkIntent",
            "innerDeepLink",
            "innerScheme",
        )
        if (!interestingMethod) return

        val summary = args.mapIndexedNotNull { index, arg ->
            when (arg) {
                null -> null
                is Intent -> "#$index Intent{action=${arg.action}, data=${arg.data}, pkg=${arg.`package`}, comp=${arg.component}}"
                is Uri -> "#$index Uri{$arg}"
                is CharSequence -> "#$index ${arg.javaClass.simpleName}{${arg.toString().take(240)}}"
                is Bundle -> "#$index Bundle{keys=${arg.keySet().joinToString(limit = 12)}}"
                else -> null
            }
        }
        if (summary.isNotEmpty()) {
            Log.i(TAG, "[URL-Source-Args] $sourcePackage:$className.$methodName ${summary.joinToString(" | ")}")
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Per-app hooks: com.xiaomi.aicr (Xiaomi HyperAI Engine)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Hook the Xiaomi AI Engine (com.xiaomi.aicr) which handles:
     * 1. Clipboard URL recognition — when you copy a link, it pops up a
     *    notification that opens in Xiaomi Browser.
     * 2. Screen recognition / smart content detection URLs.
     *
     * The SmartPasswordUtils class may be obfuscated (e.g. "i26" on HyperOS V816).
     * We try multiple candidate class names and hook by method signature.
     *
     * Key methods:
     * - jumpToXiaoMiBrowser(Context, String) — removed in newer builds;
     *   AI Engine now uses standard startActivity (caught by framework hooks).
     * - isInstallForApp(Context, String) → boolean — fakes "browser installed"
     *   to prevent redirect to market download page.
     */
    private fun hookXiaomiAiEngine(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cl = lpparam.classLoader

        XposedBridge.log("[$TAG] Hooking Xiaomi AI Engine (com.xiaomi.aicr)...")

        // Try each candidate class until one works
        var foundClass = false
        for (className in XiaomiPackageList.CLASS_SMART_PASSWORD_UTILS_CANDIDATES) {
            try {
                val clazz = XposedHelpers.findClass(className, cl)
                foundClass = true
                Log.i(TAG, "[AI-Engine] Found target class: $className")
                XposedBridge.log("[$TAG] AI-Engine: using class $className")

                // Hook isInstallForApp by signature: (Context, String) → boolean
                // The method name may be "isInstallForApp" or obfuscated (e.g. "A")
                hookIsInstallForApp(clazz, className)
                break
            } catch (_: Throwable) {
                Log.d(TAG, "[AI-Engine] Class not found: $className, trying next...")
            }
        }

        if (!foundClass) {
            Log.w(TAG, "[AI-Engine] No SmartPasswordUtils candidate found. " +
                "Framework hooks will still catch startActivity calls.")
            XposedBridge.log("[$TAG] AI-Engine: no SmartPasswordUtils found — relying on framework hooks")
        }

        // Note: jumpToXiaoMiBrowser has been removed in HyperOS V816 / aicr 3.17.3.
        // The AI Engine now uses standard startActivity() which our framework hooks catch.
    }

    /**
     * Hook the isInstallForApp method (or its obfuscated equivalent) by
     * searching for a method with signature (Context, String) → boolean.
     *
     * This is resilient to R8 obfuscation which renames the method
     * (e.g. "isInstallForApp" → "A") but preserves the signature.
     */
    private fun hookIsInstallForApp(clazz: Class<*>, className: String) {
        // Strategy 1: Try the known method name first
        for (methodName in listOf("isInstallForApp", "A")) {
            try {
                XposedHelpers.findAndHookMethod(
                    clazz, methodName,
                    Context::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val pkgToCheck = param.args[1] as? String
                            if (XiaomiPackageList.isXiaomiBrowser(pkgToCheck)) {
                                Log.d(TAG, "[AI-Engine] Faking browser installed for: $pkgToCheck")
                                param.result = true
                            }
                        }
                    })
                Log.i(TAG, "[AI-Engine] Hooked $className.$methodName(Context, String)")
                XposedBridge.log("[$TAG] AI-Engine: hooked $className.$methodName")
                return
            } catch (_: Throwable) {
                // Try next name
            }
        }

        // Strategy 2: Scan all declared methods for (Context, String) → boolean
        // This catches any obfuscated name
        try {
            for (method in clazz.declaredMethods) {
                val params = method.parameterTypes
                if (method.returnType == Boolean::class.javaPrimitiveType &&
                    params.size == 2 &&
                    params[0] == Context::class.java &&
                    params[1] == String::class.java
                ) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val pkgToCheck = param.args[1] as? String
                            if (XiaomiPackageList.isXiaomiBrowser(pkgToCheck)) {
                                Log.d(TAG, "[AI-Engine] Faking browser installed (via scan) for: $pkgToCheck")
                                param.result = true
                            }
                        }
                    })
                    Log.i(TAG, "[AI-Engine] Hooked $className.${method.name}(Context, String) via signature scan")
                    XposedBridge.log("[$TAG] AI-Engine: hooked $className.${method.name} via scan")
                    return
                }
            }
            Log.w(TAG, "[AI-Engine] No (Context, String)→boolean method found in $className")
        } catch (t: Throwable) {
            Log.w(TAG, "[AI-Engine] Signature scan failed for $className: ${t.message}")
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Per-app hooks: com.miui.voiceassist (XiaoAi / Super XiaoAi)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Hook the Xiaomi voice assistant (com.miui.voiceassist) which handles
     * "screen recognition" URLs and voice-command-triggered web links.
     *
     * The target class has changed across HyperOS versions:
     * - Original: com.xiaomi.voiceassistant.utils.b2
     * - Newer: com.xiaomi.voiceassistant.utils.f2
     *
     * Voice Assist now uses standard startActivity — framework hooks suffice.
     * We still try to hook isIntentAvailable and startActivity by signature
     * as a defense-in-depth measure.
     */
    private fun hookXiaomiVoiceAssist(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cl = lpparam.classLoader

        XposedBridge.log("[$TAG] Hooking Voice Assist (com.miui.voiceassist)...")

        var foundClass = false
        for (className in XiaomiPackageList.CLASS_VOICE_ASSIST_CANDIDATES) {
            try {
                val clazz = XposedHelpers.findClass(className, cl)
                foundClass = true
                Log.i(TAG, "[VoiceAssist] Found target class: $className")
                XposedBridge.log("[$TAG] VoiceAssist: using class $className")

                hookVoiceAssistMethods(clazz, className)
                break
            } catch (_: Throwable) {
                Log.d(TAG, "[VoiceAssist] Class not found: $className, trying next...")
            }
        }

        if (!foundClass) {
            Log.w(TAG, "[VoiceAssist] No target class found. Framework hooks will still catch startActivity calls.")
            XposedBridge.log("[$TAG] VoiceAssist: no target class found — relying on framework hooks")
        }
    }

    /**
     * Hook methods in the Voice Assist utility class by scanning for
     * methods that accept Intent and/or Context parameters.
     */
    private fun hookVoiceAssistMethods(clazz: Class<*>, className: String) {
        try {
            for (method in clazz.declaredMethods) {
                val params = method.parameterTypes

                // Hook methods returning boolean that accept an Intent parameter
                // This covers isIntentAvailable and similar checks
                if (method.returnType == Boolean::class.javaPrimitiveType &&
                    params.any { it == Intent::class.java }
                ) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            for (arg in param.args) {
                                if (arg is Intent) {
                                    val pkg = arg.`package`
                                    val comp = arg.component
                                    rewriteXiaomiBrowserDownloadIntent(
                                        arg,
                                        android.app.AndroidAppHelper.currentApplication(),
                                        "[VoiceAssist] ${method.name}"
                                    )

                                    if (XiaomiPackageList.isXiaomiBrowser(pkg) ||
                                        (comp != null && XiaomiPackageList.isXiaomiBrowser(comp.packageName)) ||
                                        (comp != null && comp.packageName.contains("browser"))) {

                                        Log.d(TAG, "[VoiceAssist] Cleaning intent in ${method.name}: pkg=$pkg, comp=$comp")

                                        val browser = DefaultBrowserResolver.resolveDefaultBrowser(
                                            android.app.AndroidAppHelper.currentApplication()
                                        )
                                        if (browser != null && browser.isDefault) {
                                            arg.setPackage(browser.packageName)
                                        } else {
                                            arg.setPackage(null)
                                        }
                                        arg.component = null
                                    }
                                }
                            }
                            // Return true so the intent can proceed
                            param.result = true
                        }
                    })
                    Log.i(TAG, "[VoiceAssist] Hooked $className.${method.name} (returns boolean, has Intent param)")
                }

                // Hook void methods that accept Context + Intent
                // These are the actual URL-launching methods
                if (method.returnType == Void::class.javaPrimitiveType &&
                    params.any { it == Context::class.java } &&
                    params.any { it == Intent::class.java }
                ) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            for (arg in param.args) {
                                if (arg is Intent) {
                                    rewriteXiaomiBrowserDownloadIntent(
                                        arg,
                                        param.args.filterIsInstance<Context>().firstOrNull()
                                            ?: android.app.AndroidAppHelper.currentApplication(),
                                        "[VoiceAssist] ${method.name}"
                                    )

                                    val data = arg.data
                                    if (data != null) {
                                        val scheme = data.scheme
                                        if (scheme == "mi" || (scheme != null && scheme.startsWith("mi"))) {
                                            val recovered = IntentInterceptor.recoverUrlFromMiScheme(arg)
                                            if (recovered != null) {
                                                Log.i(TAG, "[VoiceAssist] Recovered URL from mi:// in ${method.name}: $recovered")
                                                arg.data = recovered
                                            }
                                        }
                                    }

                                    if (XiaomiPackageList.isXiaomiBrowser(arg.`package`)) {
                                        arg.setPackage(null)
                                        arg.component = null
                                    }
                                }
                            }
                        }
                    })
                    Log.i(TAG, "[VoiceAssist] Hooked $className.${method.name} (void, has Context+Intent)")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "[VoiceAssist] Failed to hook methods in $className: ${t.javaClass.simpleName} — ${t.message}", t)
            XposedBridge.log("[$TAG] VoiceAssist hook failed: $className — ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun rewriteXiaomiBrowserDownloadIntent(
        intent: Intent,
        context: Context?,
        source: String
    ): Boolean {
        val data = intent.data ?: return false
        val scheme = data.scheme ?: return false
        if (scheme != "market" && !scheme.startsWith("mi")) return false

        val marketId = data.getQueryParameter("id")
        if (!XiaomiPackageList.isXiaomiBrowser(marketId)) return false

        val recovered = IntentInterceptor.recoverUrlForRedirect(intent)
        if (recovered == null) {
            Log.w(TAG, "$source saw Xiaomi Browser download intent but no original URL was cached: $data")
            return false
        }

        intent.action = Intent.ACTION_VIEW
        intent.data = recovered
        intent.setPackage(null)
        intent.component = null
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.addCategory(Intent.CATEGORY_DEFAULT)

        val browser = context?.let { DefaultBrowserResolver.resolveDefaultBrowser(it) }
        if (browser?.isDefault == true) {
            intent.setPackage(browser.packageName)
        }

        Log.i(TAG, "$source rewrote Xiaomi Browser download intent to: $recovered")
        return true
    }
}
