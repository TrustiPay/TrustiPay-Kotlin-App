package app.trustipay.offline.transport.nfc

import app.trustipay.offline.domain.TransportType
import app.trustipay.offline.transport.InMemoryPaymentTransport

class NfcPaymentTransport(enabled: Boolean = false) : InMemoryPaymentTransport(TransportType.NFC, enabled)
