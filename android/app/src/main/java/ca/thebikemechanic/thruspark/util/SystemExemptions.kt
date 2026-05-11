package ca.thebikemechanic.thruspark.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import ca.thebikemechanic.thruspark.shizuku.ShellResult
import ca.thebikemechanic.thruspark.shizuku.ShizukuManager
import ca.thebikemechanic.thruspark.shizuku.ShizukuState

private const val TAG = "SystemExemptions"

/**
 * Builds the set of packages that must NEVER be paused, regardless of the
 * active profile. Pausing any of these breaks the device:
 *
 *  - ThruSpark itself → app stops working mid-pause
 *  - Shizuku          → loses our binder, can't deactivate
 *  - System UI / android → screen freezes, no notifications
 *  - Current launcher → no home screen
 *  - Current IME      → can't type
 *  - Current dialer   → miss calls
 *  - Accessibility services (TalkBack, password managers, etc.) → silent break
 *
 * Device admins are NOT included here — that requires a Shizuku query
 * (dpm list-active-admins) which we'll add in v0.2.1. Most users have none.
 */
object SystemExemptions {

    /**
     * The "security blanket" — packages that must NEVER be paused regardless of
     * profile config. The user-facing intent is: "even if I forget to add the
     * keyboard to my exempt list, my keyboard still works."
     *
     * Three layers of protection in this file:
     *  1. MANDATORY (this set) — hard-coded packages that always stay alive
     *  2. Auto-detected (launcher / IME / dialer / accessibility services) — see resolveAll()
     *  3. Per-profile explicit_packages + categories — applied by AppPauser
     */
    private val MANDATORY = setOf(
        // Self + Shizuku — pausing these breaks ThruSpark immediately
        "ca.thebikemechanic.thruspark",
        "moe.shizuku.privileged.api",

        // Core Android — system process and SystemUI
        "android",
        "com.android.systemui",

        // Hardware stacks — even when "off" via radios, the stack itself must stay alive
        "com.android.bluetooth",
        "com.android.bluetoothmidiservice",
        "com.android.nfc",

        // Settings + permission infra — many apps query these mid-runtime
        "com.android.settings",
        "com.android.providers.settings",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.android.providers.calendar",
        "com.android.providers.contacts",
        "com.android.providers.media",
        "com.android.providers.downloads",
        "com.android.providers.userdictionary",
        "com.android.providers.telephony",

        // Play Services + Services Framework — countless apps will crash if suspended
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.ims",        // Carrier IMS / RCS messaging

        // System WebView providers — apps that use WebView (which is most of them)
        // crash hard if their WebView provider is suspended
        "com.google.android.webview",
        "com.android.webview",

        // Telephony / dialer / emergency calls — always-on for safety
        "com.android.phone",
        "com.android.server.telecom",
        "com.google.android.dialer",
        "com.android.emergency",
        "com.android.cellbroadcastreceiver",
        "com.google.android.cellbroadcastreceiver",

        // Print spooler — used by "Print to PDF" etc.
        "com.android.printspooler",

        // Common launchers (resolveActivity HOME also catches the active one,
        // but this is belt-and-suspenders for users who have multiple launchers)
        "com.google.android.apps.nexuslauncher",   // Pixel
        "com.android.launcher3",                    // AOSP / generic
        "com.sec.android.app.launcher",             // Samsung One UI
        "com.miui.home",                            // MIUI / Xiaomi
        "com.huawei.android.launcher",              // EMUI / Huawei
        "com.oneplus.launcher",                     // OnePlus
        "net.oneplus.launcher",                     // older OnePlus
        "com.motorola.launcher3",                   // Motorola

        // Common IME packages (Settings.Secure also returns the active one,
        // but again belt-and-suspenders if user has multiple)
        "com.google.android.inputmethod.latin",     // Gboard
        "com.samsung.android.honeyboard",           // Samsung Keyboard
        "com.touchtype.swiftkey"                    // SwiftKey
    )

    /**
     * Synchronous variant — used where Shizuku-dependent device-admin
     * enumeration isn't available (e.g. notification filter setup, which
     * runs on the main thread of the listener). Misses device admins; safer
     * fallback than blocking on a binder call.
     */
    fun resolveAll(context: Context): Set<String> = buildSet {
        addAll(MANDATORY)
        currentLauncher(context)?.let { add(it) }
        currentIme(context)?.let { add(it) }
        currentDialer(context)?.let { add(it) }
        addAll(accessibilityServices(context))
    }.also { Log.d(TAG, "${it.size} system-exempt packages (sync)") }

    /**
     * Suspend variant — additionally enumerates active device admins via
     * Shizuku (audit M3 fix). Use this from the app-pause path so corporate
     * MDM apps and other device-admin-marked apps don't get suspended.
     *
     * If Shizuku isn't granted we fall back to the sync set.
     */
    suspend fun resolveAllWithDeviceAdmins(context: Context): Set<String> = buildSet {
        addAll(resolveAll(context))
        addAll(activeDeviceAdmins())
    }.also { Log.d(TAG, "${it.size} system-exempt packages (with device admins)") }

    /**
     * Parses `dumpsys device_policy` to find packages registered as device
     * administrators. Requires Shizuku — the public DevicePolicyManager API
     * doesn't let regular apps see other apps' admin status.
     *
     * Output we look for:
     *   Active admins for user 0:
     *     admin=com.example.app/com.example.app.MyAdminReceiver
     *
     * We extract the package portion (before the slash). Empty set if
     * Shizuku isn't granted, or dumpsys returns nothing parseable.
     */
    private suspend fun activeDeviceAdmins(): Set<String> {
        if (ShizukuManager.state.value != ShizukuState.Granted) return emptySet()
        val result = ShizukuManager.exec("dumpsys device_policy")
        if (result !is ShellResult.Success || !result.ok) return emptySet()

        val adminLineRegex = Regex("""admin=([^/\s]+)/""")
        return result.stdout.lineSequence()
            .mapNotNull { line -> adminLineRegex.find(line)?.groupValues?.getOrNull(1) }
            .toSet()
            .also { Log.d(TAG, "Found ${it.size} device admin package(s): $it") }
    }

    private fun currentLauncher(context: Context): String? = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
    }.getOrNull()

    private fun currentIme(context: Context): String? = runCatching {
        // DEFAULT_INPUT_METHOD format is "package/.ServiceClass" — strip after the slash
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.split("/")?.firstOrNull()
    }.getOrNull()

    private fun currentDialer(context: Context): String? = runCatching {
        // Resolve the default ACTION_DIAL handler — works without privileged perms
        val intent = Intent(Intent.ACTION_DIAL)
        context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
    }.getOrNull()

    private fun accessibilityServices(context: Context): Set<String> = runCatching {
        val am = context.getSystemService(AccessibilityManager::class.java) ?: return@runCatching emptySet()
        am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).map { it.resolveInfo.serviceInfo.packageName }.toSet()
    }.getOrDefault(emptySet())
}
