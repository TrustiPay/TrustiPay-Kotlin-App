package app.trustipay.offline.transport.ble

import app.trustipay.offline.domain.TransportType
import app.trustipay.offline.transport.InMemoryPaymentTransport

class BlePaymentTransport(enabled: Boolean = false) : InMemoryPaymentTransport(TransportType.BLE, enabled)
