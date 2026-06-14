package com.hyperosfix.browser

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.util.Log

/**
 * Queries the system for the user's default browser.
 *
 * Strategy:
 * 1. Use [PackageManager.MATCH_DEFAULT_ONLY] to find activities that are
 *    registered as the default handler for http/https intents.
 * 2. If exactly one result: that's the default browser.
 * 3. If zero results: no default is set → should show the system chooser.
 * 4. If multiple results (unlikely with MATCH_DEFAULT_ONLY): pick the first.
 */
object DefaultBrowserResolver {

    private const val TAG = "HyperOSBrowserFix_Resolver"

    /**
     * Result of resolving the default browser.
     */
    data class BrowserInfo(
        val packageName: String,
        val activityName: String?,  // null if resolved via package-level only
        val isDefault: Boolean       // true = user has set a default
    )

    /**
     * Builds a minimal "open web page" intent to probe the system resolver.
     * Using "https://" as a neutral URI.
     */
    private fun buildProbeIntent() =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addCategory(Intent.CATEGORY_DEFAULT)
        }

    /**
     * Resolve the system's default browser for http/https intents.
     *
     * @param context Any context (Application context preferred)
     * @return [BrowserInfo] with the resolved browser, or null if no browser at all.
     */
    fun resolveDefaultBrowser(context: Context): BrowserInfo? {
        val pm = context.packageManager
        val probeIntent = buildProbeIntent()

        // Step 1: Try MATCH_DEFAULT_ONLY — returns only the configured default
        val defaultResolve = try {
            pm.resolveActivity(probeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (e: Exception) {
            Log.w(TAG, "resolveActivity with MATCH_DEFAULT_ONLY failed", e)
            null
        }

        if (defaultResolve != null && defaultResolve.activityInfo != null) {
            val pkg = defaultResolve.activityInfo.packageName
            val act = defaultResolve.activityInfo.name

            if (!XiaomiPackageList.isXiaomiBrowser(pkg)) {
                Log.i(TAG, "Default browser found: $pkg / $act")
                return BrowserInfo(pkg, act, isDefault = true)
            }
            // Fall through — if the "default" is somehow Xiaomi Browser,
            // we treat it as if no default is set (force chooser).
            Log.w(TAG, "Default browser is Xiaomi Browser ($pkg) — will force chooser instead")
        }

        // Step 2: No default set (or default is Xiaomi Browser).
        // Query all activities that can handle the intent.
        val allBrowsers: List<ResolveInfo> = try {
            pm.queryIntentActivities(probeIntent, PackageManager.MATCH_ALL)
        } catch (e: Exception) {
            Log.e(TAG, "queryIntentActivities failed", e)
            emptyList()
        }

        if (allBrowsers.isEmpty()) {
            Log.w(TAG, "No browser found at all on this device")
            return null
        }

        allBrowsers.firstOrNull { !XiaomiPackageList.isXiaomiBrowser(it.activityInfo.packageName) }
            ?.let { first ->
            val pkg = first.activityInfo.packageName
            val act = first.activityInfo.name
            Log.i(TAG, "No default; first available non-Xiaomi browser: $pkg / $act")
            return BrowserInfo(pkg, act, isDefault = false)
        }

        // If ALL browsers are Xiaomi browsers (unlikely but possible):
        // still return the first one so something opens.
        val fallback = allBrowsers.first()
        Log.w(TAG, "All browsers are Xiaomi browsers, falling back to: ${fallback.activityInfo.packageName}")
        return BrowserInfo(
            fallback.activityInfo.packageName,
            fallback.activityInfo.name,
            isDefault = false
        )
    }

    /**
     * Create an Intent that will show the system browser chooser dialog.
     * This bypasses the forced component/package targeting.
     */
    fun buildChooserIntent(originalData: Uri): Intent {
        return Intent(Intent.ACTION_VIEW, originalData).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addCategory(Intent.CATEGORY_DEFAULT)
            // Explicitly DO NOT set package or component — let the system resolve
            // Use Intent.createChooser to force the disambiguation dialog
        }
    }

    /**
     * Build an Intent targeting a specific browser package.
     * Only used as a fallback; normally we let the chooser decide.
     */
    fun buildSpecificBrowserIntent(originalData: Uri, pkg: String): Intent {
        return Intent(Intent.ACTION_VIEW, originalData).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addCategory(Intent.CATEGORY_DEFAULT)
            setPackage(pkg)
        }
    }

    /**
     * Build an Intent that opens the default browser's main activity
     * (no specific URL). Used as a fallback when the original URL was
     * lost (e.g., converted to a market:// download link by Mi Share).
     *
     * Uses https:// as a neutral entry point — browsers always handle
     * this scheme, unlike about:blank which many browsers reject
     * (resulting in START_ABORTED / result code -91).
     */
    fun buildOpenBrowserIntent(pkg: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addCategory(Intent.CATEGORY_DEFAULT)
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
