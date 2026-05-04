package app.trustipay.offline.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

import app.trustipay.offline.data.OfflinePaymentStore
import app.trustipay.offline.domain.SyncQueueStatus
import java.time.Clock

class OfflineSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val poller: SettlementStatusPoller,
    private val store: OfflinePaymentStore,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val summary = syncRepository.processSync()

        val waitingItems = store.listQueue(SyncQueueStatus.WAITING_FOR_SERVER)
        for (item in waitingItems) {
            val success = poller.pollAndApply(item.transactionId)
            if (success) {
                store.updateQueueItem(
                    item.copy(
                        status = SyncQueueStatus.SETTLED,
                        updatedLocalAt = Clock.systemUTC().instant()
                    )
                )
            }
        }

        return if (summary.uploaded >= 0) Result.success() else Result.retry()
    }
}
