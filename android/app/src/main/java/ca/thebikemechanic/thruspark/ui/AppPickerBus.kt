package ca.thebikemechanic.thruspark.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tiny singleton to pass package selection between BuilderScreen and AppPickerScreen.
 *
 * Compose Navigation's savedStateHandle pattern works for return values but is
 * awkward for passing structured input (forces us to URL-encode a list). Since
 * only one Builder is open at a time, a process-wide bus is the pragmatic v0.1
 * choice. v0.2 can swap this for a parent-route shared ViewModel if needed.
 *
 * Usage:
 *   1. Builder calls setInitial(currentSelection) before navigating to picker.
 *   2. Picker reads initial.value on first composition.
 *   3. Picker calls publishResult(newSelection) and popBackStack() on Done.
 *   4. Builder observes result, applies it to its form state, then calls
 *      consumeResult() to clear so it doesn't reapply on subsequent composes.
 */
object AppPickerBus {

    private val _initial = MutableStateFlow<Set<String>>(emptySet())
    val initial: StateFlow<Set<String>> = _initial.asStateFlow()

    private val _result = MutableStateFlow<Set<String>?>(null)
    val result: StateFlow<Set<String>?> = _result.asStateFlow()

    fun setInitial(packages: Set<String>) {
        _initial.value = packages
    }

    fun publishResult(packages: Set<String>) {
        _result.value = packages
    }

    fun consumeResult() {
        _result.value = null
    }
}
