package ca.thebikemechanic.thruspark.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

private const val TAG = "AlarmForegroundService"
private const val CHANNEL_ID = "thruspark_alarm"
private const val NOTIFICATION_ID = 2001

/**
 * On Android 10+, starting an Activity from a BroadcastReceiver while the screen
 * is off is restricted. The pattern is:
 *   AlarmReceiver → startForegroundService(AlarmForegroundService)
 *                 → service posts a high-priority notification with a fullScreenIntent
 *                 → system shows AlarmActivity as a heads-up or full-screen overlay
 *
 * This service's only job is to post that notification and then stop itself.
 * AlarmActivity takes over from there.
 */
class AlarmForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(AlarmReceiver.EXTRA_LABEL) ?: "Wake up"
        val snoozeMinutes = intent?.getIntExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, 9) ?: 9

        createNotificationChannel()

        val activityIntent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AlarmReceiver.EXTRA_LABEL, label)
            putExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label)
            .setContentText("Tap to dismiss")
            .setFullScreenIntent(fullScreenPendingIntent, /* highPriority= */ true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Foreground service started for alarm: $label")

        // The Activity will handle everything from here; we can stop once it's launched.
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ThruSpark Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Overnight alarm notifications"
            setBypassDnd(true)   // alarms should ring even when DND is on
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
