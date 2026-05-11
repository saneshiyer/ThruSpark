package ca.thebikemechanic.thruspark.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ca.thebikemechanic.thruspark.model.AlarmEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.alarmDataStore by preferencesDataStore(name = "thruspark_alarm")

/**
 * v0.2: stores a LIST of AlarmEntry (was single entry in v0.1).
 *
 * On first read after upgrade, if the legacy single-entry key is present,
 * decode it and migrate to the new list-shaped key. The legacy key is then
 * cleared so we don't double-store.
 */
object AlarmEntryStore {

    private val KEY_ALARM_LIST = stringPreferencesKey("alarm_entries_json")
    private val KEY_LEGACY_SINGLE = stringPreferencesKey("alarm_entry_json")  // v0.1 key
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(AlarmEntry.serializer())

    /** All alarms, sorted by time. */
    fun alarmsFlow(context: Context): Flow<List<AlarmEntry>> =
        context.alarmDataStore.data.map { prefs ->
            prefs[KEY_ALARM_LIST]?.let { decodeList(it) } ?: emptyList()
        }

    suspend fun getAll(context: Context): List<AlarmEntry> {
        migrateLegacyIfNeeded(context)
        val raw = context.alarmDataStore.data.first()[KEY_ALARM_LIST] ?: return emptyList()
        return decodeList(raw)
    }

    suspend fun upsert(context: Context, entry: AlarmEntry) {
        migrateLegacyIfNeeded(context)
        val current = getAll(context).toMutableList()
        val idx = current.indexOfFirst { it.id == entry.id }
        if (idx >= 0) current[idx] = entry else current.add(entry)
        writeList(context, current)
    }

    suspend fun delete(context: Context, id: String) {
        val current = getAll(context).filterNot { it.id == id }
        writeList(context, current)
    }

    suspend fun clearAll(context: Context) {
        context.alarmDataStore.edit {
            it.remove(KEY_ALARM_LIST)
            it.remove(KEY_LEGACY_SINGLE)
        }
    }

    /** Migrate from v0.1's single-entry storage if present and the new key is absent. */
    private suspend fun migrateLegacyIfNeeded(context: Context) {
        val prefs = context.alarmDataStore.data.first()
        if (prefs[KEY_ALARM_LIST] != null) return  // already migrated or new install
        val legacy = prefs[KEY_LEGACY_SINGLE] ?: return
        val migrated = runCatching { json.decodeFromString<AlarmEntry>(legacy) }.getOrNull()
        context.alarmDataStore.edit {
            if (migrated != null) it[KEY_ALARM_LIST] = json.encodeToString(listSerializer, listOf(migrated))
            it.remove(KEY_LEGACY_SINGLE)
        }
    }

    private suspend fun writeList(context: Context, list: List<AlarmEntry>) {
        context.alarmDataStore.edit {
            it[KEY_ALARM_LIST] = json.encodeToString(listSerializer, list.sortedBy { e -> e.time })
        }
    }

    private fun decodeList(raw: String): List<AlarmEntry> =
        runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
}
