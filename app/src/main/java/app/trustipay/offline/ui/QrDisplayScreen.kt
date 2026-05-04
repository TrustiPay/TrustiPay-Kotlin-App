package app.trustipay.offline.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trustipay.offline.transport.qr.QrPaymentTransport
import kotlinx.coroutines.delay

@Composable
fun QrDisplayScreen(
    transport: QrPaymentTransport,
    amountText: String,
    onClose: () -> Unit,
) {
    val bitmaps by transport.outgoingBitmaps.collectAsState()
    val currentIndex by transport.currentBitmapIndex.collectAsState()

    LaunchedEffect(bitmaps.size) {
        if (bitmaps.size > 1) {
            while (true) {
                delay(800)
                transport.advanceBitmapIndex()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Scan to Pay",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = amountText,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(32.dp))

            if (bitmaps.isEmpty()) {
                Text("Generating QR code…", color = Color.Gray)
            } else {
                val bitmap = bitmaps[currentIndex.coerceIn(0, bitmaps.lastIndex)]
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Payment QR code",
                    modifier = Modifier.size(300.dp),
                )
                if (bitmaps.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Frame ${currentIndex + 1} / ${bitmaps.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
    }
}
