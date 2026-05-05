package app.trustipay.api

import app.trustipay.api.dto.DeviceRegistrationRequest
import app.trustipay.api.dto.DeviceRegistrationResponse
import app.trustipay.api.dto.InitiatePaymentRequest
import app.trustipay.api.dto.OfflineSyncRequest
import app.trustipay.api.dto.OfflineSyncResponse
import app.trustipay.api.dto.PaymentResponse
import app.trustipay.api.dto.SettlementStatusResponse
import app.trustipay.api.dto.TokenIssuanceRequest
import app.trustipay.api.dto.TokenIssuanceResponse
import app.trustipay.api.dto.TransactionHistoryResponse
import app.trustipay.api.dto.WalletResponse
import app.trustipay.api.dto.SummaryReportResponse
import app.trustipay.api.dto.CategoriesReportResponse
import app.trustipay.api.dto.FhsReportResponse
import app.trustipay.api.dto.RecommendationsReportResponse
import app.trustipay.api.dto.BehaviorProfileResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TrustiPayApiService {
    @GET("wallets/me")
    suspend fun getMyWallet(): WalletResponse

    @GET("wallets/me/transactions")
    suspend fun getMyTransactions(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): TransactionHistoryResponse

    @POST("payments")
    suspend fun initiatePayment(
        @Body request: InitiatePaymentRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): PaymentResponse

    @POST("offline/devices/register")
    suspend fun registerDevice(
        @Body request: DeviceRegistrationRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): DeviceRegistrationResponse

    @POST("offline/tokens/request")
    suspend fun requestOfflineTokens(
        @Body request: TokenIssuanceRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): TokenIssuanceResponse

    @POST("offline/sync")
    suspend fun submitOfflineTransaction(
        @Body request: OfflineSyncRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): OfflineSyncResponse

    @GET("offline/sync/status/{transactionId}")
    suspend fun getSettlementStatus(@Path("transactionId") transactionId: String): SettlementStatusResponse

    // ── Analytics ────────────────────────────────────────────────────────────

    @GET("analytics/reports/summary")
    suspend fun getAnalyticsSummary(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("groupBy") groupBy: String = "day"
    ): SummaryReportResponse

    @GET("analytics/reports/categories")
    suspend fun getAnalyticsCategories(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): CategoriesReportResponse

    @GET("analytics/reports/fhs")
    suspend fun getFinancialHealthScore(
        @Query("month") month: String? = null
    ): FhsReportResponse

    @GET("analytics/reports/recommendations")
    suspend fun getRecommendations(
        @Query("month") month: String? = null
    ): RecommendationsReportResponse

    @GET("analytics/reports/behavior-profile")
    suspend fun getBehaviorProfile(
        @Query("month") month: String? = null
    ): BehaviorProfileResponse
}
