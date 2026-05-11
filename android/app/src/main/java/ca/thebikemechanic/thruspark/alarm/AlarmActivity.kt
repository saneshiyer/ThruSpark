package ca.thebikemechanic.thruspark.alarm

import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.thebikemechanic.thruspark.ui.ThruSparkTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Shown when the alarm fires — visible on the lock screen.
 *
 * Layout:
 *   - Large time display
 *   - Alarm label
 *   - SNOOZE button (reschedules for snooze_minutes from now)
 *   - DISMISS button (stops sound, finishes activity, deactivates overnight profile)
 *
 * Window flags make this show over the lock screen and wake the display even
 * when the phone is asleep (which is exactly what you want for an alarm).
 */
class AlarmActivity : ComponentActivity() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val label = intent.getStringExtra(AlarmReceiver.EXTRA_LABEL) ?: "Wake up"
        val snoozeMinutes = intent.getIntExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, 9)

        // Keep screen on and show over lock screen
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        // Acquire a partial wake lock so CPU stays alive if somehow we get here
        // before the screen is fully on
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "ThruSpark:AlarmWakeLock"
            ).also { it.acquire(10 * 60 * 1000L /* 10 minutes max */) }

        // Start playing alarm sound
        AlarmSoundPlayer.play(this)

        setContent {
            ThruSparkTheme {
                AlarmScreen(
                    label = label,
                    onDismiss = { handleDismiss() },
                    onSnooze = { handleSnooze(snoozeMinutes) }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AlarmSoundPlayer.stop()
        wakeLock?.release()
    }

    private fun handleDismiss() {
        AlarmSoundPlayer.stop()
        // Deactivate the overnight profile now that the user is awake
        ca.thebikemechanic.thruspark.engine.ProfileEngine.deactivate(this)
        finish()
    }

    private fun handleSnooze(snoozeMinutes: Int) {
        AlarmSoundPlayer.stop()
        // Reschedule for snoozeMinutes from now using a direct epoch offset
        val snoozeTriggerMs = System.currentTimeMillis() + snoozeMinutes * 60 * 1000L
        val cal = Calendar.getInstance().apply { timeInMillis = snoozeTriggerMs }
        val snoozeTime = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        AlarmScheduler.schedule(this, snoozeTime, "Snooze")
        finish()
    }
}

// ── Compose UI ───────────────────────────────────────────────────────────────

@Composable
private fun AlarmScreen(
    label: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    // Update the displayed time every second
    var currentTime by remember { mutableStateOf(formattedTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            currentTime = formattedTime()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),   // near-black — eyes are adjusting
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Big time display
            Text(
                text = currentTime,
                fontSize = 72.sp,
                fontWeight = FontWeight.Light,
                color = Color.White
            )

            // Label
            Text(
                text = label,
                fontSize = 20.sp,
                color = Color(0xFFAAAAAA)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier.size(width = 130.dp, height = 56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFAAAAAA))
                ) {
                    Text("Snooze", fontSize = 16.sp)
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.size(width = 130.dp, height = 56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),  // green — positive action
                        contentColor = Color.White
                    )
                ) {
                    Text("Dismiss", fontSize = 16.sp)
                }
            }
        }
    }
}

private fun formattedTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
