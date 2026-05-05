package app.trustipay.offline.security

import android.util.Base64
import app.trustipay.offline.domain.OfflineIOU
import org.json.JSONObject
import java.security.MessageDigest
import java.util.TreeMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object IOUCryptography {
    private const val HMAC_ALGO = "HmacSHA256"
    private const val SHA256_ALGO = "SHA-256"
    
    // Prototype secret - in production this should be per-device/per-user and managed securely
    private val PROTOTYPE_SECRET = "trustipay-prototype-secret-key-2025".toByteArray()

    fun sign(iou: OfflineIOU): OfflineIOU {
        val dataToSign = getDataToSign(iou)
        val signature = hmacSha256(dataToSign, PROTOTYPE_SECRET)
        return iou.copy(signature = signature)
    }

    fun verify(iou: OfflineIOU): Boolean {
        val dataToSign = getDataToSign(iou)
        val expectedSignature = hmacSha256(dataToSign, PROTOTYPE_SECRET)
        return iou.signature == expectedSignature
    }

    fun hash(data: String): String {
        val digest = MessageDigest.getInstance(SHA256_ALGO)
        val hashBytes = digest.digest(data.toByteArray())
        return Base64.encodeToString(hashBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun generateOTP(signature: String): String {
        // Simple OTP generation from signature for prototype
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(signature.toByteArray())
        val number = ((hash[0].toInt() and 0xFF) shl 24) or
                     ((hash[1].toInt() and 0xFF) shl 16) or
                     ((hash[2].toInt() and 0xFF) shl 8) or
                     (hash[3].toInt() and 0xFF)
        return String.format("%06d", Math.abs(number % 1000000))
    }

    /**
     * Creates a canonical string representation of the IOU for signing.
     * We sign the MINIFIED version to ensure the signature is based exactly 
     * on what is transmitted via QR, and to save space.
     */
    private fun getDataToSign(iou: OfflineIOU): String {
        val map = TreeMap<String, Any>()
        map["t"] = iou.tx_id
        map["s"] = iou.sender_id
        map["r"] = iou.receiver_id
        map["ts"] = iou.timestamp
        map["a"] = iou.amount
        map["c"] = iou.category
        map["l"] = iou.location
        map["d"] = iou.device_id
        map["dt"] = iou.device_type
        map["nt"] = iou.network_type
        map["n"] = iou.nonce
        map["p"] = iou.prev_hash
        
        return JSONObject(map as Map<*, *>).toString()
    }

    private fun hmacSha256(data: String, secret: ByteArray): String {
        val mac = Mac.getInstance(HMAC_ALGO)
        val secretKey = SecretKeySpec(secret, HMAC_ALGO)
        mac.init(secretKey)
        val hmacBytes = mac.doFinal(data.toByteArray())
        return Base64.encodeToString(hmacBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
