package ca.thebikemechanic.thruspark.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.thebikemechanic.thruspark.model.ThruSparkProfile
import ca.thebikemechanic.thruspark.util.ManualFallback
import kotlinx.coroutines.delay

/**
 * Modes tab — presets and user-created custom profiles.
 *
 * GO button activates the last-used profile (or first preset on first run).
 * Tapping any profile card activates that specific profile.
 * Custom profiles get a ⋮ overflow menu for Edit / Delete.
 */
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onCreateProfile: () -> Unit,
    onEditProfile: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    // Refresh custom profiles every time this screen is composed (e.g. nav back from Builder)
    LaunchedEffect(Unit) { viewModel.refreshCustomProfiles() }

    // Shared duplicate handler — works for templates, seeded profiles, and customs alike
    val onDuplicate: (String) -> Unit = { sourceName ->
        viewModel.duplicateProfile(sourceName) { newName ->
            onEditProfile(newName)   // jump straight into the new copy for editing
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // Status header
        Text(
            text = if (state.isActive) "ThruSpark Active" else "ThruSpark",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        if (state.isActive && state.activeProfileName != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.activeProfileName!!,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(32.dp))

        // Big activate / deactivate button
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(120.dp))
        } else {
            val canActivate = state.defaultProfileName != null
            Button(
                onClick = {
                    if (state.isActive) viewModel.deactivate()
                    else viewModel.activateDefault()
                },
                modifier = Modifier.size(140.dp),
                shape = CircleShape,
                enabled = state.isActive || canActivate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isActive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                if (state.isActive) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("END", style = MaterialTheme.typography.headlineLarge)
                        state.activeSessionEndMs?.let { endMs ->
                            SessionCountdown(endMs)
                        }
                    }
                } else {
                    Text("GO", style = MaterialTheme.typography.headlineLarge)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                state.isActive -> "Tap to deactivate"
                state.lastUsedProfile != null -> "Tap to start ${state.lastUsedProfile}"
                state.defaultProfileName != null -> "Tap to start ${state.defaultProfileName}"
                else -> "Create or pick a profile to begin"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(28.dp))

        // Template section (v0.3: just Minimum Power)
        if (state.presets.isNotEmpty()) {
            SectionHeader(if (state.presets.size == 1) "Template" else "Templates")
            state.presets.forEach { p ->
                PresetRow(
                    profile = p,
                    isActive = state.activeProfileName == p.name,
                    enabled = !state.isActive,
                    onTap = { viewModel.activateProfile(p.name) },
                    onDuplicate = { onDuplicate(p.name) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        // Custom profiles section
        SectionHeader("Your profiles")
        if (state.customProfiles.isEmpty()) {
            Text(
                "No custom profiles yet. Create one tuned to your trip.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        } else {
            state.customProfiles.forEach { p ->
                CustomProfileRow(
                    profile = p,
                    isActive = state.activeProfileName == p.name,
                    enabled = !state.isActive,
                    onTap = { viewModel.activateProfile(p.name) },
                    onEdit = { onEditProfile(p.name) },
                    onDuplicate = { onDuplicate(p.name) },
                    onDelete = { viewModel.deleteCustomProfile(p.name) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCreateProfile,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Create custom profile")
        }

        Spacer(Modifier.height(20.dp))

        // Applied report
        AnimatedVisibility(visible = state.lastApplied != null, enter = fadeIn(), exit = fadeOut()) {
            state.lastApplied?.let { report ->
                AppliedProfileReport(report = report, onDismiss = viewModel::dismissReport)
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (state.showManualFallback) {
        ManualFallbackSheet(onDismiss = viewModel::dismissManualFallback)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PresetRow(
    profile: ThruSparkProfile,
    isActive: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    onDuplicate: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onTap,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                profile.name,
                modifier = Modifier.weight(1f),
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                sessionLabel(profile.session.durationHours),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Template options")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Duplicate to customize") },
                    onClick = { menuExpanded = false; onDuplicate() }
                )
            }
        }
    }
}

@Composable
private fun CustomProfileRow(
    profile: ThruSparkProfile,
    isActive: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onTap,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                profile.name,
                modifier = Modifier.weight(1f),
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                sessionLabel(profile.session.durationHours),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Profile options")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = { menuExpanded = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("Duplicate") },
                    onClick = { menuExpanded = false; onDuplicate() }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuExpanded = false; confirmDelete = true }
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${profile.name}\"?") },
            text = { Text("This profile will be removed. Presets are unaffected.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

private fun sessionLabel(durationHours: Int?): String =
    when (durationHours) {
        null -> "no timer"
        1 -> "1 h"
        else -> "$durationHours h"
    }

/**
 * Live countdown to [endMs]. Ticks every second; stops when the remaining
 * time hits zero. Wall-clock-based, so it stays correct across recompositions
 * and process death — `endMs` is computed from the activation timestamp.
 */
@Composable
private fun SessionCountdown(endMs: Long) {
    var remaining by remember(endMs) {
        mutableStateOf(endMs - System.currentTimeMillis())
    }
    LaunchedEffect(endMs) {
        while (remaining > 0) {
            delay(1000L)
            remaining = endMs - System.currentTimeMillis()
        }
    }
    Text(
        text = formatRemaining(remaining.coerceAtLeast(0)),
        style = MaterialTheme.typography.labelLarge
    )
}

private fun formatRemaining(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}

/**
 * No-Shizuku field-recovery sheet. Surfaced after activating a profile that
 * wanted Tier 2 toggles when Shizuku isn't ready. Each row deep-links into
 * the corresponding system Settings screen and shows a live ✓ once the user
 * actually flips the toggle (polled while the sheet is open).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualFallbackSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    val toggles by produceState(
        initialValue = ManualFallback.isAirplaneOn(context) to ManualFallback.isBatterySaverOn(context),
        context
    ) {
        while (true) {
            value = ManualFallback.isAirplaneOn(context) to ManualFallback.isBatterySaverOn(context)
            delay(750L)
        }
    }
    val (airplaneOn, batterySaverOn) = toggles

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("Finish manually", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Shizuku isn't ready, so ThruSpark can't flip these for you. " +
                    "Tap each one to open the system toggle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            FallbackRow(
                label = "Airplane mode",
                description = "Disables every radio at once — usually the biggest battery win.",
                isOn = airplaneOn,
                onOpen = { ManualFallback.openAirplaneSettings(context) }
            )
            Spacer(Modifier.height(8.dp))
            FallbackRow(
                label = "Battery saver",
                description = "On Pixel, the next screen also offers Extreme Battery Saver.",
                isOn = batterySaverOn,
                onOpen = { ManualFallback.openBatterySaverSettings(context) }
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Done") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FallbackRow(
    label: String,
    description: String,
    isOn: Boolean,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isOn) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "On",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    "○",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onOpen) {
            Text(if (isOn) "Settings" else "Open")
        }
    }
}

@Composable
fun AppliedProfileReport(
    report: ca.thebikemechanic.thruspark.capability.AppliedProfile,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("What was applied", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
            Spacer(Modifier.height(8.dp))
            if (report.applied.isNotEmpty()) {
                report.applied.forEach { item ->
                    Text("✓ $item",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            if (report.skipped.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Skipped (needs Shizuku or permissions):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                report.skipped.forEach { item ->
                    Text("– $item",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (report.failed.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Failed:", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
                report.failed.forEach { item ->
                    Text("✗ $item",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
