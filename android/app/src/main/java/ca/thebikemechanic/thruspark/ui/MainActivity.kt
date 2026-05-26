package ca.thebikemechanic.thruspark.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ca.thebikemechanic.thruspark.data.UserPrefsStore
import kotlinx.coroutines.launch

private const val PREFS_NAME = "thruspark_prefs"
private const val KEY_ONBOARDING_DONE = "onboarding_complete"

/**
 * First-launch sequence:
 *   1. WalkthroughScreen (4 swipeable slides)        — walkthrough_done
 *   2. OnboardingScreen (system permissions)         — KEY_ONBOARDING_DONE
 *   3. ShizukuSetupScreen (skippable)                — shizuku_setup_done
 *   4. AppNav (Modes / Alarms / Settings tabs)
 *
 * Returning users land directly on AppNav. Each gate is checked in order so a
 * user who skipped Shizuku once won't be re-prompted but also won't get stuck
 * at the wrong step if they wipe app data.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThruSparkTheme {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                var permissionsDone by remember {
                    mutableStateOf(prefs.getBoolean(KEY_ONBOARDING_DONE, false))
                }
                val walkthroughDone by UserPrefsStore.walkthroughDoneFlow(this)
                    .collectAsStateWithLifecycle(initialValue = false)
                val shizukuDone by UserPrefsStore.shizukuSetupDoneFlow(this)
                    .collectAsStateWithLifecycle(initialValue = false)

                when {
                    !walkthroughDone -> WalkthroughScreen(
                        onDone = { markWalkthroughDone() }
                    )
                    !permissionsDone -> OnboardingScreen(onComplete = {
                        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
                        permissionsDone = true
                    })
                    !shizukuDone -> ShizukuOnboardingScreen(
                        onComplete = { markShizukuDone() },
                        onSkip = { markShizukuDone() }
                    )
                    else -> AppNav()
                }
            }
        }
    }

    private fun markWalkthroughDone() {
        lifecycleScope.launch { UserPrefsStore.setWalkthroughDone(this@MainActivity, true) }
    }

    private fun markShizukuDone() {
        lifecycleScope.launch { UserPrefsStore.setShizukuSetupDone(this@MainActivity, true) }
    }
}
