package app.trustipay.offline.sync

import app.trustipay.api.ApiResult
import app.trustipay.api.TrustiPayApiService
import app.trustipay.api.dto.OfflinePendingTransactionDto
import app.trustipay.api.dto.OfflineSyncRequest
import app.trustipay.api.safeApiCall
import app.trustipay.offline.OfflineFeatureFlags
import app.trustipay.offline.data.OfflinePaymentStore
import app.trustipay.offline.domain.LocalHashChainEntry
import app.trustipay.offline.domain.OfflineIdGenerator
import app.trustipay.offline.domain.OfflineTransaction
import app.trustipay.offline.domain.SecureOfflineIdGenerator
import app.trustipay.offline.domain.SyncOperationType
import app.trustipay.offline.domain.SyncQueueItem
import app.trustipay.offline.domain.SyncQueueStatus
import app.trustipay.offline.domain.TransactionDirection
import app.trustipay.offline.domain.TransactionState
import app.trustipay.offline.protocol.LocalHashChain
import app.trustipay.offline.security.DeviceKeyManager
import app.trustipay.offline.security.LocalEncryptionService
import org.json.JSONObject
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
    private val localEncryptionService: LocalEncryptionService? = null,
) {
    fun queueAcceptedTransaction(transaction: OfflineTransaction) {
        val now = clock.instant()
        val chainedTransaction = ensureHashChain(transaction, now)
        val queuePayload = chainedTransaction.receiptPayload ?: chainedTransaction.offerPayload ?: byteArrayOf()
        store.upsertTransaction(chainedTransaction.copy(state = TransactionState.SYNC_QUEUED, updatedLocalAt = now))
        store.enqueue(
            SyncQueueItem(
                queueId = idGenerator.newId("queue"),
                transactionId = chainedTransaction.transactionId,
                operationType = SyncOperationType.UPLOAD_OFFLINE_TRANSACTION,
                payload = encryptedQueuePayload(chainedTransaction, queuePayload),
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

        val deviceId = keyManager.getPublicKeyId()
        val idempotencyKey = UUID.nameUUIDFromBytes(txn.transactionId.toByteArray()).toString()
        val offerJson = JSONObject(String(offerPayload, Charsets.UTF_8))
        val receiptJson = JSONObject(String(receiptPayload, Charsets.UTF_8))
        val spentTokenIds = spentTokenIdsFromOffer(offerJson)

        val result = safeApiCall {
            api.submitOfflineTransaction(
                request = OfflineSyncRequest(
                    deviceId = deviceId,
                    pendingTransactions = listOf(
                        OfflinePendingTransactionDto(
                            transactionId = txn.transactionId,
                            paymentRequest = Base64.getUrlEncoder().withoutPadding().encodeToString(requestPayload),
                            paymentOffer = Base64.getUrlEncoder().withoutPadding().encodeToString(offerPayload),
                            paymentReceipt = Base64.getUrlEncoder().withoutPadding().encodeToString(receiptPayload),
                            spentTokenIds = spentTokenIds,
                            senderDeviceId = offerJson.optString("senderDeviceId").takeIf { it.isNotBlank() },
                            receiverDeviceId = receiptJson.optString("receiverDeviceId").takeIf { it.isNotBlank() },
                            amountMinor = txn.amountMinor,
                            currency = txn.currency,
                            transportType = txn.transportType?.name ?: "UNKNOWN",
                            createdAtDevice = txn.createdLocalAt.toString(),
                            senderPreviousHash = txn.senderPreviousHash,
                            senderChainHash = txn.senderChainHash,
                            receiverPreviousHash = txn.receiverPreviousHash,
                            receiverChainHash = txn.receiverChainHash,
                        )
                    ),
                    spentTokenIds = spentTokenIds,
                ),
                idempotencyKey = idempotencyKey,
            )
        }
        return result is ApiResult.Success
    }

    private fun ensureHashChain(transaction: OfflineTransaction, now: java.time.Instant): OfflineTransaction {
        val requestHash = transaction.requestHash
        val offerHash = transaction.offerHash
        val receiptHash = transaction.receiptHash
        return when (transaction.direction) {
            TransactionDirection.SENT -> {
                val offerJson = transaction.offerPayload?.let { JSONObject(String(it, Charsets.UTF_8)) }
                val deviceId = offerJson?.optString("senderDeviceId").orEmpty()
                if (deviceId.isBlank() || transaction.senderChainHash != null) return transaction
                val previousHash = transaction.senderPreviousHash
                    ?: offerJson?.optString("senderPreviousHash")?.takeIf { it.isNotBlank() }
                    ?: store.latestLocalChainHash(deviceId)
                    ?: LocalHashChain.GENESIS_HASH
                val chainHash = LocalHashChain.transactionHash(
                    deviceId = deviceId,
                    previousHash = previousHash,
                    transactionId = transaction.transactionId,
                    requestHash = requestHash,
                    offerHash = offerHash,
                    receiptHash = receiptHash,
                    amountMinor = transaction.amountMinor,
                    currency = transaction.currency,
                    transportType = transaction.transportType,
                    createdAtDevice = transaction.createdLocalAt,
                )
                store.appendLocalChainEntry(LocalHashChainEntry(deviceId, transaction.transactionId, previousHash, chainHash, now))
                transaction.copy(senderPreviousHash = previousHash, senderChainHash = chainHash)
            }
            TransactionDirection.RECEIVED -> {
                val receiptJson = transaction.receiptPayload?.let { JSONObject(String(it, Charsets.UTF_8)) }
                val deviceId = receiptJson?.optString("receiverDeviceId").orEmpty()
                if (deviceId.isBlank() || transaction.receiverChainHash != null) return transaction
                val previousHash = transaction.receiverPreviousHash
                    ?: receiptJson?.optString("receiverPreviousHash")?.takeIf { it.isNotBlank() }
                    ?: store.latestLocalChainHash(deviceId)
                    ?: LocalHashChain.GENESIS_HASH
                val chainHash = LocalHashChain.transactionHash(
                    deviceId = deviceId,
                    previousHash = previousHash,
                    transactionId = transaction.transactionId,
                    requestHash = requestHash,
                    offerHash = offerHash,
                    receiptHash = receiptHash,
                    amountMinor = transaction.amountMinor,
                    currency = transaction.currency,
                    transportType = transaction.transportType,
                    createdAtDevice = transaction.createdLocalAt,
                )
                store.appendLocalChainEntry(LocalHashChainEntry(deviceId, transaction.transactionId, previousHash, chainHash, now))
                transaction.copy(receiverPreviousHash = previousHash, receiverChainHash = chainHash)
            }
        }
    }

    private fun encryptedQueuePayload(transaction: OfflineTransaction, payload: ByteArray): ByteArray {
        val encryption = localEncryptionService ?: return payload
        val previousHash = transaction.senderPreviousHash
            ?: transaction.receiverPreviousHash
            ?: LocalHashChain.GENESIS_HASH
        val aad = "sync_queue:${transaction.transactionId}".toByteArray(Charsets.UTF_8)
        return with(encryption) {
            encryptWithPreviousHash(payload, previousHash, aad).encodeForStorage()
        }
    }

    private fun spentTokenIdsFromOffer(offerJson: JSONObject): List<String> {
        val tokens = offerJson.optJSONArray("offlineTokens") ?: return emptyList()
        return buildList {
            for (index in 0 until tokens.length()) {
                tokens.optJSONObject(index)?.optString("tokenId")?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}

data class SyncRunSummary(
    val uploaded: Int,
    val waitingForServer: Int,
    val message: String,
)
