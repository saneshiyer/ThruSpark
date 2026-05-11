package ca.thebikemechanic.thruspark.capability

import android.content.Context
import android.util.Log
import ca.thebikemechanic.thruspark.data.AppRestrictionsStore
import ca.thebikemechanic.thruspark.data.InstalledAppsRepository
import ca.thebikemechanic.thruspark.model.ThruSparkProfile
import ca.thebikemechanic.thruspark.shizuku.ShellResult
import ca.thebikemechanic.thruspark.shizuku.ShizukuManager
import ca.thebikemechanic.thruspark.util.AppCategories
import ca.thebikemechanic.thruspark.util.SystemExemptions

private const val TAG = "AppPauser"
private const val BATCH_SIZE = 25   // ~50 commands per Shizuku exec call

/**
 * The v0.2 killer feature. When a profile activates:
 *  - Compute the "to pause" set: all launchable apps − exemptions
 *  - For each: am force-stop + cmd appops set ... RUN_IN_BACKGROUND deny
 *  - Save the list so deactivation can restore
 *
 * On deactivate: read saved list, reset RUN_IN_BACKGROUND to default for each.
 *
 * Performance note: Shizuku.exec() is expensive (~50ms per call). For 80 apps
 * that's 80 × 2 commands = 160 calls = 8 seconds without batching. We chunk
 * commands into single shell invocations using ' ; ' separators, getting it
 * down to ~6 calls / ~300ms total.
 */
object AppPauser {

    data class PauseReport(
        val pausedCount: Int,
        val exemptCount: Int,
        val failedBatches: Int
    ) {
        val ok: Boolean get() = failedBatches == 0
    }

    suspend fun pauseNonEssential(context: Context, profile: ThruSparkProfile): PauseReport {
        // 1. All launchable user apps
        val all = InstalledAppsRepository.loadLaunchable(context)
            .map { it.packageName }
            .toSet()

        // 2. Exemption set: system-exempt (incl. device admins via Shizuku) +
        //    per-profile picker + category-matched
        val exempt = buildSet {
            addAll(SystemExemptions.resolveAllWithDeviceAdmins(context))
            addAll(profile.essentialApps.explicitPackages)
            addAll(AppCategories.installedPackages(context, profile.essentialApps.categories))
        }

        val toPause = all - exempt
        Log.d(TAG, "all=${all.size} exempt=${exempt.size} toPause=${toPause.size}")

        if (toPause.isEmpty()) {
            AppRestrictionsStore.clear(context)
            return PauseReport(0, exempt.size, 0)
        }

        // 3. Build batched commands. For each package, three actions:
        //    a. am force-stop      — kill any running processes (foreground + background)
        //    b. cmd appops set ... RUN_IN_BACKGROUND deny — block future background work
        //    c. cmd package suspend — full OS-level pause; if the user taps the icon
        //       Android shows "This app is paused" instead of letting them in. This is
        //       the same mechanism behind Digital Wellbeing's "App Timer" expiry.
        // Custom dialog message overrides the default "Suspended by Shell" text.
        // Single-quoted so spaces survive the sh -c wrapper that ShizukuManager.exec uses.
        val dialogMsg = "'Paused by ThruSpark. Open ThruSpark to deactivate the active mode.'"

        val packages = toPause.toList()
        val chunks = packages.chunked(BATCH_SIZE)
        var failed = 0
        for (chunk in chunks) {
            val cmd = chunk.joinToString(" ; ") { pkg ->
                "am force-stop $pkg ; " +
                    "cmd appops set $pkg RUN_IN_BACKGROUND deny ; " +
                    "cmd package suspend --dialogMessage $dialogMsg $pkg"
            }
            val r = ShizukuManager.exec(cmd)
            if (r !is ShellResult.Success || !r.ok) {
                failed++
                Log.w(TAG, "Batch failed (${chunk.size} pkgs): $r")
            }
        }

        // 4. Save the list for restoreAll() — even partial failures save what we tried
        AppRestrictionsStore.setRestricted(context, toPause)

        return PauseReport(toPause.size, exempt.size, failed)
    }

    /**
     * Restore RUN_IN_BACKGROUND to default for every package we restricted.
     * Idempotent — safe to call repeatedly. Doesn't restart any apps; the
     * user can launch them manually if they want them running.
     */
    suspend fun restoreAll(context: Context): Int {
        val restricted = AppRestrictionsStore.getRestricted(context)
        if (restricted.isEmpty()) return 0

        val chunks = restricted.toList().chunked(BATCH_SIZE)
        for (chunk in chunks) {
            // Mirror of pause: unsuspend + reset background appop. Order matters —
            // unsuspend first so the appop change can take effect immediately.
            val cmd = chunk.joinToString(" ; ") { pkg ->
                "cmd package unsuspend $pkg ; cmd appops set $pkg RUN_IN_BACKGROUND default"
            }
            val r = ShizukuManager.exec(cmd)
            if (r !is ShellResult.Success || !r.ok) {
                Log.w(TAG, "Restore batch failed (${chunk.size} pkgs): $r")
            }
        }

        AppRestrictionsStore.clear(context)
        Log.d(TAG, "Restored ${restricted.size} packages")
        return restricted.size
    }

    /**
     * Emergency unpause invoked from Settings. Same as restoreAll() but always
     * runs, even if AppRestrictionsStore is somehow empty (best-effort recovery
     * if state was lost — won't help if we don't know which packages, but
     * documented for the user).
     */
    suspend fun emergencyRestore(context: Context): Int = restoreAll(context)
}
