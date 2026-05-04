package app.trustipay.api.dto

data class DeviceRegistrationRequest(
    val devicePublicKeyId: String,
    val devicePublicKeyBase64: String,
    val platform: String = "android",
)

data class DeviceRegistrationResponse(
    val deviceId: String,
    val registeredAt: String,
)
