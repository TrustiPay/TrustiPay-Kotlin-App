package app.trustipay.online.domain

sealed class OnlineTransactionState {
    object Idle : OnlineTransactionState()
    object Submitting : OnlineTransactionState()
    data class Confirmed(val transactionId: String, val settledAt: String?) : OnlineTransactionState()
    data class Failed(val message: String) : OnlineTransactionState()
}
