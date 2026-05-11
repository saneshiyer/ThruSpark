package ca.thebikemechanic.thruspark.auth

/**
 * Result of a Supabase auth request — used by the ViewModel to render
 * loading / success / error states.
 *
 * v0.4: Success now optionally carries the user's access token. The token is
 * needed when calling protected endpoints like the account-deletion Edge
 * Function. Sign-up flows leave it null since the user typically hasn't
 * confirmed their email yet.
 */
sealed class AuthResult {
    object Idle : AuthResult()
    object Loading : AuthResult()
    data class Success(
        val email: String,
        val accessToken: String? = null,
        val needsEmailConfirmation: Boolean = false
    ) : AuthResult()
    data class Failure(val message: String) : AuthResult()
}
