package ca.thebikemechanic.thruspark.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import ca.thebikemechanic.thruspark.R
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.thebikemechanic.thruspark.shizuku.ShizukuManager
import ca.thebikemechanic.thruspark.shizuku.ShizukuState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
private const val GITHUB_RELEASES = "https://github.com/RikkaApps/Shizuku/releases"

enum class OnboardingStep(val number: Int, val total: Int = 5) {
    EnableDevMode(1),
    EnableWirelessDebugging(2),
    InstallShizuku(3),
    PairAndStartShizuku(4),
    GrantPermission(5),
    Done(6, total = 5)
}

/**
 * Multi-step Shizuku onboarding. Replaces the older single-screen ShizukuSetupScreen
 * for the first-launch flow. Adapts based on detected state — e.g. if Shizuku is
 * already installed, jumps to the pair/start step.
 *
 * Auto-detects each step's completion via:
 *   - Settings.Global.DEVELOPMENT_SETTINGS_ENABLED (dev mode toggle)
 *   - PackageManager (Shizuku app installed)
 *   - ShizukuManager.state (binder + permission grant)
 *   - Wireless debugging: no readable system flag, so the user confirms manually
 *
 * Polls every 1.5s while the screen is visible so changes the user makes in
 * Settings appear immediately when they return.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShizukuOnboardingScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    viewModel: ShizukuOnboardingViewModel = viewModel()
) {
    val context = LocalContext.current
    val step by viewModel.currentStep.collectAsState()
    val shizukuState by ShizukuManager.state.collectAsStateWithLifecycle()
    val devModeOn by viewModel.devModeOn.collectAsState()
    val wirelessConfirmed by viewModel.wirelessDebuggingConfirmed.collectAsState()

    // Poll state so user doesn't have to tap "re-check" after returning from Settings
    LaunchedEffect(Unit) {
        while (isActive) {
            viewModel.recheckState(context)
            delay(1500)
        }
    }

    LaunchedEffect(step) { if (step == OnboardingStep.Done) onComplete() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Set up Shizuku") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            StepIndicator(currentStep = step.number, totalSteps = OnboardingStep.EnableDevMode.total)
            Spacer(Modifier.height(12.dp))

            // Video walkthrough shortcut — opens YouTube (or browser) for users
            // who'd rather watch than read. Covers all 5 steps incl. GitHub install.
            OutlinedButton(
                onClick = { openUrl(context, URL_SETUP_VIDEO) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Watch the 2-min setup video")
            }
            Spacer(Modifier.height(20.dp))

            when (step) {
                OnboardingStep.EnableDevMode -> EnableDevModeStep(
                    devModeOn = devModeOn,
                    onOpenAbout = { openAboutPhone(context) },
                    onOpenDevOptions = { openDevOptions(context) }
                )
                OnboardingStep.EnableWirelessDebugging -> WirelessDebuggingStep(
                    confirmed = wirelessConfirmed,
                    onOpenDevOptions = { openDevOptions(context) },
                    onConfirm = { viewModel.confirmWirelessDebugging() }
                )
                OnboardingStep.InstallShizuku -> InstallShizukuStep(
                    onOpenPlay = { openPlayStore(context) },
                    onOpenGitHub = { openUrl(context, GITHUB_RELEASES) }
                )
                OnboardingStep.PairAndStartShizuku -> PairAndStartStep(
                    state = shizukuState,
                    onOpenShizuku = { openShizuku(context) }
                )
                OnboardingStep.GrantPermission -> GrantPermissionStep(
                    state = shizukuState,
                    onRequest = { ShizukuManager.requestPermission() }
                )
                OnboardingStep.Done -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            Spacer(Modifier.height(28.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("Skip Shizuku setup — use Tier 1 only")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int, totalSteps: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Step $currentStep of $totalSteps",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(totalSteps) { i ->
                val n = i + 1
                val isComplete = n < currentStep
                val isCurrent = n == currentStep
                Box(
                    modifier = Modifier
                        .size(if (isCurrent) 12.dp else 10.dp)
                        .background(
                            color = when {
                                isComplete -> MaterialTheme.colorScheme.primary
                                isCurrent -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun EnableDevModeStep(
    devModeOn: Boolean,
    onOpenAbout: () -> Unit,
    onOpenDevOptions: () -> Unit
) {
    val oemPath = remember { detectOemBuildNumberPath() }
    StepCard(
        title = "Enable Developer mode",
        body = "On Android, Developer mode is hidden behind a hidden gesture. Tap the row below 7 times until you see a 'You are now a developer' toast."
    )
    Spacer(Modifier.height(12.dp))

    // OEM-specific path. Build number lives in different places on Samsung,
    // Xiaomi, OnePlus etc. — see detectOemBuildNumberPath() for the full list.
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "On ${oemPath.oemName}:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                oemPath.path,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    StepScreenshot(R.drawable.ss_step1_build_number, "Build number row in About phone (Pixel layout — others vary)")
    Spacer(Modifier.height(12.dp))
    Button(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
        Text("Open About phone")
    }
    if (devModeOn) {
        Spacer(Modifier.height(12.dp))
        SuccessBanner("Developer mode is on — auto-advancing")
    } else {
        Spacer(Modifier.height(8.dp))
        Text(
            "After tapping 7 times, this screen will auto-advance.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The "tap Build number 7 times" gesture is hidden in different sub-menus
 * across Android skins. Some OEMs even rename the row (Xiaomi MIUI calls it
 * "MIUI version"; HyperOS calls it "OS version"). We detect the device's
 * manufacturer at runtime and surface the correct path so the user doesn't
 * have to guess.
 *
 * Falls back to the AOSP path for unrecognized manufacturers — this is
 * correct for stock Android and most smaller OEMs (Sony, Motorola, LG).
 */
