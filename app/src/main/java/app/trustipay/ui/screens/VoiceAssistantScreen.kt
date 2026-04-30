package app.trustipay.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trustipay.ui.theme.*

@Composable
fun VoiceAssistantScreen(
    onClose: () -> Unit = {}
) {
    var isListening by remember { mutableStateOf(true) }
    var recognizedText by remember { mutableStateOf("මම රුපියල් 500ක් අම්මාට යවන්න ඕනෙ...") }
    var translation by remember { mutableStateOf("I want to send 500 rupees to Mom...") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrustiPayPrimary.copy(alpha = 0.95f))
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isListening) "සවන් දෙමින්... / Listening..." else "හඳුනාගත්තා / Recognized",
                color = TrustiPayTertiary,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Animated Voice Ripple
            VoiceRippleEffect(isListening)

            Spacer(modifier = Modifier.height(64.dp))

            // Recognized Text (Sinhala)
            Text(
                text = recognizedText,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Translation (English)
            Text(
                text = translation,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (!isListening) {
                Button(
                    onClick = { /* Process Payment */ },
                    colors = ButtonDefaults.buttonColors(containerColor = TrustiPayTertiary),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Confirm Payment / ගෙවීම තහවුරු කරන්න", fontWeight = FontWeight.Bold)
                }
            } else {
                // Simulate recognition for UI demo purposes
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(3000)
                    isListening = false
                }
            }
        }

        // Local AI Processing Indicator
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = TrustiPayTertiary,
                strokeWidth = 2.dp
            )
            Text(
                text = "Processing locally with Google LiteRT-LM",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall
            )
        }
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

private fun Modifier.scale(scale: Float) = this.graphicsLayer(scaleX = scale, scaleY = scale)
