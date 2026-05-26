package ca.thebikemechanic.thruspark.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import ca.thebikemechanic.thruspark.BuildConfig
import ca.thebikemechanic.thruspark.shizuku.ShizukuState

/**
 * Third bottom-nav tab. Sections:
 *  - Shizuku: connection status + re-launch setup
 *  - Background pausing: emergency restore
 *  - Privacy & transparency: permissions explainer + data export
 *  - Reset: restart onboarding (keeps your data) OR full nuke (kills everything)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onManageShizuku: () -> Unit,
    onOpenPermissions: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var confirmRestart by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // ── Shizuku ──────────────────────────────────────────────
            SectionHeader("Shizuku (Tier 2 capabilities)")
            SettingCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, color, label, sub) = shizukuStatusUi(state.shizukuState)
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium)
                        Text(sub, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onManageShizuku, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.shizukuState == ShizukuState.Granted) "Manage Shizuku" else "Set up Shizuku")
                }
                Spacer(Modifier.height(8.dp))
                val context = LocalContext.current
                TextButton(
                    onClick = {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(URL_SETUP_VIDEO)
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Watch the setup video") }
            }

            Spacer(Modifier.height(20.dp))

            // ── App pausing ──────────────────────────────────────────
            SectionHeader("Background pausing")
            SettingCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PauseCircle, contentDescription = null,
                        tint = if (state.pausedAppCount > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (state.pausedAppCount > 0) "${state.pausedAppCount} apps currently paused"
                            else "No apps paused",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Use this if a profile didn't deactivate cleanly and apps stay restricted. Resets every paused app's background access to default.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                state.unpauseMessage?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    AssistChip(onClick = { viewModel.dismissUnpauseMessage() }, label = { Text(msg) })
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.unpauseEverything() },
                    enabled = !state.unpausing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.unpausing) CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    ) else Text("Unpause everything now")
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Privacy & transparency ───────────────────────────────
            SectionHeader("Privacy & transparency")
            SettingCard {
                val ctx = LocalContext.current
                Text(
                    "Network access",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "ThruSpark makes no network calls. The INTERNET permission is not declared in the app manifest — you can confirm via the system app info page below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        ctx.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.parse("package:${ctx.packageName}"))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open ThruSpark app info") }
            }

            Spacer(Modifier.height(8.dp))
            SettingNavRow(
                title = "Permissions used",
                subtitle = "Plain-English explanation of every Android permission ThruSpark declares",
                onClick = onOpenPermissions
            )

            Spacer(Modifier.height(20.dp))

            // ── Data ─────────────────────────────────────────────────
            SectionHeader("Data")
            SettingCard {
                Text(
                    "Export my data",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Share a JSON file containing your custom profiles and alarms. Nothing leaves the device unless you choose where to send it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.exportData() },
                    enabled = !state.exporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.exporting) CircularProgressIndicator(
                        modifier = Modifier.size(16.dp), strokeWidth = 2.dp
                    ) else Text("Export my data")
                }
                state.exportError?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    AssistChip(onClick = { viewModel.dismissExportError() }, label = { Text(msg) })
                }
                Spacer(Modifier.height(12.dp))
                LegalFooterLinks(modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(20.dp))

            // ── Reset ────────────────────────────────────────────────
            SectionHeader("Reset")
            SettingCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Restart onboarding", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium)
                        Text("Re-walk Walkthrough → Permissions → Shizuku. Custom profiles, alarms, and last-used selection are kept.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { confirmRestart = true },
                    enabled = !state.resetting,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Restart onboarding") }
            }

            Spacer(Modifier.height(12.dp))
            SettingCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Clear all data", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error)
                        Text("Delete custom profiles and alarms, deactivate active profile. Like a fresh install — but you don't lose the app itself.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { confirmClearAll = true },
                    enabled = !state.resetting,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Clear all data") }
            }

            Spacer(Modifier.height(28.dp))
            // ── About + verify build ─────────────────────────────────
            val ctx = LocalContext.current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "ThruSpark v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = {
                    ctx.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(URL_GITHUB_RELEASES))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Verify this build", style = MaterialTheme.typography.labelSmall) }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("Restart onboarding?") },
            text = { Text("You'll be sent back to the walkthrough. Custom profiles and alarms stay put.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestart = false
                    viewModel.restartOnboarding()
                }) { Text("Restart") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestart = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear all data?") },
            text = { Text("This deletes every custom profile, your alarms, and stops any active mode. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    viewModel.clearAllData()
                }) {
                    Text("Clear everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Compact tap-through row for navigating to a sub-screen. Used in the Privacy
 * & transparency section to avoid a screen-eating Card per entry.
 */
@Composable
private fun SettingNavRow(title: String, subtitle: String, onClick: () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

private data class StatusUi(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: androidx.compose.ui.graphics.Color,
    val label: String,
    val sub: String
)

@Composable
private fun shizukuStatusUi(state: ShizukuState): StatusUi = when (state) {
    ShizukuState.Granted -> StatusUi(
        Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary,
        "Granted", "Tier 2 capabilities are active when you start a profile.")
    ShizukuState.NotInstalled -> StatusUi(
        Icons.Default.Warning, MaterialTheme.colorScheme.error,
        "Not installed", "Install Shizuku to unlock airplane mode, refresh-rate cap, etc.")
    ShizukuState.BinderUnavailable -> StatusUi(
        Icons.Default.Info, MaterialTheme.colorScheme.tertiary,
        "Service not running", "Open Shizuku and start the service via wireless debugging.")
    ShizukuState.UnsupportedVersion -> StatusUi(
        Icons.Default.Warning, MaterialTheme.colorScheme.error,
        "Update Shizuku", "ThruSpark needs Shizuku v11 or newer.")
    ShizukuState.NeedsPermission -> StatusUi(
        Icons.Default.Info, MaterialTheme.colorScheme.tertiary,
        "Ready to grant", "Tap below to grant ThruSpark access.")
    ShizukuState.PermanentlyDenied -> StatusUi(
        Icons.Default.Warning, MaterialTheme.colorScheme.error,
        "Permission denied", "Open Shizuku and reset its app permissions, then retry.")
    ShizukuState.Unknown -> StatusUi(
        Icons.Default.Security, MaterialTheme.colorScheme.onSurfaceVariant,
        "Checking…", "")
}
