package app.trustipay.offline.security

data class DevicePublicKeyInfo(
    val publicKeyId: String,
)

class DeviceKeyManager(
    private val signer: AndroidKeystoreSigner = AndroidKeystoreSigner(),
) {
    fun hasDeviceKey(): Boolean = signer.hasKey()

    fun generateDeviceKey(): DevicePublicKeyInfo {
        signer.ensureKeyPair()
        return DevicePublicKeyInfo(publicKeyId = signer.publicKeyId)
    }

    fun sign(payload: ByteArray): ByteArray =
        java.util.Base64.getUrlDecoder().decode(signer.sign(payload))

    fun signAsBase64Url(payload: ByteArray): String = signer.sign(payload)

    fun getPublicKeyId(): String {
        signer.ensureKeyPair()
        return signer.publicKeyId
    }

    fun getPublicKeyBytes(): ByteArray = signer.publicKeyBytes
}
