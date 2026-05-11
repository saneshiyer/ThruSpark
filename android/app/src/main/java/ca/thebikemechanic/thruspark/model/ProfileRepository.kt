package ca.thebikemechanic.thruspark.model

import android.content.Context
import ca.thebikemechanic.thruspark.data.CustomProfileStore
import kotlinx.serialization.json.Json

private const val TAG = "ProfileRepository"

/**
 * Single read entry point for built-in template (Minimum Power) and user-created
 * custom profiles (stored via CustomProfileStore).
 *
 * v0.3 changes:
 *  - Only one bundled preset: Minimum Power (the read-only template/floor)
 *  - Three seeded user profiles ship pre-installed (City Life, Sport, Expedition)
 *    via [seedUserProfilesIfNeeded]; once seeded they behave like any custom profile
 *  - Resolution order: preset first, then customs (preset can't be shadowed by name)
 */
class ProfileRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = true
    }

    private val presetFiles = listOf(
        "profiles/minimum_power.json"
    )

    private val seedFiles = listOf(
        "profiles/seeds/city_life.json",
        "profiles/seeds/sport.json",
        "profiles/seeds/expedition.json"
    )

    fun loadPresets(): List<ThruSparkProfile> =
        presetFiles.mapNotNull { loadFromAssets(it) }

    fun loadCustomProfiles(): List<ThruSparkProfile> =
        CustomProfileStore.list(context)

    fun loadAll(): List<ThruSparkProfile> =
        loadPresets() + loadCustomProfiles()

    fun loadPreset(name: String): ThruSparkProfile? =
        loadAll().firstOrNull { it.name == name }

    fun saveCustom(profile: ThruSparkProfile) {
        CustomProfileStore.save(context, profile)
    }

    fun deleteCustom(name: String): Boolean =
        CustomProfileStore.delete(context, name)

    fun customNameTaken(name: String): Boolean =
        CustomProfileStore.nameExists(context, name) ||
            loadPresets().any { it.name.equals(name, ignoreCase = true) }

    /**
     * Copy the bundled seed profiles (City Life / Sport / Expedition) into
     * the user's custom profile store. Idempotent at the per-name level —
     * skips any that already exist (so re-running won't overwrite user edits).
     *
     * Caller is responsible for the "seeded once" flag in UserPrefsStore so
     * we don't re-seed deleted profiles.
     */
    fun seedUserProfiles() {
        seedFiles.forEach { path ->
            val seed = loadFromAssets(path) ?: return@forEach
            if (CustomProfileStore.nameExists(context, seed.name)) {
                android.util.Log.d(TAG, "Skipping seed '${seed.name}' — already in store")
                return@forEach
            }
            CustomProfileStore.save(context, seed)
            android.util.Log.d(TAG, "Seeded '${seed.name}'")
        }
    }

    /**
     * Duplicate any profile (preset, seeded, or custom) into the user's store.
     * Generates a unique name like "Name copy" or "Name copy 2" on collision.
     * Returns the new profile name so the caller can navigate to it for editing.
     */
    fun duplicate(source: ThruSparkProfile): String {
        val newName = uniqueDuplicateName(source.name)
        val copy = source.copy(name = newName)
        CustomProfileStore.save(context, copy)
        return newName
    }

    private fun uniqueDuplicateName(originalName: String): String {
        val base = "$originalName copy"
        if (!customNameTaken(base)) return base
        var n = 2
        while (customNameTaken("$base $n")) n++
        return "$base $n"
    }

    private fun loadFromAssets(path: String): ThruSparkProfile? = runCatching {
        val text = context.assets.open(path).bufferedReader().readText()
        json.decodeFromString<ThruSparkProfile>(text)
    }.getOrElse { e ->
        android.util.Log.e(TAG, "Failed to load $path: ${e.message}")
        null
    }
}
