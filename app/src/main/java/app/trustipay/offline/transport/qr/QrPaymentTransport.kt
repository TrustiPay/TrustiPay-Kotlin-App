package app.trustipay.offline.transport.qr

import android.graphics.Bitmap
import app.trustipay.offline.domain.PaymentSession
import app.trustipay.offline.domain.TransportRole
import app.trustipay.offline.domain.TransportType
import app.trustipay.offline.transport.PaymentTransport
import app.trustipay.offline.transport.TransportEnvelope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class QrPaymentTransport(
    private val generator: QrCodeGenerator = QrCodeGenerator(),
    private val enabled: Boolean = true,
) : PaymentTransport {
    override val type: TransportType = TransportType.QR

    private val _incomingMessages = MutableSharedFlow<TransportEnvelope>(extraBufferCapacity = 64)
    private val _outgoingBitmaps = MutableStateFlow<List<Bitmap>>(emptyList())
    private val _currentBitmapIndex = MutableStateFlow(0)

    val outgoingBitmaps: StateFlow<List<Bitmap>> = _outgoingBitmaps.asStateFlow()
    val currentBitmapIndex: StateFlow<Int> = _currentBitmapIndex.asStateFlow()

    override suspend fun isAvailable(): Boolean = enabled

    override suspend fun startSession(role: TransportRole, session: PaymentSession): Result<Unit> {
        _outgoingBitmaps.value = emptyList()
        _currentBitmapIndex.value = 0
        return Result.success(Unit)
    }

    override suspend fun send(peerId: String, envelope: TransportEnvelope): Result<Unit> {
        return runCatching {
            val bitmap = generator.generate(envelope)
            _outgoingBitmaps.value = _outgoingBitmaps.value + bitmap
        }
    }

    fun feedScannedString(raw: String) {
        val envelope = generator.decodeEnvelopeFromString(raw) ?: return
        _incomingMessages.tryEmit(envelope)
    }

    fun advanceBitmapIndex() {
        val count = _outgoingBitmaps.value.size
        if (count > 0) {
            _currentBitmapIndex.value = (_currentBitmapIndex.value + 1) % count
        }
    }

    override fun incomingMessages(): Flow<TransportEnvelope> = _incomingMessages.asSharedFlow()

    override suspend fun close() {
        _outgoingBitmaps.value = emptyList()
        _currentBitmapIndex.value = 0
    }
}
