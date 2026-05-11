package ca.thebikemechanic.thruspark.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * First-launch screen. Three choices:
 *   - Create account (→ SignUpScreen)
 *   - Sign in (→ SignInScreen)
 *   - Skip for now — accounts are optional in v0.1, the app works without one
 *
 * The account is for cloud sync and feature unlocks in future versions; v0.1
 * has no online-required features.
 */
@Composable
fun WelcomeScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(60.dp))
            Text(
                "ThruSpark",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Battery for the long haul",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(48.dp))
            Text(
                "An account lets you sync profiles between devices later. v0.1 works fine without one — your data stays on your phone.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onCreateAccount,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Create account") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Sign in") }
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Skip for now") }
            Spacer(Modifier.height(12.dp))
            LegalFooterLinks(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
    }
}
