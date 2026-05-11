package ca.thebikemechanic.thruspark.auth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "SupabaseAuth"

/**
 * Thin wrapper around the Supabase Auth REST API. Stateless — credentials
 * persistence lives in AuthRepository / UserPrefsStore.
 *
 * We use raw OkHttp instead of the Supabase Kotlin SDK to keep the dependency
 * footprint small (the SDK pulls in Ktor + 8 other modules). Email/password
 * auth needs only three endpoints, all simple JSON.
 */
class SupabaseAuth(
    private val baseUrl: String,
    private val anonKey: String,
    private val deleteFunctionName: String = "delete-account",
    activityLogger: Interceptor? = null
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .apply { activityLogger?.let { addInterceptor(it) } }
        .build()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val mediaType = "application/json".toMediaType()

    private val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && anonKey.isNotBlank() && baseUrl.startsWith("https://")

    suspend fun signUp(email: String, password: String): AuthResult = call("signup") {
        post("/auth/v1/signup", json.encodeToString(EmailPasswordBody.serializer(), EmailPasswordBody(email, password)))
    }

    suspend fun signIn(email: String, password: String): AuthResult = call("signin") {
        post(
            "/auth/v1/token?grant_type=password",
            json.encodeToString(EmailPasswordBody.serializer(), EmailPasswordBody(email, password))
        )
    }

    suspend fun requestPasswordReset(email: String): AuthResult = call("recover") {
        post("/auth/v1/recover", json.encodeToString(EmailOnlyBody.serializer(), EmailOnlyBody(email)))
    }

    /**
     * Calls the user-deployed `delete-account` Edge Function. The function is
     * responsible for invoking `supabase.auth.admin.deleteUser(user.id)` since
     * the client SDK can't delete its own auth row directly.
     *
     * Pass the user's fresh access token (typically obtained via signIn() right
     * before calling this — sensitive-action re-auth is the standard pattern).
     *
     * The Edge Function code is documented in Compliance-Handoff.md.
     */
    suspend fun deleteAccount(accessToken: String): AuthResult = call("delete-account") {
        postAuthed("/functions/v1/$deleteFunctionName", accessToken, "{}")
    }

    private suspend fun call(label: String, block: () -> ResponseBundle): AuthResult =
        withContext(Dispatchers.IO) {
            if (!isConfigured) {
                return@withContext AuthResult.Failure("Supabase isn't configured. Check local.properties.")
            }
            try {
                val resp = block()
                when {
                    resp.code in 200..299 -> {
                        val parsed = runCatching {
                            json.decodeFromString(AuthResponse.serializer(), resp.body)
                        }.getOrNull()
                        val email = parsed?.user?.email ?: parsed?.email ?: ""
                        val needsConfirm = parsed?.user?.confirmedAt == null && parsed?.accessToken == null
                        AuthResult.Success(
                            email = email,
                            accessToken = parsed?.accessToken,
                            needsEmailConfirmation = needsConfirm
                        )
                    }
                    else -> {
                        val err = runCatching {
                            json.decodeFromString(ErrorResponse.serializer(), resp.body)
                        }.getOrNull()
                        val msg = err?.errorDescription ?: err?.msg ?: err?.message
                            ?: "HTTP ${resp.code}"
                        Log.w(TAG, "$label failed: $msg")
                        AuthResult.Failure(humanize(msg))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "$label network error", e)
                AuthResult.Failure("Network error — are you online?")
            }
        }

    private fun post(path: String, jsonBody: String): ResponseBundle {
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(mediaType))
            .build()
        client.newCall(req).execute().use { r ->
            return ResponseBundle(r.code, r.body?.string().orEmpty())
        }
    }

    /** Like post(), but uses the user's JWT instead of the anon key for Authorization. */
    private fun postAuthed(path: String, userToken: String, jsonBody: String): ResponseBundle {
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $userToken")
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(mediaType))
            .build()
        client.newCall(req).execute().use { r ->
            return ResponseBundle(r.code, r.body?.string().orEmpty())
        }
    }

    private fun humanize(raw: String): String = when {
        raw.contains("Invalid login", ignoreCase = true) -> "Email or password didn't match."
        raw.contains("already registered", ignoreCase = true) -> "An account with that email already exists."
        raw.contains("password", ignoreCase = true) && raw.contains("characters", ignoreCase = true) ->
            "Password must be at least 6 characters."
        raw.contains("rate limit", ignoreCase = true) -> "Too many attempts — try again in a minute."
        else -> raw
    }

    private data class ResponseBundle(val code: Int, val body: String)

    @Serializable
    private data class EmailPasswordBody(val email: String, val password: String)

    @Serializable
    private data class EmailOnlyBody(val email: String)

    @Serializable
    private data class AuthResponse(
        @kotlinx.serialization.SerialName("access_token") val accessToken: String? = null,
        val user: SupabaseUser? = null,
        val email: String? = null
    )

    @Serializable
    private data class SupabaseUser(
        val id: String? = null,
        val email: String? = null,
        @kotlinx.serialization.SerialName("confirmed_at") val confirmedAt: String? = null
    )

    @Serializable
    private data class ErrorResponse(
        val msg: String? = null,
        val message: String? = null,
        @kotlinx.serialization.SerialName("error_description") val errorDescription: String? = null,
        val error: String? = null
    )
}
