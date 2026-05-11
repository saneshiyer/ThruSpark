package ca.thebikemechanic.thruspark.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ca.thebikemechanic.thruspark.auth.AuthRepository
import ca.thebikemechanic.thruspark.auth.AuthResult
import ca.thebikemechanic.thruspark.capability.AppPauser
import ca.thebikemechanic.thruspark.data.AppDataReset
import ca.thebikemechanic.thruspark.data.AppRestrictionsStore
import ca.thebikemechanic.thruspark.data.DataExporter
import ca.thebikemechanic.thruspark.shizuku.ShizukuManager
import ca.thebikemechanic.thruspark.shizuku.ShizukuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val signedInEmail: String? = null,
    val shizukuState: ShizukuState = ShizukuState.Unknown,
    val pausedAppCount: Int = 0,
    val resetting: Boolean = false,
    val unpausing: Boolean = false,
    val unpauseMessage: String? = null,
    val resetMessage: String? = null,
    // Account deletion
    val deletingAccount: Boolean = false,
    val deleteAccountError: String? = null,
    // Data export
    val exporting: Boolean = false,
    val exportError: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = AuthRepository(application)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                authRepo.signedInEmailFlow(),
                ShizukuManager.state
            ) { email, shizuku -> Pair(email, shizuku) }
                .collect { (email, shizuku) ->
                    _state.update { it.copy(signedInEmail = email, shizukuState = shizuku) }
                }
        }
        // Refresh paused-app count on every entry to Settings
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

    fun signOut() {
        viewModelScope.launch { authRepo.signOut() }
    }

    fun restartOnboarding() {
        viewModelScope.launch {
            _state.update { it.copy(resetting = true) }
            AppDataReset.restartOnboarding(getApplication())
            // No need to clear resetting=false — the reset triggers MainActivity
            // recomposition into AuthFlow, this ViewModel goes away anyway.
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            _state.update { it.copy(resetting = true) }
            AppDataReset.clearAll(getApplication())
        }
    }

    /**
     * Full account deletion: re-authenticate with [password], call the
     * Supabase Edge Function to delete the auth row, then wipe everything
     * on-device via AppDataReset.clearAll. On success the user lands back at
     * the Welcome screen via the MainActivity gating recompose.
     */
    fun deleteAccount(password: String) {
        viewModelScope.launch {
            _state.update { it.copy(deletingAccount = true, deleteAccountError = null) }
            val result = authRepo.deleteAccount(password)
            when (result) {
                is AuthResult.Success -> {
                    AppDataReset.clearAll(getApplication())
                    // No need to set deletingAccount=false — recompose into
                    // AuthFlow disposes this ViewModel anyway.
                }
                is AuthResult.Failure -> _state.update {
                    it.copy(deletingAccount = false, deleteAccountError = result.message)
                }
                else -> _state.update { it.copy(deletingAccount = false) }
            }
        }
    }

    fun dismissDeleteError() {
        _state.update { it.copy(deleteAccountError = null) }
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
