package app.trustipay.offline.sync

import android.content.Context
import app.trustipay.api.ApiResult
import app.trustipay.api.TrustiPayApiService
import app.trustipay.api.dto.IssuedTokenDto
import app.trustipay.api.dto.TokenIssuanceRequest
import app.trustipay.api.safeApiCall
import app.trustipay.offline.data.OfflinePaymentOpenHelper
import app.trustipay.offline.data.SQLiteOfflinePaymentStore
import app.trustipay.offline.domain.OfflineToken
import app.trustipay.offline.domain.OfflineTokenStatus
import app.trustipay.offline.domain.SecureOfflineIdGenerator
import app.trustipay.offline.protocol.CanonicalEncoder
import app.trustipay.offline.security.DeviceKeyManager
import app.trustipay.offline.security.PublicKeyCache
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

class TokenIssuanceRepository(
    context: Context,
    private val apiService: TrustiPayApiService,
    private val deviceKeyManager: DeviceKeyManager = DeviceKeyManager(),
    publicKeyCache: PublicKeyCache? = null,
) {
    private val store = SQLiteOfflinePaymentStore(OfflinePaymentOpenHelper(context).writableDatabase)
    private val idGenerator = SecureOfflineIdGenerator()
    val sharedPublicKeyCache: PublicKeyCache = publicKeyCache ?: PublicKeyCache()

    suspend fun requestAndStoreTokens(
        requestedAmounts: List<Long>,
        currency: String,
    ): ApiResult<Int> {
        val publicKeyId = deviceKeyManager.getPublicKeyId()
        val nonce = idGenerator.nonce(16)
        val timestamp = Instant.now().toString()

        val canonicalPayload = CanonicalEncoder.encode(
            mapOf(
                "currency" to currency,
                "devicePublicKeyId" to publicKeyId,
                "nonce" to nonce,
                "requestedAmounts" to requestedAmounts,
                "timestamp" to timestamp,
            )
        )
        val signature = deviceKeyManager.signAsBase64Url(canonicalPayload)

        val result = safeApiCall {
            apiService.requestOfflineTokens(
                TokenIssuanceRequest(
                    devicePublicKeyId = publicKeyId,
                    requestedAmounts = requestedAmounts,
                    currency = currency,
                    nonce = nonce,
                    timestamp = timestamp,
                    deviceSignature = signature,
                )
            )
        }

        return when (result) {
            is ApiResult.Success -> {
                val response = result.data
                cacheIssuerPublicKey(response.issuerPublicKeyId, response.issuerPublicKeyBase64)
                val tokens = response.tokens.map { it.toDomain() }
                tokens.forEach { store.upsertToken(it) }
                ApiResult.Success(tokens.size)
            }
            else -> @Suppress("UNCHECKED_CAST") (result as ApiResult<Int>)
        }
    }

    private fun cacheIssuerPublicKey(keyId: String, base64: String) {
        try {
            val keyBytes = Base64.getDecoder().decode(base64)
            val spec = X509EncodedKeySpec(keyBytes)
            val publicKey = KeyFactory.getInstance("EC").generatePublic(spec)
            sharedPublicKeyCache.put(keyId, publicKey)
        } catch (_: Exception) {
        }
    }

    private fun IssuedTokenDto.toDomain(): OfflineToken = OfflineToken(
        tokenId = tokenId,
        ownerUserId = ownerUserId,
        ownerDeviceId = ownerDeviceId,
        amountMinor = amountMinor,
        currency = currency,
        issuedAtServer = Instant.parse(issuedAtServer),
        expiresAtServer = Instant.parse(expiresAtServer),
        issuerKeyId = issuerKeyId,
        nonce = nonce,
        serverSignature = serverSignature,
        canonicalPayload = Base64.getUrlDecoder().decode(canonicalPayload),
        status = OfflineTokenStatus.AVAILABLE,
    )
}
