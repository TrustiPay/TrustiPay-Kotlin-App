package app.trustipay.api

import app.trustipay.api.dto.DeviceRegistrationRequest
import app.trustipay.api.dto.DeviceRegistrationResponse
import app.trustipay.api.dto.InitiatePaymentRequest
import app.trustipay.api.dto.LoginRequest
import app.trustipay.api.dto.LoginResponse
import app.trustipay.api.dto.OfflineSyncRequest
import app.trustipay.api.dto.OfflineSyncResponse
import app.trustipay.api.dto.PaymentResponse
import app.trustipay.api.dto.RefreshRequest
import app.trustipay.api.dto.RefreshResponse
import app.trustipay.api.dto.RegisterRequest
import app.trustipay.api.dto.RegisterResponse
import app.trustipay.api.dto.SettlementStatusResponse
import app.trustipay.api.dto.TokenIssuanceRequest
import app.trustipay.api.dto.TokenIssuanceResponse
import app.trustipay.api.dto.TransactionHistoryResponse
import app.trustipay.api.dto.WalletResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TrustiPayApiService {
    @POST("auth/login")
    suspend fun loginUser(@Body request: LoginRequest): LoginResponse

    @POST("auth/register")
    suspend fun registerUser(@Body request: RegisterRequest): RegisterResponse

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): RefreshResponse

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

    @POST("devices/register")
    suspend fun registerDevice(@Body request: DeviceRegistrationRequest): DeviceRegistrationResponse

    @POST("offline/tokens/issue")
    suspend fun requestOfflineTokens(@Body request: TokenIssuanceRequest): TokenIssuanceResponse

    @POST("offline/sync")
    suspend fun submitOfflineTransaction(
        @Body request: OfflineSyncRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): OfflineSyncResponse

    @GET("offline/sync/{transactionId}")
    suspend fun getSettlementStatus(@Path("transactionId") transactionId: String): SettlementStatusResponse
}
