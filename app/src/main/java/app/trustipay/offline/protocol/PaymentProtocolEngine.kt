package app.trustipay.offline.protocol

import app.trustipay.offline.domain.LocalValidationResult
import app.trustipay.offline.domain.Money
import app.trustipay.offline.domain.OfflineIdGenerator
import app.trustipay.offline.domain.OfflineToken
import app.trustipay.offline.domain.PaymentOffer
import app.trustipay.offline.domain.PaymentReceipt
import app.trustipay.offline.domain.PaymentRequest
import app.trustipay.offline.domain.SecureOfflineIdGenerator
import app.trustipay.offline.domain.TransactionState
import app.trustipay.offline.domain.TransportType
import java.time.Clock
import java.time.Duration

class PaymentProtocolEngine(
    private val clock: Clock = Clock.systemUTC(),
    private val idGenerator: OfflineIdGenerator = SecureOfflineIdGenerator(),
) {
    fun createPaymentRequest(
        receiverUserAlias: String,
        receiverDeviceId: String,
        receiverPublicKeyId: String,
        money: Money,
        description: String,
        supportedTransports: List<TransportType>,
        signer: PayloadSigner,
        validFor: Duration = Duration.ofMinutes(5),
    ): PaymentRequest {
        require(receiverPublicKeyId == signer.publicKeyId) { "Receiver signer does not match public key ID." }
        require(supportedTransports.isNotEmpty()) { "At least one transport must be supported." }
        val createdAt = clock.instant()
        val unsigned = PaymentRequest(
            protocolVersion = ProtocolVersioning.CURRENT_VERSION,
            messageType = ProtocolVersioning.PAYMENT_REQUEST,
            requestId = idGenerator.newId("req"),
            receiverUserAlias = receiverUserAlias,
            receiverDeviceId = receiverDeviceId,
            receiverPublicKeyId = receiverPublicKeyId,
            amountMinor = money.amountMinor,
            currency = money.currency,
            description = description,
            createdAtDevice = createdAt,
            expiresAtDevice = createdAt.plus(validFor),
            nonce = idGenerator.nonce(),
            supportedTransports = supportedTransports,
        )
        return unsigned.copy(receiverSignature = signer.sign(unsigned.canonicalBytes()))
    }

    fun validatePaymentRequest(
        request: PaymentRequest,
        verifier: SignatureVerifier,
    ): ProtocolValidation {
        if (request.protocolVersion != ProtocolVersioning.CURRENT_VERSION || request.messageType != ProtocolVersioning.PAYMENT_REQUEST) {
            return ProtocolValidation.rejected(TransactionState.FAILED_INVALID_SIGNATURE, "Unsupported payment request protocol.")
        }
        if (request.amountMinor <= 0) {
            return ProtocolValidation.rejected(TransactionState.FAILED_AMOUNT_MISMATCH, "Payment request amount must be positive.")
        }
        if (!request.expiresAtDevice.isAfter(clock.instant())) {
            return ProtocolValidation.rejected(TransactionState.FAILED_TRANSPORT_INTERRUPTED, "Payment request has expired.")
        }
        if (request.supportedTransports.isEmpty()) {
            return ProtocolValidation.rejected(TransactionState.FAILED_TRANSPORT_INTERRUPTED, "Payment request has no transport.")
        }
        if (!verifier.verify(request.receiverPublicKeyId, request.canonicalBytes(), request.receiverSignature)) {
            return ProtocolValidation.rejected(TransactionState.FAILED_INVALID_SIGNATURE, "Payment request signature is invalid.")
        }
        return ProtocolValidation.accepted()
    }

    fun createPaymentOffer(
        request: PaymentRequest,
        senderUserAlias: String,
        senderDeviceId: String,
        senderPublicKeyId: String,
        selectedTokens: List<OfflineToken>,
        signer: PayloadSigner,
    ): PaymentOffer {
        require(senderPublicKeyId == signer.publicKeyId) { "Sender signer does not match public key ID." }
        val tokenTotal = selectedTokens.sumOf { it.amountMinor }
        require(tokenTotal == request.amountMinor) { "Selected token total must equal request amount." }
        require(selectedTokens.all { it.currency == request.currency }) { "Token currency must match request currency." }

        val unsigned = PaymentOffer(
            protocolVersion = ProtocolVersioning.CURRENT_VERSION,
            messageType = ProtocolVersioning.PAYMENT_OFFER,
            transactionId = idGenerator.newId("txn"),
            requestId = request.requestId,
            senderUserAlias = senderUserAlias,
            senderDeviceId = senderDeviceId,
            senderPublicKeyId = senderPublicKeyId,
            receiverDeviceId = request.receiverDeviceId,
            amountMinor = request.amountMinor,
            currency = request.currency,
            offlineTokens = selectedTokens.map { it.toOfferToken() },
            requestHash = MessageHasher.sha256Base64Url(request.canonicalBytes(includeSignature = true)),
            createdAtDevice = clock.instant(),
            nonce = idGenerator.nonce(),
        )
        return unsigned.copy(senderSignature = signer.sign(unsigned.canonicalBytes()))
    }

    fun validatePaymentOffer(
        offer: PaymentOffer,
        request: PaymentRequest,
        verifier: SignatureVerifier,
        tokenValidator: TokenValidator,
        locallySpentTokenIds: Set<String>,
    ): ProtocolValidation {
        if (offer.protocolVersion != ProtocolVersioning.CURRENT_VERSION || offer.messageType != ProtocolVersioning.PAYMENT_OFFER) {
            return ProtocolValidation.rejected(TransactionState.FAILED_INVALID_SIGNATURE, "Unsupported payment offer protocol.")
        }
        if (offer.requestId != request.requestId) {
            return ProtocolValidation.rejected(TransactionState.FAILED_RECEIVER_MISMATCH, "Offer request ID mismatch.")
        }
        val expectedRequestHash = MessageHasher.sha256Base64Url(request.canonicalBytes(includeSignature = true))
        if (offer.requestHash != expectedRequestHash) {
            return ProtocolValidation.rejected(TransactionState.FAILED_RECEIVER_MISMATCH, "Offer request hash mismatch.")
        }
        if (!verifier.verify(offer.senderPublicKeyId, offer.canonicalBytes(), offer.senderSignature)) {
            return ProtocolValidation.rejected(TransactionState.FAILED_INVALID_SIGNATURE, "Payment offer signature is invalid.")
        }
        val tokenResult = tokenValidator.validateOfferTokens(offer, request, locallySpentTokenIds)
        if (!tokenResult.accepted) {
            return ProtocolValidation.rejected(tokenResult.failureState ?: TransactionState.FAILED_INVALID_SIGNATURE, tokenResult.rejectionReason.orEmpty())
        }
        return ProtocolValidation.accepted()
    }

    fun createPaymentReceipt(
        request: PaymentRequest,
        offer: PaymentOffer,
        receiverDeviceId: String,
        signer: PayloadSigner,
    ): PaymentReceipt {
        require(receiverDeviceId == request.receiverDeviceId) { "Receipt receiver must match request." }
        val unsigned = PaymentReceipt(
            protocolVersion = ProtocolVersioning.CURRENT_VERSION,
            messageType = ProtocolVersioning.PAYMENT_RECEIPT,
            transactionId = offer.transactionId,
            requestId = request.requestId,
            receiverDeviceId = receiverDeviceId,
            senderDeviceId = offer.senderDeviceId,
            offerHash = MessageHasher.sha256Base64Url(offer.canonicalBytes(includeSignature = true)),
            acceptedAtDevice = clock.instant(),
            localValidationResult = LocalValidationResult.ACCEPTED_PENDING_SYNC,
            nonce = idGenerator.nonce(),
        )
        return unsigned.copy(receiverSignature = signer.sign(unsigned.canonicalBytes()))
    }

    fun validatePaymentReceipt(
        receipt: PaymentReceipt,
        request: PaymentRequest,
        offer: PaymentOffer,
        verifier: SignatureVerifier,
    ): ProtocolValidation {
        if (receipt.protocolVersion != ProtocolVersioning.CURRENT_VERSION || receipt.messageType != ProtocolVersioning.PAYMENT_RECEIPT) {
            return ProtocolValidation.rejected(TransactionState.FAILED_INVALID_SIGNATURE, "Unsupported payment receipt protocol.")
        }
        if (receipt.transactionId != offer.transactionId || receipt.requestId != request.requestId) {
            return ProtocolValidation.rejected(TransactionState.FAILED_RECEIVER_MISMATCH, "Receipt transaction mismatch.")
        }
        if (receipt.offerHash != MessageHasher.sha256Base64Url(offer.canonicalBytes(includeSignature = true))) {
            return ProtocolValidation.rejected(TransactionState.FAILED_RECEIVER_MISMATCH, "Receipt offer hash mismatch.")
        }
        if (!verifier.verify(request.receiverPublicKeyId, receipt.canonicalBytes(), receipt.receiverSignature)) {
            return ProtocolValidation.rejected(TransactionState.FAILED_INVALID_SIGNATURE, "Payment receipt signature is invalid.")
        }
        return ProtocolValidation.accepted()
    }
}

data class ProtocolValidation(
    val accepted: Boolean,
    val failureState: TransactionState?,
    val reason: String?,
) {
    companion object {
        fun accepted(): ProtocolValidation = ProtocolValidation(true, null, null)

        fun rejected(state: TransactionState, reason: String): ProtocolValidation =
            ProtocolValidation(false, state, reason)
    }
}
