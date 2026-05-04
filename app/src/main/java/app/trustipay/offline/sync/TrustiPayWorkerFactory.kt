package app.trustipay.offline.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import app.trustipay.AppContainer
import app.trustipay.offline.data.OfflinePaymentOpenHelper
import app.trustipay.offline.data.SQLiteOfflinePaymentStore
import app.trustipay.offline.OfflineFeatureFlagProvider
import app.trustipay.offline.domain.SecureOfflineIdGenerator
import java.time.Clock

class TrustiPayWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        return when (workerClassName) {
            OfflineSyncWorker::class.java.name -> {
                val store = SQLiteOfflinePaymentStore(OfflinePaymentOpenHelper(appContext).writableDatabase)
                val flags = OfflineFeatureFlagProvider.current
                val syncRepo = SyncRepository(
                    store = store,
                    flags = flags,
                    idGenerator = SecureOfflineIdGenerator(),
                    clock = Clock.systemUTC(),
                    apiService = AppContainer.apiService,
                    deviceKeyManager = app.trustipay.offline.security.DeviceKeyManager(),
                )
                val poller = SettlementStatusPoller(
                    store = store,
                    apiService = AppContainer.apiService,
                )
                OfflineSyncWorker(appContext, workerParameters, syncRepo, poller, store)
            }
            TokenRefreshWorker::class.java.name -> {
                TokenRefreshWorker(appContext, workerParameters, AppContainer.tokenIssuanceRepository)
            }
            else -> null
        }
    }
}
