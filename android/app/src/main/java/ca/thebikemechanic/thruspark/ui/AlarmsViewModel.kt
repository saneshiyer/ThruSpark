package ca.thebikemechanic.thruspark.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ca.thebikemechanic.thruspark.alarm.AlarmScheduler
import ca.thebikemechanic.thruspark.data.AlarmEntryStore
import ca.thebikemechanic.thruspark.model.AlarmEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlarmsUiState(
    val alarms: List<AlarmEntry> = emptyList(),
    val errorMessage: String? = null
)

class AlarmsViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AlarmsUiState())
    val state: StateFlow<AlarmsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            AlarmEntryStore.alarmsFlow(application).collect { list ->
                _state.update { it.copy(alarms = list) }
            }
        }
    }

    fun toggleEnabled(entry: AlarmEntry, enabled: Boolean) {
        viewModelScope.launch {
            val updated = entry.copy(enabled = enabled)
            AlarmEntryStore.upsert(getApplication(), updated)
            if (enabled) {
                val ok = AlarmScheduler.scheduleEntry(getApplication(), updated)
                if (!ok) {
                    _state.update {
                        it.copy(errorMessage = "Need 'Alarms & reminders' permission. Settings → Apps → ThruSpark → Alarms & reminders.")
                    }
                }
            } else {
                AlarmScheduler.cancelEntry(getApplication(), entry)
            }
        }
    }

    fun delete(entry: AlarmEntry) {
        viewModelScope.launch {
            AlarmScheduler.cancelEntry(getApplication(), entry)
            AlarmEntryStore.delete(getApplication(), entry.id)
        }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
