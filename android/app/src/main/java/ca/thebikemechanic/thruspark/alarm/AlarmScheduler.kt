package ca.thebikemechanic.thruspark.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import ca.thebikemechanic.thruspark.model.AlarmEntry
import java.util.Calendar

private const val TAG = "AlarmScheduler"
private const val LEGACY_REQUEST_CODE = 1001  // for the profile-embedded alarm only (back-compat)

/**
 * Schedules and cancels both:
 *   - Standalone alarms managed from the Alarms screen (multiple, per-id)
 *   - The single profile-embedded alarm carried by ThruSparkProfile.alarm (legacy v0.1)
 *
 * Standalone alarms use [AlarmEntry.requestCode] (id.hashCode()) as the
 * AlarmManager request code so multiple can coexist. Repeating alarms
 * re-schedule themselves from AlarmReceiver after firing.
 *
 * Uses AlarmManager.setAlarmClock() which:
 *   - Is exempt from Doze (fires even in deep sleep)
 *   - Works in airplane mode
 *   - Shows the next-alarm icon on the lock screen
 *
 * Android 12+ requires SCHEDULE_EXACT_ALARM (or user grants in Settings).
 */
object AlarmScheduler {

    // ── v0.2: per-entry scheduling for the Alarms screen ───────────────────

    fun scheduleEntry(context: Context, entry: AlarmEntry): Boolean {
        if (!entry.enabled) return false
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot schedule exact alarm — permission not granted")
            return false
        }
        val triggerMs = nextOccurrenceMillis(entry.time, entry.daysOfWeek)
            ?: return false   // no valid future occurrence (shouldn't happen)
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerMs, openAppIntent(context, entry.requestCode)),
            entryReceiverIntent(context, entry)
        )
        Log.d(TAG, "Scheduled '${entry.label}' (id=${entry.id.take(8)}…) for $triggerMs " +
            "(${(triggerMs - System.currentTimeMillis()) / 60_000} min away)")
        return true
    }

    fun cancelEntry(context: Context, entry: AlarmEntry) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(entryReceiverIntent(context, entry))
        Log.d(TAG, "Cancelled '${entry.label}' (id=${entry.id.take(8)}…)")
    }

    private fun entryReceiverIntent(context: Context, entry: AlarmEntry): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            entry.requestCode,
            Intent(context, AlarmReceiver::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_ID, entry.id)
                putExtra(AlarmReceiver.EXTRA_LABEL, entry.label)
                putExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, entry.snoozeMinutes)
                putExtra(AlarmReceiver.EXTRA_TIME, entry.time)
                putExtra(AlarmReceiver.EXTRA_DAYS_CSV, entry.daysOfWeek.joinToString(","))
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // ── v0.1 back-compat: profile-embedded single alarm ────────────────────

    fun schedule(context: Context, timeString: String, label: String): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot schedule exact alarm — permission not granted")
            return false
        }
        val triggerMs = nextOccurrenceMillis(timeString, emptySet()) ?: return false
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerMs, openAppIntent(context, LEGACY_REQUEST_CODE)),
            buildLegacyReceiverIntent(context, label)
        )
        Log.d(TAG, "[legacy] Alarm scheduled for $timeString")
        return true
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(buildLegacyReceiverIntent(context, ""))
        Log.d(TAG, "[legacy] Alarm cancelled")
    }

    private fun buildLegacyReceiverIntent(context: Context, label: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            LEGACY_REQUEST_CODE,
            Intent(context, AlarmReceiver::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_LABEL, label)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, Class.forName("ca.thebikemechanic.thruspark.ui.MainActivity")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * Wall-clock time for the next occurrence of [timeString] given an optional
     * day-of-week set. Empty set = next occurrence today/tomorrow regardless of weekday.
     * Returns null only if the input is malformed.
     */
    fun nextOccurrenceMillis(timeString: String, daysOfWeek: Set<Int>): Long? {
        val parts = timeString.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (daysOfWeek.isEmpty()) {
            // One-shot: today or tomorrow
            if (cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        // Repeating: walk forward until we hit a matching day with future time
        repeat(8) {   // safety bound: at most 7 days forward
            val day = cal.get(Calendar.DAY_OF_WEEK)
            if (day in daysOfWeek && cal.timeInMillis > System.currentTimeMillis()) {
                return cal.timeInMillis
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null   // unreachable if daysOfWeek is non-empty (would always find within 7 days)
    }
}
