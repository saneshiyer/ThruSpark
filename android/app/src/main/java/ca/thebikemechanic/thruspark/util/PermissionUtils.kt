package ca.thebikemechanic.thruspark.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object PermissionUtils {

    fun hasWriteSettings(context: Context): Boolean =
        Settings.System.canWrite(context)

    fun hasDndAccess(context: Context): Boolean =
        context.getSystemService(android.app.NotificationManager::class.java)
            .isNotificationPolicyAccessGranted

    fun hasNotificationListenerAccess(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(context.packageName)
    }

    /** Opens the system screen to grant WRITE_SETTINGS */
    fun openWriteSettingsScreen(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /** Opens the DND / notification policy settings screen */
    fun openDndAccessScreen(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /** Opens the notification listener settings screen */
    fun openNotificationListenerScreen(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
