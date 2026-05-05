package app.trustipay.offline.transport.qr

import android.graphics.Bitmap
import android.graphics.Color
import app.trustipay.offline.transport.TransportEnvelope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import app.trustipay.offline.domain.PaymentOffer
import app.trustipay.offline.domain.PaymentOfferToken
import app.trustipay.offline.domain.PaymentReceipt
import app.trustipay.offline.domain.PaymentRequest
import app.trustipay.offline.domain.TransportType
import app.trustipay.offline.domain.LocalValidationResult
import org.json.JSONObject
import java.time.Instant

class QrCodeGenerator {
    private val writer = QRCodeWriter()

    fun generate(iou: app.trustipay.offline.domain.OfflineIOU, sizePx: Int = 512): Bitmap {
        val content = encodeIOU(iou)
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    fun encodeIOU(iou: app.trustipay.offline.domain.OfflineIOU): String {
        return iou.toMinifiedJson()
    }

    fun decodeIOU(raw: String): app.trustipay.offline.domain.OfflineIOU? = runCatching {
        app.trustipay.offline.domain.OfflineIOU.fromJson(raw)
    }.getOrNull()

    fun generateIOU(iou: app.trustipay.offline.domain.OfflineIOU, sizePx: Int = 512): Bitmap {
        val content = encodeIOU(iou)
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    fun decodeRequest(raw: String): PaymentRequest? = runCatching {
        val j = JSONObject(raw)
        PaymentRequest(
            protocolVersion = j.getInt("protocolVersion"),
            messageType = j.getString("messageType"),
            requestId = j.getString("requestId"),
            receiverUserAlias = j.getString("receiverUserAlias"),
            receiverDeviceId = j.getString("receiverDeviceId"),
            receiverPublicKeyId = j.getString("receiverPublicKeyId"),
            amountMinor = j.getLong("amountMinor"),
            currency = j.getString("currency"),
            description = j.getString("description"),
            createdAtDevice = Instant.parse(j.getString("createdAtDevice")),
            expiresAtDevice = Instant.parse(j.getString("expiresAtDevice")),
            nonce = j.getString("nonce"),
            supportedTransports = j.getJSONArray("supportedTransports").let { arr ->
                (0 until arr.length()).map { TransportType.valueOf(arr.getString(it)) }
            },
            receiverSignature = j.getString("receiverSignature")
        )
    }.getOrNull()

    fun decodeOffer(raw: String): PaymentOffer? = runCatching {
        val j = JSONObject(raw)
        PaymentOffer(
            protocolVersion = j.getInt("protocolVersion"),
            messageType = j.getString("messageType"),
            transactionId = j.getString("transactionId"),
            requestId = j.getString("requestId"),
            senderUserAlias = j.getString("senderUserAlias"),
            senderDeviceId = j.getString("senderDeviceId"),
            senderPublicKeyId = j.getString("senderPublicKeyId"),
            receiverDeviceId = j.getString("receiverDeviceId"),
            amountMinor = j.getLong("amountMinor"),
            currency = j.getString("currency"),
            offlineTokens = j.getJSONArray("offlineTokens").let { arr ->
                (0 until arr.length()).map { 
                    val t = arr.getJSONObject(it)
                    PaymentOfferToken(
                        tokenId = t.getString("tokenId"),
                        amountMinor = t.getLong("amountMinor"),
                        currency = t.getString("currency"),
                        issuedAtServer = Instant.parse(t.getString("issuedAtServer")),
                        expiresAtServer = Instant.parse(t.getString("expiresAtServer")),
                        issuerKeyId = t.getString("issuerKeyId"),
                        nonce = t.getString("nonce"),
                        serverSignature = t.getString("serverSignature"),
                        canonicalPayloadBase64Url = t.optString("canonicalPayloadBase64Url")
                    )
                }
            },
            requestHash = j.getString("requestHash"),
            senderPreviousHash = j.getString("senderPreviousHash"),
            createdAtDevice = Instant.parse(j.getString("createdAtDevice")),
            nonce = j.getString("nonce"),
            senderSignature = j.getString("senderSignature")
        )
    }.getOrNull()

    fun decodeReceipt(raw: String): PaymentReceipt? = runCatching {
        val j = JSONObject(raw)
        PaymentReceipt(
            protocolVersion = j.getInt("protocolVersion"),
            messageType = j.getString("messageType"),
            transactionId = j.getString("transactionId"),
            requestId = j.getString("requestId"),
            receiverDeviceId = j.getString("receiverDeviceId"),
            senderDeviceId = j.getString("senderDeviceId"),
            offerHash = j.getString("offerHash"),
            receiverPreviousHash = j.getString("receiverPreviousHash"),
            acceptedAtDevice = Instant.parse(j.getString("acceptedAtDevice")),
            localValidationResult = LocalValidationResult.valueOf(j.getString("localValidationResult")),
            nonce = j.getString("nonce"),
            receiverSignature = j.getString("receiverSignature")
        )
    }.getOrNull()

    fun decodeEnvelopeFromString(raw: String): TransportEnvelope? = runCatching {
        val j = JSONObject(raw)
        TransportEnvelope(
            protocolVersion = j.getInt("pv"),
            transportSessionId = j.getString("sid"),
            messageId = j.getString("mid"),
            messageType = j.getString("mt"),
            chunkIndex = j.getInt("ci"),
            chunkCount = j.getInt("cc"),
            payloadEncoding = j.getString("enc"),
            payloadHash = j.getString("hash"),
            previousHash = j.optString("prev").takeIf { it.isNotBlank() },
            payloadChunk = j.getString("payload"),
            sentAtDevice = Instant.parse(j.getString("sent")),
        )
    }.getOrNull()
}
