package app.trustipay.offline.domain

import java.time.Instant

data class OfflineTransaction(
    val transactionId: String,
    val requestId: String?,
    val direction: TransactionDirection,
    val counterpartyAlias: String?,
    val amountMinor: Long,
    val currency: String,
    val state: TransactionState,
    val transportType: TransportType?,
    val requestPayload: ByteArray?,
    val offerPayload: ByteArray?,
    val receiptPayload: ByteArray?,
    val requestHash: String?,
    val offerHash: String?,
    val receiptHash: String?,
    val senderPreviousHash: String? = null,
    val senderChainHash: String? = null,
    val receiverPreviousHash: String? = null,
    val receiverChainHash: String? = null,
    val createdLocalAt: Instant,
    val updatedLocalAt: Instant,
    val lastSyncAttemptAt: Instant? = null,
    val settledAtServer: Instant? = null,
    val rejectionReason: String? = null,
)

data class KnownSpentToken(
    val tokenId: String,
    val transactionId: String,
    val seenAtLocal: Instant,
    val source: String,
)

data class LocalHashChainEntry(
    val deviceId: String,
    val transactionId: String,
    val previousHash: String,
    val chainHash: String,
    val createdLocalAt: Instant,
)
