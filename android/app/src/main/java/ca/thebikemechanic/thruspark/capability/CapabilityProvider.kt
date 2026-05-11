package ca.thebikemechanic.thruspark.capability

import ca.thebikemechanic.thruspark.model.ThruSparkProfile

/**
 * Describes what the current tier can do and applies a profile.
 * Tier 1 = StandardCapabilityProvider (no extra setup needed).
 * Tier 2 = ShizukuCapabilityProvider (v2, not yet implemented).
 */
interface CapabilityProvider {
    fun canToggleDnd(): Boolean
    fun canSetBrightness(): Boolean
    fun canSetDarkMode(): Boolean
    fun canToggleBatterySaver(): Boolean
    fun canToggleAirplane(): Boolean
    fun canFreezeApps(): Boolean
    fun canForceLte(): Boolean

    suspend fun apply(profile: ThruSparkProfile): AppliedProfile
    suspend fun deactivate()
}

/**
 * Result returned after applying a profile.
 * Surfaces to the user so they know exactly what worked and what didn't.
 */
data class AppliedProfile(
    val profileName: String,
    val applied: List<String>,   // features that were successfully enabled
    val skipped: List<String>,   // features skipped (permission not granted, or Tier 2 required)
    val failed: List<String>     // features that tried but encountered an error
) {
    val hasSkips: Boolean get() = skipped.isNotEmpty()
    val hasFails: Boolean get() = failed.isNotEmpty()
    val isFullyApplied: Boolean get() = skipped.isEmpty() && failed.isEmpty()
}
