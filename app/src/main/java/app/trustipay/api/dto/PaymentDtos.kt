package app.trustipay.api.dto

data class InitiatePaymentRequest(
    val recipientIdentifier: String,
    val amountMinor: Long,
    val currency: String,
    val description: String,
)

data class PaymentResponse(
    val transactionId: String,
    val status: String,
    val amountMinor: Long,
    val currency: String,
    val settledAt: String?,
)
