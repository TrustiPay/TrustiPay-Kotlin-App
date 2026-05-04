package app.trustipay.offline.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.trustipay.api.ApiResult

class TokenRefreshWorker(
    context: Context,
    params: WorkerParameters,
    private val tokenIssuanceRepository: TokenIssuanceRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val result = tokenIssuanceRepository.requestAndStoreTokens(
            requestedAmounts = listOf(100000L, 50000L, 20000L, 10000L, 5000L, 2000L, 1000L),
            currency = "LKR",
        )
        return when (result) {
            is ApiResult.Success -> Result.success()
            is ApiResult.NetworkError -> Result.retry()
            else -> Result.failure()
        }
    }
}
