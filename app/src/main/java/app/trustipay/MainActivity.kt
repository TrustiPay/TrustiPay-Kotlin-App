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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.trustipay.auth.ui.AuthNavEvent
import app.trustipay.auth.ui.AuthViewModel
import app.trustipay.auth.ui.LoginScreen
import app.trustipay.auth.ui.RegisterScreen
import app.trustipay.offline.OfflineFeatureFlagProvider
import app.trustipay.offline.sync.OfflineSyncWorker
import app.trustipay.offline.ui.OfflinePaymentsScreen
import app.trustipay.online.ui.HomeViewModel
import app.trustipay.ui.screens.HomeScreen
import app.trustipay.ui.screens.PaymentDraft
import app.trustipay.ui.screens.VoiceAssistantScreen
import app.trustipay.ui.theme.TrustiPayTheme
import com.cactus.CactusContextInitializer
import com.cactus.services.CactusConfig
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CactusContextInitializer.initialize(this)
        CactusConfig.isTelemetryEnabled = false
        enableEdgeToEdge()
        scheduleOfflineSync()
        setContent {
            TrustiPayTheme {
                TrustiPayApp()
            }
        }
    }

    private fun scheduleOfflineSync() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "offline_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<OfflineSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build(),
        )
    }
}

@Composable
fun TrustiPayApp() {
    val authViewModel: AuthViewModel = viewModel()
    val isLoggedIn = remember {
        mutableStateOf(AppContainer.tokenStore.load()?.isExpired() == false)
    }

    LaunchedEffect(Unit) {
        authViewModel.navEvents.collect { event ->
            when (event) {
                AuthNavEvent.NavigateToHome -> isLoggedIn.value = true
            }
        }
    }

    if (!isLoggedIn.value) {
        AuthFlow(
            authViewModel = authViewModel,
            onLoggedIn = { isLoggedIn.value = true },
        )
    } else {
        MainApp(
            authViewModel = authViewModel,
            onLoggedOut = { isLoggedIn.value = false },
        )
    }
}

@Composable
private fun AuthFlow(
    authViewModel: AuthViewModel,
    onLoggedIn: () -> Unit,
) {
    var showRegister by rememberSaveable { mutableStateOf(false) }

    if (showRegister) {
        RegisterScreen(
            onNavigateToLogin = { showRegister = false },
            viewModel = authViewModel,
        )
    } else {
        LoginScreen(
            onNavigateToRegister = { showRegister = true },
            viewModel = authViewModel,
        )
    }
}

@Composable
private fun MainApp(
    authViewModel: AuthViewModel,
    onLoggedOut: () -> Unit,
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showVoiceAssistant by rememberSaveable { mutableStateOf(false) }
    var voiceDraftEventId by rememberSaveable { mutableStateOf(0) }
    var latestVoiceDraft by remember { mutableStateOf<PaymentDraft?>(null) }
    val homeViewModel: HomeViewModel = viewModel()

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
                        icon = { Icon(imageVector = it.icon, contentDescription = it.label) },
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
                        onVoiceClick = { showVoiceAssistant = true },
                        homeViewModel = homeViewModel,
                    )
                    AppDestinations.OFFLINE -> OfflinePaymentsScreen(
                        modifier = Modifier.padding(innerPadding),
                        voiceDraft = latestVoiceDraft
                    )
                    AppDestinations.HISTORY -> PlaceholderScreen("Transaction History", Modifier.padding(innerPadding))
                    AppDestinations.PROFILE -> ProfileScreen(
                        modifier = Modifier.padding(innerPadding),
                        onLogout = {
                            authViewModel.logout()
                            onLoggedOut()
                        }
                    )
                }
            }
        }

        if (showVoiceAssistant) {
            VoiceAssistantScreen(
                onClose = { showVoiceAssistant = false },
                onPaymentDraft = { draft ->
                    voiceDraftEventId += 1
                    latestVoiceDraft = draft.copy(eventId = voiceDraftEventId)
                    currentDestination = if (draft.isOffline) AppDestinations.OFFLINE else AppDestinations.HOME
                    showVoiceAssistant = false
                }
            )
        }
    }
}

@Composable
fun PlaceholderScreen(name: String, modifier: Modifier = Modifier) {
    Text(text = name, modifier = modifier)
}

@Composable
fun ProfileScreen(modifier: Modifier = Modifier, onLogout: () -> Unit) {
    val token = AppContainer.tokenStore.load()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = token?.displayName ?: "Profile",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "User ID: ${token?.userId?.take(8) ?: "—"}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onLogout) {
            Text("Log Out")
        }
    }
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
        PlaceholderScreen("Preview")
    }
}
