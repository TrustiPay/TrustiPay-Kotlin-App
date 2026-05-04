package app.trustipay.offline.transport.wifidirect

import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import app.trustipay.offline.domain.PaymentSession
import app.trustipay.offline.domain.TransportRole
import app.trustipay.offline.domain.TransportType
import app.trustipay.offline.transport.PaymentTransport
import app.trustipay.offline.transport.TransportEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.time.Instant

class WifiDirectPaymentTransport(
    private val context: Context,
    private val enabled: Boolean = true,
) : PaymentTransport {
    override val type: TransportType = TransportType.WIFI_DIRECT

    private val _incomingMessages = MutableSharedFlow<TransportEnvelope>(extraBufferCapacity = 64)
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var role: TransportRole? = null

    private val wifiP2pManager: WifiP2pManager by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    }
    private val channel: WifiP2pManager.Channel by lazy {
        wifiP2pManager.initialize(context, context.mainLooper, null)
    }

    override suspend fun isAvailable(): Boolean {
        return enabled
    }

    override suspend fun startSession(role: TransportRole, session: PaymentSession): Result<Unit> {
        this.role = role
        return runCatching {
            when (role) {
                TransportRole.RECEIVER -> startAsGroupOwner()
                TransportRole.SENDER -> discoverAndConnect()
            }
        }
    }

    private suspend fun startAsGroupOwner() = withContext(Dispatchers.IO) {
        val socket = ServerSocket(PORT)
        serverSocket = socket
        val connection = socket.accept()
        clientSocket = connection
        writer = PrintWriter(connection.getOutputStream(), true)
        val reader = BufferedReader(InputStreamReader(connection.getInputStream()))
        // Read incoming envelopes in a loop
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { raw ->
                    parseEnvelope(raw)?.let { _incomingMessages.tryEmit(it) }
                }
            }
        } catch (_: Exception) {}
    }

    private fun discoverAndConnect() {
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) {}
        })
        wifiP2pManager.requestPeers(channel) { peers ->
            val device: WifiP2pDevice = peers.deviceList.firstOrNull() ?: return@requestPeers
            val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
            wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {}
                override fun onFailure(reason: Int) {}
            })
        }
    }

    fun onGroupOwnerAddressKnown(groupOwnerAddress: String) {
        Thread {
            try {
                val socket = Socket(groupOwnerAddress, PORT)
                clientSocket = socket
                writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { raw ->
                        parseEnvelope(raw)?.let { _incomingMessages.tryEmit(it) }
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    override suspend fun send(peerId: String, envelope: TransportEnvelope): Result<Unit> {
        return runCatching {
            val json = envelopeToJson(envelope)
            writer?.println(json) ?: error("Not connected")
        }
    }

    override fun incomingMessages(): Flow<TransportEnvelope> = _incomingMessages.asSharedFlow()

    override suspend fun close() = withContext(Dispatchers.IO) {
        writer?.close()
        clientSocket?.close()
        serverSocket?.close()
        writer = null
        clientSocket = null
        serverSocket = null
        role = null
        wifiP2pManager.removeGroup(channel, null)
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

    companion object {
        private const val PORT = 9876
    }
}
