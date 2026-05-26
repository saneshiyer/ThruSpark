package ca.thebikemechanic.thruspark.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ca.thebikemechanic.thruspark.capability.AppPauser
import ca.thebikemechanic.thruspark.data.AppDataReset
import ca.thebikemechanic.thruspark.data.AppRestrictionsStore
import ca.thebikemechanic.thruspark.data.DataExporter
import ca.thebikemechanic.thruspark.shizuku.ShizukuManager
import ca.thebikemechanic.thruspark.shizuku.ShizukuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val shizukuState: ShizukuState = ShizukuState.Unknown,
    val pausedAppCount: Int = 0,
    val resetting: Boolean = false,
    val unpausing: Boolean = false,
    val unpauseMessage: String? = null,
    val resetMessage: String? = null,
    val exporting: Boolean = false,
    val exportError: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            ShizukuManager.state.collect { shizuku ->
                _state.update { it.copy(shizukuState = shizuku) }
            }
        }
        refreshPausedCount()
    }

    fun refreshPausedCount() {
        viewModelScope.launch {
            val count = AppRestrictionsStore.getRestricted(getApplication()).size
            _state.update { it.copy(pausedAppCount = count) }
        }
    }

    fun unpauseEverything() {
        viewModelScope.launch {
            _state.update { it.copy(unpausing = true, unpauseMessage = null) }
            val count = AppPauser.emergencyRestore(getApplication())
            _state.update {
                it.copy(
                    unpausing = false,
                    pausedAppCount = 0,
                    unpauseMessage = if (count > 0) "Restored $count app${if (count == 1) "" else "s"}"
                    else "Nothing to restore — no apps were tracked as paused"
                )
            }
        }
    }

    fun dismissUnpauseMessage() {
        _state.update { it.copy(unpauseMessage = null) }
    }

    fun restartOnboarding() {
        viewModelScope.launch {
            _state.update { it.copy(resetting = true) }
            AppDataReset.restartOnboarding(getApplication())
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            _state.update { it.copy(resetting = true) }
            AppDataReset.clearAll(getApplication())
        }
    }

    fun exportData() {
        viewModelScope.launch {
            _state.update { it.copy(exporting = true, exportError = null) }
            try {
                val jsonContent = DataExporter.buildJson(getApplication())
                DataExporter.share(getApplication(), jsonContent)
                _state.update { it.copy(exporting = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(exporting = false, exportError = "Couldn't build export: ${e.message}")
                }
            }
        }
    }

    fun dismissExportError() {
        _state.update { it.copy(exportError = null) }
    }
}
