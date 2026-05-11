package ca.thebikemechanic.thruspark.capability

import android.content.Context
import ca.thebikemechanic.thruspark.shizuku.ShizukuManager
import ca.thebikemechanic.thruspark.shizuku.ShizukuState

/**
 * Picks the right CapabilityProvider for the current device + permissions.
 *
 *   - Shizuku granted → ShizukuCapabilityProvider (Tier 2 — full feature set)
 *   - Otherwise       → StandardCapabilityProvider (Tier 1 — brightness, DND,
 *                       dark mode, screen timeout)
 *
 * The decision happens per-call so a user who grants Shizuku mid-session gets
 * the upgraded provider on their next profile activation without restart.
 */
object CapabilityProviderResolver {
    fun pick(context: Context): CapabilityProvider {
        return if (ShizukuManager.state.value == ShizukuState.Granted) {
            ShizukuCapabilityProvider(context)
        } else {
            StandardCapabilityProvider(context)
        }
    }
}
