package com.hyperosfix.browser

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogStore {
    private val lock = Any()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(DebugLoggingContract.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(DebugLoggingContract.KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(DebugLoggingContract.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(DebugLoggingContract.KEY_ENABLED, enabled).apply()
    }

    fun append(
        context: Context,
        level: String,
        tag: String,
        message: String,
        throwable: String?,
        process: String?,
        timestamp: Long
    ): File? {
        val dir = getLogDir(context) ?: return null
        val file = File(dir, DebugLoggingContract.LOG_FILE_NAME)
        synchronized(lock) {
            rotateIfNeeded(file)
            file.parentFile?.mkdirs()
            val entry = buildString {
                append(timeFormat.format(Date(timestamp)))
                append(" | ")
                append(level)
                append(" | ")
                append(process ?: "unknown-process")
                append(" | ")
                append(tag)
                append(" | ")
                append(message)
                if (!throwable.isNullOrBlank()) {
                    append('\n')
                    append(throwable.trim())
                }
                append('\n')
            }
            file.appendText(entry)
        }
        return file
    }

    fun getLogFile(context: Context): File? {
        val dir = getLogDir(context) ?: return null
        return File(dir, DebugLoggingContract.LOG_FILE_NAME)
    }

    fun getLogDir(context: Context): File? {
        return context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
    }

    fun stringifyThrowable(throwable: Throwable?): String? {
        if (throwable == null) return null
        return StringWriter().also { writer ->
            throwable.printStackTrace(PrintWriter(writer))
        }.toString()
    }

    private fun rotateIfNeeded(file: File) {
        if (!file.exists()) return
        if (file.length() < DebugLoggingContract.MAX_LOG_BYTES) return
        val previous = File(file.parentFile, DebugLoggingContract.ROTATED_LOG_FILE_NAME)
        if (previous.exists()) {
            previous.delete()
        }
        file.renameTo(previous)
    }
}
