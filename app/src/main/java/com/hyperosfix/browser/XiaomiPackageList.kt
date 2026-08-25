package com.hyperosfix.browser

/**
 * Package names and identifiers associated with Xiaomi's forced browser redirection.
 *
 * Maintained as a list so new HyperOS versions can be accommodated
 * without changing core interception logic.
 */
object XiaomiPackageList {

    // ── Xiaomi Browser variants ──────────────────────────────────────────
    /** Primary Xiaomi Browser package (stock) */
    const val BROWSER = "com.android.browser"

    /** Alternative Xiaomi Browser package names observed on some HyperOS builds */
    val BROWSER_ALT_NAMES = setOf(
        "com.miui.browser",
        "com.mi.globalbrowser",
        "com.xiaomi.browser",
    )

    // ── App stores that may host the "download browser" page ─────────────
    const val MARKET = "com.xiaomi.market"          // Mainland China
    const val MARKET_GLOBAL = "com.xiaomi.mi.global.appstore"   // Global
    const val MARKET_INDIA = "com.mi.india.appstore"            // India

    // ── System apps known to open URLs ───────────────────────────────────
    const val MI_SHARE = "com.miui.mishare.connectivity"
    const val MI_MIRROR = "com.xiaomi.mirror"
    const val SECURITY_CENTER = "com.miui.securitycenter"
    const val SYSTEM_UI = "com.android.systemui"
    const val SETTINGS = "com.android.settings"
    const val CONTENT_CATCHER = "com.miui.contentcatcher"

    // ── HyperOS AI / voice assist apps (discovered from reference module) ─
    /** Xiaomi HyperAI Engine — handles clipboard URL recognition, screen recognition */
    const val AI_ENGINE = "com.xiaomi.aicr"

    /** XiaoAi / Super XiaoAi voice assistant — opens URLs via voice commands */
    const val VOICE_ASSIST = "com.miui.voiceassist"

    /** Xiaomi AI vision assistant — may host screen recognition on newer HyperOS builds */
    const val AI_ASSIST_VISION = "com.xiaomi.aiasst.vision"

    // ── MIUI-specific class names (for per-process hooks) ────────────────
    // com.xiaomi.aicr — SmartPasswordUtils
    // Original: com.xiaomi.aicr.copydirect.util.SmartPasswordUtils
    // Obfuscated (HyperOS V816 / aicr 3.17.3): i26
    // Method isInstallForApp(Context,String)->boolean is now i26.A
    // Method jumpToXiaoMiBrowser has been removed; AI Engine now uses
    // standard startActivity which the framework hooks already catch.
    val CLASS_SMART_PASSWORD_UTILS_CANDIDATES = listOf(
        "com.xiaomi.aicr.copydirect.util.SmartPasswordUtils",
        "i26",
    )

    // com.miui.voiceassist — utility class changed across builds
    // Original: com.xiaomi.voiceassistant.utils.b2
    // Newer: com.xiaomi.voiceassistant.utils.f2 (isIntentAvailable moved here)
    // Voice Assist now uses standard startActivity — framework hooks suffice.
    val CLASS_VOICE_ASSIST_CANDIDATES = listOf(
        "com.xiaomi.voiceassistant.utils.b2",
        "com.xiaomi.voiceassistant.utils.f2",
    )

    /**
     * HyperOS 4 developer builds moved screen-recognition availability checks
     * into this utility class. Keep it separate from the legacy candidates so
     * HyperOS 3 devices retain the existing hook path.
     */
    const val CLASS_VOICE_ASSIST_S2 = "com.xiaomi.voiceassistant.utils.s2"

    /** Super XiaoAi 8.0.30.4121, observed on HyperOS 4 / Android 17. */
    const val VOICE_ASSIST_S2_MIN_VERSION_CODE = 508000030L

    fun shouldHookVoiceAssistS2(versionCode: Long): Boolean =
        versionCode >= VOICE_ASSIST_S2_MIN_VERSION_CODE

