package ca.thebikemechanic.thruspark.auth

import android.content.Context
import ca.thebikemechanic.thruspark.BuildConfig
import ca.thebikemechanic.thruspark.data.UserPrefsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Single point of access for auth. Wraps SupabaseAuth (network) and
 * UserPrefsStore (persistence of "who is signed in").
 *
 * Model: one-time device sign-in. Once signedInEmail is set in prefs, the app
 * treats the user as signed in indefinitely until they explicitly sign out —
 * no token refresh, no expiry check, no online dependency. The token from
 * sign-in is NOT persisted; sensitive operations (account deletion) re-prompt
 * for password to obtain a fresh token.
 */
class AuthRepository(private val context: Context) {

    private val auth = SupabaseAuth(
        baseUrl = BuildConfig.SUPABASE_URL,
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
        deleteFunctionName = BuildConfig.SUPABASE_DELETE_FUNCTION,
        activityLogger = NetworkActivityLogger(context.applicationContext)
    )

    fun signedInEmailFlow(): Flow<String?> =
        UserPrefsStore.signedInEmailFlow(context)

    suspend fun signUp(email: String, password: String): AuthResult {
        val result = auth.signUp(email, password)
        if (result is AuthResult.Success) {
            UserPrefsStore.setSignedInEmail(context, email.trim().lowercase())
        }
        return result
    }

    suspend fun signIn(email: String, password: String): AuthResult {
        val result = auth.signIn(email, password)
        if (result is AuthResult.Success) {
            UserPrefsStore.setSignedInEmail(context, email.trim().lowercase())
        }
        return result
    }

    suspend fun requestPasswordReset(email: String): AuthResult =
        auth.requestPasswordReset(email)

    suspend fun signOut() {
        UserPrefsStore.setSignedInEmail(context, null)
    }

    /**
     * Fully delete the user's Supabase auth row and clear local data.
     *
     * Re-prompts for password (sensitive-action re-auth), exchanges it for a
     * fresh JWT, calls the user-deployed `delete-account` Edge Function with
     * the JWT, then on success clears the signed-in email locally.
     *
     * Caller is responsible for then invoking AppDataReset.clearAll to wipe
     * the rest of on-device state — see SettingsViewModel.deleteAccount.
     */
    suspend fun deleteAccount(password: String): AuthResult {
        val email = UserPrefsStore.signedInEmailFlow(context).first()
            ?: return AuthResult.Failure("Not signed in.")

        // Re-authenticate to get a fresh access token
        val signIn = auth.signIn(email, password)
        val token = (signIn as? AuthResult.Success)?.accessToken
            ?: return AuthResult.Failure(
                (signIn as? AuthResult.Failure)?.message ?: "Couldn't verify password."
            )

        // Call the deletion Edge Function — user must deploy this; see Compliance-Handoff.md
        val result = auth.deleteAccount(token)
        if (result is AuthResult.Success) {
            UserPrefsStore.setSignedInEmail(context, null)
        }
        return result
    }
}
