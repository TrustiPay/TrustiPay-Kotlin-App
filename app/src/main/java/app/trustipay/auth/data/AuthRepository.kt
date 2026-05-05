package app.trustipay.auth.data

import app.trustipay.api.ApiResult
import app.trustipay.api.AuthApiService
import app.trustipay.api.safeApiCall
import app.trustipay.api.dto.LoginRequest
import app.trustipay.api.dto.RegisterRequest
import app.trustipay.auth.domain.AuthToken

class AuthRepository(
    private val apiService: AuthApiService,
    private val tokenStore: TokenStore,
) {
    suspend fun login(email: String, password: String): ApiResult<AuthToken> {
        val result = safeApiCall { apiService.loginUser(LoginRequest(email, password)) }
        if (result is ApiResult.Success) {
            val r = result.data
            val token = AuthToken.fromLoginResponse(r.accessToken, r.refreshToken, r.expiresIn, r.userId, r.displayName)
            tokenStore.save(token)
        }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(tokenStore.load()!!)
            else -> @Suppress("UNCHECKED_CAST") (result as ApiResult<AuthToken>)
        }
    }

    suspend fun register(
        fullName: String,
        email: String,
        phoneNumber: String,
        password: String,
    ): ApiResult<AuthToken> {
        val normalized = normalizePhone(phoneNumber)
        val result = safeApiCall {
            apiService.registerUser(RegisterRequest(fullName, email, normalized, password))
        }
        if (result is ApiResult.Success) {
            val r = result.data
            val token = AuthToken.fromLoginResponse(r.accessToken, r.refreshToken, r.expiresIn, r.userId, r.displayName)
            tokenStore.save(token)
        }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(tokenStore.load()!!)
            else -> @Suppress("UNCHECKED_CAST") (result as ApiResult<AuthToken>)
        }
    }

    fun isLoggedIn(): Boolean = tokenStore.load()?.isExpired() == false

    fun logout() = tokenStore.clear()

    private fun normalizePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.startsWith("94") && digits.length == 11 -> digits
            digits.startsWith("0") && digits.length == 10 -> "94${digits.drop(1)}"
            digits.length == 9 -> "94$digits"
            else -> digits
        }
    }
}
