package app.trustipay.offline.domain

import java.time.Instant

data class PaymentRequest(
    val protocolVersion: Int,
    val messageType: String,
    val requestId: String,
    val receiverUserAlias: String,
    val receiverDeviceId: String,
    val receiverPublicKeyId: String,
    val amountMinor: Long,
    val currency: String,
    val description: String,
    val createdAtDevice: Instant,
    val expiresAtDevice: Instant,
    val nonce: String,
    val supportedTransports: List<TransportType>,
    val receiverSignature: String = "",
)

data class PaymentOffer(
    val protocolVersion: Int,
    val messageType: String,
    val transactionId: String,
    val requestId: String,
    val senderUserAlias: String,
    val senderDeviceId: String,
    val senderPublicKeyId: String,
    val receiverDeviceId: String,
    val amountMinor: Long,
    val currency: String,
    val offlineTokens: List<PaymentOfferToken>,
    val requestHash: String,
    val createdAtDevice: Instant,
    val nonce: String,
    val senderSignature: String = "",
)

data class PaymentReceipt(
    val protocolVersion: Int,
    val messageType: String,
    val transactionId: String,
    val requestId: String,
    val receiverDeviceId: String,
    val senderDeviceId: String,
    val offerHash: String,
    val acceptedAtDevice: Instant,
    val localValidationResult: LocalValidationResult,
    val nonce: String,
    val receiverSignature: String = "",
)

enum class LocalValidationResult {
    ACCEPTED_PENDING_SYNC,
}
