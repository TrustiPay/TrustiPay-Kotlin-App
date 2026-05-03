package app.trustipay.offline.domain

import java.security.SecureRandom
import java.util.Base64

interface OfflineIdGenerator {
    fun newId(prefix: String): String
    fun nonce(byteCount: Int = 16): String
}

class SecureOfflineIdGenerator(
    private val random: SecureRandom = SecureRandom(),
) : OfflineIdGenerator {
    override fun newId(prefix: String): String = "${prefix}_${nonce(12)}"

    override fun nonce(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
