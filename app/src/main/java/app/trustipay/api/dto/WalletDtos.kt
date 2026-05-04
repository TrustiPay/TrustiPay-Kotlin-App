package app.trustipay.api.dto

data class WalletResponse(
    val walletId: String,
    val userId: String,
    val balanceMinor: Long,
    val currency: String,
    val accountNumber: String,
)

data class TransactionHistoryResponse(
    val transactions: List<TransactionHistoryItem>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

data class TransactionHistoryItem(
    val id: String,
    val direction: String,
    val counterpartyName: String,
    val amountMinor: Long,
    val currency: String,
    val settledAt: String,
    val description: String?,
)
