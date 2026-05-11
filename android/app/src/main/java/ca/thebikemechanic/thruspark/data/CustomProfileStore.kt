package ca.thebikemechanic.thruspark.data

import android.content.Context
import android.util.Log
import ca.thebikemechanic.thruspark.model.ThruSparkProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private const val TAG = "CustomProfileStore"
private const val DIR_NAME = "custom_profiles"

/**
 * Stores user-created profiles as individual JSON files in app-internal storage.
 *
 * Layout: {appDir}/files/custom_profiles/{slug}.json
 *
 * Slug is derived from the profile name (lowercased, non-alphanumerics → _).
 * If two profiles share a slug, the second overwrites the first — the UI is
 * responsible for preventing duplicate names.
 */
object CustomProfileStore {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun list(context: Context): List<ThruSparkProfile> {
        val dir = profileDir(context)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.extension == "json" }
            ?.sortedBy { it.lastModified() }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString<ThruSparkProfile>(file.readText()) }
                    .onFailure { Log.e(TAG, "Failed to parse ${file.name}: ${it.message}") }
                    .getOrNull()
            } ?: emptyList()
    }

    fun load(context: Context, name: String): ThruSparkProfile? {
        val file = File(profileDir(context), slug(name) + ".json")
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<ThruSparkProfile>(file.readText()) }
            .onFailure { Log.e(TAG, "Failed to load $name: ${it.message}") }
            .getOrNull()
    }

    fun save(context: Context, profile: ThruSparkProfile) {
        val dir = profileDir(context).also { it.mkdirs() }
        val file = File(dir, slug(profile.name) + ".json")
        file.writeText(json.encodeToString(profile))
        Log.d(TAG, "Saved custom profile '${profile.name}' to ${file.name}")
    }

    fun delete(context: Context, name: String): Boolean {
        val file = File(profileDir(context), slug(name) + ".json")
        val deleted = file.exists() && file.delete()
        if (deleted) Log.d(TAG, "Deleted custom profile '$name'")
        return deleted
    }

    fun nameExists(context: Context, name: String): Boolean =
        File(profileDir(context), slug(name) + ".json").exists()

    private fun profileDir(context: Context): File =
        File(context.filesDir, DIR_NAME)

    private fun slug(name: String): String =
        name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "unnamed" }
}
