package com.hyperosfix.browser

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Core interception logic for web-link intents that are being forced
 * to Xiaomi Browser or Xiaomi App Store.
 *
 * ## Architecture
 *
 * We hook two methods:
 * - [ContextImpl.startActivity] — the main gateway for all Activity starts.
 * - [Instrumentation.execStartActivity] — a secondary path used by some system components.
 *
 * When an [Intent] with ACTION_VIEW + http/https scheme is detected:
 * 1. Check if its package/component targets Xiaomi Browser or Xiaomi Market.
 * 2. If so, clean the intent (remove forced package/component).
 * 3. Re-dispatch to the system's default browser or the browser chooser.
 *
 * ## Infinite-loop prevention
 *
 * A [ThreadLocal] flag + a per-Intent-id [ConcurrentHashMap] guard
 * ensures that re-dispatching does not re-trigger the hook on the same Intent.
 *
 * ## Scope
 *
 * Only intercepts ACTION_VIEW with http/https scheme.
 * Non-web intents (tel:, sms:, geo:, market:, file:, custom schemes) pass through untouched.
 */
object IntentInterceptor {

    private const val TAG = "HyperOSBrowserFix_Intent"

    // ── Re-entrancy guard ────────────────────────────────────────────────
    // We use TWO layers to be safe:
    // 1. A ThreadLocal counter for simple same-thread recursion.
    // 2. A ConcurrentHashMap of "seen" Intent identity hash codes, cleared
    //    periodically to prevent memory leak.

    private val threadGuard = ThreadLocal<Boolean>()
    private val seenIntentIds = ConcurrentHashMap.newKeySet<Int>()

    @Volatile
    private var lastXiaomiSourceUrl: Uri? = null

    @Volatile
    private var lastXiaomiSourceUrlAt: Long = 0L

    @Volatile
    private var lastXiaomiSourceLabel: String = "unknown"

    /** Max size before we clear the seen-intents set to avoid memory leak. */
    private const val MAX_SEEN_INTENTS = 200
    private const val XIAOMI_SOURCE_URL_CACHE_MS = 2 * 60 * 1000L
    private const val MAX_OBJECT_SCAN_DEPTH = 4

    // ── Public API: called from MainHook ─────────────────────────────────

    /**
     * Called from [ContextImpl.startActivity] hook (before invocation).
     *
     * @param intent   The Intent being launched.
     * @param context  The calling Context (used for re-dispatching).
     * @param options  The optional ActivityOptions Bundle.
     * @param param    The [XC_MethodHook.MethodHookParam] so we can set result.
     */
    fun onStartActivity(
        intent: Intent?,
        context: Context?,
        options: Bundle?,
        param: XC_MethodHook.MethodHookParam
    ) {
        if (intent == null || context == null) return

        if (!shouldIntercept(intent, context.packageName)) return

        if (!enterGuard(intent)) return

        try {
            Log.i(TAG, "Intercepted web intent from ${context.packageName}: ${intent.data}")
            redirectIntent(intent, context, options, param)
        } finally {
            exitGuard(intent)
        }
    }

    /**
     * Secondary hook: [Instrumentation.execStartActivity].
     * Some system components bypass ContextImpl and call Instrumentation directly.
     */
    fun onExecStartActivity(
        context: Context?,
        intent: Intent?,
        param: XC_MethodHook.MethodHookParam
    ) {
        if (intent == null || context == null) return

        if (!shouldIntercept(intent, context.packageName)) return

        if (!enterGuard(intent)) return

        try {
            Log.i(TAG, "Intercepted (Instrumentation) from ${context.packageName}: ${intent.data}")
            redirectIntent(intent, context, null, param, " (Instrumentation)")
        } finally {
            exitGuard(intent)
        }
    }

