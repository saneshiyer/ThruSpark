package ca.thebikemechanic.thruspark.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class PermissionInfo(
    val category: String,
    val display: String,
    val manifest: String,
    val purpose: String
)

/**
 * Plain-English explanation of every permission ThruSpark declares. The intent
 * is "if you're worried about what we can do, here's the full picture in one
 * place." Manifest names are shown alongside friendly names so a security-
 * minded user can cross-reference against AndroidManifest.xml.
 *
 * Keep this list in sync with AndroidManifest.xml. When adding a new
 * permission, add a row here AND in Compliance-Handoff.md.
 */
private val PERMISSIONS = listOf(
    PermissionInfo(
        category = "Power controls (Tier 1)",
        display = "Modify System Settings",
        manifest = "android.permission.WRITE_SETTINGS",
        purpose = "Sets your screen brightness and screen-timeout when a profile activates. You grant this manually in onboarding via Android Settings."
    ),
    PermissionInfo(
        category = "Power controls (Tier 1)",
        display = "Notification Policy Access",
        manifest = "android.permission.ACCESS_NOTIFICATION_POLICY",
        purpose = "Toggles Do Not Disturb when a profile activates and turns it off when the profile ends. You grant this manually in onboarding."
    ),

    PermissionInfo(
        category = "Background work",
        display = "Foreground Service",
        manifest = "android.permission.FOREGROUND_SERVICE",
        purpose = "Keeps a persistent 'profile active' notification visible while a profile is running, so paused apps are restored if you swipe ThruSpark from recents."
    ),
    PermissionInfo(
        category = "Background work",
        display = "Foreground Service (Special Use)",
        manifest = "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
        purpose = "Required by Android 14+ for the active-profile notification service category."
    ),
    PermissionInfo(
        category = "Background work",
        display = "Receive Boot Completed",
        manifest = "android.permission.RECEIVE_BOOT_COMPLETED",
        purpose = "Restores your previously-active profile after the device restarts, so you don't have to re-activate after every reboot."
    ),

    PermissionInfo(
        category = "Alarms",
        display = "Schedule Exact Alarm",
        manifest = "android.permission.SCHEDULE_EXACT_ALARM",
        purpose = "Wake-up alarms you set in the Alarms tab need precise scheduling so they fire at the exact configured time."
    ),
    PermissionInfo(
        category = "Alarms",
        display = "Wake Lock",
        manifest = "android.permission.WAKE_LOCK",
        purpose = "Wakes the CPU briefly when a scheduled alarm fires."
    ),
    PermissionInfo(
        category = "Alarms",
        display = "Use Full-Screen Intent",
        manifest = "android.permission.USE_FULL_SCREEN_INTENT",
        purpose = "Shows the alarm UI over the lock screen when an alarm goes off."
    ),

    PermissionInfo(
        category = "Notifications",
        display = "Post Notifications",
        manifest = "android.permission.POST_NOTIFICATIONS",
        purpose = "Shows the active-profile status notification and alarm notifications. Required on Android 13+."
    ),
    PermissionInfo(
        category = "Notifications",
        display = "Notification Listener Service",
        manifest = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
        purpose = "Filters notifications from non-allowlisted apps while a Do-Not-Disturb profile is active. Notification content is read locally only and never transmitted off-device."
    ),

    PermissionInfo(
        category = "Account features",
        display = "Internet",
        manifest = "android.permission.INTERNET",
        purpose = "Used only for sign-in / sign-up / password reset and to open the YouTube setup video. No data, profiles, or analytics are sent. See Settings → Network activity for the full audit log."
    ),
    PermissionInfo(
        category = "Account features",
        display = "Access Network State",
        manifest = "android.permission.ACCESS_NETWORK_STATE",
        purpose = "Lets the app check whether you're online before attempting an auth call, so we can show a clear error message offline."
    ),

    PermissionInfo(
        category = "App visibility",
        display = "Query launchable apps (<queries>)",
        manifest = "queries / intent / ACTION_MAIN+CATEGORY_LAUNCHER",
        purpose = "Shows the list of installed apps in the per-profile picker so you can pick which apps stay active during a profile. Only apps with a launcher icon are visible — system services and headless packages are filtered out."
    ),

    PermissionInfo(
        category = "Quick Settings tile",
        display = "Bind Quick Settings Tile",
        manifest = "android.permission.BIND_QUICK_SETTINGS_TILE",
        purpose = "Lets the system place ThruSpark's tile in your Quick Settings shade so you can activate / deactivate without opening the app."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions used") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Every Android permission ThruSpark declares, what it actually does, and where in the app it's used. Cross-references AndroidManifest.xml — open-source builds let you verify the source for each permission yourself.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Group by category
            PERMISSIONS.groupBy { it.category }.forEach { (category, perms) ->
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        category.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }
                items(perms) { perm ->
                    PermissionCard(perm)
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PermissionCard(perm: PermissionInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(perm.display, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                perm.manifest,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                perm.purpose,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
