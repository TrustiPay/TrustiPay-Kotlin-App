package app.trustipay

import android.app.Application
import app.trustipay.api.ApiClientFactory
import app.trustipay.api.TrustiPayApiService
import app.trustipay.auth.data.AuthRepository
import app.trustipay.auth.data.TokenStore
import app.trustipay.offline.sync.KeyRegistrationRepository
import app.trustipay.offline.sync.TokenIssuanceRepository
import app.trustipay.online.data.OnlinePaymentRepository

object AppContainer {
    lateinit var tokenStore: TokenStore
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

    fun init(application: Application) {
        tokenStore = TokenStore(application)
        apiService = ApiClientFactory.create(tokenStore)
        authRepository = AuthRepository(apiService, tokenStore)
        onlinePaymentRepository = OnlinePaymentRepository(apiService)
        tokenIssuanceRepository = TokenIssuanceRepository(application, apiService)
        keyRegistrationRepository = KeyRegistrationRepository(application, apiService)
    }
}
