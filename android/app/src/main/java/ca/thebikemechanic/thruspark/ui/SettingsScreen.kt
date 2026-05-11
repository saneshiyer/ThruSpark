package ca.thebikemechanic.thruspark.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.AccountCircle
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
 * Third bottom-nav tab. Three sections:
 *  - Account: signed-in status + sign in / sign out
 *  - Shizuku: connection status + re-launch setup
 *  - Reset: restart onboarding (keeps your data) OR full nuke (kills everything)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSignIn: () -> Unit,
    onManageShizuku: () -> Unit,
    onOpenPermissions: () -> Unit = {},
    onOpenNetworkActivity: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var confirmRestart by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmDeleteAccount by remember { mutableStateOf(false) }

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
            // ── Account ──────────────────────────────────────────────
            SectionHeader("Account")
            SettingCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        if (state.signedInEmail != null) {
                            Text("Signed in as", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.signedInEmail!!, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                        } else {
                            Text("Not signed in", style = MaterialTheme.typography.bodyMedium)
                            Text("Sync custom profiles between devices in a future version",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (state.signedInEmail != null) {
                    OutlinedButton(onClick = { viewModel.signOut() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Sign out")
                    }
                    Spacer(Modifier.height(8.dp))
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
                        Spacer(Modifier.height(4.dp))
                        AssistChip(onClick = { viewModel.dismissExportError() }, label = { Text(msg) })
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { confirmDeleteAccount = true },
                        enabled = !state.deletingAccount,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete account")
                    }
                } else {
                    Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                        Text("Sign in or create account")
                    }
                }
                Spacer(Modifier.height(12.dp))
                LegalFooterLinks(modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(20.dp))

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
                    "ThruSpark uses the network only for sign-in / account features and to open the setup video. Connections use standard Android system trust (HTTPS via OS-validated certificates; no certificate pinning). If you skipped account creation or signed out, you can deny network access entirely from system settings — the rest of the app keeps working.",
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
            Spacer(Modifier.height(8.dp))
            SettingNavRow(
                title = "Network activity",
                subtitle = "Audit log of every network call — you should mostly see nothing",
                onClick = onOpenNetworkActivity
            )

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
                        Text("Re-walk Welcome → Walkthrough → Permissions → Shizuku. Custom profiles, alarm, and last-used selection are kept.",
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
                        Text("Delete custom profiles, alarm, sign-out, deactivate active profile. Like a fresh install — but you don't lose the app itself.",
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
            text = { Text("You'll be sent back to the Welcome screen. Custom profiles, alarms, and your sign-in stay put.") },
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
            text = { Text("This deletes every custom profile, your alarm, signs you out, and stops any active mode. This can't be undone.") },
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

    if (confirmDeleteAccount) {
        DeleteAccountDialog(
            email = state.signedInEmail.orEmpty(),
            errorMessage = state.deleteAccountError,
            isDeleting = state.deletingAccount,
            onConfirm = { password -> viewModel.deleteAccount(password) },
            onDismiss = {
                confirmDeleteAccount = false
                viewModel.dismissDeleteError()
            }
        )
    }
}

/**
 * Password re-prompt before account deletion. Sensitive-action re-auth pattern —
 * matches Google, Apple, etc. Shows the email being deleted, error inline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteAccountDialog(
    email: String,
    errorMessage: String?,
    isDeleting: Boolean,
    onConfirm: (password: String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = { Text("Delete account?") },
        text = {
            Column {
                Text("This permanently deletes your account ($email) and all on-device data. This cannot be undone.")
                Spacer(Modifier.height(12.dp))
                Text("Enter your password to confirm.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    enabled = !isDeleting,
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = !isDeleting && password.isNotBlank()
            ) {
                if (isDeleting) CircularProgressIndicator(
                    modifier = Modifier.size(16.dp), strokeWidth = 2.dp
                ) else Text("Delete account", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Cancel") }
        }
    )
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
