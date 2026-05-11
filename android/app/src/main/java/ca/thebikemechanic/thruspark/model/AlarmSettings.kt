package ca.thebikemechanic.thruspark.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Alarm configuration embedded in a profile.
 * When enabled, ProfileEngine will schedule a built-in alarm via AlarmScheduler
 * instead of relying on a third-party clock app.
 *
 * time format: "HH:mm" in 24-hour notation (e.g. "07:00")
 */
@Serializable
data class AlarmSettings(
    val enabled: Boolean = false,
    val time: String? = null,              // "HH:mm", e.g. "07:00"
    val label: String = "Wake up",
    @SerialName("snooze_minutes") val snoozeMinutes: Int = 9
)
