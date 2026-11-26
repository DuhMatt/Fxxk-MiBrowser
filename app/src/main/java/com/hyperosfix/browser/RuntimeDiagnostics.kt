package com.hyperosfix.browser

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

/** Diagnostic environment metadata for the Log APK only. */
object RuntimeDiagnostics {
    const val TAG = "HyperOSBrowserFix_Runtime"

    private const val SUPER_XIAOAI_PACKAGE = "com.miui.voiceassist"
    private const val HYPEROS_AI_ENGINE_PACKAGE = "com.xiaomi.aicr"
    private val hookedAppInfoCache = ConcurrentHashMap<String, String>()

    /**
     * Returns device and relevant Xiaomi component versions when persistent
     * logging first receives an event. This is intentionally lazy so enabling
     * logging after an app process has started does not lose the metadata.
     */
    fun environmentMetadata(context: Context): List<String> {
        val hyperOs = readSystemProperty("ro.mi.os.version.name").ifBlank { "unavailable" }
        val miui = readSystemProperty("ro.miui.ui.version.name").ifBlank { "unavailable" }

        return listOf(
            "Device: manufacturer=${Build.MANUFACTURER} model=${Build.MODEL} device=${Build.DEVICE}",
            "System: HyperOS=$hyperOs MIUI=$miui buildDisplay=${Build.DISPLAY} " +
                "buildIncremental=${Build.VERSION.INCREMENTAL}",
            "Android: version=${Build.VERSION.RELEASE} SDK=${Build.VERSION.SDK_INT} " +
                "securityPatch=${Build.VERSION.SECURITY_PATCH}",
            "System app: name=Super XiaoAi package=$SUPER_XIAOAI_PACKAGE " +
                packageVersion(context, SUPER_XIAOAI_PACKAGE),
            "System app: name=HyperOS AI Engine package=$HYPEROS_AI_ENGINE_PACKAGE " +
                packageVersion(context, HYPEROS_AI_ENGINE_PACKAGE),
            "Log module: version=${BuildConfig.VERSION_NAME} versionCode=${BuildConfig.VERSION_CODE}"
        )
    }

    /** Returns the version of the package whose process is currently hooked. */
    fun hookedAppMetadata(context: Context, packageName: String): String =
        hookedAppInfoCache.computeIfAbsent(packageName) {
            "Hooked app: package=$packageName ${packageVersion(context, packageName)}"
        }

    private fun packageVersion(context: Context, packageName: String): String {
        return try {
            val info = getPackageInfo(context.packageManager, packageName)
            "versionName=${info.versionName ?: "unknown"} versionCode=${versionCode(info)}"
        } catch (_: PackageManager.NameNotFoundException) {
            "versionName=unavailable versionCode=unavailable status=not-installed"
        } catch (error: Throwable) {
            "versionName=unavailable versionCode=unavailable error=${error.javaClass.simpleName}"
        }
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(packageManager: PackageManager, packageName: String): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            packageManager.getPackageInfo(packageName, 0)
        }
    }

    private fun versionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
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
