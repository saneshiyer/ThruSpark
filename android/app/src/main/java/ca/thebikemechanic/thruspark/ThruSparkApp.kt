package ca.thebikemechanic.thruspark

import android.app.Application
import ca.thebikemechanic.thruspark.data.UserPrefsStore
import ca.thebikemechanic.thruspark.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-level entry point. Two responsibilities at app start:
 *
 *  1. Initialize [ShizukuManager] so its state Flow is hydrated by the time
 *     the user reaches setup or home — avoids "Unknown" flash on the
 *     Shizuku-status badges in Settings.
 *
 *  2. Migrate any legacy plaintext signed-in email from DataStore into
 *     [androidx.security.crypto.EncryptedSharedPreferences] (audit fix H3).
 *     Idempotent — runs every launch but only does work the first time
 *     after the v0.5+ upgrade.
 *
 * Registered via `android:name=".ThruSparkApp"` in AndroidManifest.xml's
 * `<application>` tag.
 */
class ThruSparkApp : Application() {

    /** Long-lived scope for one-shot startup work. SupervisorJob so a single
     *  failure (e.g. legacy DataStore read error) doesn't propagate. */
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ShizukuManager.init(this)
        startupScope.launch {
            UserPrefsStore.migrateLegacyEmailIfNeeded(this@ThruSparkApp)
        }
    }
}
