package app.trustipay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trustipay.offline.OfflineFeatureFlagProvider
import app.trustipay.offline.ui.OfflineViewModel
import app.trustipay.offline.ui.QrDisplayScreen
import app.trustipay.offline.ui.QrFlowMode
import app.trustipay.offline.ui.QrProcessingScreen
import app.trustipay.offline.ui.QrScannerScreen
import app.trustipay.online.ui.HomeViewModel
import app.trustipay.ui.theme.*

data class PaymentDraft(
    val recipient: String = "",
    val amount: String = "",
    val note: String = "",
    val rawTranscript: String = "",
    val eventId: Int = 0,
    val isOffline: Boolean = false,
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    voiceDraft: PaymentDraft? = null,
    onVoiceClick: () -> Unit = {},
    homeViewModel: HomeViewModel = viewModel(),
    offlineViewModel: OfflineViewModel = viewModel(),
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val offlineSnapshot by offlineViewModel.uiState.collectAsState()
    val offlineFlags = OfflineFeatureFlagProvider.current

    Scaffold(modifier = modifier) { scaffoldPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    HomeHeader(displayName = uiState.displayName)
                }
                item {
                    BalanceCard(
                        balanceMinor = uiState.balanceMinor,
                        currency = uiState.currency,
                        accountNumber = uiState.accountNumber,
                        isLoading = uiState.isLoadingBalance,
                    )
                }
                item {
                    QuickActions(onVoiceClick = onVoiceClick)
                }
                item {
                    SendMoneyForm(
                        voiceDraft = voiceDraft,
                        offlineEnabled = offlineFlags.offlinePaymentsEnabled,
                        onSend = { recipient, amount, note ->
                            offlineViewModel.startPayFlow(amount, recipient, note)
                        },
                        onScanQr = { offlineViewModel.startReceiveFlow() },
                        onNfcTap = { offlineViewModel.startNfcReceiveFlow() },
                    )
                }
                item {
                    Text(
                        text = "Recent Transactions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TrustiPayPrimary,
                    )
                }
                if (uiState.recentTransactions.isEmpty() && !uiState.isLoadingBalance) {
                    item {
                        Text(
                            text = "No transactions yet",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    items(uiState.recentTransactions) { transaction ->
                        TransactionItem(transaction)
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }

            FloatingActionButton(
                onClick = onVoiceClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .size(72.dp),
                containerColor = TrustiPayPrimary,
                contentColor = Color.White,
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Assistant",
                    modifier = Modifier.size(36.dp),
                )
            }

            if (offlineFlags.offlinePaymentsEnabled) {
                when (offlineSnapshot.qrFlowMode) {
                    QrFlowMode.DISPLAYING -> QrDisplayScreen(
                        transport = offlineViewModel.qrTransport,
                        amountText = offlineSnapshot.qrAmount.orEmpty(),
                        otpCode = offlineSnapshot.otpCode,
                        showOtpInput = offlineSnapshot.showOtpInput,
                        otpFeedback = offlineSnapshot.otpFeedback,
                        onVerifyOtp = { offlineViewModel.verifySenderOtp(it) },
                        onClose = { offlineViewModel.cancelQrFlow() },
                    )
                    QrFlowMode.SCANNING -> QrScannerScreen(
                        nfcTransport = offlineViewModel.nfcTransport,
                        onQrScanned = { offlineViewModel.onQrScanned(it) },
                        onIouReceived = { offlineViewModel.onQrScanned(it.toMinifiedJson()) },
                        onClose = { offlineViewModel.cancelQrFlow() },
                    )
                    QrFlowMode.PROCESSING -> QrProcessingScreen(
                        message = offlineSnapshot.processingMessage ?: "Processing...",
                    )
                    QrFlowMode.IDLE -> {}
                }
            }
        }
    }
}

