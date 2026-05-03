package app.trustipay.offline

import app.trustipay.offline.domain.Money
import app.trustipay.offline.domain.SecureOfflineIdGenerator
import app.trustipay.offline.domain.TransactionState
import app.trustipay.offline.domain.TransportType
import app.trustipay.offline.protocol.JavaSigningKeyFactory
import app.trustipay.offline.protocol.OfflineTokenFactory
import app.trustipay.offline.protocol.PaymentProtocolEngine
import app.trustipay.offline.protocol.PublicKeySignatureVerifier
import app.trustipay.offline.protocol.PublicKeyTokenIssuerVerifier
import app.trustipay.offline.protocol.TokenValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class PaymentProtocolEngineTest {
    private val clock = Clock.fixed(Instant.parse("2026-05-03T10:15:00Z"), ZoneOffset.UTC)
    private val ids = SecureOfflineIdGenerator()
    private val issuer = JavaSigningKeyFactory.generate("issuer-test")
    private val sender = JavaSigningKeyFactory.generate("sender-test")
    private val receiver = JavaSigningKeyFactory.generate("receiver-test")
    private val verifier = PublicKeySignatureVerifier(
        mapOf(
            issuer.publicKeyId to issuer.keyPair.public,
            sender.publicKeyId to sender.keyPair.public,
            receiver.publicKeyId to receiver.keyPair.public,
        )
    )
    private val engine = PaymentProtocolEngine(clock, ids)
    private val tokenFactory = OfflineTokenFactory(ids)
    private val tokenValidator = TokenValidator(PublicKeyTokenIssuerVerifier(verifier), clock)

    @Test
    fun validRequestOfferReceipt_roundTripVerifies() {
        val request = engine.createPaymentRequest(
            receiverUserAlias = "merchant",
            receiverDeviceId = "receiver-device",
            receiverPublicKeyId = receiver.publicKeyId,
            money = Money.lkr(150000),
            description = "Order 123",
            supportedTransports = listOf(TransportType.QR),
            signer = receiver.signer(),
        )
        assertTrue(engine.validatePaymentRequest(request, verifier).accepted)

        val tokens = listOf(100000L, 50000L).map { amount ->
            tokenFactory.issueToken(
                ownerUserId = "sender-user",
                ownerDeviceId = "sender-device",
                amountMinor = amount,
                currency = "LKR",
                issuedAtServer = clock.instant().minus(Duration.ofHours(1)),
                expiresAtServer = clock.instant().plus(Duration.ofDays(3)),
                issuerKeyId = issuer.publicKeyId,
                issuerSigner = issuer.signer(),
            )
        }
        val offer = engine.createPaymentOffer(
            request = request,
            senderUserAlias = "sender",
            senderDeviceId = "sender-device",
            senderPublicKeyId = sender.publicKeyId,
            selectedTokens = tokens,
            signer = sender.signer(),
        )
        assertTrue(engine.validatePaymentOffer(offer, request, verifier, tokenValidator, emptySet()).accepted)

        val receipt = engine.createPaymentReceipt(request, offer, "receiver-device", receiver.signer())
        assertTrue(engine.validatePaymentReceipt(receipt, request, offer, verifier).accepted)
    }

    @Test
    fun tamperedOfferAmount_failsValidation() {
        val request = engine.createPaymentRequest(
            receiverUserAlias = "merchant",
            receiverDeviceId = "receiver-device",
            receiverPublicKeyId = receiver.publicKeyId,
            money = Money.lkr(100000),
            description = "Order 123",
            supportedTransports = listOf(TransportType.QR),
            signer = receiver.signer(),
        )
        val token = tokenFactory.issueToken(
            ownerUserId = "sender-user",
            ownerDeviceId = "sender-device",
            amountMinor = 100000,
            currency = "LKR",
            issuedAtServer = clock.instant().minus(Duration.ofHours(1)),
            expiresAtServer = clock.instant().plus(Duration.ofDays(3)),
            issuerKeyId = issuer.publicKeyId,
            issuerSigner = issuer.signer(),
        )
        val offer = engine.createPaymentOffer(
            request = request,
            senderUserAlias = "sender",
            senderDeviceId = "sender-device",
            senderPublicKeyId = sender.publicKeyId,
            selectedTokens = listOf(token),
            signer = sender.signer(),
        )

        val tampered = offer.copy(amountMinor = 90000)
        val result = engine.validatePaymentOffer(tampered, request, verifier, tokenValidator, emptySet())

        assertFalse(result.accepted)
        assertEquals(TransactionState.FAILED_INVALID_SIGNATURE, result.failureState)
    }
}
