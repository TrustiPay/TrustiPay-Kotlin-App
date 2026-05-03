package app.trustipay.offline.domain

import java.time.Instant

data class PaymentSession(
    val sessionId: String,
    val role: TransportRole,
    val transportType: TransportType,
    val createdAtDevice: Instant,
    val expiresAtDevice: Instant,
    val transactionId: String? = null,
    val peerDeviceId: String? = null,
) {
    fun isExpired(now: Instant): Boolean = !expiresAtDevice.isAfter(now)
}
