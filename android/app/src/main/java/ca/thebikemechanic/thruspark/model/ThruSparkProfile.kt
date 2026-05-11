package ca.thebikemechanic.thruspark.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThruSparkProfile(
    val name: String,
    val version: String,
    val display: DisplaySettings,
    val radios: RadioSettings,
    val notifications: NotificationSettings,
    val background: BackgroundSettings,
    @SerialName("essential_apps") val essentialApps: EssentialApps,
    val session: SessionSettings,
    val alarm: AlarmSettings = AlarmSettings()   // optional built-in alarm; defaults to disabled
)

@Serializable
data class DisplaySettings(
    val brightness: Float,
    @SerialName("dark_mode") val darkMode: Boolean,
    val grayscale: Boolean,
    @SerialName("refresh_hz") val refreshHz: Int,
    @SerialName("timeout_sec") val timeoutSec: Int
)

@Serializable
data class RadioSettings(
    val airplane: Boolean,
    val wifi: Boolean,
    val cellular: Boolean,
    @SerialName("force_lte") val forceLte: Boolean,
    val gps: Boolean,
    val bluetooth: Boolean,
    val nfc: Boolean
)

@Serializable
data class NotificationSettings(
    @SerialName("dnd_enabled") val dndEnabled: Boolean,
    @SerialName("allowlist_contacts") val allowlistContacts: List<String>,
    @SerialName("allowlist_apps") val allowlistApps: List<String>
)

@Serializable
data class BackgroundSettings(
    @SerialName("restrict_all_except") val restrictAllExcept: List<String>,
    @SerialName("freeze_non_essential") val freezeNonEssential: Boolean
)

@Serializable
data class EssentialApps(
    val categories: List<String>,
    @SerialName("explicit_packages") val explicitPackages: List<String>
)

@Serializable
data class SessionSettings(
    @SerialName("duration_hours") val durationHours: Int? = null,
    @SerialName("auto_deactivate_at") val autoDeactivateAt: String? = null
)
