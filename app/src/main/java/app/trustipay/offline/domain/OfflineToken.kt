package app.trustipay.offline.domain

import java.time.Instant

enum class OfflineTokenStatus {
    AVAILABLE,
    RESERVED_FOR_LOCAL_TXN,
    SPENT_PENDING_SYNC,
    SPENT_SYNCED,
    EXPIRED,
    REVOKED,
}

data class OfflineToken(
    val tokenId: String,
    val ownerUserId: String,
    val ownerDeviceId: String,
    val amountMinor: Long,
    val currency: String,
    val issuedAtServer: Instant,
    val expiresAtServer: Instant,
    val issuerKeyId: String,
    val nonce: String,
    val serverSignature: String,
    val canonicalPayload: ByteArray,
    val status: OfflineTokenStatus = OfflineTokenStatus.AVAILABLE,
) {
    init {
        require(tokenId.isNotBlank()) { "tokenId is required." }
        require(ownerUserId.isNotBlank()) { "ownerUserId is required." }
        require(ownerDeviceId.isNotBlank()) { "ownerDeviceId is required." }
        require(amountMinor > 0) { "amountMinor must be positive." }
        require(currency.matches(Regex("[A-Z]{3}"))) { "currency must be a three-letter ISO-style code." }
        require(expiresAtServer.isAfter(issuedAtServer)) { "Token expiry must be after issue time." }
        require(issuerKeyId.isNotBlank()) { "issuerKeyId is required." }
        require(nonce.isNotBlank()) { "nonce is required." }
        require(serverSignature.isNotBlank()) { "serverSignature is required." }
    }

    fun isSpendableAt(now: Instant): Boolean =
        status == OfflineTokenStatus.AVAILABLE && expiresAtServer.isAfter(now)

    fun toOfferToken(): PaymentOfferToken = PaymentOfferToken(
        tokenId = tokenId,
        amountMinor = amountMinor,
        currency = currency,
        expiresAtServer = expiresAtServer,
        issuerKeyId = issuerKeyId,
        serverSignature = serverSignature,
        canonicalPayloadBase64Url = java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(canonicalPayload),
    )
}

data class PaymentOfferToken(
    val tokenId: String,
    val amountMinor: Long,
    val currency: String,
    val expiresAtServer: Instant,
    val issuerKeyId: String,
    val serverSignature: String,
    val canonicalPayloadBase64Url: String,
)
