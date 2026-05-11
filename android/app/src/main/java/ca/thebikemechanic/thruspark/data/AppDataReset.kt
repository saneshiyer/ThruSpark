package ca.thebikemechanic.thruspark.data

import android.content.Context
import android.util.Log
import ca.thebikemechanic.thruspark.engine.ProfileEngine
import java.io.File

private const val TAG = "AppDataReset"
private const val PREFS_NAME = "thruspark_prefs"
private const val CUSTOM_PROFILE_DIR = "custom_profiles"

/**
 * Two reset levels offered from Settings:
 *
 * 1. restartOnboarding() — clears the *_done flags + signed-in email so the
 *    user re-walks first-launch (Welcome → Walkthrough → Permissions → Shizuku).
 *    Custom profiles, alarm, and active state are preserved.
 *
 * 2. clearAll() — full nuclear: deactivates active profile, wipes every
 *    DataStore, deletes every custom profile JSON, clears the SharedPrefs.
 *    Used when the user wants a true "fresh install" without uninstalling.
 */
object AppDataReset {

    suspend fun restartOnboarding(context: Context) {
        Log.d(TAG, "Restart onboarding — clearing flow flags and signed-in email")
        runCatching { UserPrefsStore.clearAll(context) }
            .onFailure { Log.w(TAG, "UserPrefs clear failed", it) }
        runCatching { SecureUserPrefs.clearAll(context) }
            .onFailure { Log.w(TAG, "SecureUserPrefs clear failed", it) }
        clearOnboardingPrefs(context)
    }

    suspend fun clearAll(context: Context) {
        Log.d(TAG, "Clear all — full reset")

        // 1. Deactivate any running profile so radios/brightness/DND restore
        runCatching { ProfileEngine.deactivateSuspend(context) }
            .onFailure { Log.w(TAG, "Profile deactivate during reset failed", it) }

        // 2. Clear every DataStore + the EncryptedSharedPreferences-backed secure store
        runCatching { UserPrefsStore.clearAll(context) }
        runCatching { SecureUserPrefs.clearAll(context) }
        runCatching { AlarmEntryStore.clearAll(context) }
        runCatching { ProfileStateStore.clearAll(context) }

        // 3. Delete every custom profile JSON
        runCatching {
            val dir = File(context.filesDir, CUSTOM_PROFILE_DIR)
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
        }

        // 4. Clear the legacy SharedPreferences (onboarding_complete flag)
        clearOnboardingPrefs(context)
    }

    private fun clearOnboardingPrefs(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
