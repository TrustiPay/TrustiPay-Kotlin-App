package app.trustipay.offline.protocol

import app.trustipay.offline.domain.TransportType
import java.time.Instant

object LocalHashChain {
    const val GENESIS_HASH: String = "GENESIS"

    fun transactionHash(
        deviceId: String,
        previousHash: String,
        transactionId: String,
        requestHash: String?,
        offerHash: String?,
        receiptHash: String?,
        amountMinor: Long,
        currency: String,
        transportType: TransportType?,
        createdAtDevice: Instant,
    ): String {
        require(deviceId.isNotBlank()) { "deviceId is required for local hash-chain entries." }
        require(previousHash.isNotBlank()) { "previousHash is required for local hash-chain entries." }
        require(transactionId.isNotBlank()) { "transactionId is required for local hash-chain entries." }

        val payload = CanonicalEncoder.encode(
            mapOf(
                "amountMinor" to amountMinor,
                "createdAtDevice" to createdAtDevice,
                "currency" to currency,
                "deviceId" to deviceId,
                "offerHash" to offerHash.orEmpty(),
                "previousHash" to previousHash,
                "receiptHash" to receiptHash.orEmpty(),
                "requestHash" to requestHash.orEmpty(),
                "transactionId" to transactionId,
                "transportType" to (transportType?.name ?: "UNKNOWN"),
            )
        )
        return MessageHasher.sha256Base64Url(payload)
    }

    fun encryptionAad(previousHash: String, aad: ByteArray? = null): ByteArray {
        require(previousHash.isNotBlank()) { "previousHash is required for encrypted local payloads." }
        return CanonicalEncoder.encode(
            mapOf(
                "aad" to (aad ?: byteArrayOf()),
                "previousHash" to previousHash,
                "purpose" to "trustipay-offline-local-encryption",
            )
        )
    }
}
