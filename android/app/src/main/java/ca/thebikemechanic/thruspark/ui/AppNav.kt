package ca.thebikemechanic.thruspark.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ca.thebikemechanic.thruspark.data.UserPrefsStore
import kotlinx.coroutines.launch

/**
 * v0.1 nav graph. Three destinations live in the bottom bar (Modes, Alarms,
 * Settings). Builder is reachable from Modes; Auth and Shizuku setup are
 * reachable from Settings; the bottom bar hides on those secondary screens.
 *
 * Phase C wraps this whole graph in MainActivity's auth/walkthrough/perms
 * gating — this composable only mounts after the user is past first-launch.
 */
@Composable
fun AppNav() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in setOf(Routes.HOME, Routes.ALARMS, Routes.SETTINGS)

    ShizukuReattentionDialog(navController)

    Scaffold(
        bottomBar = { if (showBottomBar) BottomBar(currentRoute, navController) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel(),
                    onCreateProfile = { navController.navigate(Routes.BUILDER_NEW) },
                    onEditProfile = { name ->
                        navController.navigate("${Routes.BUILDER_PREFIX}/$name")
                    }
                )
            }

            composable(Routes.ALARMS) {
                AlarmsScreen(
                    onCreate = { navController.navigate(Routes.ALARM_EDIT_NEW) },
                    onEdit = { id -> navController.navigate("${Routes.ALARM_EDIT_PREFIX}/$id") }
                )
            }

            composable(Routes.ALARM_EDIT_NEW) {
                AlarmEditScreen(
                    alarmId = null,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(
                route = "${Routes.ALARM_EDIT_PREFIX}/{alarmId}",
                arguments = listOf(navArgument("alarmId") { type = NavType.StringType })
            ) { entry ->
                AlarmEditScreen(
                    alarmId = entry.arguments?.getString("alarmId"),
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onSignIn = { navController.navigate(Routes.AUTH) },
                    onManageShizuku = { navController.navigate(Routes.SHIZUKU_SETUP) },
                    onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                    onOpenNetworkActivity = { navController.navigate(Routes.NETWORK_ACTIVITY) }
                )
            }

            composable(Routes.PERMISSIONS) {
                PermissionsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.NETWORK_ACTIVITY) {
                NetworkActivityScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.BUILDER_NEW) {
                BuilderScreen(
                    profileName = null,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onPickApps = { navController.navigate(Routes.APP_PICKER) }
                )
            }
            composable(
                route = "${Routes.BUILDER_PREFIX}/{profileName}",
                arguments = listOf(navArgument("profileName") { type = NavType.StringType })
            ) { entry ->
                BuilderScreen(
                    profileName = entry.arguments?.getString("profileName"),
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onPickApps = { navController.navigate(Routes.APP_PICKER) }
                )
            }

            composable(Routes.APP_PICKER) {
                AppPickerScreen(
                    onDone = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }

            composable(Routes.AUTH) {
                // Reuses the same AuthFlow as first-launch. Both Continue and Skip
                // just pop back to Settings — no need to mark auth_phase_done since
                // it's already true by the time we're inside AppNav.
                AuthFlow(
                    onComplete = { navController.popBackStack() },
                    onSkip = { navController.popBackStack() }
                )
            }

            composable(Routes.SHIZUKU_SETUP) {
                ShizukuOnboardingScreen(
                    onComplete = { navController.popBackStack() },
                    onSkip = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * One-shot dialog raised by [BootReceiver] when Shizuku was set up before the
 * reboot but isn't currently Granted. The flag is cleared on either button —
 * the next reboot's autotest re-raises it if Shizuku still isn't ready.
 */
@Composable
private fun ShizukuReattentionDialog(navController: NavHostController) {
    val context = LocalContext.current
    val pending by UserPrefsStore.shizukuReattentionPendingFlow(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()

    if (!pending) return

    val clear = { scope.launch { UserPrefsStore.setShizukuReattentionPending(context, false) } }

    AlertDialog(
        onDismissRequest = { clear() },
        title = { Text("Shizuku needs to be restarted") },
        text = {
            Text(
                "Shizuku doesn't survive a reboot. Restarting it needs Wireless debugging, " +
                    "which means joining a Wi-Fi network — any access point works (no " +
                    "internet required), but your phone can't use its own hotspot for this.\n\n" +
                    "Out of range of real Wi-Fi? Have a partner turn on their phone's " +
                    "mobile hotspot and join that — it's the most reliable field-recovery " +
                    "option. No data plan or internet needed on their side; the AP itself " +
                    "is enough.\n\n" +
                    "Even without Shizuku, brightness, dark mode, screen timeout, and Do Not " +
                    "Disturb keep working. ThruSpark will also offer manual one-tap shortcuts " +
                    "for airplane mode and battery saver next time you tap GO."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                clear()
                navController.navigate(Routes.SHIZUKU_SETUP)
            }) { Text("Set up now") }
        },
        dismissButton = {
            TextButton(onClick = { clear() }) { Text("Later") }
        }
    )
}

@Composable
private fun BottomBar(currentRoute: String?, navController: NavHostController) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Routes.HOME,
            onClick = { navTo(navController, Routes.HOME) },
            icon = { Icon(Icons.Default.Tune, contentDescription = null) },
            label = { Text("Modes") }
        )
        NavigationBarItem(
            selected = currentRoute == Routes.ALARMS,
            onClick = { navTo(navController, Routes.ALARMS) },
            icon = { Icon(Icons.Default.Alarm, contentDescription = null) },
            label = { Text("Alarms") }
        )
        NavigationBarItem(
            selected = currentRoute == Routes.SETTINGS,
            onClick = { navTo(navController, Routes.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Settings") }
        )
    }
}

private fun navTo(navController: NavHostController, route: String) {
    if (navController.currentDestination?.route == route) return
    navController.navigate(route) {
        popUpTo(navController.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private object Routes {
    const val HOME = "home"
    const val ALARMS = "alarms"
    const val SETTINGS = "settings"
    const val BUILDER_NEW = "builder"
    const val BUILDER_PREFIX = "builder"   // edit route: builder/{profileName}
    const val AUTH = "auth"
    const val SHIZUKU_SETUP = "shizuku_setup"
    const val APP_PICKER = "app_picker"
    const val ALARM_EDIT_NEW = "alarm_edit"
    const val ALARM_EDIT_PREFIX = "alarm_edit"   // edit route: alarm_edit/{alarmId}
    const val PERMISSIONS = "permissions"
    const val NETWORK_ACTIVITY = "network_activity"
}
