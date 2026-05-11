package ca.thebikemechanic.thruspark.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ca.thebikemechanic.thruspark.data.InstalledAppInfo
import ca.thebikemechanic.thruspark.data.InstalledAppsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppPickerUiState(
    val loading: Boolean = true,
    val apps: List<InstalledAppInfo> = emptyList(),
    val query: String = "",
    val selected: Set<String> = emptySet()
) {
    val filteredApps: List<InstalledAppInfo>
        get() = if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
}

class AppPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AppPickerUiState())
    val state: StateFlow<AppPickerUiState> = _state.asStateFlow()

    init {
        // Initial selection comes from the bus (set by Builder before navigating)
        _state.update { it.copy(selected = AppPickerBus.initial.value) }
        viewModelScope.launch {
            val apps = InstalledAppsRepository.loadLaunchable(getApplication())
            _state.update { it.copy(loading = false, apps = apps) }
        }
    }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
    }

    fun toggle(packageName: String) {
        _state.update {
            val next = if (packageName in it.selected) it.selected - packageName
            else it.selected + packageName
            it.copy(selected = next)
        }
    }

    fun selectAllFiltered() {
        _state.update { s ->
            val toAdd = s.filteredApps.map { it.packageName }
            s.copy(selected = s.selected + toAdd)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptySet()) }
    }

    /** Push the final selection to the bus so Builder picks it up. */
    fun commit() {
        AppPickerBus.publishResult(_state.value.selected)
    }
}
