package app.trustipay.api

import app.trustipay.api.dto.LoginRequest
import app.trustipay.api.dto.LoginResponse
import app.trustipay.api.dto.RefreshRequest
import app.trustipay.api.dto.RefreshResponse
import app.trustipay.api.dto.RegisterRequest
import app.trustipay.api.dto.RegisterResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("auth/login")
    suspend fun loginUser(@Body request: LoginRequest): LoginResponse

    @POST("auth/register")
    suspend fun registerUser(@Body request: RegisterRequest): RegisterResponse

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): RefreshResponse
}
