package app.trustipay.offline.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.trustipay.offline.protocol.MessageHasher
import app.trustipay.offline.protocol.PayloadSigner
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class AndroidKeystoreSigner(
    private val keyAlias: String = DefaultAlias,
) : PayloadSigner {
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
    }

    override val publicKeyId: String
        get() = "device_${MessageHasher.sha256Hex(publicKey.encoded).take(16)}"

    override fun sign(payload: ByteArray): String {
        ensureKeyPair()
        val signature = Signature.getInstance(SignatureAlgorithm)
        signature.initSign(privateKey)
        signature.update(payload)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign())
    }

    fun hasKey(): Boolean = keyStore.containsAlias(keyAlias)

    fun ensureKeyPair() {
        if (hasKey()) return
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, AndroidKeyStore)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()
        generator.initialize(spec)
        generator.generateKeyPair()
    }

    private val privateKey: PrivateKey
        get() = (keyStore.getEntry(keyAlias, null) as KeyStore.PrivateKeyEntry).privateKey

    private val publicKey
        get() = (keyStore.getEntry(keyAlias, null) as KeyStore.PrivateKeyEntry).certificate.publicKey

    companion object {
        private const val AndroidKeyStore = "AndroidKeyStore"
        private const val SignatureAlgorithm = "SHA256withECDSA"
        private const val DefaultAlias = "trustipay_offline_device_signing_key"
    }
}
