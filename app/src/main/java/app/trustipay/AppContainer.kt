package app.trustipay

import android.app.Application
import app.trustipay.api.ApiClientFactory
import app.trustipay.api.AuthApiService
import app.trustipay.api.TrustiPayApiService
import app.trustipay.auth.data.AuthRepository
import app.trustipay.auth.data.TokenStore
import app.trustipay.offline.sync.KeyRegistrationRepository
import app.trustipay.offline.sync.TokenIssuanceRepository
import app.trustipay.online.data.OnlinePaymentRepository
import app.trustipay.analytics.data.AnalyticsRepository

object AppContainer {
    lateinit var tokenStore: TokenStore
        private set
    lateinit var authApiService: AuthApiService
        private set
    lateinit var apiService: TrustiPayApiService
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var onlinePaymentRepository: OnlinePaymentRepository
        private set
    lateinit var tokenIssuanceRepository: TokenIssuanceRepository
        private set
    lateinit var keyRegistrationRepository: KeyRegistrationRepository
        private set
    lateinit var analyticsRepository: AnalyticsRepository
        private set

    fun init(application: Application) {
        tokenStore = TokenStore(application)
        authApiService = ApiClientFactory.createAuthService()
        apiService = ApiClientFactory.create(tokenStore)
        authRepository = AuthRepository(tokenStore)
        onlinePaymentRepository = OnlinePaymentRepository(apiService)
        tokenIssuanceRepository = TokenIssuanceRepository(application, apiService)
        keyRegistrationRepository = KeyRegistrationRepository(application, apiService)
        analyticsRepository = AnalyticsRepository(apiService)
    }
}
