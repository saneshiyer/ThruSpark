package ca.thebikemechanic.thruspark.ui

import android.app.Application
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.thebikemechanic.thruspark.alarm.AlarmScheduler
import ca.thebikemechanic.thruspark.data.AlarmEntryStore
import ca.thebikemechanic.thruspark.model.AlarmEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Add or edit a single AlarmEntry. Time, label, snooze, and weekday repeat.
 * Pass alarmId=null to create; pass an existing id to load + edit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    alarmId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AlarmEditViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(alarmId) { viewModel.load(alarmId) }
    LaunchedEffect(state.savedSuccessfully) { if (state.savedSuccessfully) onSaved() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (alarmId == null) "New alarm" else "Edit alarm") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Big tappable time
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        formatTime(state.time),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        showTimePicker(context, state.time) { picked ->
                            viewModel.setTime(picked)
                        }
                    }) { Text("Change time") }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Days of week
            Text("Repeat", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Display order: Mon Tue Wed Thu Fri Sat Sun (more familiar than Calendar's Sun-first)
                listOf(2 to "M", 3 to "T", 4 to "W", 5 to "T", 6 to "F", 7 to "S", 1 to "S").forEach { (calDay, letter) ->
                    val selected = calDay in state.daysOfWeek
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.toggleDay(calDay) },
                        label = { Text(letter) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                if (state.daysOfWeek.isEmpty()) "Fires once at the next ${formatTime(state.time)}"
                else "Fires every ${describeDays(state.daysOfWeek)} at ${formatTime(state.time)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(20.dp))

            // Label
            OutlinedTextField(
                value = state.label,
                onValueChange = viewModel::setLabel,
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            // Snooze
            Text(
                "Snooze ${state.snoozeMinutes} min",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = state.snoozeMinutes.toFloat(),
                onValueChange = { viewModel.setSnooze(it.toInt()) },
                valueRange = 1f..30f,
                steps = 28
            )

            Spacer(Modifier.height(28.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = { viewModel.save() },
                    enabled = !state.saving,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.saving) CircularProgressIndicator(
                        modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    else Text("Save alarm")
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun showTimePicker(context: android.content.Context, initial: String, onPicked: (String) -> Unit) {
    val parts = initial.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 7
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    TimePickerDialog(context, { _, hh, mm ->
        onPicked(String.format("%02d:%02d", hh, mm))
    }, h, m, true).show()
}

private fun formatTime(hhmm: String): String {
    val parts = hhmm.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return hhmm
    val m = parts.getOrNull(1) ?: return hhmm
    return String.format("%d:%s", h, m)
}

private fun describeDays(days: Set<Int>): String = when {
    days.size == 7 -> "day"
    days == setOf(2, 3, 4, 5, 6) -> "weekday"
    days == setOf(1, 7) -> "weekend"
    else -> days.sorted().joinToString(", ") { AlarmEntry.dayShortName(it) }
}

// ── ViewModel ─────────────────────────────────────────────────────────────

data class AlarmEditFormState(
    val id: String? = null,
    val enabled: Boolean = true,
    val time: String = "07:00",
    val label: String = "Wake up",
    val snoozeMinutes: Int = 9,
    val daysOfWeek: Set<Int> = emptySet(),
    val saving: Boolean = false,
    val savedSuccessfully: Boolean = false
)

class AlarmEditViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AlarmEditFormState())
    val state: StateFlow<AlarmEditFormState> = _state.asStateFlow()

    fun load(id: String?) {
        if (id == null) {
            _state.value = AlarmEditFormState()
            return
        }
        viewModelScope.launch {
            val existing = AlarmEntryStore.getAll(getApplication()).firstOrNull { it.id == id }
            if (existing != null) {
                _state.value = AlarmEditFormState(
                    id = existing.id,
                    enabled = existing.enabled,
                    time = existing.time,
                    label = existing.label,
                    snoozeMinutes = existing.snoozeMinutes,
                    daysOfWeek = existing.daysOfWeek
                )
            }
        }
    }

    fun setTime(time: String) = _state.update { it.copy(time = time) }
    fun setLabel(label: String) = _state.update { it.copy(label = label) }
    fun setSnooze(minutes: Int) = _state.update { it.copy(snoozeMinutes = minutes) }
    fun toggleDay(calendarDay: Int) = _state.update { s ->
        val next = if (calendarDay in s.daysOfWeek) s.daysOfWeek - calendarDay else s.daysOfWeek + calendarDay
        s.copy(daysOfWeek = next)
    }

    fun save() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val entry = AlarmEntry(
                id = s.id ?: java.util.UUID.randomUUID().toString(),
                enabled = s.enabled,
                time = s.time,
                label = s.label.ifBlank { "Wake up" },
                snoozeMinutes = s.snoozeMinutes,
                daysOfWeek = s.daysOfWeek
            )
            AlarmEntryStore.upsert(getApplication(), entry)
            // Re-schedule (cancels prior with same id since AlarmManager dedupes by request code)
            if (entry.enabled) AlarmScheduler.scheduleEntry(getApplication(), entry)
            else AlarmScheduler.cancelEntry(getApplication(), entry)
            _state.update { it.copy(saving = false, savedSuccessfully = true) }
        }
    }
}
