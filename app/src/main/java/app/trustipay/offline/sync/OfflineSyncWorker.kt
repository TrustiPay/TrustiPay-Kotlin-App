package app.trustipay.offline.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class OfflineSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val summary = syncRepository.processSync()
        return if (summary.uploaded >= 0) Result.success() else Result.retry()
    }
}
