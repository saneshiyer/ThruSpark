package ca.thebikemechanic.thruspark.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.thruSparkDataStore by preferencesDataStore(name = "thruspark_state")

/**
 * Active-profile state — survives reboots, observable as Flows for the UI,
 * and readable synchronously from TileService where coroutines aren't practical.
 */
object ProfileStateStore {

    private val KEY_IS_ACTIVE = booleanPreferencesKey("is_active")
    private val KEY_PROFILE_NAME = stringPreferencesKey("active_profile_name")
    private val KEY_ACTIVATED_AT_MS = stringPreferencesKey("activated_at_ms")

    // --- Flows (use in ViewModel/Compose) ---

    fun isActiveFlow(context: Context): Flow<Boolean> =
        context.thruSparkDataStore.data.map { prefs ->
            prefs[KEY_IS_ACTIVE] ?: false
        }

    fun activeProfileNameFlow(context: Context): Flow<String?> =
        context.thruSparkDataStore.data.map { prefs ->
            prefs[KEY_PROFILE_NAME]
        }

    fun activatedAtMsFlow(context: Context): Flow<Long?> =
        context.thruSparkDataStore.data.map { prefs ->
            prefs[KEY_ACTIVATED_AT_MS]?.toLongOrNull()
        }

    // --- Synchronous read (only use in TileService where coroutines aren't practical) ---

    fun isActive(context: Context): Boolean = runBlocking {
        context.thruSparkDataStore.data.first()[KEY_IS_ACTIVE] ?: false
    }

    // --- Suspend writers (use in ProfileEngine / ViewModel) ---

    suspend fun setActive(context: Context, profileName: String) {
        context.thruSparkDataStore.edit { prefs ->
            prefs[KEY_IS_ACTIVE] = true
            prefs[KEY_PROFILE_NAME] = profileName
            prefs[KEY_ACTIVATED_AT_MS] = System.currentTimeMillis().toString()
        }
    }

    suspend fun setInactive(context: Context) {
        context.thruSparkDataStore.edit { prefs ->
            prefs[KEY_IS_ACTIVE] = false
            prefs.remove(KEY_PROFILE_NAME)
            prefs.remove(KEY_ACTIVATED_AT_MS)
        }
    }

    suspend fun getActivatedAtMs(context: Context): Long? =
        context.thruSparkDataStore.data.first()[KEY_ACTIVATED_AT_MS]?.toLongOrNull()

    /** Clear every key. Used by AppDataReset for full-reset. */
    suspend fun clearAll(context: Context) {
        context.thruSparkDataStore.edit { it.clear() }
    }
}
