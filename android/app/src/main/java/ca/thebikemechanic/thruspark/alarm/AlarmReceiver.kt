package ca.thebikemechanic.thruspark.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import ca.thebikemechanic.thruspark.model.AlarmEntry

private const val TAG = "AlarmReceiver"

/**
 * Fires when the scheduled alarm time arrives.
 *
 * Responsibilities:
 *  1. Start AlarmActivity as a full-screen foreground activity (visible on lock screen)
 *  2. Hand off the label/snooze so AlarmActivity can display them
 *  3. v0.2: if this is a repeating alarm (daysOfWeek non-empty), re-schedule
 *     it for the next matching day after firing
 *
 * BroadcastReceivers have a very short execution window (~10 seconds), so we
 * keep work minimal — just start the Activity and re-schedule synchronously.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_LABEL = "alarm_label"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        const val EXTRA_TIME = "alarm_time"            // "HH:mm" — for re-schedule
        const val EXTRA_DAYS_CSV = "alarm_days_csv"    // "1,2,3" — for re-schedule
    }

    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Wake up"
        val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 9)
        val id = intent.getStringExtra(EXTRA_ID)
        val time = intent.getStringExtra(EXTRA_TIME)
        val daysCsv = intent.getStringExtra(EXTRA_DAYS_CSV).orEmpty()
        val daysOfWeek = daysCsv.split(",").mapNotNull { it.toIntOrNull() }.toSet()

        Log.d(TAG, "Alarm fired — id=${id?.take(8)} label='$label' repeat=${daysOfWeek.isNotEmpty()}")

        // Start the visible alarm UI
        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceIntent = Intent(context, AlarmForegroundService::class.java).apply {
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            }
            context.startForegroundService(serviceIntent)
        } else {
            context.startActivity(activityIntent)
        }

        // v0.2: re-schedule for next matching day if this is a repeating alarm
        if (id != null && time != null && daysOfWeek.isNotEmpty()) {
            val next = AlarmEntry(
                id = id,
                enabled = true,
                time = time,
                label = label,
                snoozeMinutes = snoozeMinutes,
                daysOfWeek = daysOfWeek
            )
            AlarmScheduler.scheduleEntry(context, next)
        }
    }
}
