package app.trustipay.offline.data

import app.trustipay.offline.domain.KnownSpentToken
import app.trustipay.offline.domain.OfflineToken
import app.trustipay.offline.domain.OfflineTransaction
import app.trustipay.offline.domain.SyncQueueItem
import app.trustipay.offline.domain.SyncQueueStatus
import app.trustipay.offline.domain.TransactionState
import java.time.Instant

class InMemoryOfflinePaymentStore : OfflinePaymentStore {
    private val tokens = linkedMapOf<String, OfflineToken>()
    private val transactions = linkedMapOf<String, OfflineTransaction>()
    private val queue = linkedMapOf<String, SyncQueueItem>()
    private val knownSpentTokens = linkedMapOf<String, KnownSpentToken>()

    override fun upsertToken(token: OfflineToken) {
        tokens[token.tokenId] = token
    }

    override fun listTokens(): List<OfflineToken> = tokens.values.toList()

    override fun updateToken(token: OfflineToken) {
        require(tokens.containsKey(token.tokenId)) { "Unknown token: ${token.tokenId}" }
        tokens[token.tokenId] = token
    }

    override fun upsertTransaction(transaction: OfflineTransaction) {
        transactions[transaction.transactionId] = transaction
    }

    override fun listTransactions(): List<OfflineTransaction> =
        transactions.values.sortedByDescending { it.updatedLocalAt }

    override fun updateTransactionState(transactionId: String, state: TransactionState, rejectionReason: String?) {
        val transaction = transactions[transactionId] ?: error("Unknown transaction: $transactionId")
        transactions[transactionId] = transaction.copy(
            state = state,
            rejectionReason = rejectionReason,
            updatedLocalAt = Instant.now(),
        )
    }

    override fun enqueue(item: SyncQueueItem) {
        queue[item.queueId] = item
    }

    override fun listQueue(status: SyncQueueStatus?): List<SyncQueueItem> =
        queue.values
            .filter { status == null || it.status == status }
            .sortedBy { it.createdLocalAt }

    override fun updateQueueItem(item: SyncQueueItem) {
        require(queue.containsKey(item.queueId)) { "Unknown queue item: ${item.queueId}" }
        queue[item.queueId] = item
    }

    override fun recordKnownSpentToken(token: KnownSpentToken) {
        knownSpentTokens[token.tokenId] = token
    }

    override fun knownSpentTokenIds(): Set<String> = knownSpentTokens.keys
}
