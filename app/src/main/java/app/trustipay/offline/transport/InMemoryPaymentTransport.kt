package app.trustipay.offline.transport

import app.trustipay.offline.domain.PaymentSession
import app.trustipay.offline.domain.TransportRole
import app.trustipay.offline.domain.TransportType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

open class InMemoryPaymentTransport(
    override val type: TransportType,
    private val enabled: Boolean = true,
) : PaymentTransport {
    private val messages = MutableSharedFlow<TransportEnvelope>(extraBufferCapacity = 64)
    private var open = false

    override suspend fun isAvailable(): Boolean = enabled

    override suspend fun startSession(role: TransportRole, session: PaymentSession): Result<Unit> {
        return if (enabled && session.role == role && session.transportType == type) {
            open = true
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("$type is not available for this session."))
        }
    }

    override suspend fun send(peerId: String, envelope: TransportEnvelope): Result<Unit> {
        if (!enabled || !open) return Result.failure(IllegalStateException("$type session is not open."))
        messages.tryEmit(envelope)
        return Result.success(Unit)
    }

    override fun incomingMessages(): Flow<TransportEnvelope> = messages

    override suspend fun close() {
        open = false
    }
}
