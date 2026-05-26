package ca.thebikemechanic.thruspark

import android.app.Application
import ca.thebikemechanic.thruspark.shizuku.ShizukuManager

/**
 * Process-level entry point. Initializes [ShizukuManager] so its state Flow is
 * hydrated by the time the user reaches setup or home — avoids "Unknown" flash
 * on the Shizuku-status badges in Settings.
 *
 * Registered via `android:name=".ThruSparkApp"` in AndroidManifest.xml's
 * `<application>` tag.
 */
class ThruSparkApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ShizukuManager.init(this)
    }
}
