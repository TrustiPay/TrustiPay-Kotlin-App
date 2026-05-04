package app.trustipay.offline.transport.nfc

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import app.trustipay.offline.domain.PaymentSession
import app.trustipay.offline.domain.TransportRole
import app.trustipay.offline.domain.TransportType
import app.trustipay.offline.transport.PaymentTransport
import app.trustipay.offline.transport.TransportEnvelope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.time.Instant

class NfcPaymentTransport(
    private val context: Context,
    private val enabled: Boolean = true,
) : PaymentTransport {
    override val type: TransportType = TransportType.NFC

    private val _outgoingEnvelopes = MutableSharedFlow<TransportEnvelope>(extraBufferCapacity = 64)

    override suspend fun isAvailable(): Boolean {
        if (!enabled) return false
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return false
        return adapter.isEnabled
    }

    override suspend fun startSession(role: TransportRole, session: PaymentSession): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun send(peerId: String, envelope: TransportEnvelope): Result<Unit> {
        return runCatching {
            val json = envelopeToJson(envelope)
            TrustiPayHceService.pendingOutgoingEnvelope = json
        }
    }

    override fun incomingMessages(): Flow<TransportEnvelope> =
        TrustiPayHceService.incomingEnvelopes.asSharedFlow()

    override suspend fun close() {
        TrustiPayHceService.pendingOutgoingEnvelope = null
        TrustiPayHceService.pendingBootstrapJson = null
    }

    fun setBootstrapPayload(bootstrap: NfcBootstrapPayload) {
        TrustiPayHceService.pendingBootstrapJson = bootstrapToJson(bootstrap)
    }

    fun enableReaderMode(activity: Activity, onTagDiscovered: (Tag) -> Unit) {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return
        adapter.enableReaderMode(
            activity,
            { tag -> onTagDiscovered(tag) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    fun disableReaderMode(activity: Activity) {
        NfcAdapter.getDefaultAdapter(context)?.disableReaderMode(activity)
    }

    suspend fun readFromTag(tag: Tag): TransportEnvelope? {
        return runCatching {
            val isoDep = IsoDep.get(tag) ?: return null
            isoDep.connect()
            val aidApdu = buildSelectAidApdu()
            val response = isoDep.transceive(aidApdu)
            isoDep.close()
            if (response.size < 2) return null
            val status = response.takeLast(2)
            if (status[0] != 0x90.toByte() || status[1] != 0x00.toByte()) return null
            val payload = response.dropLast(2).toByteArray()
            parseEnvelope(String(payload, Charsets.UTF_8))
        }.getOrNull()
    }

    private fun buildSelectAidApdu(): ByteArray {
        val aid = AID.decodeHex()
        return byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid
    }

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

    private fun envelopeToJson(envelope: TransportEnvelope): String =
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

    private fun bootstrapToJson(bootstrap: NfcBootstrapPayload): String =
        JSONObject().apply {
            put("type", bootstrap.type)
            put("pv", bootstrap.protocolVersion)
            put("receiverDeviceId", bootstrap.receiverDeviceId)
            put("receiverPublicKeyId", bootstrap.receiverPublicKeyId)
            put("amountMinor", bootstrap.amountMinor)
            put("currency", bootstrap.currency)
            put("sessionId", bootstrap.sessionId)
            put("transports", org.json.JSONArray(bootstrap.supportedTransports.map { it.name }))
            bootstrap.previousHash?.let { put("prev", it) }
        }.toString()

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Hex string must have even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    companion object {
        private const val AID = "F0544255535449504159"
    }
}
