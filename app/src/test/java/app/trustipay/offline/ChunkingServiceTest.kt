package app.trustipay.offline

import app.trustipay.offline.protocol.ChunkReassemblyResult
import app.trustipay.offline.protocol.ChunkingService
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkingServiceTest {
    @Test
    fun chunkAndReassemble_restoresOriginalPayload() {
        val service = ChunkingService(maxPayloadBytes = 40)
        val payload = "signed-payload-".repeat(30).toByteArray()

        val chunks = service.chunk("session-1", "msg-1", "PAYMENT_OFFER", payload)
        val result = service.reassemble(chunks.shuffled())

        assertTrue(result is ChunkReassemblyResult.Success)
        assertArrayEquals(payload, (result as ChunkReassemblyResult.Success).payload)
    }
}
