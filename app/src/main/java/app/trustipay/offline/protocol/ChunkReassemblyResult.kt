package app.trustipay.offline.protocol

sealed class ChunkReassemblyResult {
    data class Success(val payload: ByteArray) : ChunkReassemblyResult()
    data class Failure(val reason: String) : ChunkReassemblyResult()
}