    private fun redirectIntent(
        original: Intent,
        context: Context,
        options: Bundle?,
        param: XC_MethodHook.MethodHookParam,
        label: String = ""
    ) {
        val cleaned = cleanIntent(original)
        val browser = DefaultBrowserResolver.resolveDefaultBrowser(context)
        if (browser == null) {
            Log.w(TAG, "No browser found, letting original proceed$label")
            return
        }

        val effectiveData = cleaned.data ?: return
        if (isUnopenableXiaomiScheme(effectiveData)) {
            if (isXiaomiBrowserDownloadUri(original.data)) {
                Log.w(TAG, "URL recovery failed$label; canceling Xiaomi Market download-page intent")
                param.result = null
            } else {
                Log.w(TAG, "URL recovery failed$label; keeping original intent instead of opening https://")
            }
            return
        }

        val replacement = if (browser.isDefault) {
            DefaultBrowserResolver.buildSpecificBrowserIntent(effectiveData, browser.packageName)
        } else {
            Intent.createChooser(DefaultBrowserResolver.buildChooserIntent(effectiveData), "Open with")
        }.apply {
            flags = cleaned.flags
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtras(cleaned)
        }

        Log.i(TAG, "Redirecting$label to: ${replacement.component ?: replacement.`package` ?: "chooser"}")
        param.result = null
        try {
            context.startActivity(replacement, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start replacement$label", e)
        }
    }

    /**
     * Mi Share sometimes converts the URL into a market:// browser-download
     * Intent before startActivity/PendingIntent hooks see it. Cache the original
     * URL when we still have access to Mi Share's service Intent.
     */
    internal fun rememberMiShareUrl(intent: Intent) {
        rememberWebUrlFromIntent(intent, "Mi Share")
    }

    /**
     * Cache a web URL seen inside Xiaomi system apps before they rewrite it
     * into `market://details?id=com.android.browser`.
     *
     * XiaoAi Screen Recognition often sees the real URL first, then checks or
     * launches Xiaomi Browser. If the browser is uninstalled, the later Intent
     * may only contain the Market download page, so this short-lived cache is
     * the safest way to restore the original link.
     */
    internal fun rememberWebUrlFromIntent(intent: Intent, source: String) {
        val url = extractLikelyWebUriFromIntent(intent)
        if (url != null) {
            rememberWebUrl(url, source)
        }
    }

    internal fun rememberWebUrlFromValue(value: Any?, source: String) {
        val url = extractLikelyWebUriFromValue(value, newVisitedSet())
        if (url != null) {
            rememberWebUrl(url, source)
        }
    }

    internal fun recoverUrl(intent: Intent): Uri? {
        return extractWebUriFromIntent(intent) ?: getRecentXiaomiSourceUrl()
    }

    internal fun recoverUrlForRedirect(intent: Intent): Uri? {
        val scheme = intent.data?.scheme
        return when {
            scheme == "market" -> recoverUrlFromMarketIntent(intent)
            scheme == "intent" -> recoverUrlFromIntentScheme(intent) ?: getRecentXiaomiSourceUrl()
            scheme == "mi" || scheme?.startsWith("mi") == true ->
                recoverUrlFromMiScheme(intent) ?: getRecentXiaomiSourceUrl()
            else -> recoverUrl(intent)
        }
    }

    // ── Decision logic ───────────────────────────────────────────────────

    /**
     * Returns true if this Intent should be intercepted and potentially redirected.
     *
     * Conditions:
     * - ACTION_VIEW (or ACTION_MAIN with http/https data)
     * - http / https / market / mi scheme
     * - Target package/component is a known Xiaomi browser or market,
     *   OR the caller is a known Xiaomi system app and the data is http/https
     *   OR the scheme is "mi" (Xiaomi's custom URL scheme that wraps real URLs)
     */
    private fun shouldIntercept(intent: Intent, callerPackage: String): Boolean {
        // Only ACTION_VIEW (or ACTION_MAIN which some apps use for web intents)
        val action = intent.action
        if (action != Intent.ACTION_VIEW && action != Intent.ACTION_MAIN) return false

        // Must have a web-like URI, either as Intent.data or tucked into
        // extras/ClipData by Xiaomi system components.
        val data: Uri = intent.data ?: extractWebUriFromIntent(intent) ?: return false

        val scheme: String = data.scheme ?: return false

        // Check if this Intent is being forced to Xiaomi Browser or Market
        val targetPkg = intent.`package`
        val targetComponent = intent.component

        val targetsXiaomiBrowser = XiaomiPackageList.isXiaomiBrowser(targetPkg) ||
            (targetComponent != null && XiaomiPackageList.isXiaomiBrowser(targetComponent.packageName))

        val targetsXiaomiMarket = XiaomiPackageList.isXiaomiMarket(targetPkg) ||
            (targetComponent != null && XiaomiPackageList.isXiaomiMarket(targetComponent.packageName))

        // Case A: http/https URL forced to Xiaomi Browser → intercept
        if ((scheme == "http" || scheme == "https") && targetsXiaomiBrowser) {
            Log.d(TAG, "Will intercept (http→browser): targetPkg=$targetPkg, component=$targetComponent, " +
                "caller=$callerPackage, data=$data")
            return true
        }

        // Case B: market:// URL targeting Xiaomi Market → intercept and try to recover URL
        if (scheme == "market" && targetsXiaomiMarket) {
            Log.d(TAG, "Will intercept (market→market): targetPkg=$targetPkg, component=$targetComponent, " +
                "caller=$callerPackage, data=$data")
            return true
        }

        // Case C: http/https URL with Xiaomi Market as target (browser uninstalled redirect)
        if ((scheme == "http" || scheme == "https") && targetsXiaomiMarket) {
            Log.d(TAG, "Will intercept (http→market): targetPkg=$targetPkg, component=$targetComponent, " +
                "caller=$callerPackage, data=$data")
            return true
        }

        // Case D: http/https URL with NO explicit target but called from a
        // Xiaomi system app. HyperOS may redirect implicitly.
        if ((scheme == "http" || scheme == "https") &&
            targetPkg == null && targetComponent == null &&
            XiaomiPackageList.isXiaomiSystemApp(callerPackage)) {
            Log.d(TAG, "Will intercept (http from Xiaomi sys app): caller=$callerPackage, data=$data")
            return true
        }

        // Case D2: Wi-Fi settings' "Manage Xiaomi router" may route the
        // router admin page through Xiaomi Browser. Keep it on the user's
        // system default browser, especially when Xiaomi Browser is disabled.
        if ((scheme == "http" || scheme == "https") &&
            callerPackage == XiaomiPackageList.SETTINGS &&
            isRouterAdminUrl(data)) {
            Log.d(TAG, "Will intercept (Settings router admin): caller=$callerPackage, data=$data")
            return true
        }

        // Case E: mi:// scheme — Xiaomi's custom URL wrapper
        // (used by Xiaomi AI Engine and voice assistant)
        if ((scheme == "mi" || scheme.startsWith("mi")) &&
            (isXiaomiBrowserDownloadUri(data) || recoverUrlFromMiScheme(intent) != null)) {
            Log.d(TAG, "Will intercept (mi:// scheme): caller=$callerPackage, data=$data")
            return true
        }

        // Case F: intent:// scheme used to wrap URLs in some Xiaomi flows
        if (scheme == "intent" && targetsXiaomiBrowser) {
            Log.d(TAG, "Will intercept (intent:// scheme → browser): caller=$callerPackage, data=$data")
            return true
        }

        // Case G: market:// URL with id=<xiaomi_browser> but no explicit package target.
        // This is what Mi Share does: it calls startActivity(market://details?id=com.android.browser)
        // with pkg=null. The URL has already been converted to a market link.
        if (scheme == "market" && targetPkg == null && targetComponent == null) {
            if (isXiaomiBrowserDownloadUri(data)) {
                Log.d(TAG, "Will intercept (market://id=browser): caller=$callerPackage, data=$data")
                return true
            }
        }

        return false
    }

    // ── Intent cleaning ──────────────────────────────────────────────────

    /**
     * Remove forced package/component targeting from the Intent.
     *
     * This strips the explicit "go to Xiaomi Browser" directive so the system
     * resolver can pick the user's default browser instead.
     *
     * Also attempts to recover the original URL from extras if the data URI
     * has been transformed into a market:// download-page URI.
     */
    private fun cleanIntent(intent: Intent): Intent {
        val cleaned = Intent(intent)  // copy

        if (cleaned.data == null) {
            extractWebUriFromIntent(cleaned)?.let {
                Log.i(TAG, "Recovered URL from intent payload: $it")
                cleaned.data = it
            }
        }

        // Remove forced package targeting
        if (XiaomiPackageList.isXiaomiBrowser(cleaned.`package`) ||
            XiaomiPackageList.isXiaomiMarket(cleaned.`package`)) {
            Log.d(TAG, "Removing forced package: ${cleaned.`package`}")
            cleaned.`package` = null
        }

        // Remove forced component targeting
        val comp = cleaned.component
        if (comp != null && (XiaomiPackageList.isXiaomiBrowser(comp.packageName) ||
                XiaomiPackageList.isXiaomiMarket(comp.packageName))) {
            Log.d(TAG, "Removing forced component: $comp")
            cleaned.component = null
        }

        // Attempt to recover original URL from market:// or mi:// scheme intents.
        // When Xiaomi Browser is uninstalled, Mi Share often redirects to
        // Xiaomi Market with a market://details?... URI that embeds the
        // original URL as a referrer or extra parameter.
        // Xiaomi AI Engine and voice assistant use mi:// scheme to wrap URLs.
        val data = cleaned.data
        if (data != null) {
            val scheme = data.scheme
            if (scheme == "market") {
                val recovered = recoverUrlFromMarketIntent(cleaned)
                if (recovered != null) {
                    Log.i(TAG, "Recovered original URL from market intent: $recovered")
                    cleaned.data = recovered
                }
            } else if (scheme == "mi" || (scheme != null && scheme.startsWith("mi"))) {
                val recovered = recoverUrlForRedirect(cleaned)
                if (recovered != null) {
                    Log.i(TAG, "Recovered original URL from mi:// intent: $recovered")
                    cleaned.data = recovered
                }
            } else if (scheme == "intent") {
                val recovered = recoverUrlFromIntentScheme(cleaned)
                if (recovered != null) {
                    Log.i(TAG, "Recovered original URL from intent:// scheme: $recovered")
                    cleaned.data = recovered
                }
            }
        }

        return cleaned
    }

    /**
     * Try to recover the original web URL from an intent that has been
     * re-targeted to Xiaomi Market (market://details?...).
     *
     * Common patterns observed in HyperOS:
     * - `intent.getStringExtra("android.intent.extra.REFERRER")` contains the original URL
     * - `intent.data.getQueryParameter("url")` or `"referrer"`
     */
    private fun recoverUrlFromMarketIntent(intent: Intent): Uri? {
        extractWebUriFromIntent(intent)?.let { return it }

        val data: Uri = intent.data ?: return null

        val urlParam = data.getQueryParameter("url")
            ?: data.getQueryParameter("referrer")
            ?: data.getQueryParameter("link")
            ?: data.getQueryParameter("target_url")
        if (!urlParam.isNullOrEmpty() &&
            (urlParam.startsWith("http://") || urlParam.startsWith("https://"))) {
            return Uri.parse(urlParam)
        }

        getRecentXiaomiSourceUrl()?.let {
            Log.i(TAG, "Recovered original URL from Xiaomi source cache: $it")
            return it
        }

        return null
    }

    /**
     * Extract the real web URL from Xiaomi's mi:// custom scheme.
     *
     * Xiaomi AI Engine / voice assistant wraps URLs like:
     *   mi://<encoded_data>?url=https://real.url.com
     * or buries the URL in query parameters or extras.
     *
     * Strategy (from reference module):
     * 1. Check known query parameter keys: url, query, q, link, text
     * 2. Sniff all query parameters for http-like values
     * 3. Search extras Bundle for URL-like string values
     * 4. URL-decode and normalize the result
     */
    internal fun recoverUrlFromMiScheme(intent: Intent): Uri? {
        val data: Uri = intent.data ?: return null

        // Priority 1: Known key names
        val targetKeys = arrayOf("url", "web_url", "query", "q", "link", "target_url", "text")
        for (key in targetKeys) {
            try {
                val value = data.getQueryParameter(key)
                if (!value.isNullOrEmpty()) {
                    val trimmed = value.trim()
                    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        return Uri.parse(trimmed)
                    }
                }
            } catch (_: Exception) {}
        }

        // Priority 2: Sniff all query parameters for http-like values
        try {
            for (key in data.queryParameterNames) {
                val value = data.getQueryParameter(key)
                if (!value.isNullOrEmpty()) {
                    val trimmed = value.trim()
                    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        return Uri.parse(trimmed)
                    }
                    if (looksLikeDomain(trimmed)) {
                        return Uri.parse(normalizeUrl(trimmed))
                    }
                }
            }
        } catch (_: Exception) {}

