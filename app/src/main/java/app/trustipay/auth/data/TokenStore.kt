package app.trustipay.auth.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.trustipay.auth.domain.AuthToken

class TokenStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "trustipay_token_store",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(token: AuthToken) {
        prefs.edit()
            .putString("access_token", token.accessToken)
            .putString("refresh_token", token.refreshToken)
            .putLong("expires_at", token.expiresAt)
            .putString("user_id", token.userId)
            .putString("display_name", token.displayName)
            .apply()
    }

    fun load(): AuthToken? {
        val accessToken = prefs.getString("access_token", null) ?: return null
        val userId = prefs.getString("user_id", null) ?: return null
        val displayName = prefs.getString("display_name", null) ?: ""
        val expiresAt = prefs.getLong("expires_at", 0L)
        val refreshToken = prefs.getString("refresh_token", null)
        return AuthToken(accessToken, refreshToken, expiresAt, userId, displayName)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
