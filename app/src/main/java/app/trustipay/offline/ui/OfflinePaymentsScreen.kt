package app.trustipay.offline.ui

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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trustipay.offline.OfflineFeatureFlagProvider
import app.trustipay.offline.domain.TransactionDirection
import app.trustipay.offline.domain.TransactionState
import app.trustipay.offline.transport.AndroidTransportCapabilityProvider
import app.trustipay.ui.screens.PaymentDraft
import app.trustipay.ui.theme.TrustiPayBackground
import app.trustipay.ui.theme.TrustiPayPrimary
import app.trustipay.ui.theme.TrustiPaySecondary
import app.trustipay.ui.theme.TrustiPayTertiary

@Composable
fun OfflinePaymentsScreen(
    modifier: Modifier = Modifier,
    voiceDraft: PaymentDraft? = null,
    viewModel: OfflineViewModel = viewModel(),
) {
    val flags = OfflineFeatureFlagProvider.current
    if (!flags.offlinePaymentsEnabled) {
        DisabledOfflineScreen(modifier)
        return
    }

    val context = LocalContext.current
    val snapshot by viewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Payments", "Wallet", "Pending")
    val capabilities = remember(context, flags) {
        AndroidTransportCapabilityProvider(context, flags).capabilities()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            OfflineSummary(snapshot = snapshot) { viewModel.syncNow() }
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) },
                    )
                }
            }

            when (selectedTab) {
                0 -> PaymentsTab(snapshot, viewModel, voiceDraft)
                1 -> WalletTab(snapshot)
                else -> PendingTransactionsTab(snapshot, capabilities.map { it.type.label to it.available })
            }
        }

        when (snapshot.qrFlowMode) {
            QrFlowMode.DISPLAYING -> {
                QrDisplayScreen(
                    transport = viewModel.qrTransport,
                    amountText = snapshot.qrAmount.orEmpty(),
                    otpCode = snapshot.otpCode,
                    showOtpInput = snapshot.showOtpInput,
                    otpFeedback = snapshot.otpFeedback,
                    onVerifyOtp = { viewModel.verifySenderOtp(it) },
                    onClose = { viewModel.cancelQrFlow() }
                )
            }
            QrFlowMode.SCANNING -> {
                QrScannerScreen(
                    nfcTransport = viewModel.nfcTransport,
                    onQrScanned = { viewModel.onQrScanned(it) },
                    onIouReceived = { viewModel.onQrScanned(it.toMinifiedJson()) },
                    onClose = { viewModel.cancelQrFlow() }
                )
            }
            QrFlowMode.PROCESSING -> {
                QrProcessingScreen(message = snapshot.processingMessage ?: "Processing...")
            }
            QrFlowMode.IDLE -> {}
        }
    }
}

