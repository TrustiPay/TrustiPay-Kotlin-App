package app.trustipay.online.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.trustipay.AppContainer
import app.trustipay.api.ApiResult
import app.trustipay.online.domain.OnlineTransactionState
import app.trustipay.ui.screens.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository get() = AppContainer.onlinePaymentRepository
    private val tokenStore get() = AppContainer.tokenStore

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var _pendingIdempotencyKey: String? = null

    init {
        fetchBalance()
    }

    fun fetchBalance() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoadingBalance = true, balanceError = null)

            val stored = tokenStore.load()
            val displayName = stored?.displayName ?: ""

            when (val result = repository.fetchWallet()) {
                is ApiResult.Success -> {
                    val wallet = result.data
                    val txnResult = repository.fetchRecentTransactions()
                    val transactions = if (txnResult is ApiResult.Success) {
                        txnResult.data.map { item ->
                            Transaction(
                                name = item.counterpartyName,
                                amount = formatMinor(item.amountMinor),
                                date = item.settledAt.take(10),
                                isCredit = item.direction == "RECEIVED",
                            )
                        }
                    } else emptyList()

                    _uiState.value = _uiState.value.copy(
                        balanceMinor = wallet.balanceMinor,
                        currency = wallet.currency,
                        displayName = displayName.ifBlank { wallet.userId },
                        accountNumber = wallet.accountNumber,
                        recentTransactions = transactions,
                        isLoadingBalance = false,
                    )
                }
                is ApiResult.NetworkError -> _uiState.value = _uiState.value.copy(
                    isLoadingBalance = false,
                    balanceError = "Network error",
                    displayName = displayName,
                )
                is ApiResult.HttpError, ApiResult.AuthError -> _uiState.value = _uiState.value.copy(
                    isLoadingBalance = false,
                    displayName = displayName,
                )
            }
        }
    }

    fun submitPayment(recipient: String, amountMinor: Long, note: String) {
        val idempotencyKey = _pendingIdempotencyKey ?: UUID.randomUUID().toString().also {
            _pendingIdempotencyKey = it
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(sendMoneyState = OnlineTransactionState.Submitting)
            when (val result = repository.initiatePayment(
                recipientIdentifier = recipient,
                amountMinor = amountMinor,
                currency = _uiState.value.currency,
                description = note.ifBlank { "Payment" },
                idempotencyKey = idempotencyKey,
            )) {
                is ApiResult.Success -> {
                    _pendingIdempotencyKey = null
                    _uiState.value = _uiState.value.copy(
                        sendMoneyState = OnlineTransactionState.Confirmed(
                            transactionId = result.data.transactionId,
                            settledAt = result.data.settledAt,
                        )
                    )
                    fetchBalance()
                }
                is ApiResult.HttpError -> {
                    _pendingIdempotencyKey = null
                    _uiState.value = _uiState.value.copy(
                        sendMoneyState = OnlineTransactionState.Failed(
                            when (result.code) {
                                402 -> "Insufficient funds"
                                404 -> "Recipient not found"
                                else -> result.message
                            }
                        )
                    )
                }
                is ApiResult.NetworkError -> _uiState.value = _uiState.value.copy(
                    sendMoneyState = OnlineTransactionState.Failed("Network error — try again")
                )
                ApiResult.AuthError -> _uiState.value = _uiState.value.copy(
                    sendMoneyState = OnlineTransactionState.Failed("Session expired, please log in again")
                )
            }
        }
    }

    fun resetSendState() {
        _uiState.value = _uiState.value.copy(sendMoneyState = OnlineTransactionState.Idle)
        _pendingIdempotencyKey = null
    }

    private fun formatMinor(minor: Long): String {
        val rupees = minor / 100.0
        return "%,.2f".format(rupees)
    }
}
