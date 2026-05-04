package app.trustipay.api.dto

data class TokenIssuanceRequest(
    val deviceId: String,
    val requestedAmountMinor: Long,
    val currency: String,
    val preferredDenominations: List<Long> = emptyList(),
)

data class IssuedTokenDto(
    val tokenId: String,
    val ownerUserId: String? = null,
    val ownerDeviceId: String? = null,
    val amountMinor: Long,
    val currency: String,
    val issuedAtServer: String? = null,
    val issuedAt: String? = null,
    val expiresAtServer: String? = null,
    val expiresAt: String? = null,
    val issuerKeyId: String? = null,
    val serverKeyId: String? = null,
    val nonce: String? = null,
    val serverSignature: String,
    val canonicalPayload: String? = null,
    val protocolVersion: String? = null,
)

data class TokenIssuanceResponse(
    val reservedAmountMinor: Long? = null,
    val expiresAt: String? = null,
    val tokens: List<IssuedTokenDto>,
    val issuerPublicKeyId: String? = null,
    val issuerPublicKeyBase64: String? = null,
)

data class OfflineSyncRequest(
    val deviceId: String,
    val lastSyncCursor: String? = null,
    val pendingTransactions: List<OfflinePendingTransactionDto>,
    val spentTokenIds: List<String> = emptyList(),
)

data class OfflinePendingTransactionDto(
    val transactionId: String,
    val paymentRequest: String,
    val paymentOffer: String,
    val paymentReceipt: String,
    val spentTokenIds: List<String>,
    val senderDeviceId: String? = null,
    val receiverDeviceId: String? = null,
    val amountMinor: Long,
    val currency: String,
    val transportType: String,
    val createdAtDevice: String,
    val senderPreviousHash: String? = null,
    val senderChainHash: String? = null,
    val receiverPreviousHash: String? = null,
    val receiverChainHash: String? = null,
)

data class OfflineSyncResponse(
    val serverTime: String? = null,
    val syncCursor: String? = null,
    val settlementResults: List<OfflineSettlementResultDto> = emptyList(),
    val rejected: List<OfflineSettlementResultDto> = emptyList(),
    val disputed: List<OfflineSettlementResultDto> = emptyList(),
    val revokedTokenIds: List<String> = emptyList(),
    val revokedDeviceIds: List<String> = emptyList(),
    val newOfflineTokens: List<IssuedTokenDto> = emptyList(),
)

data class OfflineSettlementResultDto(
    val transactionId: String? = null,
    val status: String? = null,
    val settledAt: String? = null,
    val reason: String? = null,
    val errorCode: String? = null,
    val message: String? = null,
)

data class SettlementStatusResponse(
    val transactionId: String,
    val serverStatus: String? = null,
    val status: String? = null,
    val settledAt: String?,
    val rejectionReason: String?,
)
