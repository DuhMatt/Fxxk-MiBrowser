package com.hyperosfix.browser

object DebugLoggingContract {
    const val PREFS_NAME = "debug_logging"
    const val KEY_ENABLED = "enabled"

    const val METHOD_GET_STATUS = "get_status"
    const val METHOD_APPEND_LOG = "append_log"

    const val EXTRA_ENABLED = "enabled"
    const val EXTRA_LEVEL = "level"
    const val EXTRA_TAG = "tag"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_THROWABLE = "throwable"
    const val EXTRA_PROCESS = "process"
    const val EXTRA_TIMESTAMP = "timestamp"
    const val EXTRA_LOG_PATH = "log_path"

    const val LOG_FILE_NAME = "module-runtime.log"
    const val ROTATED_LOG_FILE_NAME = "module-runtime.previous.log"
    const val MAX_LOG_BYTES = 5L * 1024L * 1024L
}
