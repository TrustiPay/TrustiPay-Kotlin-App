package app.trustipay.offline.transport.qr

import app.trustipay.offline.domain.TransportType
import app.trustipay.offline.transport.InMemoryPaymentTransport

class QrPaymentTransport(enabled: Boolean = true) : InMemoryPaymentTransport(TransportType.QR, enabled)
