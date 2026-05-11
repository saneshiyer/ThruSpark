package ca.thebikemechanic.thruspark.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Refresh permissions every time the screen comes back into focus
    // (user returns from a system settings screen)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(32.dp))

        Text("Set up ThruSpark", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Grant the permissions below to enable battery-saving features. " +
                "Required ones are needed for basic operation.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        PermissionRow(
            label = "Modify System Settings",
            description = "Controls screen brightness and timeout — required",
            isGranted = state.hasWriteSettings,
            isRequired = true,
            onGrant = { viewModel.requestWriteSettings() }
        )

        Spacer(Modifier.height(16.dp))

        PermissionRow(
            label = "Do Not Disturb Access",
            description = "Silences notifications during your expedition — required",
            isGranted = state.hasDndAccess,
            isRequired = true,
            onGrant = { viewModel.requestDndAccess() }
        )

        Spacer(Modifier.height(16.dp))

        PermissionRow(
            label = "Notification Access",
            description = "Filters notifications to essential apps only — optional",
            isGranted = state.hasNotificationListener,
            isRequired = false,
            onGrant = { viewModel.requestNotificationListener() }
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onComplete,
            enabled = state.allRequiredGranted,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.allRequiredGranted) "Get Started" else "Grant required permissions above")
        }

        if (!state.allRequiredGranted) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Brightness control and Do Not Disturb are required for ThruSpark to work.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun PermissionRow(
    label: String,
    description: String,
    isGranted: Boolean,
    isRequired: Boolean,
    onGrant: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Lock,
                contentDescription = null,
                tint = if (isGranted)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(12.dp))

            if (!isGranted) {
                FilledTonalButton(onClick = onGrant) {
                    Text("Grant")
                }
            }
        }
    }
}
