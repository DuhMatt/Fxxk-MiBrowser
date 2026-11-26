package com.hyperosfix.browser

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

class DebugLogProvider : ContentProvider() {
    private val metadataLock = Any()
    private var environmentMetadataWritten = false
    private val hookedAppMetadataWritten = mutableSetOf<String>()

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val appContext = context?.applicationContext ?: return Bundle.EMPTY
        return when (method) {
            DebugLoggingContract.METHOD_GET_STATUS -> Bundle().apply {
                putBoolean(DebugLoggingContract.EXTRA_ENABLED, DebugLogStore.isEnabled(appContext))
                putString(
                    DebugLoggingContract.EXTRA_LOG_PATH,
                    DebugLogStore.getLogFile(appContext)?.absolutePath.orEmpty()
                )
            }

            DebugLoggingContract.METHOD_APPEND_LOG -> {
                if (DebugLogStore.isEnabled(appContext)) {
                    val timestamp = extras?.getLong(
                        DebugLoggingContract.EXTRA_TIMESTAMP,
                        System.currentTimeMillis()
                    ) ?: System.currentTimeMillis()
                    val process = extras?.getString(DebugLoggingContract.EXTRA_PROCESS)
                    val hookedPackage = extras
                        ?.getString(DebugLoggingContract.EXTRA_HOOKED_PACKAGE)
                        ?.takeIf(String::isNotBlank)
                        ?: process
                            ?.substringBefore(':')
                            ?.takeIf { it.isNotBlank() && it != "unknown-process" && '.' in it }
                            .orEmpty()
                    val hookedAppInfo = extras
                        ?.getString(DebugLoggingContract.EXTRA_HOOKED_APP_INFO)
                        ?.takeIf(String::isNotBlank)
                        ?: hookedPackage.takeIf(String::isNotBlank)?.let {
                            RuntimeDiagnostics.hookedAppMetadata(appContext, it)
                        }.orEmpty()

                    try {
                        appendMetadataIfNeeded(
                            appContext,
                            hookedPackage,
                            hookedAppInfo,
                            process,
                            timestamp
                        )
                    } catch (error: Throwable) {
                        android.util.Log.w(
                            "DebugLogProvider",
                            "Could not append runtime metadata: ${error.javaClass.simpleName}",
                            error
                        )
                    }

                    val file = DebugLogStore.append(
                        appContext,
                        level = extras?.getString(DebugLoggingContract.EXTRA_LEVEL) ?: "I",
                        tag = extras?.getString(DebugLoggingContract.EXTRA_TAG) ?: "UnknownTag",
                        message = extras?.getString(DebugLoggingContract.EXTRA_MESSAGE) ?: "",
                        throwable = extras?.getString(DebugLoggingContract.EXTRA_THROWABLE),
                        process = process,
                        timestamp = timestamp
                    )
                    Bundle().apply {
                        putString(DebugLoggingContract.EXTRA_LOG_PATH, file?.absolutePath.orEmpty())
                    }
                } else {
                    Bundle.EMPTY
                }
            }

            else -> Bundle.EMPTY
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private fun appendMetadataIfNeeded(
        context: android.content.Context,
        hookedPackage: String,
        hookedAppInfo: String,
        process: String?,
        timestamp: Long
    ) {
        synchronized(metadataLock) {
            val currentLog = DebugLogStore.getLogFile(context)
            if (currentLog == null || !currentLog.exists() || currentLog.length() == 0L ||
                currentLog.length() >= DebugLoggingContract.MAX_LOG_BYTES
            ) {
                environmentMetadataWritten = false
                hookedAppMetadataWritten.clear()
            }

            if (!environmentMetadataWritten) {
                val entries = RuntimeDiagnostics.environmentMetadata(context)
                val written = entries.all { message ->
                    DebugLogStore.append(
                        context,
                        level = "I",
                        tag = RuntimeDiagnostics.TAG,
                        message = message,
                        throwable = null,
                        process = process,
                        timestamp = timestamp
                    ) != null
                }
                environmentMetadataWritten = written
            }

            if (hookedPackage.isNotBlank() && hookedAppInfo.isNotBlank() &&
                hookedPackage !in hookedAppMetadataWritten
            ) {
                val file = DebugLogStore.append(
                    context,
                    level = "I",
                    tag = RuntimeDiagnostics.TAG,
                    message = hookedAppInfo,
                    throwable = null,
                    process = process,
                    timestamp = timestamp
                )
                if (file != null) hookedAppMetadataWritten.add(hookedPackage)
            }
        }
    }
}
