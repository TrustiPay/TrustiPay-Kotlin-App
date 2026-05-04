package app.trustipay.offline.transport.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import app.trustipay.offline.transport.TransportEnvelope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.time.Instant

class TrustiPayHceService : HostApduService() {
    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        return try {
            when {
                isSelectAid(commandApdu) -> {
                    // Respond with the pending bootstrap payload if available
                    val bootstrap = pendingBootstrapJson
                    if (bootstrap != null) {
                        STATUS_SUCCESS + bootstrap.toByteArray(Charsets.UTF_8)
                    } else {
                        STATUS_NO_DATA
                    }
                }
                isGetData(commandApdu) -> {
                    val envelope = pendingOutgoingEnvelope
                    if (envelope != null) {
                        STATUS_SUCCESS + envelope.toByteArray(Charsets.UTF_8)
                    } else {
                        STATUS_NO_DATA
                    }
                }
                isPutData(commandApdu) -> {
                    val dataLen = commandApdu[4].toInt() and 0xFF
                    val data = commandApdu.drop(5).take(dataLen).toByteArray()
                    val raw = String(data, Charsets.UTF_8)
                    val envelope = parseEnvelope(raw)
                    if (envelope != null) {
                        runBlocking { incomingEnvelopes.emit(envelope) }
                    }
                    STATUS_SUCCESS
                }
                else -> STATUS_UNKNOWN_CMD
            }
        } catch (_: Exception) {
            STATUS_UNKNOWN_CMD
        }
    }

    override fun onDeactivated(reason: Int) {
        // NFC field lost — notify transport if needed
    }

    private fun isSelectAid(apdu: ByteArray): Boolean =
        apdu.size >= 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte()

    private fun isGetData(apdu: ByteArray): Boolean =
        apdu.size >= 4 && apdu[0] == 0x00.toByte() && apdu[1] == 0xCA.toByte()

    private fun isPutData(apdu: ByteArray): Boolean =
        apdu.size >= 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xDA.toByte()

    private fun parseEnvelope(raw: String): TransportEnvelope? = runCatching {
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

    companion object {
        // Shared state between HCE service and NfcPaymentTransport
        val incomingEnvelopes = MutableSharedFlow<TransportEnvelope>(extraBufferCapacity = 64)
        var pendingBootstrapJson: String? = null
        var pendingOutgoingEnvelope: String? = null

        private val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val STATUS_NO_DATA = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        private val STATUS_UNKNOWN_CMD = byteArrayOf(0x6D.toByte(), 0x00.toByte())
    }
}
