package ca.thebikemechanic.thruspark.alarm

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AlarmUiState(
    val hour: Int = 7,
    val minute: Int = 0,
    val isScheduled: Boolean = false,
    val needsPermission: Boolean = false   // true on Android 12+ if exact alarm permission is missing
)

/**
 * Manages the alarm time picker state and bridges to AlarmScheduler.
 *
 * Designed to be used from the Overnight profile activation sheet — the user
 * picks a wake time, taps "Start Overnight", and we both activate the profile
 * and schedule the alarm in one action.
 */
class AlarmViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmUiState())
    val uiState: StateFlow<AlarmUiState> = _uiState.asStateFlow()

    fun setTime(hour: Int, minute: Int) {
        _uiState.value = _uiState.value.copy(hour = hour, minute = minute)
    }

    /**
     * Schedule the alarm and update UI state.
     * Returns false if the SCHEDULE_EXACT_ALARM permission is needed first.
     */
    fun scheduleAlarm(context: Context, label: String, snoozeMinutes: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                _uiState.value = _uiState.value.copy(needsPermission = true)
                return false
            }
        }

        val timeString = String.format("%02d:%02d", _uiState.value.hour, _uiState.value.minute)
        val scheduled = AlarmScheduler.schedule(context, timeString, label)
        _uiState.value = _uiState.value.copy(isScheduled = scheduled, needsPermission = false)
        return scheduled
    }

    fun cancelAlarm(context: Context) {
        AlarmScheduler.cancel(context)
        _uiState.value = _uiState.value.copy(isScheduled = false)
    }

    fun refreshPermissionState(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (am.canScheduleExactAlarms()) {
                _uiState.value = _uiState.value.copy(needsPermission = false)
            }
        }
    }
}
