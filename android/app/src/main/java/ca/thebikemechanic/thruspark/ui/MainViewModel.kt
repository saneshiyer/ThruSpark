package ca.thebikemechanic.thruspark.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ca.thebikemechanic.thruspark.capability.AppliedProfile
import ca.thebikemechanic.thruspark.data.ProfileStateStore
import ca.thebikemechanic.thruspark.data.UserPrefsStore
import ca.thebikemechanic.thruspark.engine.ProfileEngine
import ca.thebikemechanic.thruspark.model.ProfileRepository
import ca.thebikemechanic.thruspark.model.ThruSparkProfile
import ca.thebikemechanic.thruspark.shizuku.ShizukuManager
import ca.thebikemechanic.thruspark.shizuku.ShizukuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val isActive: Boolean = false,
    val activeProfileName: String? = null,
    val presets: List<ThruSparkProfile> = emptyList(),
    val customProfiles: List<ThruSparkProfile> = emptyList(),
    val lastUsedProfile: String? = null,
    val lastApplied: AppliedProfile? = null,
    val isLoading: Boolean = false,
    /** Wall-clock ms when the active profile's session auto-ends. Null when no
     *  profile is active or the active profile has no duration set. */
    val activeSessionEndMs: Long? = null,
    /** True after a GO that would have used Shizuku-only toggles but Shizuku
     *  isn't ready — surfaces the manual-toggle bottom sheet on HomeScreen. */
    val showManualFallback: Boolean = false
) {
    /** First custom profile or, failing that, the first preset. Used for the GO button when no last-used. */
    val defaultProfileName: String?
        get() = lastUsedProfile
            ?: customProfiles.firstOrNull()?.name
            ?: presets.firstOrNull()?.name
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ProfileRepository(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        // Presets are bundled — load once
        viewModelScope.launch {
            _uiState.update { it.copy(presets = repo.loadPresets()) }
            refreshCustomProfiles()
        }

        // Live state from DataStore
        viewModelScope.launch {
            combine(
                ProfileStateStore.isActiveFlow(application),
                ProfileStateStore.activeProfileNameFlow(application),
                ProfileStateStore.activatedAtMsFlow(application),
                UserPrefsStore.lastUsedProfileFlow(application)
            ) { isActive, name, activatedAtMs, lastUsed ->
                ActiveSnapshot(isActive, name, activatedAtMs, lastUsed)
            }.collect { snap ->
                val endMs = if (snap.isActive && snap.name != null && snap.activatedAtMs != null) {
                    val hours = repo.loadPreset(snap.name)?.session?.durationHours
                    hours?.let { snap.activatedAtMs + it * 3_600_000L }
                } else null
                _uiState.update {
                    it.copy(
                        isActive = snap.isActive,
                        activeProfileName = snap.name,
                        lastUsedProfile = snap.lastUsed,
                        activeSessionEndMs = endMs
                    )
                }
            }
        }
    }

    private data class ActiveSnapshot(
        val isActive: Boolean,
        val name: String?,
        val activatedAtMs: Long?,
        val lastUsed: String?
    )

    /** Re-read custom profiles from disk. Called from HomeScreen on every composition. */
    fun refreshCustomProfiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(customProfiles = repo.loadCustomProfiles()) }
        }
    }

    fun activateProfile(profileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = ProfileEngine.activateProfileSuspend(getApplication(), profileName)
            UserPrefsStore.setLastUsedProfile(getApplication(), profileName)
            val showFallback = needsManualFallback(profileName)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    lastApplied = result,
                    showManualFallback = showFallback
                )
            }
        }
    }

    /**
     * True when the user activated a profile that wanted at least one toggle
     * Shizuku would normally handle, AND Shizuku isn't currently Granted.
     * Battery saver isn't a per-profile field, but it's a universal power
     * lever — the sheet shows it whenever it surfaces.
     */
    private suspend fun needsManualFallback(profileName: String): Boolean {
        if (ShizukuManager.state.value == ShizukuState.Granted) return false
        val profile = repo.loadPreset(profileName) ?: return false
        return profile.radios.airplane
    }

    fun dismissManualFallback() {
        _uiState.update { it.copy(showManualFallback = false) }
    }

    fun activateDefault() {
        val name = _uiState.value.defaultProfileName ?: return
        activateProfile(name)
    }

    fun deactivate() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            ProfileEngine.deactivateSuspend(getApplication())
            _uiState.update { it.copy(isLoading = false, lastApplied = null) }
        }
    }

    fun deleteCustomProfile(name: String) {
        viewModelScope.launch {
            repo.deleteCustom(name)
            refreshCustomProfiles()
        }
    }

    /**
     * Duplicate any profile (preset, seeded, or custom). Returns the new name
     * via the supplied callback so the caller can navigate to BuilderScreen for
     * the new copy. ProfileRepository handles unique-name generation.
     */
    fun duplicateProfile(sourceName: String, onCreated: (newName: String) -> Unit) {
        viewModelScope.launch {
            val source = repo.loadAll().firstOrNull { it.name == sourceName } ?: return@launch
            val newName = repo.duplicate(source)
            refreshCustomProfiles()
            onCreated(newName)
        }
    }

    fun dismissReport() {
        _uiState.update { it.copy(lastApplied = null) }
    }
}
