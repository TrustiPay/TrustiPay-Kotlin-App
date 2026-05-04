package app.trustipay.auth.domain

import android.util.Base64
import org.json.JSONObject

data class AuthToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,
    val userId: String,
    val displayName: String,
) {
    fun isExpired(): Boolean = System.currentTimeMillis() / 1000 > expiresAt - 60

    companion object {
        fun fromLoginResponse(
            accessToken: String,
            refreshToken: String?,
            expiresIn: Int,
            userId: String,
            displayName: String,
        ): AuthToken {
            val expiresAt = System.currentTimeMillis() / 1000 + expiresIn
            return AuthToken(accessToken, refreshToken, expiresAt, userId, displayName)
        }

        private fun parseExp(jwt: String): Long {
            return try {
                val payload = jwt.split(".")[1]
                val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING)
                JSONObject(String(decoded)).getLong("exp")
            } catch (_: Exception) {
                0L
            }
        }
    }
}
