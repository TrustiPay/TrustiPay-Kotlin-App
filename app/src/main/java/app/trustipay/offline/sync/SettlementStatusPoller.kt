package app.trustipay.offline.sync

import app.trustipay.offline.data.OfflinePaymentStore

class SettlementStatusPoller(
    private val store: OfflinePaymentStore,
    private val conflictResolver: SyncConflictResolver = SyncConflictResolver(),
) {
    fun applyServerResult(transactionId: String, result: ServerSettlementResult, reason: String? = null) {
        store.updateTransactionState(
            transactionId = transactionId,
            state = conflictResolver.stateForServerResult(result),
            rejectionReason = reason,
        )
    }
}
