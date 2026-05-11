package ca.thebikemechanic.thruspark.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.userPrefsDataStore by preferencesDataStore(name = "thruspark_user_prefs")

/**
 * App-level user preferences that survive across launches but are NOT part
 * of profile state. Tracks one-time onboarding completion, Shizuku setup,
 * and the last-used profile (drives the GO button's default).
 *
 * Distinct from ProfileStateStore (which tracks the active profile right now)
 * and CustomProfileStore (which stores user-created profile JSON).
 */
object UserPrefsStore {

    private val KEY_WALKTHROUGH_DONE = booleanPreferencesKey("walkthrough_done")
    private val KEY_SHIZUKU_SETUP_DONE = booleanPreferencesKey("shizuku_setup_done")
    private val KEY_LAST_USED_PROFILE = stringPreferencesKey("last_used_profile")
    private val KEY_SIGNED_IN_EMAIL = stringPreferencesKey("signed_in_email")
    private val KEY_AUTH_PHASE_DONE = booleanPreferencesKey("auth_phase_done")
    private val KEY_SEEDED_USER_PROFILES_DONE = booleanPreferencesKey("seeded_user_profiles_done")
    private val KEY_SHIZUKU_REATTENTION_PENDING = booleanPreferencesKey("shizuku_reattention_pending")

    fun walkthroughDoneFlow(context: Context): Flow<Boolean> =
        context.userPrefsDataStore.data.map { it[KEY_WALKTHROUGH_DONE] ?: false }

    fun shizukuSetupDoneFlow(context: Context): Flow<Boolean> =
        context.userPrefsDataStore.data.map { it[KEY_SHIZUKU_SETUP_DONE] ?: false }

    fun lastUsedProfileFlow(context: Context): Flow<String?> =
        context.userPrefsDataStore.data.map { it[KEY_LAST_USED_PROFILE] }

    /**
     * Signed-in email, observable. v0.5+ (audit fix H3): the actual storage
     * is now [SecureUserPrefs] (EncryptedSharedPreferences). This function
     * delegates so callers don't have to change. The legacy DataStore key is
     * left in place but no longer written; [migrateLegacyEmailIfNeeded] copies
     * any prior value into the encrypted store on first app start after upgrade.
     */
    fun signedInEmailFlow(context: Context): Flow<String?> =
        SecureUserPrefs.signedInEmailFlow(context)

    /**
     * One-time copy of the signed-in email from the legacy DataStore key into
     * the new EncryptedSharedPreferences-backed [SecureUserPrefs]. Idempotent —
     * subsequent calls are no-ops once the encrypted store has the email.
     *
     * Called from [ThruSparkApp.onCreate] on a background scope.
     */
    suspend fun migrateLegacyEmailIfNeeded(context: Context) {
        if (SecureUserPrefs.signedInEmail(context) != null) return
        val legacy = context.userPrefsDataStore.data.first()[KEY_SIGNED_IN_EMAIL]
        if (legacy != null) {
            SecureUserPrefs.setSignedInEmail(context, legacy)
            context.userPrefsDataStore.edit { it.remove(KEY_SIGNED_IN_EMAIL) }
        }
    }

    fun authPhaseDoneFlow(context: Context): Flow<Boolean> =
        context.userPrefsDataStore.data.map { it[KEY_AUTH_PHASE_DONE] ?: false }

    suspend fun setAuthPhaseDone(context: Context, done: Boolean = true) {
        context.userPrefsDataStore.edit { it[KEY_AUTH_PHASE_DONE] = done }
    }

    suspend fun seededUserProfilesDone(context: Context): Boolean =
        context.userPrefsDataStore.data.first()[KEY_SEEDED_USER_PROFILES_DONE] ?: false

    suspend fun setSeededUserProfilesDone(context: Context, done: Boolean = true) {
        context.userPrefsDataStore.edit { it[KEY_SEEDED_USER_PROFILES_DONE] = done }
    }

    suspend fun setWalkthroughDone(context: Context, done: Boolean = true) {
        context.userPrefsDataStore.edit { it[KEY_WALKTHROUGH_DONE] = done }
    }

    suspend fun setShizukuSetupDone(context: Context, done: Boolean = true) {
        context.userPrefsDataStore.edit { it[KEY_SHIZUKU_SETUP_DONE] = done }
    }

    fun shizukuReattentionPendingFlow(context: Context): Flow<Boolean> =
        context.userPrefsDataStore.data.map { it[KEY_SHIZUKU_REATTENTION_PENDING] ?: false }

    suspend fun setShizukuReattentionPending(context: Context, pending: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_SHIZUKU_REATTENTION_PENDING] = pending }
    }

    suspend fun setLastUsedProfile(context: Context, profileName: String) {
        context.userPrefsDataStore.edit { it[KEY_LAST_USED_PROFILE] = profileName }
    }

    /**
     * Persist the signed-in email. v0.5+: writes go to [SecureUserPrefs]; the
     * legacy DataStore key is no longer touched on writes (cleared lazily by
     * [migrateLegacyEmailIfNeeded] on first app start after upgrade).
     */
    suspend fun setSignedInEmail(context: Context, email: String?) {
        SecureUserPrefs.setSignedInEmail(context, email)
    }

    /** Clear every key. Used by AppDataReset for both reset levels. */
    suspend fun clearAll(context: Context) {
        context.userPrefsDataStore.edit { it.clear() }
    }
}
