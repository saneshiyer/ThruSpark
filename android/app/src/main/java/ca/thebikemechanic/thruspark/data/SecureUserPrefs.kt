package ca.thebikemechanic.thruspark.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "SecureUserPrefs"
private const val PREFS_NAME = "thruspark_secure"
private const val PREFS_NAME_FALLBACK = "thruspark_secure_fallback"

/**
 * At-rest-encrypted key/value store for the small handful of values that are
 * sensitive enough to warrant app-layer encryption on top of Android's
 * full-disk encryption (audit finding H3).
 *
 * Currently stores only the signed-in email. Backed by EncryptedSharedPreferences
 * which uses an AES256_GCM master key kept in Android Keystore (hardware-backed
 * via TEE / StrongBox where available).
 *
 * **Failure mode:** on devices with a broken / locked-out Keystore (rare —
 * usually only happens after Factory Reset Protection issues or Keystore
 * compromise), MasterKey construction throws. We catch and fall back to a
 * plaintext SharedPreferences instance with a different name so we never
 * crash on a failed Keystore. The fallback is logged so a user inspecting
 * `adb logcat` can see they're not getting encryption — better than a silent
 * downgrade.
 */
object SecureUserPrefs {

    private const val KEY_EMAIL = "signed_in_email"

    /** Cached because EncryptedSharedPreferences.create() is slow on first call. */
    @Volatile private var cached: SharedPreferences? = null

    fun signedInEmail(context: Context): String? =
        prefs(context).getString(KEY_EMAIL, null)

    fun setSignedInEmail(context: Context, email: String?) {
        prefs(context).edit().apply {
            if (email == null) remove(KEY_EMAIL) else putString(KEY_EMAIL, email)
        }.apply()
    }

    /** Reactive flow — backed by SharedPreferences change listener. */
    fun signedInEmailFlow(context: Context): Flow<String?> = callbackFlow {
        val sp = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == KEY_EMAIL) trySend(p.getString(KEY_EMAIL, null))
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        trySend(sp.getString(KEY_EMAIL, null))   // emit current value immediately
        awaitClose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** Wipe — used by AppDataReset.clearAll. */
    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context): SharedPreferences {
        cached?.let { return it }
        val app = context.applicationContext
        val secure = runCatching {
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                app,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse { e ->
            // Keystore unavailable — fall back to plaintext so the app keeps working.
            // Logged loudly so a security-conscious user can detect the downgrade.
            Log.e(TAG, "Keystore unavailable, falling back to plaintext prefs: ${e.message}", e)
            app.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)
        }
        cached = secure
        return secure
    }
}
