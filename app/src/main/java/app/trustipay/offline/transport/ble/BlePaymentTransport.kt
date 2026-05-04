package app.trustipay.offline.transport.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
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

class BlePaymentTransport(
    private val context: Context,
    private val enabled: Boolean = true,
    private val maxChunkBytes: Int = 180,
) : PaymentTransport {
    override val type: TransportType = TransportType.BLE

    private val _incomingMessages = MutableSharedFlow<TransportEnvelope>(extraBufferCapacity = 64)
    private var role: TransportRole? = null
    private var gattServer: BleGattServer? = null
    private var gattClient: BleGattClient? = null

    override suspend fun isAvailable(): Boolean {
        if (!enabled) return false
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return manager.adapter?.isEnabled == true
    }

    override suspend fun startSession(role: TransportRole, session: PaymentSession): Result<Unit> {
        this.role = role
        return runCatching {
            when (role) {
                TransportRole.RECEIVER -> startAsPeripheral()
                TransportRole.SENDER -> startAsCentral()
            }
        }
    }

    private fun startAsPeripheral() {
        val server = BleGattServer(context) { data ->
            parseEnvelope(String(data, Charsets.UTF_8))?.let { _incomingMessages.tryEmit(it) }
        }
        server.start()
        gattServer = server

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val advertiser = manager.adapter.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(BleGattServer.PAYMENT_SERVICE_UUID))
            .build()
        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    private fun startAsCentral() {
        val client = BleGattClient(context) { data ->
            parseEnvelope(String(data, Charsets.UTF_8))?.let { _incomingMessages.tryEmit(it) }
        }
        gattClient = client

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = manager.adapter.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleGattServer.PAYMENT_SERVICE_UUID))
            .build()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(listOf(filter), scanSettings, scanCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {}

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            manager.adapter.bluetoothLeScanner?.stopScan(this)
            gattClient?.connect(result.device)
        }
    }

    override suspend fun send(peerId: String, envelope: TransportEnvelope): Result<Unit> {
        return runCatching {
            val json = envelopeToJson(envelope)
            val bytes = json.toByteArray(Charsets.UTF_8)
            when (role) {
                TransportRole.RECEIVER -> gattServer?.notify(bytes)
                TransportRole.SENDER -> gattClient?.write(bytes)
                null -> {}
            }
        }
    }

    override fun incomingMessages(): Flow<TransportEnvelope> = _incomingMessages.asSharedFlow()

    override suspend fun close() {
        gattServer?.stop()
        gattClient?.disconnect()
        gattServer = null
        gattClient = null
        role = null
    }

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
}
