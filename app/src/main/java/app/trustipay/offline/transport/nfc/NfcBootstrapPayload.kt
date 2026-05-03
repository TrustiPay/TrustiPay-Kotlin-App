package app.trustipay.offline.transport.nfc

import app.trustipay.offline.domain.TransportType

data class NfcBootstrapPayload(
    val type: String = "OFFLINE_PAYMENT_REQUEST_BOOTSTRAP",
    val protocolVersion: Int,
    val receiverDeviceId: String,
    val receiverPublicKeyId: String,
    val amountMinor: Long,
    val currency: String,
    val sessionId: String,
    val supportedTransports: List<TransportType>,
)
