package app.trustipay.offline.sync

import app.trustipay.api.ApiResult
import app.trustipay.api.TrustiPayApiService
import app.trustipay.api.dto.OfflineSyncRequest
import app.trustipay.api.safeApiCall
import app.trustipay.offline.OfflineFeatureFlags
import app.trustipay.offline.data.OfflinePaymentStore
import app.trustipay.offline.domain.OfflineIdGenerator
import app.trustipay.offline.domain.OfflineTransaction
import app.trustipay.offline.domain.SecureOfflineIdGenerator
import app.trustipay.offline.domain.SyncOperationType
import app.trustipay.offline.domain.SyncQueueItem
import app.trustipay.offline.domain.SyncQueueStatus
import app.trustipay.offline.domain.TransactionState
import app.trustipay.offline.security.DeviceKeyManager
import java.time.Clock
import java.util.Base64
import java.util.UUID
import kotlin.math.min
import kotlin.math.pow

class SyncRepository(
    private val store: OfflinePaymentStore,
    private val flags: OfflineFeatureFlags,
    private val idGenerator: OfflineIdGenerator = SecureOfflineIdGenerator(),
    private val clock: Clock = Clock.systemUTC(),
    private val apiService: TrustiPayApiService? = null,
    private val deviceKeyManager: DeviceKeyManager? = null,
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
            store.updateQueueItem(item.copy(
                attemptCount = item.attemptCount + 1,
                status = SyncQueueStatus.WAITING_FOR_SERVER,
                updatedLocalAt = now,
            ))
            store.updateTransactionState(item.transactionId, TransactionState.SERVER_VALIDATING)
            uploaded += 1
        }
        return SyncRunSummary(
            uploaded = uploaded,
            waitingForServer = store.listQueue(SyncQueueStatus.WAITING_FOR_SERVER).size,
            message = "Shadow sync: $uploaded transactions queued.",
        )
    }

    suspend fun processSync(): SyncRunSummary {
        if (!flags.offlineSyncEnabled) return SyncRunSummary(0, 0, "Offline sync is disabled.")
        if (!flags.offlineSettlementLiveMode || apiService == null || deviceKeyManager == null) {
            return processShadowSync()
        }

        val now = clock.instant()
        var uploaded = 0
        val transactions = store.listTransactions().associateBy { it.transactionId }

        store.listQueue(SyncQueueStatus.PENDING).forEach { item ->
            val txn = transactions[item.transactionId]
            if (txn != null) {
                val uploadResult = uploadTransaction(txn, deviceKeyManager, apiService)
                if (uploadResult) {
                    store.updateQueueItem(item.copy(
                        attemptCount = item.attemptCount + 1,
                        status = SyncQueueStatus.WAITING_FOR_SERVER,
                        updatedLocalAt = now,
                    ))
                    store.updateTransactionState(item.transactionId, TransactionState.SERVER_VALIDATING)
                    uploaded += 1
                } else {
                    val backoffSeconds = min(2.0.pow(item.attemptCount.toDouble()) * 60, 3600.0).toLong()
                    store.updateQueueItem(item.copy(
                        attemptCount = item.attemptCount + 1,
                        nextAttemptAt = now.plusSeconds(backoffSeconds),
                        updatedLocalAt = now,
                    ))
                }
            }
        }

        return SyncRunSummary(
            uploaded = uploaded,
            waitingForServer = store.listQueue(SyncQueueStatus.WAITING_FOR_SERVER).size,
            message = "Uploaded $uploaded transactions for live settlement.",
        )
    }

    private suspend fun uploadTransaction(
        txn: OfflineTransaction,
        keyManager: DeviceKeyManager,
        api: TrustiPayApiService,
    ): Boolean {
        val requestPayload = txn.requestPayload ?: return false
        val offerPayload = txn.offerPayload ?: return false
        val receiptPayload = txn.receiptPayload ?: return false

        val devicePublicKeyId = keyManager.getPublicKeyId()
        val deviceSignature = keyManager.signAsBase64Url(receiptPayload)
        val idempotencyKey = UUID.nameUUIDFromBytes(txn.transactionId.toByteArray()).toString()

        val result = safeApiCall {
            api.submitOfflineTransaction(
                request = OfflineSyncRequest(
                    transactionId = txn.transactionId,
                    requestPayloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(requestPayload),
                    offerPayloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(offerPayload),
                    receiptPayloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(receiptPayload),
                    devicePublicKeyId = devicePublicKeyId,
                    deviceSignature = deviceSignature,
                ),
                idempotencyKey = idempotencyKey,
            )
        }
        return result is ApiResult.Success
    }
}

data class SyncRunSummary(
    val uploaded: Int,
    val waitingForServer: Int,
    val message: String,
)
