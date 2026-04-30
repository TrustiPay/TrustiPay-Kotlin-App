package app.trustipay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trustipay.ui.theme.*

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
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
                QuickActions()
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
fun QuickActions() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ActionItem(Icons.Default.QrCodeScanner, "Pay")
        ActionItem(Icons.Default.Mic, "Voice Pay")
        ActionItem(Icons.Default.Notifications, "Bills")
        ActionItem(Icons.Default.Notifications, "More")
    }
}

@Composable
fun ActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(
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
