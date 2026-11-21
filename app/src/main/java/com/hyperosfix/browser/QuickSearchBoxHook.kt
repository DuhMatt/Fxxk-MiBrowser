package com.hyperosfix.browser

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.hyperosfix.browser.ModuleLog as Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.HashMap

/**
 * Redirects the HyperOS desktop pull-down search
 * (`com.android.quicksearchbox`) away from Xiaomi Browser and into the
 * user's own default browser, using that browser's own search engine.
 *
 * ## Why a dedicated hook is needed
 *
 * [IntentInterceptor] only looks at `ACTION_VIEW` / `ACTION_MAIN` intents that
 * carry a web URI. The search box never builds such an intent: it builds
 * `ACTION_SEARCH` with an explicit `com.android.browser` component and passes
 * the query in a string extra. Both of those fall outside the generic
 * interception rules, so the framework hooks in [MainHook] never fire.
 *
 * ## Hooked methods (QSB 13.1.0.110325, R8-obfuscated)
 *
 * All three live in the obfuscated `V2.p1` utility class, which apktool maps
 * back to `com.android.quicksearchbox.xiaomi.util`:
 *
 * | method | role |
 * | --- | --- |
 * | `F(Context, Intent)` | `startActivityNoThrow` — single funnel for every
 *   `startActivity` the search box performs. Also used by `XiaomiSearch`, which
 *   handles incoming `ACTION_WEB_SEARCH` intents. |
 * | `c(Context, Intent, String, String, HashMap)` | `startBrowserActivity` —
 *   the browser-search path. Builds the `FullSearchActivity` intent and puts
 *   `search_query` / `query` extras on it. |
 * | `L(Context, Intent, String, String)` | `startDpLinkActivityNoThrow` —
 *   deep-link launches, including the AI suggestion branch. |
 *
 * `c()` is a self-contained launcher: it calls `startActivity` directly rather
 * than going through `F()`, so both have to be hooked.
 *
 * ## Rewrite strategy
 *
 * The query is pulled straight out of the intent/extras, then the search box's
 * intent is discarded and replaced by:
 *
 * - `ACTION_WEB_SEARCH` + `setPackage(defaultBrowser)` when that browser
 *   declares the action (Via does), so the browser applies its *own* search
 *   engine; or
 * - `ACTION_VIEW` on a plain search URL when it does not.
 *
 * Explicitly targeting the package matters: an implicit `ACTION_WEB_SEARCH`
 * resolves to `com.android.intentresolver/.ResolverActivity` on HyperOS (no
 * default search handler is configured), which would pop a chooser instead of
 * opening the browser.
 */
object QuickSearchBoxHook {
    private const val TAG = "HyperOSBrowserFix_QSB"

    /** Desktop pull-down search box package. */
    private const val QSB = XiaomiPackageList.QUICK_SEARCH_BOX

    /**
     * Obfuscated utility class holding the launch helpers.
     *
     * Pinned to the QSB version shipped on the target device
     * (13.1.0.110325 / versionCode 213010025). Re-derive it with apktool and
     * `grep -rn 'fullsearch/FullSearchActivity'` after a QSB update.
     */
    private const val UTIL_CLASS = "V2.p1"

    /** HyperOS reports this package when no default handler is configured. */
    private const val RESOLVER_PACKAGE = "com.android.intentresolver"

    /**
     * Only used when the default browser cannot handle `ACTION_WEB_SEARCH`.
     * In that case the search engine has to be supplied here; Bing is used so
     * that the query does not fall back to the engine Xiaomi forces.
     */
    private const val FALLBACK_SEARCH_TEMPLATE = "https://www.bing.com/search?q=%s"

    /** String extras the search box may carry the raw query in. */
    private val KEYWORD_EXTRAS = arrayOf(
        "query",
        "search_query",
        "q",
        "keyword",
        "searchTerms",
    )

    /** Query parameters a search-engine URL may carry the raw query in. */
    private val KEYWORD_PARAMS = arrayOf(
        "q",
        "wd",
        "word",
        "keyword",
        "query",
        "searchTerms",
    )

    private data class SearchTarget(val packageName: String, val supportsWebSearch: Boolean)

