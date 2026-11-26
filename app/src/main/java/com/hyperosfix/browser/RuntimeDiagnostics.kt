package com.hyperosfix.browser

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.hyperosfix.browser.ModuleLog as Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.atomic.AtomicBoolean

/** Diagnostic environment metadata for the Log APK only. */
object RuntimeDiagnostics {
    private const val TAG = "HyperOSBrowserFix_Runtime"
    private val deviceInfoLogged = AtomicBoolean(false)

    fun install() {
        if (!BuildConfig.DEBUG) return

        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "attach",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.args.firstOrNull() as? Context ?: return
                        logHookedApp(context)
                        if (deviceInfoLogged.compareAndSet(false, true)) {
                            logDeviceInfo()
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to install runtime metadata hook: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun logHookedApp(context: Context) {
        val packageName = context.packageName
        val packageInfo = try {
            getPackageInfo(context.packageManager, packageName)
        } catch (t: Throwable) {
            Log.w(TAG, "Hooked app version unavailable: package=$packageName error=${t.javaClass.simpleName}")
            return
        }

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        Log.i(
            TAG,
            "Hooked app: package=$packageName versionName=${packageInfo.versionName ?: "unknown"} " +
                "versionCode=$versionCode"
        )
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(packageManager: PackageManager, packageName: String): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            packageManager.getPackageInfo(packageName, 0)
        }
    }

    private fun logDeviceInfo() {
        val hyperOs = readSystemProperty("ro.mi.os.version.name")
        val miui = readSystemProperty("ro.miui.ui.version.name")
        val systemVersion = listOfNotNull(
            hyperOs.takeIf(String::isNotBlank)?.let { "HyperOS=$it" },
            miui.takeIf(String::isNotBlank)?.let { "MIUI=$it" },
            "buildDisplay=${Build.DISPLAY}",
            "buildIncremental=${Build.VERSION.INCREMENTAL}"
        ).joinToString(" ")

        Log.i(
            TAG,
            "Device: manufacturer=${Build.MANUFACTURER} model=${Build.MODEL} device=${Build.DEVICE}; " +
                "$systemVersion; Android=${Build.VERSION.RELEASE} SDK=${Build.VERSION.SDK_INT} " +
                "securityPatch=${Build.VERSION.SECURITY_PATCH}; module=${BuildConfig.VERSION_NAME}"
        )
    }

    private fun readSystemProperty(name: String): String {
        return try {
            val properties = Class.forName("android.os.SystemProperties")
            val get = properties.getMethod("get", String::class.java, String::class.java)
            (get.invoke(null, name, "") as? String).orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }
}
