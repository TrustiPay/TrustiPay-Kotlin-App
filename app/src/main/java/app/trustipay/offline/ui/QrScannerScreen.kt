package app.trustipay.offline.ui

import android.Manifest
import android.app.Activity
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Nfc
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.trustipay.offline.transport.nfc.NfcPaymentTransport
import app.trustipay.offline.transport.qr.QrCodeScanner
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Composable
fun QrScannerScreen(
    nfcTransport: NfcPaymentTransport? = null,
    onQrScanned: (String) -> Unit,
    onIouReceived: (app.trustipay.offline.domain.OfflineIOU) -> Unit = {},
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val scanner = remember { QrCodeScanner(context, lifecycleOwner) }
    var scanned by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        if (activity != null && nfcTransport != null) {
            nfcTransport.enableReaderMode(activity) { tag ->
                scope.launch {
                    val iou = nfcTransport.readIOUFromTag(tag)
                    if (iou != null && !scanned) {
                        scanned = true
                        onIouReceived(iou)
                    }
                }
            }
        }
        onDispose { 
            scanner.stop()
            if (activity != null) {
                nfcTransport?.disableReaderMode(activity)
            }
        }
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Nfc,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Align QR code or Tap NFC device",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
    }
}
