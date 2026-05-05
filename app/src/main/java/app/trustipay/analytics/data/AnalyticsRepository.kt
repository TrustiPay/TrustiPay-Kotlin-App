package app.trustipay.analytics.data

import app.trustipay.api.TrustiPayApiService
import app.trustipay.api.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnalyticsRepository(private val apiService: TrustiPayApiService) {

    suspend fun getSummary(from: String? = null, to: String? = null, groupBy: String = "day"): Result<SummaryReportResponse> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiService.getAnalyticsSummary(from, to, groupBy))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCategories(from: String? = null, to: String? = null): Result<CategoriesReportResponse> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiService.getAnalyticsCategories(from, to))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFhs(month: String? = null): Result<FhsReportResponse> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiService.getFinancialHealthScore(month))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecommendations(month: String? = null): Result<RecommendationsReportResponse> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiService.getRecommendations(month))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBehaviorProfile(month: String? = null): Result<BehaviorProfileResponse> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiService.getBehaviorProfile(month))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
