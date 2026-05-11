package ca.thebikemechanic.thruspark.ui

import androidx.compose.runtime.*

/**
 * Tiny state machine for the first-launch auth phase. Renders Welcome by
 * default; goes to SignUp / SignIn when chosen; calls onComplete when the
 * user either authenticates or skips.
 */
private enum class AuthStep { Welcome, SignUp, SignIn }

@Composable
fun AuthFlow(
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    var step by remember { mutableStateOf(AuthStep.Welcome) }

    when (step) {
        AuthStep.Welcome -> WelcomeScreen(
            onCreateAccount = { step = AuthStep.SignUp },
            onSignIn = { step = AuthStep.SignIn },
            onSkip = onSkip
        )
        AuthStep.SignUp -> SignUpScreen(
            onBack = { step = AuthStep.Welcome },
            onSuccess = onComplete
        )
        AuthStep.SignIn -> SignInScreen(
            onBack = { step = AuthStep.Welcome },
            onSuccess = onComplete
        )
    }
}
