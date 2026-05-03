package app.trustipay.offline.protocol

import app.trustipay.offline.domain.OfflineToken
import app.trustipay.offline.domain.PaymentOffer
import app.trustipay.offline.domain.PaymentOfferToken
import app.trustipay.offline.domain.PaymentReceipt
import app.trustipay.offline.domain.PaymentRequest

fun PaymentRequest.canonicalBytes(includeSignature: Boolean = false): ByteArray =
    CanonicalEncoder.encode(unsignedCanonicalMap().withOptional("receiverSignature", receiverSignature, includeSignature))

fun PaymentOffer.canonicalBytes(includeSignature: Boolean = false): ByteArray =
    CanonicalEncoder.encode(unsignedCanonicalMap().withOptional("senderSignature", senderSignature, includeSignature))

fun PaymentReceipt.canonicalBytes(includeSignature: Boolean = false): ByteArray =
    CanonicalEncoder.encode(unsignedCanonicalMap().withOptional("receiverSignature", receiverSignature, includeSignature))

fun OfflineToken.issuerCanonicalBytes(): ByteArray = CanonicalEncoder.encode(
    mapOf(
        "amountMinor" to amountMinor,
        "currency" to currency,
        "expiresAtServer" to expiresAtServer,
        "issuedAtServer" to issuedAtServer,
        "issuerKeyId" to issuerKeyId,
        "nonce" to nonce,
        "ownerDeviceId" to ownerDeviceId,
        "ownerUserId" to ownerUserId,
        "tokenId" to tokenId,
    )
)

fun PaymentOfferToken.canonicalTokenMap(): Map<String, Any?> = mapOf(
    "amountMinor" to amountMinor,
    "canonicalPayloadBase64Url" to canonicalPayloadBase64Url,
    "currency" to currency,
    "expiresAtServer" to expiresAtServer,
    "issuerKeyId" to issuerKeyId,
    "serverSignature" to serverSignature,
    "tokenId" to tokenId,
)

private fun PaymentRequest.unsignedCanonicalMap(): Map<String, Any?> = mapOf(
    "amountMinor" to amountMinor,
    "createdAtDevice" to createdAtDevice,
    "currency" to currency,
    "description" to description,
    "expiresAtDevice" to expiresAtDevice,
    "messageType" to messageType,
    "nonce" to nonce,
    "protocolVersion" to protocolVersion,
    "receiverDeviceId" to receiverDeviceId,
    "receiverPublicKeyId" to receiverPublicKeyId,
    "receiverUserAlias" to receiverUserAlias,
    "requestId" to requestId,
    "supportedTransports" to supportedTransports.map { it.name },
)

private fun PaymentOffer.unsignedCanonicalMap(): Map<String, Any?> = mapOf(
    "amountMinor" to amountMinor,
    "createdAtDevice" to createdAtDevice,
    "currency" to currency,
    "messageType" to messageType,
    "nonce" to nonce,
    "offlineTokens" to offlineTokens.map { it.canonicalTokenMap() },
    "protocolVersion" to protocolVersion,
    "receiverDeviceId" to receiverDeviceId,
    "requestHash" to requestHash,
    "requestId" to requestId,
    "senderDeviceId" to senderDeviceId,
    "senderPublicKeyId" to senderPublicKeyId,
    "senderUserAlias" to senderUserAlias,
    "transactionId" to transactionId,
)

private fun PaymentReceipt.unsignedCanonicalMap(): Map<String, Any?> = mapOf(
    "acceptedAtDevice" to acceptedAtDevice,
    "localValidationResult" to localValidationResult.name,
    "messageType" to messageType,
    "nonce" to nonce,
    "offerHash" to offerHash,
    "protocolVersion" to protocolVersion,
    "receiverDeviceId" to receiverDeviceId,
    "requestId" to requestId,
    "senderDeviceId" to senderDeviceId,
    "transactionId" to transactionId,
)

private fun Map<String, Any?>.withOptional(key: String, value: String, include: Boolean): Map<String, Any?> =
    if (include && value.isNotBlank()) this + (key to value) else this