@Composable
fun QrProcessingScreen(message: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "processing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .scale(scale),
                    tint = TrustiPayPrimary
                )
                
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = TrustiPayPrimary,
                    trackColor = TrustiPayBackground
                )
                
                Text(
                    text = "This takes a moment to secure your transaction",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun DisabledOfflineScreen(modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = TrustiPaySecondary)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Offline payments are disabled", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun OfflineSummary(
    snapshot: OfflineUiSnapshot,
    onSyncClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TrustiPayPrimary),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Offline Balance", color = Color.White.copy(alpha = 0.75f))
                    Text(
                        text = "Rs. ${snapshot.balanceMinor.toDisplayAmount()}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Button(onClick = onSyncClick) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Sync")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("${snapshot.tokenCount} tokens") })
                AssistChip(onClick = {}, label = { Text("${snapshot.pendingSyncCount} pending") })
            }
            snapshot.lastMessage?.let {
                Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PaymentsTab(
    snapshot: OfflineUiSnapshot,
    viewModel: OfflineViewModel,
    voiceDraft: PaymentDraft? = null,
) {
    var amount by rememberSaveable { mutableStateOf("1500.00") }
    var receiver by rememberSaveable { mutableStateOf("Food City") }
    var note by rememberSaveable { mutableStateOf("Purchase") }

    LaunchedEffect(voiceDraft?.eventId) {
        val draft = voiceDraft ?: return@LaunchedEffect
        if (draft.isOffline) {
            amount = draft.amount
            receiver = draft.recipient
            note = draft.note
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OfflineFormCard(title = "Send Payment") {
                OutlinedTextField(
                    value = receiver,
                    onValueChange = { receiver = it },
                    label = { Text("Receiver ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filterMoneyInput() },
                    label = { Text("Amount") },
                    prefix = { Text("Rs.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    minLines = 1,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { viewModel.startPayFlow(amount, receiver, note) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Pay Now")
                }
            }
        }
        
        item {
            OfflineFormCard(title = "Receive Payment") {
                Text(
                    "Ask the sender to scan your QR or tap via NFC. No form filling required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.startReceiveFlow() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Scan QR")
                    }
                    Button(
                        onClick = { viewModel.startNfcReceiveFlow() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Nfc, contentDescription = null)
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("NFC Tap")
                    }
                }
            }
        }

        item { TransactionPreview(snapshot) }
    }
}

@Composable
private fun WalletTab(snapshot: OfflineUiSnapshot) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(snapshot.tokens) { token ->
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Token ${token.tokenId}", fontWeight = FontWeight.Bold, color = TrustiPayPrimary)
                        Text("Expires ${token.expiresAt}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Rs. ${token.amountMinor.toDisplayAmount()}", fontWeight = FontWeight.Bold)
                        Text(token.status, style = MaterialTheme.typography.bodySmall, color = token.status.statusColor())
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingTransactionsTab(
    snapshot: OfflineUiSnapshot,
    capabilities: List<Pair<String, Boolean>>,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                capabilities.forEach { (label, available) ->
                    FilterChip(
                        selected = available,
                        onClick = {},
                        label = { Text(label) },
                    )
                }
            }
        }
        items(snapshot.transactions) { transaction ->
            TransactionRow(transaction)
        }
    }
}

@Composable
private fun OfflineFormCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TrustiPayPrimary)
            content()
        }
    }
}

@Composable
private fun TransactionPreview(snapshot: OfflineUiSnapshot) {
    snapshot.transactions.firstOrNull()?.let { transaction ->
        TransactionRow(transaction)
    }
}

@Composable
private fun TransactionRow(transaction: OfflineTransactionUiRow) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(transaction.state)
                Column {
                    Text(transaction.counterparty.ifBlank { transaction.transactionId }, fontWeight = FontWeight.Bold)
                    Text(
                        "${transaction.direction.label()} · ${transaction.transportType?.label ?: "Offline"} · ${transaction.state.label()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
            }
            Text(
                "Rs. ${transaction.amountMinor.toDisplayAmount()}",
                fontWeight = FontWeight.Bold,
                color = if (transaction.direction == TransactionDirection.RECEIVED) TrustiPayTertiary else TrustiPayPrimary,
            )
        }
    }
}

@Composable
private fun IconBadge(state: TransactionState) {
    val tint = when (state) {
        TransactionState.SETTLED -> TrustiPayTertiary
        TransactionState.SERVER_VALIDATING,
        TransactionState.SYNC_QUEUED,
        TransactionState.LOCAL_ACCEPTED_PENDING_SYNC,
        -> TrustiPaySecondary
        else -> MaterialTheme.colorScheme.error
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(TrustiPayBackground, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = tint)
    }
}

private fun String.filterMoneyInput(): String =
    filter { (it.isDigit() || it == '.' || it == ',') }.take(18)

private fun Long.toDisplayAmount(): String {
    val major = this / 100
    val minor = kotlin.math.abs(this % 100)
    return "%,d.%02d".format(major, minor)
}

private fun String.statusColor(): Color = when (this) {
    "AVAILABLE" -> TrustiPayTertiary
    "SPENT_PENDING_SYNC", "RESERVED_FOR_LOCAL_TXN" -> TrustiPaySecondary
    else -> Color.Gray
}

private fun TransactionState.label(): String = when (this) {
    TransactionState.LOCAL_ACCEPTED_PENDING_SYNC,
    TransactionState.SYNC_QUEUED,
    -> "Accepted offline"
    TransactionState.SYNC_UPLOADED,
    TransactionState.SERVER_VALIDATING,
    -> "Waiting for settlement"
    TransactionState.SETTLED -> "Settled"
    TransactionState.DISPUTED -> "Needs review"
    else -> if (name.startsWith("REJECTED")) "Rejected" else name.replace('_', ' ')
}

private fun TransactionDirection.label(): String = when (this) {
    TransactionDirection.SENT -> "Sent"
    TransactionDirection.RECEIVED -> "Received"
}
