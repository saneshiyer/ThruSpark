package ca.thebikemechanic.thruspark.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.thebikemechanic.thruspark.shizuku.ShizukuManager
import ca.thebikemechanic.thruspark.shizuku.ShizukuState

private const val BADGE_SHIZUKU = "Requires Shizuku"

/**
 * Custom profile builder. Exposes every field in ThruSparkProfile.
 * Tier 2 fields (radios except wifi/cellular/gps/bluetooth, grayscale,
 * refresh rate, app freeze) are visible but disabled with a badge.
 *
 * @param profileName when non-null, opens the form pre-filled for editing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuilderScreen(
    profileName: String? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onPickApps: () -> Unit = {},
    viewModel: BuilderViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val appLabels by viewModel.appLabels.collectAsStateWithLifecycle()
    val shizukuState by ShizukuManager.state.collectAsStateWithLifecycle()
    val tier2Unlocked = shizukuState == ShizukuState.Granted
    val tier2Badge: String? = if (tier2Unlocked) null else BADGE_SHIZUKU
    val pickerResult by AppPickerBus.result.collectAsStateWithLifecycle()

    LaunchedEffect(profileName) {
        if (profileName != null) viewModel.loadForEdit(profileName)
    }

    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onSaved()
    }

    // When the user finishes picking apps, fold the result into the form state.
    LaunchedEffect(pickerResult) {
        pickerResult?.let { viewModel.applyPickerResult(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "Edit profile" else "New profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isEdit) {
                        IconButton(onClick = { viewModel.deleteCurrent() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            NameField(state.name) { v -> viewModel.update { it.copy(name = v) } }

            SectionHeader("Display")
            SliderRow("Brightness", state.brightness, 0f..1f,
                display = "${(state.brightness * 100).toInt()}%"
            ) { v -> viewModel.update { it.copy(brightness = v) } }
            SwitchRow("Dark mode", state.darkMode) { v -> viewModel.update { it.copy(darkMode = v) } }
            SwitchRow("Grayscale", state.grayscale, badge = tier2Badge, enabled = tier2Unlocked) {
                v -> viewModel.update { it.copy(grayscale = v) }
            }
            DropdownRow("Refresh rate", state.refreshHz, listOf(60, 90, 120),
                display = { "$it Hz" }, badge = tier2Badge, enabled = tier2Unlocked) {
                v -> viewModel.update { it.copy(refreshHz = v) }
            }
            SliderRow("Screen timeout", state.timeoutSec.toFloat(), 15f..300f,
                display = "${state.timeoutSec} s", step = 15f
            ) { v -> viewModel.update { it.copy(timeoutSec = v.toInt()) } }

            SectionHeader("Radios", badge = tier2Badge)
            OnOffRow("Airplane mode", state.airplane, enabled = tier2Unlocked) {
                v -> viewModel.update { it.copy(airplane = v) }
            }
            OnOffRow("Wi-Fi", state.wifi, enabled = tier2Unlocked) {
                v -> viewModel.update { it.copy(wifi = v) }
            }
            OnOffRow("Cellular data", state.cellular, enabled = tier2Unlocked) {
                v -> viewModel.update { it.copy(cellular = v) }
            }
            OnOffRow("Force LTE", state.forceLte, enabled = tier2Unlocked) {
                v -> viewModel.update { it.copy(forceLte = v) }
            }
            OnOffRow("GPS", state.gps, enabled = tier2Unlocked) {
                v -> viewModel.update { it.copy(gps = v) }
            }
            OnOffRow("Bluetooth", state.bluetooth, enabled = tier2Unlocked) {
                v -> viewModel.update { it.copy(bluetooth = v) }
            }
            OnOffRow("NFC", state.nfc, enabled = tier2Unlocked) {
                v -> viewModel.update { it.copy(nfc = v) }
            }

            SectionHeader("Notifications")
            SwitchRow("Do not disturb", state.dndEnabled) { v -> viewModel.update { it.copy(dndEnabled = v) } }
            ChipGroupRow("Allow contacts",
                options = listOf("starred", "favorites", "all", "none"),
                selected = state.allowContacts
            ) { item -> viewModel.toggleSet(BuilderFormState::allowContacts, item) }

            SectionHeader("Specific apps")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (state.explicitPackages.isEmpty()) "No specific apps selected"
                        else "${state.explicitPackages.size} app${if (state.explicitPackages.size == 1) "" else "s"} selected",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        if (tier2Unlocked) "Listed apps stay active. Everything else gets paused when this profile is on."
                        else "Listed apps stay active. Pausing other apps requires Shizuku.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    viewModel.handOffToPicker()
                    onPickApps()
                }) {
                    Text(if (state.explicitPackages.isEmpty()) "Choose apps" else "Edit")
                }
            }
            if (state.explicitPackages.isNotEmpty()) {
                SelectedAppsList(packages = state.explicitPackages, labels = appLabels)
            }

            SectionHeader("Session")
            DropdownRow("Auto-deactivate after",
                value = state.durationHours,
                options = listOf<Int?>(null, 1, 4, 8, 12, 24),
                display = { it?.let { h -> "$h hour${if (h == 1) "" else "s"}" } ?: "Off" }
            ) { v -> viewModel.update { it.copy(durationHours = v) } }

            Spacer(Modifier.height(20.dp))

            state.errorMessage?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = { viewModel.save() },
                    enabled = state.isValid && !state.saving,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.saving) CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    else Text(if (state.isEdit) "Save changes" else "Save profile")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Helper composables ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Profile name") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
}

@Composable
private fun SectionHeader(title: String, badge: String? = null) {
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        badge?.let {
            Spacer(Modifier.width(8.dp))
            ShizukuBadge(it)
        }
    }
    Spacer(Modifier.height(4.dp))
    HorizontalDivider()
    Spacer(Modifier.height(4.dp))
}

/**
 * Read-only chips listing each selected package's human-readable label.
 * Falls back to the package name if the label hasn't loaded yet, or if the
 * app was uninstalled after being picked (so the user can still recognize it).
 */