@Composable
private fun SendMoneyForm(
    voiceDraft: PaymentDraft?,
    offlineEnabled: Boolean,
    onSend: (recipient: String, amount: String, note: String) -> Unit,
    onScanQr: () -> Unit,
    onNfcTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var recipient by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var filledFromVoice by rememberSaveable { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var statusIsError by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(voiceDraft?.eventId) {
        val draft = voiceDraft ?: return@LaunchedEffect
        recipient = draft.recipient
        amount = draft.amount
        note = draft.note
        filledFromVoice = true
        statusIsError = false
        statusMessage = draft.rawTranscript
            .takeIf { it.isNotBlank() }
            ?.let { "Filled from voice: \"${it.compactForStatus()}\"" }
            ?: "Filled from voice request."
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
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
                Text(
                    text = "Send Money",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TrustiPayPrimary,
                )
                if (filledFromVoice) {
                    AssistChip(onClick = {}, label = { Text("Voice filled") })
                }
            }

            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it; filledFromVoice = false; statusMessage = null },
                label = { Text("Recipient") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                isError = statusIsError && recipient.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filterAmountInput(); filledFromVoice = false; statusMessage = null },
                label = { Text("Amount") },
                prefix = { Text("Rs.") },
                leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = statusIsError && (amount.parseAmountInput().let { it == null || it <= 0.0 }),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it; filledFromVoice = false; statusMessage = null },
                label = { Text("Note") },
                minLines = 1,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        recipient = ""; amount = ""; note = ""
                        filledFromVoice = false; statusMessage = null; statusIsError = false
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Clear")
                }
                Button(
                    onClick = {
                        val parsed = amount.parseAmountInput()
                        when {
                            recipient.isBlank() -> {
                                statusIsError = true
                                statusMessage = "Enter a recipient."
                            }
                            parsed == null || parsed <= 0.0 -> {
                                statusIsError = true
                                statusMessage = "Enter a valid amount."
                            }
                            else -> {
                                statusIsError = false
                                statusMessage = null
                                onSend(recipient.trim(), amount, note.trim())
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TrustiPayTertiary),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Send")
                }
            }

            if (offlineEnabled) {
                HorizontalDivider()
                Text(
                    text = "Receive Payment",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onScanQr,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Scan QR")
                    }
                    OutlinedButton(
                        onClick = onNfcTap,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Nfc, contentDescription = null)
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("NFC Tap")
                    }
                }
            }

            statusMessage?.let { message ->
                Text(
                    text = message,
                    color = if (statusIsError) MaterialTheme.colorScheme.error else TrustiPaySecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun HomeHeader(displayName: String = "") {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Ayubowan! / Hello!",
                style = MaterialTheme.typography.bodyLarge,
                color = TrustiPaySecondary,
            )
            Text(
                text = displayName.ifBlank { "Welcome" },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TrustiPayPrimary,
            )
        }
        IconButton(
            onClick = { },
            modifier = Modifier.background(Color.White, CircleShape),
        ) {
            Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = TrustiPayPrimary)
        }
    }
}

@Composable
fun BalanceCard(
    balanceMinor: Long = 0L,
    currency: String = "LKR",
    accountNumber: String = "",
    isLoading: Boolean = false,
) {
    val displayBalance = if (isLoading) "Loading…" else {
        val rupees = balanceMinor / 100.0
        "Rs. %,.2f".format(rupees)
    }
    val maskedAccount = if (accountNumber.length >= 4) "**** ${accountNumber.takeLast(4)}" else accountNumber

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    Text(
                        text = "Total Balance",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = displayBalance,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan",
                        tint = Color.White,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(currency) })
                if (maskedAccount.isNotBlank()) {
                    AssistChip(onClick = {}, label = { Text(maskedAccount) })
                }
            }
        }
    }
}

@Composable
fun QuickActions(onVoiceClick: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ActionItem(Icons.Default.QrCodeScanner, "Pay")
        ActionItem(Icons.Default.Mic, "Voice Pay", onClick = onVoiceClick)
        ActionItem(Icons.Default.Notifications, "Bills")
        ActionItem(Icons.Default.Notifications, "More")
    }
}

@Composable
fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = TrustiPaySecondary)
        }
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TrustiPayPrimary)
    }
}

@Composable
fun TransactionItem(transaction: Transaction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).background(TrustiPayBackground, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = transaction.name.firstOrNull()?.toString() ?: "?",
                        color = TrustiPayPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column {
                    Text(text = transaction.name, fontWeight = FontWeight.Bold, color = TrustiPayPrimary)
                    Text(text = transaction.date, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            Text(
                text = "${if (transaction.isCredit) "+" else "-"} Rs. ${transaction.amount}",
                fontWeight = FontWeight.Bold,
                color = if (transaction.isCredit) TrustiPayTertiary else Color.Red,
            )
        }
    }
}

data class Transaction(val name: String, val amount: String, val date: String, val isCredit: Boolean)

private fun String.filterAmountInput(): String =
    filter { it.isDigit() || it == '.' || it == ',' }.take(18)

private fun String.parseAmountInput(): Double? =
    replace(",", "").trim().toDoubleOrNull()

private fun String.compactForStatus(): String {
    val clean = trim()
    return if (clean.length <= 72) clean else "${clean.take(69)}..."
}

@Preview(showBackground = true)
@Composable
fun HomePreview() {
    TrustiPayTheme {
        HomeScreen()
    }
}