        // Priority 3: Search extras for URL-like strings
        if (intent.extras != null) {
            for (key in intent.extras!!.keySet()) {
                val value = intent.extras!!.get(key)
                if (value is String) {
                    val trimmed = value.trim()
                    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        return Uri.parse(trimmed)
                    }
                    if (looksLikeDomain(trimmed)) {
                        return Uri.parse(normalizeUrl(trimmed))
                    }
                }
            }
        }

        return null
    }

    /**
     * Extract URL from intent:// scheme (used in some Xiaomi flows).
     * intent:// URLs often wrap a real URL in query parameters.
     */
    internal fun recoverUrlFromIntentScheme(intent: Intent): Uri? {
        val data: Uri = intent.data ?: return null

        // Try query parameters
        val targetKeys = arrayOf("url", "web_url", "link", "target_url", "q")
        for (key in targetKeys) {
            try {
                val value = data.getQueryParameter(key)
                if (!value.isNullOrEmpty()) {
                    val trimmed = value.trim()
                    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        return Uri.parse(trimmed)
                    }
                }
            } catch (_: Exception) {}
        }

        // Try extras
        if (intent.extras != null) {
            for (key in intent.extras!!.keySet()) {
                val value = intent.extras!!.get(key)
                if (value is String && (value.startsWith("http://") || value.startsWith("https://"))) {
                    return Uri.parse(value)
                }
            }
        }

        return null
    }

    /**
     * Ensure a URL string has a proper scheme.
     * If the trimmed string starts with "www." or just a domain, prepend "https://".
     */
    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("www.") -> "https://$trimmed"
            else -> "https://$trimmed"
        }
    }

    private fun looksLikeDomain(raw: String): Boolean {
        val value = raw.trim()
        if (value.length <= 4 || value.contains(" ")) return false
        if (value.any { it.isUpperCase() }) return false
        if (!value.contains(".")) return false
        if (value.startsWith("0x")) return false
        if (looksLikeLocalArtifactPath(value)) return false
        if (value.startsWith("android.intent.")) return false
        if (value.contains("com.miui.") || value.contains("com.xiaomi.") || value.contains("com.android.")) {
            return false
        }
        if (XiaomiPackageList.isXiaomiBrowser(value) || XiaomiPackageList.isXiaomiMarket(value)) return false
        if (looksLikeAndroidPackageInsteadOfDomain(value)) return false

        val host = value.substringBefore('/').substringBefore('?').substringBefore('#')
        val labels = host.split('.')
        if (labels.size < 2) return false
        val tld = labels.last()
        return tld.length in 2..24 && tld.all { it.isLetter() }
    }

    private fun looksLikeLocalArtifactPath(value: String): Boolean {
        val lower = value.lowercase()
        val artifactExtensions = arrayOf(
            ".apk", ".apks", ".xapk", ".dex", ".jar", ".so", ".odex", ".vdex", ".art"
        )
        return artifactExtensions.any { extension ->
            lower == extension.removePrefix(".") ||
                lower.endsWith(extension) ||
                lower.contains("$extension!") ||
                lower.contains("$extension/")
        }
    }

    private fun isAndroidPackageName(value: String): Boolean {
        val parts = value.split('.')
        if (parts.size < 3) return false
        return parts.all { part ->
            part.isNotEmpty() &&
                part.first().isLetter() &&
                part.all { it.isLetterOrDigit() || it == '_' }
        }
    }

    private fun looksLikeAndroidPackageInsteadOfDomain(value: String): Boolean {
        val lower = value.lowercase()
        if (lower.startsWith("www.")) return false
        if (!isAndroidPackageName(lower)) return false

        return lower.startsWith("android.") ||
            lower.startsWith("androidx.") ||
            lower.startsWith("com.") ||
            lower.startsWith("org.lsposed.") ||
            lower.startsWith("de.robv.android.") ||
            lower.startsWith("me.weishu.") ||
            lower.startsWith("li.songe.")
    }

    private fun isLikelyUserWebUrl(uri: Uri): Boolean {
        val scheme = uri.scheme ?: return false
        if (scheme != "http" && scheme != "https") return false

        val host = uri.host?.lowercase() ?: return false
        if (XiaomiPackageList.isXiaomiBrowser(host) || XiaomiPackageList.isXiaomiMarket(host)) {
            return false
        }

        val url = uri.toString().lowercase()
        val path = uri.path?.lowercase().orEmpty()
        val imageOrAsset = path.endsWith(".png") ||
            path.endsWith(".jpg") ||
            path.endsWith(".jpeg") ||
            path.endsWith(".webp") ||
            path.endsWith(".gif") ||
            path.endsWith(".svg") ||
            path.endsWith(".ico")
        if (imageOrAsset) return false

        val xiaomiAssetHost = host.endsWith("mi-fds.com") ||
            host.endsWith("xiaomi.com") && (
                host.contains("icon") ||
                    host.contains("resource") ||
                    host.contains("cdn")
                )
        if (xiaomiAssetHost && (
                url.contains("icon") ||
                    url.contains("resource") ||
                    url.contains("logo") ||
                    url.contains("asset")
                )) {
            return false
        }

        return true
    }

    private fun isUnopenableXiaomiScheme(uri: Uri): Boolean {
        val scheme = uri.scheme ?: return false
        return scheme == "market" || scheme.startsWith("mi")
    }

    private fun isXiaomiBrowserDownloadUri(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme ?: return false
        if (scheme != "market" && !scheme.startsWith("mi")) return false
        return XiaomiPackageList.isXiaomiBrowser(uri.getQueryParameter("id"))
    }

    private fun isRouterAdminUrl(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        if (host == "miwifi.com" || host.endsWith(".miwifi.com")) return true
        if (host == "router.miwifi.com" || host == "www.miwifi.com") return true

        val path = uri.path?.lowercase().orEmpty()
        if ((path.contains("miwifi") || path.contains("luci")) && isPrivateHost(host)) {
            return true
        }

        // Xiaomi router admin pages usually use the current gateway address.
        return isPrivateGatewayHost(host)
    }

    private fun isPrivateGatewayHost(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false

        val nums = parts.map { it.toIntOrNull() ?: return false }
        if (nums.any { it !in 0..255 }) return false

        return when {
            nums[0] == 10 && nums[3] == 1 -> true
            nums[0] == 192 && nums[1] == 168 && nums[3] == 1 -> true
            nums[0] == 172 && nums[1] in 16..31 && nums[3] == 1 -> true
            else -> false
        }
    }

    private fun isPrivateHost(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false

        val nums = parts.map { it.toIntOrNull() ?: return false }
        if (nums.any { it !in 0..255 }) return false

        return nums[0] == 10 ||
            (nums[0] == 192 && nums[1] == 168) ||
            (nums[0] == 172 && nums[1] in 16..31)
    }

    private fun rememberWebUrl(url: Uri, source: String) {
        lastXiaomiSourceUrl = url
        lastXiaomiSourceUrlAt = System.currentTimeMillis()
        lastXiaomiSourceLabel = source
        Log.i(TAG, "Cached Xiaomi source URL from $source: $url")
    }

    private fun getRecentXiaomiSourceUrl(): Uri? {
        val url = lastXiaomiSourceUrl ?: return null
        val age = System.currentTimeMillis() - lastXiaomiSourceUrlAt
        return if (age in 0..XIAOMI_SOURCE_URL_CACHE_MS) {
            Log.d(TAG, "Using cached Xiaomi source URL from $lastXiaomiSourceLabel, age=${age}ms")
            url
        } else {
            null
        }
    }

    private fun extractWebUriFromIntent(intent: Intent): Uri? {
        extractWebUri(intent.data?.toString())?.let { return it }

        val knownExtras = arrayOf(
            Intent.EXTRA_TEXT,
            Intent.EXTRA_HTML_TEXT,
            Intent.EXTRA_REFERRER_NAME,
            "android.intent.extra.REFERRER",
            "url",
            "uri",
            "link",
            "target_url",
            "referrer",
            "text",
            "share_url",
            "content",
            "android.intent.extra.TEXT"
        )
        for (key in knownExtras) {
            val value = runCatching { intent.extras?.get(key) }.getOrNull()
            extractWebUriFromValue(value, newVisitedSet())?.let { return it }
        }

        extractWebUriFromBundle(intent.extras)?.let { return it }
        extractWebUriFromClipData(intent.clipData)?.let { return it }

        return null
    }

    private fun extractLikelyWebUriFromIntent(intent: Intent): Uri? {
        extractWebUri(intent.data?.toString())?.takeIf { isLikelyUserWebUrl(it) }?.let { return it }

        val knownExtras = arrayOf(
            Intent.EXTRA_TEXT,
            Intent.EXTRA_HTML_TEXT,
            Intent.EXTRA_REFERRER_NAME,
            "android.intent.extra.REFERRER",
            "url",
            "uri",
            "link",
            "target_url",
            "referrer",
            "text",
            "share_url",
            "content",
            "android.intent.extra.TEXT"
        )
        for (key in knownExtras) {
            val value = runCatching { intent.extras?.get(key) }.getOrNull()
            extractLikelyWebUriFromValue(value, newVisitedSet())?.let { return it }
        }

        extractLikelyWebUriFromBundle(intent.extras)?.let { return it }
        extractLikelyWebUriFromClipData(intent.clipData)?.let { return it }

        return null
    }

    private fun extractWebUriFromBundle(bundle: Bundle?): Uri? {
        if (bundle == null) return null
        for (key in bundle.keySet()) {
            val value = runCatching { bundle.get(key) }.getOrNull()
            extractWebUriFromValue(value, newVisitedSet())?.let { return it }
        }
        return null
    }

    private fun extractLikelyWebUriFromBundle(bundle: Bundle?): Uri? {
        if (bundle == null) return null
        for (key in bundle.keySet()) {
            val value = runCatching { bundle.get(key) }.getOrNull()
            extractLikelyWebUriFromValue(value, newVisitedSet())?.let { return it }
        }
        return null
    }

    private fun extractWebUriFromClipData(clipData: ClipData?): Uri? {
        if (clipData == null) return null
        for (i in 0 until clipData.itemCount) {
            val item = clipData.getItemAt(i) ?: continue
            extractWebUri(item.uri?.toString())?.let { return it }
            extractWebUri(item.text?.toString())?.let { return it }
            item.intent?.let { extractWebUriFromIntent(it)?.let { uri -> return uri } }
        }
        return null
    }

    private fun extractLikelyWebUriFromClipData(clipData: ClipData?): Uri? {
        if (clipData == null) return null
        for (i in 0 until clipData.itemCount) {
            val item = clipData.getItemAt(i) ?: continue
            extractWebUri(item.uri?.toString())?.takeIf { isLikelyUserWebUrl(it) }?.let { return it }
            extractWebUri(item.text?.toString())?.takeIf { isLikelyUserWebUrl(it) }?.let { return it }
            item.intent?.let { extractLikelyWebUriFromIntent(it)?.let { uri -> return uri } }
        }
        return null
    }

    private fun extractWebUriFromValue(
        value: Any?,
        visited: MutableSet<Any>,
        depth: Int = 0
    ): Uri? {
        return when (value) {
            null -> null
            is Uri -> extractWebUri(value.toString())
            is Intent -> extractWebUriFromIntent(value)
            is Bundle -> extractWebUriFromBundle(value)
            is CharSequence -> extractWebUri(value.toString())
            is Array<*> -> value.asSequence().mapNotNull {
                extractWebUriFromValue(it, visited, depth + 1)
            }.firstOrNull()
            is Iterable<*> -> value.asSequence().mapNotNull {
                extractWebUriFromValue(it, visited, depth + 1)
            }.firstOrNull()
            else -> extractWebUri(value.toString())
                ?: extractWebUriFromObjectFields(value, visited, depth)
        }
    }

    private fun extractLikelyWebUriFromValue(
        value: Any?,
        visited: MutableSet<Any>,
        depth: Int = 0
    ): Uri? {
        return when (value) {
            null -> null
            is Uri -> extractWebUri(value.toString())?.takeIf { isLikelyUserWebUrl(it) }
            is Intent -> extractLikelyWebUriFromIntent(value)
            is Bundle -> extractLikelyWebUriFromBundle(value)
            is CharSequence -> extractWebUri(value.toString())?.takeIf { isLikelyUserWebUrl(it) }
            is Array<*> -> value.asSequence().mapNotNull {
                extractLikelyWebUriFromValue(it, visited, depth + 1)
            }.firstOrNull()
            is Iterable<*> -> value.asSequence().mapNotNull {
                extractLikelyWebUriFromValue(it, visited, depth + 1)
            }.firstOrNull()
            else -> extractWebUri(value.toString())?.takeIf { isLikelyUserWebUrl(it) }
                ?: extractLikelyWebUriFromObjectFields(value, visited, depth)
        }
    }

    private fun extractWebUriFromObjectFields(
        value: Any,
        visited: MutableSet<Any>,
        depth: Int
    ): Uri? {
        if (depth >= MAX_OBJECT_SCAN_DEPTH) return null
        if (!visited.add(value)) return null

        var clazz: Class<*>? = value.javaClass
        val className = clazz?.name ?: return null
        if (shouldSkipObjectFieldScan(className)) {
            return null
        }

        while (clazz != null && clazz != Any::class.java) {
            val ownerName = clazz.name
            if (shouldSkipObjectFieldScan(ownerName)) break
            for (field in clazz.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) continue
                val ownerClass = clazz
                val fieldValue = runCatching {
                    field.isAccessible = true
                    field.get(value)
                }.getOrNull() ?: continue

                extractWebUriFromValue(fieldValue, visited, depth + 1)?.let {
                    Log.i(TAG, "Recovered URL from object field ${ownerClass.name}.${field.name}: $it")
                    return it
                }
            }
            clazz = clazz.superclass
        }

        return null
    }

    private fun extractLikelyWebUriFromObjectFields(
        value: Any,
        visited: MutableSet<Any>,
        depth: Int
    ): Uri? {
        if (depth >= MAX_OBJECT_SCAN_DEPTH) return null
        if (!visited.add(value)) return null

        var clazz: Class<*>? = value.javaClass
        val className = clazz?.name ?: return null
        if (shouldSkipObjectFieldScan(className)) {
            return null
        }

        while (clazz != null && clazz != Any::class.java) {
            val ownerName = clazz.name
            if (shouldSkipObjectFieldScan(ownerName)) break
            for (field in clazz.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) continue
                val ownerClass = clazz
                val fieldValue = runCatching {
                    field.isAccessible = true
                    field.get(value)
                }.getOrNull() ?: continue

                extractLikelyWebUriFromValue(fieldValue, visited, depth + 1)?.let {
                    Log.i(TAG, "Recovered likely user URL from object field ${ownerClass.name}.${field.name}: $it")
                    return it
                }
            }
            clazz = clazz.superclass
        }

        return null
    }

    private fun shouldSkipObjectFieldScan(className: String): Boolean {
        return className.startsWith("java.") ||
            className.startsWith("kotlin.") ||
            className.startsWith("android.") ||
            className.startsWith("androidx.") ||
            className.startsWith("dalvik.") ||
            className.startsWith("com.android.") ||
            className == "java.lang.ClassLoader" ||
            className.endsWith("ClassLoader")
    }

    private fun extractWebUri(raw: String?): Uri? {
        if (raw.isNullOrBlank()) return null

        val direct = raw.trim()
        if (direct.startsWith("http://") || direct.startsWith("https://")) {
            return Uri.parse(direct)
        }

        val decoded = runCatching { Uri.decode(direct) }.getOrDefault(direct)
        extractWebUriFromKnownParameter(decoded)?.let { return it }

        Regex("""https?://[^\s"'<>]+""").find(decoded)?.let { match ->
            return Uri.parse(match.value.trimEnd(')', ']', '}', ',', '.', ';'))
        }

        val domainMatch = Regex("""(?i)\b(?:www\.)?[a-z0-9][a-z0-9-]*(?:\.[a-z0-9][a-z0-9-]*)+\b(?:/[^\s"'<>#;]*)?""")
            .findAll(decoded)
            .map { it.value.trimEnd(')', ']', '}', ',', '.', ';') }
            .firstOrNull { looksLikeDomain(it) }

        return domainMatch?.let { Uri.parse(normalizeUrl(it)) }
    }

    private fun extractWebUriFromKnownParameter(decoded: String): Uri? {
        val paramPattern = Regex("""(?i)(?:^|[?&#;])(?:url|web_url|link|target_url|q)=([^&#;\s"'<>]+)""")
        for (match in paramPattern.findAll(decoded)) {
            val value = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (value.isEmpty()) continue
            val unescaped = runCatching { Uri.decode(value) }.getOrDefault(value)
            val candidate = when {
                unescaped.startsWith("http://") || unescaped.startsWith("https://") -> unescaped
                looksLikeDomain(unescaped) -> normalizeUrl(unescaped)
                else -> null
            } ?: continue

            val uri = Uri.parse(candidate)
            if (isLikelyUserWebUrl(uri)) return uri
        }
        return null
    }

    private fun newVisitedSet(): MutableSet<Any> {
        return Collections.newSetFromMap(IdentityHashMap())
    }

    // ── Re-entrancy guards ───────────────────────────────────────────────

    /**
     * Returns true if we should proceed with interception.
     * Returns false if we are already processing this intent (re-entrancy).
     */
    private fun enterGuard(intent: Intent): Boolean {
        val id = System.identityHashCode(intent)

        // Thread-local check
        if (threadGuard.get() == true) {
            Log.d(TAG, "Re-entrancy guard: skipping (same thread already processing)")
            return false
        }

        // Cross-thread check
        if (!seenIntentIds.add(id)) {
            Log.d(TAG, "Re-entrancy guard: skipping (intent already seen: $id)")
            return false
        }

        // Prevent unbounded growth of seenIntentIds
        if (seenIntentIds.size > MAX_SEEN_INTENTS) {
            Log.d(TAG, "Clearing seen intent cache (size=${seenIntentIds.size})")
            seenIntentIds.clear()
        }

        threadGuard.set(true)
        return true
    }

    private fun exitGuard(@Suppress("UNUSED_PARAMETER") intent: Intent) {
        threadGuard.remove()
        // Keep the intent in seenIntentIds briefly to prevent rapid re-entry
        // It will be cleaned up when the cache grows too large.
    }
}
