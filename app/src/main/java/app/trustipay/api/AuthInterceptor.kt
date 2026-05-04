package app.trustipay.api

import app.trustipay.auth.data.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    private val noAuthPaths = setOf("auth/login", "auth/register", "auth/refresh")

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.pathSegments.takeLast(2).joinToString("/")
        if (noAuthPaths.any { path.endsWith(it) }) {
            return chain.proceed(request)
        }
        val token = tokenStore.load()?.accessToken
        val newRequest = if (token != null) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            request
        }
        return chain.proceed(newRequest)
    }
}
