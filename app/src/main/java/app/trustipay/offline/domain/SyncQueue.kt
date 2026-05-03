package app.trustipay.offline.domain

import java.time.Instant

enum class SyncOperationType {
    UPLOAD_OFFLINE_TRANSACTION,
    UPLOAD_SPENT_TOKEN,
    FETCH_SETTLEMENT_STATUS,
    REFRESH_OFFLINE_TOKENS,
    FETCH_REVOKED_KEYS,
}

enum class SyncQueueStatus {
    PENDING,
    UPLOADED,
    WAITING_FOR_SERVER,
    SETTLED,
    REJECTED,
    FAILED_PERMANENTLY,
}

data class SyncQueueItem(
    val queueId: String,
    val transactionId: String,
    val operationType: SyncOperationType,
    val payload: ByteArray,
    val attemptCount: Int,
    val nextAttemptAt: Instant?,
    val status: SyncQueueStatus,
    val createdLocalAt: Instant,
    val updatedLocalAt: Instant,
)
