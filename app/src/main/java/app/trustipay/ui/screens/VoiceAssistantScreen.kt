package app.trustipay.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trustipay.ui.theme.TrustiPayPrimary
import app.trustipay.ui.theme.TrustiPaySecondary
import app.trustipay.ui.theme.TrustiPayTertiary
import app.trustipay.voice.VoiceAssistantUiState
import app.trustipay.voice.VoiceAssistantViewModel
import app.trustipay.voice.VoiceCaptureState
import app.trustipay.voice.VoiceModelState

@Composable
fun VoiceAssistantScreen(
    onClose: () -> Unit = {},
    viewModel: VoiceAssistantViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val hasAudioPermission =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionResult(granted)
        if (granted) {
            viewModel.startListening(hasMicrophonePermission = true)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelActiveWork()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrustiPayPrimary.copy(alpha = 0.95f))
    ) {
        IconButton(
            onClick = {
                viewModel.cancelActiveWork()
                onClose()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = screenTitle(uiState),
                color = TrustiPayTertiary,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            VoiceRippleEffect(
                isListening = uiState.captureState == VoiceCaptureState.Listening ||
                    uiState.captureState == VoiceCaptureState.LiveTranscribing
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (uiState.modelState == VoiceModelState.Ready) {
                TranscriptPanel(uiState)

                Spacer(modifier = Modifier.height(28.dp))

                CaptureControls(
                    uiState = uiState,
                    onStart = {
                        if (hasAudioPermission) {
                            viewModel.startListening(hasMicrophonePermission = true)
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStop = viewModel::stopListening,
                )
            } else {
                ModelSetupPanel(
                    uiState = uiState,
                    onDownload = viewModel::downloadModel,
                    onRetry = viewModel::refreshModelState,
                    onDelete = viewModel::deleteModel
                )
            }
        }

        StatusFooter(
            uiState = uiState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}

@Composable
private fun TranscriptPanel(uiState: VoiceAssistantUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.10f),
        contentColor = Color.White,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = uiState.transcript.ifBlank { "Tap Start and speak in Sinhala or English." },
                color = Color.White,
                style = if (uiState.transcript.length > 120) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.headlineSmall
                },
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = uiState.languageLabel,
                color = TrustiPayTertiary,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = uiState.errorMessage ?: uiState.statusMessage,
        color = Color.White.copy(alpha = 0.72f),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun CaptureControls(
    uiState: VoiceAssistantUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Button(
        onClick = {
            if (uiState.captureState == VoiceCaptureState.Listening ||
                uiState.captureState == VoiceCaptureState.LiveTranscribing
            ) {
                onStop()
            } else {
                onStart()
            }
        },
        enabled = uiState.canRequestRecording ||
            uiState.captureState == VoiceCaptureState.Listening ||
            uiState.captureState == VoiceCaptureState.LiveTranscribing,
        colors = ButtonDefaults.buttonColors(containerColor = TrustiPayTertiary),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Icon(
            imageVector = if (
                uiState.captureState == VoiceCaptureState.Listening ||
                uiState.captureState == VoiceCaptureState.LiveTranscribing
            ) {
                Icons.Default.Stop
            } else {
                Icons.Default.Mic
            },
            contentDescription = null
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = if (
                uiState.captureState == VoiceCaptureState.Listening ||
                uiState.captureState == VoiceCaptureState.LiveTranscribing
            ) {
                "Stop"
            } else {
                "Start voice request"
            },
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ModelSetupPanel(
    uiState: VoiceAssistantUiState,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.10f),
        contentColor = Color.White,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = uiState.modelName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = uiState.errorMessage ?: uiState.statusMessage,
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (uiState.isDeviceSupported) {
                    "The model is stored in app-specific storage and excluded from backup."
                } else {
                    "This app did not start native transcription, so it will not crash."
                },
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )

            if (uiState.modelState == VoiceModelState.Downloading ||
                uiState.modelState == VoiceModelState.Initializing
            ) {
                CircularProgressIndicator(
                    color = TrustiPayTertiary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(28.dp)
                )
            }

            if (uiState.canDownloadModel) {
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = TrustiPayTertiary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Download voice model", fontWeight = FontWeight.Bold)
                }
            }

            if (uiState.modelState == VoiceModelState.Failed) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Retry setup")
                }
            }

            if (uiState.canDeleteModel) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Delete voice model")
                }
            }
        }
    }
}

@Composable
private fun StatusFooter(
    uiState: VoiceAssistantUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (uiState.isBusy || uiState.captureState == VoiceCaptureState.LiveTranscribing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = TrustiPayTertiary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.size(8.dp))
        }
        Text(
            text = uiState.liveTranscriptionLabel,
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun VoiceRippleEffect(isListening: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(contentAlignment = Alignment.Center) {
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(TrustiPayTertiary.copy(alpha = 0.3f))
            )
        }
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(TrustiPayTertiary, TrustiPaySecondary)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

private fun screenTitle(uiState: VoiceAssistantUiState): String =
    when {
        uiState.modelState == VoiceModelState.Missing -> "Local voice setup"
        uiState.modelState == VoiceModelState.Downloading -> "Downloading voice model"
        uiState.modelState == VoiceModelState.Initializing -> "Preparing local STT"
        uiState.captureState == VoiceCaptureState.Listening -> "Listening"
        uiState.captureState == VoiceCaptureState.LiveTranscribing -> "Live transcribing"
        uiState.captureState == VoiceCaptureState.Finalizing -> "Recognizing speech"
        uiState.captureState == VoiceCaptureState.Error ||
            uiState.modelState == VoiceModelState.Failed -> "Voice assistant needs attention"
        else -> "TrustiPay Local Assistant"
    }

private fun Modifier.scale(scale: Float) = this.graphicsLayer(scaleX = scale, scaleY = scale)
