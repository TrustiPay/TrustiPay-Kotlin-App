package app.trustipay.offline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trustipay.offline.OfflineFeatureFlagProvider
import app.trustipay.ui.theme.TrustiPaySecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineHistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: OfflineViewModel = viewModel(),
) {
    val flags = OfflineFeatureFlagProvider.current
    if (!flags.offlinePaymentsEnabled) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = TrustiPaySecondary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Offline payments are disabled", color = Color.Gray)
            }
        }
        return
    }

    val snapshot by viewModel.uiState.collectAsState()
    var selectedTransaction by remember { mutableStateOf<OfflineTransactionUiRow?>(null) }
    val sheetState = rememberModalBottomSheetState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            OfflineSummary(snapshot = snapshot) { viewModel.syncNow() }
            HistoryContent(snapshot = snapshot, onTransactionClick = { selectedTransaction = it })
        }

        selectedTransaction?.let { txn ->
            TransactionDetailSheet(
                transaction = txn,
                sheetState = sheetState,
                onDismiss = { selectedTransaction = null },
            )
        }
    }
}

@Composable
private fun HistoryContent(
    snapshot: OfflineUiSnapshot,
    onTransactionClick: (OfflineTransactionUiRow) -> Unit,
) {
    if (snapshot.transactions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.Gray,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("No transaction history", color = Color.Gray)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(snapshot.transactions) { transaction ->
                TransactionRow(transaction, onClick = { onTransactionClick(transaction) })
            }
        }
    }
}
