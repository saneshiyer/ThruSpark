package ca.thebikemechanic.thruspark.data

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lightweight info about a launchable user app — name + package only, no icon for v0.1. */
data class InstalledAppInfo(
    val packageName: String,
    val label: String
)

/**
 * Lists user-launchable apps via PackageManager. Only returns apps with a
 * MAIN/LAUNCHER intent (i.e. things the user can actually open from the
 * launcher) — system services and headless packages are filtered out.
 *
 * Requires the <queries> declaration in AndroidManifest for Android 11+.
 */
object InstalledAppsRepository {

    suspend fun loadLaunchable(context: Context): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcherIntent, 0)
            .map { resolveInfo ->
                InstalledAppInfo(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(pm).toString()
                )
            }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }   // hide ThruSpark itself
            .sortedBy { it.label.lowercase() }
    }
}
