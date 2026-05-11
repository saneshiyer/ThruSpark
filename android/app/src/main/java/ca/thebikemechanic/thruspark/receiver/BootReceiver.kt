package ca.thebikemechanic.thruspark.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import ca.thebikemechanic.thruspark.data.ProfileStateStore
import ca.thebikemechanic.thruspark.data.UserPrefsStore
import ca.thebikemechanic.thruspark.engine.ProfileEngine
import ca.thebikemechanic.thruspark.model.ProfileRepository
import ca.thebikemechanic.thruspark.shizuku.ShizukuManager
import ca.thebikemechanic.thruspark.shizuku.ShizukuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "BootReceiver"

/**
 * Restores an active profile after the device reboots.
 *
 * v0.3: defensively checks that the previously-active profile still exists.
 * If the user upgraded across the v0.3 preset rename and was previously running
 * one of the old presets (Bikepacking / Long Flight / etc.), the profile lookup
 * fails — we clear active state and skip restoration rather than logging a hard
 * error or leaving things in an inconsistent state.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Boot completed — checking for active profile")

        CoroutineScope(Dispatchers.IO).launch {
            autoTestShizuku(context)

            val isActive = ProfileStateStore.isActiveFlow(context).first()
            val profileName = ProfileStateStore.activeProfileNameFlow(context).first()

            if (!isActive || profileName == null) {
                Log.d(TAG, "No active profile to restore")
                return@launch
            }

            // Verify the profile still exists (handles v0.3 preset rename / user-deleted profile)
            val exists = ProfileRepository(context).loadPreset(profileName) != null
            if (!exists) {
                Log.w(TAG, "Profile '$profileName' no longer exists — clearing active state")
                ProfileStateStore.setInactive(context)
                return@launch
            }

            Log.d(TAG, "Restoring active profile: $profileName")
            ProfileEngine.activateProfile(context, profileName)
        }
    }

    /**
     * If the user previously completed Shizuku setup, the binder won't survive
     * a reboot (Shizuku's adb-started service has to be restarted manually).
     * Recompute state and, if it's no longer Granted, raise a flag so the next
     * MainActivity launch can prompt the user to re-enable it. The active
     * profile still restores; the capability resolver silently falls back to
     * Tier 1 until Shizuku is back.
     */
    private suspend fun autoTestShizuku(context: Context) {
        val previouslySetUp = UserPrefsStore.shizukuSetupDoneFlow(context).first()
        if (!previouslySetUp) return

        ShizukuManager.init(context)
        val state = ShizukuManager.state.value
        if (state != ShizukuState.Granted) {
            Log.d(TAG, "Shizuku autotest after boot: state=$state — flagging for re-prompt")
            UserPrefsStore.setShizukuReattentionPending(context, true)
        }
    }
}
