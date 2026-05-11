package ca.thebikemechanic.thruspark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class Slide(val title: String, val body: String)

// v0.4: trust-focused slide deck. Explains what the app does, why it needs
// elevated permissions, and where data lives.
// Order matters — start with what / why, then trust mechanics.
private val slides = listOf(
    Slide(
        "What ThruSpark does",
        "ThruSpark gives you one-tap access to system-level power controls on your Android device — the same kind of permissions normally reserved for manufacturer (OEM) apps. Like using the Admin account on your computer, this comes with risks we manage together. ThruSpark is built for outdoor enthusiasts whose best privacy strategy is staying off the network in the first place."
    ),
    Slide(
        "Why we use Shizuku",
        "These power modes need permissions Android doesn't let regular apps grant themselves. Shizuku is a separate, free, open-source app that bridges that gap safely. Both ThruSpark and Shizuku are open-source and available on the Google Play Store, where Google Play Protect scans them for malware."
    ),
    Slide(
        "Your data stays with you",
        "By default ThruSpark works fully offline. Custom profiles, alarms, and settings stay in private storage on your device — protected by Android's full-disk encryption. If you sign in, only your email is sent off-device, and it's stored locally using Android's encrypted-key storage. Cloud sync between devices is optional and off by default."
    )
)

@Composable
fun WalkthroughScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == slides.size - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp, bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDone) { Text("Skip") }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    slides[page].title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    slides[page].body,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Page indicator dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(slides.size) { i ->
                val active = i == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 10.dp else 8.dp)
                        .background(
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                )
            }
        }

        Button(
            onClick = {
                if (isLast) onDone()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
        ) {
            Text(if (isLast) "Get started" else "Next")
        }
    }
}
