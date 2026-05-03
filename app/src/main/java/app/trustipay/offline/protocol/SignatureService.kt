package app.trustipay.offline.protocol

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

interface PayloadSigner {
    val publicKeyId: String
    fun sign(payload: ByteArray): String
}

interface SignatureVerifier {
    fun verify(publicKeyId: String, payload: ByteArray, signatureBase64Url: String): Boolean
}

class JavaPayloadSigner(
    private val privateKey: PrivateKey,
    override val publicKeyId: String,
) : PayloadSigner {
    override fun sign(payload: ByteArray): String {
        val signature = Signature.getInstance(EcdsaAlgorithm)
        signature.initSign(privateKey)
        signature.update(payload)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign())
    }
}

class PublicKeySignatureVerifier(
    private val publicKeysById: Map<String, PublicKey>,
) : SignatureVerifier {
    override fun verify(publicKeyId: String, payload: ByteArray, signatureBase64Url: String): Boolean {
        val publicKey = publicKeysById[publicKeyId] ?: return false
        return runCatching {
            val signatureBytes = Base64.getUrlDecoder().decode(signatureBase64Url)
            val signature = Signature.getInstance(EcdsaAlgorithm)
            signature.initVerify(publicKey)
            signature.update(payload)
            signature.verify(signatureBytes)
        }.getOrDefault(false)
    }
}

data class GeneratedSigningKey(
    val keyPair: KeyPair,
    val publicKeyId: String,
) {
    fun signer(): PayloadSigner = JavaPayloadSigner(keyPair.private, publicKeyId)
}

object JavaSigningKeyFactory {
    fun generate(publicKeyId: String): GeneratedSigningKey {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return GeneratedSigningKey(generator.generateKeyPair(), publicKeyId)
    }
}

private const val EcdsaAlgorithm = "SHA256withECDSA"
