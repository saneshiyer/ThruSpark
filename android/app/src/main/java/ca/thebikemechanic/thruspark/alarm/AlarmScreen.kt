package ca.thebikemechanic.thruspark.alarm

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Time-picker sheet shown when the user activates the Overnight profile.
 *
 * Lets the user choose their wake time, then calls back with the selected
 * hour and minute so the caller (e.g. HomeScreen or a bottom sheet) can
 * activate the profile and schedule the alarm together.
 *
 * Usage:
 *   OvernightAlarmPicker(
 *       onConfirm = { hour, minute ->
 *           profileEngine.activateProfile(context, "Overnight")
 *           alarmViewModel.scheduleAlarm(context, "Wake up", snoozeMinutes = 9)
 *       },
 *       onDismiss = { /* user cancelled */ }
 *   )
 */
@Composable
fun OvernightAlarmPicker(
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    alarmViewModel: AlarmViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by alarmViewModel.uiState.collectAsState()

    // Re-check permission whenever the composable is visible
    // (user might have just returned from Settings)
    LaunchedEffect(Unit) {
        alarmViewModel.refreshPermissionState(context)
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Set wake time",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "ThruSpark will silence everything overnight and wake you at the time you choose.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Time picker
            TimePickerRow(
                hour = state.hour,
                minute = state.minute,
                onHourChange = { alarmViewModel.setTime(it, state.minute) },
                onMinuteChange = { alarmViewModel.setTime(state.hour, it) }
            )

            // Warn if exact alarm permission is missing (Android 12+)
            if (state.needsPermission) {
                PermissionWarningCard(
                    onOpenSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            )
                        }
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val scheduled = alarmViewModel.scheduleAlarm(
                            context, "Wake up", snoozeMinutes = 9
                        )
                        if (scheduled) onConfirm(state.hour, state.minute)
                    }
                ) {
                    Text("Start Overnight")
                }
            }
        }
    }
}

// ── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun TimePickerRow(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Hour picker
        NumberPicker(
            value = hour,
            range = 0..23,
            label = "Hour",
            format = { "%02d".format(it) },
            onValueChange = onHourChange
        )

        Text(
            text = ":",
            fontSize = 36.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // Minute picker
        NumberPicker(
            value = minute,
            range = 0..59,
            label = "Min",
            format = { "%02d".format(it) },
            onValueChange = onMinuteChange
        )
    }
}

@Composable
private fun NumberPicker(
    value: Int,
    range: IntRange,
    label: String,
    format: (Int) -> String,
    onValueChange: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = {
            onValueChange(if (value >= range.last) range.first else value + 1)
        }) {
            Text("▲", fontSize = 18.sp)
        }

        Text(
            text = format(value),
            fontSize = 48.sp,
            fontWeight = FontWeight.Light
        )

        IconButton(onClick = {
            onValueChange(if (value <= range.first) range.last else value - 1)
        }) {
            Text("▼", fontSize = 18.sp)
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionWarningCard(onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Permission needed",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Android 12+ requires you to grant \"Alarms & Reminders\" permission for exact alarm scheduling.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenSettings) {
                Text("Open Settings")
            }
        }
    }
}