    // ── Hook installation ────────────────────────────────────────────────

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader
        val util = try {
            XposedHelpers.findClass(UTIL_CLASS, classLoader)
        } catch (t: Throwable) {
            Log.w(TAG, "Util class $UTIL_CLASS missing — QSB build changed? (${t.javaClass.simpleName})")
            return
        }

        // startActivityNoThrow(Context, Intent): Boolean
        hookMethod(
            util,
            "F",
            arrayOf<Any>(Context::class.java, Intent::class.java)
        ) { param ->
            val context = param.args[0] as? Context ?: return@hookMethod
            val intent = param.args[1] as? Intent ?: return@hookMethod
            if (!isXiaomiBrowserTarget(intent)) return@hookMethod
            val keyword = keywordFromIntent(intent) ?: return@hookMethod
            if (!dispatch(context, keyword, "F")) return@hookMethod
            // Original returns Boolean: report success so callers skip their
            // own startActivity and their "app not installed" toasts.
            param.setResult(true)
        }

        // startBrowserActivity(Context, Intent, String url, String query, HashMap): void
        hookMethod(
            util,
            "c",
            arrayOf<Any>(
                Context::class.java,
                Intent::class.java,
                String::class.java,
                String::class.java,
                HashMap::class.java,
            )
        ) { param ->
            val context = param.args[0] as? Context ?: return@hookMethod
            val intent = param.args[1] as? Intent
            val url = param.args[2] as? String
            // args[3] carries the raw query; the URL is only a fallback.
            val keyword = firstKeyword(param.args[3] as? String, keywordFromUrl(url))
                ?: keywordFromIntent(intent)
                ?: return@hookMethod
            if (!dispatch(context, keyword, "c")) return@hookMethod
            // Original returns void.
            param.setResult(null)
        }