@Composable
private fun SelectedAppsList(packages: Set<String>, labels: Map<String, String>) {
    val display = packages.map { labels[it] ?: it }.sortedBy { it.lowercase() }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        display.forEach { name ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ShizukuBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    badge: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            badge?.let {
                Spacer(Modifier.height(2.dp))
                ShizukuBadge(it)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/**
 * Two-segment "On / Off" control. Replaces SwitchRow for radio toggles where
 * the user benefits from explicit labels (rather than "is the toggle on or
 * off? hard to tell at a glance").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnOffRow(
    label: String,
    value: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = !value,
                onClick = { if (enabled) onChange(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                enabled = enabled
            ) { Text("Off") }
            SegmentedButton(
                selected = value,
                onClick = { if (enabled) onChange(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                enabled = enabled
            ) { Text("On") }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    step: Float = 0f,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(display, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val steps = if (step > 0f) ((range.endInclusive - range.start) / step).toInt() - 1 else 0
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = if (steps < 0) 0 else steps
        )
    }
}

@Composable
private fun <T> DropdownRow(
    label: String,
    value: T,
    options: List<T>,
    display: (T) -> String,
    badge: String? = null,
    enabled: Boolean = true,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            badge?.let {
                Spacer(Modifier.height(2.dp))
                ShizukuBadge(it)
            }
        }
        Box {
            TextButton(onClick = { if (enabled) expanded = true }, enabled = enabled) {
                Text(display(value))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(display(opt)) },
                        onClick = { onSelect(opt); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipGroupRow(
    label: String,
    options: List<String>,
    selected: Set<String>,
    enabled: Boolean = true,
    onToggle: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { opt ->
                FilterChip(
                    selected = opt in selected,
                    onClick = { if (enabled) onToggle(opt) },
                    enabled = enabled,
                    label = { Text(opt, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    horizontalArrangement: Arrangement.Horizontal,
    verticalArrangement: Arrangement.Vertical,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}
