package app.trustipay.online.data

import app.trustipay.api.ApiResult
import app.trustipay.api.TrustiPayApiService
import app.trustipay.api.dto.InitiatePaymentRequest
import app.trustipay.api.dto.PaymentResponse
import app.trustipay.api.dto.TransactionHistoryItem
import app.trustipay.api.dto.WalletResponse
import app.trustipay.api.safeApiCall

class OnlinePaymentRepository(private val apiService: TrustiPayApiService) {
    suspend fun fetchWallet(): ApiResult<WalletResponse> =
        safeApiCall { apiService.getMyWallet() }

    suspend fun fetchRecentTransactions(limit: Int = 20): ApiResult<List<TransactionHistoryItem>> =
        safeApiCall { apiService.getMyTransactions(limit = limit).transactions }

    suspend fun initiatePayment(
        recipientIdentifier: String,
        amountMinor: Long,
        currency: String,
        description: String,
        idempotencyKey: String,
    ): ApiResult<PaymentResponse> = safeApiCall {
        apiService.initiatePayment(
            request = InitiatePaymentRequest(recipientIdentifier, amountMinor, currency, description),
            idempotencyKey = idempotencyKey,
        )
    }
}
