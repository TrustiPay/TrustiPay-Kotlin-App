package app.trustipay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.cactus.CactusContextInitializer
import com.cactus.services.CactusConfig
import app.trustipay.offline.OfflineFeatureFlagProvider
import app.trustipay.offline.ui.OfflinePaymentsScreen
import app.trustipay.ui.screens.HomeScreen
import app.trustipay.ui.screens.PaymentDraft
import app.trustipay.ui.screens.VoiceAssistantScreen
import app.trustipay.ui.theme.TrustiPayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CactusContextInitializer.initialize(this)
        CactusConfig.isTelemetryEnabled = false
        enableEdgeToEdge()
        setContent {
            TrustiPayTheme {
                TrustiPayApp()
            }
        }
    }
}

@Composable
fun TrustiPayApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showVoiceAssistant by rememberSaveable { mutableStateOf(false) }
    var voiceDraftEventId by rememberSaveable { mutableStateOf(0) }
    var latestVoiceDraft by remember { mutableStateOf<PaymentDraft?>(null) }
    val visibleDestinations = remember {
        AppDestinations.entries.filter { destination ->
            !destination.requiresOfflineFlag || OfflineFeatureFlagProvider.current.offlinePaymentsEnabled
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                visibleDestinations.forEach {
                    item(
                        icon = {
                            Icon(
                                imageVector = it.icon,
                                contentDescription = it.label
                            )
                        },
                        label = { Text(it.label) },
                        selected = it == currentDestination,
                        onClick = { currentDestination = it }
                    )
                }
            }
        ) {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                when (currentDestination) {
                    AppDestinations.HOME -> HomeScreen(
                        modifier = Modifier.padding(innerPadding),
                        voiceDraft = latestVoiceDraft,
                        onVoiceClick = { showVoiceAssistant = true }
                    )
                    AppDestinations.OFFLINE -> OfflinePaymentsScreen(
                        modifier = Modifier.padding(innerPadding),
                        voiceDraft = latestVoiceDraft
                    )
                    AppDestinations.HISTORY -> PlaceholderScreen("Transaction History", Modifier.padding(innerPadding))
                    AppDestinations.PROFILE -> PlaceholderScreen("User Profile", Modifier.padding(innerPadding))
                }
            }
        }

        if (showVoiceAssistant) {
            VoiceAssistantScreen(
                onClose = { showVoiceAssistant = false },
                onPaymentDraft = { draft ->
                    voiceDraftEventId += 1
                    latestVoiceDraft = draft.copy(eventId = voiceDraftEventId)
                    if (draft.isOffline) {
                        currentDestination = AppDestinations.OFFLINE
                    } else {
                        currentDestination = AppDestinations.HOME
                    }
                    showVoiceAssistant = false
                }
            )
        }
    }
}

@Composable
fun PlaceholderScreen(name: String, modifier: Modifier = Modifier) {
    Text(
        text = name,
        modifier = modifier
    )
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
    val requiresOfflineFlag: Boolean = false,
) {
    HOME("Home", Icons.Default.Home),
    OFFLINE("Offline", Icons.Default.QrCodeScanner, requiresOfflineFlag = true),
    HISTORY("History", Icons.Default.History),
    PROFILE("Profile", Icons.Default.Person),
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    TrustiPayTheme {
        TrustiPayApp()
    }
}
