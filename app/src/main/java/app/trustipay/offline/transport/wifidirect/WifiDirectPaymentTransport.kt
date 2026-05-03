package app.trustipay.offline.transport.wifidirect

import app.trustipay.offline.domain.TransportType
import app.trustipay.offline.transport.InMemoryPaymentTransport

class WifiDirectPaymentTransport(enabled: Boolean = false) : InMemoryPaymentTransport(TransportType.WIFI_DIRECT, enabled)
