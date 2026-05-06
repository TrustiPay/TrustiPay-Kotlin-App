package app.trustipay.auth.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import app.trustipay.api.ApiResult
import app.trustipay.auth.domain.AuthToken
import kotlinx.coroutines.tasks.await

class AuthRepository(private val tokenStore: TokenStore) {

    private val auth = FirebaseAuth.getInstance()

    suspend fun login(email: String, password: String): ApiResult<AuthToken> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: return ApiResult.AuthError
            val token = buildToken(user)
            tokenStore.save(token)
            ApiResult.Success(token)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            ApiResult.AuthError
        } catch (e: FirebaseAuthInvalidUserException) {
            ApiResult.AuthError
        } catch (e: Exception) {
            ApiResult.NetworkError(e)
        }
    }

    suspend fun register(
        fullName: String,
        email: String,
        phoneNumber: String,
        password: String,
    ): ApiResult<AuthToken> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: return ApiResult.AuthError
            user.updateProfile(userProfileChangeRequest { displayName = fullName }).await()
            val token = buildToken(user, overrideName = fullName)
            tokenStore.save(token)
            ApiResult.Success(token)
        } catch (e: FirebaseAuthUserCollisionException) {
            ApiResult.HttpError(409, "Email already registered")
        } catch (e: FirebaseAuthWeakPasswordException) {
            ApiResult.HttpError(400, "Password must be at least 6 characters")
        } catch (e: Exception) {
            ApiResult.NetworkError(e)
        }
    }

    fun isLoggedIn(): Boolean {
        val fbUser = auth.currentUser ?: return false
        val stored = tokenStore.load()
        if (stored == null || stored.isExpired()) {
            tokenStore.save(buildToken(fbUser))
        }
        return true
    }

    fun logout() {
        auth.signOut()
        tokenStore.clear()
    }

    private fun buildToken(user: FirebaseUser, overrideName: String? = null): AuthToken {
        val name = overrideName
            ?: user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")
            ?: "User"
        return AuthToken(
            accessToken = user.uid,
            refreshToken = null,
            expiresAt = System.currentTimeMillis() / 1000 + 3600,
            userId = user.uid,
            displayName = name,
        )
    }
}
