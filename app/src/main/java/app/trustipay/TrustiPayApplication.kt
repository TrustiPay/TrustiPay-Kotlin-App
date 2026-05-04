package app.trustipay

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import app.trustipay.offline.sync.TrustiPayWorkerFactory

class TrustiPayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        WorkManager.initialize(
            this,
            Configuration.Builder()
                .setWorkerFactory(TrustiPayWorkerFactory())
                .build(),
        )
    }
}
