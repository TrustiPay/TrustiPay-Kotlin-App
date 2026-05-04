package app.trustipay.api.dto

data class TokenIssuanceRequest(
    val devicePublicKeyId: String,
    val requestedAmounts: List<Long>,
    val currency: String,
    val nonce: String,
    val timestamp: String,
    val deviceSignature: String,
)

data class IssuedTokenDto(
    val tokenId: String,
    val ownerUserId: String,
    val ownerDeviceId: String,
    val amountMinor: Long,
    val currency: String,
    val issuedAtServer: String,
    val expiresAtServer: String,
    val issuerKeyId: String,
    val nonce: String,
    val serverSignature: String,
    val canonicalPayload: String,
)

data class TokenIssuanceResponse(
    val tokens: List<IssuedTokenDto>,
    val issuerPublicKeyId: String,
    val issuerPublicKeyBase64: String,
)

data class OfflineSyncRequest(
    val transactionId: String,
    val requestPayloadBase64: String,
    val offerPayloadBase64: String,
    val receiptPayloadBase64: String,
    val devicePublicKeyId: String,
    val deviceSignature: String,
)

data class OfflineSyncResponse(
    val transactionId: String,
    val serverStatus: String,
    val settledAt: String?,
    val rejectionReason: String?,
)

data class SettlementStatusResponse(
    val transactionId: String,
    val serverStatus: String,
    val settledAt: String?,
    val rejectionReason: String?,
)
