package com.hyperosfix.browser

import android.app.Application
import android.os.Bundle
import de.robv.android.xposed.XposedBridge

object ModuleLog {
    private var enabledCache = false
    private var enabledCacheAt = 0L
    private const val STATUS_CACHE_MS = 2_000L

    fun d(tag: String, message: String): Int = log("D", tag, message, null)

    fun i(tag: String, message: String): Int = log("I", tag, message, null)

    fun w(tag: String, message: String): Int = log("W", tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable): Int = log("W", tag, message, throwable)

    fun e(tag: String, message: String): Int = log("E", tag, message, null)

    fun e(tag: String, message: String, throwable: Throwable): Int = log("E", tag, message, throwable)

    private fun log(level: String, tag: String, message: String, throwable: Throwable?): Int {
        val priority = when (level) {
            "D" -> android.util.Log.d(tag, message, throwable)
            "I" -> android.util.Log.i(tag, message, throwable)
            "W" -> android.util.Log.w(tag, message, throwable)
            "E" -> android.util.Log.e(tag, message, throwable)
            else -> android.util.Log.v(tag, message, throwable)
        }

        try {
            XposedBridge.log("[$tag][$level] $message")
            if (throwable != null) {
                XposedBridge.log(throwable)
            }
        } catch (_: Throwable) {
        }

        if (!BuildConfig.DEBUG) {
            return priority
        }

        val app = currentApplication() ?: return priority
        if (!isPersistentLoggingEnabled(app)) {
            return priority
        }

        try {
            app.contentResolver.call(
                DebugPanelUris.providerUri(app),
                DebugLoggingContract.METHOD_APPEND_LOG,
                null,
                Bundle().apply {
                    putString(DebugLoggingContract.EXTRA_LEVEL, level)
                    putString(DebugLoggingContract.EXTRA_TAG, tag)
                    putString(DebugLoggingContract.EXTRA_MESSAGE, message)
                    putString(DebugLoggingContract.EXTRA_THROWABLE, DebugLogStore.stringifyThrowable(throwable))
                    putString(DebugLoggingContract.EXTRA_PROCESS, currentProcessName())
                    putLong(DebugLoggingContract.EXTRA_TIMESTAMP, System.currentTimeMillis())
                }
            )
        } catch (_: Throwable) {
        }
        return priority
    }

    private fun isPersistentLoggingEnabled(app: Application): Boolean {
        val now = System.currentTimeMillis()
        if (now - enabledCacheAt <= STATUS_CACHE_MS) {
            return enabledCache
        }
        return try {
            val bundle = app.contentResolver.call(
                DebugPanelUris.providerUri(app),
                DebugLoggingContract.METHOD_GET_STATUS,
                null,
                null
            )
            val enabled = bundle?.getBoolean(DebugLoggingContract.EXTRA_ENABLED, false) == true
            enabledCache = enabled
            enabledCacheAt = now
            enabled
        } catch (_: Throwable) {
            false
        }
    }

    private fun currentApplication(): Application? {
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val method = activityThread.getDeclaredMethod("currentApplication")
            method.invoke(null) as? Application
        } catch (_: Throwable) {
            null
        }
    }

    private fun currentProcessName(): String {
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val method = activityThread.getDeclaredMethod("currentProcessName")
            method.invoke(null) as? String ?: "unknown-process"
        } catch (_: Throwable) {
            "unknown-process"
        }
    }
}
