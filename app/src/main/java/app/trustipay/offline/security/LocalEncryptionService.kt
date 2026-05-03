package app.trustipay.offline.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EncryptedPayload(
    val iv: ByteArray,
    val ciphertext: ByteArray,
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

    fun decrypt(payload: EncryptedPayload, aad: ByteArray? = null): ByteArray {
        ensureKey()
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TagBits, payload.iv))
        aad?.let(cipher::updateAAD)
        return cipher.doFinal(payload.ciphertext)
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
