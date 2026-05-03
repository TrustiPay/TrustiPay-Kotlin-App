package app.trustipay.offline.sync

import app.trustipay.offline.domain.TransactionState

class SyncConflictResolver {
    fun stateForServerResult(result: ServerSettlementResult): TransactionState = when (result) {
        ServerSettlementResult.SETTLED -> TransactionState.SETTLED
        ServerSettlementResult.DOUBLE_SPEND -> TransactionState.REJECTED_DOUBLE_SPEND
        ServerSettlementResult.REVOKED_DEVICE -> TransactionState.REJECTED_REVOKED_DEVICE
        ServerSettlementResult.REVOKED_TOKEN -> TransactionState.REJECTED_REVOKED_TOKEN
        ServerSettlementResult.EXPIRED_TOKEN -> TransactionState.REJECTED_EXPIRED_TOKEN
        ServerSettlementResult.DISPUTED -> TransactionState.DISPUTED
    }
}

enum class ServerSettlementResult {
    SETTLED,
    DOUBLE_SPEND,
    REVOKED_DEVICE,
    REVOKED_TOKEN,
    EXPIRED_TOKEN,
    DISPUTED,
}
