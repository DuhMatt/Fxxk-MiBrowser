package com.hyperosfix.browser

import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.hyperosfix.browser.BuildConfig
import com.hyperosfix.browser.ModuleLog as Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import org.json.JSONArray
import org.json.JSONObject

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
            hookActionCoreProviderCall(lpparam.classLoader)
            hookHyperAiCopyDirectCueDataPipeline(lpparam.classLoader)
            hookHyperAiCopyDirectRenderedIcon()
            if (BuildConfig.DEBUG) {
                hookHyperAiActionProtocolDiagnostics(lpparam)
            }
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
            hookMiShareNotificationIcon()
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
        if (BuildConfig.DEBUG) {
            hookPendingIntentSendDiagnostics()
        }

        // ── Framework-level notification icon replacement ────────────────
        // Hook NotificationManager.notify at framework level to replace
        // browser icons in Mi Share and other system notifications
        hookFrameworkNotificationIcon()

        // ── Framework-level hooks (catch-all for all processes) ──────────
        // ponytail: Single classloader — framework classes (ContextImpl, Activity,
        // Instrumentation, ContextWrapper) resolve via parent delegation anyway,
        // and app-specific classes (miui.*) only exist on lpparam.classLoader.
        tryHookAll(lpparam.classLoader)
    }

    // ponytail: "app" removed — single classloader means it's always "app"
    private fun tryHookAll(classLoader: ClassLoader) {
        // Hook 1: ContextImpl.startActivity(Intent, Bundle) — primary
        tryHook(
            classLoader,
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
            classLoader,
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
            classLoader,
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
            classLoader,
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
            classLoader,
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
            classLoader,
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
            classLoader,
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
            classLoader,
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
            classLoader,
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
            classLoader,
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
            classLoader,
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
            classLoader,
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

    // ponytail: removed loaderLabel param — now always "app"
    private fun tryHook(
        classLoader: ClassLoader,
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
            Log.i(TAG, "[app] Hooked $className.$methodName")
            XposedBridge.log("[$TAG] Hooked $className.$methodName")
        } catch (t: Throwable) {
            // ponytail: single catch — XposedHelpers can throw ClassNotFoundException,
            // NoSuchMethodError, or unexpected Error subtypes; all handled the same way.
            if (t is ClassNotFoundException) {
                // Class doesn't exist in this loader — normal
            } else {
                Log.w(TAG, "[app] Hook unavailable: $className.$methodName — ${t.javaClass.simpleName}")
                XposedBridge.log("[$TAG] Hook unavailable: $className.$methodName — ${t.javaClass.simpleName}")
            }
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

        // ponytail: DIAGNOSTIC_LOG_ALL is gated by BuildConfig.DEBUG above;
        // no need to compute isRelevant since we always log when active.
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
        if (intent.extras != null && !intent.extras!!.isEmpty) {
            for (key in intent.extras!!.keySet()) {
                val value = intent.extras!!.get(key)
                Log.d(TAG, "[DIAG]   extra: $key = $value (${value?.javaClass?.simpleName})")
            }
        }

        XposedBridge.log("[$TAG] $source: data=$data pkg=$pkg comp=$comp caller=$caller")
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
                        fakeIfBrowserPackage(param) { buildFakePackageInfo(it) }
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
                        fakeIfBrowserPackage(param) { buildFakePackageInfo(it) }
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
                        fakeIfBrowserPackage(param) { buildFakeApplicationInfo(it) }
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
                        fakeIfBrowserPackage(param) { buildFakeApplicationInfo(it) }
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
                            logHyperAiDiagnosticStack("getLaunchIntentForPackage($pkgName)")
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

        // 6. Mi Share's receive popup may draw the target browser icon from PackageManager.
        // ponytail: only swap Xiaomi Browser icons in MiShare; add exact popup-class hook if this misses a future build.
        try {
            XposedHelpers.findAndHookMethod(
                pmClass, effectiveCl,
                "getApplicationIcon",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkgName = param.args[0] as? String ?: return
                        if (!shouldSwapBrowserIcon(pkgName)) return

                        val context = android.app.AndroidAppHelper.currentApplication() ?: return
                        param.result = getDefaultBrowserDrawable(context)
                        Log.d(TAG, "[PackageManager] Replaced Xiaomi Browser icon for: $pkgName")
                    }
                })
            Log.i(TAG, "[PackageManager] Hooked getApplicationIcon(String)")
        } catch (t: Throwable) {
            Log.w(TAG, "[PackageManager] getApplicationIcon(String): ${t.javaClass.simpleName}")
        }

        try {
            XposedHelpers.findAndHookMethod(
                pmClass, effectiveCl,
                "getApplicationIcon",
                android.content.pm.ApplicationInfo::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val appInfo = param.args[0] as? android.content.pm.ApplicationInfo ?: return
                        if (!shouldSwapBrowserIcon(appInfo.packageName)) return

                        val context = android.app.AndroidAppHelper.currentApplication() ?: return
                        param.result = getDefaultBrowserDrawable(context)
                        Log.d(TAG, "[PackageManager] Replaced Xiaomi Browser icon from ApplicationInfo")
                    }
                })
            Log.i(TAG, "[PackageManager] Hooked getApplicationIcon(ApplicationInfo)")
        } catch (t: Throwable) {
            Log.w(TAG, "[PackageManager] getApplicationIcon(ApplicationInfo): ${t.javaClass.simpleName}")
        }

        // 7. HyperAI renders the Copy Direct browser name from ApplicationInfo.
        // Return the user's real default-browser label when it asks for Xiaomi Browser.
        try {
            XposedHelpers.findAndHookMethod(
                pmClass, effectiveCl,
                "getApplicationLabel",
                android.content.pm.ApplicationInfo::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val appInfo = param.args[0] as? android.content.pm.ApplicationInfo ?: return
                        if (!shouldSwapHyperAiBrowserMetadata(appInfo.packageName)) return

                        val context = android.app.AndroidAppHelper.currentApplication() ?: return
                        val label = getDefaultBrowserLabel(context) ?: return
                        param.result = label
                        Log.d(TAG, "[PackageManager] Replaced HyperAI Xiaomi Browser label with: $label")
                    }
                })
            Log.i(TAG, "[PackageManager] Hooked getApplicationLabel(ApplicationInfo)")
        } catch (t: Throwable) {
            Log.w(TAG, "[PackageManager] getApplicationLabel(ApplicationInfo): ${t.javaClass.simpleName}")
        }
    }

    private fun shouldSwapBrowserIcon(pkgName: String?): Boolean {
        val callerPackage = android.app.AndroidAppHelper.currentPackageName()
            ?: android.app.AndroidAppHelper.currentApplication()?.packageName
        return (callerPackage == XiaomiPackageList.MI_SHARE ||
            callerPackage == XiaomiPackageList.AI_ENGINE) &&
            XiaomiPackageList.isXiaomiBrowser(pkgName)
    }

    private fun shouldSwapHyperAiBrowserMetadata(pkgName: String?): Boolean {
        return android.app.AndroidAppHelper.currentPackageName() == XiaomiPackageList.AI_ENGINE &&
            XiaomiPackageList.isXiaomiBrowser(pkgName)
    }

    private fun getDefaultBrowserDrawable(context: Context) =
        DefaultBrowserResolver.resolveDefaultBrowser(context)?.let { browser ->
            runCatching { context.packageManager.getApplicationIcon(browser.packageName) }.getOrNull()
        }

    private fun getDefaultBrowserLabel(context: Context): String? {
        val browser = DefaultBrowserResolver.resolveDefaultBrowser(context) ?: return null
        if (!browser.isDefault) return null
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    browser.packageName,
                    PackageManager.ApplicationInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(browser.packageName, 0)
            }
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrNull()
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
        pi.applicationInfo = buildFakeApplicationInfo(packageName)
        return pi
    }

    /**
     * Build a minimal [ApplicationInfo] that satisfies "is the app installed?" checks.
     */
    private fun buildFakeApplicationInfo(packageName: String): android.content.pm.ApplicationInfo {
        val ai = android.content.pm.ApplicationInfo()
        ai.packageName = packageName
        ai.flags = android.content.pm.ApplicationInfo.FLAG_SYSTEM
        // ponytail: sourceDir must start with "/system" to satisfy
        // Android 16's AppRestrictionController.isSystemModule() check
        // (called from system_server, so the self-query guard doesn't help).
        // The actual path is a convention — nothing reads from it because
        // the self-query guard prevents the browser process from hitting
        // this fake ApplicationInfo.
        val stubPath = "/system/app/${packageName}/${packageName}.apk"
        ai.sourceDir = stubPath
        ai.publicSourceDir = stubPath
        return ai
    }

    /**
     * Common guard for all 4 PackageManager hooks: skip faking when
     * the calling package queries itself.
     */
    private fun fakeIfBrowserPackage(param: XC_MethodHook.MethodHookParam, builder: (String) -> Any) {
        val pkgName = param.args[0] as? String ?: return
        if (!XiaomiPackageList.isXiaomiBrowser(pkgName)) return
        // ponytail: don't fake when the package queries itself
        // (e.g. browser checks its own signatures on startup)
        if (android.app.AndroidAppHelper.currentPackageName() == pkgName) return
        logHyperAiDiagnosticStack("${param.method.name}($pkgName)")
        param.result = builder(pkgName)
        Log.d(TAG, "[PackageManager] Faked ${param.method.name}: $pkgName")
    }

    private fun logHyperAiDiagnosticStack(event: String) {
        if (!BuildConfig.DEBUG) return
        if (android.app.AndroidAppHelper.currentPackageName() != XiaomiPackageList.AI_ENGINE) return

        val frames = Throwable().stackTrace
            .asSequence()
            .filterNot { frame ->
                frame.className.startsWith("com.hyperosfix.browser.") ||
                    frame.className.startsWith("de.robv.android.xposed.") ||
                    frame.className.startsWith("org.lsposed.") ||
                    frame.className.startsWith("y.xAT.")
            }
            .take(18)
            .joinToString("\n  ") { frame ->
                "${frame.className}.${frame.methodName}:${frame.lineNumber}"
            }

        Log.w(TAG, "[AI-Engine-DIAG-STACK] $event\n  $frames")
    }

    private fun describeDiagnosticIntent(intent: Intent?): String {
        if (intent == null) return "intent=null"
        val data = intent.data?.toString()?.let { value ->
            if (value.length <= 240) value else value.take(240) + "..."
        }
        val extraKeys = runCatching {
            intent.extras?.keySet()?.sorted()?.take(16)?.joinToString(",")
        }.getOrNull()
        return "action=${intent.action}, data=$data, pkg=${intent.`package`}, " +
            "comp=${intent.component?.flattenToShortString()}, flags=0x${Integer.toHexString(intent.flags)}, " +
            "extraKeys=${extraKeys ?: ""}"
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
                        val context = param.args[0] as? Context
                        if (BuildConfig.DEBUG && context?.packageName == XiaomiPackageList.AI_ENGINE) {
                            Log.i(
                                TAG,
                                "[AI-Engine-DIAG] PendingIntent.getActivity requestCode=${param.args[1]}, " +
                                    "pendingFlags=0x${Integer.toHexString(param.args[3] as Int)} | " +
                                    describeDiagnosticIntent(intent)
                            )
                            logHyperAiDiagnosticStack("PendingIntent.getActivity")
                        }
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

    /**
     * HyperAI Copy Direct may hand a PendingIntent to Content Catcher and never
     * call startActivity in its own process. Log every send whose creator or
     * current package is HyperAI so the click path remains visible.
     */
    private fun hookPendingIntentSendDiagnostics() {
        try {
            var hooked = 0
            for (method in PendingIntent::class.java.declaredMethods) {
                if (method.name != "send") continue
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pendingIntent = param.thisObject as? PendingIntent ?: return
                        val creatorPackage = runCatching { pendingIntent.creatorPackage }.getOrNull()
                        val currentPackage = android.app.AndroidAppHelper.currentPackageName()
                        if (creatorPackage != XiaomiPackageList.AI_ENGINE &&
                            currentPackage != XiaomiPackageList.AI_ENGINE
                        ) {
                            return
                        }

                        val fillInIntent = param.args.firstOrNull { it is Intent } as? Intent
                        Log.i(
                            TAG,
                            "[AI-Engine-DIAG] PendingIntent.send overload=${method.parameterTypes.size}, " +
                                "creator=$creatorPackage, current=$currentPackage | " +
                                describeDiagnosticIntent(fillInIntent)
                        )
                        logHyperAiDiagnosticStack("PendingIntent.send/${method.parameterTypes.size}")
                    }
                })
                hooked++
            }
            Log.i(TAG, "[AI-Engine-DIAG] Hooked $hooked PendingIntent.send overloads")
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "[AI-Engine-DIAG] PendingIntent.send hook unavailable: " +
                    "${t.javaClass.simpleName} — ${t.message}"
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Debug-only HyperAI custom action protocol diagnostics
    // ══════════════════════════════════════════════════════════════════════

    private fun hookHyperAiActionProtocolDiagnostics(
        lpparam: XC_LoadPackage.LoadPackageParam
    ) {
        hookObservedHyperAiMethods(lpparam.classLoader)
    }

    private fun hookActionCoreProviderCall(classLoader: ClassLoader) {
        val className =
            "com.xiaomi.aicr.hce_framework.protocol.action.com.xiaomi.aicr.actionprovider.ActionCoreProvider"
        try {
            XposedHelpers.findAndHookMethod(
                className,
                classLoader,
                "call",
                String::class.java,
                String::class.java,
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!BuildConfig.DEBUG) return
                        val method = param.args[0] as? String
                        val arg = param.args[1] as? String
                        val extras = param.args[2] as? Bundle
                        Log.i(
                            TAG,
                            "[AI-Action-DIAG] ActionCoreProvider.call BEFORE " +
                                "method=${truncateDiagnosticText(method)}, " +
                                "arg=${truncateDiagnosticText(arg)}, " +
                                "extras=${describeDiagnosticValue(extras)}"
                        )
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        rewriteHyperAiCopyDirectResponse(
                            request = param.args[2] as? Bundle,
                            response = param.result as? Bundle
                        )
                        if (!BuildConfig.DEBUG) return
                        Log.i(
                            TAG,
                            "[AI-Action-DIAG] ActionCoreProvider.call AFTER " +
                                "result=${describeDiagnosticValue(param.result)}"
                        )
                    }
                }
            )
            Log.i(TAG, "[AI-Action-DIAG] Hooked $className.call(String,String,Bundle)")
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "[AI-Action-DIAG] ActionCoreProvider.call hook unavailable: " +
                    "${t.javaClass.simpleName} — ${t.message}"
            )
        }
    }

    /** Install only the two observed CueData queue entry hooks needed by the
     * production fix. Verbose method/result logging remains debug-only. */
    private fun hookHyperAiCopyDirectCueDataPipeline(classLoader: ClassLoader) {
        val clazz = try {
            XposedHelpers.findClass("wj0", classLoader)
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "[AI-CopyDirect-UI-FIX] CueData pipeline unavailable: " +
                    "${t.javaClass.simpleName} — ${t.message}"
            )
            return
        }

        var hooked = 0
        for (method in clazz.declaredMethods.filter { method ->
            method.name in setOf("j", "o") && method.parameterTypes.size == 1
        }) {
            try {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        rewriteHyperAiCopyDirectCueData(param.args)
                    }
                })
                hooked++
            } catch (t: Throwable) {
                Log.w(
                    TAG,
                    "[AI-CopyDirect-UI-FIX] Failed to hook wj0.${method.name}: " +
                        "${t.javaClass.simpleName} — ${t.message}"
                )
            }
        }
        Log.i(TAG, "[AI-CopyDirect-UI-FIX] Hooked $hooked CueData pipeline methods")
    }

    /**
     * HyperOS 4 builds Copy Direct's card target, app metadata, and click-time
     * resolve_intent command from the same nested intentUri. Rewriting that one
     * protocol field keeps the visible browser and the executed browser aligned.
     */
    private fun rewriteHyperAiCopyDirectResponse(
        request: Bundle?,
        response: Bundle?
    ) {
        val actionType = request?.getString("type") ?: return
        if (!actionType.contains("[com.xiaomi.aicr/context/get_copy_direct_data]")) return
        if (response == null || response.getInt("target_code", -1) != 0) {
            Log.w(TAG, "[AI-CopyDirect-FIX] Copy Direct response was not successful; preserving it")
            return
        }

        try {
            val targetOutText = response.getString("target_out") ?: run {
                Log.w(TAG, "[AI-CopyDirect-FIX] Missing target_out; preserving response")
                return
            }
            val targetOut = JSONObject(targetOutText)
            if (targetOut.optInt("status", -1) != 0) {
                Log.w(TAG, "[AI-CopyDirect-FIX] target_out status was not successful; preserving response")
                return
            }

            val copyDirectValue = targetOut.opt("copyDirectData")
            val copyDirect = when (copyDirectValue) {
                is String -> JSONObject(copyDirectValue)
                is JSONObject -> copyDirectValue
                else -> {
                    Log.w(TAG, "[AI-CopyDirect-FIX] Missing copyDirectData JSON; preserving response")
                    return
                }
            }

            val intentUri = copyDirect.optString("intentUri", "")
            if (intentUri.isBlank()) {
                Log.w(TAG, "[AI-CopyDirect-FIX] Missing intentUri; preserving response")
                return
            }

            val intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
            val targetPackage = intent.`package`
            val targetComponentPackage = intent.component?.packageName
            if (!XiaomiPackageList.isXiaomiBrowser(targetPackage) &&
                !XiaomiPackageList.isXiaomiBrowser(targetComponentPackage)
            ) {
                Log.d(TAG, "[AI-CopyDirect-FIX] Intent does not target Xiaomi Browser; preserving it")
                return
            }

            val data = intent.data
            val scheme = data?.scheme?.lowercase()
            if (data == null || (scheme != "http" && scheme != "https")) {
                Log.w(TAG, "[AI-CopyDirect-FIX] Intent is not HTTP(S); preserving it")
                return
            }

            val context = android.app.AndroidAppHelper.currentApplication() ?: run {
                Log.w(TAG, "[AI-CopyDirect-FIX] Application context unavailable; preserving response")
                return
            }
            val browser = DefaultBrowserResolver.resolveDefaultBrowser(context)
            if (browser == null || !browser.isDefault) {
                Log.w(TAG, "[AI-CopyDirect-FIX] Explicit default browser unavailable; preserving response")
                return
            }
            if (XiaomiPackageList.isXiaomiBrowser(browser.packageName)) {
                Log.d(TAG, "[AI-CopyDirect-FIX] Xiaomi Browser is already the default; preserving response")
                return
            }

            intent.component = null
            intent.setPackage(browser.packageName)
            val rewrittenIntentUri = intent.toUri(Intent.URI_INTENT_SCHEME)
            copyDirect.put("intentUri", rewrittenIntentUri)

            val browserLabel = getDefaultBrowserLabel(context) ?: browser.packageName
            val metadataReplacements = rewriteCopyDirectMetadata(
                copyDirect,
                browser.packageName,
                browserLabel
            )
            if (copyDirectValue is String) {
                targetOut.put("copyDirectData", copyDirect.toString())
            } else {
                targetOut.put("copyDirectData", copyDirect)
            }
            response.putString("target_out", targetOut.toString())

            Log.i(
                TAG,
                "[AI-CopyDirect-FIX] Rewrote Copy Direct from " +
                    "${targetPackage ?: targetComponentPackage} to ${browser.packageName}: $data; " +
                    "uiMetadataReplacements=$metadataReplacements, label=$browserLabel"
            )
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "[AI-CopyDirect-FIX] Failed to rewrite response; preserving original: " +
                    "${t.javaClass.simpleName} — ${t.message}"
            )
        }
    }

    private fun rewriteCopyDirectMetadata(
        value: Any?,
        browserPackage: String,
        browserLabel: String
    ): Int {
        return when (value) {
            is JSONObject -> {
                var changes = 0
                val keys = value.keys().asSequence().toList()
                for (key in keys) {
                    val child = value.opt(key)
                    if (child is String) {
                        val (rewritten, count) = rewriteCopyDirectMetadataString(
                            key,
                            child,
                            browserPackage,
                            browserLabel
                        )
                        if (count > 0) value.put(key, rewritten)
                        changes += count
                    } else {
                        changes += rewriteCopyDirectMetadata(child, browserPackage, browserLabel)
                    }
                }
                changes
            }
            is JSONArray -> {
                var changes = 0
                for (index in 0 until value.length()) {
                    val child = value.opt(index)
                    if (child is String) {
                        val (rewritten, count) = rewriteCopyDirectMetadataString(
                            index.toString(),
                            child,
                            browserPackage,
                            browserLabel
                        )
                        if (count > 0) value.put(index, rewritten)
                        changes += count
                    } else {
                        changes += rewriteCopyDirectMetadata(child, browserPackage, browserLabel)
                    }
                }
                changes
            }
            else -> 0
        }
    }

    private fun rewriteCopyDirectMetadataString(
        key: String,
        original: String,
        browserPackage: String,
        browserLabel: String
    ): Pair<String, Int> {
        val trimmed = original.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            runCatching {
                val nested = JSONObject(original)
                val changes = rewriteCopyDirectMetadata(nested, browserPackage, browserLabel)
                if (changes > 0) return nested.toString() to changes
            }
        } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            runCatching {
                val nested = JSONArray(original)
                val changes = rewriteCopyDirectMetadata(nested, browserPackage, browserLabel)
                if (changes > 0) return nested.toString() to changes
            }
        }

        var rewritten = original
        var changes = 0
        val metadataKey = key.lowercase().let { normalized ->
            normalized.contains("package") ||
                normalized.contains("target") ||
                normalized.contains("apk") ||
                normalized.contains("icon") ||
                normalized.contains("resource") ||
                normalized.contains("intent") ||
                normalized.contains("app")
        }
        for (xiaomiPackage in XiaomiPackageList.ALL_BROWSER_PACKAGES) {
            val updated = when {
                rewritten == xiaomiPackage -> browserPackage
                metadataKey -> rewritten.replace(xiaomiPackage, browserPackage)
                else -> rewritten
                    .replace(";package=$xiaomiPackage;", ";package=$browserPackage;")
                    .replace("android.resource://$xiaomiPackage/", "android.resource://$browserPackage/")
            }
            if (updated != rewritten) {
                changes++
                rewritten = updated
            }
        }

        val textKey = key.lowercase().let { normalized ->
            normalized.contains("title") ||
                normalized.contains("text") ||
                normalized.contains("description") ||
                normalized.contains("label") ||
                normalized.contains("name") ||
                normalized.contains("browser") ||
                normalized.contains("app")
        }
        if (textKey) {
            for (xiaomiLabel in listOf("小米浏览器", "Mi Browser", "Xiaomi Browser")) {
                val updated = rewritten.replace(xiaomiLabel, browserLabel)
                if (updated != rewritten) {
                    changes++
                    rewritten = updated
                }
            }
        }
        return rewritten to changes
    }

    private fun hookObservedHyperAiMethods(classLoader: ClassLoader) {
        val targets = linkedMapOf(
            "pw8" to setOf("k", "l"),
            "jo3" to setOf("b"),
            "ho2" to setOf("a"),
            "ok0" to setOf("a", "e"),
            "al0" to setOf("l"),
            "wj0" to setOf("j", "k", "o", "p"),
            "el" to setOf("onClick"),
        )

        for ((className, methodNames) in targets) {
            val clazz = try {
                XposedHelpers.findClass(className, classLoader)
            } catch (_: Throwable) {
                Log.d(TAG, "[AI-Action-DIAG] Class unavailable in this process: $className")
                continue
            }

            for (method in clazz.declaredMethods.filter { it.name in methodNames }) {
                val signature = buildString {
                    append(clazz.name)
                    append('.')
                    append(method.name)
                    append('(')
                    append(method.parameterTypes.joinToString(",") { it.name })
                    append("): ")
                    append(method.returnType.name)
                }
                try {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val args = param.args.mapIndexed { index, value ->
                                "#$index=${describeDiagnosticValue(value)}"
                            }.joinToString(" | ")
                            Log.i(TAG, "[AI-Action-DIAG] $signature BEFORE $args")
                        }

                        override fun afterHookedMethod(param: MethodHookParam) {
                            val outcome = param.throwable?.let {
                                "throwable=${it.javaClass.name}:${truncateDiagnosticText(it.message)}"
                            } ?: "result=${describeDiagnosticValue(param.result)}"
                            Log.i(TAG, "[AI-Action-DIAG] $signature AFTER $outcome")
                        }
                    })
                    Log.i(TAG, "[AI-Action-DIAG] Hooked $signature")
                } catch (t: Throwable) {
                    Log.w(
                        TAG,
                        "[AI-Action-DIAG] Failed to hook $signature: " +
                            "${t.javaClass.simpleName} — ${t.message}"
                    )
                }
            }
        }
    }

    /**
     * Rewrites the already-materialized Copy Direct CueData used by HyperAI's
     * bubble renderer.  HyperOS 4 does not derive this object's visual metadata
     * from the actionprovider response, so changing only intentUri cannot alter
     * the label/icon shown on screen.
     */
    private fun rewriteHyperAiCopyDirectCueData(arguments: Array<Any?>) {
        val context = android.app.AndroidAppHelper.currentApplication() ?: return
        val browser = DefaultBrowserResolver.resolveDefaultBrowser(context) ?: return
        if (!browser.isDefault || XiaomiPackageList.isXiaomiBrowser(browser.packageName)) return
        val browserLabel = getDefaultBrowserLabel(context) ?: browser.packageName
        val browserIcon = getAppIcon(context, browser.packageName)
        val visited = IdentityHashMap<Any, Boolean>()

        for (argument in arguments) {
            visitForCopyDirectCueData(
                value = argument,
                context = context,
                browserPackage = browser.packageName,
                browserLabel = browserLabel,
                browserIcon = browserIcon,
                visited = visited,
                depth = 0
            )
        }
    }

    private fun visitForCopyDirectCueData(
        value: Any?,
        context: Context,
        browserPackage: String,
        browserLabel: String,
        browserIcon: Icon?,
        visited: IdentityHashMap<Any, Boolean>,
        depth: Int
    ) {
        if (value == null || depth > 5) return
        if (value is String || value is Number || value is Boolean || value is Char) return
        if (visited.put(value, true) != null) return

        try {
            when (value) {
                is Collection<*> -> value.forEach {
                    visitForCopyDirectCueData(
                        it, context, browserPackage, browserLabel, browserIcon, visited, depth + 1
                    )
                }
                is Map<*, *> -> value.values.forEach {
                    visitForCopyDirectCueData(
                        it, context, browserPackage, browserLabel, browserIcon, visited, depth + 1
                    )
                }
                is Array<*> -> value.forEach {
                    visitForCopyDirectCueData(
                        it, context, browserPackage, browserLabel, browserIcon, visited, depth + 1
                    )
                }
                else -> {
                    if (value.javaClass.name == "com.xiaomi.ai.bubble.core.model.CueData" &&
                        isCopyDirectCueData(value)
                    ) {
                        rewriteCopyDirectCueDataObject(
                            value, context, browserPackage, browserLabel, browserIcon
                        )
                        return
                    }

                    // vj0 is the observed wrapper around the CueData list. Avoid
                    // reflecting through arbitrary Android/framework objects.
                    if (value.javaClass.name == "vj0") {
                        instanceFields(value.javaClass).forEach { field ->
                            val child = runCatching {
                                field.isAccessible = true
                                field.get(value)
                            }.getOrNull()
                            visitForCopyDirectCueData(
                                child,
                                context,
                                browserPackage,
                                browserLabel,
                                browserIcon,
                                visited,
                                depth + 1
                            )
                        }
                    }
                }
            }
        } finally {
            visited.remove(value)
        }
    }

    private fun isCopyDirectCueData(cueData: Any): Boolean {
        val stringFields = instanceFields(cueData.javaClass).mapNotNull { field ->
            runCatching {
                field.isAccessible = true
                field.get(cueData) as? String
            }.getOrNull()
        }
        return stringFields.any { it == "copy_jump" } ||
            stringFields.any { it.contains("copy_text_jump_app") }
    }

    private fun rewriteCopyDirectCueDataObject(
        cueData: Any,
        context: Context,
        browserPackage: String,
        browserLabel: String,
        browserIcon: Icon?
    ) {
        val before = describeCueDataVisualFields(cueData)
        val changes = rewriteCueDataModelFields(
            value = cueData,
            browserPackage = browserPackage,
            browserLabel = browserLabel,
            browserIcon = browserIcon,
            visited = IdentityHashMap(),
            depth = 0,
            path = "CueData"
        )
        val after = describeCueDataVisualFields(cueData)
        Log.i(
            TAG,
            "[AI-CopyDirect-UI-FIX] Rewrote rendered CueData for $browserPackage/$browserLabel; " +
                "changes=$changes; before=$before; after=$after"
        )

        // Force the real package's icon/label into PackageManager caches used
        // after CueData.targetPackage has been changed.
        runCatching { context.packageManager.getApplicationInfo(browserPackage, 0) }
    }

    private fun rewriteCueDataModelFields(
        value: Any?,
        browserPackage: String,
        browserLabel: String,
        browserIcon: Icon?,
        visited: IdentityHashMap<Any, Boolean>,
        depth: Int,
        path: String
    ): Int {
        if (value == null || depth > 7) return 0
        if (value is String || value is Number || value is Boolean || value is Char) return 0
        if (visited.put(value, true) != null) return 0

        var changes = 0
        try {
            when (value) {
                is Collection<*> -> value.forEachIndexed { index, child ->
                    changes += rewriteCueDataModelFields(
                        child, browserPackage, browserLabel, browserIcon,
                        visited, depth + 1, "$path[$index]"
                    )
                }
                is Map<*, *> -> value.values.forEach { child ->
                    changes += rewriteCueDataModelFields(
                        child, browserPackage, browserLabel, browserIcon,
                        visited, depth + 1, "$path{value}"
                    )
                }
                else -> {
                    val className = value.javaClass.name
                    if (!className.startsWith("com.xiaomi.ai.bubble.core.model.CueData")) return 0

                    for (field in instanceFields(value.javaClass)) {
                        val child = runCatching {
                            field.isAccessible = true
                            field.get(value)
                        }.getOrNull()
                        val fieldPath = "$path.${field.name}"

                        when (child) {
                            is String -> {
                                val rewritten = rewriteCueDataString(child, browserPackage, browserLabel)
                                if (rewritten != child && runCatching {
                                        field.set(value, rewritten)
                                    }.isSuccess
                                ) {
                                    changes++
                                    Log.d(TAG, "[AI-CopyDirect-UI-FIX] $fieldPath: $child -> $rewritten")
                                }
                            }
                            is Icon -> {
                                if (browserIcon != null && field.name.contains("icon", true) &&
                                    runCatching { field.set(value, browserIcon) }.isSuccess
                                ) {
                                    changes++
                                    Log.d(TAG, "[AI-CopyDirect-UI-FIX] Replaced $fieldPath Android Icon")
                                }
                            }
                            else -> changes += rewriteCueDataModelFields(
                                child,
                                browserPackage,
                                browserLabel,
                                browserIcon,
                                visited,
                                depth + 1,
                                fieldPath
                            )
                        }
                    }
                }
            }
        } finally {
            visited.remove(value)
        }
        return changes
    }

    private fun rewriteCueDataString(
        original: String,
        browserPackage: String,
        browserLabel: String
    ): String {
        var rewritten = original
        for (xiaomiPackage in XiaomiPackageList.ALL_BROWSER_PACKAGES) {
            rewritten = rewritten.replace(xiaomiPackage, browserPackage)
        }
        for (xiaomiLabel in listOf("小米浏览器", "Mi Browser", "Xiaomi Browser")) {
            rewritten = rewritten.replace(xiaomiLabel, browserLabel)
        }
        return rewritten
    }

    /**
     * HyperAI only recognizes a closed set of `builtin` icon tokens. A token
     * synthesized from a third-party package (for example app_icon_mark_via)
     * resolves to nothing. Keep the valid Xiaomi placeholder token in CueData,
     * then replace the rendered ImageView with the real default-browser
     * Drawable. This deliberately mirrors the working MiShare notification
     * approach, whose icon also comes directly from PackageManager.
     */
    private fun hookHyperAiCopyDirectRenderedIcon() {
        try {
            var textHooks = 0
            for (method in TextView::class.java.declaredMethods) {
                if (method.name != "setText") continue
                if (method.parameterTypes.none {
                        CharSequence::class.java.isAssignableFrom(it)
                    }
                ) continue

                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val textView = param.thisObject as? TextView ?: return
                        val text = param.args.firstOrNull { it is CharSequence }
                            ?.toString()
                            ?: textView.text?.toString()
                            ?: return
                        replaceCopyDirectBubbleIconIfMatched(textView, text)
                    }
                })
                textHooks++
            }
            Log.i(TAG, "[AI-CopyDirect-UI-ICON] Hooked $textHooks TextView.setText overloads")
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "[AI-CopyDirect-UI-ICON] TextView hook unavailable: " +
                    "${t.javaClass.simpleName} — ${t.message}"
            )
        }
    }

    private fun replaceCopyDirectBubbleIconIfMatched(textView: TextView, text: String) {
        if (!text.contains("打开")) return
        val context = textView.context ?: return
        val browser = DefaultBrowserResolver.resolveDefaultBrowser(context) ?: return
        if (!browser.isDefault || XiaomiPackageList.isXiaomiBrowser(browser.packageName)) return
        val label = getDefaultBrowserLabel(context) ?: browser.packageName
        if (!text.contains(label, ignoreCase = true)) return

        // Run once after the current bind and once after the next layout pass;
        // some HyperAI builds bind the builtin icon after the title text.
        val replacement = Runnable {
            replaceNearestBubbleImageView(textView, browser.packageName, label)
        }
        textView.post(replacement)
        textView.postDelayed(replacement, 120L)
    }

    private fun replaceNearestBubbleImageView(
        textView: TextView,
        browserPackage: String,
        browserLabel: String
    ) {
        if (!textView.isAttachedToWindow) return
        val drawable = getDefaultBrowserDrawable(textView.context) ?: return
        var ancestor = textView.parent as? ViewGroup
        var levels = 0

        while (ancestor != null && levels < 6) {
            val candidates = ArrayList<ImageView>()
            collectImageViews(ancestor, candidates)
            val target = candidates
                .filter { it.visibility == View.VISIBLE }
                .minByOrNull { imageViewDistance(textView, it) }
            if (target != null) {
                target.setImageDrawable(drawable.constantState?.newDrawable()?.mutate() ?: drawable)
                val idName = runCatching {
                    target.resources.getResourceEntryName(target.id)
                }.getOrNull() ?: "no-id"
                Log.i(
                    TAG,
                    "[AI-CopyDirect-UI-ICON] Injected real icon for " +
                        "$browserPackage/$browserLabel into ImageView($idName)"
                )
                return
            }
            ancestor = ancestor.parent as? ViewGroup
            levels++
        }
        Log.w(
            TAG,
            "[AI-CopyDirect-UI-ICON] Matched title for $browserPackage/$browserLabel " +
                "but found no sibling ImageView"
        )
    }

    private fun collectImageViews(root: View, output: MutableList<ImageView>) {
        if (root is ImageView) {
            output += root
            return
        }
        if (root !is ViewGroup) return
        for (index in 0 until root.childCount) {
            collectImageViews(root.getChildAt(index), output)
        }
    }

    private fun imageViewDistance(textView: TextView, imageView: ImageView): Int {
        val textPosition = IntArray(2)
        val imagePosition = IntArray(2)
        runCatching { textView.getLocationOnScreen(textPosition) }
        runCatching { imageView.getLocationOnScreen(imagePosition) }
        return kotlin.math.abs(textPosition[0] - imagePosition[0]) +
            kotlin.math.abs(textPosition[1] - imagePosition[1])
    }

    private fun describeCueDataVisualFields(cueData: Any): String {
        val leaves = ArrayList<String>()
        collectCueDataVisualLeaves(
            cueData, IdentityHashMap(), 0, "CueData", leaves
        )
        return truncateDiagnosticText(leaves.take(40).joinToString(" | "))
    }

    private fun collectCueDataVisualLeaves(
        value: Any?,
        visited: IdentityHashMap<Any, Boolean>,
        depth: Int,
        path: String,
        output: MutableList<String>
    ) {
        if (value == null || depth > 7 || output.size >= 40) return
        if (visited.put(value, true) != null) return
        try {
            val className = value.javaClass.name
            if (!className.startsWith("com.xiaomi.ai.bubble.core.model.CueData")) return
            for (field in instanceFields(value.javaClass)) {
                val child = runCatching {
                    field.isAccessible = true
                    field.get(value)
                }.getOrNull()
                val fieldPath = "$path.${field.name}"
                when (child) {
                    is String -> {
                        if (fieldPath.contains("display", true) ||
                            fieldPath.contains("icon", true) ||
                            child.contains("browser", true) ||
                            child.contains("浏览器")
                        ) output += "$fieldPath=$child"
                    }
                    else -> collectCueDataVisualLeaves(
                        child, visited, depth + 1, fieldPath, output
                    )
                }
            }
        } finally {
            visited.remove(value)
        }
    }

    private fun instanceFields(clazz: Class<*>): List<java.lang.reflect.Field> {
        val fields = ArrayList<java.lang.reflect.Field>()
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            fields += current.declaredFields.filterNot {
                it.isSynthetic || Modifier.isStatic(it.modifiers)
            }
            current = current.superclass
        }
        return fields
    }

    private fun describeDiagnosticValue(value: Any?): String {
        return describeDiagnosticValue(value, 0, IdentityHashMap())
    }

    private fun describeDiagnosticValue(
        value: Any?,
        depth: Int,
        visited: IdentityHashMap<Any, Boolean>
    ): String {
        if (value == null) return "null"
        if (depth > 2) return value.javaClass.name
        if (visited.put(value, true) != null) return "<cycle:${value.javaClass.name}>"

        return try {
            when (value) {
                is String -> "String{${truncateDiagnosticText(value)}}"
                is CharSequence -> "${value.javaClass.simpleName}{${truncateDiagnosticText(value.toString())}}"
                is Number, is Boolean, is Char -> value.toString()
                is Uri -> "Uri{${truncateDiagnosticText(value.toString())}}"
                is Intent -> "Intent{${describeDiagnosticIntent(value)}}"
                is Bundle -> {
                    val entries = runCatching {
                        value.keySet().sorted().take(24).joinToString(", ") { key ->
                            "$key=${describeDiagnosticValue(value.get(key), depth + 1, visited)}"
                        }
                    }.getOrElse { error -> "<unreadable:${error.javaClass.simpleName}>" }
                    "Bundle{$entries}"
                }
                is Bitmap -> "Bitmap{${value.width}x${value.height},${value.config}}"
                is Icon -> "Icon{type=${value.type}}"
                is Collection<*> -> {
                    val items = value.take(16).mapIndexed { index, item ->
                        "#$index=${describeDiagnosticValue(item, depth + 1, visited)}"
                    }.joinToString(", ")
                    "${value.javaClass.simpleName}[$items]"
                }
                is Array<*> -> {
                    val items = value.take(16).mapIndexed { index, item ->
                        "#$index=${describeDiagnosticValue(item, depth + 1, visited)}"
                    }.joinToString(", ")
                    "Array[$items]"
                }
                else -> describeDiagnosticObject(value, depth, visited)
            }
        } catch (t: Throwable) {
            "${value.javaClass.name}{unreadable=${t.javaClass.simpleName}}"
        } finally {
            visited.remove(value)
        }
    }

    private fun describeDiagnosticObject(
        value: Any,
        depth: Int,
        visited: IdentityHashMap<Any, Boolean>
    ): String {
        val className = value.javaClass.name
        if (className.startsWith("java.") ||
            className.startsWith("kotlin.") ||
            className.startsWith("android.") ||
            className.startsWith("androidx.")
        ) {
            return "$className{${truncateDiagnosticText(value.toString())}}"
        }

        val fields = value.javaClass.declaredFields
            .asSequence()
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .take(16)
            .map { field ->
                val fieldValue = runCatching {
                    field.isAccessible = true
                    field.get(value)
                }.getOrElse { error -> "<unreadable:${error.javaClass.simpleName}>" }
                "${field.name}=${describeDiagnosticValue(fieldValue, depth + 1, visited)}"
            }
            .joinToString(", ")
        return "$className{$fields}"
    }

    private fun truncateDiagnosticText(value: String?): String {
        if (value == null) return "null"
        val flattened = value.replace('\n', ' ').replace('\r', ' ')
        return if (flattened.length <= 240) flattened else flattened.take(240) + "..."
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

    // ponytail: intentionally separate from hookClipboardNotificationIcon — runs in Mi Share process
    private fun hookMiShareNotificationIcon() {
        try {
            XposedHelpers.findAndHookMethod(
                android.app.NotificationManager::class.java,
                "notify",
                Int::class.javaPrimitiveType,
                Notification::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val id = param.args[0] as Int
                        val notification = param.args[1] as? Notification ?: return
                        val extras = notification.extras

                        // Log ALL notifications for debugging
                        val title = extras?.getCharSequence("android.title")?.toString() ?: ""
                        val text = extras?.getCharSequence("android.text")?.toString() ?: ""
                        Log.d(TAG, "[MiShare] Notification id=$id, title=$title, text=$text")
                        XposedBridge.log("[$TAG] MiShare: notify id=$id, title=$title, text=$text")

                        // Check if this notification has a small icon set
                        val smallIcon = XposedHelpers.getObjectField(notification, "mSmallIcon")
                        if (smallIcon == null) return

                        val context = android.app.AndroidAppHelper.currentApplication() ?: return
                        if (shouldReplaceMiShareNotificationIcon(notification)) {
                            replaceNotificationIconWithDefaultBrowser(context, notification, "[MiShare]")
                        }
                    }
                })
            Log.i(TAG, "[MiShare] Hooked NotificationManager.notify for icon replacement")
            XposedBridge.log("[$TAG] MiShare: hooked NotificationManager.notify for icon replacement")
        } catch (t: Throwable) {
            Log.w(TAG, "[MiShare] Failed to hook NotificationManager.notify: ${t.message}")
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

        // Hook NotificationManager.notify to replace clipboard notification icon
        hookClipboardNotificationIcon()
    }

    private fun hookFrameworkNotificationIcon() {
        try {
            XposedHelpers.findAndHookMethod(
                android.app.NotificationManager::class.java,
                "notify",
                String::class.java,
                Int::class.javaPrimitiveType,
                Notification::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkg = param.args[0] as? String ?: return
                        val id = param.args[1] as Int
                        val notification = param.args[2] as? Notification ?: return

                        // notify(String, int, Notification)'s first argument is a tag, not a package.
                        val callerPackage = android.app.AndroidAppHelper.currentPackageName()
                            ?: android.app.AndroidAppHelper.currentApplication()?.packageName
                        if (callerPackage != XiaomiPackageList.MI_SHARE) return

                        val extras = notification.extras
                        val title = extras?.getCharSequence("android.title")?.toString() ?: ""
                        val text = extras?.getCharSequence("android.text")?.toString() ?: ""

                        Log.d(TAG, "[Framework] MiShare notification tag=$pkg id=$id, title=$title, text=$text")
                        XposedBridge.log("[$TAG] Framework: MiShare notify id=$id, title=$title, text=$text")

                        // Check if notification has a small icon
                        val smallIcon = XposedHelpers.getObjectField(notification, "mSmallIcon")
                        if (smallIcon == null) return

                        val context = android.app.AndroidAppHelper.currentApplication() ?: return
                        if (shouldReplaceMiShareNotificationIcon(notification)) {
                            replaceNotificationIconWithDefaultBrowser(context, notification, "[Framework]")
                        }
                    }
                })
            Log.i(TAG, "[Framework] Hooked NotificationManager.notify(String, int, Notification)")
            XposedBridge.log("[$TAG] Framework: hooked NotificationManager.notify for icon replacement")
        } catch (t: Throwable) {
            Log.w(TAG, "[Framework] Failed to hook notify: ${t.message}")
        }
    }

    // ponytail: intentionally separate from hookMiShareNotificationIcon — runs in AI Engine process
    private fun hookClipboardNotificationIcon() {
        try {
            XposedHelpers.findAndHookMethod(
                android.app.NotificationManager::class.java,
                "notify",
                Int::class.javaPrimitiveType,
                Notification::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val id = param.args[0] as Int
                        val notification = param.args[1] as? Notification ?: return

                        // Detect clipboard notification: id==111 with "copyText" in extras
                        if (id != 111) return
                        val extras = notification.extras ?: return
                        val copyText = extras.getString("copyText") ?: return

                        Log.d(TAG, "[AI-Engine] notify id=$id copyText='${copyText.take(200)}'")

                        val context = android.app.AndroidAppHelper.currentApplication() ?: return
                        val browser = DefaultBrowserResolver.resolveDefaultBrowser(context) ?: return

                        try {
                            // Inspect the notification's PendingIntent to determine
                            // if clicking it would open Mi Browser. Only replace icon
                            // and let framework hooks redirect when the original target
                            // is Mi Browser — address/express notifications are untouched.
                            if (!wouldClickOpenMiBrowser(notification, copyText)) {
                                Log.d(TAG, "[AI-Engine] Skipped icon replace — not a Mi Browser action")
                                return
                            }

                            val newIcon = getAppIcon(context, browser.packageName) ?: return
                            XposedHelpers.setObjectField(notification, "mSmallIcon", newIcon)
                            XposedHelpers.setObjectField(notification, "mLargeIcon", newIcon)
                            extras.putParcelable("miui.appIcon", newIcon)
                            val focusPics = extras.getBundle("miui.focus.pics")
                            if (focusPics != null) {
                                focusPics.putParcelable("miui.focus.pic_image", newIcon)
                                focusPics.putParcelable("miui.land.pic_image", newIcon)
                            }
                            Log.d(TAG, "[AI-Engine] Replaced clipboard notification icon with ${browser.packageName}")
                            XposedBridge.log("[$TAG] AI-Engine: replaced notification icon with ${browser.packageName}")
                        } catch (e: Exception) {
                            Log.w(TAG, "[AI-Engine] Failed to replace notification icon: ${e.message}")
                        }
                    }
                })
            Log.i(TAG, "[AI-Engine] Hooked NotificationManager.notify for clipboard icon replacement")
            XposedBridge.log("[$TAG] AI-Engine: hooked NotificationManager.notify for icon replacement")
        } catch (t: Throwable) {
            Log.w(TAG, "[AI-Engine] Failed to hook NotificationManager.notify: ${t.message}")
        }
    }

    /**
     * Determine whether clicking this notification would launch Xiaomi Browser,
     * by inspecting which app the PendingIntent targets.
     *
     * Only returns true when the ORIGINAL click target is a Xiaomi browser
     * package — ensuring app-specific share links (Baidu Netdisk, Xunlei, etc.)
     * are never intercepted.
     */
    private fun wouldClickOpenMiBrowser(notification: Notification, copyText: String): Boolean {
        val extras = notification.extras
        val tag = "[AI-Engine][wouldOpen]"

        // ── Approach 1: Extract target from PendingIntent ──────────────
        val pendingIntent = notification.contentIntent
        if (pendingIntent != null) {
            // Dump PendingIntent metadata for debugging
            Log.d(TAG, "$tag PendingIntent class=${pendingIntent.javaClass.name}")
            Log.d(TAG, "$tag PendingIntent creator=${pendingIntent.creatorPackage}")
            Log.d(TAG, "$tag PendingIntent toString=${pendingIntent.toString().take(500)}")

            // Try extracting wrapped Intent via multiple methods
            val wrappedIntent: Intent? = extractIntentFromPendingIntent(pendingIntent, tag)

            if (wrappedIntent != null) {
                val targetPkg = wrappedIntent.`package`
                val targetComp = wrappedIntent.component
                val targetCompPkg = targetComp?.packageName
                val dataUri = wrappedIntent.data
                val dataScheme = dataUri?.scheme?.lowercase()
                val action = wrappedIntent.action

                Log.d(TAG, "$tag Intent{action=$action, data=$dataUri, scheme=$dataScheme, " +
                    "pkg=$targetPkg, comp=$targetCompPkg}")

                // Direct target is Mi Browser → exclude only this case
                if (XiaomiPackageList.isXiaomiBrowser(targetPkg) ||
                    XiaomiPackageList.isXiaomiBrowser(targetCompPkg)
                ) {
                    Log.d(TAG, "$tag → true: targets Mi Browser directly")
                    return true
                }

                // For http/https data: we need to know whether the AI Engine
                // routes this to Mi Browser (we should intercept) or to a
                // third-party app (we should NOT intercept). The package/
                // component tells us this: if the Intent targets an app other
                // than Mi Browser, it's an app-specific link → don't touch.
                Log.d(TAG, "$tag → false: Intent targets non-browser app (pkg=$targetPkg, comp=$targetCompPkg)")
                return false
            }

            // ── Dump PendingIntent field names for debugging ─────────────
            try {
                val fields = pendingIntent.javaClass.declaredFields
                for (f in fields) {
                    f.isAccessible = true
                    val valStr = try { f.get(pendingIntent)?.toString()?.take(120) } catch (_: Throwable) { "N/A" }
                    Log.d(TAG, "$tag PI field: ${f.name} (${f.type.simpleName}) = $valStr")
                }
            } catch (_: Throwable) {}

            Log.d(TAG, "$tag PendingIntent present but Intent not extractable")
        } else {
            Log.d(TAG, "$tag contentIntent is null")
        }

        // ── Dump all notification extras for debugging ──────────────────
        if (extras != null) {
            val keys = extras.keySet().sorted()
            Log.d(TAG, "$tag Notification extras keys: ${keys.joinToString(", ")}")
            for (k in keys) {
                val v = extras.get(k)
                val vStr = v?.toString()?.take(120)
                Log.d(TAG, "$tag   extra $k = $vStr (${v?.javaClass?.simpleName})")
            }
        }

        // ── Approach 2: URL content + title/text guard ──────────────────
        // copyText is URL-like → could be browser URL or app-specific share
        // link (Baidu Netdisk, Xunlei, etc.). Check notification title to
        // distinguish: browser content has title like "打开浏览器" / "Open Browser",
        // while app-specific links mention the target app name.
        if (isLikelyUrl(copyText)) {
            val title = extras?.getCharSequence("android.title")?.toString().orEmpty()
            val text = extras?.getCharSequence("android.text")?.toString().orEmpty()
            val isBrowserNotification = title.contains("浏览器") ||
                title.contains("browser", ignoreCase = true) ||
                title.contains("Browser") ||
                text.startsWith("查看")    // "查看<URL>" → typical MIUI URL format
            Log.d(TAG, "$tag copyText is URL-like, title='$title', text='$text', " +
                "isBrowserNotification=$isBrowserNotification")
            if (isBrowserNotification) {
                Log.d(TAG, "$tag → true: URL content + browser title/text")
                return true
            }
            // Title mentions a specific app (Baidu Netdisk, Xunlei, etc.)
            Log.d(TAG, "$tag → false: URL but title='$title' is not browser-related")
            return false
        }

        // ── Approach 3: Check notification icon resource package ─────────
        val iconPackage = getNotificationIconPackage(notification)
        val isMiBrowserIcon = iconPackage != null && XiaomiPackageList.isXiaomiBrowser(iconPackage)
        Log.d(TAG, "$tag Icon pkg=$iconPackage, isMiBrowser=$isMiBrowserIcon")
        return isMiBrowserIcon
    }

    /** Try every known way to extract the Intent from a PendingIntent. */
    private fun extractIntentFromPendingIntent(pi: PendingIntent, tag: String): Intent? {
        // Method A: getIntent() — hidden API on Android 14+
        try {
            val intent = XposedHelpers.callMethod(pi, "getIntent") as? Intent
            if (intent != null) { Log.d(TAG, "$tag Extracted via getIntent()"); return intent }
        } catch (_: Throwable) {}

        // Method B: mIntent field (stock AOSP)
        try {
            val intent = XposedHelpers.getObjectField(pi, "mIntent") as? Intent
            if (intent != null) { Log.d(TAG, "$tag Extracted via mIntent"); return intent }
        } catch (_: Throwable) {}

        // Method C: mTargetIntent field (some OEM builds)
        try {
            val intent = XposedHelpers.getObjectField(pi, "mTargetIntent") as? Intent
            if (intent != null) { Log.d(TAG, "$tag Extracted via mTargetIntent"); return intent }
        } catch (_: Throwable) {}

        // Method D: Parse toString()
        try {
            val str = pi.toString()
            // Common format: PendingIntent{xxx: Intent{act=... dat=... pkg=... cmp=...}}
            val intentMatch = Regex("Intent\\{[^}]+\\}").find(str)
            if (intentMatch != null) {
                Log.d(TAG, "$tag toString contains Intent: ${intentMatch.value.take(300)}")
                // Try to extract package from the toString
                val pkgMatch = Regex("""pkg=([\\w.]+)""").find(intentMatch.value)
                val cmpMatch = Regex("""cmp=([\\w.]+/[\\w.]+)""").find(intentMatch.value)
                val datMatch = Regex("""dat=([^\\s]+)""").find(intentMatch.value)
                Log.d(TAG, "$tag toString parsed: pkg=${pkgMatch?.groupValues?.get(1)}, " +
                    "cmp=${cmpMatch?.groupValues?.get(1)}, dat=${datMatch?.groupValues?.get(1)}")
            }
        } catch (_: Throwable) {}

        // Method E: Direct reflection on fields
        try {
            for (f in pi.javaClass.declaredFields) {
                f.setAccessible(true)
                if (Intent::class.java.isAssignableFrom(f.type)) {
                    val intent = f.get(pi) as? Intent
                    if (intent != null) { Log.d(TAG, "$tag Extracted via reflection field ${f.name}"); return intent }
                }
            }
        } catch (_: Throwable) {}

        return null
    }

    /** Read mSmallIcon's resource package, or null if not available. */
    private fun getNotificationIconPackage(notification: Notification): String? {
        val smallIcon = try {
            XposedHelpers.getObjectField(notification, "mSmallIcon")
        } catch (_: Throwable) {
            return null
        }
        return try {
            XposedHelpers.callMethod(smallIcon, "getResPackage") as? String
        } catch (_: Throwable) {
            null
        }
    }

    /** Rough URL detection — covers http/https, www.*, and bare domains */
    private fun isLikelyUrl(text: String): Boolean {
        if (text.startsWith("http://") || text.startsWith("https://")) return true
        if (text.startsWith("www.")) return true
        // Match bare domains: example.com, sub.example.com/path
        return text.matches(Regex("""^[\w.-]+\.[a-zA-Z]{2,}(/.*)?$"""))
    }

    private fun shouldReplaceMiShareNotificationIcon(notification: Notification): Boolean {
        if (IntentInterceptor.hasRecentXiaomiSourceUrl()) return true

        val extras = notification.extras ?: return false
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val content = "$title\n$text\n$bigText"
        return content.contains("http") ||
            content.contains("浏览器") ||
            content.contains("browser", ignoreCase = true) ||
            content.contains("打开") ||
            content.contains("链接") ||
            content.contains("网页")
    }

    private fun replaceNotificationIconWithDefaultBrowser(
        context: Context,
        notification: Notification,
        source: String
    ) {
        val browser = DefaultBrowserResolver.resolveDefaultBrowser(context) ?: return
        try {
            val newIcon = getAppIcon(context, browser.packageName) ?: return
            XposedHelpers.setObjectField(notification, "mSmallIcon", newIcon)
            XposedHelpers.setObjectField(notification, "mLargeIcon", newIcon)
            notification.extras?.putParcelable("miui.appIcon", newIcon)
            val focusPics = notification.extras?.getBundle("miui.focus.pics")
            if (focusPics != null) {
                focusPics.putParcelable("miui.focus.pic_image", newIcon)
                focusPics.putParcelable("miui.land.pic_image", newIcon)
            }
            Log.d(TAG, "$source Replaced MiShare notification icon with ${browser.packageName}")
            XposedBridge.log("[$TAG] $source replaced MiShare icon with ${browser.packageName}")
        } catch (e: Exception) {
            Log.w(TAG, "$source Failed to replace MiShare notification icon: ${e.message}")
        }
    }

    private fun getAppIcon(context: Context, pkgName: String): Icon? {
        val pm = context.packageManager
        val drawable = try {
            pm.getApplicationIcon(pkgName)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val bitmap = if (drawable is BitmapDrawable) {
            drawable.bitmap
        } else {
            val w = Math.max(drawable.intrinsicWidth, 1)
            val h = Math.max(drawable.intrinsicHeight, 1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }
        return Icon.createWithBitmap(bitmap)
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

        hookVoiceAssistVersionGate(lpparam)
    }

    private fun hookVoiceAssistVersionGate(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "attach",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.args[0] as? Context ?: return
                        if (context.packageName != XiaomiPackageList.VOICE_ASSIST) return

                        val versionCode = readVoiceAssistVersionCode(context) ?: run {
                            Log.w(TAG, "[VoiceAssist] Version unavailable; keeping legacy-only hooks")
                            return
                        }

                        Log.i(TAG, "[VoiceAssist] Detected versionCode=$versionCode")
                        if (XiaomiPackageList.shouldHookVoiceAssistS2(versionCode)) {
                            hookHyperOs4VoiceAssistS2(lpparam.classLoader, versionCode)
                        } else {
                            Log.i(
                                TAG,
                                "[VoiceAssist] Keeping legacy-only hooks for versionCode=$versionCode " +
                                    "(< ${XiaomiPackageList.VOICE_ASSIST_S2_MIN_VERSION_CODE})"
                            )
                        }
                    }
                }
            )
            Log.i(TAG, "[VoiceAssist] Installed version gate on Application.attach")
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "[VoiceAssist] Failed to install version gate; keeping legacy-only hooks: " +
                    "${t.javaClass.simpleName} — ${t.message}"
            )
        }
    }

    private fun readVoiceAssistVersionCode(context: Context): Long? {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    XiaomiPackageList.VOICE_ASSIST,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(XiaomiPackageList.VOICE_ASSIST, 0)
            }
            info.longVersionCode
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "[VoiceAssist] Failed to read installed version: " +
                    "${t.javaClass.simpleName} — ${t.message}"
            )
            null
        }
    }

    /**
     * Super XiaoAi 8.0.30.4121 on HyperOS 4 checks the parsed screen-recognition
     * Intent through s2.isIntentAvailable() before startActivity. When Xiaomi
     * Browser is hidden, that check returns false and VoiceAssist shows
     * "未安装该应用，请先安装", so the framework startActivity hook never runs.
     *
     * This hook is deliberately installed only behind the VoiceAssist version
     * gate. Older versions keep the existing b2/f2 behavior unchanged.
     */
    private fun hookHyperOs4VoiceAssistS2(classLoader: ClassLoader, versionCode: Long) {
        val className = XiaomiPackageList.CLASS_VOICE_ASSIST_S2
        try {
            val clazz = XposedHelpers.findClass(className, classLoader)
            XposedHelpers.findAndHookMethod(
                clazz,
                "isIntentAvailable",
                Intent::class.java,
                Context::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val intent = param.args[0] as? Intent ?: return
                        val context = param.args[1] as? Context ?: return
                        if (rewriteHyperOs4VoiceAssistBrowserIntent(intent, context)) {
                            param.result = true
                        }
                    }
                }
            )
            Log.i(TAG, "[VoiceAssist-HOS4] Hooked $className.isIntentAvailable for versionCode=$versionCode")
            XposedBridge.log(
                "[$TAG] VoiceAssist-HOS4: hooked $className.isIntentAvailable " +
                    "for versionCode=$versionCode"
            )
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "[VoiceAssist-HOS4] Version gate matched ($versionCode), but $className " +
                    "hook is unavailable: ${t.javaClass.simpleName} — ${t.message}"
            )
            XposedBridge.log(
                "[$TAG] VoiceAssist-HOS4: $className unavailable for versionCode=$versionCode — " +
                    "${t.javaClass.simpleName}"
            )
        }
    }

    private fun rewriteHyperOs4VoiceAssistBrowserIntent(intent: Intent, context: Context): Boolean {
        val targetPackage = intent.`package`
        val targetComponentPackage = intent.component?.packageName
        if (!XiaomiPackageList.isXiaomiBrowser(targetPackage) &&
            !XiaomiPackageList.isXiaomiBrowser(targetComponentPackage)
        ) {
            return false
        }

        val recovered = IntentInterceptor.recoverUrlForRedirect(intent)
        if (recovered == null) {
            Log.w(
                TAG,
                "[VoiceAssist-HOS4] Xiaomi Browser intent had no recoverable web URL: ${intent.data}"
            )
            return false
        }

        intent.action = Intent.ACTION_VIEW
        intent.data = recovered
        intent.component = null
        intent.setPackage(null)
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.addCategory(Intent.CATEGORY_DEFAULT)

        val browser = DefaultBrowserResolver.resolveDefaultBrowser(context)
        if (browser?.isDefault == true) {
            intent.setPackage(browser.packageName)
        }

        Log.i(
            TAG,
            "[VoiceAssist-HOS4] Rewrote unavailable Xiaomi Browser intent to " +
                "${intent.`package` ?: "system resolver"}: $recovered"
        )
        return true
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
                            // ponytail: only return true if we actually rewrote a browser intent,
                            // otherwise let the original method proceed normally.
                            var rewroteBrowserIntent = false
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
                                        rewroteBrowserIntent = true
                                    }
                                }
                            }
                            if (rewroteBrowserIntent) {
                                param.result = true
                            }
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
        val isBrowserDownload = XiaomiPackageList.isXiaomiBrowser(marketId)
        val isVoiceAssistFallback = isVoiceAssistScreenRecognitionFallback(intent)
        if (!isBrowserDownload && !isVoiceAssistFallback) return false

        val recovered = IntentInterceptor.recoverUrlForRedirect(intent)
        if (recovered == null) {
            Log.w(TAG, "$source saw Xiaomi browser/fallback download intent but no original URL was cached: $data")
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

        val reason = if (isBrowserDownload) "xiaomi-browser-download" else "voiceassist-screen-recognition-fallback"
        Log.i(TAG, "$source rewrote $reason intent to: $recovered")
        return true
    }

    private fun isVoiceAssistScreenRecognitionFallback(intent: Intent): Boolean {
        val data = intent.data ?: return false
        val ref = data.getQueryParameter("ref")?.lowercase()
        if (ref == "xiaoai_screenrecognition") return true

        val pageRef = intent.getStringExtra("pageRef")?.lowercase()
        if (pageRef == "xiaoai_screenrecognition") return true

        val sourcePackage = intent.getStringExtra("sourcePackage")
        return sourcePackage == XiaomiPackageList.VOICE_ASSIST
    }
}
