package app.trustipay.offline.sync

import app.trustipay.api.ApiResult
import app.trustipay.api.TrustiPayApiService
import app.trustipay.api.safeApiCall
import app.trustipay.offline.data.OfflinePaymentStore

class SettlementStatusPoller(
    private val store: OfflinePaymentStore,
    private val conflictResolver: SyncConflictResolver = SyncConflictResolver(),
    private val apiService: TrustiPayApiService? = null,
) {
    fun applyServerResult(transactionId: String, result: ServerSettlementResult, reason: String? = null) {
        store.updateTransactionState(
            transactionId = transactionId,
            state = conflictResolver.stateForServerResult(result),
            rejectionReason = reason,
        )
    }

    suspend fun pollAndApply(transactionId: String): Boolean {
        val api = apiService ?: return false
        val result = safeApiCall { api.getSettlementStatus(transactionId) }
        if (result is ApiResult.Success) {
            val status = result.data
            val serverStatus = status.serverStatus ?: status.status
            val settlementResult = when {
                serverStatus == "SETTLED" || serverStatus == "SHADOW_SETTLED" -> ServerSettlementResult.SETTLED
                serverStatus?.startsWith("REJECTED") == true -> when {
                    status.rejectionReason?.contains("double", ignoreCase = true) == true -> ServerSettlementResult.DOUBLE_SPEND
                    status.rejectionReason?.contains("revoked", ignoreCase = true) == true -> ServerSettlementResult.REVOKED_TOKEN
                    status.rejectionReason?.contains("expired", ignoreCase = true) == true -> ServerSettlementResult.EXPIRED_TOKEN
                    else -> ServerSettlementResult.DISPUTED
                }
                else -> return false
            }
            applyServerResult(transactionId, settlementResult, status.rejectionReason)
            return true
        }
        return false
    }
}