private data class OemBuildNumberPath(val oemName: String, val path: String)

private fun detectOemBuildNumberPath(): OemBuildNumberPath {
    return when (android.os.Build.MANUFACTURER.lowercase()) {
        "samsung" -> OemBuildNumberPath(
            "Samsung One UI",
            "Settings → About phone → Software information → Build number (tap 7 times)"
        )
        "xiaomi", "redmi", "poco" -> OemBuildNumberPath(
            "Xiaomi / Redmi / POCO",
            "Settings → About phone → tap 'MIUI version' or 'OS version' 7 times " +
                "(Xiaomi renames the row; tap whichever shows your OS version number)"
        )
        "oneplus" -> OemBuildNumberPath(
            "OnePlus OxygenOS",
            "Settings → About device → Version → Build number (tap 7 times). " +
                "On older OxygenOS: Settings → About phone → Build number directly."
        )
        "huawei", "honor" -> OemBuildNumberPath(
            "Huawei / Honor",
            "Settings → System & updates → About phone → Build number (tap 7 times)"
        )
        "oppo", "realme" -> OemBuildNumberPath(
            "Oppo ColorOS / realme UI",
            "Settings → About device → Version → Build number (tap 7 times)"
        )
        "vivo" -> OemBuildNumberPath(
            "Vivo Funtouch / OriginOS",
            "Settings → About phone → Software version → Build number (tap 7 times)"
        )
        "google" -> OemBuildNumberPath(
            "Google Pixel",
            "Settings → About phone → Build number (tap 7 times)"
        )
        "motorola" -> OemBuildNumberPath(
            "Motorola",
            "Settings → About phone → Build number (tap 7 times)"
        )
        "sony" -> OemBuildNumberPath(
            "Sony Xperia",
            "Settings → About phone → Build number (tap 7 times)"
        )
        "lge", "lg" -> OemBuildNumberPath(
            "LG",
            "Settings → About phone → Software info → Build number (tap 7 times)"
        )
        else -> OemBuildNumberPath(
            "your phone",
            "Settings → About phone → Build number (tap 7 times). " +
                "If you don't see Build number, look in 'Software information' or 'Version' sub-menus."
        )
    }
}

