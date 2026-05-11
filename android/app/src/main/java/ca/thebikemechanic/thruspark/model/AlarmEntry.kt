package ca.thebikemechanic.thruspark.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * v0.2: AlarmEntry now has a stable id and optional day-of-week repeat.
 * AlarmEntryStore stores a list of these (backwards-compat with v0.1's single-entry).
 *
 * daysOfWeek uses java.util.Calendar's day numbering: SUNDAY=1, MONDAY=2, ..., SATURDAY=7.
 * An empty set means "fire once on the next occurrence, then disable."
 */
@Serializable
data class AlarmEntry(
    val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    val time: String = "07:00",
    val label: String = "Wake up",
    val snoozeMinutes: Int = 9,
    val daysOfWeek: Set<Int> = emptySet()
) {
    val isRepeating: Boolean get() = daysOfWeek.isNotEmpty()

    /** Stable int derived from id — used as AlarmManager request code. */
    val requestCode: Int get() = id.hashCode()

    fun describeRepeat(): String = when {
        daysOfWeek.isEmpty() -> "Once"
        daysOfWeek.size == 7 -> "Every day"
        daysOfWeek == setOf(2, 3, 4, 5, 6) -> "Weekdays"
        daysOfWeek == setOf(1, 7) -> "Weekends"
        else -> daysOfWeek.sorted().joinToString(" ") { dayShortName(it) }
    }

    companion object {
        fun dayShortName(calendarDay: Int): String = when (calendarDay) {
            1 -> "Sun"; 2 -> "Mon"; 3 -> "Tue"; 4 -> "Wed"
            5 -> "Thu"; 6 -> "Fri"; 7 -> "Sat"; else -> "?"
        }
    }
}
