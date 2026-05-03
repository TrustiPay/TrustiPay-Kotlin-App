package app.trustipay.offline.protocol

import app.trustipay.offline.domain.PaymentOffer
import app.trustipay.offline.domain.PaymentOfferToken
import app.trustipay.offline.domain.PaymentRequest
import app.trustipay.offline.domain.TransactionState
import java.time.Clock

interface TokenIssuerVerifier {
    fun verify(token: PaymentOfferToken): Boolean
}

class PublicKeyTokenIssuerVerifier(
    private val signatureVerifier: SignatureVerifier,
) : TokenIssuerVerifier {
    override fun verify(token: PaymentOfferToken): Boolean {
        val payload = runCatching {
            java.util.Base64.getUrlDecoder().decode(token.canonicalPayloadBase64Url)
        }.getOrNull() ?: return false
        return signatureVerifier.verify(token.issuerKeyId, payload, token.serverSignature)
    }
}

object ShadowTokenIssuerVerifier : TokenIssuerVerifier {
    override fun verify(token: PaymentOfferToken): Boolean =
        token.serverSignature.isNotBlank() && token.canonicalPayloadBase64Url.isNotBlank()
}

class TokenValidator(
    private val issuerVerifier: TokenIssuerVerifier,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun validateOfferTokens(
        offer: PaymentOffer,
        request: PaymentRequest,
        locallySpentTokenIds: Set<String>,
    ): TokenValidationResult {
        if (offer.amountMinor != request.amountMinor || offer.currency != request.currency) {
            return TokenValidationResult.rejected(TransactionState.FAILED_AMOUNT_MISMATCH, "Offer amount does not match request.")
        }
        if (offer.receiverDeviceId != request.receiverDeviceId) {
            return TokenValidationResult.rejected(TransactionState.FAILED_RECEIVER_MISMATCH, "Offer receiver does not match request.")
        }
        if (offer.currency != "LKR") {
            return TokenValidationResult.rejected(TransactionState.FAILED_UNSUPPORTED_CURRENCY, "Unsupported offline currency.")
        }

        val now = clock.instant()
        var total = 0L
        val seen = mutableSetOf<String>()
        offer.offlineTokens.forEach { token ->
            if (!seen.add(token.tokenId) || token.tokenId in locallySpentTokenIds) {
                return TokenValidationResult.rejected(
                    TransactionState.FAILED_TOKEN_ALREADY_USED_LOCALLY,
                    "Token ${token.tokenId} was already used locally.",
                )
            }
            if (!token.expiresAtServer.isAfter(now)) {
                return TokenValidationResult.rejected(TransactionState.FAILED_TOKEN_EXPIRED, "Token ${token.tokenId} is expired.")
            }
            if (token.currency != offer.currency) {
                return TokenValidationResult.rejected(TransactionState.FAILED_UNSUPPORTED_CURRENCY, "Token currency mismatch.")
            }
            if (!issuerVerifier.verify(token)) {
                return TokenValidationResult.rejected(TransactionState.FAILED_INVALID_SIGNATURE, "Token signature could not be verified.")
            }
            total += token.amountMinor
        }
        if (total != offer.amountMinor) {
            return TokenValidationResult.rejected(TransactionState.FAILED_AMOUNT_MISMATCH, "Token total does not equal offer amount.")
        }
        return TokenValidationResult(true, null, null)
    }
}

data class TokenValidationResult(
    val accepted: Boolean,
    val failureState: TransactionState?,
    val rejectionReason: String?,
) {
    companion object {
        fun rejected(state: TransactionState, reason: String): TokenValidationResult =
            TokenValidationResult(accepted = false, failureState = state, rejectionReason = reason)
    }
}
