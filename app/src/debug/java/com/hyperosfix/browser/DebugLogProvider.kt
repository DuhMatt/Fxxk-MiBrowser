package com.hyperosfix.browser

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

class DebugLogProvider : ContentProvider() {
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
                    val file = DebugLogStore.append(
                        appContext,
                        level = extras?.getString(DebugLoggingContract.EXTRA_LEVEL) ?: "I",
                        tag = extras?.getString(DebugLoggingContract.EXTRA_TAG) ?: "UnknownTag",
                        message = extras?.getString(DebugLoggingContract.EXTRA_MESSAGE) ?: "",
                        throwable = extras?.getString(DebugLoggingContract.EXTRA_THROWABLE),
                        process = extras?.getString(DebugLoggingContract.EXTRA_PROCESS),
                        timestamp = extras?.getLong(
                            DebugLoggingContract.EXTRA_TIMESTAMP,
                            System.currentTimeMillis()
                        ) ?: System.currentTimeMillis()
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
}
