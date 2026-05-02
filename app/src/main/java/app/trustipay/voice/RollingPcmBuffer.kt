package app.trustipay.voice

class RollingPcmBuffer(
    private val maxBytes: Int,
) {
    private var bytes = ByteArray(0)

    @Synchronized
    fun append(chunk: ByteArray) {
        if (chunk.isEmpty()) return

        val merged = ByteArray(bytes.size + chunk.size)
        bytes.copyInto(merged)
        chunk.copyInto(merged, destinationOffset = bytes.size)
        bytes = if (merged.size > maxBytes) {
            merged.copyOfRange(merged.size - maxBytes, merged.size)
        } else {
            merged
        }
    }

    @Synchronized
    fun snapshot(): ByteArray = bytes.copyOf()

    @Synchronized
    fun clear() {
        bytes = ByteArray(0)
    }
}
