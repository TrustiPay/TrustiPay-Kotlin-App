package app.trustipay.offline.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trustipay.offline.transport.qr.QrPaymentTransport
import kotlinx.coroutines.delay

@Composable
fun QrDisplayScreen(
    transport: QrPaymentTransport,
    amountText: String,
    otpCode: String? = null,
    showOtpInput: Boolean = false,
    otpFeedback: OtpFeedback = OtpFeedback.NONE,
    onVerifyOtp: (String) -> Unit = {},
    onClose: () -> Unit,
) {
    val bitmaps by transport.outgoingBitmaps.collectAsState()
    val currentIndex by transport.currentBitmapIndex.collectAsState()
    var inputOtp by remember { mutableStateOf("") }

    val offsetX = remember { Animatable(0f) }
    
    LaunchedEffect(otpFeedback) {
        if (otpFeedback == OtpFeedback.ERROR) {
            repeat(4) {
                offsetX.animateTo(
                    targetValue = if (it % 2 == 0) 20f else -20f,
                    animationSpec = tween(durationMillis = 50, easing = LinearEasing)
                )
            }
            offsetX.animateTo(0f)
        }
    }
    
    val successScale = remember { Animatable(1f) }
    LaunchedEffect(otpFeedback) {
        if (otpFeedback == OtpFeedback.SUCCESS) {
            successScale.animateTo(
                targetValue = 1.2f,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )
            successScale.animateTo(1.0f)
        }
    }

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

            Spacer(Modifier.height(24.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.Nfc, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("OR Tap phone to pay via NFC", style = MaterialTheme.typography.bodyMedium)
            }

            otpCode?.let {
                Spacer(Modifier.height(32.dp))
                Text("Verification Code", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 8.sp,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            if (showOtpInput) {
                Spacer(Modifier.height(32.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(offsetX.value.toInt(), 0) }
                        .scale(successScale.value),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Enter Receiver's Verification Code",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    OutlinedTextField(
                        value = inputOtp,
                        onValueChange = { if (it.length <= 6) inputOtp = it },
                        modifier = Modifier.fillMaxWidth(0.6f),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            textAlign = TextAlign.Center,
                            letterSpacing = 4.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = when (otpFeedback) {
                                OtpFeedback.SUCCESS -> Color.Green
                                OtpFeedback.ERROR -> Color.Red
                                else -> MaterialTheme.colorScheme.primary
                            }
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        placeholder = { Text("000000", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                    )
                    
                    if (otpFeedback != OtpFeedback.NONE) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (otpFeedback == OtpFeedback.SUCCESS) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (otpFeedback == OtpFeedback.SUCCESS) Color.Green else Color.Red
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                text = if (otpFeedback == OtpFeedback.SUCCESS) "Verified!" else "Invalid Code",
                                color = if (otpFeedback == OtpFeedback.SUCCESS) Color.Green else Color.Red,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onVerifyOtp(inputOtp) },
                        enabled = inputOtp.length >= 4 && otpFeedback == OtpFeedback.NONE,
                        modifier = Modifier.fillMaxWidth(0.6f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (otpFeedback) {
                                OtpFeedback.SUCCESS -> Color.Green
                                OtpFeedback.ERROR -> Color.Red
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        Text(if (otpFeedback == OtpFeedback.SUCCESS) "Confirmed" else "Verify & Confirm")
                    }
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