@Composable
private fun WirelessDebuggingStep(
    confirmed: Boolean,
    onOpenDevOptions: () -> Unit,
    onConfirm: () -> Unit
) {
    StepCard(
        title = "Turn on Wireless debugging",
        body = "Open Developer options and find 'Wireless debugging.' Tap the row, then enable the toggle at the top. You'll see a confirmation prompt — tap Allow."
    )
    Spacer(Modifier.height(12.dp))
    StepScreenshot(R.drawable.ss_step2_wireless_debugging, "USB debugging and Wireless debugging toggles in Developer options")
    Spacer(Modifier.height(12.dp))
    Button(onClick = onOpenDevOptions, modifier = Modifier.fillMaxWidth()) {
        Text("Open Developer options")
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
        Text(if (confirmed) "Wireless debugging confirmed" else "I've enabled it — continue")
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Android doesn't expose this setting to apps, so you'll need to confirm manually.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun InstallShizukuStep(
    onOpenPlay: () -> Unit,
    onOpenGitHub: () -> Unit
) {
    StepCard(
        title = "Install Shizuku",
        body = "Shizuku is a separate, free, open-source app. Install it from the Play Store, then come back here."
    )
    Spacer(Modifier.height(12.dp))
    Button(onClick = onOpenPlay, modifier = Modifier.fillMaxWidth()) {
        Text("Open Play Store")
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onOpenGitHub, modifier = Modifier.fillMaxWidth()) {
        Text("Or download from GitHub")
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "If you have a newer version of Android you may need to install Shizuku directly from GitHub.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PairAndStartStep(
    state: ShizukuState,
    onOpenShizuku: () -> Unit
) {
    StepCard(
        title = "Pair Shizuku and start the service",
        body = "Open Shizuku. It will guide you through pairing with your phone's Wireless debugging using a code. Once paired, tap the big 'Start' button at the top of Shizuku."
    )
    Spacer(Modifier.height(12.dp))
    StepScreenshot(R.drawable.ss_step4_shizuku_start, "Shizuku 'Start via Wireless debugging' card with Pairing and Start buttons")
    Spacer(Modifier.height(12.dp))
    Button(onClick = onOpenShizuku, modifier = Modifier.fillMaxWidth()) {
        Text("Open Shizuku")
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Shizuku has detailed instructions inside the app. When the service is running, this screen will auto-advance.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (state == ShizukuState.BinderUnavailable) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Status: Shizuku is installed but the service hasn't started yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun GrantPermissionStep(
    state: ShizukuState,
    onRequest: () -> Unit
) {
    StepCard(
        title = "Grant ThruSpark access",
        body = "Tap below — Shizuku will pop up asking whether to give ThruSpark access. Choose 'Allow always.'"
    )
    Spacer(Modifier.height(12.dp))
    Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
        Text("Request permission")
    }
    if (state == ShizukuState.PermanentlyDenied) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Permission was denied. Open Shizuku and reset its app permissions, then tap 'Request permission' again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    if (state == ShizukuState.Granted) {
        Spacer(Modifier.height(20.dp))
        WirelessDebuggingSecurityTip()
    }
}

/**
 * v0.5: post-grant security recommendation. Wireless debugging being on
 * means a paired device on your local network could potentially connect to
 * your phone, so security-minded users may want to turn it off after Shizuku
 * is set up. The tradeoff: you'll need to flip it back on briefly after each
 * reboot before Shizuku will start. We surface both options honestly so the
 * user can pick.
 */
@Composable
private fun WirelessDebuggingSecurityTip() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Optional: tighten security",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Shizuku is now connected. You can either leave Wireless debugging on (more convenient — Shizuku just works after a reboot) or turn it off (more secure — but you'll need to re-enable it briefly and restart Shizuku each time you reboot your phone).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "There's no wrong answer. If you don't reboot often, turning it off costs almost nothing. If you reboot daily, leaving it on is reasonable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Renders a real screenshot drawable framed with a subtle border and rounded
 * corners. For Shizuku setup steps where the relevant Settings panel UI is
 * shown so the user can match what they see on their phone.
 */
@Composable
private fun StepScreenshot(drawableRes: Int, contentDescription: String) {
    Image(
        painter = painterResource(drawableRes),
        contentDescription = contentDescription,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            )
    )
}

/**
 * Visible placeholder for steps that don't yet have a screenshot — currently
 * unused but kept for the rare case a step is added later that needs a stub.
 */
@Composable
@Suppress("unused")
private fun ScreenshotPlaceholder(description: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp, max = 220.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Screenshot",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StepCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SuccessBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary)
    }
}

// ── Deep links ────────────────────────────────────────────────────────────

private fun openAboutPhone(context: Context) {
    // Android 13+ (API 33) added a more-direct ACTION_BUILD_INFO_SETTINGS that
    // skips straight to the build-info screen on Pixel. OEMs may not implement
    // it, so we fall back to the general About-phone screen.
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val direct = runCatching {
            context.startActivity(
                Intent("android.settings.BUILD_INFO_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
        if (direct) return
    }
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun openDevOptions(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.recoverCatching {
        // Fallback to general Settings if dev options can't be opened directly
        context.startActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun openPlayStore(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PKG"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        openUrl(context, "https://play.google.com/store/apps/details?id=$SHIZUKU_PKG")
    }
}

private fun openShizuku(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PKG)
        ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PKG"))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

// ── ViewModel ─────────────────────────────────────────────────────────────

class ShizukuOnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val _devModeOn = MutableStateFlow(false)
    val devModeOn: StateFlow<Boolean> = _devModeOn.asStateFlow()

    private val _wirelessDebuggingConfirmed = MutableStateFlow(false)
    val wirelessDebuggingConfirmed: StateFlow<Boolean> = _wirelessDebuggingConfirmed.asStateFlow()

    private val _currentStep = MutableStateFlow(OnboardingStep.EnableDevMode)
    val currentStep: StateFlow<OnboardingStep> = _currentStep.asStateFlow()

    init {
        recheckState(application)
    }

    fun confirmWirelessDebugging() {
        _wirelessDebuggingConfirmed.value = true
        recomputeStep(getApplication())
    }

    fun recheckState(context: Context) {
        // Dev mode flag (readable via Settings.Global)
        val devOn = runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
            ) == 1
        }.getOrDefault(false)
        _devModeOn.value = devOn

        // Refresh Shizuku binder state
        ShizukuManager.refresh(context)

        recomputeStep(context)
    }

    private fun recomputeStep(context: Context) {
        val shizukuInstalled = isShizukuInstalled(context)
        val shizukuState = ShizukuManager.state.value

        _currentStep.value = when {
            shizukuState == ShizukuState.Granted -> OnboardingStep.Done
            shizukuInstalled && shizukuState == ShizukuState.NeedsPermission -> OnboardingStep.GrantPermission
            shizukuInstalled && shizukuState == ShizukuState.PermanentlyDenied -> OnboardingStep.GrantPermission
            shizukuInstalled -> OnboardingStep.PairAndStartShizuku
            !_devModeOn.value -> OnboardingStep.EnableDevMode
            !_wirelessDebuggingConfirmed.value -> OnboardingStep.EnableWirelessDebugging
            else -> OnboardingStep.InstallShizuku
        }
    }

    private fun isShizukuInstalled(context: Context): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
