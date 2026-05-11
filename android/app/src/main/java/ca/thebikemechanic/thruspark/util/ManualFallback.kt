package ca.thebikemechanic.thruspark.util

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings

/**
 * No-Shizuku fallbacks: deep-link the user into the system Settings page for
 * each toggle ThruSpark can't flip itself, and read the live state so the UI
 * can show whether the user has actually enabled it.
 *
 * Both [Settings.Global.AIRPLANE_MODE_ON] writes and
 * [PowerManager.setPowerSaveModeEnabled] are signature-protected — those are
 * exactly the operations Shizuku exists to bypass — so the user has to do
 * the actual toggling themselves. We just put them one tap away.
 */
object ManualFallback {

    fun isAirplaneOn(context: Context): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    } catch (e: Exception) {
        false
    }

    fun isBatterySaverOn(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isPowerSaveMode
    }

    fun openAirplaneSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun openBatterySaverSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