        // startDpLinkActivityNoThrow(Context, Intent, String, String): void
        hookMethod(
            util,
            "L",
            arrayOf<Any>(
                Context::class.java,
                Intent::class.java,
                String::class.java,
                String::class.java,
            )
        ) { param ->
            val context = param.args[0] as? Context ?: return@hookMethod
            val intent = param.args[1] as? Intent ?: return@hookMethod
            // Deep links are not always searches; only rewrite when the target
            // is Xiaomi Browser and a concrete query is present.
            if (!isXiaomiBrowserTarget(intent)) return@hookMethod
            val keyword = keywordFromIntent(intent) ?: return@hookMethod
            if (!dispatch(context, keyword, "L")) return@hookMethod
            param.setResult(null)
        }
    }

    /**
     * Safety net for search-box launches that bypass the three hooked helpers.
     * Called from [IntentInterceptor] before the generic rules, which would
     * otherwise reject these intents because they are not `ACTION_VIEW`.
     *
     * @return true when the intent was handled and must not proceed.
     */
    fun handleFrameworkCall(
        context: Context,
        intent: Intent,
        param: XC_MethodHook.MethodHookParam
    ): Boolean {
        // Runs on every startActivity in every scoped process, so bail out
        // before touching the intent when the caller is not the search box.
        val caller = try {
            context.packageName
        } catch (t: Throwable) {
            return false
        }
        if (caller != QSB) return false
        if (!isXiaomiBrowserTarget(intent)) return false
        val keyword = keywordFromIntent(intent) ?: return false
        if (!dispatch(context, keyword, "framework")) return false
        param.setResult(null)
        return true
    }

    // ── Intent analysis ──────────────────────────────────────────────────

    private fun isXiaomiBrowserTarget(intent: Intent?): Boolean {
        if (intent == null) return false
        if (XiaomiPackageList.isXiaomiBrowser(intent.`package`)) return true
        val component = intent.component ?: return false
        return XiaomiPackageList.isXiaomiBrowser(component.packageName)
    }

    private fun keywordFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        for (name in KEYWORD_EXTRAS) {
            val value = try {
                intent.getStringExtra(name)
            } catch (t: Throwable) {
                null
            }
            if (isPlausibleKeyword(value)) return value
        }
        return keywordFromUrl(intent.dataString)
    }

    private fun keywordFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = try {
            Uri.parse(url)
        } catch (t: Throwable) {
            return null
        }
        for (name in KEYWORD_PARAMS) {
            val value = try {
                uri.getQueryParameter(name)
            } catch (t: Throwable) {
                null
            }
            if (isPlausibleKeyword(value)) return value
        }
        return null
    }

    private fun firstKeyword(vararg candidates: String?): String? {
        for (candidate in candidates) {
            if (isPlausibleKeyword(candidate)) return candidate
        }
        return null
    }

    /**
     * Rejects blanks and unfilled URL templates, e.g. the `%s` inside
     * `https://www.baidu.com/s?wd=%s`.
     */
    private fun isPlausibleKeyword(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        if (value.contains("%s") || value.contains("{searchTerms}")) return false
        return true
    }

    // ── Re-dispatch ──────────────────────────────────────────────────────

    private fun dispatch(context: Context, keyword: String, label: String): Boolean {
        val query = keyword.trim()
        if (query.isEmpty()) return false

        val target = pickBrowser(context)
        if (target == null) {
            Log.w(TAG, "[$label] no non-Xiaomi browser available; leaving original intent")
            return false
        }

        val outgoing = if (target.supportsWebSearch) {
            Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                setPackage(target.packageName)
            }
        } else {
            val encoded = Uri.encode(query)
            DefaultBrowserResolver.buildWebIntent(
                Uri.parse(String.format(FALLBACK_SEARCH_TEMPLATE, encoded)),
                target.packageName
            )
        }
        outgoing.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(outgoing)
            Log.i(
                TAG,
                "[$label] query=\"$query\" -> ${target.packageName} " +
                    "(webSearch=${target.supportsWebSearch})"
            )
            true
        } catch (t: Throwable) {
            Log.w(TAG, "[$label] failed to launch ${target.packageName}", t)
            false
        }
    }

    private fun pickBrowser(context: Context): SearchTarget? {
        val pm = context.packageManager

        // 1. Prefer the browser the user actually configured as default.
        val resolved = DefaultBrowserResolver.resolveDefaultBrowser(context)
        val defaultPackage = resolved?.packageName
        if (defaultPackage != null &&
            defaultPackage != RESOLVER_PACKAGE &&
            !XiaomiPackageList.isXiaomiBrowser(defaultPackage)
        ) {
            return SearchTarget(defaultPackage, supportsWebSearch(pm, defaultPackage))
        }

        // 2. No default set: take any installed browser that is not Xiaomi's.
        val probe = DefaultBrowserResolver.buildProbeIntent()
        val candidates = try {
            pm.queryIntentActivities(probe, PackageManager.MATCH_ALL)
        } catch (t: Throwable) {
            Log.w(TAG, "queryIntentActivities failed", t)
            emptyList()
        }
        val packageName = candidates
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
            .firstOrNull { it != RESOLVER_PACKAGE && !XiaomiPackageList.isXiaomiBrowser(it) }
            ?: return null

        return SearchTarget(packageName, supportsWebSearch(pm, packageName))
    }

    private fun supportsWebSearch(pm: PackageManager, packageName: String): Boolean {
        val probe = Intent(Intent.ACTION_WEB_SEARCH).setPackage(packageName)
        return try {
            pm.queryIntentActivities(probe, PackageManager.MATCH_ALL).isNotEmpty()
        } catch (t: Throwable) {
            Log.w(TAG, "web-search probe failed for $packageName", t)
            false
        }
    }

    // ── Plumbing ─────────────────────────────────────────────────────────

    private fun hookMethod(
        clazz: Class<*>,
        methodName: String,
        paramTypes: Array<Any>,
        callback: (XC_MethodHook.MethodHookParam) -> Unit
    ) {
        try {
            val method = XposedHelpers.findMethodExact(clazz, methodName, *paramTypes)
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    callback(param)
                }
            })
            Log.i(TAG, "Hooked ${clazz.name}.$methodName")
            XposedBridge.log("[$TAG] Hooked ${clazz.name}.$methodName")
        } catch (t: Throwable) {
            Log.w(TAG, "Hook unavailable: ${clazz.name}.$methodName — ${t.javaClass.simpleName}")
            XposedBridge.log("[$TAG] Hook unavailable: ${clazz.name}.$methodName — ${t.javaClass.simpleName}")
        }
    }
}
