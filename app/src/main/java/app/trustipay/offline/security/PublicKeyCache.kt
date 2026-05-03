package app.trustipay.offline.security

import java.security.PublicKey

class PublicKeyCache {
    private val keysById = linkedMapOf<String, PublicKey>()

    fun put(publicKeyId: String, publicKey: PublicKey) {
        keysById[publicKeyId] = publicKey
    }

    fun get(publicKeyId: String): PublicKey? = keysById[publicKeyId]

    fun snapshot(): Map<String, PublicKey> = keysById.toMap()
}
