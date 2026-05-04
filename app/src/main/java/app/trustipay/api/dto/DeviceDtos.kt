package app.trustipay.api.dto

data class DeviceRegistrationRequest(
    val deviceId: String,
    val deviceName: String,
    val publicSigningKey: String,
    val keyAlgorithm: String = "ECDSA_P256",
    val platform: String = "ANDROID",
)

data class DeviceRegistrationResponse(
    val deviceId: String,
    val publicKeyId: String? = null,
    val status: String? = null,
    val serverTime: String? = null,
    val registeredAt: String? = null,
)
