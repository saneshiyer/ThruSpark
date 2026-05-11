package ca.thebikemechanic.thruspark.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ca.thebikemechanic.thruspark.data.InstalledAppsRepository
import ca.thebikemechanic.thruspark.data.ProfileStateStore
import ca.thebikemechanic.thruspark.engine.ProfileEngine
import ca.thebikemechanic.thruspark.model.AlarmSettings
import ca.thebikemechanic.thruspark.model.BackgroundSettings
import ca.thebikemechanic.thruspark.model.DisplaySettings
import ca.thebikemechanic.thruspark.model.EssentialApps
import ca.thebikemechanic.thruspark.model.NotificationSettings
import ca.thebikemechanic.thruspark.model.ProfileRepository
import ca.thebikemechanic.thruspark.model.RadioSettings
import ca.thebikemechanic.thruspark.model.SessionSettings
import ca.thebikemechanic.thruspark.model.ThruSparkProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Form state for the custom profile builder. Each field maps to a piece of
 * ThruSparkProfile. On save we assemble a ThruSparkProfile and persist via
 * ProfileRepository.saveCustom().
 */
data class BuilderFormState(
    val name: String = "",
    // Display
    val brightness: Float = 0.3f,
    val darkMode: Boolean = true,
    val grayscale: Boolean = false,
    val refreshHz: Int = 60,
    val timeoutSec: Int = 30,
    // Radios
    val airplane: Boolean = false,
    val wifi: Boolean = false,
    val cellular: Boolean = true,
    val forceLte: Boolean = false,
    val gps: Boolean = true,
    val bluetooth: Boolean = false,
    val nfc: Boolean = false,
    // Notifications
    val dndEnabled: Boolean = true,
    val allowContacts: Set<String> = setOf("starred"),
    val allowApps: Set<String> = setOf("nav", "messaging", "emergency"),
    // Background
    val restrictExcept: Set<String> = setOf("nav", "messaging", "emergency"),
    val freezeNonEssential: Boolean = false,
    // Essential apps
    val essentialCategories: Set<String> = setOf("nav", "messaging", "emergency", "camera"),
    val explicitPackages: Set<String> = emptySet(),     // user-picked specific apps for this profile
    // Session
    val durationHours: Int? = null,
    // UI state
    val originalName: String? = null,   // non-null when editing
    val saving: Boolean = false,
    val errorMessage: String? = null,
    val savedSuccessfully: Boolean = false
) {
    val isEdit: Boolean get() = originalName != null
    val isValid: Boolean get() = name.isNotBlank()
}

class BuilderViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ProfileRepository(application)

    private val _state = MutableStateFlow(BuilderFormState())
    val state: StateFlow<BuilderFormState> = _state.asStateFlow()

    /** package → human label, for resolving [BuilderFormState.explicitPackages] for display. */
    private val _appLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val appLabels: StateFlow<Map<String, String>> = _appLabels.asStateFlow()

    init {
        viewModelScope.launch {
            _appLabels.value = InstalledAppsRepository.loadLaunchable(getApplication())
                .associate { it.packageName to it.label }
        }
    }

    /** Populate the form from an existing custom profile (edit mode). */
    fun loadForEdit(profileName: String) {
        viewModelScope.launch {
            val p = repo.loadCustomProfiles().firstOrNull { it.name == profileName } ?: return@launch
            _state.value = BuilderFormState(
                name = p.name,
                brightness = p.display.brightness,
                darkMode = p.display.darkMode,
                grayscale = p.display.grayscale,
                refreshHz = p.display.refreshHz,
                timeoutSec = p.display.timeoutSec,
                airplane = p.radios.airplane,
                wifi = p.radios.wifi,
                cellular = p.radios.cellular,
                forceLte = p.radios.forceLte,
                gps = p.radios.gps,
                bluetooth = p.radios.bluetooth,
                nfc = p.radios.nfc,
                dndEnabled = p.notifications.dndEnabled,
                allowContacts = p.notifications.allowlistContacts.toSet(),
                allowApps = p.notifications.allowlistApps.toSet(),
                restrictExcept = p.background.restrictAllExcept.toSet(),
                freezeNonEssential = p.background.freezeNonEssential,
                essentialCategories = p.essentialApps.categories.toSet(),
                explicitPackages = p.essentialApps.explicitPackages.toSet(),
                durationHours = p.session.durationHours,
                originalName = p.name
            )
        }
    }

    /** Called by Builder before navigating to the picker — seeds the bus with current selection. */
    fun handOffToPicker() {
        AppPickerBus.setInitial(_state.value.explicitPackages)
    }

    /** Called by Builder when AppPickerBus.result emits a non-null value (the user committed). */
    fun applyPickerResult(packages: Set<String>) {
        _state.update { it.copy(explicitPackages = packages) }
        AppPickerBus.consumeResult()
    }

    fun update(transform: (BuilderFormState) -> BuilderFormState) {
        _state.update(transform)
    }

    fun toggleSet(field: (BuilderFormState) -> Set<String>, item: String) {
        _state.update { s ->
            val current = field(s)
            val next = if (item in current) current - item else current + item
            when (field) {
                BuilderFormState::allowContacts -> s.copy(allowContacts = next)
                BuilderFormState::allowApps -> s.copy(allowApps = next)
                BuilderFormState::restrictExcept -> s.copy(restrictExcept = next)
                BuilderFormState::essentialCategories -> s.copy(essentialCategories = next)
                else -> s
            }
        }
    }

    fun save() {
        val s = _state.value
        if (!s.isValid) {
            _state.update { it.copy(errorMessage = "Profile name is required") }
            return
        }
        // Block save if name collides — but allow keeping the same name when editing
        if (s.name != s.originalName && repo.customNameTaken(s.name)) {
            _state.update { it.copy(errorMessage = "A profile named \"${s.name}\" already exists") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(saving = true, errorMessage = null) }
            try {
                val app = getApplication<Application>()
                // Capture before save: was the profile being edited the currently-active one?
                // If so we re-activate after the write so live system state reflects the edit.
                val activeName = ProfileStateStore.activeProfileNameFlow(app).first()
                val originalWasActive = s.isEdit && s.originalName != null && s.originalName == activeName

                // If editing AND the name changed, delete the old file first
                if (s.isEdit && s.originalName != null && s.originalName != s.name) {
                    repo.deleteCustom(s.originalName)
                }
                repo.saveCustom(s.toProfile())

                if (originalWasActive) {
                    // Full re-orchestration: re-applies provider settings, re-primes the
                    // notification allowlist, re-runs the app-pause pass, and (per current
                    // ProfileEngine behavior) resets any session-duration timer.
                    ProfileEngine.activateProfile(app, s.name.trim())
                }

                _state.update { it.copy(saving = false, savedSuccessfully = true) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, errorMessage = "Couldn't save: ${e.message}") }
            }
        }
    }

    fun deleteCurrent() {
        val s = _state.value
        val name = s.originalName ?: return
        viewModelScope.launch {
            repo.deleteCustom(name)
            _state.update { it.copy(savedSuccessfully = true) }
        }
    }

    private fun BuilderFormState.toProfile(): ThruSparkProfile = ThruSparkProfile(
        name = name.trim(),
        version = "1.0",
        display = DisplaySettings(brightness, darkMode, grayscale, refreshHz, timeoutSec),
        radios = RadioSettings(airplane, wifi, cellular, forceLte, gps, bluetooth, nfc),
        notifications = NotificationSettings(dndEnabled, allowContacts.toList(), allowApps.toList()),
        background = BackgroundSettings(restrictExcept.toList(), freezeNonEssential),
        essentialApps = EssentialApps(essentialCategories.toList(), explicitPackages = explicitPackages.toList()),
        session = SessionSettings(durationHours = durationHours, autoDeactivateAt = null),
        alarm = AlarmSettings()  // custom profiles don't include an embedded alarm in v0.1
    )
}
