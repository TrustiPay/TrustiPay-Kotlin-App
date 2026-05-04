package app.trustipay.offline.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.trustipay.offline.protocol.LocalHashChain
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EncryptedPayload(
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val previousHash: String? = null,
)

class LocalEncryptionService(
    private val keyAlias: String = DefaultAlias,
) {
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
    }

    fun encrypt(plaintext: ByteArray, aad: ByteArray? = null): EncryptedPayload {
        ensureKey()
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        aad?.let(cipher::updateAAD)
        return EncryptedPayload(
            iv = cipher.iv,
            ciphertext = cipher.doFinal(plaintext),
        )
    }

    fun encryptWithPreviousHash(
        plaintext: ByteArray,
        previousHash: String,
        aad: ByteArray? = null,
    ): EncryptedPayload {
        val chainAad = LocalHashChain.encryptionAad(previousHash, aad)
        val encrypted = encrypt(plaintext, chainAad)
        return encrypted.copy(previousHash = previousHash)
    }

    fun decrypt(payload: EncryptedPayload, aad: ByteArray? = null): ByteArray {
        ensureKey()
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TagBits, payload.iv))
        aad?.let(cipher::updateAAD)
        return cipher.doFinal(payload.ciphertext)
    }

    fun decryptWithPreviousHash(
        payload: EncryptedPayload,
        previousHash: String = payload.previousHash ?: error("previousHash is required."),
        aad: ByteArray? = null,
    ): ByteArray =
        decrypt(payload, LocalHashChain.encryptionAad(previousHash, aad))

    fun EncryptedPayload.encodeForStorage(): ByteArray {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return listOf(
            "v1",
            previousHash.orEmpty(),
            encoder.encodeToString(iv),
            encoder.encodeToString(ciphertext),
        ).joinToString(".").toByteArray(Charsets.UTF_8)
    }

    private fun ensureKey() {
        if (keyStore.containsAlias(keyAlias)) return
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        generator.generateKey()
    }

    private val secretKey: SecretKey
        get() = keyStore.getKey(keyAlias, null) as SecretKey

    companion object {
        private const val AndroidKeyStore = "AndroidKeyStore"
        private const val Transformation = "AES/GCM/NoPadding"
        private const val TagBits = 128
        private const val DefaultAlias = "trustipay_offline_local_data_key"
    }
}
