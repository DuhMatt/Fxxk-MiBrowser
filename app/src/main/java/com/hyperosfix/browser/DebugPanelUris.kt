package com.hyperosfix.browser

import android.content.Context
import android.net.Uri

object DebugPanelUris {
    fun providerAuthority(context: Context): String = "${BuildConfig.APPLICATION_ID}.debug.provider"

    fun providerUri(context: Context): Uri = Uri.parse("content://${providerAuthority(context)}")

    fun fileProviderAuthority(context: Context): String = "${BuildConfig.APPLICATION_ID}.fileprovider"
}
