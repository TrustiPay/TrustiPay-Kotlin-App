package app.trustipay.offline.protocol

import java.security.MessageDigest
import java.util.Base64

object MessageHasher {
    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    fun sha256Base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(bytes))

    fun sha256Hex(bytes: ByteArray): String =
        sha256(bytes).joinToString(separator = "") { byte -> "%02x".format(byte) }
}
