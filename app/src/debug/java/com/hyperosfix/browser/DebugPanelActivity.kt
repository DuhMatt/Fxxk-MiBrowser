package com.hyperosfix.browser

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider

class DebugPanelActivity : Activity() {
    private lateinit var loggingSwitch: Switch
    private lateinit var shareButton: Button
    private lateinit var locationView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_panel)

        loggingSwitch = findViewById(R.id.switch_logging)
        shareButton = findViewById(R.id.button_share_log)
        locationView = findViewById(R.id.text_log_location)

        loggingSwitch.setOnCheckedChangeListener { _, isChecked ->
            DebugLogStore.setEnabled(this, isChecked)
            refreshUi()
        }

        shareButton.setOnClickListener {
            shareLogFile()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        val enabled = DebugLogStore.isEnabled(this)
        if (loggingSwitch.isChecked != enabled) {
            loggingSwitch.isChecked = enabled
        }

        val logFile = DebugLogStore.getLogFile(this)
        locationView.text = getString(
            R.string.debug_log_location_value,
            logFile?.absolutePath ?: getString(R.string.debug_log_unavailable)
        )
        // Keep the button tappable so an empty log reports what is missing
        // instead of looking like a broken control.
        shareButton.isEnabled = true
    }

    private fun shareLogFile() {
        val logFile = DebugLogStore.getLogFile(this)
        if (logFile == null || !logFile.isFile || logFile.length() == 0L) {
            Toast.makeText(
                this,
                "No log file yet. Enable logging and reproduce the issue first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val shareUri: Uri = FileProvider.getUriForFile(
                this,
                DebugPanelUris.fileProviderAuthority(this),
                logFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.debug_share_subject))
                clipData = ClipData.newUri(contentResolver, logFile.name, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(
                shareIntent,
                getString(R.string.debug_share_button)
            ).apply {
                clipData = shareIntent.clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(chooser)
        } catch (error: Exception) {
            Log.e("DebugPanelActivity", "Failed to share log file", error)
            Toast.makeText(
                this,
                "Could not open the share sheet: ${error.message ?: error.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
