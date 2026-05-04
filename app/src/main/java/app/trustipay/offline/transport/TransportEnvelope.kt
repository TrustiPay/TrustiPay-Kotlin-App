package app.trustipay.offline.transport

import java.time.Instant

data class TransportEnvelope(
    val protocolVersion: Int,
    val transportSessionId: String,
    val messageId: String,
    val messageType: String,
    val chunkIndex: Int,
    val chunkCount: Int,
    val payloadEncoding: String,
    val payloadHash: String,
    val previousHash: String? = null,
    val payloadChunk: String,
    val sentAtDevice: Instant,
)
