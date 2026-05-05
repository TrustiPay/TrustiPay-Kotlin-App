package app.trustipay.offline.protocol

import app.trustipay.offline.transport.TransportEnvelope
import java.time.Clock
import java.util.Base64

class ChunkingService(
    private val maxPayloadBytes: Int = 512,
    private val clock: Clock = Clock.systemUTC(),
) {
    init {
        require(maxPayloadBytes > 32) { "Chunk size must leave room for useful payload." }
    }

    fun chunk(
        transportSessionId: String,
        messageId: String,
        messageType: String,
        payload: ByteArray,
        previousHash: String? = null,
    ): List<TransportEnvelope> {
        val payloadHash = MessageHasher.sha256Base64Url(payload)
        return payload.asList()
            .chunked(maxPayloadBytes)
            .map { it.toByteArray() }
            .let { chunks ->
                chunks.mapIndexed { index, bytes ->
                    TransportEnvelope(
                        protocolVersion = ProtocolVersioning.CURRENT_VERSION,
                        transportSessionId = transportSessionId,
                        messageId = messageId,
                        messageType = messageType,
                        chunkIndex = index,
                        chunkCount = chunks.size,
                        payloadEncoding = PayloadEncoding,
                        payloadHash = payloadHash,
                        previousHash = previousHash,
                        payloadChunk = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
                        sentAtDevice = clock.instant(),
                    )
                }
            }
    }

    fun reassemble(chunks: Collection<TransportEnvelope>): ChunkReassemblyResult {
        if (chunks.isEmpty()) return ChunkReassemblyResult.Failure("No chunks supplied.")
        val first = chunks.first()
        if (chunks.any { it.messageId != first.messageId || it.payloadHash != first.payloadHash }) {
            return ChunkReassemblyResult.Failure("Chunk metadata is inconsistent.")
        }
        if (chunks.size != first.chunkCount) {
            return ChunkReassemblyResult.Failure("Missing chunks.")
        }
        val byIndex = chunks.associateBy { it.chunkIndex }
        if (byIndex.size != chunks.size || byIndex.keys != (0 until first.chunkCount).toSet()) {
            return ChunkReassemblyResult.Failure("Duplicate or out-of-range chunks.")
        }
        val payload = (0 until first.chunkCount).flatMap { index ->
            val envelope = byIndex.getValue(index)
            runCatching {
                Base64.getUrlDecoder().decode(envelope.payloadChunk).asIterable()
            }.getOrElse {
                return ChunkReassemblyResult.Failure("Chunk payload is not valid base64url.")
            }
        }.toByteArray()
        val hash = MessageHasher.sha256Base64Url(payload)
        return if (hash == first.payloadHash) {
            ChunkReassemblyResult.Success(payload)
        } else {
            ChunkReassemblyResult.Failure("Payload hash mismatch.")
        }
    }

    companion object {
        const val PayloadEncoding = "CANONICAL_JSON_BASE64URL"
    }
}
