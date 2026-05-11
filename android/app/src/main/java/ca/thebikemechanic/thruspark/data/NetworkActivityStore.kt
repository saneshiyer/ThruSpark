package ca.thebikemechanic.thruspark.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.networkActivityDataStore by preferencesDataStore(name = "thruspark_network_log")
private const val MAX_ENTRIES = 50

/**
 * Per-request log of every network call ThruSpark makes. Wired in via
 * NetworkActivityLogger (an OkHttp Interceptor). Cap at the last 50 entries
 * so the store stays bounded.
 *
 * v0.5 trust-building feature: an empty list is the most credible "we don't
 * phone home" proof we can offer. If we ever regress and start phoning home,
 * the user will see it here.
 */
@Serializable
data class NetworkActivityEntry(
    val timestampMs: Long,
    val method: String,
    val url: String,
    val statusCode: Int,
    val durationMs: Long
)

object NetworkActivityStore {

    private val KEY_LOG_JSON = stringPreferencesKey("activity_log_json")
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(NetworkActivityEntry.serializer())

    fun activityFlow(context: Context): Flow<List<NetworkActivityEntry>> =
        context.networkActivityDataStore.data.map { prefs ->
            prefs[KEY_LOG_JSON]?.let { decode(it) } ?: emptyList()
        }

    suspend fun append(context: Context, entry: NetworkActivityEntry) {
        context.networkActivityDataStore.edit { prefs ->
            val current = prefs[KEY_LOG_JSON]?.let { decode(it) } ?: emptyList()
            // Newest first, cap at MAX_ENTRIES
            val next = (listOf(entry) + current).take(MAX_ENTRIES)
            prefs[KEY_LOG_JSON] = json.encodeToString(listSerializer, next)
        }
    }

    suspend fun clear(context: Context) {
        context.networkActivityDataStore.edit { it.remove(KEY_LOG_JSON) }
    }

    suspend fun clearAll(context: Context) = clear(context)

    private fun decode(raw: String): List<NetworkActivityEntry> =
        runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
}
