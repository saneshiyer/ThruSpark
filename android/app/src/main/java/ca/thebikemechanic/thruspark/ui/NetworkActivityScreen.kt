package ca.thebikemechanic.thruspark.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.thebikemechanic.thruspark.data.NetworkActivityEntry
import ca.thebikemechanic.thruspark.data.NetworkActivityStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → Network activity. Shows every network call ThruSpark has made
 * (capped at last 50). The pitch: an empty list is the most credible "we
 * don't phone home" proof we can offer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkActivityScreen(
    onBack: () -> Unit,
    viewModel: NetworkActivityViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network activity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.entries.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clear() }) { Text("Clear") }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Text(
                "Every network request ThruSpark has made, newest first. We log only sign-in / sign-up / password reset calls to Supabase — there's no analytics, no tracking, no telemetry. URL parameters are stripped before logging.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )

            HorizontalDivider()

            if (state.entries.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(state.entries) { entry ->
                        ActivityRow(entry)
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("No network activity", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "ThruSpark hasn't made any network calls yet. If you skip account creation and don't watch the setup video, this list will stay empty.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        )
    }
}

@Composable
private fun ActivityRow(entry: NetworkActivityEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = methodColor(entry.method).copy(alpha = 0.15f)
        ) {
            Text(
                entry.method,
                style = MaterialTheme.typography.labelSmall,
                color = methodColor(entry.method),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.url, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(2.dp))
            Text(
                "${formatTimestamp(entry.timestampMs)} · HTTP ${entry.statusCode} · ${entry.durationMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun methodColor(method: String): Color = when (method) {
    "GET" -> MaterialTheme.colorScheme.primary
    "POST" -> MaterialTheme.colorScheme.tertiary
    "DELETE" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatTimestamp(ms: Long): String {
    val format = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())
    return format.format(Date(ms))
}

// ── ViewModel ─────────────────────────────────────────────────────────────

data class NetworkActivityUiState(
    val entries: List<NetworkActivityEntry> = emptyList()
)

class NetworkActivityViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(NetworkActivityUiState())
    val state: StateFlow<NetworkActivityUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            NetworkActivityStore.activityFlow(application).collect { entries ->
                _state.update { it.copy(entries = entries) }
            }
        }
    }

    fun clear() {
        viewModelScope.launch { NetworkActivityStore.clear(getApplication()) }
    }
}
