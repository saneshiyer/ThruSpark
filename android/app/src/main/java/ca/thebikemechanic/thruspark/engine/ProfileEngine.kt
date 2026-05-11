package ca.thebikemechanic.thruspark.engine

import android.content.Context
import android.util.Log
import ca.thebikemechanic.thruspark.capability.AppliedProfile
import ca.thebikemechanic.thruspark.capability.CapabilityProviderResolver
import ca.thebikemechanic.thruspark.data.ProfileStateStore
import ca.thebikemechanic.thruspark.model.ProfileRepository
import ca.thebikemechanic.thruspark.model.ThruSparkProfile
import ca.thebikemechanic.thruspark.alarm.AlarmScheduler
import ca.thebikemechanic.thruspark.data.UserPrefsStore
import ca.thebikemechanic.thruspark.session.ProfileLifecycleService
import ca.thebikemechanic.thruspark.session.SessionScheduler
import ca.thebikemechanic.thruspark.util.AppCategories
import ca.thebikemechanic.thruspark.util.NotificationFilterState
import ca.thebikemechanic.thruspark.util.SystemExemptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "ProfileEngine"

/**
 * Singleton entry point for activating and deactivating profiles.
 * Used by the Quick Settings tile, ViewModel, and any future surfaces.
 */
object ProfileEngine {

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Activate "the default" profile. Resolution order:
     *   1. Last-used profile (UserPrefsStore.lastUsedProfile) — if it still exists
     *   2. First custom profile in CustomProfileStore
     *   3. First bundled preset (Minimum Power)
     *
     * This is the entry point for the Quick Settings tile and any other
     * non-targeted "just turn it on" trigger. Matches HomeScreen's GO button
     * resolution so a tile-tap and a GO-tap pick the same profile.
     */
    fun activateDefault(context: Context) {
        scope.launch {
            val repo = ProfileRepository(context)
            val lastUsed = UserPrefsStore.lastUsedProfileFlow(context).first()
            val target = lastUsed?.let { repo.loadPreset(it) }
                ?: repo.loadCustomProfiles().firstOrNull()
                ?: repo.loadPresets().firstOrNull()
                ?: run {
                    Log.e(TAG, "No profiles available — cannot activate")
                    return@launch
                }
            Log.d(TAG, "activateDefault → '${target.name}' (lastUsed=$lastUsed)")
            activateProfile(context, target.name)
        }
    }

    fun activateProfile(context: Context, profileName: String) {
        scope.launch {
            val repo = ProfileRepository(context)
            val profile = repo.loadPreset(profileName) ?: run {
                Log.e(TAG, "Profile not found: $profileName")
                return@launch
            }

            val provider = CapabilityProviderResolver.pick(context)
            val result = provider.apply(profile)

            ProfileStateStore.setActive(context, profile.name)
            Log.d(TAG, "Activated '$profileName' — applied: ${result.applied.size}, skipped: ${result.skipped.size}")

            // v0.2.2: foreground service holds the persistent notification + auto-deactivates on swipe
            ProfileLifecycleService.start(context, profile.name)

            // v0.2: prime the notification filter
            if (profile.notifications.dndEnabled) {
                NotificationFilterState.setAllowlist(buildNotificationAllowlist(context, profile))
            }

            // Schedule session end if this profile has a duration
            profile.session.durationHours?.let { hours ->
                SessionScheduler.scheduleEnd(context, hours)
            }

            // Schedule built-in alarm if the profile has one enabled
            profile.alarm.takeIf { it.enabled && it.time != null }?.let { alarm ->
                val scheduled = AlarmScheduler.schedule(context, alarm.time!!, alarm.label)
                Log.d(TAG, if (scheduled) "Alarm scheduled for ${alarm.time}" else "Alarm permission missing — skipped")
            }
        }
    }

    fun deactivate(context: Context) {
        scope.launch {
            val provider = CapabilityProviderResolver.pick(context)
            provider.deactivate()
            ProfileStateStore.setInactive(context)
            NotificationFilterState.clear()
            SessionScheduler.cancelEnd(context)
            AlarmScheduler.cancel(context)
            ProfileLifecycleService.stop(context)
            Log.d(TAG, "Profile deactivated")
        }
    }

    suspend fun activateProfileSuspend(context: Context, profileName: String): AppliedProfile? {
        val repo = ProfileRepository(context)
        val profile = repo.loadPreset(profileName) ?: return null
        val provider = CapabilityProviderResolver.pick(context)
        val result = provider.apply(profile)
        ProfileStateStore.setActive(context, profile.name)

        // v0.2.2: lifecycle service for persistent notification + swipe-away deactivate
        ProfileLifecycleService.start(context, profile.name)

        // v0.2: prime the notification filter so the listener knows what to allow
        if (profile.notifications.dndEnabled) {
            NotificationFilterState.setAllowlist(buildNotificationAllowlist(context, profile))
        }

        profile.session.durationHours?.let { hours ->
            SessionScheduler.scheduleEnd(context, hours)
        }
        profile.alarm.takeIf { it.enabled && it.time != null }?.let { alarm ->
            AlarmScheduler.schedule(context, alarm.time!!, alarm.label)
        }
        return result
    }

    suspend fun deactivateSuspend(context: Context) {
        val provider = CapabilityProviderResolver.pick(context)
        provider.deactivate()
        ProfileStateStore.setInactive(context)
        NotificationFilterState.clear()
        SessionScheduler.cancelEnd(context)
        AlarmScheduler.cancel(context)
        ProfileLifecycleService.stop(context)
    }

    /**
     * Build the set of packages allowed to post notifications while [profile] is active.
     * Includes per-profile explicit picks, category-matched apps, and ALL system
     * exemptions (so launcher / dialer / IME stay vocal).
     */
    private fun buildNotificationAllowlist(context: Context, profile: ThruSparkProfile): Set<String> =
        buildSet {
            addAll(SystemExemptions.resolveAll(context))
            addAll(profile.essentialApps.explicitPackages)
            addAll(AppCategories.installedPackages(context, profile.essentialApps.categories))
        }
}
