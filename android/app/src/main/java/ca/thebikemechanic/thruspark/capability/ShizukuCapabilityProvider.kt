package ca.thebikemechanic.thruspark.capability

import android.content.Context
import android.util.Log
import ca.thebikemechanic.thruspark.model.ThruSparkProfile
import ca.thebikemechanic.thruspark.shizuku.ShellResult
import ca.thebikemechanic.thruspark.shizuku.ShizukuManager

private const val TAG = "ShizukuCapability"

/**
 * Tier 2 capability provider — delegates to a StandardCapabilityProvider for
 * everything that already works without Shizuku, then layers on the Tier 2
 * commands via Shizuku's shell binder.
 *
 * The composition pattern means we don't reimplement brightness/DND/timeout/
 * dark mode here — Standard does those, Shizuku adds airplane, refresh rate,
 * grayscale, radios, NFC.
 *
 * v0.1 Tier 2 capabilities implemented:
 *   - Airplane mode
 *   - Wi-Fi, Bluetooth, NFC, Cellular toggle
 *   - Refresh rate cap
 *   - Grayscale (Daltonizer)
 *
 * v0.2 deferred (more complex IPC):
 *   - Force LTE-only (TelephonyManager + Subscription)
 *   - App freeze (pm disable per-package)
 *   - Background restriction (appops)
 */
class ShizukuCapabilityProvider(private val context: Context) : CapabilityProvider {

    private val standard = StandardCapabilityProvider(context)

    // Most can() methods just defer to Standard's checks (which only cover Tier 1
    // permissions). For Tier 2 ops we additionally need Shizuku to be granted —
    // the apply() call below short-circuits with skipped messages if not.
    override fun canToggleDnd(): Boolean = standard.canToggleDnd()
    override fun canSetBrightness(): Boolean = standard.canSetBrightness()
    override fun canSetDarkMode(): Boolean = true
    override fun canToggleBatterySaver(): Boolean = true
    override fun canToggleAirplane(): Boolean = true
    override fun canFreezeApps(): Boolean = false  // v0.2
    override fun canForceLte(): Boolean = false    // v0.2

    override suspend fun apply(profile: ThruSparkProfile): AppliedProfile {
        // Apply Tier 1 first — gives us its applied/skipped/failed lists
        val tier1 = standard.apply(profile)
        val applied = tier1.applied.toMutableList()
        val skipped = tier1.skipped.filter { !isShizukuExcuse(it) }.toMutableList()
        val failed = tier1.failed.toMutableList()

        // ── Airplane mode ──
        if (profile.radios.airplane) {
            execAndReport(
                "settings put global airplane_mode_on 1; cmd connectivity airplane-mode enable",
                onSuccess = { applied.add("Airplane mode") },
                onFailure = { msg -> failed.add("Airplane mode: $msg") }
            )
        }

        // ── Refresh rate cap ──
        // Set both peak and min so the system actually settles at the cap.
        runCatching {
            val hz = profile.display.refreshHz.coerceIn(60, 240)
            execAndReport(
                "settings put system peak_refresh_rate $hz.0; settings put system min_refresh_rate $hz.0",
                onSuccess = { applied.add("Refresh rate capped at ${hz}Hz") },
                onFailure = { msg -> failed.add("Refresh rate: $msg") }
            )
        }

        // ── Grayscale (display Daltonizer set to grayscale mode) ──
        if (profile.display.grayscale) {
            execAndReport(
                "settings put secure accessibility_display_daltonizer_enabled 1; " +
                    "settings put secure accessibility_display_daltonizer 0",
                onSuccess = { applied.add("Grayscale") },
                onFailure = { msg -> failed.add("Grayscale: $msg") }
            )
        }

        // ── Radios via svc commands ──
        if (!profile.radios.wifi) {
            execAndReport("svc wifi disable",
                onSuccess = { applied.add("Wi-Fi off") },
                onFailure = { msg -> failed.add("Wi-Fi off: $msg") })
        }
        if (!profile.radios.bluetooth) {
            execAndReport("svc bluetooth disable",
                onSuccess = { applied.add("Bluetooth off") },
                onFailure = { msg -> failed.add("Bluetooth off: $msg") })
        }
        if (!profile.radios.cellular) {
            execAndReport("svc data disable",
                onSuccess = { applied.add("Cellular data off") },
                onFailure = { msg -> failed.add("Cellular off: $msg") })
        }
        if (!profile.radios.nfc) {
            execAndReport("svc nfc disable",
                onSuccess = { applied.add("NFC off") },
                onFailure = { msg -> failed.add("NFC off: $msg") })
        }

        // ── App pausing — v0.3: always-on, no longer gated by freezeNonEssential ──
        // Every active profile pauses every app NOT in essential_apps.explicit_packages
        // (plus the system exemption set). The user opts apps OUT of pausing via the
        // "Specific apps" picker; the freezeNonEssential field is preserved in the
        // schema for backward compatibility but its value is ignored.
        run {
            val report = AppPauser.pauseNonEssential(context, profile)
            if (report.pausedCount > 0) {
                applied.add("Paused ${report.pausedCount} apps (${report.exemptCount} kept active)")
                if (report.failedBatches > 0) {
                    failed.add("${report.failedBatches} pause batches failed — some apps may still run")
                }
            }
        }

        // ── Tier 2 features still deferred to a later version ──
        if (profile.radios.forceLte) {
            skipped.add("Force LTE — coming in a future version (needs Telephony IPC)")
        }

        Log.d(TAG, "Tier 2 apply done — applied=${applied.size} skipped=${skipped.size} failed=${failed.size}")
        return AppliedProfile(profile.name, applied, skipped, failed)
    }

    override suspend fun deactivate() {
        // Restore paused apps FIRST so they can come back online before any UI changes
        runCatching { AppPauser.restoreAll(context) }
            .onFailure { Log.w(TAG, "App restore during deactivate failed", it) }

        // Restore Tier 1
        standard.deactivate()

        // Restore Tier 2 toggles to enabled
        ShizukuManager.exec("settings put global airplane_mode_on 0; cmd connectivity airplane-mode disable")
        ShizukuManager.exec(
            "settings delete system peak_refresh_rate; settings delete system min_refresh_rate"
        )
        ShizukuManager.exec("settings put secure accessibility_display_daltonizer_enabled 0")
        ShizukuManager.exec("svc wifi enable")
        ShizukuManager.exec("svc bluetooth enable")
        ShizukuManager.exec("svc data enable")
        ShizukuManager.exec("svc nfc enable")

        Log.d(TAG, "Tier 2 deactivate complete — apps restored, radios + display restored")
    }

    private suspend fun execAndReport(
        cmd: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        when (val r = ShizukuManager.exec(cmd)) {
            is ShellResult.Success -> if (r.ok) onSuccess() else onFailure("exit ${r.exitCode}: ${r.stderr.trim()}")
            is ShellResult.Error -> onFailure(r.message)
        }
    }

    /** True if a Standard skip message is now obsolete because we cover it here. */
    private fun isShizukuExcuse(msg: String): Boolean =
        msg.contains("Pro", ignoreCase = true) ||
            msg.contains("Shizuku", ignoreCase = true) ||
            msg.contains("v2", ignoreCase = true)
}
