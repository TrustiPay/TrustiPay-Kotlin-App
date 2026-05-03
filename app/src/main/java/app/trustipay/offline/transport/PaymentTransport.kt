package app.trustipay.offline.transport

import app.trustipay.offline.domain.PaymentSession
import app.trustipay.offline.domain.TransportRole
import app.trustipay.offline.domain.TransportType
import kotlinx.coroutines.flow.Flow

interface PaymentTransport {
    val type: TransportType

    suspend fun isAvailable(): Boolean

    suspend fun startSession(
        role: TransportRole,
        session: PaymentSession,
    ): Result<Unit>

    suspend fun send(
        peerId: String,
        envelope: TransportEnvelope,
    ): Result<Unit>

    fun incomingMessages(): Flow<TransportEnvelope>

    suspend fun close()
}
