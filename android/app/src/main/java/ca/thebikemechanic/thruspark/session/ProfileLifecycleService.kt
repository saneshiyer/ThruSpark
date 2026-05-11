package ca.thebikemechanic.thruspark.session

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import ca.thebikemechanic.thruspark.R
import ca.thebikemechanic.thruspark.engine.ProfileEngine
import ca.thebikemechanic.thruspark.ui.MainActivity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "ProfileLifecycleSvc"
private const val CHANNEL_ID = "thruspark_active_profile"
private const val NOTIFICATION_ID = 4242
private const val DEACTIVATE_TIMEOUT_MS = 8_000L

/**
 * Foreground service that runs while a profile is active. Two responsibilities:
 *
 *  1. Provide a persistent "ThruSpark active" status notification with a
 *     one-tap Deactivate action — useful when the screen is dim or the user
 *     forgot a profile is running.
 *
 *  2. Listen for [onTaskRemoved] (user swipes ThruSpark from recents) and
 *     auto-deactivate the active profile so apps don't stay paused after the
 *     user thinks they've closed the app.
 *
 * Lifecycle:
 *   - ProfileEngine.activate* → ProfileLifecycleService.start(context, name)
 *   - ProfileEngine.deactivate* → ProfileLifecycleService.stop(context)
 *   - Service.onTaskRemoved → ProfileEngine.deactivateSuspend (synchronous,
 *     wrapped in withTimeoutOrNull so we don't block forever if Shizuku hangs)
 */
class ProfileLifecycleService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DEACTIVATE -> {
                Log.d(TAG, "Deactivate action tapped")
                runDeactivate()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val profileName = intent?.getStringExtra(EXTRA_PROFILE_NAME) ?: "Active"
                startForeground(NOTIFICATION_ID, buildNotification(profileName))
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed — auto-deactivating active profile")
        runDeactivate()
        stopSelf()
    }

    private fun runDeactivate() {
        // We're on the main thread; runBlocking with a timeout keeps Shizuku
        // calls from hanging the system if the binder dies mid-restore.
        runBlocking {
            withTimeoutOrNull(DEACTIVATE_TIMEOUT_MS) {
                ProfileEngine.deactivateSuspend(applicationContext)
            } ?: Log.w(TAG, "Deactivate timed out — apps may stay paused. Use Settings → Unpause everything.")
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active profile",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent indicator while a ThruSpark profile is running"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(profileName: String): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val deactivateIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ProfileLifecycleService::class.java).apply { action = ACTION_DEACTIVATE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ThruSpark active")
            .setContentText(profileName)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openIntent)
            .addAction(0, "Deactivate", deactivateIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    companion object {
        const val ACTION_DEACTIVATE = "ca.thebikemechanic.thruspark.DEACTIVATE_PROFILE"
        const val EXTRA_PROFILE_NAME = "profile_name"

        fun start(context: Context, profileName: String) {
            val intent = Intent(context, ProfileLifecycleService::class.java)
                .putExtra(EXTRA_PROFILE_NAME, profileName)
            runCatching { context.startForegroundService(intent) }
                .onFailure { Log.w(TAG, "startForegroundService failed: ${it.message}") }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ProfileLifecycleService::class.java)) }
        }
    }
}
