package app.trustipay.offline.transport

import app.trustipay.offline.domain.OfflineIdGenerator
import app.trustipay.offline.domain.PaymentSession
import app.trustipay.offline.domain.SecureOfflineIdGenerator
import app.trustipay.offline.domain.TransportRole
import app.trustipay.offline.domain.TransportType
import java.time.Clock
import java.time.Duration

class TransportSessionManager(
    private val idGenerator: OfflineIdGenerator = SecureOfflineIdGenerator(),
    private val clock: Clock = Clock.systemUTC(),
) {
    fun createSession(
        role: TransportRole,
        transportType: TransportType,
        transactionId: String? = null,
        peerDeviceId: String? = null,
        validFor: Duration = Duration.ofMinutes(10),
    ): PaymentSession {
        val now = clock.instant()
        return PaymentSession(
            sessionId = idGenerator.newId("session"),
            role = role,
            transportType = transportType,
            createdAtDevice = now,
            expiresAtDevice = now.plus(validFor),
            transactionId = transactionId,
            peerDeviceId = peerDeviceId,
        )
    }
}
