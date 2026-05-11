package ca.thebikemechanic.thruspark.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ca.thebikemechanic.thruspark.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingState(
    val hasWriteSettings: Boolean = false,
    val hasDndAccess: Boolean = false,
    val hasNotificationListener: Boolean = false
) {
    val allRequiredGranted: Boolean get() = hasWriteSettings && hasDndAccess
    val fullyGranted: Boolean get() = allRequiredGranted && hasNotificationListener
}

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun refreshPermissions() {
        val app = getApplication<Application>()
        _state.update {
            it.copy(
                hasWriteSettings = PermissionUtils.hasWriteSettings(app),
                hasDndAccess = PermissionUtils.hasDndAccess(app),
                hasNotificationListener = PermissionUtils.hasNotificationListenerAccess(app)
            )
        }
    }

    fun requestWriteSettings() {
        PermissionUtils.openWriteSettingsScreen(getApplication())
    }

    fun requestDndAccess() {
        PermissionUtils.openDndAccessScreen(getApplication())
    }

    fun requestNotificationListener() {
        PermissionUtils.openNotificationListenerScreen(getApplication())
    }
}
