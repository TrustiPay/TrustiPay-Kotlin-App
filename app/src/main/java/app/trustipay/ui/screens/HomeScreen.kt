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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    onVoiceClick: () -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                HomeHeader()
            }
            item {
                BalanceCard()
            }
            item {
                QuickActions(onVoiceClick = onVoiceClick)
            }
            item {
                SendMoneyForm(voiceDraft = voiceDraft)
            }
            item {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TrustiPayPrimary
                )
            }
            items(recentTransactions) { transaction ->
                TransactionItem(transaction)
            }
        }

        // Voice Assistant FAB
        FloatingActionButton(
            onClick = onVoiceClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(72.dp),
            containerColor = TrustiPayPrimary,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice Assistant",
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun SendMoneyForm(
    voiceDraft: PaymentDraft?,
    modifier: Modifier = Modifier,
) {
    var recipient by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var statusIsError by rememberSaveable { mutableStateOf(false) }
    var filledFromVoice by rememberSaveable { mutableStateOf(false) }

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

    val parsedAmount = amount.parseAmountInput()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Send money",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TrustiPayPrimary
                    )
                    Text(
                        text = "From TrustiPay Savings **** 4582",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                if (filledFromVoice) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Voice filled") }
                    )
                }
            }

            OutlinedTextField(
                value = recipient,
                onValueChange = {
                    recipient = it
                    filledFromVoice = false
                    statusMessage = null
                },
                label = { Text("Recipient") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                isError = statusIsError && recipient.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = amount,
                onValueChange = {
                    amount = it.filterAmountInput()
                    filledFromVoice = false
                    statusMessage = null
                },
                label = { Text("Amount") },
                prefix = { Text("Rs.") },
                leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = statusIsError && (parsedAmount == null || parsedAmount <= 0.0),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = note,
                onValueChange = {
                    note = it
                    filledFromVoice = false
                    statusMessage = null
                },
                label = { Text("Reason or note") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        recipient = ""
                        amount = ""
                        note = ""
                        filledFromVoice = false
                        statusMessage = null
                        statusIsError = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Clear")
                }

                Button(
                    onClick = {
                        val cleanRecipient = recipient.trim()
                        val cleanAmount = amount.trim()
                        when {
                            cleanRecipient.isBlank() -> {
                                statusIsError = true
                                statusMessage = "Enter the recipient name."
                            }
                            parsedAmount == null || parsedAmount <= 0.0 -> {
                                statusIsError = true
                                statusMessage = "Enter a valid amount."
                            }
                            else -> {
                                statusIsError = false
                                statusMessage = "Draft ready: Rs. $cleanAmount to $cleanRecipient."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TrustiPayTertiary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Review")
                }
            }

            statusMessage?.let { message ->
                Text(
                    text = message,
                    color = if (statusIsError) MaterialTheme.colorScheme.error else TrustiPaySecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Ayubowan! / Hello!",
                style = MaterialTheme.typography.bodyLarge,
                color = TrustiPaySecondary
            )
            Text(
                text = "Saman Kumara",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TrustiPayPrimary
            )
        }
        IconButton(
            onClick = { /* TODO */ },
            modifier = Modifier.background(Color.White, CircleShape)
        ) {
            Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = TrustiPayPrimary)
        }
    }
}

@Composable
fun BalanceCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = TrustiPayPrimary)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(TrustiPayPrimary, TrustiPaySecondary)
                    )
                )
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.TopStart)) {
                Text(
                    text = "Total Balance",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Rs. 45,250.00",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "**** **** **** 4582",
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.BottomStart),
                style = MaterialTheme.typography.bodyMedium
            )
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = "Scan",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
            )
        }
    }
}

@Composable
fun QuickActions(
    onVoiceClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
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
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(TrustiPayBackground, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = transaction.name.first().toString(),
                        color = TrustiPayPrimary,
                        fontWeight = FontWeight.Bold
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
                color = if (transaction.isCredit) TrustiPayTertiary else Color.Red
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

val recentTransactions = listOf(
    Transaction("Food City", "2,450.00", "Today, 10:30 AM", false),
    Transaction("Salary", "120,000.00", "28 Oct, 09:00 AM", true),
    Transaction("Dialog Axiata", "1,500.00", "27 Oct, 04:15 PM", false),
    Transaction("Keells Super", "3,200.00", "25 Oct, 06:20 PM", false)
)

@Preview(showBackground = true)
@Composable
fun HomePreview() {
    TrustiPayTheme {
        HomeScreen()
    }
}
