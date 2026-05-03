package app.trustipay.offline.domain

enum class TransportType(val label: String) {
    QR("QR"),
    BLE("Bluetooth"),
    WIFI_DIRECT("Wi-Fi Direct"),
    NFC("NFC"),
}

enum class TransportRole {
    SENDER,
    RECEIVER,
}
