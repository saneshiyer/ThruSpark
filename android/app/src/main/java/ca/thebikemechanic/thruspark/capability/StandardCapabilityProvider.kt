package ca.thebikemechanic.thruspark.capability

import android.app.NotificationManager
import android.app.UiModeManager
import android.content.Context
import android.provider.Settings
import android.util.Log
import ca.thebikemechanic.thruspark.model.ThruSparkProfile

private const val TAG = "StandardCapability"

/**
 * Tier 1 capability provider. Works immediately after granting:
 *   - WRITE_SETTINGS (brightness + timeout)
 *   - Notification Policy access (Do Not Disturb)
 *
 * Dark mode requires no extra permissions.
 * Airplane mode, LTE-only, and app freeze are all Tier 2 — noted as skipped.
 */
class StandardCapabilityProvider(private val context: Context) : CapabilityProvider {

    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    private val uiModeManager: UiModeManager
        get() = context.getSystemService(UiModeManager::class.java)

    override fun canToggleDnd(): Boolean =
        notificationManager.isNotificationPolicyAccessGranted

    override fun canSetBrightness(): Boolean =
        Settings.System.canWrite(context)

    override fun canSetDarkMode(): Boolean = true

    override fun canToggleBatterySaver(): Boolean = true

    // Tier 2 only — always false here
    override fun canToggleAirplane(): Boolean = false
    override fun canFreezeApps(): Boolean = false
    override fun canForceLte(): Boolean = false

    override suspend fun apply(profile: ThruSparkProfile): AppliedProfile {
        val applied = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val failed = mutableListOf<String>()

        // --- Dark mode ---
        runCatching {
            val mode = if (profile.display.darkMode)
                UiModeManager.MODE_NIGHT_YES
            else
                UiModeManager.MODE_NIGHT_NO
            uiModeManager.nightMode = mode
            applied.add("Dark mode")
        }.onFailure {
            Log.e(TAG, "Dark mode failed", it)
            failed.add("Dark mode: ${it.message}")
        }

        // --- Brightness + screen timeout ---
        if (canSetBrightness()) {
            runCatching {
                val resolver = context.contentResolver
                // Switch to manual brightness
                Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                // Set brightness level (0–255)
                val brightnessValue = (profile.display.brightness * 255).toInt().coerceIn(0, 255)
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, brightnessValue)
                // Set screen timeout (ms)
                Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    profile.display.timeoutSec * 1000
                )
                applied.add("Brightness (${(profile.display.brightness * 100).toInt()}%) + screen timeout (${profile.display.timeoutSec}s)")
            }.onFailure {
                Log.e(TAG, "Brightness/timeout failed", it)
                failed.add("Brightness/timeout: ${it.message}")
            }
        } else {
            skipped.add("Brightness + timeout — grant 'Modify System Settings' in onboarding")
        }

        // --- Do Not Disturb ---
        if (profile.notifications.dndEnabled) {
            if (canToggleDnd()) {
                runCatching {
                    notificationManager.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    )
                    applied.add("Do Not Disturb (priority contacts + allowed apps only)")
                }.onFailure {
                    Log.e(TAG, "DND failed", it)
                    failed.add("DND: ${it.message}")
                }
            } else {
                skipped.add("Do Not Disturb — grant notification policy access in onboarding")
            }
        }

        // --- Tier 2 features — always skipped in Tier 1 ---
        if (profile.radios.airplane) {
            skipped.add("Airplane mode — requires ThruSpark Pro (Shizuku, coming in v2)")
        }
        if (profile.radios.forceLte) {
            skipped.add("Force LTE-only — requires ThruSpark Pro (v2)")
        }
        if (!profile.radios.wifi) {
            skipped.add("Wi-Fi off — requires ThruSpark Pro (v2); disable manually for now")
        }
        if (!profile.radios.bluetooth) {
            skipped.add("Bluetooth off — requires ThruSpark Pro (v2); disable manually for now")
        }
        if (profile.background.freezeNonEssential) {
            skipped.add("App freeze — requires ThruSpark Pro (v2)")
        }

        Log.d(TAG, "Applied: $applied | Skipped: $skipped | Failed: $failed")
        return AppliedProfile(profile.name, applied, skipped, failed)
    }

    override suspend fun deactivate() {
        // Restore DND to all-allowed
        runCatching {
            if (canToggleDnd()) {
                notificationManager.setInterruptionFilter(
                    NotificationManager.INTERRUPTION_FILTER_ALL
                )
            }
        }

        // Restore brightness to automatic
        runCatching {
            if (canSetBrightness()) {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                )
                // Restore a sensible default timeout (2 minutes)
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    120_000
                )
            }
        }

        // Restore dark mode to "auto" — UiModeManager has no MODE_NIGHT_FOLLOW_SYSTEM
        // (that's an AppCompatDelegate constant). MODE_NIGHT_AUTO is the platform
        // equivalent and lets the system decide.
        runCatching {
            uiModeManager.nightMode = UiModeManager.MODE_NIGHT_AUTO
        }

        Log.d(TAG, "Profile deactivated — system settings restored")
    }
}
