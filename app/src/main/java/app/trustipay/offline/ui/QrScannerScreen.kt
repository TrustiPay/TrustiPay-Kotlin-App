package app.trustipay.offline.ui

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.lifecycleScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.trustipay.offline.transport.qr.QrCodeScanner
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun QrScannerScreen(
    onQrScanned: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val scanner = remember { QrCodeScanner(context, lifecycleOwner) }
    var scanned by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { scanner.stop() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        scanner.startScanning(previewView)
                            .onEach { rawValue ->
                                if (!scanned) {
                                    scanned = true
                                    onQrScanned(rawValue)
                                }
                            }
                            .launchIn(lifecycleOwner.lifecycleScope)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Camera permission required to scan QR codes.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray,
                )
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        Text(
            text = "Align QR code within the frame",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
        )
    }
}
