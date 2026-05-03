package app.trustipay.offline.security

class RevocationListStore {
    private val revokedTokenIds = linkedSetOf<String>()
    private val revokedDeviceIds = linkedSetOf<String>()
    private val revokedPublicKeyIds = linkedSetOf<String>()

    fun replaceRevokedTokens(tokenIds: Collection<String>) {
        revokedTokenIds.clear()
        revokedTokenIds.addAll(tokenIds)
    }

    fun replaceRevokedDevices(deviceIds: Collection<String>) {
        revokedDeviceIds.clear()
        revokedDeviceIds.addAll(deviceIds)
    }

    fun replaceRevokedPublicKeys(publicKeyIds: Collection<String>) {
        revokedPublicKeyIds.clear()
        revokedPublicKeyIds.addAll(publicKeyIds)
    }

    fun isTokenRevoked(tokenId: String): Boolean = tokenId in revokedTokenIds
    fun isDeviceRevoked(deviceId: String): Boolean = deviceId in revokedDeviceIds
    fun isPublicKeyRevoked(publicKeyId: String): Boolean = publicKeyId in revokedPublicKeyIds
}
