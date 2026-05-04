package app.trustipay.api.dto

data class LoginRequest(
    val email: String,
    val password: String,
)

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Int,
    val userId: String,
    val displayName: String,
)

data class RegisterRequest(
    val fullName: String,
    val email: String,
    val phoneNumber: String,
    val password: String,
)

data class RegisterResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Int,
    val userId: String,
    val displayName: String,
)

data class RefreshRequest(val refreshToken: String)
data class RefreshResponse(val accessToken: String, val expiresIn: Int)