    /**
     * XiaoAi / AI Engine classes that may receive recognized screen text or
     * URL strings before Xiaomi rewrites the launch into a browser/market
     * intent. These names are intentionally candidates: missing classes are
     * expected across MIUI / HyperOS versions and are ignored by the hooker.
     */
    val URL_SOURCE_CLASS_CANDIDATES = listOf(
        // Xiaomi AI Engine / screen recognition / clipboard recognition
        "com.xiaomi.aicr.copydirect.util.SmartPasswordUtils",
        "com.xiaomi.aicr.copydirect.CopyDirectActivity",
        "com.xiaomi.aicr.copydirect.CopyDirectService",
        "com.xiaomi.aicr.screen.ScreenRecognitionActivity",
        "com.xiaomi.aicr.screen.ScreenRecognitionService",
        "com.xiaomi.aicr.smartaction.SmartActionActivity",
        "com.xiaomi.aicr.smartaction.SmartActionService",
        // Xiaomi AI vision assistant
        "com.xiaomi.aiasst.vision.ScreenRecognitionActivity",
        "com.xiaomi.aiasst.vision.ScreenRecognitionService",
        "com.xiaomi.aiasst.vision.SmartActionHandler",
        "com.xiaomi.aiasst.vision.VisionRecognitionManager",
        // XiaoAi / Super XiaoAi utility and screen-understanding layers
        "com.miui.voiceassist.ui.ScreenRecognitionActivity",
        "com.miui.voiceassist.service.ScreenRecognitionService",
        "com.xiaomi.voiceassistant.screenrecognition.ScreenRecognitionActivity",
        "com.xiaomi.voiceassistant.screenrecognition.ScreenRecognitionPresenter",
        "com.xiaomi.voiceassistant.screenrecognition.ScreenRecognitionService",
        "com.xiaomi.voiceassistant.utils.b2",
        "com.xiaomi.voiceassistant.utils.f2",
        "com.xiaomi.voiceassistant.smartaction.SmartActionHandler",
        "com.xiaomi.voiceassistant.screen.ScreenRecognitionManager",
        // Obfuscated candidates observed on some HyperOS builds
        "i26",
    )

    /**
     * All Xiaomi browser package names to check against.
     * Add new names discovered in future HyperOS builds here.
     */
    val ALL_BROWSER_PACKAGES: Set<String> = setOf(BROWSER) + BROWSER_ALT_NAMES

    /**
     * All Xiaomi app store package names.
     */
    val ALL_MARKET_PACKAGES: Set<String> = setOf(MARKET, MARKET_GLOBAL, MARKET_INDIA)

    /**
     * Known Xiaomi system apps that might forward/redirect web links.
     * These apps may implicitly force browser choice without setting
     * explicit package/component on the Intent.
     */
    val ALL_XIAOMI_SYSTEM_APPS: Set<String> = setOf(
        MI_SHARE,
        MI_MIRROR,
        SECURITY_CENTER,
        SYSTEM_UI,
        CONTENT_CATCHER,
        AI_ENGINE,
        VOICE_ASSIST,
        AI_ASSIST_VISION,
        "com.miui.home",
        "com.miui.notes",
        "com.miui.gallery",
        "com.miui.cloudservice",
        "com.xiaomi.scanner",
        "com.milink.service",
    )

    /**
     * Returns true if [pkg] is a known Xiaomi browser package.
     */
    fun isXiaomiBrowser(pkg: String?) = pkg in ALL_BROWSER_PACKAGES

    /**
     * Returns true if [pkg] is a known Xiaomi app store package.
     */
    fun isXiaomiMarket(pkg: String?) = pkg in ALL_MARKET_PACKAGES

    /**
     * Returns true if [pkg] is a known Xiaomi system app that may
     * open/redirect web links.
     */
    fun isXiaomiSystemApp(pkg: String?): Boolean {
        if (pkg == null) return false
        // ponytail: explicit whitelist + known prefixes only. "com.mi." is
        // intentionally excluded — it matches too many third-party apps
        // (com.microsoft, com.midas, etc.). Add known Xiaomi com.mi.*
        // packages to ALL_XIAOMI_SYSTEM_APPS explicitly.
        return pkg in ALL_XIAOMI_SYSTEM_APPS ||
            pkg.startsWith("com.miui.") ||
            pkg.startsWith("com.xiaomi.")
    }
}
