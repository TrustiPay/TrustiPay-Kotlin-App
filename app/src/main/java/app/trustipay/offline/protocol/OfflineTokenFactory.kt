package app.trustipay.offline.protocol

import app.trustipay.offline.domain.OfflineIdGenerator
import app.trustipay.offline.domain.OfflineToken
import app.trustipay.offline.domain.SecureOfflineIdGenerator
import java.time.Instant

class OfflineTokenFactory(
    private val idGenerator: OfflineIdGenerator = SecureOfflineIdGenerator(),
) {
    fun issueToken(
        ownerUserId: String,
        ownerDeviceId: String,
        amountMinor: Long,
        currency: String,
        issuedAtServer: Instant,
        expiresAtServer: Instant,
        issuerKeyId: String,
        issuerSigner: PayloadSigner,
    ): OfflineToken {
        val unsigned = OfflineToken(
            tokenId = idGenerator.newId("tok"),
            ownerUserId = ownerUserId,
            ownerDeviceId = ownerDeviceId,
            amountMinor = amountMinor,
            currency = currency,
            issuedAtServer = issuedAtServer,
            expiresAtServer = expiresAtServer,
            issuerKeyId = issuerKeyId,
            nonce = idGenerator.nonce(),
            serverSignature = "unsigned",
            canonicalPayload = byteArrayOf(0),
        )
        val canonical = unsigned.issuerCanonicalBytes()
        return unsigned.copy(
            serverSignature = issuerSigner.sign(canonical),
            canonicalPayload = canonical,
        )
    }
}
