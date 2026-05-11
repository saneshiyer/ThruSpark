package ca.thebikemechanic.thruspark.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.restrictionsDataStore by preferencesDataStore(name = "thruspark_restrictions")

/**
 * Tracks which packages we have RUN_IN_BACKGROUND-restricted via Shizuku, so
 * deactivation can restore them. Stored as a comma-separated string for
 * compactness — a profile that pauses 80 apps fits in well under DataStore's
 * single-value size limits.
 *
 * Survives reboots so BootReceiver can decide whether to re-restrict (if a
 * profile was active when the device went down) or restore (if state diverged).
 */
object AppRestrictionsStore {

    private val KEY = stringPreferencesKey("restricted_packages_csv")

    suspend fun getRestricted(context: Context): Set<String> {
        val csv = context.restrictionsDataStore.data.first()[KEY] ?: return emptySet()
        return csv.split(",").filter { it.isNotBlank() }.toSet()
    }

    suspend fun setRestricted(context: Context, packages: Set<String>) {
        context.restrictionsDataStore.edit { it[KEY] = packages.joinToString(",") }
    }

    suspend fun clear(context: Context) {
        context.restrictionsDataStore.edit { it.remove(KEY) }
    }

    suspend fun clearAll(context: Context) = clear(context)
}
