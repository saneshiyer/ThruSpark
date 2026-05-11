package ca.thebikemechanic.thruspark.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.thebikemechanic.thruspark.model.AlarmEntry

/**
 * Multi-alarm list screen. Tap an alarm card to edit, ⋮ menu to delete,
 * switch to enable/disable in place, FAB to create new.
 *
 * When an alarm fires, any active ThruSpark profile deactivates (existing behavior).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsScreen(
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: AlarmsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Alarms") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = "Add alarm")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            state.errorMessage?.let { msg ->
                AssistChip(
                    onClick = { viewModel.dismissError() },
                    label = { Text(msg) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (state.alarms.isEmpty()) {
                EmptyAlarmsState()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.alarms, key = { it.id }) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            onTap = { onEdit(alarm.id) },
                            onToggle = { viewModel.toggleEnabled(alarm, it) },
                            onDelete = { viewModel.delete(alarm) }
                        )
                    }
                    item {
                        Spacer(Modifier.height(80.dp))   // padding so FAB doesn't overlap last card
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAlarmsState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Alarm,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text("No alarms set", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap + to add one. When an alarm fires, the active profile deactivates.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        )
    }
}

@Composable
private fun AlarmCard(
    alarm: AlarmEntry,
    onTap: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onTap() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatTime(alarm.time),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (alarm.enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(alarm.label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    alarm.describeRepeat() + " · snooze ${alarm.snoozeMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Alarm options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { menuExpanded = false; onTap() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

private fun formatTime(hhmm: String): String {
    val parts = hhmm.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return hhmm
    val m = parts.getOrNull(1) ?: return hhmm
    return String.format("%d:%s", h, m)
}
