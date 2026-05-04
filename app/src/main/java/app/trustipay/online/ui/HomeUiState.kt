package app.trustipay.online.ui

import app.trustipay.online.domain.OnlineTransactionState
import app.trustipay.ui.screens.Transaction

data class HomeUiState(
    val balanceMinor: Long = 0L,
    val currency: String = "LKR",
    val displayName: String = "",
    val accountNumber: String = "",
    val recentTransactions: List<Transaction> = emptyList(),
    val sendMoneyState: OnlineTransactionState = OnlineTransactionState.Idle,
    val isLoadingBalance: Boolean = false,
    val balanceError: String? = null,
)
