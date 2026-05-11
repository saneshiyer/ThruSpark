package ca.thebikemechanic.thruspark.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Notification filtering hook for active profiles.
 *
 * v0.1: empty stub — the AndroidManifest declares the service so the app can
 * compile and so the user can grant Notification Access in onboarding without
 * a future migration. All notifications pass through unchanged.
 *
 * v0.2 will use this to suppress non-allowlisted notifications when a profile
 * is active (per ThruSparkProfile.notifications.allowlistApps and
 * ThruSparkProfile.notifications.allowlistContacts).
 */
class ThruSparkNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Intentionally empty in v0.1 — we observe but do not act.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Intentionally empty in v0.1.
    }
}
