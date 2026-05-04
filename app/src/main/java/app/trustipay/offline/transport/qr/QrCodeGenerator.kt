package app.trustipay.offline.transport.qr

import android.graphics.Bitmap
import android.graphics.Color
import app.trustipay.offline.transport.TransportEnvelope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject

class QrCodeGenerator {
    private val writer = QRCodeWriter()

    fun generate(envelope: TransportEnvelope, sizePx: Int = 512): Bitmap {
        val content = encodeEnvelopeToString(envelope)
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    fun encodeEnvelopeToString(envelope: TransportEnvelope): String =
        JSONObject().apply {
            put("pv", envelope.protocolVersion)
            put("sid", envelope.transportSessionId)
            put("mid", envelope.messageId)
            put("mt", envelope.messageType)
            put("ci", envelope.chunkIndex)
            put("cc", envelope.chunkCount)
            put("enc", envelope.payloadEncoding)
            put("hash", envelope.payloadHash)
            envelope.previousHash?.let { put("prev", it) }
            put("payload", envelope.payloadChunk)
            put("sent", envelope.sentAtDevice.toString())
        }.toString()

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
            sentAtDevice = java.time.Instant.parse(j.getString("sent")),
        )
    }.getOrNull()
}
