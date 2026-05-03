package app.trustipay.offline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.trustipay.offline.OfflineFeatureFlagProvider
import app.trustipay.offline.domain.TransactionDirection
import app.trustipay.offline.domain.TransactionState
import app.trustipay.offline.transport.AndroidTransportCapabilityProvider
import app.trustipay.ui.theme.TrustiPayBackground
import app.trustipay.ui.theme.TrustiPayPrimary
import app.trustipay.ui.theme.TrustiPaySecondary
import app.trustipay.ui.theme.TrustiPayTertiary

@Composable
fun OfflinePaymentsScreen(
    modifier: Modifier = Modifier,
) {
    val flags = OfflineFeatureFlagProvider.current
    if (!flags.offlinePaymentsEnabled) {
        DisabledOfflineScreen(modifier)
        return
    }

    val context = LocalContext.current
    val controller = remember { OfflinePrototypeController.create(flags) }
    var snapshot by remember { mutableStateOf(controller.snapshot()) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Pay", "Receive", "Wallet", "Pending")
    val capabilities = remember(context, flags) {
        AndroidTransportCapabilityProvider(context, flags).capabilities()
    }

    Column(modifier = modifier.fillMaxSize()) {
        OfflineSummary(snapshot = snapshot, onSyncClick = { snapshot = controller.syncNow() })
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(label) },
                )
            }
        }

        when (selectedTab) {
            0 -> PayOfflineTab(snapshot, controller) { snapshot = it }
            1 -> ReceiveOfflineTab(snapshot, controller) { snapshot = it }
            2 -> WalletTab(snapshot)
            else -> PendingTransactionsTab(snapshot, capabilities.map { it.type.label to it.available })
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
private fun PayOfflineTab(
    snapshot: OfflineUiSnapshot,
    controller: OfflinePrototypeController,
    onSnapshot: (OfflineUiSnapshot) -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf("1500.00") }
    var receiver by rememberSaveable { mutableStateOf("Food City") }
    var note by rememberSaveable { mutableStateOf("Offline purchase") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OfflineFormCard(title = "Pay Offline") {
                OutlinedTextField(
                    value = receiver,
                    onValueChange = { receiver = it },
                    label = { Text("Receiver") },
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
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onSnapshot(controller.runQrPayment(amount, receiver, note)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Create QR Offer")
                }
            }
        }
        item { TransactionPreview(snapshot) }
    }
}

@Composable
private fun ReceiveOfflineTab(
    snapshot: OfflineUiSnapshot,
    controller: OfflinePrototypeController,
    onSnapshot: (OfflineUiSnapshot) -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf("500.00") }
    var note by rememberSaveable { mutableStateOf("Counter payment") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OfflineFormCard(title = "Receive Offline") {
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
                    label = { Text("Description") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onSnapshot(controller.createReceiveRequest(amount, note)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Create Request")
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
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(40.dp)
            .background(TrustiPayBackground, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = tint)
    }
}

private fun String.filterMoneyInput(): String =
    filter { it.isDigit() || it == '.' || it == ',' }.take(18)

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
