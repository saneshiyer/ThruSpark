package ca.thebikemechanic.thruspark.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ca.thebikemechanic.thruspark.auth.AuthRepository
import ca.thebikemechanic.thruspark.auth.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AuthRepository(application)

    private val _state = MutableStateFlow<AuthResult>(AuthResult.Idle)
    val state: StateFlow<AuthResult> = _state.asStateFlow()

    fun signUp(email: String, password: String) {
        if (!isValidEmail(email)) {
            _state.value = AuthResult.Failure("Enter a valid email address.")
            return
        }
        if (password.length < 6) {
            _state.value = AuthResult.Failure("Password must be at least 6 characters.")
            return
        }
        viewModelScope.launch {
            _state.value = AuthResult.Loading
            _state.value = repo.signUp(email.trim(), password)
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = AuthResult.Failure("Enter both email and password.")
            return
        }
        viewModelScope.launch {
            _state.value = AuthResult.Loading
            _state.value = repo.signIn(email.trim(), password)
        }
    }

    fun resetState() {
        _state.value = AuthResult.Idle
    }

    private fun isValidEmail(email: String): Boolean =
        email.contains("@") && email.contains(".") && email.length > 4
}
