package app.trustipay.offline.sync

import app.trustipay.offline.OfflineFeatureFlags
import app.trustipay.offline.data.OfflinePaymentStore
import app.trustipay.offline.domain.OfflineIdGenerator
import app.trustipay.offline.domain.OfflineTransaction
import app.trustipay.offline.domain.SecureOfflineIdGenerator
import app.trustipay.offline.domain.SyncOperationType
import app.trustipay.offline.domain.SyncQueueItem
import app.trustipay.offline.domain.SyncQueueStatus
import app.trustipay.offline.domain.TransactionState
import java.time.Clock

class SyncRepository(
    private val store: OfflinePaymentStore,
    private val flags: OfflineFeatureFlags,
    private val idGenerator: OfflineIdGenerator = SecureOfflineIdGenerator(),
    private val clock: Clock = Clock.systemUTC(),
) {
    fun queueAcceptedTransaction(transaction: OfflineTransaction) {
        val now = clock.instant()
        store.upsertTransaction(transaction.copy(state = TransactionState.SYNC_QUEUED, updatedLocalAt = now))
        store.enqueue(
            SyncQueueItem(
                queueId = idGenerator.newId("queue"),
                transactionId = transaction.transactionId,
                operationType = SyncOperationType.UPLOAD_OFFLINE_TRANSACTION,
                payload = transaction.receiptPayload ?: transaction.offerPayload ?: byteArrayOf(),
                attemptCount = 0,
                nextAttemptAt = null,
                status = SyncQueueStatus.PENDING,
                createdLocalAt = now,
                updatedLocalAt = now,
            )
        )
    }

    fun processShadowSync(): SyncRunSummary {
        if (!flags.offlineSyncEnabled) return SyncRunSummary(0, 0, "Offline sync is disabled.")
        if (!flags.offlineSettlementShadowMode && !flags.offlineSettlementLiveMode) {
            return SyncRunSummary(0, 0, "No settlement mode is enabled.")
        }

        val now = clock.instant()
        var uploaded = 0
        store.listQueue(SyncQueueStatus.PENDING).forEach { item ->
            store.updateQueueItem(
                item.copy(
                    attemptCount = item.attemptCount + 1,
                    status = SyncQueueStatus.WAITING_FOR_SERVER,
                    updatedLocalAt = now,
                )
            )
            store.updateTransactionState(item.transactionId, TransactionState.SERVER_VALIDATING)
            uploaded += 1
        }
        return SyncRunSummary(
            uploaded = uploaded,
            waitingForServer = store.listQueue(SyncQueueStatus.WAITING_FOR_SERVER).size,
            message = if (flags.offlineSettlementLiveMode) {
                "Uploaded for live settlement."
            } else {
                "Shadow sync uploaded; waiting for server validation."
            },
        )
    }
}

data class SyncRunSummary(
    val uploaded: Int,
    val waitingForServer: Int,
    val message: String,
)
