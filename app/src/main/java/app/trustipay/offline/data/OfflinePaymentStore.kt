package app.trustipay.offline.data

import app.trustipay.offline.domain.KnownSpentToken
import app.trustipay.offline.domain.OfflineToken
import app.trustipay.offline.domain.OfflineTransaction
import app.trustipay.offline.domain.SyncQueueItem
import app.trustipay.offline.domain.SyncQueueStatus
import app.trustipay.offline.domain.TransactionState

interface OfflinePaymentStore {
    fun upsertToken(token: OfflineToken)
    fun listTokens(): List<OfflineToken>
    fun updateToken(token: OfflineToken)

    fun upsertTransaction(transaction: OfflineTransaction)
    fun listTransactions(): List<OfflineTransaction>
    fun updateTransactionState(transactionId: String, state: TransactionState, rejectionReason: String? = null)

    fun enqueue(item: SyncQueueItem)
    fun listQueue(status: SyncQueueStatus? = null): List<SyncQueueItem>
    fun updateQueueItem(item: SyncQueueItem)

    fun recordKnownSpentToken(token: KnownSpentToken)
    fun knownSpentTokenIds(): Set<String>
}
